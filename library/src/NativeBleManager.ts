import { NativeEventEmitter, NativeModules } from 'react-native'
import { NativeBleInterface, type INativeBleInterface } from './NativeBleInterface'
import { PromiseSequencer } from './PromiseSequencer'
import { BleError, BleErrorCode } from './BleError'
import {
  BleCharacteristicProperty,
  type BleCharacteristicInfo,
  type BleConnectedDeviceInfo,
  type BleDeviceInfo,
  type BleManager,
  type ILogger,
  type Subscription,
} from './types'

// as required by the standard
const MAX_BLE_CHAR_SIZE = 512

export class NativeBleManager implements BleManager {
  private nativeInterface: INativeBleInterface | null = null
  private initialized = false
  private connectedDevice: BleConnectedDeviceInfo | null = null
  private readonly sequencer = new PromiseSequencer()

  private nScanActive = 0
  private nSubscriptions = new Map<string, number>()
  private onErrorSubscription: Subscription | null = null

  constructor(private readonly logger?: ILogger) {}

  private ensureInitialized() {
    if (!this.initialized) {
      throw new BleError(BleErrorCode.BleNotInitializedError, 'BLE manager not initialized')
    }
  }

  private ensureConnected() {
    if (!this.connectedDevice) {
      throw new BleError(BleErrorCode.BleNotConnectedError, 'BLE device not connected')
    }
  }

  private getChar(service: string, characteristic: string): BleCharacteristicInfo {
    if (!this.connectedDevice) {
      throw new BleError(BleErrorCode.BleNotConnectedError, 'BLE device not connected')
    }

    const serviceInfo = this.connectedDevice.services.find((s) => s.uuid === service)
    if (!serviceInfo) {
      throw new BleError(BleErrorCode.BleCharacteristicNotFoundError, `BLE service ${service} not found`)
    }

    const charInfo = serviceInfo.characteristics.find((c) => c.uuid === characteristic)
    if (!charInfo) {
      throw new BleError(BleErrorCode.BleCharacteristicNotFoundError, `BLE characteristic ${characteristic} not found`)
    }

    return charInfo
  }

  async init(): Promise<void> {
    if (!this.initialized) {
      const nativeModule = NativeModules.BleLibrary
      if (nativeModule === undefined) {
        throw new Error(`Ble native module not found. Ensure to link the library correctly!`)
      }
      const nativeEventEmitter = new NativeEventEmitter(nativeModule)
      this.nativeInterface = new NativeBleInterface(nativeModule, nativeEventEmitter, this.logger)

      this.logger?.info('[BleManager] initializing module...')
      try {
        await this.nativeInterface.initModule()
        this.logger?.info('[BleManager] module initialized')
        this.initialized = true
      } catch (e: any) {
        this.logger?.error('[BleManager] error initializing module', e)
        throw new BleError(e.code, e.message)
      }
    }
  }

  scan(
    serviceUuids: string[] | null | undefined,
    onDiscover: (devices: BleDeviceInfo[]) => void,
    onError?: (error: BleError) => void
  ): Subscription {
    this.ensureInitialized()

    const subscription = this.nativeInterface!.addListener({
      onScanResult: (data) => onDiscover(data.devices),
      onError: (data) => {
        this.logger?.error('[BleManager] scan error', data)

        if (data.error === BleErrorCode.BleScanError && onError !== undefined) {
          onError(new BleError(data.error, data.message))
        }
      },
    })

    if (this.nScanActive === 0) {
      this.logger?.info('[BleManager] starting scan...')

      this.nativeInterface!.scanStart(serviceUuids?.map((s) => s.toLowerCase()))
        .then(() => {
          this.logger?.info('[BleManager] scan started')
          this.nScanActive += 1
        })
        .catch((e) => {
          this.logger?.error('[BleManager] error starting scan')
          onError?.(new BleError(e.code, e.message))
        })
    }
    this.nScanActive += 1

    return {
      unsubscribe: () => {
        subscription.unsubscribe()

        this.nScanActive -= 1
        if (this.nScanActive === 0) {
          this.logger?.info('[BleManager] stopping scan...')

          this.nativeInterface!.scanStop().catch((e) => {
            this.logger?.error('[BleManager] error stopping scan')

            onError?.(new BleError(e.code, e.message))
          })
        }
      },
    }
  }

  connect(id: string, mtu?: number, onError?: (error: BleError) => void): Promise<BleConnectedDeviceInfo> {
    this.logger?.info(`[BleManager] enqueue connect(${id}, ${mtu})`)
    return this.sequencer.execute(async () => {
      this.logger?.info(`[BleManager] execute connect(${id}, ${mtu})`)

      this.ensureInitialized()

      if (this.connectedDevice !== null) {
        throw new BleError(BleErrorCode.BleAlreadyConnectedError, 'BLE device already connected, call disconnect first')
      }

      if (this.onErrorSubscription) {
        this.onErrorSubscription.unsubscribe()
      }
      this.onErrorSubscription = this.nativeInterface!.addListener({
        onError: (data) => {
          if (data.error === BleErrorCode.BleDeviceDisconnectedError) {
            this.logger?.error(`[BleManager] device disconnected`, data)

            this.connectedDevice = null

            if (onError !== undefined) {
              onError(new BleError(data.error, data.message))
            }
          }
        },
      })

      try {
        const { services } = await this.nativeInterface!.connect(id, mtu ?? 0)
        this.logger?.debug(`[BleManager] connected to ${id}`, JSON.stringify(services, null, 2))

        this.connectedDevice = {
          id,
          services,
        }
        this.nSubscriptions.clear()

        return this.connectedDevice
      } catch (e: any) {
        throw new BleError(e.code, e.message)
      }
    })
  }

  disconnect(): Promise<void> {
    this.ensureInitialized()

    this.logger?.info('[BleManager] enqueue disconnect()')
    return this.sequencer.execute(async () => {
      this.logger?.info('[BleManager] execute disconnect()')
      if (this.connectedDevice) {
        try {
          await this.nativeInterface!.disconnect()
        } catch (e: any) {
          throw new BleError(e.code, e.message)
        } finally {
          this.onErrorSubscription?.unsubscribe()
          this.connectedDevice = null
          this.nSubscriptions.clear()
        }
      }
    })
  }

  read(
    service: string,
    characteristic: string,
    size?: number,
    progress?: (current: number, total: number) => void
  ): Promise<Buffer> {
    service = service.toLowerCase()
    characteristic = characteristic.toLowerCase()

    this.logger?.info(`[BleManager] enqueue read(${characteristic})`)
    return this.sequencer.execute(async () => {
      this.ensureInitialized()
      this.ensureConnected()

      const char = this.getChar(service, characteristic)
      if (!(char.properties & BleCharacteristicProperty.READ)) {
        throw new BleError(BleErrorCode.BleOperationNotAllowed, `characteristic ${characteristic} does not allow READ`)
      }

      this.logger?.info(`[BleManager] execute read(${characteristic})`)

      let subscription: Subscription | undefined
      if (progress !== undefined) {
        subscription = this.nativeInterface!.addListener({
          onReadProgress: (data) => {
            if (data.characteristic === characteristic && data.service === service) {
              this.logger?.info('[BleManager] read progress', data)

              progress(data.current, data.total)
            }
          },
        })
      }

      let result: string
      try {
        result = await this.nativeInterface!.read(service, characteristic, size ?? 0)
      } catch (e: any) {
        throw new BleError(e.code, e.message)
      }

      subscription?.unsubscribe()

      return Buffer.from(result, 'base64')
    })
  }

  write(
    service: string,
    characteristic: string,
    value: Buffer,
    chunkSize = MAX_BLE_CHAR_SIZE,
    progress?: (current: number, total: number) => void
  ): Promise<void> {
    service = service.toLowerCase()
    characteristic = characteristic.toLowerCase()

    this.logger?.info(
      `[BleManager] enqueue write(${characteristic}, ${value.subarray(0, 50).toString('base64')} (len: ${
        value.length
      }))`
    )
    return this.sequencer.execute(async () => {
      this.ensureInitialized()
      this.ensureConnected()

      const char = this.getChar(service, characteristic)
      if (!(char.properties & BleCharacteristicProperty.WRITE)) {
        throw new BleError(BleErrorCode.BleOperationNotAllowed, `characteristic ${characteristic} does not allow WRITE`)
      }

      this.logger?.info(
        `[BleManager] execute write(${characteristic}, ${value.subarray(0, 50).toString('base64')} (len: ${
          value.length
        })`
      )

      let subscription: Subscription | undefined
      if (progress !== undefined) {
        subscription = this.nativeInterface!.addListener({
          onWriteProgress: (data) => {
            if (data.characteristic === characteristic && data.service === service) {
              this.logger?.info('[BleManager] write progress', data)

              progress(data.current, data.total)
            }
          },
        })
      }

      try {
        await this.nativeInterface!.write(service, characteristic, value.toString('base64'), chunkSize)
      } catch (e: any) {
        throw new BleError(e.code, e.message)
      }

      subscription?.unsubscribe()
    })
  }

  subscribe(
    service: string,
    characteristic: string,
    callback: (value: Buffer) => void,
    onError?: (error: BleError) => void
  ): Subscription {
    service = service.toLowerCase()
    characteristic = characteristic.toLowerCase()

    this.ensureInitialized()
    this.ensureConnected()

    const char = this.getChar(service, characteristic)
    if (
      !(char.properties & BleCharacteristicProperty.NOTIFY) &&
      !(char.properties & BleCharacteristicProperty.INDICATE)
    ) {
      throw new BleError(
        BleErrorCode.BleOperationNotAllowed,
        `characteristic ${characteristic} does not allow subscriptions`
      )
    }

    const subscription = this.nativeInterface!.addListener({
      onCharValueChanged: (data) => {
        if (data.characteristic === characteristic && data.service === service) {
          this.logger?.info('[BleManager] char value changed', data)

          callback(Buffer.from(data.value, 'base64'))
        }
      },
    })

    const key = `${service}:${characteristic}`
    const nSubscriptions = this.nSubscriptions.get(key) ?? 0
    if (nSubscriptions === 0) {
      this.logger?.info(`[BleManager] enqueue subscribe(${characteristic})`)
      this.sequencer
        .execute(async () => {
          this.logger?.info(`[BleManager] execute subscribe(${characteristic})`)

          this.ensureInitialized()
          this.ensureConnected()

          this.nativeInterface!.subscribe(service, characteristic)
        })
        .then(() => {
          this.logger?.info('[BleManager] subscribed to ', characteristic)
          this.nSubscriptions.set(key, nSubscriptions + 1)
        })
        .catch((e) => {
          this.logger?.error('[BleManager] error subscribing to ', characteristic)

          if (onError !== undefined) {
            onError(new BleError(e.code, e.message))
          }
        })
    }

    return {
      unsubscribe: () => {
        subscription.unsubscribe()

        const nSubscriptions = this.nSubscriptions.get(key) ?? 0
        if (nSubscriptions === 1) {
          this.logger?.info(`[BleManager] enqueue unsubscribe(${characteristic})`)
          this.sequencer
            .execute(async () => {
              this.logger?.info(`[BleManager] execute unsubscribe(${characteristic})`)

              this.ensureInitialized()
              this.ensureConnected()

              this.nativeInterface!.unsubscribe(service, characteristic)
            })
            .then(() => {
              this.logger?.info('[BleManager] unsubscribed from ', characteristic)
              this.nSubscriptions.set(key, 0)
            })
            .catch((e) => {
              this.logger?.error('[BleManager] error unsubscribing from ', characteristic)

              if (onError !== undefined) {
                onError(new BleError(e.code, e.message))
              }
            })
        } else {
          this.nSubscriptions.set(key, nSubscriptions - 1)
        }
      },
    }
  }

  getRSSI(): Promise<number> {
    this.logger?.info(`[BleManager] enqueue readRSSI()`)
    return this.sequencer.execute(async () => {
      this.logger?.info(`[BleManager] execute readRSSI()`)

      this.ensureInitialized()
      this.ensureConnected()

      try {
        return this.nativeInterface!.readRSSI()
      } catch (e: any) {
        throw new BleError(e.code, e.message)
      }
    })
  }

  dispose(): void {
    if (this.initialized) {
      this.logger?.info('[BleManager] terminating manager')
      this.nativeInterface!.disposeModule()
      this.nativeInterface = null
      this.initialized = false
      this.connectedDevice = null
      this.nSubscriptions.clear()
      this.onErrorSubscription?.unsubscribe()
    }
  }

  get device() {
    return this.connectedDevice
  }
}

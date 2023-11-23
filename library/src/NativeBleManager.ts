import { NativeEventEmitter, NativeModules } from 'react-native'
import { BleError, BleErrorCode } from './BleError'
import { NativeBleInterface, type INativeBleInterface } from './NativeBleInterface'
import {
  ConnectionState,
  type BleConnectedDeviceInfo,
  type BleDeviceInfo,
  type BleManager,
  type ILogger,
  type Subscription,
} from './types'

// as required by the standard
const MAX_BLE_CHAR_SIZE = 512

export class NativeBleManager implements BleManager {
  private subscriptions: Subscription[] = []

  private readonly nativeInterface: INativeBleInterface
  private initialized = false
  private connectedDevice: BleConnectedDeviceInfo | null = null

  private nSubscriptions = new Map<string, number>()

  constructor(private readonly logger?: ILogger) {
    const nativeModule = NativeModules.BleLibrary
    if (nativeModule === undefined) {
      throw new Error(`Ble native module not found. Ensure to link the library correctly!`)
    }

    const nativeEventEmitter = new NativeEventEmitter(nativeModule)
    this.nativeInterface = new NativeBleInterface(nativeModule, nativeEventEmitter, this.logger)
  }

  private getTransactionId() {
    return `${Date.now()}-${Math.random()}`
  }

  private ensureInitialized() {
    if (!this.initialized) {
      throw new BleError(BleErrorCode.ERROR_NOT_INITIALIZED, 'BleManager not initialized')
    }
  }

  async init(): Promise<void> {
    if (!this.initialized) {
      this.subscriptions.push(
        this.nativeInterface.addListener({
          onConnectionStateChanged: ({ state, services }) => {
            this.logger?.debug('[BleManager] connection state changed', state)

            if (this.device) {
              this.device.connectionState = state

              if (services) {
                this.device.services = services
              }
            }
          },
        })
      )

      this.logger?.info('[BleManager] initializing module...')
      try {
        await this.nativeInterface.initModule()
        this.logger?.info('[BleManager] module initialized')
        this.initialized = true
      } catch (e: any) {
        this.logger?.error('[BleManager] error initializing module', e)
        this.removeSubscriptions()

        throw new BleError(e.code, e.message)
      }
    }
  }

  onConnectionStateChanged(callback: (state: ConnectionState, error: BleError | null) => void): Subscription {
    this.ensureInitialized()

    return this.nativeInterface!.addListener({
      onConnectionStateChanged: ({ state, error, message }) => {
        if (error) {
          callback(state, new BleError(error as BleErrorCode, message))
        } else {
          callback(state, null)
        }
      },
    })
  }

  scan(
    serviceUuids: string[] | null | undefined,
    onDiscover: (devices: BleDeviceInfo[]) => void,
    onError?: (error: BleError) => void
  ): Subscription {
    this.ensureInitialized()

    const subscription = this.nativeInterface!.addListener({
      onScanResult: (data) => {
        onDiscover(
          data.devices.map((d) => ({
            ...d,
            // ensure boolean are mapped correctly (for iOS)
            isConnectable: Boolean(d.isConnectable),
            isAvailable: Boolean(d.isAvailable),
          }))
        )
      },
      onError: (data) => {
        this.logger?.error('[BleManager] scan error', data)

        if (data.error === BleErrorCode.ERROR_SCAN && onError !== undefined) {
          onError(new BleError(data.error, data.message))
        }
      },
    })

    const startScan = async () => {
      await this.nativeInterface!.scanStart(serviceUuids?.map((s) => s.toLowerCase()))
    }

    startScan()
      .then(() => {
        this.logger?.info('[BleManager] scan started')
      })
      .catch((e) => {
        this.logger?.error('[BleManager] error starting scan')
        onError?.(new BleError(e.code, e.message))
      })

    return {
      unsubscribe: () => {
        subscription.unsubscribe()

        this.logger?.info('[BleManager] stopping scan...')

        this.nativeInterface!.scanStop()
          .catch((e) => {
            this.logger?.error('[BleManager] error stopping scan')

            onError?.(new BleError(e.code, e.message))
          })
          .then(() => {
            this.logger?.info('[BleManager] scan stopped')
          })
      },
    }
  }

  async connect(id: string, mtu?: number): Promise<void> {
    this.ensureInitialized()

    this.logger?.info(`[BleManager] execute connect(${id}, ${mtu})`)

    // now we should be in a state where it's safe to connect the device (hopefully)
    this.connectedDevice = null

    try {
      await this.nativeInterface!.connect(id, mtu ?? 0)

      this.logger?.debug(`[BleManager] starting connection to ${id}`)

      this.connectedDevice = {
        id,
        connectionState: ConnectionState.CONNECTING_TO_DEVICE,
      }
      this.nSubscriptions.clear()
    } catch (e: any) {
      this.connectedDevice = null

      throw new BleError(e.code, e.message)
    }
  }

  async disconnect(): Promise<void> {
    this.ensureInitialized()

    this.logger?.info('[BleManager] execute disconnect()')
    try {
      await this.nativeInterface!.disconnect()
    } catch (e: any) {
      throw new BleError(e.code, e.message)
    } finally {
      this.connectedDevice = null
      this.nSubscriptions.clear()
    }
  }

  async read(
    service: string,
    characteristic: string,
    size?: number,
    progress?: (current: number, total: number) => void,
    abortSignal?: AbortSignal
  ): Promise<Buffer> {
    this.logger?.info(`[BleManager] execute read(${service}, ${characteristic}, ${size})`)

    service = service.toLowerCase()
    characteristic = characteristic.toLowerCase()

    const transactionId = this.getTransactionId()

    const onAbort = () => {
      this.logger?.info(`[BleManager] canceling read(${service}, ${characteristic}, ${size})`)

      this.nativeInterface!.cancel(transactionId)
    }

    abortSignal?.addEventListener('abort', onAbort)

    let progressSubscription: Subscription | undefined
    if (progress !== undefined) {
      progressSubscription = this.nativeInterface!.addListener({
        onProgress: (data) => {
          if (data.transactionId === transactionId) {
            this.logger?.info('[BleManager] read progress', data)

            progress(data.current, data.total)
          }
        },
      })
    }

    let result: string
    try {
      result = await this.nativeInterface!.read(transactionId, service, characteristic, size ?? 0)
    } catch (e: any) {
      throw new BleError(e.code, e.message)
    } finally {
      abortSignal?.removeEventListener('abort', onAbort)
      progressSubscription?.unsubscribe()
    }

    return Buffer.from(result, 'base64')
  }

  async write(
    service: string,
    characteristic: string,
    value: Buffer,
    chunkSize = MAX_BLE_CHAR_SIZE,
    progress?: (current: number, total: number) => void,
    abortSignal?: AbortSignal
  ): Promise<void> {
    this.ensureInitialized()

    service = service.toLowerCase()
    characteristic = characteristic.toLowerCase()

    this.logger?.info(
      `[BleManager] execute write(${characteristic}, ${value.subarray(0, 50).toString('base64')} (len: ${
        value.length
      }))`
    )

    const transactionId = this.getTransactionId()
    const onAbort = () => {
      this.logger?.info(
        `[BleManager] canceling write(${characteristic}, ${value.subarray(0, 50).toString('base64')} (len: ${
          value.length
        }))`
      )

      this.nativeInterface!.cancel(transactionId)
    }

    abortSignal?.addEventListener('abort', onAbort)

    let progressSubscription: Subscription | undefined

    if (progress !== undefined) {
      progressSubscription = this.nativeInterface!.addListener({
        onProgress: (data) => {
          if (data.transactionId === transactionId) {
            this.logger?.info('[BleManager] write progress', data)

            progress(data.current, data.total)
          }
        },
      })
    }

    try {
      await this.nativeInterface!.write(transactionId, service, characteristic, value.toString('base64'), chunkSize)
    } catch (e: any) {
      throw new BleError(e.code, e.message)
    } finally {
      abortSignal?.removeEventListener('abort', onAbort)
      progressSubscription?.unsubscribe()
    }
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
      this.logger?.info(`[BleManager] execute subscribe(${characteristic})`)

      this.nativeInterface!.subscribe(this.getTransactionId(), service, characteristic)
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
          this.nSubscriptions.set(key, 0)
          this.nativeInterface!.unsubscribe(this.getTransactionId(), service, characteristic)
            .then(() => {
              this.logger?.info('[BleManager] unsubscribed from ', characteristic)
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
    this.logger?.info(`[BleManager] execute readRSSI()`)

    this.ensureInitialized()

    const transactionId = this.getTransactionId()

    try {
      return this.nativeInterface!.readRSSI(transactionId)
    } catch (e: any) {
      throw new BleError(e.code, e.message)
    }
  }

  dispose(): void {
    this.removeSubscriptions()

    if (this.initialized) {
      this.logger?.info('[BleManager] terminating manager')
      this.nativeInterface.disposeModule()
      this.initialized = false
      this.connectedDevice = null
      this.nSubscriptions.clear()
    }
  }

  get device() {
    return this.connectedDevice
  }

  private removeSubscriptions() {
    for (const subscription of this.subscriptions) {
      subscription.unsubscribe()
    }
    this.subscriptions = []
  }
}

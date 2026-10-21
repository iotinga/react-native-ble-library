import { Buffer } from 'buffer'
import { EventSubscription } from 'expo-modules-core'
import { BleError, BleErrorCode } from './BleError'
import ReactNativeBleLibraryModule from './ReactNativeBleLibraryModule'
import {
  BleServiceInfo,
  ConnectionState,
  ConnectOptions,
  type BleDeviceInfo,
  type BleManager,
  type BleWriteType,
  type ILogger,
} from './types'

// as required by the standard
const MAX_BLE_CHAR_SIZE = 512

function getNativeModule() {
  if (ReactNativeBleLibraryModule === undefined) {
    throw new Error(`Native module not found (running on web?)`)
  }

  return ReactNativeBleLibraryModule
}

export class NativeBleManager implements BleManager {
  private initialized = false
  private nSubscriptions = new Map<string, number>()

  constructor(private readonly logger?: ILogger) {}

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
      this.logger?.info('[BleManager] initializing module...')
      try {
        await getNativeModule().initModule()
        this.logger?.info('[BleManager] module initialized')
        this.initialized = true
      } catch (e: any) {
        this.logger?.error('[BleManager] error initializing module', e)
        throw new BleError(e.code, e.message)
      }
    }
  }

  onConnectionStateChanged(
    callback: (state: ConnectionState, error: BleError | null, services?: BleServiceInfo[]) => void
  ): EventSubscription {
    return getNativeModule().addListener('onConnectionStateChanged', ({ state, error, services, message }) => {
      if (error) {
        callback(state, new BleError(error as BleErrorCode, message), services)
      } else {
        callback(state, null, services)
      }
    })
  }

  scan(
    serviceUuids: string[] | null | undefined,
    onDiscover: (devices: BleDeviceInfo[]) => void,
    onError?: (error: BleError) => void
  ): EventSubscription {
    this.ensureInitialized()

    const subscriptions = [
      getNativeModule().addListener('onScanResult', data => {
        onDiscover(
          data.devices.map(d => ({
            ...d,
            // ensure boolean are mapped correctly (for iOS)
            isConnectable: Boolean(d.isConnectable),
            isAvailable: Boolean(d.isAvailable),
          }))
        )
      }),

      getNativeModule().addListener('onError', data => {
        this.logger?.error('[BleManager] scan error', data)

        if (data.error === BleErrorCode.ERROR_SCAN && onError !== undefined) {
          onError(new BleError(data.error, data.message))
        }
      }),
    ]

    const startScan = async () => {
      await getNativeModule().scanStart(serviceUuids?.map(s => s.toLowerCase()))
    }

    startScan()
      .then(() => {
        this.logger?.info('[BleManager] scan started')
      })
      .catch(e => {
        this.logger?.error('[BleManager] error starting scan')
        onError?.(new BleError(e.code, e.message))
      })

    return {
      remove: () => {
        for (const subscription of subscriptions) {
          subscription.remove()
        }

        this.logger?.info('[BleManager] stopping scan...')

        getNativeModule()
          .scanStop()
          .catch(e => {
            this.logger?.error('[BleManager] error stopping scan')

            onError?.(new BleError(e.code, e.message))
          })
          .then(() => {
            this.logger?.info('[BleManager] scan stopped')
          })
      },
    }
  }

  async connect(id: string, mtu?: number, options?: ConnectOptions): Promise<void> {
    this.ensureInitialized()

    this.logger?.info(`[BleManager] execute connect(${id}, ${mtu})`)

    try {
      await getNativeModule().connect(id, mtu ?? 0, options)

      this.logger?.debug(`[BleManager] starting connection to ${id}`)
      this.nSubscriptions.clear()
    } catch (e: any) {
      throw new BleError(e.code, e.message)
    }
  }

  async disconnect(): Promise<void> {
    this.ensureInitialized()

    this.logger?.info('[BleManager] execute disconnect()')
    try {
      await getNativeModule().disconnect()
    } catch (e: any) {
      throw new BleError(e.code, e.message)
    } finally {
      this.nSubscriptions.clear()
    }
  }

  async read(
    service: string,
    characteristic: string,
    size?: number,
    progress?: (current: number, total: number) => void,
    abortSignal?: AbortSignal
  ): Promise<Uint8Array> {
    this.logger?.info(`[BleManager] execute read(${service}, ${characteristic}, ${size})`)

    service = service.toLowerCase()
    characteristic = characteristic.toLowerCase()

    const transactionId = this.getTransactionId()

    const onAbort = () => {
      this.logger?.info(`[BleManager] canceling read(${service}, ${characteristic}, ${size})`)

      getNativeModule()
        .cancel(transactionId)
        .then(() => {
          this.logger?.info(`Cancelled transaction ${transactionId}`)
        })
        .catch(e => {
          this.logger?.error(`Error cancelling transaction ${transactionId}`, e)
        })
    }

    abortSignal?.addEventListener('abort', onAbort)

    let progressSubscription: EventSubscription | undefined
    if (progress !== undefined) {
      progressSubscription = getNativeModule().addListener('onProgress', data => {
        if (data.transactionId === transactionId) {
          this.logger?.info('[BleManager] read progress', data)

          progress(data.current, data.total)
        }
      })
    }

    let result: string
    try {
      result = await getNativeModule().read(transactionId, service, characteristic, size ?? 0)
    } catch (e: any) {
      throw new BleError(e.code, e.message)
    } finally {
      abortSignal?.removeEventListener('abort', onAbort)
      progressSubscription?.remove()
    }

    return Uint8Array.from(Buffer.from(result, 'base64'))
  }

  async write(
    service: string,
    characteristic: string,
    value: Uint8Array,
    chunkSize = MAX_BLE_CHAR_SIZE,
    progress?: (current: number, total: number) => void,
    abortSignal?: AbortSignal,
    writeType: BleWriteType = 'withResponse'
  ): Promise<void> {
    this.ensureInitialized()

    service = service.toLowerCase()
    characteristic = characteristic.toLowerCase()

    this.logger?.info(
      `[BleManager] execute write(${characteristic}, ${Buffer.from(value.subarray(0, 50)).toString('base64')} (len: ${
        value.length
      }, type: ${writeType}))`
    )

    const transactionId = this.getTransactionId()
    const onAbort = () => {
      this.logger?.info(
        `[BleManager] canceling write(${characteristic}, ${Buffer.from(value.subarray(0, 50)).toString(
          'base64'
        )} (len: ${value.length}))`
      )

      getNativeModule()
        .cancel(transactionId)
        .then(() => {
          this.logger?.info(`Cancelled transaction ${transactionId}`)
        })
        .catch(e => {
          this.logger?.error(`Error cancelling transaction ${transactionId}`, e)
        })
    }

    abortSignal?.addEventListener('abort', onAbort)

    let progressSubscription: EventSubscription | undefined

    if (progress !== undefined) {
      progressSubscription = getNativeModule().addListener('onProgress', data => {
        if (data.transactionId === transactionId) {
          this.logger?.info('[BleManager] write progress', data)

          progress(data.current, data.total)
        }
      })
    }

    try {
      await getNativeModule().write(
        transactionId,
        service,
        characteristic,
        Buffer.from(value).toString('base64'),
        chunkSize,
        writeType
      )
    } catch (e: any) {
      throw new BleError(e.code, e.message)
    } finally {
      abortSignal?.removeEventListener('abort', onAbort)
      progressSubscription?.remove()
    }
  }

  subscribe(
    service: string,
    characteristic: string,
    callback: (value: Uint8Array) => void,
    onError?: (error: BleError) => void
  ): EventSubscription {
    service = service.toLowerCase()
    characteristic = characteristic.toLowerCase()

    this.ensureInitialized()

    const subscription = getNativeModule().addListener('onCharValueChanged', data => {
      if (data.characteristic === characteristic && data.service === service) {
        this.logger?.info('[BleManager] char value changed', data)

        callback(Uint8Array.from(Buffer.from(data.value, 'base64')))
      }
    })

    const key = `${service}:${characteristic}`
    const nSubscriptions = this.nSubscriptions.get(key) ?? 0
    if (nSubscriptions === 0) {
      this.logger?.info(`[BleManager] execute subscribe(${characteristic})`)

      getNativeModule()
        .subscribe(this.getTransactionId(), service, characteristic)
        .then(() => {
          this.logger?.info('[BleManager] subscribed to ', characteristic)
          this.nSubscriptions.set(key, nSubscriptions + 1)
        })
        .catch(e => {
          this.logger?.error('[BleManager] error subscribing to ', characteristic)

          if (onError !== undefined) {
            onError(new BleError(e.code, e.message))
          }
        })
    } else {
      this.nSubscriptions.set(key, nSubscriptions + 1)
    }

    return {
      remove: () => {
        subscription.remove()

        const nSub = this.nSubscriptions.get(key) ?? 0
        if (nSub === 1) {
          this.nSubscriptions.set(key, 0)
          getNativeModule()
            .unsubscribe(this.getTransactionId(), service, characteristic)
            .then(() => {
              this.logger?.info('[BleManager] unsubscribed from ', characteristic)
            })
            .catch(e => {
              this.logger?.error('[BleManager] error unsubscribing from ', characteristic)

              if (onError !== undefined) {
                onError(new BleError(e.code, e.message))
              }
            })
        } else {
          this.nSubscriptions.set(key, nSub - 1)
        }
      },
    }
  }

  getRSSI(): Promise<number> {
    this.logger?.info(`[BleManager] execute readRSSI()`)

    this.ensureInitialized()

    const transactionId = this.getTransactionId()

    try {
      return getNativeModule().readRSSI(transactionId)
    } catch (e: any) {
      throw new BleError(e.code, e.message)
    }
  }

  dispose(): void {
    if (this.initialized) {
      this.logger?.info('[BleManager] terminating manager')
      getNativeModule().disposeModule()
      this.initialized = false
      this.nSubscriptions.clear()
    }
  }
}

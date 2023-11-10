import { NativeEventEmitter, type EmitterSubscription } from 'react-native'
import type { BleErrorCode } from './BleError'
import type { BleDeviceInfo, BleServiceInfo, ConnectionState, ILogger, Subscription } from './types'

export enum BleNativeEvent {
  ERROR = 'ERROR',
  SCAN_RESULT = 'SCAN_RESULT',
  CHAR_VALUE_CHANGED = 'CHAR_VALUE_CHANGED',
  PROGRESS = 'PROGRESS',
  CONNECTION_STATE_CHANGED = 'CONNECTION_STATE_CHANGED',
}

export type BleServicesInfo = {
  services: BleServiceInfo[]
}

export interface IBleNativeEventListener {
  onError(data: { error: BleErrorCode; message: string }): void
  onScanResult(data: { devices: BleDeviceInfo[] }): void
  onCharValueChanged(data: { service: string; characteristic: string; value: string }): void
  onProgress(data: {
    service: string
    characteristic: string
    current: number
    total: number
    transactionId: string
  }): void
  onConnectionStateChanged(data: {
    state: ConnectionState
    error: BleErrorCode | null
    message: string
    android?: { status: number }
    ios?: { code: number; description: string }
    services?: BleServiceInfo[]
  }): void
}

export interface IBleNativeModule {
  initModule(): Promise<void>
  disposeModule(): Promise<void>
  scanStart(serviceUuids?: string[]): Promise<void>
  scanStop(): Promise<void>
  connect(id: string, mtu: number): Promise<BleServicesInfo>
  disconnect(): Promise<void>
  write(transactionId: string, service: string, characteristic: string, value: string, chunkSize: number): Promise<void>
  read(transactionId: string, service: string, characteristic: string, size: number): Promise<string>
  subscribe(transactionId: string, service: string, characteristic: string): Promise<void>
  unsubscribe(transactionId: string, service: string, characteristic: string): Promise<void>
  readRSSI(transactionId: string): Promise<number>
  cancel(transactionId: string): Promise<void>
}

export interface INativeBleInterface extends IBleNativeModule {
  addListener(listener: Partial<IBleNativeEventListener>): Subscription
}

function wrap<T extends Array<unknown>, R>(
  logger: ILogger | undefined,
  name: string,
  fn: (...args: T) => Promise<R>,
  ...args: T
): Promise<R> {
  logger?.info(`[NativeBleInterface] calling native method ${name}(${args})`)

  return fn(...args)
    .then((result) => {
      logger?.debug(`[NativeBleInterface] ${name}(${args}) resolved (result: ${result})`)
      return result
    })
    .catch((error) => {
      logger?.debug(`[NativeBleInterface] ${name}(${args}) rejected (error: ${error})`)
      throw error
    })
}

export class NativeBleInterface implements INativeBleInterface {
  constructor(
    private readonly nativeModule: IBleNativeModule,
    private readonly eventEmitter: NativeEventEmitter,
    private readonly logger?: ILogger
  ) {}

  initModule(): Promise<void> {
    return wrap(this.logger, 'initModule', this.nativeModule.initModule)
  }

  disposeModule(): Promise<void> {
    return wrap(this.logger, 'disposeModule', this.nativeModule.disposeModule)
  }

  scanStart(serviceUuids?: string[] | undefined): Promise<void> {
    return wrap(this.logger, 'scanStart', this.nativeModule.scanStart, serviceUuids)
  }

  scanStop(): Promise<void> {
    return wrap(this.logger, 'scanStop', this.nativeModule.scanStop)
  }

  connect(id: string, mtu: number): Promise<BleServicesInfo> {
    return wrap(this.logger, 'connect', this.nativeModule.connect, id, mtu)
  }

  disconnect(): Promise<void> {
    return wrap(this.logger, 'disconnect', this.nativeModule.disconnect)
  }

  write(
    transactionId: string,
    service: string,
    characteristic: string,
    value: string,
    chunkSize: number
  ): Promise<void> {
    return wrap(this.logger, 'write', this.nativeModule.write, transactionId, service, characteristic, value, chunkSize)
  }

  read(transactionId: string, service: string, characteristic: string, size: number): Promise<string> {
    return wrap(this.logger, 'read', this.nativeModule.read, transactionId, service, characteristic, size)
  }

  subscribe(transactionId: string, service: string, characteristic: string): Promise<void> {
    return wrap(this.logger, 'subscribe', this.nativeModule.subscribe, transactionId, service, characteristic)
  }

  unsubscribe(transactionId: string, service: string, characteristic: string): Promise<void> {
    return wrap(this.logger, 'unsubscribe', this.nativeModule.unsubscribe, transactionId, service, characteristic)
  }

  readRSSI(transactionId: string): Promise<number> {
    return wrap(this.logger, 'readRSSI', this.nativeModule.readRSSI, transactionId)
  }

  cancel(transactionId: string): Promise<void> {
    return wrap(this.logger, 'cancel', this.nativeModule.cancel, transactionId)
  }

  addListener(listener: Partial<IBleNativeEventListener>): Subscription {
    this.logger?.debug('[NativeBleInterface] adding listeners', Object.keys(listener))

    const subscriptions: EmitterSubscription[] = []

    if (listener.onError) {
      subscriptions.push(this.eventEmitter.addListener(BleNativeEvent.ERROR, listener.onError.bind(listener)))
    }
    if (listener.onScanResult) {
      subscriptions.push(
        this.eventEmitter.addListener(BleNativeEvent.SCAN_RESULT, listener.onScanResult.bind(listener))
      )
    }
    if (listener.onCharValueChanged) {
      subscriptions.push(
        this.eventEmitter.addListener(BleNativeEvent.CHAR_VALUE_CHANGED, listener.onCharValueChanged.bind(listener))
      )
    }
    if (listener.onProgress) {
      subscriptions.push(this.eventEmitter.addListener(BleNativeEvent.PROGRESS, listener.onProgress.bind(listener)))
    }
    if (listener.onConnectionStateChanged) {
      subscriptions.push(
        this.eventEmitter.addListener(
          BleNativeEvent.CONNECTION_STATE_CHANGED,
          listener.onConnectionStateChanged.bind(listener)
        )
      )
    }

    return {
      unsubscribe: () => {
        this.logger?.debug('[NativeBleInterface] removing listeners', Object.keys(listener))

        for (const subscription of subscriptions) {
          subscription.remove()
        }
      },
    }
  }
}

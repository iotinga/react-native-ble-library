import { NativeEventEmitter, type EmitterSubscription } from 'react-native'
import {
  BleNativeEvent,
  type BleServicesInfo,
  type IBleNativeEventListener,
  type IBleNativeModule,
  type INativeBleInterface,
} from './interface'
import type { ILogger, Subscription } from './types'

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
      logger?.warn(`[NativeBleInterface] ${name}(${args}) rejected (error: ${error})`)
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

  write(service: string, characteristic: string, value: string, chunkSize: number): Promise<void> {
    return wrap(this.logger, 'write', this.nativeModule.write, service, characteristic, value, chunkSize)
  }

  read(service: string, characteristic: string, size: number): Promise<string> {
    return wrap(this.logger, 'read', this.nativeModule.read, service, characteristic, size)
  }

  subscribe(service: string, characteristic: string): Promise<void> {
    return wrap(this.logger, 'subscribe', this.nativeModule.subscribe, service, characteristic)
  }

  unsubscribe(service: string, characteristic: string): Promise<void> {
    return wrap(this.logger, 'unsubscribe', this.nativeModule.unsubscribe, service, characteristic)
  }

  readRSSI(): Promise<number> {
    return wrap(this.logger, 'readRSSI', this.nativeModule.readRSSI)
  }

  addListener(listener: Partial<IBleNativeEventListener>): Subscription {
    this.logger?.debug('[NativeBleInterface] adding listeners')

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
    if (listener.onWriteProgress) {
      subscriptions.push(
        this.eventEmitter.addListener(BleNativeEvent.WRITE_PROGRESS, listener.onWriteProgress.bind(listener))
      )
    }
    if (listener.onReadProgress) {
      subscriptions.push(
        this.eventEmitter.addListener(BleNativeEvent.READ_PROGRESS, listener.onReadProgress.bind(listener))
      )
    }

    return {
      unsubscribe: () => {
        this.logger?.debug('[NativeBleInterface] removing listeners')

        for (const subscription of subscriptions) {
          subscription.remove()
        }
      },
    }
  }
}

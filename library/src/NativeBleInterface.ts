import { NativeEventEmitter } from 'react-native'
import {
  BleNativeEvent,
  type BleServicesInfo,
  type IBleNativeEventListener,
  type IBleNativeModule,
  type INativeBleInterface,
} from './interface'

function wrap<T extends Array<unknown>, R>(name: string, fn: (...args: T) => Promise<R>, ...args: T): Promise<R> {
  console.info(`[NativeBleInterface] calling native method ${name}(${args})`)

  return fn(...args)
    .then((result) => {
      console.debug(`[NativeBleInterface] ${name}(${args}) resolved (result: ${result})`)
      return result
    })
    .catch((error) => {
      console.warn(`[NativeBleInterface] ${name}(${args}) rejected (error: ${error})`)
      throw error
    })
}

export class NativeBleInterface implements INativeBleInterface {
  constructor(private readonly nativeModule: IBleNativeModule, private readonly eventEmitter: NativeEventEmitter) {}

  initModule(): Promise<void> {
    return wrap('initModule', this.nativeModule.initModule)
  }

  disposeModule(): Promise<void> {
    return wrap('disposeModule', this.nativeModule.disposeModule)
  }

  scanStart(serviceUuids?: string[] | undefined): Promise<void> {
    return wrap('scanStart', this.nativeModule.scanStart, serviceUuids)
  }

  scanStop(): Promise<void> {
    return wrap('scanStop', this.nativeModule.scanStop)
  }

  connect(id: string, mtu: number): Promise<BleServicesInfo> {
    return wrap('connect', this.nativeModule.connect, id, mtu)
  }

  disconnect(): Promise<void> {
    return wrap('disconnect', this.nativeModule.disconnect)
  }

  write(service: string, characteristic: string, value: string, chunkSize: number): Promise<void> {
    return wrap('write', this.nativeModule.write, service, characteristic, value, chunkSize)
  }

  read(service: string, characteristic: string, size: number): Promise<string> {
    return wrap('read', this.nativeModule.read, service, characteristic, size)
  }

  subscribe(service: string, characteristic: string): Promise<void> {
    return wrap('subscribe', this.nativeModule.subscribe, service, characteristic)
  }

  unsubscribe(service: string, characteristic: string): Promise<void> {
    return wrap('unsubscribe', this.nativeModule.unsubscribe, service, characteristic)
  }

  readRSSI(): Promise<number> {
    return wrap('readRSSI', this.nativeModule.readRSSI)
  }

  addListener(listener: IBleNativeEventListener): () => void {
    console.debug('[NativeBleInterface] adding listeners')

    const subscriptions = [
      this.eventEmitter.addListener(BleNativeEvent.INIT_DONE, listener.onInitDone.bind(listener)),
      this.eventEmitter.addListener(BleNativeEvent.ERROR, listener.onError.bind(listener)),
      this.eventEmitter.addListener(BleNativeEvent.SCAN_RESULT, listener.onScanResult.bind(listener)),
      this.eventEmitter.addListener(BleNativeEvent.CHAR_VALUE_CHANGED, listener.onCharValueChanged.bind(listener)),
      this.eventEmitter.addListener(BleNativeEvent.WRITE_PROGRESS, listener.onWriteProgress.bind(listener)),
      this.eventEmitter.addListener(BleNativeEvent.READ_PROGRESS, listener.onReadProgress.bind(listener)),
    ]

    return () => {
      console.debug('[NativeBleInterface] removing listeners')

      for (const subscription of subscriptions) {
        subscription.remove()
      }
    }
  }
}

import { NativeEventEmitter, NativeModules } from 'react-native'
import { BleManager } from './BleManager'
import { NativeBleInterface } from './NativeBleInterface'
import type { IBleManager, IBleManagerFactory, ILogger } from './types'

export class BleManagerFactory implements IBleManagerFactory {
  create(logger?: ILogger): IBleManager {
    const nativeModule = NativeModules.BleLibrary
    if (nativeModule === undefined) {
      throw new Error(`Ble native module not found. Ensure to link the library correctly!`)
    }
    const nativeEventEmitter = new NativeEventEmitter(nativeModule)
    const nativeInterface = new NativeBleInterface(nativeModule, nativeEventEmitter, logger)

    return new BleManager(nativeInterface, logger)
  }
}

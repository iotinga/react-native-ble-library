import { BleManager } from './BleManager'
import { NativeBleInterface } from './NativeBleInterface'
import type { IBleManager, IBleManagerFactory } from './types'
import { NativeEventEmitter, NativeModules } from 'react-native'

export class BleManagerFactory implements IBleManagerFactory {
  create(): IBleManager {
    const nativeModule = NativeModules.BleLibrary
    if (nativeModule === undefined) {
      throw new Error(`Ble native module not found. Ensure to link the library correctly!`)
    }
    const nativeEventEmitter = new NativeEventEmitter(nativeModule)
    const nativeInterface = new NativeBleInterface(nativeModule, nativeEventEmitter)

    return new BleManager(nativeInterface)
  }
}

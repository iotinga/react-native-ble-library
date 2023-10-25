import { NativeEventEmitter, NativeModules } from 'react-native'
import { BleManager } from './BleManager'
import { NativeBleInterface } from './NativeBleInterface'
import { RNPermissionManager } from './RNPermissionManager'
import type { IBleManager, IBleManagerFactory } from './types'

export class BleManagerFactory implements IBleManagerFactory {
  create(): IBleManager {
    const nativeModule = NativeModules.BleLibrary
    if (nativeModule === undefined) {
      throw new Error(`Ble native module not found. Ensure to link the library correctly!`)
    }
    const nativeEventEmitter = new NativeEventEmitter(nativeModule)
    const nativeInterface = new NativeBleInterface(nativeModule, nativeEventEmitter)
    const permissionManager = new RNPermissionManager()

    return new BleManager(nativeInterface, permissionManager)
  }
}

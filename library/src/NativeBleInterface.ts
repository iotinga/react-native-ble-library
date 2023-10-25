import { NativeEventEmitter } from 'react-native'
import type { BleCommand, BleEvent, IBleNativeModule, INativeBleInterface } from './interface'

export class NativeBleInterface implements INativeBleInterface {
  constructor(private readonly nativeModule: IBleNativeModule, private readonly eventEmitter: NativeEventEmitter) {}

  sendCommands(command: BleCommand): Promise<void> {
    return this.nativeModule.sendCommand(command)
  }

  addListener(listener: (event: BleEvent) => void): () => void {
    const subscription = this.eventEmitter.addListener('BleLibraryEvent', listener)

    return () => {
      subscription.remove()
    }
  }
}

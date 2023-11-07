import { BleError } from './BleError'
import {
  ConnectionState,
  type BleConnectedDeviceInfo,
  type BleDeviceInfo,
  type BleManager,
  type DemoState,
  type Subscription,
} from './types'

export class DemoBleManager implements BleManager {
  private values = new Map<string, Buffer>()

  constructor(private demoState: DemoState) {}

  onConnectionStateChanged(callback: (state: ConnectionState, error: BleError | null) => void): Subscription {
    return {
      unsubscribe: () => {},
    }
  }

  get device(): BleConnectedDeviceInfo {
    return {
      id: this.demoState.devices[0]!.id,
      connectionState: ConnectionState.CONNECTED,
      services: this.demoState.services,
    }
  }

  scan(
    serviceUuid: string[] | null | undefined,
    callback: (devices: BleDeviceInfo[]) => void,
    onError?: ((error: BleError) => void) | undefined
  ): Subscription {
    for (const device of this.demoState.devices) {
      callback([device])
    }

    return {
      unsubscribe: () => {},
    }
  }

  async connect(
    id: string,
    mtu?: number | undefined,
    onError?: ((error: BleError) => void) | undefined
  ): Promise<BleConnectedDeviceInfo> {
    return this.device
  }

  async disconnect(): Promise<void> {}

  async read(
    service: string,
    characteristic: string,
    size?: number | undefined,
    progress?: ((current: number, total: number) => void) | undefined
  ): Promise<Buffer> {
    return this.values.get(service + characteristic) ?? Buffer.alloc(0)
  }

  async write(
    service: string,
    characteristic: string,
    value: Buffer,
    chunkSize?: number | undefined,
    progress?: ((current: number, total: number) => void) | undefined
  ): Promise<void> {
    this.values.set(service + characteristic, value)
  }

  subscribe(service: string, characteristic: string, callback: (value: Buffer) => void): Subscription {
    return {
      unsubscribe: () => {},
    }
  }

  dispose(): void {}

  async init(): Promise<void> {}

  async getRSSI(): Promise<number> {
    return -60
  }
}

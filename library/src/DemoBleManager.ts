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
  private connectionStateChangeSubscriptions = new Set<(state: ConnectionState, error: BleError | null) => void>()

  constructor(private demoState: DemoState) {}

  onConnectionStateChanged(callback: (state: ConnectionState, error: BleError | null) => void): Subscription {
    this.connectionStateChangeSubscriptions.add(callback)

    return {
      unsubscribe: () => {
        this.connectionStateChangeSubscriptions.delete(callback)
      },
    }
  }

  get device(): BleConnectedDeviceInfo {
    return {
      id: this.demoState.devices[0]!.id,
      connectionState: ConnectionState.CONNECTED,
      services: this.demoState.services,
    }
  }

  scan(serviceUuid: string[] | null | undefined, callback: (devices: BleDeviceInfo[]) => void): Subscription {
    let i = 0
    const interval = setInterval(() => {
      callback([this.demoState.devices[i]!])

      i = (i + 1) % this.demoState.devices.length
    }, 500)

    return {
      unsubscribe: () => {
        clearInterval(interval)
      },
    }
  }

  async connect(id: string, mtu?: number | undefined): Promise<void> {
    setTimeout(() => {
      for (const subscription of this.connectionStateChangeSubscriptions) {
        subscription(ConnectionState.CONNECTED, null)
      }
    }, 2000)
  }

  async disconnect(): Promise<void> {}

  async read(service: string, characteristic: string): Promise<Buffer> {
    return this.values.get(service + characteristic) ?? Buffer.alloc(0)
  }

  async write(service: string, characteristic: string, value: Buffer): Promise<void> {
    this.values.set(service + characteristic, value)
  }

  subscribe(): Subscription {
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

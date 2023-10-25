import {
  BleCharacteristicState,
  BleConnectionState,
  BleScanState,
  type BleCharacteristic,
  type BleManagerState,
  type IBleChar,
  type IBleManager,
  type DemoState,
} from './types'

export class DemoBleManager implements IBleManager {
  private readonly callbacks = new Set<(state: BleManagerState) => void>()
  private state: BleManagerState

  constructor(demoState: DemoState) {
    this.state = {
      ready: true,
      permission: {
        granted: true,
      },
      connection: {
        state: BleConnectionState.Disconnected,
        id: '',
        rssi: -1,
      },
      scan: {
        state: BleScanState.Stopped,
        serviceUuids: [],
        discoveredDevices: demoState.devices,
      },
      services: demoState.services,
    }
  }

  private setState(state: Partial<BleManagerState>): void {
    this.state = {
      ...this.state,
      ...state,
    }
    this.notify()
  }

  private setChar(service: string, char: string, charState: Partial<BleCharacteristic>): void {
    this.setState({
      services: {
        ...this.state.services,
        [service]: {
          ...this.state.services[service],
          [char]: {
            ...this.state.services[service]?.[char]!,
            ...charState,
          },
        },
      },
    })
  }

  async askPermissions(): Promise<boolean> {
    return true
  }

  private notify(): void {
    for (const callback of this.callbacks) {
      callback(this.state)
    }
  }

  onStateChange(callback: (state: BleManagerState) => void): () => void {
    this.callbacks.add(callback)
    this.notify()

    return () => {
      this.callbacks.delete(callback)
    }
  }

  getState(): BleManagerState {
    return this.state
  }

  async scan(serviceUuids?: string[]): Promise<void> {}

  async stopScan(): Promise<void> {}

  async connect(id: string, mtu?: number): Promise<void> {
    this.setState({
      connection: {
        ...this.state.connection,
        state: BleConnectionState.Connected,
        id,
      },
    })
  }

  async disconnect(): Promise<void> {
    this.setState({
      connection: {
        ...this.state.connection,
        state: BleConnectionState.Disconnected,
      },
    })
  }

  async read(characteristics: IBleChar | IBleChar[]): Promise<void> {}

  async write(characteristic: IBleChar, value: Buffer): Promise<void> {
    this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
      state: BleCharacteristicState.Ready,
      writeProgress: {
        current: 0,
        total: value.length,
      },
      value: value,
    })
  }

  async subscribe(characteristic: IBleChar): Promise<void> {}

  async unsubscribe(characteristic: IBleChar): Promise<void> {}

  dispose(): void {}
}

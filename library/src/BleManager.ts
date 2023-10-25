import { PromiseSequencer } from './PromiseSequencer'
import { BLE_PERMISSIONS } from './constants'
import type { BleEvent, INativeBleInterface } from './interface'
import {
  BleCharacteristicState,
  BleConnectionState,
  BleScanState,
  type BleCharacteristic,
  type BleManagerState,
  type IBleChar,
  type IBleManager,
  type IPermissionManager,
} from './types'

export class BleManager implements IBleManager {
  private readonly callbacks = new Set<(state: BleManagerState) => void>()
  private readonly listenerSubscription: () => void
  private state: BleManagerState = {
    ready: false,
    permission: {
      granted: null,
    },
    connection: {
      state: BleConnectionState.Disconnected,
      id: '',
      rssi: -1,
    },
    scan: {
      state: BleScanState.Stopped,
      serviceUuids: [],
      discoveredDevices: [],
    },
    services: {},
  }
  private readonly sequencer = new PromiseSequencer()

  constructor(
    private readonly nativeInterface: INativeBleInterface,
    private readonly permissionManager: IPermissionManager<string>
  ) {
    this.listenerSubscription = this.nativeInterface.addListener(this.onEvent.bind(this))
    this.checkPermissions()
    this.ping()
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

  private async checkPermissions() {
    this.setState({
      permission: {
        granted: await this.permissionManager.hasPermission(BLE_PERMISSIONS),
      },
    })
  }

  async askPermissions(): Promise<boolean> {
    this.setState({
      permission: {
        granted: await this.permissionManager.askPermission(BLE_PERMISSIONS),
      },
    })

    return this.state.permission.granted === true
  }

  private ping(): void {
    this.nativeInterface.sendCommands([{ type: 'ping' }])
  }

  // processes an event form the native driver and updates the state of the manager
  // accordingly. Notice that the state is an immutable object, otherwise the mechanism
  // of React will not work.
  private onEvent(event: BleEvent): void {
    switch (event.type) {
      case 'pong':
        this.setState({
          ready: true,
        })
        break
      case 'disconnected':
        this.setState({
          connection: {
            ...this.state.connection,
            state: BleConnectionState.Disconnected,
          },
        })
        break
      case 'scanStarted':
        this.setState({
          scan: {
            ...this.state.scan,
            state: BleScanState.Scanning,
            discoveredDevices: [],
          },
        })
        break
      case 'scanStopped':
        this.setState({
          scan: {
            ...this.state.scan,
            state: BleScanState.Stopped,
          },
        })
        break
      case 'scanResult':
        let devices = this.state.scan.discoveredDevices
        for (const device of event.devices) {
          if (devices.find((d) => d.id === device.id) === undefined) {
            devices = [...devices, device]
          }
        }
        if (devices !== this.state.scan.discoveredDevices) {
          this.setState({
            scan: {
              ...this.state.scan,
              discoveredDevices: devices,
            },
          })
        }
        break
      case 'writeProgress':
        this.setChar(event.service, event.characteristic, {
          writeProgress: {
            current: event.current,
            total: event.total,
          },
        })
        break
      case 'readProgress':
        this.setChar(event.service, event.characteristic, {
          readProgress: {
            current: event.current,
            total: event.total,
          },
        })
        break
      case 'connected':
        this.setState({
          connection: {
            ...this.state.connection,
            state: BleConnectionState.Connected,
          },
          services: {},
        })
        break
      case 'charValueChanged':
        this.setChar(event.service, event.characteristic, {
          state: BleCharacteristicState.Ready,
          value: Buffer.from(event.value, 'base64'),
        })
        break
      case 'subscribed':
        this.setChar(event.service, event.characteristic, {
          subscribed: true,
        })
        break
      case 'unsubscribed':
        this.setChar(event.service, event.characteristic, {
          subscribed: false,
        })
        break
      case 'writeCompleted':
        this.setChar(event.service, event.characteristic, {
          state: BleCharacteristicState.Ready,
          writeProgress: undefined,
        })
        break
      case 'readCompleted':
        this.setChar(event.service, event.characteristic, {
          state: BleCharacteristicState.Ready,
          readProgress: undefined,
          value: Buffer.from(event.value, 'base64'),
        })
        break
      case 'error':
        this.setState({
          error: {
            code: event.error,
            message: event.message,
          },
        })
        break
    }
  }

  private notify(): void {
    for (const callback of this.callbacks) {
      callback(this.state)
    }
  }

  private waitEvent(...events: BleEvent['type'][]): Promise<BleEvent> {
    return new Promise<BleEvent>((resolve) => {
      const unsubscribe = this.nativeInterface.addListener((event) => {
        if (events.includes(event.type)) {
          unsubscribe()
          resolve(event)
        }
      })
    })
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

  async scan(serviceUuids?: string[]): Promise<void> {
    if (this.state.scan.state !== BleScanState.Scanning && this.state.scan.state !== BleScanState.Starting) {
      this.setState({
        scan: {
          ...this.state.scan,
          state: BleScanState.Starting,
          serviceUuids,
        },
      })
      await this.nativeInterface.sendCommands([{ type: 'scan', serviceUuids }])
      await this.waitEvent('scanStarted', 'error')
    }
  }

  async stopScan(): Promise<void> {
    if (this.state.scan.state !== BleScanState.Stopped && this.state.scan.state !== BleScanState.Stopping) {
      this.setState({
        scan: {
          ...this.state.scan,
          state: BleScanState.Stopping,
        },
      })
      await this.nativeInterface.sendCommands([{ type: 'stopScan' }])
      await this.waitEvent('scanStopped', 'error')
    }
  }

  connect(id: string, mtu?: number): Promise<void> {
    return this.sequencer.execute(async () => {
      if (
        this.state.connection.state !== BleConnectionState.Connected &&
        this.state.connection.state !== BleConnectionState.Connecting
      ) {
        this.setState({
          connection: {
            ...this.state.connection,
            state: BleConnectionState.Connecting,
            id,
          },
        })
        await this.nativeInterface.sendCommands([{ type: 'connect', id, mtu }])
        await this.waitEvent('connected', 'error')
      }
    })
  }

  disconnect(): Promise<void> {
    return this.sequencer.execute(async () => {
      if (
        this.state.connection.state !== BleConnectionState.Disconnected &&
        this.state.connection.state !== BleConnectionState.Disconnecting
      ) {
        this.setState({
          connection: {
            ...this.state.connection,
            state: BleConnectionState.Disconnecting,
          },
        })
        await this.nativeInterface.sendCommands([{ type: 'disconnect' }])
        await this.waitEvent('disconnected', 'error')
      }
    })
  }

  read(characteristic: IBleChar): Promise<void> {
    return this.sequencer.execute(async () => {
      if (this.state.connection.state === BleConnectionState.Connected) {
        this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
          state: BleCharacteristicState.Reading,
        })

        await this.nativeInterface.sendCommands([
          {
            type: 'read',
            service: characteristic.getServiceUuid(),
            characteristic: characteristic.getCharUuid(),
            size: characteristic.getSize(),
          },
        ])
        await this.waitEvent('readCompleted', 'error')
      }
    })
  }

  write(characteristic: IBleChar, value: Buffer): Promise<void> {
    return this.sequencer.execute(async () => {
      if (this.state.connection.state === BleConnectionState.Connected) {
        this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
          state: BleCharacteristicState.Writing,
          writeProgress: {
            current: 0,
            total: value.length,
          },
          value: value,
        })

        await this.nativeInterface.sendCommands([
          {
            type: 'write',
            service: characteristic.getServiceUuid(),
            characteristic: characteristic.getCharUuid(),
            value: value.toString('base64'),
            maxSize: characteristic.getChunkSize(),
          },
        ])
        await this.waitEvent('writeCompleted', 'error')
      }
    })
  }

  subscribe(characteristic: IBleChar): Promise<void> {
    return this.sequencer.execute(async () => {
      if (this.state.connection.state === BleConnectionState.Connected) {
        await this.nativeInterface.sendCommands([
          { type: 'subscribe', service: characteristic.getServiceUuid(), characteristic: characteristic.getCharUuid() },
        ])
        await this.waitEvent('subscribed', 'error')
      }
    })
  }

  unsubscribe(characteristic: IBleChar): Promise<void> {
    return this.sequencer.execute(async () => {
      if (this.state.connection.state === BleConnectionState.Connected) {
        await this.nativeInterface.sendCommands([
          {
            type: 'unsubscribe',
            service: characteristic.getServiceUuid(),
            characteristic: characteristic.getCharUuid(),
          },
        ])
        await this.waitEvent('unsubscribed', 'error')
      }
    })
  }

  dispose(): void {
    this.listenerSubscription()
  }
}

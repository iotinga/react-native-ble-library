import { PromiseSequencer } from './PromiseSequencer'
import { BLE_PERMISSIONS } from './constants'
import type { BleEvent, INativeBleInterface } from './interface'
import {
  BleConnectionState,
  BleScanState,
  type BleCharacteristic,
  type BleManagerState,
  type IBleChar,
  type IBleManager,
  type IPermissionManager,
  BleErrorCode,
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
    this.nativeInterface.sendCommands({ type: 'ping' })
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
      case 'charValueChanged':
        this.setChar(event.service, event.characteristic, {
          value: Buffer.from(event.value, 'base64'),
        })
        break
      case 'error':
        switch (event.error) {
          case BleErrorCode.BleScanError:
            this.setState({
              scan: {
                ...this.state.scan,
                state: BleScanState.Stopped,
              },
              error: {
                code: event.error,
                message: event.message,
              },
            })
            break
          case BleErrorCode.DeviceDisconnected:
            this.setState({
              connection: {
                ...this.state.connection,
                state: BleConnectionState.Disconnected,
              },
              error: {
                code: event.error,
                message: event.message,
              },
            })
            break
          case BleErrorCode.GenericError:
            this.setState({
              error: {
                code: event.error,
                message: event.message,
              },
            })
            break
        }
        break
    }
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

  async scan(serviceUuids?: string[]): Promise<void> {
    if (this.state.scan.state !== BleScanState.Scanning && this.state.scan.state !== BleScanState.Starting) {
      this.setState({
        scan: {
          ...this.state.scan,
          state: BleScanState.Starting,
          serviceUuids,
        },
      })
      await this.nativeInterface.sendCommands({ type: 'scan', serviceUuids })
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
      await this.nativeInterface.sendCommands({ type: 'stopScan' })
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
        await this.nativeInterface.sendCommands({ type: 'connect', id, mtu })
        this.setState({
          connection: {
            ...this.state.connection,
            state: BleConnectionState.Connected,
          },
        })
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
        await this.nativeInterface.sendCommands({ type: 'disconnect' })
        this.setState({
          connection: {
            ...this.state.connection,
            state: BleConnectionState.Disconnected,
          },
        })
      }
    })
  }

  read(characteristic: IBleChar): Promise<void> {
    return this.sequencer.execute(async () => {
      if (this.state.connection.state === BleConnectionState.Connected) {
        await this.nativeInterface.sendCommands({
          type: 'read',
          service: characteristic.getServiceUuid(),
          characteristic: characteristic.getCharUuid(),
          size: characteristic.getSize(),
        })
      }
    })
  }

  write(characteristic: IBleChar, value: Buffer): Promise<void> {
    return this.sequencer.execute(async () => {
      if (this.state.connection.state === BleConnectionState.Connected) {
        this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
          writeProgress: {
            current: 0,
            total: value.length,
          },
          value: value,
        })

        await this.nativeInterface.sendCommands({
          type: 'write',
          service: characteristic.getServiceUuid(),
          characteristic: characteristic.getCharUuid(),
          value: value.toString('base64'),
          chunkSize: characteristic.getChunkSize(),
        })
      }
    })
  }

  subscribe(characteristic: IBleChar): Promise<void> {
    return this.sequencer.execute(async () => {
      if (this.state.connection.state === BleConnectionState.Connected) {
        await this.nativeInterface.sendCommands({
          type: 'subscribe',
          service: characteristic.getServiceUuid(),
          characteristic: characteristic.getCharUuid(),
        })
        this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
          subscribed: true,
        })
      }
    })
  }

  unsubscribe(characteristic: IBleChar): Promise<void> {
    return this.sequencer.execute(async () => {
      if (this.state.connection.state === BleConnectionState.Connected) {
        await this.nativeInterface.sendCommands({
          type: 'unsubscribe',
          service: characteristic.getServiceUuid(),
          characteristic: characteristic.getCharUuid(),
        })
        this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
          subscribed: false,
        })
      }
    })
  }

  dispose(): void {
    this.listenerSubscription()
  }
}

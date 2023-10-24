import type { BleEvent, CharacteristicState, IBleManager, INativeBleInterface, BleManagerState } from './types'

export class BleManager implements IBleManager {
  private readonly callbacks = new Set<(state: BleManagerState) => void>()
  private state: BleManagerState = {
    ready: false,
    permission: {
      granted: null,
    },
    connection: {
      state: 'disconnected',
      id: '',
      rssi: -1,
      services: {},
    },
    scan: {
      state: 'stopped',
      serviceUuids: [],
      discoveredDevices: [],
    },
  }

  constructor(private readonly nativeInterface: INativeBleInterface) {
    this.nativeInterface.addListener(this.onEvent.bind(this))
    this.ping()
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
        this.state = {
          ...this.state,
          ready: true,
        }
        break
      case 'disconnected':
        this.state = {
          ...this.state,
          connection: { ...this.state.connection, state: 'disconnected' },
        }
        break
      case 'scanStarted':
        this.state = {
          ...this.state,
          scan: { ...this.state.scan, state: 'scanning' },
        }
        break
      case 'scanStopped':
        this.state = {
          ...this.state,
          scan: { ...this.state.scan, state: 'stopped' },
        }
        break
      case 'scanResult':
        if (this.state.scan.discoveredDevices.find((d) => d.id === event.device.id) === undefined) {
          this.state = {
            ...this.state,
            scan: {
              ...this.state.scan,
              discoveredDevices: [...this.state.scan.discoveredDevices, event.device],
            },
          }
        }
        break
      case 'writeProgress':
        this.state = {
          ...this.state,
          connection: {
            ...this.state.connection,
            services: {
              ...this.state.connection.services,
              [event.service]: {
                ...this.state.connection.services[event.service],
                [event.characteristic]: {
                  ...this.state.connection.services[event.service]?.[event.characteristic]!,
                  writeProgress: {
                    current: event.current,
                    total: event.total,
                  },
                },
              },
            },
          },
        }
        break
      case 'connected':
        this.state = {
          ...this.state,
          connection: {
            ...this.state.connection,
            state: 'connected',
          },
        }
        break
      case 'charValueChanged':
        this.state = {
          ...this.state,
          connection: {
            ...this.state.connection,
            services: {
              ...this.state.connection.services,
              [event.service]: {
                ...this.state.connection.services[event.service],
                [event.characteristic]: {
                  ...this.state.connection.services[event.service]?.[event.characteristic]!,
                  value: event.value,
                },
              },
            },
          },
        }
        break
      case 'subscribe':
        this.state = {
          ...this.state,
          connection: {
            ...this.state.connection,
            services: {
              ...this.state.connection.services,
              [event.service]: {
                ...this.state.connection.services[event.service],
                [event.characteristic]: {
                  ...this.state.connection.services[event.service]?.[event.characteristic]!,
                  state: 'subscribed',
                },
              },
            },
          },
        }
        break
      case 'writeCompleted':
        this.state = {
          ...this.state,
          connection: {
            ...this.state.connection,
            services: {
              ...this.state.connection.services,
              [event.service]: {
                ...this.state.connection.services[event.service],
                [event.characteristic]: {
                  ...this.state.connection.services[event.service]?.[event.characteristic]!,
                  state: 'none',
                },
              },
            },
          },
        }
        break
      case 'error':
        this.state = {
          ...this.state,
          error: {
            code: event.error,
            message: event.message,
          },
        }
        break
    }

    this.notify()
  }

  private notify(): void {
    for (const callback of this.callbacks) {
      callback(this.state)
    }
  }

  private getChar(service: string, characteristic: string): CharacteristicState {
    if (!this.state.connection.services[service]) {
      this.state.connection.services[service] = {}
    }

    if (!this.state.connection.services[service]![characteristic]) {
      this.state.connection.services[service]![characteristic] = {
        state: 'none',
      }
    }

    return this.state.connection.services[service]![characteristic]!
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

  scan(serviceUuid: string[]): void {
    this.state.scan.discoveredDevices = []
    this.state.scan.serviceUuids = serviceUuid
    this.notify()
    this.nativeInterface.sendCommands([{ type: 'scan', serviceUuids: serviceUuid }])
  }

  stopScan(): void {
    this.state.scan.state = 'stopping'
    this.notify()
    this.nativeInterface.sendCommands([{ type: 'stopScan' }])
  }

  connect(id: string): void {
    this.state.connection.state = 'connecting'
    this.state.connection.id = id
    this.notify()
    this.nativeInterface.sendCommands([{ type: 'connect', id }])
  }

  disconnect(): void {
    this.state.connection.state = 'disconnecting'
    this.notify()
    this.nativeInterface.sendCommands([{ type: 'disconnect' }])
  }

  read(service: string, characteristic: string): void {
    this.getChar(service, characteristic).state = 'reading'
    this.notify()
    this.nativeInterface.sendCommands([{ type: 'read', service, characteristic }])
  }

  write(service: string, characteristic: string, value: Buffer): void {
    this.getChar(service, characteristic).state = 'writing'
    this.getChar(service, characteristic).writeProgress = {
      current: 0,
      total: value.length,
    }
    this.getChar(service, characteristic).value = value
    this.notify()
    this.nativeInterface.sendCommands([{ type: 'write', service, characteristic, value }])
  }

  subscribe(service: string, characteristic: string): void {
    this.getChar(service, characteristic).state = 'subscribing'
    this.nativeInterface.sendCommands([{ type: 'subscribe', service, characteristic }])
  }
}

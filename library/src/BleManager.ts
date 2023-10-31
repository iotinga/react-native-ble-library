import { PromiseSequencer } from './PromiseSequencer'
import type { IBleNativeEventListener, INativeBleInterface } from './interface'
import {
  BleConnectionState,
  BleErrorCode,
  BleScanState,
  type BleCharacteristic,
  type BleDeviceInfo,
  type BleManagerState,
  type IBleChar,
  type IBleManager,
} from './types'

// as required by the standard
const MAX_BLE_CHAR_SIZE = 512

export class BleManager implements IBleManager, IBleNativeEventListener {
  private readonly callbacks = new Set<(state: BleManagerState) => void>()
  private readonly removeAllListeners: () => void
  private state: BleManagerState = {
    ready: null,
    enabled: null,
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

  constructor(private readonly nativeInterface: INativeBleInterface) {
    this.removeAllListeners = this.nativeInterface.addListener(this)
    this.init()
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

  async init(): Promise<void> {
    if (!this.state.ready) {
      console.info('[BleManager] initializing module...')
      try {
        await this.nativeInterface.initModule()
        console.log('[BleManager] module initialized')
        this.setState({
          ready: true,
          enabled: true,
          permission: {
            granted: true,
          },
        })
      } catch (e: any) {
        console.error('[BleManager] failed to initialize module', e)
        if (e.code === BleErrorCode.BleNotEnabledError) {
          console.error('[BleManager] bluetooth not enabled')
          this.setState({
            ready: false,
            enabled: false,
          })
        } else if (e.code === BleErrorCode.MissingPermissionError) {
          console.error('[BleManager] missing BLE permissions')
          this.setState({
            ready: false,
            permission: {
              granted: false,
            },
          })
        } else {
          console.error('[BleManager] unknown init error')
          this.setState({
            ready: false,
          })
        }
      }
    }
  }

  onInitDone(): void {
    console.info('[BleManager] pong')
    this.setState({
      ready: true,
    })
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
    console.info('[BleManager] starting scan...')
    if (this.state.scan.state !== BleScanState.Scanning && this.state.scan.state !== BleScanState.Starting) {
      this.setState({
        scan: {
          ...this.state.scan,
          state: BleScanState.Starting,
          serviceUuids,
        },
      })
      await this.nativeInterface.scanStart(serviceUuids?.map((s) => s.toLowerCase()))
    }
  }

  onScanResult(data: { devices: BleDeviceInfo[] }): void {
    console.info('[BleManager] got scan result', data)
    let devices = this.state.scan.discoveredDevices
    for (const device of data.devices) {
      if (devices.find((d) => d.id === device.id) === undefined) {
        devices = [...devices, device]
      } else if (!device.available) {
        devices = devices.filter((d) => d.id !== device.id)
      } else {
        devices = devices.map((d) => (d.id === device.id ? device : d))
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
  }

  async stopScan(): Promise<void> {
    console.info('[BleManager] stopping scan...')
    if (this.state.scan.state !== BleScanState.Stopped && this.state.scan.state !== BleScanState.Stopping) {
      this.setState({
        scan: {
          ...this.state.scan,
          state: BleScanState.Stopping,
        },
      })
      await this.nativeInterface.scanStop()
    } else {
      console.warn('[BleManager] scan already stopped')
    }
  }

  connect(id: string, mtu?: number): Promise<void> {
    console.info(`[BleManager] enqueue connect(${id}, ${mtu})`)
    return this.sequencer.execute(async () => {
      console.info(`[BleManager] execute connect(${id}, ${mtu})`)

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
        try {
          await this.nativeInterface.connect(id, mtu ?? 0)
          this.setState({
            connection: {
              ...this.state.connection,
              state: BleConnectionState.Connected,
            },
          })
        } catch (e) {
          this.setState({
            connection: {
              ...this.state.connection,
              state: BleConnectionState.Disconnected,
            },
          })
          throw e
        }
      } else {
        console.warn('[BleManager] already connected')
      }
    })
  }

  disconnect(): Promise<void> {
    console.info('[BleManager] enqueue disconnect()')
    return this.sequencer.execute(async () => {
      console.info('[BleManager] execute disconnect()')

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
        try {
          await this.nativeInterface.disconnect()
          this.setState({
            connection: {
              ...this.state.connection,
              state: BleConnectionState.Disconnected,
            },
          })
        } catch (e) {
          this.setState({
            connection: {
              ...this.state.connection,
              state: BleConnectionState.Unknown,
            },
          })
          throw e
        }
      } else {
        console.warn('[BleManager] already disconnected')
      }
    })
  }

  read(characteristic: IBleChar): Promise<void> {
    console.info(`[BleManager] enqueue read(${characteristic})`)
    return this.sequencer.execute(async () => {
      console.info(`[BleManager] execute read(${characteristic})`)
      if (this.state.connection.state === BleConnectionState.Connected) {
        const result = await this.nativeInterface.read(
          characteristic.getServiceUuid(),
          characteristic.getCharUuid(),
          characteristic.getSize() ?? 0
        )
        this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
          value: Buffer.from(result, 'base64'),
        })
      }
    })
  }

  onReadProgress(data: { service: string; characteristic: string; current: number; total: number }): void {
    console.info('[BleManager] read progress', data)
    this.setChar(data.service, data.characteristic, {
      readProgress: {
        current: data.current,
        total: data.total,
      },
    })
  }

  write(characteristic: IBleChar, value: Buffer): Promise<void> {
    console.info(
      `[BleManager] enqueue write(${characteristic}, ${value.subarray(0, 50).toString('base64')} (len: ${
        value.length
      }))`
    )
    return this.sequencer.execute(async () => {
      console.info(
        `[BleManager] execute write(${characteristic}, ${value.subarray(0, 50).toString('base64')} (len: ${
          value.length
        })`
      )
      if (this.state.connection.state === BleConnectionState.Connected) {
        this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
          writeProgress: {
            current: 0,
            total: value.length,
          },
          value: value,
        })

        await this.nativeInterface.write(
          characteristic.getServiceUuid(),
          characteristic.getCharUuid(),
          value.toString('base64'),
          characteristic.getChunkSize() ?? MAX_BLE_CHAR_SIZE
        )
      }
    })
  }

  onWriteProgress(data: { service: string; characteristic: string; current: number; total: number }): void {
    console.info('[BleManager] write progress', data)
    this.setChar(data.service, data.characteristic, {
      writeProgress: {
        current: data.current,
        total: data.total,
      },
    })
  }

  subscribe(characteristic: IBleChar, callback?: (value: Buffer) => boolean): Promise<void> {
    console.info(`[BleManager] enqueue subscribe(${characteristic})`)
    return this.sequencer.execute(async () => {
      console.info(`[BleManager] execute subscribe(${characteristic})`)
      if (this.state.connection.state === BleConnectionState.Connected) {
        await this.nativeInterface.subscribe(characteristic.getServiceUuid(), characteristic.getCharUuid())
        this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
          subscribed: true,
          callback,
        })
      }
    })
  }

  unsubscribe(characteristic: IBleChar): Promise<void> {
    console.info(`[BleManager] enqueue unsubscribe(${characteristic})`)
    return this.sequencer.execute(async () => {
      console.info(`[BleManager] execute unsubscribe(${characteristic})`)
      if (this.state.connection.state === BleConnectionState.Connected) {
        await this.nativeInterface.unsubscribe(characteristic.getServiceUuid(), characteristic.getCharUuid())
        this.setChar(characteristic.getServiceUuid(), characteristic.getCharUuid(), {
          subscribed: false,
        })
      }
    })
  }

  onCharValueChanged(data: { service: string; characteristic: string; value: string }): void {
    const callback = this.state.services[data.service]?.[data.characteristic]?.callback
    const value = Buffer.from(data.value, 'base64')
    if (callback === undefined || callback(value)) {
      console.info(
        `[BleManager] char value changed: ${value.subarray(0, 50).toString('base64')} (len: ${value.length})`
      )
      this.setChar(data.service, data.characteristic, {
        value,
      })
    }
  }

  onError(data: { error: BleErrorCode; message: string }): void {
    console.info('[BleManager] error from native code', data)
    switch (data.error) {
      case BleErrorCode.BleScanError:
        this.setState({
          scan: {
            ...this.state.scan,
            state: BleScanState.Stopped,
          },
          error: {
            code: data.error,
            message: data.message,
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
            code: data.error,
            message: data.message,
          },
        })
        break
      case BleErrorCode.GenericError:
        this.setState({
          error: {
            code: data.error,
            message: data.message,
          },
        })
        break
    }
  }

  getRSSI(): Promise<number> {
    console.info(`[BleManager] enqueue readRSSI()`)
    return this.sequencer.execute(async () => {
      console.info(`[BleManager] execute readRSSI()`)

      if (this.state.connection.state !== BleConnectionState.Connected) {
        throw new Error('not connected')
      }

      return this.nativeInterface.readRSSI()
    })
  }

  dispose(): void {
    console.info('[BleManager] terminating manager')
    this.removeAllListeners()
    this.nativeInterface.disposeModule()
  }
}

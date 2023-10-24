export enum BleErrorCode {
  GenericError = 'BleGenericError',
  DeviceDisconnected = 'BleDeviceDisconnected',
  InvalidState = 'BleInvalidState',
}

export type BleError = {
  error: BleErrorCode
  message: string
}

export type BleDeviceInfo = {
  /** ID of the device. On Android this is a MAC address, on iOS it's an opaque UUID */
  id: string

  /** Name of the device, as seen from the library */
  name: string

  /** Signal strength of the discovered device */
  rssi: number
}

export interface IBleCharacteristic {
  readonly uuid: string

  read(): Promise<Buffer>
  write(value: Buffer): Promise<void>
  subscribe(onChange: (data: Buffer) => void): Promise<{ unsubscribe: () => Promise<void> }>
}

export interface IBleService {
  readonly uuid: string

  getCharacteristic(uuid: string): IBleCharacteristic | null
}

export interface IBleDevice {
  readonly id: string

  getService(uuid: string): IBleService | null
  disconnect(): Promise<void>
}

export type CharacteristicState = {
  state: 'none' | 'reading' | 'writing' | 'ready' | 'subscribed' | 'subscribing'
  value?: Buffer
  writeProgress?: {
    current: number
    total: number
  }
}

export type BleManagerState = {
  ready: boolean
  permission: {
    granted: boolean | null
  }
  scan: {
    state: 'starting' | 'scanning' | 'stopping' | 'stopped'
    serviceUuids: string[]
    discoveredDevices: BleDeviceInfo[]
  }
  connection: {
    state: 'disconnected' | 'connecting' | 'connected' | 'disconnecting'
    id: string
    rssi: number
    services: Record<string, Record<string, CharacteristicState>>
  }
  error?: BleError
}

export interface IBleManager {
  onStateChange(callback: (state: BleManagerState) => void): () => void
  getState(): BleManagerState

  scan(serviceUuid: string[]): void
  stopScan(): void

  connect(id: string): void
  disconnect(): void
  read(service: string, characteristic: string): void
  write(service: string, characteristic: string, value: Buffer): void
  subscribe(service: string, characteristic: string): void
}

export type BleCommand =
  | { type: 'ping' }
  | { type: 'scan'; serviceUuid: string[] }
  | { type: 'stopScan' }
  | { type: 'connect'; id: string }
  | { type: 'disconnect' }
  | { type: 'write'; service: string; characteristic: string; value: Buffer }
  | { type: 'read'; service: string; characteristic: string }
  | { type: 'subscribe'; service: string; characteristic: string }

export type BleEvent =
  | { type: 'pong' }
  | { type: 'error'; error: BleError }
  | { type: 'scanResult'; device: BleDeviceInfo }
  | { type: 'scanStopped' }
  | { type: 'scanStarted' }
  | { type: 'connected'; id: string; rssi: number }
  | { type: 'disconnected' }
  | { type: 'subscribe'; service: string; characteristic: string }
  | { type: 'charValueChanged'; service: string; characteristic: string; value: Buffer }
  | { type: 'writeCompleted'; service: string; characteristic: string }
  | { type: 'writeProgress'; service: string; characteristic: string; current: number; total: number }

export interface INativeBleInterface {
  sendCommands(commands: BleCommand[]): Promise<void>
  addListener(listener: (event: BleEvent) => void): () => void
}

export interface IBleManagerFactory {
  create(): IBleManager
}

export interface IBleNativeModule {
  sendCommands(commands: BleCommand[]): Promise<void>
}

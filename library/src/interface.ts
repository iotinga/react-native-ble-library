import type { BleDeviceInfo, BleErrorCode } from './types'

export type BleCommand =
  | { type: 'ping' }
  | { type: 'scan'; serviceUuids?: string[] }
  | { type: 'stopScan' }
  | { type: 'connect'; id: string; mtu?: number }
  | { type: 'disconnect' }
  | { type: 'write'; service: string; characteristic: string; value: string; chunkSize?: number }
  | { type: 'read'; service: string; characteristic: string; size?: number }
  | { type: 'subscribe'; service: string; characteristic: string }
  | { type: 'unsubscribe'; service: string; characteristic: string }

export type BleEvent =
  | { type: 'pong' }
  | { type: 'error'; error: BleErrorCode; message: string }
  | { type: 'scanResult'; devices: BleDeviceInfo[] }
  | { type: 'charValueChanged'; service: string; characteristic: string; value: string }
  | { type: 'writeProgress'; service: string; characteristic: string; current: number; total: number }
  | { type: 'readProgress'; service: string; characteristic: string; current: number; total: number }

export interface INativeBleInterface {
  sendCommands(command: BleCommand): Promise<void>
  addListener(listener: (event: BleEvent) => void): () => void
}

export interface IBleNativeModule {
  sendCommand(commands: BleCommand): Promise<void>
}

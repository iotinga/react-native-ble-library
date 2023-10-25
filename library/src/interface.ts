import type { BleDeviceInfo, BleErrorCode } from './types'

export type BleCommand =
  | { type: 'ping' }
  | { type: 'scan'; serviceUuids?: string[] }
  | { type: 'stopScan' }
  | { type: 'connect'; id: string; mtu?: number }
  | { type: 'disconnect' }
  | { type: 'write'; service: string; characteristic: string; value: string; maxSize?: number }
  | { type: 'read'; service: string; characteristic: string; size?: number }
  | { type: 'subscribe'; service: string; characteristic: string }
  | { type: 'unsubscribe'; service: string; characteristic: string }

export type BleEvent =
  | { type: 'pong' }
  | { type: 'error'; error: BleErrorCode; message: string }
  | { type: 'scanResult'; devices: BleDeviceInfo[] }
  | { type: 'scanStopped' }
  | { type: 'scanStarted' }
  | { type: 'connected'; id: string; rssi: number }
  | { type: 'disconnected' }
  | { type: 'subscribed'; service: string; characteristic: string }
  | { type: 'unsubscribed'; service: string; characteristic: string }
  | { type: 'charValueChanged'; service: string; characteristic: string; value: string }
  | { type: 'writeCompleted'; service: string; characteristic: string }
  | { type: 'writeProgress'; service: string; characteristic: string; current: number; total: number }
  | { type: 'readProgress'; service: string; characteristic: string; current: number; total: number }
  | { type: 'readCompleted'; service: string; characteristic: string; value: string }

export interface INativeBleInterface {
  sendCommands(commands: BleCommand[]): Promise<void>
  addListener(listener: (event: BleEvent) => void): () => void
}

export interface IBleNativeModule {
  sendCommands(commands: BleCommand[]): Promise<void>
}

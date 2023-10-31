import type { BleDeviceInfo, BleErrorCode } from './types'

export enum BleNativeEvent {
  INIT_DONE = 'initDone',
  ERROR = 'error',
  SCAN_RESULT = 'scanResult',
  CHAR_VALUE_CHANGED = 'charValueChanged',
  READ_PROGRESS = 'readProgress',
  WRITE_PROGRESS = 'writeProgress',
}

export interface IBleNativeEventListener {
  onInitDone(): void
  onError(data: { error: BleErrorCode; message: string }): void
  onScanResult(data: { devices: BleDeviceInfo[] }): void
  onCharValueChanged(data: { service: string; characteristic: string; value: string }): void
  onWriteProgress(data: { service: string; characteristic: string; current: number; total: number }): void
  onReadProgress(data: { service: string; characteristic: string; current: number; total: number }): void
}

export type BleCharacteristicInfo = {
  uuid: string
  properties: number
}

export type BleServiceInfo = {
  uuid: string
  isPrimary: number
  characteristics: BleCharacteristicInfo[]
}

export type BleServicesInfo = {
  services: BleServiceInfo[]
}

export interface IBleNativeModule {
  initModule(): Promise<void>
  disposeModule(): Promise<void>
  scanStart(serviceUuids?: string[]): Promise<void>
  scanStop(): Promise<void>
  connect(id: string, mtu: number): Promise<BleServicesInfo>
  disconnect(): Promise<void>
  write(service: string, characteristic: string, value: string, chunkSize: number): Promise<void>
  read(service: string, characteristic: string, size: number): Promise<string>
  subscribe(service: string, characteristic: string): Promise<void>
  unsubscribe(service: string, characteristic: string): Promise<void>
  readRSSI(): Promise<number>
}

export interface INativeBleInterface extends IBleNativeModule {
  addListener(listener: IBleNativeEventListener): () => void
}

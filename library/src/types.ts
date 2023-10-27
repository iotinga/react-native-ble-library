export enum BleErrorCode {
  GenericError = 'BleGenericError',
  DeviceDisconnected = 'BleDeviceDisconnected',
  BleScanError = 'BleScanError',
}

export type BleDeviceInfo = {
  /** ID of the device. On Android this is a MAC address, on iOS it's an opaque UUID */
  id: string

  /** Name of the device, as seen from the library */
  name: string

  /** Signal strength of the discovered device */
  rssi: number

  available: boolean
}

export type BleProgressIndication = {
  current: number
  total: number
}

export type BleCharacteristic = {
  subscribed: boolean
  value?: Buffer
  writeProgress?: BleProgressIndication
  readProgress?: BleProgressIndication
}

export enum BleScanState {
  Stopped = 'stopped',
  Scanning = 'scanning',
  Starting = 'starting',
  Stopping = 'stopping',
}

export enum BleConnectionState {
  Disconnected = 'disconnected',
  Connecting = 'connecting',
  Connected = 'connected',
  Disconnecting = 'disconnecting',
  Unknown = 'unknown',
}

export type BleManagerState = {
  /** true if the module is ready and connected with the native driver */
  ready: boolean
  permission: {
    granted: boolean | null
  }
  scan: {
    state: BleScanState
    serviceUuids?: string[]
    discoveredDevices: BleDeviceInfo[]
  }
  connection: {
    state: BleConnectionState
    id: string
    rssi: number
  }
  error?: {
    code: BleErrorCode
    message: string
  }
  services: Record<string, Record<string, BleCharacteristic>>
}

export type IBleChar = {
  getServiceUuid(): string
  getCharUuid(): string
  getChunkSize(): number | undefined
  getSize(): number | undefined
}

export interface IBleManager {
  /**
   * If the BLE permissions are not already granted asks the user for them,
   * depending on the user platform.
   */
  askPermissions(): Promise<boolean>

  /**
   * Registers a callback that is invoked each time the BleManager state changes.
   * Typically only used internally by hooks.
   */
  onStateChange(callback: (state: BleManagerState) => void): () => void

  /**
   * Get the current BleManager state.
   */
  getState(): BleManagerState

  /**
   * Start a BLE scan for the devices that expose the specified services.
   */
  scan(serviceUuid?: string[]): Promise<void>

  /**
   * Stops the scan, if previously started.
   */
  stopScan(): Promise<void>

  /**
   * Connects to the device with the specified id (that is returned by the scan).
   * If mtu is specified the manager tries to request the specified MTU for the
   * device (if allowed by the operating system), otherwise the default (that
   * depends on the OS implementation) is used.
   */
  connect(id: string, mtu?: number): Promise<void>

  /**
   * Disconnects a previously connected device.
   */
  disconnect(): Promise<void>

  /**
   * Request a read for the specified characteristics. If the characteristic has a
   * fixed size then multiple reads are performed till all the data associated with
   * the characteristic is received.
   */
  read(characteristic: IBleChar): Promise<void>

  /**
   * Requests a write for the specified characteristic. If the characteristic is chunked the
   * write is split in chunks of chunkSize. The characteristic is then written multiple times till
   * all the message is transmitted.
   * From one write to another we wait for the device to send an ACK to confirm it has
   * received the message chunk.
   */
  write(characteristic: IBleChar, value: Buffer): Promise<void>

  /**
   * Subscribes for notification of the specified (service, characteristic). Each time the
   * characteristic changes the state is automatically updated accordingly.
   */
  subscribe(characteristic: IBleChar): Promise<void>

  /**
   * Removes a previously set subscription for (service, characteristic)
   */
  unsubscribe(characteristic: IBleChar): Promise<void>

  /**
   * Call this method after having used the BleManager to release all the resources
   */
  dispose(): void
}

export type DemoState = {
  services: Record<string, Record<string, BleCharacteristic>>
  devices: BleDeviceInfo[]
}

export interface IBleManagerFactory {
  create(): IBleManager
}

export interface IPermissionManager<T> {
  askPermission(permission: T | T[]): Promise<boolean>
  hasPermission(permission: T | T[]): Promise<boolean>
}

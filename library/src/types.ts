import type { BleError } from './BleError'

export type BleDeviceInfo = {
  /** ID of the device. On Android this is a MAC address, on iOS it's an opaque UUID */
  id: string

  /** Name of the device, as seen from the library */
  name: string

  /** Signal strength of the discovered device */
  rssi: number

  /** true if the device is available, false if device is no longer available (Android only) */
  isAvailable: boolean

  /** true if the device is connectable */
  isConnectable: boolean

  /** tx power of the device */
  txPower: number
}

export enum ConnectionState {
  CONNECTING_TO_DEVICE = 'CONNECTING_TO_DEVICE',
  REQUESTING_MTU = 'REQUESTING_MTU',
  DISCOVERING_SERVICES = 'DISCOVERING_SERVICES',
  CONNECTED = 'CONNECTED',
  DISCONNECTING = 'DISCONNECTING',
  DISCONNECTED = 'DISCONNECTED',
}

export type Subscription = {
  /** removes the subscription */
  unsubscribe: () => void
}

export const BleCharacteristicProperty = {
  READ: 0x02,
  WRITE_WITHOUT_RESPONSE: 0x04,
  WRITE: 0x08,
  NOTIFY: 0x10,
  INDICATE: 0x20,
} as const

export type BleCharacteristicInfo = {
  /** uuid of the characteristic */
  uuid: string

  /** bitmask of {@link BleCharacteristicProperty} */
  properties: number
}

export type BleServiceInfo = {
  /** uuid of the service */
  uuid: string

  /** true if the service is a primary service */
  isPrimary: boolean

  /** list of the characteristics included in this service */
  characteristics: BleCharacteristicInfo[]
}

export type BleConnectedDeviceInfo = {
  /** ID of the device */
  id: string

  /** connection state */
  connectionState: ConnectionState

  /** list of services exposed from the device */
  services?: BleServiceInfo[]
}

export interface BleManager {
  /**
   * Initializes the BLE stack:
   * - checks if the BLE is supported by the device
   * - checks if the user has the required permissions, asking to grant them if necessary
   * - checks if Bluetooth is enabled (on Android asks for activation if disabled)
   * - allocates all the necessary resources needed for the scan
   *
   * @throws {BleError} in case of an error
   */
  init(): Promise<void>

  /**
   * Start a BLE scan for the devices that expose the specified services.
   *
   * @param serviceUuids the service UUID to scan for
   * @param onDiscover callback invoked each time a new devices are discovered
   * @param onError a callback that gets invoked when a scan error occurs
   * @returns a subscription for the scan operation
   * @throws {BleError} in case of an error
   */
  scan(
    serviceUuid: string[],
    onDiscover: (devices: BleDeviceInfo[]) => void,
    onError?: (error: BleError) => void
  ): Subscription

  /**
   * Connects to the device with the specified id (that is returned by the scan).
   * If mtu is specified the manager tries to request the specified MTU for the
   * device (if allowed by the operating system), otherwise the default (that
   * depends on the OS implementation) is used.
   *
   * This function succeeds when it configured the BLE interface to connect to the device.
   * You must then listen for the {@link BleManager.onConnectionStateChanged} event to know
   * if the device connected. The module will try to connect to the device till either {@link BleManager.disconnect}
   * or {@link BleManager.dispose} is called.
   *
   * @param id id of the device to connect to. Should correspond to one device found in a previous scan!
   * @param mtu the MTU to set to the device when connecting. This is only relevant on Android,
   *  since on iOS the MTU is negotiated automatically. If not specified uses the default from the BLE driver.
   * @throws {BleError} in case of an error
   */
  connect(id: string, mtu?: number): Promise<void>

  /**
   * Set a callback to be invoked each time the device connection information changes
   *
   * @param callback callback to invoke
   * @returns a subscription for the event
   */
  onConnectionStateChanged(callback: (state: ConnectionState, error: BleError | null) => void): Subscription

  /**
   * @return the connected device info if any, otherwise null
   */
  get device(): BleConnectedDeviceInfo | null

  /**
   * Disconnects a previously connected device, if any.
   * Cancels any pending operation on the device immediately.
   *
   * @throws {BleError} in case of an error
   */
  disconnect(): Promise<void>

  /**
   * Request a read for the specified characteristics. If the characteristic has a
   * fixed size then multiple reads are performed till all the data associated with
   * the characteristic is received.
   *
   * @param service UUID of the service to read
   * @param characteristic UUID of the characteristic to read
   * @param size if specified read repeatedly till size bytes are received
   * @param progress callback to report the read progress to the application
   * @param abortSignal param that allows to cancel a long async read
   * @returns a promise that resolves with the read value
   * @throws {BleError} in case of an error
   */
  read(
    service: string,
    characteristic: string,
    size?: number,
    progress?: (current: number, total: number) => void,
    abortSignal?: AbortSignal
  ): Promise<Uint8Array>

  /**
   * Requests a write for the specified characteristic. If the characteristic is chunked the
   * write is split in chunks of chunkSize. The characteristic is then written multiple times till
   * all the message is transmitted.
   * From one write to another we wait for the device to send an ACK to confirm it has
   * received the message chunk.
   *
   * @param service UUID of the service to write
   * @param characteristic UUID of the characteristic to write
   * @param value the value to write
   * @param chunkSize if specified write at most chunkSize bytes at a time. If length of the data is
   *  greater than chunk size it will perform multiple writes till all the data is written. Default: 512 bytes
   * @param progress callback to report the write progress to the application
   * @param abortSignal param that allows to cancel a long async write
   * @throws {BleError} in case of an error
   */
  write(
    service: string,
    characteristic: string,
    value: Uint8Array,
    chunkSize?: number,
    progress?: (current: number, total: number) => void,
    abortSignal?: AbortSignal
  ): Promise<void>

  /**
   * get the connected device RSSI
   *
   * @returns the RSSI value for the device
   * @throws {BleError} in case of an error
   */
  getRSSI(): Promise<number>

  /**
   * Subscribes for notification of the specified (service, characteristic). Each time the
   * characteristic changes the state is automatically updated accordingly.
   *
   * @param service UUID of the service to write
   * @param characteristic UUID of the characteristic to write
   * @param onValueChanged callback invoked each time the characteristic changes.
   * @param onError callback invoked when an error occurs
   * @returns a subscription for the notification
   * @throws {BleError} in case of an error
   */
  subscribe(
    service: string,
    characteristic: string,
    onValueChanged: (value: Uint8Array) => void,
    onError?: (error: BleError) => void
  ): Subscription

  /**
   * Call this method after having used the BleManager to release all the resources.
   * Eventual connected devices will be disconnected and existing scan stopped.
   * After this call do not attempt to use the BleManager instance without calling init() again!
   */
  dispose(): void
}

export type DemoState = {
  /** services exposed by the device */
  services: BleServiceInfo[]

  /** devices returned in fake discovery */
  devices: BleDeviceInfo[]
}

export interface ILogger {
  debug(message: string, ...args: unknown[]): void
  info(message: string, ...args: unknown[]): void
  warn(message: string, ...args: unknown[]): void
  error(message: string, ...args: unknown[]): void
}

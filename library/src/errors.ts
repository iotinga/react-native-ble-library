export enum BleErrorCode {
  BleGenericError = 'BleGenericError',
  BleNotInitializedError = 'BleNotInitializedError',
  BleBleNotSupportedError = 'BleNotSupportedError',
  BleMissingPermissionError = 'BleMissingPermissionError',
  BleBleNotEnabledError = 'BleNotEnabledError',
  BleDeviceDisconnectedError = 'BleDeviceDisconnected',
  BleInvalidStateError = 'BleInvalidState',
  BleGATTError = 'BleGATTError',
  BleConnectionError = 'BleConnectionError',
  BleNotConnectedError = 'BleNotConnectedError',
  BleModuleBusyError = 'BleModuleBusyError',
  BleInvalidArgumentsError = 'ErrorInvalidArguments',
  BleCharacteristicNotFoundError = 'BleCharacteristicNotFoundError',
  BleServiceNotFoundError = 'BleServiceNotFoundError',
  BleScanError = 'BleScanError',
  BleAlreadyConnectedError = 'BleAlreadyConnectedError',
  BleDeviceNotFoundError = 'BleDeviceNotFoundError',
  BleOperationNotAllowed = 'BleOperationNotAllowed',
}

export class BleError extends Error {
  constructor(public readonly code: string, message = code) {
    super(message)
    this.name = 'BleError'
  }
}

export enum BleErrorCode {
  BleGenericError = 'BleGenericError',
  BleNotInitializedError = 'BleNotInitializedError',
  BleNotSupportedError = 'BleNotSupportedError',
  BleMissingPermissionError = 'BleMissingPermissionError',
  BleNotEnabledError = 'BleNotEnabledError',
  BleDeviceDisconnectedError = 'BleDeviceDisconnectedError',
  BleInvalidStateError = 'BleInvalidStateError',
  BleGATTError = 'BleGATTError',
  BleConnectionError = 'BleConnectionError',
  BleNotConnectedError = 'BleNotConnectedError',
  BleModuleBusyError = 'BleModuleBusyError',
  BleInvalidArgumentsError = 'BleInvalidArgumentsError',
  BleCharacteristicNotFoundError = 'BleCharacteristicNotFoundError',
  BleServiceNotFoundError = 'BleServiceNotFoundError',
  BleScanError = 'BleScanError',
  BleAlreadyConnectedError = 'BleAlreadyConnectedError',
  BleDeviceNotFoundError = 'BleDeviceNotFoundError',
  BleOperationNotAllowed = 'BleOperationNotAllowedError',
  BleOperationCanceled = 'BleOperationCanceledError',
}

export class BleError extends Error {
  constructor(public readonly code: BleErrorCode, message: string = code) {
    super(message)
    this.name = 'BleError'
  }
}

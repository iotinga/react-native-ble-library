import { BleError, BleErrorCode } from './BleError'

export function isBleError(error: unknown): error is BleError {
  return error instanceof BleError
}

export function isBleErrorCode(error: unknown, errorCode: BleErrorCode): boolean {
  return isBleError(error) && error.code === errorCode
}

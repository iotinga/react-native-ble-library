import { BleError } from '../lib/typescript/src'
import type { BleErrorCode } from './BleError'

export function isBleError(error: any): error is BleError {
  return error instanceof BleError
}

export function isBleErrorCode(error: any, errorCode: BleErrorCode): boolean {
  return isBleError(error) && error.code === errorCode
}

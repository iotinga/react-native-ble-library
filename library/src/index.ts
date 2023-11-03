global.Buffer = global.Buffer || require('buffer').Buffer

export { NativeBleManager } from './NativeBleManager'
export { DemoBleManager } from './DemoBleManager'
export { BleError, BleErrorCode } from './BleError'
export { CancelationToken } from './CancelationToken'
export * from './BleError'
export * from './types'

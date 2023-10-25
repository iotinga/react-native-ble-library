import type { IBleChar } from './types'

export class BleChar implements IBleChar {
  private readonly serviceUuid: string
  private readonly charUuid: string

  constructor(
    serviceUuid: string,
    charUuid: string,
    private readonly size?: number,
    private readonly chunkSize?: number
  ) {
    this.serviceUuid = serviceUuid.toLowerCase()
    this.charUuid = charUuid.toLowerCase()
  }

  getServiceUuid(): string {
    return this.serviceUuid
  }

  getCharUuid(): string {
    return this.charUuid
  }

  getChunkSize(): number | undefined {
    return this.chunkSize
  }

  getSize(): number | undefined {
    return this.size
  }

  toString(): string {
    return `Characteristic(${this.serviceUuid}, ${this.charUuid})`
  }
}

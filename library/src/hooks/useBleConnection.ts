import type { BleConnectionState } from '../types'
import { useBleManager } from './useBleManager'

export function useBleConnection(): [
  BleConnectionState,
  { connect: (id: string, mtu?: number) => Promise<void>; disconnect: () => Promise<void> }
] {
  const [state, manager] = useBleManager()

  const connect = (id: string, mtu?: number) => {
    return manager.connect(id, mtu)
  }

  const disconnect = () => {
    return manager.disconnect()
  }

  return [state.connection.state, { connect, disconnect }]
}

import type { BleConnectionState } from '../types'
import { useBleManager } from './useBleManager'

export function useBleConnection(): [
  BleConnectionState,
  { connect: (id: string, mtu?: number) => void; disconnect: () => void }
] {
  const [state, manager] = useBleManager()

  const connect = (id: string, mtu?: number) => {
    manager.connect(id, mtu)
  }

  const disconnect = () => {
    manager.disconnect()
  }

  return [state.connection.state, { connect, disconnect }]
}

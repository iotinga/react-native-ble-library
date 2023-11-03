import { useContext } from 'react'
import { BleManagerContext } from '../contexts/BleManagerContext'
import type { BleManager } from '@iotinga/react-native-ble-library'

export function useBleManager(): BleManager {
  const manager = useContext(BleManagerContext)
  if (manager === null) {
    throw new Error('useBleManager must be used within a BleManagerProvider')
  }

  return manager
}

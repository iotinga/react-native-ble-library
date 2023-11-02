import { useContext } from 'react'
import { BleManagerContext } from '../contexts/BleManagerContext'
import type { IBleManager } from '../types'

export function useBleManager(): IBleManager {
  const manager = useContext(BleManagerContext)
  if (manager === null) {
    throw new Error('useBleManager must be used within a BleManagerProvider')
  }

  return manager
}

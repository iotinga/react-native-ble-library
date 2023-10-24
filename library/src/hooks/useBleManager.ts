import { useContext, useEffect, useState } from 'react'
import type { BleManagerState, IBleManager } from '../types'
import { BleManagerContext } from '../contexts/BleManagerContext'

export function useBleManager(): [BleManagerState, IBleManager] {
  const bleManager = useContext(BleManagerContext)
  if (bleManager === null) {
    throw new Error('useBleManager must be used within a BleManagerProvider')
  }
  const [state, setState] = useState<BleManagerState>(bleManager.getState())

  useEffect(() => {
    return bleManager.onStateChange(setState)
  }, [bleManager])

  return [state, bleManager]
}

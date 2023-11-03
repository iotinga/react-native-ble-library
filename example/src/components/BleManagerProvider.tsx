import { NativeBleManager } from '@iotinga/react-native-ble-library'
import React, { useMemo } from 'react'
import { BleManagerContext } from '../contexts/BleManagerContext'

export function BleManagerProvider({ children }: { children: React.ReactNode }) {
  const manager = useMemo(() => new NativeBleManager(console), [])

  return <BleManagerContext.Provider value={manager}>{children}</BleManagerContext.Provider>
}

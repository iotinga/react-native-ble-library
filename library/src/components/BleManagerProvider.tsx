import React from 'react'
import { BleManagerContext } from '../contexts/BleManagerContext'
import type { IBleManager } from '../types'

export function BleManagerProvider({ children, manager }: { children: React.ReactNode; manager: IBleManager }) {
  return <BleManagerContext.Provider value={manager}>{children}</BleManagerContext.Provider>
}

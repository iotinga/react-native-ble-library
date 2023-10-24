import { createContext } from 'react'
import type { IBleManager } from '../types'

export const BleManagerContext = createContext<IBleManager | null>(null)

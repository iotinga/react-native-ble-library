import type { BleManager } from '@iotinga/react-native-ble-library'
import { createContext } from 'react'

export const BleManagerContext = createContext<BleManager | null>(null)

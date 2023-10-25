import { useEffect } from 'react'
import type { BleDeviceInfo } from '../types'
import { useBleManager } from './useBleManager'

export function useBleScan(serviceUuids?: string[]): BleDeviceInfo[] {
  const [state, manager] = useBleManager()

  useEffect(() => {
    manager.scan(serviceUuids)

    return () => {
      manager.stopScan()
    }
  }, [serviceUuids, manager])

  return state.scan.discoveredDevices
}

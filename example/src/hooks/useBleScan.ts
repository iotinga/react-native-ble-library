import { useEffect, useState } from 'react'
import type { BleDeviceInfo } from '@iotinga/react-native-ble-library'
import { useBleManager } from './useBleManager'
import { BleError } from '@iotinga/react-native-ble-library'

export function useBleScan(serviceUuids?: string[]): [BleDeviceInfo[], BleError | null] {
  const [devices, setDevices] = useState<BleDeviceInfo[]>([])
  const [error, setError] = useState<BleError | null>(null)

  const manager = useBleManager()

  useEffect(() => {
    const onResults = (results: BleDeviceInfo[]) => {
      setDevices((oldDevices) => {
        const currentDevices = new Map(oldDevices.map((device) => [device.id, device]))

        for (const newDevice of results) {
          if (newDevice.available) {
            currentDevices.set(newDevice.id, newDevice)
          } else {
            currentDevices.delete(newDevice.id)
          }
        }

        return Array.from(currentDevices.values())
      })
    }

    const onError = (error: BleError) => {
      setError(error)
    }

    const subscription = manager.scan(serviceUuids, onResults, onError)

    return () => {
      subscription.unsubscribe()
    }
  }, [serviceUuids, manager])

  return [devices, error]
}

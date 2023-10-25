import { useEffect } from 'react'
import { BleCharacteristicState, type IBleChar } from '../types'
import { useBleManager } from './useBleManager'

export function useBleCharacteristic(
  characteristic: IBleChar,
  subscribe = false
): [Buffer | undefined, (value: Buffer) => void] {
  const [state, manager] = useBleManager()

  const char = state.services[characteristic.getServiceUuid()]?.[characteristic.getCharUuid()]

  useEffect(() => {
    if (char?.state === undefined || char?.state === BleCharacteristicState.None) {
      manager.read(characteristic)
    }
  }, [characteristic, manager, char?.state])

  useEffect(() => {
    if (subscribe) {
      manager.subscribe(characteristic)

      return () => {
        manager.unsubscribe(characteristic)
      }
    }

    return undefined
  }, [manager, subscribe, characteristic])

  const setValue = (value: Buffer) => {
    manager.write(characteristic, value)
  }

  return [char?.value, setValue]
}

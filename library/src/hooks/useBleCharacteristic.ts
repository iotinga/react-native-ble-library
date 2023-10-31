import { useEffect } from 'react'
import { type IBleChar } from '../types'
import { useBleManager } from './useBleManager'

export function useBleCharacteristic(
  characteristic: IBleChar,
  subscribe = false
): [Buffer | undefined, (value: Buffer) => void] {
  const [state, manager] = useBleManager()

  const char = state.services[characteristic.getServiceUuid()]?.[characteristic.getCharUuid()]

  useEffect(() => {
    if (char?.value === undefined) {
      manager.read(characteristic)
    }
  }, [characteristic, manager, char?.value])

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
    return manager.write(characteristic, value)
  }

  return [char?.value, setValue]
}

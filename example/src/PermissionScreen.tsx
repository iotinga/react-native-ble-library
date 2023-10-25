import { useBlePermissions } from '@iotinga/react-native-ble-library'
import React, { useEffect } from 'react'
import { SafeAreaView, Text } from 'react-native'

export function PermissionScreen() {
  const [granted, request] = useBlePermissions()

  useEffect(() => {
    if (!granted) {
      request()
    }
  }, [granted])

  return (
    <SafeAreaView>
      <Text>{granted ? 'BLE permission granted' : 'No BLE permission'}</Text>
    </SafeAreaView>
  )
}

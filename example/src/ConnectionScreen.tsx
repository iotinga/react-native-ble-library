import { useBleConnection } from '@iotinga/react-native-ble-library'
import React, { useEffect } from 'react'
import { SafeAreaView, Text } from 'react-native'


export function ConnectionScreen({ id }: { id: string }) {
  const [connectionState, { connect }] = useBleConnection()

  useEffect(() => {
    connect(id, 250)
  }, [])

  return (
    <SafeAreaView>
      <Text>Connection state: {connectionState}</Text>
    </SafeAreaView>
  )
}

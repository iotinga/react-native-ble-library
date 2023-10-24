import React, { useMemo } from 'react'
import { Button, SafeAreaView, Text } from 'react-native'

import { BleManagerFactory, BleManagerProvider, useBleManager } from '@iotinga/react-native-ble-library'

function ExampleComponent() {
  const [bleState, bleManager] = useBleManager()
  console.log(bleState)

  return (
    <SafeAreaView>
      <Text>READY: {bleState.ready.toString()}</Text>
      <Text>SCAN: {bleState.scan.state}</Text>
      {bleState.scan.state === 'scanning' &&
        bleState.scan.discoveredDevices.map((device) => <Text key={device.id}>{device.name}</Text>)}
      <Button onPress={() => bleManager.scan([])} title="SCAN" />
      <Text>CONNECT: {bleState.connection.state}</Text>
    </SafeAreaView>
  )
}

export default function App() {
  const bleManager = useMemo(() => new BleManagerFactory().create(), [])

  return (
    <BleManagerProvider manager={bleManager}>
      <ExampleComponent />
    </BleManagerProvider>
  )
}

import {
  BleConnectionState,
  BleManagerFactory,
  BleManagerProvider,
  useBleConnection,
} from '@iotinga/react-native-ble-library'
import React, { useMemo, useState } from 'react'
import { SafeAreaView } from 'react-native'
import { ConnectionScreen } from './ConnectionScreen'
import { ScanScreen } from './ScanScreen'
import { ViewScreen } from './ViewScreen'

function ExampleComponent() {
  const [connectionState] = useBleConnection()
  const [deviceId, setDeviceId] = useState<string>()

  if (deviceId === undefined) {
    return <ScanScreen onSelectDevice={setDeviceId} />
  }
  if (connectionState !== BleConnectionState.Connected) {
    return <ConnectionScreen id={deviceId} />
  }
  return <ViewScreen />
}

export default function App() {
  const bleManager = useMemo(() => new BleManagerFactory().create(), [])

  return (
    <BleManagerProvider manager={bleManager}>
      <SafeAreaView style={{ backgroundColor: '#ffffff', flex: 1 }}>
        <ExampleComponent />
      </SafeAreaView>
    </BleManagerProvider>
  )
}

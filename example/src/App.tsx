import {
  BleConnectionState,
  BleManagerFactory,
  BleManagerProvider,
  useBleConnection,
  useBlePermissions,
} from '@iotinga/react-native-ble-library'
import React, { useMemo, useState } from 'react'
import { ConnectionScreen } from './ConnectionScreen'
import { PermissionScreen } from './PermissionScreen'
import { ScanScreen } from './ScanScreen'
import { ViewScreen } from './ViewScreen'

function ExampleComponent() {
  const [permissionGranted] = useBlePermissions()
  const [connectionState] = useBleConnection()
  const [deviceId, setDeviceId] = useState<string>()

  if (!permissionGranted) {
    return <PermissionScreen />
  }
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
      <ExampleComponent />
    </BleManagerProvider>
  )
}

import { createStackNavigator } from '@react-navigation/stack'
import { ConnectScreen } from './ConnectScreen'
import { ScanScreen } from './ScanScreen'
import { InitScreen } from './InitScreen'
import { DeviceInfoScreen } from './DeviceInfoScreen'

export type RootStackParamList = {
  Init: undefined
  Scan: undefined
  Connect: {
    id: string
  }
  DeviceInfo: undefined
}

const Stack = createStackNavigator<RootStackParamList>()

export function Navigation() {
  return (
    <Stack.Navigator>
      <Stack.Screen name="Init" component={InitScreen} />
      <Stack.Screen name="Scan" component={ScanScreen} />
      <Stack.Screen name="Connect" component={ConnectScreen} />
      <Stack.Screen name="DeviceInfo" component={DeviceInfoScreen} />
    </Stack.Navigator>
  )
}

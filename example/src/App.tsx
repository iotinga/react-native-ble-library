import { BleManagerFactory, BleManagerProvider } from '@iotinga/react-native-ble-library'
import { NavigationContainer } from '@react-navigation/native'
import { StyleSheet } from 'react-native'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import { Navigation } from './Navigation'
import { useMemo } from 'react'

export default function App() {
  const factory = useMemo(() => new BleManagerFactory(), [])

  return (
    <SafeAreaProvider>
      <NavigationContainer>
        <BleManagerProvider factory={factory}>
          <Navigation />
        </BleManagerProvider>
      </NavigationContainer>
    </SafeAreaProvider>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
})

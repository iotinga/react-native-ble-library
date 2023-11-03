import { NavigationContainer } from '@react-navigation/native'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import { Navigation } from './Navigation'
import { BleManagerProvider } from './components/BleManagerProvider'

export default function App() {
  return (
    <SafeAreaProvider>
      <NavigationContainer>
        <BleManagerProvider>
          <Navigation />
        </BleManagerProvider>
      </NavigationContainer>
    </SafeAreaProvider>
  )
}

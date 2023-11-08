import { ConnectionState } from '@iotinga/react-native-ble-library'
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native'
import { StackNavigationProp } from '@react-navigation/stack'
import { useEffect } from 'react'
import { Alert, Text } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { RootStackParamList } from './Navigation'
import { useBleManager } from './hooks/useBleManager'

export function ConnectScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>()
  const route = useRoute<RouteProp<RootStackParamList, 'Connect'>>()
  const manager = useBleManager()

  useEffect(() => {
    const timeout = setTimeout(() => {
      Alert.alert('Error', 'Timeout connecting to BLE device', [
        {
          text: 'Cancel',
          style: 'cancel',
          onPress: () => navigation.goBack(),
        },
        {
          text: 'Keep waiting',
          style: 'default',
          onPress: () => {},
        },
      ])
    }, 5000)

    const subscription = manager.onConnectionStateChanged((state, error) => {
      if (state === ConnectionState.CONNECTED) {
        clearTimeout(timeout)
        navigation.replace('DeviceInfo')
      }

      if (state !== ConnectionState.CONNECTING_TO_DEVICE && error !== null) {
        clearTimeout(timeout)
        Alert.alert('Error', 'Error connecting BLE device: ' + error.message, [
          {
            text: 'Ok',
            style: 'default',
            onPress: () => navigation.goBack(),
          },
        ])
      }
    })

    manager.connect(route.params.id, undefined).catch(e => {
      Alert.alert('Error', 'Error connecting BLE device: ' + e.message, [
        {
          text: 'Ok',
          style: 'default',
          onPress: () => navigation.goBack(),
        },
      ])
    })

    return () => {
      clearTimeout(timeout)
      subscription.unsubscribe()
    }
  })

  return (
    <SafeAreaView edges={['bottom']}>
      <Text style={{ textAlign: 'center', fontSize: 18, margin: 15 }}>Connecting to {route.params.id}...</Text>
    </SafeAreaView>
  )
}

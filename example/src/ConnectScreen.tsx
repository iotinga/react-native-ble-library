import { BleError, useBleManager } from '@iotinga/react-native-ble-library'
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native'
import { StackNavigationProp } from '@react-navigation/stack'
import { useEffect } from 'react'
import { Alert, Text } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { RootStackParamList } from './Navigation'

export function ConnectScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>()
  const route = useRoute<RouteProp<RootStackParamList, 'Connect'>>()
  const manager = useBleManager()

  useEffect(() => {
    const onError = (error: BleError) => {
      Alert.alert('Error', 'Connection to device lost!', [
        {
          text: 'Ok',
          style: 'default',
          onPress: () =>
            navigation.reset({
              index: 0,
              routes: [{ name: 'Scan' }],
            }),
        },
      ])
    }

    manager
      .disconnect()
      .then(() => manager.connect(route.params.id, undefined, onError))
      .then(() => {
        navigation.replace('DeviceInfo')
      })
      .catch(e => {
        Alert.alert('Error', 'Error connecting BLE device: ' + e.message, [
          {
            text: 'Ok',
            style: 'default',
            onPress: () => navigation.goBack(),
          },
        ])
      })
  })

  return (
    <SafeAreaView edges={['bottom']}>
      <Text style={{ textAlign: 'center', fontSize: 18, margin: 15 }}>Connecting to {route.params.id}...</Text>
    </SafeAreaView>
  )
}

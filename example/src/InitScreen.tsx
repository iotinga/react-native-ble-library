import { BluetoothNotEnabledError, InsufficientPermissionError, useBleManager } from '@iotinga/react-native-ble-library'
import { useFocusEffect, useNavigation } from '@react-navigation/native'
import { StackNavigationProp } from '@react-navigation/stack'
import { useCallback } from 'react'
import { Alert, Linking, Text } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { RootStackParamList } from './Navigation'

export function InitScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>()
  const manager = useBleManager()

  useFocusEffect(
    useCallback(() => {
      manager
        .init()
        .then(() => {
          navigation.replace('Scan')
        })
        .catch(e => {
          if (e instanceof BluetoothNotEnabledError) {
            Alert.alert('Error', 'Bluetooth is not enabled. Open the settings to enable it', [
              {
                text: 'Cancel',
                style: 'cancel',
              },
              {
                text: 'Open Settings',
                onPress: () => Linking.openSettings(),
                isPreferred: true,
                style: 'default',
              },
            ])
          } else if (e instanceof InsufficientPermissionError) {
            Alert.alert('Error', 'Permissions are not granted. You should open the settings and enable them', [
              {
                text: 'Cancel',
                style: 'cancel',
              },
              {
                text: 'Open Settings',
                onPress: () => Linking.openSettings(),
                isPreferred: true,
                style: 'default',
              },
            ])
          } else {
            Alert.alert('Error', 'BLE is not supported on this kind of device', [
              {
                text: 'Ok',
                style: 'default',
              },
            ])
          }
        })
    }, [])
  )

  return (
    <SafeAreaView>
      <Text>Initializing BLE stack...</Text>
    </SafeAreaView>
  )
}

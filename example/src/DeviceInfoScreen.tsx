import { useNavigation } from '@react-navigation/core'
import { StackNavigationProp } from '@react-navigation/stack'
import { useEffect, useState } from 'react'
import { Alert, Text } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { RootStackParamList } from './Navigation'
import { useBleManager } from './hooks/useBleManager'

const GENERIC_ACCESS_SERVICE_UUID = '180a'
const MANUFACTURER_NAME_CHARACTERISTIC_UUID = '2a29'

export function DeviceInfoScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>()
  const manager = useBleManager()

  const [manufacturerName, setManufacturerName] = useState('--')

  useEffect(() => {
    manager
      .read(GENERIC_ACCESS_SERVICE_UUID, MANUFACTURER_NAME_CHARACTERISTIC_UUID)
      .then(data => setManufacturerName(data.toString()))
      .catch(e => {
        Alert.alert('Error', 'Error reading device info: ' + e.message, [
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
      })
  }, [])

  return (
    <SafeAreaView edges={['bottom']}>
      <Text style={{ textAlign: 'center', fontSize: 18, margin: 15 }}>Manufacturer: {manufacturerName}</Text>
    </SafeAreaView>
  )
}

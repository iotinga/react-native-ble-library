import { useBleScan } from '@iotinga/react-native-ble-library'
import { useFocusEffect, useNavigation } from '@react-navigation/native'
import { StackNavigationProp } from '@react-navigation/stack'
import { Text, TouchableOpacity, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { RootStackParamList } from './Navigation'
import { FlatList } from 'react-native-gesture-handler'

export function ScanScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>()
  const [devices, error] = useBleScan()

  return (
    <SafeAreaView edges={['bottom']}>
      <Text style={{ margin: 15, fontSize: 18, textAlign: 'center' }}>{devices.length === 0 ? 'Scanning for BLE devices...' : `Found ${devices.length} devices`}</Text>
      {error && (
        <Text style={{ marginBottom: 15, fontSize: 18, color: 'red', textAlign: 'center' }}>
          BLE scan error: {error.message}
        </Text>
      )}
      <FlatList
        style={{ height: '100%', paddingHorizontal: 15 }}
        data={devices}
        renderItem={({ item, index }) => (
          <>
            <View style={{ borderBottomColor: 'black', borderBottomWidth: 1 }} />
            <TouchableOpacity
              onPress={() => navigation.navigate('Connect', { id: item.id })}
              style={{ height: 40, justifyContent: 'center' }}
            >
              <Text>
                {item.id} - {item.name}
              </Text>
            </TouchableOpacity>
          </>
        )}
      />
    </SafeAreaView>
  )
}

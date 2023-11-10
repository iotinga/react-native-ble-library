import { useNavigation } from '@react-navigation/native'
import { StackNavigationProp } from '@react-navigation/stack'
import { Text, TouchableOpacity, View } from 'react-native'
import { FlatList } from 'react-native-gesture-handler'
import { SafeAreaView } from 'react-native-safe-area-context'
import { RootStackParamList } from './Navigation'
import { useBleScan } from './hooks/useBleScan'

const FILTER_UUID = ['D19299C3-DEC3-04BE-07D4-14651FF6B6A4']

export function ScanScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>()
  const [devices, error] = useBleScan(FILTER_UUID)

  return (
    <SafeAreaView edges={['bottom']} style={{ flex: 1 }}>
      <Text style={{ margin: 15, fontSize: 18, textAlign: 'center' }}>
        {devices.length === 0 ? 'Scanning for BLE devices...' : `Found ${devices.length} devices`}
      </Text>
      {error && (
        <Text style={{ marginBottom: 15, fontSize: 18, color: 'red', textAlign: 'center' }}>
          BLE scan error: {error.message}
        </Text>
      )}
      <FlatList
        style={{ flex: 1, paddingHorizontal: 15 }}
        data={devices}
        renderItem={({ item }) => (
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

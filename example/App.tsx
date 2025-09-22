import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, ScrollView, Text, TouchableOpacity, View } from 'react-native'
import { BleDeviceInfo, NativeBleManager } from 'react-native-ble-library'
import { SafeAreaView } from 'react-native-safe-area-context'

export default function App() {
  const manager = useMemo(() => new NativeBleManager(console), [])
  const [scanActive, setScanActive] = useState(false)
  const [devices, setDevices] = useState<BleDeviceInfo[]>([])

  useEffect(() => {
    manager
      .init()
      .then(() => {
        console.log('Manager init ok')
      })
      .catch(e => {
        console.error('manager init error')
        Alert.alert('ERROR', `Init BLE manager error: ${e}`)
      })

    return () => {
      manager.dispose()
    }
  }, [manager])

  useEffect(() => {
    if (scanActive) {
      const subscription = manager.scan(
        null,
        devices => {
          console.log(`Discovered devices`, devices)

          setDevices(currentDevices => {
            return [
              ...currentDevices.filter(d => devices.find(newDevice => d.id === newDevice.id) === undefined),
              ...devices.filter(d => d.isAvailable && d.isConnectable),
            ]
          })
        },
        error => {
          Alert.alert('ERROR', `Scan error: ${error}`)
        }
      )

      return () => {
        subscription.remove()
      }
    }
  }, [manager, scanActive])

  const connect = (id: string) => {
    setScanActive(false)
    manager
      .connect(id)
      .then(() => {
        console.log('Connect OK!')
      })
      .catch(error => {
        Alert.alert('ERROR', `Connection error: ${error}`)
      })
  }

  return (
    <SafeAreaView style={styles.container}>
      <Button title={scanActive ? 'Stop scan' : 'Start scan'} onPress={() => setScanActive(active => !active)} />
      <ScrollView>
        {devices.map(item => (
          <TouchableOpacity
            key={item.id}
            onPress={() => connect(item.id)}
            style={{
              height: 20,
              justifyContent: 'center',
              flexDirection: 'row',
            }}
          >
            <Text>
              {item.name} - {item.id}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = {
  header: {
    fontSize: 30,
    margin: 20,
  },
  groupHeader: {
    fontSize: 20,
    marginBottom: 20,
  },
  group: {
    margin: 20,
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 20,
  },
  container: {
    flex: 1,
    backgroundColor: '#eee',
  },
  view: {
    flex: 1,
    height: 200,
  },
}

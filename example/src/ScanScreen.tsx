import { useBleScan } from '@iotinga/react-native-ble-library'
import React from 'react'
import { SafeAreaView, ScrollView, Text, TouchableOpacity } from 'react-native'

const SCAN_FILTER = ['D19299C3-DEC3-04BE-07D4-14651FF6B6A4']

export function ScanScreen({ onSelectDevice }: { onSelectDevice: (device: string) => void }) {
  const devices = useBleScan(SCAN_FILTER)

  return (
    <SafeAreaView>
      <Text>Scanning....</Text>
      <ScrollView>
        {devices.map((device) => (
          <TouchableOpacity
            key={device.id}
            style={{
              borderBottomColor: 'red',
              borderBottomWidth: 2,
              height: 40,
              justifyContent: 'center',
              flex: 1,
            }}
            onPress={() => {
              onSelectDevice(device.id)
            }}
          >
            <Text>{device.id} - {device.name}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </SafeAreaView>
  )
}

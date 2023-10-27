import { BleChar, useBleCharacteristic } from "@iotinga/react-native-ble-library"
import React from "react"
import { SafeAreaView, Text } from "react-native"

const SERVICE_UUID = 'D19299C3-DEC3-04BE-07D4-14651FF6B6A4'
const CHAR_REPORTED_UUID = '61D4260B-A6A5-4682-B9AE-1F2984CE4A0D'
const CHAR_DESIRED_UUID = '8B146310-44BD-4821-9030-100F0B89693D'
const CHAR_TRANSFER_UUID = '58FF39CE-05D6-42BD-9385-CA5066EABADF'

const CHAR_REPORTED = new BleChar(SERVICE_UUID, CHAR_REPORTED_UUID)
const CHAR_DESIRED = new BleChar(SERVICE_UUID, CHAR_DESIRED_UUID)
const CHAR_TRANSFER = new BleChar(SERVICE_UUID, CHAR_TRANSFER_UUID, undefined, 230)

export function ViewScreen() {
  const [reported] = useBleCharacteristic(CHAR_REPORTED)
  const [desired] = useBleCharacteristic(CHAR_DESIRED)
  const [transfer] = useBleCharacteristic(CHAR_TRANSFER)

  return (
    <SafeAreaView>
      <Text>Reported: {reported?.toString('base64')}</Text>
      <Text>Desired: {desired?.toString('base64')}</Text>
      <Text>Transfer: {transfer?.toString('base64')}</Text>
    </SafeAreaView>
  )
}

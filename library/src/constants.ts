import { Platform } from "react-native"
import { PERMISSIONS } from 'react-native-permissions'

export const BLE_PERMISSIONS: string[] = Platform.select({
  ios: [PERMISSIONS.IOS.BLUETOOTH_PERIPHERAL],
  android: [
    PERMISSIONS.ANDROID.BLUETOOTH_SCAN,
    PERMISSIONS.ANDROID.BLUETOOTH_CONNECT,
    PERMISSIONS.ANDROID.ACCESS_FINE_LOCATION,
  ],
  default: [],
})

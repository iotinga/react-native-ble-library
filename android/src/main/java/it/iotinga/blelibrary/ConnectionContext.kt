package it.iotinga.blelibrary

import android.bluetooth.BluetoothGatt

class ConnectionContext {
    var mtu: Int = 0
    var gatt: BluetoothGatt? = null
}

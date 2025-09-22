package it.iotinga.blelibrary

import android.bluetooth.BluetoothGatt
import android.content.Context
import no.nordicsemi.android.ble.BleManager

class RNBleManager(context: Context, private val mtu: Int): BleManager(context) {
  private var gatt: BluetoothGatt? = null

  override fun initialize() {
    requestMtu(mtu).enqueue()
  }

  override fun onServicesInvalidated() {
    gatt = null
  }

  override fun isOptionalServiceSupported(gatt: BluetoothGatt): Boolean {
    this.gatt = gatt

    return true
  }

  fun writeChar() {


  }

}

package it.iotinga.blelibrary

import android.bluetooth.BluetoothGatt
import android.util.Log
import androidx.annotation.RequiresPermission
import expo.modules.kotlin.Promise

class TransactionReadRssi internal constructor(
    transactionId: String,
    promise: Promise,
    gatt: BluetoothGatt,
  ) : GattTransaction(transactionId, promise, gatt) {
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    override fun start() {
        super.start()

        val success = gatt.readRemoteRssi()
        if (success) {
            Log.i(Constants.LOG_TAG, "requested read remote RSSI")
        } else {
            Log.w(Constants.LOG_TAG, "error requesting read remote RSSI")

            fail(BleError.ERROR_GATT, "error requesting remote RSSI")
        }
    }

    override fun onReadRemoteRssi(rssi: Int) {
        succeed(rssi)
    }
}

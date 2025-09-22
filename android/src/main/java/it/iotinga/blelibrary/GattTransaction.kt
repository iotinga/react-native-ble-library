package it.iotinga.blelibrary

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import expo.modules.kotlin.Promise
import java.util.UUID

abstract class GattTransaction internal constructor(
  id: String,
  promise: Promise,
  protected val gatt: BluetoothGatt
) : PromiseTransaction(id, promise) {
    open fun onCharRead(characteristic: BluetoothGattCharacteristic) {
        fail(BleError.ERROR_INVALID_STATE, "unexpected onCharRead")
    }

    open fun onCharWrite(characteristic: BluetoothGattCharacteristic) {
        fail(BleError.ERROR_INVALID_STATE, "unexpected charWrite")
    }

    fun onError(gattError: Int) {
        fail(BleError.ERROR_GATT, "GATT error code $gattError")
    }

    open fun onReadRemoteRssi(rssi: Int) {
        fail(BleError.ERROR_INVALID_STATE, "unexpected charWrite")
    }

    protected fun getCharacteristic(
        serviceUuid: String,
        characteristicUuid: String
    ): BluetoothGattCharacteristic? {
        return getCharacteristic(gatt, serviceUuid, characteristicUuid)
    }

    companion object {
        private const val STANDARD_UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB"
        fun bleUuidFromString(uuid: String): UUID? {
          val result = when (uuid.length) {
              4 -> {
                // 16 bit uuid
                "0000$uuid$STANDARD_UUID_SUFFIX"
              }
              8 -> {
                // 32 bit uuid
                uuid + STANDARD_UUID_SUFFIX
              }
              else -> {
                // 128 bit uuid
                uuid
              }
          }

            return UUID.fromString(result)
        }

        fun getCharacteristic(
            gatt: BluetoothGatt,
            serviceUuid: String,
            characteristicUuid: String
        ): BluetoothGattCharacteristic? {
            val service = gatt.getService(bleUuidFromString(serviceUuid))
            if (service == null) {
                return null
            }

            return service.getCharacteristic(bleUuidFromString(characteristicUuid))
        }
    }
}

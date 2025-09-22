package it.iotinga.blelibrary

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import androidx.annotation.RequiresPermission
import expo.modules.kotlin.Promise
import java.util.Arrays
import kotlin.math.min

class TransactionWriteChar internal constructor(
  transactionId: String,
  promise: Promise,
  gatt: BluetoothGatt,
  private val serviceUuid: String,
  private val characteristicUuid: String,
  private val bytes: ByteArray,
  private val chunkSize: Int,
  private val sendEvent: (name: String, body: Map<String, Any?>) -> Unit,
) : GattTransaction(transactionId, promise, gatt) {
  private var writtenBytes = 0

  private fun hasNextChunk(): Boolean {
    return writtenBytes < bytes.size
  }

  private val nextChunk: ByteArray?
    get() {
      if (!hasNextChunk()) {
        return null
      }

      val rangeEnd = min(writtenBytes + chunkSize, bytes.size)
      val chunk =
        Arrays.copyOfRange(bytes, writtenBytes, rangeEnd)
      writtenBytes += chunk.size

      return chunk
    }

  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  private fun sendWriteChunk(characteristic: BluetoothGattCharacteristic) {
    val setValueSuccess = characteristic.setValue(this.nextChunk)
    if (!setValueSuccess) {
      Log.w(Constants.LOG_TAG, "error setting value to be written")

      fail(BleError.ERROR_GATT, "error setting characteristic value")
    } else {
      val writeSuccess = gatt.writeCharacteristic(characteristic)
      if (!writeSuccess) {
        Log.w(Constants.LOG_TAG, "error requesting characteristic write")

        fail(BleError.ERROR_GATT, "error requesting characteristic write")
      }
    }
  }

  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  override fun start() {
    super.start()

    val characteristic = getCharacteristic(serviceUuid, characteristicUuid)
    if (characteristic == null) {
      Log.w(Constants.LOG_TAG, "characteristic with such ID was not found")

      fail(BleError.ERROR_INVALID_ARGUMENTS, "characteristic which such UUID not found")
    } else {
      Log.i(Constants.LOG_TAG, "requesting write of first chunk")

      sendWriteChunk(characteristic)
    }
  }


  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  override fun onCharWrite(characteristic: BluetoothGattCharacteristic) {
    if (hasNextChunk()) {
      Log.i(Constants.LOG_TAG, "write chunk success, requesting another chunk")

      sendEvent(
        Event.PROGRESS, mapOf(
          "transactionId" to id(),
          "service" to characteristic.service.uuid.toString(),
          "characteristic" to characteristic.uuid.toString(),
          "total" to writtenBytes,
          "current" to bytes.size,
        )
      )

      sendWriteChunk(characteristic)
    } else {
      Log.i(Constants.LOG_TAG, "written all data successfully")

      succeed(null)
    }
  }
}

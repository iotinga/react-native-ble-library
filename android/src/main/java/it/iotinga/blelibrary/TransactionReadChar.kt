package it.iotinga.blelibrary

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import androidx.annotation.RequiresPermission
import expo.modules.kotlin.Promise
import android.util.Base64

@SuppressLint("MissingPermission")
class TransactionReadChar internal constructor(
  transactionId: String,
  promise: Promise,
  gatt: BluetoothGatt,
  private val serviceUuid: String,
  private val charUuid: String,
  totalSize: Int,
  private val sendEvent: (name: String, body: Map<String, Any?>) -> Unit,
  ) : GattTransaction(transactionId, promise, gatt) {

  /** total bytes received from device  */
  private var receivedBytes = 0
  private var data: ByteArray? = null

  init {
    if (totalSize != 0) {
      this.data = ByteArray(totalSize)
    }
  }

  /**
   * Callback called when a chunk of data is received from the BLE stack
   *
   * @param bytes the bytes received
   * @return true if more data has to be received
   */
  private fun onChunk(bytes: ByteArray): Boolean {
    if (data != null) {
      if (data!!.size == 1 && data!![0] == EOF_BYTE) {
        Log.i(Constants.LOG_TAG, "read a message of 1 byte 0xff: reached EOF")

        return false
      } else {
        for (b in bytes) {
          if (receivedBytes < data!!.size) {
            data!![receivedBytes++] = b
          } else {
            Log.w(Constants.LOG_TAG, "overflow of data array. Skip exceeding data")
          }
        }
        return receivedBytes < data!!.size
      }
    } else {
      data = bytes
      receivedBytes = bytes.size
    }

    return false
  }

  private fun sendReadChunk(characteristic: BluetoothGattCharacteristic?) {
    val success = gatt.readCharacteristic(characteristic)
    if (!success) {
      Log.w(Constants.LOG_TAG, "error performing a read request")

      fail(BleError.ERROR_GATT, "readCharacteristic returned false")
    }
  }

  override fun onCharRead(characteristic: BluetoothGattCharacteristic) {
    val hasMoreChunks = onChunk(characteristic.value)
    if (hasMoreChunks) {
      Log.i(Constants.LOG_TAG, "need to read another chunk of data")

      sendEvent(
        Event.PROGRESS, mapOf(
          "transactionId" to id(),
          "service" to characteristic.service.uuid.toString(),
          "characteristic" to characteristic.uuid.toString(),
          "total" to data!!.size,
          "current" to receivedBytes,
        )
      )

      sendReadChunk(characteristic)
    } else {
      Log.i(Constants.LOG_TAG, "all data read :)")

      succeed(Base64.encodeToString(data!!.copyOf(receivedBytes), Base64.DEFAULT))
    }
  }

  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  override fun start() {
    super.start()

    val characteristic = getCharacteristic(serviceUuid, charUuid)
    if (characteristic == null) {
      Log.w(Constants.LOG_TAG, "the characteristic to be read is not found")

      fail(BleError.ERROR_INVALID_ARGUMENTS, "characteristic not found in device")
    } else {
      Log.i(Constants.LOG_TAG, "requesting a read from the device")

      sendReadChunk(characteristic)
    }
  }

  companion object {
    private const val EOF_BYTE = 0xff.toByte()
  }
}

package it.iotinga.blelibrary

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.util.Log
import androidx.annotation.RequiresPermission
import android.util.Base64
import java.util.Locale

@SuppressLint("MissingPermission")
class BleBluetoothGattCallback internal constructor(
  private val sendEvent: (name: String, body: Map<String, Any?>) -> Unit,
  private val connectionContext: ConnectionContext,
  private val executor: TransactionExecutor
) : BluetoothGattCallback() {
  private fun emitConnectionStateChange(
    state: ConnectionState,
    gattStatus: Int,
    bleServices: List<BluetoothGattService>?
  ) {

    sendEvent(
      Event.CONNECTION_STATE_CHANGED, mapOf(
      "state" to state.name,
      "message" to "connection state changed (status: $gattStatus)",
      "error" to if (gattStatus != BluetoothGatt.GATT_SUCCESS) BleError.ERROR_GATT.name else null,
      "android" to mapOf(
        "status" to gattStatus,
      ),
      "services" to bleServices?.map { service ->
        mapOf(
          "uuid" to service.uuid.toString().lowercase(Locale.getDefault()),
          "isPrimary" to (service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY),
          "characteristics" to service.characteristics.map { characteristic ->
            mapOf(
              "uuid" to characteristic.uuid.toString().lowercase(),
              "properties" to characteristic.properties,
            )
          }
        )
      }
    )
    )
  }

  override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
    Log.d(
      Constants.LOG_TAG,
      "onConnectionStateChange - status: $status, newState: $newState"
    )

    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
      Log.i(Constants.LOG_TAG, "device is successfully connected. Requesting MTU")

      if (connectionContext.mtu > 0) {
        Log.i(Constants.LOG_TAG, "setting the MTU to: " + connectionContext.mtu)

        val success = gatt.requestMtu(connectionContext.mtu)
        if (success) {
          emitConnectionStateChange(
            ConnectionState.REQUESTING_MTU,
            BluetoothGatt.GATT_SUCCESS,
            null
          )
        } else {
          Log.w(Constants.LOG_TAG, "error sending MTU request for device.")

          gatt.disconnect()

          emitConnectionStateChange(
            ConnectionState.DISCONNECTING,
            BluetoothGatt.GATT_FAILURE,
            null
          )

        }
      } else {
        Log.i(Constants.LOG_TAG, "using default MTU, discovering services")

        val success = gatt.discoverServices()
        if (success) {
          emitConnectionStateChange(
            ConnectionState.DISCOVERING_SERVICES,
            BluetoothGatt.GATT_SUCCESS,
            null
          )
        } else {
          Log.w(Constants.LOG_TAG, "error sending service discovery request for device.")

          gatt.disconnect()

          emitConnectionStateChange(
            ConnectionState.DISCONNECTING,
            BluetoothGatt.GATT_FAILURE, null
          )
        }
      }
    }

    if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
      Log.w(
        Constants.LOG_TAG,
        "connection failed unexpectedly. Trigger a new connection to device"
      )

      // cancel all pending transaction
      executor.flush(BleError.ERROR_NOT_CONNECTED, "device has disconnected (unexpectedly)")

      val success = gatt.connect()
      if (success) {
        emitConnectionStateChange(
          ConnectionState.CONNECTING_TO_DEVICE,
          status, null

        )
      } else {
        Log.w(Constants.LOG_TAG, "error asking for device reconnect")

        emitConnectionStateChange(
          ConnectionState.DISCONNECTED,
          BluetoothGatt.GATT_FAILURE,
          null
        )
      }
    }

    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
      Log.w(Constants.LOG_TAG, "expected disconnection")

      // cancel all pending transaction
      executor.flush(BleError.ERROR_NOT_CONNECTED, "device has disconnected (expectedly)")

      emitConnectionStateChange(ConnectionState.DISCONNECTED, status, null)
    }
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
    Log.d(Constants.LOG_TAG, "onMtuChanged - status: $status, mtu: $mtu")

    if (status == BluetoothGatt.GATT_FAILURE) {
      Log.w(
        Constants.LOG_TAG,
        "error setting MTU. Continuing anyway in the connection process using default MTU"
      )
    } else {
      Log.i(Constants.LOG_TAG, "set of the MTU is successful. Proceed with service discovery")
    }

    val success = gatt.discoverServices()
    if (success) {
      emitConnectionStateChange(
        ConnectionState.DISCOVERING_SERVICES,
        BluetoothGatt.GATT_SUCCESS,
        null
      )
    } else {
      Log.w(Constants.LOG_TAG, "error asking for service discovery. Try to reset connection")

      gatt.disconnect()

      emitConnectionStateChange(
        ConnectionState.DISCONNECTING,
        BluetoothGatt.GATT_FAILURE,
        null
      )
    }
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
    Log.d(Constants.LOG_TAG, "onServiceDiscovered - status: $status")

    if (status == BluetoothGatt.GATT_SUCCESS) {
      Log.i(
        Constants.LOG_TAG,
        "service discovery success. The device is now ready to be used"
      )

      emitConnectionStateChange(
        ConnectionState.CONNECTED,
        BluetoothGatt.GATT_SUCCESS, gatt.getServices(),
      )
    } else {
      Log.w(
        Constants.LOG_TAG,
        "error discovering services. Try to reset the connection with the device"
      )

      gatt.disconnect()

      emitConnectionStateChange(
        ConnectionState.DISCONNECTING, status, null
      )
    }
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  override fun onCharacteristicWrite(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    status: Int
  ) {
    Log.d(Constants.LOG_TAG, "onCharacteristicWrite - status: $status")

    val transaction = executor.executing
    if (transaction != null) {
      try {
        if (transaction is GattTransaction) {
          val gattTransaction = transaction
          if (status == BluetoothGatt.GATT_SUCCESS) {
            gattTransaction.onCharWrite(characteristic)
          } else {
            gattTransaction.onError(status)
          }
        }
      } catch (e: Exception) {
        val msg = "unexpected exception in onCharacteristicWrite()$e"
        Log.e(Constants.LOG_TAG, msg)
        transaction.fail(BleError.ERROR_GENERIC, msg)
      }
    } else {
      Log.w(Constants.LOG_TAG, "no transaction is pending. How do I get here?")
    }

    executor.process()
  }

  @Deprecated("Deprecated in Java")
  override fun onCharacteristicRead(
    gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
    status: Int
  ) {
    Log.d(Constants.LOG_TAG, "onCharacteristicRead - status: $status")

    val transaction = executor.executing
    if (transaction != null) {
      try {
        if (transaction is GattTransaction) {
          val gattTransaction = transaction
          if (status == BluetoothGatt.GATT_SUCCESS) {
            gattTransaction.onCharRead(characteristic)
          } else {
            gattTransaction.onError(status)
          }
        }
      } catch (e: Exception) {
        val msg = "unexpected exception in onCharacteristicRead() $e"
        Log.e(Constants.LOG_TAG, msg)
        transaction.fail(BleError.ERROR_GENERIC, msg)
      }
    } else {
      Log.w(Constants.LOG_TAG, "no transaction is pending. How do I get here?")
    }

    executor.process()
  }

  // NOTE: this is deprecated but useful to support older OS!
  @Deprecated("Deprecated in Java")
  override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic
  ) {
    val value = characteristic.value

    Log.d(
      Constants.LOG_TAG,
      "onCharacteristicChanged uuid " + characteristic.uuid + " value length " + value.size
    )

    sendEvent(
      Event.CHAR_VALUE_CHANGED, mapOf(
        "service" to characteristic.service.uuid.toString(),
        "characteristic" to characteristic.uuid.toString(),
        "value" to Base64.encodeToString(value, Base64.DEFAULT),
      )
    )
  }

  override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
    Log.d(Constants.LOG_TAG, "onReadRemoteRssi - rssi: $rssi status: $status")

    val transaction = executor.executing
    if (transaction != null) {
      try {
        if (transaction is GattTransaction) {
          val gattTransaction = transaction
          if (status == BluetoothGatt.GATT_SUCCESS) {
            gattTransaction.onReadRemoteRssi(rssi)
          } else {
            gattTransaction.onError(status)
          }
        }
      } catch (e: Exception) {
        val msg = "unexpected exception in onReadRemoteRssi()$e"
        Log.e(Constants.LOG_TAG, msg)
        transaction.fail(BleError.ERROR_GENERIC, msg)
      }
    } else {
      Log.w(Constants.LOG_TAG, "no transaction is pending. How do I get here?")
    }

    executor.process()
  }
}

package it.iotinga.blelibrary

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Base64
import android.util.Log
import expo.modules.kotlin.Promise
import kotlinx.coroutines.flow.callbackFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.TimeoutableRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.min

private const val STANDARD_UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB"
private const val EOF_BYTE = 0xff.toByte()
private const val TIMEOUT_MS = 5_000L

class RNBleManager(
  context: Context,
  private val sendEvent: (event: Event, payload: Map<String, Any?>) -> Unit
) : BleManager(context) {
  private val requestById = HashMap<String, TimeoutableRequest>()
  private val cancelledRequests = HashSet<String>()
  private var gatt: BluetoothGatt? = null
  private var mtu: Int? = null

  override fun getMinLogPriority(): Int {
    return Log.VERBOSE
  }

  private fun emitConnectionStateChange(
    state: ConnectionState,
    gattStatus: Int,
  ) {
    sendEvent(
      Event.CONNECTION_STATE_CHANGED, mapOf(
        "state" to state.name,
        "message" to "connection state changed (status: $gattStatus)",
        "error" to if (gattStatus != 0) BleError.ERROR_GATT.name else null,
        "android" to mapOf(
          "status" to gattStatus,
        ),
        "services" to gatt?.services?.map { service ->
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

  override fun initialize() {
    requestById.clear()
    cancelledRequests.clear()

    val mtu = mtu
    if (mtu != null) {
      requestMtu(mtu)
        .before {
          emitConnectionStateChange(
            ConnectionState.REQUESTING_MTU,
            BluetoothGatt.GATT_SUCCESS,
          )
        }
        .enqueue()
    }
  }

  override fun onDeviceReady() {
    emitConnectionStateChange(ConnectionState.CONNECTED, 0)
  }

  override fun onServicesInvalidated() {
    emitConnectionStateChange(ConnectionState.DISCONNECTED, 0)
    gatt = null
  }

  override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
    emitConnectionStateChange(
      ConnectionState.DISCOVERING_SERVICES,
      BluetoothGatt.GATT_SUCCESS,
    )

    this.gatt = gatt

    return true
  }

  fun enqueue(transactionId: String, request: TimeoutableRequest) {
    requestById[transactionId] = request

    request
      .timeout(TIMEOUT_MS)
      .then {
        requestById.remove(transactionId)
        cancelledRequests.remove(transactionId)
      }
      .enqueue()
  }

  fun cancel(transactionId: String) {
    cancelledRequests.add(transactionId)
    requestById[transactionId]?.cancel()
  }

  private fun isCancelled(transactionId: String): Boolean {
    return cancelledRequests.contains(transactionId)
  }

  fun connect(device: BluetoothDevice, mtu: Int, promise: Promise) {
    this.mtu = mtu

    if (isConnected) {
      disconnect()
        .done { Log.i(LOG_TAG, "device disconnected") }
        .before { emitConnectionStateChange(ConnectionState.DISCONNECTING, 0) }
        .fail { device, status ->
          Log.w(
            LOG_TAG,
            "error disconnecting device: $status"
          )
        }
        .enqueue()
    }

    connect(device)
      .retry(3)
      .timeout(TIMEOUT_MS)
      .useAutoConnect(false)
      .before {
        emitConnectionStateChange(ConnectionState.CONNECTING_TO_DEVICE, BluetoothGatt.GATT_SUCCESS)
      }
      .done {
        Log.i(LOG_TAG, "device connected")
        promise.resolve()
      }
      .fail { device, status ->
        Log.w(LOG_TAG, "error connecting device: $status")
        emitConnectionStateChange(ConnectionState.DISCONNECTED, status)
        promise.reject(
          BleError.ERROR_NOT_CONNECTED.name,
          "error connecting to device status: $status",
          null
        )
      }
      .enqueue()
  }

  fun disconnect(promise: Promise) {
    if (isConnected) {
      disconnect()
        .timeout(5_000)
        .before { emitConnectionStateChange(ConnectionState.DISCONNECTING, 0) }
        .done {
          Log.i(LOG_TAG, "Device disconnected")
          emitConnectionStateChange(ConnectionState.DISCONNECTED, 0)
          promise.resolve()
        }
        .fail { device, status ->
          Log.w(LOG_TAG, "Error disconnecting device")
          promise.reject(BleError.ERROR_GATT.name, "Error disconnecting device: $status", null)
        }
        .enqueue()
    } else {
      promise.resolve(null)
    }
  }

  fun getRSSI(promise: Promise) {
    readRssi()
      .with { device, rssi ->
        Log.i(LOG_TAG, "Read RSSI $rssi")
        promise.resolve(rssi)
      }
      .fail { device, status ->
        Log.w(LOG_TAG, "Error reading RSSI: $status")
        promise.reject(BleError.ERROR_GATT.name, "Error reading RSSI: $status", null)
      }
      .enqueue()
  }

  fun readChar(
    transactionId: String,
    service: String,
    characteristic: String,
    size: Int,
    promise: Promise
  ) {
    findCharacteristic(promise, service, characteristic) { characteristic ->
      readCharRecursive(transactionId, characteristic, ByteArrayOutputStream(), size, promise)
    }
  }

  private fun enableNotification(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    promise: Promise
  ) {
    Log.i(LOG_TAG, "Enabling notifications for char ${characteristic.uuid}")
    setNotificationCallback(characteristic)
      .with { device, data ->
        Log.d(LOG_TAG, "Notification received for char ${characteristic.uuid} ${data.size()}")
        sendEvent(
          Event.CHAR_VALUE_CHANGED, mapOf(
            "service" to characteristic.service.uuid.toString(),
            "characteristic" to characteristic.uuid.toString(),
            "value" to Base64.encodeToString(data.value, Base64.DEFAULT),
          )
        )
      }


    enqueue(
      transactionId, enableNotifications(characteristic)
        .done {
          Log.d(LOG_TAG, "Notifications successfully enabled")

          promise.resolve()
        }
        .fail { device, status ->
          Log.w(LOG_TAG, "Error enabling notifications: $status")
          promise.reject(
            BleError.ERROR_GATT.name,
            "Error enabling notifications: $status",
            null
          )
        }
    )
  }

  private fun disableNotification(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    promise: Promise
  ) {
    Log.i(LOG_TAG, "Disabling notifications for char ${characteristic.uuid}")

    removeNotificationCallback(characteristic)
    enqueue(
      transactionId, disableNotifications(characteristic)
        .done {
          Log.d(LOG_TAG, "Notifications successfully disabled")
          promise.resolve()
        }
        .fail { device, status ->
          Log.w(LOG_TAG, "Error enabling notifications: $status")
          promise.reject(
            BleError.ERROR_GATT.name,
            "Error enabling notifications: $status",
            null
          )
        }
    )
  }

  private fun enableIndication(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    promise: Promise
  ) {
    Log.i(LOG_TAG, "Enabling indications for char ${characteristic.uuid}")

    setIndicationCallback(characteristic)
      .with { device, data ->
        Log.d(LOG_TAG, "Indication received for char ${characteristic.uuid} ${data.size()}")
        sendEvent(
          Event.CHAR_VALUE_CHANGED, mapOf(
            "service" to characteristic.service.uuid.toString(),
            "characteristic" to characteristic.uuid.toString(),
            "value" to Base64.encodeToString(data.value, Base64.DEFAULT),
          )
        )
      }

    enqueue(
      transactionId, enableIndications(characteristic)
        .done {
          Log.d(LOG_TAG, "Indications successfully enabled")
          promise.resolve()
        }
        .fail { device, status ->
          Log.w(LOG_TAG, "Error enabling notifications: $status")
          promise.reject(
            BleError.ERROR_GATT.name,
            "Error enabling notifications: $status",
            null
          )
        }
    )
  }

  private fun disableIndication(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    promise: Promise
  ) {
    Log.i(LOG_TAG, "Disabling indications for char ${characteristic.uuid}")

    removeIndicationCallback(characteristic)
    enqueue(
      transactionId, disableIndications(characteristic)
        .done {
          Log.d(LOG_TAG, "indications successfully disabled")
          promise.resolve()
        }
        .fail { device, status ->
          Log.w(LOG_TAG, "Error enabling notifications: $status")
          promise.reject(
            BleError.ERROR_GATT.name,
            "Error enabling notifications: $status",
            null
          )
        }
    )
  }


  fun enableIndicationOrNotification(
    transactionId: String,
    service: String,
    characteristic: String,
    promise: Promise
  ) {
    findCharacteristic(promise, service, characteristic) { characteristic ->
      if (characteristic.supportsNotification()) {
        enableNotification(transactionId, characteristic, promise)
      } else if (characteristic.supportsIndication()) {
        enableIndication(transactionId, characteristic, promise)
      } else {
        promise.reject(
          BleError.ERROR_INVALID_ARGUMENTS.name,
          "Characteristic does not support notification or indication",
          null
        )
      }
    }
  }

  fun disableIndicationOrNotification(
    transactionId: String,
    service: String,
    characteristic: String,
    promise: Promise
  ) {
    findCharacteristic(promise, service, characteristic) { characteristic ->
      if (characteristic.supportsNotification()) {
        disableNotification(transactionId, characteristic, promise)
      } else if (characteristic.supportsIndication()) {
        disableIndication(transactionId, characteristic, promise)
      } else {
        promise.reject(
          BleError.ERROR_INVALID_ARGUMENTS.name,
          "Characteristic does not support notification or indication",
          null
        )
      }
    }
  }

  private fun readCharRecursive(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    received: ByteArrayOutputStream,
    toReceive: Int,
    promise: Promise
  ) {
    if (isCancelled(transactionId)) {
      promise.reject(BleError.ERROR_OPERATION_CANCELED.name, "BLE transaction was cancelled", null)
    } else {
      enqueue(
        transactionId, readCharacteristic(characteristic)
          .with { device, data ->
            val isEOF = toReceive != 0 && data.size() == 1 && data.getByte(0) == EOF_BYTE
            if (!isEOF) {
              received.write(data.value)
            }

            sendEvent(
              Event.PROGRESS, mapOf(
                "transactionId" to transactionId,
                "service" to characteristic.service.uuid.toString(),
                "characteristic" to characteristic.uuid.toString(),
                "total" to toReceive,
                "current" to received.size(),
              )
            )

            val hasMoreData = toReceive != 0 && received.size() < toReceive
            if (hasMoreData && !isEOF) {
              readCharRecursive(transactionId, characteristic, received, toReceive, promise)
            } else {
              promise.resolve(Base64.encode(received.toByteArray(), Base64.DEFAULT))
            }
          }
          .fail { device, status ->
            Log.w(LOG_TAG, "Error reading characteristic: $status")
            promise.reject(BleError.ERROR_GATT.name, "Error reading characteristic: $status", null)
          }
      )
    }
  }

  fun writeChar(
    transactionId: String,
    service: String,
    characteristic: String,
    data: ByteArray,
    chunkSize: Int,
    promise: Promise
  ) {
    findCharacteristic(promise, service, characteristic) { characteristic ->
      writeCharRecursive(
        transactionId,
        characteristic,
        ByteArrayInputStream(data),
        chunkSize,
        data.size,
        promise
      )
    }

  }

  private fun writeCharRecursive(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    toWrite: ByteArrayInputStream,
    chunkSize: Int,
    totalSize: Int,
    promise: Promise
  ) {
    if (isCancelled(transactionId)) {
      promise.reject(BleError.ERROR_OPERATION_CANCELED.name, "BLE transaction was cancelled", null)
    } else {
      val size = min(chunkSize, toWrite.available())
      val data = ByteArray(size)
      toWrite.read(data, 0, size)
      enqueue(
        transactionId, writeCharacteristic(
          characteristic, data,
          BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
          .done {
            sendEvent(
              Event.PROGRESS, mapOf(
                "transactionId" to transactionId,
                "service" to characteristic.service.uuid.toString(),
                "characteristic" to characteristic.uuid.toString(),
                "total" to totalSize,
                "current" to toWrite.available(),
              )
            )

            if (toWrite.available() > 0) {
              writeCharRecursive(
                transactionId,
                characteristic,
                toWrite,
                chunkSize,
                totalSize,
                promise
              )
            } else {
              promise.resolve()
            }
          }
          .fail { device, status ->
            promise.reject(BleError.ERROR_GATT.name, "Error writing data chunk: $status", null)
          }
      )
    }
  }

  private fun findCharacteristic(
    promise: Promise,
    serviceUuid: String,
    characteristicUuid: String,
    callback: (
      characteristic: BluetoothGattCharacteristic
    ) -> Unit
  ) {
    val gatt = gatt
    if (gatt == null) {
      promise.reject(BleError.ERROR_INVALID_STATE.name, "BLE service discovery not completed", null)
    } else {
      val service = gatt.getService(bleUuidFromString(serviceUuid))
      if (service == null) {
        promise.reject(
          BleError.ERROR_INVALID_ARGUMENTS.name,
          "Service $serviceUuid not found",
          null
        )
      } else {
        val characteristic = service.getCharacteristic(bleUuidFromString(characteristicUuid))
        if (characteristic == null) {
          promise.reject(
            BleError.ERROR_INVALID_ARGUMENTS.name,
            "Characteristic $characteristicUuid not found",
            null
          )
        } else {
          callback(characteristic)
        }
      }
    }
  }

  fun dispose(promise: Promise) {
    for (request in requestById.values) {
      request.cancel()
    }

    if (isConnected) {
      disconnect()
        .then { promise.resolve() }
        .enqueue()
    }
  }
}

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

fun BluetoothGattCharacteristic.supportsIndication(): Boolean {
  return (BluetoothGattCharacteristic.PROPERTY_INDICATE and properties) != 0
}

fun BluetoothGattCharacteristic.supportsNotification(): Boolean {
  return (BluetoothGattCharacteristic.PROPERTY_NOTIFY and properties) != 0
}

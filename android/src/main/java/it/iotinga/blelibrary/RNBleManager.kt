package it.iotinga.blelibrary

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Base64
import android.util.Log
import expo.modules.kotlin.Promise
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.TimeoutableRequest
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.Locale
import java.util.UUID
import kotlin.math.min

private const val STANDARD_UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB"
private const val EOF_BYTE = 0xff.toByte()
private const val TIMEOUT_MS = 5_000L
private const val PROGRESS_SEND_INTERVAL_MS = 500L

class RNBleManager(
  context: Context,
  private val sendEvent: (event: Event, payload: Map<String, Any?>) -> Unit,
) : BleManager(context) {
  private val requestById = HashMap<String, TimeoutableRequest>()
  private var gatt: BluetoothGatt? = null
  private var mtu: Int? = null
  private var logLevel = Log.DEBUG

  init {
    connectionObserver = object : ConnectionObserver {
      override fun onDeviceConnecting(device: BluetoothDevice) {
        log(Log.INFO, "device connecting")
        emitConnectionStateChange(ConnectionState.CONNECTING_TO_DEVICE, 0)
      }

      override fun onDeviceConnected(device: BluetoothDevice) {
        emitConnectionStateChange(ConnectionState.DISCOVERING_SERVICES, 0)
      }

      override fun onDeviceFailedToConnect(
        device: BluetoothDevice,
        reason: Int
      ) {
        emitConnectionStateChange(ConnectionState.DISCONNECTED, reason)
      }

      override fun onDeviceReady(device: BluetoothDevice) {
        emitConnectionStateChange(ConnectionState.CONNECTED, 0)
      }

      override fun onDeviceDisconnecting(device: BluetoothDevice) {
        emitConnectionStateChange(ConnectionState.DISCONNECTING, 0)
      }

      override fun onDeviceDisconnected(
        device: BluetoothDevice,
        reason: Int
      ) {
        emitConnectionStateChange(ConnectionState.DISCONNECTED, reason)
      }
    }
  }

  override fun getMinLogPriority(): Int {
    return logLevel
  }

  override fun log(priority: Int, message: String) {
    if (priority >= logLevel) {
      Log.println(priority, LOG_TAG, message)
    }
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

    val mtu = mtu
    if (mtu != null) {
      requestMtu(mtu)
        .fail { device, status ->
          log(Log.WARN, "Error requesting MTU: $status")
        }
        .done {
          log(Log.INFO, "MTU correctly exchanged")
        }
        .enqueue()
    }
  }

  override fun onServicesInvalidated() {
    gatt = null
  }

  override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
    this.gatt = gatt

    return true
  }

  fun enqueue(transactionId: String, request: TimeoutableRequest) {
    requestById[transactionId] = request

    request
      .then {
        requestById.remove(transactionId)
      }
      .enqueue()
  }

  fun cancel(transactionId: String) {
    requestById[transactionId]?.cancel()
  }

  fun connect(device: BluetoothDevice, mtu: Int, options: Map<String, Any>?, promise: Promise) {
    this.mtu = mtu

    if (isConnected) {
      disconnect()
        .done { log(Log.INFO, "device disconnected") }
        .fail { device, status ->
          Log.w(
            LOG_TAG,
            "error disconnecting device: $status"
          )
        }
        .timeout(TIMEOUT_MS)
        .enqueue()
    }

    val request = connect(device)
      .retry(3)
      .timeout(TIMEOUT_MS)
      .useAutoConnect(false)
      .done {
        log(Log.INFO, "device connected")
        promise.resolve()
      }
      .fail { device, status ->
        log(Log.WARN, "error connecting device: $status")
        promise.reject(
          BleError.ERROR_NOT_CONNECTED.name,
          "error connecting to device status: $status",
          null
        )
      }

    if (options?.contains("timeout") == true) {
      log(Log.DEBUG, "set timeout to ${options["timeout"]}")
      request.timeout(options["timeout"] as Long)
    }
    if (options?.contains("preferredPhy") == true) {
      log(Log.DEBUG, "set preferredPhy to ${options["preferredPhy"]}")
      request.usePreferredPhy(options["preferredPhy"] as Int)
    }
    if (options?.contains("logLevel") == true) {
      log(Log.DEBUG, "set logLevel to ${options["logLevel"]}")
      logLevel = options["logLevel"] as Int
    }

    request.enqueue()
  }

  fun disconnect(promise: Promise) {
    if (isConnected) {
      disconnect()
        .timeout(5_000)
        .done {
          log(Log.INFO, "Device disconnected")
          promise.resolve()
        }
        .fail { device, status ->
          log(Log.WARN, "Error disconnecting device")
          promise.reject(BleError.ERROR_GATT.name, "Error disconnecting device: $status", null)
        }
        .timeout(TIMEOUT_MS)
        .enqueue()
    } else {
      promise.resolve(null)
    }
  }

  fun getRSSI(promise: Promise) {
    readRssi()
      .with { device, rssi ->
        log(Log.INFO, "Read RSSI $rssi")
        promise.resolve(rssi)
      }
      .fail { device, status ->
        log(Log.WARN, "Error reading RSSI: $status")
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
    var received = 0
    var lastProgressSentTime = 0L
    findCharacteristic(promise, service, characteristic) { characteristic ->
      enqueue(
        transactionId, readCharacteristic(characteristic)
          .merge({ output, lastPacket, index ->
            if (size != 0 && lastPacket?.size == 1 && lastPacket.get(0) == EOF_BYTE) {
              true // EOF received
            } else {
              output.write(lastPacket)
              size == 0 || output.size() >= size
            }
          }) { device, data, index ->
            received += data?.size ?: 0

            val now = System.currentTimeMillis()
            if (now - lastProgressSentTime > PROGRESS_SEND_INTERVAL_MS) {
              sendEvent(
                Event.PROGRESS, mapOf(
                  "transactionId" to transactionId,
                  "service" to characteristic.service.uuid.toString(),
                  "characteristic" to characteristic.uuid.toString(),
                  "total" to size,
                  "current" to received,
                )
              )
              lastProgressSentTime = now
            }
          }
          .with { device, data ->
            promise.resolve(Base64.encodeToString(data.value, Base64.DEFAULT))
          }
          .fail { device, status ->
            log(Log.WARN, "Error reading characteristic: $status")
            promise.reject(BleError.ERROR_GATT.name, "Error reading characteristic: $status", null)
          }
      )
    }
  }

  private fun enableNotification(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    promise: Promise
  ) {
    log(Log.INFO, "Enabling notifications for char ${characteristic.uuid}")
    setNotificationCallback(characteristic)
      .with { device, data ->
        log(Log.VERBOSE, "Notification received for char ${characteristic.uuid} ${data.size()}")
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
          log(Log.VERBOSE, "Notifications successfully enabled")

          promise.resolve()
        }
        .fail { device, status ->
          log(Log.WARN, "Error enabling notifications: $status")
          promise.reject(
            BleError.ERROR_GATT.name,
            "Error enabling notifications: $status",
            null
          )
        }
        .timeout(TIMEOUT_MS)
    )
  }

  private fun disableNotification(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    promise: Promise
  ) {
    log(Log.INFO, "Disabling notifications for char ${characteristic.uuid}")

    removeNotificationCallback(characteristic)
    enqueue(
      transactionId, disableNotifications(characteristic)
        .done {
          log(Log.VERBOSE, "Notifications successfully disabled")
          promise.resolve()
        }
        .fail { device, status ->
          log(Log.WARN, "Error enabling notifications: $status")
          promise.reject(
            BleError.ERROR_GATT.name,
            "Error enabling notifications: $status",
            null
          )
        }
        .timeout(TIMEOUT_MS)
    )
  }

  private fun enableIndication(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    promise: Promise
  ) {
    log(Log.INFO, "Enabling indications for char ${characteristic.uuid}")

    setIndicationCallback(characteristic)
      .with { device, data ->
        log(Log.VERBOSE, "Indication received for char ${characteristic.uuid} ${data.size()}")
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
          log(Log.VERBOSE, "Indications successfully enabled")
          promise.resolve()
        }
        .fail { device, status ->
          log(Log.WARN, "Error enabling notifications: $status")
          promise.reject(
            BleError.ERROR_GATT.name,
            "Error enabling notifications: $status",
            null
          )
        }
        .timeout(TIMEOUT_MS)
    )
  }

  private fun disableIndication(
    transactionId: String,
    characteristic: BluetoothGattCharacteristic,
    promise: Promise
  ) {
    log(Log.INFO, "Disabling indications for char ${characteristic.uuid}")

    removeIndicationCallback(characteristic)
    enqueue(
      transactionId, disableIndications(characteristic)
        .done {
          log(Log.VERBOSE, "indications successfully disabled")
          promise.resolve()
        }
        .fail { device, status ->
          log(Log.WARN, "Error enabling notifications: $status")
          promise.reject(
            BleError.ERROR_GATT.name,
            "Error enabling notifications: $status",
            null
          )
        }
        .timeout(TIMEOUT_MS)
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
        log(
          Log.WARN,
          "Characteristic ${characteristic.uuid} doesn't support indication or notification"
        )
        promise.reject(
          BleError.ERROR_INVALID_ARGUMENTS.name,
          "Characteristic does not support notification or indication",
          null
        )
      }
    }
  }

  fun writeChar(
    transactionId: String,
    service: String,
    characteristic: String,
    packet: ByteArray,
    chunkSize: Int,
    promise: Promise
  ) {
    var written = 0
    var lastProgressSentTime = 0L
    findCharacteristic(promise, service, characteristic) { characteristic ->
      enqueue(
        transactionId, writeCharacteristic(
          characteristic, packet,
          BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
          .split({ message, index, maxLength ->
            val packetSize = min(maxLength, chunkSize)
            val startIndex = index * packetSize
            if (startIndex >= message.size) {
              null
            } else {
              message.slice((startIndex..min(startIndex + packetSize, message.size) - 1))
                .toByteArray()
            }
          }) { device, data, index ->
            log(Log.VERBOSE, "Wrote chunk $index of ${data?.size} bytes")
            written += data?.size ?: 0

            val now = System.currentTimeMillis()
            if (now - lastProgressSentTime > PROGRESS_SEND_INTERVAL_MS) {
              sendEvent(
                Event.PROGRESS, mapOf(
                  "transactionId" to transactionId,
                  "service" to characteristic.service.uuid.toString(),
                  "characteristic" to characteristic.uuid.toString(),
                  "total" to packet.size,
                  "current" to written,
                )
              )
              lastProgressSentTime = now
            }
          }
          .done {
            log(Log.WARN, "Successfully wrote to char ${characteristic.uuid}")
            promise.resolve()
          }
          .fail { device, status ->
            log(Log.WARN, "Error writing to char ${characteristic.uuid}: $status")
            promise.reject(
              BleError.ERROR_GATT.name,
              "Error writing to char ${characteristic.uuid}: $status",
              null
            )
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

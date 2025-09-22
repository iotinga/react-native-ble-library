package it.iotinga.blelibrary

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresPermission
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition


class ReactNativeBleLibraryModule : Module() {
  private var manager: BluetoothManager? = null
  private var adapter: BluetoothAdapter? = null
  private var scanner: BluetoothLeScanner? = null
  private var gattCallback: BluetoothGattCallback? = null
  private var scanCallback: ScanCallback? = null
  private var bleActivationPromise: Promise? = null
  private val context = ConnectionContext()
  private val executor: TransactionExecutor = TransactionQueueExecutor()

  companion object {
    const val REQUEST_ENABLE_BT = 1
  }

  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  @SuppressLint("MissingPermission")
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('ReactNativeBleLibrary')` in JavaScript.
    Name("ReactNativeBleLibrary")

    // Defines event names that the module can send to JavaScript.
    Events(
      Event.ERROR,
      Event.PROGRESS,
      Event.SCAN_RESULT,
      Event.CHAR_VALUE_CHANGED,
      Event.CONNECTION_STATE_CHANGED
    )

    AsyncFunction("initModule") { promise: Promise ->
      Log.d(Constants.LOG_TAG, "initModule()")

      Log.i(Constants.LOG_TAG, "checking permissions")

      val permissionManager =
        BlePermissionsManager(appContext.reactContext!!, appContext.permissions!!)
      permissionManager.ensure { granted: Boolean ->
        if (!granted) {
          Log.w(Constants.LOG_TAG, "permission denied")

          promise.reject(BleError.ERROR_MISSING_PERMISSIONS.name, "missing BLE permissions", null)
        } else {
          Log.i(Constants.LOG_TAG, "permission granted")

          manager =
            appContext.reactContext!!.getSystemService(
              Context.BLUETOOTH_SERVICE
            ) as BluetoothManager?
          adapter = manager!!.adapter
          if (adapter == null) {
            Log.w(Constants.LOG_TAG, "this device doesn't have a BLE adapter")

            promise.reject(
              BleError.ERROR_BLE_NOT_SUPPORTED.name,
              "this device doesn't support BLE",
              null
            )
          } else {
            Log.i(Constants.LOG_TAG, "checking if BLE is active")

            if (adapter!!.isEnabled) {
              Log.i(Constants.LOG_TAG, "BLE is active");
              promise.resolve();
            } else {
              Log.i(Constants.LOG_TAG, "asking user to turn BLE on");

              val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
              appContext.currentActivity!!.startActivityForResult(
                enableBtIntent,
                REQUEST_ENABLE_BT
              )
              bleActivationPromise = promise
            }
          }
        }
      }
    }

    OnActivityResult { activity, onActivityResultPayload ->
      val activationPromise = bleActivationPromise
      if (onActivityResultPayload.requestCode == REQUEST_ENABLE_BT && activationPromise != null) {
        if (onActivityResultPayload.resultCode == Activity.RESULT_OK) {
          activationPromise.resolve();
        } else {
          activationPromise.reject(
            BleError.ERROR_BLE_NOT_ENABLED.name,
            "User denied to activate BLE",
            null
          )
        }
      }
    }

    AsyncFunction("disposeModule") { promise: Promise ->
      Log.d(Constants.LOG_TAG, "disposeModule()")

      executor.flush(BleError.ERROR_NOT_INITIALIZED, "module disposed")

      if (context.gatt != null) {
        context.gatt!!.close()
        context.gatt = null
        gattCallback = null
      }

      if (scanner != null) {
        scanner!!.stopScan(scanCallback)
        scanner = null
        scanCallback = null
      }

      adapter = null
      manager = null

      Log.i(Constants.LOG_TAG, "module disposed correctly :)")
      promise.resolve(null)
    }

    AsyncFunction("cancel") { transactionId: String, promise: Promise ->
      Log.d(Constants.LOG_TAG, String.format("cancel(%s)", transactionId))

      executor.cancel(transactionId)

      promise.resolve(null)
    }

    AsyncFunction("scanStart") { filterUuid: MutableList<String?>?, promise: Promise ->
      Log.d(Constants.LOG_TAG, String.format("scanStart(%s)", filterUuid))

      if (adapter == null) {
        promise.reject(BleError.ERROR_NOT_INITIALIZED.name, "module is not initialized", null)
      } else {
        val isFilteringSupported = adapter!!.isOffloadedFilteringSupported()

        Log.i(
          Constants.LOG_TAG,
          String.format("starting scan filter supported %b", isFilteringSupported)
        )

        var filters: MutableList<ScanFilter?>? = null
        val settings = ScanSettings.Builder()

        settings.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        settings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        settings.setReportDelay(0)
        if (isFilteringSupported && filterUuid != null && !filterUuid.isEmpty()) {
          settings.setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.CALLBACK_TYPE_MATCH_LOST)

          filters = ArrayList()
          for (i in filterUuid.indices) {
            val serviceUuid = filterUuid[i]
            Log.d(Constants.LOG_TAG, "adding filter UUID: $serviceUuid")
            val uuid = ParcelUuid.fromString(serviceUuid)
            filters.add(ScanFilter.Builder().setServiceUuid(uuid).build())
          }
        } else {
          settings.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        }

        if (scanner == null) {
          scanner = adapter!!.getBluetoothLeScanner()
          scanCallback = BleScanCallback({ event, payload -> sendEvent(event, payload) })
        } else {
          // stopping existing scan to restart it
          try {
            scanner!!.stopScan(scanCallback)
          } catch (e: Exception) {
            Log.w(
              Constants.LOG_TAG,
              "error stopping scan: " + e.message + ". Ignoring and continuing anyway"
            )
          }
        }

        try {
          scanner!!.startScan(filters, settings.build(), scanCallback)

          promise.resolve(null)
        } catch (e: Exception) {
          Log.e(Constants.LOG_TAG, "error starting scan: " + e.message)
          promise.reject(BleError.ERROR_SCAN.name, "error starting scan: " + e.message, e)
        }
      }
    }

    AsyncFunction("scanStop") { promise: Promise ->
      Log.d(Constants.LOG_TAG, "scanStop()")

      if (scanner != null) {
        try {
          scanner!!.stopScan(scanCallback)
        } catch (e: Exception) {
          Log.w(Constants.LOG_TAG, "error stopping scan, error: " + e.message)
          // ignore error here, this is expected to fail in case BLE is turned off
        }
        scanner = null
        scanCallback = null
      }

      promise.resolve(null)
    }

    AsyncFunction("connect") { id: String, mtu: Double, promise: Promise ->
      Log.d(Constants.LOG_TAG, String.format("connect(%s, %f)", id, mtu))

      val manager = RNBleManager()
      manager.connect(adapter!!.getRemoteDevice(id))
        .retry(3)
        .timeout(15_000)
        .useAutoConnect(false)
        .done { promise.resolve() }
        .fail { device, status -> promise.reject(BleError.ERROR_NOT_CONNECTED.name, "error connecting to device status: $status", null) }
        .enqueue()

      try {
        // ensure scan is not active (can create problems on some devices)
        if (scanner != null) {
          Log.i(Constants.LOG_TAG, "stopping BLE scan")

          try {
            scanner!!.stopScan(scanCallback)
            scanner = null
            scanCallback = null
          } catch (e: Exception) {
            Log.w(Constants.LOG_TAG, "failed stopping scan: $e")
          }
        }

        // the documentation says that we must do this
        adapter!!.cancelDiscovery()

        if (context.gatt != null) {
          Log.i(Constants.LOG_TAG, "closing existing GATT instance")

          context.gatt!!.close()
          context.gatt = null
        }

        // ensure transaction queue is empty
        executor.flush(BleError.ERROR_NOT_CONNECTED, "a new connection is starting")

        val device: BluetoothDevice
        try {
          device = adapter!!.getRemoteDevice(id)
        } catch (e: Exception) {
          Log.e(Constants.LOG_TAG, "cannot find device with address $id")

          promise.reject(
            BleError.ERROR_DEVICE_NOT_FOUND.name,
            "the specified device was not found",
            null
          )
          return@AsyncFunction
        }

        Log.d(Constants.LOG_TAG, "starting GATT connection")
        context.mtu = mtu.toInt()
        gattCallback = BleBluetoothGattCallback(
          { event, payload -> sendEvent(event, payload) },
          context,
          executor
        )

        // Must specify BluetoothDevice.TRANSPORT_LE otherwise this is not working on
        // certain phones
        context.gatt = device.connectGatt(
          appContext.reactContext, false, gattCallback,
          BluetoothDevice.TRANSPORT_LE
        )
        if (context.gatt == null) {
          promise.reject(BleError.ERROR_GATT.name, "gatt instance is null", null)
        }

        // signals that the connection request is taking progress
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject(BleError.ERROR_GATT.name, "unhandled exception: " + e.message, e)
        Log.e(Constants.LOG_TAG, "unhandled exception: " + e.message)
      }
    }

    AsyncFunction("disconnect") { promise: Promise ->
      Log.d(Constants.LOG_TAG, "disconnect()")

      executor.flush(BleError.ERROR_NOT_CONNECTED, "disconnecting device")

      if (context.gatt != null) {
        try {
          context.gatt!!.disconnect()
        } catch (e: Exception) {
          Log.w(
            Constants.LOG_TAG,
            "disconnect failed. Continuing anyway, error: ${e.message}"
          )
        }
        try {
          context.gatt!!.close()
        } catch (e: Exception) {
          Log.w(
            Constants.LOG_TAG,
            "gatt close failed. Continuing anyway, error: ${e.message}"
          )
        }
        context.gatt = null
      }

      promise.resolve(null)
    }

    AsyncFunction("readRSSI") { transactionId: String, promise: Promise ->
      Log.d(Constants.LOG_TAG, "readRSSI()")

      executor.add(TransactionReadRssi(transactionId, promise, context.gatt!!))
    }

    AsyncFunction("read") { transactionId: String,
                            service: String,
                            characteristic: String,
                            size: Double,
                            promise: Promise
      ->
      Log.d(Constants.LOG_TAG, String.format("read(%s, %s, %f)", service, characteristic, size))

      val gatt = context.gatt
      if (gatt == null) {
        promise.reject(BleError.ERROR_NOT_CONNECTED.name, "GATT instance is NULL", null);
        return@AsyncFunction
      }

      executor.add(
        TransactionReadChar(
          transactionId,
          promise,
          gatt,
          service,
          characteristic,
          size.toInt()
        ) { event, payload -> sendEvent(event, payload) },
      )
    }

    AsyncFunction("write") { transactionId: String,
                             service: String,
                             characteristic: String,
                             value: String,
                             chunkSize: Int,
                             promise: Promise
      ->
      Log.d(
        Constants.LOG_TAG,
        String.format("write(%s, %s, %s, %f)", service, characteristic, value, chunkSize)
      )

      val gatt = context.gatt
      if (gatt == null) {
        promise.reject(BleError.ERROR_NOT_CONNECTED.name, "GATT client is null", null)
        return@AsyncFunction
      }

      val data = Base64.decode(value, Base64.DEFAULT)

      executor.add(
        TransactionWriteChar(
          transactionId, promise, gatt, service, characteristic, data,
          chunkSize
        ) { event, payload -> sendEvent(event, payload) }
      )
    }

    AsyncFunction("subscribe") { transactionId: String,
                                 serviceUuid: String,
                                 characteristicUuid: String,
                                 promise: Promise
      ->
      Log.d(
        Constants.LOG_TAG,
        String.format("subscribe(%s, %s, %s)", transactionId, serviceUuid, characteristicUuid)
      )

      setNotificationEnabled(promise, serviceUuid, characteristicUuid, true)
    }

    AsyncFunction("unsubscribe") { transactionId: String,
                                   serviceUuid: String,
                                   characteristicUuid: String,
                                   promise: Promise
      ->
      Log.d(
        Constants.LOG_TAG,
        String.format("unsubscribe(%s, %s, %s)", transactionId, serviceUuid, characteristicUuid)
      )

      setNotificationEnabled(promise, serviceUuid, characteristicUuid, false)
    }
  }

  @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
  private fun setNotificationEnabled(
    promise: Promise,
    serviceUuid: String,
    characteristicUuid: String,
    enabled: Boolean
  ) {
    try {
      val gatt = context.gatt

      if (gatt == null) {
        promise.reject(BleError.ERROR_NOT_CONNECTED.name, "GATT instance is null", null)
      } else {
        val characteristic = GattTransaction.getCharacteristic(
          gatt, serviceUuid,
          characteristicUuid
        )
        if (characteristic == null) {
          promise.reject(
            BleError.ERROR_INVALID_ARGUMENTS.name,
            "characteristic is not found",
            null
          )
        } else {
          val success = gatt.setCharacteristicNotification(characteristic, enabled)
          if (success) {
            promise.resolve(null)
          } else {
            promise.reject(
              BleError.ERROR_GATT.name,
              "error setting characteristic notification",
              null
            )
          }
        }
      }
    } catch (e: Exception) {
      Log.e(Constants.LOG_TAG, "unhandled exception: " + e.message)
      promise.reject(BleError.ERROR_GENERIC.name, "unhandled exception: " + e.message, e)
    }
  }
}

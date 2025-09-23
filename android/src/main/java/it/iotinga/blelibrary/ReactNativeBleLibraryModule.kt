package it.iotinga.blelibrary

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition


class ReactNativeBleLibraryModule : Module() {
  private var manager: RNBleManager? = null
  private var adapter: BluetoothAdapter? = null
  private var scanner: RNBleScanner? = null
  private var bleActivationPromise: Promise? = null

  companion object {
    const val REQUEST_ENABLE_BT = 1
  }

  private fun onBleReady() {
    val bleManager =
      appContext.reactContext!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    adapter = bleManager.adapter
    manager =
      RNBleManager(appContext.reactContext!!) { event, payload -> sendEvent(event.value, payload) }
    scanner = RNBleScanner(bleManager.adapter) { event, payload -> sendEvent(event.value, payload) }
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
      Event.ERROR.value,
      Event.PROGRESS.value,
      Event.SCAN_RESULT.value,
      Event.CHAR_VALUE_CHANGED.value,
      Event.CONNECTION_STATE_CHANGED.value
    )

    AsyncFunction("initModule") { promise: Promise ->
      Log.d(LOG_TAG, "initModule()")

      Log.i(LOG_TAG, "checking permissions")

      val permissionManager =
        BlePermissionsManager(appContext.reactContext!!, appContext.permissions!!)
      permissionManager.ensure { granted: Boolean ->
        if (!granted) {
          Log.w(LOG_TAG, "permission denied")

          promise.reject(BleError.ERROR_MISSING_PERMISSIONS.name, "missing BLE permissions", null)
        } else {
          Log.i(LOG_TAG, "permission granted")

          val manager =
            appContext.reactContext!!.getSystemService(
              Context.BLUETOOTH_SERVICE
            ) as BluetoothManager?
          adapter = manager?.adapter
          if (adapter == null) {
            Log.w(LOG_TAG, "this device doesn't have a BLE adapter")

            promise.reject(
              BleError.ERROR_BLE_NOT_SUPPORTED.name,
              "this device doesn't support BLE",
              null
            )
          } else {
            Log.i(LOG_TAG, "checking if BLE is active")

            if (adapter!!.isEnabled) {
              Log.i(LOG_TAG, "BLE is active");
              onBleReady()
              promise.resolve();
            } else {
              Log.i(LOG_TAG, "asking user to turn BLE on");

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

    // Catches the result of an activity
    // Here it's used to get the result of the activate BLE activity
    OnActivityResult { activity, onActivityResultPayload ->
      val activationPromise = bleActivationPromise
      if (onActivityResultPayload.requestCode == REQUEST_ENABLE_BT && activationPromise != null) {
        if (onActivityResultPayload.resultCode == Activity.RESULT_OK) {
          onBleReady()
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
      Log.d(LOG_TAG, "disposeModule()")

      scanner?.stop()
      scanner = null

      val managerCopy = manager
      if (managerCopy != null) {
        managerCopy.dispose(promise)
        manager = null
      } else {
        promise.resolve()
      }
    }

    AsyncFunction("cancel") { transactionId: String, promise: Promise ->
      Log.d(LOG_TAG, "cancel($transactionId)")

      ensureManagerInitialized(promise) { manager ->
        manager.cancel(transactionId)
      }

      promise.resolve(null)
    }

    AsyncFunction("scanStart") { filterUuid: MutableList<String>?, promise: Promise ->
      Log.d(LOG_TAG, "scanStart($filterUuid)")

      val scanner = scanner
      if (scanner == null) {
        promise.reject(BleError.ERROR_NOT_INITIALIZED.name, "module is not initialized", null)
      } else {
        try {
          scanner.start(filterUuid)
          promise.resolve()
        } catch (e: Exception) {
          Log.e(LOG_TAG, "error starting scan: ${e.message}")
          promise.reject(BleError.ERROR_SCAN.name, "error starting scan: ${e.message}", e)
        }
      }
    }

    AsyncFunction("scanStop") { promise: Promise ->
      Log.d(LOG_TAG, "scanStop()")
      try {
        scanner?.stop()
      } catch (e: Exception) {
        Log.w(LOG_TAG, "error stopping scan, error: " + e.message)
        // ignore error here, this is expected to fail in case BLE is turned off
      }

      promise.resolve()
    }

    AsyncFunction("connect")
    { id: String, mtu: Int, promise: Promise ->
      Log.d(LOG_TAG, "connect($id, $mtu)")

      val isAddressValid = BluetoothAdapter.checkBluetoothAddress(id)
      if (!isAddressValid) {
        promise.reject(
          BleError.ERROR_INVALID_ARGUMENTS.name,
          "BLE address not in valid format",
          null
        )
        return@AsyncFunction
      }

      val adapter = adapter
      if (adapter == null) {
        promise.reject(BleError.ERROR_NOT_INITIALIZED.name, "Manager is not initialized", null)
        return@AsyncFunction
      }

      scanner?.stop()

      ensureManagerInitialized(promise) { manager ->
        manager.connect(adapter.getRemoteDevice(id), mtu, promise)
      }
    }

    AsyncFunction("disconnect")
    { promise: Promise ->
      Log.d(LOG_TAG, "disconnect()")

      ensureManagerInitialized(promise) { manager ->
        manager.disconnect()
      }
    }

    AsyncFunction("readRSSI")
    { transactionId: String, promise: Promise ->
      Log.d(LOG_TAG, "readRSSI()")

      ensureManagerInitialized(promise) { manager ->
        manager.getRSSI(promise)
      }
    }

    AsyncFunction("read")
    { transactionId: String,
      service: String,
      characteristic: String,
      size: Int,
      promise: Promise
      ->
      Log.d(LOG_TAG, "read($service, $characteristic, $size)")

      ensureManagerInitialized(promise) { manager ->
        manager.readChar(transactionId, service, characteristic, size, promise)
      }
    }

    AsyncFunction("write")
    { transactionId: String,
      service: String,
      characteristic: String,
      value: String,
      chunkSize: Int,
      promise: Promise
      ->
      Log.d(
        LOG_TAG,
        "write($service, $characteristic, ${value.length}, $chunkSize)"
      )

      ensureManagerInitialized(promise) { manager ->
        val data = Base64.decode(value, Base64.DEFAULT)
        manager.writeChar(transactionId, service, characteristic, data, chunkSize, promise)
      }
    }

    AsyncFunction("subscribe")
    { transactionId: String,
      serviceUuid: String,
      characteristicUuid: String,
      promise: Promise
      ->
      Log.d(
        LOG_TAG,
        "subscribe($transactionId, $serviceUuid, $characteristicUuid"
      )

      ensureManagerInitialized(promise) { manager ->
        manager.enableNotification(transactionId, serviceUuid, characteristicUuid, promise)
      }
    }

    AsyncFunction("unsubscribe")
    { transactionId: String,
      serviceUuid: String,
      characteristicUuid: String,
      promise: Promise
      ->
      Log.d(
        LOG_TAG,
        "unsubscribe($transactionId, $serviceUuid, $characteristicUuid)"
      )

      ensureManagerInitialized(promise) { manager ->
        manager.disableNotification(transactionId, serviceUuid, characteristicUuid, promise)
      }
    }
  }

  private fun ensureManagerInitialized(
    promise: Promise,
    callback: (manager: RNBleManager) -> Unit
  ) {
    val manager = manager
    if (manager == null) {
      promise.reject(BleError.ERROR_NOT_INITIALIZED.name, "BLE not initialized", null)
    } else {
      callback(manager)
    }
  }
}

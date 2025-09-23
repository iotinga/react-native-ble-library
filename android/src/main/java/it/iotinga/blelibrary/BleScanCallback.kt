package it.iotinga.blelibrary

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission

class BleScanCallback internal constructor(private val sendEvent: (event: Event, payload: Map<String, Any>) -> Unit) :
  ScanCallback() {
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  override fun onScanResult(callbackType: Int, result: ScanResult) {
    Log.i(
      LOG_TAG,
      String.format("got scan result: %s (callback type: %d)", result, callbackType)
    )

    val available = callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH
      || callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES

    sendScanResult(listOf(result), available)
  }

  override fun onScanFailed(errorCode: Int) {
    Log.e(LOG_TAG, "SCAN FAILED, error = $errorCode")

    sendEvent(
      Event.ERROR, mapOf(
        "code" to BleError.ERROR_SCAN.name,
        "message" to "scan error, native error code $errorCode",
      )
    )
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  override fun onBatchScanResults(results: MutableList<ScanResult?>) {
    Log.i(LOG_TAG, "got batch result: $results")

    sendScanResult(results, true)
  }

  private fun sendScanResult(items: List<ScanResult?>, isAvailable: Boolean) {
    sendEvent(
      Event.SCAN_RESULT, mapOf(
      "devices" to items.filterNotNull().map { item ->
        mapOf(
          "rssi" to item.rssi,
          "isAvailable" to isAvailable,
          "id" to item.device.address,
          "isConnectable" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) item.isConnectable else true,
          "txPower" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) item.txPower else 0,
          "name" to (item.scanRecord?.deviceName ?: "")
        )
      }
    ))
  }
}


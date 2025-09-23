package it.iotinga.blelibrary

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission

class RNBleScanner(
  private val adapter: BluetoothAdapter,
  sendEvent: (event: Event, payload: Map<String, Any>) -> Unit
) {
  private val scanCallback = BleScanCallback(sendEvent)
  private val scanner = adapter.bluetoothLeScanner
  private var isActive = false

  @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
  fun start(filterUuid: List<String>?) {
    val isFilteringSupported = adapter.isOffloadedFilteringSupported

    Log.i(
      LOG_TAG,
      "starting scan filter supported $isFilteringSupported"
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
        Log.d(LOG_TAG, "adding filter UUID: $serviceUuid")
        val uuid = ParcelUuid.fromString(serviceUuid)
        filters.add(ScanFilter.Builder().setServiceUuid(uuid).build())
      }
    } else {
      settings.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
    }

    if (isActive) {
      scanner.stopScan(scanCallback)
    }

    scanner.startScan(filters, settings.build(), scanCallback)
    isActive = true
  }

  @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
  fun stop() {
    if (isActive) {
      scanner.stopScan(scanCallback)
      adapter.cancelDiscovery()
    }
  }
}

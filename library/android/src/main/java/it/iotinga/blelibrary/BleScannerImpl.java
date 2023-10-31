package it.iotinga.blelibrary;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.List;

public class BleScannerImpl implements BleScanner {
  private static final String TAG = "BleScannerImpl";
  private static final long SCAN_DELAY_MS = 1000;
  private final BluetoothLeScanner scanner;
  private final ScanCallback callback;
  private boolean isScanActive;

  public BleScannerImpl(BluetoothLeScanner scanner, ScanCallback callback) {
    this.scanner = scanner;
    this.callback = callback;
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
  public void start(@NonNull List<String> filter) {
    if (!isScanActive) {
      Log.i(TAG, "starting scan");

      List<ScanFilter> filters = null;
      ScanSettings.Builder settings = new ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);

      boolean hasScanFilter = filter.size() > 0;
      if (hasScanFilter) {
        settings.setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH | ScanSettings.CALLBACK_TYPE_MATCH_LOST);
        filters = new ArrayList<>();
        for (String serviceUuid : filter) {
          Log.d(TAG, "adding filter UUID: " + serviceUuid);
          ParcelUuid uuid = ParcelUuid.fromString(serviceUuid);
          filters.add(new ScanFilter.Builder().setServiceUuid(uuid).build());
        }
      } else {
        // avoid flooding JS with events
        settings.setReportDelay(SCAN_DELAY_MS);
        settings.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
      }

      scanner.startScan(filters, settings.build(), callback);
      isScanActive = true;
    } else {
      Log.w(TAG, "scan is already started");
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
  public void stop() {
    if (isScanActive) {
      Log.i(TAG, "stopping scan");
      scanner.stopScan(callback);
      isScanActive = false;
    } else {
      Log.w(TAG, "scan is already stopped");
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
  public void dispose() {
    scanner.stopScan(callback);
    isScanActive = false;
  }
}

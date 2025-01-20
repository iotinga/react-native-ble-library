package it.iotinga.blelibrary;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.List;

public class BleScanCallback extends ScanCallback {
  private static final String TAG = "BleScanCallback";
  private final EventEmitter eventEmitter;
  private final List<ParcelUuid> filter;

  BleScanCallback(EventEmitter eventEmitter, List<ParcelUuid> filter) {
    super();
    this.eventEmitter = eventEmitter;
    this.filter = filter;
  }

  private boolean resultPassesUuidFilter(ScanResult result) {
    if (filter == null || filter.isEmpty()) {
      return true;
    }

    ScanRecord record = result.getScanRecord();
    if (record == null) {
      return false;
    }

    List<ParcelUuid> uuids = record.getServiceUuids();
    if (uuids == null) {
      return false;
    }
    for (ParcelUuid uuid : uuids) {
      if (uuid == null) {
        continue;
      }
      for (ParcelUuid allowedUuid : filter) {
        if (uuid.equals(allowedUuid)) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onScanResult(int callbackType, ScanResult result) {
    Log.i(TAG, String.format("got scan result: %s (callback type: %d)", result, callbackType));

    if (resultPassesUuidFilter(result)) {
      boolean available = callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH
        || callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES;

      RNEventScanResult event = new RNEventScanResult();
      event.add(result, available);

      eventEmitter.emit(event);
    }
  }

  @Override
  public void onScanFailed(int errorCode) {
    Log.e(TAG, "SCAN FAILED, error = " + errorCode);

    eventEmitter.emit(new RNEventError(BleError.ERROR_SCAN, "scan error, native error code " + errorCode));
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onBatchScanResults(List<ScanResult> results) {
    Log.i(TAG, "got batch result: " + results);

    RNEventScanResult event = new RNEventScanResult();
    for (ScanResult result : results) {
      if (resultPassesUuidFilter(result)) {
        event.add(result, true);
      }
    }

    eventEmitter.emit(event);
  }
}


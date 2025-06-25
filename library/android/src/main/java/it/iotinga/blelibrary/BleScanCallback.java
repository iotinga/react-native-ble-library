package it.iotinga.blelibrary;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.List;

public class BleScanCallback extends ScanCallback {
  private final EventEmitter eventEmitter;

  BleScanCallback(EventEmitter eventEmitter) {
    super();
    this.eventEmitter = eventEmitter;
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onScanResult(int callbackType, ScanResult result) {
    Log.i(Constants.LOG_TAG, String.format("got scan result: %s (callback type: %d)", result, callbackType));

    boolean available = callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH
      || callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES;

    RNEventScanResult event = new RNEventScanResult();
    event.add(result, available);

    eventEmitter.emit(event);
  }

  @Override
  public void onScanFailed(int errorCode) {
    Log.e(Constants.LOG_TAG, "SCAN FAILED, error = " + errorCode);

    eventEmitter.emit(new RNEventError(BleError.ERROR_SCAN, "scan error, native error code " + errorCode));
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onBatchScanResults(List<ScanResult> results) {
    Log.i(Constants.LOG_TAG, "got batch result: " + results);

    RNEventScanResult event = new RNEventScanResult();
    for (ScanResult result : results) {
      event.add(result, true);
    }

    eventEmitter.emit(event);
  }
}


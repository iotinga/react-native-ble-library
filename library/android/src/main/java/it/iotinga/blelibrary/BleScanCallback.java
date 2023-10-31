package it.iotinga.blelibrary;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.List;

public class BleScanCallback extends ScanCallback {
  private static final String TAG = "BleScanCallback";
  private final EventEmitter eventEmitter;

  BleScanCallback(EventEmitter eventEmitter) {
    super();
    this.eventEmitter = eventEmitter;
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  private WritableMap scanResultToWritableMap(ScanResult result, boolean available) {

    BluetoothDevice device = result.getDevice();

    WritableMap deviceInfo = Arguments.createMap();
    deviceInfo.putString("name", device.getName());
    deviceInfo.putString("id", device.getAddress());
    deviceInfo.putInt("rssi", result.getRssi());
    deviceInfo.putBoolean("available", available);

    return deviceInfo;
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onScanResult(int callbackType, ScanResult result) {
    Log.i(TAG, String.format("got scan result: %s (callback type: %d)", result, callbackType));

    boolean available = callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH
      || callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
    WritableArray devices = Arguments.createArray();
    devices.pushMap(scanResultToWritableMap(result, available));

    WritableMap event = Arguments.createMap();
    event.putArray("devices", devices);
    eventEmitter.emit(EventEmitter.EVENT_SCAN_RESULT, event);
  }

  @Override
  public void onScanFailed(int errorCode) {
    Log.e(TAG, "SCAN FAILED, error = " + errorCode);
    eventEmitter.emitError(BleLibraryModule.ERROR_SCAN, "scan error, native error code " + errorCode);
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onBatchScanResults(List<ScanResult> results) {
    Log.i(TAG, "got batch result: " + results);

    WritableArray devices = Arguments.createArray();
    for (ScanResult result : results) {
      devices.pushMap(scanResultToWritableMap(result, true));
    }

    WritableMap event = Arguments.createMap();
    event.putArray("devices", devices);
    eventEmitter.emit(EventEmitter.EVENT_SCAN_RESULT, event);
  }
}


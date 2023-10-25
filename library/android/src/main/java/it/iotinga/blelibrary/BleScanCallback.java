package it.iotinga.blelibrary;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
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
  private WritableMap scanResultToWritableMap(ScanResult result) {
    BluetoothDevice device = result.getDevice();

    WritableMap deviceInfo = Arguments.createMap();
    deviceInfo.putString("name", device.getName());
    deviceInfo.putString("id", device.getAddress());
    deviceInfo.putInt("rssi", result.getRssi());

    return deviceInfo;
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  @Override
  public void onScanResult(int callbackType, ScanResult result) {
    Log.i(TAG, "got scan result: " + result);

    WritableArray devices = Arguments.createArray();
    devices.pushMap(scanResultToWritableMap(result));

    WritableMap event = Arguments.createMap();
    event.putArray("devices", devices);
    eventEmitter.emit(EventType.SCAN_RESULT, event);
  }

  @Override
  public void onScanFailed(int errorCode) {
    Log.e(TAG, "SCAN FAILED, error = " + errorCode);
    eventEmitter.emitError(ErrorType.SCAN_ERROR, "scan error, native error code " + errorCode);
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  @Override
  public void onBatchScanResults(List<ScanResult> results) {
    Log.i(TAG, "got batch result: " + results);

    WritableArray devices = Arguments.createArray();
    for (ScanResult result: results) {
      devices.pushMap(scanResultToWritableMap(result));
    }

    WritableMap event = Arguments.createMap();
    event.putArray("devices", devices);
    eventEmitter.emit(EventType.SCAN_RESULT, event);
  }
}


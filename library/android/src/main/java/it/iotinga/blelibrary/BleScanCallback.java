package it.iotinga.blelibrary;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

public class BleScanCallback extends ScanCallback {
  private static final String TAG = "BleScanCallback";
  private final EventEmitter eventEmitter;

  BleScanCallback(EventEmitter eventEmitter) {
    super();
    this.eventEmitter = eventEmitter;
  }

  @SuppressLint("MissingPermission")
  @Override
  public void onScanResult(int callbackType, ScanResult result) {
    Log.i(TAG, "got scan result: " + result.toString());

    BluetoothDevice device = result.getDevice();

    // send scan result event
    WritableMap event = Arguments.createMap();
    event.putString("name", device.getName());
    event.putString("id", device.getAddress());
    event.putInt("rssi", result.getRssi());
    eventEmitter.emit(EventType.SCAN_RESULT, event);
  }

  @Override
  public void onScanFailed(int errorCode) {
    Log.e(TAG, "SCAN FAILED, error = " + errorCode);
    eventEmitter.emitError(ErrorType.SCAN_ERROR, String.format("scan error, native error code %d", errorCode));
  }
}


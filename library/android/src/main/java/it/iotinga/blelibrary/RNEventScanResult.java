package it.iotinga.blelibrary;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

public class RNEventScanResult implements RNEvent {
  private final WritableMap payload;
  private final WritableArray devices;

  RNEventScanResult() {
    devices = Arguments.createArray();

    payload = Arguments.createMap();
    payload.putArray("devices", devices);
  }

  public void add(ScanResult result, boolean isAvailable) {
    WritableMap deviceInfo = Arguments.createMap();
    deviceInfo.putInt("rssi", result.getRssi());
    deviceInfo.putBoolean("available", isAvailable);

    BluetoothDevice device = result.getDevice();
    deviceInfo.putString("id", device.getAddress());

    ScanRecord record = result.getScanRecord();
    if (record != null) {
      deviceInfo.putString("name", record.getDeviceName());
    } else {
      deviceInfo.putString("name", "");
    }

    devices.pushMap(deviceInfo);
  }

  @Override
  public String name() {
    return "SCAN_RESULT";
  }

  @Nullable
  @Override
  public Object payload() {
    return payload;
  }
}

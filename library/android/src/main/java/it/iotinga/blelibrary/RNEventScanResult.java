package it.iotinga.blelibrary;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.ArrayList;
import java.util.List;

public class RNEventScanResult implements RNEvent {
  private static class ScanResultItem {
    public final ScanResult result;
    public final boolean isAvailable;

    private ScanResultItem(ScanResult result, boolean isAvailable) {
      this.result = result;
      this.isAvailable = isAvailable;
    }
  }

  private final List<ScanResultItem> resultItems = new ArrayList<>();

  public void add(ScanResult result, boolean isAvailable) {
    resultItems.add(new ScanResultItem(result, isAvailable));
  }

  @Override
  public String name() {
    return "SCAN_RESULT";
  }

  @Nullable
  @Override
  public Object payload() {
    WritableArray devices = Arguments.createArray();
    for (ScanResultItem item : resultItems) {
      WritableMap deviceInfo = Arguments.createMap();
      deviceInfo.putInt("rssi", item.result.getRssi());
      deviceInfo.putBoolean("isAvailable", item.isAvailable);

      BluetoothDevice device = item.result.getDevice();
      deviceInfo.putString("id", device.getAddress());
      deviceInfo.putBoolean("isConnectable", item.result.isConnectable());
      deviceInfo.putInt("txPower", item.result.getTxPower());

      ScanRecord record = item.result.getScanRecord();
      if (record != null) {
        deviceInfo.putString("name", record.getDeviceName());
      } else {
        deviceInfo.putString("name", "");
      }

      devices.pushMap(deviceInfo);
    }

    WritableMap payload = Arguments.createMap();
    payload.putArray("devices", devices);

    return payload;
  }
}

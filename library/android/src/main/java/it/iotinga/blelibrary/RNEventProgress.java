package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

public class RNEventProgress implements RNEvent {
  private final WritableMap payload;

  RNEventProgress(String transactionId, BluetoothGattCharacteristic characteristic, int current, int total) {
    payload = Arguments.createMap();
    payload.putString("transactionId", transactionId);
    payload.putString("service", characteristic.getService().getUuid().toString());
    payload.putString("characteristic", characteristic.getUuid().toString());
    payload.putInt("total", total);
    payload.putInt("current", current);
  }

  @Override
  public String name() {
    return "PROGRESS";
  }

  @Nullable
  @Override
  public Object payload() {
    return payload;
  }
}

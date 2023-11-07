package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import java.util.Base64;

public class RNEventCharacteristicChanged implements RNEvent {
  private final Base64.Encoder b64Encoder = Base64.getEncoder();

  private final WritableMap payload;

  RNEventCharacteristicChanged(BluetoothGattCharacteristic characteristic, byte[] value) {
    payload = Arguments.createMap();
    payload.putString("service", characteristic.getService().getUuid().toString());
    payload.putString("characteristic", characteristic.getUuid().toString());
    payload.putString("value", b64Encoder.encodeToString(value));
  }

  @Override
  public String name() {
    return "CHAR_VALUE_CHANGED";
  }

  @Nullable
  @Override
  public Object payload() {
    return payload;
  }
}

package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.List;

public class RNEventServiceDiscovered implements RNEvent {
  private final WritableMap payload;

  RNEventServiceDiscovered(List<BluetoothGattService> bleServices) {
    WritableArray services = Arguments.createArray();
    for (BluetoothGattService service : bleServices) {
      WritableArray characteristics = Arguments.createArray();
      for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
        WritableMap data = Arguments.createMap();
        data.putString("uuid", characteristic.getUuid().toString().toLowerCase());
        data.putInt("properties", characteristic.getProperties());
        characteristics.pushMap(data);
      }

      WritableMap data = Arguments.createMap();
      data.putString("uuid", service.getUuid().toString().toLowerCase());
      data.putBoolean("isPrimary", service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY);
      data.putArray("characteristics", characteristics);
      services.pushMap(data);
    }

    payload = Arguments.createMap();
    payload.putArray("services", services);
  }

  @Override
  public String name() {
    return "SERVICE_DISCOVERED";
  }

  @Nullable
  @Override
  public Object payload() {
    return payload;
  }
}

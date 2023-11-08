package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.List;

public class RNEventConnectionStateChanged implements RNEvent {
  public static final int SUCCESS = 0;
  public static final int FAILURE = -1;
  private final WritableMap payload;

  RNEventConnectionStateChanged(ConnectionState state, int gattStatus, @Nullable List<BluetoothGattService> bleServices) {
    payload = Arguments.createMap();
    payload.putString("state", state.name());
    payload.putString("message", "connection state changed (status: " + gattStatus + ")");
    payload.putString("error", gattStatus != SUCCESS ? BleError.ERROR_GATT.name() : null);

    WritableMap androidPayload = Arguments.createMap();
    androidPayload.putInt("status", gattStatus);

    if (bleServices != null) {
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
      payload.putArray("services", services);
    }

    payload.putMap("android", androidPayload);
  }

  RNEventConnectionStateChanged(ConnectionState state, int gattStatus) {
    this(state, gattStatus, null);
  }

  @Override
  public String name() {
    return "CONNECTION_STATE_CHANGED";
  }

  @Nullable
  @Override
  public Object payload() {
    return payload;
  }
}

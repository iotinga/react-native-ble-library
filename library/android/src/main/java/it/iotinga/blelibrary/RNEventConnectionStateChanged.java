package it.iotinga.blelibrary;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

public class RNEventConnectionStateChanged implements RNEvent {
  public static final int SUCCESS = 0;
  public static final int FAILURE = -1;
  private final WritableMap payload;

  RNEventConnectionStateChanged(ConnectionState state, int gattStatus) {
    payload = Arguments.createMap();
    payload.putString("state", state.name());
    payload.putString("message", "connection state changed (status: " + gattStatus + ")");
    payload.putString("error", gattStatus != SUCCESS ? BleError.ERROR_GATT.name() : null);

    WritableMap androidPayload = Arguments.createMap();
    androidPayload.putInt("status", gattStatus);

    payload.putMap("android", androidPayload);
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

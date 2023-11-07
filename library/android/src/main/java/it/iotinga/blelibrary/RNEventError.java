package it.iotinga.blelibrary;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

public class RNEventError implements RNEvent {
  private final WritableMap payload;

  RNEventError(BleError code, String message) {
    payload = Arguments.createMap();
    payload.putString("error", code.name());
    payload.putString("message", message);
  }

  @Override
  public String name() {
    return "ERROR";
  }

  @Nullable
  @Override
  public Object payload() {
    return payload;
  }
}

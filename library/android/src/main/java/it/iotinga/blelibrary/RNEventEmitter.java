package it.iotinga.blelibrary;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RNEventEmitter implements EventEmitter {
  private static final String TAG = "RNEventEmitter";
  private static final String EVENT_NAME = "BleLibraryEvent";
  private final ReactApplicationContext rnContext;

  RNEventEmitter(ReactApplicationContext rnContext) {
    this.rnContext = rnContext;
  }

  @Override
  public void emit(EventType event) {
    emit(event, Arguments.createMap());
  }

  @Override
  public void emit(EventType event, WritableMap payload) {
    payload.putString("type", event.toString());

    Log.d(TAG, "sending event: " + payload);

    rnContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(EVENT_NAME, payload);
  }

  @Override
  public void emitError(ErrorCode error, String message) {
    emitError(error, message, Arguments.createMap());
  }

  @Override
  public void emitError(ErrorCode error, String message, WritableMap details) {
    details.putString("error", error.toString());
    details.putString("message", message);

    emit(EventType.ERROR, details);
  }
}

package it.iotinga.blelibrary;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RNEventEmitter implements EventEmitter {
  private static final String TAG = "RNEventEmitter";
  private final ReactApplicationContext rnContext;

  RNEventEmitter(ReactApplicationContext rnContext) {
    this.rnContext = rnContext;
  }

  @Override
  public void emit(String type, WritableMap payload) {
    Log.d(TAG, String.format("sending event: %s payload %s", type, payload));

    rnContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(type, payload);
  }

  @Override
  public void emitError(String error, String message) {
    emitError(error, message, Arguments.createMap());
  }

  @Override
  public void emitError(String error, String message, WritableMap details) {
    details.putString("error", error);
    details.putString("message", message);

    emit(EventEmitter.EVENT_ERROR, details);
  }
}

package it.iotinga.blelibrary;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RNEventEmitter implements EventEmitter {
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

    rnContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(EVENT_NAME, payload);
  }

  @Override
  public void emitError(ErrorType error, String message) {
    WritableMap payload = Arguments.createMap();
    payload.putString("error", error.toString());
    payload.putString("message", message);

    emit(EventType.ERROR, payload);
  }
}

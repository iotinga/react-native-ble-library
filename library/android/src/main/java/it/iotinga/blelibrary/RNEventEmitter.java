package it.iotinga.blelibrary;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RNEventEmitter implements EventEmitter {
  private final ReactApplicationContext rnContext;

  RNEventEmitter(ReactApplicationContext rnContext) {
    this.rnContext = rnContext;
  }

  @Override
  public void emit(RNEvent event) {
    Log.d(Constants.LOG_TAG, String.format("sending event: %s payload %s", event.name(), event.payload()));

    rnContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(event.name(), event.payload());
  }
}

package it.iotinga.blelibrary;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

@ReactModule(name = BleLibraryModule.NAME)
public class BleLibraryModule extends ReactContextBaseJavaModule {
  static final String BleLibraryEventName = "BleLibraryEvent";

  static final String BleGenericError = "BleGenericError";
  static final String BleDeviceDisconnected = "BleDeviceDisconnected";
  static final String BleInvalidState = "BleInvalidState";

  static final String CmdTypePing = "ping";
  static final String CmdTypeScan = "scan";
  static final String CmdTypeStopScan = "stopScan";
  static final String CmdTypeConnect = "connect";
  static final String CmdTypeDisconnect = "disconnect";
  static final String CmdTypeWrite = "write";
  static final String CmdTypeRead = "read";
  static final String CmdTypeSubscribe = "subscribe";

  static final String CmdResponseTypePong = "pong";
  static final String CmdResponseTypeError = "error";
  static final String CmdResponseTypeScanResult = "scanResult";
  static final String CmdResponseTypeScanStopped = "scanStopped";
  static final String CmdResponseTypeScanStarted = "scanStarted";
  static final String CmdResponseTypeConnected = "connected";
  static final String CmdResponseTypeDisconnected = "disconnected";
  static final String CmdResponseTypeSubscribe = "subscribe";
  static final String CmdResponseTypeCharValueChanged = "charValueChanged";
  static final String CmdResponseTypeWriteCompleted = "writeCompleted";
  static final String CmdResponseTypeWriteProgress = "writeProgress";

  public static final String NAME = "BleLibrary";

  private int listenerCount = 0;

  public BleLibraryModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  private void sendEvent(@Nullable WritableMap params) {
    getReactApplicationContext()
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(BleLibraryEventName, params);
  }

  private void executeCommand(ReadableMap command) {
    String type = command.getString("type");
    if (type == null) {
      throw new RuntimeException("invalid arguments: missing command type");
    }

    Log.i(NAME, String.format("executing command %s", type));

    if (type.equals(CmdTypePing)) {
      WritableMap response = Arguments.createMap();
      response.putString("type", CmdResponseTypePong);
      sendEvent(response);
    }
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void sendCommands(ReadableArray commands, Promise promise) {
    Log.i(NAME, "received commands: " + commands.toString());

    for (int i = 0; i < commands.size(); i++) {
      ReadableMap command = commands.getMap(i);
      executeCommand(command);
    }

    promise.resolve(null);
  }

  @ReactMethod
  public void addListener(String eventName) {
    listenerCount += 1;
    Log.i(NAME, String.format("listener registered for event: %s num listeners: %d", eventName, listenerCount));
  }

  @ReactMethod
  public void removeListeners(Integer count) {
    listenerCount -= count;
    Log.i(NAME, String.format("%d listener removed, num listeners: %d", count, listenerCount));
  }
}

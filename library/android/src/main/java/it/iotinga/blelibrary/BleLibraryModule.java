package it.iotinga.blelibrary;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.module.annotations.ReactModule;

@ReactModule(name = BleLibraryModule.NAME)
public class BleLibraryModule extends ReactContextBaseJavaModule {
  public static final String NAME = "BleLibrary";

  private int listenerCount = 0;

  private final Dispatcher dispatcher;

  @RequiresApi(api = Build.VERSION_CODES.O)
  public BleLibraryModule(ReactApplicationContext reactContext) {
    super(reactContext);

    EventEmitter eventEmitter = new RNEventEmitter(reactContext);
    ConnectionContext connectionContext = new ConnectionContext();

    BluetoothManager bluetoothManager = (BluetoothManager) reactContext.getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
    BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

    ScanCallback scanCallback = new BleScanCallback(eventEmitter);
    BluetoothGattCallback gattCallback = new BleBluetoothGattCallback(eventEmitter, connectionContext);

    dispatcher = new CommandDispatcher();
    dispatcher.register("ping", new CommandPing(eventEmitter));
    dispatcher.register("scan", new CommandScan(bluetoothLeScanner, scanCallback));
    dispatcher.register("stopScan", new CommandScanStop(bluetoothLeScanner, scanCallback));
    dispatcher.register("connect", new CommandConnect(eventEmitter, reactContext, bluetoothAdapter, gattCallback, connectionContext));
    dispatcher.register("disconnect", new CommandDisconnect(connectionContext));
    dispatcher.register("write", new CommandWrite(eventEmitter, connectionContext));
    dispatcher.register("read", new CommandRead(eventEmitter, connectionContext));
    dispatcher.register("subscribe", new CommandSubscribe(eventEmitter, connectionContext));
    dispatcher.register("unsubscribe", new CommandUnsubscribe(eventEmitter, connectionContext));
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void sendCommands(ReadableArray commands, Promise promise) {
    Log.i(NAME, "received commands: " + commands.toString());

    try {
      for (int i = 0; i < commands.size(); i++) {
        dispatcher.dispatch(commands.getMap(i));
      }

      promise.resolve(null);
    } catch (Exception e) {
      Log.e(NAME, "command execution failed: " + e);
      promise.reject(ErrorType.GENERIC_ERROR.toString(), e.toString(), e);
    }
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

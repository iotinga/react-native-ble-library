package it.iotinga.blelibrary;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.module.annotations.ReactModule;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@ReactModule(name = BleLibraryModule.NAME)
public class BleLibraryModule extends ReactContextBaseJavaModule {
  public static final String NAME = "BleLibrary";

  private int listenerCount = 0;
  private final BleScanner scanner;
  private final BleGatt gatt;
  private final EventEmitter emitter;

  public BleLibraryModule(ReactApplicationContext reactContext) {
    super(reactContext);

    BluetoothManager bluetoothManager = (BluetoothManager) reactContext.getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
    BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

    emitter = new RNEventEmitter(reactContext);

    ScanCallback scanCallback = new BleScanCallback(emitter);
    ConnectionContext connectionContext = new ConnectionContext();
    BluetoothGattCallback gattCallback = new BleBluetoothGattCallback(emitter, connectionContext);

    scanner = new BleScannerImpl(bluetoothLeScanner, scanCallback);
    gatt = new BleGattImpl(bluetoothAdapter, gattCallback, connectionContext, emitter, reactContext);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void ping(Promise promise) {
    emitter.emit(EventType.PONG);
    promise.resolve(null);
  }

  @ReactMethod
  public void scanStart(ReadableArray filterUuid, Promise promise) {
    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      List<String> filter = new ArrayList<>();
      if (filterUuid != null) {
        for (int i = 0; i < filterUuid.size(); i++) {
          filter.add(filterUuid.getString(i));
        }
      }
      scanner.start(filter);
      operation.complete();
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void scanStop(Promise promise) {
    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      scanner.stop();
      operation.complete();
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void connect(String id, Double mtu, Promise promise) {
    Log.d(NAME, String.format("call connect(%s, %f)", id, mtu));

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.connect(operation, id, mtu.intValue());
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void disconnect(Promise promise) {
    Log.d(NAME, "call disconnect()");

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.disconnect(operation);
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void read(String service, String characteristic, Double size, Promise promise) {
    Log.d(NAME, String.format("call read(%s, %s, %f)", service, characteristic, size));

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.read(operation, service, characteristic, size.intValue());
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void write(String service, String characteristic, String value, Double chunkSize, Promise promise) {
    Log.d(NAME, String.format("call write(%s, %s, %s, %f)", service, characteristic, value, chunkSize));

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      byte[] data = Base64.getDecoder().decode(value);
      gatt.write(operation, service, characteristic, data, chunkSize.intValue());
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void subscribe(String service, String characteristic, Promise promise) {
    Log.d(NAME, String.format("call subscribe(%s, %s)", service, characteristic));

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.subscribe(service, characteristic);
      operation.complete();
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void unsubscribe(String service, String characteristic, Promise promise) {
    Log.d(NAME, String.format("call unsubscribe(%s, %s)", service, characteristic));

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.unsubscribe(service, characteristic);
      operation.complete();
    } catch (Exception e) {
      operation.fail(e);
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

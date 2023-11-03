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

  private BleScanner scanner;
  private BleGatt gatt;
  private EventEmitter emitter;

  public BleLibraryModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void initModule(Promise promise) {
    Log.d(NAME, "initModule()");

    ReactApplicationContext reactContext = getReactApplicationContext();

    Log.i(NAME, "checking permissions");
    PermissionManager permissionManager = new BlePermissionsManager(reactContext);
    permissionManager.ensure((granted) -> {
      if (!granted) {
        Log.w(NAME, "permission denied");

        promise.reject(BleException.ERROR_MISSING_PERMISSIONS, "missing BLE permissions");
      } else {
        Log.i(NAME, "permission granted");

        BluetoothManager bluetoothManager = (BluetoothManager) reactContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
          Log.w(NAME, "this device doesn't have a BLE adapter");

          promise.reject(BleException.ERROR_BLE_NOT_SUPPORTED, "this device doesn't support BLE");
        } else {
          Log.i(NAME, "checking if BLE is active");

          BleActivationManager activationManager = new BleActivationManagerImpl(bluetoothAdapter, reactContext);
          activationManager.ensureBleActive(isActive -> {
            if (!isActive) {
              Log.w(NAME, "BLE is not active");

              promise.reject(BleException.ERROR_BLE_NOT_ENABLED, "BLE is not active and user denied activation");
            } else {
              Log.i(NAME, "BLE is active, proceed with resources initialization");

              BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

              emitter = new RNEventEmitter(reactContext);

              ScanCallback scanCallback = new BleScanCallback(emitter);
              ConnectionContext connectionContext = new ConnectionContext();
              BluetoothGattCallback gattCallback = new BleBluetoothGattCallback(emitter, connectionContext);

              scanner = new BleScannerImpl(bluetoothLeScanner, scanCallback);
              gatt = new BleGattImpl(bluetoothAdapter, gattCallback, connectionContext, emitter, reactContext);

              Log.i(NAME, "module initialization done :)");
              promise.resolve(null);
            }
          });
        }
      }
    });
  }

  @ReactMethod
  public void disposeModule(Promise promise) {
    Log.d(NAME, "disposeModule()");

    gatt.dispose();
    gatt = null;

    scanner.dispose();
    scanner = null;

    emitter = null;

    Log.i(NAME, "module disposed correctly :)");
    promise.resolve(null);
  }

  @ReactMethod
  public void cancelPendingOperations(Promise promise) {
    Log.d(NAME, "cancelPendingOperations()");

    try {
      gatt.cancelPendingOperations();

      promise.resolve(null);
    } catch (BleException e) {
      promise.reject(e.getCode(), e.getMessage(), e);
    }
  }

  @ReactMethod
  public void scanStart(ReadableArray filterUuid, Promise promise) {
    Log.d(NAME, String.format("scanStart(%s)", filterUuid));

    if (scanner == null) {
      promise.reject(BleException.ERROR_NOT_INITIALIZED, "module is not initialized");
      return;
    }

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
    Log.d(NAME, "scanStop()");

    if (scanner == null) {
      promise.reject(BleException.ERROR_NOT_INITIALIZED, "module is not initialized");
      return;
    }

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
    Log.d(NAME, String.format("connect(%s, %f)", id, mtu));

    if (gatt == null) {
      promise.reject(BleException.ERROR_NOT_INITIALIZED, "module is not initialized");
      return;
    }

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.connect(operation, id, mtu.intValue());
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void disconnect(Promise promise) {
    Log.d(NAME, "disconnect()");

    if (gatt == null) {
      promise.reject(BleException.ERROR_NOT_INITIALIZED, "module is not initialized");
      return;
    }

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.disconnect(operation);
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void readRSSI(Promise promise) {
    Log.d(NAME, "readRSSI()");

    if (gatt == null) {
      promise.reject(BleException.ERROR_NOT_INITIALIZED, "module is not initialized");
      return;
    }

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.readRSSI(operation);
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void read(String service, String characteristic, Double size, Promise promise) {
    Log.d(NAME, String.format("read(%s, %s, %f)", service, characteristic, size));

    if (gatt == null) {
      promise.reject(BleException.ERROR_NOT_INITIALIZED, "module is not initialized");
      return;
    }

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.read(operation, service, characteristic, size.intValue());
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  @ReactMethod
  public void write(String service, String characteristic, String value, Double chunkSize, Promise promise) {
    Log.d(NAME, String.format("write(%s, %s, %s, %f)", service, characteristic, value, chunkSize));

    if (gatt == null) {
      promise.reject(BleException.ERROR_NOT_INITIALIZED, "module is not initialized");
      return;
    }

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
    Log.d(NAME, String.format("subscribe(%s, %s)", service, characteristic));

    if (gatt == null) {
      promise.reject(BleException.ERROR_NOT_INITIALIZED, "module is not initialized");
      return;
    }

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
    Log.d(NAME, String.format("unsubscribe(%s, %s)", service, characteristic));

    if (gatt == null) {
      promise.reject(BleException.ERROR_NOT_INITIALIZED, "module is not initialized");
      return;
    }

    PromiseAsyncOperation operation = new PromiseAsyncOperation(promise);
    try {
      gatt.unsubscribe(service, characteristic);
      operation.complete();
    } catch (Exception e) {
      operation.fail(e);
    }
  }

  // methods required for React Native NativeEventEmitter to work
  @ReactMethod
  public void addListener(String eventName) {
    Log.i(NAME, String.format("listener registered for event: %s", eventName));
  }

  @ReactMethod
  public void removeListeners(Integer count) {
    Log.i(NAME, String.format("%d listener removed", count));
  }
}

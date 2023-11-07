package it.iotinga.blelibrary;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

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
  private static final long SCAN_REPORT_DELAY_MS = 1000;

  private EventEmitter emitter;
  private BluetoothManager manager;
  private BluetoothAdapter adapter;
  private BluetoothLeScanner scanner;
  private BluetoothGattCallback gattCallback;
  private ScanCallback scanCallback;
  private final ConnectionContext context = new ConnectionContext();
  private final TransactionExecutor executor = new TransactionQueueExecutor();

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

        promise.reject(BleError.ERROR_MISSING_PERMISSIONS.name(), "missing BLE permissions");
      } else {
        Log.i(NAME, "permission granted");

        manager = (BluetoothManager) reactContext.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        if (adapter == null) {
          Log.w(NAME, "this device doesn't have a BLE adapter");

          promise.reject(BleError.ERROR_BLE_NOT_SUPPORTED.name(), "this device doesn't support BLE");
        } else {
          Log.i(NAME, "checking if BLE is active");

          BleActivationManager activationManager = new BleActivationManagerImpl(adapter, reactContext);
          activationManager.ensureBleActive(isActive -> {
            if (!isActive) {
              Log.w(NAME, "BLE is not active");

              promise.reject(BleError.ERROR_BLE_NOT_ENABLED.name(), "BLE is not active and user denied activation");
            } else {
              Log.i(NAME, "BLE is active, proceed with resources initialization");

              emitter = new RNEventEmitter(reactContext);

              Log.i(NAME, "module initialization done :)");
              promise.resolve(null);
            }
          });
        }
      }
    });
  }

  @ReactMethod
  @RequiresPermission(allOf = {"android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"})
  public void disposeModule(Promise promise) {
    Log.d(NAME, "disposeModule()");

    executor.flush();

    if (context.gatt != null) {
      context.gatt.close();
      context.gatt = null;
      gattCallback = null;
    }

    if (scanner != null) {
      scanner.stopScan(scanCallback);
      scanner = null;
      scanCallback = null;
    }

    adapter = null;
    manager = null;
    emitter = null;

    Log.i(NAME, "module disposed correctly :)");
    promise.resolve(null);
  }

  @ReactMethod
  public void cancel(String transactionId, Promise promise) {
    Log.d(NAME, String.format("cancel(%s)", transactionId));

    executor.cancel(transactionId);

    promise.resolve(null);
  }

  @ReactMethod
  @RequiresPermission("android.permission.BLUETOOTH_SCAN")
  public void scanStart(ReadableArray filterUuid, Promise promise) {
    Log.d(NAME, String.format("scanStart(%s)", filterUuid));

    if (adapter == null) {
      promise.reject(BleError.ERROR_NOT_INITIALIZED.name(), "module is not initialized");
    } else {
      Log.i(NAME, "starting scan");

      List<ScanFilter> filters = null;
      ScanSettings.Builder settings = new ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);

      if (filterUuid.size() > 0) {
        settings.setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH | ScanSettings.CALLBACK_TYPE_MATCH_LOST);
        filters = new ArrayList<>();
        for (int i = 0; i < filterUuid.size(); i++) {
          String serviceUuid = filterUuid.getString(i);
          Log.d(NAME, "adding filter UUID: " + serviceUuid);
          ParcelUuid uuid = ParcelUuid.fromString(serviceUuid);
          filters.add(new ScanFilter.Builder().setServiceUuid(uuid).build());
        }
      } else {
        // avoid flooding JS with events
        settings.setReportDelay(SCAN_REPORT_DELAY_MS);
        settings.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
      }

      if (scanner == null) {
        scanner = adapter.getBluetoothLeScanner();
        scanCallback = new BleScanCallback(emitter);
      } else {
        // stopping existing scan to restart it
        scanner.stopScan(scanCallback);
      }

      scanner.startScan(filters, settings.build(), scanCallback);

      promise.resolve(null);
    }
  }

  @ReactMethod
  @RequiresPermission("android.permission.BLUETOOTH_SCAN")
  public void scanStop(Promise promise) {
    Log.d(NAME, "scanStop()");

    if (scanner != null) {
      scanner.stopScan(scanCallback);
      scanner = null;
      scanCallback = null;
    }

    promise.resolve(null);
  }

  @ReactMethod
  @RequiresPermission(allOf = {"android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"})
  public void connect(String id, Double mtu, Promise promise) {
    Log.d(NAME, String.format("connect(%s, %f)", id, mtu));

    // ensure scan is not active (can create problems on some devices)
    if (scanner != null) {
      Log.i(NAME, "stopping BLE scan");

      try {
        scanner.stopScan(scanCallback);
        scanner = null;
        scanCallback = null;
      } catch (Exception e) {
        Log.w(NAME, "failed stopping scan: " + e);
      }
    }

    // the documentation says that we must do this
    adapter.cancelDiscovery();

    if (context.gatt != null) {
      Log.i(NAME, "closing existing GATT instance");

      context.gatt.close();
      context.gatt = null;
    }

    // ensure transaction queue is empty
    executor.flush();

    BluetoothDevice device;
    try {
      device = adapter.getRemoteDevice(id);
    } catch (Exception e) {
      Log.e(NAME, "cannot find device with address " + id);

      promise.reject(BleError.ERROR_DEVICE_NOT_FOUND.name(), "the specified device was not found");
      return;
    }


    Log.d(NAME, "starting GATT connection");
    context.mtu = mtu.intValue();
    gattCallback = new BleBluetoothGattCallback(emitter, context, executor);
    context.gatt = device.connectGatt(getReactApplicationContext(), false,  gattCallback);
    if (context.gatt == null) {
      promise.reject(BleError.ERROR_GATT.name(), "gatt instance is null");
    }

    // signals that the connection request is taking progress
    promise.resolve(null);
  }

  @ReactMethod
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  public void disconnect(Promise promise) {
    Log.d(NAME, "disconnect()");

    executor.flush();

    if (context.gatt != null) {
      context.gatt.disconnect();
    }

    promise.resolve(null);
  }

  @ReactMethod
  public void readRSSI(String transactionId, Promise promise) {
    Log.d(NAME, "readRSSI()");

    executor.add(new TransactionReadRssi(transactionId, promise, emitter, context.gatt));
  }

  @ReactMethod
  public void read(String transactionId, String service, String characteristic, Double size, Promise promise) {
    Log.d(NAME, String.format("read(%s, %s, %f)", service, characteristic, size));

    executor.add(new TransactionReadChar(transactionId, promise, emitter, context.gatt, service, characteristic, size.intValue()));
  }

  @ReactMethod
  public void write(String transactionId, String service, String characteristic, String value, Double chunkSize, Promise promise) {
    Log.d(NAME, String.format("write(%s, %s, %s, %f)", service, characteristic, value, chunkSize));

    byte[] data;
    try {
      data = Base64.getDecoder().decode(value);
    } catch (Exception e) {
      promise.reject(BleError.ERROR_INVALID_ARGUMENTS.name(), "value must be base64 encoded");
      return;
    }

    executor.add(new TransactionWriteChar(transactionId, promise, emitter, context.gatt, service, characteristic, data, chunkSize.intValue()));
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  private void setNotificationEnabled(Promise promise, String serviceUuid, String characteristicUuid, boolean enabled) {
    if (context.gatt == null) {
      promise.reject(BleError.ERROR_NOT_CONNECTED.name(), "device is not connected");
    } else {
      BluetoothGattCharacteristic characteristic = GattTransaction.getCharacteristic(context.gatt, serviceUuid, characteristicUuid);
      if (characteristic == null) {
        promise.reject(BleError.ERROR_INVALID_ARGUMENTS.name(), "characteristic is not found");
      } else {
        boolean success = context.gatt.setCharacteristicNotification(characteristic, enabled);
        if (success) {
          promise.resolve(null);
        } else {
          promise.reject(BleError.ERROR_GATT.name(), "error setting characteristic notification");
        }
      }
    }
  }

  @ReactMethod
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void subscribe(String serviceUuid, String characteristicUuid, Promise promise) {
    Log.d(NAME, String.format("subscribe(%s, %s)", serviceUuid, characteristicUuid));

    setNotificationEnabled(promise, serviceUuid, characteristicUuid, true);
  }

  @ReactMethod
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void unsubscribe(String serviceUuid, String characteristicUuid, Promise promise) {
    Log.d(NAME, String.format("unsubscribe(%s, %s)", serviceUuid, characteristicUuid));

    setNotificationEnabled(promise, serviceUuid, characteristicUuid, false);
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

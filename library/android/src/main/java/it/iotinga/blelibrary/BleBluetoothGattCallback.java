package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

public class BleBluetoothGattCallback extends BluetoothGattCallback {
  private final EventEmitter eventEmitter;
  private final ConnectionContext connectionContext;
  private final TransactionExecutor executor;

  BleBluetoothGattCallback(EventEmitter eventEmitter, ConnectionContext connectionContext,
      TransactionExecutor executor) {
    this.eventEmitter = eventEmitter;
    this.connectionContext = connectionContext;
    this.executor = executor;
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
    Log.d(Constants.LOG_TAG, "onConnectionStateChange - status: " + status + ", newState: " + newState);

    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
      Log.i(Constants.LOG_TAG, "device is successfully connected. Requesting MTU");

      if (connectionContext.mtu > 0) {
        Log.i(Constants.LOG_TAG, "setting the MTU to: " + connectionContext.mtu);

        boolean success = gatt.requestMtu(connectionContext.mtu);
        if (success) {
          eventEmitter.emit(
              new RNEventConnectionStateChanged(ConnectionState.REQUESTING_MTU, RNEventConnectionStateChanged.SUCCESS));
        } else {
          Log.w(Constants.LOG_TAG, "error sending MTU request for device.");

          gatt.disconnect();

          eventEmitter.emit(
              new RNEventConnectionStateChanged(ConnectionState.DISCONNECTING, RNEventConnectionStateChanged.FAILURE));
        }
      } else {
        Log.i(Constants.LOG_TAG, "using default MTU, discovering services");

        boolean success = gatt.discoverServices();
        if (success) {
          eventEmitter.emit(new RNEventConnectionStateChanged(ConnectionState.DISCOVERING_SERVICES,
              RNEventConnectionStateChanged.SUCCESS));
        } else {
          Log.w(Constants.LOG_TAG, "error sending service discovery request for device.");

          gatt.disconnect();

          eventEmitter.emit(
              new RNEventConnectionStateChanged(ConnectionState.DISCONNECTING, RNEventConnectionStateChanged.FAILURE));
        }
      }
    }

    if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
      Log.w(Constants.LOG_TAG, "connection failed unexpectedly. Trigger a new connection to device");

      // cancel all pending transaction
      executor.flush(BleError.ERROR_NOT_CONNECTED, "device has disconnected (unexpectedly)");

      boolean success = gatt.connect();
      if (success) {
        eventEmitter.emit(new RNEventConnectionStateChanged(ConnectionState.CONNECTING_TO_DEVICE, status));
      } else {
        Log.w(Constants.LOG_TAG, "error asking for device reconnect");

        eventEmitter.emit(
            new RNEventConnectionStateChanged(ConnectionState.DISCONNECTED, RNEventConnectionStateChanged.FAILURE));
      }
    }

    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
      Log.w(Constants.LOG_TAG, "expected disconnection");

      // cancel all pending transaction
      executor.flush(BleError.ERROR_NOT_CONNECTED, "device has disconnected (expectedly)");

      eventEmitter.emit(new RNEventConnectionStateChanged(ConnectionState.DISCONNECTED, status));
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onMtuChanged(@NonNull BluetoothGatt gatt, int mtu, int status) {
    Log.d(Constants.LOG_TAG, "onMtuChanged - status: " + status + ", mtu: " + mtu);

    if (status == BluetoothGatt.GATT_FAILURE) {
      Log.w(Constants.LOG_TAG, "error setting MTU. Continuing anyway in the connection process using default MTU");
    } else {
      Log.i(Constants.LOG_TAG, "set of the MTU is successful. Proceed with service discovery");
    }

    boolean success = gatt.discoverServices();
    if (success) {
      eventEmitter.emit(new RNEventConnectionStateChanged(ConnectionState.DISCOVERING_SERVICES,
          RNEventConnectionStateChanged.SUCCESS));
    } else {
      Log.w(Constants.LOG_TAG, "error asking for service discovery. Try to reset connection");

      gatt.disconnect();

      eventEmitter.emit(
          new RNEventConnectionStateChanged(ConnectionState.DISCONNECTING, RNEventConnectionStateChanged.FAILURE));
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
    Log.d(Constants.LOG_TAG, "onServiceDiscovered - status: " + status);

    if (status == BluetoothGatt.GATT_SUCCESS) {
      Log.i(Constants.LOG_TAG, "service discovery success. The device is now ready to be used");

      eventEmitter.emit(new RNEventConnectionStateChanged(ConnectionState.CONNECTED,
          RNEventConnectionStateChanged.SUCCESS, gatt.getServices()));
    } else {
      Log.w(Constants.LOG_TAG, "error discovering services. Try to reset the connection with the device");

      gatt.disconnect();

      eventEmitter.emit(new RNEventConnectionStateChanged(ConnectionState.DISCONNECTING, status));
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onCharacteristicWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic,
      int status) {
    Log.d(Constants.LOG_TAG, "onCharacteristicWrite - status: " + status);

    Transaction transaction = executor.getExecuting();
    if (transaction != null) {
      try {
        if (transaction instanceof GattTransaction) {
          GattTransaction gattTransaction = (GattTransaction) transaction;
          if (status == BluetoothGatt.GATT_SUCCESS) {
            gattTransaction.onCharWrite(characteristic);
          } else {
            gattTransaction.onError(status);
          }
        }
      } catch (Exception e) {
        String msg = "unexpected exception in onCharacteristicWrite()" + e;
        Log.e(Constants.LOG_TAG, msg);
        transaction.fail(BleError.ERROR_GENERIC, msg);
      }
    } else {
      Log.w(Constants.LOG_TAG, "no transaction is pending. How do I get here?");
    }

    executor.process();
  }

  @Override
  public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic,
      int status) {
    Log.d(Constants.LOG_TAG, "onCharacteristicRead - status: " + status);

    Transaction transaction = executor.getExecuting();
    if (transaction != null) {
      try {
        if (transaction instanceof GattTransaction) {
          GattTransaction gattTransaction = (GattTransaction) transaction;
          if (status == BluetoothGatt.GATT_SUCCESS) {
            gattTransaction.onCharRead(characteristic);
          } else {
            gattTransaction.onError(status);
          }
        }
      } catch (Exception e) {
        String msg = "unexpected exception in onCharacteristicRead()" + e;
        Log.e(Constants.LOG_TAG, msg);
        transaction.fail(BleError.ERROR_GENERIC, msg);
      }
    } else {
      Log.w(Constants.LOG_TAG, "no transaction is pending. How do I get here?");
    }

    executor.process();
  }

  // NOTE: this is deprecated but useful to support older OS!
  @Override
  public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
      @NonNull BluetoothGattCharacteristic characteristic) {
    byte[] value = characteristic.getValue();

    Log.d(Constants.LOG_TAG, "onCharacteristicChanged uuid " + characteristic.getUuid() + " value length " + value.length);

    eventEmitter.emit(new RNEventCharacteristicChanged(characteristic, value));
  }

  @Override
  public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
    Log.d(Constants.LOG_TAG, "onReadRemoteRssi - rssi: " + rssi + " status: " + status);

    Transaction transaction = executor.getExecuting();
    if (transaction != null) {
      try {
        if (transaction instanceof GattTransaction) {
          GattTransaction gattTransaction = (GattTransaction) transaction;
          if (status == BluetoothGatt.GATT_SUCCESS) {
            gattTransaction.onReadRemoteRssi(rssi);
          } else {
            gattTransaction.onError(status);
          }
        }
      } catch (Exception e) {
        String msg = "unexpected exception in onReadRemoteRssi()" + e;
        Log.e(Constants.LOG_TAG, msg);
        transaction.fail(BleError.ERROR_GENERIC, msg);
      }
    } else {
      Log.w(Constants.LOG_TAG, "no transaction is pending. How do I get here?");
    }

    executor.process();
  }
}

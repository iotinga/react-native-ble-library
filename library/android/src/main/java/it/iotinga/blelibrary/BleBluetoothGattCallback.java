package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import java.util.Base64;

public class BleBluetoothGattCallback extends BluetoothGattCallback {
  private static final String TAG = "BleGattCallback";

  private final EventEmitter eventEmitter;
  private final ConnectionContext connectionContext;
  private final Base64.Encoder b64Encoder = Base64.getEncoder();

  BleBluetoothGattCallback(EventEmitter eventEmitter, ConnectionContext connectionContext) {
    this.eventEmitter = eventEmitter;
    this.connectionContext = connectionContext;
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
    Log.d(TAG, "onConnectionStateChange - status: " + status + ", newState: " + newState);

    PendingGattOperation operation = connectionContext.getPendingGattOperation();
    if (operation != null) {
      if (status != BluetoothGatt.GATT_SUCCESS) {
        operation.onError(status);
      } else if (newState == BluetoothProfile.STATE_CONNECTED) {
        operation.onConnected(gatt);
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        operation.onDisconnected(gatt);
      }
    }

    // if an error occurred or I'm disconnected signal the JS and reset connection state
    if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
      connectionContext.setConnectionState(ConnectionState.DISCONNECTED);
      eventEmitter.emitError(BleException.ERROR_DEVICE_DISCONNECTED, "device disconnected");
      PendingGattOperation pendingGattOperation = connectionContext.getPendingGattOperation();
      if (pendingGattOperation != null) {
        pendingGattOperation.operation.fail(new BleException(BleException.ERROR_GATT, "device disconnected"));
      }
      connectionContext.reset();
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onMtuChanged(@NonNull BluetoothGatt gatt, int mtu, int status) {
    Log.d(TAG, "onMtuChanged - status: " + status + ", mtu: " + mtu);

    PendingGattOperation operation = connectionContext.getPendingGattOperation();
    if (operation != null) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        operation.onMtuChanged(gatt);
      } else {
        operation.onError(status);
      }
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
    Log.d(TAG, "onServiceDiscovered - status: " + status);

    PendingGattOperation operation = connectionContext.getPendingGattOperation();
    if (operation != null) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        operation.onServiceDiscovered(gatt);
      } else {
        operation.onError(status);
      }
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void onCharacteristicWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
    Log.d(TAG, "onCharacteristicWrite - status: " + status);

    PendingGattOperation operation = connectionContext.getPendingGattOperation();
    if (operation != null) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        operation.onCharWrite(gatt, characteristic);
      } else {
        operation.onError(status);
      }
    }
  }

  @Override
  public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
    Log.d(TAG, "onCharacteristicRead - status: " + status);

    PendingGattOperation operation = connectionContext.getPendingGattOperation();
    if (operation != null) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        operation.onCharRead(gatt, characteristic);
      } else {
        operation.onError(status);
      }
    }
  }

  @Override
  public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
    Log.d(TAG, "onCharacteristicChanged");

    WritableMap payload = Arguments.createMap();
    payload.putString("service", characteristic.getService().getUuid().toString());
    payload.putString("characteristic", characteristic.getUuid().toString());
    payload.putString("value", b64Encoder.encodeToString(value));
    eventEmitter.emit(EventEmitter.EVENT_CHAR_VALUE_CHANGED, payload);
  }

  @Override
  public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
    PendingGattOperation operation = connectionContext.getPendingGattOperation();
    if (operation != null) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        operation.onReadRemoteRssi(gatt, rssi);
      } else {
        operation.onError(status);
      }
    }
  }
}

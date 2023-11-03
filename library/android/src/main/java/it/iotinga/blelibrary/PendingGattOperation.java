package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public abstract class PendingGattOperation {
  protected final EventEmitter emitter;
  protected final AsyncOperation operation;

  PendingGattOperation(EventEmitter emitter, AsyncOperation operation) {
    this.emitter = emitter;
    this.operation = operation;
  }

  void onConnected(BluetoothGatt gatt) {
    operation.fail(new BleException(BleException.ERROR_INVALID_STATE, "unexpected onConnected"));
  }

  void onMtuChanged(BluetoothGatt gatt) {
    operation.fail(new BleException(BleException.ERROR_INVALID_STATE, "unexpected onMtuChanged"));
  }

  void onServiceDiscovered(BluetoothGatt gatt) {
    operation.fail(new BleException(BleException.ERROR_INVALID_STATE, "unexpected onServiceDiscovered"));
  }

  void onDisconnected(BluetoothGatt gatt) {
    operation.fail(new BleException(BleException.ERROR_INVALID_STATE, "unexpected onDisconnected"));
  }

  void onCharRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    operation.fail(new BleException(BleException.ERROR_INVALID_STATE, "unexpected onCharRead"));
  }

  void onCharWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    operation.fail(new BleException(BleException.ERROR_INVALID_STATE, "unexpected charWrite"));
  }

  void onError(int gattError) {
    operation.fail(new BleException(BleException.ERROR_GATT, "GATT error code " + gattError));
  }

  void onReadRemoteRssi(BluetoothGatt gatt, int rssi) {
    operation.fail(new BleException(BleException.ERROR_INVALID_STATE, "unexpected charWrite"));
  }

  void onCancel(BluetoothGatt gatt) {
    operation.fail(new BleException(BleException.ERROR_OPERATION_CANCELED, "current operation was canceled"));
  }

  boolean isPending() {
    return operation.isPending();
  }
}

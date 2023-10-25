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
    operation.fail(new BleInvalidStateException("unexpected onConnected"));
  }

  void onMtuChanged(BluetoothGatt gatt) {
    operation.fail(new BleInvalidStateException("unexpected onMtuChanged"));
  }

  void onServiceDiscovered(BluetoothGatt gatt) {
    operation.fail(new BleInvalidStateException("unexpected onServiceDiscovered"));
  }

  void onDisconnected(BluetoothGatt gatt) {
    operation.fail(new BleInvalidStateException("unexpected onDisconnected"));
  }

  void onCharRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    operation.fail(new BleInvalidStateException("unexpected onCharRead"));
  }

  void onCharWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    operation.fail(new BleInvalidStateException("unexpected charWrite"));
  }

  void onError(int gattError) {
    operation.fail(new BleGattException(gattError));
  }

  boolean isPending() {
    return operation.isPending();
  }
}

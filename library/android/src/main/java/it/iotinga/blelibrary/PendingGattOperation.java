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
    operation.fail(new BleException("UnexpectedCallback", "unexpected onConnected"));
  }

  void onMtuChanged(BluetoothGatt gatt) {
    operation.fail(new BleException("UnexpectedCallback", "unexpected onMtuChanged"));
  }

  void onServiceDiscovered(BluetoothGatt gatt) {
    operation.fail(new BleException("UnexpectedCallback", "unexpected onServiceDiscovered"));
  }

  void onDisconnected(BluetoothGatt gatt) {
    operation.fail(new BleException("UnexpectedCallback", "unexpected onDisconnected"));
  }

  void onCharRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    operation.fail(new BleException("UnexpectedCallback", "unexpected onCharRead"));
  }

  void onCharWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    operation.fail(new BleException("UnexpectedCallback", "unexpected charWrite"));
  }

  void onError(int gattError) {
    operation.fail(new BleException("GattError", "GATT error code " + gattError));
  }

  boolean isPending() {
    return operation.isPending();
  }
}

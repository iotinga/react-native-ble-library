package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

import androidx.annotation.RequiresPermission;

public class PendingGattConnect extends PendingGattOperation {
  private final int requestMtu;

  PendingGattConnect(EventEmitter emitter, AsyncOperation operation, int requestMtu) {
    super(emitter, operation);
    this.requestMtu = requestMtu;
  }

  PendingGattConnect(EventEmitter emitter, AsyncOperation operation) {
    this(emitter, operation, 0);
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  void onConnected(BluetoothGatt gatt) {
    boolean result;
    if (requestMtu > 0) {
      result = gatt.requestMtu(requestMtu);
    } else {
      result = gatt.discoverServices();
    }
    if (!result) {
      operation.fail(new BleException("driver busy"));
    }
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  void onMtuChanged(BluetoothGatt gatt) {
    boolean result = gatt.discoverServices();
    if (!result) {
      operation.fail(new BleException("driver busy"));
    }
  }

  @Override
  void onServiceDiscovered(BluetoothGatt gatt) {
    operation.complete();
  }
}

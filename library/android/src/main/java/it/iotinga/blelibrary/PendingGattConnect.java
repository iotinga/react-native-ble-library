package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

import androidx.annotation.RequiresPermission;

public class PendingGattConnect extends PendingGattOperation {
  private final int requestMtu;
  private final ConnectionContext context;

  PendingGattConnect(EventEmitter emitter, AsyncOperation operation, int requestMtu, ConnectionContext context) {
    super(emitter, operation);
    this.requestMtu = requestMtu;
    this.context = context;
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
      context.setConnectionState(ConnectionState.DISCONNECTED);
      operation.fail(new BleException("driver busy"));
    }
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  void onMtuChanged(BluetoothGatt gatt) {
    boolean result = gatt.discoverServices();
    if (!result) {
      context.setConnectionState(ConnectionState.DISCONNECTED);
      operation.fail(new BleException("driver busy"));
    }
  }

  @Override
  void onServiceDiscovered(BluetoothGatt gatt) {
    context.setConnectionState(ConnectionState.CONNECTED);
    operation.complete();
  }
}

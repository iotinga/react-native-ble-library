package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

public class PendingGattDisconnect extends PendingGattOperation {
  PendingGattDisconnect(EventEmitter emitter, AsyncOperation operation) {
    super(emitter, operation);
  }

  @Override
  void onDisconnected(BluetoothGatt gatt) {
    operation.complete();
  }
}

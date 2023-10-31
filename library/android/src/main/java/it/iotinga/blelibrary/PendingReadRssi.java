package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

public class PendingReadRssi extends PendingGattOperation {

  PendingReadRssi(EventEmitter emitter, AsyncOperation operation) {
    super(emitter, operation);
  }

  @Override
  void onReadRemoteRssi(BluetoothGatt gatt, int rssi) {
    operation.complete(rssi);
  }
}

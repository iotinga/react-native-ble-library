package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

public class PendingGattDisconnect extends PendingGattOperation {
  private final ConnectionContext context;

  PendingGattDisconnect(EventEmitter emitter, AsyncOperation operation, ConnectionContext context) {
    super(emitter, operation);
    this.context = context;
  }

  @Override
  void onDisconnected(BluetoothGatt gatt) {
    context.setConnectionState(ConnectionState.DISCONNECTED);
    operation.complete();
  }

  @Override
  void onError(int gattError) {
    super.onError(gattError);
    context.setConnectionState(ConnectionState.DISCONNECTED);
  }
}

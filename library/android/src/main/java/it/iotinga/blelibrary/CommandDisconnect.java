package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.ReadableMap;

public class CommandDisconnect implements Command {
  private final ConnectionContext connectionContext;
  private final EventEmitter eventEmitter;

  public CommandDisconnect(EventEmitter eventEmitter, ConnectionContext connectionContext) {
    this.connectionContext = connectionContext;
    this.eventEmitter = eventEmitter;
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  @Override
  public void execute(ReadableMap command, AsyncOperation operation) throws BleException {
    if (connectionContext.getConnectionState() != ConnectionState.DISCONNECTED) {
      PendingGattDisconnect disconnect = new PendingGattDisconnect(eventEmitter, operation);
      connectionContext.setPendingGattOperation(disconnect);
      connectionContext.setConnectionState(ConnectionState.DISCONNECTING);
      connectionContext.getGattLink().disconnect();
    } else {
      operation.complete();
    }
  }
}


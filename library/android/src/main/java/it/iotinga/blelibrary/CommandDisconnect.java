package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.ReadableMap;

public class CommandDisconnect implements Command {
  private final ConnectionContext connectionContext;

  public CommandDisconnect(ConnectionContext connectionContext) {
    this.connectionContext = connectionContext;
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  @Override
  public void execute(ReadableMap command) {
    if (connectionContext.getConnectionState() != ConnectionState.DISCONNECTED) {
      connectionContext.setConnectionState(ConnectionState.DISCONNECTING);

      BluetoothGatt gatt = connectionContext.getGattLink();
      if (gatt != null) {
        gatt.disconnect();
      }
    }
  }
}


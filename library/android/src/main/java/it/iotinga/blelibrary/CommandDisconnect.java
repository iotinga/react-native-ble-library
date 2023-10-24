package it.iotinga.blelibrary;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;

import com.facebook.react.bridge.ReadableMap;

public class CommandDisconnect implements Command {
  private final ConnectionContext connectionContext;

  public CommandDisconnect(ConnectionContext connectionContext) {
    this.connectionContext = connectionContext;
  }

  @SuppressLint("MissingPermission")
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


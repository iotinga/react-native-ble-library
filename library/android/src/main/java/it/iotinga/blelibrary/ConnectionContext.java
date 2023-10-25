package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

import java.util.HashMap;
import java.util.Map;

public class ConnectionContext {
  private ConnectionState state = ConnectionState.DISCONNECTED;
  private BluetoothGatt gattLink;
  private PendingGattOperation pendingGattOperation;

  public ConnectionState getConnectionState() {
    return this.state;
  }

  public void setConnectionState(ConnectionState state) {
    this.state = state;
  }

  public BluetoothGatt getGattLink() {
    return gattLink;
  }

  public void setGattLink(BluetoothGatt gattLink) {
    this.gattLink = gattLink;
  }

  public void setPendingGattOperation(PendingGattOperation operation) throws BleException {
    if (pendingGattOperation == null || !pendingGattOperation.isPending()) {
      pendingGattOperation = operation;
    } else {
      throw new BleException("driver busy");
    }
  }

  public PendingGattOperation getPendingGattOperation() {
    if (pendingGattOperation != null && pendingGattOperation.isPending()) {
      return pendingGattOperation;
    } else {
      return null;
    }
  }

  public void reset() {
    state = ConnectionState.DISCONNECTED;
    gattLink = null;
    pendingGattOperation = null;
  }
}

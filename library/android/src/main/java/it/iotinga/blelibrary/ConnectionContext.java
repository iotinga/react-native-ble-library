package it.iotinga.blelibrary;

public class ConnectionContext {
  private ConnectionState state = ConnectionState.DISCONNECTED;
  private PendingGattOperation pendingGattOperation;

  public ConnectionState getConnectionState() {
    return this.state;
  }

  public void setConnectionState(ConnectionState state) {
    this.state = state;
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
    pendingGattOperation = null;
  }
}

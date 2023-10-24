package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

import java.util.HashMap;
import java.util.Map;

public class ConnectionContext {
  private ConnectionState state = ConnectionState.DISCONNECTED;
  private BluetoothGatt gattLink;
  private Integer requestedMtu;
  private final Map<String, ChunkedWriteSplitter> writeChunks = new HashMap<>();

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

  public Integer getRequestedMtu() {
    return requestedMtu;
  }

  public void setRequestedMtu(Integer requestedMtu) {
    this.requestedMtu = requestedMtu;
  }

  public void setChunkedWrite(String characteristic, ChunkedWriteSplitter splitter) {
    if (splitter == null) {
      writeChunks.remove(characteristic);
    } else {
      writeChunks.put(characteristic, splitter);
    }
  }

  public ChunkedWriteSplitter getChunkedWrite(String characteristic) {
    return writeChunks.get(characteristic);
  }
}

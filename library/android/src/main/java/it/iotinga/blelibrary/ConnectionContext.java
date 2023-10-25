package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;

import java.util.HashMap;
import java.util.Map;

public class ConnectionContext {
  private ConnectionState state = ConnectionState.DISCONNECTED;
  private BluetoothGatt gattLink;
  private Integer requestedMtu;
  private final Map<String, ChunkedWriteSplitter> writeChunks = new HashMap<>();
  private final Map<String, ChunkedReadComposer> readChunks = new HashMap<>();

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

  public void setChunkedRead(String characteristic, ChunkedReadComposer composer) {
    if (composer == null) {
      readChunks.remove(characteristic);
    } else {
      readChunks.put(characteristic, composer);
    }
  }

  public ChunkedReadComposer getChunkedRead(String characteristic) {
    return readChunks.get(characteristic);
  }

  public void reset() {
    state = ConnectionState.DISCONNECTED;
    gattLink = null;
    requestedMtu = null;
    readChunks.clear();
    writeChunks.clear();
  }
}

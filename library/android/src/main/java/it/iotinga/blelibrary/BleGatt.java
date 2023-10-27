package it.iotinga.blelibrary;

public interface BleGatt {
  void connect(AsyncOperation operation, String id, int mtu) throws BleException;
  void disconnect(AsyncOperation operation) throws BleException;
  void read(AsyncOperation operation, String service, String characteristic, int size) throws BleException;
  void write(AsyncOperation operation, String service, String characteristic, byte[] value, int chunkSize) throws BleException;
  void subscribe(String service, String characteristic) throws BleException;
  void unsubscribe(String service, String characteristic) throws BleException;
}

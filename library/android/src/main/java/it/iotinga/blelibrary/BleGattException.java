package it.iotinga.blelibrary;

public class BleGattException extends BleException {
  BleGattException(int gattError) {
    super("BleGattError", "GATT driver error (code: " + gattError + ")");
  }

  BleGattException(String error) {
    super("BleGattError", "GATT driver error (reason: " + error + ")");
  }
}

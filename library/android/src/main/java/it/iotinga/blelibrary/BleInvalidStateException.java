package it.iotinga.blelibrary;

public class BleInvalidStateException extends BleException {
  BleInvalidStateException(String message) {
    super("BleInvalidState", message);
  }
}

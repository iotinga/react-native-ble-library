package it.iotinga.blelibrary;

public class BleInvalidArgumentException extends BleException{
  BleInvalidArgumentException(String message) {
    super("BleInvalidArgumentException", message);
  }
}


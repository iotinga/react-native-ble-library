package it.iotinga.blelibrary;

import androidx.annotation.NonNull;

public enum ErrorType {
  GENERIC_ERROR("BleGenericError"),
  DEVICE_DISCONNECTED("BleDeviceDisconnected"),
  INVALID_STATE("BleInvalidState"),
  SCAN_ERROR("BleScanError"),
  READ_ERROR("BleReadError"),
  WRITE_ERROR("BleWriteError"),
  CONNECT_ERROR("BleConnectError"),
  SUBSCRIBE_ERROR("BleSubscribeError"),
  UNSUBSCRIBE_ERROR("BleUnsubscribeError");

  private final String error;

  ErrorType(String error) {
    this.error = error;
  }

  @NonNull
  @Override
  public String toString() {
    return error;
  }
}

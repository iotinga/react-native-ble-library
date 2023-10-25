package it.iotinga.blelibrary;

import androidx.annotation.NonNull;

public enum ErrorCode {
  DEVICE_DISCONNECTED("BleDeviceDisconnected"),
  SCAN_ERROR("BleScanError");

  private final String error;

  ErrorCode(String error) {
    this.error = error;
  }

  @NonNull
  @Override
  public String toString() {
    return error;
  }
}

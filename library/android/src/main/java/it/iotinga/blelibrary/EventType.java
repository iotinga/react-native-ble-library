package it.iotinga.blelibrary;

import androidx.annotation.NonNull;

public enum EventType {
  PONG("pong"),
  ERROR("error"),
  SCAN_RESULT("scanResult"),
  CHAR_VALUE_CHANGED("charValueChanged"),
  READ_PROGRESS("readProgress"),
  WRITE_PROGRESS("writeProgress");

  private final String type;

  EventType(String type) {
    this.type = type;
  }

  @NonNull
  @Override
  public String toString() {
    return type;
  }
}

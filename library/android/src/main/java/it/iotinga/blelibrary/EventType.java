package it.iotinga.blelibrary;

import androidx.annotation.NonNull;

public enum EventType {
  PONG("pong"),
  ERROR("error"),
  SCAN_RESULT("scanResult"),
  SCAN_STOPPED("scanStopped"),
  SCAN_STARTED("scanStarted"),
  CONNECTED("connected"),
  DISCONNECTED("disconnected"),
  SUBSCRIBE("subscribe"),
  CHAR_VALUE_CHANGED("charValueChanged"),
  WRITE_COMPLETED("writeCompleted"),
  WRITE_PROGRESS("writeProgress"),
  MTU_CHANGED("mtuChanged");
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

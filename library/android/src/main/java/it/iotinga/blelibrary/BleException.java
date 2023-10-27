package it.iotinga.blelibrary;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

public class BleException extends Exception {
  public static final String DEFAULT_ERROR_CODE = "BleNativeModuleError";

  private final WritableMap details;

  private final String code;

  BleException(String code, String message, WritableMap details) {
    super(message);
    this.details = details;
    this.code = code;
  }

  BleException(String code, String message) {
    this(code, message, Arguments.createMap());
  }

  BleException(String message) {
    this(DEFAULT_ERROR_CODE, message);
  }

  public WritableMap getDetails() {
    return details;
  }

  public String getCode() {
    return code;
  }
}

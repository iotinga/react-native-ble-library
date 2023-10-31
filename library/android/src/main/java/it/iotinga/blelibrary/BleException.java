package it.iotinga.blelibrary;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

public class BleException extends Exception {
  public static final String DEFAULT_ERROR_CODE = BleLibraryModule.ERROR_GENERIC;

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

  public WritableMap getDetails() {
    return details;
  }

  public String getCode() {
    return code;
  }
}

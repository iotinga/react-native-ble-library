package it.iotinga.blelibrary;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

public class BleException extends Exception {
  public static final String ERROR_SCAN = "BleScanError";
  public static final String ERROR_GENERIC = "BleGenericError";
  public static final String ERROR_DEVICE_DISCONNECTED = "BleDeviceDisconnectedError";
  public static final String ERROR_INVALID_STATE = "BleInvalidStateError";
  public static final String ERROR_BLE_NOT_ENABLED = "BleNotEnabledError";
  public static final String ERROR_BLE_NOT_SUPPORTED = "BleNotSupportedError";
  public static final String ERROR_MISSING_PERMISSIONS = "BleMissingPermissionError";
  public static final String ERROR_GATT = "BleGATTError";
  public static final String ERROR_CONNECTION = "BleConnectionError";
  public static final String ERROR_NOT_CONNECTED = "BleNotConnectedError";
  public static final String ERROR_NOT_INITIALIZED = "BleNotInitializedError";
  public static final String ERROR_MODULE_BUSY = "BleModuleBusyError";
  public static final String ERROR_INVALID_ARGUMENTS = "BleInvalidArgumentsError";
  public static final String ERROR_DEVICE_NOT_FOUND = "BleDeviceNotFoundError";
  public static final String ERROR_OPERATION_CANCELED = "BleOperationCanceledError";

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

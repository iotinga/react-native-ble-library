package it.iotinga.blelibrary;

import com.facebook.react.bridge.WritableMap;

public interface EventEmitter {
  String EVENT_ERROR = "error";
  String EVENT_SCAN_RESULT = "scanResult";
  String EVENT_CHAR_VALUE_CHANGED = "charValueChanged";
  String EVENT_READ_PROGRESS = "readProgress";
  String EVENT_WRITE_PROGRESS = "writeProgress";

  void emit(String event, WritableMap payload);
  void emitError(String code, String message);
  void emitError(String code, String message, WritableMap details);
}

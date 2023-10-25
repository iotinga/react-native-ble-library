package it.iotinga.blelibrary;

import com.facebook.react.bridge.WritableMap;

public interface EventEmitter {
  void emit(EventType event);
  void emit(EventType event, WritableMap payload);
  void emitError(ErrorCode error, String message);
  void emitError(ErrorCode error, String message, WritableMap details);
}

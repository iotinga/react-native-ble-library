package it.iotinga.blelibrary;

import com.facebook.react.bridge.ReadableMap;

public interface Command {
  void execute(ReadableMap command);
}

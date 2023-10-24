package it.iotinga.blelibrary;

import com.facebook.react.bridge.ReadableMap;

public interface Dispatcher {
  void register(String type, Command command);
  void dispatch(ReadableMap payload);
}

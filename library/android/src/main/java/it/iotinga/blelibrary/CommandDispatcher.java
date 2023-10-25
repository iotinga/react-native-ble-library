package it.iotinga.blelibrary;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;

import java.util.HashMap;
import java.util.Map;

public class CommandDispatcher implements Dispatcher {
  private final Map<String, Command> commands = new HashMap<>();

  public void register(String name, Command command) {
    commands.put(name, command);
  }

  public void dispatch(ReadableMap payload, Promise promise) {
    AsyncOperation operation = new PromiseAsyncOperation(promise);

    try {
      String type = payload.getString("type");
      if (type == null) {
        throw new BleInvalidArgumentException("missing type field");
      }

      Command command = commands.get(type);
      if (command == null) {
        throw new BleInvalidArgumentException("command is not found");
      }

      command.execute(payload, operation);
    } catch (Exception e) {
      operation.fail(e);
    }
  }
}

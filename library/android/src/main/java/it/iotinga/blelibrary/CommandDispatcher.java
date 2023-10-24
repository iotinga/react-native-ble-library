package it.iotinga.blelibrary;

import com.facebook.react.bridge.ReadableMap;

import java.util.HashMap;
import java.util.Map;

public class CommandDispatcher implements Dispatcher {
  private final Map<String, Command> commands = new HashMap<>();

  public void register(String name, Command command) {
    commands.put(name, command);
  }

  public void dispatch(ReadableMap payload) {
    String type = payload.getString("type");
    if (type == null) {
      throw new RuntimeException("missing type field");
    }

    Command command = commands.get(type);
    if (command == null) {
      throw new RuntimeException("command is not found");
    }

    command.execute(payload);
  }
}

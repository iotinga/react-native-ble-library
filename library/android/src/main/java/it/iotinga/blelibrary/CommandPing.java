package it.iotinga.blelibrary;

import com.facebook.react.bridge.ReadableMap;

public class CommandPing implements Command {
  private final EventEmitter eventEmitter;

  CommandPing(EventEmitter eventEmitter) {
    this.eventEmitter = eventEmitter;
  }

  @Override
  public void execute(ReadableMap command) {
    this.eventEmitter.emit(EventType.PONG);
  }
}

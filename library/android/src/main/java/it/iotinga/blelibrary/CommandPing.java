package it.iotinga.blelibrary;

import com.facebook.react.bridge.ReadableMap;

public class CommandPing implements Command {
  private final EventEmitter eventEmitter;

  CommandPing(EventEmitter eventEmitter) {
    this.eventEmitter = eventEmitter;
  }

  @Override
  public void execute(ReadableMap command, AsyncOperation operation) {
    this.eventEmitter.emit(EventType.PONG);
    operation.complete();
  }
}

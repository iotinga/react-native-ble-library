package it.iotinga.blelibrary;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import com.facebook.react.bridge.ReadableMap;

import java.util.UUID;

public class CommandRead implements Command {
  private final EventEmitter eventEmitter;
  private final ConnectionContext connectionContext;

  public CommandRead(EventEmitter eventEmitter, ConnectionContext connectionContext) {
    this.eventEmitter = eventEmitter;
    this.connectionContext = connectionContext;
  }

  @SuppressLint("MissingPermission")
  @Override
  public void execute(ReadableMap command) {
    String serviceUuidString = command.getString("service");
    if (serviceUuidString == null) {
      throw new RuntimeException("missing service argument");
    }

    String charUuidString = command.getString("characteristic");
    if (charUuidString == null) {
      throw new RuntimeException("missing characteristic argument");
    }

    if (connectionContext.getConnectionState() == ConnectionState.CONNECTED) {
      BluetoothGatt gatt = connectionContext.getGattLink();
      BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuidString));
      BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuidString));

      // launch the read of the characteristic
      // TODO: characteristic that necessitate more than 1 read pass
      boolean result = gatt.readCharacteristic(characteristic);
      if (!result) {
        eventEmitter.emitError(ErrorType.READ_ERROR, "error reading characteristic");
      }
    }
  }
}

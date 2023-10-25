package it.iotinga.blelibrary;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import java.util.UUID;

public class CommandUnsubscribe implements Command {
  private final EventEmitter eventEmitter;
  private final ConnectionContext connectionContext;

  public CommandUnsubscribe(EventEmitter eventEmitter, ConnectionContext connectionContext) {
    this.eventEmitter = eventEmitter;
    this.connectionContext = connectionContext;
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
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

    WritableMap payload = Arguments.createMap();
    payload.putString("characteristic", charUuidString);
    payload.putString("service", serviceUuidString);

    if (connectionContext.getConnectionState() == ConnectionState.CONNECTED) {
      BluetoothGatt gatt = connectionContext.getGattLink();
      BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuidString));
      BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuidString));

      boolean result = gatt.setCharacteristicNotification(characteristic, false);
      if (!result) {
       eventEmitter.emitError(ErrorType.UNSUBSCRIBE_ERROR, "error unsubscribing from characteristic", payload);
      } else {
        eventEmitter.emit(EventType.UNSUBSCRIBED, payload);
      }
    }
  }
}

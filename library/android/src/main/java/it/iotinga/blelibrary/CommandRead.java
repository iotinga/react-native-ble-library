package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.ReadableMap;

import java.util.UUID;

public class CommandRead implements Command {
  private final EventEmitter eventEmitter;
  private final ConnectionContext connectionContext;

  public CommandRead(EventEmitter eventEmitter, ConnectionContext connectionContext) {
    this.eventEmitter = eventEmitter;
    this.connectionContext = connectionContext;
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  @Override
  public void execute(ReadableMap command, AsyncOperation operation) throws BleException {
    String serviceUuidString = command.getString("service");
    if (serviceUuidString == null) {
      throw new BleInvalidArgumentException("missing service argument");
    }

    String charUuidString = command.getString("characteristic");
    if (charUuidString == null) {
      throw new BleInvalidArgumentException("missing characteristic argument");
    }

    if (connectionContext.getConnectionState() == ConnectionState.CONNECTED) {
      BluetoothGatt gatt = connectionContext.getGattLink();
      BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuidString));
      BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuidString));

      PendingGattRead read;
      if (command.hasKey("size")) {
        read = new PendingGattRead(eventEmitter, operation, command.getInt("size"));
      } else {
        read = new PendingGattRead(eventEmitter, operation);
      }
      connectionContext.setPendingGattOperation(read);

      read.doRead(gatt, characteristic);
    } else {
      throw new BleInvalidStateException("not connected");
    }
  }
}

package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.ReadableMap;

import java.util.UUID;

public class CommandUnsubscribe implements Command {
  private final ConnectionContext connectionContext;

  public CommandUnsubscribe(ConnectionContext connectionContext) {
    this.connectionContext = connectionContext;
  }

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

      boolean result = gatt.setCharacteristicNotification(characteristic, false);
      if (!result) {
        throw new BleGattException("driver error");
      } else {
        operation.complete();
      }
    } else {
      throw new BleInvalidStateException("not connected");
    }
  }
}


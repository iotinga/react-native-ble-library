package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.ReadableMap;

import java.util.Base64;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.O)
public class CommandWrite implements Command {
  private final Base64.Decoder base64Decoder = Base64.getDecoder();
  private final EventEmitter eventEmitter;
  private final ConnectionContext connectionContext;

  public CommandWrite(EventEmitter eventEmitter, ConnectionContext connectionContext) {
    this.eventEmitter = eventEmitter;
    this.connectionContext = connectionContext;
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void execute(ReadableMap command, AsyncOperation operation) throws BleException {
    String serviceUuidString = command.getString("service");
    if (serviceUuidString == null) {
      throw new BleInvalidArgumentException("missing service argument");
    }

    String charUuidString = command.getString("characteristic");
    if (charUuidString == null) {
      throw new BleInvalidArgumentException("missing characteristic argument");
    }

    String valueBase64 = command.getString("value");
    if (valueBase64 == null) {
      throw new BleInvalidArgumentException("missing value argument");
    }

    byte[] value = base64Decoder.decode(valueBase64);

    if (connectionContext.getConnectionState() == ConnectionState.CONNECTED) {
      BluetoothGatt gatt = connectionContext.getGattLink();
      BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuidString));
      BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuidString));

      PendingGattWrite write;
      if (command.hasKey("chunkSize")) {
        write = new PendingGattWrite(eventEmitter, operation, value, command.getInt("chunkSize"));
      } else {
        write = new PendingGattWrite(eventEmitter, operation, value);
      }
      connectionContext.setPendingGattOperation(write);

      // perform initial write
      write.doWrite(gatt, characteristic);
    } else {
      throw new BleInvalidStateException("not connected");
    }
  }
}

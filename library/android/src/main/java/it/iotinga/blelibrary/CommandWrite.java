package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

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
  public void execute(ReadableMap command) {
    String serviceUuidString = command.getString("service");
    if (serviceUuidString == null) {
      throw new RuntimeException("missing service argument");
    }

    String charUuidString = command.getString("characteristic");
    if (charUuidString == null) {
      throw new RuntimeException("missing characteristic argument");
    }

    String valueBase64 = command.getString("value");
    if (valueBase64 == null) {
      throw new RuntimeException("missing value argument");
    }

    WritableMap payload = Arguments.createMap();
    payload.putString("service", serviceUuidString);
    payload.putString("characteristic", charUuidString);

    if (connectionContext.getChunkedRead(charUuidString) != null
      || connectionContext.getChunkedWrite(charUuidString) != null) {
      eventEmitter.emitError(ErrorType.INVALID_STATE, "a read/write operation is already in progress", payload);
    }

    byte[] value = base64Decoder.decode(valueBase64);

    if (connectionContext.getConnectionState() == ConnectionState.CONNECTED) {
      BluetoothGatt gatt = connectionContext.getGattLink();
      BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuidString));
      BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuidString));

      // check if this write is chunked
      if (command.hasKey("maxSize")) {
        // we need to split the write into chunks of maxSize length
        int maxSize = command.getInt("maxSize");
        ChunkedWriteSplitter splitter = new ChunkedWriteSplitterImpl(value, maxSize);
        connectionContext.setChunkedWrite(charUuidString, splitter);
        characteristic.setValue(splitter.getNextChunk());
      } else {
        // we directly write the value
        connectionContext.setChunkedWrite(charUuidString, null);
        characteristic.setValue(value);
      }

      // launch the write of the characteristic
      boolean result = gatt.writeCharacteristic(characteristic);
      if (!result) {
        eventEmitter.emitError(ErrorType.WRITE_ERROR, "error writing characteristic", payload);
      }
    }
  }
}

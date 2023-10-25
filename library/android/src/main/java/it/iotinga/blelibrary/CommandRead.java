package it.iotinga.blelibrary;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import java.util.UUID;

public class CommandRead implements Command {
  private final String TAG = "CommandRead";

  private final EventEmitter eventEmitter;
  private final ConnectionContext connectionContext;

  public CommandRead(EventEmitter eventEmitter, ConnectionContext connectionContext) {
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
    payload.putString("service", serviceUuidString);
    payload.putString("characteristic", charUuidString);

    if (connectionContext.getChunkedRead(charUuidString) != null
      || connectionContext.getChunkedWrite(charUuidString) != null) {
      eventEmitter.emitError(ErrorType.INVALID_STATE, "a read/write operation is already in progress", payload);
    }

    if (command.hasKey("size")) {
      // the read is chunked, setup for it
      int size = command.getInt("size");

      connectionContext.setChunkedRead(charUuidString, new ChunkedReadComposerImpl(size));
    }

    if (connectionContext.getConnectionState() == ConnectionState.CONNECTED) {
      BluetoothGatt gatt = connectionContext.getGattLink();
      BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuidString));
      BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuidString));

      Log.i(TAG, "permission: " + characteristic.getPermissions() + ", props: " + characteristic.getProperties());

      boolean result = gatt.readCharacteristic(characteristic);
      if (result) {
        Log.d(TAG, "requested char read, char: " + characteristic);
      } else {
        eventEmitter.emitError(ErrorType.READ_ERROR, "error reading characteristic", payload);
      }
    } else {
      Log.w(TAG, "skipping read since device not connected (state: " + connectionContext.getConnectionState() + ")");
      eventEmitter.emitError(ErrorType.INVALID_STATE, "not connected");
    }
  }
}


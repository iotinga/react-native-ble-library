package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import java.util.Arrays;

public class PendingGattWrite extends PendingGattOperation {
  private final byte[] bytes;
  private final int chunkSize;
  private int writtenBytes = 0;

  PendingGattWrite(EventEmitter emitter, AsyncOperation operation, byte[] bytes, int chunkSize) {
    super(emitter, operation);
    this.bytes = bytes;
    this.chunkSize = chunkSize;
  }

  private boolean hasNextChunk() {
    return writtenBytes < bytes.length;
  }

  private byte[] getNextChunk() {
    if (!hasNextChunk()) {
      return null;
    }

    int rangeEnd = Math.min(writtenBytes + chunkSize, bytes.length);
    byte[] chunk = Arrays.copyOfRange(bytes, writtenBytes, rangeEnd);
    writtenBytes += chunk.length;

    return chunk;
  }

  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  public void doWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    boolean result = characteristic.setValue(getNextChunk());
    if (!result) {
      operation.fail(new BleException("GattError", "setValue failed"));
    } else {
      result = gatt.writeCharacteristic(characteristic);
      if (!result) {
        operation.fail(new BleException("GattError", "writeCharacteristic failed"));
      }
    }
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  void onCharWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    if (hasNextChunk()) {
      String serviceUuid = characteristic.getService().getUuid().toString();
      String charUuid = characteristic.getUuid().toString();

      WritableMap progress = Arguments.createMap();
      progress.putString("service", serviceUuid);
      progress.putString("characteristic", charUuid);
      progress.putInt("total", bytes.length);
      progress.putInt("current", writtenBytes);
      emitter.emit(EventType.WRITE_PROGRESS, progress);

      doWrite(gatt, characteristic);
    } else {
      operation.complete();
    }
  }
}

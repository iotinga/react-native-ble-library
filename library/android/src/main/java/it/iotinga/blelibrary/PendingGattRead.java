package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import java.util.Base64;

@RequiresApi(api = Build.VERSION_CODES.O)
public class PendingGattRead extends PendingGattOperation {
  private final Base64.Encoder b64Encoder = Base64.getEncoder();
  private int receivedBytes = 0;
  private int totalSize = 0;
  private byte[] data;

  PendingGattRead(EventEmitter emitter, AsyncOperation operation, int totalSize) {
    super(emitter, operation);
    this.totalSize = totalSize;
    this.data = new byte[totalSize];
  }

  PendingGattRead(EventEmitter emitter, AsyncOperation operation) {
    super(emitter, operation);
  }

  private boolean hasMoreChunks() {
    return receivedBytes < totalSize;
  }

  private void onChunk(byte[] bytes) {
    if (data != null) {
      for (byte b : bytes) {
        data[receivedBytes++] = b;
      }
    } else {
      data = bytes;
      receivedBytes = bytes.length;
    }
  }

  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  public void doRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    boolean result = gatt.readCharacteristic(characteristic);
    if (!result) {
      operation.fail(new BleException("driver busy"));
    }
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  void onCharRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    onChunk(characteristic.getValue());
    if (hasMoreChunks()) {
      // signal progress
      String serviceUuid = characteristic.getService().getUuid().toString();
      String charUuid = characteristic.getUuid().toString();

      WritableMap progress = Arguments.createMap();
      progress.putString("service", serviceUuid);
      progress.putString("characteristic", charUuid);
      progress.putInt("total", totalSize);
      progress.putInt("current", receivedBytes);
      emitter.emit(EventType.READ_PROGRESS, progress);

      // request another read
      doRead(gatt, characteristic);
    } else {
      operation.complete(b64Encoder.encodeToString(data));
    }
  }
}
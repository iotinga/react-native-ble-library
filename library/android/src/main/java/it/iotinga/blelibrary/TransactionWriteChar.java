package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Promise;

import java.util.Arrays;

public class TransactionWriteChar extends GattTransaction {
  private static final String TAG = "TransactionWriteChar";
  private final String serviceUuid;
  private final String characteristicUuid;
  private final byte[] bytes;
  private final int chunkSize;
  private int writtenBytes = 0;

  TransactionWriteChar(String transactionId, Promise promise, EventEmitter emitter, BluetoothGatt gatt, String serviceUuid, String characteristicUuid, byte[] bytes, int chunkSize) {
    super(transactionId, promise, emitter, gatt);
    this.serviceUuid = serviceUuid;
    this.characteristicUuid = characteristicUuid;
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
  private void sendWriteChunk(BluetoothGattCharacteristic characteristic) {
    boolean setValueSuccess = characteristic.setValue(getNextChunk());
    if (!setValueSuccess) {
      Log.w(TAG, "error setting value to be written");

      fail(BleError.ERROR_GATT, "error setting characteristic value");
    } else {
      boolean writeSuccess = gatt.writeCharacteristic(characteristic);
      if (!writeSuccess) {
        Log.w(TAG, "error requesting characteristic write");

        fail(BleError.ERROR_GATT, "error requesting characteristic write");
      }
    }
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  public void start() {
    super.start();

    BluetoothGattCharacteristic characteristic = getCharacteristic(gatt, serviceUuid, characteristicUuid);
    if (characteristic == null) {
      Log.w(TAG, "characteristic with such ID was not found");

      fail(BleError.ERROR_INVALID_ARGUMENTS, "characteristic which such UUID not found");
    } else {
      Log.i(TAG, "requesting write of first chunk");

      sendWriteChunk(characteristic);
    }
  }


  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  void onCharWrite(BluetoothGattCharacteristic characteristic) {
    if (hasNextChunk()) {
      Log.i(TAG, "write chunk success, requesting another chunk");

      emitter.emit(new RNEventProgress(id(), characteristic, writtenBytes, bytes.length));

      sendWriteChunk(characteristic);
    } else {
      Log.i(TAG, "written all data successfully");

      succeed(null);
    }
  }
}

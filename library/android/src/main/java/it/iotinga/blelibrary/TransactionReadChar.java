package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Promise;

import java.util.Base64;

public class TransactionReadChar extends GattTransaction {
  private static final String TAG = "PendingGattRead";
  private static final byte EOF_BYTE = (byte) 0xff;

  private final Base64.Encoder b64Encoder = Base64.getEncoder();
  private final String serviceUuid;
  private final String charUuid;
  private int receivedBytes = 0;
  private int totalSize;
  private byte[] data;


  TransactionReadChar(String transactionId, Promise promise, EventEmitter emitter, BluetoothGatt gatt, String serviceUuid, String charUuid, int totalSize) {
    super(transactionId, promise, emitter, gatt);
    this.totalSize = totalSize;
    this.serviceUuid = serviceUuid;
    this.charUuid = charUuid;
    if (totalSize != 0) {
      this.data = new byte[this.totalSize];
    }
  }

  private boolean hasMoreChunks() {
    return receivedBytes < totalSize;
  }

  private void onChunk(byte[] bytes) {
    if (data != null) {
      if (data.length == 1 && data[0] == EOF_BYTE) {
        Log.i(TAG, "read a message of 1 byte 0xff: reached EOF");

        this.totalSize = data.length;
      } else {
        for (byte b : bytes) {
          data[receivedBytes++] = b;
        }
      }
    } else {
      data = bytes;
      receivedBytes = bytes.length;
    }
  }

  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  private void sendReadChunk(BluetoothGattCharacteristic characteristic) {
    boolean success = gatt.readCharacteristic(characteristic);
    if (!success) {
      Log.w(TAG, "error performing a read request");

      fail(BleError.ERROR_GATT, "readCharacteristic returned false");
    }
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  void onCharRead(BluetoothGattCharacteristic characteristic) {
    onChunk(characteristic.getValue());

    if (hasMoreChunks()) {
      Log.i(TAG, "need to read another chunk of data");

      emitter.emit(new RNEventProgress(id(), characteristic, receivedBytes, totalSize));

      sendReadChunk(characteristic);
    } else {
      Log.i(TAG, "all data read :)");

      succeed(b64Encoder.encodeToString(data));
    }
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  public void start() {
    super.start();

    BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUuid, charUuid);
    if (characteristic == null) {
      Log.w(TAG, "the characteristic to be read is not found");

      fail(BleError.ERROR_INVALID_ARGUMENTS, "characteristic not found in device");
    } else {
      Log.i(TAG, "requesting a read from the device");

      sendReadChunk(characteristic);
    }
  }
}

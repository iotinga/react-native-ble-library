package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Promise;

import java.util.Arrays;
import java.util.Base64;

@RequiresApi(26)
public class TransactionReadChar extends GattTransaction {
  private static final String TAG = "PendingGattRead";
  private static final byte EOF_BYTE = (byte) 0xff;

  private final Base64.Encoder b64Encoder = Base64.getEncoder();
  private final String serviceUuid;
  private final String charUuid;
  /** total bytes received from device */
  private int receivedBytes = 0;
  private byte[] data;


  TransactionReadChar(String transactionId, Promise promise, EventEmitter emitter, BluetoothGatt gatt, String serviceUuid, String charUuid, int totalSize) {
    super(transactionId, promise, emitter, gatt);
    this.serviceUuid = serviceUuid;
    this.charUuid = charUuid;
    if (totalSize != 0) {
      this.data = new byte[totalSize];
    }
  }

  /**
   * Callback called when a chunk of data is received from the BLE stack
   *
   * @param bytes the bytes received
   * @return true if more data has to be received
   */
  private boolean onChunk(byte[] bytes) {
    if (data != null) {
      if (data.length == 1 && data[0] == EOF_BYTE) {
        Log.i(TAG, "read a message of 1 byte 0xff: reached EOF");

        return false;
      } else {
        for (byte b : bytes) {
          if (receivedBytes < data.length) {
            data[receivedBytes++] = b;
          } else {
            Log.w(TAG, "overflow of data array. Skip exceeding data");
          }
        }
        return receivedBytes < data.length;
      }
    } else {
      data = bytes;
      receivedBytes = bytes.length;
    }

    return false;
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
    boolean hasMoreChunks = onChunk(characteristic.getValue());
    if (hasMoreChunks) {
      Log.i(TAG, "need to read another chunk of data");

      emitter.emit(new RNEventProgress(id(), characteristic, receivedBytes, data.length));

      sendReadChunk(characteristic);
    } else {
      Log.i(TAG, "all data read :)");

      succeed(b64Encoder.encodeToString(Arrays.copyOf(data, receivedBytes)));
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

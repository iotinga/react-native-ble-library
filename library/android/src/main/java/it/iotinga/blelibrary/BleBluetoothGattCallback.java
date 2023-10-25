package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import java.util.Base64;

@RequiresApi(api = Build.VERSION_CODES.O)
public class BleBluetoothGattCallback extends BluetoothGattCallback {
  private static final String TAG = "BleGattCallback";

  private final EventEmitter eventEmitter;
  private final ConnectionContext connectionContext;
  private final Base64.Encoder b64Encoder = Base64.getEncoder();

  BleBluetoothGattCallback(EventEmitter eventEmitter, ConnectionContext connectionContext) {
    this.eventEmitter = eventEmitter;
    this.connectionContext = connectionContext;
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  @Override
  public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
    Log.d(TAG, "onConnectionStateChange - status: " + status + ", newState: " + newState);

    if (newState == BluetoothProfile.STATE_CONNECTING) {
      Log.i(TAG, "connecting to device...");
    }

    if (newState == BluetoothProfile.STATE_DISCONNECTING) {
      Log.i(TAG, "disconnecting from device...");
    }

    if (newState == BluetoothProfile.STATE_CONNECTED) {
      Integer requestMtu = connectionContext.getRequestedMtu();
      if (requestMtu != null) {
        connectionContext.setConnectionState(ConnectionState.EXCHANGING_MTU);
        gatt.requestMtu(requestMtu);
      } else {
        connectionContext.setConnectionState(ConnectionState.DISCOVERING_SERVICES);
        gatt.discoverServices();
      }
    }

    if (newState == BluetoothProfile.STATE_DISCONNECTED) {
      eventEmitter.emit(EventType.DISCONNECTED);
      connectionContext.reset();
    }
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  @Override
  public void onMtuChanged(@NonNull BluetoothGatt gatt, int mtu, int status) {
    Log.d(TAG, "onMtuChanged - status: " + status + ", mtu: " + mtu);

    if (status == BluetoothGatt.GATT_SUCCESS) {
      Log.i(TAG, "MTU set to: " + mtu);

      WritableMap payload = Arguments.createMap();
      payload.putInt("mtu", mtu);
      eventEmitter.emit(EventType.MTU_CHANGED, payload);
      connectionContext.setConnectionState(ConnectionState.DISCOVERING_SERVICES);
      gatt.discoverServices();
    } else {
      Log.w(TAG, "cannot set MTU, status: " + status);

      // reset the connection
      gatt.disconnect();
    }
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  @Override
  public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
    Log.d(TAG, "onServiceDiscovered - status: " + status);

    if (status == BluetoothGatt.GATT_SUCCESS) {
      Log.i(TAG, "service discovery ok");

      eventEmitter.emit(EventType.CONNECTED);
      connectionContext.setConnectionState(ConnectionState.CONNECTED);
    } else {
      Log.w(TAG, "service discovery error");

      // from this point I think that is best to reset the connection
      gatt.disconnect();
    }
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  @Override
  public void onCharacteristicWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
    Log.d(TAG, "onCharacteristicWrite - status: " + status);

    String serviceUuid = characteristic.getService().getUuid().toString();
    String charUuid = characteristic.getUuid().toString();

    if (status == BluetoothGatt.GATT_SUCCESS) {
      WritableMap payload = Arguments.createMap();
      payload.putString("service", serviceUuid);
      payload.putString("characteristic", charUuid);

      ChunkedWriteSplitter chunkedWriter = connectionContext.getChunkedWrite(charUuid);
      if (chunkedWriter != null) {
        // we are writing this characteristic in chunks. See if another chunk needs to be
        // written
        if (chunkedWriter.hasNextChunk()) {
          // signal progress to the application
          payload.putInt("total", chunkedWriter.getTotalBytes());
          payload.putInt("current", chunkedWriter.getWrittenBytes());
          payload.putInt("remaining", chunkedWriter.getRemainingBytes());
          eventEmitter.emit(EventType.WRITE_PROGRESS, payload);

          // need to write another chunk of data
          byte[] nextChunk = chunkedWriter.getNextChunk();
          characteristic.setValue(nextChunk);
          boolean result = gatt.writeCharacteristic(characteristic);
          if (!result) {
            eventEmitter.emitError(ErrorType.WRITE_ERROR, "error writing characteristic");
          }
        } else {
          // finished writing characteristic. Signal success to the application.
          eventEmitter.emit(EventType.WRITE_COMPLETED, payload);
          connectionContext.setChunkedWrite(charUuid, null);
        }
      } else {
        // not a chunked write. Signal success to the application
        eventEmitter.emit(EventType.WRITE_COMPLETED, payload);
      }
    } else {
      Log.e(TAG, "error writing char: " + status);
      eventEmitter.emitError(ErrorType.WRITE_ERROR, "native status code: " + status);
    }
  }

  @Override
  public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
    Log.d(TAG, "onCharacteristicRead - status: " + status);

    String serviceUuid = characteristic.getService().getUuid().toString();
    String charUuid = characteristic.getUuid().toString();

    WritableMap payload = Arguments.createMap();
    payload.putString("service", serviceUuid);
    payload.putString("characteristic", charUuid);

    if (status == BluetoothGatt.GATT_SUCCESS) {
      ChunkedReadComposer chunkedRead = connectionContext.getChunkedRead(charUuid);
      if (chunkedRead != null) {
        chunkedRead.putChunk(characteristic.getValue());

        if (chunkedRead.hasMoreChunks()) {
          // need to receive more chunks, emit progress event
          payload.putInt("current", chunkedRead.getReceivedBytes());
          payload.putInt("total", chunkedRead.getTotalBytes());
          eventEmitter.emit(EventType.READ_COMPLETED, payload);
        } else {
          // chunked read has finished, emit final event
          payload.putString("value", b64Encoder.encodeToString(chunkedRead.getBytes()));
          eventEmitter.emit(EventType.CHAR_VALUE_CHANGED, payload);
          connectionContext.setChunkedRead(charUuid, null);
        }
      } else {
        // normal read, send response
        payload.putString("value", b64Encoder.encodeToString(characteristic.getValue()));
        eventEmitter.emit(EventType.READ_COMPLETED, payload);
      }
    } else {
      eventEmitter.emitError(ErrorType.READ_ERROR, "native status: " + status, payload);
      connectionContext.setChunkedRead(charUuid, null);
    }
  }

  @Override
  public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
    Log.d(TAG, "onCharacteristicChanged");

    WritableMap payload = Arguments.createMap();
    payload.putString("service", characteristic.getService().getUuid().toString());
    payload.putString("characteristic", characteristic.getUuid().toString());
    payload.putString("value", b64Encoder.encodeToString(value));
    eventEmitter.emit(EventType.CHAR_VALUE_CHANGED, payload);
  }
}

package it.iotinga.blelibrary;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

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

  @SuppressLint("MissingPermission")
  @Override
  public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
    super.onConnectionStateChange(gatt, status, newState);

    Log.i(TAG, "connection state changed from " + status + " to " + newState);

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
      connectionContext.setConnectionState(ConnectionState.DISCONNECTED);
      connectionContext.setGattLink(null);
      connectionContext.setRequestedMtu(null);
    }
  }

  @SuppressLint("MissingPermission")
  public void onMtuChanged(@NonNull BluetoothGatt gatt, int mtu, int status) {
    Log.i(TAG, "MTU set to: " + mtu + "(status: " + status + ")");

    WritableMap payload = Arguments.createMap();
    payload.putInt("mtu", mtu);
    eventEmitter.emit(EventType.MTU_CHANGED, payload);
    connectionContext.setConnectionState(ConnectionState.DISCOVERING_SERVICES);
    gatt.discoverServices();
  }

  @Override
  public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
      Log.i(TAG, "service discovery ok");

      eventEmitter.emit(EventType.CONNECTED);
      connectionContext.setConnectionState(ConnectionState.CONNECTED);
    } else {
      Log.e(TAG, "service discovery error");
    }
  }

  @SuppressLint("MissingPermission")
  @Override
  public void onCharacteristicWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
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
  public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
      WritableMap payload = Arguments.createMap();
      payload.putString("service", characteristic.getService().getUuid().toString());
      payload.putString("characteristic", characteristic.getUuid().toString());
      payload.putString("value", b64Encoder.encodeToString(value));
      eventEmitter.emit(EventType.CHAR_VALUE_CHANGED, payload);
    } else {
      eventEmitter.emitError(ErrorType.READ_ERROR, "native status: " + status);
    }
  }

  @Override
  public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
    WritableMap payload = Arguments.createMap();
    payload.putString("service", characteristic.getService().getUuid().toString());
    payload.putString("characteristic", characteristic.getUuid().toString());
    payload.putString("value", b64Encoder.encodeToString(value));
    eventEmitter.emit(EventType.CHAR_VALUE_CHANGED, payload);
  }
}

package it.iotinga.blelibrary;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.content.Context;

import com.facebook.react.bridge.ReadableMap;

public class CommandConnect implements Command {
  private final EventEmitter eventEmitter;
  private final BluetoothAdapter bluetoothAdapter;
  private final BluetoothGattCallback bluetoothGattCallback;
  private final Context context;

  private final ConnectionContext connectionContext;

  CommandConnect(EventEmitter eventemitter, Context context, BluetoothAdapter bluetoothAdapter, BluetoothGattCallback bluetoothGattCallback, ConnectionContext connectionContext) {
    this.eventEmitter = eventemitter;
    this.context = context;
    this.bluetoothAdapter = bluetoothAdapter;
    this.bluetoothGattCallback = bluetoothGattCallback;
    this.connectionContext = connectionContext;
  }

  @SuppressLint("MissingPermission")
  @Override
  public void execute(ReadableMap command) {
    String id = command.getString("id");
    if (id == null) {
      throw new RuntimeException("missing id field");
    }

    try {
      BluetoothDevice device = bluetoothAdapter.getRemoteDevice(id);
      BluetoothGatt gatt = device.connectGatt(context, false, bluetoothGattCallback);

      connectionContext.setGattLink(gatt);
      connectionContext.setConnectionState(ConnectionState.CONNECTING);
    } catch (IllegalArgumentException exception) {
      eventEmitter.emitError(ErrorType.CONNECT_ERROR, "device with such ID not found");
    }
  }
}

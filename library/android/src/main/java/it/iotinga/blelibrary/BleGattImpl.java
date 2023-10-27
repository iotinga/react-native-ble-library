package it.iotinga.blelibrary;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import androidx.annotation.RequiresPermission;

import java.util.UUID;

public class BleGattImpl implements BleGatt {
  private final BluetoothAdapter adapter;
  private final BluetoothGattCallback callback;
  private final ConnectionContext context;
  private final EventEmitter emitter;
  private final Context appContext;

  private BluetoothGatt gatt;

  public BleGattImpl(BluetoothAdapter adapter, BluetoothGattCallback callback, ConnectionContext context, EventEmitter emitter, Context appContext) {
    this.adapter = adapter;
    this.callback = callback;
    this.context = context;
    this.emitter = emitter;
    this.appContext = appContext;
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void connect(AsyncOperation operation, String id, int mtu) throws BleException {
    if (context.getConnectionState() == ConnectionState.DISCONNECTED) {
      try {
        BluetoothDevice device = adapter.getRemoteDevice(id);

        context.setPendingGattOperation(new PendingGattConnect(emitter, operation, mtu, context));
        gatt = device.connectGatt(appContext, false,  callback);
        context.setConnectionState(ConnectionState.CONNECTING);
      } catch (Exception exception) {
        context.setPendingGattOperation(null);
        throw exception;
      }
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void disconnect(AsyncOperation operation) throws BleException {
    if (context.getConnectionState() == ConnectionState.CONNECTED) {
      if (gatt == null) {
        throw new BleException("InternalError", "gatt is undefined");
      }
      try {
        context.setPendingGattOperation(new PendingGattDisconnect(emitter, operation));
        gatt.disconnect();
        context.setConnectionState(ConnectionState.DISCONNECTING);
      } catch (Exception exception) {
        context.setPendingGattOperation(null);
        throw exception;
      }
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void read(AsyncOperation operation, String serviceUuid, String characteristicUuid, int size) throws BleException {
    if (context.getConnectionState() != ConnectionState.CONNECTED || gatt == null) {
      throw new BleException("NotConnectedError", "not connected");
    }
    BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUuid, characteristicUuid);

    try {
      PendingGattRead read = new PendingGattRead(emitter, operation, size);
      context.setPendingGattOperation(read);
      read.doRead(gatt, characteristic);
    } catch (Exception exception) {
      context.setPendingGattOperation(null);
      throw exception;
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void write(AsyncOperation operation, String serviceUuid, String characteristicUuid, byte[] value, int chunkSize) throws BleException {
    if (context.getConnectionState() != ConnectionState.CONNECTED || gatt == null) {
      throw new BleException("NotConnectedError", "not connected");
    }
    BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUuid, characteristicUuid);

    try {
      PendingGattWrite write = new PendingGattWrite(emitter, operation, value, chunkSize);
      context.setPendingGattOperation(write);
      write.doWrite(gatt, characteristic);
    } catch (Exception exception) {
      context.setPendingGattOperation(null);
      throw exception;
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void subscribe(String serviceUuid, String characteristicUuid) throws BleException {
    if (context.getConnectionState() != ConnectionState.CONNECTED || gatt == null) {
      throw new BleException("NotConnectedError", "not connected");
    }
    BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUuid, characteristicUuid);

    boolean result = gatt.setCharacteristicNotification(characteristic, true);
    if (!result) {
      throw new BleException("GattError", "setCharacteristicNotification failed");
    }
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void unsubscribe(String serviceUuid, String characteristicUuid) throws BleException {
    if (context.getConnectionState() != ConnectionState.CONNECTED || gatt == null) {
      throw new BleException("NotConnectedError", "not connected");
    }
    BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUuid, characteristicUuid);

    boolean result = gatt.setCharacteristicNotification(characteristic, false);
    if (!result) {
      throw new BleException("GattError", "setCharacteristicNotification failed");
    }
  }

  private BluetoothGattCharacteristic getCharacteristic(String serviceUuid, String characteristicUuid) throws BleException {
    BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
    if (service == null) {
      throw new BleException("GattError", "service not found");
    }

    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
    if (characteristic == null) {
      throw new BleException("GattError", "characteristic not found");
    }

    return characteristic;
  }
}
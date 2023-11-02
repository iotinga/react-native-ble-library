package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

public class PendingGattConnect extends PendingGattOperation {
  private final int requestMtu;
  private final ConnectionContext context;

  PendingGattConnect(EventEmitter emitter, AsyncOperation operation, int requestMtu, ConnectionContext context) {
    super(emitter, operation);
    this.requestMtu = requestMtu;
    this.context = context;
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  void onConnected(BluetoothGatt gatt) {
    boolean result;
    if (requestMtu > 0) {
      result = gatt.requestMtu(requestMtu);
    } else {
      result = gatt.discoverServices();
    }
    if (!result) {
      context.setConnectionState(ConnectionState.DISCONNECTED);
      operation.fail(new BleException(BleException.ERROR_GATT, "operation returned false"));
    }
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  void onMtuChanged(BluetoothGatt gatt) {
    boolean result = gatt.discoverServices();
    if (!result) {
      context.setConnectionState(ConnectionState.DISCONNECTED);
      operation.fail(new BleException(BleException.ERROR_GATT, "operation returned false"));
    }
  }

  @Override
  void onServiceDiscovered(BluetoothGatt gatt) {
    context.setConnectionState(ConnectionState.CONNECTED);

    WritableArray services = Arguments.createArray();
    for (BluetoothGattService service : gatt.getServices()) {
      WritableArray characteristics = Arguments.createArray();
      for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
        WritableMap data = Arguments.createMap();
        data.putString("uuid", characteristic.getUuid().toString().toLowerCase());
        data.putInt("properties", characteristic.getProperties());
        characteristics.pushMap(data);
      }

      WritableMap data = Arguments.createMap();
      data.putString("uuid", service.getUuid().toString().toLowerCase());
      data.putBoolean("isPrimary", service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY);
      data.putArray("characteristics", characteristics);
      services.pushMap(data);
    }

    WritableMap data = Arguments.createMap();
    data.putArray("services", services);
    operation.complete(data);
  }
}

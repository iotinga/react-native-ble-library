package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import com.facebook.react.bridge.Promise;

import java.util.UUID;

public abstract class GattTransaction extends PromiseTransaction {
  private static final String STANDARD_UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB";
  protected final EventEmitter emitter;
  protected final BluetoothGatt gatt;

  GattTransaction(String id, Promise promise, EventEmitter emitter, BluetoothGatt gatt) {
    super(id, promise);
    this.emitter = emitter;
    this.gatt = gatt;
  }

  void onCharRead(BluetoothGattCharacteristic characteristic) {
    fail(BleError.ERROR_INVALID_STATE, "unexpected onCharRead");
  }

  void onCharWrite(BluetoothGattCharacteristic characteristic) {
    fail(BleError.ERROR_INVALID_STATE, "unexpected charWrite");
  }

  void onError(int gattError) {
    fail(BleError.ERROR_GATT, "GATT error code " + gattError);
  }

  void onReadRemoteRssi(int rssi) {
    fail(BleError.ERROR_INVALID_STATE, "unexpected charWrite");
  }

  public static UUID bleUuidFromString(String uuid) {
    String result;
    if (uuid.length() == 4) {
      // 16 bit uuid
      result = "0000" + uuid + STANDARD_UUID_SUFFIX;
    } else if (uuid.length() == 8) {
      // 32 bit uuid
      result = uuid + STANDARD_UUID_SUFFIX;
    } else {
      // 128 bit uuid
      result = uuid;
    }

    return UUID.fromString(result);
  }

  public static BluetoothGattCharacteristic getCharacteristic(BluetoothGatt gatt, String serviceUuid, String characteristicUuid) {

    BluetoothGattService service = gatt.getService(bleUuidFromString(serviceUuid));
    if (service == null) {
      return null;
    }

    return service.getCharacteristic(bleUuidFromString(characteristicUuid));
  }

  protected BluetoothGattCharacteristic getCharacteristic(String serviceUuid, String characteristicUuid) {
    return getCharacteristic(gatt, serviceUuid, characteristicUuid);
  }
}

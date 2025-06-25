package it.iotinga.blelibrary;

import android.bluetooth.BluetoothGatt;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.Promise;

public class TransactionReadRssi extends GattTransaction {

  TransactionReadRssi(String transactionId, Promise promise, EventEmitter emitter, BluetoothGatt gatt) {
    super(transactionId, promise, emitter, gatt);
  }

  @Override
  @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
  public void start() {
    super.start();

    boolean success = gatt.readRemoteRssi();
    if (success) {
      Log.i(Constants.LOG_TAG, "requested read remote RSSI");
    } else {
      Log.w(Constants.LOG_TAG, "error requesting read remote RSSI");

      fail(BleError.ERROR_GATT, "error requesting remote RSSI");
    }
  }

  @Override
  void onReadRemoteRssi(int rssi) {
    succeed(rssi);
  }
}

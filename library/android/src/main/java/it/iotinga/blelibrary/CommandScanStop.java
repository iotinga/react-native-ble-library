package it.iotinga.blelibrary;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;

import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.ReadableMap;

public class CommandScanStop implements Command {
  private final BluetoothLeScanner bluetoothLeScanner;
  private final ScanCallback scanCallback;

  CommandScanStop(BluetoothLeScanner bluetoothLeScanner, ScanCallback scanCallback) {
    this.bluetoothLeScanner = bluetoothLeScanner;
    this.scanCallback = scanCallback;
  }

  @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
  @Override
  public void execute(ReadableMap command, AsyncOperation operation) {
    bluetoothLeScanner.stopScan(scanCallback);
    operation.complete();
  }
}


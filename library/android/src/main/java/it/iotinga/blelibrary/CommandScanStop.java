package it.iotinga.blelibrary;

import android.annotation.SuppressLint;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;

import com.facebook.react.bridge.ReadableMap;

public class CommandScanStop implements Command {
  private final BluetoothLeScanner bluetoothLeScanner;
  private final ScanCallback scanCallback;

  CommandScanStop(BluetoothLeScanner bluetoothLeScanner, ScanCallback scanCallback) {
    this.bluetoothLeScanner = bluetoothLeScanner;
    this.scanCallback = scanCallback;
  }

  @SuppressLint("MissingPermission")
  @Override
  public void execute(ReadableMap command) {
    bluetoothLeScanner.stopScan(scanCallback);
  }
}

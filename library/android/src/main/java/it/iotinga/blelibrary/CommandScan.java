package it.iotinga.blelibrary;

import android.annotation.SuppressLint;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;

import java.util.ArrayList;
import java.util.List;

public class CommandScan implements Command {
  private final BluetoothLeScanner bluetoothLeScanner;
  private final ScanCallback scanCallback;

  CommandScan(BluetoothLeScanner bluetoothLeScanner, ScanCallback scanCallback) {
    this.bluetoothLeScanner = bluetoothLeScanner;
    this.scanCallback = scanCallback;
  }

  @SuppressLint("MissingPermission")
  @Override
  public void execute(ReadableMap command) {
    ReadableArray serviceUuids = command.getArray("serviceUuids");
    if (serviceUuids == null) {
      throw new RuntimeException("missing serviceUuids field");
    }

    // build a list of filters
    List<ScanFilter> filters = new ArrayList<>();
    for (int i = 0; i < serviceUuids.size(); i++) {
      String serviceUuid = serviceUuids.getString(i);
      ParcelUuid uuid = ParcelUuid.fromString(serviceUuid);
      filters.add(new ScanFilter.Builder().setServiceUuid(uuid).build());
    }

    ScanSettings settings = new ScanSettings.Builder().build();

    bluetoothLeScanner.startScan(filters, settings, scanCallback);
  }
}

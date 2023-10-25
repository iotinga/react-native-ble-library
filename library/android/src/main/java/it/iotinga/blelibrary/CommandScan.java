package it.iotinga.blelibrary;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.ParcelUuid;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;

import java.util.ArrayList;
import java.util.List;

public class CommandScan implements Command {
  private static final int SCAN_DELAY_MS = 500;
  private final BluetoothLeScanner bluetoothLeScanner;
  private final ScanCallback scanCallback;

  CommandScan(BluetoothLeScanner bluetoothLeScanner, ScanCallback scanCallback) {
    this.bluetoothLeScanner = bluetoothLeScanner;
    this.scanCallback = scanCallback;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")

  @Override
  public void execute(ReadableMap command) {
    ReadableArray serviceUuids = command.getArray("serviceUuids");
    List<ScanFilter> filters = null;
    if (serviceUuids != null) {
      filters = new ArrayList<>();
      for (int i = 0; i < serviceUuids.size(); i++) {
        String serviceUuid = serviceUuids.getString(i);
        ParcelUuid uuid = ParcelUuid.fromString(serviceUuid);
        filters.add(new ScanFilter.Builder().setServiceUuid(uuid).build());
      }
    }

    ScanSettings settings = new ScanSettings.Builder()
      .setReportDelay(SCAN_DELAY_MS)
      .build();

    bluetoothLeScanner.startScan(filters, settings, scanCallback);
  }
}

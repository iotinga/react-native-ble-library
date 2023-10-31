package it.iotinga.blelibrary;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.ReactApplicationContext;

public class BleActivationManagerImpl implements BleActivationManager, ActivityEventListener {
  private static String TAG = "BleActivationManagerImpl";

  private final static int REQUEST_ENABLE_BT = 1;
  private final BluetoothAdapter adapter;
  private final ReactApplicationContext context;
  private BleActivationCallback callback;

  public BleActivationManagerImpl(BluetoothAdapter adapter, ReactApplicationContext context) {
    this.adapter = adapter;
    this.context = context;
  }

  @Override
  @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
  public void ensureBleActive(BleActivationCallback callback) {
    if (adapter.isEnabled()) {
      Log.i(TAG, "Bluetooth is active :)");
      callback.onResult(true);
    } else {
      Activity activity = context.getCurrentActivity();
      if (activity == null) {
        Log.w(TAG, "cannot show Bluetooth turn on request since activity is null");

        callback.onResult(false);
      } else {
        Log.d(TAG, "show Bluetooth activation request UI");

        this.callback = callback;
        context.addActivityEventListener(this);

        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
      }
    }
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, @Nullable Intent intent) {
    if (requestCode == REQUEST_ENABLE_BT) {
      Log.i(TAG, "Bluetooth activation response: " + resultCode);

      context.removeActivityEventListener(this);

      if (callback != null) {
        callback.onResult(resultCode == Activity.RESULT_OK);
        callback = null;
      }
    }
  }

  @Override
  public void onNewIntent(Intent intent) {}
}

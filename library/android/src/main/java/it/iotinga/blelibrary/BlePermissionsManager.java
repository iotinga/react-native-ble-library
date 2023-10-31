package it.iotinga.blelibrary;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;

import java.util.ArrayList;
import java.util.List;

public class BlePermissionsManager implements PermissionManager, PermissionListener {
  private static final String TAG = "BlePermissionsManager";
  private static final int PERMISSION_REQUEST_CODE = 2;

  private final List<String> permissions;
  private final ReactApplicationContext context;
  private PermissionManagerCheckCallback callback;

  public BlePermissionsManager(ReactApplicationContext context) {
    this.context = context;
    this.permissions = new ArrayList<>();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      this.permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
      this.permissions.add(Manifest.permission.BLUETOOTH_SCAN);
    } else {
      this.permissions.add(Manifest.permission.BLUETOOTH);
      this.permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
    }
  }

  private List<String> getMissingPermissions() {
    List<String> missingPermissions = new ArrayList<>();

    for (String permission : permissions) {
      if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
        missingPermissions.add(permission);
        Log.w(TAG, "missing permission: " + permission);
      } else {
        Log.d(TAG, "granted permissions: " + permission);
      }
    }

    return missingPermissions;
  }

  @Override
  public void ensure(PermissionManagerCheckCallback callback) {
    List<String> missingPermissions = getMissingPermissions();
    if (missingPermissions.size() == 0) {
      Log.i(TAG, "all permissions granted :)");
      callback.onPermissionResponse(true);
    } else {
      PermissionAwareActivity activity = (PermissionAwareActivity) context.getCurrentActivity();
      if (activity == null) {
        Log.w(TAG, "request permissions since activity is null");

        callback.onPermissionResponse(false);
      } else {
        Log.d(TAG, "requesting permissions: " + missingPermissions);

        this.callback = callback;
        activity.requestPermissions(missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE, this);
      }
    }
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] responses) {
    if (requestCode == PERMISSION_REQUEST_CODE) {
      Log.d(TAG, "permissions request callback: " + requestCode);

      boolean allPermissionsGranted = true;
      for (int i = 0; i < permissions.length; i++) {
        if (responses[i] != PackageManager.PERMISSION_GRANTED) {
          Log.w(TAG, "permission not granted: " + permissions[i]);
          allPermissionsGranted = false;
        } else {
          Log.d(TAG, "permission granted: " + permissions[i]);
        }
      }

      if (callback != null) {
        callback.onPermissionResponse(allPermissionsGranted);
        callback = null;
      }
    }

    return true;
  }
}

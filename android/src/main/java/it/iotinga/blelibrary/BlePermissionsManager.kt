package it.iotinga.blelibrary

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import expo.modules.interfaces.permissions.Permissions
import expo.modules.interfaces.permissions.PermissionsStatus

class BlePermissionsManager(
  private val context: Context,
  private val permissionManager: Permissions
) {
  private val permissions: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
  } else {
    listOf(
      Manifest.permission.BLUETOOTH,
      Manifest.permission.BLUETOOTH_ADMIN,
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION
    )
  }

  fun ensure(callback: (granted: Boolean) -> Unit) {
    val missingPermissions: MutableList<String?> = ArrayList()

    for (permission in permissions) {
      if (ActivityCompat.checkSelfPermission(
          context,
          permission
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        missingPermissions.add(permission)
        Log.w(
          LOG_TAG,
          "missing permission: $permission"
        )
      } else {
        Log.d(
          LOG_TAG,
          "granted permissions: $permission"
        )
      }
    }
    if (missingPermissions.isEmpty()) {
      Log.i(LOG_TAG, "all permissions granted :)")
      callback(true)
    } else {
      Log.d(LOG_TAG, "requesting permissions: $missingPermissions")

      permissionManager.askForPermissions({ result ->
        Log.d(LOG_TAG, "permissions request callback: $result")

        var allPermissionsGranted = true
        for (permission in permissions) {
          if (result[permission]?.status != PermissionsStatus.GRANTED) {
            Log.w(LOG_TAG, "permission not granted: $permission")
            allPermissionsGranted = false
          } else {
            Log.d(LOG_TAG, "permission granted: $permission")
          }
        }

        callback(allPermissionsGranted)

      }, *missingPermissions.toTypedArray())
    }
  }
}

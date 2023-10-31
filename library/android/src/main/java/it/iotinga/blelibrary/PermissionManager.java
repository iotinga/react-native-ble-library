package it.iotinga.blelibrary;

public interface PermissionManager {
  void ensure(PermissionManagerCheckCallback callback);
}

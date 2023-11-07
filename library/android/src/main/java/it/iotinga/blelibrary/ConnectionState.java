package it.iotinga.blelibrary;

public enum ConnectionState {
  CONNECTING_TO_DEVICE,
  REQUESTING_MTU,
  DISCOVERING_SERVICES,
  CONNECTED,
  DISCONNECTING,
  DISCONNECTED,
}

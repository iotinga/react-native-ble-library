package it.iotinga.blelibrary

enum class ConnectionState {
    CONNECTING_TO_DEVICE,
    REQUESTING_MTU,
    DISCOVERING_SERVICES,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
}

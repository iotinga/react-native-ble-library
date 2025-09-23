package it.iotinga.blelibrary

enum class Event(val value: String) {
  ERROR("onError"),
  SCAN_RESULT("onScanResult"),
  CHAR_VALUE_CHANGED("onCharValueChanged"),
  PROGRESS("onProgress"),
  CONNECTION_STATE_CHANGED("onConnectionStateChanged"),
}

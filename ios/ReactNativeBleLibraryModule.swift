//
//  BleLibrary.swift
//
//  Expo Modules translation of the original Objective-C React Native module.
//  This single Swift file preserves the same public API (method names, parameters,
//  events and payload shapes) and functionality.
//
//  Notes:
//  - Requires: ExpoModulesCore, CoreBluetooth
//  - Exposes the same events:
//      ERROR, SCAN_RESULT, CHAR_VALUE_CHANGED, PROGRESS, CONNECTION_STATE_CHANGED
//  - Exposes the same methods (names and arguments preserved):
//      initModule(), disposeModule(), cancel(transactionId)
//      scanStart(serviceUuids), scanStop()
//      connect(deviceId, mtu), disconnect()
//      readRSSI(transactionId)
//      write(transactionId, serviceUuid, characteristic, value, chunkSize)
//      read(transactionId, serviceUuid, characteristic, size)
//      subscribe(transactionId, serviceUuid, characteristic)
//      unsubscribe(transactionId, serviceUuid, characteristic)
//
//  The original Objective-C helper classes (Transaction, PendingRead, PendingWrite) and
//  the string constants are translated inline below.
//
//  Caveats:
//  - Just like the original code, this file assumes that service/characteristic discovery
//    completes before read/write/subscribe operations are attempted.
//  - MTU negotiation (REQUESTING_MTU state) is not used by CoreBluetooth (kept for API parity).
//

import CoreBluetooth
import ExpoModulesCore
import Foundation

// MARK: - Constants (from BleNativeEvent.h, ConnectionState.h, BleErrorCode.h)

private let EVENT_ERROR = "onError"
private let EVENT_SCAN_RESULT = "onScanResult"
private let EVENT_CHAR_VALUE_CHANGED = "onCharValueChanged"
private let EVENT_PROGRESS = "onProgress"
private let EVENT_CONNECTION_STATE_CHANGED = "onConnectionStateChanged"

private let STATE_CONNECTING_TO_DEVICE = "CONNECTING_TO_DEVICE"
private let STATE_REQUESTING_MTU = "REQUESTING_MTU"
private let STATE_DISCOVERING_SERVICES = "DISCOVERING_SERVICES"
private let STATE_CONNECTED = "CONNECTED"
private let STATE_DISCONNECTING = "DISCONNECTING"
private let STATE_DISCONNECTED = "DISCONNECTED"

private let ERROR_GENERIC = "ERROR_GENERIC"
private let ERROR_BLE_NOT_SUPPORTED = "ERROR_BLE_NOT_SUPPORTED"
private let ERROR_MISSING_PERMISSIONS = "ERROR_MISSING_PERMISSIONS"
private let ERROR_BLE_NOT_ENABLED = "ERROR_BLE_NOT_ENABLED"
private let ERROR_INVALID_ARGUMENTS = "ERROR_INVALID_ARGUMENTS"
private let ERROR_DEVICE_NOT_FOUND = "ERROR_DEVICE_NOT_FOUND"
private let ERROR_OPERATION_CANCELED = "ERROR_OPERATION_CANCELED"
private let ERROR_SCAN = "ERROR_SCAN"
private let ERROR_INVALID_STATE = "ERROR_INVALID_STATE"
private let ERROR_GATT = "ERROR_GATT"
private let ERROR_NOT_CONNECTED = "ERROR_NOT_CONNECTED"
private let ERROR_NOT_INITIALIZED = "ERROR_NOT_INITIALIZED"

// MARK: - Transaction primitives (Swift translation)

// Matches Transaction.h/.m
private enum TransactionState {
  case executing
  case canceled
  case succeeded
  case failed
}

private class Transaction {
  private(set) var state: TransactionState = .executing
  var isCompleted: Bool { state != .executing }
  let transactionId: String
  private let promise: Promise

  init(transactionId: String, promise: Promise) {
    self.transactionId = transactionId
    self.promise = promise
  }

  func cancel() {
    if !isCompleted {
      promise.reject(
        ERROR_OPERATION_CANCELED,
        "the current transaction was canceled"
      )
      state = .canceled
    }
  }

  func succeed(_ payload: Any?) {
    if !isCompleted {
      promise.resolve(payload)
      state = .succeeded
    }
  }

  func fail(_ code: String, _ message: String) {
    if !isCompleted {
      promise.reject(code, message)
      state = .failed
    }
  }

  deinit {
    // Ensure promise is canceled when object is de-allocated
    cancel()
  }
}

// Matches PendingRead.h/.m
private final class PendingRead: Transaction {
  // Total bytes to read
  let size: UInt
  // Data read so far
  private(set) var data = Data()
  // Whether more data must be received (updated in putChunk)
  private(set) var hasMoreData = true

  var readCount: Int { data.count }
  private let EOF_BYTE: UInt8 = 0xff

  init(
    transactionId: String,
    promise: Promise,
    size: UInt
  ) {
    self.size = size
    super.init(transactionId: transactionId, promise: promise)
    self.hasMoreData = true
  }

  func putChunk(_ chunk: Data) {
    // Emulate the Objective-C logic: if we already have exactly 1 byte and it's EOF (0xFF), stop.
    if data.count == 1 {
      var byte: UInt8 = 0
      data.copyBytes(to: &byte, count: 1)
      if byte == EOF_BYTE {
        hasMoreData = false
        return
      }
    }
    data.append(chunk)
    hasMoreData = UInt(data.count) < size
  }
}

// Matches PendingWrite.h/.m
private final class PendingWrite: Transaction {
  let data: Data
  let size: Int
  private(set) var written: Int = 0
  let chunkSize: Int

  var hasMoreChunks: Bool { written < size }

  init(
    transactionId: String,
    promise: Promise,
    data: Data,
    chunkSize: UInt
  ) {
    self.data = data
    self.size = data.count
    self.chunkSize = Int(chunkSize)
    super.init(transactionId: transactionId, promise: promise)
  }

  func getChunk() -> Data? {
    guard hasMoreChunks else { return nil }
    var length = chunkSize
    let remaining = size - written
    if length > remaining { length = remaining }
    let range = written..<(written + length)
    written += length
    return data.subdata(in: range)
  }
}

// MARK: - Main Expo Module

private final class BleLibraryImpl: NSObject, CBCentralManagerDelegate,
  CBPeripheralDelegate
{
  private var module: Module

  init(module: Module) {
    self.module = module
  }

  // CBCentralManager and current peripheral
  private var manager: CBCentralManager?
  private var peripheral: CBPeripheral?

  // Transactions bookkeeping
  private var initTransaction: Transaction?
  private var readRssiTransaction: Transaction?
  private var transactionById: [String: Transaction] = [:]
  private var readOperationByCharUuid: [String: PendingRead] = [:]
  private var writeOperationByCharUuid: [String: PendingWrite] = [:]
  private var notificationUpdateByCharUuid: [String: Transaction] = [:]

  // Connection promise, that is resolved when the device has connected and discovered services
  private var connectionPromise: Promise?

  // Signals that the whole connection process (connect, service discovery) has completed
  private var connectionSuccess = false

  // Computed properties mirroring the Objective-C getters
  private var isConnected: Bool {
    return manager != nil && peripheral != nil
      && peripheral?.state == .connected
  }

  private var isModuleInitialized: Bool {
    guard let m = manager else { return false }
    return m.state == .poweredOn
  }

  // MARK: - Exported Methods

  func initModule(promise: Promise) {
    print("[BleLibrary] initModule()")
    if self.manager == nil {
      print("[BleLibrary] init manager")
      self.transactionById = [:]
      self.readOperationByCharUuid = [:]
      self.writeOperationByCharUuid = [:]
      self.notificationUpdateByCharUuid = [:]
      self.initTransaction = Transaction(
        transactionId: "_init",
        promise: promise,
      )
      self.manager = CBCentralManager(delegate: self, queue: nil)
    } else {
      print("[BleLibrary] manager already initialized")
      promise.resolve(nil)
    }
  }

  func disposeModule(promise: Promise) {
    print("[BleLibrary] disposeModule()")
    self.dispose()
    promise.resolve(nil)
  }

  func cancel(transactionId: String, promise: Promise) {
    print("[BleLibrary] cancel(\(transactionId)")
    self.transactionById[transactionId]?.cancel()
    promise.resolve(nil)
  }

  // MARK: Scan

  func scanStart(serviceUuids: [String]?, promise: Promise) {
    print("[BleLibrary] scanStart(\(serviceUuids, default: "[]")")
    guard self.isModuleInitialized else {
      print("[BleLibrary] manager is not initialized")
      return promise.reject(
        ERROR_NOT_INITIALIZED,
        "call initModule first"
      )
    }

    if self.manager?.isScanning == true {
      print("[BleLibrary] stopping existing scan...")
      self.manager?.stopScan()
    }

    var services: [CBUUID]? = nil
    if let ids = serviceUuids, !ids.isEmpty {
      services = ids.map { uuid in
        print("[BleLibrary] adding filter for \(uuid)")
        return CBUUID(string: uuid)
      }
    }

    self.manager?.scanForPeripherals(withServices: services, options: [:])
    print("[BleLibrary] scan started")
    promise.resolve(nil)
  }

  func scanStop(promise: Promise) {
    print("[BleLibrary] scanStop()")
    guard self.isModuleInitialized else {
      print("[BleLibrary] manager is not initialized")
      return promise.reject(
        ERROR_NOT_INITIALIZED,
        "call initModule first"
      )
    }
    if self.manager?.isScanning == true {
      print("[BleLibrary] stopping scan")
      self.manager?.stopScan()
    } else {
      print("[BleLibrary] scan not running, nothing to stop!")
    }
    promise.resolve(nil)
  }

  // MARK: Connection

  func connect(
    deviceId: String,
    mtu: Int,
    options: [String: Any]?,
    promise: Promise
  ) {
    print(
      "[BleLibrary] connect(\(deviceId), \(mtu), \(options, default: "null"))"
    )
    guard self.isModuleInitialized else {
      print("[BleLibrary] module is not initialized")
      return promise.reject(
        ERROR_NOT_INITIALIZED,
        "call initModule first"
      )
    }

    if self.isConnected, let p = self.peripheral, let m = self.manager {
      print(
        "[BleLibrary] a peripheral is already connected. Disconnect it first"
      )
      m.cancelPeripheralConnection(p)
    }
    self.peripheral = nil

    // Ensure all transactions are concluded
    self.cancelAllTransactions(
      code: ERROR_OPERATION_CANCELED,
      message: "starting new connection",
      error: nil
    )

    guard let uuid = UUID(uuidString: deviceId) else {
      print("[BleLibrary] invalid UUID")
      return promise.reject(
        ERROR_INVALID_ARGUMENTS,
        "the deviceId must be a valid UUID"
      )
    }

    guard let m = self.manager else {
      return promise.reject(
        ERROR_NOT_INITIALIZED,
        "BLE manager not available"
      )
    }

    let peripherals = m.retrievePeripherals(withIdentifiers: [uuid])
    guard let target = peripherals.first else {
      print("[BleLibrary] peripheral with UUID \(uuid.uuidString) not found")
      return promise.reject(
        ERROR_INVALID_ARGUMENTS,
        "peripheral with such UUID not found"
      )
    }

    self.peripheral = target
    self.peripheral?.delegate = self
    self.connectionSuccess = false
    self.connectionPromise = promise

    print("[BleLibrary] requesting connect")
    m.connect(target, options: nil)

    if let o = options {
      if let timeout = o["timeout"] as? Double {
        print("[BleLibrary] Setting timeout to \(timeout)")
        Timer.scheduledTimer(withTimeInterval: timeout / 1000.0, repeats: false)
        { _ in
          if !self.connectionSuccess {
            print("[BleLibrary] Connection timeout expired")
            m.cancelPeripheralConnection(target)

            if let p = self.connectionPromise {
              p.reject(
                ERROR_OPERATION_CANCELED,
                "timeout while connecting to device"
              )
              self.connectionPromise = nil
            }
          }
        }
      }
    }
  }

  func disconnect(promise: Promise) {
    print("[BleLibrary] disconnect()")

    // Cancel connection promise if present
    if let p = connectionPromise {
      p.reject(ERROR_OPERATION_CANCELED, "Disconnection requested")
      connectionPromise = nil
    }

    if self.isConnected, let m = self.manager, let p = self.peripheral {
      print("[BleLibrary] canceling connection")

      // Ensure all transactions are concluded
      self.cancelAllTransactions(
        code: ERROR_NOT_CONNECTED,
        message: "disconnecting device",
        error: nil
      )

      m.cancelPeripheralConnection(p)
      module.sendEvent(
        EVENT_CONNECTION_STATE_CHANGED,
        [
          "state": STATE_DISCONNECTING,
          "error": NSNull(),
          "message": "disconnecting from peripherial",
          "ios": [:],
        ]
      )
    } else {
      print("[BleLibrary] no peripheral to disconnect")
    }
    promise.resolve(nil)
  }

  // MARK: Read RSSI

  func readRSSI(transactionId: String, promise: Promise) {
    guard self.isConnected, let p = self.peripheral else {
      print("[BleLibrary] device not connected")
      return promise.reject(ERROR_NOT_CONNECTED, "call connect first")
    }

    // cancel any in-progress RSSI transaction
    self.readRssiTransaction?.cancel()

    let t = Transaction(
      transactionId: transactionId,
      promise: promise,
    )
    self.readRssiTransaction = t
    self.transactionById[transactionId] = t
    p.readRSSI()
  }

  // MARK: Write

  func write(
    transactionId: String,
    serviceUuid: String,
    characteristic: String,
    value: String,
    chunkSize: UInt,
    promise: Promise
  ) {
    let serviceUuidLC = serviceUuid.lowercased()
    let charUuidLC = characteristic.lowercased()
    print("[BleLibrary] write(\(serviceUuidLC), \(charUuidLC), \(chunkSize)")

    guard self.isConnected else {
      print("[BleLibrary] device not connected")
      return promise.reject(ERROR_NOT_CONNECTED, "call connect first")
    }

    guard
      let c = self.findCharacteristic(
        uuid: charUuidLC,
        serviceUuid: serviceUuidLC
      )
    else {
      print(
        "[BleLibrary] service \(serviceUuidLC) characteristic \(charUuidLC) not found"
      )
      return promise.reject(
        ERROR_INVALID_ARGUMENTS,
        "characteristic not found on device"
      )
    }

    guard let data = Data(base64Encoded: value) else {
      return promise.reject(
        ERROR_INVALID_ARGUMENTS,
        "value must be base64 string"
      )
    }
    print("[BleLibrary] requesting write for \(data.count) bytes")

    self.cancelPendingTransactionForChar(charUuidLC)

    let write = PendingWrite(
      transactionId: transactionId,
      promise: promise,
      data: data,
      chunkSize: chunkSize
    )

    self.transactionById[transactionId] = write
    self.writeOperationByCharUuid[charUuidLC] = write

    // Start writing first chunk (with response)
    if let chunk = write.getChunk() {
      self.peripheral?.writeValue(chunk, for: c, type: .withResponse)
    } else {
      // Nothing to write, resolve immediately
      write.succeed(Data())
      self.transactionById.removeValue(forKey: transactionId)
      self.writeOperationByCharUuid.removeValue(forKey: charUuidLC)
    }
  }

  // MARK: Read

  func read(
    transactionId: String,
    serviceUuid: String,
    characteristic: String,
    size: UInt,
    promise: Promise
  ) {
    let serviceUuidLC = serviceUuid.lowercased()
    let charUuidLC = characteristic.lowercased()
    print("[BleLibrary] read(\(serviceUuidLC), \(charUuidLC), \(size)")

    guard self.isConnected else {
      print("[BleLibrary] device not connected")
      return promise.reject(ERROR_NOT_CONNECTED, "call connect first")
    }

    guard
      let c = self.findCharacteristic(
        uuid: charUuidLC,
        serviceUuid: serviceUuidLC
      )
    else {
      print(
        "[BleLibrary] service \(serviceUuidLC) characteristic \(charUuidLC) not found"
      )
      return promise.reject(
        ERROR_INVALID_ARGUMENTS,
        "characteristic not found on device"
      )
    }

    // Cancel existing read or write for the same characteristic
    self.cancelPendingTransactionForChar(charUuidLC)

    let read = PendingRead(
      transactionId: transactionId,
      promise: promise,
      size: size
    )
    self.transactionById[transactionId] = read
    self.readOperationByCharUuid[charUuidLC] = read

    // Trigger read (didUpdateValueForCharacteristic will be called)
    self.peripheral?.readValue(for: c)
  }

  // MARK: Notifications

  func subscribe(
    transactionId: String,
    serviceUuid: String,
    characteristic: String,
    promise: Promise
  ) {
    let serviceUuidLC = serviceUuid.lowercased()
    let charUuidLC = characteristic.lowercased()
    print("[BleLibrary] subscribe(\(serviceUuidLC), \(charUuidLC)")

    guard self.isConnected else {
      print("[BleLibrary] device not connected")
      return promise.reject(ERROR_NOT_CONNECTED, "call connect first")
    }

    guard
      let c = self.findCharacteristic(
        uuid: charUuidLC,
        serviceUuid: serviceUuidLC
      )
    else {
      print(
        "[BleLibrary] service \(serviceUuidLC) characteristic \(charUuidLC) not found"
      )
      return promise.reject(
        ERROR_INVALID_ARGUMENTS,
        "characteristic not found on device"
      )
    }

    print(
      "[BleLibrary] waiting for callback didUpdateNotificationStateForCharacteristic"
    )

    if let t = self.notificationUpdateByCharUuid[charUuidLC] {
      t.fail(
        ERROR_INVALID_STATE,
        "another notification change operation is in progress"
      )
      self.transactionById.removeValue(forKey: t.transactionId)
    }

    let t = Transaction(
      transactionId: transactionId,
      promise: promise,
    )
    self.notificationUpdateByCharUuid[charUuidLC] = t
    self.transactionById[transactionId] = t

    self.peripheral?.setNotifyValue(true, for: c)
  }

  func unsubscribe(
    transactionId: String,
    serviceUuid: String,
    characteristic: String,
    promise: Promise
  ) {
    let serviceUuidLC = serviceUuid.lowercased()
    let charUuidLC = characteristic.lowercased()
    print("[BleLibrary] unsubscribe(\(serviceUuidLC), \(charUuidLC)")

    guard self.isConnected else {
      print("[BleLibrary] device not connected")
      return promise.reject(ERROR_NOT_CONNECTED, "call connect first")
    }

    guard
      let c = self.findCharacteristic(
        uuid: charUuidLC,
        serviceUuid: serviceUuidLC
      )
    else {
      print(
        "[BleLibrary] service \(serviceUuidLC) characteristic \(charUuidLC) not found"
      )
      return promise.reject(
        ERROR_INVALID_ARGUMENTS,
        "characteristic not found on device"
      )
    }

    print(
      "[BleLibrary] waiting for callback didUpdateNotificationStateForCharacteristic"
    )

    if let t = self.notificationUpdateByCharUuid[charUuidLC] {
      t.fail(
        ERROR_INVALID_STATE,
        "another notification change operation is in progress"
      )
      self.transactionById.removeValue(forKey: t.transactionId)
    }

    let t = Transaction(
      transactionId: transactionId,
      promise: promise,
    )
    self.notificationUpdateByCharUuid[charUuidLC] = t
    self.transactionById[transactionId] = t

    self.peripheral?.setNotifyValue(false, for: c)
  }

  // MARK: - Central Manager lifecycle

  private func cancelAllTransactions(
    code: String,
    message: String,
    error: Error?
  ) {
    for t in transactionById.values {
      t.fail(code, message)
    }
    transactionById.removeAll()
    readOperationByCharUuid.removeAll()
    writeOperationByCharUuid.removeAll()
    notificationUpdateByCharUuid.removeAll()
    readRssiTransaction = nil
    initTransaction = nil
  }

  public func dispose() {
    cancelAllTransactions(
      code: ERROR_NOT_INITIALIZED,
      message: "BLE manager disposed",
      error: nil
    )

    if let m = manager {
      if m.isScanning {
        print("[BleLibrary] stopping scan")
        m.stopScan()
      }
      if isConnected, let p = peripheral {
        print("[BleLibrary] disconnecting device")
        m.cancelPeripheralConnection(p)
      }
    }

    // Cancel connection promise if present
    if let p = connectionPromise {
      p.reject(ERROR_OPERATION_CANCELED, "Disconnection requested")
      connectionPromise = nil
    }

    manager = nil
    peripheral = nil

    transactionById = [:]
    readOperationByCharUuid = [:]
    writeOperationByCharUuid = [:]
    notificationUpdateByCharUuid = [:]
  }

  // Called when CBCentralManager state changes
  public func centralManagerDidUpdateState(_ central: CBCentralManager) {
    print("[BleLibrary] CBCentralManager state changed \(central.state)")

    switch central.state {
    case .unknown, .resetting:
      print("[BleLibrary] BLE unsupported or internal error")
      initTransaction?.fail(ERROR_INVALID_STATE, "invalid state")
      manager = nil

    case .unsupported:
      print("[BleLibrary] BLE unsupported on this device")
      initTransaction?.fail(
        ERROR_BLE_NOT_SUPPORTED,
        "BLE not supported on this device"
      )
      manager = nil

    case .unauthorized:
      print("[BleLibrary] permission missing")
      initTransaction?.fail(
        ERROR_MISSING_PERMISSIONS,
        "missing BLE permissions"
      )
      manager = nil

    case .poweredOff:
      print("[BleLibrary] BLE is turned OFF")
      initTransaction?.fail(ERROR_BLE_NOT_ENABLED, "BLE is off")
      manager = nil

    case .poweredOn:
      print("[BleLibrary] BLE manager active")
      initTransaction?.succeed(nil)

    @unknown default:
      print("[BleLibrary] invalid state received")
      initTransaction?.fail(ERROR_INVALID_STATE, "invalid state received")
      manager = nil
    }

    initTransaction = nil
  }

  // MARK: - Scan callbacks

  public func centralManager(
    _ central: CBCentralManager,
    didDiscover peripheral: CBPeripheral,
    advertisementData: [String: Any],
    rssi RSSI: NSNumber
  ) {
    print(
      "[BleLibrary] discovered peripheral \(peripheral) (adv data: \(advertisementData)"
    )

    let result: [String: Any] = [
      "devices": [
        [
          "id": peripheral.identifier.uuidString.lowercased(),
          "name": peripheral.name as Any? ?? NSNull(),
          "rssi": RSSI,
          "isAvailable": true,
          "isConnectable": advertisementData[
            CBAdvertisementDataIsConnectable
          ] ?? NSNull(),
          "txPower": advertisementData[
            CBAdvertisementDataTxPowerLevelKey
          ] ?? NSNull(),
        ]
      ]
    ]

    print("[BleLibrary] sending scan result to JS \(result)")
    module.sendEvent(EVENT_SCAN_RESULT, result)
  }

  // MARK: - Connection callbacks

  public func centralManager(
    _ central: CBCentralManager,
    didFailToConnect peripheral: CBPeripheral,
    error: Error?
  ) {
    print(
      "[BleLibrary] error connecting to peripheral \(peripheral) (error: \(error, default: "nil")"
    )

    // Cancel connection promise if present
    if let p = connectionPromise {
      p.reject(ERROR_OPERATION_CANCELED, "Disconnection requested")
      connectionPromise = nil
    }

    module.sendEvent(
      EVENT_CONNECTION_STATE_CHANGED,
      [
        "state": STATE_DISCONNECTED,
        "error": ERROR_GATT,
        "message": "connection to device failed",
        "ios": [
          "code": (error as NSError?)?.code as Any,
          "description": error?.localizedDescription ?? "null",
        ],
      ]
    )
  }

  public func centralManager(
    _ central: CBCentralManager,
    didConnect peripheral: CBPeripheral
  ) {
    print(
      "[BleLibrary] connected to peripheral \(peripheral). Start service discovery"
    )

    module.sendEvent(
      EVENT_CONNECTION_STATE_CHANGED,
      [
        "state": STATE_DISCOVERING_SERVICES,
        "error": NSNull(),
        "message": "starting service discovery",
        "ios": [:],
      ]
    )

    peripheral.discoverServices(nil)
  }

  public func peripheral(
    _ peripheral: CBPeripheral,
    didDiscoverServices error: Error?
  ) {
    if let error = error {
      print("[BleLibrary] error discovering services (error: \(error)")
      // Cancel connection promise if present
      if let p = connectionPromise {
        p.reject(
          ERROR_OPERATION_CANCELED,
          "Error discovering services: \(error.localizedDescription)"
        )
        connectionPromise = nil
      }

      module.sendEvent(
        EVENT_CONNECTION_STATE_CHANGED,
        [
          "state": STATE_DISCONNECTING,
          "error": ERROR_GATT,
          "message": "service discovery failed",
          "ios": [
            "code": (error as NSError).code,
            "description": (error as NSError).description,
          ],
        ]
      )
      manager?.cancelPeripheralConnection(self.peripheral ?? peripheral)
      return
    }

    print("[BleLibrary] service discovery complete")
    guard let services = peripheral.services else {
      // Resolve connection promise if present
      if let p = connectionPromise {
        p.resolve()
        connectionPromise = nil
      }

      // Edge case: no services
      module.sendEvent(
        EVENT_CONNECTION_STATE_CHANGED,
        [
          "state": STATE_CONNECTED,
          "error": NSNull(),
          "message": "service discovery done",
          "services": [],
          "ios": [:],
        ]
      )
      return
    }

    for s in services {
      print("[BleLibrary] - service \(s.uuid), discovering characteristics")
      peripheral.discoverCharacteristics(nil, for: s)
    }

    print(
      "[BleLibrary] service discovery done, now waiting to discover all characteristics"
    )
  }

  public func peripheral(
    _ peripheral: CBPeripheral,
    didDiscoverCharacteristicsFor service: CBService,
    error: Error?
  ) {
    if let error = error {
      print(
        "[BleLibrary] error discovering characteristics for service \(service.uuid) (error: \(error, default: "null")"
      )
      // Cancel connection promise if present
      if let p = connectionPromise {
        p.reject(
          ERROR_OPERATION_CANCELED,
          "Error discovering characteristic for service \(service.uuid): \(error.localizedDescription)"
        )
        connectionPromise = nil
      }

      module.sendEvent(
        EVENT_CONNECTION_STATE_CHANGED,
        [
          "state": STATE_DISCONNECTING,
          "error": ERROR_GATT,
          "message": "characteristic discovery failed",
          "ios": [
            "code": (error as NSError).code,
            "description": error.localizedDescription,
            "service": service.uuid.uuidString,
          ],
        ]
      )
      manager?.cancelPeripheralConnection(self.peripheral ?? peripheral)
      return
    }

    print("[BleLibrary] discovered characteristics for service \(service.uuid)")
    if let characteristics = service.characteristics {
      for c in characteristics {
        print(
          "[BleLibrary] - characteristic \(c.uuid) properties: \(c.properties.rawValue)"
        )
      }
    }

    var charRemainingToDiscover = false
    if let services = peripheral.services {
      for s in services {
        if s.characteristics == nil {
          charRemainingToDiscover = true
          break
        }
      }
    }

    if !charRemainingToDiscover {
      print("[BleLibrary] all characteristics discovered")
      var servicesPayload: [[String: Any]] = []
      if let services = peripheral.services {
        for s in services {
          var charsPayload: [[String: Any]] = []
          if let chars = s.characteristics {
            for c in chars {
              charsPayload.append([
                "uuid": c.uuid.uuidString.lowercased(),
                "properties": c.properties.rawValue,
              ])
            }
          }
          servicesPayload.append([
            "characteristics": charsPayload,
            "uuid": s.uuid.uuidString.lowercased(),
            "isPrimary": s.isPrimary,
          ])
        }
      }

      module.sendEvent(
        EVENT_CONNECTION_STATE_CHANGED,
        [
          "state": STATE_CONNECTED,
          "error": NSNull(),
          "message": "service discovery done",
          "services": servicesPayload,
          "ios": [:],
        ]
      )
      // Resolve connection promise if present
      connectionSuccess = true
      if let p = connectionPromise {
        p.resolve()
        connectionPromise = nil
      }

    } else {
      print(
        "[BleLibrary] waiting for another service to complete characteristic discovery"
      )
    }
  }

  public func centralManager(
    _ central: CBCentralManager,
    didDisconnectPeripheral peripheral: CBPeripheral,
    error: Error?
  ) {
    if error == nil {
      print("[BleLibrary] disconnected from peripheral \(peripheral)")
      // Cancel connection promise if present
      if let p = connectionPromise {
        p.reject(ERROR_OPERATION_CANCELED, "Device has disconnected")
        connectionPromise = nil
      }

      module.sendEvent(
        EVENT_CONNECTION_STATE_CHANGED,
        [
          "state": STATE_DISCONNECTED,
          "error": NSNull(),
          "message": "disconnected from peripherial",
          "ios": [:],
        ]
      )
    } else {
      print(
        "[BleLibrary] disconnected from peripheral \(peripheral) failed (error: \(error, default: "null"). Trigger new connection"
      )

      module.sendEvent(
        EVENT_CONNECTION_STATE_CHANGED,
        [
          "state": STATE_CONNECTING_TO_DEVICE,
          "error": ERROR_GATT,
          "message": "disconnected from peripherial unexpetedly",
          "ios": [
            "code": (error as NSError?)?.code as Any,
            "description": (error as NSError?)?.description as Any,
          ],
        ]
      )

      // Trigger a new connection to peripheral (mirrors original behavior)
      if let p = self.peripheral {
        manager?.connect(p, options: nil)
      }
    }

    // On disconnect all operations pending should fail!
    cancelAllTransactions(
      code: ERROR_NOT_CONNECTED,
      message: "device has disconnected",
      error: nil
    )

    // Cancel connection promise if present
    if let p = connectionPromise {
      p.reject(
        ERROR_OPERATION_CANCELED,
        "Device has disconnected: \(error?.localizedDescription ?? "no error")"
      )
      connectionPromise = nil
    }

    // Deallocate
    self.peripheral = nil
  }

  // MARK: - RSSI callback

  public func peripheral(
    _ peripheral: CBPeripheral,
    didReadRSSI RSSI: NSNumber,
    error: Error?
  ) {
    guard let t = readRssiTransaction else {
      print(
        "[BleLibrary] read RSSI callback received but no transaction is in progress!"
      )
      return
    }

    if error == nil {
      print("[BleLibrary] read RSSI success, RSSI = \(RSSI)")
      t.succeed(RSSI)
    } else {
      print("[BleLibrary] read RSSI error (error: \(error, default: "null")")
      t.fail(ERROR_GATT, (error! as NSError).description)
    }
    transactionById.removeValue(forKey: t.transactionId)
    readRssiTransaction = nil
  }

  // MARK: - Read/Write updates

  public func peripheral(
    _ peripheral: CBPeripheral,
    didWriteValueFor characteristic: CBCharacteristic,
    error: Error?
  ) {
    let charUuid = characteristic.uuid.uuidString.lowercased()
    let serviceUuid =
      characteristic.service?.uuid.uuidString.lowercased() ?? ""

    guard let write = writeOperationByCharUuid[charUuid], !write.isCompleted
    else {
      print(
        "[BleLibrary] transaction for char \(characteristic.uuid) nil or completed!"
      )
      return
    }

    if error == nil {
      print("[BleLibrary] write value success")
      if write.hasMoreChunks, let next = write.getChunk() {
        print(
          "[BleLibrary] write another chunk of data (\(write.size)/\(write.written)"
        )
        module.sendEvent(
          EVENT_PROGRESS,
          [
            "characteristic": charUuid,
            "service": serviceUuid,
            "current": write.written,
            "total": write.size,
            "transactionId": write.transactionId,
          ]
        )
        peripheral.writeValue(
          next,
          for: characteristic,
          type: .withResponse
        )
      } else {
        print("[BleLibrary] write is completed! Resolving Promise")
        write.succeed(write.data)
        transactionById.removeValue(forKey: write.transactionId)
        writeOperationByCharUuid.removeValue(forKey: charUuid)
      }
    } else {
      print(
        "[BleLibrary] write value failure (error: \(error, default: "null")"
      )
      write.fail(ERROR_GATT, error?.localizedDescription ?? "")
      transactionById.removeValue(forKey: write.transactionId)
      writeOperationByCharUuid.removeValue(forKey: charUuid)
    }
  }

  // Called for both explicit reads and notifications; we distinguish based on whether a read is pending.
  public func peripheral(
    _ peripheral: CBPeripheral,
    didUpdateValueFor characteristic: CBCharacteristic,
    error: Error?
  ) {
    let charUuid = characteristic.uuid.uuidString.lowercased()
    let serviceUuid =
      characteristic.service?.uuid.uuidString.lowercased() ?? ""

    let read = readOperationByCharUuid[charUuid]
    let readIsPendingForChar = (read != nil) && !(read?.isCompleted ?? true)

    if readIsPendingForChar, let read = read {
      if error == nil {
        print(
          "[BleLibrary] read progress for characteristic \(characteristic.uuid)"
        )
        read.putChunk(characteristic.value ?? Data())

        if read.hasMoreData {
          print(
            "[BleLibrary] need to receive more data (\(read.readCount)/\(read.size) for characteristic, notify JS"
          )
          module.sendEvent(
            EVENT_PROGRESS,
            [
              "characteristic": charUuid,
              "service": serviceUuid,
              "current": read.readCount,
              "total": Int(read.size),
              "transactionId": read.transactionId,
            ]
          )
          print(
            "[BleLibrary] triggering another read, and waiting for didUpdateValueForCharacteristic"
          )
          peripheral.readValue(for: characteristic)
        } else {
          print("[BleLibrary] read is complete! Resolving Promise")
          let base64 = read.data.base64EncodedString()
          read.succeed(base64)
          transactionById.removeValue(forKey: read.transactionId)
          readOperationByCharUuid.removeValue(forKey: charUuid)
        }
      } else {
        print(
          "[BleLibrary] read value failure (error: \(error, default: "null")"
        )
        read.fail(ERROR_GATT, "error reading characteristic")
        transactionById.removeValue(forKey: read.transactionId)
        readOperationByCharUuid.removeValue(forKey: charUuid)
      }
    } else {
      // Assume this is a notification update
      if error == nil {
        print(
          "[BleLibrary] subscription updated characteristic \(characteristic.uuid), notify JS"
        )
        module.sendEvent(
          EVENT_CHAR_VALUE_CHANGED,
          [
            "value": (characteristic.value ?? Data())
              .base64EncodedString(),
            "characteristic": charUuid,
            "service": serviceUuid,
          ]
        )
      } else {
        print(
          "[BleLibrary] improbable state reached in read (no read pending but error present)"
        )
      }
    }
  }

  // MARK: - Notification state updates

  public func peripheral(
    _ peripheral: CBPeripheral,
    didUpdateNotificationStateFor characteristic: CBCharacteristic,
    error: Error?
  ) {
    let charUuid = characteristic.uuid.uuidString.lowercased()
    guard let transaction = notificationUpdateByCharUuid[charUuid],
      !transaction.isCompleted
    else {
      print(
        "[BleLibrary] subscribe/unsubscribe callback received but transaction was canceled!"
      )
      return
    }

    if error == nil {
      print(
        "[BleLibrary] characteristic \(characteristic.uuid) notification state updated"
      )
      transaction.succeed(nil)
    } else {
      print(
        "[BleLibrary] characteristic \(characteristic.uuid) notification state update (error: \(error, default: "null")"
      )
      transaction.fail(ERROR_GATT, "error setting notification")
    }

    notificationUpdateByCharUuid.removeValue(forKey: charUuid)
    transactionById.removeValue(forKey: transaction.transactionId)
  }

  // MARK: - Utility

  private func findService(uuid serviceUuid: String) -> CBService? {
    guard let p = peripheral, let services = p.services else { return nil }
    return services.first(where: {
      $0.uuid.uuidString.lowercased() == serviceUuid
    })
  }

  private func findCharacteristic(
    uuid characteristicUuid: String,
    serviceUuid: String
  ) -> CBCharacteristic? {
    guard let service = findService(uuid: serviceUuid),
      let chars = service.characteristics
    else { return nil }
    return chars.first(where: {
      $0.uuid.uuidString.lowercased() == characteristicUuid
    })
  }

  private func cancelPendingTransactionForChar(_ charUuid: String) {
    if let pendingRead = readOperationByCharUuid[charUuid] {
      print(
        "[BleLibrary] warning: a read for the characteristic was already in progress. Cancel it."
      )
      pendingRead.fail(
        ERROR_OPERATION_CANCELED,
        "canceled since another operation on the same char is requested"
      )
      readOperationByCharUuid.removeValue(forKey: charUuid)
      transactionById.removeValue(forKey: pendingRead.transactionId)
    }
    if let pendingWrite = writeOperationByCharUuid[charUuid] {
      print(
        "[BleLibrary] warning: a write for the characteristic was already in progress. Cancel it."
      )
      pendingWrite.fail(
        ERROR_OPERATION_CANCELED,
        "canceled since another operation on the same char is requested"
      )
      writeOperationByCharUuid.removeValue(forKey: charUuid)
      transactionById.removeValue(forKey: pendingWrite.transactionId)
    }
  }
}

// A swift class can only extend 1 class
// Thus, since this class extends Module, it cannot extend NSObject
// Since a CBCentralDelegate needs to extend a NSObject, this wrapper is needed
public final class ReactNativeBleLibraryModule: Module {
  private var impl: BleLibraryImpl? = nil

  public required init(appContext: AppContext) {
    super.init(appContext: appContext)
    self.impl = BleLibraryImpl(module: self)
  }

  // MARK: - Expo Module definition

  public func definition() -> ModuleDefinition {
    Name("ReactNativeBleLibrary")

    // Events the JS side can subscribe to
    Events(
      EVENT_ERROR,
      EVENT_SCAN_RESULT,
      EVENT_CHAR_VALUE_CHANGED,
      EVENT_PROGRESS,
      EVENT_CONNECTION_STATE_CHANGED
    )

    // Lifecycle hooks for event subscription
    OnStartObserving {
      print("[BleLibrary] NativeEventListener registered")
    }
    OnStopObserving {
      print("[BleLibrary] NativeEventListener removed")
    }

    // Dispose hook (called when module instance is being torn down)
    OnDestroy {
      print("[BleLibrary] invalidating native module")
      impl!.dispose()
    }

    // MARK: - Exported Methods

    AsyncFunction("initModule") { (promise: Promise) in
      impl!.initModule(promise: promise)
    }

    AsyncFunction("disposeModule") { (promise: Promise) in
      impl!.disposeModule(promise: promise)
    }

    AsyncFunction("cancel") { (transactionId: String, promise: Promise) in
      impl!.cancel(transactionId: transactionId, promise: promise)
    }

    // MARK: Scan

    AsyncFunction("scanStart") {
      (serviceUuids: [String]?, promise: Promise) in
      impl!.scanStart(serviceUuids: serviceUuids, promise: promise)
    }

    AsyncFunction("scanStop") { (promise: Promise) in
      impl!.scanStop(promise: promise)
    }

    // MARK: Connection

    AsyncFunction("connect") {
      (deviceId: String, mtu: Int, options: [String: Any]?, promise: Promise) in
      impl!.connect(
        deviceId: deviceId,
        mtu: mtu,
        options: options,
        promise: promise
      )
    }

    AsyncFunction("disconnect") { (promise: Promise) in
      impl!.disconnect(promise: promise)
    }

    // MARK: Read RSSI

    AsyncFunction("readRSSI") { (transactionId: String, promise: Promise) in
      impl!.readRSSI(transactionId: transactionId, promise: promise)
    }

    // MARK: Write

    AsyncFunction("write") {
      (
        transactionId: String,
        serviceUuid: String,
        characteristic: String,
        value: String,
        chunkSize: UInt,
        promise: Promise
      ) in
      impl!.write(
        transactionId: transactionId,
        serviceUuid: serviceUuid,
        characteristic: characteristic,
        value: value,
        chunkSize: chunkSize,
        promise: promise
      )
    }

    // MARK: Read

    AsyncFunction("read") {
      (
        transactionId: String,
        serviceUuid: String,
        characteristic: String,
        size: UInt,
        promise: Promise
      ) in
      impl!.read(
        transactionId: transactionId,
        serviceUuid: serviceUuid,
        characteristic: characteristic,
        size: size,
        promise: promise
      )
    }

    // MARK: Notifications

    AsyncFunction("subscribe") {
      (
        transactionId: String,
        serviceUuid: String,
        characteristic: String,
        promise: Promise
      ) in
      impl!.subscribe(
        transactionId: transactionId,
        serviceUuid: serviceUuid,
        characteristic: characteristic,
        promise: promise
      )
    }

    AsyncFunction("unsubscribe") {
      (
        transactionId: String,
        serviceUuid: String,
        characteristic: String,
        promise: Promise
      ) in
      impl!.unsubscribe(
        transactionId: transactionId,
        serviceUuid: serviceUuid,
        characteristic: characteristic,
        promise: promise
      )
    }
  }
}

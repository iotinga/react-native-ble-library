#import <Foundation/Foundation.h>

#import "PendingRead.h"
#import "PendingWrite.h"
#import "BleLibrary.h"
#import "BleErrorCode.h"
#import "BleNativeEvent.h"
#import "ConnectionState.h"
#import "Transaction.h"

// disable logging in production builds
#ifndef DEBUG
#define NSLog(...)
#endif

@implementation BleLibrary {
    CBCentralManager *_manager;
    CBPeripheral *_peripheral;

    Transaction *_initTransaction;
    Transaction *_readRssiTransaction;
    NSMutableDictionary<NSString *, Transaction *> *_transactionById;
    NSMutableDictionary<NSString *, PendingRead *> *_readOperationByCharUuid;
    NSMutableDictionary<NSString *, PendingWrite *> *_writeOperationByCharUuid;
    NSMutableDictionary<NSString *, Transaction *> *_notificationUpdateByCharUuid;
}

RCT_EXPORT_MODULE()

#pragma mark - RN module interface

// invoked when the first listener is registerd in the JS code
- (void)startObserving {
    NSLog(@"[BleLibrary] NativeEventListener registed");
}

// invoked when no more listeners are registered in the JS code
- (void)stopObserving {
    NSLog(@"[BleLibrary] NativeEventListener removed");
}

// should returns a list of events that the JS module can add listeners to
- (NSArray<NSString *> *)supportedEvents {
    return @[
        EVENT_ERROR,
        EVENT_SCAN_RESULT,
        EVENT_CHAR_VALUE_CHANGED,
        EVENT_PROGRESS,
        EVENT_CONNECTION_STATE_CHANGED,
    ];
}

// called when the module is being unloaded
- (void)invalidate {
    NSLog(@"[BleLibrary] invalidating native module");

    [self dispose];
    [super invalidate];
}

#pragma mark - module management

- (void)cancelAllTransactions {
    for (Transaction *transaction in _transactionById.allValues) {
        [transaction cancel];
    }
    
    [_transactionById removeAllObjects];
    [_readOperationByCharUuid removeAllObjects];
    [_writeOperationByCharUuid removeAllObjects];
    [_notificationUpdateByCharUuid removeAllObjects];
    _readRssiTransaction = nil;
    _initTransaction = nil;
}

- (void)dispose {
    [self cancelAllTransactions];
    
    if (_manager != nil) {
        if ([_manager isScanning]) {
            NSLog(@"[BLeLibrary] stopping scan");
            [_manager stopScan];
        }
        if (self.isConnected) {
            NSLog(@"[BleLibrary] disconnecting device");
            [_manager cancelPeripheralConnection:_peripheral];
            _peripheral = nil;
        }
    }
    
    _manager = nil;
    _peripheral = nil;
    
    _transactionById = nil;
    _readOperationByCharUuid = nil;
    _writeOperationByCharUuid = nil;
    _notificationUpdateByCharUuid = nil;

}

RCT_EXPORT_METHOD(initModule:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] initModule()");

    if (_manager == nil) {
        NSLog(@"[BleLibrary] init manager");

        _transactionById = [[NSMutableDictionary alloc] init];
        _readOperationByCharUuid = [[NSMutableDictionary alloc] init];
        _writeOperationByCharUuid = [[NSMutableDictionary alloc] init];
        _notificationUpdateByCharUuid = [[NSMutableDictionary alloc] init];

        _initTransaction = [[Transaction alloc] init:@"_init" resolve:resolve reject:reject];
        _manager = [[CBCentralManager alloc] initWithDelegate:self queue:nil];
    } else {
        NSLog(@"[BleLibrary] manager already initialized");
        resolve(nil);
    }
}

RCT_EXPORT_METHOD(disposeModule:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] disposeModule()");

    [self dispose];
    resolve(nil);
}

- (void)centralManagerDidUpdateState:(nonnull CBCentralManager *)central {
    NSLog(@"[BleLibrary] CBCentralManager state changed %ld", central.state);

    switch (central.state) {
        case CBManagerStateUnknown:
        case CBManagerStateResetting:
            NSLog(@"[BleLibrary] BLE unsupported or internal error");
            [_initTransaction fail:ERROR_INVALID_STATE message:@"invalid state" error:nil];
            _manager = nil;
            break;
        case CBManagerStateUnsupported:
            NSLog(@"[BleLibrary] BLE unsupported on this device");
            [_initTransaction fail:ERROR_BLE_NOT_SUPPORTED message:@"BLE not supported on this device" error:nil];
            _manager = nil;
            break;
        case CBManagerStateUnauthorized:
            NSLog(@"[BleLibrary] permission missing");
            [_initTransaction fail:ERROR_MISSING_PERMISSIONS message:@"missing BLE permissions" error:nil];
            _manager = nil;
            break;
        case CBManagerStatePoweredOff:
            NSLog(@"[BleLibrary] BLE is turned OFF");
            [_initTransaction fail:ERROR_BLE_NOT_ENABLED message:@"BLE is off" error:nil];
            _manager = nil;
            break;
        case CBManagerStatePoweredOn:
            NSLog(@"[BleLibrary] BLE manager active");
            [_initTransaction succeed:nil];
            break;
        default:
            NSLog(@"[BleLibrary] invalid state received");
            [_initTransaction fail:ERROR_INVALID_STATE message:@"invalid state received" error:nil];
            _manager = nil;
            break;
    }
    
    _initTransaction = nil;
}

RCT_EXPORT_METHOD(cancel:(NSString *)transactionId
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] cancel(%@)", transactionId);
    
    [_transactionById[transactionId] cancel];
    
    resolve(nil);
}

#pragma mark - BLE scan

RCT_EXPORT_METHOD(scanStart:(NSArray<NSString *> *)serviceUuids
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] scanStart(%@)", serviceUuids);

    if (!self.isModuleInitialized) {
        NSLog(@"[BleLibrary] manager is not initialized");
        reject(ERROR_NOT_INITIALIZED, @"call initModule first", nil);
    } else {
        if (_manager.isScanning) {
            NSLog(@"[BleLibrary] stopping existing scan...");
            [_manager stopScan];
        }
        
        NSLog(@"[BleLibrary] starting scan");
        NSMutableArray<CBUUID *> *services = nil;
        if (serviceUuids != nil && serviceUuids.count > 0) {
            services = [[NSMutableArray alloc] init];
            for (NSString *uuid in serviceUuids) {
                NSLog(@"[BleLibrary] adding filter for %@", uuid);
                [services addObject:[CBUUID UUIDWithString:uuid]];
            }
        }
        
        [_manager scanForPeripheralsWithServices:services options:@{}];
        NSLog(@"[BleLibrary] scan started");
        
        resolve(nil);
    }
}

RCT_EXPORT_METHOD(scanStop:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] scanStop()");

    if (!self.isModuleInitialized) {
        NSLog(@"[BleLibrary] manager is not initialized");
        reject(ERROR_NOT_INITIALIZED, @"call initModule first", nil);
    } else {
        if (_manager.isScanning) {
            NSLog(@"[BleLibrary] stoping scan");
            [_manager stopScan];
        } else {
            NSLog(@"[BleLibrary] scan not running, nothing to stop!");
        }
        resolve(nil);
    }
}


- (void)centralManager:(CBCentralManager *)central
didDiscoverPeripheral:(CBPeripheral *)peripheral
    advertisementData:(NSDictionary<NSString *,id> *)advertisementData
                 RSSI:(NSNumber *)RSSI {
    NSLog(@"[BleLibrary] discovered peripheral %@ (adv data: %@)", peripheral, advertisementData);
        
    NSDictionary *result = @{
        @"devices": @[
            @{
                @"id": peripheral.identifier.UUIDString.lowercaseString,
                @"name": peripheral.name ?: [NSNull null],
                @"rssi": RSSI,
                @"isAvailable": @YES,
                @"isConnectable": advertisementData[CBAdvertisementDataIsConnectable] ?: [NSNull null],
                @"txPower": advertisementData[CBAdvertisementDataTxPowerLevelKey] ?: [NSNull null],
            },
        ],
    };

    NSLog(@"[BleLibrary] sending scan result to JS %@", result);
    [self sendEventWithName:EVENT_SCAN_RESULT body:result];
}

#pragma mark - BLE connection

RCT_EXPORT_METHOD(connect:(NSString *)deviceId
                  mtu:(nonnull NSNumber *)mtu
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] connect(%@, %d)", deviceId, mtu.intValue);

    if (!self.isModuleInitialized) {
        NSLog(@"[BleLibrary] module is not initalized");
        reject(ERROR_NOT_INITIALIZED, @"call initModule first", nil);
    } else {
        if (self.isConnected) {
            NSLog(@"[BleLibrary] a peripherial is already connected. Disconnect it first");
            [_manager cancelPeripheralConnection:_peripheral];
        }
        _peripheral = nil;

        // ensure all transaction are concluded
        [self cancelAllTransactions];

        NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:deviceId];
        if (uuid == nil) {
            NSLog(@"[BleLibrary] invalid UUID");
            reject(ERROR_INVALID_ARGUMENTS, @"the deviceId must be a valid UUID", nil);
        } else {
            NSArray<CBPeripheral *> *peripherals = [_manager retrievePeripheralsWithIdentifiers: @[uuid]];
            if (peripherals.count == 0 || peripherals[0] == nil) {
                NSLog(@"[BleLibrary] peripheral with UUID %@ not found", uuid);
                reject(ERROR_INVALID_ARGUMENTS, @"peripheral with such UUID not found", nil);
            } else {
                // note: it's important to keep a reference for the peripherial, otherwise the manager will
                // cancel the connection!
                _peripheral = peripherals[0];
                [_peripheral setDelegate:self];

                NSLog(@"[BleLibrary] requesting connect");
                [_manager connectPeripheral:_peripheral options:nil];
                
                resolve(nil);
            }
        }
    }
}

// callback that is invoked when a connection with a device fails
- (void)centralManager:(CBCentralManager *)central
didFailToConnectPeripheral:(CBPeripheral *)peripheral
                error:(NSError *)error {
    NSLog(@"[BleLibrary] error connecting to peripheral %@ (error: %@)", peripheral, error);

    [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
        @"state": STATE_DISCONNECTED,
        @"error": ERROR_GATT,
        @"message": @"connection to device failed",
        @"ios": @{
            @"code": @(error.code),
            @"description": error.description,
        },
    }];
}

// callback that is invoked when a peripheral is connected to the manager
- (void)centralManager:(CBCentralManager *)central
 didConnectPeripheral:(CBPeripheral *)peripheral {
    NSLog(@"[BleLibrary] connected to peripheral %@. Start service discovery", peripheral);

    [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
        @"state": STATE_DISCOVERING_SERVICES,
        @"error": [NSNull null],
        @"message": @"starting service discovery",
        @"ios": @{},
    }];

    [peripheral discoverServices:nil];
}

// callback that is invoked when the service discovery is complete
- (void)peripheral:(CBPeripheral *)peripheral
didDiscoverServices:(NSError *)error {
    if (error != nil) {
        NSLog(@"[BleLibrary] error discovering services (error: %@)", error);

        [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
            @"state": STATE_DISCONNECTING,
            @"error": ERROR_GATT,
            @"message": @"service discovery failed",
            @"ios": @{
                @"code": @(error.code),
                @"description": error.description,
            },
        }];
        
        [_manager cancelPeripheralConnection:_peripheral];
    } else {
        NSLog(@"[BleLibrary] service discovery complete");

        NSArray<CBService *> *services = peripheral.services;
        for (CBService *service in services) {
            NSLog(@"[BleLibrary] - service %@, discovering characteristics", service.UUID);
            [peripheral discoverCharacteristics:nil forService:service];
        }

        NSLog(@"[BleLibrary] service discovery done, now waiting to discover all characteristics");
    }
}

- (void)peripheral:(CBPeripheral *)peripheral
didDiscoverCharacteristicsForService:(nonnull CBService *)service
            error:(nullable NSError *)error {
    if (error != nil) {
        NSLog(@"[BleLibrary] error discovering characteristics for service %@ (error: %@)", service, error);

        [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
            @"state": STATE_DISCONNECTING,
            @"error": ERROR_GATT,
            @"message": @"characteristic discovery failed",
            @"ios": @{
                @"code": @(error.code),
                @"description": error.description,
                @"service": service.UUID.UUIDString,
            },
        }];
        
        // cancel connection
        [_manager cancelPeripheralConnection:_peripheral];
    } else {
        NSLog(@"[BleLibrary] discovered characteristics for service %@", service.UUID);
        for (CBCharacteristic *characteristic in service.characteristics) {
            NSLog(@"[BleLibrary] - characteristic %@ properties: %lu", characteristic.UUID, characteristic.properties);
        }

        BOOL charRemainingToDiscover = NO;
        for (CBService *service in peripheral.services) {
            if (service.characteristics == nil) {
                charRemainingToDiscover = YES;
            }
        }

        if (!charRemainingToDiscover) {
            NSLog(@"[BleLibrary] all characteristics discovered");
            NSMutableArray<NSDictionary *> *services = [[NSMutableArray alloc] init];
            for (CBService *service in peripheral.services) {
                NSMutableArray<NSDictionary *> *characteristics = [[NSMutableArray alloc] init];
                for (CBCharacteristic *characteristic in service.characteristics) {
                    [characteristics addObject:@{
                        @"uuid": characteristic.UUID.UUIDString.lowercaseString,
                        @"properties": @(characteristic.properties),
                    }];
                }
                [services addObject:@{
                    @"characteristics": characteristics,
                    @"uuid": service.UUID.UUIDString.lowercaseString,
                    @"isPrimary": @(service.isPrimary),
                }];
            }
            
            [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
                @"state": STATE_CONNECTED,
                @"error": [NSNull null],
                @"message": @"service discovery done",
                @"services": services,
                @"ios": @{},
            }];
        } else {
            NSLog(@"[BleLibrary] waiting for another service to complete characteristic discovery");
        }
    }
}


RCT_EXPORT_METHOD(disconnect:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] disconnect()");

    if (self.isConnected) {
        NSLog(@"[BleLibrary] canceling connection");
        
        // ensure all transaction are concluded
        [self cancelAllTransactions];
        
        [_manager cancelPeripheralConnection:_peripheral];
        [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
            @"state": STATE_DISCONNECTING,
            @"error": [NSNull null],
            @"message": @"disconnecting from peripherial",
            @"ios": @{},
        }];
    } else {
        NSLog(@"[BleLibrary] no peripherial to disconnect");
    }
    
    resolve(nil);
}

- (void)centralManager:(CBCentralManager *)central
didDisconnectPeripheral:(CBPeripheral *)peripheral
                error:(NSError *)error {
    if (error == nil) {
        NSLog(@"[BleLibrary] disconnected from peripheral %@", peripheral);
        
        [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
            @"state": STATE_DISCONNECTED,
            @"error": [NSNull null],
            @"message": @"disconnected from peripherial",
            @"ios": @{},
        }];
    } else {
        NSLog(@"[BleLibrary] disconnected from peripheral %@ failed (error: %@)", peripheral, error);
        
        [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
            @"state": STATE_DISCONNECTED,
            @"error": ERROR_GATT,
            @"message": @"disconnected from peripherial unexpetedly",
            @"ios": @{
                @"code": @(error.code),
                @"description": error.description,
            },
        }];
    }

    // in any case we deallocate the resources
    _peripheral = nil;
}

#pragma mark - read RSSI

RCT_EXPORT_METHOD(readRSSI:(NSString *)transactionId
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    if (!self.isConnected) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ERROR_NOT_CONNECTED, @"call connect first", nil);
    } else {
        // cancel read RSSI transaction if any
        [_readRssiTransaction cancel];
        
        // create new transaction
        _readRssiTransaction = [[Transaction alloc] init:transactionId resolve:resolve reject:reject];
        _transactionById[_readRssiTransaction.transactionId] = _readRssiTransaction;
        
        [_peripheral readRSSI];
    }
}

- (void)peripheral:(CBPeripheral *)peripheral didReadRSSI:(NSNumber *)RSSI error:(NSError *)error {
    if (_readRssiTransaction == nil) {
        NSLog(@"[BleLibrary] read RSSI callback received but no transaction is in progress!");
    } else {
        if (error == nil) {
            NSLog(@"[BleLibrary] read RSSI success, RSSI = %@", RSSI);
            [_readRssiTransaction succeed:RSSI];
        } else {
            NSLog(@"[BleLibrary] read RSSI error (error: %@)", error);
            [_readRssiTransaction fail:ERROR_GATT message:error.description error:error];
        }
        
        [_transactionById removeObjectForKey:_readRssiTransaction.transactionId];
        _readRssiTransaction = nil;
    }
}

#pragma mark - BLE write

- (void)cancelPendingTransactionForChar:(NSString *)charUuid {
    Transaction *pendingRead = _readOperationByCharUuid[charUuid];
    if (pendingRead != nil) {
        NSLog(@"[BleLibrary] warning: a read for the characteristic was already in progress. Cancel it.");
        [pendingRead fail:ERROR_OPERATION_CANCELED 
                  message:@"canceled since another operation on the same char is requested"
                    error:nil];
        
        [_readOperationByCharUuid removeObjectForKey:charUuid];
        [_transactionById removeObjectForKey:pendingRead.transactionId];
    }
    
    Transaction *pendingWrite = _writeOperationByCharUuid[charUuid];
    if (pendingWrite != nil) {
        NSLog(@"[BleLibrary] warning: a write for the characteristic was already in progress. Cancel it.");
        [pendingWrite fail:ERROR_OPERATION_CANCELED 
                   message:@"canceled since another operation on the same char is requested"
                     error:nil];

        [_writeOperationByCharUuid removeObjectForKey:charUuid];
        [_transactionById removeObjectForKey:pendingWrite.transactionId];
    }
}

RCT_EXPORT_METHOD(write:(NSString *)transactionId
                  serviceUuid:(NSString *)serviceUuid
                  characteristic:(NSString *)characteristicUuid
                  value:(NSString *)value
                  chunkSize:(nonnull NSNumber *)chunkSize
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    characteristicUuid = characteristicUuid.lowercaseString;
    serviceUuid = serviceUuid.lowercaseString;
    
    NSLog(@"[BleLibrary] write(%@, %@, %lu)", serviceUuid, characteristicUuid, chunkSize.unsignedLongValue);

    if (!self.isConnected) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ERROR_NOT_CONNECTED, @"call connect first", nil);
    } else {
        CBCharacteristic *characteristic = [self findCharacteristicWithUuid:characteristicUuid forServiceWithUuid:serviceUuid];
        if (characteristic == nil) {
            NSLog(@"[BleLibrary] service %@ characteristic %@ not found", serviceUuid, characteristicUuid);
            reject(ERROR_INVALID_ARGUMENTS, @"characteristic not found on device", nil);
        } else {
            NSData *data = [[NSData alloc] initWithBase64EncodedString:value options:0];
            NSLog(@"[BleLibrary] requesting write for %lu bytes", data.length);
            
            [self cancelPendingTransactionForChar:characteristicUuid];
            
            PendingWrite *write = [[PendingWrite alloc] init:transactionId resolve:resolve reject:reject
                                                        data:data chunkSize:chunkSize.unsignedIntValue];

            // store transaction pending
            _transactionById[transactionId] = write;
            _writeOperationByCharUuid[characteristicUuid] = write;

            // waiting for callback didWriteValueForCharacteristic
            [_peripheral writeValue:[write getChunk] forCharacteristic:characteristic type:CBCharacteristicWriteWithResponse];
        }
    }
}

// callback that is invoked after a write request
- (void)peripheral:(CBPeripheral *)peripheral
didWriteValueForCharacteristic:(CBCharacteristic *)characteristic
            error:(NSError *)error {
    NSString *charUuid = characteristic.UUID.UUIDString.lowercaseString;
    NSString *serviceUuid = characteristic.service.UUID.UUIDString.lowercaseString;

    PendingWrite *write = _writeOperationByCharUuid[charUuid];
    if (write == nil || write.isCompleted) {
        NSLog(@"[BleLibrary] transaction for char %@ nil or completed!", characteristic);
    } else if (error == nil) {
        NSLog(@"[BleLibrary] write value success");
        if (write.hasMoreChunks) {
            NSLog(@"[BleLibrary] write another chunk of data (%lu/%lu)", write.size, write.written);

            [self sendEventWithName:EVENT_PROGRESS body:@{
                @"characteristic": charUuid,
                @"service": serviceUuid,
                @"current": @(write.written),
                @"total": @(write.size),
                @"transactionId": write.transactionId,
            }];

            [peripheral writeValue:[write getChunk] forCharacteristic:characteristic type:CBCharacteristicWriteWithResponse];
        } else {
            NSLog(@"[BleLibrary] write is completed! Resolving Promise");

            [write succeed:write.data];
            
            [_transactionById removeObjectForKey:write.transactionId];
            [_writeOperationByCharUuid removeObjectForKey:write.transactionId];
        }
    } else {
        NSLog(@"[BleLibrary] write value failue (error: %@)", error);
        [write fail:ERROR_GATT message:error.description error:error];
        
        [_transactionById removeObjectForKey:write.transactionId];
        [_writeOperationByCharUuid removeObjectForKey:write.transactionId];
    }
}

#pragma mark - BLE read

RCT_EXPORT_METHOD(read:(NSString *)transactionId
                  serviceUuid:(NSString *)serviceUuid
                  characteristic:(NSString *)characteristicUuid
                  size:(nonnull NSNumber *)size
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    characteristicUuid = characteristicUuid.lowercaseString;
    serviceUuid = serviceUuid.lowercaseString;

    NSLog(@"[BleLibrary] read(%@, %@, %lu)", serviceUuid, characteristicUuid, size.unsignedLongValue);

    if (!self.isConnected) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ERROR_NOT_CONNECTED, @"call connect first", nil);
    } else {
        CBCharacteristic *characteristic = [self findCharacteristicWithUuid:characteristicUuid forServiceWithUuid:serviceUuid];
        if (characteristic == nil) {
            NSLog(@"[BleLibrary] service %@ characteristic %@ not found", serviceUuid, characteristicUuid);
            reject(ERROR_INVALID_ARGUMENTS, @"characteristic not found on device", nil);
        } else {
            NSLog(@"[BleLibrary] requesting read for characteristic");

            // cancel existing read or write for the same characteristic
            [self cancelPendingTransactionForChar:characteristicUuid];

            PendingRead *read = [[PendingRead alloc] init:transactionId resolve:resolve reject:reject
                                                     size:size.unsignedIntValue];

            // store transaction pending
            _transactionById[read.transactionId] = read;
            _readOperationByCharUuid[characteristicUuid.lowercaseString] = read;

            // perform write
            [_peripheral readValueForCharacteristic:characteristic];
        }
    }
}

// this callback is called both when a characteristic is changes (becasue se are subscribed to)
// or when it changes because a read was asked to the device. We need to distinguish these two
// cases.
- (void)peripheral:(CBPeripheral *)peripheral
didUpdateValueForCharacteristic:(CBCharacteristic *)characteristic
            error:(NSError *)error {
    NSString *charUuid = characteristic.UUID.UUIDString.lowercaseString;
    NSString *serviceUuid = characteristic.service.UUID.UUIDString.lowercaseString;
        
    PendingRead *read = _readOperationByCharUuid[charUuid];
    BOOL readIsPendingForChar = read != nil && !read.isCompleted;
    if (readIsPendingForChar) {
        if (error == nil) {
            NSLog(@"[BleLibrary] read progress for characteristic %@", characteristic);
            
            [read putChunk:characteristic.value];
            if (read.hasMoreData) {
                NSLog(@"[BleLibrary] need to receive more data (%ld/%ld) for characteristic, notify JS", read.read, read.size);
                
                [self sendEventWithName:EVENT_PROGRESS body: @{
                    @"characteristic": charUuid,
                    @"service": serviceUuid,
                    @"current": @(read.read),
                    @"total": @(read.size),
                    @"transactionId": read.transactionId,
                }];
                
                NSLog(@"[BleLibrary] triggering another read, and waiting for didUpdateValueForCharacteristic");
                [peripheral readValueForCharacteristic:characteristic];
            } else {
                NSLog(@"[BleLibrary] read is complete! Resolving Promise");
                [read succeed:[read.data base64EncodedStringWithOptions:0]];
                
                // remove characteristic from pending operation list
                [_transactionById removeObjectForKey:read.transactionId];
                [_readOperationByCharUuid removeObjectForKey:charUuid];
            }
        } else {
            // there is a read error to signal to the JS part
            NSLog(@"[BleLibrary] read value failure (error: %@)", error);
                        
            [read fail:ERROR_GATT message:@"error reading characteristic" error:error];
                
            // remove characteristic from pending operation list
            [_transactionById removeObjectForKey:read.transactionId];
            [_readOperationByCharUuid removeObjectForKey:charUuid];
        }
    } else { // a read is not pending
        if (error == nil) {
            // otherwise we assume this is a result of a subscription
            
            NSLog(@"[BleLibrary] subscription updated characteristic %@, notify JS", characteristic);
            [self sendEventWithName:EVENT_CHAR_VALUE_CHANGED body:@{
                @"value": [characteristic.value base64EncodedStringWithOptions:0],
                @"characteristic": charUuid,
                @"service": serviceUuid,
            }];
        } else {
            // a read is not in progress and there is an error. This state should not normally be reacheable!
            NSLog(@"[BleLibrary] improbable state reached in read");
        }
    }
}

#pragma mark - BLE notification managment

RCT_EXPORT_METHOD(subscribe:(NSString *)transactionId
                  stringUuid:(NSString *)serviceUuid
                  characteristic:(NSString *)characteristicUuid
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    characteristicUuid = characteristicUuid.lowercaseString;
    serviceUuid = serviceUuid.lowercaseString;

    NSLog(@"[BleLibrary] subscribe(%@, %@)", serviceUuid, characteristicUuid);

    if (!self.isConnected) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ERROR_NOT_CONNECTED, @"call connect first", nil);
    } else {
        CBCharacteristic *characteristic = [self findCharacteristicWithUuid:characteristicUuid forServiceWithUuid:serviceUuid];
        if (characteristic == nil) {
            NSLog(@"[BleLibrary] service %@ characteristic %@ not found", serviceUuid, characteristicUuid);
            reject(ERROR_INVALID_ARGUMENTS, @"characteristic not found on device", nil);
        } else if (characteristic.isNotifying) {
            NSLog(@"[BleLibrary] notifications are already enabled");
            resolve(nil);
        } else {
            NSLog(@"[BleLibrary] waiting for callback didUpdateNotificationStateForCharacteristic");
           
            Transaction *transaction = _notificationUpdateByCharUuid[characteristicUuid];
            if (transaction != nil) {
                [transaction fail:ERROR_INVALID_STATE 
                          message:@"another notification change operation is in progress"
                            error:nil];
                [_transactionById removeObjectForKey:transaction.transactionId];
            }
            
            // cancel eventual operation in progress
            transaction = [[Transaction alloc] init:transactionId resolve:resolve reject:reject];
            _notificationUpdateByCharUuid[characteristicUuid] = transaction;
            _transactionById[transactionId] = transaction;
            
            [_peripheral setNotifyValue:YES forCharacteristic:characteristic];
        }
    }
}

RCT_EXPORT_METHOD(unsubscribe:(NSString *)transactionId
                  serviceUuid:(NSString *)serviceUuid
                  characteristic:(NSString *)characteristicUuid
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    characteristicUuid = characteristicUuid.lowercaseString;
    serviceUuid = serviceUuid.lowercaseString;

    NSLog(@"[BleLibrary] unsubscribe(%@, %@)", serviceUuid, characteristicUuid);

    if (!self.isConnected) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ERROR_NOT_CONNECTED, @"call connect first", nil);
    } else {
        CBCharacteristic *characteristic = [self findCharacteristicWithUuid:characteristicUuid forServiceWithUuid:serviceUuid];
        if (characteristic == nil) {
            NSLog(@"[BleLibrary] service %@ characteristic %@ not found", serviceUuid, characteristicUuid);
            reject(ERROR_INVALID_ARGUMENTS, @"characteristic not found on device", nil);
        } else if (!characteristic.isNotifying) {
            NSLog(@"[BleLibrary] notifications are already disabled");
            resolve(nil);
        } else {
            NSLog(@"[BleLibrary] waiting for callback didUpdateNotificationStateForCharacteristic");

            Transaction *transaction = _notificationUpdateByCharUuid[characteristicUuid];
            if (transaction != nil) {
                [transaction fail:ERROR_INVALID_STATE 
                          message:@"another notification change operation is in progress"
                            error:nil];
                [_transactionById removeObjectForKey:transaction.transactionId];
            }
            
            // cancel eventual operation in progress
            transaction = [[Transaction alloc] init:transactionId resolve:resolve reject:reject];
            _notificationUpdateByCharUuid[characteristicUuid] = transaction;
            _transactionById[transactionId] = transaction;

            [_peripheral setNotifyValue:NO forCharacteristic:characteristic];
        }
    }
}

// callback called when the notification state of the peripheral changes
- (void)peripheral:(CBPeripheral *)peripheral
didUpdateNotificationStateForCharacteristic:(CBCharacteristic *)characteristic
            error:(NSError *)error {
    NSString *charUuid = characteristic.UUID.UUIDString.lowercaseString;

    Transaction *transaction = _notificationUpdateByCharUuid[charUuid];
    if (transaction == nil || transaction.isCompleted) {
        NSLog(@"[BleLibrary] subscribe/unsubscribe callback received by transaction was canceled!");
    } else {
        if (error == nil) {
            NSLog(@"[BleLibrary] characteristic %@ notification state updated", characteristic);
            [transaction succeed:nil];
        } else {
            NSLog(@"[BleLibrary] characteristic %@ notification state update (error: %@)", characteristic, error);
            [transaction fail:ERROR_GATT message:@"error setting notification" error:error];
        }
        
        [_notificationUpdateByCharUuid removeObjectForKey:charUuid];
        [_transactionById removeObjectForKey:transaction.transactionId];
    }
    
}

#pragma mark - utility

- (nullable CBService *)findServiceWithUuid:(NSString *)serviceUuid {
    if (_peripheral != nil && _peripheral.services != nil) {
        for (CBService *service in _peripheral.services) {
            if ([service.UUID.UUIDString.lowercaseString isEqualToString:serviceUuid]) {
                return service;
            }
        }
    }
    
    return nil;
}

- (nullable CBCharacteristic *)findCharacteristicWithUuid:(NSString *)characteristicUuid 
                                       forServiceWithUuid:(NSString *)serviceUuid {
    CBService *service = [self findServiceWithUuid:serviceUuid];
    if (service != nil && service.characteristics != nil) {
        for (CBCharacteristic *characteristic in service.characteristics) {
            if ([characteristic.UUID.UUIDString.lowercaseString isEqualToString:characteristicUuid]) {
                return characteristic;
            }
        }
    }
    
    return nil;
}

- (BOOL)isConnected {
    return _manager != nil && _peripheral != nil && _peripheral.state == CBPeripheralStateConnected;
}

- (BOOL)isModuleInitialized {
    return _manager != nil && _manager.state == CBManagerStatePoweredOn;
}

@end

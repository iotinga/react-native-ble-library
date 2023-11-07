#import <Foundation/Foundation.h>

#import "BleLibrary.h"
#import "BleErrorCode.h"
#import "BleNativeEvent.h"
#import "ConnectionState.h"

// disable logging in production builds
#ifndef DEBUG
#define NSLog(...)
#endif

static NSTimeInterval CONNECTION_TIMEOUT_SECONDS = 5.0;

@implementation BleLibrary

RCT_EXPORT_MODULE()

#pragma mark - RN module interface

// invoked when the first listener is registerd in the JS code
-(void)startObserving {
    NSLog(@"[BleLibrary] NativeEventListener registed");
}

// invoked when no more listeners are registered in the JS code
-(void)stopObserving {
    NSLog(@"[BleLibrary] NativeEventListener removed");
}

// should returns a list of events that the JS module can add listeners to
-(NSArray<NSString *> *)supportedEvents {
    return @[
        EVENT_ERROR,
        EVENT_SCAN_RESULT,
        EVENT_CHAR_VALUE_CHANGED,
        EVENT_PROGRESS,
        EVENT_CONNECTION_STATE_CHANGED,
        EVENT_SERVICE_DISCOVERED,
    ];
}

// called when the module is being unloaded
-(void)invalidate {
    NSLog(@"[BleLibrary] invalidating native module");

    [self dispose];
    [super invalidate];
}

#pragma mark - module management

-(void)dispose {
    if (self.manager != nil) {
        if (self.timeout) {
            [self.timeout invalidate];
            self.timeout = nil;
        }
        if ([self hasPendingPromise]) {
            [self reject:ERROR_GENERIC message:@"module is shutting down" error:nil];
        }
        if ([self.manager isScanning]) {
            NSLog(@"[BLeLibrary] stopping scan");
            [self.manager stopScan];
        }
        if (self.isConnected) {
            NSLog(@"[BleLibrary] disconnecting device");
            [self.manager cancelPeripheralConnection:self.peripheral];
            self.peripheral = nil;
        }
    }
    self.manager = nil;
}

RCT_EXPORT_METHOD(initModule:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] initModule()");

    if (self.manager == nil) {
        NSLog(@"[BleLibrary] init manager");
        [self setPromise:resolve reject:reject];
        self.manager = [[CBCentralManager alloc] initWithDelegate:self queue:nil];
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

-(void)centralManagerDidUpdateState:(nonnull CBCentralManager *)central {
    NSLog(@"[BleLibrary] CBCentralManager state changed %ld", central.state);

    switch (central.state) {
        case CBManagerStateUnknown:
        case CBManagerStateResetting:
            NSLog(@"[BleLibrary] BLE unsupported or internal error");
            [self reject:ERROR_INVALID_STATE message:@"invalid state" error:nil];
            self.manager = nil;
            break;
        case CBManagerStateUnsupported:
            NSLog(@"[BleLibrary] BLE unsupported on this device");
            [self reject:ERROR_BLE_NOT_SUPPORTED message:@"BLE not supported on this device" error:nil];
            self.manager = nil;
            break;
        case CBManagerStateUnauthorized:
            NSLog(@"[BleLibrary] permission missing");
            [self reject:ERROR_MISSING_PERMISSIONS message:@"missing BLE permissions" error:nil];
            self.manager = nil;
            break;
        case CBManagerStatePoweredOff:
            NSLog(@"[BleLibrary] BLE is turned OFF");
            [self reject:ERROR_BLE_NOT_ENABLED message:@"BLE is off" error:nil];
            self.manager = nil;
            break;
        case CBManagerStatePoweredOn:
            NSLog(@"[BleLibrary] BLE manager active");
            [self resolve:nil];
            break;
        default:
            NSLog(@"[BleLibrary] invalid state received");
            break;
    }
}

RCT_EXPORT_METHOD(cancelPendingOperations:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] cancelPendingOperations()");

    [self reject:ErrorOperationCanceled message:@"the current operation was canceled" error:nil];

    self.write = nil;
    self.read = nil;

    resolve(nil);
}

#pragma mark - BLE scan

RCT_EXPORT_METHOD(scanStart:(NSArray<NSString *> *)serviceUuids
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] scanStart(%@)", serviceUuids);

    if (self.manager == nil) {
        NSLog(@"[BleLibrary] manager is not initialized");
        reject(ERROR_NOT_INITIALIZED, @"call initModule first", nil);
    } else if (![self isBlePoweredOn]) {
        NSLog(@"[BleLibrary] BLE is not enabled");
        reject(ERROR_BLE_NOT_ENABLED, @"BLE is not enabled", nil);
    } else if ([self.manager isScanning]) {
        NSLog(@"[BleLibrary] already running");
        resolve(nil);
    } else {
        NSLog(@"[BleLibrary] starting scan");
        NSMutableArray<CBUUID *> *services = nil;
        if (serviceUuids != nil && serviceUuids.count > 0) {
            services = [[NSMutableArray alloc] init];
            for (NSString *uuid in serviceUuids) {
                NSLog(@"[BleLibrary] adding filter for %@", uuid);
                [services addObject:[CBUUID UUIDWithString:uuid]];
            }
        }
        NSDictionary<NSString *,id> *options = @{};
        [self.manager scanForPeripheralsWithServices:services options:options];
        NSLog(@"[BleLibrary] scan started");
        resolve(nil);
    }
}

RCT_EXPORT_METHOD(scanStop:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] scanStop()");

    if (self.manager == nil) {
        NSLog(@"[BleLibrary] manager is not initialized");
        reject(ERROR_NOT_INITIALIZED, @"call initModule first", nil);
    } else if (![self isBlePoweredOn]) {
        NSLog(@"[BleLibrary] BLE is not enabled");
        reject(ERROR_BLE_NOT_ENABLED, @"BLE is not enabled", nil);
    } else if (self.manager.isScanning) {
        NSLog(@"[BleLibrary] stoping scan");
        [self.manager stopScan];
        NSLog(@"[BleLibrary] scan stopped");
        resolve(nil);
    } else {
        NSLog(@"[BleLibrary] scan not running");
        resolve(nil);
    }
}


-(void)centralManager:(CBCentralManager *)central
didDiscoverPeripheral:(CBPeripheral *)peripheral
    advertisementData:(NSDictionary<NSString *,id> *)advertisementData
                 RSSI:(NSNumber *)RSSI {
    NSLog(@"[BleLibrary] discovered peripheral %@ (adv data: %@)", peripheral, advertisementData);

    NSDictionary *result = @{
        @"devices": @[
            @{
                @"id": peripheral.identifier.UUIDString.lowercaseString,
                @"name": peripheral.name != nil ? peripheral.name : [NSNull alloc],
                @"rssi": RSSI,
                @"available": @YES,
            },
        ],
    };

    NSLog(@"[BleLibrary] sending scan result to JS %@", result);
    [self sendEventWithName:EVENT_SCAN_RESULT body:result];
}

#pragma mark - BLE connection

RCT_EXPORT_METHOD(connect:(NSString *)deviceId
                  mtu:(NSNumber *_Nonnull)mtu
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] connect(%@, %d)", deviceId, mtu.intValue);

    if (self.manager == nil) {
        NSLog(@"[BleLibrary] module is not initalized");
        reject(ERROR_NOT_INITIALIZED, @"call initModule first", nil);
    } else if (![self isBlePoweredOn]) {
        NSLog(@"[BleLibrary] BLE is not enabled");
        reject(ERROR_BLE_NOT_ENABLED, @"BLE is not enabled", nil);
    } else {
        if (self.peripheral) {
            NSLog(@"[BleLibrary] a peripherial is already connected. Disconnect it first");
            [self.manager cancelPeripheralConnection:self.peripheral];
            self.peripheral = nil;
        }

        NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:deviceId];
        if (uuid == nil) {
            NSLog(@"[BleLibrary] invalid UUID");
            reject(ERROR_INVALID_ARGUMENTS, @"the deviceId must be a valid UUID", nil);
        } else {
            NSArray<CBPeripheral *> *peripherals = [self.manager retrievePeripheralsWithIdentifiers: @[uuid]];
            if (peripherals.count == 0 || peripherals[0] == nil) {
                NSLog(@"[BleLibrary] peripheral with UUID %@ not found", uuid);
                reject(ERROR_INVALID_ARGUMENTS, @"peripheral with such UUID not found", nil);
            } else {
                // note: it's important to keep a reference for the peripherial, otherwise the manager will
                // cancel the connection!
                self.peripheral = peripherals[0];
                [self.peripheral setDelegate:self];

                NSLog(@"[BleLibrary] requesting connect");
                [self.manager connectPeripheral:self.peripheral options:nil];
                
                resolve(nil);
            }
        }
    }
}

// callback that is invoked when a connection with a device fails
-(void)centralManager:(CBCentralManager *)central
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
-(void)centralManager:(CBCentralManager *)central
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
-(void)peripheral:(CBPeripheral *)peripheral
didDiscoverServices:(NSError *)error {
    if (error == nil) {
        NSLog(@"[BleLibrary] service discovery complete");

        [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
            @"state": STATE_DISCOVERING_SERVICES,
            @"error": [NSNull null],
            @"message": @"service discovery ok, starting characteristic discovery",
            @"ios": @{},
        }];

        NSArray<CBService *> *services = peripheral.services;
        for (CBService *service in services) {
            NSLog(@"[BleLibrary] - service %@, discovering characteristics", service.UUID);
            [peripheral discoverCharacteristics:nil forService:service];
        }

        NSLog(@"[BleLibrary] service discovery done, now waiting to discover all characteristics");
    } else {
        NSLog(@"[BleLibrary] error discovering services (error: %@)", error);

        [self sendEventWithName:EVENT_CONNECTION_STATE_CHANGED body:@{
            @"state": STATE_DISCONNECTED,
            @"error": ERROR_GATT,
            @"message": @"service discovery failed",
            @"ios": @{
                @"code": @(error.code),
                @"description": error.description,
            },
        }];
    }
}

-(void)peripheral:(CBPeripheral *)peripheral
didDiscoverCharacteristicsForService:(nonnull CBService *)service
            error:(nullable NSError *)error {
    if (error != nil) {
        NSLog(@"[BleLibrary] error discovering characteristics for service %@ (error: %@)", service, error);
        [self reject:ErrorGATT message:@"error discovering characteristics" error:error];
    } else {
        NSLog(@"[BleLibrary] discovered characteristics for service %@", service.UUID);
        for (CBCharacteristic *characteristic in service.characteristics) {
            NSLog(@"[BleLibrary] - characteristic %@ properties: %lu", characteristic.UUID, characteristic.properties);
        }

        bool charRemainingToDiscover = NO;
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

            [self resolve:@{
                @"services": services,
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

    if (![self isConnected]) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ErrorNotConnected, @"call connect first", nil);
    } else if ([self hasPendingPromise]) {
        NSLog(@"[BleLibrary] an async operation is already in progress");
        reject(ErrorModuleBusy, @"an operation is already in progress", nil);
    } else {
        NSLog(@"[BleLibrary] disconnecting peripheral %@", self.peripheral);
        [self setPromise:resolve reject:reject];
        [self.manager cancelPeripheralConnection:self.peripheral];
    }
}

-(void)centralManager:(CBCentralManager *)central
didDisconnectPeripheral:(CBPeripheral *)peripheral
                error:(NSError *)error {
    if (error == nil) {
        NSLog(@"[BleLibrary] disconnected from peripheral %@", peripheral);
        [self resolve:nil];
    } else {
        NSLog(@"[BleLibrary] disconnected from peripheral %@ failed (error: %@)", peripheral, error);
        NSDictionary *body = @{
            @"error": ErrorDeviceDisconnected,
            @"message": @"unexpected device disconnect",
            @"nativeError": error.description,
        };
        [self sendEventWithName:EventError body:body];
        [self reject:ErrorDeviceDisconnected message:@"BLE device connection lost" error:nil];
    }

    self.read = nil;
    self.write = nil;
    self.peripheral = nil;
}

RCT_EXPORT_METHOD(readRSSI:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    if (![self isConnected]) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ErrorNotConnected, @"call connect first", nil);
    } else if ([self hasPendingPromise]) {
        NSLog(@"[BleLibrary] an async operation is already in progress");
        reject(ErrorModuleBusy, @"an operation is already in progress", nil);
    } else {
        [self setPromise:resolve reject:reject];
        [self.peripheral readRSSI];
    }
}

- (void)peripheral:(CBPeripheral *)peripheral didReadRSSI:(NSNumber *)RSSI error:(NSError *)error {
    if (error == nil) {
        NSLog(@"[BleLibrary] read RSSI success, RSSI = %@", RSSI);
        [self resolve:RSSI];
    } else {
        NSLog(@"[BleLibrary] read RSSI error (error: %@)", error);
        [self reject:ErrorGATT message:@"read RSSI failed" error:error];
    }
}

#pragma mark - BLE write

RCT_EXPORT_METHOD(write:(NSString *)serviceUuid
                  characteristic:(NSString *)characteristicUuid
                  value:(NSString *)value
                  chunkSize:(NSNumber *_Nonnull)chunkSize
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] write(%@, %@, %lu)", serviceUuid, characteristicUuid, chunkSize.unsignedLongValue);

    if (![self isConnected]) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ErrorNotConnected, @"call connect first", nil);
    } else if ([self hasPendingPromise]) {
        NSLog(@"[BleLibrary] an async operation is already in progress");
        reject(ErrorModuleBusy, @"an operation is already in progress", nil);
    } else {
        CBCharacteristic *characteristic = [self findCharacteristic:characteristicUuid forService:serviceUuid];
        if (characteristic == nil) {
            NSLog(@"[BleLibrary] service %@ characteristic %@ not found", serviceUuid, characteristicUuid);
            reject(ErrorInvalidArguments, @"characteristic not found on device", nil);
        } else {
            NSData *data = [[NSData alloc] initWithBase64EncodedString:value options:0];
            NSLog(@"[BleLibrary] requesting write for %lu bytes", data.length);

            self.write = [[PendingWrite alloc] init:data chunkSize:chunkSize.unsignedIntValue];

            // waiting for callback didWriteValueForCharacteristic
            [self setPromise:resolve reject:reject];
            [self.peripheral writeValue:[self.write getChunk] forCharacteristic:characteristic type:CBCharacteristicWriteWithResponse];
        }
    }
}

// callback that is invoked after a write request
-(void)peripheral:(CBPeripheral *)peripheral
didWriteValueForCharacteristic:(CBCharacteristic *)characteristic
            error:(NSError *)error {
    if (error == nil && self.write != nil) {
        NSLog(@"[BleLibrary] write value success");
        if ([self.write hasMoreChunks]) {
            NSLog(@"[BleLibrary] write another chunk of data (%lu/%lu)", self.write.size, self.write.written);
            NSDictionary *data = @{
                @"characteristic": characteristic.UUID.UUIDString.lowercaseString,
                @"service": characteristic.service.UUID.UUIDString.lowercaseString,
                @"current": [NSNumber numberWithUnsignedInt:self.write.written],
                @"total": [NSNumber numberWithUnsignedInt:self.write.size],
            };
            [self sendEventWithName:EventWriteProgress body:data];

            [peripheral writeValue:[self.write getChunk] forCharacteristic:characteristic type:CBCharacteristicWriteWithResponse];
        } else {
            NSLog(@"[BleLibrary] write is completed! Resolving Promise");

            [self resolve:nil];
            self.write = nil;
        }
    } else {
        NSLog(@"[BleLibrary] write value failue (error: %@)", error);
        self.write = nil;
        [self reject:ErrorGATT message:@"error writing characteristic" error:error];
    }
}

#pragma mark - BLE read

RCT_EXPORT_METHOD(read:(NSString *)serviceUuid
                  characteristic:(NSString *)characteristicUuid
                  size:(NSNumber *_Nonnull)size
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] read(%@, %@, %lu)", serviceUuid, characteristicUuid, [size unsignedLongValue]);

    if (![self isConnected]) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ErrorNotConnected, @"call connect first", nil);
    } else if ([self hasPendingPromise]) {
        NSLog(@"[BleLibrary] an async operation is already in progress");
        reject(ErrorModuleBusy, @"an operation is already in progress", nil);
    } else {
        CBCharacteristic *characteristic = [self findCharacteristic:characteristicUuid forService:serviceUuid];
        if (characteristic == nil) {
            NSLog(@"[BleLibrary] service %@ characteristic %@ not found", serviceUuid, characteristicUuid);
            reject(ErrorInvalidArguments, @"characteristic not found on device", nil);
        } else {
            NSLog(@"[BleLibrary] requesting read for characteristic");

            self.read = [[PendingRead alloc] init:size.unsignedIntValue characteristic:characteristic];

            [self setPromise:resolve reject:reject];
            [self.peripheral readValueForCharacteristic:characteristic];
        }
    }
}

// this callback is called both when a characteristic is changes (becasue se are subscribed to)
// or when it changes because a read was asked to the device. We need to distinguish these two
// cases.
-(void)peripheral:(CBPeripheral *)peripheral
didUpdateValueForCharacteristic:(CBCharacteristic *)characteristic
            error:(NSError *)error {
    if (error != nil) {
        NSLog(@"[BleLibrary] read value failure (error: %@)", error);
        self.read = nil;
        [self reject:ErrorGATT message:@"error reading characteristic" error:error];
    } else if (self.read != nil && [self.read.characteristic isEqual:characteristic]) {
        NSLog(@"[BleLibrary] read progress for characteristic %@", characteristic);

        [self.read putChunk:[characteristic value]];
        if ([self.read hasMoreData]) {
            NSLog(@"[BleLibrary] need to receive more data (%ld/%ld) for characteristic, notify JS", (long)self.read.read, (long)self.read.size);

            NSDictionary *data = @{
                @"characteristic": characteristic.UUID.UUIDString.lowercaseString,
                @"service": characteristic.service.UUID.UUIDString.lowercaseString,
                @"current": [NSNumber numberWithUnsignedInt:self.read.read],
                @"total": [NSNumber numberWithUnsignedInt:self.read.size],
            };
            [self sendEventWithName:EventReadProgress body:data];

            NSLog(@"[BleLibrary] triggering another read, and waiting for didUpdateValueForCharacteristic");
            [peripheral readValueForCharacteristic:characteristic];
        } else {
            NSLog(@"[BleLibrary] read is complete! Resolving Promise");
            [self resolve:[self.read.data base64EncodedStringWithOptions:0]];
            self.read = nil;
        }
    } else {
        NSLog(@"[BleLibrary] subscription updated characteristic %@, notify JS", characteristic);
        NSDictionary *data = @{
            @"value": [characteristic.value base64EncodedStringWithOptions:0],
            @"characteristic": characteristic.UUID.UUIDString.lowercaseString,
            @"service": characteristic.service.UUID.UUIDString.lowercaseString,
        };
        [self sendEventWithName:EventCharValueChanged body:data];
    }
}

#pragma mark - BLE notification managment

RCT_EXPORT_METHOD(subscribe:(NSString *)serviceUuid
                  characteristic:(NSString *)characteristicUuid
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] subscribe(%@, %@)", serviceUuid, characteristicUuid);

    if (![self isConnected]) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ErrorNotConnected, @"call connect first", nil);
    } else if ([self hasPendingPromise]) {
        NSLog(@"[BleLibrary] an async operation is already in progress");
        reject(ErrorModuleBusy, @"an operation is already in progress", nil);
    } else {
        CBCharacteristic *characteristic = [self findCharacteristic:characteristicUuid forService:serviceUuid];
        if (characteristic == nil) {
            NSLog(@"[BleLibrary] service %@ characteristic %@ not found", serviceUuid, characteristicUuid);
            reject(ErrorInvalidArguments, @"characteristic not found on device", nil);
        } else if (characteristic.isNotifying) {
            NSLog(@"[BleLibrary] notifications are already enabled");
            resolve(nil);
        } else {
            NSLog(@"[BleLibrary] waiting for callback didUpdateNotificationStateForCharacteristic");
            [self setPromise:resolve reject:reject];
            [self.peripheral setNotifyValue:YES forCharacteristic:characteristic];
        }
    }
}

RCT_EXPORT_METHOD(unsubscribe:(NSString *)serviceUuid
                  characteristic:(NSString *)characteristicUuid
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] unsubscribe(%@, %@)", serviceUuid, characteristicUuid);

    if (![self isConnected]) {
        NSLog(@"[BleLibrary] device not connected");
        reject(ErrorNotConnected, @"call connect first", nil);
    } else if ([self hasPendingPromise]) {
        NSLog(@"[BleLibrary] an async operation is already in progress");
        reject(ErrorModuleBusy, @"an operation is already in progress", nil);
    } else {
        CBCharacteristic *characteristic = [self findCharacteristic:characteristicUuid forService:serviceUuid];
        if (characteristic == nil) {
            NSLog(@"[BleLibrary] service %@ characteristic %@ not found", serviceUuid, characteristicUuid);
            reject(ErrorInvalidArguments, @"characteristic not found on device", nil);
        } else if (!characteristic.isNotifying) {
            NSLog(@"[BleLibrary] notifications are already disabled");
            resolve(nil);
        } else {
            NSLog(@"[BleLibrary] waiting for callback didUpdateNotificationStateForCharacteristic");
            [self setPromise:resolve reject:reject];
            [self.peripheral setNotifyValue:NO forCharacteristic:characteristic];
        }
    }
}

// callback called when the notification state of the peripheral changes
-(void)peripheral:(CBPeripheral *)peripheral
didUpdateNotificationStateForCharacteristic:(CBCharacteristic *)characteristic
            error:(NSError *)error {
    if (error == nil) {
        NSLog(@"[BleLibrary] characteristic %@ notification state updated", characteristic);
        [self resolve:nil];
    } else {
        NSLog(@"[BleLibrary] characteristic %@ notification state update (error: %@)", characteristic, error);
        [self reject:ErrorGATT message:@"Error setting notification state" error:error];
    }
}

#pragma mark - utility

-(CBCharacteristic *_Nullable)findCharacteristic:(NSString *)characteristicUuid forService:(NSString *)serviceUuid {
    if (self.peripheral == nil) {
        return nil;
    }

    NSArray<CBService *> *services = [self.peripheral services];
    if (services == nil) {
        return nil;
    }
    for (CBService *service in services) {
        if ([service.UUID.UUIDString isEqualToString:serviceUuid.uppercaseString]) {
            if (service.characteristics == nil) {
                return nil;
            }
            for (CBCharacteristic *characteristic in service.characteristics) {
                if ([characteristic.UUID.UUIDString isEqualToString:characteristicUuid.uppercaseString]) {
                    return characteristic;
                }
            }
        }
    }

    return nil;
}

// resolves the promise that is pending (if there is one)
-(void)resolve:(NSObject *)data {
    if ([self hasPendingPromise]) {
        NSLog(@"[BleLibrary] resolving promise with %@", data);
        self.resolve(data);

        self.resolve = nil;
        self.reject = nil;
    } else {
        NSLog(@"[BleLibrary] error: no pending promise set");
    }
}

// rejects the promise that is pending (if there is one)
-(void)reject:(NSString *)code message:(NSString *)message error:(NSError *)error {
    if ([self hasPendingPromise]) {
        NSLog(@"[BleLibrary] rejecting promise with code:%@ message:%@ error:%@", code, message, error);
        self.reject(code, message, error);

        self.resolve = nil;
        self.reject = nil;
    } else {
        NSLog(@"[BleLibrary] error: no pending promise set");
    }
}

// sets a promise pending
-(void)setPromise:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if ([self hasPendingPromise]) {
        reject(ErrorModuleBusy, @"an async operation is already in progress!", nil);
    } else {
        self.resolve = resolve;
        self.reject = reject;
    }
}

// returns true if there is a pending promise
-(bool)hasPendingPromise {
    return self.reject != nil || self.resolve != nil;
}

// true if a device is connected to the module
-(bool)isConnected {
    return self.peripheral != nil && self.peripheral.state == CBPeripheralStateConnected;
}

// true if the manager is initialized
-(bool)isBlePoweredOn {
    return self.manager != nil && self.manager.state == CBManagerStatePoweredOn;
}

@end

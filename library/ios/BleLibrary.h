@import CoreBluetooth;

#import <CoreBluetooth/CoreBluetooth.h>

#import "PendingRead.h"
#import "PendingWrite.h"

#ifdef RCT_NEW_ARCH_ENABLED
#import "RNBleLibrarySpec.h"

@interface BleLibrary : RCTEventEmitter <NativeBleLibrarySpec, CBCentralManagerDelegate, CBPeripheralDelegate>
#else
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface BleLibrary : RCTEventEmitter <RCTBridgeModule, CBCentralManagerDelegate, CBPeripheralDelegate>
#endif
@property(strong, nullable) CBCentralManager *manager;
@property(strong, nullable) CBPeripheral *peripheral;

@property(strong, nullable) RCTPromiseRejectBlock reject;
@property(strong, nullable) RCTPromiseResolveBlock resolve;

@property(strong, nullable) PendingWrite *write;
@property(strong, nullable) PendingRead *read;

@property(strong, nonnull) NSTimer *timeout;
@end

@import CoreBluetooth;

#import <CoreBluetooth/CoreBluetooth.h>

#ifdef RCT_NEW_ARCH_ENABLED
#import "RNBleLibrarySpec.h"

@interface BleLibrary : RCTEventEmitter <NativeBleLibrarySpec, CBCentralManagerDelegate, CBPeripheralDelegate>
#else
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface BleLibrary : RCTEventEmitter <RCTBridgeModule, CBCentralManagerDelegate, CBPeripheralDelegate>
#endif

/// true if the module is initialized. This means that the module init is done, permissions are granted and BLE is enabled
@property(readonly, getter=isModuleInitialized) BOOL isModuleInitialized;

/// true if a device is connected
@property(readonly, getter=isConnected) BOOL isConnected;

@end

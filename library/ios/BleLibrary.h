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
@end

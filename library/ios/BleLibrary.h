
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNBleLibrarySpec.h"

@interface BleLibrary : NSObject <NativeBleLibrarySpec>
#else
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface BleLibrary : RCTEventEmitter <RCTBridgeModule>
#endif

@end

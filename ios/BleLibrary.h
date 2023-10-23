
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNBleLibrarySpec.h"

@interface BleLibrary : NSObject <NativeBleLibrarySpec>
#else
#import <React/RCTBridgeModule.h>

@interface BleLibrary : NSObject <RCTBridgeModule>
#endif

@end

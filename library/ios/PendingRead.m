#import "PendingRead.h"

@implementation PendingRead

-(id _Nonnull)init:(NSUInteger)size characteristic:(CBCharacteristic *_Nonnull)characteristic {
    self = [super init];
    if (self != nil) {
        self.size = size;
        self.characteristic = characteristic;
        self.read = 0;
        self.data = [[NSMutableData alloc] init];
    }
    return self;
}

-(void)putChunk:(NSData * _Nonnull)data {
    [self.data appendData:data];
}

- (bool)hasMoreData {
    return self.size > 0 && self.size < [self.data length];
}

@end

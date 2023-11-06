#import "PendingRead.h"

const uint8_t EOF_BYTE = 0xff;

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
    uint8_t byte = 0;
    if (self.data.length == 1 && ([self.data getBytes:&byte length:1], byte == EOF_BYTE)) {
      // EOF reached, ignore the rest of the data
      self.size = self.data.length;
    } else {
      [self.data appendData:data];
    }
}

- (bool)hasMoreData {
    return self.size > 0 && self.size < self.data.length;
}

@end

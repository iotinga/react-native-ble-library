#import "PendingRead.h"

const uint8_t EOF_BYTE = 0xff;

@implementation PendingRead

- (instancetype)init:(NSString *)transactionId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject size:(NSUInteger)size {
    self = [super init:transactionId resolve:resolve reject:reject];
    if (self != nil) {
        _size = size;
        _data = [[NSMutableData alloc] init];
        _hasMoreData = YES;
    }
    return self;
}

- (void)putChunk:(nonnull NSData *)data {
    uint8_t byte = 0;
    if (_data.length == 1 && ([_data getBytes:&byte length:1], byte == EOF_BYTE)) {
        // EOF reached
        _hasMoreData = NO;
    } else {
        [_data appendData:data];
        _hasMoreData = self.read < self.size;
    }
}

- (NSUInteger)read {
    return _data.length;
}

@end

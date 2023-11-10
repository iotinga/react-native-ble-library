#import "PendingWrite.h"

@implementation PendingWrite

- (instancetype)init:(NSString *)transactionId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
     data:(NSData *)data chunkSize:(NSUInteger)chunkSize {
    self = [super init:transactionId resolve:resolve reject:reject];
    if (self != nil) {
        _data = data;
        _size = data.length;
        _written = 0;
        _chunkSize = chunkSize;
    }
    return self;
}

- (BOOL)hasMoreChunks {
    return _written < _size;
}

- (nullable NSData *)getChunk {
    if (!self.hasMoreChunks) {
        return nil;
    } else {
        NSRange range = {
            .location = _written,
            .length = _chunkSize,
        };
        
        NSUInteger remainingLength = _size - _written;
        if (range.length > remainingLength) {
            range.length = remainingLength;
        }
        
        _written += range.length;
        
        return [_data subdataWithRange:range];
    }
}

@end

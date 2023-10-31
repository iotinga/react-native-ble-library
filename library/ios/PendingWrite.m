#import "PendingWrite.h"

@implementation PendingWrite

-(id _Nonnull)init:(NSData *)data chunkSize:(NSUInteger)chunkSize {
    self = [super init];
    if (self != nil) {
        self.data = data;
        self.size = data.length;
        self.written = 0;
        self.chunkSize = chunkSize;
    }
    return self;
}

-(bool)hasMoreChunks {
    return self.written < self.size;
}

-(NSData * _Nonnull)getChunk {
    NSRange range = {
        .location = self.written,
        .length = self.chunkSize,
    };
    
    NSUInteger remainingLength = self.size - self.written;
    if (range.length > remainingLength) {
        range.length = remainingLength;
    }
    
    self.written += range.length;

    return [self.data subdataWithRange:range];
}

@end

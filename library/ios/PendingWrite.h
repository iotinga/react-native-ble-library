#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

NS_ASSUME_NONNULL_BEGIN

@interface PendingWrite : NSObject
@property NSUInteger size;
@property NSUInteger written;
@property NSUInteger chunkSize;
@property(strong) NSData *data;

-(id)init:(NSData *)data chunkSize:(NSUInteger)chunkSize;
-(NSData *) getChunk;
-(bool) hasMoreChunks;

@end

NS_ASSUME_NONNULL_END

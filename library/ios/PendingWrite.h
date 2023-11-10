#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import "Transaction.h"

NS_ASSUME_NONNULL_BEGIN

@interface PendingWrite : Transaction

/// total size that needs to be written
@property(readonly) NSUInteger size;

/// bytes that were written successfully to the device
@property(readonly) NSUInteger written;

/// size to use for the write of characteristics to the device
@property(readonly) NSUInteger chunkSize;

/// data buffer to write
@property(strong, readonly, nonatomic) NSData *data;

/// true if there are more chunks of data to write
@property(readonly, getter=hasMoreChunks) BOOL hasMoreChunks;

- (instancetype)init:(NSString *)transactionId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
     data:(NSData *)data chunkSize:(NSUInteger)chunkSize;

/// get next chunk of data to write. Considers the chunk that is retrieved as written
///
/// @return new chunk of data, nil if there are no more chunk of data
- (nullable NSData *) getChunk;

@end

NS_ASSUME_NONNULL_END

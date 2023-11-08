#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import "Transaction.h"

NS_ASSUME_NONNULL_BEGIN

@interface PendingWrite : Transaction
@property NSUInteger size;
@property NSUInteger written;
@property NSUInteger chunkSize;
@property(strong) NSData *data;
@property(weak, nullable) CBCharacteristic *characteristic;

-(id)init:(NSString *)transactionId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
     data:(NSData *)data chunkSize:(NSUInteger)chunkSize;
-(NSData *) getChunk;
-(bool) hasMoreChunks;

@end

NS_ASSUME_NONNULL_END

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

#import "Transaction.h"

NS_ASSUME_NONNULL_BEGIN

@interface PendingRead : Transaction
@property NSUInteger size;
@property NSUInteger read;
@property(strong) NSMutableData *data;

-(id)init:(NSString *)transactionId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
     size:(NSUInteger)size;
-(void)putChunk:(NSData *)data;
-(bool)hasMoreData;

@end

NS_ASSUME_NONNULL_END

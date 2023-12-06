#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

#import "Transaction.h"

NS_ASSUME_NONNULL_BEGIN

@interface PendingRead : Transaction

/// total bytes to read from the device
@property(readonly) NSUInteger size;

/// bytes currently read from the device and available in data
@property(readonly, getter=read) NSUInteger read;

/// data read from the device
@property(strong, readonly, nonatomic) NSMutableData *data;

/// true if more data needs to be received from the device
@property(readonly) BOOL hasMoreData;

- (instancetype)init:(NSString *)transactionId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
     size:(NSUInteger)size;

/// put a new chunk of data, obtained from a read
///
/// @param data to add to the read data
- (void)putChunk:(NSData *)data;

@end

NS_ASSUME_NONNULL_END

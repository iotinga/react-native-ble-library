#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

NS_ASSUME_NONNULL_BEGIN

@interface PendingRead : NSObject
@property NSUInteger size;
@property NSUInteger read;
@property(strong) NSMutableData *data;
@property(weak, nullable) CBCharacteristic *characteristic;

-(id)init:(NSUInteger)size characteristic:(CBCharacteristic *_Nonnull)characteristic;
-(void)putChunk:(NSData *)data;
-(bool)hasMoreData;

@end

NS_ASSUME_NONNULL_END

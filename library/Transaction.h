#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

typedef enum {
    T_STATE_EXECUTING,
    T_STATE_CANCELED,
    T_STATE_SUCCEEDED,
    T_STATE_FAILED,
} TransactionState;

@interface Transaction : NSObject
@property(readonly) TransactionState state;
@property(readonly, getter=isCompleted) BOOL isCompleted;
@property(strong, readonly) NSString *transactionId;

-(id)init:(NSString *)transactionId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
-(void)cancel;
-(void)succeed:(id _Nullable)payload;
-(void)fail:(NSString *)code message:(NSString *)message error:(NSError *_Nullable)error;

@end

NS_ASSUME_NONNULL_END

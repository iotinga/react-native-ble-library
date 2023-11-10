#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

/// state of a transaction
typedef NS_ENUM(NSInteger, TransactionState) {
    /// the transaction is executing
    T_STATE_EXECUTING,
    
    /// the transaction was canceled
    T_STATE_CANCELED,
    
    /// the transaction completed successfully
    T_STATE_SUCCEEDED,
    
    /// the transaction failed to execute
    T_STATE_FAILED,
};

@interface Transaction : NSObject

/// returns the transaction state
@property(readonly) TransactionState state;

/// true if the transaction is in one of the 3 terminal states
@property(readonly, getter=isCompleted) BOOL isCompleted;

/// ID of the transaction
@property(strong, readonly, nonatomic) NSString *transactionId;

- (instancetype)init:(NSString *)transactionId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;

/// cancels the transaction
- (void)cancel;

/// completes the transaction successfully
///
/// @param payload optional object payload, or nil
- (void)succeed:(nullable id)payload;

/// fails the execution of the transactoin
///
/// @param code an error code (one from BleErrorCode.h)
/// @param message a message indicating the error
/// @param error NSError or nil if not applicable
- (void)fail:(NSString *)code message:(NSString *)message error:(nullable NSError *)error;

@end

NS_ASSUME_NONNULL_END

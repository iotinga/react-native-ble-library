#import "Transaction.h"
#import "BleErrorCode.h"

@implementation Transaction {
    RCTPromiseResolveBlock _resolve;
    RCTPromiseRejectBlock _reject;
}

- (instancetype)init:(NSString *)transactionId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    self = [super init];
    if (self != nil) {
        _state = T_STATE_EXECUTING;
        _transactionId = transactionId;
        _resolve = resolve;
        _reject = reject;
    }
    return self;
}

- (BOOL)isCompleted {
    return self.state != T_STATE_EXECUTING;
}

- (void)cancel {
    if (!self.isCompleted) {
        _reject(ERROR_OPERATION_CANCELED, @"the current transaction was canceled", nil);
        _state = T_STATE_CANCELED;
    }
}

- (void)succeed:(nullable id)payload {
    if (!self.isCompleted) {
        _resolve(payload);
        _state = T_STATE_SUCCEEDED;
    }
}

- (void)fail:(NSString *)code message:(NSString *)message error:(nullable NSError *)error {
    if (!self.isCompleted) {
        _reject(code, message, error);
        _state = T_STATE_FAILED;
    }
}

- (void)dealloc {
    // ensure promise are canceled when object is de-allocated
    [self cancel];
}

@end

#import "BleLibrary.h"

static NSString *const BleLibraryEventName = @"BleLibraryEvent";

static NSString *const BleGenericError = @"BleGenericError";
static NSString *const BleDeviceDisconnected = @"BleDeviceDisconnected";
static NSString *const BleInvalidState = @"BleInvalidState";

static NSString *const CmdTypePing = @"ping";
static NSString *const CmdTypeScan = @"scan";
static NSString *const CmdTypeStopScan = @"stopScan";
static NSString *const CmdTypeConnect = @"connect";
static NSString *const CmdTypeDisconnect = @"disconnect";
static NSString *const CmdTypeWrite = @"write";
static NSString *const CmdTypeRead = @"read";
static NSString *const CmdTypeSubscribe = @"subscribe";

static NSString *const CmdResponseTypePong = @"pong";
static NSString *const CmdResponseTypeError = @"error";
static NSString *const CmdResponseTypeScanResult = @"scanResult";
static NSString *const CmdResponseTypeScanStopped = @"scanStopped";
static NSString *const CmdResponseTypeScanStarted = @"scanStarted";
static NSString *const CmdResponseTypeConnected = @"connected";
static NSString *const CmdResponseTypeDisconnected = @"disconnected";
static NSString *const CmdResponseTypeSubscribe = @"subscribe";
static NSString *const CmdResponseTypeCharValueChanged = @"charValueChanged";
static NSString *const CmdResponseTypeWriteCompleted = @"writeCompleted";
static NSString *const CmdResponseTypeWriteProgress = @"writeProgress";

@implementation BleLibrary
{
    bool hasListeners;
}

RCT_EXPORT_MODULE()

-(void)startObserving {
    hasListeners = YES;
}

-(void)stopObserving {
    hasListeners = NO;
}

-(NSArray<NSString *> *)supportedEvents {
    return @[BleLibraryEventName];
}

-(void)sendEvent:(NSDictionary *)event {
    NSLog(@"[BleLibrary] send event %@", event);

    [self sendEventWithName:BleLibraryEventName body:event];
}

-(void)executeCommand:(NSDictionary *)command {
    NSLog(@"[BleLibrary] executing command %@", command);

    NSString *type = [command objectForKey:@"type"];
    if ([type isEqualToString:CmdTypePing]) {
        [self sendEvent:@{@"type":CmdResponseTypePong}];
    }
}

RCT_EXPORT_METHOD(sendCommands:(NSArray<NSDictionary *> *)commands
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"[BleLibrary] received %lu commands", (unsigned long)commands.count);

    for (NSDictionary *command in commands) {
        [self executeCommand:command];
    }

    resolve(nil);
}

@end

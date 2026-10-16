// macOS in-app updates through Sparkle for the Electron main process.
//
// The same two calls the Flutter runner answers on `gang_chat/updater`
// (macos/Runner/AppDelegate.swift and MainFlutterWindow.swift), so both
// clients update from the one appcast with the same windows:
//   isAvailable() -> bool: the build carries SUPublicEDKey and Sparkle started.
//   checkForUpdates(feedUrl, background) -> bool: false when Sparkle is not
//     configured. A background check only shows UI when an update exists; a
//     user check always reports back, including "up to date".
//
// Built by scripts/prepare_sparkle.mjs. Electron runs its main process on the
// AppKit main thread, so these calls reach Sparkle on the thread it expects.

#import <AppKit/AppKit.h>
#import <Sparkle/Sparkle.h>
#include <node_api.h>

@interface GangSparkleDelegate : NSObject <SPUUpdaterDelegate>
// Set from the app's configured release bucket so a custom bucket also moves
// the update feed; Info.plist's SUFeedURL is the fallback.
@property(nonatomic, copy, nullable) NSString *feedURL;
@end

@implementation GangSparkleDelegate
- (nullable NSString *)feedURLStringForUpdater:(SPUUpdater *)updater {
  return self.feedURL;
}
@end

static GangSparkleDelegate *gDelegate;
static SPUStandardUpdaterController *gController;
static BOOL gStarted;

// Starts Sparkle once, if this build carries an update-signing public key, so
// a build without one never shows Sparkle's configuration alert.
static BOOL StartUpdaterIfConfigured(void) {
  if (gStarted) return YES;
  id publicKey = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"SUPublicEDKey"];
  if (![publicKey isKindOfClass:[NSString class]] ||
      [[publicKey stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet] length] == 0) {
    return NO;
  }
  if (gController == nil) {
    gDelegate = [GangSparkleDelegate new];
    gController = [[SPUStandardUpdaterController alloc] initWithStartingUpdater:NO
                                                                updaterDelegate:gDelegate
                                                             userDriverDelegate:nil];
  }
  NSError *error = nil;
  if (![gController.updater startUpdater:&error]) {
    NSLog(@"Sparkle updater failed to start: %@", error);
    return NO;
  }
  gStarted = YES;
  return YES;
}

static napi_value JsBoolean(napi_env env, bool value) {
  napi_value result;
  napi_get_boolean(env, value, &result);
  return result;
}

static napi_value IsAvailable(napi_env env, napi_callback_info info) {
  return JsBoolean(env, StartUpdaterIfConfigured());
}

static napi_value CheckForUpdates(napi_env env, napi_callback_info info) {
  size_t argc = 2;
  napi_value argv[2];
  napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);

  napi_valuetype type = napi_undefined;
  if (argc > 0) napi_typeof(env, argv[0], &type);
  NSString *feedURL = nil;
  if (type == napi_string) {
    size_t length = 0;
    napi_get_value_string_utf8(env, argv[0], nullptr, 0, &length);
    char *buffer = (char *)malloc(length + 1);
    napi_get_value_string_utf8(env, argv[0], buffer, length + 1, &length);
    feedURL = [[NSString alloc] initWithBytes:buffer length:length encoding:NSUTF8StringEncoding];
    free(buffer);
  }
  bool background = false;
  if (argc > 1) napi_get_value_bool(env, argv[1], &background);

  if (feedURL.length > 0) {
    if (gDelegate == nil) gDelegate = [GangSparkleDelegate new];
    gDelegate.feedURL = feedURL;
  }
  if (!StartUpdaterIfConfigured()) return JsBoolean(env, false);
  if (background) {
    [gController.updater checkForUpdatesInBackground];
  } else {
    [gController checkForUpdates:nil];
  }
  return JsBoolean(env, true);
}

NAPI_MODULE_INIT() {
  napi_value isAvailable;
  napi_value checkForUpdates;
  napi_create_function(env, "isAvailable", NAPI_AUTO_LENGTH, IsAvailable, nullptr, &isAvailable);
  napi_create_function(env, "checkForUpdates", NAPI_AUTO_LENGTH, CheckForUpdates, nullptr, &checkForUpdates);
  napi_set_named_property(env, exports, "isAvailable", isAvailable);
  napi_set_named_property(env, exports, "checkForUpdates", checkForUpdates);
  return exports;
}

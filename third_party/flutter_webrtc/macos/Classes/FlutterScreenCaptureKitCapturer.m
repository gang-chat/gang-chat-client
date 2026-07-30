#import "FlutterScreenCaptureKitCapturer.h"
#import "FlutterScreenAudioDevice.h"

#import <CoreGraphics/CoreGraphics.h>
#import <CoreMedia/CoreMedia.h>

#if __has_include(<ScreenCaptureKit/ScreenCaptureKit.h>)
#import <ScreenCaptureKit/ScreenCaptureKit.h>
#endif

static const NSInteger kScreenAudioSampleRate = 48000;
static const NSInteger kScreenAudioChannels = 2;

@interface FlutterScreenCaptureKitCapturer ()
#if __has_include(<ScreenCaptureKit/ScreenCaptureKit.h>)
<SCStreamOutput>
#endif
@property(nonatomic, strong) RTCVideoCapturer *capturer;
@property(nonatomic, weak) id<RTCVideoCapturerDelegate> delegate;
@property(nonatomic, strong) dispatch_queue_t captureQueue;
#if __has_include(<ScreenCaptureKit/ScreenCaptureKit.h>)
@property(nonatomic, strong) SCStream *stream API_AVAILABLE(macos(12.3));
#endif
@end

@implementation FlutterScreenCaptureKitCapturer

- (instancetype)initWithDelegate:(id<RTCVideoCapturerDelegate>)delegate {
  self = [super init];
  if (self) {
    _delegate = delegate;
    _capturer = [[RTCVideoCapturer alloc] initWithDelegate:delegate];
    _captureQueue = dispatch_queue_create("com.iperius.sck.capture", DISPATCH_QUEUE_SERIAL);
  }
  return self;
}

- (void)startCaptureWithFPS:(NSInteger)fps
                   sourceId:(NSString* _Nullable)sourceId
              captureWindow:(BOOL)captureWindow
               captureAudio:(BOOL)captureAudio
                  onStarted:(void (^_Nonnull)(NSError* _Nullable error))onStarted {
  NSLog(@"SCK capturer: startCapture fps=%ld captureAudio=%d sourceId=%@ window=%d",
        (long)fps, captureAudio, sourceId, captureWindow);
#if __has_include(<ScreenCaptureKit/ScreenCaptureKit.h>)
  if (@available(macOS 12.3, *)) {
    if (!CGPreflightScreenCaptureAccess()) {
      if (!CGRequestScreenCaptureAccess()) {
        NSError *permissionDenied = [NSError errorWithDomain:@"FlutterScreenCaptureKit"
                                                        code:-3
                                                    userInfo:@{NSLocalizedDescriptionKey: @"Screen recording permission denied"}];
        onStarted(permissionDenied);
        return;
      }
    }
    [SCShareableContent getShareableContentWithCompletionHandler:^(SCShareableContent *content, NSError *error) {
      if (error != nil) {
        onStarted(error);
        return;
      }

      SCContentFilter *filter = nil;
      size_t outputWidth = 1;
      size_t outputHeight = 1;
      if (captureWindow) {
        SCWindow *window = [self selectWindowFromContent:content sourceId:sourceId];
        if (window == nil) {
          NSError *noWindow = [NSError errorWithDomain:@"FlutterScreenCaptureKit"
                                                  code:-1
                                              userInfo:@{NSLocalizedDescriptionKey: @"No matching window"}];
          onStarted(noWindow);
          return;
        }
        filter = [[SCContentFilter alloc] initWithDesktopIndependentWindow:window];
        outputWidth = (size_t)MAX(1.0, ceil(CGRectGetWidth(window.frame)));
        outputHeight = (size_t)MAX(1.0, ceil(CGRectGetHeight(window.frame)));
      } else {
        SCDisplay *display = [self selectDisplayFromContent:content sourceId:sourceId];
        if (display == nil) {
          NSError *noDisplay = [NSError errorWithDomain:@"FlutterScreenCaptureKit"
                                                   code:-1
                                               userInfo:@{NSLocalizedDescriptionKey: @"No matching display"}];
          onStarted(noDisplay);
          return;
        }
        // Exclude this process from a full-display capture. Otherwise opening
        // Gang Chat's local screen-share preview full screen creates a near
        // 1:1 video feedback loop that continuously re-encodes its own output
        // and can eventually exhaust the renderer. Application filtering also
        // covers Flutter dialogs and full-screen windows created after capture
        // starts, unlike a one-time list of window identifiers.
        const pid_t currentProcessId =
            [[NSProcessInfo processInfo] processIdentifier];
        NSMutableArray<SCRunningApplication *> *excludedApplications =
            [NSMutableArray array];
        for (SCRunningApplication *application in content.applications) {
          if (application.processID == currentProcessId) {
            [excludedApplications addObject:application];
          }
        }
        if (excludedApplications.count > 0) {
          filter = [[SCContentFilter alloc]
              initWithDisplay:display
              excludingApplications:excludedApplications
              exceptingWindows:@[]];
        } else {
          // Defensive fallback for unusual shareable-content snapshots where
          // the process is absent from `applications` but its window is still
          // listed. This keeps the capture safe without failing screen share.
          NSMutableArray<SCWindow *> *excludedWindows = [NSMutableArray array];
          for (SCWindow *window in content.windows) {
            if (window.owningApplication.processID == currentProcessId) {
              [excludedWindows addObject:window];
            }
          }
          filter = [[SCContentFilter alloc] initWithDisplay:display
                                          excludingWindows:excludedWindows];
        }
        outputWidth = (size_t)MAX(1, display.width);
        outputHeight = (size_t)MAX(1, display.height);
      }

      SCStreamConfiguration *config = [SCStreamConfiguration new];
      config.width = outputWidth;
      config.height = outputHeight;
      config.minimumFrameInterval = CMTimeMake(1, (int32_t)MAX(1, fps));
      config.pixelFormat = kCVPixelFormatType_420YpCbCr8BiPlanarFullRange;
      if (captureWindow) {
        config.scalesToFit = YES;
      }
      if (@available(macOS 13.0, *)) {
        config.showsCursor = YES;
        if (captureAudio) {
          NSLog(@"SCK capturer: configuring audio (rate=%ld ch=%ld)",
                (long)kScreenAudioSampleRate, (long)kScreenAudioChannels);
          config.sampleRate = kScreenAudioSampleRate;
          config.channelCount = kScreenAudioChannels;
          config.capturesAudio = YES;
          config.excludesCurrentProcessAudio = YES;
        }
      } else if (captureAudio) {
        NSError *unsupportedAudio = [NSError errorWithDomain:@"FlutterScreenCaptureKit"
                                                        code:-4
                                                    userInfo:@{NSLocalizedDescriptionKey: @"ScreenCaptureKit audio capture requires macOS 13.0 or newer"}];
        onStarted(unsupportedAudio);
        return;
      }

      self.stream = [[SCStream alloc] initWithFilter:filter configuration:config delegate:nil];
      NSError *addOutputError = nil;
      [self.stream addStreamOutput:self
                              type:SCStreamOutputTypeScreen
               sampleHandlerQueue:self.captureQueue
                            error:&addOutputError];
      if (addOutputError != nil) {
        onStarted(addOutputError);
        return;
      }

      if (captureAudio) {
        if (@available(macOS 13.0, *)) {
          NSError *addAudioOutputError = nil;
          [self.stream addStreamOutput:self
                                  type:SCStreamOutputTypeAudio
                   sampleHandlerQueue:self.captureQueue
                                error:&addAudioOutputError];
          if (addAudioOutputError != nil) {
            NSLog(@"SCK capturer: audio output add FAILED: %@", addAudioOutputError);
            onStarted(addAudioOutputError);
            return;
          }
          NSLog(@"SCK capturer: audio output added successfully");
        } else {
          NSError *unsupportedAudio = [NSError errorWithDomain:@"FlutterScreenCaptureKit"
                                                          code:-4
                                                      userInfo:@{NSLocalizedDescriptionKey: @"ScreenCaptureKit audio capture requires macOS 13.0 or newer"}];
          onStarted(unsupportedAudio);
          return;
        }
      }

      NSLog(@"SCK capturer: starting capture...");
      [self.stream startCaptureWithCompletionHandler:^(NSError * _Nullable startError) {
        if (startError != nil) {
          NSLog(@"SCK capturer: startCapture FAILED: %@", startError);
        } else {
          NSLog(@"SCK capturer: startCapture succeeded");
        }
        onStarted(startError);
      }];
    }];
    return;
  }
#endif

  NSError *unavailable = [NSError errorWithDomain:@"FlutterScreenCaptureKit"
                                             code:-2
                                         userInfo:@{NSLocalizedDescriptionKey: @"ScreenCaptureKit not available"}];
  onStarted(unavailable);
}

- (void)startAudioOnlyCaptureForWindowSourceId:(NSString* _Nonnull)sourceId
                                     onStarted:(void (^_Nonnull)(NSError* _Nullable error))onStarted {
  NSLog(@"SCK capturer: startAudioOnlyCapture sourceId=%@", sourceId);
#if __has_include(<ScreenCaptureKit/ScreenCaptureKit.h>)
  if (@available(macOS 13.0, *)) {
    if (!CGPreflightScreenCaptureAccess()) {
      if (!CGRequestScreenCaptureAccess()) {
        NSError *permissionDenied = [NSError errorWithDomain:@"FlutterScreenCaptureKit"
                                                        code:-3
                                                    userInfo:@{NSLocalizedDescriptionKey: @"Screen recording permission denied"}];
        onStarted(permissionDenied);
        return;
      }
    }
    [SCShareableContent getShareableContentWithCompletionHandler:^(SCShareableContent *content, NSError *error) {
      if (error != nil) {
        onStarted(error);
        return;
      }
      SCWindow *window = [self selectWindowFromContent:content sourceId:sourceId];
      if (window == nil) {
        NSError *noWindow = [NSError errorWithDomain:@"FlutterScreenCaptureKit"
                                                code:-1
                                            userInfo:@{NSLocalizedDescriptionKey: @"No matching window for audio capture"}];
        onStarted(noWindow);
        return;
      }
      SCContentFilter *filter = [[SCContentFilter alloc] initWithDesktopIndependentWindow:window];

      SCStreamConfiguration *config = [SCStreamConfiguration new];
      // SCStream requires a valid video configuration even when only audio is
      // consumed; keep it minimal since no screen output is added.
      config.width = (size_t)MAX(2.0, ceil(CGRectGetWidth(window.frame)));
      config.height = (size_t)MAX(2.0, ceil(CGRectGetHeight(window.frame)));
      config.minimumFrameInterval = CMTimeMake(1, 1);
      config.pixelFormat = kCVPixelFormatType_420YpCbCr8BiPlanarFullRange;
      config.scalesToFit = YES;
      NSLog(@"SCK capturer: configuring audio-only (rate=%ld ch=%ld)",
            (long)kScreenAudioSampleRate, (long)kScreenAudioChannels);
      config.sampleRate = kScreenAudioSampleRate;
      config.channelCount = kScreenAudioChannels;
      config.capturesAudio = YES;
      config.excludesCurrentProcessAudio = YES;

      self.stream = [[SCStream alloc] initWithFilter:filter configuration:config delegate:nil];
      NSError *addAudioOutputError = nil;
      [self.stream addStreamOutput:self
                              type:SCStreamOutputTypeAudio
               sampleHandlerQueue:self.captureQueue
                            error:&addAudioOutputError];
      if (addAudioOutputError != nil) {
        NSLog(@"SCK capturer: audio-only output add FAILED: %@", addAudioOutputError);
        onStarted(addAudioOutputError);
        return;
      }
      NSLog(@"SCK capturer: audio-only output added successfully");
      [self.stream startCaptureWithCompletionHandler:^(NSError * _Nullable startError) {
        if (startError != nil) {
          NSLog(@"SCK capturer: audio-only startCapture FAILED: %@", startError);
        } else {
          NSLog(@"SCK capturer: audio-only startCapture succeeded");
        }
        onStarted(startError);
      }];
    }];
    return;
  }
#endif
  NSError *unavailable = [NSError errorWithDomain:@"FlutterScreenCaptureKit"
                                             code:-2
                                         userInfo:@{NSLocalizedDescriptionKey: @"ScreenCaptureKit audio capture requires macOS 13.0 or newer"}];
  onStarted(unavailable);
}

- (void)stopCaptureWithCompletion:(void (^_Nonnull)(void))completion {
#if __has_include(<ScreenCaptureKit/ScreenCaptureKit.h>)
  if (@available(macOS 12.3, *)) {
    if (self.stream == nil) {
      completion();
      return;
    }
    SCStream *stream = self.stream;
    self.stream = nil;
    [stream stopCaptureWithCompletionHandler:^(__unused NSError * _Nullable error) {
      completion();
    }];
  }
#endif
  completion();
}

#if __has_include(<ScreenCaptureKit/ScreenCaptureKit.h>)
- (SCDisplay *)selectDisplayFromContent:(SCShareableContent *)content
                               sourceId:(NSString *)sourceId API_AVAILABLE(macos(12.3)) {
  if (content.displays.count == 0) {
    return nil;
  }

  if (sourceId != nil && sourceId.length > 0) {
    for (SCDisplay *display in content.displays) {
      if ([[NSString stringWithFormat:@"%u", display.displayID] isEqualToString:sourceId]) {
        return display;
      }
    }
  }

  CGDirectDisplayID mainDisplay = CGMainDisplayID();
  for (SCDisplay *display in content.displays) {
    if (display.displayID == mainDisplay) {
      return display;
    }
  }

  return content.displays.firstObject;
}

- (SCWindow *)selectWindowFromContent:(SCShareableContent *)content
                             sourceId:(NSString *)sourceId API_AVAILABLE(macos(12.3)) {
  if (sourceId == nil || sourceId.length == 0) {
    return nil;
  }
  for (SCWindow *window in content.windows) {
    if ([[NSString stringWithFormat:@"%u", window.windowID] isEqualToString:sourceId]) {
      return window;
    }
  }
  return nil;
}

- (void)stream:(SCStream *)stream
didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
        ofType:(SCStreamOutputType)type API_AVAILABLE(macos(12.3)) {
  if (@available(macOS 13.0, *)) {
    if (type == SCStreamOutputTypeAudio) {
      static BOOL loggedFirstAudio = NO;
      if (!loggedFirstAudio) {
        loggedFirstAudio = YES;
        NSLog(@"SCK capturer: first AUDIO sample received, forwarding to device");
      }
      [[FlutterScreenAudioDevice sharedInstance] enqueueSampleBuffer:sampleBuffer];
      return;
    }
  }

  CVPixelBufferRef pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
  if (pixelBuffer == nil) {
    return;
  }

  CMTime timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer);
  int64_t timeStampNs = (int64_t)(CMTimeGetSeconds(timestamp) * 1000000000.0);

  id<RTCVideoFrameBuffer> rtcBuffer = [[RTCCVPixelBuffer alloc] initWithPixelBuffer:pixelBuffer];
  RTCVideoFrame *frame = [[RTCVideoFrame alloc] initWithBuffer:rtcBuffer
                                                      rotation:RTCVideoRotation_0
                                                   timeStampNs:timeStampNs];
  [self.delegate capturer:self.capturer didCaptureVideoFrame:frame];
}
#endif

@end

import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/live/screen_share_quality.dart';

void main() {
  group('normalizedScreenShareMaxHeight', () {
    test('passes through supported options', () {
      for (final option in screenShareHeightOptions) {
        expect(normalizedScreenShareMaxHeight(option), option);
      }
    });

    test('falls back to default for null/unsupported values', () {
      expect(normalizedScreenShareMaxHeight(null), defaultScreenShareMaxHeight);
      expect(normalizedScreenShareMaxHeight(0), defaultScreenShareMaxHeight);
      expect(normalizedScreenShareMaxHeight(900), defaultScreenShareMaxHeight);
      expect(normalizedScreenShareMaxHeight(2160), defaultScreenShareMaxHeight);
    });

    test('default is 720', () {
      expect(defaultScreenShareMaxHeight, 720);
    });
  });

  group('screenShareResolutionForHeight', () {
    test('returns 16:9 dimensions with an even width', () {
      expect(
        screenShareResolutionForHeight(480),
        const ScreenShareResolution(854, 480),
      );
      expect(
        screenShareResolutionForHeight(720),
        const ScreenShareResolution(1280, 720),
      );
      expect(
        screenShareResolutionForHeight(1080),
        const ScreenShareResolution(1920, 1080),
      );
    });

    test('width is always even', () {
      for (final option in screenShareHeightOptions) {
        expect(screenShareResolutionForHeight(option).width.isEven, isTrue);
      }
    });

    test('coerces unsupported heights to the default resolution', () {
      expect(
        screenShareResolutionForHeight(999),
        const ScreenShareResolution(1280, 720),
      );
    });
  });

  group('normalizedScreenShareFrameRate', () {
    test('passes through supported desktop options', () {
      for (final option in screenShareFrameRateOptions) {
        expect(normalizedScreenShareFrameRate(option), option);
      }
    });

    test('falls back to the platform default and applies its cap', () {
      expect(normalizedScreenShareFrameRate(null), 30);
      expect(normalizedScreenShareFrameRate(24), 30);
      expect(
        normalizedScreenShareFrameRate(
          60,
          maxFrameRate: androidScreenShareFrameRateCap,
        ),
        30,
      );
      expect(
        normalizedScreenShareFrameRate(
          null,
          maxFrameRate: androidScreenShareFrameRateCap,
        ),
        30,
      );
    });
  });

  group('screenShareQualityForHeight', () {
    test('combines resolution and frame rate into a bitrate profile', () {
      expect(
        screenShareQualityForHeight(480, frameRate: 15),
        const ScreenShareQuality(
          maxHeight: 480,
          maxFrameRate: 15,
          maxBitrate: 1000 * 1000,
        ),
      );
      expect(
        screenShareQualityForHeight(720, frameRate: 30),
        const ScreenShareQuality(
          maxHeight: 720,
          maxFrameRate: 30,
          maxBitrate: 3000 * 1000,
        ),
      );
      expect(
        screenShareQualityForHeight(1080, frameRate: 60),
        const ScreenShareQuality(
          maxHeight: 1080,
          maxFrameRate: 60,
          maxBitrate: 8000 * 1000,
        ),
      );
    });

    test('caps frame rate and bitrate together for Android', () {
      expect(
        screenShareQualityForHeight(
          1080,
          frameRate: 60,
          maxFrameRate: androidScreenShareFrameRateCap,
        ),
        const ScreenShareQuality(
          maxHeight: 1080,
          maxFrameRate: 30,
          maxBitrate: 4000 * 1000,
        ),
      );
      expect(
        screenShareQualityForHeight(
          720,
          frameRate: 60,
          maxFrameRate: androidScreenShareFrameRateCap,
        ).maxFrameRate,
        30,
      );
    });

    test('coerces unsupported heights to the default profile', () {
      expect(
        screenShareQualityForHeight(999, frameRate: 30),
        screenShareQualityForHeight(defaultScreenShareMaxHeight, frameRate: 30),
      );
    });

    test('keeps a stable bits-per-frame budget across FPS options', () {
      expect(
        screenShareQualityForHeight(720, frameRate: 15).maxBitrate,
        1500 * 1000,
      );
      expect(
        screenShareQualityForHeight(720, frameRate: 60).maxBitrate,
        6000 * 1000,
      );
    });
  });

  group('screenShareScaleDownBy', () {
    test('downscales a taller source to the target', () {
      // 4K display capped at 720p.
      expect(
        screenShareScaleDownBy(sourceHeight: 2160, targetHeight: 720),
        closeTo(3.0, 1e-9),
      );
      // 1440p capped at 480p.
      expect(
        screenShareScaleDownBy(sourceHeight: 1440, targetHeight: 480),
        closeTo(3.0, 1e-9),
      );
    });

    test('never scales up when the source already fits', () {
      expect(
        screenShareScaleDownBy(sourceHeight: 720, targetHeight: 1080),
        1.0,
      );
      expect(
        screenShareScaleDownBy(sourceHeight: 1080, targetHeight: 1080),
        1.0,
      );
    });

    test('returns 1.0 for unknown/non-positive source heights', () {
      expect(screenShareScaleDownBy(sourceHeight: 0, targetHeight: 720), 1.0);
      expect(screenShareScaleDownBy(sourceHeight: -5, targetHeight: 720), 1.0);
    });

    test('coerces an unsupported target to the default before scaling', () {
      // target 999 -> 720; a 1080p source is capped at 720p.
      expect(
        screenShareScaleDownBy(sourceHeight: 1080, targetHeight: 999),
        1.5,
      );
    });
  });

  group('screenShareSourceScaleForSample', () {
    test('uses an RID/SSRC matched scale without guessing', () {
      expect(
        screenShareSourceScaleForSample(
          encodedHeight: 540,
          targetHeight: 1080,
          encodingScales: const [1, 2],
          matchedScale: 2,
        ),
        2,
      );
    });

    test('reconstructs a low simulcast sample when native stats omit ids', () {
      expect(
        screenShareSourceScaleForSample(
          encodedHeight: 540,
          targetHeight: 1080,
          encodingScales: const [1, 2],
        ),
        2,
      );
      expect(
        screenShareSourceScaleForSample(
          encodedHeight: 1080,
          targetHeight: 720,
          encodingScales: const [1, 2],
        ),
        1,
      );
    });

    test('uses a known source to identify the closest active layer', () {
      expect(
        screenShareSourceScaleForSample(
          encodedHeight: 240,
          targetHeight: 480,
          encodingScales: const [2.25, 4.5],
          knownSourceHeight: 1080,
        ),
        4.5,
      );
    });
  });

  group('screenShareCappedOutputResolution', () {
    test('keeps source aspect ratio while capping height', () {
      expect(
        screenShareCappedOutputResolution(
          sourceWidth: 3440,
          sourceHeight: 1440,
          targetHeight: 720,
        ),
        const ScreenShareResolution(1720, 720),
      );
    });

    test('does not upscale a smaller source', () {
      expect(
        screenShareCappedOutputResolution(
          sourceWidth: 960,
          sourceHeight: 540,
          targetHeight: 1080,
        ),
        const ScreenShareResolution(960, 540),
      );
    });

    test('uses the selected preset before source dimensions are known', () {
      expect(
        screenShareCappedOutputResolution(
          sourceWidth: null,
          sourceHeight: null,
          targetHeight: 720,
        ),
        const ScreenShareResolution(1280, 720),
      );
    });
  });

  group('screenShareQualityCheckResult', () {
    test(
      'defers verification when parameters are ready but no frames flow',
      () {
        expect(
          screenShareQualityCheckResult(
            parametersReady: true,
            hasOutboundFrames: false,
            resolutionVerified: false,
            frameRateCapVerified: true,
          ),
          ScreenShareQualityCheckResult.awaitingOutboundFrames,
        );
      },
    );

    test('does not hide a rejected parameter update behind missing stats', () {
      expect(
        screenShareQualityCheckResult(
          parametersReady: false,
          hasOutboundFrames: false,
          resolutionVerified: false,
          frameRateCapVerified: true,
        ),
        ScreenShareQualityCheckResult.retry,
      );
    });

    test('requires parameters, resolution and frame rate to match', () {
      expect(
        screenShareQualityCheckResult(
          parametersReady: true,
          hasOutboundFrames: true,
          resolutionVerified: true,
          frameRateCapVerified: true,
        ),
        ScreenShareQualityCheckResult.verified,
      );
      expect(
        screenShareQualityCheckResult(
          parametersReady: true,
          hasOutboundFrames: true,
          resolutionVerified: false,
          frameRateCapVerified: true,
        ),
        ScreenShareQualityCheckResult.retry,
      );
    });
  });

  group('screen-share picker labels', () {
    test('formats supported resolutions and frame rates', () {
      expect(screenShareResolutionLabel(480), '480p');
      expect(screenShareResolutionLabel(720), '720p');
      expect(screenShareResolutionLabel(1080), '1080p');
      expect(screenShareFrameRateLabel(15), '15 FPS');
      expect(screenShareFrameRateLabel(30), '30 FPS');
      expect(screenShareFrameRateLabel(60), '60 FPS');
    });

    test('coerces unsupported values to default labels', () {
      expect(screenShareResolutionLabel(999), '720p');
      expect(screenShareFrameRateLabel(24), '30 FPS');
    });
  });
}

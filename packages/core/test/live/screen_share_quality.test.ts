import { describe, expect, test } from 'vitest';

import {
  ScreenShareQuality,
  ScreenShareQualityCheckResult,
  ScreenShareResolution,
  androidScreenShareFrameRateCap,
  defaultScreenShareMaxHeight,
  normalizedScreenShareFrameRate,
  normalizedScreenShareMaxHeight,
  screenShareCappedOutputResolution,
  screenShareFrameRateLabel,
  screenShareFrameRateOptions,
  screenShareHeightOptions,
  screenShareQualityCheckResult,
  screenShareQualityForHeight,
  screenShareResolutionForHeight,
  screenShareResolutionLabel,
  screenShareScaleDownBy,
  screenShareSourceScaleForSample,
} from '../../src/live/screen_share_quality';

// Port of test/unit/app/screen_share_quality_test.dart.
describe('normalizedScreenShareMaxHeight', () => {
  test('passes through supported options', () => {
    for (const option of screenShareHeightOptions) {
      expect(normalizedScreenShareMaxHeight(option)).toBe(option);
    }
  });

  test('falls back to default for null/unsupported values', () => {
    expect(normalizedScreenShareMaxHeight(null)).toBe(defaultScreenShareMaxHeight);
    expect(normalizedScreenShareMaxHeight(0)).toBe(defaultScreenShareMaxHeight);
    expect(normalizedScreenShareMaxHeight(900)).toBe(defaultScreenShareMaxHeight);
    expect(normalizedScreenShareMaxHeight(2160)).toBe(defaultScreenShareMaxHeight);
  });

  test('default is 720', () => {
    expect(defaultScreenShareMaxHeight).toBe(720);
  });
});

describe('screenShareResolutionForHeight', () => {
  test('returns 16:9 dimensions with an even width', () => {
    expect(screenShareResolutionForHeight(480).equals(new ScreenShareResolution(854, 480))).toBe(true);
    expect(screenShareResolutionForHeight(720).equals(new ScreenShareResolution(1280, 720))).toBe(true);
    expect(screenShareResolutionForHeight(1080).equals(new ScreenShareResolution(1920, 1080))).toBe(true);
  });

  test('width is always even', () => {
    for (const option of screenShareHeightOptions) {
      expect(screenShareResolutionForHeight(option).width % 2).toBe(0);
    }
  });

  test('coerces unsupported heights to the default resolution', () => {
    expect(screenShareResolutionForHeight(999).equals(new ScreenShareResolution(1280, 720))).toBe(true);
  });
});

describe('normalizedScreenShareFrameRate', () => {
  test('passes through supported desktop options', () => {
    for (const option of screenShareFrameRateOptions) {
      expect(normalizedScreenShareFrameRate(option)).toBe(option);
    }
  });

  test('falls back to the platform default and applies its cap', () => {
    expect(normalizedScreenShareFrameRate(null)).toBe(30);
    expect(normalizedScreenShareFrameRate(24)).toBe(30);
    expect(normalizedScreenShareFrameRate(60, { maxFrameRate: androidScreenShareFrameRateCap })).toBe(30);
    expect(normalizedScreenShareFrameRate(null, { maxFrameRate: androidScreenShareFrameRateCap })).toBe(30);
  });
});

describe('screenShareQualityForHeight', () => {
  test('combines resolution and frame rate into a bitrate profile', () => {
    expect(
      screenShareQualityForHeight(480, { frameRate: 15 }).equals(
        new ScreenShareQuality({ maxHeight: 480, maxFrameRate: 15, maxBitrate: 1000 * 1000 }),
      ),
    ).toBe(true);
    expect(
      screenShareQualityForHeight(720, { frameRate: 30 }).equals(
        new ScreenShareQuality({ maxHeight: 720, maxFrameRate: 30, maxBitrate: 3000 * 1000 }),
      ),
    ).toBe(true);
    expect(
      screenShareQualityForHeight(1080, { frameRate: 60 }).equals(
        new ScreenShareQuality({ maxHeight: 1080, maxFrameRate: 60, maxBitrate: 8000 * 1000 }),
      ),
    ).toBe(true);
  });

  test('caps frame rate and bitrate together for Android', () => {
    expect(
      screenShareQualityForHeight(1080, { frameRate: 60, maxFrameRate: androidScreenShareFrameRateCap }).equals(
        new ScreenShareQuality({ maxHeight: 1080, maxFrameRate: 30, maxBitrate: 4000 * 1000 }),
      ),
    ).toBe(true);
    expect(
      screenShareQualityForHeight(720, { frameRate: 60, maxFrameRate: androidScreenShareFrameRateCap }).maxFrameRate,
    ).toBe(30);
  });

  test('coerces unsupported heights to the default profile', () => {
    expect(
      screenShareQualityForHeight(999, { frameRate: 30 }).equals(
        screenShareQualityForHeight(defaultScreenShareMaxHeight, { frameRate: 30 }),
      ),
    ).toBe(true);
  });

  test('keeps a stable bits-per-frame budget across FPS options', () => {
    expect(screenShareQualityForHeight(720, { frameRate: 15 }).maxBitrate).toBe(1500 * 1000);
    expect(screenShareQualityForHeight(720, { frameRate: 60 }).maxBitrate).toBe(6000 * 1000);
  });
});

describe('screenShareScaleDownBy', () => {
  test('downscales a taller source to the target', () => {
    // 4K display capped at 720p.
    expect(screenShareScaleDownBy({ sourceHeight: 2160, targetHeight: 720 })).toBeCloseTo(3.0, 9);
    // 1440p capped at 480p.
    expect(screenShareScaleDownBy({ sourceHeight: 1440, targetHeight: 480 })).toBeCloseTo(3.0, 9);
  });

  test('never scales up when the source already fits', () => {
    expect(screenShareScaleDownBy({ sourceHeight: 720, targetHeight: 1080 })).toBe(1.0);
    expect(screenShareScaleDownBy({ sourceHeight: 1080, targetHeight: 1080 })).toBe(1.0);
  });

  test('returns 1.0 for unknown/non-positive source heights', () => {
    expect(screenShareScaleDownBy({ sourceHeight: 0, targetHeight: 720 })).toBe(1.0);
    expect(screenShareScaleDownBy({ sourceHeight: -5, targetHeight: 720 })).toBe(1.0);
  });

  test('coerces an unsupported target to the default before scaling', () => {
    // target 999 -> 720; a 1080p source is capped at 720p.
    expect(screenShareScaleDownBy({ sourceHeight: 1080, targetHeight: 999 })).toBe(1.5);
  });
});

describe('screenShareSourceScaleForSample', () => {
  test('uses an RID/SSRC matched scale without guessing', () => {
    expect(
      screenShareSourceScaleForSample({ encodedHeight: 540, targetHeight: 1080, encodingScales: [1, 2], matchedScale: 2 }),
    ).toBe(2);
  });

  test('reconstructs a low simulcast sample when native stats omit ids', () => {
    expect(screenShareSourceScaleForSample({ encodedHeight: 540, targetHeight: 1080, encodingScales: [1, 2] })).toBe(2);
    expect(screenShareSourceScaleForSample({ encodedHeight: 1080, targetHeight: 720, encodingScales: [1, 2] })).toBe(1);
  });

  test('uses a known source to identify the closest active layer', () => {
    expect(
      screenShareSourceScaleForSample({
        encodedHeight: 240,
        targetHeight: 480,
        encodingScales: [2.25, 4.5],
        knownSourceHeight: 1080,
      }),
    ).toBe(4.5);
  });
});

describe('screenShareCappedOutputResolution', () => {
  test('keeps source aspect ratio while capping height', () => {
    expect(
      screenShareCappedOutputResolution({ sourceWidth: 3440, sourceHeight: 1440, targetHeight: 720 }).equals(
        new ScreenShareResolution(1720, 720),
      ),
    ).toBe(true);
  });

  test('does not upscale a smaller source', () => {
    expect(
      screenShareCappedOutputResolution({ sourceWidth: 960, sourceHeight: 540, targetHeight: 1080 }).equals(
        new ScreenShareResolution(960, 540),
      ),
    ).toBe(true);
  });

  test('uses the selected preset before source dimensions are known', () => {
    expect(
      screenShareCappedOutputResolution({ sourceWidth: null, sourceHeight: null, targetHeight: 720 }).equals(
        new ScreenShareResolution(1280, 720),
      ),
    ).toBe(true);
  });
});

describe('screenShareQualityCheckResult', () => {
  test('defers verification when parameters are ready but no frames flow', () => {
    expect(
      screenShareQualityCheckResult({
        parametersReady: true,
        hasOutboundFrames: false,
        resolutionVerified: false,
        frameRateCapVerified: true,
      }),
    ).toBe(ScreenShareQualityCheckResult.awaitingOutboundFrames);
  });

  test('does not hide a rejected parameter update behind missing stats', () => {
    expect(
      screenShareQualityCheckResult({
        parametersReady: false,
        hasOutboundFrames: false,
        resolutionVerified: false,
        frameRateCapVerified: true,
      }),
    ).toBe(ScreenShareQualityCheckResult.retry);
  });

  test('requires parameters, resolution and frame rate to match', () => {
    expect(
      screenShareQualityCheckResult({
        parametersReady: true,
        hasOutboundFrames: true,
        resolutionVerified: true,
        frameRateCapVerified: true,
      }),
    ).toBe(ScreenShareQualityCheckResult.verified);
    expect(
      screenShareQualityCheckResult({
        parametersReady: true,
        hasOutboundFrames: true,
        resolutionVerified: false,
        frameRateCapVerified: true,
      }),
    ).toBe(ScreenShareQualityCheckResult.retry);
  });
});

describe('screen-share picker labels', () => {
  test('formats supported resolutions and frame rates', () => {
    expect(screenShareResolutionLabel(480)).toBe('480p');
    expect(screenShareResolutionLabel(720)).toBe('720p');
    expect(screenShareResolutionLabel(1080)).toBe('1080p');
    expect(screenShareFrameRateLabel(15)).toBe('15 FPS');
    expect(screenShareFrameRateLabel(30)).toBe('30 FPS');
    expect(screenShareFrameRateLabel(60)).toBe('60 FPS');
  });

  test('coerces unsupported values to default labels', () => {
    expect(screenShareResolutionLabel(999)).toBe('720p');
    expect(screenShareFrameRateLabel(24)).toBe('30 FPS');
  });
});

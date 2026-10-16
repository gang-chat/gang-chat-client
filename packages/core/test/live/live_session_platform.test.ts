import { expect, test } from 'vitest';

import type { ScreenCaptureBridge, ScreenCaptureSource } from '../../src/platform/platform';
import { androidWebRtcInitializationOptions } from '../../src/live/android_audio_initialization';
import {
  LiveSession,
  cachedScreenSourceThumbnailForTest,
  resetScreenSourceThumbnailCacheForTest,
  supportsBrowserScreenShare,
  supportsDesktopScreenShare,
  supportsLocalScreenShare,
} from '../../src/live/live_session';
import { installFakePlatform } from '../support/fake_platform';

// Host-dependent parts of test/unit/live/live_session_test.dart and
// test/unit/live/web_screen_share_support_test.dart, driven through a fake
// platform bridge instead of debugDefaultTargetPlatformOverride.

class _FakeScreenCapture implements ScreenCaptureBridge {
  sources: ScreenCaptureSource[] = [];
  listCalls: Array<{ thumbnailWidth: number; thumbnailHeight: number }> = [];

  async listSources(options: { thumbnailWidth: number; thumbnailHeight: number }): Promise<ScreenCaptureSource[]> {
    this.listCalls.push(options);
    return this.sources.map((s) => ({ ...s }));
  }

  async selectSource(): Promise<void> {}

  async supportsSystemAudio(): Promise<boolean> {
    return true;
  }

  async permissionStatus(): Promise<'granted'> {
    return 'granted';
  }
}

test('desktop hosts share through the desktop source picker', () => {
  installFakePlatform({ os: 'windows' });
  expect(supportsDesktopScreenShare()).toBe(true);
  expect(supportsLocalScreenShare()).toBe(true);
  expect(supportsBrowserScreenShare()).toBe(false);
});

test('desktop browsers share through the browser picker', () => {
  installFakePlatform({ os: 'web' });
  expect(supportsBrowserScreenShare()).toBe(true);
  expect(supportsLocalScreenShare()).toBe(true);
  // The native desktop source picker is never used in a browser.
  expect(supportsDesktopScreenShare()).toBe(false);
});

test('Android captures natively', () => {
  installFakePlatform({ os: 'android' });
  expect(supportsDesktopScreenShare()).toBe(false);
  expect(supportsBrowserScreenShare()).toBe(false);
  expect(supportsLocalScreenShare()).toBe(true);
});

test('listScreenSources maps host sources, names and thumbnails for the picker', async () => {
  resetScreenSourceThumbnailCacheForTest();
  const capture = new _FakeScreenCapture();
  capture.sources = [
    { id: 'screen:0:0', name: '  ', isWindow: false, thumbnailDataUrl: 'data:image/png;base64,AAA' },
    { id: 'window:1:0', name: 'NVIDIA GeForce Overlay', isWindow: true, thumbnailDataUrl: null },
    { id: 'window:2:0', name: ' Code ', isWindow: true, thumbnailDataUrl: null },
  ];
  installFakePlatform({ os: 'macos', screenCapture: capture });

  const sources = await LiveSession.listScreenSources();

  expect(capture.listCalls).toEqual([{ thumbnailWidth: 640, thumbnailHeight: 360 }]);
  expect(sources.map((s) => [s.id, s.name, s.isWindow, s.thumbnailKey])).toEqual([
    ['screen:0:0', '屏幕', false, 'screen:screen:0:0'],
    ['window:2:0', 'Code', true, 'window:window:2:0'],
  ]);
  expect(sources[0]!.thumbnail).toBe('data:image/png;base64,AAA');
  expect(sources[1]!.thumbnail).toBeNull();
  expect(cachedScreenSourceThumbnailForTest('screen:screen:0:0')).toBe('data:image/png;base64,AAA');
});

test('refreshScreenSourceThumbnails re-emits thumbnails to open pickers', async () => {
  resetScreenSourceThumbnailCacheForTest();
  const capture = new _FakeScreenCapture();
  capture.sources = [{ id: 'window:2:0', name: 'Code', isWindow: true, thumbnailDataUrl: null }];
  installFakePlatform({ os: 'windows', screenCapture: capture });

  const [source] = await LiveSession.listScreenSources();
  const updates: string[] = [];
  const sub = source!.thumbnailUpdates!.listen((t) => updates.push(t));

  capture.sources = [{ id: 'window:2:0', name: 'Code', isWindow: true, thumbnailDataUrl: 'data:image/png;base64,BBB' }];
  await LiveSession.refreshScreenSourceThumbnails();

  expect(updates).toEqual(['data:image/png;base64,BBB']);
  // A reopened picker starts from the cached thumbnail.
  capture.sources = [{ id: 'window:2:0', name: 'Code', isWindow: true, thumbnailDataUrl: null }];
  const [reopened] = await LiveSession.listScreenSources();
  expect(reopened!.thumbnail).toBe('data:image/png;base64,BBB');
  sub.cancel();
});

test('hosts without screen capture list no sources', async () => {
  installFakePlatform({ os: 'linux', screenCapture: null });
  await expect(LiveSession.listScreenSources()).resolves.toEqual([]);
});

// Port of test/unit/live/android_audio_initialization_test.dart.
test('Android WebRTC initializes playback with media audio attributes', () => {
  expect(androidWebRtcInitializationOptions).toEqual({
    androidUseHardwareAudioProcessing: false,
    androidAudioConfiguration: {
      androidAudioAttributesUsageType: 'media',
      androidAudioAttributesContentType: 'music',
    },
  });
});

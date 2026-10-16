import { expect, test } from 'vitest';
import { isAndroidTabletLogicalSize } from '../../src/shell/android_form_factor';
import { AppOrientationController, DeviceOrientation } from '../../src/shell/app_orientation_controller';

function recordingController(size: { width: number; height: number }, fail = false) {
  const requests: DeviceOrientation[][] = [];
  let calls = 0;
  const controller = new AppOrientationController({
    platform: 'android',
    logicalViewSize: () => size,
    setPreferredOrientations: async (orientations) => {
      calls += 1;
      requests.push([...orientations]);
      if (fail && calls === 1) throw Object.assign(new Error('unsupported'), { code: 'unsupported' });
    },
  });
  return { controller, requests };
}

test('Android defaults to portrait-only', async () => {
  const { controller, requests } = recordingController({ width: 400, height: 800 });

  await controller.lockPortrait();

  expect(requests).toEqual([AppOrientationController.portraitOnly]);
});

test('Android full-screen media allows both landscape directions', async () => {
  const { controller, requests } = recordingController({ width: 400, height: 800 });

  await controller.allowFullScreenMediaLandscape();

  expect(requests).toEqual([
    [DeviceOrientation.portraitUp, DeviceOrientation.landscapeLeft, DeviceOrientation.landscapeRight],
  ]);
});

test('Android tablet keeps unrestricted default orientation', async () => {
  const { controller, requests } = recordingController({ width: 1280, height: 800 });

  await controller.restoreDefaultOrientation();
  await controller.allowFullScreenMediaLandscape();

  expect(controller.restoresPortraitByDefault).toBe(false);
  expect(requests).toEqual([AppOrientationController.unrestricted, AppOrientationController.unrestricted]);
});

test('Android tablet boundary uses the standard 600dp shortest side', () => {
  expect(isAndroidTabletLogicalSize({ width: 599, height: 1024 })).toBe(false);
  expect(isAndroidTabletLogicalSize({ width: 600, height: 960 })).toBe(true);
  expect(isAndroidTabletLogicalSize({ width: 1280, height: 800 })).toBe(true);
});

test('orientation requests never reach non-Android platforms', async () => {
  let calls = 0;
  const controller = new AppOrientationController({
    platform: 'windows',
    setPreferredOrientations: async () => {
      calls += 1;
    },
  });

  await controller.allowFullScreenMediaLandscape();
  await controller.lockPortrait();

  expect(calls).toBe(0);
});

test('portrait restoration still runs after an earlier request fails', async () => {
  const { controller, requests } = recordingController({ width: 400, height: 800 }, true);

  await expect(controller.allowFullScreenMediaLandscape()).rejects.toThrow('unsupported');
  await controller.lockPortrait();

  expect(requests.at(-1)).toEqual(AppOrientationController.portraitOnly);
});

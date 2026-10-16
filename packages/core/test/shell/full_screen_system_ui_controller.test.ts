import { expect, test } from 'vitest';
import {
  FullScreenSystemUiController,
  SystemUiMode,
  SystemUiOverlay,
} from '../../src/shell/full_screen_system_ui_controller';

test('Android system bars follow full-screen media controls', async () => {
  const requests: Array<{ mode: SystemUiMode; overlays: SystemUiOverlay[] | undefined }> = [];
  const controller = new FullScreenSystemUiController({
    platform: 'android',
    setEnabledSystemUIMode: async (mode, opts) => {
      requests.push({ mode, overlays: opts?.overlays });
    },
  });

  await controller.setControlsVisible(true);
  await controller.setControlsVisible(false);
  await controller.restore();

  expect(requests).toHaveLength(3);
  expect(requests[0]!.mode).toBe(SystemUiMode.manual);
  expect(requests[0]!.overlays).toEqual(Object.values(SystemUiOverlay));
  expect(requests[1]!.mode).toBe(SystemUiMode.immersiveSticky);
  expect(requests[1]!.overlays).toBeUndefined();
  expect(requests[2]!.mode).toBe(SystemUiMode.manual);
  expect(requests[2]!.overlays).toEqual(Object.values(SystemUiOverlay));
});

test('system UI requests never reach non-Android platforms', async () => {
  let calls = 0;
  const controller = new FullScreenSystemUiController({
    platform: 'windows',
    setEnabledSystemUIMode: async () => {
      calls += 1;
    },
  });

  await controller.setControlsVisible(false);
  await controller.restore();

  expect(calls).toBe(0);
});

test('restore still runs after an earlier platform failure', async () => {
  const modes: SystemUiMode[] = [];
  let calls = 0;
  const controller = new FullScreenSystemUiController({
    platform: 'android',
    setEnabledSystemUIMode: async (mode) => {
      calls += 1;
      modes.push(mode);
      if (calls === 1) throw Object.assign(new Error('unsupported'), { code: 'unsupported' });
    },
  });

  await expect(controller.setControlsVisible(false)).rejects.toThrow('unsupported');
  await controller.restore();

  expect(modes).toEqual([SystemUiMode.immersiveSticky, SystemUiMode.manual]);
});

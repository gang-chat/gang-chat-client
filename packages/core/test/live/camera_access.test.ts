import { expect, test } from 'vitest';

import { CameraAccessDeniedException, ensureCameraAccess } from '../../src/live/camera_access';
import { userFacingErrorMessage } from '../../src/app/error_display';
import { installFakePlatform } from '../support/fake_platform';

test('the camera starts when the OS grants access', async () => {
  const asked: string[] = [];
  installFakePlatform({
    os: 'macos',
    mediaAccess: {
      requestAccess: async (kind) => {
        asked.push(kind);
        return true;
      },
    },
  });

  await expect(ensureCameraAccess()).resolves.toBeUndefined();
  expect(asked).toEqual(['camera']);
});

test('a refused camera says how to turn it on', async () => {
  installFakePlatform({ os: 'macos', mediaAccess: { requestAccess: async () => false } });

  const error = await ensureCameraAccess().catch((e: unknown) => e);
  expect(error).toBeInstanceOf(CameraAccessDeniedException);
  expect(userFacingErrorMessage(error)).toBe('没有摄像头权限，请在系统设置的隐私设置里允许 Gang Chat 使用摄像头');
});

test('hosts without the bridge leave it to the browser', async () => {
  installFakePlatform({ os: 'web' });

  await expect(ensureCameraAccess()).resolves.toBeUndefined();
});

/**
 * OS camera permission, checked before the camera starts.
 *
 * PORT: no Dart counterpart. Electron on macOS hands out a live camera track
 * that never delivers a frame when Gang Chat has no camera access, so the
 * camera looked on while viewers saw nothing; the Flutter capture plugin asked
 * the OS itself.
 */
import { hasPlatform, platform } from '../platform/platform';

const cameraAccessDeniedMessage = '没有摄像头权限，请在系统设置的隐私设置里允许 Gang Chat 使用摄像头';

/** The OS has camera access switched off for Gang Chat. */
export class CameraAccessDeniedException extends Error {
  constructor() {
    super(cameraAccessDeniedMessage);
    this.name = 'CameraAccessDeniedException';
  }

  override toString(): string {
    return cameraAccessDeniedMessage;
  }
}

/**
 * Asks the OS for camera access (the macOS prompt on first use) and throws
 * [CameraAccessDeniedException] when it is off. Hosts without
 * `platform().mediaAccess`, such as browsers, ask on their own and pass
 * straight through.
 */
export async function ensureCameraAccess(): Promise<void> {
  const access = hasPlatform() ? platform().mediaAccess : null;
  if (access == null) return;
  if (!(await access.requestAccess('camera'))) throw new CameraAccessDeniedException();
}

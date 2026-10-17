import { hasPlatform, platform } from '../platform/platform';
import type { HostOs } from '../platform/platform';

/** Port of Flutter's `SystemUiMode`. */
export enum SystemUiMode {
  leanBack = 'leanBack',
  immersive = 'immersive',
  immersiveSticky = 'immersiveSticky',
  edgeToEdge = 'edgeToEdge',
  manual = 'manual',
}

/** Port of Flutter's `SystemUiOverlay`. */
export enum SystemUiOverlay {
  top = 'top',
  bottom = 'bottom',
}

export type SystemUiModeSetter = (mode: SystemUiMode, opts?: { overlays?: SystemUiOverlay[] }) => Promise<void>;

/**
 * Owns the Android system-UI boundary for full-screen media controls.
 *
 * Desktop platforms are deliberately ignored. On Android, the status and
 * navigation bars follow the same visibility state as Gang Chat's media
 * controls and are always restored when the full-screen scope is disposed.
 *
 * PORT: Android is the native client under android/, so the default setter
 * (Flutter's `SystemChrome.setEnabledSystemUIMode`) is a no-op.
 */
export class FullScreenSystemUiController {
  constructor(opts: { platform?: HostOs; setEnabledSystemUIMode?: SystemUiModeSetter } = {}) {
    this._platform = opts.platform ?? (hasPlatform() ? platform().os : 'web');
    this._setEnabledSystemUIMode = opts.setEnabledSystemUIMode ?? (async () => {});
  }

  private readonly _platform: HostOs;
  private readonly _setEnabledSystemUIMode: SystemUiModeSetter;
  private _pending: Promise<void> = Promise.resolve();

  setControlsVisible(visible: boolean): Promise<void> {
    if (this._platform !== 'android') return Promise.resolve();
    return this._enqueue(
      visible
        ? () => this._setEnabledSystemUIMode(SystemUiMode.manual, { overlays: Object.values(SystemUiOverlay) })
        : () => this._setEnabledSystemUIMode(SystemUiMode.immersiveSticky),
    );
  }

  restore(): Promise<void> {
    return this.setControlsVisible(true);
  }

  private _enqueue(operation: () => Promise<void>): Promise<void> {
    const next = this._pending.then(() => operation());
    // A failed platform call must not prevent a later touch or disposal from
    // restoring the system bars.
    this._pending = next.catch(() => {});
    return next;
  }
}

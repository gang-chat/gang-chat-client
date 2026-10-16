import { hasPlatform, platform } from '../platform/platform';
import type { HostOs } from '../platform/platform';
import { isAndroidTabletLogicalSize } from './android_form_factor';
import type { Size } from './desktop_window_controller';

/** Port of Flutter's `DeviceOrientation`. */
export enum DeviceOrientation {
  portraitUp = 'portraitUp',
  landscapeLeft = 'landscapeLeft',
  portraitDown = 'portraitDown',
  landscapeRight = 'landscapeRight',
}

export type PreferredOrientationsSetter = (orientations: readonly DeviceOrientation[]) => Promise<void>;
export type LogicalViewSizeReader = () => Size;

/**
 * Owns the platform boundary for Gang Chat's Android orientation policy.
 *
 * Other platforms are deliberately ignored. Android phones stay portrait-only
 * unless full-screen media is active, while Android tablets use the platform's
 * unrestricted orientation behavior.
 *
 * PORT: Android stays on Flutter, so no host wires `SystemChrome
 * .setPreferredOrientations`: the default setter does nothing and the default
 * view size is zero. A React Native Android app injects both (e.g.
 * expo-screen-orientation + `Dimensions`). The policy itself is unchanged.
 */
export class AppOrientationController {
  constructor(
    opts: {
      platform?: HostOs;
      setPreferredOrientations?: PreferredOrientationsSetter;
      logicalViewSize?: LogicalViewSizeReader;
    } = {},
  ) {
    this._platform = opts.platform ?? (hasPlatform() ? platform().os : 'web');
    this._setPreferredOrientations = opts.setPreferredOrientations ?? (async () => {});
    this._logicalViewSize = opts.logicalViewSize ?? (() => ({ width: 0, height: 0 }));
  }

  static readonly portraitOnly: readonly DeviceOrientation[] = [DeviceOrientation.portraitUp];

  static readonly fullScreenMedia: readonly DeviceOrientation[] = [
    DeviceOrientation.portraitUp,
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ];
  static readonly unrestricted: readonly DeviceOrientation[] = [];

  private readonly _platform: HostOs;
  private readonly _setPreferredOrientations: PreferredOrientationsSetter;
  private readonly _logicalViewSize: LogicalViewSizeReader;
  private _pending: Promise<void> = Promise.resolve();

  get restoresPortraitByDefault(): boolean {
    return this._platform === 'android' && !isAndroidTabletLogicalSize(this._logicalViewSize());
  }

  lockPortrait(): Promise<void> {
    return this._enqueue(AppOrientationController.portraitOnly);
  }

  restoreDefaultOrientation(): Promise<void> {
    return this._enqueue(
      this.restoresPortraitByDefault ? AppOrientationController.portraitOnly : AppOrientationController.unrestricted,
    );
  }

  allowFullScreenMediaLandscape(): Promise<void> {
    return this._enqueue(
      this.restoresPortraitByDefault ? AppOrientationController.fullScreenMedia : AppOrientationController.unrestricted,
    );
  }

  private _enqueue(orientations: readonly DeviceOrientation[]): Promise<void> {
    if (this._platform !== 'android') return Promise.resolve();
    const operation = this._pending.then(() => this._setPreferredOrientations(orientations));
    // Keep later restoration requests running even if a platform call fails.
    this._pending = operation.catch(() => {});
    return operation;
  }
}

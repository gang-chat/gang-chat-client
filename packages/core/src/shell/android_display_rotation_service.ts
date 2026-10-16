export type AndroidDisplayRotationReader = () => Promise<number>;

/**
 * Reads Android's current display rotation as quarter turns from its natural
 * orientation. Callers are responsible for restricting use to Android.
 *
 * PORT: Android stays on Flutter; the `gang_chat/display_orientation`
 * channel has no TypeScript host, so [currentQuarterTurns] always reports the
 * natural orientation unless a [reader] is injected.
 */
export class AndroidDisplayRotationService {
  constructor(opts: { reader?: AndroidDisplayRotationReader } = {}) {
    this._reader = opts.reader ?? null;
  }

  private readonly _reader: AndroidDisplayRotationReader | null;

  async currentQuarterTurns(): Promise<number> {
    const value = this._reader == null ? null : await this._reader();
    return normalizeDisplayRotationQuarterTurns(value);
  }
}

export function normalizeDisplayRotationQuarterTurns(value: number | null | undefined): number {
  switch (value) {
    case 1:
      return 1;
    case 2:
      return 2;
    case 3:
      return 3;
    default:
      return 0;
  }
}

/**
 * Counter-rotation that keeps a frozen portrait frame fixed while Android's
 * display surface rotates underneath the image-preview route.
 */
export function counterDisplayRotationQuarterTurns(displayQuarterTurns: number): number {
  const normalized = normalizeDisplayRotationQuarterTurns(displayQuarterTurns);
  return (4 - normalized) % 4;
}

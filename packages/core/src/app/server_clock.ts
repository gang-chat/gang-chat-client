import { _tryParseDateTime } from './settings_about';

export { gangServerTimeHeader } from '../protocol/server_time_header';

export type ServerClockListener = () => void;

/** Monotonic milliseconds, the stand-in for Dart's `Stopwatch`. */
function _monotonicMilliseconds(): number {
  return typeof performance !== 'undefined' && typeof performance.now === 'function' ? performance.now() : Date.now();
}

export class ServerClock {
  /**
   * [monotonicNow] returns elapsed milliseconds on a monotonic clock (the
   * Dart version took a `Duration`).
   */
  constructor(opts: { localNow?: () => Date; monotonicNow?: () => number } = {}) {
    this._localNow = opts.localNow ?? (() => new Date());
    this._monotonicNow = opts.monotonicNow ?? null;
  }

  private readonly _localNow: () => Date;
  private readonly _monotonicNow: (() => number) | null;
  private readonly _stopwatchStart = _monotonicMilliseconds();
  private _stopwatchStoppedAt: number | null = null;
  private readonly _listeners = new Set<ServerClockListener>();
  private _serverBaseTime: Date | null = null;
  private _monotonicBaseTime: number | null = null;
  private _requestRoundTrip: number | null = null;

  now(): Date {
    const serverBaseTime = this._serverBaseTime;
    const monotonicBaseTime = this._monotonicBaseTime;
    if (serverBaseTime == null || monotonicBaseTime == null) {
      return this._localNow();
    }
    return new Date(serverBaseTime.getTime() + (this._elapsed() - monotonicBaseTime));
  }

  /** Latest request round-trip in milliseconds. */
  get requestRoundTrip(): number | null {
    return this._requestRoundTrip;
  }

  updateFromHeader(value: string | null): boolean {
    const serverTime = parseServerTimeHeader(value);
    if (serverTime == null) return false;
    this._serverBaseTime = serverTime;
    this._monotonicBaseTime = this._elapsed();
    this._notifyListeners();
    return true;
  }

  /** [value] is in milliseconds. */
  updateRequestRoundTrip(value: number): boolean {
    if (value < 0) return false;
    this._requestRoundTrip = value;
    this._notifyListeners();
    return true;
  }

  private _notifyListeners(): void {
    for (const listener of [...this._listeners]) {
      listener();
    }
  }

  addListener(listener: ServerClockListener): void {
    this._listeners.add(listener);
  }

  removeListener(listener: ServerClockListener): void {
    this._listeners.delete(listener);
  }

  dispose(): void {
    this._listeners.clear();
    this._stopwatchStoppedAt ??= _monotonicMilliseconds();
  }

  private _elapsed(): number {
    if (this._monotonicNow != null) return this._monotonicNow();
    return (this._stopwatchStoppedAt ?? _monotonicMilliseconds()) - this._stopwatchStart;
  }
}

export function parseServerTimeHeader(value: string | null | undefined): Date | null {
  const normalized = value?.trim();
  if (normalized == null || normalized.length === 0) return null;
  return _tryParseDateTime(normalized);
}

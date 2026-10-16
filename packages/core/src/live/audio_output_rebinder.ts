/**
 * Port of `lib/src/live/audio_output_rebinder.dart`.
 */
import { Timer, unawaited, type Milliseconds } from '../lib/async';
import type { EventStream, StreamSubscription } from '../lib/event_stream';

/**
 * Recovers audio output after the OS swaps the default output endpoint
 * underneath a live room.
 *
 * The prime case is a Bluetooth headset flipping between its A2DP (stereo
 * "music" profile, output only) and HFP (mono "call" profile, with mic). When
 * the mic opens, macOS forces the headset into HFP; when it closes, the
 * headset flips back to A2DP. Each flip tears down the old output endpoint
 * and builds a new one; playback that keeps rendering into the dead endpoint
 * degrades until it is rebound to the live endpoint.
 *
 * The OS posts a device-change event on each flip. On that signal this
 * rebinds the output device (re-selecting forces playout to rebind against
 * the current endpoint, even when the device id is unchanged) and re-applies
 * track volumes. Everything is best-effort: a failed rebind leaves the
 * existing routing untouched rather than throwing into the caller.
 */
export class AudioOutputRebinder {
  constructor(opts: {
    deviceChanges: EventStream<void>;
    currentOutputDeviceId: () => Promise<string | null>;
    selectOutput: (deviceId: string) => Promise<void>;
    onRebound: () => Promise<void>;
    debounce?: Milliseconds;
  }) {
    this._deviceChanges = opts.deviceChanges;
    this._currentOutputDeviceId = opts.currentOutputDeviceId;
    this._selectOutput = opts.selectOutput;
    this._onRebound = opts.onRebound;
    this._debounce = opts.debounce ?? 300;
  }

  private readonly _deviceChanges: EventStream<void>;
  private readonly _currentOutputDeviceId: () => Promise<string | null>;
  private readonly _selectOutput: (deviceId: string) => Promise<void>;
  private readonly _onRebound: () => Promise<void>;
  private readonly _debounce: Milliseconds;

  private _subscription: StreamSubscription | null = null;
  private _debounceTimer: Timer | null = null;
  private _rebinding = false;
  private _stopped = false;

  /**
   * Begins observing device changes. Safe to call once; a second call is a
   * no-op while already started.
   */
  start(): void {
    if (this._subscription != null || this._stopped) return;
    this._subscription = this._deviceChanges.listen(() => this._scheduleRebind(), { onError: () => {} });
  }

  /**
   * Stops observing and cancels any pending rebind. After this the rebinder is
   * inert and cannot be restarted.
   */
  async stop(): Promise<void> {
    this._stopped = true;
    this._debounceTimer?.cancel();
    this._debounceTimer = null;
    const sub = this._subscription;
    this._subscription = null;
    sub?.cancel();
  }

  private _scheduleRebind(): void {
    if (this._stopped) return;
    // Coalesce the burst of events a single profile flip emits, and give the
    // OS a beat to finish building the new endpoint before we bind to it.
    this._debounceTimer?.cancel();
    this._debounceTimer = new Timer(this._debounce, () => {
      unawaited(this._rebind());
    });
  }

  private async _rebind(): Promise<void> {
    if (this._stopped || this._rebinding) return;
    this._rebinding = true;
    try {
      const deviceId = await this._currentOutputDeviceId();
      if (this._stopped) return;
      if (deviceId != null && deviceId.length > 0) {
        await this._selectOutput(deviceId);
      }
      if (this._stopped) return;
      await this._onRebound();
    } catch {
      // Best-effort recovery: keep the existing routing on any failure.
    } finally {
      this._rebinding = false;
    }
  }
}

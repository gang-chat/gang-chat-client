/**
 * Port of `lib/src/live/camera_device_reconciler.dart`.
 */
import { Completer, Timer, unawaited, withTimeout, type Milliseconds } from '../lib/async';
import type { EventStream, StreamSubscription } from '../lib/event_stream';

/**
 * Debounces and serializes camera-topology refreshes.
 *
 * Windows reports camera arrival/removal separately from audio endpoint
 * changes (see `SystemAudioDevices.videoChanges`), so camera hotplug never
 * restarts microphone capture or playout routing.
 */
export class CameraDeviceReconciler {
  constructor(opts: {
    deviceChanges: EventStream<void>;
    refreshDevices: () => Promise<void>;
    debounce?: Milliseconds;
    operationTimeout?: Milliseconds;
  }) {
    this._deviceChanges = opts.deviceChanges;
    this._refreshDevices = opts.refreshDevices;
    this._debounce = opts.debounce ?? 300;
    this._operationTimeout = opts.operationTimeout ?? 7_000;
  }

  private readonly _deviceChanges: EventStream<void>;
  private readonly _refreshDevices: () => Promise<void>;
  private readonly _debounce: Milliseconds;
  private readonly _operationTimeout: Milliseconds;

  private _subscription: StreamSubscription | null = null;
  private _debounceTimer: Timer | null = null;
  private readonly _stopSignal = new Completer<void>();
  private _activeRefresh: Promise<void> | null = null;
  private _refreshing = false;
  private _refreshQueued = false;
  private _stopped = false;

  start(): void {
    if (this._subscription != null || this._stopped) return;
    this._subscription = this._deviceChanges.listen(() => this._scheduleRefresh(), { onError: () => {} });
  }

  async stop(): Promise<void> {
    if (this._stopped) {
      await this._activeRefresh;
      return;
    }
    this._stopped = true;
    if (!this._stopSignal.isCompleted) this._stopSignal.complete();
    this._debounceTimer?.cancel();
    this._debounceTimer = null;
    const subscription = this._subscription;
    this._subscription = null;
    subscription?.cancel();
    try {
      await this._activeRefresh;
    } catch {
      // Stopping deliberately invalidates the in-flight refresh.
    }
  }

  private _scheduleRefresh(): void {
    if (this._stopped) return;
    this._debounceTimer?.cancel();
    this._debounceTimer = new Timer(this._debounce, () => {
      const refresh = this._refresh();
      this._activeRefresh = refresh;
      unawaited(
        refresh.finally(() => {
          if (this._activeRefresh === refresh) this._activeRefresh = null;
        }),
      );
    });
  }

  private async _refresh(): Promise<void> {
    if (this._stopped) return;
    if (this._refreshing) {
      this._refreshQueued = true;
      return;
    }
    this._refreshing = true;
    try {
      await Promise.race([
        withTimeout(Promise.resolve().then(this._refreshDevices), this._operationTimeout),
        this._stopSignal.promise,
      ]);
    } catch {
      // A later topology event receives a fresh bounded attempt.
    } finally {
      this._refreshing = false;
      if (this._refreshQueued && !this._stopped) {
        this._refreshQueued = false;
        this._scheduleRefresh();
      }
    }
  }
}

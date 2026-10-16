/**
 * Port of `lib/src/live/audio_device_rebinder.dart`.
 */
import { Completer, Timer, unawaited, withTimeout, type Milliseconds } from '../lib/async';
import type { EventStream, StreamSubscription } from '../lib/event_stream';
import {
  AudioDeviceInfo,
  audioDeviceInfoGroupId,
  audioDeviceInfoId,
  audioDeviceInfoKind,
  audioDeviceInfoLabel,
} from '../app/audio_device_info';
import { StoredAudioDevices, preferredStoredAudioDeviceFrom, type AudioDeviceStore } from '../app/audio_device_store';
import type { LiveAudioDeviceService } from './audio_device_service';
import type { SystemAudioDevices } from './system_audio_devices';

/** The input/output choice resolved from one device-list snapshot. */
export class PreferredLiveAudioDevices {
  constructor(opts: { inputDeviceId?: string | null; outputDeviceId?: string | null } = {}) {
    this.inputDeviceId = opts.inputDeviceId ?? null;
    this.outputDeviceId = opts.outputDeviceId ?? null;
  }

  readonly inputDeviceId: string | null;
  readonly outputDeviceId: string | null;
}

/**
 * Resolves both preferred endpoints together.
 *
 * In particular, [audioDevices.enumerateDevices] is invoked exactly once.
 * This prevents an input and output change from racing two device
 * enumerations against each other.
 */
export async function preferredLiveAudioDevices(opts: {
  audioDeviceStore: AudioDeviceStore | null;
  audioDevices: LiveAudioDeviceService;
  systemAudio: SystemAudioDevices;
}): Promise<PreferredLiveAudioDevices> {
  const { audioDeviceStore, audioDevices, systemAudio } = opts;
  const defaultsFuture = Promise.all([systemAudio.currentInputDeviceId(), systemAudio.currentOutputDeviceId()]);
  const storedFuture = _readStoredAudioDevices(audioDeviceStore);

  // Do not turn an enumeration failure into an empty topology. Keeping the
  // existing endpoints is safer than accidentally rebinding both directions
  // to a default while the OS is still recovering.
  const devices = await audioDevices.enumerateDevices();

  const defaults = await defaultsFuture;
  const stored = await storedFuture;

  const input = preferredStoredAudioDeviceFrom<AudioDeviceInfo>(devices, {
    kind: 'audioinput',
    storedDeviceId: stored.inputDeviceId,
    storedDeviceLabel: stored.inputDeviceLabel,
    storedDeviceGroupId: stored.inputDeviceGroupId,
    kindOf: audioDeviceInfoKind,
    deviceIdOf: audioDeviceInfoId,
    labelOf: audioDeviceInfoLabel,
    groupIdOf: audioDeviceInfoGroupId,
    systemDefaultDeviceId: defaults[0],
  });
  const output = preferredStoredAudioDeviceFrom<AudioDeviceInfo>(devices, {
    kind: 'audiooutput',
    storedDeviceId: stored.outputDeviceId,
    storedDeviceLabel: stored.outputDeviceLabel,
    storedDeviceGroupId: stored.outputDeviceGroupId,
    kindOf: audioDeviceInfoKind,
    deviceIdOf: audioDeviceInfoId,
    labelOf: audioDeviceInfoLabel,
    groupIdOf: audioDeviceInfoGroupId,
    systemDefaultDeviceId: defaults[1],
  });

  return new PreferredLiveAudioDevices({
    inputDeviceId: input?.deviceId ?? defaults[0],
    outputDeviceId: output?.deviceId ?? defaults[1],
  });
}

async function _readStoredAudioDevices(store: AudioDeviceStore | null): Promise<StoredAudioDevices> {
  if (store == null) return new StoredAudioDevices();
  try {
    return await store.read();
  } catch {
    return new StoredAudioDevices();
  }
}

/**
 * Coalesces desktop input/output changes into one serialized refresh.
 *
 * A device transition can emit several notifications and can emit another
 * notification while a rebind is still running. At most one refresh runs at
 * once; one trailing refresh is retained so the final settled topology is
 * always applied.
 */
export class AudioDeviceRebinder {
  constructor(opts: {
    deviceChanges: EventStream<void>;
    resolvePreferredDevices: () => Promise<PreferredLiveAudioDevices>;
    rebindInput: (deviceId: string | null) => Promise<void>;
    selectOutput: (deviceId: string) => Promise<void>;
    onOutputRebound: () => Promise<void>;
    debounce?: Milliseconds;
    operationTimeout?: Milliseconds;
  }) {
    this._deviceChanges = opts.deviceChanges;
    this._resolvePreferredDevices = opts.resolvePreferredDevices;
    this._rebindInput = opts.rebindInput;
    this._selectOutput = opts.selectOutput;
    this._onOutputRebound = opts.onOutputRebound;
    this._debounce = opts.debounce ?? 300;
    this._operationTimeout = opts.operationTimeout ?? 7_000;
  }

  private readonly _deviceChanges: EventStream<void>;
  private readonly _resolvePreferredDevices: () => Promise<PreferredLiveAudioDevices>;
  private readonly _rebindInput: (deviceId: string | null) => Promise<void>;
  private readonly _selectOutput: (deviceId: string) => Promise<void>;
  private readonly _onOutputRebound: () => Promise<void>;
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
      // Stopping deliberately cancels/invalidates the in-flight refresh.
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

  private _runStage<T>(operation: () => Promise<T>): Promise<T> {
    const stage = withTimeout(_syncFuture(operation), this._operationTimeout);
    const cancelled = this._stopSignal.promise.then<T>(() => {
      throw new _RebindCancelled();
    });
    // The losing branch must not surface as an unhandled rejection.
    stage.catch(() => {});
    cancelled.catch(() => {});
    return Promise.race([stage, cancelled]);
  }

  private async _refresh(): Promise<void> {
    if (this._stopped) return;
    if (this._refreshing) {
      this._refreshQueued = true;
      return;
    }

    this._refreshing = true;
    try {
      const preferred = await this._runStage(this._resolvePreferredDevices);
      if (this._stopped) return;

      // Keep input and output operations serialized too.
      try {
        await this._runStage(() => this._rebindInput(preferred.inputDeviceId));
      } catch {
        // Input recovery is best-effort; still give output a chance to recover.
      }
      if (this._stopped) return;

      const outputDeviceId = preferred.outputDeviceId;
      if (outputDeviceId != null && outputDeviceId.length > 0) {
        try {
          await this._runStage(() => this._selectOutput(outputDeviceId));
        } catch {
          // The OS may already have moved playback to its new default. Always
          // reapply routing/volume even when explicit selection is unavailable.
        }
      }
      if (this._stopped) return;
      await this._runStage(this._onOutputRebound);
    } catch {
      // The next device event gets another attempt without disconnecting room.
    } finally {
      this._refreshing = false;
      if (this._refreshQueued && !this._stopped) {
        this._refreshQueued = false;
        this._scheduleRefresh();
      }
    }
  }
}

/** `Future.sync(operation)`: a synchronous throw becomes a rejected promise. */
function _syncFuture<T>(operation: () => Promise<T>): Promise<T> {
  try {
    return Promise.resolve(operation());
  } catch (error) {
    return Promise.reject(error);
  }
}

class _RebindCancelled extends Error {
  constructor() {
    super('audio device rebind cancelled');
    this.name = '_RebindCancelled';
  }
}

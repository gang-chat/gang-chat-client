/**
 * Port of `lib/src/live/audio_input_rebinder.dart`.
 */
import { Timer, unawaited, type Milliseconds } from '../lib/async';
import type { EventStream, StreamSubscription } from '../lib/event_stream';
import {
  AudioDeviceInfo,
  audioDeviceInfoGroupId,
  audioDeviceInfoId,
  audioDeviceInfoKind,
  audioDeviceInfoLabel,
} from '../app/audio_device_info';
import { preferredStoredAudioDeviceFrom, type AudioDeviceStore } from '../app/audio_device_store';
import type { LiveAudioDeviceService } from './audio_device_service';
import type { SystemAudioDevices } from './system_audio_devices';

/**
 * Recovers the local microphone publisher after desktop audio input hotplug.
 *
 * Bluetooth headsets can remove and recreate their input endpoint when the
 * battery dies, reconnects, or flips between output-only and call profiles.
 * The published mic track may keep the old capture endpoint attached, which
 * then surfaces as publisher reconnect failures. This helper is deliberately
 * best-effort: it coalesces the burst of device-change events, resolves the
 * preferred live input device, and asks the session to restart the local mic
 * capture without reconnecting to the room.
 */
export class AudioInputRebinder {
  constructor(opts: {
    deviceChanges: EventStream<void>;
    currentInputDeviceId: () => Promise<string | null>;
    rebindInput: (deviceId: string | null) => Promise<void>;
    debounce?: Milliseconds;
  }) {
    this._deviceChanges = opts.deviceChanges;
    this._currentInputDeviceId = opts.currentInputDeviceId;
    this._rebindInput = opts.rebindInput;
    this._debounce = opts.debounce ?? 300;
  }

  private readonly _deviceChanges: EventStream<void>;
  private readonly _currentInputDeviceId: () => Promise<string | null>;
  private readonly _rebindInput: (deviceId: string | null) => Promise<void>;
  private readonly _debounce: Milliseconds;

  private _subscription: StreamSubscription | null = null;
  private _debounceTimer: Timer | null = null;
  private _rebinding = false;
  private _rebindQueued = false;
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
    // A single Bluetooth reconnect can emit several add/remove/default-change
    // events. Wait for the device list to settle before restarting capture.
    this._debounceTimer?.cancel();
    this._debounceTimer = new Timer(this._debounce, () => {
      unawaited(this._rebind());
    });
  }

  private async _rebind(): Promise<void> {
    if (this._stopped) return;
    if (this._rebinding) {
      this._rebindQueued = true;
      return;
    }
    this._rebinding = true;
    try {
      const deviceId = await this._currentInputDeviceId();
      if (this._stopped) return;
      await this._rebindInput(deviceId);
    } catch {
      // Best-effort recovery: the next hardware event gets another attempt.
    } finally {
      this._rebinding = false;
      if (this._rebindQueued && !this._stopped) {
        this._rebindQueued = false;
        this._scheduleRebind();
      }
    }
  }
}

export async function preferredLiveInputDeviceId(opts: {
  audioDeviceStore: AudioDeviceStore | null;
  audioDevices: LiveAudioDeviceService;
  systemAudio: SystemAudioDevices;
}): Promise<string | null> {
  const { audioDeviceStore, audioDevices, systemAudio } = opts;
  const systemDefaultInputId = await systemAudio.currentInputDeviceId();
  if (audioDeviceStore == null) return systemDefaultInputId;
  try {
    const stored = await audioDeviceStore.read();
    const devices = await audioDevices.enumerateDevices();
    const storedInput = preferredStoredAudioDeviceFrom<AudioDeviceInfo>(devices, {
      kind: 'audioinput',
      storedDeviceId: stored.inputDeviceId,
      storedDeviceLabel: stored.inputDeviceLabel,
      storedDeviceGroupId: stored.inputDeviceGroupId,
      kindOf: audioDeviceInfoKind,
      deviceIdOf: audioDeviceInfoId,
      labelOf: audioDeviceInfoLabel,
      groupIdOf: audioDeviceInfoGroupId,
      systemDefaultDeviceId: systemDefaultInputId,
    });
    return storedInput?.deviceId ?? systemDefaultInputId;
  } catch {
    return systemDefaultInputId;
  }
}

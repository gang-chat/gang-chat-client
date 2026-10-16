/**
 * Port of `lib/src/live/system_audio_devices.dart`.
 *
 * PORT: the Flutter client read CoreAudio (macOS) / WASAPI (Windows) through
 * the native `gang_chat/audio_devices` method channel, because flutter_webrtc
 * listed no audio devices outside a room (macOS) or could block inside the
 * WebRTC ADM while an endpoint was rebuilt (Windows). Chromium's
 * `navigator.mediaDevices` has neither problem, so this class is rebuilt on
 * `enumerateDevices()` + the `devicechange` event:
 *  - the synthetic `default` / `communications` entries are folded away, the
 *    way the native lists only reported real endpoints, and the "current
 *    default" is the real endpoint the `default` entry points at;
 *  - the native channel's typed notifications (`defaultInputDeviceChanged`,
 *    `defaultOutputDeviceChanged`, `audioDevicesChanged`,
 *    `videoDevicesChanged`) are reconstructed by diffing consecutive device
 *    snapshots on each `devicechange`;
 *  - `startListening` cannot fail, so the native retry/backoff is gone.
 */
import { HostPlatform } from '../config/host_platform';
import { StreamController, type EventStream } from '../lib/event_stream';
import { AudioDeviceInfo } from '../app/audio_device_info';
import { defaultMediaDevices, type MediaDevicesLike } from './live_hardware';

type _RawDevice = Pick<MediaDeviceInfo, 'deviceId' | 'label' | 'kind' | 'groupId'>;

const _pseudoDeviceIds = new Set(['default', 'communications']);

/**
 * Desktop system audio-device access.
 *
 * Windows treats this list as the authoritative audio-device source (see
 * `LiveAudioDeviceService`); macOS merges it into the WebRTC list.
 */
export class SystemAudioDevices {
  constructor(opts: { mediaDevices?: MediaDevicesLike | null; supported?: boolean; sharedEvents?: boolean } = {}) {
    this._mediaDevicesOverride = opts.mediaDevices;
    this._supportedOverride = opts.supported;
    this._usesSharedEvents = opts.mediaDevices === undefined && (opts.sharedEvents ?? true);
  }

  private readonly _mediaDevicesOverride: MediaDevicesLike | null | undefined;
  private readonly _supportedOverride: boolean | undefined;
  private readonly _usesSharedEvents: boolean;
  private readonly _inputChanges = new StreamController<string | null>();
  private readonly _outputChanges = new StreamController<string | null>();
  private readonly _videoChanges = new StreamController<void>();
  private readonly _changes = new StreamController<string | null>();
  private _watcher: _DeviceChangeWatcher | null = null;
  private _disposed = false;

  private get _mediaDevices(): MediaDevicesLike | null {
    return this._mediaDevicesOverride !== undefined ? this._mediaDevicesOverride : defaultMediaDevices();
  }

  private get _supported(): boolean {
    return this._supportedOverride ?? (!HostPlatform.isWeb && (HostPlatform.isMacOS || HostPlatform.isWindows));
  }

  /** Native input devices, or an empty list when unsupported/unavailable. */
  enumerateInputs(): Promise<AudioDeviceInfo[]> {
    return this._enumerate(['audioinput']);
  }

  /** Native output devices, or an empty list when unsupported/unavailable. */
  enumerateOutputs(): Promise<AudioDeviceInfo[]> {
    return this._enumerate(['audiooutput']);
  }

  /**
   * Captures one logical input/output snapshot.
   *
   * Both directions are read from a single enumeration so callers never
   * perform two independent full refreshes for the same device-change burst.
   */
  enumerateDevices(): Promise<AudioDeviceInfo[]> {
    return this._enumerate(['audioinput', 'audiooutput']);
  }

  /**
   * Captures a snapshot without converting a platform failure into an empty
   * topology.
   *
   * Device pickers intentionally use [enumerateDevices] so they can remain
   * usable when a platform integration is unavailable. Automatic rebinding
   * must use this strict variant: treating a failure as "no devices" would
   * incorrectly switch both endpoints to their defaults.
   */
  async enumerateDevicesOrThrow(): Promise<AudioDeviceInfo[]> {
    if (!this._supported) return [];
    return this._enumerate(['audioinput', 'audiooutput'], { rethrowError: true });
  }

  private async _enumerate(kinds: readonly string[], opts: { rethrowError?: boolean } = {}): Promise<AudioDeviceInfo[]> {
    if (!this._supported) return [];
    try {
      const raw = await this._rawDevices();
      const result: AudioDeviceInfo[] = [];
      for (const kind of kinds) {
        const fallbackLabel = kind === 'audioinput' ? '麦克风' : '扬声器';
        for (const entry of raw) {
          if (entry.kind !== kind || entry.deviceId.length === 0 || _pseudoDeviceIds.has(entry.deviceId)) continue;
          result.push(
            new AudioDeviceInfo({
              deviceId: entry.deviceId,
              label: entry.label || fallbackLabel,
              kind,
              groupId: entry.groupId ?? '',
            }),
          );
        }
      }
      return result;
    } catch (error) {
      if (opts.rethrowError) throw error;
      return [];
    }
  }

  private async _rawDevices(): Promise<readonly _RawDevice[]> {
    const mediaDevices = this._mediaDevices;
    if (mediaDevices == null) throw new Error('navigator.mediaDevices is unavailable');
    return mediaDevices.enumerateDevices();
  }

  currentInputDeviceId(): Promise<string | null> {
    return this._currentDeviceId('audioinput');
  }

  currentOutputDeviceId(): Promise<string | null> {
    return this._currentDeviceId('audiooutput');
  }

  /** Backwards-compatible alias for the default input device id. */
  currentDeviceId(): Promise<string | null> {
    return this.currentInputDeviceId();
  }

  private async _currentDeviceId(kind: string): Promise<string | null> {
    if (!this._supported) return null;
    try {
      return _defaultEndpointId(await this._rawDevices(), kind);
    } catch {
      // The picker degrades to the enumerated/WebRTC list when the default is
      // unavailable; surfacing the failure would not help the user.
      return null;
    }
  }

  get inputChanges(): EventStream<string | null> {
    return this._ensureListening()?.inputChanges ?? this._inputChanges.stream;
  }

  get outputChanges(): EventStream<string | null> {
    return this._ensureListening()?.outputChanges ?? this._outputChanges.stream;
  }

  /**
   * Device-tree notifications used to reconcile camera selection.
   *
   * This is separate from [changes] so camera-only hotplug never restarts
   * microphone capture or playout routing.
   */
  get videoChanges(): EventStream<void> {
    return this._ensureListening()?.videoChanges ?? this._videoChanges.stream;
  }

  /**
   * Any input/output default or endpoint-topology change.
   *
   * The value is the new default endpoint when one is known, otherwise null
   * for add/remove notifications.
   */
  get changes(): EventStream<string | null> {
    return this._ensureListening()?.changes ?? this._changes.stream;
  }

  /** Returns the shared bus when used, otherwise null after starting this instance's own watcher. */
  private _ensureListening(): _DeviceChangeWatcher | null {
    if (!this._supported || this._disposed) return null;
    const mediaDevices = this._mediaDevices;
    if (mediaDevices == null) return null;
    if (this._usesSharedEvents) {
      const bus = _sharedWatcherFor(mediaDevices);
      bus.ensureListening();
      return bus;
    }
    if (this._watcher == null) {
      this._watcher = new _DeviceChangeWatcher(mediaDevices, {
        inputChanges: this._inputChanges,
        outputChanges: this._outputChanges,
        videoChanges: this._videoChanges,
        changes: this._changes,
      });
    }
    this._watcher.ensureListening();
    return null;
  }

  async dispose(): Promise<void> {
    this._disposed = true;
    this._watcher?.stopListening();
    this._watcher = null;
    this._inputChanges.close();
    this._outputChanges.close();
    this._videoChanges.close();
    this._changes.close();
  }
}

/**
 * Resolves the real endpoint behind Chromium's synthetic `default` entry
 * (labelled `Default - <device label>` and sharing the device's group id).
 * Falls back to `default` itself, and to null when no default exists.
 */
export function _defaultEndpointId(devices: readonly _RawDevice[], kind: string): string | null {
  const ofKind = devices.filter((device) => device.kind === kind && device.deviceId.length > 0);
  const pseudo = ofKind.find((device) => device.deviceId === 'default');
  if (pseudo == null) return null;
  const real = ofKind.filter((device) => !_pseudoDeviceIds.has(device.deviceId));
  const separator = pseudo.label.indexOf(' - ');
  const targetLabel = separator >= 0 ? pseudo.label.substring(separator + 3) : null;
  if (targetLabel != null && targetLabel.length > 0) {
    const byLabel = real.filter(
      (device) => device.label === targetLabel && (pseudo.groupId == null || pseudo.groupId === '' || device.groupId === pseudo.groupId),
    );
    if (byLabel.length >= 1) return byLabel[0]!.deviceId;
  }
  if (pseudo.groupId) {
    const byGroup = real.filter((device) => device.groupId === pseudo.groupId);
    if (byGroup.length === 1) return byGroup[0]!.deviceId;
  }
  return 'default';
}

interface _DeviceSnapshot {
  defaultInput: string | null;
  defaultOutput: string | null;
  audioTopology: string;
  videoTopology: string;
}

function _snapshotOf(devices: readonly _RawDevice[]): _DeviceSnapshot {
  const topology = (kinds: readonly string[]): string =>
    devices
      .filter((device) => kinds.includes(device.kind) && device.deviceId.length > 0)
      .map((device) => `${device.kind}:${device.deviceId}:${device.label}`)
      .sort()
      .join('\n');
  return {
    defaultInput: _defaultEndpointId(devices, 'audioinput'),
    defaultOutput: _defaultEndpointId(devices, 'audiooutput'),
    audioTopology: topology(['audioinput', 'audiooutput']),
    videoTopology: topology(['videoinput']),
  };
}

interface _ChangeSinks {
  inputChanges: StreamController<string | null>;
  outputChanges: StreamController<string | null>;
  videoChanges: StreamController<void>;
  changes: StreamController<string | null>;
}

/**
 * Turns `devicechange` into the native channel's typed notifications by
 * diffing serialized snapshots. One watcher per `MediaDevices` object is
 * shared by every default [SystemAudioDevices] instance, so opening Settings
 * and joining a room do not enumerate devices once per instance.
 */
class _DeviceChangeWatcher {
  constructor(
    private readonly _mediaDevices: MediaDevicesLike,
    private readonly _sinks: _ChangeSinks,
  ) {}

  private _listening = false;
  private _previous: _DeviceSnapshot | null = null;
  private _tail: Promise<void> = Promise.resolve();
  private readonly _listener = (): void => {
    this._tail = this._tail.then(() => this._handleChange());
  };

  get inputChanges(): EventStream<string | null> {
    return this._sinks.inputChanges.stream;
  }

  get outputChanges(): EventStream<string | null> {
    return this._sinks.outputChanges.stream;
  }

  get videoChanges(): EventStream<void> {
    return this._sinks.videoChanges.stream;
  }

  get changes(): EventStream<string | null> {
    return this._sinks.changes.stream;
  }

  ensureListening(): void {
    if (this._listening) return;
    this._listening = true;
    this._mediaDevices.addEventListener?.('devicechange', this._listener);
    // Baseline for the first diff.
    this._tail = this._tail.then(async () => {
      try {
        this._previous = _snapshotOf(await this._mediaDevices.enumerateDevices());
      } catch {
        this._previous = null;
      }
    });
  }

  stopListening(): void {
    if (!this._listening) return;
    this._listening = false;
    this._mediaDevices.removeEventListener?.('devicechange', this._listener);
  }

  private async _handleChange(): Promise<void> {
    if (!this._listening) return;
    let next: _DeviceSnapshot;
    try {
      next = _snapshotOf(await this._mediaDevices.enumerateDevices());
    } catch {
      // Unknown topology: report a generic change so recovery still runs.
      this._previous = null;
      this._sinks.changes.add(null);
      this._sinks.videoChanges.add();
      return;
    }
    const previous = this._previous;
    this._previous = next;
    if (!this._listening) return;
    if (previous == null) {
      this._sinks.changes.add(null);
      this._sinks.videoChanges.add();
      return;
    }
    if (previous.defaultInput !== next.defaultInput) {
      this._sinks.inputChanges.add(next.defaultInput);
      this._sinks.changes.add(next.defaultInput);
    }
    if (previous.defaultOutput !== next.defaultOutput) {
      this._sinks.outputChanges.add(next.defaultOutput);
      this._sinks.changes.add(next.defaultOutput);
    }
    if (previous.audioTopology !== next.audioTopology) {
      this._sinks.changes.add(null);
    }
    if (previous.videoTopology !== next.videoTopology) {
      this._sinks.videoChanges.add();
    }
  }
}

const _sharedWatchers = new WeakMap<MediaDevicesLike, _DeviceChangeWatcher>();

function _sharedWatcherFor(mediaDevices: MediaDevicesLike): _DeviceChangeWatcher {
  let watcher = _sharedWatchers.get(mediaDevices);
  if (watcher == null) {
    watcher = new _DeviceChangeWatcher(mediaDevices, {
      inputChanges: new StreamController<string | null>(),
      outputChanges: new StreamController<string | null>(),
      videoChanges: new StreamController<void>(),
      changes: new StreamController<string | null>(),
    });
    _sharedWatchers.set(mediaDevices, watcher);
  }
  return watcher;
}

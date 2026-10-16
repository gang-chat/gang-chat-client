/**
 * Port of `lib/src/live/audio_device_service.dart`.
 */
import { HostPlatform } from '../config/host_platform';
import type { EventStream, StreamSubscription } from '../lib/event_stream';
import { AudioDeviceInfo } from '../app/audio_device_info';
import { LiveHardware, MediaDevice } from './live_hardware';
import { SystemAudioDevices } from './system_audio_devices';

export class LiveAudioDeviceService {
  /**
   * PORT: [hardware] replaces the Flutter SDK's `lk.Hardware.instance`
   * singleton (injectable for tests); see `live_hardware.ts`.
   */
  constructor(opts: { systemAudioOnly?: boolean; systemAudio?: SystemAudioDevices; hardware?: LiveHardware } = {}) {
    this._systemAudioOnlyOverride = opts.systemAudioOnly;
    this._systemAudioOverride = opts.systemAudio;
    this._hardwareOverride = opts.hardware;
  }

  private readonly _systemAudioOnlyOverride: boolean | undefined;
  private readonly _systemAudioOverride: SystemAudioDevices | undefined;
  private readonly _hardwareOverride: LiveHardware | undefined;

  // Desktop system audio access. macOS merges system devices into the WebRTC
  // list; Windows treats the system list as authoritative.
  private static _sharedSystemAudioInstance: SystemAudioDevices | null = null;

  private static get _sharedSystemAudio(): SystemAudioDevices {
    return (LiveAudioDeviceService._sharedSystemAudioInstance ??= new SystemAudioDevices());
  }

  private get _systemAudio(): SystemAudioDevices {
    return this._systemAudioOverride ?? LiveAudioDeviceService._sharedSystemAudio;
  }

  private get _hardware(): LiveHardware {
    return this._hardwareOverride ?? LiveHardware.instance;
  }

  private get _systemAudioOnly(): boolean {
    return (
      this._systemAudioOnlyOverride ??
      systemAudioOnlyForPlatform({
        isWeb: HostPlatform.isWeb,
        isWindows: !HostPlatform.isWeb && HostPlatform.isWindows,
      })
    );
  }

  get devicesChanged(): EventStream<AudioDeviceInfo[]> {
    if (this._systemAudioOnly) {
      // Windows listens to the system endpoint stream directly.
      const systemAudio = this._systemAudio;
      return _asyncMapStream(systemAudio.changes, () => systemAudio.enumerateDevices());
    }
    // Re-merge system devices on every hardware change so a hotplug doesn't
    // wipe devices the WebRTC list does not report.
    return _asyncMapStream(this._hardware.onDeviceChange, (devices) =>
      this._mergeNativeDevices(_audioDeviceInfos(devices)),
    );
  }

  async enumerateDevices(): Promise<AudioDeviceInfo[]> {
    if (this._systemAudioOnly) {
      return this._systemAudio.enumerateDevicesOrThrow();
    }
    const webrtc = _audioDeviceInfos(await this._hardware.enumerateDevices());
    return this._mergeNativeDevices(webrtc);
  }

  // Adds system input and output devices that WebRTC didn't report (matched
  // by kind + deviceId). When WebRTC already lists devices the lists overlap
  // and nothing new is added.
  private async _mergeNativeDevices(webrtc: AudioDeviceInfo[]): Promise<AudioDeviceInfo[]> {
    const native = [...(await this._systemAudio.enumerateInputs()), ...(await this._systemAudio.enumerateOutputs())];
    if (native.length === 0) return webrtc;
    const seen = new Set(webrtc.map((d) => `${d.kind}:${d.deviceId}`));
    const merged = [...webrtc];
    for (const device of native) {
      const key = `${device.kind}:${device.deviceId}`;
      if (seen.has(key)) continue;
      seen.add(key);
      merged.push(device);
    }
    return merged;
  }

  get selectedAudioInput(): AudioDeviceInfo | null {
    return _audioDeviceInfoOrNull(this._hardware.selectedAudioInput);
  }

  get selectedAudioOutput(): AudioDeviceInfo | null {
    return _audioDeviceInfoOrNull(this._hardware.selectedAudioOutput);
  }

  selectAudioInput(device: AudioDeviceInfo): Promise<void> {
    return this._hardware.selectAudioInput(_mediaDevice(device));
  }

  selectAudioOutput(device: AudioDeviceInfo): Promise<void> {
    return this._hardware.selectAudioOutput(_mediaDevice(device));
  }
}

export function systemAudioOnlyForPlatform(opts: { isWeb: boolean; isWindows: boolean }): boolean {
  return !opts.isWeb && opts.isWindows;
}

function _audioDeviceInfos(devices: readonly MediaDevice[]): AudioDeviceInfo[] {
  return devices.map(_audioDeviceInfo);
}

function _audioDeviceInfoOrNull(device: MediaDevice | null): AudioDeviceInfo | null {
  return device == null ? null : _audioDeviceInfo(device);
}

function _audioDeviceInfo(device: MediaDevice): AudioDeviceInfo {
  return new AudioDeviceInfo({
    deviceId: device.deviceId,
    label: device.label,
    kind: device.kind,
    groupId: device.groupId ?? '',
  });
}

function _mediaDevice(device: AudioDeviceInfo): MediaDevice {
  return new MediaDevice(device.deviceId, device.label, device.kind, device.groupId);
}

/**
 * Dart `stream.asyncMap(convert)`: events are converted one at a time, in
 * order; a failed conversion is delivered as an error event.
 */
export function _asyncMapStream<S, T>(source: EventStream<S>, convert: (event: S) => Promise<T>): EventStream<T> {
  return {
    listen(onData, options) {
      let cancelled = false;
      let tail: Promise<void> = Promise.resolve();
      const sub: StreamSubscription = source.listen(
        (event) => {
          tail = tail.then(async () => {
            if (cancelled) return;
            try {
              const converted = await convert(event);
              if (!cancelled) onData(converted);
            } catch (error) {
              if (!cancelled) options?.onError?.(error);
            }
          });
        },
        {
          onError: (error) => {
            tail = tail.then(() => {
              if (!cancelled) options?.onError?.(error);
            });
          },
          onDone: () => {
            tail = tail.then(() => {
              if (!cancelled) options?.onDone?.();
            });
          },
        },
      );
      return {
        cancel: () => {
          cancelled = true;
          sub.cancel();
        },
      };
    },
  };
}

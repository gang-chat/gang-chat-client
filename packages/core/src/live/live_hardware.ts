/**
 * The slice of the Flutter SDK's `lk.Hardware.instance` the Dart client used
 * (device enumeration, the device-change stream and the selected audio
 * input/output), rebuilt on the WebRTC `navigator.mediaDevices` global.
 *
 * PORT: livekit-client has no process-wide hardware singleton. On Flutter,
 * `Hardware.selectAudioInput/selectAudioOutput` re-routed the single native
 * audio-device module for every track at once. Chromium (and
 * react-native-webrtc) route per track/element instead, so the selection is
 * remembered here and applied by the consumers: [LiveSession] follows
 * [audioOutputSelections] with `room.switchActiveDevice('audiooutput')`, the
 * app keeps the input in sync through `LiveSession.setInputDeviceId`, and the
 * audio test plays through the selected output.
 */
import { StreamController, type EventStream } from '../lib/event_stream';

/** Port of the Flutter SDK's `MediaDevice`. */
export class MediaDevice {
  constructor(
    readonly deviceId: string,
    readonly label: string,
    readonly kind: string,
    readonly groupId: string | null = null,
  ) {}
}

/**
 * The part of `MediaDevices` the live layer needs. Event listeners are
 * optional because some react-native-webrtc versions only implement
 * `enumerateDevices`.
 */
export interface MediaDevicesLike {
  enumerateDevices(): Promise<ReadonlyArray<Pick<MediaDeviceInfo, 'deviceId' | 'label' | 'kind' | 'groupId'>>>;
  addEventListener?(type: 'devicechange', listener: () => void): void;
  removeEventListener?(type: 'devicechange', listener: () => void): void;
}

/** `navigator.mediaDevices`, or null where the host exposes none (unit tests). */
export function defaultMediaDevices(): MediaDevicesLike | null {
  if (typeof navigator === 'undefined') return null;
  const devices = (navigator as Partial<Navigator>).mediaDevices;
  return devices ?? null;
}

export class LiveHardware {
  constructor(opts: { mediaDevices?: MediaDevicesLike | null } = {}) {
    this._mediaDevicesOverride = opts.mediaDevices;
  }

  static readonly instance = new LiveHardware();

  private readonly _mediaDevicesOverride: MediaDevicesLike | null | undefined;
  private readonly _deviceChanges = new StreamController<MediaDevice[]>();
  private readonly _outputSelections = new StreamController<MediaDevice | null>();
  private _listening = false;

  selectedAudioInput: MediaDevice | null = null;
  selectedAudioOutput: MediaDevice | null = null;

  get mediaDevices(): MediaDevicesLike | null {
    return this._mediaDevicesOverride !== undefined ? this._mediaDevicesOverride : defaultMediaDevices();
  }

  /** Emits the full device list after every OS `devicechange`. */
  get onDeviceChange(): EventStream<MediaDevice[]> {
    this._ensureListening();
    return this._deviceChanges.stream;
  }

  /** Emits whenever [selectAudioOutput] changes the playback device. */
  get audioOutputSelections(): EventStream<MediaDevice | null> {
    return this._outputSelections.stream;
  }

  async enumerateDevices(): Promise<MediaDevice[]> {
    const mediaDevices = this.mediaDevices;
    if (mediaDevices == null) return [];
    const infos = await mediaDevices.enumerateDevices();
    return infos.map((info) => new MediaDevice(info.deviceId, info.label, info.kind, info.groupId || null));
  }

  /** Re-enumerates and republishes the device list (camera hotplug recovery). */
  async refreshDevices(): Promise<void> {
    const devices = await this.enumerateDevices();
    this._deviceChanges.add(devices);
  }

  async selectAudioInput(device: MediaDevice): Promise<void> {
    this.selectedAudioInput = device;
  }

  async selectAudioOutput(device: MediaDevice): Promise<void> {
    this.selectedAudioOutput = device;
    this._outputSelections.add(device);
  }

  private _ensureListening(): void {
    if (this._listening) return;
    const mediaDevices = this.mediaDevices;
    if (mediaDevices?.addEventListener == null) return;
    this._listening = true;
    mediaDevices.addEventListener('devicechange', () => {
      void this.enumerateDevices().then(
        (devices) => this._deviceChanges.add(devices),
        () => {},
      );
    });
  }
}

import { type EventStream, StreamController } from '../lib/event_stream';
import { LiveAudioDeviceService } from '../live/audio_device_service';
import type { GangApi } from '../protocol/api_client';
import { UserAudioSettings } from '../protocol/models';
import type { AudioDeviceInfo } from './audio_device_info';
import { AudioDeviceStore, StoredAudioDevices } from './audio_device_store';
import { normalizedAudioVolume } from './audio_levels';

/**
 * Adapts the server-backed audio defaults of a managed account to the same
 * volume controls used by the ordinary settings page. Hardware device ids and
 * screen-share resolution remain device-local and are deliberately not
 * represented here.
 */
export class ManagedUserAudioSettingsStore extends AudioDeviceStore {
  constructor(opts: { api: GangApi; userId: string }) {
    super();
    this.api = opts.api;
    this.userId = opts.userId;
  }

  readonly api: GangApi;
  readonly userId: string;

  private _settings: UserAudioSettings | null = null;
  private _writes: Promise<void> = Promise.resolve();

  override async read(): Promise<StoredAudioDevices> {
    await this._writes;
    const settings = await this._load();
    return new StoredAudioDevices({
      inputVolume: normalizedAudioVolume(settings.defaultAudioInputVolume / 100),
      outputVolume: normalizedAudioVolume(settings.defaultAudioOutputVolume / 100),
      musicBoxVolume: normalizedAudioVolume(settings.liveMusicOutputVolume / 100),
      screenShareVolume: normalizedAudioVolume(settings.liveScreenShareOutputVolume / 100),
    });
  }

  override writeInputVolume(volume: number): Promise<void> {
    return this._update({ defaultInput: this._percent(volume) });
  }

  override writeOutputVolume(volume: number): Promise<void> {
    return this._update({ defaultOutput: this._percent(volume) });
  }

  override writeMusicBoxVolume(volume: number): Promise<void> {
    return this._update({ music: this._percent(volume) });
  }

  override writeScreenShareVolume(volume: number): Promise<void> {
    return this._update({ screenShare: this._percent(volume) });
  }

  override async writeInputDeviceId(deviceId: string): Promise<void> {}

  override async writeOutputDeviceId(deviceId: string): Promise<void> {}

  override async writeScreenShareMaxHeight(height: number): Promise<void> {}

  private _percent(volume: number): number {
    return Math.round(normalizedAudioVolume(volume) * 100);
  }

  private async _load(): Promise<UserAudioSettings> {
    return (this._settings ??= await this.api.getForcedUserAudioSettings(this.userId));
  }

  private _update(opts: { defaultInput?: number; defaultOutput?: number; screenShare?: number; music?: number }): Promise<void> {
    const operation = this._writes.then(async () => {
      const current = await this._load();
      this._settings = await this.api.updateForcedUserAudioSettings({
        userId: this.userId,
        settings: new UserAudioSettings({
          defaultAudioInputVolume: opts.defaultInput ?? current.defaultAudioInputVolume,
          defaultAudioOutputVolume: opts.defaultOutput ?? current.defaultAudioOutputVolume,
          liveMicInputVolume: current.liveMicInputVolume,
          liveVoiceOutputVolume: current.liveVoiceOutputVolume,
          liveScreenShareOutputVolume: opts.screenShare ?? current.liveScreenShareOutputVolume,
          liveMusicOutputVolume: opts.music ?? current.liveMusicOutputVolume,
          updatedAt: current.updatedAt,
        }),
      });
    });
    this._writes = operation.catch(() => {});
    return operation;
  }
}

/**
 * Prevents a managed account page from enumerating or selecting hardware on
 * the administrator's computer.
 */
export class ManagedUserAudioDeviceService extends LiveAudioDeviceService {
  constructor() {
    super();
  }

  override get devicesChanged(): EventStream<AudioDeviceInfo[]> {
    // `const Stream.empty()`: completes as soon as it is listened to.
    const controller = new StreamController<AudioDeviceInfo[]>();
    controller.close();
    return controller.stream;
  }

  override async enumerateDevices(): Promise<AudioDeviceInfo[]> {
    return [];
  }

  override get selectedAudioInput(): AudioDeviceInfo | null {
    return null;
  }

  override get selectedAudioOutput(): AudioDeviceInfo | null {
    return null;
  }

  override async selectAudioInput(device: AudioDeviceInfo): Promise<void> {}

  override async selectAudioOutput(device: AudioDeviceInfo): Promise<void> {}
}

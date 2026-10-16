import { AudioDeviceStore } from '../app/audio_device_store';
import {
  StoredAudioDevices,
  defaultAudioVolume,
  storedAudioDeviceIdFromStorageValue,
  storedAudioDeviceSignatureFromStorageValue,
} from '../app/audio_device_preferences';
import {
  defaultParticipantVoiceVolume,
  normalizedAudioVolume,
  normalizedParticipantVoiceVolume,
} from '../app/audio_levels';
import { normalizedScreenShareFrameRate, normalizedScreenShareMaxHeight } from '../live/screen_share_quality';
import { platform } from '../platform/platform';
import type { PreferencesStore } from '../platform/platform';

/**
 * Persists audio device + volume preferences in the platform preferences
 * store (SharedPreferences in the Flutter client; keys are unchanged).
 *
 * These values (which mic/speaker, what volumes) are not sensitive, so they
 * live in plain local preferences rather than the keychain. On macOS reading
 * the keychain pops an authorization prompt every launch under a debug/unstable
 * code signature; plain preferences never do. The refresh token stays in
 * secure storage — see TokenStore.
 */
export class LocalAudioDeviceStore extends AudioDeviceStore {
  constructor(opts: { preferences?: PreferencesStore } = {}) {
    super();
    this._preferences = opts.preferences ?? null;
  }

  private readonly _preferences: PreferencesStore | null;

  private static readonly _inputDeviceIdKey = 'gang.audioInputDeviceId';
  private static readonly _inputDeviceLabelKey = 'gang.audioInputDeviceLabel';
  private static readonly _inputDeviceGroupIdKey = 'gang.audioInputDeviceGroupId';
  private static readonly _outputDeviceIdKey = 'gang.audioOutputDeviceId';
  private static readonly _outputDeviceLabelKey = 'gang.audioOutputDeviceLabel';
  private static readonly _outputDeviceGroupIdKey = 'gang.audioOutputDeviceGroupId';
  private static readonly _inputVolumeKey = 'gang.audioInputVolume';
  private static readonly _outputVolumeKey = 'gang.audioOutputVolume';
  private static readonly _musicBoxVolumeKey = 'gang.musicBoxVolume';
  private static readonly _screenShareVolumeKey = 'gang.screenShareVolume';
  private static readonly _screenShareMaxHeightKey = 'gang.screenShareMaxHeight';
  private static readonly _screenShareFrameRateKey = 'gang.screenShareFrameRate';
  private static readonly _participantVoiceVolumePrefix = 'gang.participantVoiceVolume.';

  private get _prefs(): PreferencesStore {
    return this._preferences ?? platform().preferences;
  }

  override async read(): Promise<StoredAudioDevices> {
    const prefs = this._prefs;
    const S = LocalAudioDeviceStore;
    return new StoredAudioDevices({
      inputDeviceId: storedAudioDeviceIdFromStorageValue(prefs.getString(S._inputDeviceIdKey)),
      inputDeviceLabel: storedAudioDeviceSignatureFromStorageValue(prefs.getString(S._inputDeviceLabelKey)),
      inputDeviceGroupId: storedAudioDeviceSignatureFromStorageValue(prefs.getString(S._inputDeviceGroupIdKey)),
      outputDeviceId: storedAudioDeviceIdFromStorageValue(prefs.getString(S._outputDeviceIdKey)),
      outputDeviceLabel: storedAudioDeviceSignatureFromStorageValue(prefs.getString(S._outputDeviceLabelKey)),
      outputDeviceGroupId: storedAudioDeviceSignatureFromStorageValue(prefs.getString(S._outputDeviceGroupIdKey)),
      inputVolume: this._readVolume(prefs, S._inputVolumeKey),
      outputVolume: this._readVolume(prefs, S._outputVolumeKey),
      musicBoxVolume: this._readVolume(prefs, S._musicBoxVolumeKey),
      screenShareVolume: this._readVolume(prefs, S._screenShareVolumeKey),
      screenShareMaxHeight: normalizedScreenShareMaxHeight(prefs.getInt(S._screenShareMaxHeightKey)),
      screenShareFrameRate: normalizedScreenShareFrameRate(prefs.getInt(S._screenShareFrameRateKey)),
    });
  }

  override async writeInputDeviceId(deviceId: string): Promise<void> {
    await this._prefs.setString(LocalAudioDeviceStore._inputDeviceIdKey, deviceId);
  }

  override async writeInputDevicePreference(opts: {
    deviceId: string;
    label?: string | null;
    groupId?: string | null;
  }): Promise<void> {
    await this._writeDevicePreference(this._prefs, {
      idKey: LocalAudioDeviceStore._inputDeviceIdKey,
      labelKey: LocalAudioDeviceStore._inputDeviceLabelKey,
      groupIdKey: LocalAudioDeviceStore._inputDeviceGroupIdKey,
      deviceId: opts.deviceId,
      label: opts.label ?? null,
      groupId: opts.groupId ?? null,
    });
  }

  override async writeOutputDeviceId(deviceId: string): Promise<void> {
    await this._prefs.setString(LocalAudioDeviceStore._outputDeviceIdKey, deviceId);
  }

  override async writeOutputDevicePreference(opts: {
    deviceId: string;
    label?: string | null;
    groupId?: string | null;
  }): Promise<void> {
    await this._writeDevicePreference(this._prefs, {
      idKey: LocalAudioDeviceStore._outputDeviceIdKey,
      labelKey: LocalAudioDeviceStore._outputDeviceLabelKey,
      groupIdKey: LocalAudioDeviceStore._outputDeviceGroupIdKey,
      deviceId: opts.deviceId,
      label: opts.label ?? null,
      groupId: opts.groupId ?? null,
    });
  }

  override async writeInputVolume(volume: number): Promise<void> {
    await this._prefs.setDouble(LocalAudioDeviceStore._inputVolumeKey, normalizedAudioVolume(volume));
  }

  override async writeOutputVolume(volume: number): Promise<void> {
    await this._prefs.setDouble(LocalAudioDeviceStore._outputVolumeKey, normalizedAudioVolume(volume));
  }

  override async writeMusicBoxVolume(volume: number): Promise<void> {
    await this._prefs.setDouble(LocalAudioDeviceStore._musicBoxVolumeKey, normalizedAudioVolume(volume));
  }

  override async writeScreenShareVolume(volume: number): Promise<void> {
    await this._prefs.setDouble(LocalAudioDeviceStore._screenShareVolumeKey, normalizedAudioVolume(volume));
  }

  override async writeScreenShareMaxHeight(height: number): Promise<void> {
    await this._prefs.setInt(LocalAudioDeviceStore._screenShareMaxHeightKey, normalizedScreenShareMaxHeight(height));
  }

  override async writeScreenShareFrameRate(frameRate: number): Promise<void> {
    await this._prefs.setInt(LocalAudioDeviceStore._screenShareFrameRateKey, normalizedScreenShareFrameRate(frameRate));
  }

  override async readParticipantVoiceVolume(userId: string): Promise<number> {
    const value = this._prefs.getDouble(this._participantVoiceVolumeKey(userId));
    if (value == null) return defaultParticipantVoiceVolume;
    return normalizedParticipantVoiceVolume(value);
  }

  override async writeParticipantVoiceVolume(userId: string, volume: number): Promise<void> {
    const prefs = this._prefs;
    const normalized = normalizedParticipantVoiceVolume(volume);
    const key = this._participantVoiceVolumeKey(userId);
    if (normalized === defaultParticipantVoiceVolume) {
      await prefs.remove(key);
      return;
    }
    await prefs.setDouble(key, normalized);
  }

  private _readVolume(prefs: PreferencesStore, key: string): number {
    const value = prefs.getDouble(key);
    return value == null ? defaultAudioVolume : normalizedAudioVolume(value);
  }

  private async _writeDevicePreference(
    prefs: PreferencesStore,
    opts: {
      idKey: string;
      labelKey: string;
      groupIdKey: string;
      deviceId: string;
      label: string | null;
      groupId: string | null;
    },
  ): Promise<void> {
    await prefs.setString(opts.idKey, opts.deviceId);
    await this._writeOptionalString(prefs, opts.labelKey, opts.label);
    await this._writeOptionalString(prefs, opts.groupIdKey, opts.groupId);
  }

  private async _writeOptionalString(prefs: PreferencesStore, key: string, value: string | null): Promise<void> {
    const trimmed = value?.trim();
    if (trimmed == null || trimmed.length === 0) {
      await prefs.remove(key);
      return;
    }
    await prefs.setString(key, trimmed);
  }

  private _participantVoiceVolumeKey(userId: string): string {
    // Dart `Uri.encodeComponent` leaves ! ' ( ) * unescaped too, like encodeURIComponent.
    return `${LocalAudioDeviceStore._participantVoiceVolumePrefix}${encodeURIComponent(userId)}`;
  }
}

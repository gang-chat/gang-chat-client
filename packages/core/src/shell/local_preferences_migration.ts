import { defaultAudioVolume } from '../app/audio_device_preferences';
import { HostPlatform } from '../config/host_platform';
import { isJsonMap } from '../lib/json';
import type { JsonMap } from '../lib/json';
import { utf8Decode } from '../platform/http';
import { platform } from '../platform/platform';
import type { FileSystemBridge, PreferencesStore } from '../platform/platform';

/**
 * Migrates device-wide preferences from historical application identities.
 *
 * Windows derives its SharedPreferences directory from the executable's
 * CompanyName + ProductName metadata. Gang Chat used `client` as ProductName
 * before the visible product rename, so an in-place update appeared to reset
 * every preference even though the old JSON file was still present. Android's
 * application id and macOS's bundle id have remained stable and need no file
 * migration.
 *
 * PORT: the legacy file holds the same `gang.*` keys this port reads, so the
 * logic is kept. `%APPDATA%` comes from `files.directories().roamingAppData`.
 * Carrying the Flutter app's *current* preferences
 * (`%APPDATA%\com.gangchat\Gang Chat\shared_preferences.json`) over to the
 * Electron app is the desktop host's `PreferencesStore` concern.
 */
export class LocalPreferencesMigration {
  constructor(opts: { preferences?: PreferencesStore; files?: FileSystemBridge } = {}) {
    this._preferences = opts.preferences ?? null;
    this._files = opts.files ?? null;
  }

  private readonly _preferences: PreferencesStore | null;
  private readonly _files: FileSystemBridge | null;

  private static readonly _windowsMigrationMarker = 'gang.preferencesMigration.windowsProductName.v1';
  private static readonly _flutterStorageKeyPrefix = 'flutter.';
  private static readonly _audioVolumeKeys: ReadonlySet<string> = new Set([
    'gang.audioInputVolume',
    'gang.audioOutputVolume',
    'gang.musicBoxVolume',
    'gang.screenShareVolume',
  ]);
  /** Keys LocalAudioDeviceStore writes with setDouble; JSON cannot tell `1.0` from `1`. */
  private static readonly _participantVoiceVolumePrefix = 'gang.participantVoiceVolume.';

  async migrate(): Promise<void> {
    if (HostPlatform.isWeb || !HostPlatform.isWindows) return;
    const files = this._files ?? platform().files;
    const roamingAppData = (await files.directories()).roamingAppData?.trim();
    if (roamingAppData == null || roamingAppData.length === 0) return;

    const preferences = this._preferences ?? platform().preferences;
    if (preferences.getBool(LocalPreferencesMigration._windowsMigrationMarker) === true) return;

    const legacyFile = files.joinPath(roamingAppData, 'com.gangchat', 'client', 'shared_preferences.json');
    if (!(await files.exists(legacyFile))) return;

    const values = await this._readLegacyPreferences(files, legacyFile);
    await LocalPreferencesMigration.migrateLegacyPreferences({ preferences, legacyValues: values });
    await preferences.setBool(LocalPreferencesMigration._windowsMigrationMarker, true);
  }

  private async _readLegacyPreferences(files: FileSystemBridge, file: string): Promise<JsonMap> {
    try {
      const decoded: unknown = JSON.parse(utf8Decode(await files.readFile(file)));
      if (!isJsonMap(decoded)) return {};
      return decoded;
    } catch {
      // A corrupt legacy file must never prevent the current app from opening.
      return {};
    }
  }

  /** @visibleForTesting */
  static async migrateLegacyPreferences(opts: { preferences: PreferencesStore; legacyValues: JsonMap }): Promise<number> {
    const { preferences, legacyValues } = opts;
    const S = LocalPreferencesMigration;
    let migrated = 0;
    for (const [key, value] of Object.entries(legacyValues)) {
      const storageKey = key.startsWith(S._flutterStorageKeyPrefix) ? key.slice(S._flutterStorageKeyPrefix.length) : key;
      if (!S._isSafeSharedPreferenceKey(storageKey)) {
        continue;
      }
      const hasCurrentValue = preferences.containsKey(storageKey);
      if (hasCurrentValue && !S._shouldRecoverLegacyDefault(storageKey, S._currentNumber(preferences, storageKey))) {
        continue;
      }
      if (await S._writeSupportedValue(preferences, storageKey, value)) {
        migrated += 1;
      }
    }
    return migrated;
  }

  private static _isSafeSharedPreferenceKey(key: string): boolean {
    if (!key.startsWith('gang.')) return false;
    // Authentication secrets and remembered passwords belong to secure
    // storage and must never be copied into plaintext JSON.
    return key !== 'gang.refreshToken' && !key.startsWith('gang.loginAccountPassword.');
  }

  private static _currentNumber(preferences: PreferencesStore, key: string): number | null {
    try {
      return preferences.getDouble(key) ?? preferences.getInt(key);
    } catch {
      return null;
    }
  }

  private static _shouldRecoverLegacyDefault(key: string, currentValue: number | null): boolean {
    if (!LocalPreferencesMigration._audioVolumeKeys.has(key) || currentValue == null) return false;
    return Math.abs(currentValue - defaultAudioVolume) < 0.000001;
  }

  private static _isDoubleKey(key: string): boolean {
    return (
      LocalPreferencesMigration._audioVolumeKeys.has(key) ||
      key.startsWith(LocalPreferencesMigration._participantVoiceVolumePrefix)
    );
  }

  private static async _writeSupportedValue(preferences: PreferencesStore, key: string, value: unknown): Promise<boolean> {
    if (typeof value === 'boolean') {
      await preferences.setBool(key, value);
      return true;
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
      if (Number.isInteger(value) && !LocalPreferencesMigration._isDoubleKey(key)) {
        await preferences.setInt(key, value);
      } else {
        await preferences.setDouble(key, value);
      }
      return true;
    }
    if (typeof value === 'string') {
      await preferences.setString(key, value);
      return true;
    }
    if (Array.isArray(value) && value.every((item) => typeof item === 'string')) {
      await preferences.setStringList(key, value as string[]);
      return true;
    }
    return false;
  }
}

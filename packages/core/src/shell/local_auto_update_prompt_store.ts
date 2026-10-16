import { AutoUpdatePromptStore, defaultAutoUpdatePromptEnabled } from '../app/settings_about';
import { platform } from '../platform/platform';
import type { PreferencesStore } from '../platform/platform';

export class LocalAutoUpdatePromptStore extends AutoUpdatePromptStore {
  constructor(opts: { preferences?: PreferencesStore } = {}) {
    super();
    this._preferences = opts.preferences ?? null;
  }

  private readonly _preferences: PreferencesStore | null;

  private static readonly _autoUpdatePromptKey = 'gang.autoUpdatePrompt';
  private static readonly _ignoredVersionKey = 'gang.autoUpdateIgnoredVersion';

  private get _prefs(): PreferencesStore {
    return this._preferences ?? platform().preferences;
  }

  override async read(): Promise<boolean> {
    return this._prefs.getBool(LocalAutoUpdatePromptStore._autoUpdatePromptKey) ?? defaultAutoUpdatePromptEnabled;
  }

  override async write(enabled: boolean): Promise<void> {
    await this._prefs.setBool(LocalAutoUpdatePromptStore._autoUpdatePromptKey, enabled);
  }

  override async readIgnoredVersion(): Promise<string | null> {
    const version = this._prefs.getString(LocalAutoUpdatePromptStore._ignoredVersionKey)?.trim();
    if (version == null || version.length === 0) return null;
    return version;
  }

  override async writeIgnoredVersion(version: string | null): Promise<void> {
    const normalized = version?.trim();
    if (normalized == null || normalized.length === 0) {
      await this._prefs.remove(LocalAutoUpdatePromptStore._ignoredVersionKey);
      return;
    }
    await this._prefs.setString(LocalAutoUpdatePromptStore._ignoredVersionKey, normalized);
  }
}

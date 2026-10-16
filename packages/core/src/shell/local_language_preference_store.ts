import { LanguagePreferenceStore, normalizeLanguagePreference } from '../app/language_preference';
import { platform } from '../platform/platform';
import type { PreferencesStore } from '../platform/platform';

export class LocalLanguagePreferenceStore extends LanguagePreferenceStore {
  constructor(opts: { preferences?: PreferencesStore } = {}) {
    super();
    this._preferences = opts.preferences ?? null;
  }

  private readonly _preferences: PreferencesStore | null;

  private static readonly _languageKey = 'gang.language';

  private get _prefs(): PreferencesStore {
    return this._preferences ?? platform().preferences;
  }

  override async read(): Promise<string> {
    return normalizeLanguagePreference(this._prefs.getString(LocalLanguagePreferenceStore._languageKey));
  }

  override async write(language: string): Promise<void> {
    await this._prefs.setString(LocalLanguagePreferenceStore._languageKey, normalizeLanguagePreference(language));
  }
}

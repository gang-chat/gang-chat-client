import { defaultUserLanguage } from '../protocol/models';

export const defaultLanguagePreference = defaultUserLanguage;

export function normalizeLanguagePreference(language: string | null | undefined): string {
  switch (language?.trim()) {
    case 'zh-Hans':
      return 'zh-Hans';
    case 'zh-Hant':
      return 'zh-Hant';
    case 'en':
      return 'en';
    default:
      return defaultLanguagePreference;
  }
}

export class LanguagePreferenceStore {
  read(): Promise<string> {
    throw new Error('LanguagePreferenceStore.read must be implemented.');
  }

  write(language: string): Promise<void> {
    throw new Error('LanguagePreferenceStore.write must be implemented.');
  }
}

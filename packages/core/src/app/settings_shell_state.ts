export enum SettingsSection {
  profile = 'profile',
  preferences = 'preferences',
  security = 'security',
  voice = 'voice',
  stickers = 'stickers',
  playlists = 'playlists',
  about = 'about',
}

export class SettingsSectionPatch {
  constructor(opts: {
    section: SettingsSection;
    notice: string | null;
    shouldLoadStickers: boolean;
    shouldLoadSessions: boolean;
    shouldInitializeVoice: boolean;
  }) {
    this.section = opts.section;
    this.notice = opts.notice;
    this.shouldLoadStickers = opts.shouldLoadStickers;
    this.shouldLoadSessions = opts.shouldLoadSessions;
    this.shouldInitializeVoice = opts.shouldInitializeVoice;
  }

  readonly section: SettingsSection;
  readonly notice: string | null;
  readonly shouldLoadStickers: boolean;
  readonly shouldLoadSessions: boolean;
  readonly shouldInitializeVoice: boolean;
}

export class SettingsNoticePatch {
  constructor(opts: { notice: string | null }) {
    this.notice = opts.notice;
  }

  readonly notice: string | null;
}

export function settingsSectionSelected(opts: {
  section: SettingsSection;
  sessionsEmpty: boolean;
  loadingSessions: boolean;
}): SettingsSectionPatch {
  const { section, sessionsEmpty, loadingSessions } = opts;
  return new SettingsSectionPatch({
    section,
    notice: null,
    shouldLoadStickers: section === SettingsSection.stickers,
    shouldLoadSessions: section === SettingsSection.security && sessionsEmpty && !loadingSessions,
    shouldInitializeVoice: section === SettingsSection.voice,
  });
}

export function settingsSectionTitle(section: SettingsSection): string {
  switch (section) {
    case SettingsSection.profile:
      return '用户资料';
    case SettingsSection.preferences:
      return '偏好设置';
    case SettingsSection.stickers:
      return '表情包管理';
    case SettingsSection.playlists:
      return '我的歌单';
    case SettingsSection.security:
      return '隐私和安全';
    case SettingsSection.voice:
      return '语音和视频';
    case SettingsSection.about:
      return '关于Gang Chat';
  }
}

export function settingsNoticeShown(message: string): SettingsNoticePatch {
  return new SettingsNoticePatch({ notice: message });
}

export function settingsSectionRefreshing(opts: {
  section: SettingsSection;
  loadingAccount: boolean;
  loadingPreferences: boolean;
  loadingStickers: boolean;
  loadingSessions: boolean;
  loadingVoice: boolean;
  loadingPlaylists?: boolean;
  loadingAbout?: boolean;
}): boolean {
  const { loadingPlaylists = false, loadingAbout = false } = opts;
  switch (opts.section) {
    case SettingsSection.profile:
      return opts.loadingAccount;
    case SettingsSection.preferences:
      return opts.loadingPreferences;
    case SettingsSection.stickers:
      return opts.loadingStickers;
    case SettingsSection.playlists:
      return loadingPlaylists;
    case SettingsSection.security:
      return opts.loadingAccount || opts.loadingSessions;
    case SettingsSection.voice:
      return opts.loadingVoice;
    case SettingsSection.about:
      return loadingAbout;
  }
}

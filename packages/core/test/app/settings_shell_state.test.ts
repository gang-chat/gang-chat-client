import { describe, expect, test } from 'vitest';

import {
  SettingsSection,
  settingsNoticeShown,
  settingsSectionRefreshing,
  settingsSectionSelected,
  settingsSectionTitle,
} from '../../src/app/settings_shell_state';

const idle = {
  loadingAccount: false,
  loadingPreferences: false,
  loadingStickers: false,
  loadingSessions: false,
  loadingVoice: false,
};

describe('settings_shell_state', () => {
  test('settings section selection clears notice and flags lazy loads', () => {
    const stickers = settingsSectionSelected({
      section: SettingsSection.stickers,
      sessionsEmpty: true,
      loadingSessions: false,
    });

    expect(stickers.section).toBe(SettingsSection.stickers);
    expect(stickers.notice).toBeNull();
    expect(stickers.shouldLoadStickers).toBe(true);
    expect(stickers.shouldLoadSessions).toBe(false);
    expect(stickers.shouldInitializeVoice).toBe(false);

    const security = settingsSectionSelected({
      section: SettingsSection.security,
      sessionsEmpty: true,
      loadingSessions: false,
    });

    expect(security.shouldLoadStickers).toBe(false);
    expect(security.shouldLoadSessions).toBe(true);
    expect(security.shouldInitializeVoice).toBe(false);

    const loadingSecurity = settingsSectionSelected({
      section: SettingsSection.security,
      sessionsEmpty: true,
      loadingSessions: true,
    });

    expect(loadingSecurity.shouldLoadSessions).toBe(false);

    const voice = settingsSectionSelected({ section: SettingsSection.voice, sessionsEmpty: false, loadingSessions: false });

    expect(voice.shouldLoadStickers).toBe(false);
    expect(voice.shouldLoadSessions).toBe(false);
    expect(voice.shouldInitializeVoice).toBe(true);

    for (const section of [SettingsSection.preferences, SettingsSection.about]) {
      const patch = settingsSectionSelected({ section, sessionsEmpty: true, loadingSessions: false });
      expect(patch.shouldLoadStickers).toBe(false);
      expect(patch.shouldLoadSessions).toBe(false);
      expect(patch.shouldInitializeVoice).toBe(false);
    }
  });

  test('settings section titles are shared with shell UI', () => {
    expect(settingsSectionTitle(SettingsSection.profile)).toBe('用户资料');
    expect(settingsSectionTitle(SettingsSection.preferences)).toBe('偏好设置');
    expect(settingsSectionTitle(SettingsSection.security)).toBe('隐私和安全');
    expect(settingsSectionTitle(SettingsSection.voice)).toBe('语音和视频');
    expect(settingsSectionTitle(SettingsSection.stickers)).toBe('表情包管理');
    expect(settingsSectionTitle(SettingsSection.playlists)).toBe('我的歌单');
    expect(settingsSectionTitle(SettingsSection.about)).toBe('关于Gang Chat');
  });

  test('settings notice patch shows transient feedback text', () => {
    expect(settingsNoticeShown('Copied').notice).toBe('Copied');
  });

  test('settings section refreshing maps section to backing loads', () => {
    expect(settingsSectionRefreshing({ section: SettingsSection.playlists, ...idle, loadingPlaylists: true })).toBe(true);
    expect(settingsSectionRefreshing({ section: SettingsSection.profile, ...idle, loadingAccount: true })).toBe(true);
    expect(settingsSectionRefreshing({ section: SettingsSection.security, ...idle, loadingSessions: true })).toBe(true);
    expect(
      settingsSectionRefreshing({
        section: SettingsSection.preferences,
        loadingAccount: true,
        loadingPreferences: true,
        loadingStickers: true,
        loadingSessions: true,
        loadingVoice: true,
      }),
    ).toBe(true);
    expect(
      settingsSectionRefreshing({
        section: SettingsSection.voice,
        loadingAccount: true,
        loadingPreferences: true,
        loadingStickers: true,
        loadingSessions: true,
        loadingVoice: false,
      }),
    ).toBe(false);
    expect(settingsSectionRefreshing({ section: SettingsSection.about, ...idle, loadingAbout: true })).toBe(true);
  });
});

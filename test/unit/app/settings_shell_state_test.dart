import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/settings_shell_state.dart';

void main() {
  test('settings section selection clears notice and flags lazy loads', () {
    final stickers = settingsSectionSelected(
      section: SettingsSection.stickers,
      sessionsEmpty: true,
      loadingSessions: false,
    );

    expect(stickers.section, SettingsSection.stickers);
    expect(stickers.notice, isNull);
    expect(stickers.shouldLoadStickers, isTrue);
    expect(stickers.shouldLoadSessions, isFalse);
    expect(stickers.shouldInitializeVoice, isFalse);

    final security = settingsSectionSelected(
      section: SettingsSection.security,
      sessionsEmpty: true,
      loadingSessions: false,
    );

    expect(security.shouldLoadStickers, isFalse);
    expect(security.shouldLoadSessions, isTrue);
    expect(security.shouldInitializeVoice, isFalse);

    final loadingSecurity = settingsSectionSelected(
      section: SettingsSection.security,
      sessionsEmpty: true,
      loadingSessions: true,
    );

    expect(loadingSecurity.shouldLoadSessions, isFalse);

    final voice = settingsSectionSelected(
      section: SettingsSection.voice,
      sessionsEmpty: false,
      loadingSessions: false,
    );

    expect(voice.shouldLoadStickers, isFalse);
    expect(voice.shouldLoadSessions, isFalse);
    expect(voice.shouldInitializeVoice, isTrue);

    final preferences = settingsSectionSelected(
      section: SettingsSection.preferences,
      sessionsEmpty: true,
      loadingSessions: false,
    );

    expect(preferences.shouldLoadStickers, isFalse);
    expect(preferences.shouldLoadSessions, isFalse);
    expect(preferences.shouldInitializeVoice, isFalse);

    final about = settingsSectionSelected(
      section: SettingsSection.about,
      sessionsEmpty: true,
      loadingSessions: false,
    );

    expect(about.shouldLoadStickers, isFalse);
    expect(about.shouldLoadSessions, isFalse);
    expect(about.shouldInitializeVoice, isFalse);
  });

  test('settings section titles are shared with shell UI', () {
    expect(settingsSectionTitle(SettingsSection.profile), '用户资料');
    expect(settingsSectionTitle(SettingsSection.preferences), '偏好设置');
    expect(settingsSectionTitle(SettingsSection.security), '隐私和安全');
    expect(settingsSectionTitle(SettingsSection.voice), '语音和视频');
    expect(settingsSectionTitle(SettingsSection.stickers), '表情包管理');
    expect(settingsSectionTitle(SettingsSection.playlists), '我的歌单');
    expect(settingsSectionTitle(SettingsSection.about), '关于Gang Chat');
  });

  test('settings notice patch shows transient feedback text', () {
    expect(settingsNoticeShown('Copied').notice, 'Copied');
  });

  test('settings section refreshing maps section to backing loads', () {
    expect(
      settingsSectionRefreshing(
        section: SettingsSection.playlists,
        loadingAccount: false,
        loadingPreferences: false,
        loadingStickers: false,
        loadingSessions: false,
        loadingVoice: false,
        loadingPlaylists: true,
      ),
      isTrue,
    );
    expect(
      settingsSectionRefreshing(
        section: SettingsSection.profile,
        loadingAccount: true,
        loadingPreferences: false,
        loadingStickers: false,
        loadingSessions: false,
        loadingVoice: false,
      ),
      isTrue,
    );
    expect(
      settingsSectionRefreshing(
        section: SettingsSection.security,
        loadingAccount: false,
        loadingPreferences: false,
        loadingStickers: false,
        loadingSessions: true,
        loadingVoice: false,
      ),
      isTrue,
    );
    expect(
      settingsSectionRefreshing(
        section: SettingsSection.preferences,
        loadingAccount: true,
        loadingPreferences: true,
        loadingStickers: true,
        loadingSessions: true,
        loadingVoice: true,
      ),
      isTrue,
    );
    expect(
      settingsSectionRefreshing(
        section: SettingsSection.voice,
        loadingAccount: true,
        loadingPreferences: true,
        loadingStickers: true,
        loadingSessions: true,
        loadingVoice: false,
      ),
      isFalse,
    );
    expect(
      settingsSectionRefreshing(
        section: SettingsSection.about,
        loadingAccount: false,
        loadingPreferences: false,
        loadingStickers: false,
        loadingSessions: false,
        loadingVoice: false,
        loadingAbout: true,
      ),
      isTrue,
    );
  });
}

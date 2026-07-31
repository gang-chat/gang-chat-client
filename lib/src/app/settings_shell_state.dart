enum SettingsSection {
  profile,
  preferences,
  security,
  voice,
  stickers,
  playlists,
  about,
}

class SettingsSectionPatch {
  const SettingsSectionPatch({
    required this.section,
    required this.notice,
    required this.shouldLoadStickers,
    required this.shouldLoadSessions,
    required this.shouldInitializeVoice,
  });

  final SettingsSection section;
  final String? notice;
  final bool shouldLoadStickers;
  final bool shouldLoadSessions;
  final bool shouldInitializeVoice;
}

class SettingsNoticePatch {
  const SettingsNoticePatch({required this.notice});

  final String? notice;
}

SettingsSectionPatch settingsSectionSelected({
  required SettingsSection section,
  required bool sessionsEmpty,
  required bool loadingSessions,
}) {
  return SettingsSectionPatch(
    section: section,
    notice: null,
    shouldLoadStickers: section == SettingsSection.stickers,
    shouldLoadSessions:
        section == SettingsSection.security &&
        sessionsEmpty &&
        !loadingSessions,
    shouldInitializeVoice: section == SettingsSection.voice,
  );
}

String settingsSectionTitle(SettingsSection section) {
  return switch (section) {
    SettingsSection.profile => '用户资料',
    SettingsSection.preferences => '偏好设置',
    SettingsSection.stickers => '表情包管理',
    SettingsSection.playlists => '我的歌单',
    SettingsSection.security => '隐私和安全',
    SettingsSection.voice => '语音和视频',
    SettingsSection.about => '关于Gang Chat',
  };
}

SettingsNoticePatch settingsNoticeShown(String message) {
  return SettingsNoticePatch(notice: message);
}

bool settingsSectionRefreshing({
  required SettingsSection section,
  required bool loadingAccount,
  required bool loadingPreferences,
  required bool loadingStickers,
  required bool loadingSessions,
  required bool loadingVoice,
  bool loadingPlaylists = false,
  bool loadingAbout = false,
}) {
  return switch (section) {
    SettingsSection.profile => loadingAccount,
    SettingsSection.preferences => loadingPreferences,
    SettingsSection.stickers => loadingStickers,
    SettingsSection.playlists => loadingPlaylists,
    SettingsSection.security => loadingAccount || loadingSessions,
    SettingsSection.voice => loadingVoice,
    SettingsSection.about => loadingAbout,
  };
}

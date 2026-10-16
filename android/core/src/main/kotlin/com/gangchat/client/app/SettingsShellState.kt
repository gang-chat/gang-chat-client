package com.gangchat.client.app

enum class SettingsSection {
    profile,
    preferences,
    security,
    voice,
    stickers,
    playlists,
    about,
}

class SettingsSectionPatch(
    val section: SettingsSection,
    val notice: String?,
    val shouldLoadStickers: Boolean,
    val shouldLoadSessions: Boolean,
    val shouldInitializeVoice: Boolean,
)

class SettingsNoticePatch(val notice: String?)

fun settingsSectionSelected(
    section: SettingsSection,
    sessionsEmpty: Boolean,
    loadingSessions: Boolean,
): SettingsSectionPatch {
    return SettingsSectionPatch(
        section = section,
        notice = null,
        shouldLoadStickers = section == SettingsSection.stickers,
        shouldLoadSessions =
            section == SettingsSection.security &&
                sessionsEmpty &&
                !loadingSessions,
        shouldInitializeVoice = section == SettingsSection.voice,
    )
}

fun settingsSectionTitle(section: SettingsSection): String {
    return when (section) {
        SettingsSection.profile -> "用户资料"
        SettingsSection.preferences -> "偏好设置"
        SettingsSection.stickers -> "表情包管理"
        SettingsSection.playlists -> "我的歌单"
        SettingsSection.security -> "隐私和安全"
        SettingsSection.voice -> "语音和视频"
        SettingsSection.about -> "关于Gang Chat"
    }
}

fun settingsNoticeShown(message: String): SettingsNoticePatch {
    return SettingsNoticePatch(notice = message)
}

fun settingsSectionRefreshing(
    section: SettingsSection,
    loadingAccount: Boolean,
    loadingPreferences: Boolean,
    loadingStickers: Boolean,
    loadingSessions: Boolean,
    loadingVoice: Boolean,
    loadingPlaylists: Boolean = false,
    loadingAbout: Boolean = false,
): Boolean {
    return when (section) {
        SettingsSection.profile -> loadingAccount
        SettingsSection.preferences -> loadingPreferences
        SettingsSection.stickers -> loadingStickers
        SettingsSection.playlists -> loadingPlaylists
        SettingsSection.security -> loadingAccount || loadingSessions
        SettingsSection.voice -> loadingVoice
        SettingsSection.about -> loadingAbout
    }
}

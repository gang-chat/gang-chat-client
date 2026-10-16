package com.gangchat.client.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsShellStateTest {
    @Test
    fun `settings section selection clears notice and flags lazy loads`() {
        val stickers =
            settingsSectionSelected(
                section = SettingsSection.stickers,
                sessionsEmpty = true,
                loadingSessions = false,
            )

        assertEquals(SettingsSection.stickers, stickers.section)
        assertNull(stickers.notice)
        assertTrue(stickers.shouldLoadStickers)
        assertFalse(stickers.shouldLoadSessions)
        assertFalse(stickers.shouldInitializeVoice)

        val security =
            settingsSectionSelected(
                section = SettingsSection.security,
                sessionsEmpty = true,
                loadingSessions = false,
            )

        assertFalse(security.shouldLoadStickers)
        assertTrue(security.shouldLoadSessions)
        assertFalse(security.shouldInitializeVoice)

        val loadingSecurity =
            settingsSectionSelected(
                section = SettingsSection.security,
                sessionsEmpty = true,
                loadingSessions = true,
            )

        assertFalse(loadingSecurity.shouldLoadSessions)

        val voice =
            settingsSectionSelected(
                section = SettingsSection.voice,
                sessionsEmpty = false,
                loadingSessions = false,
            )

        assertFalse(voice.shouldLoadStickers)
        assertFalse(voice.shouldLoadSessions)
        assertTrue(voice.shouldInitializeVoice)

        val preferences =
            settingsSectionSelected(
                section = SettingsSection.preferences,
                sessionsEmpty = true,
                loadingSessions = false,
            )

        assertFalse(preferences.shouldLoadStickers)
        assertFalse(preferences.shouldLoadSessions)
        assertFalse(preferences.shouldInitializeVoice)

        val about =
            settingsSectionSelected(
                section = SettingsSection.about,
                sessionsEmpty = true,
                loadingSessions = false,
            )

        assertFalse(about.shouldLoadStickers)
        assertFalse(about.shouldLoadSessions)
        assertFalse(about.shouldInitializeVoice)
    }

    @Test
    fun `settings section titles are shared with shell UI`() {
        assertEquals("用户资料", settingsSectionTitle(SettingsSection.profile))
        assertEquals("偏好设置", settingsSectionTitle(SettingsSection.preferences))
        assertEquals("隐私和安全", settingsSectionTitle(SettingsSection.security))
        assertEquals("语音和视频", settingsSectionTitle(SettingsSection.voice))
        assertEquals("表情包管理", settingsSectionTitle(SettingsSection.stickers))
        assertEquals("我的歌单", settingsSectionTitle(SettingsSection.playlists))
        assertEquals("关于Gang Chat", settingsSectionTitle(SettingsSection.about))
    }

    @Test
    fun `settings notice patch shows transient feedback text`() {
        assertEquals("Copied", settingsNoticeShown("Copied").notice)
    }

    @Test
    fun `settings section refreshing maps section to backing loads`() {
        assertTrue(
            settingsSectionRefreshing(
                section = SettingsSection.playlists,
                loadingAccount = false,
                loadingPreferences = false,
                loadingStickers = false,
                loadingSessions = false,
                loadingVoice = false,
                loadingPlaylists = true,
            ),
        )
        assertTrue(
            settingsSectionRefreshing(
                section = SettingsSection.profile,
                loadingAccount = true,
                loadingPreferences = false,
                loadingStickers = false,
                loadingSessions = false,
                loadingVoice = false,
            ),
        )
        assertTrue(
            settingsSectionRefreshing(
                section = SettingsSection.security,
                loadingAccount = false,
                loadingPreferences = false,
                loadingStickers = false,
                loadingSessions = true,
                loadingVoice = false,
            ),
        )
        assertTrue(
            settingsSectionRefreshing(
                section = SettingsSection.preferences,
                loadingAccount = true,
                loadingPreferences = true,
                loadingStickers = true,
                loadingSessions = true,
                loadingVoice = true,
            ),
        )
        assertFalse(
            settingsSectionRefreshing(
                section = SettingsSection.voice,
                loadingAccount = true,
                loadingPreferences = true,
                loadingStickers = true,
                loadingSessions = true,
                loadingVoice = false,
            ),
        )
        assertTrue(
            settingsSectionRefreshing(
                section = SettingsSection.about,
                loadingAccount = false,
                loadingPreferences = false,
                loadingStickers = false,
                loadingSessions = false,
                loadingVoice = false,
                loadingAbout = true,
            ),
        )
    }
}

package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAboutTest {
    @Test
    fun `debug builds use fixed app version`() {
        assertEquals("1.0.0", gangChatClientVersion)
    }

    @Test
    fun `appVersionLabel normalizes display text`() {
        assertEquals("v1.2.3", appVersionLabel("1.2.3"))
        assertEquals("v2.0.0", appVersionLabel("v2.0.0"))
    }

    @Test
    fun `appVersionNumberLabel strips display prefix`() {
        assertEquals("1.2.3", appVersionNumberLabel("1.2.3"))
        assertEquals("2.0.0", appVersionNumberLabel("v2.0.0"))
    }

    @Test
    fun `compareAppVersions compares semantic version parts`() {
        assertEquals(0, compareAppVersions("1.2.3", "1.2.3"))
        assertTrue(compareAppVersions("1.2.4", "1.2.3") > 0)
        assertTrue(compareAppVersions("1.2.3", "1.3.0") < 0)
        assertEquals(0, compareAppVersions("v1.2.3+4", "1.2.3"))
    }

    @Test
    fun `updateCheckSucceededText describes current and newer versions`() {
        assertEquals(
            "当前已是最新版本",
            updateCheckSucceededText(currentVersion = "1.0.0", latestVersion = "1.0.0"),
        )
        assertEquals(
            "发现新版本 v1.1.0",
            updateCheckSucceededText(currentVersion = "1.0.0", latestVersion = "1.1.0"),
        )
    }

    @Test
    fun `feedback helpers use bound email and version`() {
        assertEquals("user@example.test", boundEmailForFeedback(" user@example.test "))
        assertNull(boundEmailForFeedback(""))
        assertTrue(feedbackMailSubject("1.0.0").contains("v1.0.0"))
        assertTrue(
            feedbackMailBody(
                senderEmail = "user@example.test",
                currentVersion = "1.0.0",
            ).contains("发件人（绑定邮箱）：user@example.test"),
        )
    }

    @Test
    fun `official release time converts UTC across the Beijing date boundary`() {
        assertEquals(
            "2026/07/13 04:34 UTC+08:00",
            officialDateTimeLabel(DateTime.utc(2026, 7, 12, 20, 34)),
        )
        assertEquals(
            "2026/07/13 04:34 UTC+08:00",
            officialVersionTimeLabel("2026-07-12T20:34:27.126Z"),
        )
    }

    @Test
    fun `install time stays local and supports legacy date files`() {
        assertEquals("2026/07/01", installTimeLabel("2026/07/01"))
        assertEquals("2026/07/01 12:34", installTimeLabel("2026-07-01T12:34:56"))
    }
}

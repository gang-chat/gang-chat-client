package com.gangchat.client.shell

import com.gangchat.client.plugins.SharedPreferences
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalPreferencesMigrationTest {
    @get:Rule
    val gang = GangTestRule()

    @Before
    fun setUp() {
        SharedPreferences.setMockInitialValues(
            mapOf(
                "gang.audioInputVolume" to 0.8,
            ),
        )
    }

    @Test
    fun `migrates every supported gang preference without overwriting`() = gang.runTest {
        val preferences = SharedPreferences.getInstance()

        val count =
            LocalPreferencesMigration.migrateLegacyPreferences(
                preferences = preferences,
                legacyValues =
                    mapOf(
                        "flutter.gang.audioInputVolume" to 0.25,
                        "flutter.gang.audioOutputVolume" to 0.65,
                        "flutter.gang.closeBehavior" to "minimize_to_tray",
                        "flutter.gang.autoUpdatePrompt" to false,
                        "flutter.gang.futureStringList" to listOf("one", "two"),
                        "flutter.gang.refreshToken" to "must-not-migrate",
                        "flutter.gang.loginAccountPassword.alice" to "must-not-migrate",
                        "flutter.unrelated.preference" to "ignored",
                        "flutter.gang.unsupported" to mapOf<String, Any?>("value" to 1),
                    ),
            )

        assertEquals(4, count)
        assertEquals(0.8, preferences.getDouble("gang.audioInputVolume"))
        assertEquals(0.65, preferences.getDouble("gang.audioOutputVolume"))
        assertEquals("minimize_to_tray", preferences.getString("gang.closeBehavior"))
        assertEquals(false, preferences.getBool("gang.autoUpdatePrompt"))
        assertEquals(
            listOf(
                "one",
                "two",
            ),
            preferences.getStringList("gang.futureStringList"),
        )
        assertFalse(preferences.containsKey("unrelated.preference"))
        assertFalse(preferences.containsKey("gang.unsupported"))
        assertFalse(preferences.containsKey("gang.refreshToken"))
        assertFalse(
            preferences.containsKey("gang.loginAccountPassword.alice"),
        )
    }

    @Test
    fun `recovers defaulted volumes but preserves later custom values`() = gang.runTest {
        SharedPreferences.setMockInitialValues(
            mapOf(
                "gang.audioInputVolume" to 0.5,
                "gang.audioOutputVolume" to 0.35,
                "gang.musicBoxVolume" to 0.5,
            ),
        )
        val preferences = SharedPreferences.getInstance()

        val count =
            LocalPreferencesMigration.migrateLegacyPreferences(
                preferences = preferences,
                legacyValues =
                    mapOf(
                        "flutter.gang.audioInputVolume" to 0.9,
                        "flutter.gang.audioOutputVolume" to 0.8,
                        "flutter.gang.musicBoxVolume" to 0.65,
                    ),
            )

        assertEquals(2, count)
        assertEquals(0.9, preferences.getDouble("gang.audioInputVolume"))
        assertEquals(0.35, preferences.getDouble("gang.audioOutputVolume"))
        assertEquals(0.65, preferences.getDouble("gang.musicBoxVolume"))
    }
}

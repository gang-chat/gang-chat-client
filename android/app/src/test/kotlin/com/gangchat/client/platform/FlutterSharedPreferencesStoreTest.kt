package com.gangchat.client.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gangchat.client.plugins.SharedPreferences
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The store must read what shared_preferences_android 2.4.26 (legacy API)
 * wrote and write the same format. Fixtures are built exactly as the plugin
 * stored values in `FlutterSharedPreferences`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlutterSharedPreferencesStoreTest {
    @get:Rule
    val gang = GangTestRule()

    private lateinit var context: Context
    private lateinit var raw: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        raw = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        raw.edit().clear().commit()
    }

    private suspend fun preferences(): SharedPreferences {
        SharedPreferences.installStore(FlutterSharedPreferencesStore(context))
        return SharedPreferences.getInstance()
    }

    @Test
    fun `reads every value type the Flutter plugin wrote`() =
        gang.runTest {
            raw.edit()
                .putString("flutter.gang.apiBaseUrl", "https://chat.example/api/v1")
                .putBoolean("flutter.gang.autoUpdate", true)
                .putLong("flutter.gang.outputVolume", 7L)
                .putString("flutter.gang.inputGain", "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu0.85")
                .putString(
                    "flutter.gang.devices",
                    "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!" + """["麦克风 A","speaker \"B\""]""",
                )
                .putString("unrelated", "not a Flutter key")
                .commit()

            val prefs = preferences()

            assertEquals(setOf("gang.apiBaseUrl", "gang.autoUpdate", "gang.outputVolume", "gang.inputGain", "gang.devices"), prefs.getKeys())
            assertEquals("https://chat.example/api/v1", prefs.getString("gang.apiBaseUrl"))
            assertEquals(true, prefs.getBool("gang.autoUpdate"))
            assertEquals(7, prefs.getInt("gang.outputVolume"))
            assertEquals(7L, prefs.getLong("gang.outputVolume"))
            assertEquals(0.85, prefs.getDouble("gang.inputGain")!!, 0.0)
            assertEquals(listOf("麦克风 A", "speaker \"B\""), prefs.getStringList("gang.devices"))
        }

    @Test
    fun `reads legacy base64 serialized string lists`() =
        gang.runTest {
            raw.edit()
                .putString("flutter.history", FlutterSharedPreferencesStore.encodeLegacyList(listOf("alice", "鲍勃")))
                .commit()
            // The plugin wrote `LIST_PREFIX + Base64.encodeToString(bytes, 0)`, with line breaks.
            assertTrue(raw.getString("flutter.history", null)!!.startsWith("VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"))

            assertEquals(listOf("alice", "鲍勃"), preferences().getStringList("history"))
        }

    @Test
    fun `reads ints stored as Integer, legacy BigIntegers and migrates string sets`() =
        gang.runTest {
            raw.edit()
                .putInt("flutter.small", 42)
                .putString("flutter.big", "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy" + java.math.BigInteger.valueOf(1234567890123L).toString(36))
                .putStringSet("flutter.set", setOf("only"))
                .commit()

            val prefs = preferences()

            assertEquals(42L, prefs.getLong("small"))
            assertEquals(1234567890123L, prefs.getLong("big"))
            assertEquals(listOf("only"), prefs.getStringList("set"))
            assertEquals(
                "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu![\"only\"]",
                raw.getString("flutter.set", null),
            )
        }

    @Test
    fun `writes the Flutter plugin's on-disk format`() =
        gang.runTest {
            val prefs = preferences()

            assertTrue(prefs.setString("name", "Gang"))
            assertTrue(prefs.setBool("enabled", false))
            assertTrue(prefs.setInt("count", 3))
            assertTrue(prefs.setLong("stamp", 1_700_000_000_000L))
            assertTrue(prefs.setDouble("gain", 1.5))
            assertTrue(prefs.setStringList("tags", listOf("a", "中文", "x\"y")))

            assertEquals("Gang", raw.getString("flutter.name", null))
            assertEquals(false, raw.getBoolean("flutter.enabled", true))
            assertEquals(3L, raw.getLong("flutter.count", -1))
            assertEquals(1_700_000_000_000L, raw.getLong("flutter.stamp", -1))
            assertEquals("VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu1.5", raw.getString("flutter.gain", null))
            assertEquals(
                "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu![\"a\",\"中文\",\"x\\\"y\"]",
                raw.getString("flutter.tags", null),
            )

            // A fresh load reads everything back.
            prefs.reload()
            assertEquals("Gang", prefs.getString("name"))
            assertEquals(false, prefs.getBool("enabled"))
            assertEquals(3, prefs.getInt("count"))
            assertEquals(1_700_000_000_000L, prefs.getLong("stamp"))
            assertEquals(1.5, prefs.getDouble("gain")!!, 0.0)
            assertEquals(listOf("a", "中文", "x\"y"), prefs.getStringList("tags"))
        }

    @Test
    fun `remove and clear only touch Flutter keys`() =
        gang.runTest {
            raw.edit().putString("flutter.a", "1").putString("flutter.b", "2").putString("native.c", "3").commit()
            val prefs = preferences()

            assertTrue(prefs.remove("a"))
            assertFalse(raw.contains("flutter.a"))
            assertTrue(prefs.clear())

            assertFalse(raw.contains("flutter.b"))
            assertEquals("3", raw.getString("native.c", null))
            assertNull(prefs.getString("b"))
        }

    @Test
    fun `strings that clash with the type prefixes are rejected like the plugin`() =
        gang.runTest {
            val prefs = preferences()
            try {
                prefs.setString("x", "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu1.0")
                fail("expected the plugin's StorageError")
            } catch (error: PlatformException) {
                assertTrue(error.message!!.startsWith("StorageError"))
            }
            assertFalse(raw.contains("flutter.x"))
        }
}

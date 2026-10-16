package com.gangchat.client

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Looper
import android.net.Uri as AndroidUri
import com.gangchat.client.foundation.http.OkHttpClientAdapter
import com.gangchat.client.platform.AndroidAudioRoute
import com.gangchat.client.platform.GangChatAppVisibility
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.shell.AndroidSystemService
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.pumpEventQueue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MainActivityTest {
    @get:Rule
    val gang = GangTestRule()

    @Before
    fun setUp() {
        OkHttpClientAdapter.directHosts = emptySet()
    }

    private fun TestScope.runUntil(condition: () -> Boolean) {
        repeat(500) {
            pumpEventQueue()
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("startup did not finish")
    }

    @Test
    fun `startup applies the orientation default and the configured proxy bypass`() =
        gang.runTest {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            runUntil { OkHttpClientAdapter.directHosts.isNotEmpty() }

            assertSame(activity, GangChatPlatform.activity)
            assertEquals(setOf("ky-z.com", "os.ky-z.com"), OkHttpClientAdapter.directHosts)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
        }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun `tablets rotate freely after startup`() =
        gang.runTest {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            runUntil { OkHttpClientAdapter.directHosts.isNotEmpty() }

            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
        }

    @Test
    fun `a notification launch opens its room once and later taps are events`() =
        gang.runTest {
            val launch = Intent().putExtra(MainActivity.notificationRoomIdExtra, "room-7")
            val controller = Robolectric.buildActivity(MainActivity::class.java, launch).setup()
            val android = AndroidSystemService()

            assertEquals("room-7", android.takeInitialNotificationRoomId())
            assertNull(android.takeInitialNotificationRoomId())
            assertFalse(controller.get().intent.hasExtra(MainActivity.notificationRoomIdExtra))

            val selected = mutableListOf<String>()
            val subscription = android.selectedNotificationRoomIds.listen({ selected.add(it) })
            controller.newIntent(Intent().putExtra(MainActivity.notificationRoomIdExtra, "room-8"))
            pumpEventQueue()

            assertEquals(listOf("room-8"), selected)
            subscription.cancel()
        }

    @Test
    fun `activity results reach the platform services`() =
        gang.runTest {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            val android = AndroidSystemService()

            val pending = async { android.createDocument(suggestedName = "photo.png", mimeType = "image/png") }
            pumpEventQueue()
            val request = shadowOf(activity).nextStartedActivityForResult
            shadowOf(activity).receiveResult(
                request.intent,
                Activity.RESULT_OK,
                Intent().setData(AndroidUri.parse("content://documents/photo")),
            )

            assertEquals("content://documents/photo", pending.await()?.uri)
        }

    @Test
    fun `visibility follows the activity's started state`() =
        gang.runTest {
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            assertTrue(GangChatAppVisibility.isForeground)

            controller.pause().stop()
            assertFalse(GangChatAppVisibility.isForeground)

            controller.destroy()
            assertNull(GangChatPlatform.activity)
        }

    @Test
    fun `the music box audio route binds the volume keys and audio mode`() =
        gang.runTest {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            val audioManager = activity.getSystemService(AudioManager::class.java)

            AndroidAudioRoute.setMusicBoxActive(active = true, liveSessionActive = true)
            assertEquals(AudioManager.STREAM_MUSIC, activity.volumeControlStream)
            assertEquals(AudioManager.MODE_NORMAL, audioManager.mode)

            AndroidAudioRoute.setMusicBoxActive(active = false, liveSessionActive = true)
            assertEquals(AudioManager.USE_DEFAULT_STREAM_TYPE, activity.volumeControlStream)
            assertEquals(AudioManager.MODE_IN_COMMUNICATION, audioManager.mode)

            AndroidAudioRoute.setMusicBoxActive(active = false, liveSessionActive = false)
            assertEquals(AudioManager.MODE_NORMAL, audioManager.mode)
        }
}

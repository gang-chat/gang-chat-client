package com.gangchat.client.shell

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.net.Uri as AndroidUri
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.gangchat.client.MainActivity
import com.gangchat.client.foundation.Uri
import com.gangchat.client.platform.AndroidSystemBridge
import com.gangchat.client.platform.GangChatNotifications
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.platform.GangChatPushRegistrationProvider
import com.gangchat.client.platform.PlatformException
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.pumpEventQueue
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import org.robolectric.shadows.ShadowTextToSpeech

/**
 * Port of test/unit/shell/android_system_service_test.dart. The Dart test
 * mocked the `gang_chat/android_system` channel; here the same calls run
 * through the real [AndroidSystemBridge] against Robolectric's Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSystemServiceTest {
    @get:Rule
    val gang = GangTestRule()

    private lateinit var application: Application
    private lateinit var activity: ComponentActivity
    private val android = AndroidSystemService()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        GangChatPlatform.attachActivity(activity)
    }

    @After
    fun tearDown() {
        GangChatPlatform.detachActivity(activity)
        GangChatPushRegistrationProvider.tokenSourceOverride = null
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `Android shell launchers use native intents`() =
        gang.runTest {
            ExternalUriLauncher(androidSystemService = android).open(Uri.parse("https://ky-z.com"))
            FeedbackMailService(androidSystemService = android).openMailto(Uri.parse("mailto:gang-chat@outlook.com"))

            // Robolectric hands back the most recent start first.
            val mail = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_SENDTO, mail.action)
            assertEquals("mailto:gang-chat@outlook.com", mail.dataString)
            val view = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, view.action)
            assertEquals("https://ky-z.com", view.dataString)
        }

    @Test
    fun `launchers reject schemes outside their allow list`() =
        gang.runTest {
            try {
                android.openUri(Uri.parse("mailto:someone@example.com"))
                fail("expected invalid_uri")
            } catch (error: PlatformException) {
                assertEquals("invalid_uri", error.code)
            }
            try {
                android.openMailto(Uri.parse("https://ky-z.com"))
                fail("expected invalid_uri")
            } catch (error: PlatformException) {
                assertEquals("invalid_uri", error.code)
            }
            assertNull(shadowOf(activity).nextStartedActivity)
        }

    @Test
    fun `Android install time comes from PackageManager bridge`() =
        gang.runTest {
            shadowOf(application.packageManager)
                .getInternalMutablePackageInfo(application.packageName)
                .lastUpdateTime = Instant.parse("2026-07-25T04:05:06Z").toEpochMilli()

            assertEquals("2026-07-25T04:05:06.000Z", android.installedAt())
        }

    @Test
    fun `Android save location keeps URI separate from staging path`() =
        gang.runTest {
            val service = FileSelectionService(androidSystemService = android)
            val pending = async { service.getSaveLocation(suggestedName = "report.zip") }
            pumpEventQueue()

            val started = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
            assertEquals("application/zip", started.intent.type)
            assertEquals("report.zip", started.intent.getStringExtra(Intent.EXTRA_TITLE))
            assertEquals(4201, started.requestCode)
            val documentUri = AndroidUri.parse("content://documents/report")
            assertTrue(
                AndroidSystemBridge.instance.onActivityResult(4201, Activity.RESULT_OK, Intent().setData(documentUri)),
            )

            val location = pending.await()!!
            val stagingRoot = File(application.cacheDir, "document-staging").absolutePath + File.separator
            assertTrue(location.path, location.path.startsWith(stagingRoot) && location.path.endsWith("-report.zip"))
            assertEquals("content://documents/report", location.documentUri)
            assertTrue(location.requiresCommit)

            val written = ByteArrayOutputStream()
            shadowOf(application.contentResolver).registerOutputStream(documentUri, written)
            service.saveBytesToLocation(bytes = byteArrayOf(1, 2, 3), location = location, filename = "report.zip")
            assertArrayEquals(byteArrayOf(1, 2, 3), written.toByteArray())

            // The staged-file path: write privately, then commit into the document.
            File(location.path).writeBytes(byteArrayOf(4, 5))
            service.commitLocation(location)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), written.toByteArray())
            assertFalse(File(location.path).exists())

            service.discardLocation(location)
            try {
                android.discardDocumentFile(File(application.filesDir, "elsewhere.zip").path)
                fail("expected invalid_staging_file")
            } catch (error: PlatformException) {
                assertEquals("invalid_staging_file", error.code)
            }
        }

    @Test
    fun `a cancelled save dialog yields no location`() =
        gang.runTest {
            val pending = async { FileSelectionService(androidSystemService = android).getSaveLocation("a.png") }
            pumpEventQueue()
            assertEquals("image/png", shadowOf(activity).nextStartedActivityForResult.intent.type)
            AndroidSystemBridge.instance.onActivityResult(4201, Activity.RESULT_CANCELED, null)
            assertNull(pending.await())
        }

    @Test
    fun `notification bridge carries room content and unread badge`() =
        gang.runTest {
            shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

            android.showRoomMessage(
                roomId = "room-1",
                roomName = "测试房间",
                sender = "Alice",
                body = "你好",
                unreadCount = 7,
                messageId = "message-1",
            )
            android.syncBadge(7)

            val notifications = shadowOf(application.getSystemService(NotificationManager::class.java))
            val roomNotificationId = 20_000 + ("room-1".hashCode() and Int.MAX_VALUE) % 100_000
            val message = notifications.getNotification(roomNotificationId)
            assertNotNull(message)
            assertEquals("gang_chat_messages", message.channelId)
            assertEquals("测试房间", message.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("Alice：你好", message.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals(7, message.number)
            val tap = shadowOf(message.contentIntent)
            assertEquals("room-1", tap.savedIntent.getStringExtra(MainActivity.notificationRoomIdExtra))
            assertEquals("room-1".hashCode() and Int.MAX_VALUE, tap.requestCode)

            val summary = notifications.getNotification(1002)
            assertEquals("7 条未读消息", summary.extras.getCharSequence(Notification.EXTRA_TEXT).toString())

            // The same message id is never notified twice.
            android.cancelRoomNotification("room-1")
            android.showRoomMessage("room-1", "测试房间", "Alice", "你好", 7, "message-1")
            assertNull(notifications.getNotification(roomNotificationId))

            android.syncBadge(0)
            assertNull(notifications.getNotification(1002))
        }

    @Test
    fun `the in-app notification switch is stored where the Flutter host kept it`() =
        gang.runTest {
            shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            assertTrue(android.notificationPreferenceEnabled())
            assertTrue(android.notificationsEnabled())

            assertFalse(android.setNotificationPreferenceEnabled(false))

            val stored =
                application.getSharedPreferences("gang_chat_android_preferences", Context.MODE_PRIVATE)
                    .getBoolean("notifications_enabled", true)
            assertFalse(stored)
            assertFalse(android.notificationsEnabled())
            android.showRoomMessage("room-3", "房间", "Bob", "hi", 1)
            assertTrue(shadowOf(application.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
        }

    @Test
    fun `Android audio routing permission is requested through the bridge`() =
        gang.runTest {
            val pending = async { android.requestBluetoothConnectPermission() }
            pumpEventQueue()
            val request = shadowOf(activity).lastRequestedPermission
            assertEquals(4203, request.requestCode)
            assertArrayEquals(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), request.requestedPermissions)

            shadowOf(application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
            AndroidSystemBridge.instance.onRequestPermissionsResult(4203)

            assertTrue(pending.await())
            assertTrue(android.requestBluetoothConnectPermission())
        }

    @Test
    fun `Android update notifications and background state use native bridge`() =
        gang.runTest {
            shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

            assertTrue(android.requestUpdateNotificationPermission())
            android.setUpdateDownloadActive(true)
            assertTrue(GangChatNotifications.isUpdateDownloadActive())
            android.setUpdateDownloadActive(false)
            assertFalse(GangChatNotifications.isUpdateDownloadActive())
        }

    @Test
    fun `a denied notification permission prompt reports false`() =
        gang.runTest {
            shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            val pending = async { android.requestNotificationPermission() }
            pumpEventQueue()
            assertEquals(4202, shadowOf(activity).lastRequestedPermission.requestCode)

            AndroidSystemBridge.instance.onRequestPermissionsResult(4202)

            assertFalse(pending.await())
        }

    @Test
    fun `Android push registration is parsed from the native bridge`() =
        gang.runTest {
            application.getSharedPreferences("gang_chat_android_preferences", Context.MODE_PRIVATE)
                .edit().putString("push_installation_id", "install-1").commit()
            GangChatPushRegistrationProvider.tokenSourceOverride = { "token-1" }

            val registration = android.pushRegistration()

            assertEquals("fcm", registration?.provider)
            assertEquals("install-1", registration?.installationId)
            assertEquals("token-1", registration?.token)
            assertEquals(true, registration?.enabled)
        }

    @Test
    fun `without Firebase there is no push registration`() =
        gang.runTest {
            assertNull(android.pushRegistration())
        }

    @Test
    fun `refreshed push tokens reach pushRegistrationChanges`() =
        gang.runTest {
            val changes = mutableListOf<AndroidPushRegistration>()
            val subscription = android.pushRegistrationChanges.listen({ changes.add(it) })

            GangChatPushRegistrationProvider.onTokenRefreshed(application, " token-2 ")
            idleMainLooper()
            pumpEventQueue()

            assertEquals("token-2", changes.single().token)
            assertEquals("fcm", changes.single().provider)
            subscription.cancel()
        }

    @Test
    fun `task removal callback waits for graceful cleanup completion`() =
        gang.runTest {
            val requests = mutableListOf<AndroidTaskRemovalRequest>()
            val subscription = android.taskRemovalRequests.listen({ requests.add(it) })
            var nativeReplyReceived = false

            assertTrue(AndroidSystemBridge.notifyTaskRemoved { nativeReplyReceived = true })
            idleMainLooper()

            assertEquals(1, requests.size)
            assertFalse(nativeReplyReceived)

            requests.single().complete()
            assertTrue(nativeReplyReceived)
            subscription.cancel()

            // Nobody listening: the service may stop right away.
            var stoppedImmediately = false
            AndroidSystemBridge.notifyTaskRemoved { stoppedImmediately = true }
            idleMainLooper()
            assertTrue(stoppedImmediately)
        }

    @Test
    fun `notification taps open rooms`() =
        gang.runTest {
            val launch = Intent().putExtra(MainActivity.notificationRoomIdExtra, " room-9 ")
            val launched = Robolectric.buildActivity(ComponentActivity::class.java, launch).setup().get()
            AndroidSystemBridge.instance.onActivityCreated(launched)
            assertFalse(launched.intent.hasExtra(MainActivity.notificationRoomIdExtra))

            assertEquals("room-9", android.takeInitialNotificationRoomId())
            assertNull(android.takeInitialNotificationRoomId())

            val selected = mutableListOf<String>()
            val subscription = android.selectedNotificationRoomIds.listen({ selected.add(it) })
            AndroidSystemBridge.instance.handleIntent(Intent().putExtra(MainActivity.notificationRoomIdExtra, "room-2"))
            AndroidSystemBridge.instance.handleIntent(Intent().putExtra(MainActivity.notificationRoomIdExtra, "  "))
            pumpEventQueue()

            assertEquals(listOf("room-2"), selected)
            subscription.cancel()
        }

    @Test
    fun `speech speaks the cleaned segments until it is stopped`() =
        gang.runTest {
            val speaking = async { android.speak(listOf(" 你好 ", "", "世界"), volume = 2.0, pause = 280.milliseconds) }
            pumpEventQueue()
            val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
            shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
            idleMainLooper()
            pumpEventQueue()

            assertEquals(listOf("你好", "世界"), shadowOf(tts).spokenTextList)
            assertEquals(1f, ShadowTextToSpeech.getLastParams()!!.getFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME))
            assertFalse(speaking.isCompleted)

            android.disposeSpeech()
            pumpEventQueue()

            assertTrue(speaking.isCompleted)
            assertTrue(shadowOf(tts).isShutdown)
        }

    @Test
    fun `background mode runs the foreground service`() =
        gang.runTest {
            android.enterBackground()
            val started = shadowOf(application).nextStartedService
            assertEquals(
                "com.gangchat.client.platform.GangChatBackgroundService",
                started.component?.className,
            )

            android.enterForeground()
            assertEquals(
                "com.gangchat.client.platform.GangChatBackgroundService",
                shadowOf(application).nextStoppedService.component?.className,
            )
        }
}

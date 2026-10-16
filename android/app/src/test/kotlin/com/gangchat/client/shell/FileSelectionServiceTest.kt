package com.gangchat.client.shell

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.gangchat.client.platform.FileSelector
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.platform.PlatformException
import com.gangchat.client.platform.resetFileProviderPathCache
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.pumpEventQueue
import java.io.File
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

/** The Android system picker path of FileSelectionService (file_selector_android behaviour). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileSelectionServiceTest {
    @get:Rule
    val gang = GangTestRule()

    private lateinit var application: Application
    private lateinit var activity: ComponentActivity

    @Before
    fun setUp() {
        resetFileProviderPathCache()
        application = ApplicationProvider.getApplicationContext()
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        GangChatPlatform.attachActivity(activity)
    }

    @After
    fun tearDown() {
        GangChatPlatform.detachActivity(activity)
    }

    /** A document served by the app's FileProvider stands in for a picked document. */
    private fun document(name: String, bytes: ByteArray): android.net.Uri {
        val file = File(File(application.cacheDir, "clipboard-images").apply { mkdirs() }, name)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
    }

    @Test
    fun `openFile filters by type and reads the picked document`() =
        gang.runTest {
            val service = FileSelectionService()
            val uri = document("photo.jpeg", byteArrayOf(1, 2, 3, 4))

            val pending = async { service.openFile(listOf(FileTypeGroup(label = "图片", extensions = listOf(".jpg")))) }
            pumpEventQueue()
            val request = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
            assertEquals("image/jpeg", request.intent.type)
            assertEquals(FileSelector.openFileRequestCode, request.requestCode)
            FileSelector.onActivityResult(request.requestCode, Activity.RESULT_OK, Intent().setData(uri))

            val file = pending.await()!!
            // Like file_selector's XFile: the private copy's name, extension from the MIME type.
            assertEquals("photo.jpg", file.name)
            assertEquals("image/jpeg", file.mimeType)
            assertEquals(4L, file.length())
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), file.readAsBytes())
        }

    @Test
    fun `openFiles returns every picked document and nothing when cancelled`() =
        gang.runTest {
            val service = FileSelectionService()
            val first = document("a.png", byteArrayOf(1))
            val second = document("b.png", byteArrayOf(2, 2))

            val pending = async { service.openFiles() }
            pumpEventQueue()
            val request = shadowOf(activity).nextStartedActivityForResult
            assertEquals("*/*", request.intent.type)
            assertTrue(request.intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            val clip = ClipData.newRawUri("", first).apply { addItem(ClipData.Item(second)) }
            FileSelector.onActivityResult(request.requestCode, Activity.RESULT_OK, Intent().apply { clipData = clip })

            val files = pending.await()
            assertEquals(listOf("a.png", "b.png"), files.map { it.name })
            assertEquals(listOf(1L, 2L), files.map { it.length() })

            val cancelled = async { service.openFiles() }
            pumpEventQueue()
            FileSelector.onActivityResult(shadowOf(activity).nextStartedActivityForResult.requestCode, Activity.RESULT_CANCELED, null)
            assertEquals(emptyList<SelectedFile>(), cancelled.await())

            val single = async { service.openFile() }
            pumpEventQueue()
            FileSelector.onActivityResult(shadowOf(activity).nextStartedActivityForResult.requestCode, Activity.RESULT_CANCELED, null)
            assertNull(single.await())
        }

    @Test
    fun `without an activity the picker reports the plugin's error`() =
        gang.runTest {
            GangChatPlatform.detachActivity(activity)
            try {
                FileSelectionService().openFile()
                fail("expected an error")
            } catch (error: PlatformException) {
                assertEquals("Exception", error.code)
                assertEquals("java.lang.Exception: No activity is available.", error.message)
            }
        }
}

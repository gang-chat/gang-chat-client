package com.gangchat.client.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gangchat.client.platform.resetFileProviderPathCache
import com.gangchat.client.testing.GangTestRule
import java.io.File
import org.junit.Before
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Port of test/unit/shell/clipboard_service_test.dart. The Dart
 * "readImageFile parses native image payload" case ran only on Windows
 * (the `gang_chat/clipboard` channel); its Android counterpart is the image
 * round trip through the system clipboard below.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardServiceTest {
    @get:Rule
    val gang = GangTestRule()

    @Before
    fun setUp() {
        resetFileProviderPathCache()
    }

    @Test
    fun `SelectedFile fromBytes keeps clipboard image bytes uploadable`() =
        gang.runTest {
            val source = byteArrayOf(1, 2, 3)
            val file = SelectedFile.fromBytes(name = "clipboard-image.png", mimeType = "image/png", bytes = source)

            source[0] = 9
            val firstRead = file.readAsBytes()
            firstRead[1] = 8

            assertEquals("clipboard-image.png", file.name)
            assertEquals("image/png", file.mimeType)
            assertEquals(3L, file.length())
            assertArrayEquals(byteArrayOf(1, 2, 3), file.readAsBytes())
        }

    @Test
    fun `images round trip through the Android clipboard`() =
        gang.runTest {
            val service = ClipboardService()
            val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)

            assertTrue(service.writeImage(png, mimeType = "image/png"))

            val context = ApplicationProvider.getApplicationContext<Context>()
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!
            assertEquals("Gang Chat 图片", clip.description.label)
            assertEquals("content", clip.getItemAt(0).uri.scheme)
            assertEquals("com.gangchat.client.fileprovider", clip.getItemAt(0).uri.authority)
            assertTrue(File(context.cacheDir, "clipboard-images").listFiles()!!.single().name.endsWith(".png"))

            val image = service.readImageFile()
            assertNotNull(image)
            assertEquals("clipboard-image.png", image!!.filename)
            assertEquals("image/png", image.mimeType)
            assertArrayEquals(png, image.bytes)
        }

    @Test
    fun `empty images are not written and text clips are not images`() =
        gang.runTest {
            val service = ClipboardService()
            assertEquals(false, service.writeImage(ByteArray(0)))

            service.writeText("你好")
            assertNull(service.readImageFile())
            assertEquals("你好", service.readText())
        }

    @Test
    fun `text reads back and an empty clipboard reads null`() =
        gang.runTest {
            val service = ClipboardService()
            assertNull(service.readText())

            service.writeText("hello")
            assertEquals("hello", service.readText())

            val context = ApplicationProvider.getApplicationContext<Context>()
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("label", ""))
            assertNull(service.readText())
            assertEquals(emptyList<String>(), service.readFilePaths())
            assertEquals(false, service.writeFilePaths(listOf("/a")))
        }
}

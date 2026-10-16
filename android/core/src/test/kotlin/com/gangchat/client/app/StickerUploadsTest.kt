package com.gangchat.client.app

import com.gangchat.client.foundation.base64Decode
import com.gangchat.client.foundation.utf8Encode
import com.gangchat.client.testing.GangTestRule
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StickerUploadsTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `stickerUploadItemsFromFiles extracts valid images from zip files`() = gang.runTest {
        val zipBytes =
            _zip(
                "stickers/hello.png" to _pngBytes,
                "__MACOSX/ignored.png" to _pngBytes,
                "notes.txt" to utf8Encode("notes"),
            )

        val temp = File(gang.tempDir, "sticker_uploads_").also { it.mkdirs() }
        val zipFile = File(temp, "pack.zip")
        zipFile.writeBytes(zipBytes)

        val items =
            stickerUploadItemsFromFiles(
                listOf(
                    StickerUploadSource(
                        filename = "pack.zip",
                        readBytes = { zipFile.readBytes() },
                    ),
                ),
                decodeImageDimensions = { _ ->
                    StickerImageDimensions(width = 1, height = 1)
                },
            )

        assertEquals(1, items.size)
        assertEquals("hello.png", items.single().filename)
        assertArrayEquals(_pngBytes, items.single().bytes)
    }

    @Test
    fun `sticker upload helpers produce stable names for UI callers`() {
        assertEquals("hello-world", stickerNameFromFilename("hello-world.webp"))
        assertEquals("sticker", stickerNameFromFilename(".png"))

        val filename = stickerUploadFilename("hello world.WEBP", 3)
        assertTrue(filename.startsWith("hello-world-"))
        assertTrue(filename.endsWith("-3.webp"))
    }

    @Test
    fun `sticker upload error copy stays outside UI`() {
        assertEquals("表情包需要登录后从服务端读取", stickerPackRequiresServerMessage())
        assertEquals("无法打开文件选择器", stickerPickerOpenFailureMessage("denied"))
        assertEquals("没有找到可上传的图片", stickerNoUploadableImagesMessage())
    }
}

private val _pngBytes =
    base64Decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=",
    )

/** package:archive's `ZipEncoder().encode(archive)` (deflated entries). */
private fun _zip(vararg files: Pair<String, ByteArray>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        for ((name, content) in files) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

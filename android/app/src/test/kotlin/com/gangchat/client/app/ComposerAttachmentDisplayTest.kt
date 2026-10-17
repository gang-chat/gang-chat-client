package com.gangchat.client.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of test/unit/app/composer_attachment_display_test.dart. */
class ComposerAttachmentDisplayTest {
    @Test
    fun `size label is null when the picker reports no size`() {
        val view =
            ComposerAttachmentView(
                id = "a",
                filename = "a.txt",
                status = ComposerAttachmentStatus.uploaded,
            )
        assertNull(view.sizeLabel)
    }

    @Test
    fun `size label formats a known size`() {
        val view =
            ComposerAttachmentView(
                id = "a",
                filename = "a.txt",
                status = ComposerAttachmentStatus.uploaded,
                sizeBytes = 2048,
            )
        assertEquals("2.0 KB", view.sizeLabel)
    }

    @Test
    fun `status flags follow the upload lifecycle`() {
        val uploading = ComposerAttachmentView(id = "a", filename = "a", status = ComposerAttachmentStatus.uploading)
        val failed = ComposerAttachmentView(id = "b", filename = "b", status = ComposerAttachmentStatus.failed)

        assertTrue(uploading.isUploading)
        assertFalse(uploading.isUploaded)
        assertFalse(uploading.hasFailed)
        assertTrue(failed.hasFailed)
        assertFalse(failed.isUploading)
    }

    @Test
    fun `glyph follows the mime type family`() {
        assertSame(
            Icons.Outlined.Image,
            composerAttachmentGlyph(mimeType = "image/png", filename = "p.png"),
        )
        assertSame(
            Icons.Outlined.Movie,
            composerAttachmentGlyph(mimeType = "video/mp4", filename = "v.mp4"),
        )
        assertSame(
            Icons.Outlined.Audiotrack,
            composerAttachmentGlyph(mimeType = "audio/mpeg", filename = "s.mp3"),
        )
        assertSame(
            Icons.Outlined.PictureAsPdf,
            composerAttachmentGlyph(mimeType = "application/pdf", filename = "d.pdf"),
        )
        assertSame(
            Icons.Outlined.Description,
            composerAttachmentGlyph(mimeType = "text/plain", filename = "n.txt"),
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `glyph falls back to the extension when no mime is reported`() {
        assertSame(
            Icons.Outlined.FolderZip,
            composerAttachmentGlyph(mimeType = null, filename = "bundle.zip"),
        )
        assertSame(
            Icons.Outlined.FolderZip,
            composerAttachmentGlyph(mimeType = "", filename = "archive.tar.gz"),
        )
        assertSame(
            Icons.Outlined.InsertDriveFile,
            composerAttachmentGlyph(mimeType = null, filename = "mystery"),
        )
    }
}

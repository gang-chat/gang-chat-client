package com.gangchat.client.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Lifecycle of a file staged in the composer. Files now upload as soon as they
 * are picked, so the chip reflects in-flight progress, success, or failure
 * before the message is ever sent.
 */
enum class ComposerAttachmentStatus { uploading, uploaded, failed }

/**
 * A file staged in the composer, waiting to ride the next outgoing message as
 * an attachment. This is the view model handed to the composer's chip strip;
 * the raw picked file (and its in-flight upload) is held by the shell and
 * matched back by [id].
 */
class ComposerAttachmentView(
    val id: String,
    val filename: String,
    val status: ComposerAttachmentStatus,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    /**
     * Upload progress in the range 0..1, or null when the total size is not yet
     * known (the chip then shows an indeterminate spinner).
     */
    val progress: Double? = null,
    val errorMessage: String? = null,
) {
    val isUploading: Boolean get() = status == ComposerAttachmentStatus.uploading
    val isUploaded: Boolean get() = status == ComposerAttachmentStatus.uploaded
    val hasFailed: Boolean get() = status == ComposerAttachmentStatus.failed

    /** Human-readable size, or null when the picker could not report one. */
    val sizeLabel: String?
        get() = if (sizeBytes == null) null else formatFileSize(sizeBytes)
}

private val _archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz")

/**
 * Pick a representative glyph for a staged file from its mime type, with a
 * small extension fallback so common types still read sensibly when the picker
 * reports no mime.
 */
fun composerAttachmentGlyph(mimeType: String? = null, filename: String): ImageVector {
    val mime = (mimeType ?: "").lowercase()
    if (mime.startsWith("image/")) return Icons.Outlined.Image
    if (mime.startsWith("video/")) return Icons.Outlined.Movie
    if (mime.startsWith("audio/")) return Icons.Outlined.Audiotrack
    if (mime == "application/pdf") return Icons.Outlined.PictureAsPdf
    if (mime.startsWith("text/")) return Icons.Outlined.Description

    val dot = filename.lastIndexOf('.')
    val ext = if (dot >= 0) filename.substring(dot + 1).lowercase() else ""
    if (_archiveExtensions.contains(ext)) return Icons.Outlined.FolderZip
    // Flutter's insert_drive_file_outlined does not mirror in right-to-left
    // layouts, so this keeps the non-mirrored glyph rather than AutoMirrored.
    @Suppress("DEPRECATION")
    return Icons.Outlined.InsertDriveFile
}

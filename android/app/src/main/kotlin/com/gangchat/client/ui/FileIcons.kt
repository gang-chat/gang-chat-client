package com.gangchat.client.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector

fun fileIconForMime(mimeType: String?): ImageVector {
    val value = (mimeType ?: "").lowercase()
    if (value.startsWith("image/")) return Icons.Outlined.Image
    if (value == "application/pdf") return Icons.Outlined.PictureAsPdf
    if (value.startsWith("audio/")) return Icons.Outlined.AudioFile
    if (value.startsWith("video/")) return Icons.Outlined.VideoFile
    if (value.contains("zip") || value.contains("tar") || value.contains("compressed")) {
        return Icons.Outlined.FolderZip
    }
    if (value.startsWith("text/") || value.contains("json")) {
        return Icons.Outlined.Description
    }
    return Icons.AutoMirrored.Outlined.InsertDriveFile
}

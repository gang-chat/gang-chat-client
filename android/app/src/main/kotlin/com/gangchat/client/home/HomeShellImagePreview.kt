package com.gangchat.client.home

import com.gangchat.client.app.MediaCacheRequest
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.get
import com.gangchat.client.platform.PlatformException
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import java.io.File

// Port of lib/src/home/home_shell_image_preview.dart.
//
// Actions backing the full-screen image preview overlay (see
// [ChatImagePreviewActions]): download to the Downloads folder, save-as via a
// picker, copy the image to the clipboard, and add a sticker into the user's
// personal stickers.
//
// PORT: only the Android paths are kept (no browser downloads, no desktop
// Downloads directory).

internal val _HomeShellState._imagePreviewActions: ChatImagePreviewActions
    get() {
        // The "添加到房间表情包" action only makes sense when the current user can
        // administer the room whose messages are on screen.
        val room = _selectedRoom
        val canManageRoomStickers = room != null && (room.isAdmin || _currentUser.isSuperuser)
        return ChatImagePreviewActions(
            onDownload = { url, suggestedName -> _previewDownloadToDownloads(url, suggestedName) },
            onSaveAs = { url, suggestedName -> _previewSaveAs(url, suggestedName) },
            onCopyToClipboard = { url -> _previewCopyToClipboard(url) },
            onSaveSticker = { message, attachment -> _previewSaveSticker(message, attachment) },
            onSaveRoomSticker =
                if (canManageRoomStickers) {
                    { message, attachment -> _previewSaveRoomSticker(message, attachment) }
                } else {
                    null
                },
            mediaCache = _mediaCacheController,
        )
    }

/**
 * Fetch the bytes for an image at [url]. Throws a user-facing message on a
 * non-2xx response or transport failure.
 */
private suspend fun _HomeShellState._fetchImageBytes(url: String): ByteArray {
    val request = MediaCacheRequest.tryFromUrl(url = url)
    if (request != null) {
        return _mediaCacheController.readBytes(request = request)
    }

    val uri = Uri.tryParse(url) ?: throw Exception("图片地址无效")
    val client = HttpClient()
    try {
        val response = client.get(uri)
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw Exception("下载失败 (${response.statusCode})")
        }
        return response.bodyBytes
    } finally {
        client.close()
    }
}

private suspend fun _HomeShellState._previewDownloadToDownloads(url: String, suggestedName: String) {
    val bytes = _fetchImageBytes(url)
    try {
        _fileSelectionService.saveBytesToDownloads(bytes = bytes, filename = suggestedName)
    } catch (error: PlatformException) {
        if (error.code != "downloads_unavailable") throw error
        val location =
            _fileSelectionService.getSaveLocation(suggestedName = suggestedName) ?: throw ImagePreviewActionCancelled()
        _fileSelectionService.saveBytesToLocation(bytes = bytes, location = location, filename = suggestedName)
    }
}

private suspend fun _HomeShellState._previewSaveAs(url: String, suggestedName: String) {
    // Fetch first so a transport failure surfaces before the picker opens.
    val bytes = _fetchImageBytes(url)
    // User cancelled the picker; treat as a silent no-op.
    val location = _fileSelectionService.getSaveLocation(suggestedName = suggestedName) ?: throw ImagePreviewActionCancelled()
    _fileSelectionService.saveBytesToLocation(bytes = bytes, location = location, filename = suggestedName)
}

private suspend fun _HomeShellState._previewCopyToClipboard(url: String) {
    val bytes = _fetchImageBytes(url)
    val ok = _clipboardService.writeImage(bytes)
    if (!ok) {
        throw Exception("当前平台不支持复制图片到剪贴板")
    }
}

private suspend fun _HomeShellState._previewSaveSticker(message: Message, attachment: MessageAttachment) {
    val stickerId = attachment.stickerId
    if (stickerId == null || stickerId.isEmpty()) {
        throw Exception("该表情无法添加")
    }
    _stickerPacksController.saveSticker(
        roomId = message.roomId,
        stickerId = stickerId,
        sourceMessageId = message.id,
        targetScope = "personal",
        userId = _currentUser.id,
    )
}

private suspend fun _HomeShellState._previewSaveRoomSticker(message: Message, attachment: MessageAttachment) {
    val stickerId = attachment.stickerId
    if (stickerId == null || stickerId.isEmpty()) {
        throw Exception("该表情无法添加")
    }
    val room = _selectedRoom
    if (room == null || (!room.isAdmin && !_currentUser.isSuperuser)) {
        throw Exception("没有权限管理房间表情")
    }
    _stickerPacksController.saveSticker(
        roomId = room.id,
        stickerId = stickerId,
        sourceMessageId = message.id,
        targetScope = "room",
        userId = _currentUser.id,
    )
}

/**
 * Builds a destination path inside [directory] for [filename], appending a
 * " (n)" suffix before the extension when the name already exists so a
 * download never silently overwrites an existing file.
 */
internal fun _uniqueDestinationPath(directory: String, filename: String): String {
    val separator = File.separator
    val base = if (directory.endsWith(separator)) directory.substring(0, directory.length - 1) else directory

    fun candidate(name: String): String = "$base$separator$name"

    if (!File(candidate(filename)).exists()) return candidate(filename)

    val dot = filename.lastIndexOf('.')
    val stem = if (dot <= 0) filename else filename.substring(0, dot)
    val ext = if (dot <= 0) "" else filename.substring(dot)
    for (i in 1 until 1000) {
        val name = "$stem ($i)$ext"
        if (!File(candidate(name)).exists()) return candidate(name)
    }
    return candidate(filename)
}

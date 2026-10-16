package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Uri
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.UploadedAsset
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToLong

enum class FileAttachmentTrailingKind {
    sending,
    failed,
    activeTransfer,
    placeholder,
    download,
}

class FileAttachmentTransferSlot(
    val downloadKey: String,
    val transfer: FileTransferState?,
    val usesUploadTransfer: Boolean,
)

class FileAttachmentInteractionState(
    val canDownload: Boolean,
    val tooltip: String,
)

class FileTransferProgressState(
    val value: Double?,
    val label: String,
    val failed: Boolean,
)

class FileAttachmentTrailingState(
    val kind: FileAttachmentTrailingKind,
    val showDismiss: Boolean = false,
    val pauseResumeTooltip: String? = null,
    val pauseResumeIsResume: Boolean = false,
    val cancelTooltip: String? = null,
)

class FileImagePreviewSize(val width: Double, val height: Double)

fun basename(value: String): String {
    val normalized = value.replace("\\", "/").trim()
    if (normalized.isEmpty()) return "file"
    val slash = normalized.lastIndexOf('/')
    val name = if (slash >= 0) normalized.substring(slash + 1) else normalized
    val query = name.indexOf('?')
    val fragment = name.indexOf('#')
    val end =
        listOfNotNull(
            if (query >= 0) query else null,
            if (fragment >= 0) fragment else null,
        ).fold(name.length) { min, value -> if (value < min) value else min }
    val clean = name.substring(0, end).trim()
    return if (clean.isEmpty()) "file" else clean
}

fun normalizedFilePaths(paths: Iterable<String>): List<String> {
    val seen = mutableSetOf<String>()
    val normalized = mutableListOf<String>()
    for (path in paths) {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || !seen.add(trimmed)) continue
        normalized.add(trimmed)
    }
    return normalized
}

@Suppress("UNUSED_PARAMETER")
fun clipboardFilesReadFailureMessage(error: Any): String {
    return "无法读取剪贴板中的文件"
}

fun clipboardImageUploadFilename(
    timestamp: DateTime,
    sequence: Int,
    mimeType: String = "image/png",
): String {
    val local = timestamp.toLocal()
    val serial = (if (sequence < 1) 1 else sequence).toString().padStart(3, '0')
    return "clipboard-image-" +
        _fixedDigits(local.year, 4) +
        _fixedDigits(local.month, 2) +
        "${_fixedDigits(local.day, 2)}-" +
        _fixedDigits(local.hour, 2) +
        _fixedDigits(local.minute, 2) +
        "${_fixedDigits(local.second, 2)}-" +
        "${_fixedDigits(local.millisecond, 3)}-" +
        "$serial.${_imageExtensionForMimeType(mimeType)}"
}

@Suppress("UNUSED_PARAMETER")
fun filePickerOpenFailureMessage(error: Any): String {
    return "无法打开文件选择器"
}

@Suppress("UNUSED_PARAMETER")
fun fileReadFailureMessage(error: Any): String {
    return "无法读取文件"
}

fun fileEmptyMessage(): String {
    return "文件为空"
}

fun fileDownloadUri(url: String): Uri? {
    return Uri.tryParse(url)
}

fun fileDownloadUnavailableMessage(): String {
    return "无法下载文件"
}

fun fileDownloadedNotice(): String {
    return "文件已下载"
}

fun extensionOf(filename: String): String {
    val name = basename(filename).lowercase()
    val dot = name.lastIndexOf('.')
    if (dot < 0 || dot == name.length - 1) return ""
    return name.substring(dot + 1)
}

fun mimeTypeFromFilename(filename: String): String {
    return when (extensionOf(filename)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "json" -> "application/json"
        "zip" -> "application/zip"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}

private fun _fixedDigits(value: Int, width: Int): String = value.toString().padStart(width, '0')

private fun _imageExtensionForMimeType(mimeType: String): String {
    return when (mimeType.lowercase().split(';').first().trim()) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/bmp" -> "bmp"
        else -> "png"
    }
}

/**
 * The file extension (without a leading dot) appropriate for an image of the
 * given [mimeType], e.g. `image/jpeg` -> `jpg`. Defaults to `png`.
 */
fun imageExtensionForMimeType(mimeType: String): String = _imageExtensionForMimeType(mimeType)

fun fileAttachmentTitle(attachment: MessageAttachment): String {
    val explicitName = attachment.name?.trim()
    if (explicitName != null && explicitName.isNotEmpty()) return explicitName
    val assetName = attachment.asset?.filename?.trim()
    if (assetName != null && assetName.isNotEmpty()) return assetName
    return filenameFromAssetUrl(attachment.asset?.url) ?: "file"
}

fun filenameFromAssetUrl(url: String?): String? {
    if (url == null || url.trim().isEmpty()) return null
    val uri = Uri.tryParse(url)
    val raw =
        if (uri != null && uri.pathSegments.isNotEmpty()) {
            uri.pathSegments.last()
        } else {
            url
        }
    val decoded = Uri.decodeComponent(raw)
    val name = basename(decoded)
    return if (name.trim().isEmpty()) null else name
}

fun fileAttachmentMeta(asset: UploadedAsset?): String {
    if (asset == null) return ""
    val parts = mutableListOf<String>()
    val mimeType = asset.mimeType.trim()
    if (mimeType.isNotEmpty()) parts.add(mimeType)
    val sizeBytes = asset.sizeBytes
    if (sizeBytes != null) parts.add(formatFileSize(sizeBytes))
    return parts.joinToString(" - ")
}

fun isImageMimeType(mimeType: String?): Boolean {
    return (mimeType ?: "").lowercase().startsWith("image/")
}

fun isAudioMimeType(mimeType: String?): Boolean {
    return (mimeType ?: "").lowercase().startsWith("audio/")
}

fun canPauseFileTransfer(transfer: FileTransferState?): Boolean {
    return transfer != null && transfer.active && !transfer.paused
}

fun canResumeFileTransfer(transfer: FileTransferState?): Boolean {
    return transfer != null && transfer.paused
}

fun canCancelActiveFileTransfer(transfer: FileTransferState?): Boolean {
    return transfer != null && transfer.active
}

fun fileAttachmentPreviewPath(asset: UploadedAsset?): String? {
    if (asset == null || !isImageMimeType(asset.mimeType)) return null
    return asset.thumbnailUrl ?: asset.url
}

fun fileAttachmentPreviewSize(
    asset: UploadedAsset?,
    maxWidth: Double = 320.0,
    maxHeight: Double = 240.0,
    fallbackWidth: Double = 220.0,
    fallbackHeight: Double = 150.0,
): FileImagePreviewSize {
    val fallback =
        FileImagePreviewSize(
            width = fallbackWidth,
            height = fallbackHeight,
        )
    val sourceWidth = asset?.width
    val sourceHeight = asset?.height
    if (sourceWidth == null ||
        sourceHeight == null ||
        sourceWidth <= 0 ||
        sourceHeight <= 0 ||
        maxWidth <= 0 ||
        maxHeight <= 0
    ) {
        return fallback
    }

    val scale = min(maxWidth / sourceWidth, maxHeight / sourceHeight)
    val safeScale = if (scale.isFinite() && scale > 0) min(1.0, scale) else 1.0
    return FileImagePreviewSize(
        width = sourceWidth * safeScale,
        height = sourceHeight * safeScale,
    )
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex += 1
    }
    val digits = if (value < 10) 1 else 0
    return "${String.format(Locale.ROOT, "%.${digits}f", value)} ${units[unitIndex]}"
}

fun formatFileSpeed(bytesPerSecond: Double): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    return "${formatFileSize(bytesPerSecond.roundToLong())}/s"
}

fun formatPercent(value: Double): String {
    return "${(value.coerceIn(0.0, 1.0) * 100).roundToLong()}%"
}

fun fileTransferLabel(transfer: FileTransferState): String {
    if (transfer.failed) return "传输失败"
    if (transfer.sendingMessage) return "发送中"

    val status =
        if (transfer.paused) {
            "已暂停"
        } else if (transfer.isDownload) {
            "下载中"
        } else {
            "上传中"
        }
    val progress =
        if (transfer.hasKnownTotal) {
            formatPercent(transfer.progress)
        } else {
            formatFileSize(transfer.sentBytes)
        }
    val speed =
        if (transfer.paused || transfer.bytesPerSecond <= 0) {
            ""
        } else {
            " · ${formatFileSpeed(transfer.bytesPerSecond)}"
        }
    return "$status $progress$speed"
}

fun fileTransferProgressState(transfer: FileTransferState): FileTransferProgressState {
    return FileTransferProgressState(
        value =
            if (transfer.sendingMessage) {
                1.0
            } else if (transfer.hasKnownTotal) {
                transfer.progress
            } else {
                null
            },
        label = transfer.error ?: fileTransferLabel(transfer),
        failed = transfer.failed,
    )
}

fun fileDownloadKey(
    message: Message,
    attachment: MessageAttachment,
    index: Int,
): String {
    val asset = attachment.asset
    val assetKey =
        asset?.id
            ?: asset?.url
            ?: attachment.name
            ?: fileAttachmentTitle(attachment)
    return "${message.clientMessageId}:$index:$assetKey"
}

fun fileAttachmentTransferSlot(
    message: Message,
    attachment: MessageAttachment,
    index: Int,
    uploadTransfer: FileTransferState?,
    downloads: Map<String, FileTransferState>,
): FileAttachmentTransferSlot {
    val downloadKey = fileDownloadKey(message, attachment, index)
    val activeUploadTransfer = if (index == 0) uploadTransfer else null
    val activeDownloadTransfer = downloads[downloadKey]
    val transfer = activeUploadTransfer ?: activeDownloadTransfer
    return FileAttachmentTransferSlot(
        downloadKey = downloadKey,
        transfer = transfer,
        usesUploadTransfer = activeUploadTransfer != null,
    )
}

fun fileAttachmentInteractionState(
    title: String,
    url: String?,
    transfer: FileTransferState?,
): FileAttachmentInteractionState {
    val canDownload = url != null && transfer == null
    return FileAttachmentInteractionState(
        canDownload = canDownload,
        tooltip = if (canDownload) "下载文件" else title,
    )
}

fun fileAttachmentTrailingState(
    transfer: FileTransferState?,
    canDownload: Boolean,
): FileAttachmentTrailingState {
    if (transfer != null) {
        if (transfer.sendingMessage) {
            return FileAttachmentTrailingState(
                kind = FileAttachmentTrailingKind.sending,
            )
        }
        if (transfer.failed) {
            return FileAttachmentTrailingState(
                kind = FileAttachmentTrailingKind.failed,
                showDismiss = transfer.isDownload,
            )
        }

        val action = if (transfer.isDownload) "下载" else "上传"
        return FileAttachmentTrailingState(
            kind = FileAttachmentTrailingKind.activeTransfer,
            pauseResumeTooltip = if (transfer.paused) "继续$action" else "暂停$action",
            pauseResumeIsResume = transfer.paused,
            cancelTooltip = "取消$action",
        )
    }

    return FileAttachmentTrailingState(
        kind =
            if (canDownload) {
                FileAttachmentTrailingKind.download
            } else {
                FileAttachmentTrailingKind.placeholder
            },
    )
}

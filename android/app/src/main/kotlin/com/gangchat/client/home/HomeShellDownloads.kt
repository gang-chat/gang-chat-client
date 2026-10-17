package com.gangchat.client.home

import com.gangchat.client.app.DownloadCancelledException
import com.gangchat.client.app.FileDownloadProgressHandler
import com.gangchat.client.app.FileDownloadStatePatch
import com.gangchat.client.app.MediaCacheCancelledException
import com.gangchat.client.app.MediaCacheProgressHandler
import com.gangchat.client.app.MediaCacheRequest
import com.gangchat.client.app.fileAttachmentTitle
import com.gangchat.client.app.fileDownloadKey
import com.gangchat.client.app.fileDownloadUnavailableMessage
import com.gangchat.client.app.fileDownloadUri
import com.gangchat.client.app.fileDownloadedNotice
import com.gangchat.client.app.filePickerOpenFailureMessage
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.shell.SaveFileLocation
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.showFloatingNotice
import kotlinx.coroutines.CancellationException

// Port of lib/src/home/home_shell_downloads.dart.
//
// Download orchestration for file attachments shown in the chat.
//
// Kept deliberately thin: the streaming/IO lives in [FileDownloadsController]
// and all of the "what state is this transfer in" decisions live in
// `FileDisplay.kt`. This part only wires the two to the widget tree and the
// local [_fileDownloads] map, and surfaces user-facing notices.
//
// PORT: the browser download path (`_downloadAttachmentInBrowser`) is web-only.

/**
 * Start saving [attachment] to a user-picked location. [resolvedUrl] is the
 * absolute asset URL (resolved by the widget layer against `AppConfig`).
 */
internal suspend fun _HomeShellState._downloadAttachment(
    message: Message,
    attachment: MessageAttachment,
    index: Int,
    resolvedUrl: String,
) {
    val downloadKey = fileDownloadKey(message, attachment, index)
    if (!_fileDownloadsController.canStartDownload(downloads = _fileDownloads, downloadKey = downloadKey)) {
        return
    }

    val uri = fileDownloadUri(resolvedUrl)
    if (uri == null) {
        _showDownloadNotice(fileDownloadUnavailableMessage(), tone = FloatingNoticeTone.error)
        return
    }

    val suggestedName = fileAttachmentTitle(attachment)
    val location: SaveFileLocation?
    try {
        location = _fileSelectionService.getSaveLocation(suggestedName = suggestedName)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _showDownloadNotice(filePickerOpenFailureMessage(error), tone = FloatingNoticeTone.error)
        return
    }
    if (!mounted || location == null) return

    val transfer =
        _fileDownloadsController.createDownload(
            totalBytes = attachment.asset?.sizeBytes ?: 0,
            destinationPath = location.path,
        )
    _setHomeState {
        _applyDownloadPatch(
            _fileDownloadsController.patchStartedDownload(
                downloads = _fileDownloads,
                downloadKey = downloadKey,
                transfer = transfer,
            ),
        )
    }

    fun applyProgress() {
        if (!mounted) return
        val patch =
            _fileDownloadsController.patchDownloadProgress(
                downloads = _fileDownloads,
                downloadKey = downloadKey,
                transfer = transfer,
            ) ?: return
        _setHomeState { _applyDownloadPatch(patch) }
    }

    try {
        val cacheRequest =
            MediaCacheRequest.tryFromUrl(
                url = resolvedUrl,
                filename = suggestedName,
                mimeType = attachment.asset?.mimeType,
                expectedBytes = attachment.asset?.sizeBytes,
            )
        if (cacheRequest == null) {
            _fileDownloadsController.downloadToFile(
                uri = uri,
                transfer = transfer,
                onProgress = FileDownloadProgressHandler { _, _ -> applyProgress() },
            )
        } else {
            val cached =
                _mediaCacheController.getOrDownload(
                    request = cacheRequest,
                    transfer = transfer,
                    onProgress = MediaCacheProgressHandler { _, _ -> applyProgress() },
                )
            _mediaCacheController.copyFileToPath(
                source = cached,
                destinationPath = location.path,
                transfer = transfer,
                onProgress = MediaCacheProgressHandler { _, _ -> applyProgress() },
            )
        }
        if (!_fileDownloadsController.canCompleteDownload(
                downloads = _fileDownloads,
                downloadKey = downloadKey,
                transfer = transfer,
            )
        ) {
            return
        }
        _fileSelectionService.commitLocation(location)
        if (!mounted) return
        _setHomeState {
            _applyDownloadPatch(
                _fileDownloadsController.patchCompletedDownload(downloads = _fileDownloads, downloadKey = downloadKey),
            )
        }
        _showDownloadNotice(fileDownloadedNotice(), tone = FloatingNoticeTone.success)
    } catch (error: MediaCacheCancelledException) {
        // Cancellation already removed the entry via [_cancelDownload]; nothing
        // to report.
    } catch (error: DownloadCancelledException) {
        // Cancellation already removed the entry via [_cancelDownload]; nothing
        // to report.
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState {
            _applyDownloadPatch(
                _fileDownloadsController.patchFailedDownload(downloads = _fileDownloads, transfer = transfer, failure = error),
            )
        }
    } finally {
        try {
            _fileSelectionService.discardLocation(location)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Best-effort cleanup. A completed Android commit already removed the
            // staging file; cleanup failures must not overwrite download state.
        }
    }
}

internal fun _HomeShellState._pauseDownload(downloadKey: String) {
    val patch = _fileDownloadsController.patchPausedDownload(downloads = _fileDownloads, downloadKey = downloadKey) ?: return
    _setHomeState { _applyDownloadPatch(patch) }
}

internal fun _HomeShellState._resumeDownload(downloadKey: String) {
    val patch = _fileDownloadsController.patchResumedDownload(downloads = _fileDownloads, downloadKey = downloadKey) ?: return
    _setHomeState { _applyDownloadPatch(patch) }
}

/**
 * Cancel an in-flight download and drop it from the map. The streaming loop
 * in [com.gangchat.client.app.FileDownloadsController.downloadToFile] sees
 * the cancellation, cleans up the partial file, and throws
 * [DownloadCancelledException].
 */
internal fun _HomeShellState._cancelDownload(downloadKey: String) {
    val transfer = _fileDownloads[downloadKey] ?: return
    _fileDownloadsController.cancel(transfer)
    _setHomeState {
        _applyDownloadPatch(_fileDownloadsController.patchRemovedDownload(downloads = _fileDownloads, downloadKey = downloadKey))
    }
}

/**
 * Clear a finished-but-failed download entry so the tile returns to its idle,
 * re-downloadable state.
 */
internal fun _HomeShellState._dismissDownload(downloadKey: String) {
    if (!_fileDownloads.containsKey(downloadKey)) return
    _setHomeState {
        _applyDownloadPatch(_fileDownloadsController.patchRemovedDownload(downloads = _fileDownloads, downloadKey = downloadKey))
    }
}

/**
 * Cancel every active download (used on dispose) so no http client or sink is
 * left dangling after the shell goes away.
 */
internal fun _HomeShellState._cancelActiveDownloads() {
    for (transfer in _fileDownloads.values) {
        _fileDownloadsController.cancel(transfer)
    }
}

private fun _HomeShellState._applyDownloadPatch(patch: FileDownloadStatePatch) {
    _fileDownloads = patch.downloads
}

private fun _HomeShellState._showDownloadNotice(message: String, tone: FloatingNoticeTone = FloatingNoticeTone.info) {
    if (!mounted) return
    showFloatingNotice(context, message, tone = tone)
}

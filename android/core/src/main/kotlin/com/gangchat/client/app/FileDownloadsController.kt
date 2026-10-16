package com.gangchat.client.app

import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.protocol.UploadTransferController
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Dart's `typedef FileDownloadProgressHandler = void Function({required int
 * sentBytes, required int totalBytes})`; invoke with named arguments.
 */
fun interface FileDownloadProgressHandler {
    operator fun invoke(sentBytes: Long, totalBytes: Long)
}

class DownloadCancelledException : Exception("下载已取消") {
    override fun toString(): String = "下载已取消"
}

class FileDownloadStatePatch(val downloads: Map<String, FileTransferState>)

open class FileDownloadsController(httpClientFactory: (() -> HttpClient)? = null) {
    private val _httpClientFactory: () -> HttpClient = httpClientFactory ?: { HttpClient() }

    open fun createDownload(
        totalBytes: Long,
        destinationPath: String,
    ): FileTransferState {
        return FileTransferState.download(
            controller = UploadTransferController(),
            totalBytes = totalBytes,
            destinationPath = destinationPath,
        )
    }

    open fun canStartDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
    ): Boolean {
        return !downloads.containsKey(downloadKey)
    }

    open fun patchStartedDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
        transfer: FileTransferState,
    ): FileDownloadStatePatch {
        return FileDownloadStatePatch(
            downloads = LinkedHashMap(downloads).apply { put(downloadKey, transfer) },
        )
    }

    open fun shouldApplyDownloadProgress(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
        transfer: FileTransferState,
    ): Boolean {
        return downloads[downloadKey] === transfer
    }

    open fun patchDownloadProgress(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
        transfer: FileTransferState,
    ): FileDownloadStatePatch? {
        if (!shouldApplyDownloadProgress(
                downloads = downloads,
                downloadKey = downloadKey,
                transfer = transfer,
            )
        ) {
            return null
        }
        return FileDownloadStatePatch(downloads = downloads)
    }

    open fun canCompleteDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
        transfer: FileTransferState,
    ): Boolean {
        return !transfer.cancelled &&
            shouldApplyDownloadProgress(
                downloads = downloads,
                downloadKey = downloadKey,
                transfer = transfer,
            )
    }

    open fun patchCompletedDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
    ): FileDownloadStatePatch {
        return patchRemovedDownload(downloads = downloads, downloadKey = downloadKey)
    }

    open fun patchFailedDownload(
        downloads: Map<String, FileTransferState>,
        transfer: FileTransferState,
        failure: Any,
    ): FileDownloadStatePatch {
        transfer.markFailed(failure, stopTransferSpeed = true)
        return FileDownloadStatePatch(downloads = downloads)
    }

    open fun patchRemovedDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
    ): FileDownloadStatePatch {
        val next = LinkedHashMap(downloads).apply { remove(downloadKey) }
        return FileDownloadStatePatch(downloads = next)
    }

    open fun partialDownloadPathToDelete(transfer: FileTransferState): String? {
        val destinationPath = transfer.destinationPath
        if (destinationPath == null || !transfer.wroteDestination) return null
        return destinationPath
    }

    open fun pauseDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
    ): Boolean {
        val transfer = downloads[downloadKey]
        if (transfer == null || !transfer.isDownload) return false
        return transfer.pauseTransfer()
    }

    open fun patchPausedDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
    ): FileDownloadStatePatch? {
        val changed =
            pauseDownload(
                downloads = downloads,
                downloadKey = downloadKey,
            )
        if (!changed) return null
        return FileDownloadStatePatch(downloads = downloads)
    }

    open fun resumeDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
    ): Boolean {
        val transfer = downloads[downloadKey]
        if (transfer == null || !transfer.isDownload) return false
        return transfer.resumeTransfer()
    }

    open fun patchResumedDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
    ): FileDownloadStatePatch? {
        val changed =
            resumeDownload(
                downloads = downloads,
                downloadKey = downloadKey,
            )
        if (!changed) return null
        return FileDownloadStatePatch(downloads = downloads)
    }

    open fun cancelDownload(
        downloads: Map<String, FileTransferState>,
        downloadKey: String,
    ): Boolean {
        val transfer = downloads[downloadKey]
        if (transfer == null || !transfer.isDownload) return false
        return transfer.cancelTransfer()
    }

    /**
     * Streams [uri] into the transfer's destination file. File writes run on
     * [Dispatchers.IO]; the response is pulled chunk by chunk, so a paused
     * transfer stops reading like Dart's paused `await for`.
     */
    open suspend fun downloadToFile(
        uri: Uri,
        transfer: FileTransferState,
        onProgress: FileDownloadProgressHandler? = null,
    ) {
        val destinationPath =
            transfer.destinationPath ?: throw IllegalStateException("下载位置不存在")

        var client: HttpClient? = null
        var sink: OutputStream? = null
        try {
            client = _httpClientFactory()
            transfer.downloadClient = client
            val request = HttpRequest("GET", uri)
            val response = client.send(request)
            if (response.statusCode < 200 || response.statusCode >= 300) {
                throw IllegalStateException("下载失败 (${response.statusCode})")
            }

            val totalBytes = response.contentLength ?: transfer.totalBytes
            var receivedBytes = 0L
            transfer.updateProgress(sentBytes = 0, totalBytes = totalBytes)
            onProgress?.invoke(sentBytes = 0, totalBytes = totalBytes)

            val file = File(destinationPath)
            val output = withContext(Dispatchers.IO) { FileOutputStream(file) }
            sink = output
            transfer.wroteDestination = true
            response.stream.flow.collect { chunk ->
                transfer.controller.waitIfPaused()
                if (transfer.cancelled) throw DownloadCancelledException()

                withContext(Dispatchers.IO) { output.write(chunk) }
                receivedBytes += chunk.size
                transfer.updateProgress(
                    sentBytes = receivedBytes,
                    totalBytes = totalBytes,
                )
                onProgress?.invoke(sentBytes = receivedBytes, totalBytes = totalBytes)
            }

            withContext(Dispatchers.IO) {
                output.flush()
                output.close()
            }
            sink = null

            if (transfer.cancelled) throw DownloadCancelledException()
        } catch (error: DownloadCancelledException) {
            if (transfer.wroteDestination) {
                deletePartialDownload(destinationPath)
            }
            throw error
        } catch (error: Exception) {
            if (transfer.cancelled) {
                if (transfer.wroteDestination) {
                    deletePartialDownload(destinationPath)
                }
                // Coroutine cancellation keeps propagating as such.
                if (error is CancellationException) throw error
                throw DownloadCancelledException()
            }
            throw error
        } finally {
            client?.close()
            transfer.downloadClient = null
            val openSink = sink
            if (openSink != null) {
                try {
                    withContext(NonCancellable + Dispatchers.IO) { openSink.close() }
                } catch (_: Exception) {
                    // The client may have been closed to cancel the response stream.
                }
            }
        }
    }

    open fun cancel(transfer: FileTransferState) {
        transfer.cancelTransfer()
    }

    open suspend fun deletePartialDownload(path: String) {
        try {
            // Cleanup also runs when the calling coroutine is being cancelled.
            withContext(NonCancellable + Dispatchers.IO) {
                val file = File(path)
                if (file.exists()) file.delete()
            }
        } catch (_: Exception) {
            // Best effort cleanup for a cancelled or failed partial download.
        }
    }
}

package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.protocol.UploadTransferController

enum class FileTransferDirection { upload, download }

/**
 * Dart's named constructors `FileTransferState.upload(...)` and
 * `FileTransferState.download(...)` are the companion factories [upload] and
 * [download]; call them the same way.
 */
class FileTransferState private constructor(
    val direction: FileTransferDirection,
    val controller: UploadTransferController,
    var totalBytes: Long,
    val destinationPath: String?,
) {
    var sentBytes: Long = 0
    var bytesPerSecond: Double = 0.0
    var downloadClient: HttpClient? = null
    var wroteDestination: Boolean = false
    var sendingMessage: Boolean = false
    var failed: Boolean = false
    var error: String? = null
    private var _speedSampleAt: DateTime? = null
    private var _speedSampleBytes: Long = 0

    val isDownload: Boolean get() = direction == FileTransferDirection.download
    val paused: Boolean get() = controller.isPaused
    val cancelled: Boolean get() = controller.isCancelled
    val active: Boolean get() = !failed && !cancelled && !sendingMessage
    val hasKnownTotal: Boolean get() = totalBytes > 0

    val progress: Double
        get() {
            if (totalBytes <= 0) return 0.0
            return (sentBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0)
        }

    fun updateProgress(sentBytes: Long, totalBytes: Long) {
        val now = DateTime.now()
        val speedSampleAt = _speedSampleAt
        if (speedSampleAt == null) {
            _speedSampleAt = now
            _speedSampleBytes = this.sentBytes
        } else {
            val elapsed = now.difference(speedSampleAt).inWholeMilliseconds
            if (elapsed >= 400 || sentBytes >= totalBytes && totalBytes > 0) {
                val deltaBytes = sentBytes - _speedSampleBytes
                bytesPerSecond = if (elapsed > 0) (deltaBytes * 1000).toDouble() / elapsed else 0.0
                _speedSampleAt = now
                _speedSampleBytes = sentBytes
            }
        }

        this.sentBytes = sentBytes
        this.totalBytes = totalBytes
    }

    fun markSendingMessage() {
        sendingMessage = true
        updateProgress(sentBytes = totalBytes, totalBytes = totalBytes)
    }

    fun markFailed(failure: Any, stopTransferSpeed: Boolean = false) {
        failed = true
        sendingMessage = false
        error = userFacingErrorMessage(failure, fallback = "文件传输失败")
        if (stopTransferSpeed) stopSpeed()
    }

    fun pauseTransfer(): Boolean {
        if (!active || paused) return false
        controller.pause()
        stopSpeed()
        return true
    }

    fun resumeTransfer(): Boolean {
        if (!paused) return false
        controller.resume()
        return true
    }

    fun cancelTransfer(): Boolean {
        if (!active) return false
        controller.cancel()
        downloadClient?.close()
        return true
    }

    fun stopSpeed() {
        bytesPerSecond = 0.0
        _speedSampleAt = null
        _speedSampleBytes = sentBytes
    }

    companion object {
        fun upload(controller: UploadTransferController, totalBytes: Long): FileTransferState =
            FileTransferState(
                direction = FileTransferDirection.upload,
                controller = controller,
                totalBytes = totalBytes,
                destinationPath = null,
            )

        fun download(
            controller: UploadTransferController,
            totalBytes: Long,
            destinationPath: String?,
        ): FileTransferState =
            FileTransferState(
                direction = FileTransferDirection.download,
                controller = controller,
                totalBytes = totalBytes,
                destinationPath = destinationPath,
            )
    }
}

package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Stream
import com.gangchat.client.platform.Amplitude
import com.gangchat.client.platform.AudioEncoder
import com.gangchat.client.platform.AudioRecorder
import com.gangchat.client.platform.RecordConfig
import com.gangchat.client.plugins.PathProvider
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VoiceRecorderPermissionException : Exception("麦克风权限被拒绝") {
    override fun toString(): String = "麦克风权限被拒绝"
}

/**
 * Thin wrapper around the `record` plugin ([AudioRecorder]) that captures a
 * single voice clip to a temp file. The recorder phase/timer state machine
 * lives in `voice_message_display`; this class only owns the device side:
 * permission, start/stop/cancel, and reading the captured bytes for upload.
 */
class VoiceRecorderController(recorder: AudioRecorder? = null) {
    private val _recorder: AudioRecorder = recorder ?: AudioRecorder()
    private var _currentPath: String? = null

    // PORT: the web-only WAV fallback (`_webWavConfig`, for browsers without an
    // AAC MediaRecorder) is not ported; Android always records AAC.

    /** MIME type and file extension of the current (or last) clip. */
    val clipMimeType: String get() = kVoiceMessageMimeType
    val clipExtension: String get() = kVoiceMessageExtension

    suspend fun hasPermission(): Boolean = _recorder.hasPermission()

    /**
     * Begins recording to a fresh temp file. Throws
     * [VoiceRecorderPermissionException] if the mic is denied.
     */
    suspend fun start() {
        if (!_recorder.hasPermission()) {
            throw VoiceRecorderPermissionException()
        }
        val stamp = DateTime.now().microsecondsSinceEpoch
        val dir = PathProvider.getTemporaryDirectory()
        val path = "${dir.path}/voice_$stamp.$kVoiceMessageExtension"
        _currentPath = path
        _recorder.start(_config, path = path)
    }

    /**
     * Stops recording and returns the captured file path, or null if nothing was
     * written.
     */
    suspend fun stop(): String? {
        val path = _recorder.stop()
        _currentPath = null
        return path
    }

    /** Stops and discards the recording, deleting the temp file. */
    suspend fun cancel() {
        try {
            _recorder.cancel()
        } finally {
            _deleteTemp(_currentPath)
            _currentPath = null
        }
    }

    /** Live microphone amplitude for an optional level meter while recording. */
    fun amplitudeStream(interval: Duration = 200.milliseconds): Stream<Amplitude> {
        return _recorder.onAmplitudeChanged(interval)
    }

    /**
     * Reads a clip's bytes for upload. Does not delete the file, so a failed
     * send can retry from the same path; call [discardClip] once the clip is no
     * longer needed.
     */
    suspend fun readClip(path: String): ByteArray {
        val file = File(path)
        return withContext(Dispatchers.IO) { file.readBytes() }
    }

    /** Deletes a finished clip's temp file. Safe to call with a stale path. */
    suspend fun discardClip(path: String?) = _deleteTemp(path)

    private suspend fun _deleteTemp(path: String?) {
        if (path == null) return
        try {
            withContext(Dispatchers.IO) {
                val file = File(path)
                if (file.exists()) file.delete()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // A leftover temp clip in the OS temp dir is harmless; never let cleanup
            // failure surface to the user.
        }
    }

    suspend fun dispose() {
        _recorder.dispose()
    }

    companion object {
        /**
         * Records mono AAC-LC at a speech-friendly bitrate — small files that still
         * sound clean for voice, wrapped in an m4a/[kVoiceMessageMimeType] container.
         */
        private val _config =
            RecordConfig(
                encoder = AudioEncoder.aacLc,
                bitRate = 64000,
                sampleRate = 44100,
                numChannels = 1,
            )
    }
}

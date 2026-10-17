package com.gangchat.client.platform.webrtc

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import livekit.org.webrtc.audio.JavaAudioDeviceModule

/**
 * Feeds Android playback-capture PCM into an isolated WebRTC audio device
 * module (the screen-audio aux room's).
 *
 * The app UID is explicitly excluded so Gang Chat voice, sound effects, and
 * music-box playback cannot be captured back into the shared audio.
 *
 * Port of the Gang Chat flutter_webrtc fork's `AndroidScreenAudioCapturer`.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AndroidScreenAudioCapturer(
    private val mediaProjection: MediaProjection,
) : JavaAudioDeviceModule.AudioBufferCallback {
    private val audioRecordLock = Any()

    private var audioRecord: AudioRecord? = null
    private var initializationAttempted = false
    private var released = false

    override fun onBuffer(
        buffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        ignoredBytesRead: Int,
        ignoredCaptureTimeNs: Long,
    ): Long {
        val current = ensureAudioRecord(audioFormat, channelCount, sampleRate, buffer.capacity())
        if (current == null) {
            clearBuffer(buffer)
            SystemClock.sleep(EMPTY_BUFFER_DELAY_MILLIS)
            return System.nanoTime()
        }

        buffer.clear()
        val read: Int
        try {
            read = current.read(buffer, buffer.capacity(), AudioRecord.READ_BLOCKING)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Playback AudioRecord.read failed", error)
            releaseAudioResources()
            clearBuffer(buffer)
            return System.nanoTime()
        }

        if (read < 0) {
            Log.e(TAG, "Playback AudioRecord.read failed with code $read")
            releaseAudioResources()
            clearBuffer(buffer)
        } else if (read < buffer.capacity()) {
            for (i in maxOf(read, 0) until buffer.capacity()) buffer.put(i, 0.toByte())
        }
        buffer.position(0)
        return System.nanoTime()
    }

    @SuppressLint("MissingPermission")
    private fun ensureAudioRecord(
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        callbackBufferSize: Int,
    ): AudioRecord? {
        synchronized(audioRecordLock) {
            if (released) return null
            audioRecord?.let { return it }
            if (initializationAttempted) return null
            initializationAttempted = true

            if (channelCount != 1 && channelCount != 2) {
                Log.e(TAG, "Unsupported playback channel count: $channelCount")
                return null
            }

            val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
            val encoding = if (audioFormat == AudioFormat.ENCODING_DEFAULT) AudioFormat.ENCODING_PCM_16BIT else audioFormat
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
            if (minBufferSize <= 0) {
                Log.e(TAG, "Invalid playback capture buffer size: $minBufferSize")
                return null
            }

            val captureConfiguration =
                try {
                    AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .excludeUid(Process.myUid())
                        .build()
                } catch (error: RuntimeException) {
                    Log.e(TAG, "Failed to configure Android playback capture", error)
                    return null
                }

            var created: AudioRecord? = null
            try {
                created =
                    AudioRecord.Builder()
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(encoding)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelMask)
                                .build(),
                        )
                        .setBufferSizeInBytes(maxOf(BUFFER_SIZE_FACTOR * minBufferSize, callbackBufferSize))
                        .setAudioPlaybackCaptureConfig(captureConfiguration)
                        .build()
                created.startRecording()
                if (created.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "Playback AudioRecord did not enter recording state")
                    created.release()
                    return null
                }
                audioRecord = created
                Log.i(TAG, "Android playback capture started at $sampleRate Hz, channels=$channelCount")
                return created
            } catch (error: RuntimeException) {
                Log.e(TAG, "Failed to start Android playback capture", error)
                created?.release()
                return null
            }
        }
    }

    fun releaseAudioResources() {
        synchronized(audioRecordLock) {
            released = true
            val current = audioRecord ?: return
            audioRecord = null
            try {
                current.stop()
            } catch (_: RuntimeException) {
                // A revoked MediaProjection can stop the record first.
            }
            current.release()
        }
    }

    private companion object {
        const val TAG = "ScreenAudioCapturer"
        const val BUFFER_SIZE_FACTOR = 2
        const val EMPTY_BUFFER_DELAY_MILLIS = 10L

        fun clearBuffer(buffer: ByteBuffer) {
            buffer.clear()
            for (i in 0 until buffer.capacity()) buffer.put(i, 0.toByte())
            buffer.position(0)
        }
    }
}

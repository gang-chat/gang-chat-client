package com.gangchat.client.live

import android.Manifest
import com.gangchat.client.app.normalizedAudioVolume
import com.gangchat.client.foundation.Timer
import com.gangchat.client.platform.AudioPlayer
import com.gangchat.client.platform.BytesSource
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.platform.ReleaseMode
import com.gangchat.client.platform.webrtc.WebRtc
import com.gangchat.client.platform.webrtc.WebRtcRequests
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.audio.NoAudioHandler
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import livekit.org.webrtc.AudioTrackSink
import livekit.org.webrtc.audio.JavaAudioDeviceModule

/**
 * The settings page's microphone and speaker tests.
 *
 * The input test captures the microphone outside any room and reports its
 * level every 120 ms. PORT: the Dart service measured the level from WebRTC
 * sender stats through a loopback peer-connection pair; the LiveKit Android
 * SDK exposes no standalone peer connections, so the level is computed from
 * the captured samples instead — the same RMS energy WebRTC's
 * `totalAudioEnergy / totalSamplesDuration` describes — and shown through the
 * same display curve.
 */
class AudioTestService {
    suspend fun startInputTest(
        inputDeviceId: String?,
        volume: Double,
        onLevel: (level: Double) -> Unit,
    ): AudioTestHandle {
        var capture: _InputCapture? = null
        try {
            capture = _createTestAudioCapture(inputDeviceId)
            capture.start(onLevel)
            return AudioTestHandle(inputCapture = capture)
        } catch (e: Throwable) {
            capture?.dispose()
            if (e is Exception) throw e
            if (e is VirtualMachineError) throw e
            throw LkTrackCreateException(e.message ?: "Failed to create track")
        }
    }

    suspend fun startOutputTest(
        volume: Double,
        onLevel: (level: Double) -> Unit,
    ): AudioTestHandle {
        var player: AudioPlayer? = null
        var levelTimer: Timer? = null
        try {
            player = AudioPlayer()
            player.setReleaseMode(ReleaseMode.loop)
            player.play(
                BytesSource(_outputTestToneWav, mimeType = "audio/wav"),
                volume = normalizedAudioVolume(volume),
            )
            onLevel(1.0)
            levelTimer = Timer.periodic(80.milliseconds) { onLevel(1.0) }
            return AudioTestHandle(outputPlayer = player, levelTimer = levelTimer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            levelTimer?.cancel()
            _disposeOutputPlayer(player)
            throw e
        }
    }

    private suspend fun _createTestAudioCapture(@Suppress("UNUSED_PARAMETER") inputDeviceId: String?): _InputCapture {
        // getUserMedia's permission prompt. As in flutter_webrtc on Android, the
        // per-track device id is not used; the input chosen in settings is the
        // audio device module's preferred input.
        WebRtcRequests.ensureUserMediaPermissions(listOf(Manifest.permission.RECORD_AUDIO))
        val room =
            LiveKit.create(
                appContext = GangChatPlatform.context,
                overrides =
                    LiveKitOverrides(
                        audioOptions =
                            AudioOptions(
                                audioHandler = NoAudioHandler(),
                                javaAudioDeviceModuleCustomizer = WebRtc::javaAudioDeviceModuleCustomizer,
                                disableCommunicationModeWorkaround = true,
                            ),
                    ),
            )
        (room.lkObjects.audioDeviceModule as? JavaAudioDeviceModule)?.let(WebRtc::registerAudioDeviceModule)
        val track =
            try {
                room.localParticipant.createAudioTrack(
                    name = "audio-test",
                    options = LiveKitAndroidSetup.audioCaptureDefaults,
                )
            } catch (e: Throwable) {
                withContext(Dispatchers.Default) { room.release() }
                throw e
            }
        return _InputCapture(room, track)
    }
}

class AudioTestHandle internal constructor(
    private var inputCapture: _InputCapture? = null,
    private var outputPlayer: AudioPlayer? = null,
    private var levelTimer: Timer? = null,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun setCaptureVolume(volume: Double) {
        // Input-test levels are scaled in app state. Do not write the local
        // capture track volume here.
    }

    suspend fun setPlaybackVolume(volume: Double) {
        val player = outputPlayer ?: return
        player.setVolume(normalizedAudioVolume(volume))
    }

    suspend fun dispose() {
        val capture = inputCapture
        val player = outputPlayer
        val timer = levelTimer
        inputCapture = null
        outputPlayer = null
        levelTimer = null
        timer?.cancel()
        capture?.dispose()
        _disposeOutputPlayer(player)
    }
}

/**
 * A microphone capture outside any room: the test room's audio device module
 * records (prewarmed, as LiveKit does before publishing) and its samples feed
 * a level meter sampled every 120 ms.
 */
internal class _InputCapture(
    private val room: Room,
    private val track: LocalAudioTrack,
) : AudioTrackSink {
    private val lock = Any()
    private var energy = 0.0
    private var duration = 0.0
    private var timer: Timer? = null
    private var activated = false

    fun start(onLevel: (Double) -> Unit) {
        track.addSink(this)
        WebRtc.acquireAudioSwitch()
        activated = true
        track.prewarm()
        track.start()
        onLevel(0.0)
        timer =
            Timer.periodic(120.milliseconds) {
                val level = sample()
                if (level != null) onLevel(level)
            }
    }

    /** RMS of the samples captured since the last poll, on the display curve. */
    private fun sample(): Double? {
        val (energyDelta, durationDelta) =
            synchronized(lock) {
                val result = energy to duration
                energy = 0.0
                duration = 0.0
                result
            }
        if (durationDelta <= 0) return null
        val energyLevel =
            if (!energyDelta.isFinite() || energyDelta <= 0) 0.0 else sqrt(energyDelta / durationDelta)
        return _displayAudioLevel(max(0.0, energyLevel))
    }

    override fun onData(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        absoluteCaptureTimestampMs: Long,
    ) {
        if (bitsPerSample != 16 || sampleRate <= 0 || numberOfChannels <= 0) return
        val samples = audioData.duplicate().order(ByteOrder.nativeOrder())
        samples.position(0)
        val count = minOf(numberOfFrames * numberOfChannels, samples.remaining() / 2)
        var squareSum = 0.0
        for (i in 0 until count) {
            val value = samples.getShort(i * 2) / 32768.0
            squareSum += value * value
        }
        if (count == 0) return
        val seconds = numberOfFrames.toDouble() / sampleRate
        synchronized(lock) {
            // totalAudioEnergy accumulates mean-square level × duration.
            energy += squareSum / count * seconds
            duration += seconds
        }
    }

    suspend fun dispose() {
        timer?.cancel()
        timer = null
        try {
            track.removeSink(this)
        } catch (_: Exception) {
        }
        withContext(Dispatchers.Default) {
            try {
                track.stopPrewarm()
            } catch (_: Exception) {
            }
            try {
                track.stop()
            } catch (_: Exception) {
            }
            try {
                track.dispose()
            } catch (_: Exception) {
            }
            try {
                room.release()
            } catch (_: Exception) {
            }
        }
        if (activated) {
            activated = false
            WebRtc.releaseAudioSwitch()
        }
    }
}

private fun _displayAudioLevel(rawLevel: Double): Double {
    val noiseFloor = 0.015
    val displayGain = 2.2
    if (!rawLevel.isFinite() || rawLevel <= noiseFloor) return 0.0
    val normalized = (rawLevel - noiseFloor) / (1 - noiseFloor)
    return (normalized * displayGain).coerceIn(0.0, 1.0)
}

private suspend fun _disposeOutputPlayer(player: AudioPlayer?) {
    if (player == null) return
    try {
        player.stop()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
    }
    try {
        player.dispose()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
    }
}

private val _outputTestToneWav: ByteArray by lazy { _buildOutputTestToneWav() }

private fun _buildOutputTestToneWav(): ByteArray {
    val sampleRate = 48000
    val durationMs = 500
    val frequency = 1000.0
    val amplitude = 0.22
    val samples = sampleRate * durationMs / 1000
    val dataByteCount = samples * 2
    val data = ByteBuffer.allocate(44 + dataByteCount).order(ByteOrder.LITTLE_ENDIAN)

    fun writeAscii(offset: Int, value: String) {
        for (i in value.indices) data.put(offset + i, value[i].code.toByte())
    }

    writeAscii(0, "RIFF")
    data.putInt(4, 36 + dataByteCount)
    writeAscii(8, "WAVE")
    writeAscii(12, "fmt ")
    data.putInt(16, 16)
    data.putShort(20, 1)
    data.putShort(22, 1)
    data.putInt(24, sampleRate)
    data.putInt(28, sampleRate * 2)
    data.putShort(32, 2)
    data.putShort(34, 16)
    writeAscii(36, "data")
    data.putInt(40, dataByteCount)

    for (i in 0 until samples) {
        val sample = sin(2 * PI * frequency * i / sampleRate) * amplitude
        val scaled = sample * 32767
        // Dart's round(): halves away from zero.
        val rounded = if (scaled >= 0) kotlin.math.floor(scaled + 0.5) else -kotlin.math.floor(-scaled + 0.5)
        data.putShort(44 + i * 2, rounded.toInt().toShort())
    }
    return data.array()
}

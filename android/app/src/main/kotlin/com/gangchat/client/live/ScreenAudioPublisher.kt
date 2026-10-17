package com.gangchat.client.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Build
import android.util.Log
import com.gangchat.client.foundation.VisibleForTesting
import com.gangchat.client.foundation.debugPrint
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.timeout
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.platform.webrtc.AndroidScreenAudioCapturer
import com.gangchat.client.platform.webrtc.OrientationAwareScreenCapturer
import com.gangchat.client.protocol.ScreenAudioToken
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.audio.NoAudioHandler
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioPresets
import io.livekit.android.room.participant.AudioTrackPublishOptions
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.Track
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import livekit.org.webrtc.audio.JavaAudioDeviceModule

/**
 * Fetches a publish-only LiveKit token for the hidden screen-audio aux
 * participant. Implemented by the app layer (wired to `GangApi`), keeping
 * this class decoupled from the API client.
 */
typealias ScreenAudioTokenProvider = suspend (roomId: String) -> ScreenAudioToken

@VisibleForTesting
interface ScreenAudioPublisherBackend {
    suspend fun connect(liveKitUrl: String, token: String)

    suspend fun publish()

    suspend fun stop()
}

typealias ScreenAudioPublisherBackendFactory = () -> ScreenAudioPublisherBackend

/**
 * Publishes screen-share audio as an independent `screenShareAudio` track
 * through a *second* LiveKit Room whose PeerConnections live on an isolated
 * WebRTC factory.
 *
 * The isolated factory is fully separate from the primary microphone factory:
 * Android feeds it from playback capture on the active MediaProjection. This
 * removes the shared audio state that would otherwise fan mic capture into
 * the screen-audio send stream.
 *
 * The aux participant joins with identity `<ownerId>--screen-audio`, is
 * publish-only (`canSubscribe=false`), and never appears in the roster (no
 * `live_participants` row is created for it). Receivers merge its audio into
 * the owner's `screenShareVolume` via the existing `_applyScreenShareVolume`
 * logic, which iterates all `screenShareAudio` publications across all remote
 * participants.
 */
class ScreenAudioPublisher(
    val tokenProvider: ScreenAudioTokenProvider,
    @VisibleForTesting backendFactory: ScreenAudioPublisherBackendFactory? = null,
) {
    private val _backendFactory: ScreenAudioPublisherBackendFactory =
        backendFactory ?: ::createLiveKitScreenAudioPublisherBackend

    private var _backend: ScreenAudioPublisherBackend? = null
    private var _startOperation: Deferred<Unit>? = null
    private var _publishing = false
    private var _stopRequested = false

    val isPublishing: Boolean get() = _publishing

    /**
     * Connects the aux room and publishes the screen-audio track.
     *
     * [liveKitUrl] is the resolved LiveKit server URL (same as the main room).
     * [roomName] is the LiveKit room name (== roomID). The aux token is fetched
     * on demand so it is always fresh regardless of how long the user has been
     * in the call.
     */
    suspend fun start(liveKitUrl: String, roomName: String) {
        if (_stopRequested) return
        val operation =
            _startOperation ?: future { _runStart(liveKitUrl = liveKitUrl, roomName = roomName) }
                .also { _startOperation = it }
        operation.await()
    }

    private suspend fun _runStart(liveKitUrl: String, roomName: String) {
        debugPrint("screen-audio: starting publisher (url=$liveKitUrl, room=$roomName)")
        val tokenResult = tokenProvider(roomName)
        debugPrint("screen-audio: token acquired (identity=${tokenResult.identity})")
        if (_stopRequested) return

        val backend = _backendFactory()
        _backend = backend
        try {
            debugPrint("screen-audio: connecting aux room...")
            backend.connect(liveKitUrl = liveKitUrl, token = tokenResult.token)
            debugPrint("screen-audio: aux room connected")
            if (_stopRequested) {
                _releaseBackend(backend)
                return
            }

            debugPrint("screen-audio: publishing track...")
            backend.publish()
            if (_stopRequested) {
                _releaseBackend(backend)
                return
            }
            _publishing = true
            debugPrint("screen-audio: track published successfully")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _releaseBackend(backend)
            if (!_stopRequested) {
                throw error
            }
        }
    }

    private suspend fun _releaseBackend(backend: ScreenAudioPublisherBackend) {
        if (_backend !== backend) return
        _backend = null
        _publishing = false
        backend.stop()
    }

    /**
     * Cancels any in-flight start before releasing the aux room and track.
     *
     * Token acquisition can be arbitrarily slow, so a stop requested before a
     * backend exists returns immediately; `_runStart` observes the cancellation
     * before allocating native resources. Once a backend exists, give the
     * connect/publish operation a bounded opportunity to reach a cancellation
     * checkpoint. If it takes longer, `_runStart` owns the eventual cleanup and
     * cannot mark the publisher active after this method returns.
     */
    suspend fun stop() {
        _stopRequested = true
        val startOperation = _startOperation
        if (_backend != null && startOperation != null) {
            try {
                timeout(_inFlightStopWait) { startOperation.await() }
            } catch (e: com.gangchat.client.foundation.TimeoutException) {
                // The in-flight operation will stop its backend at the next checkpoint.
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // A failed start already performs its own best-effort cleanup.
            }
        }

        val backend = _backend
        _startOperation = null
        _publishing = false
        if (backend != null) {
            _releaseBackend(backend)
        }
    }

    private companion object {
        val _inFlightStopWait = 800.milliseconds
    }
}

/** The production backend: a second LiveKit Android SDK room on its own audio device module. */
internal fun createLiveKitScreenAudioPublisherBackend(): ScreenAudioPublisherBackend =
    _LiveKitScreenAudioPublisherBackend()

/**
 * The Gang Chat flutter_webrtc fork's Android screen-audio factory, rebuilt on
 * LiveKit: the aux room gets its own [JavaAudioDeviceModule] whose input is
 * playback capture on the active MediaProjection (the microphone is never
 * opened), no audio routing handler, and no playout of its own.
 */
private class _LiveKitScreenAudioPublisherBackend : ScreenAudioPublisherBackend {
    private var _room: Room? = null
    private var _stopTrack: (suspend () -> Unit)? = null
    private var _capturer: AndroidScreenAudioCapturer? = null
    private var _deviceModule: JavaAudioDeviceModule? = null

    override suspend fun connect(liveKitUrl: String, token: String) {
        val context = GangChatPlatform.context
        val deviceModule = _createScreenAudioDeviceModule(context)
        val room =
            LiveKit.create(
                appContext = context,
                overrides =
                    LiveKitOverrides(
                        okHttpClient = LiveKitAndroidSetup.directOkHttpClient,
                        audioOptions =
                            AudioOptions(
                                audioHandler = NoAudioHandler(),
                                audioDeviceModule = deviceModule,
                                disableCommunicationModeWorkaround = true,
                                disableAudioPrewarming = true,
                            ),
                    ),
            )
        _room = room
        room.connect(liveKitUrl, token)
    }

    /** `ensureScreenAudioFactory`. */
    private fun _createScreenAudioDeviceModule(context: Context): JavaAudioDeviceModule {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IllegalStateException("Android playback capture requires Android 10 or newer")
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException("RECORD_AUDIO permission is required for Android playback capture")
        }
        val mediaProjection =
            OrientationAwareScreenCapturer.activeScreenMediaProjection()
                ?: throw IllegalStateException("No active Android screen-share projection is available")

        val capturer = AndroidScreenAudioCapturer(mediaProjection)
        val deviceModule =
            try {
                JavaAudioDeviceModule.builder(context)
                    .setInputSampleRate(48000)
                    .setAudioFormat(AudioFormat.ENCODING_PCM_16BIT)
                    .setUseStereoInput(true)
                    .setUseHardwareAcousticEchoCanceler(false)
                    .setUseHardwareNoiseSuppressor(false)
                    .setAudioBufferCallback(capturer)
                    .createAudioDeviceModule()
            } catch (error: RuntimeException) {
                Log.e("FlutterWebRTCPlugin", "Failed to create Android screen-audio factory", error)
                capturer.releaseAudioResources()
                throw IllegalStateException("Failed to create Android screen-audio factory: ${error.message}")
            }
        // The callback above supplies every 10 ms input buffer. Disabling the
        // normal AudioRecord prevents the isolated factory from opening the mic.
        deviceModule.setAudioRecordEnabled(false)
        _capturer = capturer
        _deviceModule = deviceModule
        return deviceModule
    }

    override suspend fun publish() {
        val local =
            _room?.localParticipant
                ?: throw IllegalStateException("screen-audio aux room connected without a local participant")

        // Create the audio track on the isolated factory. Its audio is pulled from
        // the aux audio device module, which playback capture feeds.
        debugPrint("screen-audio: creating track on factory-2...")
        val track =
            local.createAudioTrack(
                name = "screen_share_audio",
                options =
                    LocalAudioTrackOptions(
                        noiseSuppression = false,
                        echoCancellation = false,
                        autoGainControl = false,
                        highPassFilter = false,
                        typingNoiseDetection = false,
                    ),
            )
        debugPrint("screen-audio: track created")
        _stopTrack = {
            withContext(Dispatchers.Default) {
                try {
                    track.stop()
                } catch (_: Exception) {
                }
                try {
                    track.dispose()
                } catch (_: Exception) {
                }
            }
        }
        track.start()
        val published =
            local.publishAudioTrack(
                track,
                AudioTrackPublishOptions(
                    name = "screen_share_audio",
                    audioBitrate = AudioPresets.MUSIC.maxBitrate,
                    source = Track.Source.SCREEN_SHARE_AUDIO,
                ),
            )
        if (!published) throw LkTrackPublishException()
    }

    override suspend fun stop() {
        val room = _room
        val stopTrack = _stopTrack
        val capturer = _capturer
        val deviceModule = _deviceModule
        _room = null
        _stopTrack = null
        _capturer = null
        _deviceModule = null

        if (room != null) {
            try {
                withContext(Dispatchers.Default) { room.disconnect() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        stopTrack?.invoke()
        if (room != null) {
            try {
                withContext(Dispatchers.Default) { room.release() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        // Release native capture on a worker: tearing the isolated audio stack down
        // can wait on WebRTC threads (the fork's screenAudioCleanupExecutor).
        if (capturer != null || deviceModule != null) {
            withContext(Dispatchers.Default) {
                capturer?.releaseAudioResources()
                deviceModule?.release()
            }
        }
    }
}

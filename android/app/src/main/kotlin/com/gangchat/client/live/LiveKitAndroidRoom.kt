package com.gangchat.client.live

import android.Manifest
import android.content.Context
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import com.gangchat.client.foundation.AppCoroutines
import com.gangchat.client.foundation.debugPrint
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.platform.webrtc.OrientationAwareScreenCapturer
import com.gangchat.client.platform.webrtc.WebRtc
import com.gangchat.client.platform.webrtc.WebRtcRequests
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioHandler
import io.livekit.android.audio.AudioProcessorOptions
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.ParticipantPermission
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.participant.VideoTrackPublishOptions
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.TrackPublication
import io.livekit.android.room.track.VideoCaptureParameter
import io.livekit.android.room.track.VideoEncoding
import io.livekit.android.room.track.VideoTrack
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.Proxy as NetProxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import livekit.LivekitModels
import livekit.org.webrtc.CameraVideoCapturer
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.RtpParameters
import livekit.org.webrtc.RtpSender
import livekit.org.webrtc.audio.JavaAudioDeviceModule
import okhttp3.OkHttpClient

/** The production [LiveRoomFactory]: a room on the LiveKit Android SDK. */
fun createLiveKitAndroidRoom(options: LkRoomOptions): LkRoom = LiveKitAndroidRoom(options)

/**
 * livekit_client's `DisconnectReasonExt.toSDKType` (with the Gang Chat fork's
 * compatibility mapping): protocol reasons added after the Dart SDK's enum
 * are folded into the closest existing category. In particular, a mobile
 * process killed by the OS is reported as CONNECTION_TIMEOUT and remains a
 * normal participant departure.
 */
fun liveKitProtocolDisconnectReason(reason: LivekitModels.DisconnectReason?): LkDisconnectReason =
    when (reason) {
        LivekitModels.DisconnectReason.UNKNOWN_REASON -> LkDisconnectReason.unknown
        LivekitModels.DisconnectReason.CLIENT_INITIATED -> LkDisconnectReason.clientInitiated
        LivekitModels.DisconnectReason.DUPLICATE_IDENTITY -> LkDisconnectReason.duplicateIdentity
        LivekitModels.DisconnectReason.SERVER_SHUTDOWN -> LkDisconnectReason.serverShutdown
        LivekitModels.DisconnectReason.PARTICIPANT_REMOVED -> LkDisconnectReason.participantRemoved
        LivekitModels.DisconnectReason.ROOM_DELETED -> LkDisconnectReason.roomDeleted
        LivekitModels.DisconnectReason.STATE_MISMATCH -> LkDisconnectReason.stateMismatch
        LivekitModels.DisconnectReason.JOIN_FAILURE -> LkDisconnectReason.joinFailure
        LivekitModels.DisconnectReason.MIGRATION -> LkDisconnectReason.disconnected
        LivekitModels.DisconnectReason.SIGNAL_CLOSE -> LkDisconnectReason.signalingConnectionFailure
        LivekitModels.DisconnectReason.ROOM_CLOSED -> LkDisconnectReason.roomDeleted
        LivekitModels.DisconnectReason.USER_UNAVAILABLE -> LkDisconnectReason.disconnected
        LivekitModels.DisconnectReason.USER_REJECTED -> LkDisconnectReason.disconnected
        LivekitModels.DisconnectReason.SIP_TRUNK_FAILURE -> LkDisconnectReason.disconnected
        LivekitModels.DisconnectReason.CONNECTION_TIMEOUT -> LkDisconnectReason.disconnected
        LivekitModels.DisconnectReason.MEDIA_FAILURE -> LkDisconnectReason.disconnected
        else -> LkDisconnectReason.unknown
    }

/**
 * The room-level reason the Android SDK reports, in livekit_client terms.
 * The Android SDK reports an exhausted reconnect as UNKNOWN_REASON (Dart:
 * reconnectAttemptsExceeded); both are recoverable with a fresh join.
 */
fun liveKitDisconnectReason(reason: DisconnectReason): LkDisconnectReason =
    when (reason) {
        DisconnectReason.UNKNOWN_REASON -> LkDisconnectReason.unknown
        DisconnectReason.CLIENT_INITIATED -> LkDisconnectReason.clientInitiated
        DisconnectReason.DUPLICATE_IDENTITY -> LkDisconnectReason.duplicateIdentity
        DisconnectReason.SERVER_SHUTDOWN -> LkDisconnectReason.serverShutdown
        DisconnectReason.PARTICIPANT_REMOVED -> LkDisconnectReason.participantRemoved
        DisconnectReason.ROOM_DELETED -> LkDisconnectReason.roomDeleted
        DisconnectReason.STATE_MISMATCH -> LkDisconnectReason.stateMismatch
        DisconnectReason.JOIN_FAILURE -> LkDisconnectReason.joinFailure
        DisconnectReason.MIGRATION -> LkDisconnectReason.disconnected
        DisconnectReason.SIGNAL_CLOSE -> LkDisconnectReason.signalingConnectionFailure
        DisconnectReason.ROOM_CLOSED -> LkDisconnectReason.roomDeleted
        DisconnectReason.USER_UNAVAILABLE,
        DisconnectReason.USER_REJECTED,
        DisconnectReason.SIP_TRUNK_FAILURE,
        DisconnectReason.CONNECTION_TIMEOUT,
        DisconnectReason.MEDIA_FAILURE,
        -> LkDisconnectReason.disconnected
    }

internal fun Track.Source.toLk(): LkTrackSource =
    when (this) {
        Track.Source.CAMERA -> LkTrackSource.camera
        Track.Source.MICROPHONE -> LkTrackSource.microphone
        Track.Source.SCREEN_SHARE -> LkTrackSource.screenShareVideo
        Track.Source.SCREEN_SHARE_AUDIO -> LkTrackSource.screenShareAudio
        Track.Source.UNKNOWN -> LkTrackSource.unknown
    }

internal fun LkTrackSource.toSdk(): Track.Source =
    when (this) {
        LkTrackSource.camera -> Track.Source.CAMERA
        LkTrackSource.microphone -> Track.Source.MICROPHONE
        LkTrackSource.screenShareVideo -> Track.Source.SCREEN_SHARE
        LkTrackSource.screenShareAudio -> Track.Source.SCREEN_SHARE_AUDIO
        LkTrackSource.unknown -> Track.Source.UNKNOWN
    }

/**
 * Shared LiveKit SDK configuration of the Flutter build's WebRTC setup.
 */
internal object LiveKitAndroidSetup {
    /**
     * The LiveKit signalling connection always connects directly
     * (`HttpOverrides findProxyFromEnvironment: DIRECT` in Dart).
     */
    val directOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().proxy(NetProxy.NO_PROXY).build()
    }

    /** The process-wide `AudioSwitchManager` as one room's LiveKit audio handler. */
    fun audioHandler(): AudioHandler =
        object : AudioHandler {
            private var active = false

            @Synchronized
            override fun start() {
                if (active) return
                active = true
                WebRtc.acquireAudioSwitch()
            }

            @Synchronized
            override fun stop() {
                if (!active) return
                active = false
                WebRtc.releaseAudioSwitch()
            }
        }

    /**
     * Main-room overrides: the process-wide AudioSwitch for routing, the
     * initialization options (software APM, media playback attributes) on the
     * audio device module, and the local input-volume post-processor. LiveKit's
     * communication-mode keep-alive track is off, as flutter_webrtc had none.
     */
    fun mainRoomOverrides(): LiveKitOverrides =
        LiveKitOverrides(
            okHttpClient = directOkHttpClient,
            audioOptions =
                AudioOptions(
                    audioHandler = audioHandler(),
                    javaAudioDeviceModuleCustomizer = WebRtc::javaAudioDeviceModuleCustomizer,
                    disableCommunicationModeWorkaround = true,
                    audioProcessorOptions = AudioProcessorOptions(capturePostProcessor = WebRtc.capturePostProcessing),
                ),
        )

    /** livekit_client's default `AudioCaptureOptions` processing flags. */
    val audioCaptureDefaults =
        LocalAudioTrackOptions(
            noiseSuppression = true,
            echoCancellation = true,
            autoGainControl = true,
            highPassFilter = false,
            typingNoiseDetection = true,
        )
}

/** A remote or local audio track. */
private class AndroidAudioTrack(val track: Track) : LkAudioTrack {
    override suspend fun setVolume(volume: Double) {
        (track as? RemoteAudioTrack)?.setVolume(volume)
    }

    override fun equals(other: Any?): Boolean = other is AndroidAudioTrack && other.track === track

    override fun hashCode(): Int = System.identityHashCode(track)
}

/** A renderable SDK video track, with the room's EGL context for renderers. */
open class AndroidVideoTrack internal constructor(
    val track: VideoTrack,
    val eglBase: EglBase,
) : LkVideoTrack {
    override fun equals(other: Any?): Boolean = other is AndroidVideoTrack && other.track === track

    override fun hashCode(): Int = System.identityHashCode(track)
}

private class AndroidRtpEncoding(val encoding: RtpParameters.Encoding) : LkRtpEncoding {
    override val rid: String? get() = encoding.rid
    override val ssrc: Long? get() = encoding.ssrc
    override var scaleResolutionDownBy: Double?
        get() = encoding.scaleResolutionDownBy
        set(value) {
            encoding.scaleResolutionDownBy = value
        }
    override var maxFramerate: Int?
        get() = encoding.maxFramerate
        set(value) {
            encoding.maxFramerate = value
        }
    override var maxBitrate: Int?
        get() = encoding.maxBitrateBps
        set(value) {
            encoding.maxBitrateBps = value
        }
}

private class AndroidRtpParameters(val parameters: RtpParameters) : LkRtpParameters {
    override val encodings: List<LkRtpEncoding> = parameters.encodings.map(::AndroidRtpEncoding)
}

private class AndroidRtpSender(val sender: RtpSender) : LkRtpSender {
    override val parameters: LkRtpParameters get() = AndroidRtpParameters(sender.parameters)

    override suspend fun setParameters(parameters: LkRtpParameters): Boolean {
        val native = (parameters as AndroidRtpParameters).parameters
        return withContext(Dispatchers.Default) { sender.setParameters(native) }
    }
}

private class AndroidLocalVideoTrack(
    val localTrack: LocalVideoTrack,
    eglBase: EglBase,
    private val owner: AndroidLocalParticipant,
) : AndroidVideoTrack(localTrack, eglBase), LkLocalVideoTrack {
    override val isCameraTrack: Boolean get() = localTrack.capturer is CameraVideoCapturer

    override val cameraPosition: LkCameraPosition?
        get() {
            if (!isCameraTrack) return null
            val position = owner.requestedCameraPositions[localTrack] ?: localTrack.options.position
            return if (position == CameraPosition.BACK) LkCameraPosition.back else LkCameraPosition.front
        }

    /** `LocalVideoTrack.sender` is internal to the SDK module; read it reflectively. */
    override val sender: LkRtpSender?
        get() =
            try {
                val method = LocalVideoTrack::class.java.getMethod("getSender\$livekit_android_sdk_release")
                (method.invoke(localTrack) as? RtpSender)?.let(::AndroidRtpSender)
            } catch (error: ReflectiveOperationException) {
                null
            }

    override fun captureSettings(): Map<String, Any?> {
        val capturer = localTrack.capturer as? OrientationAwareScreenCapturer ?: return emptyMap()
        return mapOf("width" to capturer.captureWidth, "height" to capturer.captureHeight)
    }

    override suspend fun getSenderStats(): List<LkVideoSenderStats> {
        val report = withContext(Dispatchers.Default) { localTrack.getRTCStats() } ?: return emptyList()
        return report.statsMap.values
            .filter { stats ->
                stats.type == "outbound-rtp" &&
                    (stats.members["kind"] ?: stats.members["mediaType"]) == "video"
            }
            .map { stats ->
                val members = stats.members
                LkVideoSenderStats(
                    frameWidth = (members["frameWidth"] as? Number)?.toDouble(),
                    frameHeight = (members["frameHeight"] as? Number)?.toDouble(),
                    framesPerSecond = (members["framesPerSecond"] as? Number)?.toDouble(),
                    rid = members["rid"] as? String,
                    ssrc = (members["ssrc"] as? Number)?.toDouble(),
                )
            }
    }

    override suspend fun setCameraPosition(position: LkCameraPosition) {
        val target = if (position == LkCameraPosition.back) CameraPosition.BACK else CameraPosition.FRONT
        owner.requestedCameraPositions[localTrack] = target
        localTrack.switchCamera(position = target)
    }
}

private open class AndroidTrackPublication(
    val publication: TrackPublication,
    val room: LiveKitAndroidRoom,
) : LkTrackPublication {
    override val sid: String get() = publication.sid
    override val source: LkTrackSource get() = publication.source.toLk()
    override val muted: Boolean get() = publication.muted
    override val track: LkTrack? get() = room.wrapTrack(publication.track)

    override fun equals(other: Any?): Boolean =
        other is AndroidTrackPublication && other.publication === publication

    override fun hashCode(): Int = System.identityHashCode(publication)
}

private class AndroidRemoteTrackPublication(
    val remotePublication: RemoteTrackPublication,
    room: LiveKitAndroidRoom,
) : AndroidTrackPublication(remotePublication, room), LkRemoteTrackPublication {
    override val subscribed: Boolean get() = remotePublication.subscribed

    override suspend fun subscribe() {
        remotePublication.setSubscribed(true)
    }

    override suspend fun unsubscribe() {
        remotePublication.setSubscribed(false)
    }
}

private open class AndroidParticipant(
    open val participant: Participant,
    val room: LiveKitAndroidRoom,
) : LkParticipant {
    override val identity: String get() = participant.identity?.value ?: ""

    protected fun publications(kind: Track.Kind): List<LkTrackPublication> =
        participant.trackPublications.values
            .filter { publication -> publication.kind == kind }
            .map(room::wrapPublication)

    override val audioTrackPublications: List<LkTrackPublication> get() = publications(Track.Kind.AUDIO)
    override val videoTrackPublications: List<LkTrackPublication> get() = publications(Track.Kind.VIDEO)

    override fun getTrackPublicationBySource(source: LkTrackSource): LkTrackPublication? =
        participant.getTrackPublication(source.toSdk())?.let(room::wrapPublication)

    override fun equals(other: Any?): Boolean = other is AndroidParticipant && other.participant === participant

    override fun hashCode(): Int = System.identityHashCode(participant)
}

private class AndroidRemoteParticipant(
    override val participant: RemoteParticipant,
    room: LiveKitAndroidRoom,
) : AndroidParticipant(participant, room), LkRemoteParticipant

private fun ParticipantPermission?.toLk(): LkParticipantPermissions =
    if (this == null) LkParticipantPermissions() else LkParticipantPermissions(canPublish, canSubscribe)

/** Wraps the SDK's errors the way flutter_webrtc surfaced them (no JVM error types). */
private suspend fun <T> sdkCall(block: suspend () -> T): T =
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw error
    } catch (error: Throwable) {
        if (error is VirtualMachineError) throw error
        throw LkTrackCreateException(error.message ?: "Failed to create track")
    }

private class AndroidLocalParticipant(
    override val participant: LocalParticipant,
    room: LiveKitAndroidRoom,
) : AndroidParticipant(participant, room), LkLocalParticipant {
    /** Camera positions requested through flips (the SDK updates its options asynchronously). */
    val requestedCameraPositions: MutableMap<LocalVideoTrack, CameraPosition> =
        java.util.WeakHashMap()

    override val permissions: LkParticipantPermissions get() = participant.permissions.toLk()

    override fun getTrackPublicationBySid(sid: String): LkTrackPublication? =
        participant.trackPublications[sid]?.let(room::wrapPublication)

    override suspend fun setMicrophoneEnabled(enabled: Boolean, audioCaptureOptions: LkAudioCaptureOptions?) {
        sdkCall {
            // A new microphone track goes through getUserMedia's permission prompt;
            // an existing publication is only unmuted. The capture device follows
            // the input chosen in settings (flutter_webrtc ignored the per-track
            // deviceId on Android).
            if (enabled && participant.getTrackPublication(Track.Source.MICROPHONE) == null) {
                WebRtcRequests.ensureUserMediaPermissions(listOf(Manifest.permission.RECORD_AUDIO))
            }
            if (!participant.setMicrophoneEnabled(enabled)) throw LkTrackPublishException()
        }
    }

    override suspend fun setCameraEnabled(enabled: Boolean) {
        sdkCall {
            if (enabled && participant.getTrackPublication(Track.Source.CAMERA) == null) {
                WebRtcRequests.ensureUserMediaPermissions(listOf(Manifest.permission.CAMERA))
            }
            if (!participant.setCameraEnabled(enabled)) throw LkTrackPublishException()
        }
    }

    override suspend fun setScreenShareEnabled(
        enabled: Boolean,
        captureScreenAudio: Boolean,
        screenShareCaptureOptions: LkScreenShareCaptureOptions?,
        screenSharePublishOptions: LkVideoPublishOptions?,
    ) {
        sdkCall {
            val existing = participant.getTrackPublication(Track.Source.SCREEN_SHARE)
            if (enabled) {
                if (existing != null) {
                    existing.muted = false
                    return@sdkCall
                }
                _publishScreenShare(screenSharePublishOptions ?: room.roomOptions.defaultVideoPublishOptions)
            } else {
                val track = existing?.track ?: return@sdkCall
                participant.unpublishTrack(track)
                participant.getTrackPublication(Track.Source.SCREEN_SHARE_AUDIO)?.track?.let { participant.unpublishTrack(it) }
                withContext(Dispatchers.Default) { track.dispose() }
            }
        }
    }

    /**
     * flutter_webrtc's Android `getDisplayMedia` (with the Gang Chat fork's
     * changes) published through LiveKit: MediaProjection consent, the
     * screen-capture foreground service, then an orientation-aware capturer at
     * the display's real size and 30 fps. The publish encoding carries the
     * selected frame-rate and bitrate caps; LiveSession scales the sender down
     * to the selected height afterwards.
     */
    private suspend fun _publishScreenShare(publishOptions: LkVideoPublishOptions) {
        val projectionData = WebRtcRequests.requestScreenCapture()
        WebRtcRequests.startScreenCaptureForegroundService()
        val context = GangChatPlatform.context
        val capturer =
            OrientationAwareScreenCapturer(
                projectionData,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        // After Huawei P30 and Android 10 version test, the onstop method is
                        // called, which will not affect the next process, and there is no
                        // need to report an error.
                    }
                },
            )
        val (displayWidth, displayHeight) = _realDisplaySize(context)
        val track =
            participant.createVideoTrack(
                name = "screenshare",
                capturer = capturer,
                options =
                    LocalVideoTrackOptions(
                        isScreencast = true,
                        captureParams =
                            VideoCaptureParameter(
                                width = displayWidth,
                                height = displayHeight,
                                maxFps = _defaultScreenCaptureFps,
                                adaptOutputToDimensions = false,
                            ),
                    ),
            )
        var published = false
        try {
            track.startCapture()
            val encoding = publishOptions.screenShareEncoding
            published =
                participant.publishVideoTrack(
                    track,
                    VideoTrackPublishOptions(
                        name = "screenshare",
                        base =
                            VideoTrackPublishDefaults(
                                videoEncoding = encoding?.let { VideoEncoding(maxBitrate = it.maxBitrate, maxFps = it.maxFramerate) },
                                simulcast = publishOptions.simulcast,
                            ),
                        source = Track.Source.SCREEN_SHARE,
                    ),
                )
            if (!published) throw LkTrackPublishException()
        } finally {
            if (!published) {
                withContext(Dispatchers.Default) {
                    try {
                        track.stopCapture()
                    } catch (_: Exception) {
                    }
                    track.dispose()
                }
            }
        }
    }

    override suspend fun removePublishedTrack(trackSid: String) {
        val track = participant.trackPublications[trackSid]?.track ?: return
        participant.unpublishTrack(track)
    }

    /** The Android SDK cannot send `UpdateLocalVideoTrack` dimension updates. */
    override fun updatePublishedVideoTrackDimensions(trackSid: String, dimensions: LkVideoDimensions): Boolean = false

    private companion object {
        /** flutter_webrtc's `GetUserMediaImpl.DEFAULT_FPS`. */
        const val _defaultScreenCaptureFps = 30

        @Suppress("DEPRECATION")
        fun _realDisplaySize(context: Context): Pair<Int, Int> {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            return metrics.widthPixels to metrics.heightPixels
        }
    }
}

/**
 * An [LkRoom] on the LiveKit Android SDK, configured like the Flutter build's
 * WebRTC (see [LiveKitAndroidSetup]).
 *
 * Events: the SDK emits room events on its own threads; they are forwarded,
 * in order, to the app's event loop. The SDK does not surface the signal
 * "resume" phase of a reconnect nor per-participant disconnect reasons, which
 * the Dart fork relied on (RoomResumingEvent, participant-removed departures).
 * Both come from the engine callbacks, observed by wrapping the room's
 * internal engine listener; if that wrapper cannot be installed, reconnects
 * are reported only as full reconnects and departures as plain leaves.
 */
internal class LiveKitAndroidRoom(
    override val roomOptions: LkRoomOptions,
    context: Context = GangChatPlatform.context,
) : LkRoom {
    val room: Room =
        LiveKit.create(
            appContext = context,
            options =
                RoomOptions(
                    adaptiveStream = roomOptions.adaptiveStream,
                    dynacast = roomOptions.dynacast,
                    audioTrackCaptureDefaults = LiveKitAndroidSetup.audioCaptureDefaults,
                    screenShareTrackPublishDefaults =
                        VideoTrackPublishDefaults(
                            videoEncoding =
                                roomOptions.defaultVideoPublishOptions.screenShareEncoding?.let {
                                    VideoEncoding(maxBitrate = it.maxBitrate, maxFps = it.maxFramerate)
                                },
                        ),
                ),
            overrides = LiveKitAndroidSetup.mainRoomOverrides(),
        )

    private val _local = AndroidLocalParticipant(room.localParticipant, this)
    private val _eglBase: EglBase get() = room.lkObjects.eglBase
    private val _disconnectReasons = ConcurrentHashMap<String, LkDisconnectReason>()
    private val _listeners = CopyOnWriteArrayList<Channel<Any>>()
    private val _engineCallbacksObserved: Boolean

    init {
        (room.lkObjects.audioDeviceModule as? JavaAudioDeviceModule)?.let(WebRtc::registerAudioDeviceModule)
        _engineCallbacksObserved = _observeEngineCallbacks()
    }

    override val connectionState: LkConnectionState
        get() =
            when (room.state) {
                Room.State.CONNECTING -> LkConnectionState.connecting
                Room.State.CONNECTED -> LkConnectionState.connected
                Room.State.RECONNECTING -> LkConnectionState.reconnecting
                Room.State.DISCONNECTED -> LkConnectionState.disconnected
            }

    override val localParticipant: LkLocalParticipant get() = _local

    override val remoteParticipants: Map<String, LkRemoteParticipant>
        get() {
            val result = LinkedHashMap<String, LkRemoteParticipant>()
            for ((identity, participant) in room.remoteParticipants) {
                result[identity.value] = AndroidRemoteParticipant(participant, this)
            }
            return result
        }

    internal fun wrapPublication(publication: TrackPublication): LkTrackPublication =
        if (publication is RemoteTrackPublication) {
            AndroidRemoteTrackPublication(publication, this)
        } else {
            AndroidTrackPublication(publication, this)
        }

    internal fun wrapTrack(track: Track?): LkTrack? =
        when (track) {
            null -> null
            is LocalVideoTrack -> AndroidLocalVideoTrack(track, _eglBase, _local)
            is VideoTrack -> AndroidVideoTrack(track, _eglBase)
            else -> AndroidAudioTrack(track)
        }

    private fun _wrapParticipant(participant: Participant): LkParticipant =
        when (participant) {
            is LocalParticipant -> _local
            is RemoteParticipant -> AndroidRemoteParticipant(participant, this)
            else -> AndroidParticipant(participant, this)
        }

    override fun listen(onEvent: (LkRoomEvent) -> Unit): LkCancelListenFunc {
        val channel = Channel<Any>(Channel.UNLIMITED)
        _listeners.add(channel)
        // Enqueue on the SDK's emitting thread so synthetic engine events and SDK
        // events keep their relative order; deliver on the app's event loop.
        val collector =
            CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
                room.events.events.collect { event -> channel.trySend(event) }
            }
        val consumer =
            AppCoroutines.scope.launch(AppCoroutines.loop) {
                for (item in channel) {
                    val event = _convert(item) ?: continue
                    onEvent(event)
                }
            }
        return {
            _listeners.remove(channel)
            collector.cancel()
            consumer.cancel()
            channel.close()
        }
    }

    private fun _post(event: LkRoomEvent) {
        for (channel in _listeners) channel.trySend(event)
    }

    private fun _convert(item: Any): LkRoomEvent? =
        when (item) {
            is LkRoomEvent -> item
            is RoomEvent.Reconnecting -> if (_engineCallbacksObserved) null else LkRoomReconnectingEvent()
            is RoomEvent.Reconnected -> LkRoomReconnectedEvent()
            is RoomEvent.Disconnected ->
                // A failed join is reported by the connect call itself.
                if (item.reason == DisconnectReason.JOIN_FAILURE) {
                    null
                } else {
                    LkRoomDisconnectedEvent(liveKitDisconnectReason(item.reason))
                }
            is RoomEvent.ActiveSpeakersChanged -> LkActiveSpeakersChangedEvent(item.speakers.map(::_wrapParticipant))
            is RoomEvent.ParticipantConnected ->
                LkParticipantConnectedEvent(AndroidRemoteParticipant(item.participant, this))
            is RoomEvent.ParticipantDisconnected -> {
                val identity = item.participant.identity?.value ?: ""
                LkParticipantDisconnectedEvent(
                    AndroidRemoteParticipant(item.participant, this),
                    reason = _disconnectReasons.remove(identity),
                )
            }
            is RoomEvent.ParticipantPermissionsChanged ->
                LkParticipantPermissionsUpdatedEvent(
                    participant = _wrapParticipant(item.participant),
                    permissions = item.newPermissions.toLk(),
                    oldPermissions = item.oldPermissions.toLk(),
                )
            is RoomEvent.LocalTrackSubscribed -> LkLocalTrackSubscribedEvent(item.publication.sid)
            is RoomEvent.TrackSubscribed -> LkTrackLifecycleEvent(LkTrackLifecycleEvent.Kind.trackSubscribed)
            is RoomEvent.TrackUnsubscribed -> LkTrackLifecycleEvent(LkTrackLifecycleEvent.Kind.trackUnsubscribed)
            is RoomEvent.TrackPublished ->
                LkTrackLifecycleEvent(
                    if (item.participant is LocalParticipant) {
                        LkTrackLifecycleEvent.Kind.localTrackPublished
                    } else {
                        LkTrackLifecycleEvent.Kind.trackPublished
                    },
                )
            is RoomEvent.TrackUnpublished ->
                LkTrackLifecycleEvent(
                    if (item.participant is LocalParticipant) {
                        LkTrackLifecycleEvent.Kind.localTrackUnpublished
                    } else {
                        LkTrackLifecycleEvent.Kind.trackUnpublished
                    },
                )
            is RoomEvent.TrackMuted -> LkTrackLifecycleEvent(LkTrackLifecycleEvent.Kind.trackMuted)
            is RoomEvent.TrackUnmuted -> LkTrackLifecycleEvent(LkTrackLifecycleEvent.Kind.trackUnmuted)
            else -> null
        }

    private fun _reconnectSnapshot(): Pair<Set<String>, Boolean> {
        val identities = room.remoteParticipants.keys.map { it.value }.toSet()
        val hadMicrophone = room.localParticipant.getTrackPublication(Track.Source.MICROPHONE) != null
        return identities to hadMicrophone
    }

    /** Runs on the SDK's engine thread before the room handles the callback. */
    private fun _beforeEngineCallback(name: String, args: Array<out Any?>?) {
        when (name) {
            "onEngineResuming" -> {
                val (identities, hadMicrophone) = _reconnectSnapshot()
                _post(LkRoomResumingEvent(identities, hadMicrophone))
            }
            "onEngineResumed" -> _post(LkRoomReconnectedEvent())
            "onEngineReconnecting" -> {
                val (identities, hadMicrophone) = _reconnectSnapshot()
                _post(LkRoomReconnectingEvent(identities, hadMicrophone))
            }
            "onUpdateParticipants" -> {
                val updates = args?.firstOrNull() as? List<*> ?: return
                for (info in updates) {
                    if (info !is LivekitModels.ParticipantInfo) continue
                    if (info.state != LivekitModels.ParticipantInfo.State.DISCONNECTED) continue
                    val identity = info.identity
                    if (identity.isNullOrEmpty()) continue
                    _disconnectReasons[identity] = liveKitProtocolDisconnectReason(info.disconnectReason)
                }
            }
        }
    }

    /**
     * Wraps the room's internal `RTCEngine.Listener` (Kotlin-internal API,
     * reached reflectively) so engine callbacks the SDK does not turn into
     * room events can be observed. Every call is forwarded unchanged.
     */
    private fun _observeEngineCallbacks(): Boolean =
        try {
            val engine = Room::class.java.getMethod("getEngine\$livekit_android_sdk_release").invoke(room)
            val listenerClass = Class.forName("io.livekit.android.room.RTCEngine\$Listener")
            val getter = engine.javaClass.getMethod("getListener\$livekit_android_sdk_release")
            val setter = engine.javaClass.getMethod("setListener\$livekit_android_sdk_release", listenerClass)
            val original = getter.invoke(engine)
            if (original == null) {
                false
            } else {
                val proxy =
                    Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { _, method, args ->
                        if (method.declaringClass == listenerClass) {
                            try {
                                _beforeEngineCallback(method.name, args)
                            } catch (error: RuntimeException) {
                                debugPrint("live: engine callback observer failed: $error")
                            }
                        }
                        try {
                            if (args == null) method.invoke(original) else method.invoke(original, *args)
                        } catch (error: InvocationTargetException) {
                            throw error.targetException
                        }
                    }
                setter.invoke(engine, proxy)
                true
            }
        } catch (error: Exception) {
            debugPrint("live: engine callbacks unavailable ($error); reconnects are reported as full reconnects")
            false
        }

    override suspend fun connect(url: String, token: String) {
        sdkCall { room.connect(url, token) }
    }

    override suspend fun disconnect() {
        // Room.disconnect blocks on its state lock; keep it off the main thread.
        withContext(Dispatchers.Default) { room.disconnect() }
    }

    override suspend fun dispose() {
        withContext(Dispatchers.Default) { room.release() }
    }
}

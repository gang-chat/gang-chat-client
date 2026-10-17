package com.gangchat.client.live

/*
 * The subset of the livekit_client (Dart) API that LiveSession and
 * ScreenAudioPublisher use, as Kotlin interfaces. The production
 * implementation (LiveKitAndroidRoom.kt) is backed by the LiveKit Android
 * SDK; tests drive LiveSession through fakes and `debugHandleRoomEvent`, as
 * the Dart tests did. Names follow livekit_client with an `Lk` prefix.
 */

/** livekit_client's `TrackSource`. */
enum class LkTrackSource { unknown, camera, microphone, screenShareVideo, screenShareAudio }

/** livekit_client's `ConnectionState`. */
enum class LkConnectionState { disconnected, connecting, reconnecting, connected }

/** livekit_client's `DisconnectReason` (`types/other.dart`). */
enum class LkDisconnectReason {
    unknown,
    clientInitiated,
    duplicateIdentity,
    serverShutdown,
    participantRemoved,
    roomDeleted,
    stateMismatch,
    joinFailure,
    disconnected,
    signalingConnectionFailure,
    reconnectAttemptsExceeded,
}

/** livekit_client's `CameraPosition`. */
enum class LkCameraPosition { front, back }

/** livekit_client's `ParticipantPermissions` (the fields the app reads). */
class LkParticipantPermissions(
    val canPublish: Boolean = true,
    val canSubscribe: Boolean = true,
)

/** livekit_client's `Priority`. */
enum class LkPriority { veryLow, low, medium, high }

/** livekit_client's `VideoEncoding`. */
class LkVideoEncoding(
    val maxFramerate: Int,
    val maxBitrate: Int,
    val bitratePriority: LkPriority? = null,
    val networkPriority: LkPriority? = null,
)

/** livekit_client's `VideoDimensions`. */
class LkVideoDimensions(val width: Int, val height: Int)

/** livekit_client's `VideoParameters`. */
class LkVideoParameters(val dimensions: LkVideoDimensions, val encoding: LkVideoEncoding? = null)

/** livekit_client's `AudioCaptureOptions` (the fields the app sets). */
class LkAudioCaptureOptions(
    val stopAudioCaptureOnMute: Boolean = true,
    val deviceId: String? = null,
)

/** livekit_client's `ScreenShareCaptureOptions` (the fields the app sets). */
class LkScreenShareCaptureOptions(
    val captureScreenAudio: Boolean = false,
    val sourceId: String? = null,
    val maxFrameRate: Double? = null,
    val params: LkVideoParameters? = null,
)

/** livekit_client's `VideoPublishOptions` (the fields the app sets). */
class LkVideoPublishOptions(
    val simulcast: Boolean = true,
    val screenShareEncoding: LkVideoEncoding? = null,
) {
    fun copyWith(simulcast: Boolean? = null, screenShareEncoding: LkVideoEncoding? = null): LkVideoPublishOptions =
        LkVideoPublishOptions(
            simulcast = simulcast ?: this.simulcast,
            screenShareEncoding = screenShareEncoding ?: this.screenShareEncoding,
        )
}

/** livekit_client's `RoomOptions` (the fields the app sets). */
class LkRoomOptions(
    val adaptiveStream: Boolean = false,
    val dynacast: Boolean = false,
    val defaultAudioCaptureOptions: LkAudioCaptureOptions = LkAudioCaptureOptions(),
    val defaultVideoPublishOptions: LkVideoPublishOptions = LkVideoPublishOptions(),
)

/** A media track. */
interface LkTrack

/** A remote or local audio track. */
interface LkAudioTrack : LkTrack {
    /** `RemoteAudioTrack.setVolume`: WebRTC playout gain, 0..10. */
    suspend fun setVolume(volume: Double)
}

/** A renderable video track. */
interface LkVideoTrack : LkTrack

/** One RTP encoding of a sender (`RTCRtpEncoding`), mutable like the Dart object. */
interface LkRtpEncoding {
    val rid: String?
    val ssrc: Long?
    var scaleResolutionDownBy: Double?
    var maxFramerate: Int?
    var maxBitrate: Int?
}

/** `RTCRtpParameters`: [encodings] edits apply on [LkRtpSender.setParameters]. */
interface LkRtpParameters {
    val encodings: List<LkRtpEncoding>?
}

/** `RTCRtpSender`. */
interface LkRtpSender {
    val parameters: LkRtpParameters

    suspend fun setParameters(parameters: LkRtpParameters): Boolean
}

/** One outbound-rtp sample of `LocalVideoTrack.getSenderStats()`. */
class LkVideoSenderStats(
    val frameWidth: Double? = null,
    val frameHeight: Double? = null,
    val framesPerSecond: Double? = null,
    val rid: String? = null,
    val ssrc: Double? = null,
)

/** livekit_client's `LocalVideoTrack` (the parts the session uses). */
interface LkLocalVideoTrack : LkVideoTrack {
    /** The camera position for a camera track; null for a screen share. */
    val cameraPosition: LkCameraPosition?

    /** True for a camera capture track (`currentOptions is CameraCaptureOptions`). */
    val isCameraTrack: Boolean

    val sender: LkRtpSender?

    /** `mediaStreamTrack.getSettings()` width/height, if the platform reports them. */
    fun captureSettings(): Map<String, Any?>

    suspend fun getSenderStats(): List<LkVideoSenderStats>

    /** `LocalVideoTrackExt.setCameraPosition`. */
    suspend fun setCameraPosition(position: LkCameraPosition)
}

interface LkTrackPublication {
    val sid: String
    val source: LkTrackSource
    val muted: Boolean
    val track: LkTrack?
}

interface LkRemoteTrackPublication : LkTrackPublication {
    val subscribed: Boolean

    suspend fun subscribe()

    suspend fun unsubscribe()
}

interface LkParticipant {
    val identity: String
    val audioTrackPublications: List<LkTrackPublication>
    val videoTrackPublications: List<LkTrackPublication>

    fun getTrackPublicationBySource(source: LkTrackSource): LkTrackPublication?
}

interface LkRemoteParticipant : LkParticipant

interface LkLocalParticipant : LkParticipant {
    val permissions: LkParticipantPermissions

    fun getTrackPublicationBySid(sid: String): LkTrackPublication?

    suspend fun setMicrophoneEnabled(enabled: Boolean, audioCaptureOptions: LkAudioCaptureOptions? = null)

    suspend fun setCameraEnabled(enabled: Boolean)

    suspend fun setScreenShareEnabled(
        enabled: Boolean,
        captureScreenAudio: Boolean = false,
        screenShareCaptureOptions: LkScreenShareCaptureOptions? = null,
        screenSharePublishOptions: LkVideoPublishOptions? = null,
    )

    suspend fun removePublishedTrack(trackSid: String)

    /**
     * Announces new published dimensions to the server. Returns false when
     * the platform SDK cannot send the update.
     */
    fun updatePublishedVideoTrackDimensions(trackSid: String, dimensions: LkVideoDimensions): Boolean
}

/** livekit_client's `CancelListenFunc`. */
typealias LkCancelListenFunc = () -> Unit

/** livekit_client's `Room`. */
interface LkRoom {
    val roomOptions: LkRoomOptions
    val connectionState: LkConnectionState
    val localParticipant: LkLocalParticipant?
    val remoteParticipants: Map<String, LkRemoteParticipant>

    /** Delivers room events on the app's event loop until cancelled. */
    fun listen(onEvent: (LkRoomEvent) -> Unit): LkCancelListenFunc

    suspend fun connect(url: String, token: String)

    suspend fun disconnect()

    suspend fun dispose()
}

/** livekit_client's `RoomEvent`s that LiveSession handles. */
sealed class LkRoomEvent

/**
 * livekit_client's `ReconnectingEvent` mixin. The Android SDK changes the
 * room's participants on its own threads, so the adapter snapshots them when
 * the transport starts reconnecting: [participantIdentities] are the remote
 * identities and [hadMicrophonePublication] whether the local microphone was
 * published at that moment (null when unknown).
 */
sealed class LkReconnectingEvent(
    val participantIdentities: Set<String>? = null,
    val hadMicrophonePublication: Boolean? = null,
) : LkRoomEvent()

class LkRoomReconnectingEvent(
    participantIdentities: Set<String>? = null,
    hadMicrophonePublication: Boolean? = null,
) : LkReconnectingEvent(participantIdentities, hadMicrophonePublication)

class LkRoomResumingEvent(
    participantIdentities: Set<String>? = null,
    hadMicrophonePublication: Boolean? = null,
) : LkReconnectingEvent(participantIdentities, hadMicrophonePublication)

class LkRoomReconnectedEvent : LkRoomEvent()

class LkRoomDisconnectedEvent(val reason: LkDisconnectReason? = null) : LkRoomEvent()

class LkActiveSpeakersChangedEvent(val speakers: List<LkParticipant>) : LkRoomEvent()

class LkParticipantConnectedEvent(val participant: LkRemoteParticipant) : LkRoomEvent()

class LkParticipantDisconnectedEvent(
    val participant: LkRemoteParticipant,
    val reason: LkDisconnectReason? = null,
) : LkRoomEvent()

class LkParticipantPermissionsUpdatedEvent(
    val participant: LkParticipant,
    val permissions: LkParticipantPermissions,
    val oldPermissions: LkParticipantPermissions,
) : LkRoomEvent()

class LkLocalTrackSubscribedEvent(val trackSid: String) : LkRoomEvent()

/**
 * The publish/subscribe/mute lifecycle events that make LiveSession rebuild
 * its rendered tracks: livekit_client's TrackSubscribed, TrackUnsubscribed,
 * LocalTrackPublished, LocalTrackUnpublished, TrackPublished,
 * TrackUnpublished, TrackMuted and TrackUnmuted events.
 */
class LkTrackLifecycleEvent(val kind: Kind) : LkRoomEvent() {
    enum class Kind {
        trackSubscribed,
        trackUnsubscribed,
        localTrackPublished,
        localTrackUnpublished,
        trackPublished,
        trackUnpublished,
        trackMuted,
        trackUnmuted,
    }
}

/** livekit_client's `TrackPublishException`. */
class LkTrackPublishException(message: String = "Failed to publish track") : Exception(message) {
    override fun toString(): String = "LiveKit Exception: [TrackPublishException] $message"
}

/** livekit_client's `TrackCreateException`. */
class LkTrackCreateException(message: String = "Failed to create track") : Exception(message) {
    override fun toString(): String = "LiveKit Exception: [TrackCreateException] $message"
}

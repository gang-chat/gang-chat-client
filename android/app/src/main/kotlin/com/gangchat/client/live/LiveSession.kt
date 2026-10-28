package com.gangchat.client.live

import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.audioVolumeBoostFactor
import com.gangchat.client.app.boostedAudioVolume
import com.gangchat.client.app.defaultAudioVolume
import com.gangchat.client.app.defaultInputVolume
import com.gangchat.client.app.defaultOutputVolume
import com.gangchat.client.app.defaultParticipantVoiceVolume
import com.gangchat.client.app.maxParticipantVoiceVolume
import com.gangchat.client.app.normalizedAudioVolume
import com.gangchat.client.app.normalizedParticipantVoiceVolume
import com.gangchat.client.foundation.ChangeNotifier
import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.VisibleForTesting
import com.gangchat.client.foundation.completedFuture
import com.gangchat.client.foundation.debugPrint
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.timeout
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.platform.AndroidAudioRoute
import com.gangchat.client.platform.webrtc.AndroidAudioAttributesContentType
import com.gangchat.client.platform.webrtc.AndroidAudioAttributesUsageType
import com.gangchat.client.platform.webrtc.AndroidAudioConfiguration
import com.gangchat.client.platform.webrtc.AndroidAudioFocusMode
import com.gangchat.client.platform.webrtc.AndroidAudioMode
import com.gangchat.client.platform.webrtc.AndroidAudioStreamType
import com.gangchat.client.platform.webrtc.WebRtc
import com.gangchat.client.protocol.musicBoxBotIdentity
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Deferred

typealias CameraVideoInputCountProvider = suspend () -> Int

/** Creates the LiveKit room for a connection (the SDK-backed room in production). */
typealias LiveRoomFactory = (options: LkRoomOptions) -> LkRoom

private suspend fun _defaultCameraVideoInputCountProvider(): Int {
    val devices = WebRtc.enumerateDevices()
    return devices
        .filter { device -> device.kind == "videoinput" }
        .map { device -> device.deviceId }
        .filter { deviceId -> deviceId.isNotEmpty() }
        .toSet()
        .size
}

/**
 * Resolves an Android camera-device probe without treating an unavailable
 * pre-permission list as confirmed single-camera hardware.
 */
@VisibleForTesting
fun cameraFlipAvailabilityFromVideoInputCount(inputCount: Int): Boolean? {
    if (inputCount <= 0) return null
    return inputCount > 1
}

/**
 * Suffix of the hidden screen-audio aux participant identity
 * (`<ownerId>--screen-audio`). These participants publish screen-share audio
 * through an isolated factory and must be filtered out of the UI (they are
 * never real users). On the owner's own receiver, their audio must also be
 * muted to avoid self-echo. The `--` separator is URL-safe (unlike `#`,
 * which is truncated as a URL fragment in the LiveKit WebSocket signal path,
 * causing the aux identity to collide with the main room participant).
 */
private const val _screenAudioAuxSuffix = "--screen-audio"

/** Whether [identity] is a hidden screen-audio aux participant. */
private fun _isScreenAudioAux(identity: String): Boolean = identity.endsWith(_screenAudioAuxSuffix)

/**
 * Whether [identity] is the LOCAL user's own screen-audio aux participant
 * (the one whose audio would echo back if played). [localIdentity] is the
 * main room's local participant identity (== userID).
 */
private fun _isOwnScreenAudioAux(identity: String, localIdentity: String?): Boolean {
    if (localIdentity == null || localIdentity.isEmpty()) return false
    return identity == "$localIdentity$_screenAudioAuxSuffix"
}

fun isLivePresenceSoundParticipantIdentity(identity: String): Boolean {
    return identity.isNotEmpty() &&
        !_isScreenAudioAux(identity) &&
        identity != musicBoxBotIdentity
}

/**
 * Android WebRTC uses one native playback device for the complete room mix.
 * The device is created with media AudioAttributes during app startup. Use
 * normal/media mode while the music-box track is present so Android binds the
 * actual playback and hardware volume keys to media volume, then restore the
 * communication preset when the track disappears. Software WebRTC AEC stays
 * enabled in both modes.
 */
@VisibleForTesting
fun androidLiveAudioConfiguration(musicBoxActive: Boolean): AndroidAudioConfiguration {
    if (!musicBoxActive) return AndroidAudioConfiguration.communication
    return AndroidAudioConfiguration(
        manageAudioFocus = true,
        androidAudioMode = AndroidAudioMode.normal,
        androidAudioFocusMode = AndroidAudioFocusMode.gain,
        androidAudioStreamType = AndroidAudioStreamType.music,
        androidAudioAttributesUsageType = AndroidAudioAttributesUsageType.media,
        androidAudioAttributesContentType = AndroidAudioAttributesContentType.music,
        forceHandleAudioRouting = true,
    )
}

@VisibleForTesting
class LiveReconnectPresenceChanges(
    val joined: Set<String>,
    val left: Set<String>,
)

/**
 * Computes real presence changes which happened while LiveKit callbacks were
 * suppressed during a reconnect. A full transport restart removes and then
 * recreates every remote participant, so replaying those raw SDK events would
 * produce false leave/join sounds for users who never left the room.
 */
@VisibleForTesting
fun liveReconnectPresenceChanges(before: Set<String>, after: Set<String>): LiveReconnectPresenceChanges {
    return LiveReconnectPresenceChanges(
        joined = after - before,
        left = before - after,
    )
}

/**
 * A publication object can survive signal recovery even when its underlying
 * publisher SDP has fallen back to `recvonly`. In that state mute/unmute is
 * insufficient because it never creates a new sender or renegotiates media.
 */
@VisibleForTesting
fun shouldRepublishLocalMicrophoneAfterReconnect(
    resumedSignalConnection: Boolean,
    hadPublicationBeforeReconnect: Boolean,
    canPublish: Boolean,
): Boolean {
    return resumedSignalConnection && hadPublicationBeforeReconnect && canPublish
}

private val _liveReconnectMicrophoneRetryDelays: List<Duration> =
    listOf(
        Duration.ZERO,
        400.milliseconds,
        1200.milliseconds,
    )

/**
 * Retries a reconnect-only microphone publication after the LiveKit
 * publisher transport has reported itself connected.
 *
 * Native WebRTC teardown can finish slightly after RoomReconnectedEvent. A
 * first publication attempt may therefore race a closing receiver even
 * though the subscriber path is already usable. Keep retry policy independent
 * from the platform implementation so Windows, macOS and Android recover with
 * identical bounded semantics.
 */
@VisibleForTesting
suspend fun retryLiveReconnectMicrophoneRecovery(
    attempt: suspend () -> Boolean,
    isCurrent: () -> Boolean,
    retryDelays: List<Duration> = _liveReconnectMicrophoneRetryDelays,
    wait: (suspend (Duration) -> Unit)? = null,
): Boolean {
    val waitFor: suspend (Duration) -> Unit = wait ?: { delay -> delayed(delay) }
    for (delay in retryDelays) {
        if (!isCurrent()) return false
        if (delay > Duration.ZERO) waitFor(delay)
        if (!isCurrent()) return false
        try {
            if (attempt()) return true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // The caller owns diagnostics and the final unavailable-state update.
        }
    }
    return false
}

enum class LiveParticipantDepartureKind { left, removed }

fun liveParticipantDepartureKind(reason: LkDisconnectReason?): LiveParticipantDepartureKind {
    return if (reason == LkDisconnectReason.participantRemoved) {
        LiveParticipantDepartureKind.removed
    } else {
        LiveParticipantDepartureKind.left
    }
}

/**
 * Returns LiveKit's microphone mute state only after a publication exists.
 *
 * During reconnect LiveKit can restore the participant before restoring its
 * microphone publication. Treating a missing publication as muted would
 * temporarily override the app server's authoritative logical mute state.
 */
@VisibleForTesting
fun liveParticipantMicPublicationMuted(participant: LkParticipant): Boolean? {
    return participant.getTrackPublicationBySource(LkTrackSource.microphone)?.muted
}

/**
 * Serializes remote-video subscription reconciliation and makes newer
 * selections supersede work that has not started yet.
 *
 * LiveKit's subscribe/unsubscribe methods only enqueue signal messages; they
 * do not wait for the server to confirm the resulting state. Keeping each
 * media kind on a single ordered tail ensures that a stale unsubscribe cannot
 * be sent after the user's newer selection.
 */
@VisibleForTesting
class LatestLiveSubscriptionReconciler {
    private var _tail: Deferred<Unit> = completedFuture(Unit)
    private var _revision = 0

    fun schedule(reconcile: suspend (isCurrent: () -> Boolean) -> Unit): Deferred<Unit> {
        val revision = ++_revision
        val previous = _tail
        val next =
            future {
                try {
                    previous.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                if (revision != _revision) return@future
                reconcile { revision == _revision }
            }
        _tail =
            future {
                try {
                    next.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        return next
    }

    fun invalidate() {
        _revision += 1
    }
}

@VisibleForTesting
fun shouldApplyLivePublishPermissionUpdate(
    currentCanPublish: Boolean,
    oldCanPublish: Boolean,
    nextCanPublish: Boolean,
): Boolean {
    if (oldCanPublish == nextCanPublish) return false
    return currentCanPublish != nextCanPublish
}

private fun _screenShareAudioOwnerIdentity(identity: String): String {
    if (!_isScreenAudioAux(identity)) return identity
    return identity.substring(0, identity.length - _screenAudioAuxSuffix.length)
}

/**
 * A capturable desktop source (a whole screen or a single window), used to
 * populate the desktop screen-share picker. Android shares through the system
 * MediaProjection prompt, so [LiveSession.listScreenSources] is always empty
 * here; the type stays part of the controller API.
 */
class ScreenSource(
    val id: String,
    val name: String,
    val thumbnail: ByteArray?,
    val isWindow: Boolean,
    thumbnailKey: String? = null,
    val thumbnailUpdates: com.gangchat.client.foundation.Stream<ByteArray>? = null,
) {
    val thumbnailKey: String = thumbnailKey ?: id
}

/** Desktop picker platforms (Windows / macOS / Linux): never on Android. */
val supportsDesktopScreenShare: Boolean get() = false

/** Android shares the screen through MediaProjection. */
val supportsLocalScreenShare: Boolean get() = true

/** Browser screen share (web only): never on Android. */
val supportsBrowserScreenShare: Boolean get() = false

@Suppress("UNUSED_PARAMETER")
@VisibleForTesting
fun shouldRequestScreenShareAudio(
    sourceId: String?,
    isDesktopSourcePickerPlatform: Boolean,
    isWindowsDesktop: Boolean,
    isAndroidPlatform: Boolean,
): Boolean {
    // Screen audio is published by ScreenAudioPublisher through a separate
    // native factory/aux participant. getDisplayMedia still receives audio=true
    // so platform capture can be configured, but the returned stream remains
    // video-only and is never published on the main factory.
    return isDesktopSourcePickerPlatform || isAndroidPlatform
}

/**
 * A single video track currently published in the live room, tagged with
 * whose it is and whether it's a camera or a screen share. The UI uses these
 * to render participant video tiles.
 */
class LiveVideoTrack(
    val identity: String,
    val track: LkVideoTrack,
    val isScreenShare: Boolean,
    val isLocal: Boolean,
)

/**
 * Wraps a LiveKit room and exposes a small UI-friendly snapshot of who is
 * currently speaking, whose microphone is muted, and which video tracks are
 * live.
 *
 * The server signs LiveKit tokens with `identity == user.id`, so a LiveKit
 * participant's identity matches `UserSummary.id` from the protocol layer.
 * The UI uses that as the join key.
 *
 * [audioDeviceRebinderFactory] builds the production recovery hook that
 * resolves and rebinds input/output together after a desktop device change.
 * The separate input/output factories remain injectable for focused legacy
 * tests. Supplying either disables the combined production factory so tests
 * cannot accidentally start real platform listeners. On Android the
 * production factories build nothing (AudioSwitch owns routing), as in the
 * Flutter build.
 *
 * PORT: [roomFactory] creates the LiveKit room (`lk.Room(roomOptions:)` in
 * Dart); it defaults to the LiveKit Android SDK.
 */
open class LiveSession(
    audioDeviceRebinderFactory: ((LiveSession) -> AudioDeviceRebinder?)? = null,
    inputRebinderFactory: ((LiveSession) -> AudioInputRebinder?)? = null,
    outputRebinderFactory: ((LiveSession) -> AudioOutputRebinder?)? = null,
    cameraDeviceReconcilerFactory: (() -> CameraDeviceReconciler?)? = null,
    audioDeviceStore: AudioDeviceStore? = null,
    screenAudioTokenProvider: ScreenAudioTokenProvider? = null,
    cameraVideoInputCountProvider: CameraVideoInputCountProvider? = null,
    roomFactory: LiveRoomFactory? = null,
) : ChangeNotifier() {
    private val _audioDeviceRebinderFactory: ((LiveSession) -> AudioDeviceRebinder?)? =
        audioDeviceRebinderFactory
            ?: if (inputRebinderFactory == null && outputRebinderFactory == null) {
                { session -> _defaultAudioDeviceRebinderFactory(session, audioDeviceStore = audioDeviceStore) }
            } else {
                null
            }
    private val _inputRebinderFactory: ((LiveSession) -> AudioInputRebinder?)? = inputRebinderFactory
    private val _outputRebinderFactory: ((LiveSession) -> AudioOutputRebinder?)? = outputRebinderFactory
    private val _cameraDeviceReconcilerFactory: (() -> CameraDeviceReconciler?)? =
        cameraDeviceReconcilerFactory
            ?: if (audioDeviceRebinderFactory == null &&
                inputRebinderFactory == null &&
                outputRebinderFactory == null
            ) {
                ::_defaultCameraDeviceReconcilerFactory
            } else {
                null
            }
    private var _audioDeviceRebinder: AudioDeviceRebinder? = null
    private var _inputRebinder: AudioInputRebinder? = null
    private var _outputRebinder: AudioOutputRebinder? = null
    private var _cameraDeviceReconciler: CameraDeviceReconciler? = null
    private val _screenAudioTokenProvider: ScreenAudioTokenProvider? = screenAudioTokenProvider
    private val _cameraVideoInputCountProvider: CameraVideoInputCountProvider =
        cameraVideoInputCountProvider ?: ::_defaultCameraVideoInputCountProvider
    private val _roomFactory: LiveRoomFactory = roomFactory ?: ::createLiveKitAndroidRoom
    private var _screenAudioPublisher: ScreenAudioPublisher? = null

    private var _room: LkRoom? = null
    private var _cancelEvents: LkCancelListenFunc? = null
    private var _roomName: String? = null
    private var _connecting = false
    private var _screenSharing = false
    private var _cameraFlipAvailable: Boolean? = null
    private var _localVideoTransitionTail: Deferred<Unit> = completedFuture(Unit)
    private var _localMicrophonePublishUnavailable = false
    private var _resolvedLiveKitUrl: String? = null
    private var _canPublish = true
    private var _presenceCallbacksEnabled = false
    private var _participantIdentitiesBeforeReconnect: Set<String>? = null
    private var _microphonePublishedBeforeReconnect: Boolean? = null
    private var _resumedSignalConnection = false
    private var _reconnectRecoveryRevision = 0
    private var _reconnectRecoveryTail: Deferred<Unit> = completedFuture(Unit)
    private var _suppressAutomaticMicReconcile = false
    private var _inputVolume = defaultInputVolume
    private var _outputVolume = defaultOutputVolume
    private var _inputVolumeBoost = false
    private var _outputVolumeBoost = false
    private var _musicBoxVolume = defaultAudioVolume
    private var _androidMusicMediaRouteRequested = false
    private var _androidMusicMediaRouteApplied: Boolean? = null
    private var _androidAudioRouteTail: Deferred<Unit> = completedFuture(Unit)
    private var _screenShareVolume = defaultAudioVolume
    private var _watchedScreenShareIdentity: String? = null
    private var _watchedCameraIdentity: String? = null
    private val _screenShareSubscriptionReconciler = LatestLiveSubscriptionReconciler()
    private val _cameraSubscriptionReconciler = LatestLiveSubscriptionReconciler()
    private val _participantVoiceVolumes = LinkedHashMap<String, Double>()
    private var _outputMuted = false

    // Local mic mute (Option A): muting keeps the capture running and only zeroes
    // the outgoing audio, so it never tears down the shared input. This is the
    // source of truth for the local mute state, since the LiveKit track is
    // intentionally never mute()d.
    private var _micMuted = false

    // The deviceId to capture the mic from. Null means the ADM's
    // current/default device. Applied when the mic track is published; a change
    // while connected republishes the track on the new device.
    private var _inputDeviceId: String? = null

    // Target max height (px) for the local screen share — one of
    // screenShareHeightOptions. Defaults to balanced 720p. Applied at the
    // next share; a change while sharing re-scales the live publication.
    private var _screenShareMaxHeight = defaultScreenShareMaxHeight
    private var _screenShareFrameRate = defaultScreenShareFrameRate
    private var _screenShareSourceWidth: Int? = null
    private var _screenShareSourceHeight: Int? = null
    private var _screenShareAnnouncedResolution: ScreenShareResolution? = null
    private var _screenShareQualityRevision = 0
    private var _screenShareQualityParameterTail: Deferred<Unit> = completedFuture(Unit)
    private var _screenShareQualityLastCheck = ScreenShareQualityCheckResult.retry
    private var _screenShareQualityLastDiagnostic = "not-attempted"

    private val _speakingIdentities = LinkedHashSet<String>()
    private val _micMutedByIdentity = LinkedHashMap<String, Boolean>()

    /**
     * Fired when the server force-disconnects the local participant from
     * LiveKit (an admin `kick` -> `RemoveParticipant`). The UI uses this to drop
     * its joined state and exit the voice panel without auto-reconnecting. Only
     * raised for involuntary removals, not for our own [disconnect].
     */
    var onForciblyRemoved: (() -> Unit)? = null

    /**
     * Fired after LiveKit exhausts its own reconnect path for a recoverable
     * media/signalling failure. The app can request a fresh token and rebuild
     * the session even when its ordinary realtime/SSE connection stayed online.
     */
    var onUnexpectedlyDisconnected: (() -> Unit)? = null

    /**
     * Fired when LiveKit reports the local participant's publish permission
     * changed (an admin `mute_mic` -> canPublish=false, or `restore_voice` ->
     * canPublish=true). The UI reconciles the mic button: disabled while
     * false, re-enabled (still muted) when restored.
     */
    var onPublishPermissionChanged: ((canPublish: Boolean) -> Unit)? = null

    /**
     * Fired for real remote users after the initial room population is done.
     * Hidden screen-audio and music-box participants are excluded.
     */
    var onParticipantJoined: ((participantIdentity: String) -> Unit)? = null

    /** Fired when a real remote user leaves while the room is fully connected. */
    var onParticipantLeft: ((participantIdentity: String, departureKind: LiveParticipantDepartureKind) -> Unit)? = null

    open val isConnected: Boolean get() = _room?.connectionState == LkConnectionState.connected
    open val isConnecting: Boolean get() = _connecting
    open val isScreenSharing: Boolean get() = _screenSharing

    /**
     * Whether the current Android device is known to have another camera.
     *
     * A null/unknown probe remains usable instead of hiding the action: camera
     * capture itself is the permission-requesting operation, so the probe only
     * runs after a successful capture. Only a confirmed single-camera result
     * disables the action.
     */
    open val canFlipCamera: Boolean get() = _cameraFlipAvailable != false
    open val roomName: String? get() = _roomName

    open fun isAttachedToRoom(roomName: String): Boolean {
        if (_roomName != roomName) return false
        if (_connecting) return true
        val state = _room?.connectionState
        return state == LkConnectionState.connected ||
            state == LkConnectionState.connecting ||
            state == LkConnectionState.reconnecting
    }

    /**
     * Whether LiveKit currently grants the local participant publish rights.
     * False once an admin `mute_mic` revokes it; LiveKit is the source of
     * truth here, not any locally-tracked flag.
     */
    open val canPublish: Boolean get() = _canPublish
    open val inputVolume: Double get() = _inputVolume
    open val outputVolume: Double get() = _outputVolume
    open val inputVolumeBoost: Boolean get() = _inputVolumeBoost
    open val outputVolumeBoost: Boolean get() = _outputVolumeBoost

    /**
     * Per-remote-user relative volume for ordinary voice audio. Defaults to
     * 100%, then multiplies with [outputVolume]. Screen-share audio is excluded
     * and continues to use [screenShareVolume].
     */
    open fun participantVoiceVolume(userId: String): Double {
        return _participantVoiceVolumes[userId] ?: defaultParticipantVoiceVolume
    }

    /**
     * Local listening volume applied only to the music box bot's audio track,
     * independent of [outputVolume] (which covers every other remote speaker).
     */
    open val musicBoxVolume: Double get() = _musicBoxVolume

    /**
     * Local listening volume applied only to remote screen-share audio tracks,
     * independent of [outputVolume] (which covers ordinary voice tracks).
     */
    open val screenShareVolume: Double get() = _screenShareVolume

    /**
     * The screen-share owner whose remote share is currently being watched.
     * Screen-share audio is muted unless it belongs to this identity.
     */
    open val watchedScreenShareIdentity: String? get() = _watchedScreenShareIdentity

    /** The camera owner whose remote camera is currently being watched. */
    open val watchedCameraIdentity: String? get() = _watchedCameraIdentity

    open val outputMuted: Boolean get() = _outputMuted

    /** Target max height (px) for the local screen share. */
    open val screenShareMaxHeight: Int get() = _screenShareMaxHeight

    open val screenShareFrameRate: Int get() = _screenShareFrameRate

    private val _platformScreenShareFrameRateCap: Int get() = androidScreenShareFrameRateCap

    private val _selectedScreenShareQuality: ScreenShareQuality
        get() =
            screenShareQualityForHeight(
                _screenShareMaxHeight,
                frameRate = _screenShareFrameRate,
                maxFrameRate = _platformScreenShareFrameRateCap,
            )

    /**
     * Screen share publishes a single full-quality layer. Without a lower
     * simulcast layer every viewer starts on the selected profile instead of a
     * blurry fallback, and the publisher encodes one stream instead of two; a
     * viewer whose bandwidth cannot fit it has the video paused by the SFU
     * rather than downgraded. The camera keeps the room's simulcast default.
     */
    private fun _screenSharePublishOptions(quality: ScreenShareQuality): LkVideoPublishOptions {
        return _room!!.roomOptions.defaultVideoPublishOptions.copyWith(
            simulcast = false,
            screenShareEncoding = _screenShareEncoding(quality),
        )
    }

    /**
     * Whether the local microphone is effectively muted. With Option A muting
     * keeps a successfully published LiveKit track live and silences outgoing
     * audio through app-local gain. A missing/failed publication must still be
     * reported as muted so the local UI and server snapshot cannot claim that
     * other participants can hear a track which does not exist.
     */
    open val localMicMuted: Boolean
        get() {
            val local = _room?.localParticipant
            if (local == null ||
                !_canPublish ||
                _shouldMuteLocalMicrophone ||
                _localMicrophonePublishUnavailable
            ) {
                return true
            }
            val publication = local.getTrackPublicationBySource(LkTrackSource.microphone)
            return publication == null || publication.muted
        }

    open val speakingIdentities: Set<String> get() = _speakingIdentities.toSet()

    open val connectedParticipantIdentities: Set<String>
        get() {
            val room = _room ?: return emptySet()
            val identities = LinkedHashSet<String>()
            val localIdentity = room.localParticipant?.identity
            if (localIdentity != null && localIdentity.isNotEmpty()) {
                identities.add(localIdentity)
            }
            for (participant in room.remoteParticipants.values) {
                if (_isScreenAudioAux(participant.identity)) continue
                identities.add(participant.identity)
            }
            return identities
        }

    open val micMutedByIdentity: Map<String, Boolean> get() = LinkedHashMap(_micMutedByIdentity)

    open fun isSpeaking(userId: String): Boolean = _speakingIdentities.contains(userId)

    /**
     * All live video tracks (local + remote, camera + screen share) that the UI
     * can currently render. Rebuilt on demand from the room's participants.
     */
    open val videoTracks: List<LiveVideoTrack>
        get() {
            val room = _room ?: return emptyList()
            val result = mutableListOf<LiveVideoTrack>()

            fun collect(participant: LkParticipant, isLocal: Boolean) {
                for (pub in participant.videoTrackPublications) {
                    val track = pub.track ?: continue
                    // A muted publication still has a (now-stopped) track attached. livekit
                    // mutes rather than unpublishes the camera when it's turned off, so
                    // rendering it would paint a black rectangle instead of letting the UI
                    // fall back to the avatar. Skip muted tracks entirely.
                    if (pub.muted) continue
                    if (!isLocal && pub is LkRemoteTrackPublication && !pub.subscribed) continue
                    if (track !is LkVideoTrack) continue
                    result.add(
                        LiveVideoTrack(
                            identity = participant.identity,
                            track = track,
                            isScreenShare = pub.source == LkTrackSource.screenShareVideo,
                            isLocal = isLocal,
                        ),
                    )
                }
            }

            val local = room.localParticipant
            if (local != null) collect(local, isLocal = true)
            for (remote in room.remoteParticipants.values) {
                if (_isScreenAudioAux(remote.identity)) continue
                collect(remote, isLocal = false)
            }
            return result
        }

    /**
     * The first screen-share track for [userId], if any. Returns null when that
     * participant isn't sharing (or the track isn't subscribed yet).
     */
    open fun screenShareFor(userId: String): LiveVideoTrack? {
        for (t in videoTracks) {
            if (t.identity == userId && t.isScreenShare) return t
        }
        return null
    }

    /** The first camera track for [userId], if any. */
    open fun cameraFor(userId: String): LiveVideoTrack? {
        for (t in videoTracks) {
            if (t.identity == userId && !t.isScreenShare) return t
        }
        return null
    }

    /**
     * Connect to a LiveKit room. If already in [roomName], aligns mic state
     * and returns. Otherwise disconnects from the current room first.
     */
    open suspend fun connect(
        url: String,
        token: String,
        roomName: String,
        micMuted: Boolean,
    ) {
        if (_room != null && _roomName == roomName && isConnected) {
            setMicMuted(micMuted)
            return
        }
        // A click on a remote share selects it before joining. Preserve that
        // desired target while replacing the previous LiveKit room so the new
        // room's initial auto-subscription is not immediately cancelled.
        _disconnect(clearWatchedTargets = false)

        _connecting = true
        notifyListeners()

        val initialScreenShareQuality = _selectedScreenShareQuality
        val room =
            _roomFactory(
                LkRoomOptions(
                    // Adaptive stream sizes the subscribed quality to the rendering
                    // widget's pixel size. When a screen share is viewed in the small
                    // (non-fullscreen) stage, that requests a low-quality layer; combined
                    // with dynacast (which pauses layers nobody subscribes to at full
                    // quality on the sender), the publisher drops to its lowest temporal
                    // layer and the share renders at a few fps — choppy until you go
                    // fullscreen and the renderer grows enough to request full quality.
                    // Keep subscriptions at full quality so the share is smooth at any
                    // tile size; the camera-tile bandwidth saving isn't worth a degraded
                    // screen share for a small group.
                    adaptiveStream = false,
                    dynacast = true,
                    // Don't stop the OS audio capture session when the mic is muted.
                    // Muting only stops sending audio; the device stays open and
                    // unmute is instant.
                    defaultAudioCaptureOptions =
                        LkAudioCaptureOptions(
                            stopAudioCaptureOnMute = false,
                            deviceId = _inputDeviceId,
                        ),
                    defaultVideoPublishOptions =
                        LkVideoPublishOptions(
                            // The capture rate only sets how fast the grabber samples the
                            // screen. What viewers actually receive is governed by the
                            // *publish* encoding. Set it explicitly so the encoder isn't the
                            // bottleneck. maxBitrate must scale with the frame rate or the
                            // encoder starves frames to stay under budget. The selected
                            // quality profile keeps resolution, frame-rate and bitrate in
                            // sync. Screen share itself always publishes with
                            // _screenSharePublishOptions.
                            screenShareEncoding = _screenShareEncoding(initialScreenShareQuality),
                        ),
                ),
            )
        _room = room
        _roomName = roomName
        _resolvedLiveKitUrl = url
        _invalidateScreenShareQualityApplication(resetSource = true)
        _screenSharing = false
        _localMicrophonePublishUnavailable = false
        _canPublish = true
        _presenceCallbacksEnabled = false
        _participantIdentitiesBeforeReconnect = null
        _microphonePublishedBeforeReconnect = null
        _resumedSignalConnection = false
        _reconnectRecoveryRevision += 1
        _suppressAutomaticMicReconcile = false
        _cancelEvents = room.listen(::_onEvent)

        try {
            // The LiveKit signalling connection bypasses any system proxy
            // (HttpOverrides `DIRECT` in Dart); the SDK room does that itself.
            room.connect(url, token)
            // Seed publish permission from the token LiveKit just validated: a
            // voice-banned user joins with canPublish=false, and we must not try to
            // publish the mic below (LiveKit would reject it).
            _canPublish = room.localParticipant?.permissions?.canPublish ?: true
            _micMuted = micMuted
            // Publish the microphone track. The OS prompts for permission on the
            // first publish. Skip it when publishing is blocked, and when joining
            // muted: the one unavoidable capture start is deferred to the first
            // unmute, which lazily publishes. Once published, muting is app-local
            // gain 0 after capture; it never writes system input gain or disables the
            // WebRTC track.
            if (_canPublish && !_shouldMuteLocalMicrophone) {
                room.localParticipant?.setMicrophoneEnabled(true, audioCaptureOptions = _audioCaptureOptions())
            }
            _applyLocalMicrophoneState()
            _applyOutputVolume()
            _applyMusicBoxVolume()
            _applyScreenShareSubscriptions().await()
            _applyCameraSubscriptions().await()
            _applyScreenShareVolume()
            _refreshAllMicStates()
            _startAudioDeviceRebinder()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            _cancelEvents?.invoke()
            _cancelEvents = null
            _stopAudioDeviceRebinderAndWait()
            try {
                room.dispose()
            } catch (_: Exception) {
            }
            _room = null
            _roomName = null
            _connecting = false
            _invalidateScreenShareQualityApplication(resetSource = true)
            _screenSharing = false
            _localMicrophonePublishUnavailable = false
            _watchedScreenShareIdentity = null
            _watchedCameraIdentity = null
            _presenceCallbacksEnabled = false
            _participantIdentitiesBeforeReconnect = null
            _microphonePublishedBeforeReconnect = null
            _resumedSignalConnection = false
            _reconnectRecoveryRevision += 1
            _suppressAutomaticMicReconcile = false
            unawaited { _stopScreenAudioPublisher() }
            _speakingIdentities.clear()
            _micMutedByIdentity.clear()
            unawaited { _resetLocalInputVolume() }
            _reconcileAndroidMusicBoxAudioRoute()
            notifyListeners()
            throw LiveSessionConnectException(url = url, cause = e)
        }

        _presenceCallbacksEnabled = true
        _connecting = false
        notifyListeners()
    }

    /**
     * Mute/unmute the local microphone. Safe to call when not connected.
     *
     * Muting is app-local gain 0 after capture: the shared OS input device and
     * WebRTC track stay live, and we do not write the system input volume. A 0
     * input-volume slider is treated as the same local mute.
     */
    open suspend fun setMicMuted(muted: Boolean) {
        _micMuted = muted
        if (!muted) {
            _localMicrophonePublishUnavailable = false
        }
        _applyLocalMicrophoneState()
        _refreshAllMicStates()
        notifyListeners()
    }

    open suspend fun setInputVolume(volume: Double) {
        val wasMutedByVolume = _inputVolume <= 0
        _inputVolume = normalizedAudioVolume(volume)
        if (wasMutedByVolume && _inputVolume > 0) {
            _localMicrophonePublishUnavailable = false
        }
        _applyLocalMicrophoneState()
    }

    /**
     * Select the microphone capture device, or null for the system default.
     * Takes effect at the next publish; when already publishing, republishes
     * the mic on the new device, preserving the current mute state.
     */
    open suspend fun setInputDeviceId(deviceId: String?) {
        if (_inputDeviceId == deviceId) return
        _inputDeviceId = deviceId
        _localMicrophonePublishUnavailable = false
        val local = _room?.localParticipant ?: return
        // Republish only if a mic track exists; otherwise the new id is picked up
        // by the next publish (connect or first unmute).
        val hasTrack = local.getTrackPublicationBySource(LkTrackSource.microphone) != null
        if (!hasTrack) return
        // Switching capture device unavoidably stops and restarts the input. That
        // is acceptable here: it only happens on an explicit device change, not on
        // a mute toggle. Restore the mute state through LiveKit without touching
        // the system input gain.
        _restartLocalMicrophoneCapture(local, markUnavailableOnFailure = false)
    }

    internal suspend fun _rebindInputDeviceId(deviceId: String?) {
        _inputDeviceId = deviceId
        _localMicrophonePublishUnavailable = false
        val local = _room?.localParticipant ?: return
        val hasTrack = local.getTrackPublicationBySource(LkTrackSource.microphone) != null
        if (!hasTrack) {
            _applyLocalMicrophoneState()
            _refreshAllMicStates()
            notifyListeners()
            return
        }
        _restartLocalMicrophoneCapture(local, markUnavailableOnFailure = true)
    }

    private suspend fun _restartLocalMicrophoneCapture(
        local: LkLocalParticipant,
        markUnavailableOnFailure: Boolean,
    ) {
        if (!_canPublish) return
        try {
            _applyLocalInputVolume()
            local.setMicrophoneEnabled(false, audioCaptureOptions = _audioCaptureOptions())
            if (_room?.localParticipant !== local) return
            _applyLocalInputVolume()
            local.setMicrophoneEnabled(true, audioCaptureOptions = _audioCaptureOptions())
            if (_room?.localParticipant !== local) return
            _localMicrophonePublishUnavailable = false
            _applyLocalMicrophoneState()
            _refreshAllMicStates()
            notifyListeners()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (markUnavailableOnFailure) {
                _markLocalMicrophonePublishUnavailable()
            }
            // Capture recovery is best-effort; keep the room connected on failure.
        }
    }

    open suspend fun setOutputVolume(volume: Double) {
        _outputVolume = normalizedAudioVolume(volume)
        _applyOutputVolume()
    }

    /** The 增强 switch: the microphone gain becomes the volume × [audioVolumeBoostFactor]. */
    open suspend fun setInputVolumeBoost(boost: Boolean) {
        if (_inputVolumeBoost == boost) return
        _inputVolumeBoost = boost
        _applyLocalMicrophoneState()
    }

    /** The 增强 switch: voice playback becomes the volume × [audioVolumeBoostFactor]. */
    open suspend fun setOutputVolumeBoost(boost: Boolean) {
        if (_outputVolumeBoost == boost) return
        _outputVolumeBoost = boost
        _applyOutputVolume()
    }

    open suspend fun setParticipantVoiceVolume(userId: String, volume: Double) {
        val normalized = normalizedParticipantVoiceVolume(volume)
        if (normalized == defaultParticipantVoiceVolume) {
            _participantVoiceVolumes.remove(userId)
        } else {
            _participantVoiceVolumes[userId] = normalized
        }
        _applyParticipantVoiceVolume(userId)
    }

    open suspend fun setMusicBoxVolume(volume: Double) {
        _musicBoxVolume = normalizedAudioVolume(volume)
        _applyMusicBoxVolume()
    }

    open suspend fun setScreenShareVolume(volume: Double) {
        _screenShareVolume = normalizedAudioVolume(volume)
        _applyScreenShareVolume()
    }

    open suspend fun setWatchedScreenShareIdentity(identity: String?) {
        val next = identity?.trim()
        val normalized = if (next == null || next.isEmpty()) null else next
        val changed = _watchedScreenShareIdentity != normalized
        _watchedScreenShareIdentity = normalized
        _applyScreenShareSubscriptions().await()
        _applyScreenShareVolume()
        if (changed) notifyListeners()
    }

    open suspend fun setWatchedCameraIdentity(identity: String?) {
        val next = identity?.trim()
        val normalized = if (next == null || next.isEmpty()) null else next
        val changed = _watchedCameraIdentity != normalized
        _watchedCameraIdentity = normalized
        _applyCameraSubscriptions().await()
        if (changed) notifyListeners()
    }

    open suspend fun setOutputMuted(muted: Boolean) {
        _outputMuted = muted
        _applyOutputVolume()
    }

    /**
     * Start or stop sharing the screen. On Android the system MediaProjection
     * prompt picks what is shared ([sourceId] is a desktop picker id and is
     * null here). Returns the resulting share state. Throws if capture fails
     * (e.g. the user cancels the OS prompt) — the caller surfaces that.
     */
    open suspend fun setScreenShareEnabled(enabled: Boolean, sourceId: String? = null): Boolean {
        return _serializeLocalVideoTransition { _setScreenShareEnabled(enabled, sourceId = sourceId) }
    }

    private suspend fun _setScreenShareEnabled(enabled: Boolean, sourceId: String? = null): Boolean {
        val local = _room?.localParticipant ?: return false
        if (enabled) {
            _invalidateScreenShareQualityApplication(resetSource = true)
            local.setCameraEnabled(false)
            // Android keeps its MediaProjection session at the display's native
            // size and uses sender caps for the selected profile, because
            // restarting the projection would require a new permission flow.
            // Publisher encoding is the source of truth for output quality.
            val quality = _selectedScreenShareQuality
            val target = quality.resolution
            val captureScreenAudio =
                shouldRequestScreenShareAudio(
                    sourceId = sourceId,
                    isDesktopSourcePickerPlatform = supportsDesktopScreenShare,
                    isWindowsDesktop = false,
                    isAndroidPlatform = true,
                )
            val captureFrameRate = quality.maxFrameRate.toDouble()
            val options =
                LkScreenShareCaptureOptions(
                    captureScreenAudio = captureScreenAudio,
                    sourceId = sourceId,
                    maxFrameRate = captureFrameRate,
                    params =
                        LkVideoParameters(
                            dimensions = LkVideoDimensions(target.width, target.height),
                            encoding =
                                LkVideoEncoding(
                                    maxFramerate = captureFrameRate.toInt(),
                                    maxBitrate = quality.maxBitrate,
                                ),
                        ),
                )
            local.setScreenShareEnabled(
                true,
                // Never let the main room create an audio track for the share:
                // screen audio is published separately on its own factory by
                // ScreenAudioPublisher (the playback capture never shares an
                // audio device module with the microphone).
                captureScreenAudio = false,
                screenShareCaptureOptions = options,
                // Preserve the room's configured codec choice. The screen-share
                // encoding budget and single-layer publish belong to this setting.
                screenSharePublishOptions = _screenSharePublishOptions(quality),
            )
            _screenSharing = true
            // Do not delay the share UI while the first outbound frame becomes
            // available. The initial publish already has the selected FPS/bitrate;
            // this bounded background pass discovers native source dimensions,
            // applies scaling, then verifies the actual outbound resolution.
            unawaited { _applyScreenShareQualityWithRetries() }
            // Start the screen-audio aux publisher in the background so it never
            // blocks the screen-share video. If the aux room fails to connect (ICE,
            // token, etc.) the video share still works — just without independent
            // audio. Errors are logged, not surfaced: the user didn't ask to "share
            // audio", they asked to "share screen", and audio is best-effort.
            if (captureScreenAudio) {
                unawaited {
                    try {
                        _startScreenAudioPublisher()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        debugPrint("screen-audio publisher failed: $e")
                    }
                }
            }
        } else {
            _invalidateScreenShareQualityApplication(resetSource = true)
            // Playback capture uses the active MediaProjection. Cancel and tear
            // down its auxiliary publisher before revoking that projection, or an
            // in-flight publish can create an audio track against an invalid token.
            _screenSharing = false
            try {
                _stopScreenAudioPublisher()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                debugPrint("screen-audio publisher stop failed: $error")
            }
            local.setScreenShareEnabled(false)
            _awaitAndroidScreenCaptureRelease()
        }
        notifyListeners()
        return _screenSharing
    }

    private suspend fun _awaitAndroidScreenCaptureRelease() {
        // LiveKit awaits unpublishing, but Android's MediaProjection/virtual
        // display teardown crosses a native handler thread. Give that thread a
        // bounded opportunity to remove the publication, followed by a short
        // platform-only grace period before opening camera capture.
        var attempt = 0
        while (attempt < 10 && _localHasScreenShare()) {
            delayed(16.milliseconds)
            attempt++
        }
        delayed(160.milliseconds)
    }

    /**
     * Set the target max height for the local screen share. A running share is
     * updated through sender parameters without restarting capture.
     */
    open suspend fun setScreenShareMaxHeight(height: Int) {
        val normalized = normalizedScreenShareMaxHeight(height)
        if (_screenShareMaxHeight == normalized) return
        _screenShareMaxHeight = normalized
        if (_screenSharing) {
            _applyScreenShareQualityWithRetries(refreshDesktopCapture = true)
        }
        notifyListeners()
    }

    /** Set the output frame-rate cap independently from resolution. */
    open suspend fun setScreenShareFrameRate(frameRate: Int) {
        val normalized =
            normalizedScreenShareFrameRate(
                frameRate,
                maxFrameRate = _platformScreenShareFrameRateCap,
            )
        if (_screenShareFrameRate == normalized) return
        _screenShareFrameRate = normalized
        if (_screenSharing) {
            _applyScreenShareQualityWithRetries(refreshDesktopCapture = true)
        }
        notifyListeners()
    }

    private fun _invalidateScreenShareQualityApplication(resetSource: Boolean) {
        _screenShareQualityRevision += 1
        _screenShareQualityLastCheck = ScreenShareQualityCheckResult.retry
        _screenShareQualityLastDiagnostic = "not-attempted"
        if (resetSource) {
            _screenShareSourceWidth = null
            _screenShareSourceHeight = null
            _screenShareAnnouncedResolution = null
        }
    }

    /**
     * Applies and verifies the selected quality without interrupting capture.
     *
     * Native source dimensions and sender stats are not guaranteed to exist on
     * the first event-loop turn after publishing. Retry for a bounded period so
     * 480p/720p do not silently remain at the display's native resolution.
     * ([refreshDesktopCapture] only rebuilds desktop capture sources; Android
     * keeps its MediaProjection session.)
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun _applyScreenShareQualityWithRetries(refreshDesktopCapture: Boolean = false) {
        val revision = ++_screenShareQualityRevision
        // No attempt at zero: the SDK publishes the selected profile already, and
        // an attempt before the first outbound frame can only churn the encoder
        // (nothing to verify yet). The first probe lands once frames should flow.
        val delays =
            listOf(
                300.milliseconds,
                450.milliseconds,
                850.milliseconds,
                1400.milliseconds,
                2200.milliseconds,
            )
        for (delay in delays) {
            if (revision != _screenShareQualityRevision || !_screenSharing) return
            if (delay != Duration.ZERO) delayed(delay)
            if (revision != _screenShareQualityRevision || !_screenSharing) return
            val check = _serializeScreenShareQualityAttempt { _applyScreenShareQualityAttempt(revision) }
            if (check == ScreenShareQualityCheckResult.verified) {
                val quality = _selectedScreenShareQuality
                debugPrint(
                    "screen-share quality caps verified: " +
                        "${quality.maxHeight}p/${quality.maxFrameRate}fps " +
                        "($_screenShareQualityLastDiagnostic)",
                )
                return
            }
        }
        if (revision == _screenShareQualityRevision && _screenSharing) {
            val quality = _selectedScreenShareQuality
            val profile = "${quality.maxHeight}p/${quality.maxFrameRate}fps"
            if (_screenShareQualityLastCheck == ScreenShareQualityCheckResult.awaitingOutboundFrames) {
                debugPrint(
                    "screen-share quality caps ready; outbound verification " +
                        "will resume when a viewer subscribes: $profile " +
                        "($_screenShareQualityLastDiagnostic)",
                )
            } else {
                debugPrint(
                    "screen-share quality cap verification still pending: $profile " +
                        "($_screenShareQualityLastDiagnostic)",
                )
            }
        }
    }

    private suspend fun _serializeScreenShareQualityAttempt(
        operation: suspend () -> ScreenShareQualityCheckResult,
    ): ScreenShareQualityCheckResult {
        val completer = Completer<ScreenShareQualityCheckResult>()
        val previous = _screenShareQualityParameterTail
        _screenShareQualityParameterTail =
            future {
                try {
                    previous.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                try {
                    completer.complete(operation())
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    debugPrint("screen-share quality apply failed: $error\n${error.stackTraceToString()}")
                    completer.complete(ScreenShareQualityCheckResult.retry)
                }
            }
        return completer.future.await()
    }

    private suspend fun _applyScreenShareQualityAttempt(revision: Int): ScreenShareQualityCheckResult {
        if (revision != _screenShareQualityRevision || !_screenSharing) {
            return ScreenShareQualityCheckResult.verified
        }
        val local = _room?.localParticipant ?: return ScreenShareQualityCheckResult.retry
        val pub = local.getTrackPublicationBySource(LkTrackSource.screenShareVideo)
        val track = pub?.track as? LkLocalVideoTrack ?: return ScreenShareQualityCheckResult.retry
        val sender = track.sender ?: return ScreenShareQualityCheckResult.retry

        val params = sender.parameters
        val encodings = params.encodings
        if (encodings == null || encodings.isEmpty()) {
            return ScreenShareQualityCheckResult.retry
        }
        val quality = _selectedScreenShareQuality
        fun encodingScale(encoding: LkRtpEncoding): Double {
            val scale = encoding.scaleResolutionDownBy
            return if (scale == null || scale < 1) 1.0 else scale
        }

        // Simulcast encodings are ordered low-to-high in LiveKit, but do not rely
        // on that implementation detail. The smallest scale is the highest layer.
        val currentHighQualityScale = encodings.map(::encodingScale).reduce { left, right -> if (left < right) left else right }

        // Android exposes the native capture size synchronously. Outbound stats
        // below remain the portable fallback.
        if (_screenShareSourceHeight == null) {
            try {
                val settings = track.captureSettings()
                val settingsWidth = _positiveScreenShareDimension(settings["width"])
                val settingsHeight = _positiveScreenShareDimension(settings["height"])
                if (settingsHeight != null) {
                    _screenShareSourceWidth = settingsWidth
                    _screenShareSourceHeight = settingsHeight
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        var encodedWidth: Int? = null
        var encodedHeight: Int? = null
        var observedSourceWidth: Int? = null
        var observedSourceHeight: Int? = null
        var outboundFrameRate: Double? = null
        val outboundSamples = mutableListOf<Pair<Int, Double>>()
        try {
            val stats = timeout(1.seconds) { track.getSenderStats() }
            for (s in stats) {
                val h = s.frameHeight
                if (h != null && h > 0) {
                    val value = _round(h).toInt()
                    if (encodedHeight == null || value > encodedHeight) {
                        encodedHeight = value
                        encodedWidth = _positiveScreenShareDimension(s.frameWidth)
                    }
                    var matchingEncoding: LkRtpEncoding? = null
                    val rid = s.rid
                    if (rid != null) {
                        for (encoding in encodings) {
                            if (encoding.rid == rid) {
                                matchingEncoding = encoding
                                break
                            }
                        }
                    }
                    val statsSsrc = s.ssrc?.let { _round(it) }
                    if (matchingEncoding == null && statsSsrc != null) {
                        for (encoding in encodings) {
                            if (encoding.ssrc == statsSsrc) {
                                matchingEncoding = encoding
                                break
                            }
                        }
                    }
                    if (matchingEncoding == null && encodings.size == 1) {
                        matchingEncoding = encodings.single()
                    }
                    val sourceScale =
                        screenShareSourceScaleForSample(
                            encodedHeight = value,
                            targetHeight = quality.maxHeight,
                            encodingScales = encodings.map(::encodingScale),
                            matchedScale = matchingEncoding?.let(::encodingScale),
                            knownSourceHeight = _screenShareSourceHeight,
                        )
                    outboundSamples.add(value to sourceScale)
                    val sourceCandidate = _round(value * sourceScale).toInt()
                    val width = _positiveScreenShareDimension(s.frameWidth)
                    if (observedSourceHeight == null || sourceCandidate > observedSourceHeight) {
                        observedSourceHeight = sourceCandidate
                        observedSourceWidth = if (width == null) null else _round(width * sourceScale).toInt()
                    }
                }
                val fps = s.framesPerSecond
                if (fps != null && (outboundFrameRate == null || fps > outboundFrameRate)) {
                    outboundFrameRate = fps
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        // Lock the native source size once per capture session. Reconstructing it
        // repeatedly from delayed frames after changing scale can multiply the old
        // output by the new scale and make a 1080p display look like a 4K source.
        if (_screenShareSourceHeight == null && observedSourceHeight != null) {
            _screenShareSourceWidth = observedSourceWidth
            _screenShareSourceHeight = observedSourceHeight
        } else if (_screenShareSourceWidth == null && observedSourceWidth != null) {
            _screenShareSourceWidth = observedSourceWidth
        }

        if (revision != _screenShareQualityRevision || !_screenSharing) {
            return ScreenShareQualityCheckResult.verified
        }
        val sourceHeight = _screenShareSourceHeight
        // Android keeps its native MediaProjection frames and therefore needs
        // sender scaling.
        val desiredHighQualityScale =
            if (sourceHeight == null) {
                currentHighQualityScale
            } else {
                screenShareScaleDownBy(sourceHeight = sourceHeight, targetHeight = quality.maxHeight)
            }

        // Screen share publishes a single layer (see _screenSharePublishOptions),
        // so every encoding is held to the selected profile.
        var parametersMatch = true
        for (encoding in encodings) {
            val existingScale = encodingScale(encoding)
            val relativeScale = existingScale / currentHighQualityScale
            val desiredScale = desiredHighQualityScale * relativeScale
            if (abs(existingScale - desiredScale) > 0.001 ||
                encoding.maxFramerate != quality.maxFrameRate ||
                encoding.maxBitrate != quality.maxBitrate
            ) {
                parametersMatch = false
            }
        }

        var resolutionVerified = false
        if (sourceHeight != null && outboundSamples.isNotEmpty()) {
            var mismatchedSample = false
            for ((height, scale) in outboundSamples) {
                val expectedHeight = _round(sourceHeight / scale)
                val proportionalTolerance = _round(expectedHeight * 0.05)
                val tolerance = if (proportionalTolerance > 4) proportionalTolerance else 4
                if (abs(height - expectedHeight) > tolerance) {
                    mismatchedSample = true
                }
            }
            resolutionVerified = !mismatchedSample
        }
        val outboundFps = outboundFrameRate
        val frameRateCapVerified = outboundFps == null || outboundFps <= quality.maxFrameRate + 1
        if (parametersMatch) {
            _syncScreenShareTrackDescription(local = local, trackSid = pub.sid, quality = quality)
        }
        var check =
            screenShareQualityCheckResult(
                parametersReady = parametersMatch,
                hasOutboundFrames = outboundSamples.isNotEmpty(),
                resolutionVerified = resolutionVerified,
                frameRateCapVerified = frameRateCapVerified,
            )
        _screenShareQualityLastCheck = check
        _screenShareQualityLastDiagnostic =
            "source=${sourceHeight ?: "unknown"}, " +
            "outbound=${encodedWidth ?: "?"}x${encodedHeight ?: "none"}, " +
            "observedFps=${outboundFps?.let { _toStringAsFixed1(it) } ?: "none"}, " +
            "parameters=${if (parametersMatch) "matched" else "pending"}"
        if (check != ScreenShareQualityCheckResult.retry) return check

        for (encoding in encodings) {
            val existingScale = encodingScale(encoding)
            val relativeScale = existingScale / currentHighQualityScale
            encoding.scaleResolutionDownBy = desiredHighQualityScale * relativeScale
            encoding.maxFramerate = quality.maxFrameRate
            encoding.maxBitrate = quality.maxBitrate
            // `active` is left as read: dynacast owns it.
        }
        val applied = timeout(1.seconds, onTimeout = { false }) { sender.setParameters(params) }
        if (!applied) {
            debugPrint("screen-share sender rejected quality parameters")
        } else {
            _syncScreenShareTrackDescription(local = local, trackSid = pub.sid, quality = quality)
        }
        check =
            screenShareQualityCheckResult(
                parametersReady = parametersMatch || applied,
                hasOutboundFrames = outboundSamples.isNotEmpty(),
                // These samples were collected before setParameters. Never treat them
                // as proof that a newly-applied scale/FPS cap has reached the encoder.
                resolutionVerified = parametersMatch && resolutionVerified,
                frameRateCapVerified = parametersMatch && frameRateCapVerified,
            )
        _screenShareQualityLastCheck = check
        _screenShareQualityLastDiagnostic =
            "source=${sourceHeight ?: "unknown"}, " +
            "outbound=${encodedWidth ?: "?"}x${encodedHeight ?: "none"}, " +
            "observedFps=${outboundFps?.let { _toStringAsFixed1(it) } ?: "none"}, " +
            "parameters=${if (applied) "applied" else "rejected"}"
        // A later attempt verifies frames produced after the encoder reconfigures.
        return check
    }

    /**
     * Announces the capped output size to the server once it changes.
     * (Dart also rewrote the track's capture/publish option bookkeeping, which
     * the Android SDK keeps per publication.)
     */
    private fun _syncScreenShareTrackDescription(
        local: LkLocalParticipant,
        trackSid: String,
        quality: ScreenShareQuality,
    ) {
        val output =
            screenShareCappedOutputResolution(
                sourceWidth = _screenShareSourceWidth,
                sourceHeight = _screenShareSourceHeight,
                targetHeight = quality.maxHeight,
            )
        if (_screenShareAnnouncedResolution == output) return
        val sent =
            local.updatePublishedVideoTrackDimensions(
                trackSid,
                LkVideoDimensions(output.width, output.height),
            )
        if (sent) _screenShareAnnouncedResolution = output
    }

    private fun _positiveScreenShareDimension(value: Any?): Int? {
        if (value is Number && value.toDouble() > 0) return _round(value.toDouble()).toInt()
        val parsed = value?.toString()?.toIntOrNull()
        return if (parsed != null && parsed > 0) parsed else null
    }

    /**
     * Start or stop the local camera. Returns the resulting state. Throws if
     * capture fails (no camera, permission denied) — the caller surfaces it.
     */
    open suspend fun setCameraEnabled(enabled: Boolean): Boolean {
        return _serializeLocalVideoTransition {
            val local = _room?.localParticipant ?: return@_serializeLocalVideoTransition false
            if (enabled && _screenSharing) {
                _setScreenShareEnabled(false)
            }
            local.setCameraEnabled(enabled)
            if (enabled) {
                // setCameraEnabled is intentionally first: on Android it owns the OS
                // camera-permission prompt. Never interpret a pre-permission empty
                // device list as proof that the phone has only one camera.
                _refreshCameraFlipAvailability()
            } else {
                _cameraFlipAvailable = null
            }
            notifyListeners()
            enabled
        }
    }

    /**
     * Switches the active local camera between the front and rear positions.
     * Returns false when there is no published camera track or the platform
     * did not expose camera capture options for that track.
     */
    open suspend fun flipCamera(): Boolean {
        return _serializeLocalVideoTransition {
            val local = _room?.localParticipant ?: return@_serializeLocalVideoTransition false
            var cameraTrack: LkLocalVideoTrack? = null
            for (publication in local.videoTrackPublications) {
                if (publication.source != LkTrackSource.camera) continue
                val track = publication.track
                if (track is LkLocalVideoTrack) {
                    cameraTrack = track
                    break
                }
            }
            if (cameraTrack == null) return@_serializeLocalVideoTransition false
            if (!cameraTrack.isCameraTrack) return@_serializeLocalVideoTransition false
            val nextPosition =
                if (cameraTrack.cameraPosition == LkCameraPosition.front) {
                    LkCameraPosition.back
                } else {
                    LkCameraPosition.front
                }
            cameraTrack.setCameraPosition(nextPosition)
            notifyListeners()
            true
        }
    }

    private suspend fun _refreshCameraFlipAvailability() {
        try {
            val inputCount = _cameraVideoInputCountProvider()
            _cameraFlipAvailable = cameraFlipAvailabilityFromVideoInputCount(inputCount)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Enumeration can still be unavailable on vendor Android builds after
            // the permission prompt. Keep the action available so a transient probe
            // failure is not mistaken for confirmed single-camera hardware.
            _cameraFlipAvailable = null
        }
    }

    private suspend fun <T> _serializeLocalVideoTransition(operation: suspend () -> T): T {
        val previous = _localVideoTransitionTail
        val result =
            future {
                try {
                    previous.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                operation()
            }
        _localVideoTransitionTail =
            future {
                try {
                    result.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        return result.await()
    }

    open suspend fun disconnect() = _disconnect()

    private suspend fun _disconnect(clearWatchedTargets: Boolean = true) {
        val room = _room
        val cancel = _cancelEvents
        _cancelEvents = null
        _screenShareSubscriptionReconciler.invalidate()
        _cameraSubscriptionReconciler.invalidate()
        _stopAudioDeviceRebinderAndWait()
        _room = null
        _roomName = null
        _resolvedLiveKitUrl = null
        _connecting = false
        _invalidateScreenShareQualityApplication(resetSource = true)
        _screenSharing = false
        _cameraFlipAvailable = null
        if (clearWatchedTargets) {
            _watchedScreenShareIdentity = null
            _watchedCameraIdentity = null
        }
        _localMicrophonePublishUnavailable = false
        _canPublish = true
        _presenceCallbacksEnabled = false
        _participantIdentitiesBeforeReconnect = null
        _microphonePublishedBeforeReconnect = null
        _resumedSignalConnection = false
        _reconnectRecoveryRevision += 1
        _suppressAutomaticMicReconcile = false
        _speakingIdentities.clear()
        _micMutedByIdentity.clear()
        _resetLocalInputVolume()
        _reconcileAndroidMusicBoxAudioRoute().await()
        _stopScreenAudioPublisher()
        if (cancel != null) {
            try {
                cancel()
            } catch (_: Exception) {
            }
        }
        if (room != null) {
            try {
                room.disconnect()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            try {
                room.dispose()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        notifyListeners()
    }

    override fun dispose() {
        val room = _room
        val cancel = _cancelEvents
        _cancelEvents = null
        _screenShareSubscriptionReconciler.invalidate()
        _cameraSubscriptionReconciler.invalidate()
        _stopAudioDeviceRebinder()
        _room = null
        _roomName = null
        _invalidateScreenShareQualityApplication(resetSource = true)
        _screenSharing = false
        _cameraFlipAvailable = null
        _watchedScreenShareIdentity = null
        _watchedCameraIdentity = null
        _localMicrophonePublishUnavailable = false
        _presenceCallbacksEnabled = false
        _participantIdentitiesBeforeReconnect = null
        _microphonePublishedBeforeReconnect = null
        _resumedSignalConnection = false
        _reconnectRecoveryRevision += 1
        _suppressAutomaticMicReconcile = false
        unawaited { _stopScreenAudioPublisher() }
        _speakingIdentities.clear()
        _micMutedByIdentity.clear()
        unawaited { _resetLocalInputVolume() }
        _reconcileAndroidMusicBoxAudioRoute()
        cancel?.invoke()
        if (room != null) {
            // Best-effort async cleanup; swallow errors.
            unawaited {
                try {
                    room.disconnect()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                try {
                    room.dispose()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
        super.dispose()
    }

    // ---- Event handling -------------------------------------------------------

    private fun _onEvent(event: LkRoomEvent) {
        when (event) {
            is LkReconnectingEvent -> {
                _androidMusicMediaRouteApplied = null
                val room = _room
                _participantIdentitiesBeforeReconnect =
                    _participantIdentitiesBeforeReconnect
                        ?: event.participantIdentities?.filter(::isLivePresenceSoundParticipantIdentity)?.toSet()
                        ?: if (room == null) emptySet() else _presenceParticipantIdentities(room)
                _microphonePublishedBeforeReconnect =
                    _microphonePublishedBeforeReconnect
                        ?: event.hadMicrophonePublication
                        ?: (room?.localParticipant?.getTrackPublicationBySource(LkTrackSource.microphone) != null)
                if (event is LkRoomResumingEvent) {
                    _resumedSignalConnection = true
                }
                _reconnectRecoveryRevision += 1
                _suppressAutomaticMicReconcile = true
                _presenceCallbacksEnabled = false
                // Speaking and per-publication mute state are media-connection facts,
                // not durable room state. A reconnect can replace every publication
                // without emitting a matching "stopped speaking" event, so drop the
                // remote cache immediately and let the server snapshot remain the
                // authoritative fallback until LiveKit has restored its publications.
                _speakingIdentities.clear()
                _micMutedByIdentity.clear()
                val local = _room?.localParticipant
                if (local != null) {
                    _micMutedByIdentity[local.identity] = localMicMuted
                }
                notifyListeners()
            }
            is LkRoomReconnectedEvent -> {
                // A full reconnect may restore already-published tracks without
                // replaying every participant/track event that normally applies these
                // local preferences. Reconcile them serially so native WebRTC operations
                // cannot overlap one another or continue into a newer room connection.
                val room = _room
                if (room != null) {
                    _scheduleReconnectRecovery(
                        room,
                        revision = _reconnectRecoveryRevision,
                        participantsBefore =
                            _participantIdentitiesBeforeReconnect ?: _presenceParticipantIdentities(room),
                        hadMicrophonePublication =
                            _microphonePublishedBeforeReconnect
                                ?: (room.localParticipant?.getTrackPublicationBySource(LkTrackSource.microphone) != null),
                        resumedSignalConnection = _resumedSignalConnection,
                    )
                }
                notifyListeners()
            }
            is LkActiveSpeakersChangedEvent -> {
                _speakingIdentities.clear()
                _speakingIdentities.addAll(
                    event.speakers
                        .filter { p -> !_isScreenAudioAux(p.identity) }
                        .map { p -> p.identity },
                )
                notifyListeners()
            }
            is LkParticipantConnectedEvent -> {
                if (_isScreenAudioAux(event.participant.identity)) {
                    // An aux participant joining still means a new screen-audio track may
                    // arrive; apply the volume so the track plays at the right level.
                    unawaited { _applyScreenShareVolume() }
                    return
                }
                val micMuted = liveParticipantMicPublicationMuted(event.participant)
                if (micMuted == null) {
                    _micMutedByIdentity.remove(event.participant.identity)
                } else {
                    _micMutedByIdentity[event.participant.identity] = micMuted
                }
                unawaited { _applyOutputVolume() }
                unawaited { _applyMusicBoxVolume() }
                _applyScreenShareSubscriptions()
                _applyCameraSubscriptions()
                unawaited { _applyScreenShareVolume() }
                notifyListeners()
                if (_presenceCallbacksEnabled &&
                    isLivePresenceSoundParticipantIdentity(event.participant.identity)
                ) {
                    onParticipantJoined?.invoke(event.participant.identity)
                }
            }
            is LkParticipantDisconnectedEvent -> {
                if (_isScreenAudioAux(event.participant.identity)) {
                    unawaited { _applyScreenShareVolume() }
                    return
                }
                _micMutedByIdentity.remove(event.participant.identity)
                _speakingIdentities.remove(event.participant.identity)
                _reconcileAndroidMusicBoxAudioRoute()
                notifyListeners()
                if (_presenceCallbacksEnabled &&
                    isLivePresenceSoundParticipantIdentity(event.participant.identity)
                ) {
                    onParticipantLeft?.invoke(
                        event.participant.identity,
                        liveParticipantDepartureKind(event.reason),
                    )
                }
            }
            // An admin mute_mic / restore_voice flips the local participant's publish
            // permission server-side. Headphone mute only changes canSubscribe, so
            // ignore permission events where canPublish did not actually change.
            is LkParticipantPermissionsUpdatedEvent -> {
                val local = _room?.localParticipant
                if (local != null && event.participant.identity == local.identity) {
                    val next = event.permissions.canPublish
                    if (shouldApplyLivePublishPermissionUpdate(
                            currentCanPublish = _canPublish,
                            oldCanPublish = event.oldPermissions.canPublish,
                            nextCanPublish = next,
                        )
                    ) {
                        _canPublish = next
                        unawaited { _applyLocalMicrophoneState() }
                        notifyListeners()
                        onPublishPermissionChanged?.invoke(next)
                    }
                }
            }
            is LkLocalTrackSubscribedEvent -> {
                val publication = _room?.localParticipant?.getTrackPublicationBySid(event.trackSid)
                if (_screenSharing && publication?.source == LkTrackSource.screenShareVideo) {
                    // With dynacast, an unobserved local share may produce no outbound
                    // frames. The first subscriber activates the encoder, which is the
                    // earliest reliable point for measuring and enforcing native-source
                    // scaling.
                    unawaited { _applyScreenShareQualityWithRetries() }
                }
            }
            // Video subscription / publish lifecycle: rebuild the rendered track list.
            is LkTrackLifecycleEvent -> {
                // LiveKit re-applies its communication preset as native audio tracks
                // change. Invalidate our cache so an active music box restores the media
                // route after the SDK has finished handling this lifecycle event.
                _androidMusicMediaRouteApplied = null
                if (!_suppressAutomaticMicReconcile) {
                    unawaited { _applyLocalMicrophoneState() }
                }
                unawaited { _applyOutputVolume() }
                unawaited { _applyMusicBoxVolume() }
                unawaited { _applyScreenShareVolume() }
                _refreshAllMicStates()
                // A local screen-share track can be stopped from the OS (e.g. the
                // "stop sharing" bar); keep our flag honest.
                val wasScreenSharing = _screenSharing
                _screenSharing = _localHasScreenShare()
                if (wasScreenSharing && !_screenSharing) {
                    _invalidateScreenShareQualityApplication(resetSource = true)
                    unawaited { _stopScreenAudioPublisher() }
                }
                val watchedScreenShare = _watchedScreenShareIdentity
                if (watchedScreenShare != null && !_remoteScreenSharePublicationExists(watchedScreenShare)) {
                    _watchedScreenShareIdentity = null
                }
                val watchedCamera = _watchedCameraIdentity
                if (watchedCamera != null && !_remoteCameraPublicationExists(watchedCamera)) {
                    _watchedCameraIdentity = null
                }
                _applyScreenShareSubscriptions()
                _applyCameraSubscriptions()
                unawaited { _applyScreenShareVolume() }
                notifyListeners()
            }
            is LkRoomDisconnectedEvent -> {
                _presenceCallbacksEnabled = false
                _participantIdentitiesBeforeReconnect = null
                _microphonePublishedBeforeReconnect = null
                _resumedSignalConnection = false
                _reconnectRecoveryRevision += 1
                _suppressAutomaticMicReconcile = false
                _invalidateScreenShareQualityApplication(resetSource = true)
                _screenSharing = false
                _watchedScreenShareIdentity = null
                _watchedCameraIdentity = null
                _localMicrophonePublishUnavailable = false
                unawaited { _stopScreenAudioPublisher() }
                _speakingIdentities.clear()
                _micMutedByIdentity.clear()
                notifyListeners()
                // An admin kick force-disconnects us via RemoveParticipant; LiveKit
                // reports it as participantRemoved. Distinguish it from our own
                // disconnect() (which cancels event delivery before the event can fire,
                // so this handler never runs for a voluntary leave) and from transient
                // network drops, so the UI can exit the voice panel without auto-
                // reconnecting into a room we were just removed from.
                if (event.reason == LkDisconnectReason.participantRemoved ||
                    event.reason == LkDisconnectReason.roomDeleted
                ) {
                    onForciblyRemoved?.invoke()
                } else if (event.reason != LkDisconnectReason.clientInitiated &&
                    event.reason != LkDisconnectReason.duplicateIdentity
                ) {
                    // Do not fight another device that intentionally replaced this
                    // account's LiveKit identity. Ordinary network/server failures are
                    // recoverable with a fresh app-level join.
                    onUnexpectedlyDisconnected?.invoke()
                }
            }
        }
    }

    private fun _localHasScreenShare(): Boolean {
        val local = _room?.localParticipant ?: return false
        return local.videoTrackPublications.any { pub ->
            pub.source == LkTrackSource.screenShareVideo && pub.track != null
        }
    }

    private fun _presenceParticipantIdentities(room: LkRoom): Set<String> {
        return room.remoteParticipants.values
            .map { participant -> participant.identity }
            .filter(::isLivePresenceSoundParticipantIdentity)
            .toSet()
    }

    private fun _scheduleReconnectRecovery(
        reconnectedRoom: LkRoom,
        revision: Int,
        participantsBefore: Set<String>,
        hadMicrophonePublication: Boolean,
        resumedSignalConnection: Boolean,
    ) {
        val previous = _reconnectRecoveryTail
        val recovery =
            future {
                try {
                    previous.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                if (_room !== reconnectedRoom || revision != _reconnectRecoveryRevision) {
                    return@future
                }
                _reconcileMediaAfterReconnect(
                    reconnectedRoom,
                    hadMicrophonePublication = hadMicrophonePublication,
                    resumedSignalConnection = resumedSignalConnection,
                )
                if (_room !== reconnectedRoom || revision != _reconnectRecoveryRevision) {
                    return@future
                }

                val changes =
                    liveReconnectPresenceChanges(
                        before = participantsBefore,
                        after = _presenceParticipantIdentities(reconnectedRoom),
                    )
                _participantIdentitiesBeforeReconnect = null
                _microphonePublishedBeforeReconnect = null
                _resumedSignalConnection = false
                _presenceCallbacksEnabled = true
                _suppressAutomaticMicReconcile = false
                for (identity in changes.left) {
                    onParticipantLeft?.invoke(identity, LiveParticipantDepartureKind.left)
                }
                for (identity in changes.joined) {
                    onParticipantJoined?.invoke(identity)
                }
                _refreshAllMicStates()
                notifyListeners()
            }
        _reconnectRecoveryTail =
            future {
                try {
                    recovery.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (_room === reconnectedRoom && revision == _reconnectRecoveryRevision) {
                        _participantIdentitiesBeforeReconnect = null
                        _microphonePublishedBeforeReconnect = null
                        _resumedSignalConnection = false
                        _presenceCallbacksEnabled = true
                        _suppressAutomaticMicReconcile = false
                        _refreshAllMicStates()
                        notifyListeners()
                    }
                }
            }
    }

    private suspend fun _reconcileMediaAfterReconnect(
        reconnectedRoom: LkRoom,
        hadMicrophonePublication: Boolean,
        resumedSignalConnection: Boolean,
    ) {
        if (_room !== reconnectedRoom) return
        val local = reconnectedRoom.localParticipant
        val previousCanPublish = _canPublish
        _canPublish = local?.permissions?.canPublish ?: _canPublish
        if (previousCanPublish != _canPublish) {
            onPublishPermissionChanged?.invoke(_canPublish)
        }
        if (local != null &&
            shouldRepublishLocalMicrophoneAfterReconnect(
                resumedSignalConnection = resumedSignalConnection,
                hadPublicationBeforeReconnect = hadMicrophonePublication,
                canPublish = _canPublish,
            )
        ) {
            val microphoneRecovered = _republishLocalMicrophoneAfterReconnect(reconnectedRoom, local)
            if (!microphoneRecovered &&
                _room === reconnectedRoom &&
                reconnectedRoom.localParticipant === local &&
                _canPublish
            ) {
                // The subscriber can recover while the publisher remains wedged. A
                // bounded republish failure is not a usable voice connection: replace
                // the whole LiveKit session so the app-level reconnect obtains a new
                // token, participant SID and publisher PeerConnection.
                _disconnect(clearWatchedTargets = false)
                onUnexpectedlyDisconnected?.invoke()
                return
            }
        } else {
            _applyLocalMicrophoneState()
        }
        if (_room !== reconnectedRoom) return
        _screenSharing = _localHasScreenShare()
        if (_screenSharing) {
            unawaited { _applyScreenShareQualityWithRetries() }
        }
        if (_screenSharing && _screenAudioPublisher?.isPublishing != true) {
            try {
                _stopScreenAudioPublisher()
                if (_room === reconnectedRoom) _startScreenAudioPublisher()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // The video share remains usable when isolated system-audio recovery
                // fails. A later explicit share can retry with a fresh aux session.
            }
        }
        if (_room !== reconnectedRoom) return
        _applyOutputVolume()
        if (_room !== reconnectedRoom) return
        _applyMusicBoxVolume()
        if (_room !== reconnectedRoom) return
        _applyScreenShareSubscriptions().await()
        if (_room !== reconnectedRoom) return
        _applyCameraSubscriptions().await()
        if (_room !== reconnectedRoom) return
        _applyScreenShareVolume()
    }

    private suspend fun _republishLocalMicrophoneAfterReconnect(
        reconnectedRoom: LkRoom,
        local: LkLocalParticipant,
    ): Boolean {
        var attemptNumber = 0
        val recovered =
            retryLiveReconnectMicrophoneRecovery(
                isCurrent = {
                    _room === reconnectedRoom &&
                        reconnectedRoom.localParticipant === local &&
                        _canPublish
                },
                attempt = attempt@{
                    attemptNumber += 1
                    try {
                        val publication = local.getTrackPublicationBySource(LkTrackSource.microphone)
                        if (publication != null) {
                            local.removePublishedTrack(publication.sid)
                        }
                        if (_room !== reconnectedRoom ||
                            reconnectedRoom.localParticipant !== local ||
                            !_canPublish
                        ) {
                            return@attempt false
                        }
                        local.setMicrophoneEnabled(true, audioCaptureOptions = _audioCaptureOptions())
                        if (_room !== reconnectedRoom || reconnectedRoom.localParticipant !== local) {
                            return@attempt false
                        }
                        val recoveredPublication = local.getTrackPublicationBySource(LkTrackSource.microphone)
                        if (recoveredPublication == null || recoveredPublication.track == null) {
                            return@attempt false
                        }
                        _localMicrophonePublishUnavailable = false
                        _applyLocalInputVolume()
                        true
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        debugPrint(
                            "live reconnect: microphone recovery attempt " +
                                "$attemptNumber failed: $error",
                        )
                        false
                    }
                },
            )
        if (!recovered && _room === reconnectedRoom) {
            _markLocalMicrophonePublishUnavailable()
            notifyListeners()
        }
        return recovered
    }

    private fun _refreshAllMicStates() {
        val room = _room ?: return
        val refreshed = LinkedHashMap<String, Boolean>()
        val local = room.localParticipant
        if (local != null) {
            refreshed[local.identity] = localMicMuted
        }
        for (p in room.remoteParticipants.values) {
            if (_isScreenAudioAux(p.identity)) continue
            val micMuted = liveParticipantMicPublicationMuted(p)
            if (micMuted != null) refreshed[p.identity] = micMuted
        }
        _micMutedByIdentity.clear()
        _micMutedByIdentity.putAll(refreshed)
    }

    private val _shouldMuteLocalMicrophone: Boolean get() = _micMuted || _inputVolume <= 0

    private val _effectiveInputVolume: Double
        get() =
            if (!_canPublish || _shouldMuteLocalMicrophone) {
                0.0
            } else {
                boostedAudioVolume(_inputVolume, _inputVolumeBoost)
            }

    private fun _audioCaptureOptions(): LkAudioCaptureOptions {
        return LkAudioCaptureOptions(
            stopAudioCaptureOnMute = false,
            deviceId = _inputDeviceId,
        )
    }

    private fun _markLocalMicrophonePublishUnavailable() {
        _localMicrophonePublishUnavailable = true
    }

    /**
     * Start the hidden screen-audio aux participant. The aux room publishes the
     * screen-share audio track through an isolated WebRTC factory (second
     * PeerConnectionFactory fed by Android playback capture), so screen audio
     * never shares an AudioState with the mic. Requires the main room to be
     * connected (for the resolved LiveKit URL + room name) and a token provider
     * to have been injected.
     */
    private suspend fun _startScreenAudioPublisher() {
        if (_screenAudioPublisher != null) return
        val provider = _screenAudioTokenProvider
        val url = _resolvedLiveKitUrl
        val name = _roomName
        debugPrint(
            "screen-audio: _startScreenAudioPublisher " +
                "provider=${provider != null} url=$url roomName=$name",
        )
        if (provider == null || url == null || name == null) return
        val publisher = ScreenAudioPublisher(tokenProvider = provider)
        _screenAudioPublisher = publisher
        try {
            publisher.start(liveKitUrl = url, roomName = name)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            debugPrint("screen-audio: publisher start failed: $e\n${e.stackTraceToString()}")
            if (_screenAudioPublisher === publisher) {
                _screenAudioPublisher = null
            }
            publisher.stop()
            throw e
        }
    }

    private suspend fun _stopScreenAudioPublisher() {
        val publisher = _screenAudioPublisher
        _screenAudioPublisher = null
        publisher?.stop()
    }

    private suspend fun _applyLocalMicrophoneState() {
        val local = _room?.localParticipant
        _applyLocalInputVolume()
        if (local == null) return

        var microphonePublication = local.getTrackPublicationBySource(LkTrackSource.microphone)
        if (microphonePublication == null &&
            !_shouldMuteLocalMicrophone &&
            _canPublish &&
            !_localMicrophonePublishUnavailable
        ) {
            try {
                local.setMicrophoneEnabled(true, audioCaptureOptions = _audioCaptureOptions())
                microphonePublication = local.getTrackPublicationBySource(LkTrackSource.microphone)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _markLocalMicrophonePublishUnavailable()
                return
            }
        }
        if (microphonePublication == null) return

        // Keep the LiveKit track enabled. Local mute is represented by the
        // app-level live state plus capture gain 0, not by disabling the track.
        if (_canPublish && microphonePublication.muted) {
            try {
                local.setMicrophoneEnabled(true, audioCaptureOptions = _audioCaptureOptions())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        _applyLocalInputVolume()
    }

    private fun _applyLocalInputVolume() {
        try {
            WebRtc.setLocalAudioInputVolume(_effectiveInputVolume)
        } catch (_: Exception) {
        }
    }

    private fun _resetLocalInputVolume() {
        try {
            WebRtc.setLocalAudioInputVolume(1.0)
        } catch (_: Exception) {
        }
    }

    private suspend fun _applyOutputVolume() {
        val room = _room ?: return
        val participants = room.remoteParticipants.values.toList()
        for (participant in participants) {
            // The music box bot has its own independent volume knob; leave it to
            // _applyMusicBoxVolume so the two don't fight over the same track.
            if (participant.identity == musicBoxBotIdentity) continue
            _applyParticipantVoiceVolumeToParticipant(participant)
        }
    }

    private suspend fun _applyParticipantVoiceVolume(userId: String) {
        val participant = _room?.remoteParticipants?.get(userId)
        if (participant == null || participant.identity == musicBoxBotIdentity) {
            return
        }
        _applyParticipantVoiceVolumeToParticipant(participant)
    }

    /**
     * A remote voice's playback gain: the output volume (× the 增强 factor)
     * times that participant's own volume, at most both maxima combined.
     */
    private fun _voicePlaybackVolume(participantIdentity: String): Double {
        val baseVolume = if (_outputMuted) 0.0 else boostedAudioVolume(_outputVolume, _outputVolumeBoost)
        val volume = baseVolume * participantVoiceVolume(participantIdentity)
        return volume.coerceIn(0.0, maxParticipantVoiceVolume * audioVolumeBoostFactor)
    }

    private suspend fun _applyParticipantVoiceVolumeToParticipant(participant: LkRemoteParticipant) {
        val volume = _voicePlaybackVolume(participant.identity)
        for (pub in participant.audioTrackPublications) {
            if (pub.source == LkTrackSource.screenShareAudio) continue
            val track = pub.track as? LkAudioTrack ?: continue
            try {
                track.setVolume(volume)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private suspend fun _applyMusicBoxVolume() {
        _reconcileAndroidMusicBoxAudioRoute().await()
        val room = _room ?: return
        val participants = room.remoteParticipants.values.toList()
        for (participant in participants) {
            if (participant.identity != musicBoxBotIdentity) continue
            for (pub in participant.audioTrackPublications) {
                val track = pub.track as? LkAudioTrack ?: continue
                try {
                    track.setVolume(_musicBoxVolume)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    private fun _hasActiveMusicBoxAudioTrack(): Boolean {
        val participant = _room?.remoteParticipants?.get(musicBoxBotIdentity) ?: return false
        return participant.audioTrackPublications.any { publication ->
            publication.track != null && !publication.muted
        }
    }

    private fun _reconcileAndroidMusicBoxAudioRoute(): Deferred<Unit> {
        _androidMusicMediaRouteRequested = _hasActiveMusicBoxAudioTrack()
        val previous = _androidAudioRouteTail
        _androidAudioRouteTail =
            future {
                try {
                    previous.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                val requested = _androidMusicMediaRouteRequested
                if (_androidMusicMediaRouteApplied == requested) return@future
                try {
                    WebRtc.setAndroidAudioConfiguration(androidLiveAudioConfiguration(musicBoxActive = requested))
                    _androidMusicMediaRouteApplied = requested
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Audio routing is best-effort. LiveKit audio remains usable when
                    // an OEM rejects a runtime stream reconfiguration.
                }
                try {
                    AndroidAudioRoute.setMusicBoxActive(
                        active = requested,
                        liveSessionActive = _room != null,
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // The WebRTC route above remains authoritative. This Activity hint is
                    // only needed for OEMs that otherwise keep hardware volume keys bound
                    // to the call stream while a media AudioTrack is active.
                }
            }
        return _androidAudioRouteTail
    }

    private suspend fun _applyScreenShareVolume() {
        val room = _room ?: return
        val localIdentity = room.localParticipant?.identity
        val participants = room.remoteParticipants.values.toList()
        for (participant in participants) {
            // Self-echo suppression: the local user's own screen-audio aux
            // participant (`<ownId>--screen-audio`) is publishing audio the user
            // is already hearing locally (it's their own screen). Mute it to 0 so
            // it never plays back to them. Other users' aux tracks play at the
            // screen-share volume.
            val isOwnAux = _isOwnScreenAudioAux(participant.identity, localIdentity)
            val ownerIdentity = _screenShareAudioOwnerIdentity(participant.identity)
            val isWatched =
                _watchedScreenShareIdentity != null && ownerIdentity == _watchedScreenShareIdentity
            for (pub in participant.audioTrackPublications) {
                if (pub.source != LkTrackSource.screenShareAudio) continue
                val track = pub.track as? LkAudioTrack ?: continue
                try {
                    track.setVolume(if (isOwnAux || !isWatched) 0.0 else _screenShareVolume)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    private fun _applyScreenShareSubscriptions(): Deferred<Unit> {
        return _screenShareSubscriptionReconciler.schedule { isCurrent ->
            _reconcileVideoSubscriptions(
                source = LkTrackSource.screenShareVideo,
                watchedIdentity = _watchedScreenShareIdentity,
                isCurrent = isCurrent,
            )
        }
    }

    private fun _applyCameraSubscriptions(): Deferred<Unit> {
        return _cameraSubscriptionReconciler.schedule { isCurrent ->
            _reconcileVideoSubscriptions(
                source = LkTrackSource.camera,
                watchedIdentity = _watchedCameraIdentity,
                isCurrent = isCurrent,
            )
        }
    }

    private suspend fun _reconcileVideoSubscriptions(
        source: LkTrackSource,
        watchedIdentity: String?,
        isCurrent: () -> Boolean,
    ) {
        val room = _room ?: return
        val participants = room.remoteParticipants.values.toList()
        for (participant in participants) {
            if (!isCurrent() || _room !== room) return
            if (_isScreenAudioAux(participant.identity)) continue
            for (pub in participant.videoTrackPublications) {
                if (!isCurrent() || _room !== room) return
                if (pub.source != source) continue
                if (pub !is LkRemoteTrackPublication) continue
                val shouldSubscribe = watchedIdentity != null && participant.identity == watchedIdentity
                try {
                    if (shouldSubscribe) {
                        pub.subscribe()
                    } else {
                        pub.unsubscribe()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    private fun _remoteScreenSharePublicationExists(identity: String): Boolean {
        val participant = _room?.remoteParticipants?.get(identity) ?: return false
        return participant.videoTrackPublications.any { pub -> pub.source == LkTrackSource.screenShareVideo }
    }

    private fun _remoteCameraPublicationExists(identity: String): Boolean {
        val participant = _room?.remoteParticipants?.get(identity) ?: return false
        return participant.videoTrackPublications.any { pub -> pub.source == LkTrackSource.camera }
    }

    // Re-bind WebRTC's audio output to the live default endpoint and re-apply
    // every track volume. Invoked by a desktop output rebinder after the OS swaps
    // the default output; Android never builds one (AudioSwitch routes output).
    internal suspend fun _reapplyAudioRouting() {
        if (_room == null) return
        _applyOutputVolume()
        _applyMusicBoxVolume()
        _applyScreenShareVolume()
    }

    private fun _startAudioDeviceRebinder() {
        _stopAudioDeviceRebinder()
        val cameraReconciler = _cameraDeviceReconcilerFactory?.invoke()
        _cameraDeviceReconciler = cameraReconciler
        cameraReconciler?.start()
        val combinedFactory = _audioDeviceRebinderFactory
        if (combinedFactory != null) {
            val rebinder = combinedFactory(this)
            _audioDeviceRebinder = rebinder
            rebinder?.start()
            return
        }
        // Compatibility path for focused tests that inject the former split
        // coordinators. Production always uses the combined coordinator.
        _startInputRebinder()
        _startOutputRebinder()
    }

    private fun _stopAudioDeviceRebinder() {
        unawaited { _stopAudioDeviceRebinderAndWait() }
    }

    private suspend fun _stopAudioDeviceRebinderAndWait() {
        val rebinder = _audioDeviceRebinder
        _audioDeviceRebinder = null
        val inputRebinder = _inputRebinder
        _inputRebinder = null
        val outputRebinder = _outputRebinder
        _outputRebinder = null
        val cameraReconciler = _cameraDeviceReconciler
        _cameraDeviceReconciler = null
        waitAll(
            listOfNotNull(
                rebinder?.let { future { it.stop() } },
                inputRebinder?.let { future { it.stop() } },
                outputRebinder?.let { future { it.stop() } },
                cameraReconciler?.let { future { it.stop() } },
            ),
        )
    }

    private fun _startInputRebinder() {
        _stopInputRebinder()
        val rebinder = _inputRebinderFactory?.invoke(this)
        _inputRebinder = rebinder
        rebinder?.start()
    }

    private fun _stopInputRebinder() {
        val rebinder = _inputRebinder
        _inputRebinder = null
        if (rebinder != null) unawaited { rebinder.stop() }
    }

    private fun _startOutputRebinder() {
        _stopOutputRebinder()
        val rebinder = _outputRebinderFactory?.invoke(this)
        _outputRebinder = rebinder
        rebinder?.start()
    }

    private fun _stopOutputRebinder() {
        val rebinder = _outputRebinder
        _outputRebinder = null
        if (rebinder != null) unawaited { rebinder.stop() }
    }

    /**
     * Drives the output-rebinder lifecycle without a live LiveKit connection, so
     * the recovery wiring can be exercised in tests. Production code reaches
     * these through [connect]/[disconnect].
     */
    @VisibleForTesting
    fun debugStartInputRebinder() = _startInputRebinder()

    @VisibleForTesting
    fun debugStopInputRebinder() = _stopInputRebinder()

    @VisibleForTesting
    fun debugStartOutputRebinder() = _startOutputRebinder()

    @VisibleForTesting
    fun debugStopOutputRebinder() = _stopOutputRebinder()

    @VisibleForTesting
    fun debugStartAudioDeviceRebinder() = _startAudioDeviceRebinder()

    @VisibleForTesting
    fun debugStopAudioDeviceRebinder() = _stopAudioDeviceRebinder()

    /**
     * Exercises reconnect/event cache reconciliation without a real LiveKit
     * transport. Production events reach the same handler through [connect].
     */
    @VisibleForTesting
    fun debugHandleRoomEvent(event: LkRoomEvent) = _onEvent(event)

    companion object {
        /**
         * The screen/window sources of the desktop share picker. Android shares
         * through the system MediaProjection prompt, so there are none (the
         * Flutter build's desktop capturer enumeration failed on Android and
         * returned an empty list).
         */
        suspend fun listScreenSources(): List<ScreenSource> = emptyList()

        /** Desktop picker thumbnails; nothing to refresh on Android. */
        suspend fun refreshScreenSourceThumbnails() {}

        /** The full-quality encoding for the selected profile. Screen share gets
         *  bitrate priority over the camera on the same connection. */
        private fun _screenShareEncoding(quality: ScreenShareQuality): LkVideoEncoding {
            return LkVideoEncoding(
                maxFramerate = quality.maxFrameRate,
                maxBitrate = quality.maxBitrate,
                bitratePriority = LkPriority.high,
                networkPriority = LkPriority.high,
            )
        }
    }
}

/**
 * Default desktop recovery for endpoint hotplug/profile flips. Desktop only:
 * on Android AudioSwitch follows route changes, so there is nothing to build
 * (the Dart factory returned null off macOS/Windows).
 */
@Suppress("UNUSED_PARAMETER")
private fun _defaultAudioDeviceRebinderFactory(
    session: LiveSession,
    audioDeviceStore: AudioDeviceStore?,
): AudioDeviceRebinder? = null

/** Windows-only camera topology reconciliation; null on Android. */
private fun _defaultCameraDeviceReconcilerFactory(): CameraDeviceReconciler? = null

class LiveSessionConnectException(val url: String, override val cause: Throwable) : Exception(cause) {
    override fun toString(): String {
        return "无法连接语音服务"
    }
}

/** Dart's `double.round()` for the non-negative values used here. */
private fun _round(value: Double): Long = value.roundToLong()

private fun _toStringAsFixed1(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

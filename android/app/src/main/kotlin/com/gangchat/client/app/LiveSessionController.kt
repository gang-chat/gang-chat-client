package com.gangchat.client.app

import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.Timer
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.future
import com.gangchat.client.live.LiveParticipantDepartureKind
import com.gangchat.client.live.LiveSession
import com.gangchat.client.live.LiveVideoTrack
import com.gangchat.client.live.ScreenAudioTokenProvider
import com.gangchat.client.live.ScreenSource
import com.gangchat.client.live.SystemAudioDevices
import com.gangchat.client.live.resolveLiveKitServerUrl
import com.gangchat.client.live.restoreStoredAudioDevices
import com.gangchat.client.protocol.LiveJoinResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.selects.select

typealias AudioDeviceRestorer = suspend (store: AudioDeviceStore) -> String?

/**
 * The plain strings Dart threw from [LiveSessionController.connectWithRetry]
 * (`'已取消加入直播'`, `'LiveKit 连接失败'`), as an exception whose
 * `toString()` is that text.
 */
class LiveSessionControllerError(override val message: String) : Exception(message) {
    override fun toString(): String = message
}

open class LiveSessionController(
    val apiBaseUrl: String,
    val audioDeviceStore: AudioDeviceStore,
    session: LiveSession? = null,
    audioDeviceRestorer: AudioDeviceRestorer? = null,
    screenAudioTokenProvider: ScreenAudioTokenProvider? = null,
) {
    val session: LiveSession =
        session
            ?: LiveSession(
                audioDeviceStore = audioDeviceStore,
                screenAudioTokenProvider = screenAudioTokenProvider,
            )

    private val _audioDeviceRestorer: AudioDeviceRestorer =
        audioDeviceRestorer ?: { store ->
            // On desktop the OS-selected mic is known via the native channel;
            // pass it so a room join follows the system default when the user
            // has not pinned a device. Unsupported platforms (Android) return null.
            val systemAudio = SystemAudioDevices()
            var systemDefaultInputId: String? = null
            var systemDefaultOutputId: String? = null
            try {
                val inputId = future { systemAudio.currentInputDeviceId() }
                val outputId = future { systemAudio.currentOutputDeviceId() }
                systemDefaultInputId = inputId.await()
                systemDefaultOutputId = outputId.await()
            } finally {
                systemAudio.dispose()
            }
            val restored =
                restoreStoredAudioDevices(
                    store,
                    systemDefaultInputId = systemDefaultInputId,
                    systemDefaultOutputId = systemDefaultOutputId,
                )
            // The device the published mic should capture from: the resolved
            // input (stored preference or system default), else null to leave
            // LiveKit on the ADM's current device.
            restored.input?.deviceId ?: systemDefaultInputId
        }
    private val _restoredParticipantVoiceVolumeIds = LinkedHashSet<String>()
    private val _restoringParticipantVoiceVolumeIds = LinkedHashSet<String>()
    private val _participantVoiceVolumeBeforeMute = LinkedHashMap<String, Double>()

    suspend fun listScreenSources(): List<ScreenSource> {
        return LiveSession.listScreenSources()
    }

    suspend fun refreshScreenSourceThumbnails() {
        return LiveSession.refreshScreenSourceThumbnails()
    }

    val isScreenSharing: Boolean get() = session.isScreenSharing
    val canFlipCamera: Boolean get() = session.canFlipCamera
    val isConnected: Boolean get() = session.isConnected
    val localMicMuted: Boolean get() = session.localMicMuted
    val roomName: String? get() = session.roomName

    fun isAttachedToRoom(roomName: String): Boolean = session.isAttachedToRoom(roomName)

    val speakingIdentities: Set<String> get() = session.speakingIdentities
    val connectedParticipantIdentities: Set<String> get() = session.connectedParticipantIdentities
    val micMutedByIdentity: Map<String, Boolean> get() = session.micMutedByIdentity
    val videoTracks: List<LiveVideoTrack> get() = session.videoTracks

    fun cameraFor(userId: String): LiveVideoTrack? = session.cameraFor(userId)

    fun attachSessionCallbacks(
        onChanged: () -> Unit,
        onForciblyRemoved: () -> Unit,
        onUnexpectedlyDisconnected: () -> Unit,
        onPublishPermissionChanged: (canPublish: Boolean) -> Unit,
        onParticipantJoined: (participantIdentity: String) -> Unit,
        onParticipantLeft: (participantIdentity: String, departureKind: LiveParticipantDepartureKind) -> Unit,
    ) {
        session.addListener(onChanged)
        session.onForciblyRemoved = onForciblyRemoved
        session.onUnexpectedlyDisconnected = onUnexpectedlyDisconnected
        session.onPublishPermissionChanged = onPublishPermissionChanged
        session.onParticipantJoined = onParticipantJoined
        session.onParticipantLeft = onParticipantLeft
    }

    fun detachSessionCallbacks(onChanged: () -> Unit) {
        session.removeListener(onChanged)
        session.onForciblyRemoved = null
        session.onUnexpectedlyDisconnected = null
        session.onPublishPermissionChanged = null
        session.onParticipantJoined = null
        session.onParticipantLeft = null
    }

    suspend fun setMicMuted(muted: Boolean) = session.setMicMuted(muted)

    suspend fun setCameraEnabled(enabled: Boolean): Boolean {
        return session.setCameraEnabled(enabled)
    }

    suspend fun flipCamera(): Boolean = session.flipCamera()

    suspend fun setScreenShareEnabled(enabled: Boolean, sourceId: String? = null): Boolean {
        return session.setScreenShareEnabled(enabled, sourceId = sourceId)
    }

    val inputVolume: Double get() = session.inputVolume

    /**
     * Applies the microphone gain. Mute controls pass [persist] as false so a
     * transient muted gain does not become the next process' startup volume.
     */
    suspend fun setInputVolume(volume: Double, persist: Boolean = true) {
        session.setInputVolume(volume)
        if (!persist) return
        try {
            audioDeviceStore.writeInputVolume(session.inputVolume)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failed persist shouldn't undo the live change.
        }
    }

    /**
     * Pin the microphone capture device. Keeps [LiveSession]'s tracked input id
     * in sync so a later mute/unmute republish stays on the chosen device. The
     * native ADM is routed separately by the Settings picker's selectAudioInput;
     * this only keeps the LiveKit republish path consistent. Persisted so a
     * future room join restores the same device.
     */
    suspend fun setInputDeviceId(deviceId: String?) {
        session.setInputDeviceId(deviceId)
        try {
            if (deviceId != null) {
                audioDeviceStore.writeInputDeviceId(deviceId)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failed persist shouldn't undo the live change.
        }
    }

    val outputVolume: Double get() = session.outputVolume

    fun participantVoiceVolume(userId: String): Double {
        return session.participantVoiceVolume(userId)
    }

    suspend fun restoreParticipantVoiceVolume(userId: String): Boolean {
        if (userId.isEmpty() ||
            _restoredParticipantVoiceVolumeIds.contains(userId) ||
            _restoringParticipantVoiceVolumeIds.contains(userId)
        ) {
            return false
        }
        _restoringParticipantVoiceVolumeIds.add(userId)
        val before = session.participantVoiceVolume(userId)
        try {
            val volume = audioDeviceStore.readParticipantVoiceVolume(userId)
            if (_restoredParticipantVoiceVolumeIds.contains(userId)) return false
            session.setParticipantVoiceVolume(userId, volume)
            val restored = session.participantVoiceVolume(userId)
            if (restored > 0) _participantVoiceVolumeBeforeMute[userId] = restored
            _restoredParticipantVoiceVolumeIds.add(userId)
            return restored != before
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _restoredParticipantVoiceVolumeIds.add(userId)
            return false
        } finally {
            _restoringParticipantVoiceVolumeIds.remove(userId)
        }
    }

    /**
     * Applies the playout gain. Headphone mute is transient and must not
     * overwrite the user's last non-zero volume preference.
     */
    suspend fun setOutputVolume(volume: Double, persist: Boolean = true) {
        session.setOutputVolume(volume)
        if (!persist) return
        try {
            audioDeviceStore.writeOutputVolume(session.outputVolume)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failed persist shouldn't undo the live change.
        }
    }

    suspend fun setParticipantVoiceVolume(userId: String, volume: Double) {
        session.setParticipantVoiceVolume(
            userId,
            normalizedParticipantVoiceVolume(volume),
        )
        val normalized = session.participantVoiceVolume(userId)
        if (normalized > 0) _participantVoiceVolumeBeforeMute[userId] = normalized
        _restoredParticipantVoiceVolumeIds.add(userId)
        try {
            audioDeviceStore.writeParticipantVoiceVolume(userId, normalized)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failed persist shouldn't undo the live change; it just won't survive
            // the next launch.
        }
    }

    suspend fun toggleParticipantVoiceMuted(userId: String) {
        val current = session.participantVoiceVolume(userId)
        if (current <= 0) {
            return setParticipantVoiceVolume(
                userId,
                _participantVoiceVolumeBeforeMute[userId] ?: defaultParticipantVoiceVolume,
            )
        }
        _participantVoiceVolumeBeforeMute[userId] = current
        return setParticipantVoiceVolume(userId, 0.0)
    }

    val musicBoxVolume: Double get() = session.musicBoxVolume

    suspend fun setMusicBoxVolume(volume: Double) {
        session.setMusicBoxVolume(volume)
        try {
            audioDeviceStore.writeMusicBoxVolume(volume)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failed persist shouldn't undo the live volume change; it just won't
            // survive the next launch.
        }
    }

    val screenShareVolume: Double get() = session.screenShareVolume

    val watchedScreenShareIdentity: String? get() = session.watchedScreenShareIdentity

    val watchedCameraIdentity: String? get() = session.watchedCameraIdentity

    suspend fun setScreenShareVolume(volume: Double) {
        session.setScreenShareVolume(volume)
        try {
            audioDeviceStore.writeScreenShareVolume(session.screenShareVolume)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failed persist shouldn't undo the live change.
        }
    }

    suspend fun setWatchedScreenShareIdentity(identity: String?) {
        return session.setWatchedScreenShareIdentity(identity)
    }

    suspend fun setWatchedCameraIdentity(identity: String?) {
        return session.setWatchedCameraIdentity(identity)
    }

    val screenShareMaxHeight: Int get() = session.screenShareMaxHeight

    val screenShareFrameRate: Int get() = session.screenShareFrameRate

    /**
     * Apply and persist the screen-share resolution cap. Takes effect on the
     * next share, and re-scales the current share live when one is running.
     */
    suspend fun setScreenShareMaxHeight(height: Int) {
        session.setScreenShareMaxHeight(height)
        try {
            audioDeviceStore.writeScreenShareMaxHeight(height)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failed persist shouldn't undo the live change; it just won't survive
            // the next launch.
        }
    }

    suspend fun setScreenShareFrameRate(frameRate: Int) {
        session.setScreenShareFrameRate(frameRate)
        try {
            // Persist the platform-normalized value (Android currently caps capture
            // at 30 FPS) rather than an unsupported caller request.
            audioDeviceStore.writeScreenShareFrameRate(session.screenShareFrameRate)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failed persist shouldn't undo the live change.
        }
    }

    suspend fun setOutputMuted(muted: Boolean) = session.setOutputMuted(muted)

    suspend fun connectWithRetry(
        result: LiveJoinResult,
        isCancelled: (() -> Boolean)? = null,
        initialMicMuted: Boolean? = null,
    ) {
        var lastError: Throwable? = null
        for (attempt in 0 until 2) {
            if (attempt > 0) {
                delayed(650.milliseconds)
            }
            if (isCancelled?.invoke() == true) throw LiveSessionControllerError("已取消加入直播")
            try {
                val liveKitUrl =
                    resolveLiveKitServerUrl(
                        serverUrl = result.liveKit.serverUrl,
                        apiBaseUrl = apiBaseUrl,
                    )
                restoreStoredAudioSettings()
                session.connect(
                    url = liveKitUrl,
                    token = result.liveKit.token,
                    roomName = result.liveKit.roomName,
                    // The join response can briefly contain the previous process'
                    // participant snapshot. Prefer the state chosen for this join so an
                    // old muted snapshot cannot suppress the initial mic publication.
                    micMuted = initialMicMuted ?: result.participant.micMuted,
                )
                return
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = e
                disconnect()
            }
        }
        throw lastError ?: LiveSessionControllerError("LiveKit 连接失败")
    }

    suspend fun restoreStoredAudioSettings() {
        try {
            val stored = audioDeviceStore.read()
            // Older clients persisted the temporary zero gain used for mute/double
            // mute. The next process starts with mute controls open, so restoring
            // that zero would make the real media state contradict the UI. Heal the
            // legacy value once and keep persistence reserved for usable gains.
            val inputVolume = restoredAudioVolume(stored.inputVolume)
            val outputVolume = restoredAudioVolume(stored.outputVolume)
            session.setInputVolume(inputVolume)
            session.setOutputVolume(outputVolume)
            if (inputVolume != stored.inputVolume) {
                try {
                    audioDeviceStore.writeInputVolume(inputVolume)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Runtime recovery must continue even if the one-time migration
                    // cannot be persisted. A later join can safely retry it.
                }
            }
            if (outputVolume != stored.outputVolume) {
                try {
                    audioDeviceStore.writeOutputVolume(outputVolume)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Keep restoring the remaining audio settings and selected devices.
                }
            }
            session.setMusicBoxVolume(stored.musicBoxVolume)
            session.setScreenShareVolume(stored.screenShareVolume)
            session.setScreenShareMaxHeight(stored.screenShareMaxHeight)
            session.setScreenShareFrameRate(stored.screenShareFrameRate)
            // Capture the published mic from the device the restorer resolved (the
            // user's pinned device). Null leaves LiveKit on the ADM's current
            // device.
            val inputDeviceId = _audioDeviceRestorer(audioDeviceStore)
            session.setInputDeviceId(inputDeviceId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Joining voice should still work with LiveKit's current/default device
            // if a stored local preference cannot be applied.
        }
    }

    suspend fun disconnect(timeout: Duration? = null) {
        val disconnect =
            future {
                try {
                    session.disconnect()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        if (timeout == null) {
            disconnect.await()
            return
        }
        val timeoutCompleter = Completer<Unit>()
        val timeoutTimer =
            Timer(timeout) {
                if (!timeoutCompleter.isCompleted) timeoutCompleter.complete()
            }
        try {
            select {
                disconnect.onAwait { }
                timeoutCompleter.future.onAwait { }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        timeoutTimer.cancel()
    }

    fun dispose() = session.dispose()
}

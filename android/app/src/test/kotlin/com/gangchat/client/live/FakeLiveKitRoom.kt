package com.gangchat.client.live

/** Test doubles for the livekit_client-shaped room interface LiveSession drives. */
internal class FakeAudioTrack : LkAudioTrack {
    val volumes = mutableListOf<Double>()
    val volume: Double? get() = volumes.lastOrNull()

    override suspend fun setVolume(volume: Double) {
        volumes.add(volume)
    }
}

internal class FakeVideoTrack : LkVideoTrack

internal open class FakePublication(
    override val sid: String,
    override val source: LkTrackSource,
    override var muted: Boolean = false,
    override var track: LkTrack? = null,
) : LkTrackPublication

internal class FakeRemotePublication(
    sid: String,
    source: LkTrackSource,
    muted: Boolean = false,
    track: LkTrack? = null,
    override var subscribed: Boolean = true,
) : FakePublication(sid, source, muted, track), LkRemoteTrackPublication {
    override suspend fun subscribe() {
        subscribed = true
    }

    override suspend fun unsubscribe() {
        subscribed = false
    }
}

internal open class FakeParticipantBase(override val identity: String) : LkParticipant {
    val publications = LinkedHashMap<String, LkTrackPublication>()

    override val audioTrackPublications: List<LkTrackPublication>
        get() =
            publications.values.filter {
                it.source == LkTrackSource.microphone || it.source == LkTrackSource.screenShareAudio
            }

    override val videoTrackPublications: List<LkTrackPublication>
        get() =
            publications.values.filter {
                it.source == LkTrackSource.camera || it.source == LkTrackSource.screenShareVideo
            }

    override fun getTrackPublicationBySource(source: LkTrackSource): LkTrackPublication? =
        publications.values.firstOrNull { it.source == source }
}

internal class FakeRemoteParticipant(identity: String) : FakeParticipantBase(identity), LkRemoteParticipant {
    fun addAudio(source: LkTrackSource = LkTrackSource.microphone, muted: Boolean = false): FakeAudioTrack {
        val track = FakeAudioTrack()
        val sid = "TR_${identity}_${publications.size}"
        publications[sid] = FakeRemotePublication(sid, source, muted = muted, track = track)
        return track
    }
}

internal class FakeLocalParticipant(identity: String = "me") : FakeParticipantBase(identity), LkLocalParticipant {
    override var permissions = LkParticipantPermissions()
    val calls = mutableListOf<String>()
    var screenShareOptions: LkScreenShareCaptureOptions? = null
    var screenSharePublishOptions: LkVideoPublishOptions? = null
    var screenShareCaptureAudio: Boolean? = null
    var failMicrophonePublish = false
    private var nextSid = 0

    override fun getTrackPublicationBySid(sid: String): LkTrackPublication? = publications[sid]

    override suspend fun setMicrophoneEnabled(enabled: Boolean, audioCaptureOptions: LkAudioCaptureOptions?) {
        calls.add("mic:$enabled")
        val existing = getTrackPublicationBySource(LkTrackSource.microphone) as FakePublication?
        if (existing != null) {
            existing.muted = !enabled
            return
        }
        if (!enabled) return
        if (failMicrophonePublish) throw LkTrackPublishException()
        val sid = "TR_mic_${nextSid++}"
        publications[sid] = FakePublication(sid, LkTrackSource.microphone, track = FakeAudioTrack())
    }

    override suspend fun setCameraEnabled(enabled: Boolean) {
        calls.add("camera:$enabled")
        val existing = getTrackPublicationBySource(LkTrackSource.camera) as FakePublication?
        if (existing != null) {
            existing.muted = !enabled
            return
        }
        if (!enabled) return
        val sid = "TR_camera_${nextSid++}"
        publications[sid] = FakePublication(sid, LkTrackSource.camera, track = FakeVideoTrack())
    }

    override suspend fun setScreenShareEnabled(
        enabled: Boolean,
        captureScreenAudio: Boolean,
        screenShareCaptureOptions: LkScreenShareCaptureOptions?,
        screenSharePublishOptions: LkVideoPublishOptions?,
    ) {
        calls.add("screen:$enabled")
        if (enabled) {
            screenShareCaptureAudio = captureScreenAudio
            screenShareOptions = screenShareCaptureOptions
            this.screenSharePublishOptions = screenSharePublishOptions
            val sid = "TR_screen_${nextSid++}"
            publications[sid] = FakePublication(sid, LkTrackSource.screenShareVideo, track = FakeVideoTrack())
        } else {
            val publication = getTrackPublicationBySource(LkTrackSource.screenShareVideo) ?: return
            publications.remove(publication.sid)
        }
    }

    override suspend fun removePublishedTrack(trackSid: String) {
        calls.add("remove:${publications[trackSid]?.source}")
        publications.remove(trackSid)
    }

    override fun updatePublishedVideoTrackDimensions(trackSid: String, dimensions: LkVideoDimensions): Boolean = false
}

internal class FakeLiveKitRoom(
    override val roomOptions: LkRoomOptions,
    var connectError: Exception? = null,
) : LkRoom {
    override var connectionState = LkConnectionState.disconnected
    override val localParticipant = FakeLocalParticipant()
    override val remoteParticipants = LinkedHashMap<String, LkRemoteParticipant>()
    var handler: ((LkRoomEvent) -> Unit)? = null
    var connects = 0
    var disconnects = 0
    var disposes = 0

    override fun listen(onEvent: (LkRoomEvent) -> Unit): LkCancelListenFunc {
        handler = onEvent
        return { handler = null }
    }

    override suspend fun connect(url: String, token: String) {
        connects += 1
        connectError?.let { throw it }
        connectionState = LkConnectionState.connected
    }

    override suspend fun disconnect() {
        disconnects += 1
        connectionState = LkConnectionState.disconnected
    }

    override suspend fun dispose() {
        disposes += 1
    }

    fun addRemote(identity: String): FakeRemoteParticipant =
        FakeRemoteParticipant(identity).also { remoteParticipants[identity] = it }

    fun emit(event: LkRoomEvent) {
        handler?.invoke(event)
    }
}

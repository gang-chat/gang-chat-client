package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.live.LiveParticipantDepartureKind
import com.gangchat.client.live.LiveSession
import com.gangchat.client.protocol.LiveJoinResult
import com.gangchat.client.protocol.LiveKitConnectionInfo
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.testing.GangTestRule
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Port of test/unit/app/live_session_controller_test.dart. */
class LiveSessionControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `connectWithRetry restores audio settings and retries LiveKit connect`() = gang.runTest {
        val session = FakeLiveSession(failFirstConnect = true)
        var restoredDevices = 0
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = FakeAudioDeviceStore(),
                audioDeviceRestorer = {
                    restoredDevices += 1
                    null
                },
            )

        controller.connectWithRetry(_liveJoinResult)

        assertEquals(2, session.connectAttempts)
        assertEquals(1, session.disconnects)
        assertEquals(listOf(0.35, 0.35), session.inputVolumes)
        assertEquals(listOf(0.75, 0.75), session.outputVolumes)
        assertEquals(listOf(0.5, 0.5), session.screenShareVolumes)
        assertEquals(2, restoredDevices)
        assertEquals("wss://live.example.test", session.connectedUrl)
        assertEquals("room_live_1", session.connectedRoomName)
        assertEquals(true, session.connectedMicMuted)
    }

    @Test
    fun `disconnect respects timeout when LiveKit teardown hangs`() = gang.runTest {
        val session = FakeLiveSession(hangDisconnect = true)
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = FakeAudioDeviceStore(),
                audioDeviceRestorer = { null },
            )

        controller.disconnect(timeout = 1.milliseconds)

        assertEquals(1, session.disconnects)
    }

    @Test
    fun `live session callbacks attach through controller boundary`() = gang.runTest {
        val session = FakeLiveSession()
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = FakeAudioDeviceStore(),
                audioDeviceRestorer = { null },
            )
        var changes = 0
        var removals = 0
        var unexpectedDisconnects = 0
        val joins = mutableListOf<String>()
        val leaves = mutableListOf<String>()
        val publishPermissions = mutableListOf<Boolean>()
        val onChanged: () -> Unit = { changes += 1 }

        controller.attachSessionCallbacks(
            onChanged = onChanged,
            onForciblyRemoved = { removals += 1 },
            onUnexpectedlyDisconnected = { unexpectedDisconnects += 1 },
            onPublishPermissionChanged = { publishPermissions.add(it) },
            onParticipantJoined = { joins.add(it) },
            onParticipantLeft = { identity, kind -> leaves.add("$identity:${kind.name}") },
        )
        session.emitChange()
        session.onForciblyRemoved?.invoke()
        session.onUnexpectedlyDisconnected?.invoke()
        session.onPublishPermissionChanged?.invoke(false)
        session.onParticipantJoined?.invoke("user-2")
        session.onParticipantLeft?.invoke("user-3", LiveParticipantDepartureKind.removed)

        assertEquals(1, changes)
        assertEquals(1, removals)
        assertEquals(1, unexpectedDisconnects)
        assertEquals(listOf(false), publishPermissions)
        assertEquals(listOf("user-2"), joins)
        assertEquals(listOf("user-3:removed"), leaves)

        controller.detachSessionCallbacks(onChanged = onChanged)
        session.emitChange()
        assertEquals(1, changes)
        assertNull(session.onForciblyRemoved)
        assertNull(session.onUnexpectedlyDisconnected)
        assertNull(session.onPublishPermissionChanged)
        assertNull(session.onParticipantJoined)
        assertNull(session.onParticipantLeft)
    }

    @Test
    fun `live session media controls proxy through controller`() = gang.runTest {
        val session = FakeLiveSession()
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = FakeAudioDeviceStore(),
                audioDeviceRestorer = { null },
            )

        controller.setMicMuted(true)
        controller.setCameraEnabled(true)
        controller.flipCamera()
        controller.setScreenShareEnabled(true, sourceId = "screen_1")
        controller.setOutputMuted(true)
        controller.setInputVolume(0.4)
        controller.setOutputVolume(0.6)
        controller.setParticipantVoiceVolume("user_2", 0.25)
        controller.setScreenShareVolume(0.8)

        assertEquals(listOf(true), session.micMutes)
        assertEquals(listOf(true), session.cameraEnables)
        assertEquals(1, session.cameraFlips)
        assertEquals(listOf(true), session.screenShareEnables)
        assertEquals(listOf<String?>("screen_1"), session.screenShareSourceIds)
        assertEquals(listOf(true), session.outputMutes)
        assertEquals(listOf(0.4), session.inputVolumes)
        assertEquals(listOf(0.6), session.outputVolumes)
        assertEquals(listOf("user_2:0.25"), session.participantVoiceVolumeWrites)
        assertEquals(0.25, controller.participantVoiceVolume("user_2"), 0.0)
        assertEquals(listOf(0.8), session.screenShareVolumes)
    }

    @Test
    fun `live session volume controls persist through the local store`() = gang.runTest {
        val session = FakeLiveSession()
        val store = RecordingAudioDeviceStore()
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = store,
                audioDeviceRestorer = { null },
            )

        controller.setInputVolume(0.4)
        controller.setOutputVolume(0.6)
        controller.setParticipantVoiceVolume("user_2", 1.5)
        controller.setScreenShareVolume(0.8)

        assertEquals(listOf(0.4), store.inputVolumeWrites)
        assertEquals(listOf(0.6), store.outputVolumeWrites)
        assertEquals(listOf("user_2:1.50"), store.participantVoiceVolumeWrites)
        assertEquals(listOf(0.8), store.screenShareVolumeWrites)
    }

    @Test
    fun `transient mute gains do not overwrite stored volumes`() = gang.runTest {
        val session = FakeLiveSession()
        val store = RecordingAudioDeviceStore()
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = store,
                audioDeviceRestorer = { null },
            )

        controller.setInputVolume(0.0, persist = false)
        controller.setOutputVolume(0.0, persist = false)

        assertEquals(listOf(0.0), session.inputVolumes)
        assertEquals(listOf(0.0), session.outputVolumes)
        assertTrue(store.inputVolumeWrites.isEmpty())
        assertTrue(store.outputVolumeWrites.isEmpty())
    }

    @Test
    fun `stored legacy mute gains are healed before joining`() = gang.runTest {
        val session = FakeLiveSession()
        val store = RecordingAudioDeviceStore(storedInputVolume = 0.0, storedOutputVolume = 0.0)
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = store,
                audioDeviceRestorer = { null },
            )

        controller.restoreStoredAudioSettings()

        assertEquals(listOf(defaultAudioVolume), session.inputVolumes)
        assertEquals(listOf(defaultAudioVolume), session.outputVolumes)
        assertEquals(listOf(defaultAudioVolume), store.inputVolumeWrites)
        assertEquals(listOf(defaultAudioVolume), store.outputVolumeWrites)
    }

    @Test
    fun `legacy gain migration failure does not stop remaining restore`() = gang.runTest {
        val session = FakeLiveSession()
        val store =
            RecordingAudioDeviceStore(
                storedInputVolume = 0.0,
                storedOutputVolume = 0.0,
                storedScreenShareMaxHeight = 480,
                failVolumeWrites = true,
            )
        var restoredDevices = 0
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = store,
                audioDeviceRestorer = {
                    restoredDevices += 1
                    null
                },
            )

        controller.restoreStoredAudioSettings()

        assertEquals(listOf(defaultAudioVolume), session.inputVolumes)
        assertEquals(listOf(defaultAudioVolume), session.outputVolumes)
        assertEquals(listOf(480), session.screenShareMaxHeights)
        assertEquals(listOf(30), session.screenShareFrameRates)
        assertEquals(1, restoredDevices)
    }

    @Test
    fun `current join mic choice overrides a stale muted snapshot`() = gang.runTest {
        val session = FakeLiveSession()
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = FakeAudioDeviceStore(),
                audioDeviceRestorer = { null },
            )

        controller.connectWithRetry(_liveJoinResult, initialMicMuted = false)

        assertTrue(_liveJoinResult.participant.micMuted)
        assertEquals(false, session.connectedMicMuted)
    }

    @Test
    fun `participant voice volume restores and toggles local mute`() = gang.runTest {
        val session = FakeLiveSession()
        val store = RecordingAudioDeviceStore(storedParticipantVoiceVolumes = mapOf("user_2" to 1.5))
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = store,
                audioDeviceRestorer = { null },
            )

        assertTrue(controller.restoreParticipantVoiceVolume("user_2"))
        assertEquals(1.5, controller.participantVoiceVolume("user_2"), 0.0)

        controller.toggleParticipantVoiceMuted("user_2")
        assertEquals(0.0, controller.participantVoiceVolume("user_2"), 0.0)

        controller.toggleParticipantVoiceMuted("user_2")
        assertEquals(1.5, controller.participantVoiceVolume("user_2"), 0.0)
        assertEquals(
            listOf("user_2:1.50", "user_2:0.00", "user_2:1.50"),
            session.participantVoiceVolumeWrites,
        )
        assertEquals(listOf("user_2:0.00", "user_2:1.50"), store.participantVoiceVolumeWrites)
    }

    @Test
    fun `setScreenShareMaxHeight applies to the session and persists`() = gang.runTest {
        val session = FakeLiveSession()
        val store = RecordingAudioDeviceStore()
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = store,
                audioDeviceRestorer = { null },
            )

        controller.setScreenShareMaxHeight(720)

        assertEquals(listOf(720), session.screenShareMaxHeights)
        assertEquals(listOf(720), store.screenShareWrites)
    }

    @Test
    fun `setScreenShareFrameRate applies to the session and persists`() = gang.runTest {
        val session = FakeLiveSession()
        val store = RecordingAudioDeviceStore()
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = store,
                audioDeviceRestorer = { null },
            )

        controller.setScreenShareFrameRate(30)

        assertEquals(listOf(30), session.screenShareFrameRates)
        assertEquals(listOf(30), store.screenShareFrameRateWrites)
    }

    @Test
    fun `connectWithRetry restores the stored screen-share height`() = gang.runTest {
        val session = FakeLiveSession()
        val store = RecordingAudioDeviceStore(storedScreenShareMaxHeight = 480, storedScreenShareFrameRate = 15)
        val controller =
            LiveSessionController(
                apiBaseUrl = "https://api.example.test/api/v1",
                session = session,
                audioDeviceStore = store,
                audioDeviceRestorer = { null },
            )

        controller.connectWithRetry(_liveJoinResult)

        assertEquals(listOf(480), session.screenShareMaxHeights)
        assertEquals(listOf(15), session.screenShareFrameRates)
    }
}

private fun _fixed2(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

/**
 * The Dart fake only overrides `read()`; its writes fall through to the base
 * class, which throws `UnimplementedError` and the controller's `catch (_)`
 * swallows. The Kotlin base throws [NotImplementedError] (an `Error`, which
 * the ported `catch (_: Exception)` does not catch), so the fake throws an
 * ordinary exception from the writes the tests reach to keep that shape.
 */
private class FakeAudioDeviceStore : AudioDeviceStore() {
    override suspend fun read(): StoredAudioDevices {
        return StoredAudioDevices(inputVolume = 0.35, outputVolume = 0.75)
    }

    override suspend fun writeInputVolume(volume: Double) {
        throw UnsupportedOperationException("AudioDeviceStore.writeInputVolume must be implemented.")
    }

    override suspend fun writeOutputVolume(volume: Double) {
        throw UnsupportedOperationException("AudioDeviceStore.writeOutputVolume must be implemented.")
    }

    override suspend fun writeScreenShareVolume(volume: Double) {
        throw UnsupportedOperationException("AudioDeviceStore.writeScreenShareVolume must be implemented.")
    }
}

private class FakeLiveSession(
    val failFirstConnect: Boolean = false,
    val hangDisconnect: Boolean = false,
) : LiveSession() {
    var connectAttempts = 0
    var disconnects = 0
    val inputVolumes = mutableListOf<Double>()
    val outputVolumes = mutableListOf<Double>()
    val participantVoiceVolumeWrites = mutableListOf<String>()
    val screenShareVolumes = mutableListOf<Double>()
    val outputMutes = mutableListOf<Boolean>()
    val micMutes = mutableListOf<Boolean>()
    val cameraEnables = mutableListOf<Boolean>()
    var cameraFlips = 0
    val screenShareEnables = mutableListOf<Boolean>()
    val screenShareSourceIds = mutableListOf<String?>()
    var connectedUrl: String? = null
    var connectedRoomName: String? = null
    var connectedMicMuted: Boolean? = null
    private var _fakeInputVolume = defaultAudioVolume
    private var _fakeOutputVolume = defaultAudioVolume
    private var _fakeScreenShareVolume = defaultAudioVolume
    private var _fakeScreenShareFrameRate = 30
    private val _fakeParticipantVoiceVolumes = LinkedHashMap<String, Double>()
    val screenShareMaxHeights = mutableListOf<Int>()
    val screenShareFrameRates = mutableListOf<Int>()

    fun emitChange() = notifyListeners()

    override val inputVolume: Double get() = _fakeInputVolume

    override val outputVolume: Double get() = _fakeOutputVolume

    override fun participantVoiceVolume(userId: String): Double {
        return _fakeParticipantVoiceVolumes[userId] ?: 1.0
    }

    override val screenShareVolume: Double get() = _fakeScreenShareVolume

    override val screenShareFrameRate: Int get() = _fakeScreenShareFrameRate

    override suspend fun connect(url: String, token: String, roomName: String, micMuted: Boolean) {
        connectAttempts += 1
        if (failFirstConnect && connectAttempts == 1) {
            throw IllegalStateException("first connect failed")
        }
        connectedUrl = url
        connectedRoomName = roomName
        connectedMicMuted = micMuted
    }

    override suspend fun disconnect() {
        disconnects += 1
        if (hangDisconnect) CompletableDeferred<Unit>().await()
    }

    override suspend fun setInputVolume(volume: Double) {
        _fakeInputVolume = volume
        inputVolumes.add(volume)
    }

    override suspend fun setOutputVolume(volume: Double) {
        _fakeOutputVolume = volume
        outputVolumes.add(volume)
    }

    override suspend fun setParticipantVoiceVolume(userId: String, volume: Double) {
        _fakeParticipantVoiceVolumes[userId] = volume
        participantVoiceVolumeWrites.add("$userId:${_fixed2(volume)}")
    }

    override suspend fun setScreenShareVolume(volume: Double) {
        _fakeScreenShareVolume = volume
        screenShareVolumes.add(volume)
    }

    override suspend fun setOutputMuted(muted: Boolean) {
        outputMutes.add(muted)
    }

    override suspend fun setMicMuted(muted: Boolean) {
        micMutes.add(muted)
    }

    override suspend fun setCameraEnabled(enabled: Boolean): Boolean {
        cameraEnables.add(enabled)
        return enabled
    }

    override suspend fun flipCamera(): Boolean {
        cameraFlips += 1
        return true
    }

    override suspend fun setScreenShareEnabled(enabled: Boolean, sourceId: String?): Boolean {
        screenShareEnables.add(enabled)
        screenShareSourceIds.add(sourceId)
        return enabled
    }

    override suspend fun setScreenShareMaxHeight(height: Int) {
        screenShareMaxHeights.add(height)
    }

    override suspend fun setScreenShareFrameRate(frameRate: Int) {
        _fakeScreenShareFrameRate = frameRate
        screenShareFrameRates.add(frameRate)
    }
}

private class RecordingAudioDeviceStore(
    val storedScreenShareMaxHeight: Int = 720,
    val storedScreenShareFrameRate: Int = 30,
    val storedInputVolume: Double = 0.35,
    val storedOutputVolume: Double = 0.75,
    val failVolumeWrites: Boolean = false,
    storedParticipantVoiceVolumes: Map<String, Double> = emptyMap(),
) : AudioDeviceStore() {
    private val _storedParticipantVoiceVolumes = LinkedHashMap(storedParticipantVoiceVolumes)
    val inputVolumeWrites = mutableListOf<Double>()
    val outputVolumeWrites = mutableListOf<Double>()
    val participantVoiceVolumeWrites = mutableListOf<String>()
    val screenShareVolumeWrites = mutableListOf<Double>()
    val screenShareWrites = mutableListOf<Int>()
    val screenShareFrameRateWrites = mutableListOf<Int>()

    override suspend fun read(): StoredAudioDevices {
        return StoredAudioDevices(
            inputVolume = storedInputVolume,
            outputVolume = storedOutputVolume,
            screenShareVolume = 1.0,
            screenShareMaxHeight = storedScreenShareMaxHeight,
            screenShareFrameRate = storedScreenShareFrameRate,
        )
    }

    override suspend fun writeInputVolume(volume: Double) {
        if (failVolumeWrites) throw IllegalStateException("input volume write failed")
        inputVolumeWrites.add(volume)
    }

    override suspend fun writeOutputVolume(volume: Double) {
        if (failVolumeWrites) throw IllegalStateException("output volume write failed")
        outputVolumeWrites.add(volume)
    }

    override suspend fun readParticipantVoiceVolume(userId: String): Double {
        return _storedParticipantVoiceVolumes[userId] ?: 1.0
    }

    override suspend fun writeParticipantVoiceVolume(userId: String, volume: Double) {
        _storedParticipantVoiceVolumes[userId] = volume
        participantVoiceVolumeWrites.add("$userId:${_fixed2(volume)}")
    }

    override suspend fun writeScreenShareVolume(volume: Double) {
        screenShareVolumeWrites.add(volume)
    }

    override suspend fun writeScreenShareMaxHeight(height: Int) {
        screenShareWrites.add(height)
    }

    override suspend fun writeScreenShareFrameRate(frameRate: Int) {
        screenShareFrameRateWrites.add(frameRate)
    }
}

private val _sender =
    UserSummary(
        id = "user_1",
        username = "alice",
        displayName = "Alice",
        avatarUrl = null,
        defaultAvatarKey = "blue-3",
    )

private val _participant =
    LiveParticipant(
        liveSessionId = "live_1",
        user = _sender,
        joinedAt = DateTime.utc(2026, 6, 1),
        micMuted = true,
        headphonesMuted = false,
        voiceBlocked = false,
        cameraOn = false,
        screenSharing = false,
        connectionState = "joined",
    )

private val _liveJoinResult =
    LiveJoinResult(
        liveKit =
            LiveKitConnectionInfo(
                serverUrl = "wss://live.example.test",
                token = "token",
                tokenExpiresAt = DateTime.utc(2026, 6, 1, 1),
                roomName = "room_live_1",
            ),
        participant = _participant,
        live =
            LiveState(
                roomId = "room_1",
                participantCount = 1,
                participants = listOf(_participant),
                updatedAt = DateTime.utc(2026, 6, 1),
            ),
    )

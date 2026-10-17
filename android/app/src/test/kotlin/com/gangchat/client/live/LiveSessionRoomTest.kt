package com.gangchat.client.live

import com.gangchat.client.platform.webrtc.WebRtc
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.pumpEventQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * Port-specific coverage of LiveSession against a fake LiveKit room: the
 * connection, mute, volume, presence, reconnect and screen-share paths that
 * the Dart suite could only exercise on a device.
 */
class LiveSessionRoomTest {
    @get:Rule
    val gang = GangTestRule()

    private val rooms = mutableListOf<FakeLiveKitRoom>()

    private fun session(connectError: Exception? = null): LiveSession =
        LiveSession(
            inputRebinderFactory = { null },
            outputRebinderFactory = { null },
            cameraVideoInputCountProvider = { 2 },
            roomFactory = { options -> FakeLiveKitRoom(options, connectError).also(rooms::add) },
        )

    private suspend fun LiveSession.join(micMuted: Boolean = false): FakeLiveKitRoom {
        connect(url = "wss://live.example.test", token = "token", roomName = "room_1", micMuted = micMuted)
        return rooms.last()
    }

    @Test
    fun `joining unmuted publishes the microphone with app-local gain`() = gang.runTest {
        val session = session()
        try {
            val room = session.join()

            assertTrue(session.isConnected)
            assertFalse(room.roomOptions.adaptiveStream)
            assertTrue(room.roomOptions.dynacast)
            assertFalse(room.roomOptions.defaultAudioCaptureOptions.stopAudioCaptureOnMute)
            assertNotNull(room.localParticipant.getTrackPublicationBySource(LkTrackSource.microphone))
            assertFalse(session.localMicMuted)
            assertEquals(0.5, WebRtc.capturePostProcessing.volume, 0.0)
            assertEquals(mapOf("me" to false), session.micMutedByIdentity)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `muting keeps the published track and zeroes the local gain`() = gang.runTest {
        val session = session()
        try {
            val room = session.join()

            session.setMicMuted(true)

            val publication = room.localParticipant.getTrackPublicationBySource(LkTrackSource.microphone)
            assertNotNull(publication)
            assertFalse(publication!!.muted)
            assertTrue(session.localMicMuted)
            assertEquals(0.0, WebRtc.capturePostProcessing.volume, 0.0)
            assertEquals(mapOf("me" to true), session.micMutedByIdentity)

            session.setMicMuted(false)
            assertEquals(0.5, WebRtc.capturePostProcessing.volume, 0.0)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `joining muted defers the microphone publication until unmute`() = gang.runTest {
        val session = session()
        try {
            val room = session.join(micMuted = true)

            assertNull(room.localParticipant.getTrackPublicationBySource(LkTrackSource.microphone))
            assertTrue(session.localMicMuted)

            session.setMicMuted(false)

            assertNotNull(room.localParticipant.getTrackPublicationBySource(LkTrackSource.microphone))
            assertFalse(session.localMicMuted)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `a failed microphone publication reports the microphone as muted`() = gang.runTest {
        val session = session()
        try {
            session.join(micMuted = true)
            rooms.last().localParticipant.failMicrophonePublish = true

            session.setMicMuted(false)

            assertTrue(session.localMicMuted)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `voice volume multiplies output volume and screen audio follows the watched share`() = gang.runTest {
        val session = session()
        try {
            val room = session.join()
            val other = room.addRemote("user-2")
            val voice = other.addAudio()
            val otherAux = room.addRemote("user-2--screen-audio")
            val otherScreenAudio = otherAux.addAudio(LkTrackSource.screenShareAudio)
            val ownAux = room.addRemote("me--screen-audio")
            val ownScreenAudio = ownAux.addAudio(LkTrackSource.screenShareAudio)

            session.setOutputVolume(0.5)
            session.setParticipantVoiceVolume("user-2", 2.0)
            assertEquals(1.0, voice.volume!!, 0.0)

            session.setOutputMuted(true)
            assertEquals(0.0, voice.volume!!, 0.0)
            session.setOutputMuted(false)

            session.setScreenShareVolume(0.8)
            assertEquals(0.0, otherScreenAudio.volume!!, 0.0)

            session.setWatchedScreenShareIdentity("user-2")
            assertEquals(0.8, otherScreenAudio.volume!!, 0.0)
            // Self-echo suppression: the local user's own aux track never plays.
            assertEquals(0.0, ownScreenAudio.volume!!, 0.0)
            // Hidden aux participants are never users.
            assertEquals(setOf("me", "user-2"), session.connectedParticipantIdentities)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `presence callbacks fire for real users after the initial connection`() = gang.runTest {
        val session = session()
        try {
            val room = session.join()
            val joined = mutableListOf<String>()
            val left = mutableListOf<String>()
            session.onParticipantJoined = { joined.add(it) }
            session.onParticipantLeft = { identity, kind -> left.add("$identity:${kind.name}") }

            val user = room.addRemote("user-3")
            room.emit(LkParticipantConnectedEvent(user))
            room.emit(LkParticipantConnectedEvent(room.addRemote("user-3--screen-audio")))
            room.emit(LkParticipantConnectedEvent(room.addRemote("__musicbox__")))
            room.remoteParticipants.remove("user-3")
            room.emit(LkParticipantDisconnectedEvent(user, reason = LkDisconnectReason.participantRemoved))

            assertEquals(listOf("user-3"), joined)
            assertEquals(listOf("user-3:removed"), left)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `a resumed reconnect republishes the microphone and replays only real presence changes`() =
        gang.runTest {
            val session = session()
            try {
                val room = session.join()
                room.addRemote("stay")
                room.addRemote("gone")
                val joined = mutableListOf<String>()
                val left = mutableListOf<String>()
                session.onParticipantJoined = { joined.add(it) }
                session.onParticipantLeft = { identity, kind -> left.add("$identity:${kind.name}") }

                room.emit(LkRoomResumingEvent(participantIdentities = setOf("stay", "gone"), hadMicrophonePublication = true))
                room.remoteParticipants.remove("gone")
                // Events raised while reconnecting do not reach presence callbacks.
                room.emit(LkParticipantDisconnectedEvent(FakeRemoteParticipant("gone")))
                room.addRemote("new")
                room.emit(LkParticipantConnectedEvent(FakeRemoteParticipant("new")))
                room.localParticipant.calls.clear()
                room.emit(LkRoomReconnectedEvent())
                pumpEventQueue()

                assertEquals(listOf("remove:microphone", "mic:true"), room.localParticipant.calls)
                assertEquals(listOf("gone:left"), left)
                assertEquals(listOf("new"), joined)
                assertNotNull(room.localParticipant.getTrackPublicationBySource(LkTrackSource.microphone))
            } finally {
                session.dispose()
            }
        }

    @Test
    fun `a failed connect tears the room down with the Chinese error`() = gang.runTest {
        val session = session(connectError = IllegalStateException("signal failed"))
        try {
            try {
                session.join()
                fail("expected LiveSessionConnectException")
            } catch (error: LiveSessionConnectException) {
                assertEquals("无法连接语音服务", error.toString())
            }
            val room = rooms.last()
            assertEquals(1, room.disposes)
            assertNull(room.handler)
            assertFalse(session.isConnected)
            assertNull(session.roomName)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `a forced removal stops events and asks the UI to leave`() = gang.runTest {
        val session = session()
        try {
            val room = session.join()
            var removals = 0
            var reconnects = 0
            session.onForciblyRemoved = { removals += 1 }
            session.onUnexpectedlyDisconnected = { reconnects += 1 }

            room.emit(LkRoomDisconnectedEvent(LkDisconnectReason.roomDeleted))

            assertEquals(1, removals)
            assertEquals(0, reconnects)

            session.disconnect()
            assertNull(room.handler)
            assertEquals(1, room.disconnects)
            assertEquals(1, room.disposes)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `screen share publishes a single high-priority layer without main-room audio`() = gang.runTest {
        val session = session()
        try {
            val room = session.join()
            session.setCameraEnabled(true)

            assertTrue(session.setScreenShareEnabled(true))

            val local = room.localParticipant
            assertEquals(false, local.screenShareCaptureAudio)
            assertEquals(true, local.screenShareOptions!!.captureScreenAudio)
            val publish = local.screenSharePublishOptions!!
            assertFalse(publish.simulcast)
            val encoding = publish.screenShareEncoding!!
            assertEquals(30, encoding.maxFramerate)
            assertEquals(3_000_000, encoding.maxBitrate)
            assertEquals(LkPriority.high, encoding.bitratePriority)
            assertEquals(LkPriority.high, encoding.networkPriority)
            // Sharing turns the camera off first.
            assertTrue(local.getTrackPublicationBySource(LkTrackSource.camera)!!.muted)
            assertTrue(session.isScreenSharing)
            assertEquals(1, session.videoTracks.count { it.isScreenShare && it.isLocal })

            assertFalse(session.setScreenShareEnabled(false))
            assertNull(local.getTrackPublicationBySource(LkTrackSource.screenShareVideo))
            assertFalse(session.isScreenSharing)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun `screen share frame rate is capped at the Android capture rate`() = gang.runTest {
        val session = session()
        try {
            session.setScreenShareFrameRate(60)
            assertEquals(30, session.screenShareFrameRate)
            session.setScreenShareMaxHeight(1080)
            assertEquals(1080, session.screenShareMaxHeight)
            session.setScreenShareMaxHeight(999)
            assertEquals(720, session.screenShareMaxHeight)
        } finally {
            session.dispose()
        }
    }
}

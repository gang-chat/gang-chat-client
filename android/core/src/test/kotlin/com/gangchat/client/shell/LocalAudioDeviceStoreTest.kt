package com.gangchat.client.shell

import com.gangchat.client.app.normalizedAudioVolume
import com.gangchat.client.plugins.SharedPreferences
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalAudioDeviceStoreTest {
    @get:Rule
    val gang = GangTestRule()

    @Before
    fun setUp() {
        SharedPreferences.setMockInitialValues(emptyMap())
    }

    @Test
    fun `read returns defaults when nothing is stored`() = gang.runTest {
        val store = LocalAudioDeviceStore()

        val stored = store.read()

        assertNull(stored.inputDeviceId)
        assertNull(stored.outputDeviceId)
        assertEquals(0.5, stored.inputVolume, 0.0)
        assertEquals(0.5, stored.outputVolume, 0.0)
        assertEquals(0.5, stored.musicBoxVolume, 0.0)
        assertEquals(0.5, stored.screenShareVolume, 0.0)
        assertEquals(720, stored.screenShareMaxHeight)
        assertEquals(30, stored.screenShareFrameRate)
        assertEquals(1.0, store.readParticipantVoiceVolume("user_2"), 0.0)
    }

    @Test
    fun `writes round-trip through SharedPreferences`() = gang.runTest {
        val store = LocalAudioDeviceStore()

        store.writeInputDevicePreference(
            deviceId = "87",
            label = "Desk Mic",
            groupId = "group_mic",
        )
        store.writeOutputDevicePreference(
            deviceId = "54",
            label = "USB Headset",
            groupId = "group_headset",
        )
        store.writeInputVolume(0.4)
        store.writeOutputVolume(0.6)
        store.writeMusicBoxVolume(0.2)
        store.writeScreenShareVolume(0.8)
        store.writeScreenShareMaxHeight(720)
        store.writeScreenShareFrameRate(30)
        store.writeParticipantVoiceVolume("user/2", 1.75)

        val stored = store.read()
        assertEquals("87", stored.inputDeviceId)
        assertEquals("Desk Mic", stored.inputDeviceLabel)
        assertEquals("group_mic", stored.inputDeviceGroupId)
        assertEquals("54", stored.outputDeviceId)
        assertEquals("USB Headset", stored.outputDeviceLabel)
        assertEquals("group_headset", stored.outputDeviceGroupId)
        assertEquals(0.4, stored.inputVolume, 1e-9)
        assertEquals(0.6, stored.outputVolume, 1e-9)
        assertEquals(0.2, stored.musicBoxVolume, 1e-9)
        assertEquals(0.8, stored.screenShareVolume, 1e-9)
        assertEquals(720, stored.screenShareMaxHeight)
        assertEquals(30, stored.screenShareFrameRate)
        assertEquals(
            1.75,
            store.readParticipantVoiceVolume("user/2"),
            1e-9,
        )
    }

    @Test
    fun `keeps an explicitly selected desktop high profile`() = gang.runTest {
        val store = LocalAudioDeviceStore()

        store.writeScreenShareMaxHeight(1080)
        store.writeScreenShareFrameRate(60)
        val stored = store.read()

        assertEquals(1080, stored.screenShareMaxHeight)
        assertEquals(60, stored.screenShareFrameRate)
    }

    @Test
    fun `screen share height is coerced to a supported option on write`() = gang.runTest {
        val store = LocalAudioDeviceStore()

        store.writeScreenShareMaxHeight(999)
        val stored = store.read()

        assertEquals(720, stored.screenShareMaxHeight)
    }

    @Test
    fun `screen share frame rate is coerced to a supported option`() = gang.runTest {
        val store = LocalAudioDeviceStore()

        store.writeScreenShareFrameRate(24)
        val stored = store.read()

        assertEquals(30, stored.screenShareFrameRate)
    }

    @Test
    fun `volumes are clamped to the valid range on write`() = gang.runTest {
        val store = LocalAudioDeviceStore()

        store.writeOutputVolume(5.0)
        store.writeScreenShareVolume(-0.5)
        store.writeParticipantVoiceVolume("user_2", 5.0)
        store.writeParticipantVoiceVolume("user_3", -1.0)
        val stored = store.read()

        assertEquals(normalizedAudioVolume(5.0), stored.outputVolume, 0.0)
        assertEquals(1.0, stored.outputVolume, 0.0)
        assertEquals(0.0, stored.screenShareVolume, 0.0)
        assertEquals(2.0, store.readParticipantVoiceVolume("user_2"), 0.0)
        assertEquals(0.0, store.readParticipantVoiceVolume("user_3"), 0.0)
    }
}

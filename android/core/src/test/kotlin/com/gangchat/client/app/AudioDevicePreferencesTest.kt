package com.gangchat.client.app

import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class AudioDevicePreferencesTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `stored audio device ids trim empty storage values`() {
        assertNull(storedAudioDeviceIdFromStorageValue(null))
        assertNull(storedAudioDeviceIdFromStorageValue("  "))
        assertEquals("mic_1", storedAudioDeviceIdFromStorageValue(" mic_1 "))
    }

    @Test
    fun `stored audio volume parsing falls back and clamps`() {
        assertEquals(1.0, storedAudioVolumeFromStorageValue(null), 0.0)
        assertEquals(1.0, storedAudioVolumeFromStorageValue("bad"), 0.0)
        assertEquals(0.0, storedAudioVolumeFromStorageValue("-0.5"), 0.0)
        assertEquals(1.0, storedAudioVolumeFromStorageValue("1.5"), 0.0)
        assertEquals(0.425, storedAudioVolumeFromStorageValue("0.425"), 0.0)
        assertEquals("1.000", audioVolumeStorageString(1.2))
        assertEquals("0.425", audioVolumeStorageString(0.425))
    }

    @Test
    fun `preferredStoredAudioDeviceFrom uses stored id then default fallback`() {
        val devices =
            listOf(
                _Device("default", "Default Mic", "audioinput"),
                _Device("mic_1", "Desk Mic", "audioinput"),
                _Device("speaker_1", "Speaker", "audiooutput"),
            )

        assertSame(
            devices[1],
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audioinput",
                storedDeviceId = "mic_1",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
            ),
        )
        assertSame(
            devices[0],
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audioinput",
                storedDeviceId = "missing",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
            ),
        )
        assertNull(
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audiooutput",
                storedDeviceId = "missing",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
            ),
        )
    }

    @Test
    fun `preferredStoredAudioDeviceFrom restores hotplugged device signature`() {
        val devices =
            listOf(
                _Device("speaker_2", "Desk Speaker", "audiooutput", "group_speaker"),
                _Device("speaker_new", "USB Headset", "audiooutput", "group_headset"),
            )

        assertSame(
            devices[1],
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audiooutput",
                storedDeviceId = "speaker_old",
                storedDeviceLabel = "USB Headset",
                storedDeviceGroupId = "group_headset",
                systemDefaultDeviceId = "speaker_2",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
                labelOf = _labelOf,
                groupIdOf = _groupIdOf,
            ),
        )
    }

    @Test
    fun `preferredStoredAudioDeviceFrom avoids ambiguous label-only restoration`() {
        val devices =
            listOf(
                _Device("speaker_1", "USB Headset", "audiooutput"),
                _Device("speaker_2", "USB Headset", "audiooutput"),
                _Device("speaker_3", "Desk Speaker", "audiooutput"),
            )

        assertSame(
            devices[2],
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audiooutput",
                storedDeviceId = "speaker_old",
                storedDeviceLabel = "USB Headset",
                storedDeviceGroupId = null,
                systemDefaultDeviceId = "speaker_3",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
                labelOf = _labelOf,
                groupIdOf = _groupIdOf,
            ),
        )
    }

    @Test
    fun `preferredStoredAudioDeviceFrom restores unique label after group id churn`() {
        val devices =
            listOf(
                _Device("speaker_new", "USB Headset", "audiooutput", "group_new"),
                _Device("speaker_2", "Desk Speaker", "audiooutput", "group_desk"),
            )

        assertSame(
            devices[0],
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audiooutput",
                storedDeviceId = "speaker_old",
                storedDeviceLabel = "USB Headset",
                storedDeviceGroupId = "group_old",
                systemDefaultDeviceId = "speaker_2",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
                labelOf = _labelOf,
                groupIdOf = _groupIdOf,
            ),
        )
    }

    @Test
    fun `group id churn does not restore an ambiguous device label`() {
        val devices =
            listOf(
                _Device("speaker_a", "USB Headset", "audiooutput", "group_a"),
                _Device("speaker_b", "USB Headset", "audiooutput", "group_b"),
                _Device("speaker_default", "Desk Speaker", "audiooutput", "group_desk"),
            )

        assertSame(
            devices[2],
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audiooutput",
                storedDeviceId = "speaker_old",
                storedDeviceLabel = "USB Headset",
                storedDeviceGroupId = "group_old",
                systemDefaultDeviceId = "speaker_default",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
                labelOf = _labelOf,
                groupIdOf = _groupIdOf,
            ),
        )
    }

    @Test
    fun `preferredStoredAudioDeviceFrom prefers stored, then system default, then synthetic default`() {
        val devices =
            listOf(
                _Device("default", "Default Mic", "audioinput"),
                _Device("mic_1", "Desk Mic", "audioinput"),
                _Device("mic_2", "Room Mic", "audioinput"),
            )

        // A saved preference still wins over the OS default.
        assertSame(
            devices[1],
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audioinput",
                storedDeviceId = "mic_1",
                systemDefaultDeviceId = "mic_2",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
            ),
        )
        // No saved preference: follow the system default the native channel
        // reported, ahead of the synthetic "default" device.
        assertSame(
            devices[2],
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audioinput",
                storedDeviceId = null,
                systemDefaultDeviceId = "mic_2",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
            ),
        )
        // No saved preference and no usable system default: fall back to the
        // synthetic "default" device (the Windows path).
        assertSame(
            devices[0],
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audioinput",
                storedDeviceId = null,
                systemDefaultDeviceId = "missing",
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
            ),
        )
    }

    @Test
    fun `selectStoredAudioDeviceIfChanged skips selected devices`() = gang.runTest {
        val device = _Device("mic_1", "Desk Mic", "audioinput")
        var selects = 0

        val selected =
            selectStoredAudioDeviceIfChanged(
                selected = device,
                device = device,
                select = { _ -> selects += 1 },
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
            )

        assertSame(device, selected)
        assertEquals(0, selects)
    }

    @Test
    fun `selectStoredAudioDeviceIfChanged reports failed selection as null`() = gang.runTest {
        val selected = _Device("mic_1", "Desk Mic", "audioinput")
        val next = _Device("mic_2", "Room Mic", "audioinput")

        assertNull(
            selectStoredAudioDeviceIfChanged(
                selected = selected,
                device = next,
                select = { _ -> throw IllegalStateException("denied") },
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
            ),
        )
    }

    private class _Device(
        val deviceId: String,
        val label: String,
        val kind: String,
        val groupId: String = "",
    )

    private companion object {
        val _kindOf: AudioDeviceKindOf<_Device> = { device -> device.kind }
        val _deviceIdOf: AudioDeviceIdOf<_Device> = { device -> device.deviceId }
        val _labelOf: AudioDeviceLabelOf<_Device> = { device -> device.label }
        val _groupIdOf: AudioDeviceGroupIdOf<_Device> = { device -> device.groupId }
    }
}

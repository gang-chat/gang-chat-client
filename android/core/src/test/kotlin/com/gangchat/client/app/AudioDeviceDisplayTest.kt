package com.gangchat.client.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDeviceDisplayTest {
    @Test
    fun `audioDeviceKey is stable from kind and id`() {
        assertEquals(
            "audioinput:mic_1",
            audioDeviceKey(kind = "audioinput", deviceId = "mic_1"),
        )
        assertEquals(
            "audioinput:mic_1",
            audioDeviceKeyOf(
                _Device("mic_1", "Mic", "audioinput"),
                kindOf = ::_kindOf,
                deviceIdOf = ::_deviceIdOf,
            ),
        )
    }

    @Test
    fun `audio device selection helpers expose busy state`() {
        val device = _Device("mic_1", "Mic", "audioinput")

        assertTrue(canStartAudioDeviceSelection(null))
        assertFalse(canStartAudioDeviceSelection("audioinput:mic_1"))
        assertTrue(
            audioDeviceBusy(
                device,
                "audioinput:mic_1",
                kindOf = ::_kindOf,
                deviceIdOf = ::_deviceIdOf,
            ),
        )
        assertFalse(
            audioDeviceBusy(
                device,
                "audiooutput:mic_1",
                kindOf = ::_kindOf,
                deviceIdOf = ::_deviceIdOf,
            ),
        )
        assertEquals("无法保存音频设备偏好", audioDevicePreferenceSaveFailureMessage("denied"))
    }

    @Test
    fun `isSameAudioDevice compares kind and device id`() {
        val device = _Device("mic_1", "Mic", "audioinput")
        assertTrue(
            isSameAudioDevice(
                device,
                device,
                kindOf = ::_kindOf,
                deviceIdOf = ::_deviceIdOf,
            ),
        )
        assertFalse(
            isSameAudioDevice(
                _Device("mic_1", "Speaker", "audiooutput"),
                device,
                kindOf = ::_kindOf,
                deviceIdOf = ::_deviceIdOf,
            ),
        )
    }

    @Test
    fun `audioDeviceLabels normalizes empty and duplicate labels`() {
        val labels =
            audioDeviceLabels(
                listOf(
                    _Device("default", "", "audioinput"),
                    _Device("communications", "", "audioinput"),
                    _Device("mic_1", "", "audioinput"),
                    _Device("mic_2", "Desk Mic", "audioinput"),
                    _Device("mic_3", "Desk Mic", "audioinput"),
                ),
                fallbackLabel = "麦克风",
                labelOf = ::_labelOf,
                deviceIdOf = ::_deviceIdOf,
            )

        assertEquals(listOf("系统默认", "通信设备", "麦克风 3", "Desk Mic", "Desk Mic #2"), labels)
    }

    @Test
    fun `audioDevicesByKind filters devices by WebRTC kind`() {
        val inputs =
            audioDevicesByKind(
                listOf(
                    _Device("mic_1", "Mic", "audioinput"),
                    _Device("speaker_1", "Speaker", "audiooutput"),
                ),
                "audioinput",
                kindOf = ::_kindOf,
            )

        assertEquals(listOf("mic_1"), inputs.map { device -> device.deviceId })
    }

    @Test
    fun `selectedAudioDeviceFrom picks first matching candidate then fallback`() {
        val devices =
            listOf(
                _Device("mic_1", "Mic 1", "audioinput"),
                _Device("mic_2", "Mic 2", "audioinput"),
            )

        assertEquals(
            "mic_2",
            selectedAudioDeviceFrom(
                devices,
                listOf(
                    _Device("missing", "Missing", "audioinput"),
                    _Device("mic_2", "Mic 2", "audioinput"),
                ),
                kindOf = ::_kindOf,
                deviceIdOf = ::_deviceIdOf,
            )?.deviceId,
        )
        assertEquals(
            "mic_1",
            selectedAudioDeviceFrom<_Device>(
                devices,
                emptyList(),
                kindOf = ::_kindOf,
                deviceIdOf = ::_deviceIdOf,
            )?.deviceId,
        )
        assertNull(
            selectedAudioDeviceFrom(
                emptyList<_Device>(),
                devices,
                kindOf = ::_kindOf,
                deviceIdOf = ::_deviceIdOf,
            ),
        )
    }

    @Test
    fun `audio test tooltips reflect active state`() {
        assertEquals("测试输入音量", audioInputTestTooltip(false))
        assertEquals("停止输入测试", audioInputTestTooltip(true))
        assertEquals("测试输出音量", audioOutputTestTooltip(false))
        assertEquals("停止输出测试", audioOutputTestTooltip(true))
    }

    // Nested so the same-named helpers of the other audio device tests in
    // this package don't clash on the JVM.
    private class _Device(val deviceId: String, val label: String, val kind: String)

    private fun _kindOf(device: _Device): String = device.kind

    private fun _deviceIdOf(device: _Device): String = device.deviceId

    private fun _labelOf(device: _Device): String = device.label
}

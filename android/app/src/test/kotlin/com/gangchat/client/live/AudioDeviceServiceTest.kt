package com.gangchat.client.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of test/unit/live/audio_device_service_test.dart.
 *
 * Not ported (Windows only; they drive the desktop runners'
 * `gang_chat/audio_devices` MethodChannel, which has no Android counterpart):
 * "Windows audio enumeration uses only SystemAudioDevices" and "Windows
 * topology event refreshes the system endpoint list".
 */
class AudioDeviceServiceTest {
    @Test
    fun `only native Windows uses the system-only audio path`() {
        assertTrue(systemAudioOnlyForPlatform(isWeb = false, isWindows = true))
        // macOS keeps its existing CoreAudio/WebRTC merge.
        assertFalse(systemAudioOnlyForPlatform(isWeb = false, isWindows = false))
        // web/Android keep their existing WebRTC implementation.
        assertFalse(systemAudioOnlyForPlatform(isWeb = true, isWindows = false))
    }
}

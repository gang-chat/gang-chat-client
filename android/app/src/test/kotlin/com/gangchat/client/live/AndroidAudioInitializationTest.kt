package com.gangchat.client.live

import org.junit.Assert.assertEquals
import org.junit.Test

/** Port of test/unit/live/android_audio_initialization_test.dart. */
class AndroidAudioInitializationTest {
    @Test
    fun `Android WebRTC initializes playback with media audio attributes`() {
        assertEquals(
            mapOf(
                "androidUseHardwareAudioProcessing" to false,
                "androidAudioConfiguration" to
                    mapOf(
                        "androidAudioAttributesUsageType" to "media",
                        "androidAudioAttributesContentType" to "music",
                    ),
            ),
            androidWebRtcInitializationOptions,
        )
    }
}

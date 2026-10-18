package com.gangchat.client.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLevelsTest {
    @Test
    fun `normalizedAudioVolume clamps volume to audio range`() {
        assertEquals(0.0, normalizedAudioVolume(-0.2), 0.0)
        assertEquals(0.45, normalizedAudioVolume(0.45), 0.0)
        assertEquals(1.0, normalizedAudioVolume(1.7), 0.0)
    }

    @Test
    fun `audioVolumePercentText formats clamped volume`() {
        assertEquals("0%", audioVolumePercentText(-0.2))
        assertEquals("46%", audioVolumePercentText(0.455))
        assertEquals("100%", audioVolumePercentText(1.7))
    }

    @Test
    fun `participant voice volume supports 200 percent boosts`() {
        assertEquals(0.0, normalizedParticipantVoiceVolume(-0.2), 0.0)
        assertEquals(1.25, normalizedParticipantVoiceVolume(1.25), 0.0)
        assertEquals(2.0, normalizedParticipantVoiceVolume(3.0), 0.0)
        assertEquals("125%", participantVoiceVolumePercentText(1.25))
        assertEquals("200%", participantVoiceVolumePercentText(3.0))
    }

    @Test
    fun `level meter helpers calculate segment counts and active bars`() {
        assertEquals(24, audioLevelSegmentCount(120.0))
        assertEquals(35, audioLevelSegmentCount(420.0))
        assertEquals(56, audioLevelSegmentCount(900.0))

        assertEquals(
            4,
            activeAudioLevelSegmentCount(level = 0.31, active = true, segmentCount = 10),
        )
        assertEquals(
            0,
            activeAudioLevelSegmentCount(
                level = 0.31,
                active = false,
                segmentCount = 10,
            ),
        )
        assertEquals(
            10,
            activeAudioLevelSegmentCount(level = 2.0, active = true, segmentCount = 10),
        )
    }

    @Test
    fun `audioLevelFromVisualizerBands uses overall audio energy`() {
        assertEquals(0.0, audioLevelFromVisualizerBands(listOf(0.0, 0.03, null, Double.NaN)), 0.0)

        val singleSpike =
            audioLevelFromVisualizerBands(
                listOf<Any?>(1.0) + List(13) { 0.0 },
            )
        val broadVoice =
            audioLevelFromVisualizerBands(
                List(14) { 0.5 },
            )

        assertTrue(singleSpike > 0)
        assertTrue(singleSpike < 0.7)
        assertTrue(broadVoice > singleSpike)
        assertEquals(1.0, audioLevelFromVisualizerBands(List(14) { 1.0 }), 0.0)
    }

    @Test
    fun `the boost switch multiplies the gain by 1_5`() {
        assertEquals(1.5, audioVolumeBoostFactor, 0.0)
        assertEquals(1.0, boostedAudioVolume(1.0, false), 0.0)
        assertEquals(1.5, boostedAudioVolume(1.0, true), 0.0)
        assertEquals(0.6, boostedAudioVolume(0.4, true), 1e-9)
        // The slider value is clamped to 0..1 before the boost.
        assertEquals(1.5, boostedAudioVolume(2.0, true), 0.0)
        assertEquals(0.0, boostedAudioVolume(-1.0, true), 0.0)
        assertEquals("100%", boostedAudioVolumePercentText(1.0, false))
        assertEquals("150%", boostedAudioVolumePercentText(1.0, true))
        assertEquals("75%", boostedAudioVolumePercentText(0.5, true))
    }
}

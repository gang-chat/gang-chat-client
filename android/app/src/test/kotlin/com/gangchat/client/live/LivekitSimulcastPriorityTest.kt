package com.gangchat.client.live

import io.livekit.android.room.track.VideoEncoding
import livekit.org.webrtc.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Port of test/unit/live/livekit_simulcast_priority_test.dart.
 *
 * The Dart test pinned the livekit_client fork's `computeVideoEncodings`. On
 * Android the LiveKit SDK builds encodings itself through
 * [VideoEncoding.toRtpEncoding]: the full-resolution layer (scale 1.0) carries
 * high bitrate/network priority and every scaled-down layer is low priority.
 * The Dart fork instead moved the priority onto encodings[0] because desktop
 * libwebrtc rejected per-layer priorities; the Android screen share avoids the
 * question entirely by publishing a single layer (simulcast = false, see
 * LiveSessionRoomTest), so only the single-encoding case reaches the wire.
 */
class LivekitSimulcastPriorityTest {
    @Test
    fun `a single encoding keeps its own priority`() {
        val encoding = VideoEncoding(maxBitrate = 3_000_000, maxFps = 30).toRtpEncoding()

        assertNull(encoding.rid)
        assertEquals(3_000_000, encoding.maxBitrateBps)
        assertEquals(30, encoding.maxFramerate)
        assertEquals(Priority.HIGH, encoding.networkPriority)
        assertEquals(4.0, encoding.bitratePriority, 0.0)
    }

    @Test
    fun `simulcast priority is carried only on the full resolution layer`() {
        val low = VideoEncoding(maxBitrate = 1_000_000, maxFps = 15).toRtpEncoding("q", 2.0)
        val full = VideoEncoding(maxBitrate = 3_000_000, maxFps = 30).toRtpEncoding("h", 1.0)

        assertEquals("q", low.rid)
        assertEquals(1_000_000, low.maxBitrateBps)
        assertEquals(Priority.LOW, low.networkPriority)
        assertEquals(1.0, low.bitratePriority, 0.0)
        assertEquals("h", full.rid)
        assertEquals(3_000_000, full.maxBitrateBps)
        assertEquals(Priority.HIGH, full.networkPriority)
        assertEquals(4.0, full.bitratePriority, 0.0)
    }
}

package com.gangchat.client.app

import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkLatencyTest {
    @Test
    fun `classifies latency into unavailable, good, fair, and poor bands`() {
        assertEquals(NetworkLatencyQuality.unavailable, networkLatencyQuality(null))
        assertEquals(
            NetworkLatencyQuality.good,
            networkLatencyQuality(150.milliseconds),
        )
        assertEquals(
            NetworkLatencyQuality.fair,
            networkLatencyQuality(151.milliseconds),
        )
        assertEquals(
            NetworkLatencyQuality.fair,
            networkLatencyQuality(350.milliseconds),
        )
        assertEquals(
            NetworkLatencyQuality.poor,
            networkLatencyQuality(351.milliseconds),
        )
    }

    @Test
    fun `maps latency bands to signal bar counts and tooltip text`() {
        assertEquals(0, networkLatencySignalBars(null))
        assertEquals(3, networkLatencySignalBars(80.milliseconds))
        assertEquals(2, networkLatencySignalBars(220.milliseconds))
        assertEquals(1, networkLatencySignalBars(620.milliseconds))

        assertEquals("延迟检测中", networkLatencyTooltip(null))
        assertEquals("228 ms", networkLatencyTooltip(228.milliseconds))
    }
}

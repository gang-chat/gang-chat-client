package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerClockTest {
    @Test
    fun `parses valid server time headers as UTC`() {
        assertEquals(
            DateTime.utc(2026, 7, 8, 9),
            parseServerTimeHeader("2026-07-08T09:00:00Z"),
        )
        assertNull(parseServerTimeHeader(""))
        assertNull(parseServerTimeHeader("not-a-time"))
    }

    @Test
    fun `normalizes whitespace and explicit timezone offsets to UTC`() {
        assertEquals(
            DateTime.utc(2026, 7, 8, 9),
            parseServerTimeHeader(" 2026-07-08T17:00:00+08:00 "),
        )
    }

    @Test
    fun `derives current time from server time and monotonic elapsed time`() {
        var localNow = DateTime.utc(2026, 7, 8, 1)
        var monotonicNow = Duration.ZERO
        val clock =
            ServerClock(
                localNow = { localNow },
                monotonicNow = { monotonicNow },
            )
        var notifications = 0
        clock.addListener { notifications += 1 }

        assertEquals(localNow, clock.now())

        assertTrue(clock.updateFromHeader("2026-07-08T09:00:00Z"))
        assertEquals(1, notifications)

        localNow = DateTime.utc(2026, 7, 8, 3, 5)
        monotonicNow = 5.minutes
        assertEquals(DateTime.utc(2026, 7, 8, 9, 5), clock.now())

        assertFalse(clock.updateFromHeader("not-a-time"))
        assertEquals(1, notifications)
    }

    @Test
    fun `server-derived time ignores local wall-clock rollback`() {
        var localNow = DateTime.utc(2026, 7, 8, 1)
        var monotonicNow = Duration.ZERO
        val clock =
            ServerClock(
                localNow = { localNow },
                monotonicNow = { monotonicNow },
            )

        assertTrue(clock.updateFromHeader("2026-07-08T09:00:00Z"))
        localNow = DateTime.utc(2020)
        monotonicNow = 3.minutes

        assertEquals(DateTime.utc(2026, 7, 8, 9, 3), clock.now())
    }

    @Test
    fun `a newer server header rebases monotonic elapsed time`() {
        var monotonicNow = Duration.ZERO
        val clock = ServerClock(monotonicNow = { monotonicNow })

        clock.updateFromHeader("2026-07-08T09:00:00Z")
        monotonicNow = 5.minutes
        assertEquals(DateTime.utc(2026, 7, 8, 9, 5), clock.now())

        clock.updateFromHeader("2026-07-08T09:04:00Z")
        monotonicNow = 6.minutes
        assertEquals(DateTime.utc(2026, 7, 8, 9, 5), clock.now())
    }

    @Test
    fun `records the latest successful request round-trip latency`() {
        val clock = ServerClock()
        var notifications = 0
        clock.addListener { notifications += 1 }

        assertNull(clock.requestRoundTrip)
        assertTrue(
            clock.updateRequestRoundTrip(87.milliseconds),
        )

        assertEquals(87.milliseconds, clock.requestRoundTrip)
        assertEquals(1, notifications)
        assertFalse(
            clock.updateRequestRoundTrip((-1).milliseconds),
        )
        assertEquals(87.milliseconds, clock.requestRoundTrip)
        assertEquals(1, notifications)
    }
}

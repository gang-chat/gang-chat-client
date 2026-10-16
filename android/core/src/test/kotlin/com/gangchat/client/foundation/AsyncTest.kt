package com.gangchat.client.foundation

import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.elapse
import com.gangchat.client.testing.pumpEventQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AsyncTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun unawaitedRunsSynchronouslyUntilFirstSuspension() = gang.runTest {
        val log = mutableListOf<String>()
        unawaited {
            log.add("start")
            delayed(Duration.ZERO)
            log.add("after")
        }
        log.add("caller")
        assertEquals(listOf("start", "caller"), log)
        pumpEventQueue()
        assertEquals(listOf("start", "caller", "after"), log)
    }

    @Test
    fun timersFireOnVirtualTime() = gang.runTest {
        var fired = 0
        val timer = Timer(5.seconds) { fired++ }
        elapse(4.seconds)
        assertEquals(0, fired)
        assertTrue(timer.isActive)
        elapse(1.seconds)
        assertEquals(1, fired)
        assertFalse(timer.isActive)
    }

    @Test
    fun zeroTimerIsAsynchronous() = gang.runTest {
        var fired = false
        Timer(Duration.ZERO) { fired = true }
        assertFalse(fired)
        pumpEventQueue()
        assertTrue(fired)
    }

    @Test
    fun periodicTimerCountsTicksAndCancels() = gang.runTest {
        var ticks = 0
        val timer = Timer.periodic(100.milliseconds) { t -> ticks = t.tick }
        elapse(350.milliseconds)
        assertEquals(3, ticks)
        timer.cancel()
        elapse(1.seconds)
        assertEquals(3, ticks)
    }

    @Test
    fun timeoutThrowsDartTimeoutException() = gang.runTest {
        val result =
            runCatching {
                timeout(1.seconds) { delayed(2.seconds) }
            }
        val error = result.exceptionOrNull()
        assertTrue(error is TimeoutException)
        assertEquals("TimeoutException after 0:00:01.000000: Future not completed", error.toString())
    }

    @Test
    fun broadcastStreamDeliversAsynchronouslyInOrder() = gang.runTest {
        val controller = StreamController.broadcast<Int>()
        val seen = mutableListOf<Int>()
        var done = false
        controller.stream.where { it % 2 == 1 }.map { it * 10 }.listen({ seen.add(it) }, onDone = { done = true })
        controller.add(1)
        controller.add(2)
        controller.add(3)
        controller.close()
        assertEquals(emptyList<Int>(), seen)
        pumpEventQueue()
        assertEquals(listOf(10, 30), seen)
        assertTrue(done)
    }

    @Test
    fun singleSubscriptionBuffersUntilListened() = gang.runTest {
        val controller = StreamController<String>()
        controller.add("a")
        controller.add("b")
        val seen = mutableListOf<String>()
        controller.stream.listen({ seen.add(it) })
        pumpEventQueue()
        assertEquals(listOf("a", "b"), seen)
    }

    @Test
    fun firstWhereAwaitsMatchingEvent() = gang.runTest {
        val controller = StreamController.broadcast<Int>()
        val pending = future { controller.stream.firstWhere { it > 1 } }
        controller.add(1)
        controller.add(2)
        pumpEventQueue()
        assertEquals(2, pending.await())
    }

    @Test
    fun utf8LinesSplitsAcrossChunks() = gang.runTest {
        val controller = StreamController<ByteArray>()
        val lines = mutableListOf<String>()
        controller.stream.utf8Lines().listen({ lines.add(it) })
        val bytes = "event: hi\r\ndata: 你好\n\npartial".toByteArray()
        controller.add(bytes.copyOfRange(0, 13))
        controller.add(bytes.copyOfRange(13, bytes.size))
        controller.close()
        pumpEventQueue()
        assertEquals(listOf("event: hi", "data: 你好", "", "partial"), lines)
    }
}

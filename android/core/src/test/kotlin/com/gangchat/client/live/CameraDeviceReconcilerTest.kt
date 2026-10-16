package com.gangchat.client.live

import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.timeout
import com.gangchat.client.testing.GangTestRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CameraDeviceReconcilerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `camera topology burst is debounced to one refresh`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var refreshes = 0
        val reconciler =
            CameraDeviceReconciler(
                deviceChanges = changes.stream,
                refreshDevices = { refreshes += 1 },
                debounce = 10.milliseconds,
            )
        try {
            reconciler.start()

            changes.add(Unit)
            changes.add(Unit)
            changes.add(Unit)
            _waitFor { refreshes == 1 }
        } finally {
            reconciler.stop()
            changes.close()
        }
    }

    @Test
    fun `timed-out camera refresh does not block a later hotplug`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val firstStarted = Completer<Unit>()
        val neverCompletes = Completer<Unit>()
        var attempts = 0
        var completed = 0
        val reconciler =
            CameraDeviceReconciler(
                deviceChanges = changes.stream,
                refreshDevices = refresh@{
                    attempts += 1
                    if (attempts == 1) {
                        firstStarted.complete()
                        neverCompletes.future.await()
                        return@refresh
                    }
                    completed += 1
                },
                debounce = Duration.ZERO,
                operationTimeout = 20.milliseconds,
            )
        try {
            reconciler.start()

            changes.add(Unit)
            firstStarted.future.await()
            delayed(30.milliseconds)
            changes.add(Unit)
            _waitFor { completed == 1 }

            assertEquals(2, attempts)
        } finally {
            reconciler.stop()
            changes.close()
        }
    }

    @Test
    fun `stop cancels logical wait for an in-flight camera refresh`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val started = Completer<Unit>()
        val neverCompletes = Completer<Unit>()
        val reconciler =
            CameraDeviceReconciler(
                deviceChanges = changes.stream,
                refreshDevices = {
                    started.complete()
                    neverCompletes.future.await()
                },
                debounce = Duration.ZERO,
                operationTimeout = 30.seconds,
            )
        try {
            reconciler.start()

            changes.add(Unit)
            started.future.await()
            timeout(200.milliseconds) { reconciler.stop() }
        } finally {
            changes.close()
        }
    }
}

/**
 * Dart's `_waitFor`: polls [condition] every millisecond for up to two
 * seconds. The port measures the deadline on the test's virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope._waitFor(condition: () -> Boolean) {
    val deadline = currentTime + 2.seconds.inWholeMilliseconds
    while (!condition()) {
        if (currentTime > deadline) {
            throw AssertionError("Timed out waiting for camera-device reconciliation.")
        }
        delayed(1.milliseconds)
    }
}

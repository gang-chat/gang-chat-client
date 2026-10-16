package com.gangchat.client.live

import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.delayed
import com.gangchat.client.testing.GangTestRule
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AudioOutputRebinderTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `rebinds output and re-applies routing on a device change`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val selected = mutableListOf<String>()
        var rebounds = 0

        val rebinder =
            AudioOutputRebinder(
                deviceChanges = changes.stream,
                currentOutputDeviceId = { "speaker_1" },
                selectOutput = { id -> selected.add(id) },
                onRebound = { rebounds += 1 },
                debounce = 10.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            delayed(30.milliseconds)

            assertEquals(listOf("speaker_1"), selected)
            assertEquals(1, rebounds)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `coalesces a burst of changes into a single rebind`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var selects = 0
        var rebounds = 0

        val rebinder =
            AudioOutputRebinder(
                deviceChanges = changes.stream,
                currentOutputDeviceId = { "speaker_1" },
                selectOutput = { selects += 1 },
                onRebound = { rebounds += 1 },
                debounce = 20.milliseconds,
            )
        try {
            rebinder.start()

            // A single profile flip emits several events in quick succession.
            changes.add(Unit)
            changes.add(Unit)
            changes.add(Unit)
            delayed(50.milliseconds)

            assertEquals(1, selects)
            assertEquals(1, rebounds)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `re-applies routing even without a known output id`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var selects = 0
        var rebounds = 0

        val rebinder =
            AudioOutputRebinder(
                deviceChanges = changes.stream,
                currentOutputDeviceId = { null },
                selectOutput = { selects += 1 },
                onRebound = { rebounds += 1 },
                debounce = 10.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            delayed(30.milliseconds)

            // No id to select, but routing is still refreshed so volumes re-bind to
            // whatever endpoint WebRTC ended up on.
            assertEquals(0, selects)
            assertEquals(1, rebounds)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `a failed rebind is swallowed and does not block later ones`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var attempts = 0

        val rebinder =
            AudioOutputRebinder(
                deviceChanges = changes.stream,
                currentOutputDeviceId = {
                    attempts += 1
                    if (attempts == 1) throw IllegalStateException("native failure")
                    "speaker_2"
                },
                selectOutput = { },
                onRebound = { },
                debounce = 10.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            delayed(30.milliseconds)
            changes.add(Unit)
            delayed(30.milliseconds)

            assertEquals(2, attempts)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `stop cancels a pending rebind`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var rebounds = 0

        val rebinder =
            AudioOutputRebinder(
                deviceChanges = changes.stream,
                currentOutputDeviceId = { "speaker_1" },
                selectOutput = { },
                onRebound = { rebounds += 1 },
                debounce = 30.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            rebinder.stop()
            delayed(50.milliseconds)

            assertEquals(0, rebounds)
        } finally {
            changes.close()
        }
    }

    @Test
    fun `ignores events after stop`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var rebounds = 0

        val rebinder =
            AudioOutputRebinder(
                deviceChanges = changes.stream,
                currentOutputDeviceId = { "speaker_1" },
                selectOutput = { },
                onRebound = { rebounds += 1 },
                debounce = 10.milliseconds,
            )
        try {
            rebinder.start()
            rebinder.stop()

            changes.add(Unit)
            delayed(30.milliseconds)

            assertEquals(0, rebounds)
        } finally {
            changes.close()
        }
    }
}

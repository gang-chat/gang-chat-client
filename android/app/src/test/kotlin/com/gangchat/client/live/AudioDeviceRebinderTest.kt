package com.gangchat.client.live

import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.StoredAudioDevices
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
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/** Port of test/unit/live/audio_device_rebinder_test.dart. */
class AudioDeviceRebinderTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `resolves input and output from one device enumeration`() = gang.runTest {
        val service =
            FakeRebinderAudioDeviceService(
                devices =
                    listOf(
                        AudioDeviceInfo(deviceId = "mic_new", label = "USB Headset", kind = "audioinput"),
                        AudioDeviceInfo(deviceId = "speaker_new", label = "USB Headset", kind = "audiooutput"),
                    ),
            )
        val system = FakeRebinderSystemAudioDevices(inputDeviceId = "mic_default", outputDeviceId = "speaker_default")

        val resolved =
            preferredLiveAudioDevices(
                audioDeviceStore =
                    FakeRebinderAudioDeviceStore(
                        inputDeviceId = "mic_old",
                        inputDeviceLabel = "USB Headset",
                        outputDeviceId = "speaker_old",
                        outputDeviceLabel = "USB Headset",
                    ),
                audioDevices = service,
                systemAudio = system,
            )

        assertEquals(1, service.enumerations)
        assertEquals("mic_new", resolved.inputDeviceId)
        assertEquals("speaker_new", resolved.outputDeviceId)
    }

    @Test
    fun `one device-change burst rebinds both directions once`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var resolutions = 0
        val operations = mutableListOf<String>()
        val rebinder =
            AudioDeviceRebinder(
                deviceChanges = changes.stream,
                resolvePreferredDevices = {
                    resolutions += 1
                    PreferredLiveAudioDevices(inputDeviceId = "mic_1", outputDeviceId = "speaker_1")
                },
                rebindInput = { id -> operations.add("input:$id") },
                selectOutput = { id -> operations.add("output:$id") },
                onOutputRebound = { operations.add("routing") },
                debounce = 10.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            changes.add(Unit)
            changes.add(Unit)
            _waitFor { operations.size == 3 }

            assertEquals(1, resolutions)
            assertEquals(listOf("input:mic_1", "output:speaker_1", "routing"), operations)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `refresh is single-flight and retains one trailing refresh`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val firstStarted = Completer<Unit>()
        val releaseFirst = Completer<Unit>()
        var resolutions = 0
        var activeResolutions = 0
        var maxActiveResolutions = 0
        var inputRebinds = 0
        var outputRebinds = 0

        val rebinder =
            AudioDeviceRebinder(
                deviceChanges = changes.stream,
                resolvePreferredDevices = {
                    resolutions += 1
                    activeResolutions += 1
                    if (activeResolutions > maxActiveResolutions) {
                        maxActiveResolutions = activeResolutions
                    }
                    if (resolutions == 1) {
                        firstStarted.complete()
                        releaseFirst.future.await()
                    }
                    activeResolutions -= 1
                    PreferredLiveAudioDevices(inputDeviceId = "mic", outputDeviceId = "speaker")
                },
                rebindInput = { inputRebinds += 1 },
                selectOutput = { outputRebinds += 1 },
                onOutputRebound = { },
                debounce = Duration.ZERO,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            firstStarted.future.await()
            changes.add(Unit)
            changes.add(Unit)
            delayed(Duration.ZERO)
            releaseFirst.complete()
            _waitFor { resolutions == 2 && outputRebinds == 2 }

            assertEquals(1, maxActiveResolutions)
            assertEquals(2, inputRebinds)
            assertEquals(2, outputRebinds)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `long playback event storm stays serialized and recoverable`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var resolutions = 0
        var activeOperations = 0
        var maxActiveOperations = 0
        var routingReapplies = 0

        suspend fun operation() {
            activeOperations += 1
            if (activeOperations > maxActiveOperations) {
                maxActiveOperations = activeOperations
            }
            delayed(Duration.ZERO)
            activeOperations -= 1
        }

        val rebinder =
            AudioDeviceRebinder(
                deviceChanges = changes.stream,
                resolvePreferredDevices = {
                    resolutions += 1
                    operation()
                    PreferredLiveAudioDevices(inputDeviceId = "mic", outputDeviceId = "speaker")
                },
                rebindInput = { operation() },
                selectOutput = { operation() },
                onOutputRebound = {
                    operation()
                    routingReapplies += 1
                },
                debounce = Duration.ZERO,
            )
        try {
            rebinder.start()

            // Simulates repeated endpoint/profile churn during a long viewing session
            // without making the suite sleep for real minutes.
            for (cycle in 0 until 100) {
                changes.add(Unit)
                changes.add(Unit)
                _waitFor { routingReapplies == cycle + 1 }
            }

            assertEquals(100, resolutions)
            assertEquals(100, routingReapplies)
            assertEquals(1, maxActiveOperations)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `a timed-out refresh does not block a later device change`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val firstStarted = Completer<Unit>()
        val neverCompletes = Completer<PreferredLiveAudioDevices>()
        var resolutions = 0
        val operations = mutableListOf<String>()
        val rebinder =
            AudioDeviceRebinder(
                deviceChanges = changes.stream,
                resolvePreferredDevices = {
                    resolutions += 1
                    if (resolutions == 1) {
                        firstStarted.complete()
                        neverCompletes.future.await()
                    } else {
                        PreferredLiveAudioDevices(
                            inputDeviceId = "mic_recovered",
                            outputDeviceId = "speaker_recovered",
                        )
                    }
                },
                rebindInput = { id -> operations.add("input:$id") },
                selectOutput = { id -> operations.add("output:$id") },
                onOutputRebound = { operations.add("routing") },
                debounce = Duration.ZERO,
                operationTimeout = 20.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            firstStarted.future.await()
            delayed(30.milliseconds)
            changes.add(Unit)
            _waitFor { operations.size == 3 }

            assertEquals(2, resolutions)
            assertEquals(listOf("input:mic_recovered", "output:speaker_recovered", "routing"), operations)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `stop cancels an in-flight refresh without waiting for native work`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val started = Completer<Unit>()
        val neverCompletes = Completer<PreferredLiveAudioDevices>()
        var inputRebinds = 0
        val rebinder =
            AudioDeviceRebinder(
                deviceChanges = changes.stream,
                resolvePreferredDevices = {
                    started.complete()
                    neverCompletes.future.await()
                },
                rebindInput = { inputRebinds += 1 },
                selectOutput = { },
                onOutputRebound = { },
                debounce = Duration.ZERO,
                operationTimeout = 30.seconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            started.future.await()
            timeout(200.milliseconds) { rebinder.stop() }

            assertEquals(0, inputRebinds)
        } finally {
            changes.close()
        }
    }

    @Test
    fun `output selection failure still reapplies routing and volume`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var routingReapplies = 0
        val rebinder =
            AudioDeviceRebinder(
                deviceChanges = changes.stream,
                resolvePreferredDevices = {
                    PreferredLiveAudioDevices(inputDeviceId = "mic", outputDeviceId = "speaker")
                },
                rebindInput = { },
                selectOutput = { throw IllegalStateException("endpoint disappeared") },
                onOutputRebound = { routingReapplies += 1 },
                debounce = Duration.ZERO,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            _waitFor { routingReapplies == 1 }
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `device enumeration failure is not mistaken for an empty topology`() = gang.runTest {
        val service =
            FakeRebinderAudioDeviceService(
                devices = emptyList(),
                error = IllegalStateException("native enumeration timed out"),
            )

        try {
            preferredLiveAudioDevices(
                audioDeviceStore = null,
                audioDevices = service,
                systemAudio =
                    FakeRebinderSystemAudioDevices(
                        inputDeviceId = "default_mic",
                        outputDeviceId = "default_speaker",
                    ),
            )
            fail("expected the enumeration error")
        } catch (error: IllegalStateException) {
            assertEquals("native enumeration timed out", error.message)
        }
    }
}

/**
 * Dart's `_waitFor`: polls [condition] every millisecond for up to two
 * seconds, on the test's virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope._waitFor(condition: () -> Boolean) {
    val deadline = currentTime + 2.seconds.inWholeMilliseconds
    while (!condition()) {
        if (currentTime > deadline) {
            fail("Timed out waiting for asynchronous audio-device operation.")
        }
        delayed(1.milliseconds)
    }
}

private class FakeRebinderAudioDeviceService(
    val devices: List<AudioDeviceInfo>,
    val error: Exception? = null,
) : LiveAudioDeviceService() {
    var enumerations = 0

    override suspend fun enumerateDevices(): List<AudioDeviceInfo> {
        enumerations += 1
        error?.let { throw it }
        return devices
    }
}

private class FakeRebinderSystemAudioDevices(
    val inputDeviceId: String?,
    val outputDeviceId: String?,
) : SystemAudioDevices(supported = false) {
    override suspend fun currentInputDeviceId(): String? = inputDeviceId

    override suspend fun currentOutputDeviceId(): String? = outputDeviceId
}

private class FakeRebinderAudioDeviceStore(
    val inputDeviceId: String? = null,
    val inputDeviceLabel: String? = null,
    val outputDeviceId: String? = null,
    val outputDeviceLabel: String? = null,
) : AudioDeviceStore() {
    override suspend fun read(): StoredAudioDevices {
        return StoredAudioDevices(
            inputDeviceId = inputDeviceId,
            inputDeviceLabel = inputDeviceLabel,
            outputDeviceId = outputDeviceId,
            outputDeviceLabel = outputDeviceLabel,
        )
    }
}

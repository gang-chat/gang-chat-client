package com.gangchat.client.live

import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.StoredAudioDevices
import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.delayed
import com.gangchat.client.testing.GangTestRule
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Port of test/unit/live/audio_input_rebinder_test.dart. */
class AudioInputRebinderTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `rebinds input on a device change`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val rebound = mutableListOf<String?>()

        val rebinder =
            AudioInputRebinder(
                deviceChanges = changes.stream,
                currentInputDeviceId = { "mic_1" },
                rebindInput = { id -> rebound.add(id) },
                debounce = 10.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            delayed(30.milliseconds)

            assertEquals(listOf<String?>("mic_1"), rebound)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `coalesces a burst of changes into a single input rebind`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var rebounds = 0

        val rebinder =
            AudioInputRebinder(
                deviceChanges = changes.stream,
                currentInputDeviceId = { "headset_mic" },
                rebindInput = { rebounds += 1 },
                debounce = 20.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            changes.add(Unit)
            changes.add(Unit)
            delayed(50.milliseconds)

            assertEquals(1, rebounds)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `treats later same-device changes as fresh input rebinds`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val rebound = mutableListOf<String?>()

        val rebinder =
            AudioInputRebinder(
                deviceChanges = changes.stream,
                currentInputDeviceId = { "headset_mic" },
                rebindInput = { id -> rebound.add(id) },
                debounce = 10.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            delayed(30.milliseconds)
            changes.add(Unit)
            delayed(30.milliseconds)

            assertEquals(listOf<String?>("headset_mic", "headset_mic"), rebound)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `queues changes that arrive while an input rebind is running`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        val firstStarted = Completer<Unit>()
        val finishFirst = Completer<Unit>()
        var attempts = 0

        val rebinder =
            AudioInputRebinder(
                deviceChanges = changes.stream,
                currentInputDeviceId = { "headset_mic" },
                rebindInput = {
                    attempts += 1
                    if (attempts == 1) {
                        firstStarted.complete()
                        finishFirst.future.await()
                    }
                },
                debounce = 10.milliseconds,
            )
        try {
            rebinder.start()

            changes.add(Unit)
            firstStarted.future.await()
            changes.add(Unit)
            delayed(30.milliseconds)
            finishFirst.complete()
            delayed(30.milliseconds)

            assertEquals(2, attempts)
        } finally {
            rebinder.stop()
            changes.close()
        }
    }

    @Test
    fun `a failed input rebind is swallowed and does not block later ones`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var attempts = 0

        val rebinder =
            AudioInputRebinder(
                deviceChanges = changes.stream,
                currentInputDeviceId = { "headset_mic" },
                rebindInput = {
                    attempts += 1
                    if (attempts == 1) throw IllegalStateException("device vanished")
                },
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
    fun `stop cancels a pending input rebind`() = gang.runTest {
        val changes = StreamController.broadcast<Unit>()
        var rebounds = 0

        val rebinder =
            AudioInputRebinder(
                deviceChanges = changes.stream,
                currentInputDeviceId = { "mic_1" },
                rebindInput = { rebounds += 1 },
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
    fun `preferredLiveInputDeviceId restores hotplugged input by signature`() = gang.runTest {
        val defaultMic =
            AudioDeviceInfo(deviceId = "default_mic", label = "Default Mic", kind = "audioinput", groupId = "group_default")
        val repluggedHeadset =
            AudioDeviceInfo(
                deviceId = "headset_new",
                label = "Bluetooth Headset",
                kind = "audioinput",
                groupId = "group_headset",
            )

        assertEquals(
            "headset_new",
            preferredLiveInputDeviceId(
                audioDeviceStore =
                    FakeInputAudioDeviceStore(
                        inputDeviceId = "headset_old",
                        inputDeviceLabel = "Bluetooth Headset",
                        inputDeviceGroupId = "group_headset",
                    ),
                audioDevices = FakeInputLiveAudioDeviceService(devices = listOf(defaultMic, repluggedHeadset)),
                systemAudio = FakeInputSystemAudioDevices(inputDeviceId = "default_mic"),
            ),
        )
    }

    @Test
    fun `preferredLiveInputDeviceId falls back to system default input`() = gang.runTest {
        val defaultMic = AudioDeviceInfo(deviceId = "mic_2", label = "Room Mic", kind = "audioinput")

        assertEquals(
            "mic_2",
            preferredLiveInputDeviceId(
                audioDeviceStore = FakeInputAudioDeviceStore(inputDeviceId = "missing_mic"),
                audioDevices = FakeInputLiveAudioDeviceService(devices = listOf(defaultMic)),
                systemAudio = FakeInputSystemAudioDevices(inputDeviceId = "mic_2"),
            ),
        )
    }

    @Test
    fun `preferredLiveInputDeviceId falls back when enumeration fails`() = gang.runTest {
        assertEquals(
            "default_mic",
            preferredLiveInputDeviceId(
                audioDeviceStore = FakeInputAudioDeviceStore(inputDeviceId = "headset_mic"),
                audioDevices =
                    FakeInputLiveAudioDeviceService(
                        devices = emptyList(),
                        enumerateError = IllegalStateException("enumerate failed"),
                    ),
                systemAudio = FakeInputSystemAudioDevices(inputDeviceId = "default_mic"),
            ),
        )
    }
}

private class FakeInputAudioDeviceStore(
    val inputDeviceId: String? = null,
    val inputDeviceLabel: String? = null,
    val inputDeviceGroupId: String? = null,
) : AudioDeviceStore() {
    override suspend fun read(): StoredAudioDevices {
        return StoredAudioDevices(
            inputDeviceId = inputDeviceId,
            inputDeviceLabel = inputDeviceLabel,
            inputDeviceGroupId = inputDeviceGroupId,
        )
    }
}

private class FakeInputLiveAudioDeviceService(
    val devices: List<AudioDeviceInfo>,
    val enumerateError: Exception? = null,
) : LiveAudioDeviceService() {
    override suspend fun enumerateDevices(): List<AudioDeviceInfo> {
        enumerateError?.let { throw it }
        return devices
    }
}

private class FakeInputSystemAudioDevices(val inputDeviceId: String?) : SystemAudioDevices(supported = false) {
    override suspend fun currentInputDeviceId(): String? = inputDeviceId
}

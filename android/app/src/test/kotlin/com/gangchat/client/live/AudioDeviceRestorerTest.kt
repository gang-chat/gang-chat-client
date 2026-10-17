package com.gangchat.client.live

import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.StoredAudioDevices
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Port of test/unit/live/audio_device_restorer_test.dart. */
class AudioDeviceRestorerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `restoreStoredAudioDevices uses injected audio device service`() = gang.runTest {
        val defaultInput =
            AudioDeviceInfo(deviceId = "default", label = "System Default", kind = "audioinput", groupId = "group_default")
        val speaker =
            AudioDeviceInfo(deviceId = "speaker_1", label = "Speaker", kind = "audiooutput", groupId = "group_speaker")
        val service = FakeRestorerLiveAudioDeviceService(devices = listOf(defaultInput, speaker), selectedInput = defaultInput)

        val restored = restoreStoredAudioDevices(FakeRestorerAudioDeviceStore(), audioDevices = service)

        assertEquals(defaultInput, restored.input)
        assertEquals(speaker, restored.output)
        assertEquals(1, service.enumerateCalls)
        assertEquals(0, service.inputSelects)
        assertEquals(1, service.outputSelects)
        assertEquals(speaker, service.selectedOutput)
    }

    @Test
    fun `restoreStoredAudioDevices falls back to system default output`() = gang.runTest {
        val speakerOne =
            AudioDeviceInfo(deviceId = "speaker_1", label = "Speaker 1", kind = "audiooutput", groupId = "group_speaker_1")
        val speakerTwo =
            AudioDeviceInfo(deviceId = "speaker_2", label = "Speaker 2", kind = "audiooutput", groupId = "group_speaker_2")
        val service = FakeRestorerLiveAudioDeviceService(devices = listOf(speakerOne, speakerTwo), selectedOutput = speakerOne)

        val restored =
            restoreStoredAudioDevices(
                FakeRestorerAudioDeviceStore(outputDeviceId = "missing_output"),
                audioDevices = service,
                systemDefaultOutputId = "speaker_2",
            )

        assertEquals(speakerTwo, restored.output)
        assertEquals(1, service.outputSelects)
        assertEquals(speakerTwo, service.selectedOutput)
    }

    @Test
    fun `restoreStoredAudioDevices restores output by signature after hotplug`() = gang.runTest {
        val speaker =
            AudioDeviceInfo(deviceId = "speaker_2", label = "Desk Speaker", kind = "audiooutput", groupId = "group_speaker")
        val repluggedHeadset =
            AudioDeviceInfo(deviceId = "headset_new", label = "USB Headset", kind = "audiooutput", groupId = "group_headset")
        val service = FakeRestorerLiveAudioDeviceService(devices = listOf(speaker, repluggedHeadset), selectedOutput = speaker)

        val store =
            FakeRestorerAudioDeviceStore(
                outputDeviceId = "headset_old",
                outputDeviceLabel = "USB Headset",
                outputDeviceGroupId = "group_headset",
            )

        val restored =
            restoreStoredAudioDevices(
                store,
                audioDevices = service,
                systemDefaultOutputId = "speaker_2",
            )

        assertEquals(repluggedHeadset, restored.output)
        assertEquals(1, service.outputSelects)
        assertEquals(repluggedHeadset, service.selectedOutput)
        assertEquals("headset_new", store.writtenOutputDeviceId)
        assertEquals("USB Headset", store.writtenOutputDeviceLabel)
        assertEquals("group_headset", store.writtenOutputDeviceGroupId)
    }
}

private class FakeRestorerAudioDeviceStore(
    val outputDeviceId: String? = "speaker_1",
    val outputDeviceLabel: String? = null,
    val outputDeviceGroupId: String? = null,
) : AudioDeviceStore() {
    var writtenOutputDeviceId: String? = null
    var writtenOutputDeviceLabel: String? = null
    var writtenOutputDeviceGroupId: String? = null

    override suspend fun read(): StoredAudioDevices {
        return StoredAudioDevices(
            inputDeviceId = "missing_input",
            outputDeviceId = outputDeviceId,
            outputDeviceLabel = outputDeviceLabel,
            outputDeviceGroupId = outputDeviceGroupId,
        )
    }

    override suspend fun writeOutputDevicePreference(deviceId: String, label: String?, groupId: String?) {
        writtenOutputDeviceId = deviceId
        writtenOutputDeviceLabel = label
        writtenOutputDeviceGroupId = groupId
    }
}

private class FakeRestorerLiveAudioDeviceService(
    val devices: List<AudioDeviceInfo>,
    selectedInput: AudioDeviceInfo? = null,
    selectedOutput: AudioDeviceInfo? = null,
) : LiveAudioDeviceService() {
    private var _selectedInput: AudioDeviceInfo? = selectedInput
    private var _selectedOutput: AudioDeviceInfo? = selectedOutput
    var enumerateCalls = 0
    var inputSelects = 0
    var outputSelects = 0

    override suspend fun enumerateDevices(): List<AudioDeviceInfo> {
        enumerateCalls += 1
        return devices
    }

    override val selectedAudioInput: AudioDeviceInfo? get() = _selectedInput

    override val selectedAudioOutput: AudioDeviceInfo? get() = _selectedOutput

    val selectedOutput: AudioDeviceInfo? get() = _selectedOutput

    override suspend fun selectAudioInput(device: AudioDeviceInfo) {
        inputSelects += 1
        _selectedInput = device
    }

    override suspend fun selectAudioOutput(device: AudioDeviceInfo) {
        outputSelects += 1
        _selectedOutput = device
    }
}

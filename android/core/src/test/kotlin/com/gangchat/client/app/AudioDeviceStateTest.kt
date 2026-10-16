package com.gangchat.client.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDeviceStateTest {
    @Test
    fun `audioDeviceListLoadStarted preserves devices and clears error`() {
        val input = _Device("mic_1", "Mic", "audioinput")
        val output = _Device("speaker_1", "Speaker", "audiooutput")
        val patch =
            audioDeviceListLoadStarted(
                inputs = listOf(input),
                outputs = listOf(output),
                selectedInput = input,
                selectedOutput = output,
            )

        assertEquals(listOf(input), patch.inputs)
        assertEquals(listOf(output), patch.outputs)
        assertSame(input, patch.selectedInput)
        assertSame(output, patch.selectedOutput)
        assertTrue(patch.loading)
        assertNull(patch.error)
    }

    @Test
    fun `audioDeviceListLoadFailed preserves device state and reports error`() {
        val input = _Device("mic_1", "Mic", "audioinput")
        val patch =
            audioDeviceListLoadFailed(
                inputs = listOf(input),
                outputs = emptyList(),
                selectedInput = input,
                selectedOutput = null,
                failure = "device denied",
            )

        assertEquals(listOf(input), patch.inputs)
        assertTrue(patch.outputs.isEmpty())
        assertSame(input, patch.selectedInput)
        assertNull(patch.selectedOutput)
        assertFalse(patch.loading)
        assertEquals("操作失败，请稍后重试", patch.error)
    }

    @Test
    fun `audioDeviceListApplied filters and selects from priority candidates`() {
        val inputOne = _Device("mic_1", "Mic 1", "audioinput")
        val inputTwo = _Device("mic_2", "Mic 2", "audioinput")
        val outputOne = _Device("speaker_1", "Speaker 1", "audiooutput")
        val outputTwo = _Device("speaker_2", "Speaker 2", "audiooutput")

        val patch =
            audioDeviceListApplied<_Device>(
                devices = listOf(inputOne, inputTwo, outputOne, outputTwo),
                restoredInput = inputTwo,
                restoredOutput = null,
                hardwareInput = inputOne,
                hardwareOutput = outputTwo,
                currentInput = null,
                currentOutput = outputOne,
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
                error = "previous warning",
            )

        assertEquals(listOf(inputOne, inputTwo), patch.inputs)
        assertEquals(listOf(outputOne, outputTwo), patch.outputs)
        assertSame(inputTwo, patch.selectedInput)
        assertSame(outputTwo, patch.selectedOutput)
        assertFalse(patch.loading)
        assertEquals("previous warning", patch.error)
    }

    @Test
    fun `audioDeviceListApplied follows system default input when none restored`() {
        val inputOne = _Device("mic_1", "Mic 1", "audioinput")
        val inputTwo = _Device("mic_2", "Mic 2", "audioinput")

        val patch =
            audioDeviceListApplied<_Device>(
                devices = listOf(inputOne, inputTwo),
                restoredInput = null,
                restoredOutput = null,
                hardwareInput = inputOne,
                hardwareOutput = null,
                currentInput = null,
                currentOutput = null,
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
                error = null,
                systemDefaultInput = inputTwo,
            )

        // The system default sits ahead of the hardware/current device, so it is
        // chosen even though the hardware reports mic_1.
        assertSame(inputTwo, patch.selectedInput)
    }

    @Test
    fun `audioDeviceListApplied keeps restored input over system default`() {
        val inputOne = _Device("mic_1", "Mic 1", "audioinput")
        val inputTwo = _Device("mic_2", "Mic 2", "audioinput")

        val patch =
            audioDeviceListApplied<_Device>(
                devices = listOf(inputOne, inputTwo),
                restoredInput = inputOne,
                restoredOutput = null,
                hardwareInput = null,
                hardwareOutput = null,
                currentInput = null,
                currentOutput = null,
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
                error = null,
                systemDefaultInput = inputTwo,
            )

        // An explicit saved preference still wins over the OS default.
        assertSame(inputOne, patch.selectedInput)
    }

    @Test
    fun `audioDeviceListApplied follows system default output when none restored`() {
        val outputOne = _Device("speaker_1", "Speaker 1", "audiooutput")
        val outputTwo = _Device("speaker_2", "Speaker 2", "audiooutput")

        val patch =
            audioDeviceListApplied<_Device>(
                devices = listOf(outputOne, outputTwo),
                restoredInput = null,
                restoredOutput = null,
                hardwareInput = null,
                hardwareOutput = outputOne,
                currentInput = null,
                currentOutput = null,
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
                error = null,
                systemDefaultOutput = outputTwo,
            )

        assertSame(outputTwo, patch.selectedOutput)
    }

    @Test
    fun `audioDeviceListApplied keeps restored output over system default`() {
        val outputOne = _Device("speaker_1", "Speaker 1", "audiooutput")
        val outputTwo = _Device("speaker_2", "Speaker 2", "audiooutput")

        val patch =
            audioDeviceListApplied<_Device>(
                devices = listOf(outputOne, outputTwo),
                restoredInput = null,
                restoredOutput = outputOne,
                hardwareInput = null,
                hardwareOutput = null,
                currentInput = null,
                currentOutput = null,
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
                error = null,
                systemDefaultOutput = outputTwo,
            )

        assertSame(outputOne, patch.selectedOutput)
    }

    @Test
    fun `audioDeviceSelectionStarted marks selected device busy`() {
        val input = _Device("mic_1", "Mic", "audioinput")
        val output = _Device("speaker_1", "Speaker", "audiooutput")
        val patch =
            audioDeviceSelectionStarted(
                device = input,
                selectedInput = input,
                selectedOutput = output,
                kindOf = _kindOf,
                deviceIdOf = _deviceIdOf,
            )

        assertSame(input, patch.selectedInput)
        assertSame(output, patch.selectedOutput)
        assertEquals("audioinput:mic_1", patch.busyDeviceId)
        assertNull(patch.error)
    }

    @Test
    fun `audioDeviceSelectionSucceeded updates only matching kind`() {
        val oldInput = _Device("mic_1", "Mic 1", "audioinput")
        val newOutput = _Device("speaker_2", "Speaker 2", "audiooutput")

        val patch =
            audioDeviceSelectionSucceeded<_Device>(
                device = newOutput,
                selectedInput = oldInput,
                selectedOutput = null,
                kindOf = _kindOf,
                storageFailure = "storage denied",
            )

        assertSame(oldInput, patch.selectedInput)
        assertSame(newOutput, patch.selectedOutput)
        assertNull(patch.busyDeviceId)
        assertEquals("无法保存音频设备偏好", patch.error)
    }

    @Test
    fun `audioDeviceSelectionFailed preserves selection and clears busy`() {
        val input = _Device("mic_1", "Mic", "audioinput")
        val patch =
            audioDeviceSelectionFailed(
                selectedInput = input,
                selectedOutput = null,
                failure = "select failed",
            )

        assertSame(input, patch.selectedInput)
        assertNull(patch.selectedOutput)
        assertNull(patch.busyDeviceId)
        assertEquals("操作失败，请稍后重试", patch.error)
    }

    @Test
    fun `audio device selection effects describe follow-up test work`() {
        var effects =
            audioInputDeviceSelectedEffects(
                wasTestingInput = true,
                wasTestingOutput = false,
            )
        assertTrue(effects.restartInputTest)
        assertFalse(effects.restartOutputTest)

        effects =
            audioInputDeviceSelectedEffects(
                wasTestingInput = false,
                wasTestingOutput = true,
            )
        assertFalse(effects.restartInputTest)
        assertTrue(effects.restartOutputTest)

        effects = audioOutputDeviceSelectedEffects()
        assertFalse(effects.restartInputTest)
        assertFalse(effects.restartOutputTest)
    }

    @Test
    fun `audioStoredVolumesApplied normalizes stored volumes`() {
        val patch =
            audioStoredVolumesApplied(
                inputVolume = 1.2,
                outputVolume = -0.2,
            )

        assertEquals(0.0, patch.inputVolume, 0.0)
        assertEquals(0.0, patch.outputVolume, 0.0)
    }

    @Test
    fun `audio volume changes enforce live mute coupling`() {
        val inputPatch =
            audioInputVolumeChanged(
                inputVolume = 0.42,
                outputVolume = 0.8,
            )
        val restoredOutputPatch =
            audioInputVolumeChanged(
                inputVolume = 0.42,
                outputVolume = 0.0,
                restoreOutputVolume = 0.73,
            )
        val outputPatch =
            audioOutputVolumeChanged(
                inputVolume = 0.42,
                outputVolume = 1.4,
            )
        val mutedOutputPatch =
            audioOutputVolumeChanged(
                inputVolume = 0.42,
                outputVolume = 0.0,
            )

        assertEquals(0.42, inputPatch.inputVolume, 0.0)
        assertEquals(0.8, inputPatch.outputVolume, 0.0)
        assertEquals(0.42, restoredOutputPatch.inputVolume, 0.0)
        assertEquals(0.73, restoredOutputPatch.outputVolume, 0.0)
        assertEquals(0.42, outputPatch.inputVolume, 0.0)
        assertEquals(1.0, outputPatch.outputVolume, 0.0)
        assertEquals(0.0, mutedOutputPatch.inputVolume, 0.0)
        assertEquals(0.0, mutedOutputPatch.outputVolume, 0.0)
    }

    @Test
    fun `audio volume effects describe persistence and local test updates`() {
        var effects =
            audioInputVolumeChangedEffects(
                inputVolume = 1.2,
                hasInputTestTrack = true,
            )
        assertEquals("audioinput", effects.deviceKind)
        assertEquals(1.0, effects.volume, 0.0)
        assertTrue(effects.updateInputTestTrack)
        assertFalse(effects.updateOutputTestPlayback)

        effects =
            audioOutputVolumeChangedEffects(
                outputVolume = -0.2,
                hasOutputTestPlayback = true,
            )
        assertEquals("audiooutput", effects.deviceKind)
        assertEquals(0.0, effects.volume, 0.0)
        assertFalse(effects.updateInputTestTrack)
        assertTrue(effects.updateOutputTestPlayback)
    }

    @Test
    fun `audioInputTestStarted resets input level and clears error`() {
        val patch = audioInputTestStarted(testingOutput = true, outputLevel = 0.6)

        assertTrue(patch.testingInput)
        assertTrue(patch.testingOutput)
        assertEquals(0.0, patch.inputLevel, 0.0)
        assertEquals(0.6, patch.outputLevel, 0.0)
        assertNull(patch.error)
    }

    @Test
    fun `audioInputTestStopped and failed clear input test state`() {
        val stopped =
            audioInputTestStopped(
                testingOutput = true,
                outputLevel = 0.6,
                error = "previous warning",
            )
        val failed =
            audioInputTestFailed(
                testingOutput = false,
                outputLevel = 0.2,
                failure = "input failed",
            )

        assertFalse(stopped.testingInput)
        assertTrue(stopped.testingOutput)
        assertEquals(0.0, stopped.inputLevel, 0.0)
        assertEquals(0.6, stopped.outputLevel, 0.0)
        assertEquals("previous warning", stopped.error)
        assertFalse(failed.testingInput)
        assertFalse(failed.testingOutput)
        assertEquals(0.0, failed.inputLevel, 0.0)
        assertEquals(0.2, failed.outputLevel, 0.0)
        assertEquals("操作失败，请稍后重试", failed.error)
    }

    @Test
    fun `audioOutputTestStarted resets output level and clears error`() {
        val patch = audioOutputTestStarted(testingInput = true, inputLevel = 0.5)

        assertTrue(patch.testingInput)
        assertTrue(patch.testingOutput)
        assertEquals(0.5, patch.inputLevel, 0.0)
        assertEquals(0.0, patch.outputLevel, 0.0)
        assertNull(patch.error)
    }

    @Test
    fun `audioOutputTestStopped and failed clear output test state`() {
        val stopped =
            audioOutputTestStopped(
                testingInput = true,
                inputLevel = 0.7,
                error = "previous warning",
            )
        val failed =
            audioOutputTestFailed(
                testingInput = false,
                inputLevel = 0.1,
                failure = "output failed",
            )

        assertTrue(stopped.testingInput)
        assertFalse(stopped.testingOutput)
        assertEquals(0.7, stopped.inputLevel, 0.0)
        assertEquals(0.0, stopped.outputLevel, 0.0)
        assertEquals("previous warning", stopped.error)
        assertFalse(failed.testingInput)
        assertFalse(failed.testingOutput)
        assertEquals(0.1, failed.inputLevel, 0.0)
        assertEquals(0.0, failed.outputLevel, 0.0)
        assertEquals("操作失败，请稍后重试", failed.error)
    }

    @Test
    fun `audio test level changes apply current volume`() {
        val inputPatch =
            audioInputLevelChanged(
                level = 0.8,
                inputVolume = 0.5,
                testingOutput = true,
                outputLevel = 0.3,
                error = null,
            )
        val outputPatch =
            audioOutputLevelChanged(
                level = 0.8,
                outputVolume = 1.5,
                testingInput = true,
                inputLevel = 0.4,
                error = "previous warning",
            )

        assertTrue(inputPatch.testingInput)
        assertTrue(inputPatch.testingOutput)
        assertEquals(0.4, inputPatch.inputLevel, 0.0)
        assertEquals(0.3, inputPatch.outputLevel, 0.0)
        assertNull(inputPatch.error)
        assertTrue(outputPatch.testingInput)
        assertTrue(outputPatch.testingOutput)
        assertEquals(0.4, outputPatch.inputLevel, 0.0)
        assertEquals(1.0, outputPatch.outputLevel, 0.0)
        assertEquals("previous warning", outputPatch.error)
    }

    private class _Device(
        val deviceId: String,
        val label: String,
        val kind: String,
    )

    private companion object {
        val _kindOf: AudioDeviceKindOf<_Device> = { device -> device.kind }
        val _deviceIdOf: AudioDeviceIdOf<_Device> = { device -> device.deviceId }
    }
}

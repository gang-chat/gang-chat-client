package com.gangchat.client.app

class AudioDeviceListPatch<T>(
    val inputs: List<T>,
    val outputs: List<T>,
    val selectedInput: T?,
    val selectedOutput: T?,
    val loading: Boolean,
    val error: String?,
)

class AudioDeviceSelectionPatch<T>(
    val selectedInput: T?,
    val selectedOutput: T?,
    val busyDeviceId: String?,
    val error: String?,
)

class AudioDeviceSelectionEffects(
    val restartInputTest: Boolean,
    val restartOutputTest: Boolean,
)

class AudioVolumePatch(
    val inputVolume: Double,
    val outputVolume: Double,
)

class AudioVolumeEffects(
    val deviceKind: String,
    val volume: Double,
    val updateInputTestTrack: Boolean,
    val updateOutputTestPlayback: Boolean,
)

class AudioTestStatePatch(
    val testingInput: Boolean,
    val testingOutput: Boolean,
    val inputLevel: Double,
    val outputLevel: Double,
    val error: String?,
)

fun <T> audioDeviceListLoadStarted(
    inputs: List<T>,
    outputs: List<T>,
    selectedInput: T?,
    selectedOutput: T?,
): AudioDeviceListPatch<T> {
    return AudioDeviceListPatch(
        inputs = inputs,
        outputs = outputs,
        selectedInput = selectedInput,
        selectedOutput = selectedOutput,
        loading = true,
        error = null,
    )
}

fun <T> audioDeviceListLoadFailed(
    inputs: List<T>,
    outputs: List<T>,
    selectedInput: T?,
    selectedOutput: T?,
    failure: Any,
): AudioDeviceListPatch<T> {
    return AudioDeviceListPatch(
        inputs = inputs,
        outputs = outputs,
        selectedInput = selectedInput,
        selectedOutput = selectedOutput,
        loading = false,
        error = userFacingErrorMessage(failure),
    )
}

fun <T> audioDeviceListApplied(
    devices: List<T>,
    restoredInput: T?,
    restoredOutput: T?,
    hardwareInput: T?,
    hardwareOutput: T?,
    currentInput: T?,
    currentOutput: T?,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
    error: String?,
    systemDefaultInput: T? = null,
    systemDefaultOutput: T? = null,
): AudioDeviceListPatch<T> {
    val inputs = audioDevicesByKind(devices, "audioinput", kindOf = kindOf)
    val outputs = audioDevicesByKind(devices, "audiooutput", kindOf = kindOf)
    return AudioDeviceListPatch(
        inputs = inputs,
        outputs = outputs,
        // System defaults sit between the saved preference and the hardware/current
        // device so that, absent an explicit choice, the picker follows the OS.
        selectedInput =
            selectedAudioDeviceFrom(
                inputs,
                listOf(restoredInput, systemDefaultInput, hardwareInput, currentInput),
                kindOf = kindOf,
                deviceIdOf = deviceIdOf,
            ),
        selectedOutput =
            selectedAudioDeviceFrom(
                outputs,
                listOf(restoredOutput, systemDefaultOutput, hardwareOutput, currentOutput),
                kindOf = kindOf,
                deviceIdOf = deviceIdOf,
            ),
        loading = false,
        error = error,
    )
}

fun <T> audioDeviceSelectionStarted(
    device: T,
    selectedInput: T?,
    selectedOutput: T?,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
): AudioDeviceSelectionPatch<T> {
    return AudioDeviceSelectionPatch(
        selectedInput = selectedInput,
        selectedOutput = selectedOutput,
        busyDeviceId =
            audioDeviceKeyOf(
                device,
                kindOf = kindOf,
                deviceIdOf = deviceIdOf,
            ),
        error = null,
    )
}

fun <T> audioDeviceSelectionSucceeded(
    device: T,
    selectedInput: T?,
    selectedOutput: T?,
    kindOf: AudioDeviceKindOf<T>,
    storageFailure: Any?,
): AudioDeviceSelectionPatch<T> {
    return AudioDeviceSelectionPatch(
        selectedInput = if (kindOf(device) == "audioinput") device else selectedInput,
        selectedOutput = if (kindOf(device) == "audiooutput") device else selectedOutput,
        busyDeviceId = null,
        error =
            if (storageFailure == null) {
                null
            } else {
                audioDevicePreferenceSaveFailureMessage(storageFailure)
            },
    )
}

fun <T> audioDeviceSelectionFailed(
    selectedInput: T?,
    selectedOutput: T?,
    failure: Any,
): AudioDeviceSelectionPatch<T> {
    return AudioDeviceSelectionPatch(
        selectedInput = selectedInput,
        selectedOutput = selectedOutput,
        busyDeviceId = null,
        error = userFacingErrorMessage(failure),
    )
}

fun audioInputDeviceSelectedEffects(
    wasTestingInput: Boolean,
    wasTestingOutput: Boolean,
): AudioDeviceSelectionEffects {
    return AudioDeviceSelectionEffects(
        restartInputTest = wasTestingInput,
        restartOutputTest = wasTestingOutput,
    )
}

fun audioOutputDeviceSelectedEffects(): AudioDeviceSelectionEffects {
    return AudioDeviceSelectionEffects(
        restartInputTest = false,
        restartOutputTest = false,
    )
}

fun audioStoredVolumesApplied(
    inputVolume: Double,
    outputVolume: Double,
): AudioVolumePatch {
    val output = normalizedAudioVolume(outputVolume)
    return AudioVolumePatch(
        inputVolume = if (output == 0.0) 0.0 else normalizedAudioVolume(inputVolume),
        outputVolume = output,
    )
}

fun audioInputVolumeChanged(
    inputVolume: Double,
    outputVolume: Double,
    restoreOutputVolume: Double = defaultOutputVolume,
): AudioVolumePatch {
    val input = normalizedAudioVolume(inputVolume)
    val output = normalizedAudioVolume(outputVolume)
    return AudioVolumePatch(
        inputVolume = input,
        outputVolume =
            if (input > 0 && output == 0.0) {
                restoredAudioVolume(restoreOutputVolume, defaultOutputVolume)
            } else {
                output
            },
    )
}

fun audioOutputVolumeChanged(
    inputVolume: Double,
    outputVolume: Double,
): AudioVolumePatch {
    val output = normalizedAudioVolume(outputVolume)
    return AudioVolumePatch(
        inputVolume = if (output == 0.0) 0.0 else normalizedAudioVolume(inputVolume),
        outputVolume = output,
    )
}

/** [fallback] is the volume to come back to when [volume] is 0 (muted). */
fun rememberedAudioVolume(volume: Double, fallback: Double = defaultAudioVolume): Double {
    val normalized = normalizedAudioVolume(volume)
    return if (normalized > 0) normalized else fallback
}

fun restoredAudioVolume(volume: Double, fallback: Double = defaultAudioVolume): Double {
    val normalized = normalizedAudioVolume(volume)
    return if (normalized > 0) normalized else fallback
}

fun audioInputVolumeChangedEffects(
    inputVolume: Double,
    hasInputTestTrack: Boolean,
): AudioVolumeEffects {
    return AudioVolumeEffects(
        deviceKind = "audioinput",
        volume = normalizedAudioVolume(inputVolume),
        updateInputTestTrack = hasInputTestTrack,
        updateOutputTestPlayback = false,
    )
}

fun audioOutputVolumeChangedEffects(
    outputVolume: Double,
    hasOutputTestPlayback: Boolean,
): AudioVolumeEffects {
    return AudioVolumeEffects(
        deviceKind = "audiooutput",
        volume = normalizedAudioVolume(outputVolume),
        updateInputTestTrack = false,
        updateOutputTestPlayback = hasOutputTestPlayback,
    )
}

fun audioInputTestStarted(
    testingOutput: Boolean,
    outputLevel: Double,
): AudioTestStatePatch {
    return AudioTestStatePatch(
        testingInput = true,
        testingOutput = testingOutput,
        inputLevel = 0.0,
        outputLevel = outputLevel,
        error = null,
    )
}

fun audioInputTestStopped(
    testingOutput: Boolean,
    outputLevel: Double,
    error: String?,
): AudioTestStatePatch {
    return AudioTestStatePatch(
        testingInput = false,
        testingOutput = testingOutput,
        inputLevel = 0.0,
        outputLevel = outputLevel,
        error = error,
    )
}

fun audioInputTestFailed(
    testingOutput: Boolean,
    outputLevel: Double,
    failure: Any,
): AudioTestStatePatch {
    return AudioTestStatePatch(
        testingInput = false,
        testingOutput = testingOutput,
        inputLevel = 0.0,
        outputLevel = outputLevel,
        error = userFacingErrorMessage(failure),
    )
}

fun audioOutputTestStarted(
    testingInput: Boolean,
    inputLevel: Double,
): AudioTestStatePatch {
    return AudioTestStatePatch(
        testingInput = testingInput,
        testingOutput = true,
        inputLevel = inputLevel,
        outputLevel = 0.0,
        error = null,
    )
}

fun audioOutputTestStopped(
    testingInput: Boolean,
    inputLevel: Double,
    error: String?,
): AudioTestStatePatch {
    return AudioTestStatePatch(
        testingInput = testingInput,
        testingOutput = false,
        inputLevel = inputLevel,
        outputLevel = 0.0,
        error = error,
    )
}

fun audioOutputTestFailed(
    testingInput: Boolean,
    inputLevel: Double,
    failure: Any,
): AudioTestStatePatch {
    return AudioTestStatePatch(
        testingInput = testingInput,
        testingOutput = false,
        inputLevel = inputLevel,
        outputLevel = 0.0,
        error = userFacingErrorMessage(failure),
    )
}

fun audioInputLevelChanged(
    level: Double,
    inputVolume: Double,
    testingOutput: Boolean,
    outputLevel: Double,
    error: String?,
): AudioTestStatePatch {
    return AudioTestStatePatch(
        testingInput = true,
        testingOutput = testingOutput,
        inputLevel = normalizedAudioVolume(level * inputVolume),
        outputLevel = outputLevel,
        error = error,
    )
}

fun audioOutputLevelChanged(
    level: Double,
    outputVolume: Double,
    testingInput: Boolean,
    inputLevel: Double,
    error: String?,
): AudioTestStatePatch {
    return AudioTestStatePatch(
        testingInput = testingInput,
        testingOutput = true,
        inputLevel = inputLevel,
        outputLevel = normalizedAudioVolume(level * outputVolume),
        error = error,
    )
}

package com.gangchat.client.live

import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.RestoredAudioDevices
import com.gangchat.client.app.audioDeviceInfoGroupId
import com.gangchat.client.app.audioDeviceInfoId
import com.gangchat.client.app.audioDeviceInfoKind
import com.gangchat.client.app.audioDeviceInfoLabel
import com.gangchat.client.app.preferredStoredAudioDeviceFrom
import com.gangchat.client.app.selectStoredAudioDeviceIfChanged
import com.gangchat.client.app.storedPreferredAudioDeviceFrom
import kotlin.coroutines.cancellation.CancellationException

suspend fun restoreStoredAudioDevices(
    store: AudioDeviceStore,
    audioDevices: LiveAudioDeviceService = LiveAudioDeviceService(),
    devices: List<AudioDeviceInfo>? = null,
    systemDefaultInputId: String? = null,
    systemDefaultOutputId: String? = null,
): RestoredAudioDevices<AudioDeviceInfo> {
    val stored = store.read()
    val availableDevices = devices ?: audioDevices.enumerateDevices()
    val storedInputPreference =
        storedPreferredAudioDeviceFrom(
            availableDevices,
            kind = "audioinput",
            storedDeviceId = stored.inputDeviceId,
            storedDeviceLabel = stored.inputDeviceLabel,
            storedDeviceGroupId = stored.inputDeviceGroupId,
            kindOf = ::audioDeviceInfoKind,
            deviceIdOf = ::audioDeviceInfoId,
            labelOf = ::audioDeviceInfoLabel,
            groupIdOf = ::audioDeviceInfoGroupId,
        )
    val storedOutputPreference =
        storedPreferredAudioDeviceFrom(
            availableDevices,
            kind = "audiooutput",
            storedDeviceId = stored.outputDeviceId,
            storedDeviceLabel = stored.outputDeviceLabel,
            storedDeviceGroupId = stored.outputDeviceGroupId,
            kindOf = ::audioDeviceInfoKind,
            deviceIdOf = ::audioDeviceInfoId,
            labelOf = ::audioDeviceInfoLabel,
            groupIdOf = ::audioDeviceInfoGroupId,
        )
    val input =
        preferredStoredAudioDeviceFrom(
            availableDevices,
            kind = "audioinput",
            storedDeviceId = stored.inputDeviceId,
            storedDeviceLabel = stored.inputDeviceLabel,
            storedDeviceGroupId = stored.inputDeviceGroupId,
            kindOf = ::audioDeviceInfoKind,
            deviceIdOf = ::audioDeviceInfoId,
            labelOf = ::audioDeviceInfoLabel,
            groupIdOf = ::audioDeviceInfoGroupId,
            systemDefaultDeviceId = systemDefaultInputId,
        )
    val output =
        preferredStoredAudioDeviceFrom(
            availableDevices,
            kind = "audiooutput",
            storedDeviceId = stored.outputDeviceId,
            storedDeviceLabel = stored.outputDeviceLabel,
            storedDeviceGroupId = stored.outputDeviceGroupId,
            kindOf = ::audioDeviceInfoKind,
            deviceIdOf = ::audioDeviceInfoId,
            labelOf = ::audioDeviceInfoLabel,
            groupIdOf = ::audioDeviceInfoGroupId,
            systemDefaultDeviceId = systemDefaultOutputId,
        )

    val restoredInput =
        if (input == null) {
            null
        } else {
            selectStoredAudioDeviceIfChanged(
                selected = audioDevices.selectedAudioInput,
                device = input,
                select = audioDevices::selectAudioInput,
                kindOf = ::audioDeviceInfoKind,
                deviceIdOf = ::audioDeviceInfoId,
            )
        }
    val restoredOutput =
        if (output == null) {
            null
        } else {
            selectStoredAudioDeviceIfChanged(
                selected = audioDevices.selectedAudioOutput,
                device = output,
                select = audioDevices::selectAudioOutput,
                kindOf = ::audioDeviceInfoKind,
                deviceIdOf = ::audioDeviceInfoId,
            )
        }

    _rememberRestoredPreferences(
        store,
        input = if (storedInputPreference == null) null else restoredInput,
        output = if (storedOutputPreference == null) null else restoredOutput,
    )

    return RestoredAudioDevices(input = restoredInput, output = restoredOutput)
}

private suspend fun _rememberRestoredPreferences(
    store: AudioDeviceStore,
    input: AudioDeviceInfo?,
    output: AudioDeviceInfo?,
) {
    try {
        if (input != null) {
            store.writeInputDevicePreference(
                deviceId = input.deviceId,
                label = input.label,
                groupId = input.groupId,
            )
        }
        if (output != null) {
            store.writeOutputDevicePreference(
                deviceId = output.deviceId,
                label = output.label,
                groupId = output.groupId,
            )
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        // Signature backfill is a convenience; routing already succeeded.
    }
}

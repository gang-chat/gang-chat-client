package com.gangchat.client.app

open class AudioDeviceStore {
    open suspend fun read(): StoredAudioDevices {
        throw NotImplementedError("AudioDeviceStore.read must be implemented.")
    }

    open suspend fun writeInputDeviceId(deviceId: String) {
        throw NotImplementedError(
            "AudioDeviceStore.writeInputDeviceId must be implemented.",
        )
    }

    open suspend fun writeInputDevicePreference(
        deviceId: String,
        label: String? = null,
        groupId: String? = null,
    ) {
        return writeInputDeviceId(deviceId)
    }

    open suspend fun writeOutputDeviceId(deviceId: String) {
        throw NotImplementedError(
            "AudioDeviceStore.writeOutputDeviceId must be implemented.",
        )
    }

    open suspend fun writeOutputDevicePreference(
        deviceId: String,
        label: String? = null,
        groupId: String? = null,
    ) {
        return writeOutputDeviceId(deviceId)
    }

    open suspend fun writeInputVolume(volume: Double) {
        throw NotImplementedError(
            "AudioDeviceStore.writeInputVolume must be implemented.",
        )
    }

    open suspend fun writeOutputVolume(volume: Double) {
        throw NotImplementedError(
            "AudioDeviceStore.writeOutputVolume must be implemented.",
        )
    }

    open suspend fun writeMusicBoxVolume(volume: Double) {
        throw NotImplementedError(
            "AudioDeviceStore.writeMusicBoxVolume must be implemented.",
        )
    }

    open suspend fun writeScreenShareVolume(volume: Double) {
        throw NotImplementedError(
            "AudioDeviceStore.writeScreenShareVolume must be implemented.",
        )
    }

    open suspend fun writeScreenShareMaxHeight(height: Int) {
        throw NotImplementedError(
            "AudioDeviceStore.writeScreenShareMaxHeight must be implemented.",
        )
    }

    open suspend fun writeScreenShareFrameRate(frameRate: Int) {
        throw NotImplementedError(
            "AudioDeviceStore.writeScreenShareFrameRate must be implemented.",
        )
    }

    open suspend fun readParticipantVoiceVolume(userId: String): Double {
        return defaultParticipantVoiceVolume
    }

    open suspend fun writeParticipantVoiceVolume(
        userId: String,
        volume: Double,
    ) {}
}

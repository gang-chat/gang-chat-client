package com.gangchat.client.shell

import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.StoredAudioDevices
import com.gangchat.client.app.defaultAudioVolume
import com.gangchat.client.app.defaultInputVolume
import com.gangchat.client.app.defaultOutputVolume
import com.gangchat.client.app.defaultParticipantVoiceVolume
import com.gangchat.client.app.normalizedAudioVolume
import com.gangchat.client.app.normalizedParticipantVoiceVolume
import com.gangchat.client.app.storedAudioDeviceIdFromStorageValue
import com.gangchat.client.app.storedAudioDeviceSignatureFromStorageValue
import com.gangchat.client.foundation.Uri
import com.gangchat.client.live.normalizedScreenShareFrameRate
import com.gangchat.client.live.normalizedScreenShareMaxHeight
import com.gangchat.client.plugins.SharedPreferences
import kotlin.math.abs

/**
 * Persists audio device + volume preferences in [SharedPreferences].
 *
 * These values (which mic/speaker, what volumes) are not sensitive, so they
 * live in plain local preferences rather than the keychain. On macOS reading
 * the keychain pops an authorization prompt every launch under a debug/unstable
 * code signature; SharedPreferences (a plist on macOS) never does. The refresh
 * token stays in flutter_secure_storage — see TokenStore.
 */
class LocalAudioDeviceStore : AudioDeviceStore() {
    override suspend fun read(): StoredAudioDevices {
        val prefs = SharedPreferences.getInstance()
        _moveToUnityVolumeDefaults(prefs)
        return StoredAudioDevices(
            inputDeviceId =
                storedAudioDeviceIdFromStorageValue(
                    prefs.getString(_inputDeviceIdKey),
                ),
            inputDeviceLabel =
                storedAudioDeviceSignatureFromStorageValue(
                    prefs.getString(_inputDeviceLabelKey),
                ),
            inputDeviceGroupId =
                storedAudioDeviceSignatureFromStorageValue(
                    prefs.getString(_inputDeviceGroupIdKey),
                ),
            outputDeviceId =
                storedAudioDeviceIdFromStorageValue(
                    prefs.getString(_outputDeviceIdKey),
                ),
            outputDeviceLabel =
                storedAudioDeviceSignatureFromStorageValue(
                    prefs.getString(_outputDeviceLabelKey),
                ),
            outputDeviceGroupId =
                storedAudioDeviceSignatureFromStorageValue(
                    prefs.getString(_outputDeviceGroupIdKey),
                ),
            inputVolume = _readVolume(prefs, _inputVolumeKey, defaultInputVolume),
            outputVolume = _readVolume(prefs, _outputVolumeKey, defaultOutputVolume),
            inputVolumeBoost = prefs.getBool(_inputVolumeBoostKey) ?: false,
            outputVolumeBoost = prefs.getBool(_outputVolumeBoostKey) ?: false,
            musicBoxVolume = _readVolume(prefs, _musicBoxVolumeKey),
            screenShareVolume = _readVolume(prefs, _screenShareVolumeKey),
            screenShareMaxHeight =
                normalizedScreenShareMaxHeight(
                    prefs.getInt(_screenShareMaxHeightKey),
                ),
            screenShareFrameRate =
                normalizedScreenShareFrameRate(
                    prefs.getInt(_screenShareFrameRateKey),
                ),
        )
    }

    override suspend fun writeInputDeviceId(deviceId: String) {
        val prefs = SharedPreferences.getInstance()
        prefs.setString(_inputDeviceIdKey, deviceId)
    }

    override suspend fun writeInputDevicePreference(
        deviceId: String,
        label: String?,
        groupId: String?,
    ) {
        val prefs = SharedPreferences.getInstance()
        _writeDevicePreference(
            prefs,
            idKey = _inputDeviceIdKey,
            labelKey = _inputDeviceLabelKey,
            groupIdKey = _inputDeviceGroupIdKey,
            deviceId = deviceId,
            label = label,
            groupId = groupId,
        )
    }

    override suspend fun writeOutputDeviceId(deviceId: String) {
        val prefs = SharedPreferences.getInstance()
        prefs.setString(_outputDeviceIdKey, deviceId)
    }

    override suspend fun writeOutputDevicePreference(
        deviceId: String,
        label: String?,
        groupId: String?,
    ) {
        val prefs = SharedPreferences.getInstance()
        _writeDevicePreference(
            prefs,
            idKey = _outputDeviceIdKey,
            labelKey = _outputDeviceLabelKey,
            groupIdKey = _outputDeviceGroupIdKey,
            deviceId = deviceId,
            label = label,
            groupId = groupId,
        )
    }

    override suspend fun writeInputVolume(volume: Double) {
        val prefs = SharedPreferences.getInstance()
        prefs.setDouble(_inputVolumeKey, normalizedAudioVolume(volume))
    }

    override suspend fun writeOutputVolume(volume: Double) {
        val prefs = SharedPreferences.getInstance()
        prefs.setDouble(_outputVolumeKey, normalizedAudioVolume(volume))
    }

    override suspend fun writeInputVolumeBoost(boost: Boolean) {
        val prefs = SharedPreferences.getInstance()
        prefs.setBool(_inputVolumeBoostKey, boost)
    }

    override suspend fun writeOutputVolumeBoost(boost: Boolean) {
        val prefs = SharedPreferences.getInstance()
        prefs.setBool(_outputVolumeBoostKey, boost)
    }

    override suspend fun writeMusicBoxVolume(volume: Double) {
        val prefs = SharedPreferences.getInstance()
        prefs.setDouble(_musicBoxVolumeKey, normalizedAudioVolume(volume))
    }

    override suspend fun writeScreenShareVolume(volume: Double) {
        val prefs = SharedPreferences.getInstance()
        prefs.setDouble(_screenShareVolumeKey, normalizedAudioVolume(volume))
    }

    override suspend fun writeScreenShareMaxHeight(height: Int) {
        val prefs = SharedPreferences.getInstance()
        prefs.setInt(
            _screenShareMaxHeightKey,
            normalizedScreenShareMaxHeight(height),
        )
    }

    override suspend fun writeScreenShareFrameRate(frameRate: Int) {
        val prefs = SharedPreferences.getInstance()
        prefs.setInt(
            _screenShareFrameRateKey,
            normalizedScreenShareFrameRate(frameRate),
        )
    }

    override suspend fun readParticipantVoiceVolume(userId: String): Double {
        val prefs = SharedPreferences.getInstance()
        val value = prefs.getDouble(_participantVoiceVolumeKey(userId)) ?: return defaultParticipantVoiceVolume
        return normalizedParticipantVoiceVolume(value)
    }

    override suspend fun writeParticipantVoiceVolume(userId: String, volume: Double) {
        val prefs = SharedPreferences.getInstance()
        val normalized = normalizedParticipantVoiceVolume(volume)
        val key = _participantVoiceVolumeKey(userId)
        if (normalized == defaultParticipantVoiceVolume) {
            prefs.remove(key)
            return
        }
        prefs.setDouble(key, normalized)
    }

    private fun _readVolume(
        prefs: SharedPreferences,
        key: String,
        fallback: Double = defaultAudioVolume,
    ): Double {
        val value = prefs.getDouble(key)
        return if (value == null) fallback else normalizedAudioVolume(value)
    }

    /**
     * Once: an input or output volume still at the old 0.5 default moves to the
     * unity default. Clients before 0.11.3 started at 0.5 and saved it with the
     * first device or volume change, so 0.5 is almost always the default rather
     * than a choice.
     */
    private suspend fun _moveToUnityVolumeDefaults(prefs: SharedPreferences) {
        if (prefs.getBool(_unityVolumeDefaultsKey) == true) return
        for ((key, unity) in listOf(
            _inputVolumeKey to defaultInputVolume,
            _outputVolumeKey to defaultOutputVolume,
        )) {
            val value = prefs.getDouble(key)
            if (value != null && abs(value - defaultAudioVolume) < 0.000001) {
                prefs.setDouble(key, unity)
            }
        }
        prefs.setBool(_unityVolumeDefaultsKey, true)
    }

    private suspend fun _writeDevicePreference(
        prefs: SharedPreferences,
        idKey: String,
        labelKey: String,
        groupIdKey: String,
        deviceId: String,
        label: String?,
        groupId: String?,
    ) {
        prefs.setString(idKey, deviceId)
        _writeOptionalString(prefs, labelKey, label)
        _writeOptionalString(prefs, groupIdKey, groupId)
    }

    private suspend fun _writeOptionalString(
        prefs: SharedPreferences,
        key: String,
        value: String?,
    ) {
        val trimmed = value?.trim()
        if (trimmed == null || trimmed.isEmpty()) {
            prefs.remove(key)
            return
        }
        prefs.setString(key, trimmed)
    }

    private fun _participantVoiceVolumeKey(userId: String): String {
        return "$_participantVoiceVolumePrefix${Uri.encodeComponent(userId)}"
    }

    private companion object {
        const val _inputDeviceIdKey = "gang.audioInputDeviceId"
        const val _inputDeviceLabelKey = "gang.audioInputDeviceLabel"
        const val _inputDeviceGroupIdKey = "gang.audioInputDeviceGroupId"
        const val _outputDeviceIdKey = "gang.audioOutputDeviceId"
        const val _outputDeviceLabelKey = "gang.audioOutputDeviceLabel"
        const val _outputDeviceGroupIdKey = "gang.audioOutputDeviceGroupId"
        const val _inputVolumeKey = "gang.audioInputVolume"
        const val _outputVolumeKey = "gang.audioOutputVolume"
        const val _inputVolumeBoostKey = "gang.audioInputVolumeBoost"
        const val _outputVolumeBoostKey = "gang.audioOutputVolumeBoost"
        const val _unityVolumeDefaultsKey = "gang.audioUnityVolumeDefaults"
        const val _musicBoxVolumeKey = "gang.musicBoxVolume"
        const val _screenShareVolumeKey = "gang.screenShareVolume"
        const val _screenShareMaxHeightKey = "gang.screenShareMaxHeight"
        const val _screenShareFrameRateKey = "gang.screenShareFrameRate"
        const val _participantVoiceVolumePrefix = "gang.participantVoiceVolume."
    }
}

package com.gangchat.client.app

import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.completedFuture
import com.gangchat.client.foundation.future
import com.gangchat.client.live.LiveAudioDeviceService
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.UserAudioSettings
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Deferred

/**
 * Adapts the server-backed audio defaults of a managed account to the same
 * volume controls used by the ordinary settings page. Hardware device ids and
 * screen-share resolution remain device-local and are deliberately not
 * represented here.
 */
class ManagedUserAudioSettingsStore(
    val api: GangApi,
    val userId: String,
) : AudioDeviceStore() {
    private var _settings: UserAudioSettings? = null
    private var _writes: Deferred<Unit> = completedFuture(Unit)

    override suspend fun read(): StoredAudioDevices {
        _writes.await()
        val settings = _load()
        return StoredAudioDevices(
            inputVolume = normalizedAudioVolume(settings.defaultAudioInputVolume.toDouble() / 100),
            outputVolume = normalizedAudioVolume(settings.defaultAudioOutputVolume.toDouble() / 100),
            musicBoxVolume = normalizedAudioVolume(settings.liveMusicOutputVolume.toDouble() / 100),
            screenShareVolume = normalizedAudioVolume(settings.liveScreenShareOutputVolume.toDouble() / 100),
        )
    }

    override suspend fun writeInputVolume(volume: Double) {
        _update(defaultInput = _percent(volume))
    }

    override suspend fun writeOutputVolume(volume: Double) {
        _update(defaultOutput = _percent(volume))
    }

    override suspend fun writeMusicBoxVolume(volume: Double) {
        _update(music = _percent(volume))
    }

    override suspend fun writeScreenShareVolume(volume: Double) {
        _update(screenShare = _percent(volume))
    }

    override suspend fun writeInputDeviceId(deviceId: String) {}

    override suspend fun writeOutputDeviceId(deviceId: String) {}

    override suspend fun writeScreenShareMaxHeight(height: Int) {}

    private fun _percent(volume: Double): Int {
        // Dart's round() on a non-negative value.
        return (normalizedAudioVolume(volume) * 100).roundToInt()
    }

    private suspend fun _load(): UserAudioSettings {
        return _settings ?: api.getForcedUserAudioSettings(userId).also { _settings = it }
    }

    private suspend fun _update(
        defaultInput: Int? = null,
        defaultOutput: Int? = null,
        screenShare: Int? = null,
        music: Int? = null,
    ) {
        val previous = _writes
        val operation =
            future {
                previous.await()
                val current = _load()
                _settings =
                    api.updateForcedUserAudioSettings(
                        userId = userId,
                        settings =
                            UserAudioSettings(
                                defaultAudioInputVolume = defaultInput ?: current.defaultAudioInputVolume,
                                defaultAudioOutputVolume = defaultOutput ?: current.defaultAudioOutputVolume,
                                liveMicInputVolume = current.liveMicInputVolume,
                                liveVoiceOutputVolume = current.liveVoiceOutputVolume,
                                liveScreenShareOutputVolume = screenShare ?: current.liveScreenShareOutputVolume,
                                liveMusicOutputVolume = music ?: current.liveMusicOutputVolume,
                                updatedAt = current.updatedAt,
                            ),
                    )
            }
        _writes =
            future {
                try {
                    operation.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        operation.await()
    }
}

/**
 * Prevents a managed account page from enumerating or selecting hardware on
 * the administrator's device.
 */
class ManagedUserAudioDeviceService : LiveAudioDeviceService() {
    override val devicesChanged: Stream<List<AudioDeviceInfo>> get() = Stream.empty()

    override suspend fun enumerateDevices(): List<AudioDeviceInfo> = emptyList()

    override val selectedAudioInput: AudioDeviceInfo? get() = null

    override val selectedAudioOutput: AudioDeviceInfo? get() = null

    override suspend fun selectAudioInput(device: AudioDeviceInfo) {}

    override suspend fun selectAudioOutput(device: AudioDeviceInfo) {}
}

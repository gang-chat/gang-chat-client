package com.gangchat.client.live

import com.gangchat.client.platform.webrtc.WebRtc

/**
 * WebRTC factory options used only by the Android app process.
 *
 * Some Android vendors expose hardware AEC/noise suppression even when their
 * implementation produces a persistent buzz or other artifacts. Keep the
 * normal WebRTC voice-processing pipeline, but let its software APM handle
 * echo and noise consistently across devices.
 *
 * WebRTC creates one native AudioTrack for the complete remote-room mix.
 * Its audio attributes are immutable after the WebRTC factory is created, so
 * they must be media attributes from process startup. Runtime routing can
 * still switch between communication mode for voice-only rooms and normal
 * mode while the music-box participant is audible.
 */
val androidWebRtcInitializationOptions: Map<String, Any?> =
    mapOf(
        "androidUseHardwareAudioProcessing" to false,
        "androidAudioConfiguration" to
            mapOf(
                "androidAudioAttributesUsageType" to "media",
                "androidAudioAttributesContentType" to "music",
            ),
    )

suspend fun initializeAndroidWebRtcAudio() {
    WebRtc.initialize(options = androidWebRtcInitializationOptions)
}

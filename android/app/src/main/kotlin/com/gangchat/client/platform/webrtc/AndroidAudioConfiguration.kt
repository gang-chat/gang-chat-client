package com.gangchat.client.platform.webrtc

import android.media.AudioAttributes
import android.media.AudioManager
import android.util.Log

/*
 * flutter_webrtc's Android audio configuration (lib/src/native/android/
 * audio_configuration.dart) and the string → Android constant mapping its
 * plugin applied (android/.../audio/AudioUtils.java). The Dart enum names are
 * kept because the app's audio routing code and its tests name them.
 */

enum class AndroidAudioMode { normal, callScreening, inCall, inCommunication, ringtone }

enum class AndroidAudioFocusMode { gain, gainTransient, gainTransientExclusive, gainTransientMayDuck }

enum class AndroidAudioStreamType { accessibility, alarm, dtmf, music, notification, ring, system, voiceCall }

enum class AndroidAudioAttributesUsageType {
    alarm,
    assistanceAccessibility,
    assistanceNavigationGuidance,
    assistanceSonification,
    assistant,
    game,
    media,
    notification,
    notificationEvent,
    notificationRingtone,
    unknown,
    voiceCommunication,
    voiceCommunicationSignalling,
}

enum class AndroidAudioAttributesContentType { movie, music, sonification, speech, unknown }

/** flutter_webrtc's `AndroidAudioConfiguration`. */
class AndroidAudioConfiguration(
    val manageAudioFocus: Boolean? = null,
    val androidAudioMode: AndroidAudioMode? = null,
    val androidAudioFocusMode: AndroidAudioFocusMode? = null,
    val androidAudioStreamType: AndroidAudioStreamType? = null,
    val androidAudioAttributesUsageType: AndroidAudioAttributesUsageType? = null,
    val androidAudioAttributesContentType: AndroidAudioAttributesContentType? = null,
    /**
     * On certain Android devices, audio routing does not function properly and
     * bluetooth microphones will not work unless audio mode is set to
     * `inCommunication` or `inCall`. Audio routing is turned off those cases.
     *
     * If this set to true, will attempt to do audio routing regardless of audio mode.
     */
    val forceHandleAudioRouting: Boolean? = null,
) {
    /** The method-channel map the Dart side sent (`toMap()`), names as strings. */
    fun toMap(): Map<String, Any> =
        buildMap {
            if (manageAudioFocus != null) put("manageAudioFocus", manageAudioFocus)
            if (androidAudioMode != null) put("androidAudioMode", androidAudioMode.name)
            if (androidAudioFocusMode != null) put("androidAudioFocusMode", androidAudioFocusMode.name)
            if (androidAudioStreamType != null) put("androidAudioStreamType", androidAudioStreamType.name)
            if (androidAudioAttributesUsageType != null) {
                put("androidAudioAttributesUsageType", androidAudioAttributesUsageType.name)
            }
            if (androidAudioAttributesContentType != null) {
                put("androidAudioAttributesContentType", androidAudioAttributesContentType.name)
            }
            if (forceHandleAudioRouting != null) put("forceHandleAudioRouting", forceHandleAudioRouting)
        }

    companion object {
        /** A pre-configured AndroidAudioConfiguration for media playback. */
        val media =
            AndroidAudioConfiguration(
                manageAudioFocus = true,
                androidAudioMode = AndroidAudioMode.normal,
                androidAudioFocusMode = AndroidAudioFocusMode.gain,
                androidAudioStreamType = AndroidAudioStreamType.music,
                androidAudioAttributesUsageType = AndroidAudioAttributesUsageType.media,
                androidAudioAttributesContentType = AndroidAudioAttributesContentType.unknown,
            )

        /** A pre-configured AndroidAudioConfiguration for voice communication. */
        val communication =
            AndroidAudioConfiguration(
                manageAudioFocus = true,
                androidAudioMode = AndroidAudioMode.inCommunication,
                androidAudioFocusMode = AndroidAudioFocusMode.gain,
                androidAudioStreamType = AndroidAudioStreamType.voiceCall,
                androidAudioAttributesUsageType = AndroidAudioAttributesUsageType.voiceCommunication,
                androidAudioAttributesContentType = AndroidAudioAttributesContentType.speech,
            )
    }
}

/** flutter_webrtc's `AudioUtils` string → Android constant mapping. */
object WebRtcAudioUtils {
    private const val TAG = "AudioUtils"

    fun getAudioModeForString(audioModeString: String?): Int? =
        when (audioModeString) {
            null -> null
            "normal" -> AudioManager.MODE_NORMAL
            "callScreening" -> AudioManager.MODE_CALL_SCREENING
            "inCall" -> AudioManager.MODE_IN_CALL
            "inCommunication" -> AudioManager.MODE_IN_COMMUNICATION
            "ringtone" -> AudioManager.MODE_RINGTONE
            else -> {
                Log.w(TAG, "Unknown audio mode: $audioModeString")
                null
            }
        }

    fun getFocusModeForString(focusModeString: String?): Int? =
        when (focusModeString) {
            null -> null
            "gain" -> AudioManager.AUDIOFOCUS_GAIN
            "gainTransient" -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            "gainTransientExclusive" -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            "gainTransientMayDuck" -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            "loss" -> AudioManager.AUDIOFOCUS_LOSS
            else -> {
                Log.w(TAG, "Unknown audio focus mode: $focusModeString")
                null
            }
        }

    fun getStreamTypeForString(streamTypeString: String?): Int? =
        when (streamTypeString) {
            null -> null
            "accessibility" -> AudioManager.STREAM_ACCESSIBILITY
            "alarm" -> AudioManager.STREAM_ALARM
            "dtmf" -> AudioManager.STREAM_DTMF
            "music" -> AudioManager.STREAM_MUSIC
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "ring" -> AudioManager.STREAM_RING
            "system" -> AudioManager.STREAM_SYSTEM
            "voiceCall" -> AudioManager.STREAM_VOICE_CALL
            else -> {
                Log.w(TAG, "Unknown audio stream type: $streamTypeString")
                null
            }
        }

    fun getAudioAttributesUsageTypeForString(usageTypeString: String?): Int? =
        when (usageTypeString) {
            null -> null
            "alarm" -> AudioAttributes.USAGE_ALARM
            "assistanceAccessibility" -> AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
            "assistanceNavigationGuidance" -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            "assistanceSonification" -> AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
            "assistant" -> AudioAttributes.USAGE_ASSISTANT
            "game" -> AudioAttributes.USAGE_GAME
            "media" -> AudioAttributes.USAGE_MEDIA
            "notification" -> AudioAttributes.USAGE_NOTIFICATION
            "notificationEvent" -> AudioAttributes.USAGE_NOTIFICATION_EVENT
            "notificationRingtone" -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            "unknown" -> AudioAttributes.USAGE_UNKNOWN
            "voiceCommunication" -> AudioAttributes.USAGE_VOICE_COMMUNICATION
            "voiceCommunicationSignalling" -> AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING
            else -> {
                Log.w(TAG, "Unknown audio attributes usage type: $usageTypeString")
                null
            }
        }

    fun getAudioAttributesContentTypeFromString(contentTypeString: String?): Int? =
        when (contentTypeString) {
            null -> null
            "movie" -> AudioAttributes.CONTENT_TYPE_MOVIE
            "music" -> AudioAttributes.CONTENT_TYPE_MUSIC
            "sonification" -> AudioAttributes.CONTENT_TYPE_SONIFICATION
            "speech" -> AudioAttributes.CONTENT_TYPE_SPEECH
            "unknown" -> AudioAttributes.CONTENT_TYPE_UNKNOWN
            else -> {
                Log.w(TAG, "Unknown audio attributes content type:$contentTypeString")
                null
            }
        }
}

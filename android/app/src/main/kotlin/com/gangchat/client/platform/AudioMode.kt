package com.gangchat.client.platform

import android.content.Context
import android.media.AudioManager

/**
 * Whether a voice call holds the audio mode. LiveKit's AudioSwitch sets
 * [AudioManager.MODE_IN_COMMUNICATION] while the app is in a voice channel, and
 * then owns the communication route.
 */
fun isInVoiceCall(context: Context = GangChatPlatform.context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
        audioManager.mode == AudioManager.MODE_IN_CALL
}

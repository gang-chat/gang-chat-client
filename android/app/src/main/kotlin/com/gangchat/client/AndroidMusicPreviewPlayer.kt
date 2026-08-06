package com.gangchat.client

import android.media.AudioAttributes
import android.media.MediaPlayer
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Local song preview player that deliberately does not request audio focus and
 * does not touch AudioManager.mode or speakerphone routing. This lets WebRTC
 * voice capture/output continue unchanged while a user explicitly previews a
 * saved playlist track.
 */
class AndroidMusicPreviewPlayer(private val channel: MethodChannel) {
    private var player: MediaPlayer? = null
    private var pendingPlayResult: MethodChannel.Result? = null
    private var generation = 0

    fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "play" -> play(call.argument<String>("path"), result)
            "stop" -> {
                stopInternal()
                result.success(null)
            }
            "dispose" -> {
                stopInternal()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    fun dispose() {
        stopInternal()
    }

    private fun play(path: String?, result: MethodChannel.Result) {
        if (path.isNullOrBlank()) {
            result.error("invalid_path", "Preview path is required", null)
            return
        }
        stopInternal()
        val requestGeneration = ++generation
        val next = MediaPlayer()
        player = next
        pendingPlayResult = result
        next.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        next.setOnPreparedListener { prepared ->
            if (player !== prepared || generation != requestGeneration) return@setOnPreparedListener
            pendingPlayResult?.success(null)
            pendingPlayResult = null
            prepared.start()
        }
        next.setOnCompletionListener { completed ->
            if (player !== completed || generation != requestGeneration) return@setOnCompletionListener
            releaseCurrent(completed)
            channel.invokeMethod("completed", null)
        }
        next.setOnErrorListener { failed, what, extra ->
            if (player === failed && generation == requestGeneration) {
                pendingPlayResult?.error(
                    "preview_playback_failed",
                    "Android MediaPlayer failed ($what/$extra)",
                    null,
                )
                pendingPlayResult = null
                releaseCurrent(failed)
            }
            true
        }
        try {
            next.setDataSource(path)
            next.prepareAsync()
        } catch (error: Exception) {
            pendingPlayResult?.error("preview_playback_failed", error.message, null)
            pendingPlayResult = null
            releaseCurrent(next)
        }
    }

    private fun stopInternal() {
        generation += 1
        pendingPlayResult?.success(null)
        pendingPlayResult = null
        player?.let(::releaseCurrent)
    }

    private fun releaseCurrent(target: MediaPlayer) {
        if (player === target) player = null
        try {
            target.stop()
        } catch (_: IllegalStateException) {
        }
        target.reset()
        target.release()
    }
}

package com.gangchat.client.platform

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CompletableDeferred

/**
 * Local song preview player that deliberately does not request audio focus and
 * does not touch AudioManager.mode or speakerphone routing. This lets WebRTC
 * voice capture/output continue unchanged while a user explicitly previews a
 * saved playlist track. (Formerly the `gang_chat/music_preview` channel.)
 */
class AndroidMusicPreviewPlayer {
    private var player: MediaPlayer? = null
    private var pendingPlayResult: CompletableDeferred<Unit>? = null
    private var generation = 0

    /** Called when a preview plays to its end (the channel's `completed` call). */
    var onCompleted: (() -> Unit)? = null

    /**
     * Starts playing the local file at [path]. Returns once playback started,
     * or when a later [stop] supersedes it; failures are [PlatformException]s
     * with code `invalid_path` or `preview_playback_failed`.
     */
    suspend fun play(path: String?) {
        if (path.isNullOrBlank()) {
            throw PlatformException("invalid_path", "Preview path is required")
        }
        stopInternal()
        val requestGeneration = ++generation
        val next = MediaPlayer()
        val result = CompletableDeferred<Unit>()
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
            pendingPlayResult?.complete(Unit)
            pendingPlayResult = null
            prepared.start()
        }
        next.setOnCompletionListener { completed ->
            if (player !== completed || generation != requestGeneration) return@setOnCompletionListener
            releaseCurrent(completed)
            onCompleted?.invoke()
        }
        next.setOnErrorListener { failed, what, extra ->
            if (player === failed && generation == requestGeneration) {
                pendingPlayResult?.completeExceptionally(
                    PlatformException("preview_playback_failed", "Android MediaPlayer failed ($what/$extra)"),
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
            pendingPlayResult?.completeExceptionally(PlatformException("preview_playback_failed", error.message))
            pendingPlayResult = null
            releaseCurrent(next)
        }
        result.await()
    }

    fun stop() {
        stopInternal()
    }

    fun dispose() {
        stopInternal()
    }

    private fun stopInternal() {
        generation += 1
        pendingPlayResult?.complete(Unit)
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

    companion object {
        @Volatile
        private var installed: AndroidMusicPreviewPlayer? = null

        /** The process-wide player; [MissingPluginException] before the app installs it. */
        val instance: AndroidMusicPreviewPlayer
            get() = installed
                ?: throw MissingPluginException("No implementation found for gang_chat/music_preview")

        fun install(player: AndroidMusicPreviewPlayer) {
            installed = player
        }
    }
}

package com.gangchat.client.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.gangchat.client.foundation.timeout
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plays the short synthesized WAV cues (the message sound and the voice-channel
 * presence sounds) through a [SoundPool].
 *
 * The Flutter build played them as byte sources in audioplayers' low-latency
 * mode, which audioplayers_android rejects, so Android never sounded them.
 * SoundPool loads from a file, so each cue is written to the cache directory
 * once per process and stays loaded. SoundPool requests no audio focus: a cue
 * mixes with other apps' audio instead of pausing it.
 *
 * Playing a cue again stops the previous playback of the same cue first, like
 * the `stop()` + `play()` the services did on one audioplayers player per cue.
 */
open class SoundPoolCuePlayer(
    /** An [AudioAttributes] usage, e.g. [AudioAttributes.USAGE_NOTIFICATION_EVENT]. */
    val usage: Int,
    context: Context? = null,
    private val loadTimeout: Duration = 5.seconds,
) {
    private val explicitContext: Context? = context?.applicationContext ?: context
    private val context: Context get() = explicitContext ?: GangChatPlatform.context

    internal var pool: SoundPool? = null
        private set
    private val _loaded = mutableMapOf<String, CompletableDeferred<Int>>()
    private val _pendingLoads = mutableMapOf<Int, CompletableDeferred<Int>>()
    private val _streams = mutableMapOf<String, Int>()
    private var _disposed = false

    /** Plays [wav] (cached under [name]) at [volume] (0..1). */
    open suspend fun play(name: String, wav: ByteArray, volume: Double) {
        if (_disposed) return
        val soundId = _load(name, wav)
        val soundPool = pool
        if (_disposed || soundPool == null) return
        _streams.remove(name)?.let(soundPool::stop)
        val level = volume.coerceIn(0.0, 1.0).toFloat()
        val streamId = soundPool.play(soundId, level, level, 1, 0, 1f)
        if (streamId == 0) {
            throw PlatformException("AndroidAudioError", "SoundPool could not play the $name cue.")
        }
        _streams[name] = streamId
    }

    /** Releases the pool; later calls to [play] do nothing. */
    open fun dispose() {
        if (_disposed) return
        _disposed = true
        val failure = CancellationException("SoundPoolCuePlayer disposed")
        for (pending in _pendingLoads.values) pending.cancel(failure)
        _pendingLoads.clear()
        _loaded.clear()
        _streams.clear()
        pool?.release()
        pool = null
    }

    /** Loads waiting for SoundPool's load-complete callback (for tests). */
    internal val pendingLoadCount: Int get() = _pendingLoads.size

    /** The file a cue is loaded from. */
    internal fun cueFile(name: String): File = File(File(context.cacheDir, "sound_cues"), "$name.wav")

    private suspend fun _load(name: String, wav: ByteArray): Int {
        _loaded[name]?.let { return it.await() }
        val result = CompletableDeferred<Int>()
        _loaded[name] = result
        var soundId = 0
        try {
            val file = cueFile(name)
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                file.writeBytes(wav)
            }
            if (_disposed) throw CancellationException("SoundPoolCuePlayer disposed")
            soundId = _pool().load(file.path, 1)
            if (soundId == 0) {
                throw PlatformException("AndroidAudioError", "SoundPool could not load the $name cue.")
            }
            // The load-complete callback runs on this (main) looper, so it
            // cannot arrive before the id is registered.
            _pendingLoads[soundId] = result
            return timeout(loadTimeout) { result.await() }
        } catch (error: Exception) {
            // A later play retries; callers waiting on this load see the failure.
            _pendingLoads.remove(soundId)
            if (_loaded[name] === result) _loaded.remove(name)
            if (!result.isCompleted) result.completeExceptionally(error)
            throw error
        }
    }

    private fun _pool(): SoundPool {
        pool?.let { return it }
        val created =
            SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).build()
        created.setOnLoadCompleteListener { _, sampleId, status ->
            val pending = _pendingLoads.remove(sampleId) ?: return@setOnLoadCompleteListener
            if (status == 0) {
                pending.complete(sampleId)
            } else {
                pending.completeExceptionally(
                    PlatformException("AndroidAudioError", "SoundPool failed to load a cue (status $status)."),
                )
            }
        }
        pool = created
        return created
    }
}

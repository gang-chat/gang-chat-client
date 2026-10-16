package com.gangchat.client.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.view.Choreographer
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.debugPrint
import com.gangchat.client.foundation.timeout
import java.io.File
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** audioplayers' `PlayerState`. */
enum class PlayerState { stopped, playing, paused, completed, disposed }

/** audioplayers' `PlayerMode`. */
enum class PlayerMode { mediaPlayer, lowLatency }

/** audioplayers' `ReleaseMode`. */
enum class ReleaseMode { release, loop, stop }

/** audioplayers' `Source`. */
sealed class Source

class UrlSource(val url: String, val mimeType: String? = null) : Source()

class DeviceFileSource(val path: String, val mimeType: String? = null) : Source()

class BytesSource(val bytes: ByteArray, val mimeType: String? = null) : Source()

/**
 * The subset of audioplayers' `AudioPlayer` the app uses, with the
 * behaviour of audioplayers 6.7 / audioplayers_android 5.2 on Android:
 * `mediaPlayer` mode plays through [MediaPlayer], `lowLatency` mode through
 * a [SoundPool]; media attributes (usage media, content music); audio focus
 * `AUDIOFOCUS_GAIN` is requested when playback starts and abandoned on
 * stop/release; `play` returns once the source is prepared (30 s timeout);
 * the position stream ticks every frame while playing.
 *
 * Events are delivered synchronously, which keeps the ordering the Dart
 * services relied on (a `stop()`'s `stopped` event arrives before the
 * caller's next statement).
 *
 * Native failures are [PlatformException]s with code `AndroidAudioError`,
 * like the plugin's. As in audioplayers_android, a [BytesSource] cannot be
 * played in [PlayerMode.lowLatency] mode ("Bytes sources are not supported on
 * LOW_LATENCY mode yet.").
 */
open class AudioPlayer(context: Context? = null) {
    private val explicitContext: Context? = context?.applicationContext ?: context
    private val context: Context get() = explicitContext ?: GangChatPlatform.context

    private val playerStateController = StreamController.broadcast<PlayerState>(sync = true)
    private val positionController = StreamController.broadcast<Duration>(sync = true)
    private val durationController = StreamController.broadcast<Duration>(sync = true)
    private val completeController = StreamController.broadcast<Unit>(sync = true)

    open val onPlayerStateChanged: Stream<PlayerState> get() = playerStateController.stream
    open val onPositionChanged: Stream<Duration> get() = positionController.stream
    open val onDurationChanged: Stream<Duration> get() = durationController.stream
    open val onPlayerComplete: Stream<Unit> get() = completeController.stream

    private var playerState = PlayerState.stopped
    private var desiredState = PlayerState.stopped

    var state: PlayerState
        get() = playerState
        protected set(value) {
            if (playerState == PlayerState.disposed) throw IllegalStateException("AudioPlayer has been disposed")
            if (!playerStateController.isClosed) playerStateController.add(value)
            playerState = value
            desiredState = value
        }

    var source: Source? = null
        private set
    var releaseMode: ReleaseMode = ReleaseMode.release
        private set
    var mode: PlayerMode = PlayerMode.mediaPlayer
        private set
    var volume: Double = 1.0
        private set

    // -- Dart AudioPlayer --------------------------------------------------------

    open suspend fun play(source: Source, volume: Double? = null, mode: PlayerMode? = null) {
        desiredState = PlayerState.playing
        if (mode != null) setPlayerMode(mode)
        if (volume != null) setVolume(volume)
        setSource(source)
        resumeIfDesired()
    }

    open suspend fun setPlayerMode(mode: PlayerMode) {
        this.mode = mode
        nativeSetPlayerMode(mode)
    }

    open suspend fun setVolume(volume: Double) {
        this.volume = volume
        nativeSetVolume(volume.toFloat())
    }

    open suspend fun setReleaseMode(releaseMode: ReleaseMode) {
        this.releaseMode = releaseMode
        nativeSetLooping()
    }

    open suspend fun setSource(source: Source) {
        when (source) {
            is UrlSource -> {
                // Android's MediaPlayer reads data: URIs natively.
                this.source = source
                completePrepared { nativeSetUrl(encodeOnce(source.url), isLocal = false) }
            }
            is DeviceFileSource -> {
                this.source = source
                completePrepared { nativeSetUrl(source.path, isLocal = true) }
            }
            is BytesSource -> {
                this.source = source
                completePrepared { nativeSetBytes(source) }
            }
        }
    }

    open suspend fun resume() {
        desiredState = PlayerState.playing
        resumeIfDesired()
    }

    private fun resumeIfDesired() {
        if (desiredState == PlayerState.playing) {
            nativePlay()
            state = PlayerState.playing
            positionUpdater.start()
        }
    }

    open suspend fun pause() {
        desiredState = PlayerState.paused
        if (desiredState == PlayerState.paused) {
            nativePause()
            state = PlayerState.paused
            positionUpdater.stopAndUpdate()
        }
    }

    open suspend fun stop() {
        desiredState = PlayerState.stopped
        if (desiredState == PlayerState.stopped) {
            nativeStop()
            state = PlayerState.stopped
            positionUpdater.stopAndUpdate()
        }
    }

    open suspend fun release() {
        stop()
        nativeRelease()
        source = null
    }

    open suspend fun getCurrentPosition(): Duration? = currentPositionMillis()?.milliseconds

    open suspend fun getDuration(): Duration? = durationMillis()?.milliseconds

    open suspend fun dispose() {
        release()
        state = PlayerState.disposed
        positionUpdater.stop()
        playerStateController.close()
        positionController.close()
        durationController.close()
        completeController.close()
        source = null
    }

    private suspend fun completePrepared(setSource: () -> Unit) {
        val waiter = CompletableDeferred<Unit>()
        preparedWaiter = waiter
        try {
            setSource()
            timeout(preparationTimeout) { waiter.await() }
        } finally {
            if (preparedWaiter === waiter) preparedWaiter = null
        }
        positionUpdater.update()
    }

    /** The Dart player's own completion listener. */
    private fun handleComplete() {
        if (playerState != PlayerState.disposed) state = PlayerState.completed
        if (releaseMode == ReleaseMode.release) source = null
        positionUpdater.stopAndUpdate()
        completeController.add(Unit)
    }

    private val positionUpdater = FramePositionUpdater()

    /** audioplayers' `FramePositionUpdater`: one position event per frame while playing. */
    private inner class FramePositionUpdater : Choreographer.FrameCallback {
        private var running = false

        fun update() {
            val position = currentPositionMillis() ?: return
            if (!positionController.isClosed) positionController.add(position.milliseconds)
        }

        fun start() {
            running = true
            doFrame(0)
        }

        fun stop() {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }

        fun stopAndUpdate() {
            stop()
            update()
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            update()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // -- Native player (audioplayers_android WrappedPlayer) -----------------------

    private var nativeMode = PlayerMode.mediaPlayer
    private var mediaPlayer: MediaPlayer? = null
    private var soundId: Int? = null
    private var streamId: Int? = null
    private var nativeKey: Any? = null
    private var released = true
    private var playing = false
    private var preparedWaiter: CompletableDeferred<Unit>? = null
    private var loadGeneration = 0
    private var focusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    private var prepared = false
        set(value) {
            if (field == value) return
            field = value
            if (value) preparedWaiter?.complete(Unit)
        }

    private val isLooping: Boolean get() = releaseMode == ReleaseMode.loop

    private fun nativeSetUrl(url: String, isLocal: Boolean) {
        val key = "url:$isLocal:$url"
        if (nativeKey == key && !released) {
            // Same source again: the plugin only re-sends "prepared".
            preparedWaiter?.complete(Unit)
            return
        }
        when (nativeMode) {
            PlayerMode.mediaPlayer -> prepareMediaPlayer(key) { it.setDataSource(url) }
            PlayerMode.lowLatency -> loadSoundPool(key) { if (isLocal) url.removePrefix("file://") else downloadToTempFile(url) }
        }
    }

    private fun nativeSetBytes(source: BytesSource) {
        when (nativeMode) {
            PlayerMode.mediaPlayer -> prepareMediaPlayer(source) { it.setDataSource(ByteDataSource(source.bytes)) }
            PlayerMode.lowLatency ->
                throw PlatformException("AndroidAudioError", "Bytes sources are not supported on LOW_LATENCY mode yet.")
        }
    }

    private fun prepareMediaPlayer(key: Any, setDataSource: (MediaPlayer) -> Unit) {
        val player = mediaPlayer?.takeIf { !released } ?: createMediaPlayer()
        player.reset()
        prepared = false
        released = false
        nativeKey = key
        try {
            setDataSource(player)
        } catch (error: Exception) {
            throw PlatformException("AndroidAudioError", error.message, error.toString())
        }
        player.setVolume(volume.toFloat(), volume.toFloat())
        player.isLooping = isLooping
        player.prepareAsync()
    }

    private fun createMediaPlayer(): MediaPlayer {
        releaseSound()
        val player =
            MediaPlayer().apply {
                setOnPreparedListener { onPrepared(it) }
                setOnCompletionListener { if (it === mediaPlayer) onCompletion() }
                setOnErrorListener { failed, what, extra -> if (failed === mediaPlayer) onError(what, extra) else false }
                setAudioAttributes(mediaAttributes)
            }
        mediaPlayer = player
        return player
    }

    private fun onPrepared(player: MediaPlayer) {
        if (player !== mediaPlayer) return
        prepared = true
        val duration = player.duration.takeUnless { it == -1 } ?: 0
        durationController.add(duration.milliseconds)
        if (playing) requestFocusAndStart()
    }

    private fun onCompletion() {
        if (releaseMode != ReleaseMode.loop) nativeStop()
        handleComplete()
    }

    private fun onError(what: Int, extra: Int): Boolean {
        val whatMsg =
            if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) "MEDIA_ERROR_SERVER_DIED" else "MEDIA_ERROR_UNKNOWN {what:$what}"
        val extraMsg =
            when (extra) {
                MEDIA_ERROR_SYSTEM -> "MEDIA_ERROR_SYSTEM"
                MediaPlayer.MEDIA_ERROR_IO -> "MEDIA_ERROR_IO"
                MediaPlayer.MEDIA_ERROR_MALFORMED -> "MEDIA_ERROR_MALFORMED"
                MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "MEDIA_ERROR_UNSUPPORTED"
                MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "MEDIA_ERROR_TIMED_OUT"
                else -> "MEDIA_ERROR_UNKNOWN {extra:$extra}"
            }
        val error =
            if (!prepared && extraMsg == "MEDIA_ERROR_SYSTEM") {
                PlatformException(
                    "AndroidAudioError",
                    "Failed to set source. For troubleshooting, see: " +
                        "https://github.com/bluefireteam/audioplayers/blob/main/troubleshooting.md",
                    "$whatMsg, $extraMsg",
                )
            } else {
                // When an error occurs, reset player to not prepared.
                prepared = false
                PlatformException("AndroidAudioError", whatMsg, extraMsg)
            }
        val waiter = preparedWaiter
        if (waiter != null && !waiter.isCompleted) {
            waiter.completeExceptionally(error)
        } else {
            debugPrint("AudioPlayer error: $error")
        }
        return false
    }

    private fun loadSoundPool(key: Any, resolvePath: () -> String) {
        releaseMediaPlayer()
        releaseSound()
        prepared = false
        released = false
        nativeKey = key
        val generation = ++loadGeneration
        val pool = SharedSoundPool.get()
        com.gangchat.client.foundation.unawaited {
            val path =
                try {
                    withContext(Dispatchers.IO) { resolvePath() }
                } catch (error: Exception) {
                    preparedWaiter?.completeExceptionally(PlatformException("AndroidAudioError", error.message))
                    return@unawaited
                }
            if (generation != loadGeneration || released) return@unawaited
            val id = pool.load(path, 1)
            soundId = id
            SharedSoundPool.onLoaded[id] = {
                if (soundId == id) {
                    prepared = true
                    if (playing) startSound()
                }
            }
        }
    }

    private fun startSound() {
        val pool = SharedSoundPool.get()
        val stream = streamId
        val sound = soundId
        if (stream != null) {
            pool.resume(stream)
        } else if (sound != null) {
            streamId = pool.play(sound, volume.toFloat(), volume.toFloat(), 0, if (isLooping) -1 else 0, 1f)
        }
    }

    private fun releaseSound() {
        val pool = SharedSoundPool.peek()
        streamId?.let { pool?.stop(it) }
        streamId = null
        soundId?.let {
            pool?.unload(it)
            SharedSoundPool.onLoaded.remove(it)
        }
        soundId = null
    }

    private fun releaseMediaPlayer() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        player.reset()
        player.release()
    }

    private fun nativeSetPlayerMode(mode: PlayerMode) {
        if (nativeMode == mode) return
        nativeMode = mode
        // Switching implementations drops the current native player; the next
        // source creates one of the new kind (the app always stops first).
        nativeRelease()
    }

    private fun nativeSetVolume(value: Float) {
        if (released) return
        mediaPlayer?.setVolume(value, value)
        streamId?.let { SharedSoundPool.peek()?.setVolume(it, value, value) }
    }

    private fun nativeSetLooping() {
        if (released) return
        mediaPlayer?.isLooping = isLooping
        streamId?.let { SharedSoundPool.peek()?.setLoop(it, if (isLooping) -1 else 0) }
    }

    private fun nativePlay() {
        if (!playing && !released) {
            playing = true
            if (prepared) requestFocusAndStart()
        }
    }

    private fun nativePause() {
        if (playing) {
            playing = false
            if (prepared) {
                mediaPlayer?.pause()
                streamId?.let { SharedSoundPool.peek()?.pause(it) }
            }
        }
    }

    private fun nativeStop() {
        abandonFocus()
        if (released) return
        if (releaseMode != ReleaseMode.release) {
            nativePause()
            if (prepared) {
                val player = mediaPlayer
                if (player != null) {
                    val duration = player.duration.takeUnless { it == -1 }
                    if (duration == null || duration == 0) {
                        player.stop()
                        prepared = false
                        player.prepareAsync()
                    } else {
                        player.seekTo(0)
                    }
                } else {
                    streamId?.let { SharedSoundPool.peek()?.stop(it) }
                    streamId = null
                }
            }
        } else {
            nativeRelease()
        }
    }

    private fun nativeRelease() {
        abandonFocus()
        if (released) return
        if (playing) {
            try {
                mediaPlayer?.stop()
            } catch (_: IllegalStateException) {
            }
        }
        releaseMediaPlayer()
        releaseSound()
        released = true
        prepared = false
        playing = false
        nativeKey = null
    }

    private fun currentPositionMillis(): Int? =
        if (prepared && !released) mediaPlayer?.currentPosition else null

    private fun durationMillis(): Int? =
        if (prepared && !released) mediaPlayer?.duration?.takeUnless { it == -1 } else null

    private fun requestFocusAndStart() {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request =
                    focusRequest
                        ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(mediaAttributes)
                            .setOnAudioFocusChangeListener { handleFocusResult(it) }
                            .build()
                            .also { focusRequest = it }
                audioManager.requestAudioFocus(request)
            } else {
                val listener =
                    legacyFocusListener
                        ?: AudioManager.OnAudioFocusChangeListener { handleFocusResult(it) }
                            .also { legacyFocusListener = it }
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        handleFocusResult(result)
    }

    private fun handleFocusResult(result: Int) {
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> if (playing) startNative()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Keep the playing state; it resumes when focus is granted again.
                mediaPlayer?.pause()
                streamId?.let { SharedSoundPool.peek()?.pause(it) }
            }
            AudioManager.AUDIOFOCUS_LOSS -> nativePause()
        }
    }

    private fun startNative() {
        val player = mediaPlayer
        if (player != null) player.start() else startSound()
    }

    private fun abandonFocus() {
        val audioManager = explicitContext?.getSystemService(AudioManager::class.java)
            ?: if (GangChatPlatform.isInstalled) context.getSystemService(AudioManager::class.java) else null
        if (audioManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            legacyFocusListener?.let(audioManager::abandonAudioFocus)
        }
    }

    private fun downloadToTempFile(url: String): String {
        val bytes = URI.create(url).toURL().openStream().use { it.readBytes() }
        val file = File.createTempFile("sound", "")
        file.writeBytes(bytes)
        file.deleteOnExit()
        return file.absolutePath
    }

    /** audioplayers_android's `ByteDataSource`. */
    private class ByteDataSource(private val data: ByteArray) : MediaDataSource() {
        @Synchronized
        override fun getSize(): Long = data.size.toLong()

        @Synchronized
        override fun close() = Unit

        @Synchronized
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val remaining = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, remaining)
            return remaining
        }
    }

    /** The process-wide SoundPool for low-latency players (media attributes, 32 streams). */
    private object SharedSoundPool {
        private var pool: SoundPool? = null
        val onLoaded = mutableMapOf<Int, () -> Unit>()

        fun peek(): SoundPool? = pool

        fun get(): SoundPool =
            pool ?: SoundPool.Builder()
                .setAudioAttributes(mediaAttributes)
                .setMaxStreams(32)
                .build()
                .also { created ->
                    created.setOnLoadCompleteListener { _, sampleId, _ -> onLoaded.remove(sampleId)?.invoke() }
                    pool = created
                }
    }

    companion object {
        /** audioplayers' `AudioPlayer.preparationTimeout`. */
        var preparationTimeout: Duration = 30.seconds

        // For some reason this cannot be accessed from MediaPlayer.MEDIA_ERROR_SYSTEM.
        private const val MEDIA_ERROR_SYSTEM = -2147483648

        private val mediaAttributes: AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

        /** audioplayers' `UriCoder.encodeOnce`: encodes a URL unless it already is. */
        fun encodeOnce(uri: String): String {
            try {
                // If decoded differs, the uri was already encoded.
                val decodedUri = Uri.decodeFull(uri)
                if (decodedUri != uri) return uri
            } catch (_: IllegalArgumentException) {
            }
            return Uri.encodeFull(uri)
        }
    }
}

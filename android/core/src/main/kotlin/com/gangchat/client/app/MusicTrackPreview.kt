package com.gangchat.client.app

import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.protocol.MusicTrackPreviewApi
import java.math.BigInteger

enum class MusicTrackPreviewPhase { idle, loading, playing }

class MusicTrackPreviewTrack(val source: String, val trackId: String) {
    val key: String get() = musicTrackPreviewCacheKey(source, trackId)
}

class MusicTrackPreviewSnapshot(
    val phase: MusicTrackPreviewPhase = MusicTrackPreviewPhase.idle,
    val activeTrackKey: String? = null,
) {
    fun isLoading(trackKey: String): Boolean =
        activeTrackKey == trackKey && phase == MusicTrackPreviewPhase.loading

    fun isPlaying(trackKey: String): Boolean =
        activeTrackKey == trackKey && phase == MusicTrackPreviewPhase.playing
}

class MusicTrackPreviewAsset(
    /** Opaque platform locator. The default implementation uses a local path. */
    val location: String,
)

interface MusicTrackPreviewPlatform {
    val onCompleted: Stream<Unit>

    suspend fun findCached(cacheKey: String): MusicTrackPreviewAsset?

    suspend fun store(cacheKey: String, bytes: ByteArray): MusicTrackPreviewAsset

    suspend fun play(asset: MusicTrackPreviewAsset)

    suspend fun stop()

    suspend fun dispose()
}

interface MusicTrackPreviewPlatformFactory {
    fun create(): MusicTrackPreviewPlatform
}

/**
 * Owns one local preview at a time. Downloading may finish and populate the
 * cache after a card closes, but the generation check prevents stale work
 * from ever starting playback.
 */
class MusicTrackPreviewController(
    api: MusicTrackPreviewApi,
    platform: MusicTrackPreviewPlatform,
) {
    private val _api: MusicTrackPreviewApi = api
    private val _platform: MusicTrackPreviewPlatform = platform
    private val _changes: StreamController<MusicTrackPreviewSnapshot> =
        StreamController.broadcast(sync = true)
    private var _snapshot: MusicTrackPreviewSnapshot = MusicTrackPreviewSnapshot()
    private var _generation = 0
    private var _disposed = false
    private val _completionSubscription: StreamSubscription<Unit> =
        _platform.onCompleted.listen({ _ ->
            if (_disposed || _snapshot.phase != MusicTrackPreviewPhase.playing) {
                return@listen
            }
            _publish(MusicTrackPreviewSnapshot())
        })

    val snapshot: MusicTrackPreviewSnapshot get() = _snapshot
    val changes: Stream<MusicTrackPreviewSnapshot> get() = _changes.stream

    suspend fun toggle(track: MusicTrackPreviewTrack) {
        val key = track.key
        if (_snapshot.isPlaying(key)) {
            stop()
            return
        }
        preview(track)
    }

    suspend fun preview(track: MusicTrackPreviewTrack) {
        if (_disposed) return
        val generation = ++_generation
        _publish(
            MusicTrackPreviewSnapshot(
                phase = MusicTrackPreviewPhase.loading,
                activeTrackKey = track.key,
            ),
        )

        try {
            _platform.stop()
            if (_disposed || generation != _generation) return
            var asset = _platform.findCached(track.key)
            if (asset == null) {
                val downloaded =
                    _api.downloadMusicTrackPreview(
                        source = track.source,
                        trackId = track.trackId,
                    )
                if (downloaded.bytes.isEmpty()) {
                    throw IllegalStateException("试听文件为空")
                }
                asset = _platform.store(track.key, downloaded.bytes)
            }
            if (_disposed || generation != _generation) return
            _platform.play(asset)
            if (_disposed || generation != _generation) {
                _platform.stop()
                return
            }
            _publish(
                MusicTrackPreviewSnapshot(
                    phase = MusicTrackPreviewPhase.playing,
                    activeTrackKey = track.key,
                ),
            )
        } catch (e: Exception) {
            if (!_disposed && generation == _generation) {
                _publish(MusicTrackPreviewSnapshot())
            }
            throw e
        }
    }

    suspend fun stopIf(trackKey: String) {
        if (_snapshot.activeTrackKey != trackKey) return
        stop()
    }

    suspend fun stop() {
        if (_disposed) return
        _generation += 1
        _platform.stop()
        if (!_disposed) _publish(MusicTrackPreviewSnapshot())
    }

    suspend fun dispose() {
        if (_disposed) return
        _disposed = true
        _generation += 1
        _completionSubscription.cancel()
        _platform.stop()
        _platform.dispose()
        _changes.close()
    }

    private fun _publish(next: MusicTrackPreviewSnapshot) {
        _snapshot = next
        if (!_changes.isClosed) _changes.add(next)
    }
}

private val _low63Bits: BigInteger = BigInteger.ONE.shiftLeft(63).subtract(BigInteger.ONE)

fun musicTrackPreviewCacheKey(source: String, trackId: String): String {
    val value = "${source.trim()}\u0000${trackId.trim()}"
    // The low 63 bits keep the key positive.
    val hash = fnv1a64(value).and(_low63Bits)
    return hash.toString(16).padStart(16, '0')
}

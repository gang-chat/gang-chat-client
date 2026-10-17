package com.gangchat.client.shell

import com.gangchat.client.app.MusicTrackPreviewAsset
import com.gangchat.client.app.MusicTrackPreviewPlatform
import com.gangchat.client.app.MusicTrackPreviewPlatformFactory
import com.gangchat.client.config.HostPlatform
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.future
import com.gangchat.client.platform.AndroidMusicPreviewPlayer
import com.gangchat.client.platform.MissingPluginException
import com.gangchat.client.plugins.PathProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultMusicTrackPreviewPlatformFactory : MusicTrackPreviewPlatformFactory {
    // PORT: the browser platform (in-memory previews) only ran on the web.
    override fun create(): MusicTrackPreviewPlatform = LocalMusicTrackPreviewPlatform()
}

/**
 * Stores authenticated preview responses in the application cache and plays
 * them locally. Android uses a tiny native MediaPlayer bridge
 * ([AndroidMusicPreviewPlayer]) that neither requests audio focus nor mutates
 * AudioManager mode, so an active WebRTC voice session keeps its routing and
 * capture state.
 */
class LocalMusicTrackPreviewPlatform(
    androidPlayer: AndroidMusicPreviewPlayer? = null,
    cacheDirectoryProvider: (suspend () -> File)? = null,
) : MusicTrackPreviewPlatform {
    private val _androidPlayerOverride: AndroidMusicPreviewPlayer? = androidPlayer
    private val _cacheDirectoryProvider: suspend () -> File =
        cacheDirectoryProvider ?: { PathProvider.getApplicationCacheDirectory() }
    private val _completed: StreamController<Unit> = StreamController.broadcast(sync = true)
    private var _cacheDirectory: Deferred<File>? = null
    private var _disposed = false

    init {
        // Dart: `_androidChannel.setMethodCallHandler(_handleAndroidMethod)`.
        try {
            _androidPlayer().onCompleted = ::_handleAndroidCompleted
        } catch (_: MissingPluginException) {
            // Tests and unsupported Android embeddings expose no native bridge.
        }
    }

    override val onCompleted: Stream<Unit> get() = _completed.stream

    /** The native player, or [MissingPluginException] (like an unanswered channel call). */
    private fun _androidPlayer(): AndroidMusicPreviewPlayer =
        _androidPlayerOverride ?: AndroidMusicPreviewPlayer.instance

    private suspend fun _previewDirectory(): File {
        val pending =
            _cacheDirectory ?: future {
                val root = _cacheDirectoryProvider()
                val directory =
                    File(
                        "${root.path}${HostPlatform.pathSeparator}music-track-previews" +
                            "${HostPlatform.pathSeparator}v1",
                    )
                withContext(Dispatchers.IO) {
                    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                        throw IOException("Creation failed, path = '${directory.path}'")
                    }
                }
                _removeLegacyPreviews(directory)
                directory
            }.also { _cacheDirectory = it }
        return pending.await()
    }

    private suspend fun _removeLegacyPreviews(directory: File) {
        try {
            withContext(Dispatchers.IO) {
                for (entity in directory.listFiles().orEmpty()) {
                    if (entity.isFile && (entity.path.endsWith(".ogg") || entity.path.endsWith(".mp3"))) {
                        if (!entity.delete()) throw IOException("Deletion failed, path = '${entity.path}'")
                    }
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // A locked legacy file can be retried on the next application start.
        }
    }

    private suspend fun _cacheFile(cacheKey: String): File {
        val directory = _previewDirectory()
        return File("${directory.path}${HostPlatform.pathSeparator}$cacheKey.m4a")
    }

    override suspend fun findCached(cacheKey: String): MusicTrackPreviewAsset? {
        val file = _cacheFile(cacheKey)
        try {
            val touched =
                withContext(Dispatchers.IO) {
                    // Dart's `file.length()` throws for a missing file, and
                    // `setLastModified` throws when it fails.
                    if (!file.isFile) throw IOException("Cannot retrieve length of file")
                    if (file.length() <= 0) return@withContext false
                    if (!file.setLastModified(DateTime.now().millisecondsSinceEpoch)) {
                        throw IOException("Failed to set file modification time")
                    }
                    true
                }
            if (touched) return MusicTrackPreviewAsset(file.path)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Missing, empty, or unreadable files are treated as cache misses.
        }
        try {
            withContext(Dispatchers.IO) { if (file.exists()) file.delete() }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
        }
        return null
    }

    override suspend fun store(cacheKey: String, bytes: ByteArray): MusicTrackPreviewAsset {
        val file = _cacheFile(cacheKey)
        val temporary = File("${file.path}.tmp-${DateTime.now().microsecondsSinceEpoch}")
        try {
            withContext(Dispatchers.IO) {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
                if (file.exists() && !file.delete()) {
                    throw IOException("Deletion failed, path = '${file.path}'")
                }
                if (!temporary.renameTo(file)) {
                    throw IOException("Cannot rename file to '${file.path}', path = '${temporary.path}'")
                }
            }
            _trimCache(protectedPath = file.path)
        } finally {
            try {
                withContext(Dispatchers.IO) { if (temporary.exists()) temporary.delete() }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
        return MusicTrackPreviewAsset(file.path)
    }

    private class _CacheEntry(val file: File, val bytes: Long, val modified: Long)

    private suspend fun _trimCache(protectedPath: String) {
        try {
            val directory = _previewDirectory()
            withContext(Dispatchers.IO) {
                val entries = mutableListOf<_CacheEntry>()
                var totalBytes = 0L
                for (entity in directory.listFiles().orEmpty()) {
                    if (!entity.isFile || !entity.path.endsWith(".m4a")) continue
                    val size = entity.length()
                    totalBytes += size
                    entries.add(_CacheEntry(file = entity, bytes = size, modified = entity.lastModified()))
                }
                if (totalBytes <= _maxCacheBytes) return@withContext
                entries.sortBy { it.modified }
                for (entry in entries) {
                    if (totalBytes <= _maxCacheBytes) break
                    if (entry.file.path == protectedPath) continue
                    if (!entry.file.delete()) {
                        throw IOException("Deletion failed, path = '${entry.file.path}'")
                    }
                    totalBytes -= entry.bytes
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Preview cache eviction is best effort and must never block playback.
        }
    }

    override suspend fun play(asset: MusicTrackPreviewAsset) {
        if (_disposed) return
        _androidPlayer().play(asset.location)
    }

    override suspend fun stop() {
        try {
            _androidPlayer().stop()
        } catch (_: MissingPluginException) {
            // Tests and unsupported Android embeddings expose no native bridge.
        }
    }

    override suspend fun dispose() {
        if (_disposed) return
        _disposed = true
        // Dart clears the channel's handler (`setMethodCallHandler(null)`).
        try {
            val player = _androidPlayer()
            player.onCompleted = null
            player.dispose()
        } catch (_: MissingPluginException) {
            // Tests and unsupported Android embeddings expose no native bridge.
        }
        _completed.close()
    }

    private fun _handleAndroidCompleted() {
        if (!_disposed && !_completed.isClosed) {
            _completed.add(Unit)
        }
    }

    companion object {
        private const val _maxCacheBytes: Long = 256L shl 20
    }
}

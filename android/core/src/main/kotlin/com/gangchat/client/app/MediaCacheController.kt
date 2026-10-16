package com.gangchat.client.app

import com.gangchat.client.config.HostPlatform
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.plugins.PathProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val _mediaCacheVersion = "v1"

/** Dart's `typedef MediaCacheDirectoryProvider = Future<Directory> Function()`. */
typealias MediaCacheDirectoryProvider = suspend () -> File

/**
 * Dart's `typedef MediaCacheProgressHandler = void Function({required int
 * sentBytes, required int totalBytes})`; invoke with named arguments.
 */
fun interface MediaCacheProgressHandler {
    operator fun invoke(sentBytes: Long, totalBytes: Long)
}

class MediaCacheCancelledException : Exception("下载已取消") {
    override fun toString(): String = "下载已取消"
}

class MediaCacheRequest private constructor(
    val uri: Uri,
    val cacheKey: String,
    val cacheFilename: String,
    val expectedBytes: Long? = null,
) {
    companion object {
        fun tryFromUrl(
            url: String?,
            filename: String? = null,
            mimeType: String? = null,
            expectedBytes: Long? = null,
            namespace: String = "asset",
        ): MediaCacheRequest? {
            val trimmed = url?.trim()
            if (trimmed == null || trimmed.isEmpty()) return null
            val uri = Uri.tryParse(trimmed)
            if (uri == null || !_isHttpUri(uri)) return null

            val sourceName =
                _firstNonEmpty(
                    listOf(
                        filename,
                        _basenameFromUriPath(uri.path),
                        "asset",
                    ),
                )!!
            val extension =
                _extensionFor(
                    filename = sourceName,
                    uriPath = uri.path,
                    mimeType = mimeType,
                )
            val stem = _safeStem(sourceName, extension = extension)
            val key = "$namespace:$uri"
            val hash = _fnv1a64Hex(key)
            return MediaCacheRequest(
                uri = uri,
                cacheKey = hash,
                cacheFilename = "$stem-$hash$extension",
                expectedBytes = expectedBytes,
            )
        }
    }
}

/**
 * On-disk cache of remote media under `<cache dir>/media-assets/v1/`.
 *
 * PORT: the Dart controller also had a web-only in-memory cache
 * (`_webMemory`, `_downloadBytes`); the native client never takes that path,
 * so it is not ported and [getOrDownloadBytes] always reads through the disk
 * cache. Disk access runs on [Dispatchers.IO].
 */
open class MediaCacheController(
    httpClientFactory: (() -> HttpClient)? = null,
    cacheDirectoryProvider: MediaCacheDirectoryProvider? = null,
) {
    private val _httpClientFactory: () -> HttpClient = httpClientFactory ?: { HttpClient() }
    private val _cacheDirectoryProvider: MediaCacheDirectoryProvider =
        cacheDirectoryProvider ?: { PathProvider.getApplicationCacheDirectory() }
    private val _inFlight: MutableMap<String, Deferred<File>> = LinkedHashMap()

    open suspend fun cachedFile(request: MediaCacheRequest): File? {
        val file = _cacheFile(request)
        return try {
            withContext(Dispatchers.IO) {
                if (!file.exists()) {
                    null
                } else if (file.length() <= 0) {
                    null
                } else {
                    file
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
    }

    open suspend fun getOrDownload(
        request: MediaCacheRequest,
        transfer: FileTransferState? = null,
        onProgress: MediaCacheProgressHandler? = null,
    ): File {
        val existing = cachedFile(request)
        if (existing != null) return existing

        val active = _inFlight[request.cacheKey]
        if (active != null) return active.await()

        val future =
            future {
                _downloadToCache(
                    request = request,
                    transfer = transfer,
                    onProgress = onProgress,
                )
            }
        _inFlight[request.cacheKey] = future
        try {
            return future.await()
        } finally {
            if (_inFlight[request.cacheKey] === future) {
                _inFlight.remove(request.cacheKey)
            }
        }
    }

    open suspend fun readBytes(request: MediaCacheRequest): ByteArray {
        return getOrDownloadBytes(request = request)
    }

    /**
     * The media's bytes, read through the on-disk cache.
     *
     * [transfer] and [onProgress] report progress and allow pausing and
     * cancelling, as for [getOrDownload].
     */
    open suspend fun getOrDownloadBytes(
        request: MediaCacheRequest,
        transfer: FileTransferState? = null,
        onProgress: MediaCacheProgressHandler? = null,
    ): ByteArray {
        val file =
            getOrDownload(
                request = request,
                transfer = transfer,
                onProgress = onProgress,
            )
        return withContext(Dispatchers.IO) { file.readBytes() }
    }

    open suspend fun copyFileToPath(
        source: File,
        destinationPath: String,
        transfer: FileTransferState? = null,
        onProgress: MediaCacheProgressHandler? = null,
    ) {
        if (source.path == destinationPath) {
            val total = withContext(Dispatchers.IO) { _length(source) }
            transfer?.updateProgress(sentBytes = total, totalBytes = total)
            onProgress?.invoke(sentBytes = total, totalBytes = total)
            return
        }

        val destination = File(destinationPath)
        withContext(Dispatchers.IO) { _createParent(destination) }
        val temp = File("$destinationPath.gangtmp-${DateTime.now().microsecondsSinceEpoch}")
        var sink: OutputStream? = null
        var input: InputStream? = null
        var received = 0L
        val total = withContext(Dispatchers.IO) { _length(source) }
        try {
            val output = withContext(Dispatchers.IO) { FileOutputStream(temp) }
            sink = output
            transfer?.updateProgress(sentBytes = 0, totalBytes = total)
            onProgress?.invoke(sentBytes = 0, totalBytes = total)
            val reader = withContext(Dispatchers.IO) { FileInputStream(source) }
            input = reader
            // Dart's `File.openRead()` delivers 64 KiB chunks.
            val buffer = ByteArray(_readChunkSize)
            while (true) {
                val count = withContext(Dispatchers.IO) { reader.read(buffer) }
                if (count < 0) break
                if (count == 0) continue
                _waitIfPaused(transfer)
                if (transfer?.cancelled == true) {
                    throw MediaCacheCancelledException()
                }
                withContext(Dispatchers.IO) { output.write(buffer, 0, count) }
                received += count
                transfer?.updateProgress(sentBytes = received, totalBytes = total)
                onProgress?.invoke(sentBytes = received, totalBytes = total)
            }
            withContext(Dispatchers.IO) {
                output.flush()
                output.close()
            }
            sink = null

            if (transfer?.cancelled == true) {
                throw MediaCacheCancelledException()
            }
            withContext(Dispatchers.IO) {
                if (destination.exists()) Files.delete(destination.toPath())
                _rename(temp, destination)
            }
            if (transfer != null) transfer.wroteDestination = true
        } catch (error: Exception) {
            // Dart: `on MediaCacheCancelledException` and `catch (_)` both
            // delete the temp file and rethrow.
            _deleteQuietly(temp)
            throw error
        } finally {
            _closeQuietly(input)
            _closeQuietly(sink)
        }
    }

    private suspend fun _downloadToCache(
        request: MediaCacheRequest,
        transfer: FileTransferState?,
        onProgress: MediaCacheProgressHandler?,
    ): File {
        val file = _cacheFile(request)
        withContext(Dispatchers.IO) { _createParent(file) }
        val temp = File("${file.path}.tmp-${DateTime.now().microsecondsSinceEpoch}")

        var client: HttpClient? = null
        var sink: OutputStream? = null
        var received = 0L
        try {
            val activeClient = _httpClientFactory()
            client = activeClient
            if (transfer != null) transfer.downloadClient = activeClient
            val response = activeClient.send(HttpRequest("GET", request.uri))
            if (response.statusCode < 200 || response.statusCode >= 300) {
                throw IllegalStateException("下载失败 (${response.statusCode})")
            }
            val total = response.contentLength ?: request.expectedBytes ?: 0
            transfer?.updateProgress(sentBytes = 0, totalBytes = total)
            onProgress?.invoke(sentBytes = 0, totalBytes = total)

            val output = withContext(Dispatchers.IO) { FileOutputStream(temp) }
            sink = output
            response.stream.flow.collect { chunk ->
                _waitIfPaused(transfer)
                if (transfer?.cancelled == true) {
                    throw MediaCacheCancelledException()
                }
                withContext(Dispatchers.IO) { output.write(chunk) }
                received += chunk.size
                transfer?.updateProgress(sentBytes = received, totalBytes = total)
                onProgress?.invoke(sentBytes = received, totalBytes = total)
            }
            withContext(Dispatchers.IO) {
                output.flush()
                output.close()
            }
            sink = null

            if (transfer?.cancelled == true) {
                throw MediaCacheCancelledException()
            }
            val expectedLength = response.contentLength ?: request.expectedBytes
            if (expectedLength != null &&
                expectedLength > 0 &&
                expectedLength != received
            ) {
                throw IllegalStateException("下载不完整")
            }
            return withContext(Dispatchers.IO) {
                if (file.exists()) Files.delete(file.toPath())
                _rename(temp, file)
            }
        } catch (error: Exception) {
            // Dart: `on MediaCacheCancelledException` and `catch (_)` both
            // delete the temp file and rethrow.
            _deleteQuietly(temp)
            throw error
        } finally {
            if (transfer != null && transfer.downloadClient === client) {
                transfer.downloadClient = null
            }
            client?.close()
            _closeQuietly(sink)
        }
    }

    private suspend fun _cacheFile(request: MediaCacheRequest): File {
        val root = _cacheDirectoryProvider()
        val separator = HostPlatform.pathSeparator
        return File(
            "${root.path}${separator}media-assets$separator$_mediaCacheVersion$separator${request.cacheFilename}",
        )
    }

    private companion object {
        const val _readChunkSize = 64 * 1024

        suspend fun _waitIfPaused(transfer: FileTransferState?) {
            val controller = transfer?.controller ?: return
            controller.waitIfPaused()
        }

        suspend fun _deleteQuietly(file: File) {
            try {
                // Cleanup also runs when the calling coroutine is being cancelled.
                withContext(NonCancellable + Dispatchers.IO) {
                    if (file.exists()) Files.delete(file.toPath())
                }
            } catch (_: Exception) {
            }
        }

        suspend fun _closeQuietly(stream: java.io.Closeable?) {
            if (stream == null) return
            try {
                withContext(NonCancellable + Dispatchers.IO) { stream.close() }
            } catch (_: Exception) {
            }
        }

        /** Dart's `File.length()`: throws when the file does not exist. */
        fun _length(file: File): Long {
            if (!file.isFile) throw java.io.FileNotFoundException("Cannot retrieve length of file, path = '${file.path}'")
            return file.length()
        }

        /** Dart's `parent.create(recursive: true)`. */
        fun _createParent(file: File) {
            val parent = file.absoluteFile.parentFile ?: return
            Files.createDirectories(parent.toPath())
        }

        /** Dart's `File.rename(newPath)` (POSIX `rename`, replacing the target). */
        fun _rename(source: File, target: File): File {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return target
        }
    }
}

private fun _isHttpUri(uri: Uri): Boolean = uri.scheme == "http" || uri.scheme == "https"

private fun _firstNonEmpty(values: Iterable<String?>): String? {
    for (value in values) {
        val trimmed = value?.trim()
        if (trimmed != null && trimmed.isNotEmpty()) return trimmed
    }
    return null
}

private fun _basenameFromUriPath(path: String): String? {
    if (path.isEmpty()) return null
    val segments = path.split('/').filter { segment -> segment.isNotEmpty() }
    if (segments.isEmpty()) return null
    return Uri.decodeComponent(segments.last())
}

private fun _extensionFor(filename: String?, uriPath: String?, mimeType: String?): String {
    for (candidate in listOf(filename, _basenameFromUriPath(uriPath ?: ""))) {
        val ext = _extensionFromName(candidate)
        if (ext != null) return ext
    }
    return when ((mimeType ?: "").lowercase()) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "application/pdf" -> ".pdf"
        "text/plain" -> ".txt"
        else -> ""
    }
}

private fun _extensionFromName(value: String?): String? {
    val name = value?.trim()
    if (name == null || name.isEmpty()) return null
    val slash = _lastSeparatorIndex(name)
    val base = if (slash >= 0) name.substring(slash + 1) else name
    val dot = base.lastIndexOf('.')
    if (dot <= 0 || dot == base.length - 1) return null
    val ext = base.substring(dot).lowercase()
    if (ext.length > 12) return null
    for (unit in ext) {
        val code = unit.code
        val isDot = code == 46
        val isDigit = code in 48..57
        val isLower = code in 97..122
        if (!isDot && !isDigit && !isLower) return null
    }
    return ext
}

private fun _safeStem(value: String, extension: String): String {
    var base = value.replace('\\', '/')
    val slash = base.lastIndexOf('/')
    if (slash >= 0) base = base.substring(slash + 1)
    base = base.trim()
    if (extension.isNotEmpty() && base.lowercase().endsWith(extension)) {
        base = base.substring(0, base.length - extension.length)
    }
    val buffer = StringBuilder()
    var lastDash = false
    for (unit in base) {
        if (_isSafeStemUnit(unit.code)) {
            buffer.append(unit)
            lastDash = false
        } else if (!lastDash) {
            buffer.append('-')
            lastDash = true
        }
    }
    val raw = buffer.toString()
    var start = 0
    var end = raw.length
    while (start < end && _isTrimmedStemUnit(raw[start].code)) {
        start++
    }
    while (end > start && _isTrimmedStemUnit(raw[end - 1].code)) {
        end--
    }
    val stem = if (start == end) "asset" else raw.substring(start, end)
    return if (stem.length <= 64) stem else stem.substring(0, 64)
}

private fun _lastSeparatorIndex(value: String): Int {
    val slash = value.lastIndexOf('/')
    val backslash = value.lastIndexOf('\\')
    return if (slash > backslash) slash else backslash
}

private fun _isSafeStemUnit(unit: Int): Boolean {
    return unit == 45 ||
        unit == 46 ||
        unit == 95 ||
        unit in 48..57 ||
        unit in 65..90 ||
        unit in 97..122
}

private fun _isTrimmedStemUnit(unit: Int): Boolean = unit == 45 || unit == 46

private fun _fnv1a64Hex(value: String): String {
    // Signed, so keys match the files already cached on disk (some start
    // with '-').
    return fnv1a64Signed(value).toString(16).padStart(16, '0')
}

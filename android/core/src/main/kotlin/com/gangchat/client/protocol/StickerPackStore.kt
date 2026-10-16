package com.gangchat.client.protocol

import com.gangchat.client.config.HostPlatform
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.base64UrlEncode
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Encode
import com.gangchat.client.plugins.PathProvider
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StickerPackStore {
    suspend fun readPersonalPacks(
        userId: String,
        apiBaseUrl: String,
    ): List<StickerPack>? {
        if (userId.isEmpty() || apiBaseUrl.isEmpty()) return null
        val account = _accountKey(userId, apiBaseUrl)
        val memory = _memoryCache[account]
        if (memory != null) return memory
        try {
            val file = _cacheFile(account)
            val raw = withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }
            if (raw.isEmpty()) return null
            val decoded = jsonDecode(raw)
            if (decoded !is Map<*, *>) return null
            val version = decoded["version"]
            // Dart compares with `!=`, where 1 == 1.0.
            if (!(version is Number && version.toDouble() == _version.toDouble())) return null
            val packs = decoded["packs"]
            if (packs !is List<*>) return null
            @Suppress("UNCHECKED_CAST")
            val parsed = packs.map { StickerPack.fromJson(it as JsonMap) }
            _memoryCache[account] = parsed
            return parsed
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return null
        }
    }

    suspend fun writePersonalPacks(
        userId: String,
        apiBaseUrl: String,
        packs: List<StickerPack>,
    ) {
        if (userId.isEmpty() || apiBaseUrl.isEmpty()) return
        val account = _accountKey(userId, apiBaseUrl)
        _memoryCache[account] = packs
        try {
            val file = _cacheFile(account)
            val contents =
                jsonEncode(
                    linkedMapOf(
                        "version" to _version,
                        "saved_at" to DateTime.now().toUtc().toIso8601String(),
                        "packs" to packs.map { pack -> pack.toJson() },
                    ),
                )
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val tmp = File("${file.path}.tmp")
                tmp.writeText(contents, Charsets.UTF_8)
                if (file.exists()) file.delete()
                if (!tmp.renameTo(file)) throw IOException("Cannot rename file to '${file.path}'")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // The cache is a convenience path. Network data should remain usable even
            // when the platform cache directory is unavailable.
        }
    }

    suspend fun clearPersonalPacks(
        userId: String,
        apiBaseUrl: String,
    ) {
        if (userId.isEmpty() || apiBaseUrl.isEmpty()) return
        val account = _accountKey(userId, apiBaseUrl)
        _memoryCache.remove(account)
        try {
            val file = _cacheFile(account)
            withContext(Dispatchers.IO) {
                if (file.exists()) file.delete()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    private fun _accountKey(userId: String, apiBaseUrl: String): String =
        base64UrlEncode(utf8Encode("$apiBaseUrl\n$userId")).replace("=", "")

    private suspend fun _cacheFile(account: String): File {
        val dir = PathProvider.getApplicationCacheDirectory()
        val separator = HostPlatform.pathSeparator
        return File(
            "${dir.path}${separator}sticker-packs$separator$_filePrefix-$account.json",
        )
    }

    companion object {
        private const val _version = 1
        private const val _filePrefix = "gang-sticker-packs-v1"

        private val _memoryCache: MutableMap<String, List<StickerPack>> = LinkedHashMap()
    }
}

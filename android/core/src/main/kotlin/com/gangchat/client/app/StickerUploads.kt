package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.FormatException
import com.gangchat.client.foundation.utf8Decode
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.coroutines.cancellation.CancellationException

val stickerImageExtensions: Set<String> = setOf("png", "jpg", "jpeg", "webp", "gif")
const val maxStickerUploadsPerBatch = 500
const val maxStickerImageBytes = 25 * 1024 * 1024

typealias StickerBytesReader = suspend () -> ByteArray
typealias StickerImageDecoder = suspend (bytes: ByteArray) -> StickerImageDimensions

class StickerUploadSource(val filename: String, val readBytes: StickerBytesReader)

class StickerUploadItem(val filename: String, val bytes: ByteArray)

class StickerImageDimensions(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"
}

fun stickerPackRequiresServerMessage(): String {
    return "表情包需要登录后从服务端读取"
}

@Suppress("UNUSED_PARAMETER")
fun stickerPickerOpenFailureMessage(error: Any): String {
    return "无法打开文件选择器"
}

fun stickerNoUploadableImagesMessage(): String {
    return "没有找到可上传的图片"
}

suspend fun stickerUploadItemsFromFiles(
    files: Iterable<StickerUploadSource>,
    decodeImageDimensions: StickerImageDecoder,
): List<StickerUploadItem> {
    val items = mutableListOf<StickerUploadItem>()
    for (file in files) {
        val filename = _basename(file.filename)
        if (_isZipFilename(filename)) {
            items.addAll(
                _stickerUploadItemsFromZip(
                    file,
                    decodeImageDimensions = decodeImageDimensions,
                ),
            )
        } else if (_isStickerImageFilename(filename)) {
            items.add(
                _stickerUploadItemFromBytes(
                    filename,
                    file.readBytes(),
                    decodeImageDimensions = decodeImageDimensions,
                ),
            )
        }
        if (items.size > maxStickerUploadsPerBatch) {
            throw IllegalStateException("一次最多上传 $maxStickerUploadsPerBatch 个表情")
        }
    }
    return items
}

fun stickerUploadFilename(originalName: String, index: Int): String {
    val cleaned =
        originalName
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .replace(Regex("-+"), "-")
    val extensionMatch = Regex("\\.([A-Za-z0-9]+)$").find(cleaned)
    val extension =
        if (extensionMatch == null) {
            "png"
        } else {
            extensionMatch.groupValues[1].lowercase()
        }
    val stem = cleaned.replaceFirst(Regex("\\.[A-Za-z0-9]+$"), "")
    val safeStem = if (stem.isEmpty()) "sticker" else stem
    return "$safeStem-${DateTime.now().millisecondsSinceEpoch}-$index.$extension"
}

fun stickerNameFromFilename(filename: String): String {
    val stem = filename.trim().replaceFirst(Regex("\\.[^.]+$"), "").trim()
    if (stem.isEmpty()) return "sticker"
    val runes = stem.codePoints().limit(32).toArray()
    val chars = String(runes, 0, runes.size)
    return if (chars.isEmpty()) "sticker" else chars
}

private suspend fun _stickerUploadItemsFromZip(
    file: StickerUploadSource,
    decodeImageDimensions: StickerImageDecoder,
): List<StickerUploadItem> {
    val bytes = file.readBytes()
    if (bytes.isEmpty()) throw IllegalStateException("${file.filename} 文件为空")

    val archive = _ZipDecoder.decodeBytes(bytes)
    val items = mutableListOf<StickerUploadItem>()
    for (entry in archive.files) {
        val entryName = entry.name
        if (!entry.isFile ||
            _isIgnoredZipEntry(entryName) ||
            !_isStickerImageFilename(entryName)
        ) {
            continue
        }
        if (entry.size > maxStickerImageBytes) {
            throw IllegalStateException("${_basename(entryName)} 超过 25MB")
        }
        val content = entry.readBytes() ?: continue
        items.add(
            _stickerUploadItemFromBytes(
                _basename(entryName),
                content,
                decodeImageDimensions = decodeImageDimensions,
            ),
        )
        if (items.size > maxStickerUploadsPerBatch) {
            throw IllegalStateException("一次最多上传 $maxStickerUploadsPerBatch 个表情")
        }
    }
    return items
}

private suspend fun _stickerUploadItemFromBytes(
    filename: String,
    bytes: ByteArray,
    decodeImageDimensions: StickerImageDecoder,
): StickerUploadItem {
    if (bytes.isEmpty()) throw IllegalStateException("$filename 文件为空")
    if (bytes.size > maxStickerImageBytes) {
        throw IllegalStateException("$filename 超过 25MB")
    }
    try {
        decodeImageDimensions(bytes)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        throw IllegalStateException("$filename 不是可识别的图片")
    }
    return StickerUploadItem(filename = filename, bytes = bytes)
}

private fun _isStickerImageFilename(filename: String): Boolean {
    return stickerImageExtensions.contains(_extensionOf(filename))
}

private fun _isZipFilename(filename: String): Boolean = _extensionOf(filename) == "zip"

private fun _isIgnoredZipEntry(name: String): Boolean {
    val normalized = name.replace('\\', '/')
    val parts = normalized.split('/').filter { part -> part.isNotEmpty() }
    if (parts.any { part -> part == "__MACOSX" }) return true
    return _basename(normalized).startsWith(".")
}

private fun _basename(path: String): String {
    val normalized = path.replace('\\', '/')
    val parts = normalized.split('/').filter { part -> part.isNotEmpty() }
    val name = if (parts.isEmpty()) "" else parts.last()
    return if (name.isEmpty()) "sticker" else name
}

private fun _extensionOf(filename: String): String {
    val name = _basename(filename).lowercase()
    val index = name.lastIndexOf('.')
    if (index < 0 || index == name.length - 1) return ""
    return name.substring(index + 1)
}

// --- package:archive's ZipDecoder ------------------------------------------
//
// PORT: the parts of `package:archive` (4.x) `ZipDecoder` this file relies on,
// with java.util.zip's Inflater for deflated data. Like archive it reads the
// central directory (so sizes are known for entries written with data
// descriptors), checks every local header up front, keeps the last of
// several entries with the same name in the first one's place, decides
// `isFile` the same way, and decompresses an entry only on `readBytes()`.
// Stored and deflated entries are supported; encrypted entries and other
// compression methods (archive also reads bzip2) read as `null` and are
// skipped by the caller.

private class _ZipArchiveFile(
    /** Path inside the archive, `/`-separated as stored. */
    val name: String,
    val isFile: Boolean,
    /** Uncompressed size in bytes. */
    val size: Long,
    private val content: () -> ByteArray?,
) {
    /** Decompresses the entry; `null` for unsupported (encrypted / exotic) entries. */
    fun readBytes(): ByteArray? = content()
}

private class _ZipArchive(val files: List<_ZipArchiveFile>)

private object _ZipDecoder {
    private const val eocdSignature = 0x06054b50L
    private const val zip64EocdLocatorSignature = 0x07064b50L
    private const val zip64EocdSignature = 0x06064b50L
    private const val centralDirectorySignature = 0x02014b50L
    private const val localHeaderSignature = 0x04034b50L

    fun decodeBytes(bytes: ByteArray): _ZipArchive {
        val eocd = findEndOfCentralDirectory(bytes)
        var entryCount = u16(bytes, eocd + 10)
        var directorySize = u32(bytes, eocd + 12)
        var directoryOffset = u32(bytes, eocd + 16)

        if (entryCount == 0xffffL || directorySize == 0xffffffffL || directoryOffset == 0xffffffffL) {
            val locator = eocd - 20
            if (locator >= 0 && u32(bytes, locator) == zip64EocdLocatorSignature) {
                val zip64Offset = u64(bytes, locator + 8)
                if (zip64Offset < 0 || zip64Offset + 56 > bytes.size || u32(bytes, zip64Offset.toInt()) != zip64EocdSignature) {
                    throw FormatException("Invalid ZIP64 end of central directory")
                }
                entryCount = u64(bytes, zip64Offset.toInt() + 32)
                directorySize = u64(bytes, zip64Offset.toInt() + 40)
                directoryOffset = u64(bytes, zip64Offset.toInt() + 48)
            }
        }

        // archive.add() replaces an entry with the same name in place.
        val files = LinkedHashMap<String, _ZipArchiveFile>()
        val directoryEnd = minOf(directoryOffset + directorySize, bytes.size.toLong())
        var offset = directoryOffset
        var index = 0L
        while (index < entryCount && offset + 46 <= directoryEnd) {
            val position = offset.toInt()
            // Like archive, stop at the first record that is not a file header.
            if (u32(bytes, position) != centralDirectorySignature) break
            val versionMadeBy = u16(bytes, position + 4).toInt()
            val flags = u16(bytes, position + 8).toInt()
            val method = u16(bytes, position + 10).toInt()
            var compressedSize = u32(bytes, position + 20)
            var uncompressedSize = u32(bytes, position + 24)
            val nameLength = u16(bytes, position + 28).toInt()
            val extraLength = u16(bytes, position + 30).toInt()
            val commentLength = u16(bytes, position + 32).toInt()
            val externalAttributes = u32(bytes, position + 38)
            var localHeaderOffset = u32(bytes, position + 42)
            val nameStart = position + 46
            if (nameStart + nameLength + extraLength > bytes.size) {
                throw FormatException("Invalid central directory entry")
            }
            val name = readName(bytes, nameStart, nameLength)

            // ZIP64 extended information: only the fields saturated in the
            // header are present, in this fixed order.
            var extra = nameStart + nameLength
            val extraEnd = extra + extraLength
            while (extra + 4 <= extraEnd) {
                val headerId = u16(bytes, extra).toInt()
                val size = u16(bytes, extra + 2).toInt()
                if (headerId == 0x0001) {
                    var field = extra + 4
                    val fieldsEnd = minOf(extra + 4 + size, extraEnd)
                    if (uncompressedSize == 0xffffffffL && field + 8 <= fieldsEnd) {
                        uncompressedSize = u64(bytes, field)
                        field += 8
                    }
                    if (compressedSize == 0xffffffffL && field + 8 <= fieldsEnd) {
                        compressedSize = u64(bytes, field)
                        field += 8
                    }
                    if (localHeaderOffset == 0xffffffffL && field + 8 <= fieldsEnd) {
                        localHeaderOffset = u64(bytes, field)
                    }
                }
                extra += 4 + size
            }

            // Same rule as package:archive: Unix-made entries use the mode
            // bits, everything else treats a trailing slash as a directory.
            var isFile: Boolean
            if (versionMadeBy shr 8 == 3) {
                val fileType = (externalAttributes ushr 16) and 0xf000L
                isFile = fileType == 0x8000L || fileType == 0x0000L
                if (name.endsWith("/")) isFile = false
            } else {
                isFile = !name.endsWith("/")
            }

            val dataStart = localEntryDataStart(bytes, localHeaderOffset)
            val encrypted = (flags and 0x1) != 0
            val entrySize = uncompressedSize
            val entryCompressedSize = compressedSize
            files[name] =
                _ZipArchiveFile(
                    name = name,
                    isFile = isFile,
                    size = entrySize,
                    content = content@{
                        if (encrypted) return@content null
                        if (method != 0 && method != 8) return@content null
                        val end = dataStart + entryCompressedSize
                        if (end > bytes.size) throw FormatException("Truncated ZIP entry")
                        val data = bytes.copyOfRange(dataStart.toInt(), end.toInt())
                        if (method == 0) data else inflateRaw(data, entrySize)
                    },
                )

            offset = (extraEnd + commentLength).toLong()
            index++
        }
        return _ZipArchive(files.values.toList())
    }

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
        for (offset in bytes.size - 22 downTo 0) {
            if (u32(bytes, offset) == eocdSignature) return offset
        }
        throw FormatException("Could not find End of Central Directory Record")
    }

    /** Validates the local file header and returns where the entry's data starts. */
    private fun localEntryDataStart(bytes: ByteArray, offset: Long): Long {
        if (offset < 0 || offset + 30 > bytes.size || u32(bytes, offset.toInt()) != localHeaderSignature) {
            throw FormatException("Invalid Zip Signature")
        }
        val position = offset.toInt()
        val nameLength = u16(bytes, position + 26)
        val extraLength = u16(bytes, position + 28)
        return offset + 30 + nameLength + extraLength
    }

    /** UTF-8, falling back to one character per byte like archive's `readString`. */
    private fun readName(bytes: ByteArray, start: Int, length: Int): String {
        val raw = bytes.copyOfRange(start, start + length)
        return try {
            utf8Decode(raw)
        } catch (_: FormatException) {
            String(raw, Charsets.ISO_8859_1)
        }
    }

    /** Decompresses a raw DEFLATE stream (no zlib/gzip wrapper). */
    private fun inflateRaw(data: ByteArray, expectedSize: Long): ByteArray {
        val inflater = Inflater(true)
        try {
            // zlib may need one byte past the end of a raw stream (see Inflater).
            inflater.setInput(data.copyOf(data.size + 1))
            val output = ByteArrayOutputStream(expectedSize.coerceIn(32L, 1L shl 20).toInt())
            val chunk = ByteArray(64 * 1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(chunk)
                if (count > 0) {
                    output.write(chunk, 0, count)
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    throw FormatException("Unexpected end of deflate stream")
                }
            }
            return output.toByteArray()
        } catch (error: DataFormatException) {
            throw FormatException(error.message ?: "Invalid deflate data")
        } finally {
            inflater.end()
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 2 > bytes.size) throw FormatException("Unexpected end of ZIP data")
        return ((bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8))
    }

    private fun u32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) throw FormatException("Unexpected end of ZIP data")
        return u16(bytes, offset) or (u16(bytes, offset + 2) shl 16)
    }

    private fun u64(bytes: ByteArray, offset: Int): Long {
        return u32(bytes, offset) or (u32(bytes, offset + 4) shl 32)
    }
}

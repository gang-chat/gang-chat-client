package com.gangchat.client.shell

import com.gangchat.client.platform.FileSelector
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileTypeGroup(val label: String, val extensions: List<String>)

/** A file chosen by the user (Dart's `XFile` wrapper). */
class SelectedFile private constructor(
    val name: String,
    val mimeType: String?,
    private val readAsBytes: suspend () -> ByteArray,
    private val length: suspend () -> Long,
) {
    suspend fun readAsBytes(): ByteArray = readAsBytes.invoke()

    suspend fun length(): Long = length.invoke()

    companion object {
        /**
         * A document from the system picker. Like file_selector's `XFile`,
         * the name is the private copy's file name.
         */
        internal fun _fromPickedFile(file: FileSelector.PickedFile): SelectedFile {
            val path = file.path
            val bytes = file.bytes
            return SelectedFile(
                name = path?.substringAfterLast(File.separatorChar).orEmpty(),
                mimeType = file.mimeType,
                readAsBytes = {
                    if (bytes != null) bytes else withContext(Dispatchers.IO) { File(path!!).readBytes() }
                },
                length = { file.size.toLong() },
            )
        }

        fun fromPath(path: String): SelectedFile =
            SelectedFile(
                name = path.substringAfterLast(File.separatorChar),
                mimeType = null,
                readAsBytes = { withContext(Dispatchers.IO) { File(path).readBytes() } },
                length = { withContext(Dispatchers.IO) { File(path).length() } },
            )

        /**
         * A file whose bytes are loaded on demand, such as one dropped or pasted
         * into a browser, which exposes contents but no path.
         */
        fun deferred(
            name: String,
            mimeType: String,
            length: Long,
            readAsBytes: suspend () -> ByteArray,
        ): SelectedFile =
            SelectedFile(
                name = name,
                mimeType = mimeType.ifEmpty { null },
                readAsBytes = readAsBytes,
                length = { length },
            )

        fun fromBytes(name: String, mimeType: String, bytes: ByteArray): SelectedFile {
            val data = bytes.copyOf()
            return SelectedFile(
                name = name,
                mimeType = mimeType,
                readAsBytes = { data.copyOf() },
                length = { data.size.toLong() },
            )
        }
    }
}

class SaveFileLocation(val path: String, val documentUri: String? = null) {
    val requiresCommit: Boolean get() = documentUri != null
}

/**
 * Opening and saving files. Android opens documents with the system picker
 * and saves through the system "save as" dialog (a document URI plus a
 * private staging path) or into public Downloads.
 */
open class FileSelectionService(
    val androidSystemService: AndroidSystemService = AndroidSystemService(),
) {
    open suspend fun openFile(acceptedTypeGroups: List<FileTypeGroup> = emptyList()): SelectedFile? {
        val file = FileSelector.openFile(extensions = _selectorExtensions(acceptedTypeGroups))
        return if (file == null) null else SelectedFile._fromPickedFile(file)
    }

    open suspend fun openFiles(acceptedTypeGroups: List<FileTypeGroup> = emptyList()): List<SelectedFile> {
        val files = FileSelector.openFiles(extensions = _selectorExtensions(acceptedTypeGroups))
        return files.map(SelectedFile::_fromPickedFile)
    }

    @Suppress("UNUSED_PARAMETER")
    open suspend fun getSaveLocation(
        suggestedName: String,
        acceptedTypeGroups: List<FileTypeGroup> = emptyList(),
        confirmButtonText: String? = null,
    ): SaveFileLocation? {
        if (!androidSystemService.isSupported) return null
        val location =
            androidSystemService.createDocument(
                suggestedName = suggestedName,
                mimeType = _mimeTypeForSuggestedName(suggestedName),
            )
        return if (location == null) {
            null
        } else {
            SaveFileLocation(path = location.stagingPath, documentUri = location.uri)
        }
    }

    open fun filesFromPaths(paths: Iterable<String>): List<SelectedFile> = paths.map(SelectedFile::fromPath)

    @Suppress("UNUSED_PARAMETER")
    open suspend fun saveBytesToPath(bytes: ByteArray, path: String, filename: String, mimeType: String? = null) {
        withContext(Dispatchers.IO) { File(path).writeBytes(bytes) }
    }

    open suspend fun saveBytesToLocation(
        bytes: ByteArray,
        location: SaveFileLocation,
        filename: String,
        mimeType: String? = null,
    ) {
        val documentUri = location.documentUri
        if (documentUri != null) {
            androidSystemService.writeDocumentBytes(uri = documentUri, bytes = bytes)
            return
        }
        saveBytesToPath(bytes = bytes, path = location.path, filename = filename, mimeType = mimeType)
    }

    open suspend fun commitLocation(location: SaveFileLocation) {
        val documentUri = location.documentUri ?: return
        androidSystemService.commitDocumentFile(uri = documentUri, stagingPath = location.path)
    }

    open suspend fun discardLocation(location: SaveFileLocation) {
        if (!location.requiresCommit) return
        androidSystemService.discardDocumentFile(location.path)
    }

    /** Browser downloads only exist on the web; always false on Android. */
    val savesAsBrowserDownload: Boolean get() = false

    open suspend fun saveBytesToDownloads(bytes: ByteArray, filename: String, mimeType: String? = null): String? {
        return androidSystemService.saveToDownloads(
            filename = filename,
            bytes = bytes,
            mimeType = mimeType ?: _mimeTypeForSuggestedName(filename),
        )
    }
}

/** file_selector's `XTypeGroup` drops leading dots from extensions. */
private fun _selectorExtensions(groups: List<FileTypeGroup>): List<String> =
    groups.flatMap { group -> group.extensions.map { it.trimStart('.') } }.distinct()

private fun _mimeTypeForSuggestedName(filename: String): String {
    val extension = filename.split('.').last().lowercase()
    return when (extension) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "zip" -> "application/zip"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}

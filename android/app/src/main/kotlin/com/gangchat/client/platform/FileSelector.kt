package com.gangchat.client.platform

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The system "open file" picker, ported from the `file_selector_android`
 * plugin the Flutter build used: `ACTION_OPEN_DOCUMENT` filtered by MIME
 * types, each picked document copied into a private cache directory so it
 * can be read by path.
 */
object FileSelector {
    /**
     * A picked document (the plugin's `FileResponse`). [path] is the private
     * copy; when copying failed, [bytes] holds the content instead.
     */
    class PickedFile(
        val path: String?,
        val mimeType: String?,
        val name: String?,
        val size: Int,
        val bytes: ByteArray?,
    )

    const val openFileRequestCode = 221
    const val openFilesRequestCode = 222

    private val pending = mutableMapOf<Int, CompletableDeferred<Pair<Int, Intent?>>>()

    /** Opens the picker for one document; `null` when cancelled. */
    suspend fun openFile(extensions: List<String> = emptyList(), mimeTypes: List<String> = emptyList()): PickedFile? {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        setMimeTypes(intent, extensions, mimeTypes)
        val (resultCode, data) = startForResult(intent, openFileRequestCode)
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val uri = data.data ?: throw Exception("Failed to retrieve data from opening file.")
        return toPickedFile(uri) ?: throw Exception("Failed to read file: $uri")
    }

    /** Opens the picker for several documents; empty when cancelled. */
    suspend fun openFiles(extensions: List<String> = emptyList(), mimeTypes: List<String> = emptyList()): List<PickedFile> {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        setMimeTypes(intent, extensions, mimeTypes)
        val (resultCode, data) = startForResult(intent, openFilesRequestCode)
        if (resultCode != Activity.RESULT_OK || data == null) return emptyList()
        // The plugin answered with the single `data` URI when present (its
        // first reply wins), otherwise with every clip item.
        val uri = data.data
        if (uri != null) {
            return listOf(toPickedFile(uri) ?: throw Exception("Failed to read file: $uri"))
        }
        val clipData = data.clipData ?: return emptyList()
        val files = ArrayList<PickedFile>(clipData.itemCount)
        for (i in 0 until clipData.itemCount) {
            val itemUri = clipData.getItemAt(i).uri
            files.add(toPickedFile(itemUri) ?: throw Exception("Failed to read file: $uri"))
        }
        return files
    }

    /** Routes a result from the activity; `true` when it belonged to the picker. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val waiter = pending.remove(requestCode) ?: return false
        waiter.complete(resultCode to data)
        return true
    }

    private suspend fun startForResult(intent: Intent, requestCode: Int): Pair<Int, Intent?> {
        val activity = GangChatPlatform.activity ?: throw Exception("No activity is available.")
        val waiter = CompletableDeferred<Pair<Int, Intent?>>()
        pending.remove(requestCode)?.complete(Activity.RESULT_CANCELED to null)
        pending[requestCode] = waiter
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, requestCode)
        } catch (error: Exception) {
            pending.remove(requestCode)
            throw error
        }
        return waiter.await()
    }

    private fun setMimeTypes(intent: Intent, extensions: List<String>, mimeTypes: List<String>) {
        val allMimeTypes = HashSet<String>()
        allMimeTypes.addAll(mimeTypes)
        allMimeTypes.addAll(tryConvertExtensionsToMimeTypes(extensions))
        when {
            allMimeTypes.isEmpty() -> intent.type = "*/*"
            allMimeTypes.size == 1 -> intent.type = allMimeTypes.first()
            else -> {
                intent.type = "*/*"
                intent.putExtra(Intent.EXTRA_MIME_TYPES, allMimeTypes.toTypedArray())
            }
        }
    }

    private fun tryConvertExtensionsToMimeTypes(extensions: List<String>): List<String> {
        if (extensions.isEmpty()) return emptyList()
        val mimeTypeMap = MimeTypeMap.getSingleton()
        return extensions.mapNotNullTo(HashSet()) { mimeTypeMap.getMimeTypeFromExtension(it) }.toList()
    }

    private suspend fun toPickedFile(uri: Uri): PickedFile? {
        val context = GangChatPlatform.context
        return withContext(Dispatchers.IO) { readPickedFile(context, uri) }
    }

    private fun readPickedFile(context: Context, uri: Uri): PickedFile? {
        val contentResolver = context.contentResolver
        var name: String? = null
        var size: Int? = null
        contentResolver.query(uri, null, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getInt(sizeIndex)
            }
        }
        val length = size ?: return null

        val path =
            try {
                pathFromCopyOfFileFromUri(context, uri)
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
        var bytes: ByteArray? = null
        if (path == null) {
            bytes = ByteArray(length)
            try {
                contentResolver.openInputStream(uri).use { input ->
                    DataInputStream(input ?: return null).readFully(bytes)
                }
            } catch (_: IOException) {
                return null
            }
        } else if (File(path).length() < length) {
            // The plugin read exactly SIZE bytes and failed on a short read.
            return null
        }
        return PickedFile(path, contentResolver.getType(uri), name, length, bytes)
    }

    private fun pathFromCopyOfFileFromUri(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream) { "The selected document cannot be opened." }
            val uuid = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString()
            val targetDirectory = File(context.cacheDir, uuid)
            targetDirectory.mkdir()
            targetDirectory.deleteOnExit()
            var fileName = getFileName(context, uri)
            val extension = getFileExtension(context, uri)
            if (fileName == null) {
                if (extension == null) {
                    throw IllegalStateException("No name nor extension found for file.")
                } else {
                    fileName = "file_selector$extension"
                }
            } else if (extension != null) {
                fileName = getBaseName(fileName) + extension
            }
            val outputFile = File(targetDirectory, fileName)
            if (!outputFile.canonicalPath.startsWith(targetDirectory.canonicalPath)) {
                throw IllegalArgumentException(
                    "Trying to open path outside of the expected directory. File: " +
                        outputFile.canonicalPath + " was expected to be within directory: " +
                        targetDirectory.canonicalPath + ".",
                )
            }
            FileOutputStream(outputFile).use { output -> inputStream.copyTo(output, 4 * 1024) }
            return outputFile.path
        }
    }

    private fun getFileExtension(context: Context, uri: Uri): String? {
        val extension =
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri))
            } else {
                val path = uri.path ?: return null
                MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(File(path)).toString())
            }
        if (extension.isNullOrEmpty()) return null
        return "." + sanitizeFilename(extension)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst() || cursor.columnCount < 1) return null
                return sanitizeFilename(cursor.getString(0))
            }
        return null
    }

    private fun getBaseName(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex < 0) fileName else fileName.substring(0, lastDotIndex)
    }

    private fun sanitizeFilename(displayName: String?): String? {
        if (displayName == null) return null
        var fileName = displayName.split("/").last()
        for (suspicious in listOf("..", "/")) fileName = fileName.replace(suspicious, "_")
        return fileName
    }
}

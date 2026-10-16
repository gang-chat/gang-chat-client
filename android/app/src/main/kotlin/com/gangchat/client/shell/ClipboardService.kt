package com.gangchat.client.shell

import com.gangchat.client.platform.Clipboard
import com.gangchat.client.platform.ClipboardData

class ClipboardImageFile(
    val bytes: ByteArray,
    val filename: String = "clipboard-image.png",
    val mimeType: String = "image/png",
)

/**
 * Clipboard access for the composer and message actions. On Android, text
 * goes through the system clipboard and images through
 * [AndroidSystemService]; file paths (a desktop feature) and the browser
 * paste hook are no-ops, as they were on Android in the Flutter build.
 */
class ClipboardService(
    val androidSystemService: AndroidSystemService = AndroidSystemService(),
) {
    /** Desktop only; always empty on Android. */
    suspend fun readFilePaths(): List<String> = emptyList()

    suspend fun readImageFile(): ClipboardImageFile? {
        if (androidSystemService.isSupported) {
            val image = androidSystemService.readClipboardImage()
            return if (image == null) {
                null
            } else {
                ClipboardImageFile(
                    bytes = image.bytes,
                    filename = image.filename,
                    mimeType = image.mimeType,
                )
            }
        }
        return null
    }

    /** Web only (browser paste events); does nothing on Android. */
    @Suppress("UNUSED_PARAMETER")
    fun setWebPasteHandler(handler: ((files: List<SelectedFile>, text: String?) -> Boolean)?) {
    }

    suspend fun readText(): String? {
        val data = Clipboard.getData(Clipboard.kTextPlain)
        val text = data?.text
        return if (text == null || text.isEmpty()) null else text
    }

    suspend fun writeText(text: String) {
        Clipboard.setData(ClipboardData(text = text))
    }

    /** Desktop only (pasting files into other apps); returns false on Android. */
    suspend fun writeFilePaths(paths: List<String>): Boolean = false

    /**
     * Writes raw image [bytes] (with the given [mimeType]) to the system
     * clipboard so it can be pasted into other apps. Returns true on success.
     */
    suspend fun writeImage(bytes: ByteArray, mimeType: String = "image/png"): Boolean {
        if (androidSystemService.isSupported) {
            return androidSystemService.writeClipboardImage(bytes = bytes, mimeType = mimeType)
        }
        return false
    }
}

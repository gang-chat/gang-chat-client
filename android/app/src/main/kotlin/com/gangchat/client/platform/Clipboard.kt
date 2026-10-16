package com.gangchat.client.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Flutter's `ClipboardData`. */
data class ClipboardData(val text: String?)

/**
 * Flutter's `Clipboard` (text only), with the Flutter engine's Android
 * behaviour: a non-text item is coerced to text when it is a `content://`
 * URI that offers a text representation; anything else reads as `null`.
 */
object Clipboard {
    const val kTextPlain = "text/plain"

    suspend fun getData(format: String = kTextPlain): ClipboardData? {
        if (format != kTextPlain) return null
        val context = GangChatPlatform.context
        val text = withContext(Dispatchers.IO) { readText(context) } ?: return null
        return ClipboardData(text.toString())
    }

    suspend fun setData(data: ClipboardData) {
        val context = GangChatPlatform.context
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text label?", data.text))
    }

    private fun readText(context: Context): CharSequence? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return null
        var itemText: CharSequence? = null
        try {
            val clip = clipboard.primaryClip ?: return null
            val item = clip.getItemAt(0)
            // First, try getting clipboard data as text; no further processing
            // required if so.
            itemText = item.text
            if (itemText == null) {
                // Clipboard data does not contain text, so check whether or not it
                // contains a URI to extract text from.
                val itemUri = item.uri ?: return null
                // Will only try to extract text from URI if it has the content scheme.
                if (itemUri.scheme != "content") return null
                val assetFileDescriptor =
                    context.contentResolver.openTypedAssetFileDescriptor(itemUri, "text/*", null)
                itemText = item.coerceToText(context)
                assetFileDescriptor?.close()
            }
            return itemText
        } catch (_: SecurityException) {
            return null
        } catch (_: FileNotFoundException) {
            return null
        } catch (_: IOException) {
            return itemText
        }
    }
}

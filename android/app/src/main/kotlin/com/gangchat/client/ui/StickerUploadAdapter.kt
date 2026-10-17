package com.gangchat.client.ui

import android.graphics.BitmapFactory
import com.gangchat.client.app.StickerImageDimensions
import com.gangchat.client.app.StickerUploadSource
import com.gangchat.client.shell.SelectedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun stickerUploadSourcesFromSelectedFiles(files: Iterable<SelectedFile>): List<StickerUploadSource> =
    files.map { file -> StickerUploadSource(filename = file.name, readBytes = { file.readAsBytes() }) }

/**
 * The pixel size of the image's first frame. Dart decoded it with the engine's
 * image codec; this reads the header with `BitmapFactory`, which accepts the
 * same formats (PNG, JPEG, GIF, WebP, BMP, ICO) and throws for anything else.
 */
suspend fun decodeStickerImageDimensions(bytes: ByteArray): StickerImageDimensions =
    withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IllegalArgumentException("Invalid image data")
        }
        StickerImageDimensions(width = options.outWidth, height = options.outHeight)
    }

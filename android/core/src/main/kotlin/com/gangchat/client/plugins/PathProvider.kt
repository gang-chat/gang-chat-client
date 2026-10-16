package com.gangchat.client.plugins

import java.io.File

/**
 * Kotlin facade for Flutter's `path_provider`. The Android app installs real
 * directories at startup ([install]); tests install temporary ones.
 */
object PathProvider {
    /** Directory layout supplied by the host platform. */
    class Directories(
        val applicationSupport: File,
        val applicationDocuments: File,
        val applicationCache: File,
        val temporary: File,
        val downloads: File?,
    )

    @Volatile
    private var directories: Directories? = null

    fun install(directories: Directories) {
        this.directories = directories
    }

    private fun require(): Directories =
        directories ?: throw IllegalStateException("PathProvider has no platform directories installed")

    suspend fun getApplicationSupportDirectory(): File = require().applicationSupport.also { it.mkdirs() }

    suspend fun getApplicationDocumentsDirectory(): File = require().applicationDocuments.also { it.mkdirs() }

    suspend fun getApplicationCacheDirectory(): File = require().applicationCache.also { it.mkdirs() }

    suspend fun getTemporaryDirectory(): File = require().temporary.also { it.mkdirs() }

    suspend fun getDownloadsDirectory(): File? = require().downloads
}

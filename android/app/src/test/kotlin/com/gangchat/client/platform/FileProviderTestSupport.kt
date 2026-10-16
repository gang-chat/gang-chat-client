package com.gangchat.client.platform

import androidx.core.content.FileProvider

/**
 * FileProvider caches its configured roots in a static map, but Robolectric
 * gives every test its own data directory. Tests that create FileProvider
 * URIs call this first so the roots point at the current test's directories.
 */
fun resetFileProviderPathCache() {
    val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null)
    synchronized(cache) { (cache as MutableMap<*, *>).clear() }
}

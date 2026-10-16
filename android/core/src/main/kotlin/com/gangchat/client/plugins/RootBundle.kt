package com.gangchat.client.plugins

/**
 * Kotlin facade for Flutter's `rootBundle` (bundled assets). Paths are the
 * Flutter asset keys (`assets/config/app_config.json`); the Android app serves
 * them from the APK assets, which contain the repository's `assets/` folder.
 */
object rootBundle {
    /** Reads raw bytes of an asset; throws when it does not exist. */
    fun interface Loader {
        suspend fun load(key: String): ByteArray
    }

    @Volatile
    private var loader: Loader = Loader { key -> throw IllegalStateException("Unable to load asset: \"$key\".") }

    fun install(platformLoader: Loader) {
        loader = platformLoader
    }

    /** Test helper: serves [assets] from memory. */
    fun setMockAssets(assets: Map<String, String>) {
        loader = Loader { key -> assets[key]?.toByteArray(Charsets.UTF_8) ?: throw IllegalStateException("Unable to load asset: \"$key\".") }
    }

    suspend fun load(key: String): ByteArray = loader.load(key)

    suspend fun loadString(key: String): String = String(load(key), Charsets.UTF_8)
}

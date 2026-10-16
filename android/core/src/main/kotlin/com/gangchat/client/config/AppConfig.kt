package com.gangchat.client.config

import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.plugins.rootBundle

/**
 * Static, build-time URL configuration for the Gang Chat client.
 *
 * Values come from three sources, in priority order:
 *   1. Build-time defines (`GANG_API_BASE_URL`, ... environment variables at
 *      Gradle build time, exposed through [environment]; Flutter's
 *      `--dart-define`).
 *   2. `assets/config/app_config.json`, the committed default.
 *   3. The hard-coded fallbacks in this file (last-resort dev defaults).
 *
 * The asset is loaded once at app startup via [AppConfig.load].
 */
class AppConfig(
    /** Base URL of the REST API, including the `/api/v1` prefix. */
    val apiBaseUrl: String,
    /**
     * Origin used to resolve relative asset URLs (avatars, attachments, ...).
     * Should not contain a trailing slash.
     */
    val assetBaseUrl: String,
    /**
     * Public S3-compatible bucket URL used to discover and download releases.
     * Should not contain a trailing slash.
     */
    val releaseBucketUrl: String,
) {
    /**
     * Resolve [path] (which may be relative or absolute) against [assetBaseUrl].
     * Returns `null` if [path] is null or empty.
     */
    fun resolveAssetUrl(path: String?): String? {
        if (path == null || path.isEmpty()) return null
        val normalizedBase =
            if (assetBaseUrl.endsWith("/")) assetBaseUrl.substring(0, assetBaseUrl.length - 1) else assetBaseUrl

        val uri = Uri.tryParse(path)
        if (uri != null && (uri.scheme == "http" || uri.scheme == "https")) {
            if (uri.path.startsWith("/assets/")) {
                return "$normalizedBase${uri.path}${if (uri.hasQuery) "?${uri.query}" else ""}"
            }
            return path
        }
        if (path.lowercase().startsWith("data:")) return path

        val suffix = if (path.startsWith("/")) path else "/$path"
        return "$normalizedBase$suffix"
    }

    companion object {
        private const val _fallbackApiBaseUrl = "http://127.0.0.1:21116/api/v1"
        private const val _fallbackAssetBaseUrl = "http://127.0.0.1:21116"
        private const val _fallbackReleaseBucketUrl = "https://os.ky-z.com:9000/gang-chat"

        private const val _assetPath = "assets/config/app_config.json"

        /**
         * Build-time defines (Flutter's `String.fromEnvironment`). The app sets
         * these from `BuildConfig` before calling [load].
         */
        @Volatile
        var environment: Map<String, String> = emptyMap()

        /**
         * Compile-time fallback config. Useful for tests and as a last-resort
         * default when [load] cannot be awaited (e.g. synchronous widget creation).
         */
        fun defaults(): AppConfig = AppConfig(_fallbackApiBaseUrl, _fallbackAssetBaseUrl, _fallbackReleaseBucketUrl)

        /**
         * Load the configuration. Failures are tolerated; the loader falls back to
         * build-time defines, then to the hard-coded defaults.
         */
        suspend fun load(): AppConfig {
            var assetValues: Map<String, Any?> = emptyMap()
            try {
                val raw = rootBundle.loadString(_assetPath)
                val decoded = jsonDecode(raw)
                @Suppress("UNCHECKED_CAST")
                if (decoded is Map<*, *>) assetValues = decoded as Map<String, Any?>
            } catch (_: Exception) {
                // The asset is optional. Fall back to defines + defaults.
            }

            fun pick(key: String, fallback: String, defineValue: String): String {
                if (defineValue.isNotEmpty()) return defineValue
                val value = assetValues[key]
                if (value is String && value.isNotEmpty()) return value
                return fallback
            }

            return AppConfig(
                apiBaseUrl = pick("api_base_url", _fallbackApiBaseUrl, environment["GANG_API_BASE_URL"].orEmpty()),
                assetBaseUrl = pick("asset_base_url", _fallbackAssetBaseUrl, environment["GANG_ASSET_BASE_URL"].orEmpty()),
                releaseBucketUrl =
                    pick("release_bucket_url", _fallbackReleaseBucketUrl, environment["GANG_RELEASE_BUCKET_URL"].orEmpty()),
            )
        }
    }
}

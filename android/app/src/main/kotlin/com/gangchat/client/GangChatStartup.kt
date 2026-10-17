package com.gangchat.client

import com.gangchat.client.config.AppConfig
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.OkHttpClientAdapter
import com.gangchat.client.shell.AppOrientationController
import com.gangchat.client.shell.LocalPreferencesMigration
import kotlinx.coroutines.CancellationException

/**
 * The Android part of `lib/main.dart`: what runs before the UI root is
 * installed, in the same order. `MainActivity` runs it each time it is
 * created, as the Flutter engine ran `main()` for each new activity.
 * Process-wide installs (plugin stores, directories, assets, notification
 * channels) happen earlier, in [GangChatApplication].
 */
object GangChatStartup {
    suspend fun main(orientationController: AppOrientationController = AppOrientationController()): AppConfig {
        // Run before AppConfig, auth history, and audio settings read
        // SharedPreferences. This is a one-time, conflict-aware migration from
        // the historical Windows ProductName (`client`) to the stable Gang Chat
        // domain.
        try {
            migrateLocalPreferences()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Local migration is best-effort and must never block application launch.
        }

        // Android phones launch portrait-only. Large-screen Android devices keep
        // the platform's normal rotation behavior and naturally use the wide app
        // layout.
        try {
            orientationController.restoreDefaultOrientation()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // A platform orientation failure must not prevent the app from starting.
        }

        // Configure platform audio before the WebRTC factory is created.
        initializeAndroidWebRtcAudio()

        val config = AppConfig.load()
        _installConfiguredHostProxyBypass(config)
        return config
    }

    /**
     * `LocalPreferencesMigration().migrate()`
     * (`shell/local_preferences_migration.dart`): a no-op on Android, where
     * Dart migrates only the Windows preference file.
     */
    suspend fun migrateLocalPreferences() {
        LocalPreferencesMigration().migrate()
    }

    /**
     * `initializeAndroidWebRtcAudio()` (`live/android_audio_initialization.dart`):
     * configures the WebRTC audio attributes and starts the audio switch,
     * before any room is created.
     */
    suspend fun initializeAndroidWebRtcAudio() {
        com.gangchat.client.live.initializeAndroidWebRtcAudio()
    }

    /**
     * The app's own API, asset and release hosts always connect directly,
     * bypassing any system proxy.
     */
    fun _installConfiguredHostProxyBypass(config: AppConfig) {
        val directHosts =
            linkedSetOf(
                _hostFromUrl(config.apiBaseUrl),
                _hostFromUrl(config.assetBaseUrl),
                _hostFromUrl(config.releaseBucketUrl),
            ).apply { remove("") }
        if (directHosts.isEmpty()) return
        OkHttpClientAdapter.directHosts = directHosts
    }

    private fun _hostFromUrl(value: String): String {
        return Uri.tryParse(value)?.host?.lowercase() ?: ""
    }
}

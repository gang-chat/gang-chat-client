package com.gangchat.client

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import com.gangchat.client.config.AppConfig
import com.gangchat.client.foundation.debugPrintSink
import com.gangchat.client.foundation.kDebugMode
import com.gangchat.client.platform.AndroidMusicPreviewPlayer
import com.gangchat.client.platform.AndroidSystemBridge
import com.gangchat.client.platform.AndroidUpdateInstaller
import com.gangchat.client.platform.FlutterSecureStorageStore
import com.gangchat.client.platform.FlutterSharedPreferencesStore
import com.gangchat.client.platform.GangChatNotifications
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.plugins.FlutterSecureStorage
import com.gangchat.client.plugins.PathProvider
import com.gangchat.client.plugins.SharedPreferences
import com.gangchat.client.plugins.rootBundle
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Process start: installs the real implementations behind the `:core`
 * runtime and plugin facades (what the Flutter engine and its plugins
 * provided), and the platform services. The per-launch part of `main.dart`
 * runs in `MainActivity` ([GangChatStartup]).
 *
 * FCM needs no setup here: when the build includes google-services.json,
 * Firebase initializes itself at process start, and tokens and messages reach
 * `GangChatFirebaseMessagingService`.
 */
class GangChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        kDebugMode = BuildConfig.DEBUG
        debugPrintSink = ::logToLogcat
        AppConfig.environment =
            mapOf(
                "GANG_API_BASE_URL" to BuildConfig.GANG_API_BASE_URL,
                "GANG_ASSET_BASE_URL" to BuildConfig.GANG_ASSET_BASE_URL,
                "GANG_RELEASE_BUCKET_URL" to BuildConfig.GANG_RELEASE_BUCKET_URL,
            )

        // path_provider_android's directories.
        PathProvider.install(
            PathProvider.Directories(
                applicationSupport = filesDir,
                applicationDocuments = getDir("flutter", Context.MODE_PRIVATE),
                applicationCache = cacheDir,
                temporary = cacheDir,
                downloads = getExternalFilesDirs(Environment.DIRECTORY_DOWNLOADS).filterNotNull().firstOrNull(),
            ),
        )
        rootBundle.install(
            rootBundle.Loader { key ->
                withContext(Dispatchers.IO) {
                    try {
                        assets.open(apkAssetPath(key)).use { it.readBytes() }
                    } catch (_: FileNotFoundException) {
                        throw IllegalStateException("Unable to load asset: \"$key\".")
                    }
                }
            },
        )
        SharedPreferences.installStore(FlutterSharedPreferencesStore(this))
        FlutterSecureStorage.installStore(FlutterSecureStorageStore(this))

        GangChatPlatform.install(this)
        AndroidSystemBridge.install(AndroidSystemBridge(this))
        AndroidUpdateInstaller.install(AndroidUpdateInstaller(this))
        AndroidMusicPreviewPlayer.install(AndroidMusicPreviewPlayer())
        GangChatNotifications.ensureChannels(this)
    }

    companion object {
        private const val logTag = "GangChat"

        /**
         * Flutter asset keys (`assets/config/app_config.json`) map onto the APK
         * assets, which are the repository's `assets/` folder itself.
         */
        fun apkAssetPath(key: String): String = key.removePrefix("assets/")

        /** debugPrint → logcat, split to stay under logcat's line limit. */
        private fun logToLogcat(message: String) {
            if (message.length <= 4000) {
                Log.i(logTag, message)
                return
            }
            message.chunked(4000).forEach { Log.i(logTag, it) }
        }
    }
}

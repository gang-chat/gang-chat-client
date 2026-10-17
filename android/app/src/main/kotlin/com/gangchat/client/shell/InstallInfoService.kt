package com.gangchat.client.shell

import com.gangchat.client.app.gangChatClientInstallInfoFileName
import kotlinx.coroutines.CancellationException

open class InstallInfoService(
    // PORT: only the desktop branch read this file (next to the executable);
    // kept so callers construct the service as in Dart.
    val fileName: String = gangChatClientInstallInfoFileName,
    val androidSystemService: AndroidSystemService = AndroidSystemService(),
) {
    open suspend fun readInstalledAt(): String? {
        try {
            if (androidSystemService.isSupported) {
                return androidSystemService.installedAt()
            }
            // PORT: the desktop install-info file next to the executable does
            // not exist on Android; without the platform layer (plain JVM
            // tests) there is no install time.
            return null
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return null
        }
    }
}

package com.gangchat.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gangchat.client.config.AppConfig

/**
 * THE entry point for the app's UI root (`shell/gang_app.dart`'s `GangApp`).
 *
 * `MainActivity` calls this after [GangChatStartup.main] and installs the
 * returned composable as its content; the launch screen stays up until then,
 * like the Flutter splash until the first frame.
 *
 * When `GangApp` is ported, replace the body with the rest of `main()`:
 * ```
 * val tokenStore = TokenStore()
 * val hasStoredSession = tokenStore.readRefreshToken()?.isNotEmpty() ?: false
 * return { GangApp(config = config, tokenStore = tokenStore, startsAuthenticated = hasStoredSession) }
 * ```
 */
@Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
suspend fun prepareGangApp(config: AppConfig): @Composable () -> Unit {
    return { GangAppPlaceholder() }
}

/** Shown until `GangApp` is ported: the launch background colour. */
@Composable
private fun GangAppPlaceholder() {
    Box(Modifier.fillMaxSize().background(Color(0xFF181C24)))
}

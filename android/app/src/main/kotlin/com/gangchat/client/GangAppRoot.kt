package com.gangchat.client

import androidx.compose.runtime.Composable
import com.gangchat.client.auth.TokenStore
import com.gangchat.client.config.AppConfig
import com.gangchat.client.shell.GangApp

/**
 * THE entry point for the app's UI root: the rest of `main()` after
 * [GangChatStartup.main], which returns the `runApp(GangApp(...))` content.
 *
 * `MainActivity` installs the returned composable as its content and keeps
 * its launch screen up until [showInitialWindow] is called: once the auth
 * gate has decided between the login page and the restored session (Dart
 * kept the desktop window hidden until then), or 4 s into a stored-session
 * restore that has not finished, when the loading page shows instead.
 */
suspend fun prepareGangApp(config: AppConfig, showInitialWindow: () -> Unit): @Composable () -> Unit {
    // Pre-read the refresh token so the auth gate knows whether a session is
    // being restored (loading page) or the login page can show at once.
    val tokenStore = TokenStore()
    val hasStoredSession = tokenStore.readRefreshToken()?.isNotEmpty() ?: false
    return {
        GangApp(
            config = config,
            tokenStore = tokenStore,
            startsAuthenticated = hasStoredSession,
            showInitialWindow = showInitialWindow,
        )
    }
}

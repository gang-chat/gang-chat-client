package com.gangchat.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.gangchat.client.config.AppConfig
import com.gangchat.client.widgets.BuildContext

/** The active [AppConfig]; [AppConfig.defaults] when no [AppConfigScope] provides one. */
val LocalAppConfig = staticCompositionLocalOf { AppConfig.defaults() }

/** Inherited scope that exposes the active [AppConfig] to descendants. */
@Composable
fun AppConfigScope(config: AppConfig, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppConfig provides config, content = content)
}

/** Dart's static `AppConfigScope.of(context)`. */
object AppConfigScope {
    fun of(context: BuildContext): AppConfig = context[LocalAppConfig]
}

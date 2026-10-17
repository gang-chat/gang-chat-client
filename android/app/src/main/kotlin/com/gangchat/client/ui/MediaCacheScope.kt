package com.gangchat.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.widgets.BuildContext

private val _fallbackMediaCacheController: MediaCacheController by lazy { MediaCacheController() }

/** Dart's `MediaCacheScope` inherited widget (null outside any scope). */
val LocalMediaCache = staticCompositionLocalOf<MediaCacheController?> { null }

/** Provides [cache] to the subtree (Dart's `MediaCacheScope(cache:, child:)`). */
@Composable
fun MediaCacheScope(cache: MediaCacheController, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMediaCache provides cache, content = content)
}

/** Dart's static `MediaCacheScope.of` / `maybeOf`. */
object MediaCacheScope {
    fun of(context: BuildContext): MediaCacheController = maybeOf(context) ?: _fallbackMediaCacheController

    fun maybeOf(context: BuildContext): MediaCacheController? = context[LocalMediaCache]

    /** Composition-time [of]. */
    val current: MediaCacheController
        @Composable get() = LocalMediaCache.current ?: _fallbackMediaCacheController
}

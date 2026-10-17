package com.gangchat.client.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.MediaQuery

private val LocalHomeAdaptiveLayout = compositionLocalOf<Boolean?> { null }

/**
 * Shares one width-driven compact/wide decision across the home subtree.
 * Platform-specific controls remain responsible for their own OS behavior.
 * Android phones get the compact layout, tablets the wide one.
 */
@Composable
fun HomeAdaptiveLayout(compact: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHomeAdaptiveLayout provides compact, content = content)
}

/** Dart's static `HomeAdaptiveLayout.usesCompactLayout`. */
object HomeAdaptiveLayout {
    fun usesCompactLayout(context: BuildContext): Boolean =
        context[LocalHomeAdaptiveLayout] ?: (MediaQuery.sizeOf(context).width < narrowBreakpoint)
}

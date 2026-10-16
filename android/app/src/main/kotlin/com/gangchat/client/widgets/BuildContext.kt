package com.gangchat.client.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Flutter's `BuildContext` for ported code.
 *
 * Compose reads ambient values (`CompositionLocal`s) only during
 * composition, but Flutter code also uses its context from event handlers,
 * `initState` and async callbacks (`showDialog(context: context)`,
 * `Navigator.of(context).pop()`, `if (!context.mounted) return`). A
 * [BuildContext] is a snapshot of the composition's locals, refreshed on every
 * recomposition, that can be used anywhere on the main thread:
 *
 * ```
 * val navigator = Navigator.of(context)        // context[LocalNavigator]
 * val size = MediaQuery.sizeOf(context)        // context[LocalMediaQuery].size
 * val config = AppConfigScope.of(context)      // context[LocalAppConfig]
 * ```
 *
 * Get one with [rememberBuildContext]; `ViewState` exposes its own as
 * `context`.
 */
class BuildContext internal constructor() {
    internal var locals: CompositionLocalMap? = null

    /** Flutter's `context.mounted`. */
    var mounted: Boolean = true
        internal set

    /** Reads an ambient value (Flutter's `dependOnInheritedWidgetOfExactType`). */
    operator fun <T> get(local: CompositionLocal<T>): T {
        val map = locals ?: throw IllegalStateException("BuildContext used before its first composition")
        return map[local]
    }

    /** The Android [Context] of the hosting activity. */
    val androidContext: Context get() = this[LocalContext]
}

/** Returns this composition's [BuildContext], kept current across recompositions. */
@Composable
fun rememberBuildContext(): BuildContext {
    val context = remember { BuildContext() }
    context.locals = currentComposer.currentCompositionLocalMap
    DisposableEffect(context) {
        context.mounted = true
        onDispose { context.mounted = false }
    }
    return context
}

package com.gangchat.client.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Flutter's `OverlayPortalController`. */
class OverlayPortalController {
    internal var slot: OverlayPortalSlot? = null

    /** Flutter's `isShowing`. */
    val isShowing: Boolean get() = slot?.showing == true

    fun show() {
        slot?.show()
    }

    fun hide() {
        slot?.hide()
    }
}

/** One [OverlayPortal]'s overlay child, rendered by `Overlay` above the portal's entry. */
internal class OverlayPortalSlot {
    private var entry: OverlayEntry? = null
    var locals: CompositionLocalContext? by mutableStateOf(null)
    var builder: (@Composable (BuildContext) -> Unit)? by mutableStateOf(null)
    var showing = false
        private set

    fun show() {
        showing = true
        val target = entry ?: return
        // Like Flutter, the most recently shown portal child paints on top.
        target.portals.remove(this)
        target.portals.add(this)
    }

    fun hide() {
        showing = false
        entry?.portals?.remove(this)
    }

    fun attach(target: OverlayEntry?) {
        if (entry === target) return
        entry?.portals?.remove(this)
        entry = target
        if (showing) target?.portals?.add(this)
    }

    fun detach() {
        entry?.portals?.remove(this)
        entry = null
    }

    @Composable
    fun Content() {
        val locals = locals ?: return
        val builder = builder ?: return
        CompositionLocalProvider(locals) {
            val context = rememberBuildContext()
            builder(context)
        }
    }
}

/**
 * Flutter's `OverlayPortal`: [child] stays in place while
 * [overlayChildBuilder] (shown with [controller]) is rendered in the
 * enclosing `Overlay`, right above the overlay entry that contains the
 * portal and below any entry inserted later (dialogs and routes pushed
 * afterwards cover it). The overlay child fills the overlay, and — as in
 * Flutter, where it builds with the portal's context — it sees the portal's
 * composition locals (theme, scopes, media query).
 */
@Composable
fun OverlayPortal(
    controller: OverlayPortalController,
    overlayChildBuilder: @Composable (BuildContext) -> Unit,
    child: @Composable () -> Unit,
) {
    val slot = remember { OverlayPortalSlot() }
    val entry = LocalOverlayEntry.current
    slot.locals = currentCompositionLocalContext
    slot.builder = overlayChildBuilder
    controller.slot = slot
    DisposableEffect(slot, entry) {
        slot.attach(entry)
        onDispose { slot.detach() }
    }
    child()
}

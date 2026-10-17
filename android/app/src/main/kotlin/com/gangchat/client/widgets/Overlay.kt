package com.gangchat.client.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Flutter's `OverlayEntry`: a full-size layer in an [OverlayState].
 * [builder] receives the entry's own [BuildContext].
 */
class OverlayEntry(
    opaque: Boolean = false,
    maintainState: Boolean = false,
    val builder: @Composable (BuildContext) -> Unit,
) {
    var opaque: Boolean by mutableStateOf(opaque)
    var maintainState: Boolean by mutableStateOf(maintainState)
    internal var overlay: OverlayState? = null
    internal val revision = mutableIntStateOf(0)

    /** [OverlayPortal] children shown above this entry, bottom to top. */
    internal val portals = mutableStateListOf<OverlayPortalSlot>()

    val mounted: Boolean get() = overlay != null

    /** Removes this entry from its overlay. */
    fun remove() {
        overlay?.removeEntry(this)
    }

    /** Rebuilds the entry (Flutter's `markNeedsBuild`). */
    fun markNeedsBuild() {
        revision.intValue += 1
    }
}

/** Flutter's `OverlayState`. */
class OverlayState {
    internal val entries = mutableStateListOf<OverlayEntry>()

    fun insert(entry: OverlayEntry, below: OverlayEntry? = null, above: OverlayEntry? = null) {
        insertAll(listOf(entry), below, above)
    }

    fun insertAll(newEntries: List<OverlayEntry>, below: OverlayEntry? = null, above: OverlayEntry? = null) {
        if (newEntries.isEmpty()) return
        val index =
            when {
                below != null && entries.contains(below) -> entries.indexOf(below)
                above != null && entries.contains(above) -> entries.indexOf(above) + 1
                else -> entries.size
            }
        for (entry in newEntries) entry.overlay = this
        entries.addAll(index, newEntries)
    }

    internal fun removeEntry(entry: OverlayEntry) {
        entries.remove(entry)
        entry.overlay = null
    }

    companion object {
        /** Flutter's `Overlay.of(context)`. */
        fun of(context: BuildContext, rootOverlay: Boolean = false): OverlayState =
            context[LocalOverlay] ?: throw IllegalStateException("No Overlay widget found")

        fun maybeOf(context: BuildContext): OverlayState? = context[LocalOverlay]
    }
}

val LocalOverlay = staticCompositionLocalOf<OverlayState?> { null }

/** The [OverlayEntry] whose subtree this is (where an [OverlayPortal] shows its child). */
val LocalOverlayEntry = staticCompositionLocalOf<OverlayEntry?> { null }

/**
 * Flutter's `Overlay`: stacks the entries bottom to top. Entries below the
 * topmost opaque entry are not built unless they `maintainState`, in which
 * case they stay composed but are neither drawn nor hit-tested (`Offstage`).
 */
@Composable
fun Overlay(state: OverlayState, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalOverlay provides state) {
        Box(modifier.fillMaxSize()) {
            val entries = state.entries.toList()
            val onstage = BooleanArray(entries.size)
            var covered = false
            for (i in entries.indices.reversed()) {
                onstage[i] = !covered
                if (entries[i].opaque) covered = true
            }
            for ((index, entry) in entries.withIndex()) {
                if (!onstage[index] && !entry.maintainState) continue
                key(entry) {
                    Box(Modifier.fillMaxSize().then(if (onstage[index]) Modifier else Modifier.offstage())) {
                        entry.revision.intValue
                        CompositionLocalProvider(LocalOverlayEntry provides entry) {
                            val context = rememberBuildContext()
                            entry.builder(context)
                        }
                        // Like Flutter's OverlayPortal, portal children paint
                        // right above their entry, below any later entry.
                        for (slot in entry.portals.toList()) {
                            key(slot) { Box(Modifier.matchParentSize()) { slot.Content() } }
                        }
                    }
                }
            }
        }
    }
}

/** Flutter's `Offstage`: measured, but neither placed, drawn nor hit-tested. */
fun Modifier.offstage(): Modifier =
    this.layout { measurable, constraints ->
        measurable.measure(constraints)
        layout(0, 0) {}
    }

/**
 * Keeps the layout size but neither places, draws nor hit-tests the content.
 * Use it for Flutter's `IgnorePointer` over interactive content that is fully
 * hidden, so taps reach whatever is underneath ([ignorePointer] would absorb
 * them instead).
 */
fun Modifier.unplaced(): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {}
    }

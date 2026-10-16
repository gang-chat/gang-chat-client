package com.gangchat.client.widgets

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize

/**
 * Stands in for `context.findRenderObject() as RenderBox` when Flutter code
 * positions a popup next to a widget: attach with [Modifier.renderBox] and
 * read geometry in dp, relative to the window root (which is also the overlay
 * and dialog coordinate space).
 */
class RenderBoxHandle {
    internal var coordinates: LayoutCoordinates? = null
    internal var density: Density? = null

    val hasSize: Boolean get() = coordinates?.isAttached == true

    val size: DpSize
        get() {
            val coords = coordinates ?: return DpSize.Zero
            val d = density ?: return DpSize.Zero
            return with(d) { DpSize(coords.size.width.toDp(), coords.size.height.toDp()) }
        }

    /** Flutter's `localToGlobal(offset)` (offset in dp within this box). */
    fun localToGlobal(offset: DpOffset = DpOffset.Zero): DpOffset {
        val coords = coordinates ?: return offset
        val d = density ?: return offset
        if (!coords.isAttached) return offset
        val origin = coords.positionInRoot()
        return with(d) { DpOffset(origin.x.toDp() + offset.x, origin.y.toDp() + offset.y) }
    }

    /** The box's rectangle in window coordinates (dp). */
    val globalRect: DpRect
        get() {
            val topLeft = localToGlobal()
            val s = size
            return DpRect(topLeft.x, topLeft.y, topLeft.x + s.width, topLeft.y + s.height)
        }
}

/** Records this layout's coordinates into [handle]. */
fun Modifier.renderBox(handle: RenderBoxHandle): Modifier =
    composed {
        handle.density = LocalDensity.current
        this.onGloballyPositioned { handle.coordinates = it }
    }

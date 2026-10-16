package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.OverlayEntry
import com.gangchat.client.widgets.OverlayState
import com.gangchat.client.widgets.RenderBoxHandle
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TapRegion
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.renderBox
import kotlin.math.max

/** A raised popover panel (rounded, bordered, with the popover shadow). */
@Composable
fun AnchoredPanel(
    modifier: Modifier = Modifier,
    width: Dp = 320.dp,
    backgroundColor: Color = UiColors.surfaceRaised,
    borderColor: Color = UiColors.borderStrong,
    child: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(UiRadii.lg)
    Box(
        modifier
            .width(width)
            .boxShadow(UiShadows.popover, shape)
            .decoration(backgroundColor, shape, borderColor),
        propagateMinConstraints = true,
    ) {
        child()
    }
}

/**
 * An [anchor] that opens an [AnchoredPanel] next to itself in the overlay
 * (below it, or above when only that fits). A tap outside both closes it.
 * [anchor] receives whether the panel is open and a toggle; [panelBuilder]
 * (preferred over [panel]) receives a close callback.
 */
@Composable
fun AnchoredPanelAnchor(
    anchor: @Composable (context: BuildContext, open: Boolean, toggle: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    panel: (@Composable () -> Unit)? = null,
    panelBuilder: (@Composable (context: BuildContext, close: () -> Unit) -> Unit)? = null,
    width: Dp = 320.dp,
    gap: Dp = 8.dp,
    backgroundColor: Color = UiColors.surfaceRaised,
    borderColor: Color = UiColors.borderStrong,
) = StatefulWidget(AnchoredPanelAnchorWidget(anchor, panel, panelBuilder, width, gap, backgroundColor, borderColor, modifier)) {
    _AnchoredPanelAnchorState()
}

class AnchoredPanelAnchorWidget(
    val anchor: @Composable (context: BuildContext, open: Boolean, toggle: () -> Unit) -> Unit,
    val panel: (@Composable () -> Unit)?,
    val panelBuilder: (@Composable (context: BuildContext, close: () -> Unit) -> Unit)?,
    val width: Dp,
    val gap: Dp,
    val backgroundColor: Color,
    val borderColor: Color,
    val modifier: Modifier,
)

private class _AnchoredPanelAnchorState : ViewState<AnchoredPanelAnchorWidget>() {
    private val _anchorKey = RenderBoxHandle()
    private val _tapRegionGroup = Any()
    private var _entry: OverlayEntry? = null

    // The overlay child reads the latest configuration (Flutter's OverlayPortal
    // rebuilds its overlay child with the anchor).
    private val _current = mutableStateOf<AnchoredPanelAnchorWidget?>(null)

    private val _open: Boolean get() = _entry != null

    private fun _toggle() = if (_open) _close() else _openPanel()

    private fun _openPanel() {
        if (_entry != null) return
        val overlay = OverlayState.maybeOf(context) ?: return
        val entry = OverlayEntry { overlayContext -> _OverlayChild(overlayContext) }
        _entry = entry
        overlay.insert(entry)
        setState()
    }

    private fun _close() {
        val entry = _entry ?: return
        _entry = null
        entry.remove()
        if (mounted) setState()
    }

    override fun dispose() {
        _entry?.remove()
        _entry = null
    }

    @Composable
    private fun _OverlayChild(overlayContext: BuildContext) {
        val current = _current.value ?: return
        if (!_anchorKey.hasSize) return
        val anchorRect = _anchorKey.globalRect
        Layout(
            content = {
                TapRegion(groupId = _tapRegionGroup) {
                    AnchoredPanel(width = current.width, backgroundColor = current.backgroundColor, borderColor = current.borderColor) {
                        val builder = current.panelBuilder
                        when {
                            builder != null -> builder(overlayContext, ::_close)
                            current.panel != null -> current.panel.invoke()
                            else -> Box(Modifier)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val panelWidthPx = current.width.roundToPx().coerceAtMost(constraints.maxWidth)
            val placeable =
                measurables.first().measure(
                    constraints.copy(minWidth = panelWidthPx, maxWidth = panelWidthPx, minHeight = 0),
                )
            layout(constraints.maxWidth, constraints.maxHeight) {
                val rect = DpRect(anchorRect.left, anchorRect.top, anchorRect.right, anchorRect.bottom)
                val gapPx = current.gap.toPx()
                val left = rect.left.toPx()
                val right = rect.right.toPx()
                val top = rect.top.toPx()
                val bottom = rect.bottom.toPx()
                val overlayHeight = constraints.maxHeight.toFloat()
                val placeBelow = _shouldPlaceBelow(top, bottom, overlayHeight, placeable.height.toFloat(), gapPx)
                val maxLeft = max(0f, constraints.maxWidth - placeable.width.toFloat())
                val x = ((left + right) / 2 - placeable.width / 2f).coerceIn(0f, maxLeft)
                val maxTop = max(0f, overlayHeight - placeable.height)
                val y =
                    if (placeBelow) {
                        (bottom + gapPx).coerceIn(0f, maxTop)
                    } else {
                        (top - gapPx - placeable.height).coerceIn(0f, maxTop)
                    }
                placeable.place(x.toInt(), y.toInt())
            }
        }
    }

    @Composable
    override fun build() {
        SideEffect { _current.value = widget }
        TapRegion(modifier = widget.modifier, groupId = _tapRegionGroup, onTapOutside = { _close() }) {
            Box(Modifier.renderBox(_anchorKey), propagateMinConstraints = true) {
                widget.anchor(context, _open, ::_toggle)
            }
        }
    }
}

private fun _shouldPlaceBelow(anchorTop: Float, anchorBottom: Float, overlayHeight: Float, panelHeight: Float, gap: Float): Boolean {
    val aboveTop = anchorTop - gap - panelHeight
    if (aboveTop >= 0) return false

    val belowBottom = anchorBottom + gap + panelHeight
    if (belowBottom <= overlayHeight) return true

    val spaceAbove = anchorTop
    val spaceBelow = overlayHeight - anchorBottom
    return spaceBelow > spaceAbove
}

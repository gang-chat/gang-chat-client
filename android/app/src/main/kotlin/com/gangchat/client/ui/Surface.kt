package com.gangchat.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.isTouchLike
import com.gangchat.client.widgets.kTouchSlop
import com.gangchat.client.widgets.padding

enum class SurfaceCorner { topLeft, topRight, bottomLeft, bottomRight }

/**
 * The kit's single pressable primitive: a flat rounded surface that tints on
 * hover and press. Buttons, rows, cards and segmented items all build on it so
 * interaction feedback is identical everywhere.
 *
 * Like the Flutter `Listener` it is built on, it observes raw pointer events
 * without competing with ancestors' gestures: a release inside the surface
 * fires [onPressed] even after an ancestor scrolled, unless
 * [cancelTouchPressOnDrag] is set.
 *
 * [width]: null sizes to the content; `Dp.Infinity` is treated like null, as
 * in Dart.
 */
@Composable
fun PressableSurface(
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    padding: EdgeInsets = EdgeInsets.zero,
    margin: EdgeInsets = EdgeInsets.zero,
    onPressed: (() -> Unit)? = null,
    tooltip: String? = null,
    enabled: Boolean = true,
    interactive: Boolean = false,
    loading: Boolean = false,
    pressRequiresHover: Boolean = false,
    /**
     * Cancels [onPressed] when a touch-like pointer moves beyond Flutter's
     * normal touch slop. Opt in for surfaces nested in a scrollable whose raw
     * pointer handling would otherwise bypass the scroll gesture.
     */
    cancelTouchPressOnDrag: Boolean = false,
    selected: Boolean = false,
    backgroundColor: Color = UiColors.surface,
    /** Background while hovered. Defaults to a light tint of the resting color. */
    hoverBackgroundColor: Color? = null,
    selectedBackgroundColor: Color = UiColors.selected,
    /** Background while pressed. Defaults to a darkened resting color. */
    pressedBackgroundColor: Color? = null,
    disabledBackgroundColor: Color = UiColors.disabledSurface,
    borderColor: Color = UiColors.border,
    selectedBorderColor: Color = UiColors.selectedBorder,
    disabledBorderColor: Color = UiColors.disabledBorder,
    borderRadius: Dp = UiRadii.md,
    hoverEffect: Boolean = true,
    pressEffect: Boolean = true,
    cornerCut: DpSize = DpSize.Zero,
    cutCorner: SurfaceCorner? = null,
    content: @Composable () -> Unit,
) = StatefulWidget(
    PressableSurfaceWidget(
        content, height, width, padding, margin, onPressed, tooltip, enabled, interactive, loading,
        pressRequiresHover, cancelTouchPressOnDrag, selected, backgroundColor, hoverBackgroundColor,
        selectedBackgroundColor, pressedBackgroundColor, disabledBackgroundColor, borderColor,
        selectedBorderColor, disabledBorderColor, borderRadius, hoverEffect, pressEffect, cornerCut,
        cutCorner, modifier,
    ),
) { _PressableSurfaceState() }

class PressableSurfaceWidget(
    val child: @Composable () -> Unit,
    val height: Dp,
    val width: Dp?,
    val padding: EdgeInsets,
    val margin: EdgeInsets,
    val onPressed: (() -> Unit)?,
    val tooltip: String?,
    val enabled: Boolean,
    val interactive: Boolean,
    val loading: Boolean,
    val pressRequiresHover: Boolean,
    val cancelTouchPressOnDrag: Boolean,
    val selected: Boolean,
    val backgroundColor: Color,
    val hoverBackgroundColor: Color?,
    val selectedBackgroundColor: Color,
    val pressedBackgroundColor: Color?,
    val disabledBackgroundColor: Color,
    val borderColor: Color,
    val selectedBorderColor: Color,
    val disabledBorderColor: Color,
    val borderRadius: Dp,
    val hoverEffect: Boolean,
    val pressEffect: Boolean,
    val cornerCut: DpSize,
    val cutCorner: SurfaceCorner?,
    val modifier: Modifier,
)

private class _PressableSurfaceState : ViewState<PressableSurfaceWidget>() {
    private var _hovered = false
    private var _pressed = false
    private var _pressedPointer: PointerId? = null
    private var _pressGlobalOrigin: Offset? = null
    private var _trackingTouchDrag = false
    private var _touchDragCancelled = false

    private val _isInteractive: Boolean get() = widget.enabled && (widget.interactive || widget.onPressed != null)

    fun _setHover(hovered: Boolean) {
        if (!mounted) return
        _hovered = hovered
        if (!hovered && _pressedPointer == null) _pressed = false
        if (mounted) setState()
    }

    fun _setPressed(pressed: Boolean) {
        if (!mounted) return
        if (!_isInteractive || !widget.pressEffect) return
        if (pressed && widget.pressRequiresHover && !_hovered) return
        if (_pressed == pressed) return
        setState { _pressed = pressed }
    }

    fun _handleTap() {
        if (!mounted) return
        if (!widget.enabled || widget.loading) return
        widget.onPressed?.invoke()
    }

    private fun _isInsideHitTarget(position: Offset, size: IntSize): Boolean =
        position.x >= 0 && position.y >= 0 && position.x <= size.width && position.y <= size.height

    fun _handlePointerDown(change: PointerInputChange, size: IntSize) {
        if (!mounted) return
        if (!_isInteractive || widget.loading || _pressedPointer != null) return
        _pressedPointer = change.id
        _pressGlobalOrigin = change.position
        _trackingTouchDrag = widget.cancelTouchPressOnDrag && change.type.isTouchLike()
        _touchDragCancelled = false
        _setPressed(_isInsideHitTarget(change.position, size))
    }

    fun _handlePointerMove(change: PointerInputChange, slopPx: Float) {
        if (!mounted) return
        if (change.id != _pressedPointer) return
        val origin = _pressGlobalOrigin
        if (_trackingTouchDrag && !_touchDragCancelled && origin != null &&
            (change.position - origin).getDistanceSquared() > slopPx * slopPx
        ) {
            _touchDragCancelled = true
            _setPressed(false)
            return
        }
        if (_touchDragCancelled) return
        // Keep the pressed effect for the whole hold, even if the pointer wanders
        // outside the hit target. Whether the tap actually fires is decided at
        // pointer-up from the release position.
        _setPressed(true)
    }

    fun _handlePointerUp(change: PointerInputChange, size: IntSize) {
        if (!mounted) return
        if (change.id != _pressedPointer) return
        val shouldPress = !_touchDragCancelled && _isInsideHitTarget(change.position, size)
        _clearPointerTracking()
        _setPressed(false)
        if (shouldPress) _handleTap()
    }

    fun _handlePointerCancel(change: PointerInputChange) {
        if (!mounted) return
        if (change.id != _pressedPointer) return
        _clearPointerTracking()
        _setPressed(false)
    }

    private fun _clearPointerTracking() {
        _pressedPointer = null
        _pressGlobalOrigin = null
        _trackingTouchDrag = false
        _touchDragCancelled = false
    }

    override fun didUpdateWidget(oldWidget: PressableSurfaceWidget) {
        if (!_isInteractive && (_hovered || _pressed)) {
            _hovered = false
            _pressed = false
        }
    }

    @Composable
    override fun build() {
        val radius = if (widget.borderRadius <= 0.dp) 0.dp else widget.borderRadius
        val interactive = _isInteractive
        val resting =
            if (widget.enabled) {
                if (widget.selected) widget.selectedBackgroundColor else widget.backgroundColor
            } else {
                widget.disabledBackgroundColor
            }
        val restingBorder =
            if (widget.enabled) {
                if (widget.selected) widget.selectedBorderColor else widget.borderColor
            } else {
                widget.disabledBorderColor
            }
        val pressed = widget.loading || (_pressed && widget.pressEffect)
        val hovered = widget.hoverEffect && _hovered && interactive
        val background =
            when {
                pressed -> {
                    val pressedColor = widget.pressedBackgroundColor
                    if (pressedColor == null) UiStates.pressed(resting) else lerp(resting, pressedColor, 0.4f)
                }
                hovered -> if (widget.selected) UiStates.hover(resting) else widget.hoverBackgroundColor ?: UiStates.hover(resting)
                else -> resting
            }
        val border = if (hovered && _visibleBorder(restingBorder)) lerp(restingBorder, Color.White, 0.07f) else restingBorder
        val explicitWidth = widget.width?.takeIf { it != Dp.Unspecified && it != Dp.Infinity }

        val result: @Composable (Modifier) -> Unit = { outer ->
            Box(
                outer
                    .padding(widget.margin)
                    .semantics(mergeDescendants = true) {
                        if (widget.onPressed != null) role = Role.Button
                        if (widget.selected) selected = true
                        if (!interactive) disabled()
                        if (interactive && widget.onPressed != null) {
                            onClick {
                                _handleTap()
                                true
                            }
                        }
                    }
                    .pointerInput(this) {
                        val slopPx = kTouchSlop.dp.toPx()
                        awaitPointerEventScope {
                            while (true) {
                                // Observe before descendants can consume, like Flutter's Listener.
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                when (event.type) {
                                    PointerEventType.Enter ->
                                        if (event.changes.any { it.type == PointerType.Mouse }) _setHover(true)
                                    PointerEventType.Exit ->
                                        if (event.changes.any { it.type == PointerType.Mouse }) _setHover(false)
                                    PointerEventType.Press ->
                                        for (change in event.changes) {
                                            if (change.pressed && !change.previousPressed) _handlePointerDown(change, size)
                                        }
                                    PointerEventType.Move -> for (change in event.changes) _handlePointerMove(change, slopPx)
                                    PointerEventType.Release ->
                                        for (change in event.changes) {
                                            if (change.pressed || !change.previousPressed) continue
                                            // Compose reports a cancelled gesture as an
                                            // already-consumed release.
                                            if (change.isConsumed) _handlePointerCancel(change) else _handlePointerUp(change, size)
                                        }
                                    else -> Unit
                                }
                            }
                        }
                    }
                    .then(if (explicitWidth != null) Modifier.width(explicitWidth) else Modifier)
                    .height(widget.height),
                propagateMinConstraints = true,
            ) {
                _SurfaceLayer(
                    background = background,
                    borderColor = border,
                    borderRadius = radius,
                    cornerCut = widget.cornerCut,
                    cutCorner = widget.cutCorner,
                    padding = widget.padding,
                    child = widget.child,
                )
            }
        }

        val tooltip = widget.tooltip
        if (tooltip != null && tooltip.isNotEmpty()) {
            UiTooltip(message = tooltip, modifier = widget.modifier) { result(Modifier) }
        } else {
            result(widget.modifier)
        }
    }
}

@Composable
private fun _SurfaceLayer(
    background: Color,
    borderColor: Color,
    borderRadius: Dp,
    padding: EdgeInsets,
    child: @Composable () -> Unit,
    cornerCut: DpSize,
    cutCorner: SurfaceCorner?,
) {
    val animatedBackground by animateColorAsState(
        background,
        tween(UiMotion.fast.inWholeMilliseconds.toInt(), easing = UiMotion.curve),
        label = "surfaceBackground",
    )
    val animatedBorder by animateColorAsState(
        borderColor,
        tween(UiMotion.fast.inWholeMilliseconds.toInt(), easing = UiMotion.curve),
        label = "surfaceBorder",
    )
    val shape: Shape = if (borderRadius <= 0.dp) RectangleShape else RoundedCornerShape(borderRadius)
    val clipShape: Shape =
        if (cornerCut.width <= 0.dp || cornerCut.height <= 0.dp || cutCorner == null) shape else _SurfaceClipShape(shape, cornerCut, cutCorner)
    // Flutter's AnimatedContainer insets its child by the decoration's border.
    val borderInset = if (_visibleBorder(borderColor)) EdgeInsets.all(1.dp) else EdgeInsets.zero
    Box(
        Modifier
            .clip(clipShape)
            .decoration(animatedBackground, shape, if (_visibleBorder(borderColor)) animatedBorder else null)
            .padding(padding + borderInset),
        propagateMinConstraints = true,
    ) {
        // The bounded-width hint is for the button itself, not its content.
        CompositionLocalProvider(LocalButtonFillsBoundedWidth provides false, content = child)
    }
}

private class _SurfaceClipShape(
    private val borderRadius: Shape,
    private val cornerCut: DpSize,
    private val cutCorner: SurfaceCorner,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cut = with(density) { Size(cornerCut.width.toPx(), cornerCut.height.toPx()) }
        val path = _surfacePath(Rect(Offset.Zero, size), cut, cutCorner)
        return if (path == null) borderRadius.createOutline(size, layoutDirection, density) else Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is _SurfaceClipShape && other.borderRadius == borderRadius && other.cornerCut == cornerCut && other.cutCorner == cutCorner

    override fun hashCode(): Int = java.util.Objects.hash(borderRadius, cornerCut, cutCorner)
}

private fun _visibleBorder(color: Color): Boolean = color.alpha > 0f

/** Null when the cut is empty (the caller then clips to the rounded rect). */
private fun _surfacePath(rect: Rect, cornerCut: Size, cutCorner: SurfaceCorner): Path? {
    val cw = cornerCut.width.coerceIn(0f, rect.width)
    val ch = cornerCut.height.coerceIn(0f, rect.height)
    if (cw <= 0 || ch <= 0) return null

    val path = Path()
    val l = rect.left
    val t = rect.top
    val r = rect.right
    val b = rect.bottom

    when (cutCorner) {
        SurfaceCorner.topLeft -> {
            path.moveTo(l + cw, t)
            path.lineTo(r, t)
            path.lineTo(r, b)
            path.lineTo(l, b)
            path.lineTo(l, t + ch)
            path.lineTo(l + cw, t + ch)
        }
        SurfaceCorner.topRight -> {
            path.moveTo(l, t)
            path.lineTo(r - cw, t)
            path.lineTo(r - cw, t + ch)
            path.lineTo(r, t + ch)
            path.lineTo(r, b)
            path.lineTo(l, b)
        }
        SurfaceCorner.bottomRight -> {
            path.moveTo(l, t)
            path.lineTo(r, t)
            path.lineTo(r, b - ch)
            path.lineTo(r - cw, b - ch)
            path.lineTo(r - cw, b)
            path.lineTo(l, b)
        }
        SurfaceCorner.bottomLeft -> {
            path.moveTo(l, t)
            path.lineTo(r, t)
            path.lineTo(r, b)
            path.lineTo(l + cw, b)
            path.lineTo(l + cw, b - ch)
            path.lineTo(l, b - ch)
        }
    }

    path.close()
    return path
}

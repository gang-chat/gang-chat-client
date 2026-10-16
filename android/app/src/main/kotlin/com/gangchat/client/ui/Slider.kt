package com.gangchat.client.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.Axis
import com.gangchat.client.widgets.BoxShadow
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration

// A flat slider: a thin rounded rail, an iris fill, and a round white knob that
// grows slightly while hovered or dragged. No Material Slider — that brings its
// own overlay theme we deliberately suppress app-wide. Supports both
// orientations; vertical fills from the bottom (min) up to the top (max).
private val _trackThickness = 4.dp
private val _handleLong = 14.dp
private val _handleShort = 14.dp
private val _hoverLabelWidth = 36.dp
private val _hoverLabelHeight = 18.dp
private val _hoverLabelGap = 4.dp

/** Test tag of the hover label (Dart's `ValueKey('ui-slider-hover-label')`). */
const val uiSliderHoverLabelTag = "ui-slider-hover-label"

/** Test tag of the knob's 14 dp slot. */
const val uiSliderHandleTag = "ui-slider-handle"

@Composable
fun UiSlider(
    /** Current value, clamped into [min]..[max] for display. */
    value: Double,
    /** Fired continuously while dragging or on a track tap. Null disables. */
    onChanged: ((Double) -> Unit)?,
    modifier: Modifier = Modifier,
    /** Fired once when an interaction begins (pointer down on the track). */
    onChangeStart: ((Double) -> Unit)? = null,
    /** Fired once when an interaction ends (pointer up or cancel). */
    onChangeEnd: ((Double) -> Unit)? = null,
    min: Double = 0.0,
    max: Double = 1.0,
    enabled: Boolean = true,
    /** Layout orientation. Vertical sliders run low→high bottom→top. */
    axis: Axis = Axis.horizontal,
    /**
     * Optional value text shown above the current thumb while a pointer hovers
     * or drags the slider.
     */
    hoverLabel: String? = null,
) = StatefulWidget(UiSliderWidget(value, onChanged, onChangeStart, onChangeEnd, min, max, enabled, axis, hoverLabel, modifier)) {
    _UiSliderState()
}

class UiSliderWidget(
    val value: Double,
    val onChanged: ((Double) -> Unit)?,
    val onChangeStart: ((Double) -> Unit)?,
    val onChangeEnd: ((Double) -> Unit)?,
    val min: Double,
    val max: Double,
    val enabled: Boolean,
    val axis: Axis,
    val hoverLabel: String?,
    val modifier: Modifier,
)

private class _UiSliderState : ViewState<UiSliderWidget>() {
    private var _pointer: PointerId? = null
    private var _hovered = false

    private val _interactive: Boolean get() = widget.enabled && widget.onChanged != null
    private val _vertical: Boolean get() = widget.axis == Axis.vertical

    private val _span: Double
        get() {
            val span = widget.max - widget.min
            return if (span <= 0) 1.0 else span
        }

    private val _fraction: Double get() = ((widget.value - widget.min) / _span).coerceIn(0.0, 1.0)

    private fun _emitFromPosition(localPosition: Offset, size: IntSize, handleLongPx: Float) {
        val fraction: Double
        if (_vertical) {
            val travel = size.height - handleLongPx
            if (travel <= 0) return
            // Bottom is min, so invert the y reading.
            fraction = (1 - (localPosition.y - handleLongPx / 2) / travel).toDouble().coerceIn(0.0, 1.0)
        } else {
            val travel = size.width - handleLongPx
            if (travel <= 0) return
            fraction = ((localPosition.x - handleLongPx / 2) / travel).toDouble().coerceIn(0.0, 1.0)
        }
        widget.onChanged?.invoke(widget.min + fraction * _span)
    }

    override fun didUpdateWidget(oldWidget: UiSliderWidget) {
        if (!_interactive) _pointer = null
    }

    @Composable
    override fun build() {
        val enabled = _interactive
        val grooveColor = if (enabled) Color(0xFF2A2A2F) else UiColors.disabledSurface
        val fillColor = if (enabled) UiColors.accentStrong else UiColors.textMuted
        val thumbColor = if (enabled) UiColors.inverse else UiColors.textMuted
        val active = enabled && (_hovered || _pointer != null)

        // Center the track along the cross axis, leaving room for the handle that
        // overhangs it.
        val crossInset = (_handleShort - _trackThickness) / 2

        val hoverLabel = widget.hoverLabel?.trim()
        val showHoverLabel = !_vertical && !hoverLabel.isNullOrEmpty() && (_hovered || _pointer != null)
        val activeT by animateFloatAsState(
            if (active) 1f else 0f,
            tween(UiMotion.fast.inWholeMilliseconds.toInt(), easing = UiMotion.curve),
            label = "sliderKnob",
        )

        BoxWithConstraints(
            widget.modifier
                .pointerInput(this) {
                    val handleLongPx = _handleLong.toPx()
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Enter ->
                                    if (event.changes.any { it.type == PointerType.Mouse }) setState { _hovered = true }
                                PointerEventType.Exit ->
                                    if (event.changes.any { it.type == PointerType.Mouse }) setState { _hovered = false }
                                PointerEventType.Press -> {
                                    val change = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                                    if (!_interactive || _pointer != null) continue
                                    setState { _pointer = change.id }
                                    widget.onChangeStart?.invoke(widget.value)
                                    _emitFromPosition(change.position, size, handleLongPx)
                                }
                                PointerEventType.Move -> {
                                    val change = event.changes.firstOrNull { it.id == _pointer } ?: continue
                                    _emitFromPosition(change.position, size, handleLongPx)
                                }
                                PointerEventType.Release -> {
                                    if (event.changes.none { it.id == _pointer && !it.pressed }) continue
                                    setState { _pointer = null }
                                    widget.onChangeEnd?.invoke(widget.value)
                                }
                                else -> Unit
                            }
                        }
                    }
                }
                .then(
                    if (_vertical) {
                        Modifier.width(_handleShort).fillMaxHeight()
                    } else {
                        Modifier.height(_handleShort).fillMaxWidth()
                    },
                ),
        ) {
            val extent = if (_vertical) maxHeight else maxWidth
            val travel = (extent - _handleLong).coerceAtLeast(0.dp)
            val handleStart = travel * _fraction.toFloat()
            val fillExtent = handleStart + _handleLong / 2
            val hoverLabelLeft =
                (handleStart + _handleLong / 2 - _hoverLabelWidth / 2).coerceIn(0.dp, (extent - _hoverLabelWidth).coerceAtLeast(0.dp))
            val trackShape = RoundedCornerShape(_trackThickness / 2)

            // The recessed track, spanning the full main-axis extent.
            Box(
                if (_vertical) {
                    Modifier.offset(x = crossInset).width(_trackThickness).fillMaxHeight()
                } else {
                    Modifier.offset(y = crossInset).height(_trackThickness).fillMaxWidth()
                }.decoration(grooveColor, trackShape, Color.Transparent),
            )
            // The accent fill from the low end up to the handle.
            Box(
                if (_vertical) {
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = crossInset)
                        .size(_trackThickness, fillExtent)
                } else {
                    Modifier.offset(y = crossInset).size(fillExtent, _trackThickness)
                }.decoration(fillColor, trackShape),
            )
            // A round knob straddling the track.
            val knobSize = _handleShort - 2.dp + 2.dp * activeT
            val knobShadows =
                buildList {
                    add(BoxShadow(color = Color(0x66000000), blurRadius = 4.dp, offset = DpOffset(0.dp, 1.dp)))
                    if (activeT > 0f) {
                        add(BoxShadow(color = UiColors.accentStrong.copy(alpha = 0.35f), spreadRadius = 4.dp * activeT))
                    }
                }
            Box(
                (
                    if (_vertical) {
                        // start is measured from the low (bottom) end.
                        Modifier.align(Alignment.BottomStart).offset(y = -handleStart)
                    } else {
                        Modifier.offset(x = handleStart)
                    }
                )
                    .testTag(uiSliderHandleTag)
                    .size(_handleLong, _handleShort),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(knobSize).boxShadow(knobShadows, CircleShape).decoration(thumbColor, CircleShape))
            }
            if (showHoverLabel) {
                Box(
                    Modifier
                        .testTag(uiSliderHoverLabelTag)
                        .offset(x = hoverLabelLeft, y = -(_hoverLabelGap + _hoverLabelHeight))
                        .size(_hoverLabelWidth, _hoverLabelHeight)
                        .decoration(UiColors.surfaceHover, RoundedCornerShape(UiRadii.sm), UiColors.borderStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        hoverLabel,
                        style = UiTypography.label.copyWith(color = UiColors.text, fontSize = 10.0, fontWeight = FontWeight.W600),
                    )
                }
            }
        }
    }
}

package com.gangchat.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration

private val _switchWidth = 42.dp
private val _switchHeight = 24.dp
private val _switchThumbSize = 18.dp
private val _switchHorizontalInset = 3.dp
private const val _switchDurationMillis = 160

@Composable
fun UiSwitch(
    value: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tooltip: String? = null,
) = StatefulWidget(UiSwitchWidget(value, onChanged, enabled, tooltip, modifier)) { _UiSwitchState() }

class UiSwitchWidget(
    val value: Boolean,
    val onChanged: ((Boolean) -> Unit)?,
    val enabled: Boolean,
    val tooltip: String?,
    val modifier: Modifier,
)

private class _UiSwitchState : ViewState<UiSwitchWidget>() {
    private var _pressedPointer: PointerId? = null

    private val _interactive: Boolean get() = widget.enabled && widget.onChanged != null

    private fun _toggle() {
        if (!_interactive) return
        widget.onChanged?.invoke(!widget.value)
    }

    override fun didUpdateWidget(oldWidget: UiSwitchWidget) {
        if (!_interactive && _pressedPointer != null) _pressedPointer = null
    }

    @Composable
    override fun build() {
        val active = widget.value
        val enabled = _interactive
        val trackColor =
            when {
                !enabled -> if (active) UiColors.accentStrong.copy(alpha = 0.35f) else UiColors.disabledSurface
                active -> UiColors.accentStrong
                else -> Color(0xFF2A2A2F)
            }
        val trackBorder = if (active || !enabled) Color.Transparent else UiColors.borderStrong
        val thumbColor =
            when {
                !enabled -> UiColors.textMuted
                active -> UiColors.onAccent
                else -> Color(0xFF8E8E95)
            }
        val thumbTop = (_switchHeight - _switchThumbSize) / 2
        val spec = tween<Color>(_switchDurationMillis, easing = Curves.easeOutCubic)
        val animatedTrack by animateColorAsState(trackColor, spec, label = "switchTrack")
        val animatedBorder by animateColorAsState(trackBorder, spec, label = "switchBorder")
        val animatedThumb by animateColorAsState(thumbColor, spec, label = "switchThumb")
        val thumbLeft by animateDpAsState(
            if (active) _switchWidth - _switchThumbSize - _switchHorizontalInset else _switchHorizontalInset,
            tween(_switchDurationMillis, easing = Curves.easeOutCubic),
            label = "switchThumbLeft",
        )

        val result: @Composable (Modifier) -> Unit = { outer ->
            Box(
                outer
                    .semantics(mergeDescendants = true) {
                        toggleableState = ToggleableState(widget.value)
                        role = Role.Switch
                        if (!_interactive) disabled()
                        if (_interactive) {
                            onClick {
                                _toggle()
                                true
                            }
                        }
                    }
                    .pointerInput(this) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                for (change in event.changes) {
                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            if (!_interactive || _pressedPointer != null) continue
                                            if (change.pressed && !change.previousPressed) _pressedPointer = change.id
                                        }
                                        PointerEventType.Release -> {
                                            if (change.id != _pressedPointer || change.pressed) continue
                                            val position = change.position
                                            val inside =
                                                position.x >= 0 && position.y >= 0 && position.x <= size.width && position.y <= size.height
                                            _pressedPointer = null
                                            // A consumed release is Compose's pointer cancel.
                                            if (!change.isConsumed && inside) widget.onChanged?.invoke(!widget.value)
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                    .size(_switchWidth, _switchHeight),
            ) {
                Box(Modifier.fillMaxSize().decoration(animatedTrack, RoundedCornerShape(UiRadii.pill), animatedBorder))
                Box(
                    Modifier
                        .offset(x = thumbLeft, y = thumbTop)
                        .size(_switchThumbSize)
                        .boxShadow(if (enabled) UiShadows.raised else emptyList(), CircleShape)
                        .decoration(animatedThumb, CircleShape),
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

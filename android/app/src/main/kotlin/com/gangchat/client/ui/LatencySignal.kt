package com.gangchat.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.PointerDownEvent
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TapRegion
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/** Test tag of the badge (Dart's `ValueKey('latency-signal-badge')`). */
const val latencySignalBadgeTag = "latency-signal-badge"

/** Test tag of bar [index] (1..3) (Dart's `ValueKey('latency-signal-bar-$index')`). */
fun latencySignalBarTag(index: Int) = "latency-signal-bar-$index"

/**
 * Three signal bars with a latency tooltip. A tap pins the tooltip open
 * (touch has no hover); a tap anywhere else closes it.
 */
@Composable
fun LatencySignalBadge(
    activeBars: Int,
    activeColor: Color,
    tooltip: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) = StatefulWidget(LatencySignalBadgeWidget(activeBars, activeColor, tooltip, size, modifier)) { _LatencySignalBadgeState() }

class LatencySignalBadgeWidget(
    val activeBars: Int,
    val activeColor: Color,
    val tooltip: String,
    val size: Dp,
    val modifier: Modifier,
)

private class _LatencySignalBadgeState : ViewState<LatencySignalBadgeWidget>() {
    private val _tooltipKey = UiTooltipState()
    private var _pinned = false

    private fun _togglePinned() {
        if (_pinned) {
            setState { _pinned = false }
            UiTooltip.dismissAllToolTips()
            return
        }
        setState { _pinned = true }
    }

    private fun _handleTapOutside(event: PointerDownEvent) {
        if (!_pinned) return
        setState { _pinned = false }
        UiTooltip.dismissAllToolTips()
    }

    @Composable
    override fun build() {
        // Flutter shows the pinned tooltip after the frame that pins it.
        LaunchedEffect(_pinned) {
            if (!mounted || !_pinned) return@LaunchedEffect
            _tooltipKey.ensureTooltipVisible()
        }
        val bars = widget.activeBars.coerceIn(0, 3)
        TapRegion(modifier = widget.modifier, onTapOutside = ::_handleTapOutside) {
            Box(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::_togglePinned,
                ),
            ) {
                UiTooltip(
                    message = widget.tooltip,
                    state = _tooltipKey,
                    waitDuration = _hoverWaitDuration,
                    triggerMode = TooltipTriggerMode.manual,
                    // Material Tooltip has no persistent-hover flag. A pinned tooltip
                    // therefore keeps its hover-exit timer dormant; any outside pointer
                    // down still dismisses it immediately through the normal route.
                    exitDuration = if (_pinned) _pinnedExitDuration else null,
                ) {
                    Row(
                        Modifier
                            .testTag(latencySignalBadgeTag)
                            .size(widget.size, widget.size - 2.dp)
                            .padding(start = 3.5.dp, top = 3.dp, end = 3.5.dp, bottom = 3.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        for (index in 1..3) {
                            _SignalBar(index = index, active = bars >= index, color = widget.activeColor)
                            if (index != 3) Spacer(Modifier.width(1.dp))
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val _hoverWaitDuration = 350.milliseconds
        private val _pinnedExitDuration = 365.days
    }
}

@Composable
private fun _SignalBar(index: Int, active: Boolean, color: Color) {
    val fill = if (active) color else Color(0xFF8A93A3)
    Box(
        Modifier
            .testTag(latencySignalBarTag(index))
            .size(3.dp, (3.0 + index * 2.0).dp)
            .decoration(fill, RoundedCornerShape(999.dp)),
    )
}

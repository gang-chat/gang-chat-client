package com.gangchat.client.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.rememberCoroutineScope
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.startTimer
import kotlinx.coroutines.CoroutineScope
import com.gangchat.client.widgets.LocalTapRegionRegistry
import com.gangchat.client.widgets.PointerDownEvent
import com.gangchat.client.widgets.TapRegionRegistry
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.kLongPressTimeout
import com.gangchat.client.widgets.kTouchSlop
import com.gangchat.client.widgets.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Flutter's `TooltipTriggerMode`. */
enum class TooltipTriggerMode { manual, longPress, tap }

/** Semantics key carrying a [UiTooltip] message (Flutter's `Semantics(tooltip:)`, `find.byTooltip`). */
val TooltipSemanticsKey = SemanticsPropertyKey<String>("Tooltip")

var SemanticsPropertyReceiver.tooltip by TooltipSemanticsKey

/**
 * Programmatic handle on a [UiTooltip] (Dart's `GlobalKey<UiTooltipState>`):
 * pass one as `state` and call [ensureTooltipVisible].
 */
class UiTooltipState {
    internal var controller: TooltipController? = null

    /** Shows the tooltip programmatically; mirrors Flutter's `TooltipState`. */
    fun ensureTooltipVisible(): Boolean = controller?.ensureVisible() ?: false
}

/** Static helpers of Dart's `UiTooltip`. */
object UiTooltip {
    internal val visible = LinkedHashSet<TooltipController>()

    /** Dismisses every open tooltip on screen. */
    fun dismissAllToolTips() {
        for (controller in visible.toList()) controller.dismissNow()
    }
}

internal class TooltipController {
    var visible by mutableStateOf(false)
        private set
    var registry: TapRegionRegistry? = null
    var scope: CoroutineScope? = null
    var waitDuration: Duration = Duration.ZERO
    var exitDuration: Duration = 100.milliseconds
    private var showTimer: CompositionTimer? = null
    private var dismissTimer: CompositionTimer? = null

    private val globalPointerDown: (PointerDownEvent) -> Unit = { dismissNow() }

    fun ensureVisible(): Boolean {
        show()
        return true
    }

    fun show() {
        cancelTimers()
        if (visible) return
        visible = true
        UiTooltip.visible.add(this)
        registry?.addGlobalPointerDownListener(globalPointerDown)
    }

    fun scheduleShow(delay: Duration) {
        dismissTimer?.cancel()
        dismissTimer = null
        if (visible) return
        showTimer?.cancel()
        showTimer = if (delay <= Duration.ZERO) null.also { show() } else scope?.startTimer(delay) { show() }
    }

    fun scheduleDismiss(delay: Duration) {
        showTimer?.cancel()
        showTimer = null
        if (!visible) return
        dismissTimer?.cancel()
        dismissTimer = if (delay <= Duration.ZERO) null.also { dismissNow() } else scope?.startTimer(delay) { dismissNow() }
    }

    fun dismissNow() {
        cancelTimers()
        if (!visible) return
        visible = false
        UiTooltip.visible.remove(this)
        registry?.removeGlobalPointerDownListener(globalPointerDown)
    }

    fun dispose() {
        dismissNow()
    }

    private fun cancelTimers() {
        showTimer?.cancel()
        showTimer = null
        dismissTimer?.cancel()
        dismissTimer = null
    }
}

/** Flutter's default tooltip `showDuration` for touch long presses. */
private val _defaultShowDuration = 1500.milliseconds
private const val _defaultMargin = 10f
private const val _fadeInMillis = 150
private const val _fadeOutMillis = 75

/**
 * Material `Tooltip` as the kit uses it (Dart's `UiTooltip`, which also gave
 * each tooltip its own semantics container). On Android a long press shows
 * the bubble (with haptic feedback) for 1.5 s after release; a mouse hover
 * shows it after the theme's wait. The bubble follows `uiTheme()`'s tooltip
 * theme and Flutter's `positionDependentBox` placement (24 dp below the
 * anchor's centre, above when it does not fit).
 */
@Composable
fun UiTooltip(
    message: String,
    modifier: Modifier = Modifier,
    state: UiTooltipState? = null,
    waitDuration: Duration? = null,
    exitDuration: Duration? = null,
    preferBelow: Boolean? = null,
    verticalOffset: androidx.compose.ui.unit.Dp? = null,
    triggerMode: TooltipTriggerMode? = null,
    content: @Composable () -> Unit,
) {
    if (message.isEmpty()) {
        Box(modifier, propagateMinConstraints = true) { content() }
        return
    }
    val theme = uiTheme().tooltipTheme
    val controller = remember { TooltipController() }
    controller.registry = LocalTapRegionRegistry.current
    val scope = rememberCoroutineScope()
    controller.scope = scope
    controller.waitDuration = waitDuration ?: theme.waitDuration
    controller.exitDuration = exitDuration ?: 100.milliseconds
    state?.controller = controller
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    val haptics = LocalHapticFeedback.current
    val mode = triggerMode ?: TooltipTriggerMode.longPress
    val density = LocalDensity.current
    val touchSlopPx = with(density) { kTouchSlop.dp.toPx() }

    Box(
        modifier
            .semantics { tooltip = message }
            .pointerInput(mode) {
                awaitPointerEventScope {
                    var downPosition: Offset? = null
                    var downId: Any? = null
                    var longPressTimer: CompositionTimer? = null
                    var longPressed = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Enter ->
                                if (change.type == PointerType.Mouse) controller.scheduleShow(controller.waitDuration)
                            PointerEventType.Exit ->
                                if (change.type == PointerType.Mouse) controller.scheduleDismiss(controller.exitDuration)
                            PointerEventType.Press -> {
                                if (downId != null) continue
                                downId = change.id
                                downPosition = change.position
                                longPressed = false
                                if (mode == TooltipTriggerMode.longPress) {
                                    longPressTimer?.cancel()
                                    longPressTimer =
                                        scope.startTimer(kLongPressTimeout) {
                                            longPressed = true
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            controller.show()
                                        }
                                }
                            }
                            PointerEventType.Move -> {
                                val tracked = event.changes.firstOrNull { it.id == downId } ?: continue
                                val origin = downPosition ?: continue
                                if (!longPressed && (tracked.position - origin).getDistance() > touchSlopPx) {
                                    longPressTimer?.cancel()
                                    longPressTimer = null
                                }
                            }
                            PointerEventType.Release -> {
                                val tracked = event.changes.firstOrNull { it.id == downId } ?: continue
                                if (!tracked.changedToUpIgnoreConsumed()) continue
                                longPressTimer?.cancel()
                                longPressTimer = null
                                if (longPressed) {
                                    controller.scheduleDismiss(_defaultShowDuration)
                                } else if (mode == TooltipTriggerMode.tap) {
                                    controller.show()
                                    controller.scheduleDismiss(_defaultShowDuration)
                                }
                                downId = null
                                downPosition = null
                                longPressed = false
                            }
                            else -> Unit
                        }
                    }
                }
            },
        propagateMinConstraints = true,
    ) {
        content()
        TooltipBubble(controller, message, preferBelow ?: true, verticalOffset ?: theme.verticalOffset)
    }
}

@Composable
private fun TooltipBubble(controller: TooltipController, message: String, preferBelow: Boolean, verticalOffset: androidx.compose.ui.unit.Dp) {
    val visible = controller.visible
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) _fadeInMillis else _fadeOutMillis),
        label = "tooltipOpacity",
    )
    if (!visible && opacity == 0f) return
    val theme = uiTheme().tooltipTheme
    val density = LocalDensity.current
    val provider =
        remember(preferBelow, verticalOffset, density) {
            TooltipPositionProvider(
                preferBelow = preferBelow,
                verticalOffsetPx = with(density) { verticalOffset.toPx() },
                marginPx = with(density) { _defaultMargin.dp.toPx() },
            )
        }
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        val shape = RoundedCornerShape(theme.radius)
        Box(
            Modifier
                .alpha(opacity)
                .heightIn(min = theme.height)
                .boxShadow(theme.shadows, shape)
                .decoration(theme.color, shape, theme.borderColor)
                .padding(theme.padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(message, style = theme.textStyle)
        }
    }
}

/** Flutter's `positionDependentBox` with the tooltip's target at the anchor's centre. */
private class TooltipPositionProvider(
    val preferBelow: Boolean,
    val verticalOffsetPx: Float,
    val marginPx: Float,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val targetX = (anchorBounds.left + anchorBounds.right) / 2f
        val targetY = (anchorBounds.top + anchorBounds.bottom) / 2f
        val childWidth = popupContentSize.width.toFloat()
        val childHeight = popupContentSize.height.toFloat()
        val fitsBelow = targetY + verticalOffsetPx + childHeight <= windowSize.height - marginPx
        val fitsAbove = targetY - verticalOffsetPx - childHeight >= marginPx
        val tooltipBelow = if (fitsAbove == fitsBelow) preferBelow else fitsBelow
        val y =
            if (tooltipBelow) {
                min(targetY + verticalOffsetPx, windowSize.height - marginPx)
            } else {
                max(targetY - verticalOffsetPx - childHeight, marginPx)
            }
        val flexibleSpace = windowSize.width - childWidth
        val x =
            if (flexibleSpace <= 2 * marginPx) {
                flexibleSpace / 2f
            } else {
                (targetX - childWidth / 2f).coerceIn(marginPx, flexibleSpace - marginPx)
            }
        return IntOffset(x.toInt(), y.toInt())
    }
}

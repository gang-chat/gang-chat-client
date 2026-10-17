package com.gangchat.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.DefaultTextStyle
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.PopupRoute
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.rememberBuildContext
import kotlin.math.max

private val _contextMenuScreenPadding = 8.dp
private val _contextMenuWidth = 196.dp
private val _contextMenuMinItemHeight = 32.dp
private val _contextMenuHorizontalPadding = 10.dp

class UiContextMenuSection(val items: List<UiContextMenuItem>)

class UiContextMenuItem(
    val label: String,
    val onPressed: (() -> Unit)?,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val selected: Boolean = false,
    val danger: Boolean = false,
)

/**
 * Gives application context-menu targets one platform-consistent trigger.
 *
 * Secondary mouse click remains available. Android also maps a touch/stylus
 * hold to the same callback. The raw pointer tracker is a fallback for
 * targets containing editable text, whose native long-press gesture can
 * otherwise consume the hold.
 *
 * [onTriggered] receives the pointer position in window dp (the coordinate
 * space of `RenderBoxHandle`, the overlay and [showUiContextMenu]).
 */
@Composable
fun UiContextMenuTriggerRegion(
    onTriggered: (DpOffset) -> Unit,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    child: @Composable () -> Unit,
) = StatefulWidget(UiContextMenuTriggerRegionWidget(onTriggered, onTap, child, modifier)) { _UiContextMenuTriggerRegionState() }

class UiContextMenuTriggerRegionWidget(
    val onTriggered: (DpOffset) -> Unit,
    val onTap: (() -> Unit)?,
    val child: @Composable () -> Unit,
    val modifier: Modifier,
)

private class _UiContextMenuTriggerRegionState : ViewState<UiContextMenuTriggerRegionWidget>() {
    private var _androidLongPressTracker: UiAndroidLongPressTracker? = null
    private var _triggeredForCurrentPointer = false
    private var _coordinates: LayoutCoordinates? = null

    override fun dispose() {
        _androidLongPressTracker?.cancel()
    }

    private fun _triggerAndroidLongPress(globalPosition: DpOffset) {
        if (!mounted) return
        _trigger(globalPosition, androidLongPress = true)
    }

    private fun _trigger(globalPosition: DpOffset, androidLongPress: Boolean) {
        if (androidLongPress) {
            if (_triggeredForCurrentPointer) return
            _triggeredForCurrentPointer = true
            removeAnyTextContextMenu()
        }
        widget.onTriggered(globalPosition)
    }

    @Composable
    override fun build() {
        val scope = rememberCoroutineScope()
        val tracker = _androidLongPressTracker ?: UiAndroidLongPressTracker(::_triggerAndroidLongPress, scope).also { _androidLongPressTracker = it }
        val density = LocalDensity.current
        fun window(local: androidx.compose.ui.geometry.Offset): DpOffset {
            val coords = _coordinates
            val position = if (coords != null && coords.isAttached) coords.localToWindow(local) else local
            return density.toWindowDp(position)
        }
        Box(
            widget.modifier
                .onGloballyPositioned { _coordinates = it }
                // Raw pointer events, observed before any descendant consumes them.
                .pointerInput(this) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Press ->
                                    for (change in event.changes) {
                                        if (!change.pressed || change.previousPressed) continue
                                        _triggeredForCurrentPointer = false
                                        if (change.type == PointerType.Mouse && event.buttons.isSecondaryPressed) {
                                            _trigger(window(change.position), androidLongPress = false)
                                        }
                                        tracker.handlePointerDown(change.id, window(change.position), change.type, enabled = true)
                                    }
                                PointerEventType.Move ->
                                    for (change in event.changes) tracker.handlePointerMove(change.id, window(change.position))
                                PointerEventType.Release ->
                                    for (change in event.changes) {
                                        if (change.pressed || !change.previousPressed) continue
                                        if (change.isConsumed) tracker.handlePointerCancel(change.id) else tracker.handlePointerUp(change.id)
                                        _triggeredForCurrentPointer = false
                                    }
                                else -> Unit
                            }
                        }
                    }
                }
                .pointerInput(widget.onTap != null) {
                    if (widget.onTap != null) {
                        detectTapGestures(
                            onTap = { _ -> widget.onTap?.invoke() },
                            onLongPress = { offset -> _trigger(window(offset), androidLongPress = true) },
                        )
                    } else {
                        // Flutter's gesture arena: a lone long-press recognizer
                        // never claims a quick tap, so an ancestor's tap (a
                        // profile card around a message avatar) still gets it;
                        // only a completed long press keeps the pointer.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val ended =
                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    waitForUpOrCancellation()
                                    true
                                }
                            if (ended != null) return@awaitEachGesture
                            _trigger(window(down.position), androidLongPress = true)
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }
                },
            propagateMinConstraints = true,
        ) {
            widget.child()
        }
    }
}

/**
 * Shows the kit's context menu at [position] (window dp) on the root
 * navigator and suspends until it closes. Sections without items are
 * skipped; with none left nothing is shown.
 */
suspend fun showUiContextMenu(context: BuildContext, position: DpOffset, sections: List<UiContextMenuSection>) {
    val visibleSections = sections.filter { it.items.isNotEmpty() }
    if (visibleSections.isEmpty()) return
    Navigator.of(context, rootNavigator = true).push(_UiContextMenuRoute(position, visibleSections)).await()
}

private class _UiContextMenuRoute(
    val position: DpOffset,
    val sections: List<UiContextMenuSection>,
) : PopupRoute<Unit>() {
    override val barrierColor: Color? get() = Color.Transparent
    override val barrierDismissible: Boolean get() = true
    override val transitionDuration: Int get() = 110
    override val reverseTransitionDuration: Int get() = 60

    @Composable
    override fun buildPage(context: BuildContext) {
        val paddingAbove = LocalMediaQuery.current.padding.top + _contextMenuScreenPadding
        val density = LocalDensity.current
        val anchorX = with(density) { (position.x - _contextMenuScreenPadding).roundToPx() }
        val anchorY = with(density) { (position.y - paddingAbove).roundToPx() }
        Layout(
            content = { _UiContextMenuPanel(sections) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = _contextMenuScreenPadding, top = paddingAbove, end = _contextMenuScreenPadding, bottom = _contextMenuScreenPadding),
        ) { measurables, constraints ->
            val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val maxX = max(0, constraints.maxWidth - placeable.width)
                val maxY = max(0, constraints.maxHeight - placeable.height)
                placeable.place(anchorX.coerceIn(0, maxX), anchorY.coerceIn(0, maxY))
            }
        }
    }

    @Composable
    override fun buildTransitions(context: BuildContext, animation: Float, content: @Composable () -> Unit) {
        val t = animation.coerceIn(0f, 1f)
        // FadeTransition over a ScaleTransition (0.96 -> 1, top-left aligned on
        // the panel itself, which the page positions).
        val scale = 0.96f + 0.04f * UiMotion.curve.transform(t)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = t }) {
            androidx.compose.runtime.CompositionLocalProvider(_LocalMenuScale provides scale) { content() }
        }
    }
}

private val _LocalMenuScale = androidx.compose.runtime.compositionLocalOf { 1f }

@Composable
private fun _UiContextMenuPanel(sections: List<UiContextMenuSection>) {
    val shape = RoundedCornerShape(UiRadii.lg - 2.dp)
    val scale = _LocalMenuScale.current
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .width(_contextMenuWidth)
            .boxShadow(UiShadows.popover, shape)
            .decoration(UiColors.surfaceRaised, shape, UiColors.borderStrong)
            .clip(shape)
            .padding(5.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            for ((sectionIndex, section) in sections.withIndex()) {
                if (sectionIndex > 0) _UiContextMenuDivider()
                for (item in section.items) _UiContextMenuItemWidget(item)
            }
        }
    }
}

private class _UiContextMenuItemWidgetConfig(val item: UiContextMenuItem)

@Composable
private fun _UiContextMenuItemWidget(item: UiContextMenuItem) =
    StatefulWidget(_UiContextMenuItemWidgetConfig(item)) { _UiContextMenuItemWidgetState() }

/**
 * Dart's `TextStyle(inherit: false, ...)`: nothing comes from the ambient
 * style (routes in Flutter sit outside any `Material`).
 */
private fun _standaloneStyle(color: Color, fontSize: Double, height: Double): TextStyle =
    TextStyle(
        color = color,
        fontFamily = UiFonts.client,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.W400,
        letterSpacing = 0.sp,
        lineHeight = (fontSize * height).sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
        textDecoration = TextDecoration.None,
    )

private class _UiContextMenuItemWidgetState : ViewState<_UiContextMenuItemWidgetConfig>() {
    private var _hovered = false

    private val _enabled: Boolean get() = widget.item.onPressed != null

    private fun _setHovered(hovered: Boolean) {
        if (_hovered == hovered) return
        setState { _hovered = hovered }
    }

    @Composable
    override fun build() {
        val item = widget.item
        val context = rememberBuildContext()
        val foreground =
            when {
                !_enabled -> UiColors.textMuted
                item.danger -> UiColors.danger
                item.selected -> UiColors.accent
                else -> UiColors.text
            }
        val shortcut = item.shortcut?.trim() ?: ""
        val background =
            when {
                !_enabled -> Color.Transparent
                _hovered -> if (item.danger) UiColors.danger.copy(alpha = 0.14f) else UiColors.surfaceHover
                item.selected -> UiColors.selected
                else -> Color.Transparent
            }
        DefaultTextStyle(TextStyle(), merge = false) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.none { it.type == PointerType.Mouse }) continue
                                when (event.type) {
                                    PointerEventType.Enter -> _setHovered(true)
                                    PointerEventType.Exit -> _setHovered(false)
                                    else -> Unit
                                }
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = _enabled,
                        onClick = {
                            val onPressed = item.onPressed ?: return@clickable
                            unawaited {
                                Navigator.of(context).maybePop()
                                onPressed()
                            }
                        },
                    )
                    .heightIn(min = _contextMenuMinItemHeight)
                    .decoration(background, RoundedCornerShape(UiRadii.sm))
                    .padding(horizontal = _contextMenuHorizontalPadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = item.icon
                if (icon != null) {
                    Icon(
                        icon,
                        size = 17.dp,
                        color = if (item.danger || item.selected || !_enabled) foreground else UiColors.textSecondary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    item.label,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = _standaloneStyle(foreground, 13.0, 1.2),
                )
                if (shortcut.isNotEmpty()) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        shortcut,
                        maxLines = 1,
                        style = _standaloneStyle(if (_enabled) UiColors.textSecondary else UiColors.textMuted.copy(alpha = 0.68f), 12.0, 1.2),
                    )
                }
            }
        }
    }
}

@Composable
private fun _UiContextMenuDivider() {
    Box(
        Modifier
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .decoration(UiColors.borderStrong),
    )
}

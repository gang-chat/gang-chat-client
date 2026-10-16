package com.gangchat.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.RenderBoxHandle
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.renderBox
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** A segment of a [SegmentedControl]. [badgeKey] becomes the badge's test tag. */
class Segment<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val showBadge: Boolean = false,
    val badgeKey: String? = null,
)

/** Test tag of the overflow scroll view (Dart's `ValueKey('segmented-control-scroll-view')`). */
const val segmentedControlScrollViewTag = "segmented-control-scroll-view"

/** Test tag of segment [index]'s hit target. */
fun segmentedControlSegmentTag(index: Int) = "segmented-control-segment-$index"

/** Test tag of the selected thumb. */
const val segmentedControlThumbTag = "segmented-control-thumb"

private val _segmentPadding = 10.dp
private val _minimumSegmentPadding = 6.dp
private val _iconSize = 15.dp
private val _iconGap = 6.dp
private val _overflowControlGap = 6.dp

// TextPainter and the rendered paragraph can differ by a few subpixels across
// platform font fallbacks. Keep a small buffer so a fully measured label is
// never forced back into clipping at a fractional-width boundary.
private val _contentWidthSafety = 6.dp
private val _thumbInset = 3.dp
private const val _durationMillis = 180
private val _segmentTextStyle = uiTextStyle(fontFamily = UiFonts.client, fontSize = 13.0, fontWeight = FontWeight.W600)

/**
 * A pill track of segments with a sliding white thumb. When the segments do
 * not fit even at their minimum padding, the track scrolls horizontally with
 * a "回到开头" button and a "选择选项" menu.
 */
@Composable
fun <T> SegmentedControl(
    segments: List<Segment<T>>,
    value: T,
    onChanged: (T) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    /** Height of each segment. Defaults to 36; shrink for denser layouts. */
    height: Dp = 36.dp,
) = StatefulWidget(SegmentedControlWidget(segments, value, onChanged, expanded, height, modifier)) { _SegmentedControlState<T>() }

/** Dart's static `SegmentedControl.minimumWidthFor`. */
object SegmentedControl {
    /**
     * The narrowest width that can show every segment without entering the
     * overflow/scroll layout.
     */
    fun <T> minimumWidthFor(context: BuildContext, segments: List<Segment<T>>): Dp =
        _sum(segments.map { measureSegmentContentWidth(context, it) + _minimumSegmentPadding * 2 })

    internal fun <T> measureSegmentContentWidth(context: BuildContext, segment: Segment<T>): Dp {
        val painter = TextPainter(segment.label, _segmentTextStyle, maxLines = 1).layout(context)
        val iconWidth = if (segment.icon == null) 0.dp else _iconSize + _iconGap
        val badgeWidth = if (segment.showBadge) 8.dp else 0.dp
        return painter.width.dp + iconWidth + badgeWidth + _contentWidthSafety
    }
}

class SegmentedControlWidget<T>(
    val segments: List<Segment<T>>,
    val value: T,
    val onChanged: (T) -> Unit,
    val expanded: Boolean,
    val height: Dp,
    val modifier: Modifier,
)

private class _SegmentWidthFit(val widths: List<Dp>, val horizontalPadding: Dp)

private class _SegmentedControlState<T> : ViewState<SegmentedControlWidget<T>>() {
    private var _scrollController: ScrollState? = null
    private val _menuAnchorKey = RenderBoxHandle()
    private var scope: CoroutineScope? = null
    private var density = 1f

    private var _hoveredIndex: Int? = null
    private var _pressedIndex: Int? = null
    private var _showJumpToStart = false
    private var _menuOpen = false
    private var _overflowLayoutLastBuild = false
    private var _selectedIndexLastBuild: Int? = null
    private var _overflowAvailableWidthLastBuild: Dp? = null
    private var _centerSelectedScheduled = false
    private var _overflowSegmentWidths: List<Dp> = emptyList()

    private val _selectedIndex: Int
        get() {
            val index = widget.segments.indexOfFirst { it.value == widget.value }
            return if (index < 0) 0 else index
        }

    @Composable
    override fun build() {
        if (widget.segments.isEmpty()) return
        val scrollState = rememberScrollState()
        _scrollController = scrollState
        scope = rememberCoroutineScope()
        density = LocalDensity.current.density
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }.collect { _handleScrollChanged() }
        }

        BoxWithConstraints(widget.modifier) {
            val contentWidths = widget.segments.map { SegmentedControl.measureSegmentContentWidth(context, it) }
            val preferredWidths = contentWidths.map { it + _segmentPadding * 2 }
            val minimumWidths = contentWidths.map { it + _minimumSegmentPadding * 2 }
            val preferredWidth = _sum(preferredWidths)
            val minimumWidth = _sum(minimumWidths)
            val constrained = constraints.hasBoundedWidth
            val availableWidth = if (constrained) maxWidth.coerceAtLeast(0.dp) else preferredWidth
            if (availableWidth <= 0.dp) return@BoxWithConstraints

            val selectedIndex = _selectedIndex.coerceIn(0, widget.segments.size - 1)
            val usesOverflowLayout = constrained && minimumWidth > availableWidth
            if (usesOverflowLayout) {
                val lastWidth = _overflowAvailableWidthLastBuild
                val overflowLayoutChanged =
                    !_overflowLayoutLastBuild ||
                        lastWidth == null ||
                        abs((lastWidth - availableWidth).value) > 0.5f ||
                        _segmentWidthsDiffer(_overflowSegmentWidths, preferredWidths)
                _overflowSegmentWidths = preferredWidths
                _scheduleSelectedCenterIfNeeded(selectedIndex, overflowLayoutChanged = overflowLayoutChanged)
                _overflowLayoutLastBuild = true
                _overflowAvailableWidthLastBuild = availableWidth
                _selectedIndexLastBuild = selectedIndex
                Box(Modifier.then(if (constrained) Modifier.fillMaxWidth() else Modifier.width(preferredWidth)).height(widget.height)) {
                    _buildOverflowLayout(scrollState, preferredWidths, selectedIndex)
                }
                return@BoxWithConstraints
            }

            _overflowLayoutLastBuild = false
            _overflowAvailableWidthLastBuild = null
            _selectedIndexLastBuild = selectedIndex
            val targetWidth =
                when {
                    widget.expanded && constrained -> availableWidth
                    constrained && preferredWidth > availableWidth -> availableWidth
                    else -> preferredWidth
                }
            val fit =
                _fitSegmentWidths(
                    contentWidths = contentWidths,
                    preferredWidths = preferredWidths,
                    minimumWidth = minimumWidth,
                    preferredWidth = preferredWidth,
                    targetWidth = targetWidth,
                )
            Box(Modifier.then(if (widget.expanded && constrained) Modifier.fillMaxWidth() else Modifier.width(targetWidth)).height(widget.height)) {
                _buildTrack(segmentWidths = fit.widths, horizontalPadding = fit.horizontalPadding, selectedIndex = selectedIndex)
            }
        }
    }

    @Composable
    private fun _buildOverflowLayout(scrollState: ScrollState, segmentWidths: List<Dp>, selectedIndex: Int) {
        val iconButtonSize = widget.height
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
            if (_showJumpToStart) {
                _SegmentControlIconButton(
                    tooltip = "回到开头",
                    icon = Icons.Rounded.KeyboardDoubleArrowLeft,
                    size = iconButtonSize,
                    onPressed = ::_scrollToStart,
                )
                Spacer(Modifier.width(_overflowControlGap))
            }
            Box(
                Modifier
                    .weight(1f)
                    .clipToBounds()
                    .testTag(segmentedControlScrollViewTag)
                    .horizontalScroll(scrollState),
            ) {
                Box(Modifier.size(_sum(segmentWidths), widget.height)) {
                    _buildTrack(segmentWidths = segmentWidths, horizontalPadding = _segmentPadding, selectedIndex = selectedIndex)
                }
            }
            Spacer(Modifier.width(_overflowControlGap))
            _SegmentControlIconButton(
                tooltip = "选择选项",
                icon = Icons.Rounded.Menu,
                size = iconButtonSize,
                selected = _menuOpen,
                onPressed = { unawaited { _showSelectionMenu() } },
                modifier = Modifier.renderBox(_menuAnchorKey),
            )
        }
    }

    @Composable
    private fun _buildTrack(segmentWidths: List<Dp>, horizontalPadding: Dp, selectedIndex: Int) {
        val selectedLeft = _sum(segmentWidths.take(selectedIndex))
        val selectedPressed = _pressedIndex == selectedIndex
        val thumbLeft by animateDpAsState(
            selectedLeft + _thumbInset,
            tween(_durationMillis, easing = Curves.easeOutCubic),
            label = "segmentThumbLeft",
        )
        val thumbWidth by animateDpAsState(
            (segmentWidths[selectedIndex] - _thumbInset * 2).coerceAtLeast(0.dp),
            tween(_durationMillis, easing = Curves.easeOutCubic),
            label = "segmentThumbWidth",
        )
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().decoration(UiColors.fieldFill, RoundedCornerShape(UiRadii.pill), UiColors.border))
            Box(
                Modifier
                    .offset(x = thumbLeft, y = _thumbInset)
                    .size(thumbWidth, (widget.height - _thumbInset * 2).coerceAtLeast(0.dp))
                    .testTag(segmentedControlThumbTag),
            ) {
                _SegmentThumb(pressed = selectedPressed)
            }
            Row(Modifier.matchParentSize()) {
                for (index in widget.segments.indices) {
                    Box(Modifier.width(segmentWidths[index]).height(widget.height)) {
                        _SegmentHitTarget(
                            index = index,
                            segment = widget.segments[index],
                            selected = index == selectedIndex,
                            hovered = index == _hoveredIndex,
                            height = widget.height,
                            horizontalPadding = horizontalPadding,
                            onHoverChanged = { hovered ->
                                setState {
                                    _hoveredIndex = if (hovered) index else null
                                    if (!hovered && _pressedIndex == index) _pressedIndex = null
                                }
                            },
                            onPressedChanged = { pressed -> setState { _pressedIndex = if (pressed) index else null } },
                            onTap = { _selectSegment(index) },
                        )
                    }
                }
            }
        }
    }

    private fun _fitSegmentWidths(
        contentWidths: List<Dp>,
        preferredWidths: List<Dp>,
        minimumWidth: Dp,
        preferredWidth: Dp,
        targetWidth: Dp,
    ): _SegmentWidthFit {
        if (targetWidth >= preferredWidth) {
            val scale = if (preferredWidth <= 0.dp) 1f else targetWidth / preferredWidth
            return _SegmentWidthFit(widths = preferredWidths.map { it * scale }, horizontalPadding = _segmentPadding)
        }

        val contentWidth = _sum(contentWidths)
        val padding =
            if (widget.segments.isEmpty()) {
                _minimumSegmentPadding
            } else {
                ((targetWidth - contentWidth) / (widget.segments.size * 2).toFloat()).coerceIn(_minimumSegmentPadding, _segmentPadding)
            }
        val widths = contentWidths.map { it + padding * 2 }.toMutableList()
        val roundingDelta = targetWidth - _sum(widths)
        if (widths.isNotEmpty() && targetWidth >= minimumWidth) {
            widths[widths.size - 1] = widths[widths.size - 1] + roundingDelta
        }
        return _SegmentWidthFit(widths = widths, horizontalPadding = padding)
    }

    private fun _selectSegment(index: Int) {
        widget.onChanged(widget.segments[index].value)
        if (_overflowLayoutLastBuild) _scheduleCenterSelectedSegment(index)
    }

    private fun _handleScrollChanged() {
        val controller = _scrollController ?: return
        val show = controller.value / density > 1
        if (show == _showJumpToStart || !mounted) return
        setState { _showJumpToStart = show }
    }

    private fun _scrollToStart() {
        val controller = _scrollController ?: return
        scope?.launch { controller.animateScrollTo(0, tween(_durationMillis, easing = Curves.easeOutCubic)) }
    }

    private fun _scheduleSelectedCenterIfNeeded(selectedIndex: Int, overflowLayoutChanged: Boolean) {
        if (!overflowLayoutChanged && _selectedIndexLastBuild == selectedIndex) return
        _scheduleCenterSelectedSegment(selectedIndex)
    }

    private fun _scheduleCenterSelectedSegment(index: Int) {
        if (_centerSelectedScheduled) return
        _centerSelectedScheduled = true
        // Flutter's post-frame callback: run once this frame is laid out.
        scope?.launch {
            withFrameNanos { }
            _centerSelectedScheduled = false
            if (!mounted || !_overflowLayoutLastBuild) return@launch
            _centerSegment(index)
        }
    }

    private fun _centerSegment(index: Int) {
        val controller = _scrollController ?: return
        if (index < 0 || index >= _overflowSegmentWidths.size) return
        val start = _sum(_overflowSegmentWidths.take(index))
        val end = start + _overflowSegmentWidths[index]
        val futureJumpButtonSpace = if (_showJumpToStart || start <= 0.dp) 0.dp else widget.height + _overflowControlGap
        val viewportWidth = (controller.viewportSize / density).dp - futureJumpButtonSpace
        val maxScroll = controller.maxValue / density
        val target = (((start + end) / 2) - (viewportWidth.coerceAtLeast(0.dp) / 2)).value.coerceIn(0f, maxScroll)
        if (abs(target - controller.value / density) < 0.5f) return
        scope?.launch { _animateToCenteredPosition(target, index) }
    }

    private suspend fun _animateToCenteredPosition(target: Float, index: Int) {
        val controller = _scrollController ?: return
        try {
            controller.animateScrollTo((target * density).toInt(), tween(_durationMillis, easing = Curves.easeOutCubic))
        } catch (_: Exception) {
            return
        }
        if (mounted && _overflowLayoutLastBuild) {
            // Showing the left jump button reduces the viewport after the first
            // scroll frame. A second pass uses the final viewport and reaches the
            // exact center, or the nearest scroll edge when centering is impossible.
            _scheduleCenterSelectedSegment(index)
        }
    }

    private suspend fun _showSelectionMenu() {
        if (_menuOpen || widget.segments.isEmpty()) return
        if (!_menuAnchorKey.hasSize) return
        val anchor = _menuAnchorKey.localToGlobal(DpOffset(0.dp, _menuAnchorKey.size.height))
        setState { _menuOpen = true }
        try {
            showUiContextMenu(
                context,
                position = anchor,
                sections =
                    listOf(
                        UiContextMenuSection(
                            widget.segments.mapIndexed { index, segment ->
                                UiContextMenuItem(
                                    label = segment.label,
                                    icon = segment.icon,
                                    selected = index == _selectedIndex,
                                    onPressed = { _selectSegment(index) },
                                )
                            },
                        ),
                    ),
            )
        } finally {
            if (mounted) setState { _menuOpen = false }
        }
    }
}

@Composable
private fun _SegmentControlIconButton(
    tooltip: String,
    icon: ImageVector,
    size: Dp,
    onPressed: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    PressableSurface(
        tooltip = tooltip,
        onPressed = onPressed,
        selected = selected,
        width = size,
        height = size,
        borderRadius = UiRadii.pill,
        backgroundColor = UiColors.fieldFill,
        selectedBackgroundColor = UiColors.selected,
        borderColor = UiColors.border,
        selectedBorderColor = UiColors.accentBorder,
        modifier = modifier,
    ) {
        Icon(icon, size = _iconSize + 2.dp, color = if (selected) UiColors.accent else UiColors.textSecondary)
    }
}

private fun _sum(values: Iterable<Dp>): Dp = values.fold(0.dp) { sum, value -> sum + value }

private fun _segmentWidthsDiffer(left: List<Dp>, right: List<Dp>): Boolean {
    if (left.size != right.size) return true
    for (index in left.indices) {
        if (abs((left[index] - right[index]).value) > 0.5f) return true
    }
    return false
}

@Composable
private fun _SegmentThumb(pressed: Boolean) {
    val color by animateColorAsState(
        if (pressed) Color(0xFFD9D9D6) else UiColors.inverse,
        tween(UiMotion.fast.inWholeMilliseconds.toInt(), easing = UiMotion.curve),
        label = "segmentThumb",
    )
    val shape = RoundedCornerShape(UiRadii.pill)
    Box(Modifier.fillMaxSize().boxShadow(UiShadows.raised, shape).decoration(color, shape))
}

@Composable
private fun <T> _SegmentHitTarget(
    index: Int,
    segment: Segment<T>,
    selected: Boolean,
    hovered: Boolean,
    height: Dp,
    horizontalPadding: Dp,
    onHoverChanged: (Boolean) -> Unit,
    onPressedChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    val foreground =
        when {
            selected -> UiColors.onInverse
            hovered -> UiColors.text
            else -> UiColors.textSecondary
        }
    val iconColor = foreground
    Box(
        Modifier
            .testTag(segmentedControlSegmentTag(index))
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.type == PointerType.Mouse }) continue
                        when (event.type) {
                            PointerEventType.Enter -> onHoverChanged(true)
                            PointerEventType.Exit -> onHoverChanged(false)
                            else -> Unit
                        }
                    }
                }
            }
            .pointerInput(onTap) {
                detectTapGestures(
                    onPress = {
                        onPressedChanged(true)
                        tryAwaitRelease()
                        onPressedChanged(false)
                    },
                    onTap = { onTap() },
                )
            }
            .height(height)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            _ScaleDown {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (segment.icon != null) {
                        Icon(segment.icon, size = _iconSize, color = iconColor)
                        Spacer(Modifier.width(_iconGap))
                    }
                    Text(
                        segment.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        style = _segmentTextStyle.copyWith(color = foreground, fontWeight = if (selected) FontWeight.W600 else FontWeight.W500),
                    )
                }
            }
            if (segment.showBadge) {
                BadgeDot(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-3).dp)
                            .then(segment.badgeKey?.let { Modifier.testTag(it) } ?: Modifier),
                    size = 7.dp,
                )
            }
        }
    }
}

/** Flutter's `FittedBox(fit: BoxFit.scaleDown)`: shrinks the child to fit, never grows it. */
@Composable
private fun _ScaleDown(content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val scale =
            min(
                1f,
                min(
                    if (constraints.hasBoundedWidth && placeable.width > 0) constraints.maxWidth.toFloat() / placeable.width else 1f,
                    if (constraints.hasBoundedHeight && placeable.height > 0) constraints.maxHeight.toFloat() / placeable.height else 1f,
                ),
            )
        val width = (placeable.width * scale).toInt()
        val height = (placeable.height * scale).toInt()
        layout(width, height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

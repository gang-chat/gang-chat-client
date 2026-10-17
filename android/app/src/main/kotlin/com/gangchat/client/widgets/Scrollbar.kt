package com.gangchat.client.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a [rawScrollbar] needs from a scrollable: pixel extents and a way to jump. */
interface ScrollbarAdapter {
    val scrollOffset: Float
    val contentSize: Float
    val viewportSize: Float
    val isScrollInProgress: Boolean

    suspend fun scrollTo(offset: Float)
}

private class _ScrollStateAdapter(private val state: ScrollState) : ScrollbarAdapter {
    override val scrollOffset: Float get() = state.value.toFloat()
    override val contentSize: Float get() = viewportSize + state.maxValue.toFloat()
    override val viewportSize: Float get() = state.viewportSize.toFloat()
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress

    override suspend fun scrollTo(offset: Float) {
        state.scrollTo(offset.toInt())
    }
}

private class _LazyListAdapter(private val state: LazyListState) : ScrollbarAdapter {
    private val averageItemSize: Float
        get() {
            val items = state.layoutInfo.visibleItemsInfo
            if (items.isEmpty()) return 0f
            return items.sumOf { it.size + state.layoutInfo.mainAxisItemSpacing }.toFloat() / items.size
        }

    override val scrollOffset: Float
        get() = state.firstVisibleItemIndex * averageItemSize + state.firstVisibleItemScrollOffset

    override val contentSize: Float
        get() {
            val info = state.layoutInfo
            return max(0f, info.totalItemsCount * averageItemSize - info.mainAxisItemSpacing) + info.beforeContentPadding + info.afterContentPadding
        }

    override val viewportSize: Float
        get() = (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset).toFloat()

    override val isScrollInProgress: Boolean get() = state.isScrollInProgress

    override suspend fun scrollTo(offset: Float) {
        val itemSize = averageItemSize
        if (itemSize <= 0f) return
        val index = (offset / itemSize).toInt().coerceIn(0, max(0, state.layoutInfo.totalItemsCount - 1))
        state.scrollToItem(index, (offset - index * itemSize).toInt())
    }
}

@Composable
fun rememberScrollbarAdapter(state: ScrollState): ScrollbarAdapter = remember(state) { _ScrollStateAdapter(state) }

@Composable
fun rememberScrollbarAdapter(state: LazyListState): ScrollbarAdapter = remember(state) { _LazyListAdapter(state) }

/**
 * Flutter's vertical `RawScrollbar` drawn over the scrollable it decorates
 * (apply to the scrollable's own modifier chain, after its size).
 *
 * Like Flutter it fades in while scrolling and out [timeToFade] after (unless
 * [thumbVisibility]); with [interactive] the thumb can be dragged. Lazy lists
 * estimate their extent from the visible items, so the thumb of a list with
 * uneven rows is approximate.
 */
@Composable
fun Modifier.rawScrollbar(
    adapter: ScrollbarAdapter,
    thickness: Dp = 6.dp,
    radius: Dp = 0.dp,
    thumbColor: Color = Color(0x66BCBCBC),
    thumbVisibility: Boolean = false,
    interactive: Boolean = false,
    crossAxisMargin: Dp = 0.dp,
    mainAxisMargin: Dp = 0.dp,
    minThumbLength: Dp = 18.dp,
    fadeDuration: Int = 300,
    timeToFade: Int = 600,
): Modifier {
    val alpha = remember { Animatable(if (thumbVisibility) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val dragging = remember { BooleanArray(1) }
    val scrolling = adapter.isScrollInProgress
    LaunchedEffect(scrolling, thumbVisibility) {
        if (thumbVisibility) {
            alpha.snapTo(1f)
            return@LaunchedEffect
        }
        if (scrolling) {
            alpha.animateTo(1f, tween(fadeDuration))
        } else if (!dragging[0]) {
            delay(timeToFade.toLong())
            alpha.animateTo(0f, tween(fadeDuration))
        }
    }

    fun thumbGeometry(height: Float, density: Float): Pair<Float, Float>? {
        val content = adapter.contentSize
        val viewport = adapter.viewportSize
        if (content <= viewport + 0.5f || viewport <= 0f) return null
        val track = height - mainAxisMargin.value * density * 2
        val thumbLength = max(minThumbLength.value * density, track * viewport / content).coerceAtMost(track)
        val maxOffset = content - viewport
        val fraction = (adapter.scrollOffset / maxOffset).coerceIn(0f, 1f)
        val top = mainAxisMargin.value * density + (track - thumbLength) * fraction
        return top to thumbLength
    }

    val drawModifier =
        Modifier.drawWithContent {
            drawContent()
            val visibleAlpha = alpha.value
            if (visibleAlpha <= 0f) return@drawWithContent
            val (top, length) = thumbGeometry(size.height, density) ?: return@drawWithContent
            val width = thickness.toPx()
            val left = size.width - crossAxisMargin.toPx() - width
            drawRoundRect(
                color = thumbColor.copy(alpha = thumbColor.alpha * visibleAlpha),
                topLeft = Offset(left, top),
                size = Size(width, length),
                cornerRadius = CornerRadius(radius.toPx().coerceAtMost(width / 2), radius.toPx().coerceAtMost(width / 2)),
            )
        }
    if (!interactive) return this.then(drawModifier)
    return this.then(drawModifier).pointerInput(adapter) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (alpha.value <= 0f) return@awaitEachGesture
            val (top, length) = thumbGeometry(size.height.toFloat(), density) ?: return@awaitEachGesture
            val touchWidth = max(thickness.toPx(), 24.dp.toPx())
            val inThumb =
                down.position.x >= size.width - crossAxisMargin.toPx() - touchWidth &&
                    down.position.y >= top && down.position.y <= top + length
            if (!inThumb) return@awaitEachGesture
            down.consume()
            dragging[0] = true
            val startOffset = adapter.scrollOffset
            val track = size.height - mainAxisMargin.toPx() * 2
            val scrollPerPixel = (adapter.contentSize - adapter.viewportSize) / max(1f, track - length)
            var lastY = down.position.y
            var total = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                total += change.position.y - lastY
                lastY = change.position.y
                change.consume()
                val target = (startOffset + total * scrollPerPixel).coerceIn(0f, adapter.contentSize - adapter.viewportSize)
                scope.launch { adapter.scrollTo(target) }
            }
            dragging[0] = false
            if (!thumbVisibility) {
                scope.launch {
                    delay(timeToFade.toLong())
                    if (!adapter.isScrollInProgress && !dragging[0]) alpha.animateTo(0f, tween(fadeDuration))
                }
            }
        }
    }
}

package com.gangchat.client.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Flutter's `FittedBox` for [BoxFit.scaleDown] and [BoxFit.contain]: the
 * child is laid out unconstrained at its natural size, then painted scaled
 * to fit the incoming constraints and placed by [alignment].
 */
@Composable
fun FittedBox(
    modifier: Modifier = Modifier,
    fit: BoxFit = BoxFit.contain,
    alignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val childWidth = placeable.width.toFloat()
        val childHeight = placeable.height.toFloat()
        if (childWidth <= 0f || childHeight <= 0f) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
        val maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.POSITIVE_INFINITY
        val containScale = min(maxWidth / childWidth, maxHeight / childHeight)
        val scale =
            when (fit) {
                BoxFit.scaleDown -> min(1f, containScale)
                else -> if (containScale.isInfinite()) 1f else containScale
            }
        val scaled = IntSize((childWidth * scale).roundToInt(), (childHeight * scale).roundToInt())
        val width = scaled.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = scaled.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            val offset = alignment.align(scaled, IntSize(width, height), LayoutDirection.Ltr)
            placeable.placeWithLayer(offset.x, offset.y) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

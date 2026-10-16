package com.gangchat.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration

/**
 * The Gang Chat mark (headset wrapped around a speech bubble with a voice
 * waveform), painted as vectors so it always follows the current palette.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    /**
     * Draws the mark on a rounded tile. Without the tile only the strokes are
     * painted, for places that already provide a background.
     */
    tile: Boolean = true,
) {
    val ink = if (tile) _ink else UiColors.accent
    val mark: @Composable (Modifier) -> Unit = { markModifier ->
        Canvas(markModifier) { _paintBrandMark(ink) }
    }
    if (!tile) {
        mark(modifier.size(size))
        return
    }
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier
            .size(size)
            .boxShadow(UiShadows.glow(UiColors.accent, strength = 0.35f), shape)
            .decoration(UiColors.accentStrong, shape)
            .padding(size * 0.12f),
    ) {
        mark(Modifier.fillMaxSize())
    }
}

private val _ink = UiColors.onAccent

// Geometry on a 64-unit grid. Every element keeps at least half a stroke
// of clear space from its neighbours so nothing overlaps at any size, and
// the mark's ink bounds are centred on the grid.
private const val _strokeWidth = 4.4f
private const val _cupTop = 30.5f
private const val _cupWidth = 7.2f
private const val _cupHeight = 18.0f
private const val _leftCupX = 4.0f
private const val _rightCupX = 52.8f

// Headband: lands exactly on the centre of each cup's top edge, so its caps
// merge into the cup outline instead of poking inside it. Flutter's
// `arcToPoint(clockwise: true)` is SVG's sweep flag 1.
private val _headbandPath by lazy {
    val leftCupCenter = _leftCupX + _cupWidth / 2
    val rightCupCenter = _rightCupX + _cupWidth / 2
    val rx = (rightCupCenter - leftCupCenter) / 2
    PathParser().parsePathString("M $leftCupCenter $_cupTop A $rx 22 0 0 1 $rightCupCenter $_cupTop").toPath()
}

// Speech bubble and tail as one outline: the bottom edge stops where the
// tail opens, so no line runs through it.
private val _bubblePath by lazy {
    PathParser()
        .parsePathString(
            "M 26 22.5 L 38 22.5 A 8 8 0 0 1 46 30.5 L 46 39.5 A 8 8 0 0 1 38 47.5 L 34 47.5 L 26 55.5 " +
                "L 26 47.5 A 8 8 0 0 1 18 39.5 L 18 30.5 A 8 8 0 0 1 26 22.5 Z",
        )
        .toPath()
}

private fun DrawScope._paintBrandMark(ink: Color) {
    val scale = minOf(size.width, size.height) / 64f
    val stroke = Stroke(width = _strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    scale(scale, pivot = Offset.Zero) {
        drawPath(_headbandPath, ink, style = stroke)
        for (left in floatArrayOf(_leftCupX, _rightCupX)) {
            drawRoundRect(
                color = ink,
                topLeft = Offset(left, _cupTop),
                size = Size(_cupWidth, _cupHeight),
                cornerRadius = CornerRadius(_cupWidth / 2),
                style = stroke,
            )
        }
        drawPath(_bubblePath, ink, style = stroke)
        // Voice waveform, centred in the bubble.
        drawLine(ink, Offset(25f, 32f), Offset(25f, 38f), strokeWidth = _strokeWidth, cap = StrokeCap.Round)
        drawLine(ink, Offset(32f, 29f), Offset(32f, 41f), strokeWidth = _strokeWidth, cap = StrokeCap.Round)
        drawLine(ink, Offset(39f, 32f), Offset(39f, 38f), strokeWidth = _strokeWidth, cap = StrokeCap.Round)
    }
}

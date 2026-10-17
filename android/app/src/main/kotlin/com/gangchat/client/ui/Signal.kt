package com.gangchat.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration

/**
 * Fills its area with [color] and a faint dot grid, the canvas texture of
 * the Signal look. Without [child] it expands to fill its parent.
 */
@Composable
fun DotGridBackground(
    modifier: Modifier = Modifier,
    color: Color = UiColors.background,
    dotColor: Color = Color(0x16FFFFFF),
    spacing: Dp = 18.dp,
    dotRadius: Dp = 0.8.dp,
    child: (@Composable () -> Unit)? = null,
) {
    val paint =
        Modifier.drawBehind {
            drawRect(color)
            val step = spacing.toPx()
            if (step <= 0f) return@drawBehind
            val points = ArrayList<Offset>()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    points.add(Offset(x, y))
                    x += step
                }
                y += step
            }
            drawPoints(points, PointMode.Points, dotColor, strokeWidth = dotRadius.toPx() * 2, cap = StrokeCap.Round)
        }
    if (child == null) {
        Box(modifier.fillMaxSize().then(paint))
    } else {
        Box(modifier.then(paint), propagateMinConstraints = true) { child() }
    }
}

/** Small monospace uppercase tag, e.g. `● LIVE 3`. */
@Composable
fun SignalTag(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = UiColors.textSecondary,
    /** Solid tag (signal colour background, dark text). */
    filled: Boolean = false,
    /** Leading status dot. */
    dot: Boolean = false,
    icon: ImageVector? = null,
) {
    val foreground = if (filled) UiColors.onAccent else color
    val shape = RoundedCornerShape(UiRadii.pill)
    // Flutter's Container adds the 1 dp border to the 7 dp padding.
    val inset = if (filled) 0.dp else 1.dp
    Row(
        modifier
            .height(20.dp)
            .decoration(
                color = if (filled) color else color.copy(alpha = color.alpha * 0.1f),
                shape = shape,
                borderColor = if (filled) null else color.copy(alpha = color.alpha * 0.28f),
            )
            .padding(horizontal = 7.dp + inset, vertical = inset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).decoration(foreground, CircleShape))
            Spacer(Modifier.width(5.dp))
        }
        if (icon != null) {
            Icon(icon, size = 11.dp, color = foreground)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            style = UiTypography.overline.copyWith(color = foreground, fontSize = 9.5, letterSpacing = 0.9, height = 1.0),
        )
    }
}

/**
 * A frozen equalizer: a row of rounded bars of varying height. Used as the
 * "voice is happening here" glyph. Static on purpose so it never keeps a
 * ticker alive.
 */
@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = UiColors.accent,
    height: Dp = 14.dp,
    barWidth: Dp = 3.dp,
    gap: Dp = 2.dp,
    levels: List<Double> = listOf(0.45, 0.9, 0.6, 1.0, 0.35),
) {
    Row(modifier.height(height), verticalAlignment = Alignment.Bottom) {
        for ((index, level) in levels.withIndex()) {
            if (index > 0) Spacer(Modifier.width(gap))
            Box(
                Modifier
                    .width(barWidth)
                    .height((height * level.toFloat()).coerceIn(barWidth, height))
                    .decoration(color, RoundedCornerShape(barWidth)),
            )
        }
    }
}

/** A dot-matrix numeral with a monospace caption underneath. */
@Composable
fun DotCounter(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = UiColors.text,
    size: Double = 34.0,
) {
    Column(modifier, horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Top) {
        Text(value.coerceIn(0, 99).toString().padStart(2, '0'), style = UiTypography.dot.copyWith(color = color, fontSize = size))
        Spacer(Modifier.height(6.dp))
        Text(label, style = UiTypography.overline)
    }
}

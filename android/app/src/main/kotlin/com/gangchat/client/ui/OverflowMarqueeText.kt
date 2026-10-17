package com.gangchat.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.LocalDefaultTextStyle
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.rememberBuildContext
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keeps a single-line label static while it fits, then moves it between both
 * overflow boundaries with a short pause at either end.
 *
 * This is shared by compact identity and media controls so they use the same
 * overflow threshold, speed, and pause timing instead of falling back to
 * ellipses or maintaining separate marquee implementations.
 *
 * [trackKey] becomes the test tag of the moving track.
 */
@Composable
fun OverflowMarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    trackKey: String? = null,
) {
    val context = rememberBuildContext()
    val density = LocalDensity.current
    // Text merges an inheriting style with DefaultTextStyle while painting.
    // Measure and render with that same resolved style; otherwise an inherited
    // font family, weight or letter spacing can make the real line wider than
    // the marquee's computed travel distance and leave its tail clipped.
    val effectiveStyle = LocalDefaultTextStyle.current.merge(style)
    BoxWithConstraints(modifier) {
        val painter = TextPainter(text, effectiveStyle, maxLines = 1).layout(context)
        val viewportWidth = maxWidth.value.toDouble()
        // Align the text box to a physical-pixel boundary. Fractional glyph
        // advances otherwise risk clipping the final antialiased column even
        // when the logical widths are mathematically equal.
        val devicePixelRatio = density.density
        val textWidth =
            if (devicePixelRatio > 0) ceil(painter.width * devicePixelRatio) / devicePixelRatio else ceil(painter.width)
        val scrollDistance = (textWidth - viewportWidth).coerceAtLeast(0.0)
        val progress = remember { Animatable(0f) }

        // Flutter's `_configure`: restart (with the initial pause) whenever the
        // viewport or the travel distance changes; then bounce between the ends.
        LaunchedEffect(viewportWidth.roundTenth(), scrollDistance.roundTenth(), text, effectiveStyle) {
            progress.snapTo(0f)
            if (scrollDistance <= 0) return@LaunchedEffect
            val milliseconds = (scrollDistance / 28 * 1000).coerceIn(1200.0, 10000.0).roundToInt()
            while (true) {
                delay(850.milliseconds)
                progress.animateTo(1f, tween(milliseconds, easing = LinearEasing))
                delay(850.milliseconds)
                progress.animateTo(0f, tween(milliseconds, easing = LinearEasing))
            }
        }

        if (scrollDistance <= 0) {
            Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, style = effectiveStyle)
            return@BoxWithConstraints
        }
        Box(Modifier.height(painter.height.dp).clipToBounds()) {
            Box(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .offset { IntOffset((-scrollDistance * progress.value * density.density).roundToInt(), 0) }
                    .then(if (trackKey != null) Modifier.testTag(trackKey) else Modifier)
                    .width(textWidth.dp),
            ) {
                Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, style = effectiveStyle)
            }
        }
    }
}

private fun Double.roundTenth(): Long = (this * 10).roundToInt().toLong()

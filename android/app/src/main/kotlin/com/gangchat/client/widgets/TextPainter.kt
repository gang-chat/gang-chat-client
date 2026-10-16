package com.gangchat.client.widgets

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Flutter's `TextPainter` for measuring text outside of layout (button and
 * segment widths, line counts). Sizes are logical pixels (dp); font sizes
 * scale with the system font scale like Flutter's `TextScaler`.
 *
 * ```
 * val painter = TextPainter(label, style, maxLines = 1).layout(context)
 * painter.width   // ceil'd like Flutter's TextPainter.width
 * ```
 *
 * [style] is used as given (Flutter's TextPainter does not merge the ambient
 * `DefaultTextStyle`); merge it yourself when the Dart code did.
 */
class TextPainter(
    val text: AnnotatedString,
    val style: TextStyle,
    val maxLines: Int = Int.MAX_VALUE,
    val softWrap: Boolean = true,
) {
    constructor(text: String, style: TextStyle, maxLines: Int = Int.MAX_VALUE, softWrap: Boolean = true) :
        this(AnnotatedString(text), style, maxLines, softWrap)

    private var result: TextLayoutResult? = null
    private var density: Float = 1f
    private var minWidthPx: Float = 0f
    private var maxWidthPx: Float = Float.POSITIVE_INFINITY

    private val layoutResult: TextLayoutResult
        get() = result ?: throw IllegalStateException("TextPainter.layout() has not been called")

    /** Lays the text out within [maxWidth] (unbounded by default). */
    fun layout(context: BuildContext, minWidth: Dp = 0.dp, maxWidth: Dp = Dp.Infinity): TextPainter {
        val densityValue = context[LocalDensity]
        density = densityValue.density
        val measurer = TextMeasurer(context[LocalFontFamilyResolver], densityValue, LayoutDirection.Ltr, cacheSize = 0)
        val maxPx = if (maxWidth == Dp.Infinity) Constraints.Infinity else (maxWidth.value * density).toInt().coerceAtLeast(0)
        val minPx = (minWidth.value * density).toInt().coerceIn(0, maxPx)
        minWidthPx = minPx.toFloat()
        maxWidthPx = if (maxPx == Constraints.Infinity) Float.POSITIVE_INFINITY else maxPx.toFloat()
        result =
            measurer.measure(
                text = text,
                style = style,
                overflow = TextOverflow.Clip,
                softWrap = softWrap,
                maxLines = maxLines,
                constraints = Constraints(minWidth = minPx, maxWidth = maxPx),
            )
        return this
    }

    /**
     * Flutter's `TextPainter.width`: the max intrinsic width clamped to the
     * layout constraints, ceiled.
     */
    val width: Double
        get() = ceilLogical(layoutResult.multiParagraph.intrinsics.maxIntrinsicWidth.coerceIn(minWidthPx, maxWidthPx))

    /** Laid-out height. */
    val height: Double get() = ceilLogical(layoutResult.multiParagraph.height)

    /** Widest line (Flutter's `maxIntrinsicWidth` for a single line). */
    val maxIntrinsicWidth: Double get() = layoutResult.multiParagraph.intrinsics.maxIntrinsicWidth.toDouble() / density

    val lineCount: Int get() = layoutResult.lineCount

    val didExceedMaxLines: Boolean get() = layoutResult.multiParagraph.didExceedMaxLines

    /** Flutter's `preferredLineHeight`: the height of one line in [style]. */
    val preferredLineHeight: Double
        get() {
            val r = layoutResult
            if (r.lineCount == 0) return 0.0
            return (r.getLineBottom(0) - r.getLineTop(0)).toDouble() / density
        }

    private fun ceilLogical(px: Float): Double = ceil(px.toDouble() / density)
}

package com.gangchat.client.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.gangchat.client.widgets.LocalDefaultTextStyle
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.rememberBuildContext

/**
 * Text with every case-insensitive match of [query] highlighted (lime, bold,
 * tinted background). Without a match it is plain text.
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    highlightStyle: TextStyle? = null,
    maxLines: Int? = null,
    overflow: TextOverflow? = null,
    textAlign: TextAlign? = null,
    softWrap: Boolean? = null,
) {
    val effectiveStyle = style ?: LocalDefaultTextStyle.current
    val spans =
        highlightedText(
            text = text,
            query = query,
            highlightStyle = highlightStyle ?: _defaultHighlightStyle(effectiveStyle),
        )
    if (spans == null) {
        Text(
            text,
            modifier = modifier,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            softWrap = softWrap,
            style = effectiveStyle,
        )
        return
    }
    Text(
        spans,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        softWrap = softWrap,
        style = effectiveStyle,
    )
}

/**
 * Keeps short labels on their natural line count, then allows longer labels
 * to wrap in full while reducing the font only when the comfortable line
 * count is exceeded.
 */
@Composable
fun AdaptiveHighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    highlightStyle: TextStyle? = null,
    comfortableLines: Int = 2,
    maximumFontSizeReduction: Double = 2.0,
    minimumFontSize: Double = 10.0,
    textAlign: TextAlign? = null,
) {
    require(comfortableLines > 0)
    require(maximumFontSizeReduction > 0)
    require(minimumFontSize > 0)
    val context = rememberBuildContext()
    val ambient = LocalDefaultTextStyle.current
    BoxWithConstraints(modifier) {
        val baseStyle = style ?: ambient
        var effectiveStyle = baseStyle
        val width: Dp = maxWidth
        val baseSize: TextUnit = baseStyle.fontSize
        if (width != Dp.Infinity && width.value > 0 && baseSize.isSpecified) {
            // Flutter's TextPainter merges nothing: measure the given style.
            val painter = TextPainter(if (text.isEmpty()) " " else text, baseStyle).layout(context, maxWidth = width)
            val lineCount = painter.lineCount
            if (lineCount > comfortableLines) {
                val reduction = (lineCount - comfortableLines).toDouble().coerceIn(1.0, maximumFontSizeReduction)
                val size = baseSize.value.toDouble()
                effectiveStyle = baseStyle.copy(fontSize = (size - reduction).coerceIn(minimumFontSize, size).sp)
            }
        }
        HighlightedText(
            text = text,
            query = query,
            style = effectiveStyle,
            highlightStyle = highlightStyle,
            textAlign = textAlign,
            softWrap = true,
        )
    }
}

private fun _defaultHighlightStyle(baseStyle: TextStyle): TextStyle =
    baseStyle.copyWith(
        color = UiColors.accent,
        fontWeight = FontWeight.W700,
        backgroundColor = UiColors.accent.copy(alpha = 0.2f),
    )

/**
 * Dart's `_highlightedSpans`: the text with every case-insensitive match of
 * [query] styled with [highlightStyle], or null when nothing matches.
 */
fun highlightedText(text: String, query: String, highlightStyle: TextStyle): AnnotatedString? {
    val needle = query.trim()
    if (text.isEmpty() || needle.isEmpty()) return null

    val lowerText = text.lowercase()
    val lowerNeedle = needle.lowercase()
    var searchStart = 0
    var hasMatch = false
    val spanStyle = SpanStyle(
        color = highlightStyle.color,
        fontWeight = highlightStyle.fontWeight,
        background = highlightStyle.background,
        fontSize = highlightStyle.fontSize,
        fontFamily = highlightStyle.fontFamily,
        letterSpacing = highlightStyle.letterSpacing,
    )
    val result =
        buildAnnotatedString {
            while (searchStart < text.length) {
                val matchStart = lowerText.indexOf(lowerNeedle, searchStart)
                if (matchStart < 0) break
                val matchEnd = (matchStart + needle.length).coerceAtMost(text.length)
                if (matchStart > searchStart) append(text.substring(searchStart, matchStart))
                pushStyle(spanStyle)
                append(text.substring(matchStart, matchEnd))
                pop()
                hasMatch = true
                searchStart = matchEnd
            }
            if (searchStart < text.length) append(text.substring(searchStart))
        }
    return if (hasMatch) result else null
}

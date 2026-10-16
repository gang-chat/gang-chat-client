package com.gangchat.client.widgets

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Flutter's `DefaultTextStyle` value (style + overflow/maxLines defaults). */
val LocalDefaultTextStyle = compositionLocalOf { TextStyle.Default }

/**
 * The non-style fields of Flutter's `DefaultTextStyle` (`maxLines`,
 * `overflow`, `softWrap`) that [Text] falls back to when its own argument is
 * null.
 */
@Immutable
data class DefaultTextProperties(val maxLines: Int? = null, val overflow: TextOverflow? = null, val softWrap: Boolean? = null)

val LocalDefaultTextProperties = compositionLocalOf { DefaultTextProperties() }

/**
 * Flutter's `DefaultTextStyle(style:)` / `DefaultTextStyle.merge(style:,
 * maxLines:, overflow:, softWrap:)`. With [merge] the null arguments keep the
 * ambient values; without it they reset to Flutter's defaults.
 */
@Composable
fun DefaultTextStyle(
    style: TextStyle,
    merge: Boolean = true,
    maxLines: Int? = null,
    overflow: TextOverflow? = null,
    softWrap: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val resolved = if (merge) LocalDefaultTextStyle.current.merge(style) else style
    val ambient = if (merge) LocalDefaultTextProperties.current else DefaultTextProperties()
    val properties =
        DefaultTextProperties(
            maxLines = maxLines ?: ambient.maxLines,
            overflow = overflow ?: ambient.overflow,
            softWrap = softWrap ?: ambient.softWrap,
        )
    CompositionLocalProvider(
        LocalDefaultTextStyle provides resolved,
        LocalDefaultTextProperties provides properties,
        content = content,
    )
}

/**
 * Builds a Compose [TextStyle] with Flutter's text metrics:
 * - [height] is Flutter's line-height multiplier (`fontSize * height`), with
 *   leading split proportionally above/below like Flutter's default
 *   `TextLeadingDistribution.proportional`;
 * - sizes are logical pixels and scale with the system font scale (sp), like
 *   Flutter's `TextScaler`.
 */
fun flutterTextStyle(
    color: Color = Color.Unspecified,
    fontSize: Double? = null,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    letterSpacing: Double? = null,
    height: Double? = null,
    fontFamily: FontFamily? = null,
    decoration: TextDecoration? = null,
): TextStyle {
    val lineHeight: TextUnit =
        when {
            height == null -> TextUnit.Unspecified
            fontSize != null -> (fontSize * height).sp
            else -> height.em
        }
    return TextStyle(
        color = color,
        fontSize = fontSize?.sp ?: TextUnit.Unspecified,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        letterSpacing = letterSpacing?.sp ?: TextUnit.Unspecified,
        lineHeight = lineHeight,
        lineHeightStyle =
            if (height == null) null else LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
        fontFamily = fontFamily,
        textDecoration = decoration,
    )
}

/**
 * Flutter's `Text`: merges [style] over the ambient [DefaultTextStyle].
 * Null [softWrap] / [overflow] / [maxLines] take the ambient
 * [DefaultTextStyle] values (Flutter's defaults: wrap, clip, unlimited).
 */
@Composable
fun Text(
    data: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    textAlign: TextAlign? = null,
    softWrap: Boolean? = null,
    overflow: TextOverflow? = null,
    maxLines: Int? = null,
    selectable: Boolean = false,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    Text(AnnotatedString(data), modifier, style, textAlign, softWrap, overflow, maxLines, selectable, emptyMap(), onTextLayout)
}

/** Flutter's `Text.rich` / `RichText` with an [AnnotatedString]. */
@Composable
fun Text(
    data: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    textAlign: TextAlign? = null,
    softWrap: Boolean? = null,
    overflow: TextOverflow? = null,
    maxLines: Int? = null,
    selectable: Boolean = false,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    var merged = LocalDefaultTextStyle.current
    if (style != null) merged = merged.merge(style)
    if (textAlign != null) merged = merged.copy(textAlign = textAlign)
    val defaults = LocalDefaultTextProperties.current
    val text: @Composable () -> Unit = {
        BasicText(
            text = data,
            modifier = modifier,
            style = merged,
            onTextLayout = onTextLayout,
            overflow = overflow ?: defaults.overflow ?: TextOverflow.Clip,
            softWrap = softWrap ?: defaults.softWrap ?: true,
            maxLines = maxLines ?: defaults.maxLines ?: Int.MAX_VALUE,
            inlineContent = inlineContent,
        )
    }
    if (selectable) SelectionContainer { text() } else text()
}

package com.gangchat.client.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flutter's `IconThemeData`: the ambient icon colour and size. Null fields
 * inherit from the enclosing [IconTheme].
 */
@Immutable
data class IconThemeData(val color: Color? = null, val size: Dp? = null) {
    /** Flutter's `IconThemeData.merge`: [other]'s non-null fields win. */
    fun merge(other: IconThemeData?): IconThemeData =
        if (other == null) this else IconThemeData(other.color ?: color, other.size ?: size)
}

/**
 * The ambient [IconThemeData]. The fallback matches the app's dark
 * `ThemeData` (white, 24).
 */
val LocalIconTheme = compositionLocalOf { IconThemeData(color = Color.White, size = 24.dp) }

/** Flutter's `IconTheme(data:)` / `IconTheme.merge(data:)`. */
@Composable
fun IconTheme(data: IconThemeData, merge: Boolean = true, content: @Composable () -> Unit) {
    val resolved = if (merge) LocalIconTheme.current.merge(data) else data
    CompositionLocalProvider(LocalIconTheme provides resolved, content = content)
}

/** Semantics key carrying the [ImageVector] an [Icon] paints (Flutter's `find.byIcon`). */
val IconSemanticsKey = SemanticsPropertyKey<ImageVector>("Icon")

var SemanticsPropertyReceiver.iconData by IconSemanticsKey

/**
 * Flutter's `Icon(icon, size:, color:, semanticLabel:)`: a square of [size]
 * (ambient [IconTheme] size by default) painting [icon] tinted with [color]
 * (ambient colour by default). A null [icon] keeps the empty square, like
 * Flutter.
 */
@Composable
fun Icon(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    color: Color? = null,
    semanticLabel: String? = null,
) {
    val theme = LocalIconTheme.current
    val resolvedSize = size ?: theme.size ?: 24.dp
    val resolvedColor = color ?: theme.color ?: Color.White
    val base =
        modifier
            .size(resolvedSize)
            .semantics {
                if (icon != null) iconData = icon
                if (semanticLabel != null) contentDescription = semanticLabel
            }
    if (icon == null) {
        Box(base)
        return
    }
    val painter = rememberVectorPainter(icon)
    // Like Flutter's Icon, a box forced larger than [size] keeps the glyph at
    // [size], centred.
    Box(base, contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(resolvedSize).paint(painter, colorFilter = ColorFilter.tint(resolvedColor)))
    }
}

package com.gangchat.client.widgets

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flutter's `BoxDecoration(color:, border: Border.all(color:, width:),
 * borderRadius:/shape:)`, painted *behind* the content like `DecoratedBox`
 * (Compose's `Modifier.border` draws over it). The border sits inside the
 * box (Flutter's `BorderSide.strokeAlignInside`). A transparent [borderColor]
 * or null paints no border, like Flutter's `Border.all` with a zero alpha.
 */
fun Modifier.decoration(
    color: Color = Color.Transparent,
    shape: Shape = RectangleShape,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
): Modifier =
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val width = borderWidth.toPx()
        val paintsBorder = borderColor != null && borderColor.alpha > 0f && width > 0f
        var borderPath: Path? = null
        var genericPath: Path? = null
        if (paintsBorder) {
            when (outline) {
                is Outline.Rectangle -> {
                    borderPath =
                        Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(outline.rect)
                            addRect(outline.rect.deflate(width))
                        }
                }
                is Outline.Rounded -> {
                    val outer = outline.roundRect
                    val inner =
                        RoundRect(
                            left = outer.left + width,
                            top = outer.top + width,
                            right = outer.right - width,
                            bottom = outer.bottom - width,
                            topLeftCornerRadius = outer.topLeftCornerRadius.deflate(width),
                            topRightCornerRadius = outer.topRightCornerRadius.deflate(width),
                            bottomRightCornerRadius = outer.bottomRightCornerRadius.deflate(width),
                            bottomLeftCornerRadius = outer.bottomLeftCornerRadius.deflate(width),
                        )
                    borderPath =
                        Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRoundRect(outer)
                            addRoundRect(inner)
                        }
                }
                is Outline.Generic -> genericPath = outline.path
            }
        }
        onDrawBehind {
            if (color.alpha > 0f) drawOutline(outline, color)
            if (borderColor != null) {
                borderPath?.let { drawPath(it, borderColor) }
                genericPath?.let { path ->
                    // A stroke twice as wide, clipped to the shape, keeps the
                    // visible half inside like Flutter.
                    clipPath(path) { drawPath(path, borderColor, style = Stroke(width * 2)) }
                }
            }
        }
    }

private fun CornerRadius.deflate(width: Float) = CornerRadius((x - width).coerceAtLeast(0f), (y - width).coerceAtLeast(0f))

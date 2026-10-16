package com.gangchat.client.widgets

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Flutter's `BoxShadow` (logical pixels = dp). */
@Immutable
data class BoxShadow(
    val color: Color = Color(0xFF000000),
    val offset: DpOffset = DpOffset.Zero,
    val blurRadius: Dp = 0.dp,
    val spreadRadius: Dp = 0.dp,
)

/**
 * Paints Flutter `BoxDecoration.boxShadow`s behind the content, in list order
 * (first shadow at the bottom), for a box of [shape].
 */
fun Modifier.boxShadow(shadows: List<BoxShadow>, shape: Shape = RectangleShape): Modifier {
    var result = this
    for (shadow in shadows) {
        result =
            result.dropShadow(
                shape,
                Shadow(
                    radius = shadow.blurRadius,
                    color = shadow.color,
                    spread = shadow.spreadRadius,
                    offset = shadow.offset,
                ),
            )
    }
    return result
}

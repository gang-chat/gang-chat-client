package com.gangchat.client.widgets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import kotlin.math.min

/** Flutter's `BoxFit`. */
enum class BoxFit { fill, contain, cover, fitWidth, fitHeight, none, scaleDown }

/** The Compose [ContentScale] that paints like [BoxFit] (null is Flutter's image default, `scaleDown`). */
fun BoxFit?.toContentScale(): ContentScale =
    when (this) {
        BoxFit.fill -> ContentScale.FillBounds
        BoxFit.contain -> ContentScale.Fit
        BoxFit.cover -> ContentScale.Crop
        BoxFit.fitWidth -> ContentScale.FillWidth
        BoxFit.fitHeight -> ContentScale.FillHeight
        BoxFit.none -> ContentScale.None
        BoxFit.scaleDown, null -> ContentScale.Inside
    }

/**
 * Flutter's `applyBoxFit(fit, inputSize, outputSize).destination`: the size
 * painted inside [outputSize].
 */
fun applyBoxFitDestination(fit: BoxFit, inputSize: Size, outputSize: Size): Size {
    if (inputSize.width <= 0f || inputSize.height <= 0f || outputSize.width <= 0f || outputSize.height <= 0f) return Size.Zero
    return when (fit) {
        BoxFit.fill, BoxFit.cover -> outputSize
        BoxFit.contain ->
            if (outputSize.width / outputSize.height > inputSize.width / inputSize.height) {
                Size(inputSize.width * outputSize.height / inputSize.height, outputSize.height)
            } else {
                Size(outputSize.width, inputSize.height * outputSize.width / inputSize.width)
            }
        BoxFit.fitWidth -> Size(outputSize.width, inputSize.height * outputSize.width / inputSize.width)
        BoxFit.fitHeight -> Size(inputSize.width * outputSize.height / inputSize.height, outputSize.height)
        BoxFit.none -> Size(min(inputSize.width, outputSize.width), min(inputSize.height, outputSize.height))
        BoxFit.scaleDown -> {
            val contained = applyBoxFitDestination(BoxFit.contain, inputSize, outputSize)
            if (contained.width > inputSize.width) inputSize else contained
        }
    }
}

/** Flutter's `Alignment.center.inscribe(size, Offset.zero & container)`. */
fun inscribeCentered(size: Size, container: Size): Rect =
    Rect(Offset((container.width - size.width) / 2f, (container.height - size.height) / 2f), size)

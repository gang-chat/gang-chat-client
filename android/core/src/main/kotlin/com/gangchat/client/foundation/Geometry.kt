package com.gangchat.client.foundation

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * dart:ui's `Size` in logical pixels, for framework-free code in `:core`
 * (Compose code converts a `DpSize` with `Size(width.value.toDouble(),
 * height.value.toDouble())`).
 */
class Size(val width: Double, val height: Double) {
    val shortestSide: Double get() = min(abs(width), abs(height))

    val longestSide: Double get() = max(abs(width), abs(height))

    val isEmpty: Boolean get() = width <= 0.0 || height <= 0.0

    val aspectRatio: Double
        get() {
            if (height != 0.0) return width / height
            if (width > 0.0) return Double.POSITIVE_INFINITY
            if (width < 0.0) return Double.NEGATIVE_INFINITY
            return 0.0
        }

    override fun equals(other: Any?): Boolean = other is Size && other.width == width && other.height == height

    override fun hashCode(): Int = java.util.Objects.hash(width, height)

    override fun toString(): String =
        "Size(${String.format(Locale.ROOT, "%.1f", width)}, ${String.format(Locale.ROOT, "%.1f", height)})"

    companion object {
        val zero: Size = Size(0.0, 0.0)
    }
}

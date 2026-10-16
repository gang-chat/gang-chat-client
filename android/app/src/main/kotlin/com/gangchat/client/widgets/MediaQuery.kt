package com.gangchat.client.widgets

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp

/** Flutter's `EdgeInsets` (logical pixels = dp). */
@Immutable
data class EdgeInsets(val left: Dp = 0.dp, val top: Dp = 0.dp, val right: Dp = 0.dp, val bottom: Dp = 0.dp) {
    val horizontal: Dp get() = left + right
    val vertical: Dp get() = top + bottom

    operator fun plus(other: EdgeInsets) = EdgeInsets(left + other.left, top + other.top, right + other.right, bottom + other.bottom)

    operator fun minus(other: EdgeInsets) = EdgeInsets(left - other.left, top - other.top, right - other.right, bottom - other.bottom)

    fun copyWith(left: Dp? = null, top: Dp? = null, right: Dp? = null, bottom: Dp? = null) =
        EdgeInsets(left ?: this.left, top ?: this.top, right ?: this.right, bottom ?: this.bottom)

    internal fun clampNonNegative() =
        EdgeInsets(left.coerceAtLeast(0.dp), top.coerceAtLeast(0.dp), right.coerceAtLeast(0.dp), bottom.coerceAtLeast(0.dp))

    companion object {
        val zero = EdgeInsets()

        fun all(value: Dp) = EdgeInsets(value, value, value, value)

        fun symmetric(horizontal: Dp = 0.dp, vertical: Dp = 0.dp) = EdgeInsets(horizontal, vertical, horizontal, vertical)

        fun only(left: Dp = 0.dp, top: Dp = 0.dp, right: Dp = 0.dp, bottom: Dp = 0.dp) = EdgeInsets(left, top, right, bottom)

        fun fromLTRB(left: Dp, top: Dp, right: Dp, bottom: Dp) = EdgeInsets(left, top, right, bottom)
    }
}

/** Applies Flutter [EdgeInsets] as padding (left/right are absolute, like Flutter). */
fun Modifier.padding(insets: EdgeInsets): Modifier =
    this.then(Modifier.padding(start = insets.left, top = insets.top, end = insets.right, bottom = insets.bottom))

/** Flutter's `TextScaler` (linear). */
@Immutable
data class TextScaler(val textScaleFactor: Float) {
    fun scale(fontSize: Float): Float = fontSize * textScaleFactor

    fun scale(fontSize: Double): Double = fontSize * textScaleFactor

    companion object {
        val noScaling = TextScaler(1f)
    }
}

enum class Orientation { portrait, landscape }

enum class Brightness { dark, light }

/** Flutter's `MediaQueryData` in dp. */
@Immutable
data class MediaQueryData(
    val size: DpSize = DpSize.Zero,
    val devicePixelRatio: Float = 1f,
    val textScaler: TextScaler = TextScaler.noScaling,
    val platformBrightness: Brightness = Brightness.light,
    /** System UI (status/navigation bars, cutout) not overlapped by [viewInsets]. */
    val padding: EdgeInsets = EdgeInsets.zero,
    /** Fully obscured areas, i.e. the on-screen keyboard. */
    val viewInsets: EdgeInsets = EdgeInsets.zero,
    /** System UI insets regardless of the keyboard. */
    val viewPadding: EdgeInsets = EdgeInsets.zero,
) {
    val orientation: Orientation get() = if (size.width > size.height) Orientation.landscape else Orientation.portrait

    fun removePadding(left: Boolean = false, top: Boolean = false, right: Boolean = false, bottom: Boolean = false): MediaQueryData =
        copy(
            padding = padding.copyWith(
                left = if (left) 0.dp else null,
                top = if (top) 0.dp else null,
                right = if (right) 0.dp else null,
                bottom = if (bottom) 0.dp else null,
            ),
            viewPadding = viewPadding.copyWith(
                left = if (left) (viewPadding.left - padding.left).coerceAtLeast(0.dp) else null,
                top = if (top) (viewPadding.top - padding.top).coerceAtLeast(0.dp) else null,
                right = if (right) (viewPadding.right - padding.right).coerceAtLeast(0.dp) else null,
                bottom = if (bottom) (viewPadding.bottom - padding.bottom).coerceAtLeast(0.dp) else null,
            ),
        )

    fun removeViewInsets(left: Boolean = false, top: Boolean = false, right: Boolean = false, bottom: Boolean = false): MediaQueryData =
        copy(
            viewInsets = viewInsets.copyWith(
                left = if (left) 0.dp else null,
                top = if (top) 0.dp else null,
                right = if (right) 0.dp else null,
                bottom = if (bottom) 0.dp else null,
            ),
        )
}

val LocalMediaQuery = staticCompositionLocalOf { MediaQueryData() }

/** Flutter's `MediaQuery` static accessors. */
object MediaQuery {
    fun of(context: BuildContext): MediaQueryData = context[LocalMediaQuery]

    fun maybeOf(context: BuildContext): MediaQueryData? = context[LocalMediaQuery]

    fun sizeOf(context: BuildContext): DpSize = of(context).size

    fun paddingOf(context: BuildContext): EdgeInsets = of(context).padding

    fun viewPaddingOf(context: BuildContext): EdgeInsets = of(context).viewPadding

    fun viewInsetsOf(context: BuildContext): EdgeInsets = of(context).viewInsets

    fun textScalerOf(context: BuildContext): TextScaler = of(context).textScaler

    fun devicePixelRatioOf(context: BuildContext): Float = of(context).devicePixelRatio

    fun orientationOf(context: BuildContext): Orientation = of(context).orientation

    fun platformBrightnessOf(context: BuildContext): Brightness = of(context).platformBrightness

    /** Composition-time reads. */
    val current: MediaQueryData @Composable get() = LocalMediaQuery.current
}

/** Provides [MediaQueryData] for the window (the app root does this once). */
@Composable
fun WindowMediaQuery(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val configuration = LocalConfiguration.current
    fun WindowInsets.toEdgeInsets(): EdgeInsets =
        with(density) {
            EdgeInsets(
                getLeft(density, LayoutDirection.Ltr).toDp(),
                getTop(density).toDp(),
                getRight(density, LayoutDirection.Ltr).toDp(),
                getBottom(density).toDp(),
            )
        }
    val viewPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).toEdgeInsets()
    val viewInsets = WindowInsets.ime.toEdgeInsets()
    val data =
        with(density) {
            MediaQueryData(
                size = DpSize(windowSize.width.toDp(), windowSize.height.toDp()),
                devicePixelRatio = density.density,
                textScaler = TextScaler(density.fontScale),
                platformBrightness =
                    if ((configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                        Brightness.dark
                    } else {
                        Brightness.light
                    },
                padding = (viewPadding - viewInsets).clampNonNegative(),
                viewInsets = viewInsets,
                viewPadding = viewPadding,
            )
        }
    CompositionLocalProvider(LocalMediaQuery provides data, content = content)
}

/** Flutter's `MediaQuery.removePadding(context:, removeX:, child:)`. */
@Composable
fun MediaQueryRemovePadding(
    removeLeft: Boolean = false,
    removeTop: Boolean = false,
    removeRight: Boolean = false,
    removeBottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    val data = LocalMediaQuery.current.removePadding(removeLeft, removeTop, removeRight, removeBottom)
    CompositionLocalProvider(LocalMediaQuery provides data, content = content)
}

/** Flutter's `SafeArea`: pads by the system insets and removes them below. */
@Composable
fun SafeArea(
    modifier: Modifier = Modifier,
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true,
    minimum: EdgeInsets = EdgeInsets.zero,
    maintainBottomViewPadding: Boolean = false,
    content: @Composable () -> Unit,
) {
    val data = LocalMediaQuery.current
    val padding = if (maintainBottomViewPadding) data.padding.copyWith(bottom = data.viewPadding.bottom) else data.padding
    val applied =
        EdgeInsets(
            if (left) maxOf(padding.left, minimum.left) else minimum.left,
            if (top) maxOf(padding.top, minimum.top) else minimum.top,
            if (right) maxOf(padding.right, minimum.right) else minimum.right,
            if (bottom) maxOf(padding.bottom, minimum.bottom) else minimum.bottom,
        )
    Box(modifier.padding(applied)) {
        MediaQueryRemovePadding(left, top, right, bottom, content)
    }
}

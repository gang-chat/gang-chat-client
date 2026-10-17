package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.ViewState

/**
 * Keeps the Android live music-box surface anchored while the IME overlays
 * the room. Other home inputs retain the normal resize behavior.
 *
 * Dart also took the platform: every non-Android platform always resized,
 * so only the Android rule is ported.
 */
fun shouldResizeHomeForKeyboard(liveMusicBoxVisible: Boolean): Boolean = !liveMusicBoxVisible

/**
 * Preserves the pre-keyboard home geometry while Android animates the IME.
 *
 * The keyboard insets reach the home tree as progressively shorter
 * constraints while the IME animates (the shell pads by `viewInsets`, as
 * Flutter's `adjustResize` did), even when the surrounding scaffold opts
 * out of inset resizing. The cached viewport prevents those intermediate
 * constraints from repeatedly re-laying out the live room and music box.
 * Current keyboard insets are retained so focused text fields can still keep
 * themselves visible. The cache is refreshed as soon as the keyboard is
 * fully closed.
 */
@Composable
fun HomeKeyboardOverlayViewport(enabled: Boolean, modifier: Modifier = Modifier, child: @Composable () -> Unit) =
    StatefulWidget(HomeKeyboardOverlayViewportWidget(enabled, child, modifier)) { _HomeKeyboardOverlayViewportState() }

class HomeKeyboardOverlayViewportWidget(val enabled: Boolean, val child: @Composable () -> Unit, val modifier: Modifier)

private class _HomeKeyboardOverlayViewportState : ViewState<HomeKeyboardOverlayViewportWidget>() {
    private var _stableSize: DpSize? = null
    private var _stablePadding: EdgeInsets? = null
    private var _stableViewPadding: EdgeInsets? = null

    @Composable
    override fun build() {
        if (!widget.enabled) {
            _stableSize = null
            _stablePadding = null
            _stableViewPadding = null
            Box(widget.modifier, propagateMinConstraints = true) { widget.child() }
            return
        }
        val mediaQuery = MediaQuery.current
        val keyboardVisible = mediaQuery.viewInsets.bottom > 0.dp
        BoxWithConstraints(widget.modifier) {
            val currentSize = DpSize(maxWidth, maxHeight)
            if (!keyboardVisible) {
                _stableSize = currentSize
                _stablePadding = mediaQuery.padding
                _stableViewPadding = mediaQuery.viewPadding
            }
            val stableSize = _stableSize
            val preserveStableViewport = keyboardVisible && stableSize != null
            val viewportSize = if (preserveStableViewport) stableSize!! else currentSize
            val viewportMediaQuery =
                if (preserveStableViewport) {
                    mediaQuery.copy(
                        size = viewportSize,
                        padding = _stablePadding ?: mediaQuery.padding,
                        viewPadding = _stableViewPadding ?: mediaQuery.viewPadding,
                    )
                } else {
                    mediaQuery
                }
            // Flutter's OverflowBox(alignment: topLeft) at the viewport size.
            Box(Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart, unbounded = true)) {
                Box(Modifier.requiredSize(viewportSize), propagateMinConstraints = true) {
                    CompositionLocalProvider(LocalMediaQuery provides viewportMediaQuery) { widget.child() }
                }
            }
        }
    }
}

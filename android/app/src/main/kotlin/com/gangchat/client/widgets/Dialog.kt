package com.gangchat.client.widgets

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Flutter's Material `Dialog` as the app uses it (transparent, no elevation):
 * the keyboard inset plus [insetPadding] around a centered box at least 280
 * wide. The child sees `MediaQuery.viewInsets` removed, as in Flutter.
 */
@Composable
fun Dialog(
    modifier: Modifier = Modifier,
    insetPadding: EdgeInsets = EdgeInsets.symmetric(horizontal = 40.dp, vertical = 24.dp),
    alignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    val media = MediaQuery.current
    val target = media.viewInsets + insetPadding
    // Flutter animates the inset change over 100 ms with Curves.decelerate.
    val left by animateDpAsState(target.left, tween(100, easing = Curves.decelerate), label = "dialogInsetLeft")
    val top by animateDpAsState(target.top, tween(100, easing = Curves.decelerate), label = "dialogInsetTop")
    val right by animateDpAsState(target.right, tween(100, easing = Curves.decelerate), label = "dialogInsetRight")
    val bottom by animateDpAsState(target.bottom, tween(100, easing = Curves.decelerate), label = "dialogInsetBottom")
    Box(
        modifier.fillMaxSize().padding(start = left, top = top, end = right, bottom = bottom),
        contentAlignment = alignment,
    ) {
        CompositionLocalProvider(LocalMediaQuery provides media.removeViewInsets(true, true, true, true)) {
            // Flutter's ConstrainedBox passes its minimum on to the child.
            Box(Modifier.widthIn(min = 280.dp), propagateMinConstraints = true) { content() }
        }
    }
}

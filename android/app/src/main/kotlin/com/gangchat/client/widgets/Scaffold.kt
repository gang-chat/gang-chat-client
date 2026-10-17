package com.gangchat.client.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Flutter's Material `Scaffold(backgroundColor:, body:)` without app bars:
 * [backgroundColor] fills the whole window (behind the system bars, the
 * activity being edge-to-edge), and the body keeps the window's padding.
 * With [resizeToAvoidBottomInset] (Flutter's default) the body ends above the
 * on-screen keyboard and sees `MediaQuery.viewInsets.bottom` removed, like
 * Scaffold's `removeBottomInset`.
 */
@Composable
fun Scaffold(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    resizeToAvoidBottomInset: Boolean = true,
    body: @Composable () -> Unit,
) {
    val media = LocalMediaQuery.current
    val bottomInset = if (resizeToAvoidBottomInset) media.viewInsets.bottom else 0.dp
    val bodyMedia = if (resizeToAvoidBottomInset) media.removeViewInsets(bottom = true) else media
    Box(modifier.fillMaxSize().background(backgroundColor)) {
        Box(Modifier.fillMaxSize().padding(bottom = bottomInset), propagateMinConstraints = true) {
            CompositionLocalProvider(LocalMediaQuery provides bodyMedia, content = body)
        }
    }
}

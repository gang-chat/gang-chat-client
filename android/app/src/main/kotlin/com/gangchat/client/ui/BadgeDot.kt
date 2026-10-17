package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.decoration

@Composable
fun BadgeDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    color: Color = UiColors.danger,
    borderColor: Color = UiColors.surface,
) {
    // Dart wraps the dot in IgnorePointer. A Compose node without pointer
    // input is already invisible to hit testing; `ignorePointer()` would
    // instead make this small dot a hit target (Compose widens it to the
    // 48 dp minimum touch target) whose Initial-pass consumption cancels
    // taps on the segment or button carrying the badge.
    Box(
        modifier
            .size(size)
            .decoration(color, CircleShape, borderColor, borderWidth = 1.4.dp),
    )
}

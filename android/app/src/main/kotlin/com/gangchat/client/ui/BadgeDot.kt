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
import com.gangchat.client.widgets.ignorePointer

@Composable
fun BadgeDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    color: Color = UiColors.danger,
    borderColor: Color = UiColors.surface,
) {
    Box(
        modifier
            .ignorePointer()
            .size(size)
            .decoration(color, CircleShape, borderColor, borderWidth = 1.4.dp),
    )
}

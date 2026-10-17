package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gangchat.client.ui.UiColors

/** The empty home pane: the background colour at the size the parent requires (Dart's `ColoredBox`). */
@Composable
fun HomeContent(modifier: Modifier = Modifier) {
    Box(modifier.background(UiColors.background))
}

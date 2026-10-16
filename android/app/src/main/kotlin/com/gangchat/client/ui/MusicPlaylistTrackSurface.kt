package com.gangchat.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.EdgeInsets

/**
 * Shared song-row surface used by saved playlists and read-only playlist
 * previews. Keeping the padding and decoration here prevents the same track
 * from changing height when it is opened from a different music-box entry.
 */
@Composable
fun MusicPlaylistTrackSurface(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    child: @Composable () -> Unit,
) {
    val background by animateColorAsState(
        if (highlighted) UiColors.selected else UiColors.background,
        tween(90, easing = Curves.easeOutCubic),
        label = "trackBackground",
    )
    val border by animateColorAsState(
        if (highlighted) UiColors.selectedBorder else UiColors.border,
        tween(90, easing = Curves.easeOutCubic),
        label = "trackBorder",
    )
    val shape = RoundedCornerShape(UiRadii.md)
    // Flutter's Container adds the 1 dp border to its 14 dp padding.
    Box(modifier.decoration(background, shape, border).padding(EdgeInsets.all(15.dp)), propagateMinConstraints = true) {
        child()
    }
}

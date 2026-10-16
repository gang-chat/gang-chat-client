package com.gangchat.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

class NavigationItem<T>(val value: T, val label: String, val icon: ImageVector? = null)

@Composable
fun <T> NavigationTabs(
    items: List<NavigationItem<T>>,
    value: T,
    onChanged: (T) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    SegmentedControl(
        value = value,
        onChanged = onChanged,
        expanded = expanded,
        segments = items.map { item -> Segment(value = item.value, label = item.label, icon = item.icon) },
        modifier = modifier,
    )
}

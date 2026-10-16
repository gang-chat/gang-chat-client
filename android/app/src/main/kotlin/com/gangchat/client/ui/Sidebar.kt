package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.DefaultTextStyle
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.IconTheme
import com.gangchat.client.widgets.IconThemeData
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding

class SidebarGroup(val label: String? = null, val items: List<SidebarItem>)

class SidebarItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: String? = null,
    val enabled: Boolean = true,
    val tooltip: String? = null,
)

/** Flutter's `Border(right: BorderSide(color:))`, painted behind the content. */
internal fun Modifier.rightBorder(color: Color, width: Dp = 1.dp): Modifier =
    this.drawBehind {
        val w = width.toPx()
        drawRect(color, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
    }

/** The wide-layout navigation column: grouped pill items with optional header and footer. */
@Composable
fun Sidebar(
    groups: List<SidebarGroup>,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    onItemSelected: ((String) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    width: Dp = 248.dp,
    height: Dp? = null,
    padding: EdgeInsets = EdgeInsets.fromLTRB(12.dp, 18.dp, 12.dp, 14.dp),
    backgroundColor: Color = UiColors.background,
    borderColor: Color = UiColors.border,
) {
    BoxWithConstraints(
        modifier
            .width(width)
            .then(if (height != null) Modifier.height(height) else Modifier)
            .decoration(backgroundColor)
            .rightBorder(borderColor)
            .padding(padding),
    ) {
        val content: @Composable () -> Unit = { _SidebarGroups(groups, selectedId, onItemSelected) }
        if (!constraints.hasBoundedHeight) {
            Column(Modifier.fillMaxWidth()) {
                if (header != null) {
                    header()
                    Spacer(Modifier.height(20.dp))
                }
                content()
                if (footer != null) {
                    Spacer(Modifier.height(20.dp))
                    footer()
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().height(maxHeight)) {
                if (header != null) {
                    header()
                    Spacer(Modifier.height(20.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { content() }
                if (footer != null) {
                    Spacer(Modifier.height(16.dp))
                    footer()
                }
            }
        }
    }
}

@Composable
private fun _SidebarGroups(groups: List<SidebarGroup>, selectedId: String?, onItemSelected: ((String) -> Unit)?) {
    Column(Modifier.fillMaxWidth()) {
        for ((groupIndex, group) in groups.withIndex()) {
            if (groupIndex > 0) Spacer(Modifier.height(14.dp))
            _SidebarGroupBlock(group, selectedId, onItemSelected)
        }
    }
}

@Composable
private fun _SidebarGroupBlock(group: SidebarGroup, selectedId: String?, onItemSelected: ((String) -> Unit)?) {
    Column(Modifier.fillMaxWidth()) {
        val label = group.label
        if (label != null) {
            Text(
                label,
                modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.overline,
            )
        }
        for ((itemIndex, item) in group.items.withIndex()) {
            if (itemIndex > 0) Spacer(Modifier.height(2.dp))
            _SidebarItemButton(
                item = item,
                selected = item.id == selectedId,
                onPressed = if (onItemSelected == null) null else ({ onItemSelected(item.id) }),
            )
        }
    }
}

@Composable
private fun _SidebarItemButton(item: SidebarItem, selected: Boolean, onPressed: (() -> Unit)?) {
    val enabled = item.enabled && onPressed != null
    val foreground =
        when {
            !item.enabled -> UiColors.textMuted
            selected -> UiColors.onInverse
            else -> UiColors.textSecondary
        }
    val accent = if (selected) UiColors.onInverse else UiColors.textMuted

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = if (constraints.hasBoundedWidth) maxWidth else null
        PressableSurface(
            width = width,
            height = 36.dp,
            onPressed = if (enabled) onPressed else null,
            tooltip = item.tooltip,
            enabled = item.enabled,
            selected = selected,
            backgroundColor = Color.Transparent,
            hoverBackgroundColor = UiColors.surfaceRaised,
            selectedBackgroundColor = UiColors.inverse,
            disabledBackgroundColor = Color.Transparent,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            borderRadius = UiRadii.pill,
            padding = EdgeInsets.symmetric(horizontal = 12.dp),
        ) {
            IconTheme(IconThemeData(color = accent, size = 18.dp)) {
                DefaultTextStyle(
                    uiTextStyle(
                        color = foreground,
                        fontSize = 13.0,
                        fontWeight = if (selected) FontWeight.W600 else FontWeight.W500,
                        letterSpacing = 0.0,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon)
                        Spacer(Modifier.width(10.dp))
                        Text(item.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val badge = item.badge
                        if (badge != null) {
                            Spacer(Modifier.width(8.dp))
                            _SidebarBadge(label = badge, selected = selected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun _SidebarBadge(label: String, selected: Boolean) {
    val color = if (selected) UiColors.onAccent else UiColors.textSecondary
    Box(
        Modifier
            .decoration(if (selected) UiColors.accentStrong else UiColors.surfaceHover, RoundedCornerShape(UiRadii.pill))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = uiTextStyle(color = color, fontSize = 11.0, fontWeight = FontWeight.W600, letterSpacing = 0.0),
        )
    }
}

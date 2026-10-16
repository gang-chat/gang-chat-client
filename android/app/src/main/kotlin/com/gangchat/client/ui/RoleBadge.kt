package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding

enum class RoleBadgeTone { member, admin, creator, superuser, neutral }

fun roleBadgeToneForLabel(label: String): RoleBadgeTone =
    when (label.trim()) {
        "成员" -> RoleBadgeTone.member
        "管理员" -> RoleBadgeTone.admin
        "创建者" -> RoleBadgeTone.creator
        "超级用户" -> RoleBadgeTone.superuser
        else -> RoleBadgeTone.neutral
    }

fun roleBadgeForegroundColorForLabel(label: String): Color = _roleBadgeStyle(roleBadgeToneForLabel(label)).foreground

@Composable
fun RoleBadge(
    label: String,
    modifier: Modifier = Modifier,
    query: String = "",
    padding: EdgeInsets = EdgeInsets.symmetric(horizontal = 9.dp, vertical = 5.dp),
    borderRadius: Dp = 999.dp,
    fontSize: Double = 12.0,
    fontWeight: FontWeight = FontWeight.W600,
) {
    val style = _roleBadgeStyle(roleBadgeToneForLabel(label))
    val textStyle = UiTypography.label.copyWith(color = style.foreground, fontSize = fontSize, fontWeight = fontWeight)
    Box(modifier.decoration(style.background, RoundedCornerShape(borderRadius), style.border).padding(padding)) {
        if (query.trim().isEmpty()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = textStyle)
        } else {
            HighlightedText(text = label, query = query, maxLines = 1, overflow = TextOverflow.Ellipsis, style = textStyle)
        }
    }
}

private class _RoleBadgeStyle(val foreground: Color, val background: Color, val border: Color)

private fun _roleBadgeStyle(tone: RoleBadgeTone): _RoleBadgeStyle =
    when (tone) {
        RoleBadgeTone.member -> _RoleBadgeStyle(UiColors.roleMember, UiColors.roleMemberSurface, UiColors.roleMemberBorder)
        RoleBadgeTone.admin -> _RoleBadgeStyle(UiColors.roleAdmin, UiColors.roleAdminSurface, UiColors.roleAdminBorder)
        RoleBadgeTone.creator -> _RoleBadgeStyle(UiColors.roleCreator, UiColors.roleCreatorSurface, UiColors.roleCreatorBorder)
        RoleBadgeTone.superuser ->
            _RoleBadgeStyle(UiColors.roleSuperuser, UiColors.roleSuperuserSurface, UiColors.roleSuperuserBorder)
        RoleBadgeTone.neutral -> _RoleBadgeStyle(UiColors.textSecondary, UiColors.surfacePressed, UiColors.border)
    }

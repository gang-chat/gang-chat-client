package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.RoomMemberPresence
import com.gangchat.client.app.roomMemberPresenceLabel
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding

enum class PresencePillTone { voice, online, offline, suspended }

fun roomMemberPresenceIcon(presence: RoomMemberPresence): ImageVector =
    when (presence) {
        RoomMemberPresence.live -> Icons.Filled.Call
        RoomMemberPresence.online -> Icons.Filled.Circle
        RoomMemberPresence.offline -> Icons.Outlined.Circle
    }

fun roomMemberPresencePillTone(presence: RoomMemberPresence): PresencePillTone =
    when (presence) {
        RoomMemberPresence.live -> PresencePillTone.voice
        RoomMemberPresence.online -> PresencePillTone.online
        RoomMemberPresence.offline -> PresencePillTone.offline
    }

@Composable
fun PresencePill(label: String, tone: PresencePillTone, modifier: Modifier = Modifier) {
    val style = _presencePillStyle(tone)
    Row(
        modifier
            .decoration(style.surface, RoundedCornerShape(999.dp), style.border)
            .padding(EdgeInsets.symmetric(horizontal = 9.dp, vertical = 5.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        _PresenceDot(color = style.foreground, filled = style.dotFilled)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.label.copyWith(color = style.foreground, fontWeight = FontWeight.W600),
        )
    }
}

/** Dart's `PresencePill.member/online/offline/voice/suspended/fromLabel` factories. */
object PresencePill {
    @Composable
    fun member(presence: RoomMemberPresence, modifier: Modifier = Modifier) =
        PresencePill(label = roomMemberPresenceLabel(presence), tone = roomMemberPresencePillTone(presence), modifier = modifier)

    @Composable
    fun online(modifier: Modifier = Modifier, label: String = "在线") = PresencePill(label, PresencePillTone.online, modifier)

    @Composable
    fun offline(modifier: Modifier = Modifier, label: String = "离线") = PresencePill(label, PresencePillTone.offline, modifier)

    @Composable
    fun voice(modifier: Modifier = Modifier, label: String = "语音") = PresencePill(label, PresencePillTone.voice, modifier)

    @Composable
    fun suspended(modifier: Modifier = Modifier, label: String = "封禁中") = PresencePill(label, PresencePillTone.suspended, modifier)

    @Composable
    fun fromLabel(label: String, modifier: Modifier = Modifier) {
        val tone =
            when (label.trim()) {
                "语音" -> PresencePillTone.voice
                "离线" -> PresencePillTone.offline
                "封禁中" -> PresencePillTone.suspended
                else -> PresencePillTone.online
            }
        PresencePill(label, tone, modifier)
    }
}

@Composable
private fun _PresenceDot(color: Color, filled: Boolean) {
    Box(
        Modifier
            .size(10.dp)
            .decoration(if (filled) color else Color.Transparent, CircleShape, if (filled) null else color, 1.4.dp),
    )
}

private class _PresencePillStyle(val foreground: Color, val surface: Color, val border: Color, val dotFilled: Boolean)

private fun _presencePillStyle(tone: PresencePillTone): _PresencePillStyle =
    when (tone) {
        PresencePillTone.voice ->
            _PresencePillStyle(UiColors.presenceVoice, UiColors.presenceVoiceSurface, UiColors.presenceVoiceBorder, dotFilled = true)
        PresencePillTone.online ->
            _PresencePillStyle(UiColors.presenceOnline, UiColors.presenceOnlineSurface, UiColors.presenceOnlineBorder, dotFilled = true)
        PresencePillTone.offline ->
            _PresencePillStyle(UiColors.presenceOffline, UiColors.presenceOfflineSurface, UiColors.presenceOfflineBorder, dotFilled = false)
        PresencePillTone.suspended ->
            _PresencePillStyle(UiColors.danger, UiColors.roleSuperuserSurface, UiColors.dangerBorder, dotFilled = true)
    }

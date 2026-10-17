package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding

// Port of lib/src/home/chat_profile_card.dart (a part of chat_pane.dart).
//
// Dart's `AvatarHoverCardForTest` lives in the test sources
// (`HomeTestSupport.kt`), where the avatar hover card tests were ported first.

/** Wraps a message avatar so hovering (tapping, on Android) it reveals a floating profile card. */
@Composable
internal fun _AvatarHoverCard(
    user: UserSummary,
    currentUser: CurrentUser,
    onResolveProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterCommonRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
    inLive: Boolean = false,
    showRoomRole: Boolean = true,
    child: @Composable () -> Unit,
) {
    // gang-bot is not an account: there is no profile to show.
    if (user.isBot) {
        UiTooltip(message = kGangBotDescription) { child() }
        return
    }
    UserHoverCard(
        user = user,
        currentUser = currentUser,
        onResolveProfile = onResolveProfile,
        onResolveRoomProfile = onResolveRoomProfile,
        onEnterCommonRoom = onEnterCommonRoom,
        profileActionBuilder = profileActionBuilder,
        inLive = inLive,
        showRoomRole = showRoomRole,
        child = child,
    )
}

const val kGangBotDescription: String =
    "gang-bot 是群里的 AI，不是真人账号。@gang-bot 就能和它聊，房间管理员可以在房间设置里关掉它。"

/** gang-bot's avatar: a robot mark instead of initials. */
@Composable
internal fun _GangBotAvatar(size: Dp) {
    Box(
        Modifier
            .size(size)
            .decoration(UiColors.selected, CircleShape, UiColors.selectedBorder),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.SmartToy, color = UiColors.accent, size = size * 0.56f)
    }
}

/** The "AI" tag next to gang-bot's name, so it never passes for a person. */
@Composable
internal fun _GangBotBadge() {
    // Flutter's DecoratedBox paints behind its child without insetting it.
    Box(
        Modifier
            .decoration(UiColors.selected, RoundedCornerShape(4.dp), UiColors.selectedBorder)
            .padding(EdgeInsets.symmetric(horizontal = 4.dp, vertical = 1.dp)),
    ) {
        Text(
            "AI",
            style = UiTypography.label.copyWith(color = UiColors.accent, fontSize = 9.0, fontWeight = FontWeight.W700, height = 1.1),
        )
    }
}

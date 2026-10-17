package com.gangchat.client.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.BadgeDot
import com.gangchat.client.ui.ButtonIconPlain
import com.gangchat.client.ui.LocalAppConfig
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rememberBuildContext
import kotlin.math.max
import kotlin.math.min

// Port of lib/src/home/chat_header.dart (a part of chat_pane.dart).

const val chatHeaderLiveButtonTag = "chat-header-live-button"
const val chatHeaderBackButtonTag = "chat-header-back-button"
const val chatHeaderRoomAvatarTag = "chat-header-room-avatar"
const val chatHeaderRoomTitleTag = "chat-header-room-title"
const val chatHeaderRoomMetaTag = "chat-header-room-meta"
const val chatHeaderLivePreviewTag = "chat-header-live-preview"
const val chatHeaderLivePreviewIconTag = "chat-header-live-preview-icon"
const val chatHeaderLivePreviewCountTag = "chat-header-live-preview-count"
const val roomMembersEntryBadgeTag = "room-members-entry-badge"

@Composable
internal fun _RoomHeader(
    onNavigateBack: (() -> Unit)?,
    title: String,
    avatarLabel: String,
    avatarUrl: String?,
    defaultAvatarKey: String,
    memberCount: Int?,
    onlineMemberCount: Int?,
    liveParticipantCount: Int?,
    liveAvatarPreview: List<UserSummary>,
    hasPendingJoinRequests: Boolean,
    onLivePressed: () -> Unit,
    onMembersPressed: () -> Unit,
    onSettingsPressed: () -> Unit,
) {
    val headerTopInset = _chatHeaderVisualTopInset
    Row(
        Modifier
            .fillMaxWidth()
            .height(_chatHeaderHeight)
            .padding(EdgeInsets.fromLTRB(_chatHeaderHorizontalInset, headerTopInset, _chatHeaderHorizontalInset, _chatHeaderBottomInset)),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.weight(1f)) {
            _LiveChannelHeaderCard(
                onNavigateBack = onNavigateBack,
                title = title,
                avatarLabel = avatarLabel,
                avatarUrl = avatarUrl,
                defaultAvatarKey = defaultAvatarKey,
                memberCount = memberCount,
                onlineMemberCount = onlineMemberCount,
                liveParticipantCount = liveParticipantCount,
                liveAvatarPreview = liveAvatarPreview,
                onPressed = onLivePressed,
            )
        }
        Spacer(Modifier.width(10.dp))
        _RoomHeaderActions(
            hasPendingJoinRequests = hasPendingJoinRequests,
            onMembersPressed = onMembersPressed,
            onSettingsPressed = onSettingsPressed,
        )
    }
}

@Composable
private fun _LiveChannelHeaderCard(
    onNavigateBack: (() -> Unit)?,
    title: String,
    avatarLabel: String,
    avatarUrl: String?,
    defaultAvatarKey: String,
    memberCount: Int?,
    onlineMemberCount: Int?,
    liveParticipantCount: Int?,
    liveAvatarPreview: List<UserSummary>,
    onPressed: () -> Unit,
) {
    val context = rememberBuildContext()
    val liveActive = (liveParticipantCount ?: 0) > 0
    val roomInfo: @Composable (Modifier) -> Unit = { modifier ->
        _LiveHeaderRoomInfo(
            title = title,
            avatarLabel = avatarLabel,
            avatarUrl = avatarUrl,
            defaultAvatarKey = defaultAvatarKey,
            memberCount = memberCount,
            onlineMemberCount = onlineMemberCount,
            liveActive = liveActive,
            modifier = modifier,
        )
    }
    if (!HomeAdaptiveLayout.usesCompactLayout(context)) {
        _buildDesktopCard(liveActive, liveParticipantCount, liveAvatarPreview, onPressed, roomInfo)
        return
    }
    _buildAndroidCard(liveActive, liveParticipantCount, onNavigateBack, onPressed, roomInfo)
}

/** The wide (tablet) header card: the whole card is the live-channel button. */
@Composable
private fun _buildDesktopCard(
    liveActive: Boolean,
    liveParticipantCount: Int?,
    liveAvatarPreview: List<UserSummary>,
    onPressed: () -> Unit,
    roomInfo: @Composable (Modifier) -> Unit,
) {
    PressableSurface(
        modifier = Modifier.fillMaxWidth().testTag(chatHeaderLiveButtonTag),
        height = _liveHeaderCardHeight,
        borderRadius = UiRadii.md,
        backgroundColor = UiColors.surface,
        selectedBackgroundColor = UiColors.selected,
        borderColor = UiColors.border,
        selectedBorderColor = UiColors.selectedBorder,
        selected = liveActive,
        padding = EdgeInsets.symmetric(horizontal = 10.dp),
        tooltip = "进入语音频道",
        onPressed = onPressed,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val previewMaxWidth = if (liveActive) min(_desktopLiveHeaderPreviewMaxWidth.value, maxWidth.value).dp else 0.dp
            val roomInfoWidth =
                max(0f, maxWidth.value - (if (previewMaxWidth > 0.dp) (previewMaxWidth + _liveHeaderSideGap).value else 0f)).dp
            if (roomInfoWidth >= _liveHeaderRoomInfoMinWidth) {
                Box(Modifier.align(Alignment.CenterStart)) { roomInfo(Modifier.width(roomInfoWidth)) }
            }
            if (liveActive && previewMaxWidth >= _desktopLiveHeaderPreviewMinWidth) {
                Box(Modifier.align(Alignment.CenterEnd).widthIn(max = previewMaxWidth)) {
                    _LiveHeaderAvatarPreview(users = liveAvatarPreview, participantCount = liveParticipantCount ?: 0, maxWidth = previewMaxWidth)
                }
            }
        }
    }
}

/** The compact (phone) header card: a back column plus the live-channel tap target. */
@Composable
private fun _buildAndroidCard(
    liveActive: Boolean,
    liveParticipantCount: Int?,
    onNavigateBack: (() -> Unit)?,
    onPressed: () -> Unit,
    roomInfo: @Composable (Modifier) -> Unit,
) {
    PressableSurface(
        modifier = Modifier.fillMaxWidth().testTag(chatHeaderLiveButtonTag),
        height = _liveHeaderCardHeight,
        borderRadius = UiRadii.md,
        backgroundColor = UiColors.surface,
        selectedBackgroundColor = UiColors.selected,
        borderColor = UiColors.border,
        selectedBorderColor = UiColors.selectedBorder,
        selected = liveActive,
        interactive = true,
        padding = EdgeInsets.only(left = _liveHeaderLeftPadding, right = _liveHeaderRightPadding),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (onNavigateBack != null) {
                _LiveHeaderBackColumn(onPressed = onNavigateBack)
                Spacer(Modifier.width(_liveHeaderBackGap))
            }
            UiTooltip(message = "进入语音频道", modifier = Modifier.weight(1f).fillMaxHeight()) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .semantics(mergeDescendants = true) { contentDescription = "进入语音频道" }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onPressed,
                        )
                        .clipToBounds(),
                ) {
                    val previewMaxWidth = if (liveActive) min(_androidLiveHeaderPreviewMaxWidth.value, maxWidth.value).dp else 0.dp
                    val roomInfoWidth =
                        max(0f, maxWidth.value - (if (previewMaxWidth > 0.dp) (previewMaxWidth + _liveHeaderSideGap).value else 0f)).dp
                    if (roomInfoWidth >= _liveHeaderRoomInfoMinWidth) {
                        Box(Modifier.align(Alignment.CenterStart)) { roomInfo(Modifier.width(roomInfoWidth)) }
                    }
                    if (liveActive && previewMaxWidth >= _androidLiveHeaderPreviewMinWidth) {
                        Box(Modifier.align(Alignment.CenterEnd).widthIn(max = previewMaxWidth)) {
                            _LiveHeaderParticipantCount(participantCount = liveParticipantCount ?: 0)
                        }
                    }
                }
            }
        }
    }
}

private val _liveHeaderLeftPadding = 7.dp
private val _liveHeaderRightPadding = 13.dp
private val _liveHeaderBackColumnWidth = 30.dp
private val _liveHeaderBackGap = 3.dp
private val _androidLiveHeaderPreviewMaxWidth = 52.dp
private val _desktopLiveHeaderPreviewMaxWidth = 190.dp
private val _liveHeaderSideGap = 10.dp
private val _liveHeaderRoomInfoMinWidth = 44.dp
private val _androidLiveHeaderPreviewMinWidth = 32.dp
private val _desktopLiveHeaderPreviewMinWidth = 60.dp

@Composable
private fun _LiveHeaderBackColumn(onPressed: () -> Unit) {
    // Dart's `height: double.infinity`: the column spans the card's height.
    ButtonIconPlain(
        modifier = Modifier.fillMaxHeight().testTag(chatHeaderBackButtonTag),
        tooltip = "返回房间列表",
        onPressed = onPressed,
        width = _liveHeaderBackColumnWidth,
        icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack) },
    )
}

@Composable
private fun _LiveHeaderRoomInfo(
    title: String,
    avatarLabel: String,
    avatarUrl: String?,
    defaultAvatarKey: String,
    memberCount: Int?,
    onlineMemberCount: Int?,
    liveActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(
            modifier = Modifier.testTag(chatHeaderRoomAvatarTag),
            label = avatarLabel,
            imageUrl = LocalAppConfig.current.resolveAssetUrl(avatarUrl),
            defaultAvatarKey = defaultAvatarKey,
            size = 34.dp,
            active = liveActive,
            activeBorderWidth = 1.1.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text(
                title,
                modifier = Modifier.testTag(chatHeaderRoomTitleTag),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copyWith(color = UiColors.text, fontWeight = FontWeight.W600),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                _roomMeta(memberCount = memberCount, onlineMemberCount = onlineMemberCount),
                modifier = Modifier.testTag(chatHeaderRoomMetaTag),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copyWith(color = UiColors.textMuted),
            )
        }
    }
}

@Composable
private fun _LiveHeaderParticipantCount(participantCount: Int) {
    if (participantCount <= 0) return
    Row(Modifier.testTag(chatHeaderLivePreviewTag), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            modifier = Modifier.testTag(chatHeaderLivePreviewIconTag),
            color = UiColors.accent,
            size = 18.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$participantCount",
            modifier = Modifier.testTag(chatHeaderLivePreviewCountTag),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.label.copyWith(color = Color.White, fontWeight = FontWeight.W700, fontSize = 13.0),
        )
    }
}

@Composable
private fun _LiveHeaderAvatarPreview(users: List<UserSummary>, participantCount: Int, maxWidth: Dp) {
    if (participantCount <= 0 || users.isEmpty()) return
    val context = rememberBuildContext()
    val config = AppConfigScope.of(context)
    val exactCountLabel = "共 $participantCount 人"
    val overflowCountLabel = "等共 $participantCount 人"
    val labelStyle = UiTypography.label.copyWith(color = Color.White, fontWeight = FontWeight.W600, fontSize = 11.0)
    val avatarSize = 24.dp
    val iconSize = 18.dp
    val overlap = 8.dp
    val gap = 7.dp

    fun measureLabelWidth(label: String): Dp = TextPainter(label, labelStyle, maxLines = 1).layout(context).width.dp

    val maxVisible = min(5, users.size)
    var visibleCount = 0
    var countLabel = ""
    for (count in maxVisible downTo 1) {
        val avatarWidth = avatarSize + (avatarSize - overlap) * (count - 1)
        val candidateLabel = if (participantCount > count) overflowCountLabel else exactCountLabel
        val neededWidth = iconSize + gap + avatarWidth + gap + measureLabelWidth(candidateLabel)
        if (neededWidth <= maxWidth) {
            visibleCount = count
            countLabel = candidateLabel
            break
        }
    }
    if (visibleCount == 0) return
    val visibleUsers = users.take(visibleCount)
    val avatarWidth = avatarSize + (avatarSize - overlap) * (visibleUsers.size - 1)
    Row(Modifier.testTag(chatHeaderLivePreviewTag), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            modifier = Modifier.testTag(chatHeaderLivePreviewIconTag),
            color = UiColors.accent,
            size = iconSize,
        )
        Spacer(Modifier.width(gap))
        // Stack(clipBehavior: Clip.none): the avatars overlap without clipping.
        Box(Modifier.size(avatarWidth, avatarSize)) {
            for ((index, user) in visibleUsers.withIndex()) {
                Avatar(
                    modifier = Modifier.offset(x = (avatarSize - overlap) * index),
                    label = userAvatarLabel(user),
                    imageUrl = config.resolveAssetUrl(user.avatarUrl),
                    defaultAvatarKey = user.defaultAvatarKey,
                    size = avatarSize,
                    active = true,
                    activeBorderWidth = 1.dp,
                )
            }
        }
        Spacer(Modifier.width(gap))
        Text(
            countLabel,
            modifier = Modifier.testTag(chatHeaderLivePreviewCountTag),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = labelStyle,
        )
    }
}

@Composable
private fun _RoomHeaderActions(
    hasPendingJoinRequests: Boolean,
    onMembersPressed: () -> Unit,
    onSettingsPressed: () -> Unit,
) {
    Column(Modifier.width(_headerActionButtonSize)) {
        _HeaderIconButton(
            tooltip = "房间成员",
            icon = Icons.Outlined.Groups,
            showBadge = hasPendingJoinRequests,
            badgeKey = roomMembersEntryBadgeTag,
            onPressed = onMembersPressed,
        )
        Spacer(Modifier.height(_headerActionGap))
        _HeaderIconButton(tooltip = "房间设置", icon = Icons.Filled.MoreHoriz, onPressed = onSettingsPressed)
    }
}

@Composable
private fun _HeaderIconButton(
    tooltip: String,
    icon: ImageVector,
    onPressed: () -> Unit,
    showBadge: Boolean = false,
    badgeKey: String? = null,
) {
    PressableSurface(
        width = _headerActionButtonSize,
        height = _headerActionButtonSize,
        borderRadius = UiRadii.sm,
        tooltip = tooltip,
        padding = EdgeInsets.zero,
        onPressed = onPressed,
        backgroundColor = UiColors.surface,
        borderColor = UiColors.border,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Stack(clipBehavior: Clip.none): the badge sits outside the glyph.
            Box {
                Icon(icon, size = 16.dp, color = UiColors.textSecondary)
                if (showBadge) {
                    BadgeDot(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 5.dp, y = (-5).dp)
                                .then(if (badgeKey != null) Modifier.testTag(badgeKey) else Modifier),
                        size = 8.dp,
                    )
                }
            }
        }
    }
}

internal fun _roomMeta(memberCount: Int?, onlineMemberCount: Int?): String {
    val parts = mutableListOf<String>()
    val members = memberCount ?: 0
    if (members > 0) parts.add("$members 名成员")
    val online = onlineMemberCount ?: 0
    if (online > 0) parts.add("$online 人在线")
    return if (parts.isEmpty()) "就绪" else parts.joinToString(" · ")
}

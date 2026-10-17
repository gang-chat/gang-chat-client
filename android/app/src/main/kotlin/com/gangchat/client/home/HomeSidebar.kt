package com.gangchat.client.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.NetworkLatencyQuality
import com.gangchat.client.app.currentUserPresenceLabel
import com.gangchat.client.app.networkLatencyQuality
import com.gangchat.client.app.networkLatencySignalBars
import com.gangchat.client.app.networkLatencyTooltip
import com.gangchat.client.app.normalizeRoomNotificationPolicy
import com.gangchat.client.app.roomCardAvatarLabel
import com.gangchat.client.app.roomDraftPreview
import com.gangchat.client.app.roomSidebarLastMessageTime
import com.gangchat.client.app.roomSidebarSubtitle
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.FloatingNotice
import com.gangchat.client.ui.FloatingNoticeEmitter
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.HighlightedText
import com.gangchat.client.ui.LatencySignalBadge
import com.gangchat.client.ui.LocalAppConfig
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.rightBorder
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rawScrollbar
import com.gangchat.client.widgets.rememberScrollbarAdapter
import kotlin.time.Duration

private val _sidebarTopPadding = 16.dp
private val _sidebarBottomPadding = 16.dp
private val _homeSidebarHeaderHeight = 30.dp
private val _homeSidebarHeaderGap = 14.dp
private val _serverCardHeight = 68.dp
private val _serverCardGap = 10.dp
private val _serverCardHorizontalPadding = 10.dp
private val _serverListScrollbarGutter = 8.dp
private val _footerButtonSize = 38.dp
private val _footerButtonGap = 8.dp
private val _footerButtonOuterHeight = _footerButtonSize + 3.dp + 5.dp
private val _compactFooterBreakpoint = 130.dp
private val _compactSummaryBreakpoint = 88.dp
private val _latencySignalGoodColor = Color(0xFF26B36F)
private val _latencySignalFairColor = Color(0xFFE0A12A)
private val _latencySignalPoorColor = Color(0xFFE25A5A)
private val _iconShadowColor = Color(0xAA0F1115)

const val homeSidebarRoomScrollbarTag = "home-sidebar-room-scrollbar"
const val homeSidebarRoomListTag = "home-sidebar-room-list"
const val homeSidebarCreateRoomButtonTag = "home-sidebar-create-room-button"
const val homeSidebarNotificationsButtonTag = "home-sidebar-notifications-button"
const val homeSidebarSettingsButtonTag = "home-sidebar-settings-button"
const val homeSidebarUserSummaryTag = "home-sidebar-user-summary"
const val homeSidebarPresenceDotTag = "home-sidebar-presence-dot"

fun homeSidebarRoomTag(id: String) = "home-sidebar-room-$id"

fun homeSidebarRoomMentionTag(id: String) = "home-sidebar-room-mention-$id"

fun homeSidebarRoomDraftTag(id: String) = "home-sidebar-room-draft-$id"

fun homeSidebarRoomPinnedTag(id: String) = "home-sidebar-room-pinned-$id"

fun homeSidebarRoomPendingJoinRequestsTag(id: String) = "home-sidebar-room-pending-join-requests-$id"

fun homeSidebarRoomLiveTag(id: String) = "home-sidebar-room-live-$id"

/**
 * The room list column: the signed-in user's summary bar, an optional
 * [header] (search), the rooms (or [bodyOverride]) and the footer buttons.
 *
 * [includeWindowChromeOffset] keeps the content below Android's status bar
 * (Dart's `WindowChromeInsets.sidebarTopOffset`, the top view padding on
 * Android).
 */
@Composable
fun HomeSidebar(
    width: Dp,
    currentUser: CurrentUser,
    servers: List<RoomCard>,
    timestampNow: DateTime,
    selectedServerId: String?,
    joinedLiveRoomId: String?,
    realtimeReconnecting: Boolean,
    searchQuery: String,
    loading: Boolean,
    error: String?,
    settingsActive: Boolean,
    createRoomActive: Boolean,
    notificationsActive: Boolean,
    logoutActive: Boolean,
    hasPendingNotifications: Boolean,
    pendingNotificationCount: Int,
    onServerSelected: (RoomCard) -> Unit,
    onCreateRoom: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    roomDrafts: Map<String, String> = emptyMap(),
    requestRoundTrip: Duration? = null,
    includeWindowChromeOffset: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    bodyOverride: (@Composable () -> Unit)? = null,
    footerMiddle: (@Composable () -> Unit)? = null,
) {
    val topChromeOffset = if (includeWindowChromeOffset) MediaQuery.current.viewPadding.top else 0.dp
    Box(
        modifier
            .width(width)
            .decoration(UiColors.surfaceLow)
            .rightBorder(UiColors.border)
            .padding(
                EdgeInsets.fromLTRB(
                    sidebarHorizontalPadding,
                    _sidebarTopPadding + topChromeOffset,
                    sidebarHorizontalPadding - _serverListScrollbarGutter,
                    _sidebarBottomPadding,
                ),
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val headerHeight = if (header == null) 0.dp else _homeSidebarHeaderHeight + _homeSidebarHeaderGap
            val availableBodyHeight = maxHeight - headerHeight
            val showSummary = availableBodyHeight >= _compactSummaryBreakpoint
            val showFooter = availableBodyHeight >= _compactFooterBreakpoint
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showSummary) {
                    Box(Modifier.padding(end = _serverListScrollbarGutter)) {
                        _UserSummaryBar(
                            user = currentUser,
                            inLive = joinedLiveRoomId != null,
                            reconnecting = realtimeReconnecting,
                            requestRoundTrip = requestRoundTrip,
                            logoutActive = logoutActive,
                            onLogout = onLogout,
                        )
                    }
                    Spacer(Modifier.height(if (header != null || showFooter) _homeSidebarHeaderGap else 10.dp))
                }
                if (header != null) {
                    Box(Modifier.padding(end = _serverListScrollbarGutter).height(_homeSidebarHeaderHeight).fillMaxWidth(), propagateMinConstraints = true) {
                        header()
                    }
                    Spacer(Modifier.height(_homeSidebarHeaderGap))
                }
                if (bodyOverride != null) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(end = _serverListScrollbarGutter), propagateMinConstraints = true) {
                        bodyOverride()
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), propagateMinConstraints = true) {
                        _buildServerList(
                            servers = servers,
                            loading = loading,
                            error = error,
                            timestampNow = timestampNow,
                            roomDrafts = roomDrafts,
                            selectedServerId = selectedServerId,
                            joinedLiveRoomId = joinedLiveRoomId,
                            searchQuery = searchQuery,
                            onServerSelected = onServerSelected,
                        )
                    }
                    if (showFooter) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.padding(end = _serverListScrollbarGutter)) {
                            _SidebarFooter(
                                settingsActive = settingsActive,
                                createRoomActive = createRoomActive,
                                notificationsActive = notificationsActive,
                                hasPendingNotifications = hasPendingNotifications,
                                pendingNotificationCount = pendingNotificationCount,
                                middle = footerMiddle,
                                onCreateRoom = onCreateRoom,
                                onOpenNotifications = onOpenNotifications,
                                onOpenSettings = onOpenSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun _buildServerList(
    servers: List<RoomCard>,
    loading: Boolean,
    error: String?,
    timestampNow: DateTime,
    roomDrafts: Map<String, String>,
    selectedServerId: String?,
    joinedLiveRoomId: String?,
    searchQuery: String,
    onServerSelected: (RoomCard) -> Unit,
) {
    if (servers.isEmpty() && loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = UiColors.accent,
                strokeWidth = 2.2.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Square,
            )
        }
        return
    }

    if (servers.isEmpty() && error != null) {
        FloatingNoticeEmitter(notices = listOf(FloatingNotice(message = error, tone = FloatingNoticeTone.error, duration = null))) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ErrorOutline, color = UiColors.danger, size = 22.dp)
            }
        }
        return
    }

    _ServerList(
        servers = servers,
        timestampNow = timestampNow,
        roomDrafts = roomDrafts,
        selectedServerId = selectedServerId,
        joinedLiveRoomId = joinedLiveRoomId,
        searchQuery = searchQuery,
        onServerSelected = onServerSelected,
    )
}

@Composable
private fun _ServerList(
    servers: List<RoomCard>,
    timestampNow: DateTime,
    roomDrafts: Map<String, String>,
    selectedServerId: String?,
    joinedLiveRoomId: String?,
    searchQuery: String,
    onServerSelected: (RoomCard) -> Unit,
) {
    val listState = rememberLazyListState()
    // The scrollbar is already laid out inside the app's safe content (Dart
    // passes zero padding on Android so the insets are not applied twice).
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(homeSidebarRoomListTag)
                .rawScrollbar(
                    rememberScrollbarAdapter(listState),
                    thickness = 7.dp,
                    radius = 999.dp,
                    thumbColor = UiColors.textMuted.copy(alpha = 0.82f),
                    interactive = true,
                )
                .testTag(homeSidebarRoomScrollbarTag),
        contentPadding = PaddingValues(end = _serverListScrollbarGutter),
        verticalArrangement = Arrangement.spacedBy(_serverCardGap),
    ) {
        items(servers.size, key = { servers[it].id }) { index ->
            val server = servers[index]
            _ServerCard(
                server = server,
                timestampNow = timestampNow,
                draft = roomDrafts[server.id],
                selected = server.id == selectedServerId,
                voiceJoined = server.id == joinedLiveRoomId,
                searchQuery = searchQuery,
                onPressed = { onServerSelected(server) },
            )
        }
    }
}

@Composable
private fun _SidebarFooter(
    settingsActive: Boolean,
    createRoomActive: Boolean,
    notificationsActive: Boolean,
    hasPendingNotifications: Boolean,
    pendingNotificationCount: Int,
    middle: (@Composable () -> Unit)?,
    onCreateRoom: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(_footerButtonOuterHeight), verticalAlignment = Alignment.CenterVertically) {
        ButtonIcon(
            modifier = Modifier.testTag(homeSidebarCreateRoomButtonTag),
            tooltip = "创建房间",
            icon = { Icon(Icons.Filled.AddCircleOutline) },
            selected = createRoomActive,
            onPressed = onCreateRoom,
            size = _footerButtonSize,
        )
        Spacer(Modifier.width(_footerButtonGap))
        _NotificationFooterButton(
            selected = notificationsActive,
            hasPending = hasPendingNotifications,
            count = pendingNotificationCount,
            onPressed = onOpenNotifications,
        )
        if (middle == null) {
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.width(_footerButtonGap))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { middle() }
            Spacer(Modifier.width(_footerButtonGap))
        }
        ButtonIcon(
            modifier = Modifier.testTag(homeSidebarSettingsButtonTag),
            tooltip = "设置",
            icon = { Icon(Icons.Outlined.Settings) },
            selected = settingsActive,
            onPressed = onOpenSettings,
            size = _footerButtonSize,
        )
    }
}

@Composable
private fun _NotificationFooterButton(selected: Boolean, hasPending: Boolean, count: Int, onPressed: () -> Unit) {
    // The flat button is shorter than the footer row, so centre it in the row
    // like its create-room and settings siblings, and keep the badge pinned
    // to the button's own corner.
    Box(Modifier.size(_footerButtonSize, _footerButtonOuterHeight), contentAlignment = Alignment.Center) {
        Box(Modifier.size(_footerButtonSize)) {
            ButtonIcon(
                modifier = Modifier.testTag(homeSidebarNotificationsButtonTag),
                tooltip = "通知",
                icon = { Icon(Icons.Filled.NotificationsNone) },
                selected = selected,
                onPressed = onPressed,
                size = _footerButtonSize,
            )
            if (hasPending) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)) {
                    _UnreadBadge(count = if (count <= 0) 1 else count)
                }
            }
        }
    }
}

@Composable
private fun _UserSummaryBar(
    user: CurrentUser,
    inLive: Boolean,
    reconnecting: Boolean,
    requestRoundTrip: Duration?,
    logoutActive: Boolean,
    onLogout: () -> Unit,
) {
    val statusLabel = currentUserPresenceLabel(user, inLive = inLive, reconnecting = reconnecting)
    val latencyQuality = networkLatencyQuality(requestRoundTrip)
    val latencyColor = _latencySignalColor(latencyQuality)
    val latencyLabel = networkLatencyTooltip(requestRoundTrip)
    val config = LocalAppConfig.current
    Row(
        Modifier
            .testTag(homeSidebarUserSummaryTag)
            .fillMaxWidth()
            .height(58.dp)
            .decoration(UiColors.surface, RoundedCornerShape(UiRadii.md), UiColors.border)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp)) {
            Avatar(
                label = userAvatarLabel(user.toSummary()),
                imageUrl = config.resolveAssetUrl(user.avatarUrl),
                defaultAvatarKey = user.defaultAvatarKey,
                size = 38.dp,
                showBorder = false,
            )
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 2.dp)) {
                LatencySignalBadge(
                    activeBars = networkLatencySignalBars(requestRoundTrip),
                    activeColor = latencyColor,
                    tooltip = latencyLabel,
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(
                user.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = uiTextStyle(color = UiColors.text, fontSize = 13.0, fontWeight = FontWeight.W600, letterSpacing = 0.0),
            )
            Spacer(Modifier.height(4.dp))
            _SidebarPresenceLabel(label = statusLabel)
        }
        Spacer(Modifier.width(8.dp))
        _FlatIconButton(
            tooltip = "退出登录",
            icon = Icons.AutoMirrored.Filled.Logout,
            color = if (logoutActive) UiColors.accent else UiColors.textMuted,
            size = 30.dp,
            onPressed = onLogout,
        )
    }
}

private fun _latencySignalColor(quality: NetworkLatencyQuality): Color =
    when (quality) {
        NetworkLatencyQuality.good -> _latencySignalGoodColor
        NetworkLatencyQuality.fair -> _latencySignalFairColor
        NetworkLatencyQuality.poor -> _latencySignalPoorColor
        NetworkLatencyQuality.unavailable -> UiColors.textMuted
    }

/** Compact status label with a colored presence dot. */
@Composable
private fun _SidebarPresenceLabel(label: String) {
    val color =
        when (label) {
            "语音" -> UiColors.presenceVoice
            "重连中" -> UiColors.presenceReconnecting
            "离线" -> UiColors.presenceOffline
            else -> UiColors.presenceOnline
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.testTag(homeSidebarPresenceDotTag).size(6.dp).decoration(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.label.copyWith(color = color, fontWeight = FontWeight.W600),
        )
    }
}

@Composable
private fun _FlatIconButton(tooltip: String, icon: ImageVector, color: Color, size: Dp, onPressed: () -> Unit) {
    UiTooltip(message = tooltip) {
        Box(
            Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onPressed,
                )
                .size(size),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, color = color, size = size * 0.54f)
        }
    }
}

@Composable
private fun _ServerCard(
    server: RoomCard,
    timestampNow: DateTime,
    draft: String?,
    selected: Boolean,
    voiceJoined: Boolean,
    searchQuery: String,
    onPressed: () -> Unit,
) {
    val lastMessageTime = roomSidebarLastMessageTime(server, now = timestampNow)
    val draftPreview = roomDraftPreview(draft)
    val subtitle = roomSidebarSubtitle(server)
    val hasUnreadMention = server.unreadMentionCount > 0
    val secondaryColor = if (selected) UiColors.textSecondary else UiColors.textMuted
    PressableSurface(
        modifier = Modifier.testTag(homeSidebarRoomTag(server.id)),
        width = Dp.Infinity,
        height = _serverCardHeight,
        selected = selected,
        backgroundColor = UiColors.surfaceLow,
        selectedBackgroundColor = UiColors.selected,
        pressedBackgroundColor = UiColors.surfacePressed,
        borderColor = UiColors.border,
        selectedBorderColor = UiColors.selectedBorder,
        borderRadius = UiRadii.md,
        padding = EdgeInsets.symmetric(horizontal = _serverCardHorizontalPadding),
        cancelTouchPressOnDrag = true,
        onPressed = onPressed,
    ) {
        Box {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                _ServerAvatar(server = server, selected = selected, voiceJoined = voiceJoined)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HighlightedText(
                            text = server.displayName,
                            query = searchQuery,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = uiTextStyle(color = UiColors.text, fontSize = 13.0, fontWeight = FontWeight.W600, letterSpacing = 0.0),
                        )
                        if (lastMessageTime.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                lastMessageTime,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = UiTypography.label.copyWith(color = secondaryColor, fontSize = 10.5),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (draftPreview == null) {
                        if (hasUnreadMention) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(UiTypography.label.copyWith(color = UiColors.accent, fontWeight = FontWeight.W700).toSpanStyle()) {
                                        append("[@我] ")
                                    }
                                    withStyle(UiTypography.label.copyWith(color = secondaryColor).toSpanStyle()) { append(subtitle) }
                                },
                                modifier = Modifier.testTag(homeSidebarRoomMentionTag(server.id)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = UiTypography.label,
                            )
                        } else {
                            HighlightedText(
                                text = subtitle,
                                query = searchQuery,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = UiTypography.label.copyWith(color = secondaryColor),
                            )
                        }
                    } else {
                        Text(
                            buildAnnotatedString {
                                withStyle(UiTypography.label.copyWith(color = UiColors.accent, fontWeight = FontWeight.W700).toSpanStyle()) {
                                    append("[草稿] ")
                                }
                                withStyle(UiTypography.label.copyWith(color = secondaryColor).toSpanStyle()) { append(draftPreview) }
                            },
                            modifier = Modifier.testTag(homeSidebarRoomDraftTag(server.id)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = UiTypography.label,
                        )
                    }
                }
            }
            if (server.isPinned) {
                Box(Modifier.offset(x = 2.dp - _serverCardHorizontalPadding, y = 2.dp)) {
                    _ShadowedIcon(
                        Icons.Filled.PushPin,
                        modifier = Modifier.testTag(homeSidebarRoomPinnedTag(server.id)),
                        color = UiColors.text,
                        size = 14.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun _ServerAvatar(server: RoomCard, selected: Boolean, voiceJoined: Boolean) {
    val hasLiveParticipants = server.liveParticipantCount > 0
    val notificationPolicy = normalizeRoomNotificationPolicy(server.notificationPolicy)
    val mutedBadge = notificationPolicy == "silent"
    val showPendingJoinRequestBadge = server.unreadCount <= 0 && server.hasPendingJoinRequests && notificationPolicy != "blocked"
    val config = LocalAppConfig.current
    Box(Modifier.size(44.dp)) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Avatar(
                label = roomCardAvatarLabel(server),
                imageUrl = config.resolveAssetUrl(server.avatarUrl),
                defaultAvatarKey = server.defaultAvatarKey,
                size = 40.dp,
                active = selected,
                activeBorderWidth = 1.2.dp,
            )
        }
        if (server.unreadCount > 0) {
            Box(Modifier.align(Alignment.TopEnd)) { _UnreadBadge(count = server.unreadCount, muted = mutedBadge) }
        }
        if (showPendingJoinRequestBadge) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-3).dp, y = 3.dp)) {
                _PendingJoinRequestBadge(muted = mutedBadge, modifier = Modifier.testTag(homeSidebarRoomPendingJoinRequestsTag(server.id)))
            }
        }
        if (hasLiveParticipants) {
            Box(Modifier.align(Alignment.BottomEnd).offset(y = (-1).dp)) {
                _ShadowedIcon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    modifier = Modifier.testTag(homeSidebarRoomLiveTag(server.id)),
                    color = if (voiceJoined) UiColors.accent else Color.White,
                    size = 15.dp,
                )
            }
        }
    }
}

/**
 * Flutter's `Icon(shadows: [Shadow(color: 0xAA0F1115, blurRadius: 3,
 * offset: (0, 1))])`: a blurred copy under the glyph (Compose blurs on API
 * 31+; older devices get the same offset shadow without the blur).
 */
@Composable
private fun _ShadowedIcon(icon: ImageVector, modifier: Modifier, color: Color, size: Dp) {
    Box(modifier.size(size)) {
        Icon(icon, modifier = Modifier.offset(y = 1.dp).blur(1.5.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded), color = _iconShadowColor, size = size)
        Icon(icon, color = color, size = size)
    }
}

@Composable
private fun _PendingJoinRequestBadge(muted: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .decoration(
                if (muted) Color(0xFF737985) else Color(0xFFE14747),
                CircleShape,
                if (muted) Color(0xFF282D35) else Color(0xFF35191D),
                1.1.dp,
            ),
    )
}

@Composable
private fun _UnreadBadge(count: Int, muted: Boolean = false) {
    val label = if (count > 99) "99+" else "$count"
    Box(
        Modifier
            .decoration(
                if (muted) Color(0xFF737985) else Color(0xFFE14747),
                RoundedCornerShape(999.dp),
                if (muted) Color(0xFF282D35) else Color(0xFF35191D),
                1.2.dp,
            )
            .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
            .padding(horizontal = 5.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            maxLines = 1,
            style = uiTextStyle(color = Color.White, fontSize = 10.0, fontWeight = FontWeight.W600, height = 1.0, letterSpacing = 0.0),
        )
    }
}

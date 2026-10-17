package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.SystemMessageEvent
import com.gangchat.client.app.kSystemEventLiveJoined
import com.gangchat.client.app.kSystemEventLiveLeft
import com.gangchat.client.app.kSystemEventRoomDescriptionChanged
import com.gangchat.client.app.kSystemEventRoomJoinPolicyChanged
import com.gangchat.client.app.kSystemEventRoomMemberJoined
import com.gangchat.client.app.kSystemEventRoomMemberLeft
import com.gangchat.client.app.kSystemEventRoomMemberRemoved
import com.gangchat.client.app.kSystemEventRoomNameChanged
import com.gangchat.client.app.kSystemEventRoomRoleChanged
import com.gangchat.client.app.kSystemEventRoomVisibilityChanged
import com.gangchat.client.app.liveParticipantByUserId
import com.gangchat.client.app.roomRoleLabel
import com.gangchat.client.app.systemMessageJoinPolicyLabel
import com.gangchat.client.app.systemMessageRoleChangeOmitsActor
import com.gangchat.client.app.systemMessageRoleLabel
import com.gangchat.client.app.systemMessageRoleVerb
import com.gangchat.client.app.systemMessageVisibilityLabel
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.LocalAppConfig
import com.gangchat.client.ui.ReadOnlySelectableText
import com.gangchat.client.ui.RoleBadge
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiContextMenuItem
import com.gangchat.client.ui.UiContextMenuSection
import com.gangchat.client.ui.UiContextMenuTriggerRegion
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.roleBadgeForegroundColorForLabel
import com.gangchat.client.ui.showUiContextMenu
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rememberBuildContext
import kotlin.time.Duration.Companion.milliseconds

// Part of the chat_messages.dart port: system and removed-message rows and the
// message context menu.

/** One inline part of a system row (Dart returned a `List<Widget>`). */
internal typealias _SystemPart = @Composable () -> Unit

/**
 * Renders a system message exactly as it appears in a text channel.
 *
 * Message-history and other secondary surfaces reuse this widget so inline
 * avatars, profile cards, role colors, and information actions stay
 * interactive instead of falling back to plain text.
 *
 * [wrapAlignment] is Dart's `WrapAlignment` (start / center / end).
 */
@Composable
fun ChatSystemMessageContent(
    message: Message,
    event: SystemMessageEvent,
    currentUser: CurrentUser,
    ownerUserId: String?,
    live: LiveState?,
    modifier: Modifier = Modifier,
    messageActions: ChatMessageActions? = null,
    enableContextMenu: Boolean = true,
    highlighted: Boolean = false,
    showSurface: Boolean = true,
    alignment: Alignment = Alignment.Center,
    wrapAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    textStyle: TextStyle = UiTypography.label,
    avatarSize: Dp = 16.dp,
    onResolveSenderProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
) {
    _SystemMessageContainer(
        message = if (enableContextMenu) message else null,
        actions = if (enableContextMenu) messageActions else null,
        highlighted = highlighted,
        showSurface = showSurface,
        alignment = alignment,
        wrapAlignment = wrapAlignment,
        modifier = modifier,
        children =
            _SystemMessageParts(
                event = event,
                currentUser = currentUser,
                ownerUserId = ownerUserId,
                live = live,
                textStyle = textStyle,
                avatarSize = avatarSize,
                onResolveSenderProfile = onResolveSenderProfile,
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterProfileRoom = onEnterProfileRoom,
                profileActionBuilder = profileActionBuilder,
            ).build(),
    )
}

@Composable
private fun _SystemMessageContainer(
    children: List<_SystemPart>,
    message: Message? = null,
    actions: ChatMessageActions? = null,
    highlighted: Boolean = false,
    showSurface: Boolean = true,
    alignment: Alignment = Alignment.Center,
    wrapAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    modifier: Modifier = Modifier,
) = StatefulWidget(_SystemMessageContainerWidget(children, message, actions, highlighted, showSurface, alignment, wrapAlignment, modifier)) {
    _SystemMessageContainerState()
}

private class _SystemMessageContainerWidget(
    val children: List<_SystemPart>,
    val message: Message?,
    val actions: ChatMessageActions?,
    val highlighted: Boolean,
    val showSurface: Boolean,
    val alignment: Alignment,
    val wrapAlignment: Alignment.Horizontal,
    val modifier: Modifier,
)

private class _SystemMessageContainerState : ViewState<_SystemMessageContainerWidget>() {
    private var _contextMenuActive = false

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun build() {
        val active = _contextMenuActive
        val contentColor = UiColors.surfacePressed.copy(alpha = 0.82f)
        val highlightColor =
            when {
                active -> UiColors.selected
                widget.highlighted -> UiColors.selected.copy(alpha = 0.86f)
                else -> contentColor
            }
        val parts: @Composable () -> Unit = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp, widget.wrapAlignment),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                for (child in widget.children) child()
            }
        }
        val pill = RoundedCornerShape(999.dp)
        val content: @Composable () -> Unit =
            if (widget.showSurface) {
                {
                    val animation = if (widget.highlighted) snap<Color>() else tween(90, easing = Curves.easeOutCubic)
                    val animatedColor by animateColorAsState(highlightColor, animation, label = "systemMessageColor")
                    val animatedBorder by animateColorAsState(
                        if (active || widget.highlighted) UiColors.selectedBorder else UiColors.border,
                        animation,
                        label = "systemMessageBorder",
                    )
                    // AnimatedContainer insets its child by the 1 px border.
                    Box(
                        Modifier
                            .testTag(systemMessageSurfaceTag)
                            .decoration(animatedColor, pill, animatedBorder)
                            .padding(EdgeInsets.symmetric(horizontal = 11.dp, vertical = 7.dp)),
                    ) {
                        Box(Modifier.decoration(highlightColor, pill)) { parts() }
                    }
                }
            } else {
                parts
            }
        val menuMessage = widget.message
        val menuActions = widget.actions
        Box(widget.modifier.fillMaxWidth(), contentAlignment = widget.alignment) {
            Box(Modifier.widthIn(max = _messageMaxWidth + 96.dp)) {
                if (menuMessage == null || menuActions == null) {
                    content()
                } else {
                    _MessageContextMenuRegion(
                        message = menuMessage,
                        actions = menuActions,
                        onContextMenuActiveChanged = ::_setContextMenuActive,
                        child = content,
                    )
                }
            }
        }
    }

    private fun _setContextMenuActive(active: Boolean) {
        if (!mounted || _contextMenuActive == active) return
        setState { _contextMenuActive = active }
    }
}

/** Test tag of a system/removed row's pill (Dart's `AnimatedContainer` under `ChatSystemMessageContent`). */
const val systemMessageSurfaceTag = "system-message-surface"

/** Renders a recalled or force-deleted message with the text-channel layout. */
@Composable
fun ChatRemovedMessageContent(
    message: Message,
    currentUser: CurrentUser,
    ownerUserId: String?,
    live: LiveState?,
    messageActions: ChatMessageActions,
    modifier: Modifier = Modifier,
    enableContextMenu: Boolean = true,
    highlighted: Boolean = false,
    showSurface: Boolean = true,
    alignment: Alignment = Alignment.Center,
    wrapAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    onResolveSenderProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
) {
    fun userChip(user: UserSummary): _SystemPart = {
        _SystemUserChip(
            user = user,
            currentUser = currentUser,
            ownerUserId = ownerUserId,
            onResolveProfile = onResolveSenderProfile,
            onResolveRoomProfile = onResolveRoomProfile,
            onEnterProfileRoom = onEnterProfileRoom,
            profileActionBuilder = profileActionBuilder,
            inLive = liveParticipantByUserId(live, user.id) != null,
        )
    }

    fun removedTextAction(): _SystemPart? {
        if (!message.isRecalled || message.type != "text") return null
        if (message.body.isEmpty()) return null
        if (messageActions.canReeditRecalledText(message)) {
            return {
                _RemovedMessageInlineButton(
                    tag = "message-reedit-${message.id}",
                    icon = Icons.Outlined.Edit,
                    tooltip = "重新编辑",
                    onPressed = { messageActions.onReeditRecalledText(message) },
                )
            }
        }
        if (messageActions.canInspectRecalledText(message)) {
            return { _SystemInfoButton(tag = "message-info-${message.id}", message = message.body) }
        }
        return null
    }

    fun buildParts(): List<_SystemPart> {
        if (message.isForceDeleted) {
            val actor = message.forceDeletedBy
            return buildList {
                if (actor != null) add(userChip(actor))
                add { _systemText(if (actor == null) "消息已被删除" else "删除了一条消息") }
            }
        }

        val actor = message.recalledBy ?: message.sender
        val recalledOwnMessage = actor.id == message.sender.id
        val action = removedTextAction()
        return buildList {
            add(userChip(actor))
            if (recalledOwnMessage) {
                add { _systemTextWithTrailingAction("撤回了一条消息", action) }
            } else {
                add { _systemText("撤回了一条来自") }
                add(userChip(message.sender))
                add { _systemTextWithTrailingAction("的消息", action) }
            }
        }
    }

    _SystemMessageContainer(
        message = if (enableContextMenu) message else null,
        actions = if (enableContextMenu) messageActions else null,
        highlighted = highlighted,
        showSurface = showSurface,
        alignment = alignment,
        wrapAlignment = wrapAlignment,
        modifier = modifier,
        children = buildParts(),
    )
}

@Composable
private fun _RemovedMessageInlineButton(tag: String, icon: ImageVector, tooltip: String, onPressed: (() -> Unit)? = null) {
    UiTooltip(message = tooltip, waitDuration = 350.milliseconds) {
        Box(
            Modifier
                .testTag(tag)
                .size(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = onPressed != null,
                    onClick = { onPressed?.invoke() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, size = 14.dp, color = UiColors.accent)
        }
    }
}

@Composable
private fun _SystemInfoButton(tag: String, message: String) {
    val context = rememberBuildContext()
    HoverCardAnchor(
        modifier = Modifier.testTag(tag),
        resetKey = message,
        cardWidth = hoverInfoCardWidth(context, message),
        gap = 8.dp,
        cardBuilder = { _ -> _SystemInfoCard(message = message) },
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Info, size = 14.dp, color = UiColors.accent)
        }
    }
}

@Composable
private fun _SystemInfoCard(message: String) {
    val hoverScope = HoverCardTapRegionScope.current
    Box(Modifier.padding(EdgeInsets.symmetric(horizontal = 12.dp, vertical = 10.dp))) {
        ReadOnlySelectableText(
            value = message,
            maxLines = 8,
            style = UiTypography.body.copyWith(color = UiColors.text, fontSize = 12.0, height = 1.38),
            contextMenuTapRegionGroupId = hoverScope?.tapRegionGroup,
            onContextMenuOpenChanged = hoverScope?.onOverlayActivityChanged,
        )
    }
}

@Composable
private fun _MessageContextMenuRegion(
    message: Message,
    actions: ChatMessageActions,
    child: @Composable () -> Unit,
    onContextMenuActiveChanged: ((Boolean) -> Unit)? = null,
) {
    val context = rememberBuildContext()
    UiContextMenuTriggerRegion(
        onTriggered = { position ->
            onContextMenuActiveChanged?.invoke(true)
            unawaited {
                try {
                    _showChatMessageContextMenu(context = context, position = position, message = message, actions = actions)
                } finally {
                    onContextMenuActiveChanged?.invoke(false)
                }
            }
        },
        child = child,
    )
}

internal suspend fun _showChatMessageContextMenu(
    context: BuildContext,
    position: DpOffset,
    message: Message,
    actions: ChatMessageActions,
) {
    val sections =
        _messageContextMenuSections(
            context = context,
            message = message,
            actions = actions,
            includeCopy = true,
            includeQuote = true,
            includeDelete = true,
            includeRecall = !message.isRemoved,
        )
    showUiContextMenu(context, position = position, sections = sections)
}

internal fun _messageContextMenuSections(
    context: BuildContext,
    message: Message,
    actions: ChatMessageActions,
    includeCopy: Boolean = true,
    includeQuote: Boolean = true,
    includeDelete: Boolean = true,
    includeRecall: Boolean = true,
): List<UiContextMenuSection> =
    buildList {
        if (includeCopy || (includeQuote && actions.canQuote(message))) {
            add(
                UiContextMenuSection(
                    buildList {
                        if (includeCopy) {
                            add(UiContextMenuItem(label = "复制", shortcut = "Ctrl+C", onPressed = { unawaited { actions.onCopy(context, message) } }))
                        }
                        if (includeQuote && actions.canQuote(message)) {
                            add(UiContextMenuItem(label = "引用", onPressed = { actions.onQuote(message) }))
                        }
                    },
                ),
            )
        }
        if (includeRecall && actions.canRecall(message)) {
            add(UiContextMenuSection(listOf(UiContextMenuItem(label = "撤回", onPressed = { unawaited { actions.onRecall(context, message) } }))))
        }
        if (includeDelete) {
            add(
                UiContextMenuSection(
                    listOf(UiContextMenuItem(label = "删除", onPressed = { unawaited { actions.onDeleteForMe(context, message) } })),
                ),
            )
        }
    }

@Composable
private fun _systemText(value: String) {
    Text(
        value,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = UiTypography.label.copyWith(color = UiColors.textSecondary, fontSize = 12.0),
    )
}

@Composable
private fun _systemTextWithTrailingAction(value: String, action: _SystemPart?) {
    if (action == null) {
        _systemText(value)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        _systemText(value)
        action()
    }
}

private class _SystemMessageParts(
    val event: SystemMessageEvent,
    val currentUser: CurrentUser,
    val ownerUserId: String?,
    val live: LiveState?,
    val textStyle: TextStyle = UiTypography.label,
    val avatarSize: Dp = 16.dp,
    val onResolveSenderProfile: UserProfileResolver? = null,
    val onResolveRoomProfile: RoomProfileResolver? = null,
    val onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    val profileActionBuilder: UserProfileActionBuilder? = null,
) {
    fun build(): List<_SystemPart> {
        val subject = event.subject
        return when (event.event) {
            kSystemEventRoomMemberJoined -> listOf(_userChip(subject), _text("加入了房间"))
            kSystemEventRoomMemberLeft -> listOf(_userChip(subject), _text("离开了房间"))
            kSystemEventRoomMemberRemoved -> {
                val actor = event.actor
                buildList {
                    add(_userChip(subject))
                    if (actor == null) {
                        add(_text("被踢出了房间"))
                    } else {
                        add(_text("被"))
                        add(_userChip(actor))
                        add(_text("踢出了房间"))
                    }
                }
            }
            kSystemEventLiveJoined -> listOf(_userChip(subject), _text("进入了语音频道"))
            kSystemEventLiveLeft -> listOf(_userChip(subject), _text("退出了语音频道"))
            kSystemEventRoomRoleChanged -> {
                val actor = event.actor
                val roleLabel = systemMessageRoleLabel(event.toRole)
                val verb = systemMessageRoleVerb(event)
                val omitActor = systemMessageRoleChangeOmitsActor(event)
                buildList {
                    add(_userChip(subject, roleOverride = event.toRole))
                    if (!omitActor && actor != null) {
                        add(_text("被"))
                        add(_userChip(actor))
                    }
                    add(_text(verb))
                    add {
                        RoleBadge(
                            label = roleLabel,
                            padding = EdgeInsets.symmetric(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 11.0,
                            fontWeight = FontWeight.W700,
                        )
                    }
                }
            }
            kSystemEventRoomNameChanged -> {
                val actor = event.actor ?: event.user
                buildList {
                    add(_textWithInfoButton("房间名称", "原房间名称：${event.oldValue ?: ""}"))
                    _addActorParts(actor)
                    add(_highlightText(_systemChangedValueLabel(event.newValue)))
                }
            }
            kSystemEventRoomDescriptionChanged -> {
                val actor = event.actor ?: event.user
                buildList {
                    add(_textWithInfoButton("房间简介", "原房间简介：${event.oldValue ?: ""}"))
                    _addActorParts(actor)
                    add(_lineBreak())
                    add(_highlightText(_systemChangedValueLabel(event.newValue), maxLines = 4))
                }
            }
            kSystemEventRoomVisibilityChanged -> {
                val actor = event.actor ?: event.user
                buildList {
                    add(_textWithInfoButton("房间可见性", "原可见性：${systemMessageVisibilityLabel(event.oldValue)}"))
                    _addActorParts(actor)
                    add(_highlightText(systemMessageVisibilityLabel(event.newValue)))
                }
            }
            kSystemEventRoomJoinPolicyChanged -> {
                val actor = event.actor ?: event.user
                buildList {
                    add(_textWithInfoButton("房间加入方式", "原加入方式：${systemMessageJoinPolicyLabel(event.oldValue)}"))
                    _addActorParts(actor)
                    add(_highlightText(systemMessageJoinPolicyLabel(event.newValue)))
                }
            }
            else -> {
                val fallback = event.message.body.trim()
                buildList {
                    add(_userChip(subject))
                    if (fallback.isNotEmpty()) add(_text(fallback))
                }
            }
        }
    }

    private fun MutableList<_SystemPart>._addActorParts(actor: UserSummary?) {
        if (actor == null) {
            add(_text("修改为"))
        } else {
            add(_text("被"))
            add(_userChip(actor))
            add(_text("修改为"))
        }
    }

    private fun _userChip(user: UserSummary, roleOverride: String? = null): _SystemPart = {
        _SystemUserChip(
            user = user,
            roleOverride = roleOverride,
            currentUser = currentUser,
            ownerUserId = ownerUserId,
            onResolveProfile = onResolveSenderProfile,
            onResolveRoomProfile = onResolveRoomProfile,
            onEnterProfileRoom = onEnterProfileRoom,
            profileActionBuilder = profileActionBuilder,
            inLive = liveParticipantByUserId(live, user.id) != null,
            textStyle = textStyle,
            avatarSize = avatarSize,
        )
    }

    private fun _text(value: String): _SystemPart = {
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = textStyle.copyWith(color = UiColors.textSecondary))
    }

    private fun _textWithInfoButton(value: String, info: String): _SystemPart = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = textStyle.copyWith(color = UiColors.textSecondary))
            _infoButton(info)
        }
    }

    @Composable
    private fun _infoButton(value: String) {
        _SystemInfoButton(tag = "system-info-${event.message.clientMessageId}-${event.event}", message = value)
    }

    private fun _highlightText(value: String, maxLines: Int = 1): _SystemPart = {
        Text(
            value,
            modifier = Modifier.widthIn(max = _messageMaxWidth),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = textStyle.copyWith(color = UiColors.accent, fontWeight = FontWeight.W700),
        )
    }

    // Dart's `SizedBox(width: _messageMaxWidth + 96, height: 0)`: wider than
    // the row, so the wrap breaks the line here.
    private fun _lineBreak(): _SystemPart = { Spacer(Modifier.width(_messageMaxWidth + 96.dp).height(0.dp)) }

    private fun _systemChangedValueLabel(value: String?): String {
        val normalized = value ?: ""
        if (normalized.isEmpty()) return "（空）"
        return normalized
    }
}

@Composable
private fun _SystemUserChip(
    user: UserSummary,
    currentUser: CurrentUser,
    ownerUserId: String?,
    inLive: Boolean,
    roleOverride: String? = null,
    textStyle: TextStyle = UiTypography.label,
    avatarSize: Dp = 16.dp,
    onResolveProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
) {
    val name = _senderName(user)
    val colorUser = if (roleOverride == null) user else user.copyWith(roomRole = roleOverride)
    Row(verticalAlignment = Alignment.CenterVertically) {
        _AvatarHoverCard(
            user = user,
            currentUser = currentUser,
            onResolveProfile = onResolveProfile,
            onResolveRoomProfile = onResolveRoomProfile,
            onEnterCommonRoom = onEnterProfileRoom,
            profileActionBuilder = profileActionBuilder,
            inLive = inLive,
        ) {
            Avatar(
                label = userAvatarLabel(user),
                imageUrl = LocalAppConfig.current.resolveAssetUrl(user.avatarUrl),
                defaultAvatarKey = user.defaultAvatarKey,
                size = avatarSize,
                activeBorderWidth = 1.dp,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            name,
            modifier = Modifier.widthIn(max = 128.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style =
                textStyle.copyWith(
                    color = chatRoomUsernameColor(user = colorUser, currentUser = currentUser, ownerUserId = ownerUserId),
                    fontWeight = FontWeight.W600,
                ),
        )
    }
}

fun chatRoomUsernameColor(user: UserSummary, currentUser: CurrentUser, ownerUserId: String? = null): Color {
    if (user.id == currentUser.id) return UiColors.accent
    return roleBadgeForegroundColorForLabel(roomRoleLabel(user, ownerUserId = ownerUserId))
}

internal fun _senderName(user: UserSummary): String {
    val roomName = user.roomDisplayName?.trim()
    if (roomName != null && roomName.isNotEmpty()) return roomName
    return user.displayName
}

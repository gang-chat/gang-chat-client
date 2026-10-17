package com.gangchat.client.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.RoomMemberFilterCounts
import com.gangchat.client.app.RoomMemberPermissionState
import com.gangchat.client.app.RoomMemberPresence
import com.gangchat.client.app.RoomMemberPresenceFilter
import com.gangchat.client.app.RoomMemberRoleFilter
import com.gangchat.client.app.joinRequestDetailReasonText
import com.gangchat.client.app.joinRequestFromInvitation
import com.gangchat.client.app.joinRequestSourceText
import com.gangchat.client.app.roomBlacklistActionLabel
import com.gangchat.client.app.roomBlacklistCandidates
import com.gangchat.client.app.roomInviteCandidates
import com.gangchat.client.app.roomMemberDefaultDisplayName
import com.gangchat.client.app.roomMemberDisplayName
import com.gangchat.client.app.roomMemberPresence
import com.gangchat.client.app.roomMemberPresenceFilterLabel
import com.gangchat.client.app.roomMemberPresenceLabel
import com.gangchat.client.app.roomMemberRoleFilterLabel
import com.gangchat.client.app.roomMemberRoomDisplayNameOriginalLabel
import com.gangchat.client.app.roomMemberRoomDisplayNameValue
import com.gangchat.client.app.roomRoleLabel
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.app.userPrimaryName
import com.gangchat.client.app.userUsernameLabel
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.JoinRequest
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomBlacklistEntry
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AdaptiveHighlightedText
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.HighlightedText
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.LocalAppConfig
import com.gangchat.client.ui.PresencePill
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.RoleBadge
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.SettingsCard
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiSpacing
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.rememberBuildContext

// Test tags of the member filters (Dart's `ValueKey`s).
const val memberPresenceFilterTag = "member-presence-filter"
const val memberRoleFilterTag = "member-role-filter"

fun memberActionWrapTag(userId: String) = "member-action-wrap-$userId"

fun memberInformationTag(userId: String) = "member-information-$userId"

fun memberPresenceTag(userId: String) = "member-presence-$userId"

fun memberRoleTag(userId: String) = "member-role-$userId"

@Composable
internal fun _MemberFilters(
    controller: TextEditingController,
    filterCounts: RoomMemberFilterCounts,
    presenceFilter: RoomMemberPresenceFilter,
    roleFilter: RoomMemberRoleFilter,
    onPresenceChanged: (RoomMemberPresenceFilter) -> Unit,
    onRoleChanged: (RoomMemberRoleFilter) -> Unit,
) {
    val context = rememberBuildContext()
    val presenceSegments =
        RoomMemberPresenceFilter.entries.map { filter ->
            Segment(value = filter, label = roomMemberPresenceFilterLabel(filter, filterCounts))
        }
    val roleSegments =
        RoomMemberRoleFilter.entries.map { filter ->
            Segment(value = filter, label = roomMemberRoleFilterLabel(filter, filterCounts))
        }
    val presence: @Composable (Modifier) -> Unit = { modifier ->
        SegmentedControl(
            modifier = modifier.testTag(memberPresenceFilterTag),
            expanded = true,
            value = presenceFilter,
            onChanged = onPresenceChanged,
            segments = presenceSegments,
        )
    }
    val roles: @Composable (Modifier) -> Unit = { modifier ->
        SegmentedControl(
            modifier = modifier.testTag(memberRoleFilterTag),
            expanded = true,
            value = roleFilter,
            onChanged = onRoleChanged,
            segments = roleSegments,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = HomeAdaptiveLayout.usesCompactLayout(context)
        val pairedControlWidth = if (constraints.hasBoundedWidth) ((maxWidth - 10.dp) / 2).coerceAtLeast(0.dp) else Dp.Infinity
        val stackBeforeOverflow =
            SegmentedControl.minimumWidthFor(context, presenceSegments) > pairedControlWidth ||
                SegmentedControl.minimumWidthFor(context, roleSegments) > pairedControlWidth
        val stackFilters = narrow || stackBeforeOverflow
        Column(Modifier.fillMaxWidth()) {
            Input(
                modifier = Modifier.fillMaxWidth(),
                controller = controller,
                hintText = "搜索成员",
                prefixIcon = Icons.Filled.Search,
                showClearButton = true,
            )
            Spacer(Modifier.height(10.dp))
            if (stackFilters) {
                presence(Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                roles(Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    presence(Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    roles(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun _MemberRow(
    member: RoomMember,
    currentUser: CurrentUser,
    live: LiveState,
    permission: RoomMemberPermissionState,
    stackActions: Boolean,
    nameMaxLines: Int,
    ownerUserId: String?,
    query: String,
    busy: Boolean,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    onEditRoomDisplayName: () -> Unit,
    onSetAdmin: () -> Unit,
    onUnsetAdmin: () -> Unit,
    onRemoveMember: () -> Unit,
    onTransferCreator: () -> Unit,
) {
    val config = LocalAppConfig.current
    val presence = roomMemberPresence(member, live = live)
    val role = roomRoleLabel(member.user, ownerUserId = ownerUserId)
    val actionCount = _memberActionCount(permission)
    val hasActions = actionCount > 0
    val actions: @Composable () -> Unit = {
        Box(Modifier.width(_memberActionsWidth(actionCount))) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag(memberActionWrapTag(member.user.id)),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (permission.canEditRoomDisplayName) {
                    ButtonIcon(
                        tooltip = "修改房间内用户名",
                        icon = { Icon(Icons.Outlined.Edit) },
                        onPressed = onEditRoomDisplayName,
                        size = 34.dp,
                    )
                }
                if (permission.canRoleEdit) {
                    ButtonIcon(
                        tooltip = if (permission.isAdmin) "移除管理员" else "设为管理员",
                        icon = { Icon(if (permission.isAdmin) Icons.Outlined.AdminPanelSettings else Icons.Filled.AdminPanelSettings) },
                        selected = permission.isAdmin,
                        onPressed = if (permission.isAdmin) onUnsetAdmin else onSetAdmin,
                        size = 34.dp,
                    )
                }
                if (permission.canRemoveMember) {
                    ButtonIcon(
                        tooltip = "踢出此用户",
                        icon = { Icon(Icons.Outlined.PersonRemove) },
                        tone = ButtonTone.danger,
                        onPressed = onRemoveMember,
                        size = 34.dp,
                    )
                }
                if (permission.canRoleEdit) {
                    ButtonIcon(
                        tooltip = "转让创建者",
                        icon = { Icon(Icons.Filled.SwapHoriz) },
                        tone = ButtonTone.danger,
                        onPressed = onTransferCreator,
                        size = 34.dp,
                    )
                }
            }
        }
    }
    val information: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            modifier.testTag(memberInformationTag(member.user.id)).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserHoverCard(
                user = member.user,
                currentUser = currentUser,
                onResolveProfile = onResolveProfile,
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterCommonRoom = onOpenRoom,
                inLive = presence == RoomMemberPresence.live,
                showRoomRole = true,
            ) {
                Avatar(
                    label = userAvatarLabel(member.user),
                    imageUrl = config.resolveAssetUrl(member.user.avatarUrl),
                    defaultAvatarKey = member.user.defaultAvatarKey,
                    size = 38.dp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                AdaptiveHighlightedText(
                    text = roomMemberDisplayName(member),
                    query = query,
                    comfortableLines = nameMaxLines,
                    style = UiTypography.body.copyWith(fontWeight = FontWeight.W600),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "@${member.user.username}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTypography.label.copyWith(color = UiColors.textMuted, fontSize = 12.0),
                )
            }
            Spacer(Modifier.width(10.dp))
            PresencePill.member(presence, modifier = Modifier.testTag(memberPresenceTag(member.user.id)))
            Spacer(Modifier.width(6.dp))
            RoleBadge(
                modifier = Modifier.testTag(memberRoleTag(member.user.id)),
                label = role,
                padding = EdgeInsets.symmetric(horizontal = 8.dp, vertical = 5.dp),
            )
            if (busy) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = UiColors.accent,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Square,
                )
            }
        }
    }
    _RowSurface {
        if (stackActions && !busy && hasActions) {
            Column(Modifier.fillMaxWidth()) {
                information(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { actions() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                information(Modifier.weight(1f))
                if (!busy && hasActions) {
                    Spacer(Modifier.width(8.dp))
                    actions()
                }
            }
        }
    }
}

internal fun _memberRowNeedsStackedActions(
    context: BuildContext,
    availableWidth: Dp,
    member: RoomMember,
    live: LiveState,
    permission: RoomMemberPermissionState,
    ownerUserId: String?,
): Boolean {
    val actionCount = _memberActionCount(permission)
    if (actionCount == 0) return false

    val presence = roomMemberPresence(member, live = live)
    val role = roomRoleLabel(member.user, ownerUserId = ownerUserId)
    val displayNameStyle = UiTypography.body.copyWith(fontWeight = FontWeight.W600)
    val usernameStyle = UiTypography.label.copyWith(color = UiColors.textMuted, fontSize = 12.0)
    val presenceStyle = UiTypography.label.copyWith(fontWeight = FontWeight.W600)
    val roleStyle = UiTypography.label.copyWith(fontSize = 12.0, fontWeight = FontWeight.W600)
    val displayNameWidth = _memberRowTextWidth(context, roomMemberDisplayName(member), displayNameStyle)
    val usernameWidth = _memberRowTextWidth(context, "@${member.user.username}", usernameStyle)
    val identityWidth = if (displayNameWidth > usernameWidth) displayNameWidth else usernameWidth
    val displayNameGuardWidth = _memberRowTextWidth(context, "…", displayNameStyle)
    val usernameGuardWidth = _memberRowTextWidth(context, "…", usernameStyle)
    val truncationGuardWidth = if (displayNameGuardWidth > usernameGuardWidth) displayNameGuardWidth else usernameGuardWidth
    val presenceWidth = 2 + 18 + 10 + 6 + _memberRowTextWidth(context, roomMemberPresenceLabel(presence), presenceStyle)
    val roleWidth = 2 + 16 + _memberRowTextWidth(context, role, roleStyle)
    val actionsWidth = _memberActionsWidth(actionCount).value.toDouble()
    val measurementSafety = 2.0
    val requiredWidth =
        38 + 10 + identityWidth + 10 + presenceWidth + 6 + roleWidth + 8 + actionsWidth + truncationGuardWidth + measurementSafety
    return requiredWidth > availableWidth.value
}

internal fun _memberRowNeedsTwoLineName(
    context: BuildContext,
    availableWidth: Dp,
    member: RoomMember,
    live: LiveState,
    ownerUserId: String?,
    busy: Boolean,
): Boolean {
    val presence = roomMemberPresence(member, live = live)
    val role = roomRoleLabel(member.user, ownerUserId = ownerUserId)
    val displayNameStyle = UiTypography.body.copyWith(fontWeight = FontWeight.W600)
    val presenceStyle = UiTypography.label.copyWith(fontWeight = FontWeight.W600)
    val roleStyle = UiTypography.label.copyWith(fontSize = 12.0, fontWeight = FontWeight.W600)
    val presenceWidth = 2 + 18 + 10 + 6 + _memberRowTextWidth(context, roomMemberPresenceLabel(presence), presenceStyle)
    val roleWidth = 2 + 16 + _memberRowTextWidth(context, role, roleStyle)
    val requiredWidth =
        38 + 10 +
            _memberRowTextWidth(context, roomMemberDisplayName(member), displayNameStyle) +
            _memberRowTextWidth(context, "…", displayNameStyle) +
            10 + presenceWidth + 6 + roleWidth +
            (if (busy) 10 + 18 else 0) +
            2
    return requiredWidth > availableWidth.value
}

internal fun _memberActionCount(permission: RoomMemberPermissionState): Int =
    (if (permission.canEditRoomDisplayName) 1 else 0) +
        (if (permission.canRoleEdit) 2 else 0) +
        (if (permission.canRemoveMember) 1 else 0)

internal fun _memberActionsWidth(actionCount: Int): Dp {
    if (actionCount <= 0) return 0.dp
    return (actionCount * 34 + (actionCount - 1) * 6).dp
}

internal fun _memberRowTextWidth(context: BuildContext, text: String, style: TextStyle): Double =
    TextPainter(text, style, maxLines = 1).layout(context).width

@Composable
internal fun _EditRoomDisplayNameDialog(member: RoomMember) =
    StatefulWidget(_EditRoomDisplayNameDialogWidget(member)) { _EditRoomDisplayNameDialogState() }

internal class _EditRoomDisplayNameDialogWidget(val member: RoomMember)

private class _EditRoomDisplayNameDialogState : ViewState<_EditRoomDisplayNameDialogWidget>() {
    private lateinit var _controller: TextEditingController

    override fun initState() {
        _controller = TextEditingController(text = roomMemberRoomDisplayNameValue(widget.member))
    }

    override fun dispose() {
        _controller.dispose()
    }

    @Composable
    override fun build() {
        val defaultName = roomMemberDefaultDisplayName(widget.member)
        val originalName = roomMemberRoomDisplayNameOriginalLabel(widget.member)
        DialogFrame(
            title = "修改${defaultName}的房间内用户名",
            icon = Icons.Outlined.Edit,
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                    ResponsiveDialogAction(
                        label = "确认修改",
                        tone = ButtonTone.primary,
                        onPressed = { Navigator.of(context).pop(_controller.text) },
                    ),
                ),
            child = {
                Column(Modifier.fillMaxWidth()) {
                    Input(controller = _controller, hintText = defaultName)
                    Spacer(Modifier.height(UiSpacing.xs))
                    Text("原名称：$originalName", style = UiTypography.label.copyWith(color = UiColors.textMuted))
                }
            },
        )
    }
}

/** Material's (2023) `LinearProgressIndicator(minHeight: 2, color:, backgroundColor:)`, indeterminate. */
@Composable
private fun _SearchProgress() {
    androidx.compose.material3.LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = UiColors.accent,
        trackColor = UiColors.surfacePressed,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
    )
}

@Composable
private fun _UserIdentityColumn(user: UserSummary, query: String?, modifier: Modifier) {
    Column(modifier) {
        if (query != null) {
            HighlightedText(
                text = userPrimaryName(user),
                query = query,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copyWith(color = UiColors.text, fontWeight = FontWeight.W600),
            )
            Spacer(Modifier.height(2.dp))
            HighlightedText(
                text = userUsernameLabel(user),
                query = query,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copyWith(color = UiColors.textMuted),
            )
        } else {
            Text(
                userPrimaryName(user),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copyWith(fontWeight = FontWeight.W600),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                userUsernameLabel(user),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copyWith(color = UiColors.textMuted),
            )
        }
    }
}

@Composable
private fun _UserHoverAvatar(
    user: UserSummary,
    currentUser: CurrentUser,
    size: Dp,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
) {
    val config = LocalAppConfig.current
    UserHoverCard(
        user = user,
        currentUser = currentUser,
        onResolveProfile = onResolveProfile,
        onResolveRoomProfile = onResolveRoomProfile,
        onEnterCommonRoom = onOpenRoom,
    ) {
        Avatar(
            label = userAvatarLabel(user),
            imageUrl = config.resolveAssetUrl(user.avatarUrl),
            defaultAvatarKey = user.defaultAvatarKey,
            size = size,
        )
    }
}

@Composable
internal fun _InviteSection(
    controller: TextEditingController,
    currentUser: CurrentUser,
    query: String,
    searching: Boolean,
    results: List<UserSummary>,
    members: List<RoomMember>,
    pendingInviteUserIds: Set<String>,
    busyUserIds: Set<String>,
    error: String?,
    enabled: Boolean,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    onInvite: (UserSummary) -> Unit,
) {
    val candidates =
        roomInviteCandidates(
            searchResults = results,
            members = members,
            query = query,
            pendingInviteUserIds = pendingInviteUserIds,
            busyUserIds = busyUserIds,
            invitesEnabled = enabled,
        )
    SettingsCard(
        title = "邀请成员",
        children =
            listOf {
                Column(Modifier.fillMaxWidth()) {
                    Input(
                        controller = controller,
                        hintText = "按用户名、昵称或 UID 搜索",
                        prefixIcon = Icons.Filled.PersonAddAlt1,
                        enabled = enabled,
                        showClearButton = true,
                    )
                    if (searching) {
                        Spacer(Modifier.height(10.dp))
                        _SearchProgress()
                    } else if (enabled && query.length >= 2) {
                        Spacer(Modifier.height(8.dp))
                        if (candidates.isEmpty()) {
                            Text("未找到用户", style = UiTypography.label.copyWith(color = UiColors.textMuted))
                        } else {
                            for ((index, candidate) in candidates.withIndex()) {
                                _InviteUserRow(
                                    user = candidate.user,
                                    currentUser = currentUser,
                                    query = query,
                                    alreadyMember = candidate.existing,
                                    pending = candidate.pending,
                                    busy = candidate.busy,
                                    enabled = enabled,
                                    onResolveProfile = onResolveProfile,
                                    onResolveRoomProfile = onResolveRoomProfile,
                                    onOpenRoom = onOpenRoom,
                                    onInvite = { onInvite(candidate.user) },
                                )
                                if (index != candidates.lastIndex) Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            },
    )
}

@Composable
private fun _InviteUserRow(
    user: UserSummary,
    currentUser: CurrentUser,
    query: String,
    alreadyMember: Boolean,
    pending: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    onInvite: () -> Unit,
) {
    val actionLabel = if (alreadyMember) "在房间内" else if (pending) "已邀请" else "邀请"
    _RowSurface(compact = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            _UserHoverAvatar(user, currentUser, 32.dp, onResolveProfile, onResolveRoomProfile, onOpenRoom)
            Spacer(Modifier.width(9.dp))
            _UserIdentityColumn(user, query, Modifier.weight(1f))
            Button(
                height = 34.dp,
                loading = busy,
                onPressed = if (!enabled || alreadyMember || pending) null else onInvite,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
internal fun _BlockUserSection(
    controller: TextEditingController,
    currentUser: CurrentUser,
    query: String,
    searching: Boolean,
    results: List<UserSummary>,
    members: List<RoomMember>,
    blacklist: List<RoomBlacklistEntry>,
    busyUserIds: Set<String>,
    error: String?,
    enabled: Boolean,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    onBlock: (UserSummary) -> Unit,
    onUnblock: (UserSummary) -> Unit,
) {
    val candidates =
        roomBlacklistCandidates(
            searchResults = results,
            blacklist = blacklist,
            members = members,
            query = query,
            busyUserIds = busyUserIds,
        )
    SettingsCard(
        title = "拉黑用户",
        children =
            listOf {
                Column(Modifier.fillMaxWidth()) {
                    Input(
                        controller = controller,
                        hintText = "按用户名、昵称或 UID 搜索",
                        prefixIcon = Icons.Filled.Block,
                        enabled = enabled,
                        showClearButton = true,
                        textInputAction = ImeAction.Search,
                    )
                    if (searching) {
                        Spacer(Modifier.height(10.dp))
                        _SearchProgress()
                    } else if (enabled && query.length >= 2) {
                        Spacer(Modifier.height(8.dp))
                        if (candidates.isEmpty()) {
                            Text("未找到用户", style = UiTypography.label.copyWith(color = UiColors.textMuted))
                        } else {
                            for ((index, candidate) in candidates.withIndex()) {
                                _BlockUserRow(
                                    user = candidate.user,
                                    currentUser = currentUser,
                                    query = query,
                                    member = candidate.member,
                                    blocked = candidate.blocked,
                                    superuser = candidate.superuser,
                                    busy = candidate.busy,
                                    enabled = enabled,
                                    onResolveProfile = onResolveProfile,
                                    onResolveRoomProfile = onResolveRoomProfile,
                                    onOpenRoom = onOpenRoom,
                                    onBlock = { onBlock(candidate.user) },
                                    onUnblock = { onUnblock(candidate.user) },
                                )
                                if (index != candidates.lastIndex) Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            },
    )
}

@Composable
private fun _BlockUserRow(
    user: UserSummary,
    currentUser: CurrentUser,
    query: String,
    member: Boolean,
    blocked: Boolean,
    superuser: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    val actionLabel = roomBlacklistActionLabel(member = member, superuser = superuser, blocked = blocked)
    val onPressed: (() -> Unit)? =
        when {
            !enabled || member || superuser -> null
            blocked -> onUnblock
            else -> onBlock
        }
    _RowSurface(compact = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            _UserHoverAvatar(user, currentUser, 32.dp, onResolveProfile, onResolveRoomProfile, onOpenRoom)
            Spacer(Modifier.width(9.dp))
            _UserIdentityColumn(user, query, Modifier.weight(1f))
            Button(
                height = 34.dp,
                loading = busy,
                tone = if (blocked) ButtonTone.neutral else ButtonTone.danger,
                onPressed = onPressed,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
internal fun _BlacklistSection(
    entries: List<RoomBlacklistEntry>,
    currentUser: CurrentUser,
    busyUserIds: Set<String>,
    loading: Boolean,
    error: String?,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    onUnblock: (UserSummary) -> Unit,
) {
    SettingsCard(
        title = "黑名单",
        children =
            listOf {
                Column(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    if (loading) {
                        Spacer(Modifier.height(8.dp))
                        _SearchProgress()
                    } else if (entries.isEmpty()) {
                        Text(
                            "暂无黑名单用户",
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                            style = UiTypography.label.copyWith(color = UiColors.textMuted),
                        )
                    } else {
                        for ((index, entry) in entries.withIndex()) {
                            _BlacklistUserRow(
                                user = entry.user,
                                currentUser = currentUser,
                                busy = busyUserIds.contains(entry.user.id),
                                onResolveProfile = onResolveProfile,
                                onResolveRoomProfile = onResolveRoomProfile,
                                onOpenRoom = onOpenRoom,
                                onUnblock = { onUnblock(entry.user) },
                            )
                            if (index != entries.lastIndex) Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            },
    )
}

@Composable
private fun _BlacklistUserRow(
    user: UserSummary,
    currentUser: CurrentUser,
    busy: Boolean,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    onUnblock: () -> Unit,
) {
    _RowSurface(compact = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            _UserHoverAvatar(user, currentUser, 32.dp, onResolveProfile, onResolveRoomProfile, onOpenRoom)
            Spacer(Modifier.width(9.dp))
            _UserIdentityColumn(user, null, Modifier.weight(1f))
            Button(height = 34.dp, loading = busy, onPressed = onUnblock) { Text("取消拉黑") }
        }
    }
}

@Composable
internal fun _JoinRequestsSection(
    requests: List<JoinRequest>,
    currentUser: CurrentUser,
    busyRequestIds: Set<String>,
    activeDetailRequestId: String?,
    error: String?,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    onDetail: (JoinRequest) -> Unit,
    onApprove: (JoinRequest) -> Unit,
    onReject: (JoinRequest) -> Unit,
) {
    SettingsCard(
        title = "加入申请",
        children =
            listOf {
                Column(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    if (requests.isEmpty()) {
                        Text(
                            "暂无待处理申请",
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                            style = UiTypography.label.copyWith(color = UiColors.textMuted),
                        )
                    } else {
                        for ((index, request) in requests.withIndex()) {
                            _JoinRequestRow(
                                request = request,
                                currentUser = currentUser,
                                busy = busyRequestIds.contains(request.id),
                                detailActive = activeDetailRequestId == request.id,
                                onResolveProfile = onResolveProfile,
                                onResolveRoomProfile = onResolveRoomProfile,
                                onOpenRoom = onOpenRoom,
                                onDetail = { onDetail(request) },
                                onApprove = { onApprove(request) },
                                onReject = { onReject(request) },
                            )
                            if (index != requests.lastIndex) Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            },
    )
}

@Composable
private fun _JoinRequestRow(
    request: JoinRequest,
    currentUser: CurrentUser,
    busy: Boolean,
    detailActive: Boolean,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    onDetail: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    _RowSurface(compact = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            _UserHoverAvatar(request.user, currentUser, 32.dp, onResolveProfile, onResolveRoomProfile, onOpenRoom)
            Spacer(Modifier.width(9.dp))
            Text(
                userPrimaryName(request.user),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copyWith(fontWeight = FontWeight.W600),
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = UiColors.accent,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Square,
                )
            } else {
                ButtonIcon(
                    tooltip = "详情",
                    icon = { Icon(Icons.Outlined.Info) },
                    selected = detailActive,
                    onPressed = onDetail,
                    size = 32.dp,
                )
                Spacer(Modifier.width(6.dp))
                ButtonIcon(
                    tooltip = "拒绝",
                    icon = { Icon(Icons.Filled.Close) },
                    tone = ButtonTone.danger,
                    onPressed = onReject,
                    size = 32.dp,
                )
                Spacer(Modifier.width(6.dp))
                ButtonIcon(
                    tooltip = "通过",
                    icon = { Icon(Icons.Filled.Check) },
                    tone = ButtonTone.primary,
                    onPressed = onApprove,
                    size = 32.dp,
                )
            }
        }
    }
}

@Composable
internal fun _JoinRequestDetailsDialog(
    request: JoinRequest,
    currentUser: CurrentUser,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
) {
    val context = rememberBuildContext()
    DialogFrame(
        title = "申请详情",
        icon = Icons.AutoMirrored.Outlined.FactCheck,
        actions = listOf { Button(onPressed = { Navigator.of(context).pop() }) { Text("关闭") } },
        child = {
            Column(Modifier.fillMaxWidth()) {
                _JoinRequestDetailBlock(label = "来源") {
                    _JoinRequestSourceContent(
                        request = request,
                        currentUser = currentUser,
                        onResolveProfile = onResolveProfile,
                        onResolveRoomProfile = onResolveRoomProfile,
                        onOpenRoom = onOpenRoom,
                    )
                }
                Spacer(Modifier.height(14.dp))
                _JoinRequestDetailBlock(label = "申请理由") {
                    Text(joinRequestDetailReasonText(request), style = UiTypography.body)
                }
            }
        },
    )
}

@Composable
private fun _JoinRequestDetailBlock(label: String, child: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = UiTypography.label.copyWith(color = UiColors.textMuted, fontWeight = FontWeight.W600))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { child() }
    }
}

@Composable
private fun _JoinRequestSourceContent(
    request: JoinRequest,
    currentUser: CurrentUser,
    onResolveProfile: UserProfileResolver,
    onResolveRoomProfile: RoomProfileResolver,
    onOpenRoom: ((PublicRoom) -> Unit)?,
) {
    if (!joinRequestFromInvitation(request) || request.inviters.isEmpty()) {
        Text(joinRequestSourceText(request), style = UiTypography.body.copyWith(fontWeight = FontWeight.W600))
        return
    }
    Column(Modifier.fillMaxWidth()) {
        for ((index, inviter) in request.inviters.withIndex()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                _UserHoverAvatar(inviter, currentUser, 28.dp, onResolveProfile, onResolveRoomProfile, onOpenRoom)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${userPrimaryName(inviter)} 的邀请",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTypography.body.copyWith(fontWeight = FontWeight.W600),
                )
            }
            if (index != request.inviters.lastIndex) Spacer(Modifier.height(6.dp))
        }
    }
}

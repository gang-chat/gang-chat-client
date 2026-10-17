package com.gangchat.client.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.commonRoomAvatarLabel
import com.gangchat.client.app.commonRoomDisplayName
import com.gangchat.client.app.publicRoomAvatarLabel
import com.gangchat.client.app.roomJoinPolicyLabel
import com.gangchat.client.app.roomRoleLabel
import com.gangchat.client.app.roomRoleLabelFromValue
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.app.userPrimaryName
import com.gangchat.client.app.visibilityLabel
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserCommonRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AnchoredPanel
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.LocalAppConfig
import com.gangchat.client.ui.PresencePill
import com.gangchat.client.ui.RoleBadge
import com.gangchat.client.ui.StatusBadge
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiSpacing
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.genderMark
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FittedBox
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.PopupRoute
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rawScrollbar
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.rememberScrollbarAdapter
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

typealias RoomProfileResolver = suspend (room: PublicRoom) -> PublicRoom
typealias UserProfileResolver = suspend (user: UserSummary) -> UserSummary
typealias UserProfileActionBuilder = (user: UserSummary) -> UserProfileAction?

class UserProfileAction(val label: String, val icon: ImageVector, val onPressed: () -> Unit)

/** Test tag of a user card's avatar (Dart's `ValueKey('user-profile-card-avatar-preview')`). */
const val userProfileCardAvatarPreviewTag = "user-profile-card-avatar-preview"

/** Test tag of a room card's icon (Dart's `ValueKey('room-profile-card-icon-preview')`). */
const val roomProfileCardIconPreviewTag = "room-profile-card-icon-preview"

/** Test tag of the deleted-user tombstone (Dart's `ValueKey('deleted-user-profile-card')`). */
const val deletedUserProfileCardTag = "deleted-user-profile-card"

/** Test tag of the deleted-room tombstone (Dart's `ValueKey('deleted-room-profile-card')`). */
const val deletedRoomProfileCardTag = "deleted-room-profile-card"

@Composable
fun RoomHoverCard(
    room: PublicRoom,
    currentUser: CurrentUser,
    modifier: Modifier = Modifier,
    onResolveRoom: RoomProfileResolver? = null,
    onResolveUserProfile: UserProfileResolver? = null,
    onEnterRoom: ((PublicRoom) -> Unit)? = null,
    child: @Composable () -> Unit,
) = StatefulWidget(RoomHoverCardWidget(room, currentUser, child, onResolveRoom, onResolveUserProfile, onEnterRoom, modifier)) {
    _RoomHoverCardState()
}

class RoomHoverCardWidget(
    val room: PublicRoom,
    val currentUser: CurrentUser,
    val child: @Composable () -> Unit,
    val onResolveRoom: RoomProfileResolver?,
    val onResolveUserProfile: UserProfileResolver?,
    val onEnterRoom: ((PublicRoom) -> Unit)?,
    val modifier: Modifier,
)

private class _RoomHoverCardState : ViewState<RoomHoverCardWidget>() {
    private var _resolved: PublicRoom? = null
    private var _resolveFuture: Deferred<Unit>? = null

    private val _displayRoom: PublicRoom get() = _resolved ?: widget.room

    override fun didUpdateWidget(oldWidget: RoomHoverCardWidget) {
        val roomChanged = oldWidget.room.id != widget.room.id
        val deletionChanged = oldWidget.room.isDeleted != widget.room.isDeleted
        val resolverPresenceChanged = (oldWidget.onResolveRoom == null) != (widget.onResolveRoom == null)
        if (!roomChanged && !deletionChanged && !resolverPresenceChanged) return
        _resolved = null
        _resolveFuture = null
    }

    private suspend fun _resolveRoom() {
        if (widget.room.isDeleted) return
        val resolver = widget.onResolveRoom ?: return
        val existing = _resolveFuture
        if (existing != null) return existing.await()

        val requestedRoom = widget.room
        val future =
            com.gangchat.client.foundation.future {
                try {
                    val room = resolver(requestedRoom)
                    if (!mounted || widget.room.id != requestedRoom.id) return@future
                    setState { _resolved = room }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (!mounted || widget.room.id != requestedRoom.id) return@future
                    // Fall back to the current lightweight summary instead of
                    // keeping a previously resolved card that may now be stale.
                    setState { _resolved = null }
                } finally {
                    if (mounted && widget.room.id == requestedRoom.id) _resolveFuture = null
                }
            }
        if (!future.isCompleted) _resolveFuture = future
        future.await()
    }

    @Composable
    override fun build() {
        HoverCardAnchor(
            modifier = widget.modifier,
            resetKey = listOf(widget.room.id, widget.room.isDeleted, widget.onResolveRoom != null, widget.onResolveUserProfile != null),
            onBeforeOpen = if (widget.room.isDeleted || widget.onResolveRoom == null) null else ({ _resolveRoom() }),
            cardBuilder = { _ ->
                _RoomProfileCard(
                    room = _displayRoom,
                    currentUser = widget.currentUser,
                    onResolveRoomProfile = widget.onResolveRoom,
                    onResolveUserProfile = widget.onResolveUserProfile,
                    onEnterRoom = widget.onEnterRoom,
                )
            },
            child = widget.child,
        )
    }
}

/**
 * A user's profile card on their avatar. [onResolveProfile] fetches a richer,
 * up-to-date profile (gender, common rooms) before the card opens; when null
 * the card shows only the supplied summary.
 */
@Composable
fun UserHoverCard(
    user: UserSummary,
    modifier: Modifier = Modifier,
    currentUser: CurrentUser? = null,
    onResolveProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterCommonRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
    inLive: Boolean = false,
    showRoomRole: Boolean = false,
    child: @Composable () -> Unit,
) = StatefulWidget(
    UserHoverCardWidget(
        user, child, currentUser, onResolveProfile, onResolveRoomProfile, onEnterCommonRoom, profileActionBuilder, inLive, showRoomRole,
        modifier,
    ),
) { _UserHoverCardState() }

class UserHoverCardWidget(
    val user: UserSummary,
    val child: @Composable () -> Unit,
    val currentUser: CurrentUser?,
    val onResolveProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    val profileActionBuilder: UserProfileActionBuilder?,
    val inLive: Boolean,
    val showRoomRole: Boolean,
    val modifier: Modifier,
)

/**
 * Opens a user's profile card at [position] (window dp) in a transparent,
 * dismissible popup route; it stays inside the system safe area.
 */
suspend fun showUserProfileCardAtPosition(
    context: BuildContext,
    position: DpOffset,
    user: UserSummary,
    currentUser: CurrentUser? = null,
    onResolveProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterCommonRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
    inLive: Boolean = false,
    showRoomRole: Boolean = false,
    resolveOnOpen: Boolean = true,
) {
    Navigator.of(context, rootNavigator = true)
        .push(
            _UserProfileCardPopupRoute(
                position = position,
                user = user,
                currentUser = currentUser,
                onResolveProfile = onResolveProfile,
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterCommonRoom = onEnterCommonRoom,
                profileActionBuilder = profileActionBuilder,
                inLive = inLive,
                showRoomRole = showRoomRole,
                resolveOnOpen = resolveOnOpen,
            ),
        )
        .await()
}

private class _UserHoverCardState : ViewState<UserHoverCardWidget>() {
    private var _resolved: UserSummary? = null
    private var _resolveFuture: Deferred<Unit>? = null

    private val _displayUser: UserSummary get() = _resolved ?: widget.user

    override fun didUpdateWidget(oldWidget: UserHoverCardWidget) {
        val userChanged = oldWidget.user.id != widget.user.id
        val deletionChanged = oldWidget.user.isDeleted != widget.user.isDeleted
        val suspensionChanged = oldWidget.user.isSuspended != widget.user.isSuspended
        val currentUserChanged = oldWidget.currentUser?.id != widget.currentUser?.id
        val resolverPresenceChanged = (oldWidget.onResolveProfile == null) != (widget.onResolveProfile == null)
        val liveChanged = oldWidget.inLive != widget.inLive
        val roleVisibilityChanged = oldWidget.showRoomRole != widget.showRoomRole
        val actionPresenceChanged = (oldWidget.profileActionBuilder == null) != (widget.profileActionBuilder == null)
        if (!userChanged &&
            !deletionChanged &&
            !suspensionChanged &&
            !currentUserChanged &&
            !resolverPresenceChanged &&
            !liveChanged &&
            !roleVisibilityChanged &&
            !actionPresenceChanged
        ) {
            return
        }
        _resolved = null
        _resolveFuture = null
    }

    private suspend fun _resolveProfile() {
        if (widget.user.isDeleted) return
        val resolver = widget.onResolveProfile ?: return
        val existing = _resolveFuture
        if (existing != null) return existing.await()

        val requestedUser = widget.user
        val future =
            com.gangchat.client.foundation.future {
                try {
                    val profile = resolver(requestedUser)
                    if (!mounted || widget.user.id != requestedUser.id) return@future
                    setState { _resolved = profile }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (!mounted || widget.user.id != requestedUser.id) return@future
                    // Keep showing the current lightweight summary on failure.
                    setState { _resolved = null }
                } finally {
                    if (mounted && widget.user.id == requestedUser.id) _resolveFuture = null
                }
            }
        if (!future.isCompleted) _resolveFuture = future
        future.await()
    }

    @Composable
    override fun build() {
        val displayUser = _displayUser
        HoverCardAnchor(
            modifier = widget.modifier,
            resetKey =
                listOf(
                    widget.user.id,
                    widget.user.isDeleted,
                    widget.user.isSuspended,
                    widget.onResolveProfile != null,
                    widget.onResolveRoomProfile != null,
                    widget.currentUser?.id,
                    widget.inLive,
                    widget.showRoomRole,
                    widget.profileActionBuilder != null,
                ),
            onBeforeOpen = if (widget.user.isDeleted || widget.onResolveProfile == null) null else ({ _resolveProfile() }),
            cardBuilder = { _ ->
                _UserProfileCard(
                    user = displayUser,
                    currentUser = widget.currentUser,
                    onResolveUserProfile = widget.onResolveProfile,
                    onResolveRoomProfile = widget.onResolveRoomProfile,
                    onEnterCommonRoom = widget.onEnterCommonRoom,
                    inLive = widget.inLive,
                    showRoomRole = widget.showRoomRole,
                    action = if (displayUser.isDeleted) null else widget.profileActionBuilder?.invoke(displayUser),
                )
            },
            child = widget.child,
        )
    }
}

private class _UserProfileCardPopupRoute(
    val position: DpOffset,
    val user: UserSummary,
    val currentUser: CurrentUser?,
    val onResolveProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    val profileActionBuilder: UserProfileActionBuilder?,
    val inLive: Boolean,
    val showRoomRole: Boolean,
    val resolveOnOpen: Boolean,
) : PopupRoute<Unit>() {
    override val barrierColor: Color get() = Color.Transparent
    override val barrierDismissible: Boolean get() = true

    // Dart's barrierLabel '关闭用户名片' only labels the barrier for screen readers.
    override val transitionDuration: Int get() = 110

    @Composable
    override fun buildPage(context: BuildContext) {
        val safeInsets = hoverCardOverlaySafeInsets(context)
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                AnchoredPanel(width = hoverCardDefaultWidth) {
                    _ResolvingUserProfileCard(
                        user = user,
                        currentUser = currentUser,
                        onResolveProfile = onResolveProfile,
                        onResolveRoomProfile = onResolveRoomProfile,
                        onEnterCommonRoom = onEnterCommonRoom,
                        profileActionBuilder = profileActionBuilder,
                        inLive = inLive,
                        showRoomRole = showRoomRole,
                        resolveOnOpen = resolveOnOpen,
                    )
                }
            },
        ) { measurables, constraints ->
            val screenPadding = _screenPadding.toPx()
            val gap = _gap.toPx()
            val safeLeft = safeInsets.left.toPx()
            val safeTop = safeInsets.top.toPx()
            val safeRight = safeInsets.right.toPx()
            val safeBottom = safeInsets.bottom.toPx()
            val maxWidth = max(0f, constraints.maxWidth - safeLeft - safeRight - screenPadding * 2).roundToInt()
            val maxHeight = max(0f, constraints.maxHeight - safeTop - safeBottom - screenPadding * 2).roundToInt()
            val placeable = measurables.first().measure(Constraints(maxWidth = maxWidth, maxHeight = maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val width = constraints.maxWidth.toFloat()
                val height = constraints.maxHeight.toFloat()
                val anchorX = position.x.toPx()
                val anchorY = position.y.toPx()
                val minX = safeLeft + screenPadding
                val maxX = max(minX, width - safeRight - placeable.width - screenPadding)
                val x = anchorX.coerceIn(minX, maxX)
                var y = anchorY + gap
                val safeBottomEdge = height - safeBottom - screenPadding
                if (y + placeable.height > safeBottomEdge) y = anchorY - placeable.height - gap
                val minY = safeTop + screenPadding
                val maxY = max(minY, safeBottomEdge - placeable.height)
                y = y.coerceIn(minY, maxY)
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }

    @Composable
    override fun buildTransitions(context: BuildContext, animation: Float, content: @Composable () -> Unit) {
        val opacity = Curves.easeOutCubic.transform(animation.coerceIn(0f, 1f))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity }) { content() }
    }

    companion object {
        private val _screenPadding = 8.dp
        private val _gap = 10.dp
    }
}

@Composable
private fun _ResolvingUserProfileCard(
    user: UserSummary,
    currentUser: CurrentUser?,
    onResolveProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    profileActionBuilder: UserProfileActionBuilder?,
    inLive: Boolean,
    showRoomRole: Boolean,
    resolveOnOpen: Boolean,
) = StatefulWidget(
    _ResolvingUserProfileCardWidget(
        user, currentUser, onResolveProfile, onResolveRoomProfile, onEnterCommonRoom, profileActionBuilder, inLive, showRoomRole, resolveOnOpen,
    ),
) { _ResolvingUserProfileCardState() }

private class _ResolvingUserProfileCardWidget(
    val user: UserSummary,
    val currentUser: CurrentUser?,
    val onResolveProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    val profileActionBuilder: UserProfileActionBuilder?,
    val inLive: Boolean,
    val showRoomRole: Boolean,
    val resolveOnOpen: Boolean,
)

private class _ResolvingUserProfileCardState : ViewState<_ResolvingUserProfileCardWidget>() {
    private var _resolved: UserSummary? = null

    private val _displayUser: UserSummary get() = _resolved ?: widget.user

    override fun initState() {
        if (widget.resolveOnOpen && !widget.user.isDeleted) unawaited { _resolve() }
    }

    private suspend fun _resolve() {
        if (widget.user.isDeleted) return
        val resolver = widget.onResolveProfile ?: return
        val requestedUser = widget.user
        try {
            val profile = resolver(requestedUser)
            if (!mounted || widget.user.id != requestedUser.id) return
            setState { _resolved = profile }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted || widget.user.id != requestedUser.id) return
            setState { _resolved = null }
        }
    }

    @Composable
    override fun build() {
        val user = _displayUser
        _UserProfileCard(
            user = user,
            currentUser = widget.currentUser,
            onResolveUserProfile = widget.onResolveProfile,
            onResolveRoomProfile = widget.onResolveRoomProfile,
            onEnterCommonRoom = widget.onEnterCommonRoom,
            inLive = widget.inLive,
            showRoomRole = widget.showRoomRole,
            action = if (user.isDeleted) null else widget.profileActionBuilder?.invoke(user),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _UserProfileCard(
    user: UserSummary,
    currentUser: CurrentUser? = null,
    onResolveUserProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterCommonRoom: ((PublicRoom) -> Unit)? = null,
    inLive: Boolean = false,
    showRoomRole: Boolean = false,
    action: UserProfileAction? = null,
) {
    if (user.isDeleted) {
        _DeletedUserProfileCard()
        return
    }
    val name = userPrimaryName(user)
    val gender = genderMark(user.gender)
    val role = if (showRoomRole) roomRoleLabel(user) else null
    val isCurrentUser = currentUser?.id == user.id
    val online = !user.isSuspended && (inLive || isCurrentUser || (user.isOnline ?: false))
    val bio = user.bio?.trim()
    val uid = user.uid?.trim()
    val commonRooms = user.commonRooms.filter { it.isUsable }
    val config = LocalAppConfig.current
    val avatarUrl = config.resolveAssetUrl(user.avatarUrl)
    val previewAvatarUrl = if (_nonEmpty(user.avatarUrl) == null) null else _nonEmpty(avatarUrl)

    Column(Modifier.padding(EdgeInsets.all(UiSpacing.lg))) {
        Row(verticalAlignment = Alignment.Top) {
            _ProfileImagePreview(
                imageUrl = previewAvatarUrl,
                suggestedName = "${userPrimaryName(user)}-avatar.png",
                modifier = Modifier.testTag(userProfileCardAvatarPreviewTag),
            ) {
                Avatar(
                    label = userAvatarLabel(user),
                    imageUrl = avatarUrl,
                    defaultAvatarKey = user.defaultAvatarKey,
                    size = 48.dp,
                    active = online,
                    activeBorderWidth = 2.dp,
                    activeBorderColor = if (inLive) UiColors.presenceVoice else null,
                    paintBorderOnForeground = true,
                )
            }
            Spacer(Modifier.width(UiSpacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    HoverCardSelectableText(
                        value = name,
                        style = UiTypography.title.copyWith(fontSize = 16.0),
                        maxLines = 12,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (gender != null) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            gender.symbol,
                            maxLines = 1,
                            style = UiTypography.title.copyWith(color = gender.color, fontSize = 17.0, fontWeight = FontWeight.W900),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                HoverCardSelectableText(
                    value = "@${user.username}",
                    copyStartOffset = 1,
                    maxLines = 12,
                    style = UiTypography.label.copyWith(color = UiColors.textMuted),
                )
            }
        }
        Spacer(Modifier.height(UiSpacing.md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                user.isSuspended -> PresencePill.suspended()
                inLive -> PresencePill.voice()
                online -> PresencePill.online()
                else -> PresencePill.offline()
            }
            if (role != null) RoleBadge(label = role)
        }
        if (bio != null && bio.isNotEmpty()) {
            Spacer(Modifier.height(UiSpacing.md))
            HoverCardSelectableText(value = bio, maxLines = 4, style = UiTypography.body.copyWith(color = UiColors.textSecondary))
        }
        if (commonRooms.isNotEmpty()) {
            Spacer(Modifier.height(UiSpacing.md))
            Text("${commonRooms.size} 个共同房间", style = UiTypography.label.copyWith(color = UiColors.textMuted))
            Spacer(Modifier.height(UiSpacing.xs))
            _UserCommonRoomList(
                rooms = commonRooms,
                currentUser = currentUser,
                onResolveUserProfile = onResolveUserProfile,
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterRoom = onEnterCommonRoom,
            )
        }
        if (uid != null && uid.isNotEmpty()) {
            Spacer(Modifier.height(UiSpacing.sm))
            HoverCardSelectableText(
                value = "UID: $uid",
                copyStartOffset = "UID: ".length,
                style = UiTypography.label.copyWith(color = UiColors.textMuted),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(UiSpacing.md))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    icon = { Icon(action.icon) },
                    tone = ButtonTone.primary,
                    height = 34.dp,
                    padding = EdgeInsets.symmetric(horizontal = 14.dp),
                    onPressed = action.onPressed,
                ) {
                    Text(action.label)
                }
            }
        }
    }
}

@Composable
private fun _DeletedUserProfileCard() {
    Row(
        Modifier.padding(EdgeInsets.all(UiSpacing.lg)).testTag(deletedUserProfileCardTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(label = "", defaultAvatarKey = "graphite-2", size = 42.dp, showFallbackText = false)
        Spacer(Modifier.width(UiSpacing.md))
        Text("用户已注销", style = UiTypography.body.copyWith(color = UiColors.textSecondary))
    }
}

@Composable
private fun _ProfileImagePreview(
    imageUrl: String?,
    suggestedName: String,
    modifier: Modifier = Modifier,
    child: @Composable () -> Unit,
) {
    val context = rememberBuildContext()
    val url = _nonEmpty(imageUrl)
    if (url == null) {
        Box(modifier, propagateMinConstraints = true) { child() }
        return
    }

    suspend fun openPreview() {
        val hoverScope = HoverCardTapRegionScope.maybeOf(context)
        val previewActions = ChatImagePreviewActionsScope.maybeOf(context)
        hoverScope?.onOverlayActivityChanged?.invoke(true)
        try {
            showChatImagePreview(
                context,
                imageUrl = url,
                suggestedName = suggestedName,
                actions = previewActions ?: ChatImagePreviewActions.disabled(),
                showActionBar = previewActions != null,
                forceSquare = true,
            )
        } finally {
            hoverScope?.onOverlayActivityChanged?.invoke(false)
        }
    }

    Box(
        modifier.clickable(
            interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
            indication = null,
            role = Role.Image,
            onClick = { unawaited { openPreview() } },
        ),
        propagateMinConstraints = true,
    ) {
        child()
    }
}

private val _commonRoomListMaxHeight = 116.dp

@Composable
private fun _UserCommonRoomList(
    rooms: List<UserCommonRoom>,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterRoom: ((PublicRoom) -> Unit)?,
) {
    val list: @Composable (Modifier) -> Unit = { listModifier ->
        Column(listModifier) {
            for (room in rooms) {
                Box(Modifier.padding(top = 4.dp)) {
                    _UserCommonRoomRow(
                        room = room,
                        currentUser = currentUser,
                        onResolveUserProfile = onResolveUserProfile,
                        onResolveRoomProfile = onResolveRoomProfile,
                        onEnterRoom = onEnterRoom,
                    )
                }
            }
        }
    }
    if (rooms.size <= 4) {
        list(Modifier)
        return
    }
    // Material's Android scrollbar: always visible here, 4 dp, square.
    val scrollState = rememberScrollState()
    Box(
        Modifier
            .heightIn(max = _commonRoomListMaxHeight)
            .rawScrollbar(
                rememberScrollbarAdapter(scrollState),
                thickness = 4.dp,
                thumbColor = UiColors.text.copy(alpha = 0.5f),
                thumbVisibility = true,
            ),
    ) {
        list(Modifier.verticalScroll(scrollState).padding(end = UiSpacing.sm))
    }
}

@Composable
private fun _UserCommonRoomRow(
    room: UserCommonRoom,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterRoom: ((PublicRoom) -> Unit)?,
) {
    val publicRoom = _publicRoomFromCommonRoom(room)
    val config = LocalAppConfig.current
    _CompactProfileIdentity(
        name = commonRoomDisplayName(room),
        style = UiTypography.body.copyWith(color = UiColors.textSecondary, fontSize = 13.0),
        avatarBuilder = { size ->
            val avatar: @Composable () -> Unit = {
                Avatar(
                    label = commonRoomAvatarLabel(room),
                    imageUrl = config.resolveAssetUrl(room.avatarUrl),
                    defaultAvatarKey = room.defaultAvatarKey,
                    size = size,
                    activeBorderWidth = 1.dp,
                )
            }
            if (currentUser == null) {
                avatar()
            } else {
                RoomHoverCard(
                    room = publicRoom,
                    currentUser = currentUser,
                    onResolveRoom = onResolveRoomProfile,
                    onResolveUserProfile = onResolveUserProfile,
                    onEnterRoom = onEnterRoom,
                    child = avatar,
                )
            }
        },
    )
}

private val _normalAvatarSize = 20.dp
private val _compactAvatarSize = 16.dp
private val _textLayoutSlack = 8.dp

@Composable
private fun _CompactProfileIdentity(name: String, style: TextStyle, avatarBuilder: @Composable (Dp) -> Unit) {
    val context = rememberBuildContext()
    BoxWithConstraints {
        // This is the row's real width after the owning card has applied its
        // width clamp and padding. Never derive the compact breakpoint from
        // the screen or the default hover-card width.
        val painter = TextPainter(name, style, maxLines = 1).layout(context)
        val painterWidth = painter.width.dp
        val normalWidth = _normalAvatarSize + UiSpacing.sm + painterWidth + _textLayoutSlack
        val compact = normalWidth > maxWidth
        val avatarSize = if (compact) _compactAvatarSize else _normalAvatarSize
        val identityWidth = (avatarSize + UiSpacing.sm + painterWidth + _textLayoutSlack).coerceIn(avatarSize + UiSpacing.sm + 1.dp, maxWidth)
        Row(Modifier.width(identityWidth), verticalAlignment = Alignment.CenterVertically) {
            avatarBuilder(avatarSize)
            Spacer(Modifier.width(UiSpacing.sm))
            if (compact) {
                FittedBox(Modifier.weight(1f), fit = BoxFit.scaleDown, alignment = Alignment.CenterStart) {
                    Text(name, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, style = style)
                }
            } else {
                Text(name, Modifier.weight(1f), maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, style = style)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _RoomProfileCard(
    room: PublicRoom,
    currentUser: CurrentUser,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onResolveUserProfile: UserProfileResolver? = null,
    onEnterRoom: ((PublicRoom) -> Unit)? = null,
) {
    if (room.isDeleted) {
        _DeletedRoomProfileCard()
        return
    }
    val config = LocalAppConfig.current
    val description = _nonEmpty(room.description)
    val rid = _nonEmpty(room.rid) ?: room.id
    val creator = room.createdBy
    val joined = room.joined
    val myName = _myRoomDisplayName(room, currentUser)
    val myAvatarUrl = _myRoomAvatarUrl(room, currentUser)
    val myDefaultAvatarKey = _myRoomDefaultAvatarKey(room, currentUser)
    val myRole = _myRoomRoleLabel(room, currentUser)
    val roomAvatarUrl = config.resolveAssetUrl(room.avatarUrl)
    val previewRoomAvatarUrl = if (_nonEmpty(room.avatarUrl) == null) null else _nonEmpty(roomAvatarUrl)

    Column(Modifier.padding(EdgeInsets.all(UiSpacing.lg))) {
        Row(verticalAlignment = Alignment.Top) {
            _ProfileImagePreview(
                imageUrl = previewRoomAvatarUrl,
                suggestedName = "${room.name}-icon.png",
                modifier = Modifier.testTag(roomProfileCardIconPreviewTag),
            ) {
                Avatar(
                    label = publicRoomAvatarLabel(room),
                    imageUrl = roomAvatarUrl,
                    defaultAvatarKey = room.defaultAvatarKey,
                    size = 48.dp,
                    activeBorderWidth = 2.dp,
                )
            }
            Spacer(Modifier.width(UiSpacing.md))
            Column(Modifier.weight(1f)) {
                HoverCardSelectableText(value = room.name, style = UiTypography.title.copyWith(fontSize = 16.0), maxLines = 12)
                Spacer(Modifier.height(2.dp))
                Text("${room.memberCount} 名成员", maxLines = 2, style = UiTypography.label.copyWith(color = UiColors.textMuted))
            }
        }
        Spacer(Modifier.height(UiSpacing.md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge(
                label = if (room.joined) "已加入" else "未加入",
                icon = if (room.joined) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                active = room.joined,
            )
            StatusBadge(label = visibilityLabel(room.visibility))
            StatusBadge(label = roomJoinPolicyLabel(room.joinPolicy))
        }
        if (description != null) {
            Spacer(Modifier.height(UiSpacing.md))
            HoverCardSelectableText(value = description, maxLines = 4, style = UiTypography.body.copyWith(color = UiColors.textSecondary))
        }
        if (creator != null) {
            Spacer(Modifier.height(UiSpacing.md))
            Text("创建者", style = UiTypography.label.copyWith(color = UiColors.textMuted))
            Spacer(Modifier.height(UiSpacing.xs))
            _RoomCardPersonRow(
                avatarLabel = userAvatarLabel(creator),
                avatarUrl = creator.avatarUrl,
                defaultAvatarKey = creator.defaultAvatarKey,
                name = userPrimaryName(creator),
                profileUser = creator,
                currentUser = currentUser,
                onResolveUserProfile = onResolveUserProfile,
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterCommonRoom = onEnterRoom,
            )
        }
        if (joined) {
            Spacer(Modifier.height(UiSpacing.md))
            Text("我的房间内信息", style = UiTypography.label.copyWith(color = UiColors.textMuted))
            Spacer(Modifier.height(UiSpacing.xs))
            _RoomCardPersonRow(
                avatarLabel = _currentUserAvatarLabel(currentUser),
                avatarUrl = myAvatarUrl,
                defaultAvatarKey = myDefaultAvatarKey,
                name = myName,
                trailing = { RoleBadge(label = myRole) },
            )
        }
        Spacer(Modifier.height(UiSpacing.sm))
        HoverCardSelectableText(
            value = "RID: $rid",
            copyStartOffset = "RID: ".length,
            style = UiTypography.label.copyWith(color = UiColors.textMuted),
        )
        if (joined) {
            Spacer(Modifier.height(UiSpacing.md))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    icon = { Icon(Icons.AutoMirrored.Rounded.Login) },
                    tone = ButtonTone.primary,
                    height = 34.dp,
                    padding = EdgeInsets.symmetric(horizontal = 14.dp),
                    onPressed = if (onEnterRoom == null) null else ({ onEnterRoom(room) }),
                ) {
                    Text("进入房间")
                }
            }
        }
    }
}

@Composable
private fun _DeletedRoomProfileCard() {
    Row(
        Modifier.padding(EdgeInsets.all(UiSpacing.lg)).testTag(deletedRoomProfileCardTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(label = "", defaultAvatarKey = "graphite-2", size = 42.dp, showFallbackText = false)
        Spacer(Modifier.width(UiSpacing.md))
        Text("房间已删除", style = UiTypography.body.copyWith(color = UiColors.textSecondary))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _RoomCardPersonRow(
    avatarLabel: String,
    avatarUrl: String?,
    defaultAvatarKey: String,
    name: String,
    profileUser: UserSummary? = null,
    currentUser: CurrentUser? = null,
    onResolveUserProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterCommonRoom: ((PublicRoom) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val nameStyle = UiTypography.body.copyWith(color = UiColors.textSecondary, fontSize = 13.0)
    val config = LocalAppConfig.current
    val identity: @Composable () -> Unit = {
        _CompactProfileIdentity(
            name = name,
            style = nameStyle,
            avatarBuilder = { size ->
                val avatar: @Composable () -> Unit = {
                    Avatar(
                        label = avatarLabel,
                        imageUrl = config.resolveAssetUrl(avatarUrl),
                        defaultAvatarKey = defaultAvatarKey,
                        size = size,
                        activeBorderWidth = 1.dp,
                    )
                }
                if (profileUser == null || currentUser == null) {
                    avatar()
                } else {
                    UserHoverCard(
                        user = profileUser,
                        currentUser = currentUser,
                        onResolveProfile = onResolveUserProfile,
                        onResolveRoomProfile = onResolveRoomProfile,
                        onEnterCommonRoom = onEnterCommonRoom,
                        child = avatar,
                    )
                }
            },
        )
    }
    if (trailing == null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { identity() }
        return
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        identity()
        trailing()
    }
}

private fun _myRoomDisplayName(room: PublicRoom, currentUser: CurrentUser): String =
    _nonEmpty(room.personalProfile.displayName) ?: currentUser.displayName

@Suppress("UNUSED_PARAMETER")
private fun _myRoomAvatarUrl(room: PublicRoom, currentUser: CurrentUser): String? = currentUser.avatarUrl

@Suppress("UNUSED_PARAMETER")
private fun _myRoomDefaultAvatarKey(room: PublicRoom, currentUser: CurrentUser): String = currentUser.defaultAvatarKey

private fun _currentUserAvatarLabel(currentUser: CurrentUser): String = userAvatarLabel(currentUser.toSummary())

private fun _myRoomRoleLabel(room: PublicRoom, currentUser: CurrentUser): String {
    val role = roomRoleLabelFromValue(room.myMembership?.role)
    if (role != null) return role
    return if (currentUser.isSuperuser) "超级用户" else "成员"
}

private fun _nonEmpty(value: String?): String? {
    val trimmed = value?.trim()
    return if (trimmed == null || trimmed.isEmpty()) null else trimmed
}

private fun _publicRoomFromCommonRoom(room: UserCommonRoom): PublicRoom =
    PublicRoom(
        id = room.id,
        rid = room.rid,
        name = commonRoomDisplayName(room),
        avatarLabel = commonRoomAvatarLabel(room),
        avatarUrl = room.avatarUrl,
        defaultAvatarKey = room.defaultAvatarKey,
        visibility = room.visibility,
        joinPolicy = "closed",
        memberCount = 0,
        onlineMemberCount = 0,
        liveParticipantCount = 0,
        joined = true,
        joinState = "joined",
    )

/** Test-only entry point (Dart's `@visibleForTesting RoomHoverCardForTest`). */
@Composable
fun RoomHoverCardForTest(
    room: PublicRoom,
    currentUser: CurrentUser,
    modifier: Modifier = Modifier,
    onResolveRoom: RoomProfileResolver? = null,
    onResolveUserProfile: UserProfileResolver? = null,
    onEnterRoom: ((PublicRoom) -> Unit)? = null,
) {
    RoomHoverCard(
        room = room,
        currentUser = currentUser,
        modifier = modifier,
        onResolveRoom = onResolveRoom,
        onResolveUserProfile = onResolveUserProfile,
        onEnterRoom = onEnterRoom,
    ) {
        Avatar(label = publicRoomAvatarLabel(room), size = 34.dp)
    }
}

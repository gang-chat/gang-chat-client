package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.formatDateTime
import com.gangchat.client.app.publicRoomAvatarLabel
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.app.userPrimaryName
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.LocalAppConfig
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiSpacing
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FittedBox
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.padding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

/**
 * Presentation data for a playlist hover card.
 *
 * The music-box snapshot intentionally does not invent metadata that the
 * server does not provide. For example, the temporary queue has no creation
 * timestamp, so [createdAt] remains null and the card renders it as unknown.
 *
 * [showPlayingStatus] is true only when the card was opened from the
 * authoritative current-source header: it replaces the play-all action with a
 * disabled playing action. [showCreatedAt] renders the creation timestamp row
 * (temporary queues omit it rather than show a misleading "unknown").
 */
class MusicPlaylistCardData(
    val id: String,
    val name: String,
    val songCount: Int,
    val createdAt: DateTime?,
    val creator: UserSummary? = null,
    val room: PublicRoom? = null,
    val showPlayingStatus: Boolean = false,
    val showCreatedAt: Boolean = true,
) {
    fun copyWith(name: String? = null, songCount: Int? = null, createdAt: DateTime? = null, creator: UserSummary? = null) =
        MusicPlaylistCardData(
            id = id,
            name = name ?: this.name,
            songCount = songCount ?: this.songCount,
            createdAt = createdAt ?: this.createdAt,
            creator = creator ?: this.creator,
            room = room,
            showPlayingStatus = showPlayingStatus,
            showCreatedAt = showCreatedAt,
        )
}

typealias MusicPlaylistCardResolver = suspend (current: MusicPlaylistCardData) -> MusicPlaylistCardData

typealias MusicPlaylistViewCallback = suspend (playlist: PersonalMusicPlaylist, roomScoped: Boolean) -> Unit

typealias MusicPlaylistEditCallback = suspend (playlistId: String) -> Unit

/** Dart's `MusicPlaylistCardHostScope` inherited data. */
class MusicPlaylistCardHostScopeData(
    val currentUser: CurrentUser?,
    val room: PublicRoom?,
    val onResolveUserProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    val userProfileActionBuilder: UserProfileActionBuilder?,
    val onStateChanged: ((MusicBoxState) -> Unit)?,
    val onViewPlaylist: MusicPlaylistViewCallback,
    val onEditPersonalPlaylist: MusicPlaylistEditCallback? = null,
    val onEditRoomPlaylist: MusicPlaylistEditCallback? = null,
)

private val LocalMusicPlaylistCardHostScope = staticCompositionLocalOf<MusicPlaylistCardHostScopeData?> { null }

/**
 * Supplies playlist identity and navigation actions to cards opened several
 * layers below the music-box body (notably the add-to-playlist picker inside a
 * song card) without duplicating those parameters through every song widget.
 */
@Composable
fun MusicPlaylistCardHostScope(data: MusicPlaylistCardHostScopeData, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMusicPlaylistCardHostScope provides data, content = content)
}

object MusicPlaylistCardHostScope {
    fun maybeOf(context: BuildContext): MusicPlaylistCardHostScopeData? = context[LocalMusicPlaylistCardHostScope]
}

/** Test tag of a playlist card (Dart's `ValueKey('music-playlist-card:$id')`). */
fun musicPlaylistCardTag(id: String) = "music-playlist-card:$id"

const val musicPlaylistCardTitleTag = "music-playlist-card-title"
const val musicPlaylistCardTitleIconTag = "music-playlist-card-title-icon"
const val musicPlaylistCardPlayAllTag = "music-playlist-card-play-all"
const val musicPlaylistCardViewTag = "music-playlist-card-view"
const val musicPlaylistIdentityNameTag = "music-playlist-identity-name"

/** Shared playlist hover/tap card used by the current-source header and saved playlist rows. */
@Composable
fun MusicPlaylistHoverCard(
    data: MusicPlaylistCardData,
    onPlayAll: (suspend () -> Unit)?,
    onViewPlaylist: (suspend () -> Unit)?,
    modifier: Modifier = Modifier,
    resolveData: MusicPlaylistCardResolver? = null,
    currentUser: CurrentUser? = null,
    onResolveUserProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterCommonRoom: ((PublicRoom) -> Unit)? = null,
    userProfileActionBuilder: UserProfileActionBuilder? = null,
    primaryActionLabel: String = "播放全部",
    primaryActionIcon: ImageVector = Icons.Filled.PlayArrow,
    showPrimaryActionWhenDisabled: Boolean = true,
    secondaryActionLabel: String = "查看歌单",
    secondaryActionIcon: ImageVector = Icons.AutoMirrored.Filled.QueueMusic,
    secondaryActionTone: ButtonTone = ButtonTone.neutral,
    child: @Composable () -> Unit,
) = StatefulWidget(
    MusicPlaylistHoverCardWidget(
        data, child, onPlayAll, onViewPlaylist, resolveData, currentUser, onResolveUserProfile, onResolveRoomProfile, onEnterCommonRoom,
        userProfileActionBuilder, primaryActionLabel, primaryActionIcon, showPrimaryActionWhenDisabled, secondaryActionLabel,
        secondaryActionIcon, secondaryActionTone, modifier,
    ),
) { _MusicPlaylistHoverCardState() }

class MusicPlaylistHoverCardWidget(
    val data: MusicPlaylistCardData,
    val child: @Composable () -> Unit,
    val onPlayAll: (suspend () -> Unit)?,
    val onViewPlaylist: (suspend () -> Unit)?,
    val resolveData: MusicPlaylistCardResolver?,
    val currentUser: CurrentUser?,
    val onResolveUserProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    val userProfileActionBuilder: UserProfileActionBuilder?,
    val primaryActionLabel: String,
    val primaryActionIcon: ImageVector,
    val showPrimaryActionWhenDisabled: Boolean,
    val secondaryActionLabel: String,
    val secondaryActionIcon: ImageVector,
    val secondaryActionTone: ButtonTone,
    val modifier: Modifier,
)

private class _MusicPlaylistHoverCardState : ViewState<MusicPlaylistHoverCardWidget>() {
    private var _resolved: MusicPlaylistCardData? = null
    private var _resolveFuture: Deferred<Unit>? = null
    private var _playing = false
    private var _viewing = false
    private var _dismissEpoch = 0

    private val _displayData: MusicPlaylistCardData get() = _resolved ?: widget.data

    override fun didUpdateWidget(oldWidget: MusicPlaylistHoverCardWidget) {
        val old = oldWidget.data
        val new = widget.data
        if (old.id == new.id &&
            old.name == new.name &&
            old.songCount == new.songCount &&
            old.createdAt == new.createdAt &&
            old.creator?.id == new.creator?.id &&
            old.creator?.roomDisplayName == new.creator?.roomDisplayName &&
            old.creator?.displayName == new.creator?.displayName &&
            old.creator?.avatarUrl == new.creator?.avatarUrl &&
            old.room?.id == new.room?.id &&
            old.showPlayingStatus == new.showPlayingStatus &&
            old.showCreatedAt == new.showCreatedAt
        ) {
            return
        }
        _resolved = null
        _resolveFuture = null
    }

    private suspend fun _resolve() {
        val playlistResolver = widget.resolveData
        val creatorResolver = widget.onResolveUserProfile
        if (playlistResolver == null && (creatorResolver == null || widget.data.creator == null)) return
        val existing = _resolveFuture
        if (existing != null) return existing.await()
        val requested = widget.data
        val future =
            future {
                try {
                    var resolved = requested
                    if (playlistResolver != null) {
                        try {
                            resolved = playlistResolver(requested)
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            // Keep the snapshot metadata visible when richer playlist
                            // metadata is unavailable (for example another user's
                            // private playlist).
                        }
                    }
                    val creator = resolved.creator
                    if (creatorResolver != null && creator != null) {
                        try {
                            val profile = creatorResolver(creator)
                            resolved = resolved.copyWith(creator = profile)
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            // The lightweight identity remains usable when its
                            // room-member profile cannot be refreshed.
                        }
                    }
                    if (mounted && widget.data.id == requested.id) setState { _resolved = resolved }
                } finally {
                    if (mounted && widget.data.id == requested.id) _resolveFuture = null
                }
            }
        if (!future.isCompleted) _resolveFuture = future
        future.await()
    }

    private suspend fun _playAll() {
        val callback = widget.onPlayAll
        if (callback == null || _playing) return
        setState { _playing = true }
        try {
            callback()
        } finally {
            if (mounted) setState { _playing = false }
        }
    }

    private suspend fun _viewPlaylist() {
        val callback = widget.onViewPlaylist
        if (callback == null || _viewing) return
        setState { _viewing = true }
        try {
            callback()
        } finally {
            if (mounted) {
                setState {
                    _viewing = false
                    // Viewing replaces the underlying browser or reveals the
                    // already visible current queue. Resetting the anchor closes
                    // the pinned card instead of leaving it over the content the
                    // user asked to see.
                    _dismissEpoch += 1
                }
            }
        }
    }

    @Composable
    override fun build() {
        val data = _displayData
        HoverCardAnchor(
            modifier = widget.modifier,
            cardWidth = 304.dp,
            resetKey = listOf(widget.data.id, widget.data.showPlayingStatus, _dismissEpoch),
            onBeforeOpen =
                if (widget.resolveData == null && (widget.onResolveUserProfile == null || widget.data.creator == null)) {
                    null
                } else {
                    { _resolve() }
                },
            cardBuilder = { _ ->
                _MusicPlaylistProfileCard(
                    data = data,
                    currentUser = widget.currentUser,
                    onResolveUserProfile = widget.onResolveUserProfile,
                    onResolveRoomProfile = widget.onResolveRoomProfile,
                    onEnterCommonRoom = widget.onEnterCommonRoom,
                    userProfileActionBuilder = widget.userProfileActionBuilder,
                    playing = _playing,
                    viewing = _viewing,
                    onPlayAll = if (widget.onPlayAll == null) null else ({ unawaited { _playAll() } }),
                    onViewPlaylist = if (widget.onViewPlaylist == null) null else ({ unawaited { _viewPlaylist() } }),
                    primaryActionLabel = widget.primaryActionLabel,
                    primaryActionIcon = widget.primaryActionIcon,
                    showPrimaryActionWhenDisabled = widget.showPrimaryActionWhenDisabled,
                    secondaryActionLabel = widget.secondaryActionLabel,
                    secondaryActionIcon = widget.secondaryActionIcon,
                    secondaryActionTone = widget.secondaryActionTone,
                )
            },
            child = widget.child,
        )
    }
}

@Composable
private fun _MusicPlaylistProfileCard(
    data: MusicPlaylistCardData,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
    playing: Boolean,
    viewing: Boolean,
    onPlayAll: (() -> Unit)?,
    onViewPlaylist: (() -> Unit)?,
    primaryActionLabel: String,
    primaryActionIcon: ImageVector,
    showPrimaryActionWhenDisabled: Boolean,
    secondaryActionLabel: String,
    secondaryActionIcon: ImageVector,
    secondaryActionTone: ButtonTone,
) {
    val isRoomPlaylist = data.room != null
    Column(Modifier.testTag(musicPlaylistCardTag(data.id)).fillMaxWidth().padding(EdgeInsets.all(UiSpacing.lg))) {
        Row(Modifier.testTag(musicPlaylistCardTitleTag).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                modifier = Modifier.testTag(musicPlaylistCardTitleIconTag),
                size = 22.dp,
                color = UiColors.accent,
            )
            Spacer(Modifier.width(UiSpacing.sm))
            HoverCardSelectableText(
                value = data.name,
                maxLines = 12,
                style = UiTypography.title.copyWith(fontSize = 16.0),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(UiSpacing.md))
        _MusicPlaylistDetailRow(label = "歌曲数量") {
            Text("${data.songCount} 首", textAlign = TextAlign.End, style = UiTypography.label)
        }
        Spacer(Modifier.height(UiSpacing.sm))
        _MusicPlaylistDetailRow(label = if (isRoomPlaylist) "房间" else "创建人") {
            _MusicPlaylistIdentity(
                creator = data.creator,
                room = data.room,
                currentUser = currentUser,
                onResolveUserProfile = onResolveUserProfile,
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterCommonRoom = onEnterCommonRoom,
                userProfileActionBuilder = userProfileActionBuilder,
            )
        }
        if (data.showCreatedAt) {
            Spacer(Modifier.height(UiSpacing.sm))
            _MusicPlaylistDetailRow(label = "创建日期") {
                HoverCardSelectableText(
                    value = formatDateTime(data.createdAt),
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    style = UiTypography.label,
                )
            }
        }
        Spacer(Modifier.height(UiSpacing.lg))
        ResponsiveDialogActionBar(
            expanded = true,
            modifier = Modifier.fillMaxWidth(),
            actions =
                listOfNotNull(
                    if (onPlayAll != null || showPrimaryActionWhenDisabled) {
                        ResponsiveDialogAction(
                            buttonKey = musicPlaylistCardPlayAllTag,
                            label = if (data.showPlayingStatus) "正在播放" else primaryActionLabel,
                            icon = if (data.showPlayingStatus) Icons.Filled.PlayArrow else primaryActionIcon,
                            tone = ButtonTone.primary,
                            loading = !data.showPlayingStatus && playing,
                            onPressed = if (data.showPlayingStatus) null else onPlayAll,
                        )
                    } else {
                        null
                    },
                    ResponsiveDialogAction(
                        buttonKey = musicPlaylistCardViewTag,
                        label = secondaryActionLabel,
                        icon = secondaryActionIcon,
                        tone = secondaryActionTone,
                        loading = viewing,
                        onPressed = onViewPlaylist,
                    ),
                ),
        )
    }
}

@Composable
private fun _MusicPlaylistDetailRow(label: String, child: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(58.dp)) { Text(label, style = UiTypography.label.copyWith(color = UiColors.textMuted)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { child() }
    }
}

@Composable
private fun _MusicPlaylistIdentity(
    creator: UserSummary?,
    room: PublicRoom?,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
) {
    val config = LocalAppConfig.current
    if (room != null) {
        val identity: @Composable () -> Unit = {
            _MusicPlaylistIdentityValue(
                label = publicRoomAvatarLabel(room),
                imageUrl = config.resolveAssetUrl(room.avatarUrl),
                defaultAvatarKey = room.defaultAvatarKey,
                name = room.name,
            )
        }
        if (currentUser == null) {
            identity()
            return
        }
        RoomHoverCard(
            room = room,
            currentUser = currentUser,
            onResolveRoom = onResolveRoomProfile,
            onResolveUserProfile = onResolveUserProfile,
            onEnterRoom = onEnterCommonRoom,
            child = identity,
        )
        return
    }

    if (creator != null) {
        UserHoverCard(
            user = creator,
            currentUser = currentUser,
            onResolveProfile = onResolveUserProfile,
            onResolveRoomProfile = onResolveRoomProfile,
            onEnterCommonRoom = onEnterCommonRoom,
            profileActionBuilder = userProfileActionBuilder,
            showRoomRole = true,
        ) {
            _MusicPlaylistIdentityValue(
                label = userAvatarLabel(creator),
                imageUrl = config.resolveAssetUrl(creator.avatarUrl),
                defaultAvatarKey = creator.defaultAvatarKey,
                name = userPrimaryName(creator),
            )
        }
        return
    }

    Text("未知", textAlign = TextAlign.End, style = UiTypography.label.copyWith(color = UiColors.textMuted))
}

@Composable
private fun _MusicPlaylistIdentityValue(label: String, imageUrl: String?, defaultAvatarKey: String, name: String) {
    val style = UiTypography.label.copyWith(color = UiColors.text)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        FittedBox(alignment = Alignment.CenterEnd, fit = BoxFit.scaleDown) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(label = label, imageUrl = imageUrl, defaultAvatarKey = defaultAvatarKey, size = 24.dp, showBorder = false)
                Spacer(Modifier.width(6.dp))
                Text(
                    name,
                    modifier = Modifier.testTag(musicPlaylistIdentityNameTag),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    style = style,
                )
            }
        }
    }
}

package com.gangchat.client.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicBoxController
import com.gangchat.client.app.musicBoxArtistFieldLabel
import com.gangchat.client.app.musicBoxArtistsLabel
import com.gangchat.client.app.musicBoxControlErrorMessage
import com.gangchat.client.app.musicBoxFormatDuration
import com.gangchat.client.app.musicBoxSourceLabel
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.MusicBoxActiveSource
import com.gangchat.client.protocol.MusicBoxActiveSourceType
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxRequester
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiSpacing
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.rememberBuildContext
import kotlinx.coroutines.CancellationException

// Song profile card shared by every music-box row: queue items, search hits,
// and saved-playlist tracks. Opened as a hover card on the row's text area;
// its actions (优先播放 / 添加到歌单) are gated by server capabilities.

internal fun _musicBoxActiveSourceOwner(source: MusicBoxActiveSource, currentUser: CurrentUser?): UserSummary? {
    val owner = source.owner
    if (owner != null) {
        return UserSummary(
            id = owner.userId,
            username = owner.username,
            displayName = owner.avatarLabel,
            roomDisplayName = owner.displayName,
            avatarUrl = owner.avatarUrl,
            defaultAvatarKey = owner.defaultAvatarKey,
        )
    }
    if (currentUser != null && (source.ownerUserId.isEmpty() || source.ownerUserId == currentUser.id)) {
        return currentUser.toSummary()
    }
    return null
}

private fun _musicBoxPlaylistById(playlists: List<PersonalMusicPlaylist>, id: String): PersonalMusicPlaylist? {
    for (playlist in playlists) {
        if (playlist.id == id) return playlist
    }
    return null
}

/** Dart's `_MusicBoxSongCard.queue`. */
@Composable
internal fun _MusicBoxSongCardQueue(
    item: MusicBoxQueueItem,
    isCurrent: Boolean,
    queueSongCount: Int,
    controller: MusicBoxController?,
    roomId: String?,
    activeSource: MusicBoxActiveSource,
    onStateChanged: ((MusicBoxState) -> Unit)?,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
) = StatefulWidget(
    _MusicBoxSongCardWidget(
        item = item,
        result = null,
        catalogDurationMs = null,
        isCurrent = isCurrent,
        queueSongCount = queueSongCount,
        controller = controller,
        roomId = roomId,
        activeSource = activeSource,
        onStateChanged = onStateChanged,
        onQueueResult = null,
        alreadyInRequestQueue = false,
        currentUser = currentUser,
        onResolveUserProfile = onResolveUserProfile,
        onResolveRoomProfile = onResolveRoomProfile,
        onEnterCommonRoom = onEnterCommonRoom,
        userProfileActionBuilder = userProfileActionBuilder,
    ),
) { _MusicBoxSongCardState() }

/** Dart's `_MusicBoxSongCard.search`. */
@Composable
internal fun _MusicBoxSongCardSearch(
    result: MusicBoxSearchResult,
    controller: MusicBoxController?,
    roomId: String?,
    onQueueResult: ((MusicBoxSearchResult) -> Unit)?,
    alreadyInRequestQueue: Boolean,
    catalogDurationMs: Int? = null,
) = StatefulWidget(
    _MusicBoxSongCardWidget(
        item = null,
        result = result,
        catalogDurationMs = catalogDurationMs,
        isCurrent = false,
        queueSongCount = 0,
        controller = controller,
        roomId = roomId,
        activeSource = null,
        onStateChanged = null,
        onQueueResult = onQueueResult,
        alreadyInRequestQueue = alreadyInRequestQueue,
        currentUser = null,
        onResolveUserProfile = null,
        onResolveRoomProfile = null,
        onEnterCommonRoom = null,
        userProfileActionBuilder = null,
    ),
) { _MusicBoxSongCardState() }

private class _MusicBoxSongCardWidget(
    val item: MusicBoxQueueItem?,
    val result: MusicBoxSearchResult?,
    val catalogDurationMs: Int?,
    val isCurrent: Boolean,
    val queueSongCount: Int,
    val controller: MusicBoxController?,
    val roomId: String?,
    val activeSource: MusicBoxActiveSource?,
    val onStateChanged: ((MusicBoxState) -> Unit)?,
    val onQueueResult: ((MusicBoxSearchResult) -> Unit)?,
    val alreadyInRequestQueue: Boolean,
    val currentUser: CurrentUser?,
    val onResolveUserProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    val userProfileActionBuilder: UserProfileActionBuilder?,
)

private class _MusicBoxSongCardState : ViewState<_MusicBoxSongCardWidget>() {
    private var _playingNow = false

    private suspend fun _playNow() {
        val controller = widget.controller
        val roomId = widget.roomId
        val item = widget.item
        if (controller == null || roomId == null || item == null || _playingNow) return
        setState { _playingNow = true }
        try {
            val state = controller.playNow(roomId = roomId, item = item)
            widget.onStateChanged?.invoke(state)
            if (mounted) showFloatingSuccessNotice(context, "已优先播放")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) showFloatingErrorNotice(context, musicBoxControlErrorMessage(error, "优先播放失败，请重试"))
        } finally {
            if (mounted) setState { _playingNow = false }
        }
    }

    private suspend fun _openPlaylistPicker() {
        _musicBoxAddToPlaylist(
            context,
            controller = widget.controller,
            roomId = widget.roomId,
            item = widget.item,
            result = widget.result,
            durationMs = widget.catalogDurationMs,
        )
    }

    @Composable
    override fun build() {
        val item = widget.item
        val result = widget.result
        val isSearchResult = result != null
        val title = item?.title ?: result!!.name
        val artist = item?.artist?.trim() ?: musicBoxArtistsLabel(result!!.artists).trim()
        val source = item?.source ?: result!!.source
        val trackId = item?.trackId ?: result!!.trackId
        val durationMs = item?.durationMs ?: widget.catalogDurationMs ?: 0
        val isBilibili = source.trim().lowercase() == "bilibili"
        val canPlayNow = item != null && item.canPlayNow && item.playable && widget.controller != null && widget.roomId != null
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .testTag(
                    if (isSearchResult) "music-box-song-card:search:${result!!.source}:${result.trackId}" else "music-box-song-card:${item!!.id}",
                )
                .padding(UiSpacing.lg),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, color = UiColors.accent, size = 28.dp)
                Spacer(Modifier.width(UiSpacing.sm))
                _MusicBoxAdaptiveFullText(
                    modifier = Modifier.weight(1f).testTag("music-box-song-card-title"),
                    text = title,
                    style = UiTypography.title.copyWith(fontSize = 16.0),
                )
            }
            Spacer(Modifier.height(UiSpacing.md))
            _MusicBoxSongDetailRow(label = "时长", value = if (durationMs > 0) musicBoxFormatDuration(durationMs) else "未知")
            _MusicBoxSongDetailRow(
                label = musicBoxArtistFieldLabel(source),
                value = if (artist.isEmpty()) (if (isBilibili) "未知作者" else "未知艺人") else artist,
            )
            _MusicBoxSongDetailRow(label = "来源", value = musicBoxSourceLabel(source))
            if (!isSearchResult) {
                Spacer(Modifier.height(UiSpacing.sm))
                _MusicBoxSongAttribution(
                    source = widget.activeSource!!,
                    requester = item!!.requestedBy,
                    songCount = widget.queueSongCount,
                    controller = widget.controller,
                    roomId = widget.roomId,
                    onStateChanged = widget.onStateChanged,
                    currentUser = widget.currentUser,
                    onResolveUserProfile = widget.onResolveUserProfile,
                    onResolveRoomProfile = widget.onResolveRoomProfile,
                    onEnterCommonRoom = widget.onEnterCommonRoom,
                    userProfileActionBuilder = widget.userProfileActionBuilder,
                )
            }
            if (isBilibili) {
                Spacer(Modifier.height(UiSpacing.sm))
                _MusicBoxSongDetailRow(label = "详情", value = _musicBoxBilibiliBvId(trackId))
            }
            Spacer(Modifier.height(UiSpacing.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Preset tracks pass no queue callback: they are played from
                // the music box, not requested, so the card shows no queue
                // action for them.
                if (isSearchResult && (widget.onQueueResult != null || widget.alreadyInRequestQueue)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        width = Dp.Infinity,
                        icon = {
                            Icon(if (widget.alreadyInRequestQueue) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd)
                        },
                        tone = if (widget.alreadyInRequestQueue) ButtonTone.neutral else ButtonTone.primary,
                        height = 34.dp,
                        onPressed = if (widget.alreadyInRequestQueue) null else ({ widget.onQueueResult?.invoke(result!!) }),
                    ) {
                        Text(if (widget.alreadyInRequestQueue) "已在队列中" else "点歌队列")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (canPlayNow) {
                    Button(
                        modifier = Modifier.weight(1f),
                        width = Dp.Infinity,
                        icon = { Icon(Icons.Filled.PlayArrow) },
                        height = 34.dp,
                        loading = _playingNow,
                        onPressed = if (widget.isCurrent) null else ({ unawaited { _playNow() } }),
                    ) {
                        Text(if (widget.isCurrent) "正在播放" else "优先播放")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    width = Dp.Infinity,
                    icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd) },
                    tone = ButtonTone.primary,
                    height = 34.dp,
                    onPressed = if (widget.controller == null) null else ({ unawaited { _openPlaylistPicker() } }),
                ) {
                    Text("添加到歌单")
                }
            }
        }
    }
}

@Composable
private fun _MusicBoxSongAttribution(
    source: MusicBoxActiveSource,
    requester: MusicBoxRequester?,
    songCount: Int,
    controller: MusicBoxController?,
    roomId: String?,
    onStateChanged: ((MusicBoxState) -> Unit)?,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
) {
    val context = rememberBuildContext()

    fun playlistResolver(): MusicPlaylistCardResolver? {
        val musicController = controller
        if (musicController == null || source.id.isEmpty()) return null
        if (source.type == MusicBoxActiveSourceType.roomPlaylist) {
            val currentRoomId = roomId ?: return null
            return { current ->
                val page = musicController.loadRoomPlaylists(roomId = currentRoomId)
                val playlist = _musicBoxPlaylistById(page.playlists, source.id)
                if (playlist == null) {
                    current
                } else {
                    current.copyWith(name = playlist.name, songCount = playlist.itemCount, createdAt = playlist.createdAt)
                }
            }
        }
        val ownerId = source.ownerUserId.ifEmpty { source.owner?.userId ?: "" }
        if (source.type != MusicBoxActiveSourceType.userPlaylist ||
            currentUser == null ||
            (ownerId.isNotEmpty() && ownerId != currentUser.id)
        ) {
            return null
        }
        return { current ->
            val page = musicController.loadMyPlaylists()
            val playlist = _musicBoxPlaylistById(page.playlists, source.id)
            if (playlist == null) {
                current
            } else {
                current.copyWith(name = playlist.name, songCount = playlist.itemCount, createdAt = playlist.createdAt)
            }
        }
    }

    suspend fun playPlaylist() {
        val musicController = controller
        val currentRoomId = roomId
        if (musicController == null || currentRoomId == null || source.id.isEmpty()) return
        try {
            val state = musicController.activatePlaylist(roomId = currentRoomId, sourceType = source.type, playlistId = source.id)
            onStateChanged?.invoke(state)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (context.mounted) showFloatingErrorNotice(context, musicBoxControlErrorMessage(error, "播放歌单失败，请重试"))
        }
    }

    val isPlaylist = source.type != MusicBoxActiveSourceType.temporary
    val playlistName = if (source.name.trim().isEmpty()) "未命名歌单" else source.name
    val host = MusicPlaylistCardHostScope.maybeOf(context)
    val creator = if (isPlaylist) _musicBoxActiveSourceOwner(source, currentUser) else null
    val ownerId = source.ownerUserId.ifEmpty { source.owner?.userId ?: "" }
    val viewer = currentUser
    val isOwnedPersonalPlaylist =
        source.type == MusicBoxActiveSourceType.userPlaylist && viewer != null && (ownerId.isEmpty() || ownerId == viewer.id)
    val editPlaylist: MusicPlaylistEditCallback? =
        when {
            source.type == MusicBoxActiveSourceType.roomPlaylist -> host?.onEditRoomPlaylist
            isOwnedPersonalPlaylist -> host?.onEditPersonalPlaylist
            else -> null
        }
    val value: @Composable () -> Unit =
        when {
            isPlaylist && source.id.isNotEmpty() -> {
                {
                    MusicPlaylistHoverCard(
                        data =
                            MusicPlaylistCardData(
                                id = source.id,
                                name = playlistName,
                                songCount = songCount,
                                createdAt = source.createdAt,
                                creator = if (source.type == MusicBoxActiveSourceType.userPlaylist) creator else null,
                                room = if (source.type == MusicBoxActiveSourceType.roomPlaylist) host?.room else null,
                            ),
                        resolveData = playlistResolver(),
                        currentUser = currentUser,
                        onResolveUserProfile = onResolveUserProfile,
                        onResolveRoomProfile = onResolveRoomProfile,
                        onEnterCommonRoom = onEnterCommonRoom,
                        userProfileActionBuilder = userProfileActionBuilder,
                        onPlayAll = if (controller == null || roomId == null) null else ({ playPlaylist() }),
                        onViewPlaylist =
                            when {
                                editPlaylist != null -> ({ editPlaylist(source.id) })
                                host == null -> null
                                else -> ({
                                    host.onViewPlaylist(
                                        PersonalMusicPlaylist(
                                            id = source.id,
                                            name = playlistName,
                                            description = "",
                                            revision = 0,
                                            itemCount = songCount,
                                            createdAt = source.createdAt,
                                            updatedAt = null,
                                        ),
                                        source.type == MusicBoxActiveSourceType.roomPlaylist,
                                    )
                                })
                            },
                        secondaryActionLabel = if (editPlaylist == null) "查看歌单" else "编辑歌单",
                        secondaryActionIcon = if (editPlaylist == null) Icons.AutoMirrored.Filled.QueueMusic else Icons.Outlined.Edit,
                    ) {
                        _MusicBoxPlaylistAttributionValue(name = playlistName)
                    }
                }
            }
            isPlaylist -> ({ _MusicBoxPlaylistAttributionValue(name = playlistName) })
            else -> ({
                _MusicBoxSongAttributionValue(
                    value = requester?.displayName ?: "未知用户",
                    person = requester,
                    showAvatar = requester != null,
                    currentUser = currentUser,
                    onResolveUserProfile = onResolveUserProfile,
                    onResolveRoomProfile = onResolveRoomProfile,
                    onEnterCommonRoom = onEnterCommonRoom,
                    userProfileActionBuilder = userProfileActionBuilder,
                )
            })
        }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(38.dp)) {
            HoverCardSelectableText(
                value = if (isPlaylist) "歌单" else "点歌人",
                style = UiTypography.label.copyWith(color = UiColors.textMuted),
            )
        }
        Box(Modifier.weight(1f)) { value() }
    }
}

@Composable
private fun _MusicBoxPlaylistAttributionValue(name: String) {
    Row(
        Modifier.fillMaxWidth().testTag("music-box-song-playlist-attribution"),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.QueueMusic,
            modifier = Modifier.testTag("music-box-song-playlist-icon"),
            size = 17.dp,
            color = UiColors.accent,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            name,
            modifier = Modifier.weight(1f, fill = false).testTag("music-box-song-attribution-name"),
            textAlign = TextAlign.End,
            softWrap = true,
            style = UiTypography.label.copyWith(color = UiColors.text),
        )
    }
}

@Composable
private fun _MusicBoxSongAttributionValue(
    value: String,
    person: MusicBoxRequester?,
    showAvatar: Boolean,
    currentUser: CurrentUser?,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
) {
    val context = rememberBuildContext()
    val style = UiTypography.label.copyWith(color = UiColors.text)
    val user =
        if (person == null) {
            null
        } else {
            UserSummary(
                id = person.userId,
                username = person.displayName,
                displayName = person.displayName,
                avatarUrl = person.avatarUrl,
                defaultAvatarKey = person.defaultAvatarKey,
            )
        }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val avatarSize = 26.0
        val avatarGap = 5.0
        // TextPainter and the rendered paragraph can differ by a few physical
        // pixels after font fallback and device-pixel rounding. Keep enough
        // trailing room that a measured single line never loses its final glyph.
        val textLayoutSlack = 8.0
        val avatarWidth = if (showAvatar) avatarSize + avatarGap else 0.0
        val maxTextWidth = (maxWidth.value - avatarWidth).coerceAtLeast(1.0)
        val measurementWidth = (maxTextWidth - textLayoutSlack).coerceIn(1.0, maxTextWidth)
        val painter =
            // Like Dart's TextPainter, the style is measured as given.
            TextPainter(if (value.isEmpty()) " " else value, style)
                .layout(context, maxWidth = measurementWidth.dp)
        val longestLine = painter.maxIntrinsicWidth.coerceAtMost(measurementWidth)
        val textWidth = if (painter.lineCount > 1) maxTextWidth else (longestLine + textLayoutSlack).coerceIn(1.0, maxTextWidth)
        Row(
            Modifier.fillMaxWidth().testTag("music-box-song-attribution-value-group"),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAvatar && user != null && person != null) {
                UserHoverCard(
                    user = user,
                    currentUser = currentUser,
                    onResolveProfile = onResolveUserProfile,
                    onResolveRoomProfile = onResolveRoomProfile,
                    onEnterCommonRoom = onEnterCommonRoom,
                    profileActionBuilder = userProfileActionBuilder,
                    showRoomRole = true,
                ) {
                    Avatar(
                        modifier = Modifier.testTag("music-box-song-attribution-avatar"),
                        label = person.avatarLabel,
                        imageUrl = AppConfigScope.of(context).resolveAssetUrl(person.avatarUrl),
                        defaultAvatarKey = person.defaultAvatarKey,
                        size = avatarSize.dp,
                        showBorder = false,
                    )
                }
                Spacer(Modifier.width(avatarGap.dp))
            }
            Text(
                value,
                modifier = Modifier.width(textWidth.dp).testTag("music-box-song-attribution-name"),
                textAlign = TextAlign.End,
                style = style,
                softWrap = true,
            )
        }
    }
}

@Composable
private fun _MusicBoxSongDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(38.dp)) {
            HoverCardSelectableText(value = label, style = UiTypography.label.copyWith(color = UiColors.textMuted))
        }
        Box(Modifier.weight(1f)) {
            HoverCardSelectableText(
                value = value,
                textAlign = TextAlign.End,
                style = UiTypography.label.copyWith(color = UiColors.text),
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val _musicBoxBvidPattern = Regex("BV[0-9A-Za-z]+", RegexOption.IGNORE_CASE)

private fun _musicBoxBilibiliBvId(trackId: String): String {
    val raw = trackId.trim()
    val match = _musicBoxBvidPattern.find(raw)?.value
    if (match == null || match.length <= 2) return if (raw.isEmpty()) "未知" else raw
    return "BV${match.substring(2)}"
}

@Composable
private fun _MusicBoxAdaptiveFullText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val context = rememberBuildContext()
    BoxWithConstraints(modifier) {
        val baseSize = if (style.fontSize.isSp) style.fontSize.value.toDouble() else 16.0
        // Like Dart's TextPainter, the style is measured as given.
        val painter = TextPainter(text, style).layout(context, maxWidth = maxWidth)
        val lineCount = painter.lineCount
        val reduction = if (lineCount <= 2) 0.0 else (lineCount - 2).coerceIn(1, 4).toDouble()
        val fontSize = (baseSize - reduction).coerceIn(11.0, baseSize)
        HoverCardSelectableText(
            value = text,
            style = style.copyWith(fontSize = fontSize),
            maxLines = if (lineCount < 1) 1 else lineCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Opens the add-to-playlist picker for a queue item or catalog track and
 * submits to the personal or room playlist API according to the chosen
 * target's scope.
 */
private suspend fun _musicBoxAddToPlaylist(
    context: BuildContext,
    controller: MusicBoxController?,
    roomId: String?,
    item: MusicBoxQueueItem? = null,
    result: MusicBoxSearchResult? = null,
    durationMs: Int? = null,
) {
    if (controller == null) return

    suspend fun loadTargets(): List<MusicTrackPlaylistTarget> {
        val targets = mutableListOf<MusicTrackPlaylistTarget>()
        var roomError: Exception? = null
        var personalError: Exception? = null
        if (roomId != null) {
            try {
                val page = controller.loadRoomPlaylists(roomId = roomId)
                targets.addAll(page.playlists.map { playlist -> MusicTrackPlaylistTarget.room(playlist = playlist, roomId = roomId) })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                roomError = error
            }
        }
        try {
            val page = controller.loadMyPlaylists()
            targets.addAll(page.playlists.map { MusicTrackPlaylistTarget.personal(it) })
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            personalError = error
        }
        if (targets.isEmpty() && (roomError != null || personalError != null)) {
            throw roomError ?: personalError!!
        }
        return targets
    }

    suspend fun add(target: MusicTrackPlaylistTarget) {
        if (target.roomScoped) {
            val targetRoomId = target.roomId ?: roomId ?: return
            if (item != null) {
                controller.addQueueItemToRoomPlaylist(roomId = targetRoomId, playlistId = target.playlist.id, item = item)
            } else {
                controller.addSearchResultToRoomPlaylist(
                    roomId = targetRoomId,
                    playlistId = target.playlist.id,
                    result = result!!,
                    durationMs = durationMs,
                )
            }
            return
        }
        if (item != null) {
            controller.addQueueItemToMyPlaylist(playlistId = target.playlist.id, item = item)
        } else {
            controller.addSearchResultToMyPlaylist(playlistId = target.playlist.id, result = result!!, durationMs = durationMs)
        }
    }

    val target = showMusicTrackPlaylistTargetDialog(context, loadTargets = { loadTargets() }, onAdd = { add(it) })
    if (target == null || !context.mounted) return
    showFloatingSuccessNotice(context, "已添加到「${target.playlist.name}」")
}

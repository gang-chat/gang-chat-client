package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.FileAttachmentTrailingKind
import com.gangchat.client.app.FileAttachmentTrailingState
import com.gangchat.client.app.FileTransferState
import com.gangchat.client.app.MessageContentKind
import com.gangchat.client.app.fileAttachmentInteractionState
import com.gangchat.client.app.fileAttachmentMeta
import com.gangchat.client.app.fileAttachmentPreviewPath
import com.gangchat.client.app.fileAttachmentPreviewSize
import com.gangchat.client.app.fileAttachmentTitle
import com.gangchat.client.app.fileAttachmentTrailingState
import com.gangchat.client.app.fileAttachmentTransferSlot
import com.gangchat.client.app.fileTransferProgressState
import com.gangchat.client.app.formatChatTimestamp
import com.gangchat.client.app.formatDetailedChatTimestamp
import com.gangchat.client.app.formatVoiceBubbleDuration
import com.gangchat.client.app.messageContentKind
import com.gangchat.client.app.messageDeliveryStatusText
import com.gangchat.client.app.shouldShowFileAttachmentBody
import com.gangchat.client.app.stickerAttachmentTitle
import com.gangchat.client.app.stickerPreviewFilename
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.app.voiceAttachmentDuration
import com.gangchat.client.app.voiceMessageAttachment
import com.gangchat.client.app.voiceWaveformWidth
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.ValueNotifier
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.SharedMusicPlaylist
import com.gangchat.client.protocol.SharedMusicTrack
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.app.MusicTrackPreviewController
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.CachedAssetImage
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.LocalAppConfig
import com.gangchat.client.ui.UiAndroidLongPressTracker
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiContextMenuItem
import com.gangchat.client.ui.UiContextMenuSection
import com.gangchat.client.ui.UiContextMenuTriggerRegion
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.fileIconForMime
import com.gangchat.client.ui.removeAnyTextContextMenu
import com.gangchat.client.ui.showFloatingNotice
import com.gangchat.client.ui.showUiContextMenu
import com.gangchat.client.widgets.BoxFit
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
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException

// Part of the chat_messages.dart port: message rows, bubbles and the bodies
// inside them.

fun messageBubbleSurfaceTag(messageId: String) = "message-bubble-surface-$messageId"

fun messageBubbleContentTag(messageId: String) = "message-bubble-content-$messageId"

fun messageQuoteTag(messageId: String) = "message-quote-$messageId"

fun messageQuoteIndicatorTag(messageId: String) = "message-quote-indicator-$messageId"

fun messageQuoteThumbnailTag(messageId: String) = "message-quote-thumbnail-$messageId"

fun composerQuoteCloseTag(messageId: String) = "composer-quote-close-$messageId"

fun sharedMusicTrackMessageTag(id: String) = "shared-music-track-message-$id"

fun sharedMusicPlaylistMessageTag(id: String) = "shared-music-playlist-message-$id"

fun sharedMusicPlaylistOpenTag(id: String) = "shared-music-playlist-open-$id"

const val voiceBodyTag = "voice-body"
const val voiceWaveformTag = "voice-waveform"

/** Test tag of a sticker message's image (Dart finds it as the bubble's `CachedAssetImage`). */
const val chatStickerImageTag = "chat-sticker-image"

/** Test tag of a file tile's image preview (Dart finds it as the tile's `CachedAssetImage`). */
const val chatFilePreviewImageTag = "chat-file-preview-image"

@Composable
internal fun _MessageRow(
    message: Message,
    outgoing: Boolean,
    timestampNow: DateTime,
    showDetailedTimestamps: Boolean,
    currentUser: CurrentUser,
    currentUserMentionIdentity: UserSummary,
    ownerUserId: String?,
    mentionMembers: List<RoomMember>,
    transfer: FileTransferState?,
    fileDownloads: Map<String, FileTransferState>,
    downloadActions: ChatFileDownloadActions,
    voicePlaybackActions: ChatVoicePlaybackActions,
    imagePreviewActions: ChatImagePreviewActions,
    messageActions: ChatMessageActions,
    mentionHighlighted: Boolean,
    mentionTargetsCurrentUser: Boolean,
    inLive: Boolean,
    onResolveSenderProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
    onMentionUser: ((UserSummary) -> Unit)? = null,
    isUserInLive: ((String) -> Boolean)? = null,
) {
    val sender = _senderName(message.sender)
    val senderColor = chatRoomUsernameColor(user = message.sender, currentUser = currentUser, ownerUserId = ownerUserId)
    val config = LocalAppConfig.current

    val avatarHoverCard: @Composable () -> Unit = {
        _AvatarHoverCard(
            user = message.sender,
            currentUser = currentUser,
            onResolveProfile = onResolveSenderProfile,
            onResolveRoomProfile = onResolveRoomProfile,
            onEnterCommonRoom = onEnterProfileRoom,
            profileActionBuilder = profileActionBuilder,
            inLive = inLive,
        ) {
            _MessageAvatarMentionMenu(
                user = message.sender,
                onMentionUser = if (message.sender.id == currentUser.id) null else onMentionUser,
            ) {
                if (message.sender.isBot) {
                    _GangBotAvatar(size = 32.dp)
                } else {
                    Avatar(
                        label = userAvatarLabel(message.sender),
                        imageUrl = config.resolveAssetUrl(message.sender.avatarUrl),
                        defaultAvatarKey = message.sender.defaultAvatarKey,
                        size = 32.dp,
                        showBorder = false,
                    )
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!outgoing) {
            avatarHoverCard()
            Spacer(Modifier.width(10.dp))
        }
        Column(
            Modifier.weight(1f, fill = false),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        ) {
            Row(
                Modifier.widthIn(max = _messageMaxWidth).padding(EdgeInsets.symmetric(horizontal = 2.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sender,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (outgoing) TextAlign.Right else TextAlign.Left,
                    style = UiTypography.label.copyWith(color = senderColor, fontSize = 11.0, fontWeight = FontWeight.W600),
                )
                if (message.sender.isBot) {
                    Spacer(Modifier.width(4.dp))
                    _GangBotBadge()
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.widthIn(max = _messageMaxWidth)) {
                _MessageBubble(
                    message = message,
                    outgoing = outgoing,
                    timestampNow = timestampNow,
                    showDetailedTimestamps = showDetailedTimestamps,
                    transfer = transfer,
                    fileDownloads = fileDownloads,
                    downloadActions = downloadActions,
                    voicePlaybackActions = voicePlaybackActions,
                    imagePreviewActions = imagePreviewActions,
                    messageActions = messageActions,
                    mentionHighlighted = mentionHighlighted,
                    mentionTargetsCurrentUser = mentionTargetsCurrentUser,
                    currentUser = currentUser,
                    currentUserMentionIdentity = currentUserMentionIdentity,
                    ownerUserId = ownerUserId,
                    mentionMembers = mentionMembers,
                    onResolveSenderProfile = onResolveSenderProfile,
                    onResolveRoomProfile = onResolveRoomProfile,
                    onEnterProfileRoom = onEnterProfileRoom,
                    profileActionBuilder = profileActionBuilder,
                    isUserInLive = isUserInLive,
                )
            }
        }
        if (outgoing) {
            Spacer(Modifier.width(10.dp))
            avatarHoverCard()
        }
    }
}

@Composable
private fun _MessageAvatarMentionMenu(
    user: UserSummary,
    onMentionUser: ((UserSummary) -> Unit)?,
    child: @Composable () -> Unit,
) {
    val onMention = onMentionUser
    if (onMention == null) {
        child()
        return
    }
    val context = rememberBuildContext()
    val label = _senderName(user)
    UiContextMenuTriggerRegion(
        onTriggered = { position ->
            unawaited {
                showUiContextMenu(
                    context,
                    position = position,
                    sections = listOf(UiContextMenuSection(listOf(UiContextMenuItem(label = "@$label", onPressed = { onMention(user) })))),
                )
            }
        },
        child = child,
    )
}

/**
 * Renders the exact contents used inside a normal chat message bubble without
 * adding the bubble background, border, padding, or message context menu.
 *
 * Message-history and other read-only surfaces use this widget so links,
 * mentions, stickers, voice messages, files, and image previews keep the same
 * behavior as the main chat instead of maintaining a second renderer.
 */
@Composable
fun ChatMessageContent(
    message: Message,
    outgoing: Boolean,
    fileDownloads: Map<String, FileTransferState>,
    downloadActions: ChatFileDownloadActions,
    voicePlaybackActions: ChatVoicePlaybackActions,
    imagePreviewActions: ChatImagePreviewActions,
    currentUser: CurrentUser,
    currentUserMentionIdentity: UserSummary,
    ownerUserId: String?,
    mentionMembers: List<RoomMember>,
    modifier: Modifier = Modifier,
    transfer: FileTransferState? = null,
    mentionHighlighted: Boolean = false,
    onResolveSenderProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
    isUserInLive: ((String) -> Boolean)? = null,
    onSelectionActiveChanged: ((Boolean) -> Unit)? = null,
    textSelectionResetListenable: com.gangchat.client.foundation.Listenable? = null,
    onOpenQuote: (suspend (context: BuildContext, quote: MessageQuote) -> Unit)? = null,
    timestampNow: DateTime? = null,
    showDetailedTimestamps: Boolean = false,
    onViewSharedPlaylist: (suspend (context: BuildContext, message: Message, playlist: SharedMusicPlaylist) -> Unit)? = null,
    onEditOwnedSharedPlaylist: (suspend (context: BuildContext, message: Message, playlist: SharedMusicPlaylist) -> Unit)? = null,
    sharedTrackPreviewController: MusicTrackPreviewController? = null,
    loadSharedTrackPlaylists: (suspend (roomId: String) -> List<MusicTrackPlaylistTarget>)? = null,
    onAddSharedTrackToPlaylist: (suspend (track: SharedMusicTrack, target: MusicTrackPlaylistTarget) -> Unit)? = null,
) {
    val context = rememberBuildContext()
    val contentKind = messageContentKind(message)
    val status = messageDeliveryStatusText(message)
    val quotes = message.effectiveQuotes
    Column(modifier, horizontalAlignment = Alignment.Start) {
        for ((index, quote) in quotes.withIndex()) {
            _MessageQuoteCard(
                quote = quote,
                timestampNow = timestampNow,
                showDetailedTimestamps = showDetailedTimestamps,
                imagePreviewActions = imagePreviewActions,
                onTap = if (onOpenQuote == null) null else ({ unawaited { onOpenQuote(context, quote) } }),
            )
            Spacer(Modifier.height(if (index == quotes.size - 1) 8.dp else 6.dp))
        }
        when (contentKind) {
            MessageContentKind.sticker ->
                _StickerBody(message = message, attachment = message.stickerAttachment!!, imagePreviewActions = imagePreviewActions)
            MessageContentKind.voice ->
                _VoiceBody(message = message, attachment = voiceMessageAttachment(message)!!, playbackActions = voicePlaybackActions)
            MessageContentKind.files ->
                _FileBody(
                    message = message,
                    outgoing = outgoing,
                    transfer = transfer,
                    fileDownloads = fileDownloads,
                    downloadActions = downloadActions,
                    imagePreviewActions = imagePreviewActions,
                )
            MessageContentKind.playlist ->
                _PlaylistShareBody(
                    message = message,
                    playlist = message.playlistAttachment!!.playlist!!,
                    onView = onViewSharedPlaylist,
                    onEditOwned = onEditOwnedSharedPlaylist,
                    currentUser = currentUser,
                    onResolveUserProfile = onResolveSenderProfile,
                    onResolveRoomProfile = onResolveRoomProfile,
                    onEnterCommonRoom = onEnterProfileRoom,
                    userProfileActionBuilder = profileActionBuilder,
                )
            MessageContentKind.musicTrack ->
                _MusicTrackShareBody(
                    roomId = message.roomId,
                    track = message.musicTrackAttachment!!.track!!,
                    previewController = sharedTrackPreviewController,
                    loadPlaylists = loadSharedTrackPlaylists,
                    onAddToPlaylist = onAddSharedTrackToPlaylist,
                )
            MessageContentKind.text ->
                _TextBody(
                    message = message,
                    profileCurrentUser = currentUser,
                    currentUser = currentUserMentionIdentity,
                    ownerUserId = ownerUserId,
                    mentionMembers = mentionMembers,
                    mentionHighlighted = mentionHighlighted,
                    onResolveSenderProfile = onResolveSenderProfile,
                    onResolveRoomProfile = onResolveRoomProfile,
                    onEnterProfileRoom = onEnterProfileRoom,
                    profileActionBuilder = profileActionBuilder,
                    isUserInLive = isUserInLive,
                    onSelectionActiveChanged = onSelectionActiveChanged ?: ::_ignoreMessageSelectionChange,
                    selectionResetListenable = textSelectionResetListenable,
                )
        }
        if (status != null) {
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.pending && !message.failed) {
                    _ChatProgressIndicator(size = 11.dp, strokeWidth = 1.6.dp, color = UiColors.textMuted)
                    Spacer(Modifier.width(6.dp))
                }
                Text(status, style = UiTypography.label.copyWith(color = if (message.failed) UiColors.danger else UiColors.textMuted))
            }
        }
    }
}

@Composable
private fun _MusicTrackShareBody(
    roomId: String,
    track: SharedMusicTrack,
    previewController: MusicTrackPreviewController?,
    loadPlaylists: (suspend (roomId: String) -> List<MusicTrackPlaylistTarget>)?,
    onAddToPlaylist: (suspend (track: SharedMusicTrack, target: MusicTrackPlaylistTarget) -> Unit)?,
) {
    val data =
        MusicTrackCardData(
            id = track.id,
            source = track.source,
            trackId = track.trackId,
            title = track.title,
            artists = track.artists,
            durationMs = track.durationMs,
        )
    val artists = track.artists.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("、")
    val panel: @Composable () -> Unit = {
        Row(
            Modifier
                .testTag(sharedMusicTrackMessageTag(track.id))
                .widthIn(min = 210.dp, max = 360.dp)
                .decoration(UiColors.surfaceRaised, RoundedCornerShape(8.dp), UiColors.border)
                .padding(EdgeInsets.all(13.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.MusicNote, color = UiColors.accent, size = 26.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                // PORT: Flutter's `TextOverflow.fade`; Compose clips instead.
                Text(
                    track.title,
                    maxLines = 4,
                    overflow = TextOverflow.Clip,
                    style = UiTypography.body.copyWith(color = UiColors.text, fontWeight = FontWeight.W700),
                )
                if (artists.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(artists, maxLines = 2, overflow = TextOverflow.Clip, style = UiTypography.label.copyWith(color = UiColors.textMuted))
                }
            }
        }
    }
    val preview = previewController
    if (preview == null) {
        panel()
        return
    }
    MusicTrackHoverCard(
        data = data,
        previewController = preview,
        loadPlaylistTargets = if (loadPlaylists == null) null else ({ loadPlaylists(roomId) }),
        playlistTargetsLoadKey = roomId,
        onAddToPlaylistTarget = if (onAddToPlaylist == null) null else ({ target -> onAddToPlaylist(track, target) }),
        child = panel,
    )
}

private fun _ignoreMessageSelectionChange(@Suppress("UNUSED_PARAMETER") active: Boolean) {}

@Composable
private fun _PlaylistShareBody(
    message: Message,
    playlist: SharedMusicPlaylist,
    onView: (suspend (context: BuildContext, message: Message, playlist: SharedMusicPlaylist) -> Unit)?,
    onEditOwned: (suspend (context: BuildContext, message: Message, playlist: SharedMusicPlaylist) -> Unit)?,
    currentUser: CurrentUser,
    onResolveUserProfile: UserProfileResolver?,
    onResolveRoomProfile: RoomProfileResolver?,
    onEnterCommonRoom: ((PublicRoom) -> Unit)?,
    userProfileActionBuilder: UserProfileActionBuilder?,
) {
    val context = rememberBuildContext()

    suspend fun open() {
        val edit = if (playlist.creator.id == currentUser.id) onEditOwned else null
        if (edit != null) return edit(context, message, playlist)
        val view = onView ?: return
        view(context, message, playlist)
    }

    val canEdit = playlist.creator.id == currentUser.id && onEditOwned != null
    val cardData =
        MusicPlaylistCardData(
            id = playlist.id,
            name = playlist.name,
            songCount = playlist.itemCount,
            createdAt = playlist.createdAt,
            creator = playlist.creator,
        )
    MusicPlaylistHoverCard(
        data = cardData,
        currentUser = currentUser,
        onResolveUserProfile = onResolveUserProfile,
        onResolveRoomProfile = onResolveRoomProfile,
        onEnterCommonRoom = onEnterCommonRoom,
        userProfileActionBuilder = userProfileActionBuilder,
        onPlayAll = null,
        showPrimaryActionWhenDisabled = false,
        onViewPlaylist = { open() },
        secondaryActionLabel = if (canEdit) "编辑歌单" else "查看歌单",
        secondaryActionIcon = if (canEdit) Icons.Filled.Edit else Icons.AutoMirrored.Filled.QueueMusic,
    ) {
        Row(
            Modifier
                .testTag(sharedMusicPlaylistMessageTag(playlist.id))
                .widthIn(min = 210.dp, max = 360.dp)
                .decoration(UiColors.surfaceRaised, RoundedCornerShape(8.dp), UiColors.border)
                .padding(EdgeInsets.all(13.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, color = UiColors.accent, size = 26.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = UiTypography.body.copyWith(color = UiColors.text, fontWeight = FontWeight.W700))
                Spacer(Modifier.height(3.dp))
                Text("${playlist.itemCount} 首歌曲", style = UiTypography.label.copyWith(color = UiColors.textMuted))
            }
            Spacer(Modifier.width(8.dp))
            _MaterialIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                modifier = Modifier.testTag(sharedMusicPlaylistOpenTag(playlist.id)),
                tooltip = if (canEdit) "编辑歌单" else "查看歌单",
                onPressed = if (onView == null && !canEdit) null else ({ unawaited { open() } }),
                padding = EdgeInsets.zero,
                minWidth = 36.dp,
                maxWidth = 36.dp,
                minHeight = 40.dp,
                maxHeight = 40.dp,
                iconSize = 22.dp,
                iconColor = UiColors.textMuted,
            )
        }
    }
}

@Composable
private fun _MessageBubble(
    message: Message,
    outgoing: Boolean,
    timestampNow: DateTime,
    showDetailedTimestamps: Boolean,
    transfer: FileTransferState?,
    fileDownloads: Map<String, FileTransferState>,
    downloadActions: ChatFileDownloadActions,
    voicePlaybackActions: ChatVoicePlaybackActions,
    imagePreviewActions: ChatImagePreviewActions,
    messageActions: ChatMessageActions,
    mentionHighlighted: Boolean,
    mentionTargetsCurrentUser: Boolean,
    currentUser: CurrentUser,
    currentUserMentionIdentity: UserSummary,
    ownerUserId: String?,
    mentionMembers: List<RoomMember>,
    onResolveSenderProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
    isUserInLive: ((String) -> Boolean)? = null,
) = StatefulWidget(
    _MessageBubbleWidget(
        message, outgoing, timestampNow, showDetailedTimestamps, transfer, fileDownloads, downloadActions, voicePlaybackActions,
        imagePreviewActions, messageActions, mentionHighlighted, mentionTargetsCurrentUser, currentUser, currentUserMentionIdentity,
        ownerUserId, mentionMembers, onResolveSenderProfile, onResolveRoomProfile, onEnterProfileRoom, profileActionBuilder, isUserInLive,
    ),
) { _MessageBubbleState() }

private class _MessageBubbleWidget(
    val message: Message,
    val outgoing: Boolean,
    val timestampNow: DateTime,
    val showDetailedTimestamps: Boolean,
    val transfer: FileTransferState?,
    val fileDownloads: Map<String, FileTransferState>,
    val downloadActions: ChatFileDownloadActions,
    val voicePlaybackActions: ChatVoicePlaybackActions,
    val imagePreviewActions: ChatImagePreviewActions,
    val messageActions: ChatMessageActions,
    val mentionHighlighted: Boolean,
    val mentionTargetsCurrentUser: Boolean,
    val currentUser: CurrentUser,
    val currentUserMentionIdentity: UserSummary,
    val ownerUserId: String?,
    val mentionMembers: List<RoomMember>,
    val onResolveSenderProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterProfileRoom: ((PublicRoom) -> Unit)?,
    val profileActionBuilder: UserProfileActionBuilder?,
    val isUserInLive: ((String) -> Boolean)?,
)

private class _MessageBubbleState : ViewState<_MessageBubbleWidget>() {
    private var _androidLongPressTracker: UiAndroidLongPressTracker? = null
    private val _textSelectionReset = ValueNotifier(0)
    private var _textSelectionActive = false
    private var _contextMenuActive = false

    // Android is the only platform here (Dart: `Theme.of(context).platform == android`).
    private val _androidLongPressEnabled = true
    private var _textSelectionActiveAtPointerDown = false
    private var _androidLongPressHandled = false
    private var _coordinates: LayoutCoordinates? = null

    override fun dispose() {
        _androidLongPressTracker?.cancel()
        _textSelectionReset.dispose()
    }

    @Composable
    override fun build() {
        val scope = rememberCoroutineScope()
        val tracker = _androidLongPressTracker ?: UiAndroidLongPressTracker(::_handleAndroidBubbleLongPress, scope).also { _androidLongPressTracker = it }
        val density = LocalDensity.current
        val contentKind = messageContentKind(widget.message)
        val contextMenuActive = _contextMenuActive
        val backgroundColor = if (widget.outgoing) _outgoingBubble else _incomingBubble
        val mentionBackgroundColor = if (widget.outgoing) UiColors.amber.copy(alpha = 0.18f) else UiColors.amber.copy(alpha = 0.13f)
        val contextMentionBackgroundColor = if (widget.outgoing) UiColors.amber.copy(alpha = 0.28f) else UiColors.amber.copy(alpha = 0.22f)
        val targetsCurrentUser = widget.mentionTargetsCurrentUser
        val mentionHighlightColor = _alphaBlend(mentionBackgroundColor, backgroundColor)
        val contextMentionHighlightColor = _alphaBlend(contextMentionBackgroundColor, backgroundColor)
        val mentionEmphasized = targetsCurrentUser && (contextMenuActive || widget.mentionHighlighted)
        val highlightColor =
            when {
                contextMenuActive && !targetsCurrentUser -> UiColors.selected
                targetsCurrentUser -> if (mentionEmphasized) contextMentionHighlightColor else mentionHighlightColor
                widget.mentionHighlighted -> UiColors.selected.copy(alpha = 0.86f)
                else -> backgroundColor
            }
        val borderColor =
            when {
                contextMenuActive && !targetsCurrentUser -> UiColors.selectedBorder
                targetsCurrentUser -> if (mentionEmphasized) UiColors.amber.copy(alpha = 0.72f) else UiColors.amber.copy(alpha = 0.58f)
                widget.mentionHighlighted -> UiColors.selectedBorder
                widget.outgoing -> UiColors.accentBorder
                else -> UiColors.border
            }
        val animation =
            if (widget.mentionTargetsCurrentUser || widget.mentionHighlighted) snap<Color>() else tween(90, easing = Curves.easeOutCubic)
        val animatedColor by animateColorAsState(highlightColor, animation, label = "messageBubbleColor")
        val animatedBorder by animateColorAsState(borderColor, animation, label = "messageBubbleBorder")
        val messageId = widget.message.id

        fun window(position: androidx.compose.ui.geometry.Offset): DpOffset {
            val coords = _coordinates
            val windowPosition = if (coords != null && coords.isAttached) coords.localToWindow(position) else position
            return with(density) { DpOffset(windowPosition.x.toDp(), windowPosition.y.toDp()) }
        }

        Box(
            Modifier
                .testTag(messageBubbleSurfaceTag(messageId))
                .onGloballyPositioned { _coordinates = it }
                // Dart's Listener: raw pointer events, observed before any
                // descendant (the text field, a sticker) can consume them.
                .pointerInput(this) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Press ->
                                    for (change in event.changes) {
                                        if (!change.pressed || change.previousPressed) continue
                                        if (change.type == PointerType.Mouse && event.buttons.isSecondaryPressed) {
                                            _showBubbleContextMenuAt(window(change.position), contentKind, textSelectionActive = _textSelectionActive)
                                        }
                                        _textSelectionActiveAtPointerDown = _textSelectionActive
                                        _androidLongPressHandled = false
                                        tracker.handlePointerDown(change.id, window(change.position), change.type, enabled = _androidLongPressEnabled)
                                    }
                                PointerEventType.Move ->
                                    for (change in event.changes) tracker.handlePointerMove(change.id, window(change.position))
                                PointerEventType.Release ->
                                    for (change in event.changes) {
                                        if (change.pressed || !change.previousPressed) continue
                                        if (change.isConsumed) tracker.handlePointerCancel(change.id) else tracker.handlePointerUp(change.id)
                                        _androidLongPressHandled = false
                                    }
                                else -> Unit
                            }
                        }
                    }
                }
                .decoration(animatedColor, RoundedCornerShape(UiRadii.lg), animatedBorder)
                // AnimatedContainer insets its child by the 1 px border.
                .padding(_messageBubblePadding + EdgeInsets.all(1.dp)),
        ) {
            Box(
                Modifier
                    .testTag(messageBubbleContentTag(messageId))
                    .decoration(highlightColor, RoundedCornerShape(maxOf(0.dp, UiRadii.lg - 5.dp))),
            ) {
                ChatMessageContent(
                    message = widget.message,
                    outgoing = widget.outgoing,
                    timestampNow = widget.timestampNow,
                    showDetailedTimestamps = widget.showDetailedTimestamps,
                    transfer = widget.transfer,
                    fileDownloads = widget.fileDownloads,
                    downloadActions = widget.downloadActions,
                    voicePlaybackActions = widget.voicePlaybackActions,
                    imagePreviewActions = widget.imagePreviewActions,
                    currentUser = widget.currentUser,
                    currentUserMentionIdentity = widget.currentUserMentionIdentity,
                    ownerUserId = widget.ownerUserId,
                    mentionMembers = widget.mentionMembers,
                    mentionHighlighted = widget.mentionHighlighted,
                    onResolveSenderProfile = widget.onResolveSenderProfile,
                    onResolveRoomProfile = widget.onResolveRoomProfile,
                    onEnterProfileRoom = widget.onEnterProfileRoom,
                    profileActionBuilder = widget.profileActionBuilder,
                    isUserInLive = widget.isUserInLive,
                    onSelectionActiveChanged = ::_handleTextSelectionActiveChanged,
                    textSelectionResetListenable = _textSelectionReset,
                    onOpenQuote = widget.messageActions.onOpenQuote,
                    onViewSharedPlaylist = widget.messageActions.onViewSharedPlaylist,
                    onEditOwnedSharedPlaylist = widget.messageActions.onEditOwnedSharedPlaylist,
                    sharedTrackPreviewController = widget.messageActions.sharedTrackPreviewController,
                    loadSharedTrackPlaylists = widget.messageActions.loadSharedTrackPlaylists,
                    onAddSharedTrackToPlaylist = widget.messageActions.onAddSharedTrackToPlaylist,
                )
            }
        }
    }

    private fun _handleAndroidBubbleLongPress(position: DpOffset) {
        if (!_androidLongPressEnabled || _androidLongPressHandled) return
        _androidLongPressHandled = true
        val contentKind = messageContentKind(widget.message)
        if (contentKind == MessageContentKind.text && _textSelectionActiveAtPointerDown) return
        if (contentKind == MessageContentKind.text) {
            _textSelectionReset.value = _textSelectionReset.value + 1
        }
        removeAnyTextContextMenu()
        _showBubbleContextMenuAt(position, contentKind, textSelectionActive = _textSelectionActiveAtPointerDown)
    }

    private fun _showBubbleContextMenuAt(position: DpOffset, contentKind: MessageContentKind, textSelectionActive: Boolean) {
        if (contentKind == MessageContentKind.text && textSelectionActive) return
        if (contentKind == MessageContentKind.sticker) {
            _showContextMenuWithHighlight {
                _showStickerContextMenu(
                    context = context,
                    position = position,
                    message = widget.message,
                    attachment = widget.message.stickerAttachment!!,
                    imagePreviewActions = widget.imagePreviewActions,
                    messageActions = widget.messageActions,
                )
            }
            return
        }
        _showContextMenuWithHighlight {
            _showChatMessageContextMenu(context = context, position = position, message = widget.message, actions = widget.messageActions)
        }
    }

    private fun _handleTextSelectionActiveChanged(active: Boolean) {
        if (_textSelectionActive == active) return
        setState { _textSelectionActive = active }
    }

    private fun _showContextMenuWithHighlight(showMenu: suspend () -> Unit) {
        _setContextMenuActive(true)
        unawaited {
            try {
                showMenu()
            } finally {
                if (mounted) _setContextMenuActive(false)
            }
        }
    }

    private fun _setContextMenuActive(active: Boolean) {
        if (!mounted || _contextMenuActive == active) return
        setState { _contextMenuActive = active }
    }
}

/** Flutter's `Color.alphaBlend(foreground, background)`. */
internal fun _alphaBlend(foreground: Color, background: Color): Color {
    val alpha = foreground.alpha
    if (alpha == 0f) return background
    val invAlpha = 1f - alpha
    val backAlpha = background.alpha
    if (backAlpha == 1f) {
        return Color(
            red = alpha * foreground.red + invAlpha * background.red,
            green = alpha * foreground.green + invAlpha * background.green,
            blue = alpha * foreground.blue + invAlpha * background.blue,
            alpha = 1f,
        )
    }
    val outAlpha = alpha + backAlpha * invAlpha
    return Color(
        red = (foreground.red * alpha + background.red * backAlpha * invAlpha) / outAlpha,
        green = (foreground.green * alpha + background.green * backAlpha * invAlpha) / outAlpha,
        blue = (foreground.blue * alpha + background.blue * backAlpha * invAlpha) / outAlpha,
        alpha = outAlpha,
    )
}

@Composable
internal fun _MessageQuoteCard(
    quote: MessageQuote,
    onTap: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    compact: Boolean = false,
    imagePreviewActions: ChatImagePreviewActions? = null,
    timestampNow: DateTime? = null,
    showDetailedTimestamps: Boolean = false,
) {
    val body = if (quote.body.trim().isEmpty()) "[消息]" else quote.body.trim()
    val hasThumbnail = quote.previewAttachment?.asset != null
    val timestamp =
        if (showDetailedTimestamps) formatDetailedChatTimestamp(quote.createdAt) else formatChatTimestamp(quote.createdAt, now = timestampNow)
    val senderDisplayName = quote.senderDisplayName.trim()
    val header = if (senderDisplayName.isEmpty()) timestamp else "$senderDisplayName  $timestamp"
    val tapModifier =
        if (onTap == null) {
            Modifier
        } else {
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
        }
    // Container(padding:, decoration: border): the padding plus the 1 px border.
    val vertical = if (compact) 8.dp else 9.dp
    Box(
        tapModifier
            .testTag(messageQuoteTag(quote.messageId))
            .fillMaxWidth()
            .decoration(UiColors.surfaceLow, RoundedCornerShape(UiRadii.md), UiColors.border)
            .padding(EdgeInsets.fromLTRB(11.dp, vertical, 7.dp, vertical)),
    ) {
        Row(Modifier.fillMaxWidth().padding(EdgeInsets.only(left = 12.dp)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    header,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTypography.label.copyWith(color = UiColors.textSecondary, fontWeight = FontWeight.W600),
                )
                Spacer(Modifier.height(3.dp))
                if (hasThumbnail) {
                    _MessageQuoteThumbnail(quote = quote, actions = imagePreviewActions, compact = compact, fallbackText = body)
                } else {
                    Text(
                        body,
                        maxLines = if (compact) 1 else 4,
                        overflow = TextOverflow.Ellipsis,
                        style = UiTypography.body.copyWith(color = UiColors.text, fontSize = 12.0, height = 1.35),
                    )
                }
            }
            if (onClose != null) {
                Spacer(Modifier.width(6.dp))
                _MaterialIconButton(
                    icon = Icons.Filled.Close,
                    modifier = Modifier.testTag(composerQuoteCloseTag(quote.messageId)),
                    onPressed = onClose,
                    iconSize = 16.dp,
                    iconColor = UiColors.textMuted,
                    tooltip = "取消引用",
                    padding = EdgeInsets.zero,
                    minWidth = 28.dp,
                    minHeight = 28.dp,
                )
            }
        }
        // Positioned(left: 0, top: 0, bottom: 0, width: 3): the full-height indicator.
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .testTag(messageQuoteIndicatorTag(quote.messageId))
                    .width(3.dp)
                    .fillMaxHeight()
                    .decoration(UiColors.textMuted, RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun _MessageQuoteThumbnail(
    quote: MessageQuote,
    actions: ChatImagePreviewActions?,
    compact: Boolean,
    fallbackText: String,
) {
    val context = rememberBuildContext()
    val attachment = quote.previewAttachment
    val asset = attachment?.asset
    if (attachment == null || asset == null) return
    val config = LocalAppConfig.current
    val thumbnailUrl = config.resolveAssetUrl(asset.thumbnailUrl ?: asset.url)
    val fullUrl = config.resolveAssetUrl(asset.url)
    if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
        Text(
            fallbackText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.body.copyWith(color = UiColors.text, fontSize = 12.0),
        )
        return
    }
    val isSticker = attachment.type == "sticker"
    val title = if (isSticker) stickerPreviewFilename(attachment) else fileAttachmentTitle(attachment)
    val dimension = if (compact) 44.dp else 64.dp
    val image: @Composable () -> Unit = {
        Box(Modifier.clip(RoundedCornerShape(UiRadii.sm))) {
            CachedAssetImage(
                url = thumbnailUrl,
                filename = asset.filename ?: title,
                mimeType = asset.mimeType,
                expectedBytes = asset.sizeBytes,
                cache = actions?.mediaCache,
                width = dimension,
                height = dimension,
                fit = if (isSticker) BoxFit.contain else BoxFit.cover,
                errorBuilder = { _, _ ->
                    Box(Modifier.width(if (compact) 120.dp else 180.dp)) {
                        Text(
                            fallbackText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = UiTypography.body.copyWith(color = UiColors.text, fontSize = 12.0),
                        )
                    }
                },
            )
        }
    }
    val previewActions = actions
    val previewUrl = if (fullUrl == null || fullUrl.isEmpty()) thumbnailUrl else fullUrl
    if (previewActions == null) {
        image()
        return
    }
    UiTooltip(message = "查看预览") {
        Box(
            Modifier
                .testTag(messageQuoteThumbnailTag(quote.messageId))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        unawaited {
                            showChatImagePreview(context, imageUrl = previewUrl, suggestedName = title, actions = previewActions)
                        }
                    },
                ),
        ) {
            image()
        }
    }
}

@Composable
private fun _StickerBody(message: Message, attachment: MessageAttachment, imagePreviewActions: ChatImagePreviewActions) {
    val context = rememberBuildContext()
    val asset = attachment.asset
    val config = LocalAppConfig.current
    val imageUrl = if (asset == null) null else config.resolveAssetUrl(asset.thumbnailUrl ?: asset.url)
    // The preview shows the full-resolution sticker, not the thumbnail.
    val previewUrl = if (asset == null) null else config.resolveAssetUrl(asset.url)
    val name = stickerAttachmentTitle(attachment)
    val image: @Composable () -> Unit =
        if (imageUrl != null && imageUrl.isNotEmpty()) {
            {
                Box(Modifier.clip(RoundedCornerShape(UiRadii.md))) {
                    CachedAssetImage(
                        url = imageUrl,
                        modifier = Modifier.testTag(chatStickerImageTag),
                        filename = asset?.filename ?: name,
                        mimeType = asset?.mimeType,
                        expectedBytes = asset?.sizeBytes,
                        cache = imagePreviewActions.mediaCache,
                        width = 132.dp,
                        height = 132.dp,
                        fit = BoxFit.contain,
                        errorBuilder = { _, _ -> _StickerFallback(name = name) },
                    )
                }
            }
        } else {
            { _StickerFallback(name = name) }
        }

    val tappablePreviewUrl =
        when {
            previewUrl != null && previewUrl.isNotEmpty() -> previewUrl
            imageUrl != null && imageUrl.isNotEmpty() -> imageUrl
            else -> null
        }

    UiTooltip(message = name, waitDuration = 350.milliseconds) {
        if (tappablePreviewUrl == null) {
            image()
        } else {
            Box(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        unawaited {
                            showChatImagePreview(
                                context,
                                imageUrl = tappablePreviewUrl,
                                suggestedName = stickerPreviewFilename(attachment),
                                actions = imagePreviewActions,
                                stickerSource = if (attachment.stickerId != null) ChatImagePreviewStickerSource(message, attachment) else null,
                            )
                        }
                    },
                ),
            ) {
                image()
            }
        }
    }
}

private suspend fun _showStickerContextMenu(
    context: BuildContext,
    position: DpOffset,
    message: Message,
    attachment: MessageAttachment,
    imagePreviewActions: ChatImagePreviewActions,
    messageActions: ChatMessageActions,
) {
    val stickerId = attachment.stickerId
    val stickerItems =
        buildList {
            val onSaveSticker = imagePreviewActions.onSaveSticker
            if (onSaveSticker != null) {
                add(
                    UiContextMenuItem(
                        label = "添加到我的表情包",
                        onPressed = {
                            unawaited { _runStickerContextAction(context, message, attachment, onSaveSticker, successMessage = "已添加到我的表情包") }
                        },
                    ),
                )
            }
            val onSaveRoomSticker = imagePreviewActions.onSaveRoomSticker
            if (onSaveRoomSticker != null) {
                add(
                    UiContextMenuItem(
                        label = "添加到房间表情包",
                        onPressed = {
                            unawaited { _runStickerContextAction(context, message, attachment, onSaveRoomSticker, successMessage = "已添加到房间表情包") }
                        },
                    ),
                )
            }
        }
    val sections =
        buildList {
            addAll(
                _messageContextMenuSections(
                    context = context,
                    message = message,
                    actions = messageActions,
                    includeCopy = true,
                    includeQuote = true,
                    includeDelete = false,
                    includeRecall = false,
                ),
            )
            if (stickerId != null && stickerId.isNotEmpty() && stickerItems.isNotEmpty()) add(UiContextMenuSection(stickerItems))
            addAll(
                _messageContextMenuSections(
                    context = context,
                    message = message,
                    actions = messageActions,
                    includeCopy = false,
                    includeQuote = false,
                    includeDelete = false,
                    includeRecall = true,
                ),
            )
            addAll(
                _messageContextMenuSections(
                    context = context,
                    message = message,
                    actions = messageActions,
                    includeCopy = false,
                    includeQuote = false,
                    includeDelete = true,
                    includeRecall = false,
                ),
            )
        }
    showUiContextMenu(context, position = position, sections = sections)
}

private suspend fun _runStickerContextAction(
    context: BuildContext,
    message: Message,
    attachment: MessageAttachment,
    action: suspend (message: Message, attachment: MessageAttachment) -> Unit,
    successMessage: String,
) {
    try {
        action(message, attachment)
        if (!context.mounted) return
        _showMessageContextNotice(context, successMessage, tone = FloatingNoticeTone.success)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!context.mounted) return
        _showMessageContextNotice(context, userFacingErrorMessage(error, fallback = "表情操作失败"), tone = FloatingNoticeTone.error)
    }
}

internal fun _showMessageContextNotice(context: BuildContext, message: String, tone: FloatingNoticeTone = FloatingNoticeTone.info) {
    showFloatingNotice(context, message, tone = tone)
}

@Composable
private fun _StickerFallback(name: String) {
    Box(
        Modifier
            .size(132.dp, 96.dp)
            .decoration(UiColors.surfaceLow, RoundedCornerShape(UiRadii.md), UiColors.border),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, style = UiTypography.label)
    }
}

private val _voiceAccent = Color.White

@Composable
private fun _VoiceBody(message: Message, attachment: MessageAttachment, playbackActions: ChatVoicePlaybackActions) {
    val resolvedUrl = LocalAppConfig.current.resolveAssetUrl(attachment.asset?.url)
    val playbackKey = message.clientMessageId
    val playing = playbackActions.isPlaying(playbackKey)
    val canPlay = resolvedUrl != null && resolvedUrl.isNotEmpty() && playbackActions.onToggle != null
    val duration = voiceAttachmentDuration(attachment)
    val durationText = formatVoiceBubbleDuration(duration)
    val waveformWidth = voiceWaveformWidth(duration)
    val playbackProgress = playbackActions.progressFor(playbackKey, fallbackDuration = duration)

    fun togglePlayback() {
        val url = resolvedUrl
        val onToggle = playbackActions.onToggle
        if (url == null || url.isEmpty() || onToggle == null) return
        onToggle(playbackKey, url)
    }

    Box(
        Modifier
            .semantics {
                if (canPlay) role = Role.Button
                contentDescription = if (playing) "停止播放录音" else "播放录音"
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = canPlay,
                onClick = ::togglePlayback,
            ),
    ) {
        Row(
            Modifier.testTag(voiceBodyTag).widthIn(min = 150.dp, max = 304.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    color = if (canPlay) _voiceAccent else UiColors.textMuted,
                    size = 22.dp,
                )
            }
            Spacer(Modifier.width(8.dp))
            _VoiceWaveform(width = waveformWidth.dp, progress = playbackProgress)
            if (durationText.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    durationText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTypography.body.copyWith(color = _voiceAccent, fontWeight = FontWeight.W500),
                )
            }
        }
    }
}

private val _voiceWaveformHeights = listOf(8, 16, 22, 13, 18, 10, 24, 15, 9, 19, 12, 21, 7)

@Composable
private fun _VoiceWaveform(width: Dp, progress: Double) {
    Box(Modifier.testTag(voiceWaveformTag).size(width, 28.dp)) {
        _VoiceWaveformBars(color = _voiceAccent.copy(alpha = 0.28f), width = width)
        // ClipRect with _VoiceProgressClipper: the played fraction in full colour.
        val clamped = progress.coerceIn(0.0, 1.0).toFloat()
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent { clipRect(right = size.width * clamped) { this@drawWithContent.drawContent() } },
        ) {
            _VoiceWaveformBars(color = _voiceAccent, width = width)
        }
    }
}

@Composable
private fun _VoiceWaveformBars(color: Color, width: Dp) {
    Row(
        Modifier.size(width, 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (height in _waveformHeightsForWidth(width)) {
            Box(Modifier.size(3.dp, height.dp).decoration(color, RoundedCornerShape(999.dp)))
        }
    }
}

private fun _waveformHeightsForWidth(width: Dp): List<Int> {
    val count = (width.value / 6).roundToInt().coerceIn(12, 28)
    return List(count) { index -> _voiceWaveformHeights[index % _voiceWaveformHeights.size] }
}

@Composable
private fun _FileBody(
    message: Message,
    outgoing: Boolean,
    transfer: FileTransferState?,
    fileDownloads: Map<String, FileTransferState>,
    downloadActions: ChatFileDownloadActions,
    imagePreviewActions: ChatImagePreviewActions,
) {
    val attachments = message.fileAttachments
    val showBody = shouldShowFileAttachmentBody(body = message.body, attachments = attachments)
    Column(horizontalAlignment = Alignment.Start) {
        for ((index, attachment) in attachments.withIndex()) {
            if (index > 0) Spacer(Modifier.height(8.dp))
            _FileAttachmentTile(
                message = message,
                outgoing = outgoing,
                attachment = attachment,
                index = index,
                // The outgoing upload transfer only ever applies to the first
                // attachment; the slot helper resolves upload vs. download for us.
                slot =
                    fileAttachmentTransferSlot(
                        message = message,
                        attachment = attachment,
                        index = index,
                        uploadTransfer = transfer,
                        downloads = fileDownloads,
                    ),
                downloadActions = downloadActions,
                imagePreviewActions = imagePreviewActions,
            )
        }
        if (showBody) {
            Spacer(Modifier.height(8.dp))
            Text(message.body, style = UiTypography.body)
        }
    }
}

@Composable
private fun _FileAttachmentTile(
    message: Message,
    outgoing: Boolean,
    attachment: MessageAttachment,
    index: Int,
    slot: com.gangchat.client.app.FileAttachmentTransferSlot,
    downloadActions: ChatFileDownloadActions,
    imagePreviewActions: ChatImagePreviewActions,
) {
    val context = rememberBuildContext()
    val asset = attachment.asset
    val transfer = slot.transfer
    val title = fileAttachmentTitle(attachment)
    val meta = if (transfer == null) fileAttachmentMeta(asset) else fileTransferProgressState(transfer).label
    val progress = if (transfer == null) null else fileTransferProgressState(transfer)
    val previewPath = fileAttachmentPreviewPath(asset)

    // Resolve the asset URL here, at the widget layer, so the download
    // orchestration stays free of AppConfig.
    val config = LocalAppConfig.current
    val resolvedUrl = config.resolveAssetUrl(asset?.url)
    val previewUrl = config.resolveAssetUrl(previewPath)
    val previewSize = fileAttachmentPreviewSize(asset)
    val interaction = fileAttachmentInteractionState(title = title, url = resolvedUrl, transfer = transfer)
    val trailing = fileAttachmentTrailingState(transfer = transfer, canDownload = interaction.canDownload)

    fun startDownload() {
        val url = resolvedUrl ?: return
        downloadActions.onDownload(message, attachment, index, url)
    }

    val tile: @Composable (Modifier) -> Unit = { modifier ->
        // No fill; the outline matches the surrounding bubble so it reads on
        // both the green outgoing bubble and the darker incoming one.
        // (DecoratedBox paints behind the padding without insetting it.)
        Column(
            modifier
                .decoration(Color.Transparent, RoundedCornerShape(UiRadii.md), if (outgoing) UiColors.accentBorder else UiColors.borderStrong)
                .padding(EdgeInsets.all(10.dp)),
            horizontalAlignment = Alignment.Start,
        ) {
            if (previewUrl != null && previewUrl.isNotEmpty()) {
                _FileImagePreview(
                    url = previewUrl,
                    title = title,
                    width = previewSize.width.dp,
                    height = previewSize.height.dp,
                    mediaCache = imagePreviewActions.mediaCache,
                    onTap = {
                        val fullUrl = if (resolvedUrl != null && resolvedUrl.isNotEmpty()) resolvedUrl else previewUrl
                        unawaited { showChatImagePreview(context, imageUrl = fullUrl, suggestedName = title, actions = imagePreviewActions) }
                    },
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(fileIconForMime(asset?.mimeType), color = UiColors.textSecondary, size = 22.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.Start) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = UiTypography.body.copyWith(fontWeight = FontWeight.W600))
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            meta,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = UiTypography.label.copyWith(color = if (progress?.failed == true) UiColors.danger else UiColors.textMuted),
                        )
                    }
                    if (progress != null) {
                        Spacer(Modifier.height(8.dp))
                        _LinearProgress(
                            value = progress.value,
                            color = if (progress.failed) UiColors.danger else UiColors.accent,
                            backgroundColor = UiColors.border,
                            modifier = Modifier.width(180.dp).height(3.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                _FileAttachmentTrailing(state = trailing, downloadKey = slot.downloadKey, actions = downloadActions)
            }
        }
    }

    // Tapping an idle, downloadable tile starts the download. While a transfer
    // is in flight the trailing controls own the interaction instead.
    if (trailing.kind != FileAttachmentTrailingKind.download) {
        tile(Modifier)
        return
    }
    UiTooltip(message = title) {
        tile(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::startDownload))
    }
}

/** Material's (2023) `LinearProgressIndicator(value:, minHeight:, color:, backgroundColor:)`; a null [value] runs indeterminate. */
@Composable
private fun _LinearProgress(value: Double?, color: Color, backgroundColor: Color, modifier: Modifier) {
    if (value == null) {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = backgroundColor,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
    } else {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { value.toFloat() },
            modifier = modifier,
            color = color,
            trackColor = backgroundColor,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun _FileImagePreview(
    url: String,
    title: String,
    width: Dp,
    height: Dp,
    mediaCache: MediaCacheController? = null,
    onTap: (() -> Unit)? = null,
) {
    val preview: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier
                .clip(RoundedCornerShape(UiRadii.sm))
                .decoration(UiColors.surfaceLow, androidx.compose.ui.graphics.RectangleShape, UiColors.border)
                .size(width, height),
        ) {
            CachedAssetImage(
                url = url,
                modifier = Modifier.fillMaxSize().testTag(chatFilePreviewImageTag),
                filename = title,
                cache = mediaCache,
                fit = BoxFit.contain,
                errorBuilder = { _, _ ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            title,
                            modifier = Modifier.padding(EdgeInsets.all(12.dp)),
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            style = UiTypography.label.copyWith(color = UiColors.textMuted),
                        )
                    }
                },
            )
        }
    }
    if (onTap == null) {
        preview(Modifier)
        return
    }
    preview(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap))
}

/**
 * Trailing controls for a file tile. Renders purely from
 * [FileAttachmentTrailingState]; the decision of which controls to show lives
 * in `FileDisplay.kt`, so this widget only paints them.
 */
@Composable
private fun _FileAttachmentTrailing(state: FileAttachmentTrailingState, downloadKey: String, actions: ChatFileDownloadActions) {
    when (state.kind) {
        // Idle/downloadable tiles carry no trailing control: tapping the tile
        // itself starts the download.
        FileAttachmentTrailingKind.download -> Unit
        FileAttachmentTrailingKind.activeTransfer ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ButtonIcon(
                    icon = { Icon(if (state.pauseResumeIsResume) Icons.Rounded.PlayArrow else Icons.Rounded.Pause) },
                    tooltip = state.pauseResumeTooltip ?: "",
                    size = 32.dp,
                    onPressed = { if (state.pauseResumeIsResume) actions.onResume(downloadKey) else actions.onPause(downloadKey) },
                )
                Spacer(Modifier.width(4.dp))
                ButtonIcon(
                    icon = { Icon(Icons.Rounded.Close) },
                    tooltip = state.cancelTooltip ?: "",
                    size = 32.dp,
                    onPressed = { actions.onCancel(downloadKey) },
                )
            }
        FileAttachmentTrailingKind.failed ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.showDismiss) {
                    ButtonIcon(icon = { Icon(Icons.Rounded.Close) }, tooltip = "清除", size = 32.dp, onPressed = { actions.onDismiss(downloadKey) })
                }
            }
        FileAttachmentTrailingKind.sending -> _ChatProgressIndicator(size = 18.dp, strokeWidth = 2.dp, color = UiColors.accent)
        FileAttachmentTrailingKind.placeholder -> Unit
    }
}

/**
 * Flutter's Material 3 `IconButton` as the Android theme lays it out: the
 * visual button (the icon plus [padding], within the min/max size) centred in
 * a 48 dp tap target (`MaterialTapTargetSize.padded`). The kit theme turns
 * off splashes, so there is no ripple.
 */
@Composable
internal fun _MaterialIconButton(
    icon: ImageVector,
    onPressed: (() -> Unit)?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    iconColor: Color? = null,
    tooltip: String? = null,
    padding: EdgeInsets = EdgeInsets.all(8.dp),
    minWidth: Dp = 40.dp,
    minHeight: Dp = 40.dp,
    maxWidth: Dp = Dp.Infinity,
    maxHeight: Dp = Dp.Infinity,
) {
    val enabled = onPressed != null
    val visualWidth = (iconSize + padding.horizontal).coerceIn(minWidth, maxWidth)
    val visualHeight = (iconSize + padding.vertical).coerceIn(minHeight, maxHeight)
    // Disabled M3 icon buttons draw in onSurface at 38 %.
    val color = if (enabled) iconColor ?: UiColors.textSecondary else UiColors.text.copy(alpha = 0.38f)
    val button: @Composable () -> Unit = {
        Box(
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { role = Role.Button }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = { onPressed?.invoke() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(visualWidth, visualHeight), contentAlignment = Alignment.Center) {
                Icon(icon, size = iconSize, color = color)
            }
        }
    }
    if (tooltip != null && tooltip.isNotEmpty()) UiTooltip(message = tooltip) { button() } else button()
}

/** Test-only entry point for a single private message bubble (Dart's `MessageBubbleForTest`). */
@Composable
fun MessageBubbleForTest(
    message: Message,
    downloadActions: ChatFileDownloadActions,
    imagePreviewActions: ChatImagePreviewActions? = null,
    outgoing: Boolean = false,
    transfer: FileTransferState? = null,
    fileDownloads: Map<String, FileTransferState> = emptyMap(),
    voicePlaybackActions: ChatVoicePlaybackActions = ChatVoicePlaybackActions.disabled(),
    messageActions: ChatMessageActions = ChatMessageActions.disabled(),
    timestampNow: DateTime? = null,
    showDetailedTimestamps: Boolean = false,
    currentUser: CurrentUser = _messageBubbleTestCurrentUser,
) {
    Box(Modifier.widthIn(max = _messageMaxWidth)) {
        _MessageBubble(
            message = message,
            outgoing = outgoing,
            timestampNow = timestampNow ?: DateTime.now(),
            showDetailedTimestamps = showDetailedTimestamps,
            transfer = transfer,
            fileDownloads = fileDownloads,
            downloadActions = downloadActions,
            voicePlaybackActions = voicePlaybackActions,
            imagePreviewActions = imagePreviewActions ?: ChatImagePreviewActions.disabled(),
            messageActions = messageActions,
            mentionHighlighted = false,
            mentionTargetsCurrentUser = false,
            currentUser = currentUser,
            currentUserMentionIdentity = currentUser.toSummary(),
            ownerUserId = null,
            mentionMembers = emptyList(),
        )
    }
}

/** Dart's `_avatarHoverCardTestCurrentUser`, the viewer [MessageBubbleForTest] renders for. */
private val _messageBubbleTestCurrentUser =
    CurrentUser(
        id = "avatar-hover-card-test-user",
        uid = "",
        username = "viewer",
        displayName = "Viewer",
        bio = "",
        gender = "secret",
        email = null,
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "green-2",
        isSuperuser = false,
        createdAt = null,
    )

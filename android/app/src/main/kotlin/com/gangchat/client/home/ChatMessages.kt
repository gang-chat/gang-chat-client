package com.gangchat.client.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.FileTransferState
import com.gangchat.client.app.MusicTrackPreviewController
import com.gangchat.client.app.formatChatTimestamp
import com.gangchat.client.app.formatDetailedChatTimestamp
import com.gangchat.client.app.liveParticipantByUserId
import com.gangchat.client.app.messageMentionRanges
import com.gangchat.client.app.messageMentionsUser
import com.gangchat.client.app.shouldShowChatTimestamp
import com.gangchat.client.app.systemMessageEvent
import com.gangchat.client.app.voicePlaybackProgress
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.SharedMusicPlaylist
import com.gangchat.client.protocol.SharedMusicTrack
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.FloatingNotice
import com.gangchat.client.ui.FloatingNoticeEmitter
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.RenderBoxHandle
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.renderBox
import com.gangchat.client.widgets.startTimer
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Port of lib/src/home/chat_messages.dart (a part of chat_pane.dart): the
// action bundles and the room's message list (_MessageStage). The rest of the
// part lives in ChatMessagesSystem.kt (system and removed rows, the message
// context menu), ChatMessagesContent.kt (rows, bubbles and their bodies) and
// ChatMessagesText.kt (the selectable text body).

/**
 * How close (in logical pixels) to the newest message counts as "at bottom"
 * for saved scroll anchors and the jump-to-latest affordance.
 */
private const val _autoScrollFollowThreshold = 120f
private val _chatMessageListTopPadding = 18.dp

/**
 * Dart's `ScrollCacheExtent.pixels(1200)`: rows within this distance of the
 * viewport were built by Flutter's list, so they can be revealed and measured.
 * The lazy column only composes what is on screen; rows in this band are
 * located from their measured (or average) heights instead.
 */
private const val _messageListCacheExtent = 1200f
private val _messageListSeparator = 12.dp

/** Dart's `_messageBubblePadding`. */
internal val _messageBubblePadding: EdgeInsets = EdgeInsets.symmetric(horizontal = 12.dp, vertical = 10.dp)

const val chatMessageListTag = "chat-message-list"
const val chatJumpToLatestTag = "chat-jump-to-latest"
const val chatJumpToFirstNewTag = "chat-jump-to-first-new"

/**
 * A remembered scroll position for one room's message list. The list widget is
 * torn down and rebuilt whenever the pane swaps (e.g. opening the live
 * channel and coming back), so we stash the last position here keyed by room
 * id and restore it on the next mount instead of snapping to the bottom.
 *
 * PORT: Dart stored the reversed list's pixel offset (the distance from the
 * newest message). The lazy column stores its first visible item (counted
 * from the newest message) and the offset into it, which restores the same
 * distance from the bottom.
 */
private class _ScrollAnchor(val firstVisibleItemIndex: Int, val firstVisibleItemScrollOffset: Int, val atBottom: Boolean)

/**
 * Callbacks for file-attachment downloads, bundled so they can travel from
 * [ChatPane] down to each file tile without threading five separate
 * parameters through every intermediate widget.
 */
class ChatFileDownloadActions(
    /**
     * Start downloading [attachment] (the [index]th attachment of [message]).
     * [resolvedUrl] is the absolute asset URL, already resolved by the widget
     * layer against `AppConfig`.
     */
    val onDownload: (message: Message, attachment: MessageAttachment, index: Int, resolvedUrl: String) -> Unit,
    val onPause: (String) -> Unit,
    val onResume: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onDismiss: (String) -> Unit,
)

class ChatVoicePlaybackActions(
    val activeMessageId: String?,
    val onToggle: ((messageId: String, resolvedUrl: String) -> Unit)?,
    val activePosition: Duration = Duration.ZERO,
    val activeDuration: Duration = Duration.ZERO,
) {
    fun isPlaying(messageId: String): Boolean = activeMessageId == messageId

    fun progressFor(messageId: String, fallbackDuration: Duration? = null): Double {
        if (!isPlaying(messageId)) return 0.0
        val duration = if (activeDuration > Duration.ZERO) activeDuration else fallbackDuration
        return voicePlaybackProgress(position = activePosition, duration = duration)
    }

    companion object {
        fun disabled(): ChatVoicePlaybackActions = ChatVoicePlaybackActions(activeMessageId = null, onToggle = null)
    }
}

class ChatMessageActions(
    val onCopy: suspend (context: BuildContext, message: Message) -> Unit,
    val onDeleteForMe: suspend (context: BuildContext, message: Message) -> Unit,
    val onRecall: suspend (context: BuildContext, message: Message) -> Unit,
    val canRecall: (Message) -> Boolean,
    val onQuote: (Message) -> Unit = ::_syncNoop,
    val onOpenQuote: suspend (context: BuildContext, quote: MessageQuote) -> Unit = { _, _ -> },
    val canQuote: (Message) -> Boolean = ::_neverCanRecall,
    val onReeditRecalledText: (Message) -> Unit = ::_syncNoop,
    val canReeditRecalledText: (Message) -> Boolean = ::_neverCanRecall,
    val canInspectRecalledText: (Message) -> Boolean = ::_neverCanRecall,
    val onViewSharedPlaylist: suspend (context: BuildContext, message: Message, playlist: SharedMusicPlaylist) -> Unit =
        { _, _, _ -> },
    val onEditOwnedSharedPlaylist: (suspend (context: BuildContext, message: Message, playlist: SharedMusicPlaylist) -> Unit)? = null,
    val sharedTrackPreviewController: MusicTrackPreviewController? = null,
    val loadSharedTrackPlaylists: (suspend (roomId: String) -> List<MusicTrackPlaylistTarget>)? = null,
    val onAddSharedTrackToPlaylist: (suspend (track: SharedMusicTrack, target: MusicTrackPlaylistTarget) -> Unit)? = null,
) {
    companion object {
        fun disabled(): ChatMessageActions =
            ChatMessageActions(
                onCopy = { _, _ -> },
                onDeleteForMe = { _, _ -> },
                onRecall = { _, _ -> },
                canRecall = ::_neverCanRecall,
            )
    }
}

private fun _syncNoop(@Suppress("UNUSED_PARAMETER") message: Message) {}

private fun _neverCanRecall(@Suppress("UNUSED_PARAMETER") message: Message): Boolean = false

@Composable
internal fun _MessageStage(
    roomId: String?,
    currentUser: CurrentUser,
    currentUserRoomDisplayName: String,
    currentUserRoomRole: String?,
    ownerUserId: String?,
    roomReady: Boolean,
    loading: Boolean,
    error: String?,
    timestampNow: DateTime,
    messages: List<Message>,
    newMessageCount: Int,
    focusMessageId: String?,
    onFocusMessageHandled: ((String) -> Unit)?,
    mentionMembers: List<RoomMember>,
    mentionMembersReady: Boolean,
    fileTransfers: Map<String, FileTransferState>,
    fileDownloads: Map<String, FileTransferState>,
    live: LiveState?,
    downloadActions: ChatFileDownloadActions,
    voicePlaybackActions: ChatVoicePlaybackActions,
    imagePreviewActions: ChatImagePreviewActions,
    messageActions: ChatMessageActions,
    onRetry: () -> Unit,
    onViewedNewMessages: () -> Unit,
    // Breathing room at the bottom of the list, between the last message and the
    // composer row that now sits below it.
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    onResolveSenderProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    senderProfileActionBuilder: UserProfileActionBuilder? = null,
    onMentionUser: ((UserSummary) -> Unit)? = null,
) = StatefulWidget(
    _MessageStageWidget(
        roomId, currentUser, currentUserRoomDisplayName, currentUserRoomRole, ownerUserId, roomReady, loading, error,
        timestampNow, messages, newMessageCount, focusMessageId, onFocusMessageHandled, mentionMembers, mentionMembersReady,
        fileTransfers, fileDownloads, live, downloadActions, voicePlaybackActions, imagePreviewActions, messageActions,
        onRetry, onViewedNewMessages, bottomInset, onResolveSenderProfile, onResolveRoomProfile, onEnterProfileRoom,
        senderProfileActionBuilder, onMentionUser, modifier,
    ),
) { _MessageStageState() }

internal class _MessageStageWidget(
    val roomId: String?,
    val currentUser: CurrentUser,
    val currentUserRoomDisplayName: String,
    val currentUserRoomRole: String?,
    val ownerUserId: String?,
    val roomReady: Boolean,
    val loading: Boolean,
    val error: String?,
    val timestampNow: DateTime,
    val messages: List<Message>,
    val newMessageCount: Int,
    val focusMessageId: String?,
    val onFocusMessageHandled: ((String) -> Unit)?,
    val mentionMembers: List<RoomMember>,
    val mentionMembersReady: Boolean,
    val fileTransfers: Map<String, FileTransferState>,
    val fileDownloads: Map<String, FileTransferState>,
    val live: LiveState?,
    val downloadActions: ChatFileDownloadActions,
    val voicePlaybackActions: ChatVoicePlaybackActions,
    val imagePreviewActions: ChatImagePreviewActions,
    val messageActions: ChatMessageActions,
    val onRetry: () -> Unit,
    val onViewedNewMessages: () -> Unit,
    val bottomInset: Dp,
    val onResolveSenderProfile: UserProfileResolver?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onEnterProfileRoom: ((PublicRoom) -> Unit)?,
    val senderProfileActionBuilder: UserProfileActionBuilder?,
    val onMentionUser: ((UserSummary) -> Unit)?,
    val modifier: Modifier,
)

/** What the scroll observer compares between layouts (see [_MessageStageState._handleScrollSnapshot]). */
private data class _ScrollSnapshot(val index: Int, val offset: Int, val key: Any?)

private class _MessageStageState : ViewState<_MessageStageWidget>() {
    companion object {
        // Last known scroll position per room, surviving widget teardown when the
        // pane swaps (live channel, settings, etc.). Static so it outlives the State.
        private val _scrollAnchors = mutableMapOf<String, _ScrollAnchor>()
    }

    private var _showDetailedTimestamps = false
    private var _showLatestButton = false
    private var _showNewMessageJumpButton = false
    private var _newMessageJumpDismissed = false
    private var _retainedNewMessageClientId: String? = null
    private var _retainedNewMessageLabelCount = 0
    private lateinit var _scrollController: LazyListState
    private val _messageListKey = RenderBoxHandle()

    // Dart's `_firstNewMessageKey`: the divider of the first unread row, per row
    // so a stale divider never answers for the current one.
    private val _firstNewMessageKeys = mutableMapOf<String, RenderBoxHandle>()
    private val _messageRowKeys = mutableMapOf<String, RenderBoxHandle>()
    private var _messageIndexByClientId: Map<String, Int> = emptyMap()
    private val _viewedUnreadMentionClientIds = mutableSetOf<String>()
    private var _highlightedMentionClientId: String? = null
    private var _highlightedMessageId: String? = null
    private var _handledFocusMessageId: String? = null
    private var _highlightedMentionTimer: CompositionTimer? = null
    private var _highlightedMessageTimer: CompositionTimer? = null
    private var _listReadyScheduled = false
    private var _latestButtonVisibilityScheduled = false
    private var _newMessageJumpVisibilityScheduled = false
    private var _messageListReady = false
    private var _incomingUnreadWaitingForUserScroll = false
    private var _restoringIncomingViewport = false

    // Guards queued scroll requests so overlapping requests don't stack up; the newest
    // request wins by bumping this token.
    private var _scrollToBottomToken = 0

    // Compose plumbing: the composition's scope (timers, scrolls, Dart's post-frame
    // callbacks), measured row heights (dp) and the last observed scroll position.
    private var _scope: CoroutineScope? = null
    private val _pendingPostFrameCallbacks = mutableListOf<() -> Unit>()
    private val _messageExtents = mutableMapOf<String, Float>()
    private val _dividerExtents = mutableMapOf<String, Float>()
    private var _density = 1f
    private var _listComposed = false
    private var _lastScrollSnapshot: _ScrollSnapshot? = null

    override fun initState() {
        _captureNewMessageAnchorFromWidget()
        // Seed the list with the remembered position so the very first layout
        // lands in place — restoring after the first frame would render one
        // frame pinned to the latest message first, which reads as a flash.
        val anchor = _anchorForRoom(widget.roomId)
        val restoring = anchor != null && !anchor.atBottom
        _scrollController =
            LazyListState(
                firstVisibleItemIndex = if (restoring) anchor!!.firstVisibleItemIndex else 0,
                firstVisibleItemScrollOffset = if (restoring) anchor!!.firstVisibleItemScrollOffset else 0,
            )
        // If we're not restoring a browsed-history position, land at the newest
        // message when the room first opens.
        if (!restoring) {
            _scrollToBottom(animated = false)
        }
        _scheduleLatestButtonVisibilitySync()
        _prepareFocusedMessageHighlight(widget.focusMessageId)
        _scheduleFocusedMessageJump(widget.focusMessageId)
    }

    override fun didUpdateWidget(oldWidget: _MessageStageWidget) {
        if (oldWidget.focusMessageId != widget.focusMessageId && widget.focusMessageId == null) {
            _handledFocusMessageId = null
        }
        if (widget.focusMessageId != null && oldWidget.messages.isNotEmpty() && widget.messages.isEmpty()) {
            _highlightedMessageTimer?.cancel()
            _highlightedMessageTimer = null
            _highlightedMessageId = null
            _handledFocusMessageId = null
        }
        if (oldWidget.roomId != widget.roomId) {
            _showDetailedTimestamps = false
            _showLatestButton = false
            _showNewMessageJumpButton = false
            _newMessageJumpDismissed = false
            _viewedUnreadMentionClientIds.clear()
            _highlightedMentionClientId = null
            _highlightedMessageId = null
            _handledFocusMessageId = null
            _highlightedMentionTimer?.cancel()
            _highlightedMentionTimer = null
            _highlightedMessageTimer?.cancel()
            _highlightedMessageTimer = null
            _incomingUnreadWaitingForUserScroll = false
            _restoringIncomingViewport = false
            _clearRetainedNewMessageAnchor()
            _captureNewMessageAnchorFromWidget()
            _messageRowKeys.clear()
            _firstNewMessageKeys.clear()
            _messageExtents.clear()
            _dividerExtents.clear()
            _messageListReady = false
            // New room: restore its remembered position, or snap to latest.
            val anchor = _anchorForRoom(widget.roomId)
            if (anchor != null && !anchor.atBottom) {
                _restoreScroll(anchor)
            } else {
                _scrollToBottom(animated = false)
            }
            _prepareFocusedMessageHighlight(widget.focusMessageId)
            _scheduleFocusedMessageJump(widget.focusMessageId)
            return
        }
        if (oldWidget.focusMessageId != widget.focusMessageId || oldWidget.messages.size != widget.messages.size) {
            _prepareFocusedMessageHighlight(widget.focusMessageId)
            _scheduleFocusedMessageJump(widget.focusMessageId)
        }
        val addedMessages = _addedMessagesSince(oldWidget)
        val preserveIncomingViewport = _shouldPreserveViewportForIncoming(addedMessages)
        // PORT: Flutter's reversed list keeps its pixel offset (the distance
        // from the newest message) when rows change, and Dart re-anchors the
        // viewport only for incoming unread messages. The lazy column keeps its
        // first visible row by key instead, which is that re-anchoring; every
        // other update keeps the first visible row's index, like the pixel
        // offset.
        val keepIndex = !preserveIncomingViewport && _hasClients && _firstVisibleKeyShifts(oldWidget.messages)

        if (oldWidget.newMessageCount != widget.newMessageCount) {
            _showNewMessageJumpButton = false
            if (widget.newMessageCount > 0) {
                _newMessageJumpDismissed = false
                val current = _currentUnreadMentionClientIds
                _viewedUnreadMentionClientIds.removeAll { id -> !current.contains(id) }
                _captureNewMessageAnchorFromWidget()
            } else {
                _incomingUnreadWaitingForUserScroll = false
                _viewedUnreadMentionClientIds.clear()
            }
        }
        if (oldWidget.messages.size != widget.messages.size ||
            (oldWidget.messages.isNotEmpty() && widget.messages.isNotEmpty() &&
                oldWidget.messages.last().clientMessageId != widget.messages.last().clientMessageId)
        ) {
            _showNewMessageJumpButton = false
            if (widget.newMessageCount > 0) {
                _newMessageJumpDismissed = false
                _captureNewMessageAnchorFromWidget()
            }
        }
        if (keepIndex) {
            _scrollController.requestScrollToItem(
                _scrollController.firstVisibleItemIndex,
                _scrollController.firstVisibleItemScrollOffset,
            )
        }
        if (_shouldFollowToBottom(oldWidget, addedMessages)) {
            _scrollToBottom(animated = true)
        } else if (preserveIncomingViewport && _hasClients) {
            _incomingUnreadWaitingForUserScroll = _scrollController.canScrollForward || _scrollController.canScrollBackward
            _restoreViewportAnchorAfterIncoming()
        }
    }

    override fun dispose() {
        _rememberScrollPosition()
        _highlightedMentionTimer?.cancel()
        _highlightedMessageTimer?.cancel()
    }

    private val _hasClients: Boolean get() = _listComposed && _messageListKey.hasSize

    private fun _anchorForRoom(roomId: String?): _ScrollAnchor? {
        if (roomId == null) return null
        return _scrollAnchors[roomId]
    }

    // Records the live scroll position (and whether it's pinned to the latest
    // message) so a later remount can restore it. The list is reversed, so the
    // newest message sits at the start — "at bottom" means the position is
    // within the follow threshold of it. Called on every scroll and on dispose.
    private fun _rememberScrollPosition() {
        val roomId = widget.roomId ?: return
        if (!_hasClients) return
        _scrollAnchors[roomId] =
            _ScrollAnchor(
                firstVisibleItemIndex = _scrollController.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = _scrollController.firstVisibleItemScrollOffset,
                atBottom = _distanceFromBottom() <= _autoScrollFollowThreshold,
            )
    }

    // Restores a remembered position once the list has laid out (the lazy
    // column clamps it to the current content).
    private fun _restoreScroll(anchor: _ScrollAnchor) {
        val token = ++_scrollToBottomToken
        _addPostFrameCallback {
            if (!mounted || token != _scrollToBottomToken || !_hasClients) return@_addPostFrameCallback
            _scope?.launch {
                _scrollController.scrollToItem(anchor.firstVisibleItemIndex, anchor.firstVisibleItemScrollOffset)
                if (!mounted) return@launch
                _rememberScrollPosition()
                _syncLatestButtonVisibility()
            }
        }
    }

    private fun _addedMessagesSince(oldWidget: _MessageStageWidget): List<Message> {
        if (widget.messages.isEmpty()) return emptyList()
        val grew = widget.messages.size > oldWidget.messages.size
        val lastChanged =
            oldWidget.messages.isNotEmpty() &&
                widget.messages.isNotEmpty() &&
                oldWidget.messages.last().clientMessageId != widget.messages.last().clientMessageId
        if (!grew && !lastChanged) return emptyList()
        val oldClientMessageIds = oldWidget.messages.mapTo(HashSet()) { it.clientMessageId }
        return widget.messages.filter { !oldClientMessageIds.contains(it.clientMessageId) }
    }

    private fun _shouldPreserveViewportForIncoming(addedMessages: List<Message>): Boolean {
        if (widget.newMessageCount <= 0 || addedMessages.isEmpty()) return false
        return addedMessages.any { message -> message.sender.id != widget.currentUser.id }
    }

    // Decides whether an update introduced a new message worth following. We
    // always chase our own outgoing messages; messages from others remain unread
    // until the divider actually enters the viewport.
    private fun _shouldFollowToBottom(oldWidget: _MessageStageWidget, addedMessages: List<Message>): Boolean {
        val messages = widget.messages
        if (messages.isEmpty()) return false
        if (addedMessages.isNotEmpty()) {
            return addedMessages.last().sender.id == widget.currentUser.id
        }
        val lastChanged =
            oldWidget.messages.isNotEmpty() && messages.last().clientMessageId != oldWidget.messages.last().clientMessageId
        if (!lastChanged) return false
        if (messages.last().sender.id == widget.currentUser.id) return true
        return false
    }

    /** Whether the row at the first visible position belongs to another message after the update. */
    private fun _firstVisibleKeyShifts(oldMessages: List<Message>): Boolean {
        val index = _scrollController.firstVisibleItemIndex
        val oldKey = oldMessages.getOrNull(oldMessages.size - 1 - index)?.clientMessageId
        val newKey = widget.messages.getOrNull(widget.messages.size - 1 - index)?.clientMessageId
        return oldKey != newKey
    }

    private fun _isNearBottom(): Boolean {
        if (!_hasClients) return true
        return _distanceFromBottom() <= _autoScrollFollowThreshold
    }

    /**
     * Dart's `position.pixels - position.minScrollExtent` (logical pixels): how
     * far the newest message has been scrolled away from the bottom.
     */
    private fun _distanceFromBottom(): Float {
        val list = _listRect() ?: return 0f
        val visible = _scrollController.layoutInfo.visibleItemsInfo
        val bottomItem = visible.minByOrNull { it.index } ?: return 0f
        val rect = _rowRectIfLaidOut(bottomItem.index)
        var distance =
            if (rect != null) {
                rect.bottom.value + widget.bottomInset.value - list.bottom.value
            } else {
                _scrollController.firstVisibleItemScrollOffset / _density
            }
        for (index in 0 until bottomItem.index) distance += _extentOf(index) + _messageListSeparator.value
        return max(0f, distance)
    }

    private fun _userInLive(userId: String): Boolean = liveParticipantByUserId(widget.live, userId) != null

    private val _currentUserMentionIdentity: UserSummary
        get() =
            widget.currentUser.toSummary().copyWith(
                roomDisplayName = widget.currentUserRoomDisplayName,
                roomRole = widget.currentUserRoomRole,
            )

    private fun _handleScrollChanged() {
        if (_incomingUnreadWaitingForUserScroll && !_restoringIncomingViewport) {
            _incomingUnreadWaitingForUserScroll = false
        }
        _rememberScrollPosition()
        _syncLatestButtonVisibility()
        _scheduleNewMessageJumpVisibilitySync()
    }

    /**
     * The lazy column's equivalent of Dart's scroll-controller listener. Rows
     * inserted below the first visible row move its index but keep its key and
     * offset (the column keeps it in place, Dart's viewport restore), and a
     * kept index keeps the index and offset: neither is a scroll. Anything else
     * moved the content.
     */
    private fun _handleScrollSnapshot(snapshot: _ScrollSnapshot) {
        val previous = _lastScrollSnapshot
        _lastScrollSnapshot = snapshot
        if (previous == null || !mounted) return
        val keyOrOffsetChanged = previous.key != snapshot.key || previous.offset != snapshot.offset
        val indexOrOffsetChanged = previous.index != snapshot.index || previous.offset != snapshot.offset
        if (keyOrOffsetChanged && indexOrOffsetChanged) {
            _handleScrollChanged()
        } else {
            _rememberScrollPosition()
            _syncLatestButtonVisibility()
            _scheduleNewMessageJumpVisibilitySync()
        }
    }

    private fun _syncLatestButtonVisibility() {
        if (!_hasClients) return
        val next = !_isNearBottom()
        if (next == _showLatestButton) return
        setState { _showLatestButton = next }
    }

    private fun _scheduleLatestButtonVisibilitySync() {
        if (_latestButtonVisibilityScheduled) return
        _latestButtonVisibilityScheduled = true
        _addPostFrameCallback {
            _latestButtonVisibilityScheduled = false
            if (!mounted) return@_addPostFrameCallback
            _rememberScrollPosition()
            _syncLatestButtonVisibility()
        }
    }

    private fun _captureNewMessageAnchorFromWidget() {
        val count = _effectiveWidgetNewMessageCount
        if (count <= 0) return
        val index = widget.messages.size - count
        if (index < 0 || index >= widget.messages.size) return
        _retainedNewMessageClientId = widget.messages[index].clientMessageId
        _retainedNewMessageLabelCount = _newMessageLabelCountFromWidget
    }

    private fun _clearRetainedNewMessageAnchor() {
        _retainedNewMessageClientId = null
        _retainedNewMessageLabelCount = 0
    }

    // PORT: Dart grew a spacer below the newest message until the oldest one
    // sat at the top of a short list, and kept the list invisible until that
    // settled. The lazy column top-aligns short content itself
    // (`Arrangement.Top`), so only the readiness flag remains.
    private fun _scheduleListReady() {
        if (_messageListReady || _listReadyScheduled) return
        _listReadyScheduled = true
        _addPostFrameCallback {
            _listReadyScheduled = false
            if (!mounted || _messageListReady) return@_addPostFrameCallback
            setState { _messageListReady = true }
        }
    }

    private fun _scheduleNewMessageJumpVisibilitySync() {
        if (_newMessageJumpVisibilityScheduled) return
        _newMessageJumpVisibilityScheduled = true
        _addPostFrameCallback {
            _newMessageJumpVisibilityScheduled = false
            if (!mounted) return@_addPostFrameCallback
            if (_firstNewMessageIsVisible() && !_newMessageJumpDismissed) {
                if (!_incomingUnreadWaitingForUserScroll) {
                    _markNewMessagesViewed()
                }
                return@_addPostFrameCallback
            }
            val next = _shouldShowNewMessageJumpButton()
            if (next == _showNewMessageJumpButton) return@_addPostFrameCallback
            setState { _showNewMessageJumpButton = next }
        }
    }

    private fun _shouldShowNewMessageJumpButton(): Boolean {
        val firstIndex = _firstNewMessageIndex
        if (firstIndex == null || _newMessageJumpDismissed) return false
        if (!_messageListReady || !_hasClients) return false
        val list = _listRect() ?: return false
        val reversedIndex = widget.messages.size - 1 - firstIndex
        // If the first-new anchor is not in the sliver cache, it is above the
        // visible window and needs the one-shot jump affordance.
        if (!_isRowBuilt(reversedIndex)) return true
        val anchor = _firstNewMessageAnchorRect(reversedIndex) ?: return true
        return anchor.top.value < list.top.value + 1
    }

    private fun _firstNewMessageAnchorIsVisible(): Boolean {
        val firstIndex = _firstNewMessageIndex ?: return false
        val message = widget.messages.getOrNull(firstIndex) ?: return false
        val reversedIndex = widget.messages.size - 1 - firstIndex
        if (!_isLaidOut(reversedIndex)) return false
        return _renderBoxIntersectsMessageList(_firstNewMessageKeys[message.clientMessageId])
    }

    private fun _firstNewMessageIsVisible(): Boolean {
        if (_firstNewMessageAnchorIsVisible()) return true
        val firstIndex = _firstNewMessageIndex
        if (firstIndex == null || firstIndex < 0 || firstIndex >= widget.messages.size) return false
        val message = widget.messages[firstIndex]
        if (!_isLaidOut(widget.messages.size - 1 - firstIndex)) return false
        return _renderBoxIntersectsMessageList(_messageRowKeys[message.clientMessageId])
    }

    private fun _renderBoxIntersectsMessageList(box: RenderBoxHandle?): Boolean {
        val list = _listRect()
        if (list == null || box == null || !box.hasSize) return false
        val rect = box.globalRect
        return rect.bottom >= list.top && rect.top <= list.bottom
    }

    // The list is reversed, so the latest message lives at the start. New rooms
    // render directly at the final bottom position instead of painting history
    // first and jumping on the next frame.
    private fun _scrollToBottom(animated: Boolean) {
        val token = ++_scrollToBottomToken
        _addPostFrameCallback {
            if (!mounted || token != _scrollToBottomToken || !_hasClients) return@_addPostFrameCallback
            val distance = _distanceFromBottom()
            if (distance < 1f) {
                _syncLatestButtonVisibility()
                return@_addPostFrameCallback
            }
            val scope = _scope ?: return@_addPostFrameCallback
            if (animated) {
                scope.launch {
                    try {
                        _scrollController.animateScrollBy(-distance * _density, tween(220, easing = Curves.easeOutCubic))
                        // Rows below the viewport were estimated: land exactly.
                        if (mounted && token == _scrollToBottomToken && _distanceFromBottom() >= 1f) {
                            _scrollController.scrollToItem(0)
                        }
                    } finally {
                        if (mounted) _syncLatestButtonVisibility()
                    }
                }
                return@_addPostFrameCallback
            }
            scope.launch {
                _scrollController.scrollToItem(0)
                if (mounted) _syncLatestButtonVisibility()
            }
        }
    }

    private fun _messageRowKey(clientMessageId: String): RenderBoxHandle = _messageRowKeys.getOrPut(clientMessageId) { RenderBoxHandle() }

    private fun _syncMessageRowKeys() {
        val messages = widget.messages
        val index = LinkedHashMap<String, Int>()
        for ((position, message) in messages.withIndex()) index[message.clientMessageId] = position
        _messageIndexByClientId = index
        _messageRowKeys.entries.removeAll { (id, key) -> !index.containsKey(id) || !key.hasSize }
        _firstNewMessageKeys.entries.removeAll { (id, _) -> !index.containsKey(id) }
        _messageExtents.keys.retainAll(index.keys)
    }

    // PORT: Dart's `_captureViewportAnchorFrom` and the restore passes after an
    // incoming unread message: the lazy column keeps the first visible row in
    // place by its key, so only the follow-up bookkeeping remains.
    private fun _restoreViewportAnchorAfterIncoming() {
        val token = ++_scrollToBottomToken
        _addPostFrameCallback {
            if (!mounted || token != _scrollToBottomToken || !_hasClients) return@_addPostFrameCallback
            _finishViewportAnchorRestore()
        }
    }

    private fun _finishViewportAnchorRestore() {
        _rememberScrollPosition()
        _syncLatestButtonVisibility()
        _scheduleNewMessageJumpVisibilitySync()
    }

    private val _effectiveWidgetNewMessageCount: Int
        get() {
            if (widget.messages.isEmpty() || widget.newMessageCount <= 0) return 0
            return widget.newMessageCount.coerceIn(0, widget.messages.size)
        }

    private val _newMessageLabelCountFromWidget: Int
        get() = if (widget.newMessageCount < 0) 0 else widget.newMessageCount

    private val _activeNewMessageCount: Int
        get() {
            val widgetCount = _effectiveWidgetNewMessageCount
            if (widgetCount > 0) return widgetCount
            if (_retainedNewMessageIndex == null) return 0
            return _retainedNewMessageLabelCount.coerceIn(0, widget.messages.size)
        }

    private val _newMessageLabelCount: Int
        get() {
            val widgetCount = _effectiveWidgetNewMessageCount
            val raw = if (widgetCount > 0) _newMessageLabelCountFromWidget else _retainedNewMessageLabelCount
            return max(0, raw - _viewedUnreadMentionClientIds.size)
        }

    private val _retainedNewMessageIndex: Int?
        get() {
            val retainedId = _retainedNewMessageClientId ?: return null
            val index = widget.messages.indexOfFirst { it.clientMessageId == retainedId }
            return if (index == -1) null else index
        }

    private val _firstNewMessageIndex: Int?
        get() {
            val widgetCount = _effectiveWidgetNewMessageCount
            if (widgetCount > 0) return widget.messages.size - widgetCount
            return _retainedNewMessageIndex
        }

    private val _unreadMentionIndexes: List<Int>
        get() {
            val firstIndex = _firstNewMessageIndex ?: return emptyList()
            val indexes = mutableListOf<Int>()
            val currentUser = _currentUserMentionIdentity
            for (index in firstIndex until widget.messages.size) {
                val message = widget.messages[index]
                if (_viewedUnreadMentionClientIds.contains(message.clientMessageId)) continue
                if (message.pending || message.isRemoved) continue
                if (message.sender.id == widget.currentUser.id) continue
                if (messageMentionsUser(
                        text = message.body,
                        mentions = message.mentions,
                        user = currentUser,
                        ownerUserId = widget.ownerUserId,
                    )
                ) {
                    indexes.add(index)
                }
            }
            return indexes
        }

    private val _currentUnreadMentionClientIds: Set<String>
        get() = _unreadMentionIndexes.mapTo(LinkedHashSet()) { widget.messages[it].clientMessageId }

    private val _nextUnreadMentionIndex: Int?
        get() {
            val indexes = _unreadMentionIndexes
            if (indexes.isEmpty()) return null
            return indexes.last()
        }

    private fun _markNewMessagesViewed() {
        if (_newMessageJumpDismissed) return
        setState {
            _newMessageJumpDismissed = true
            _showNewMessageJumpButton = false
            _incomingUnreadWaitingForUserScroll = false
            _viewedUnreadMentionClientIds.clear()
        }
        widget.onViewedNewMessages()
    }

    private fun _markUnreadMentionViewed(message: Message) {
        _viewedUnreadMentionClientIds.add(message.clientMessageId)
    }

    private fun _scrollToNextUnreadMention() {
        val index = _nextUnreadMentionIndex
        if (index == null || index < 0 || index >= widget.messages.size) {
            _scrollToFirstNewMessage()
            return
        }
        val message = widget.messages[index]
        setState {
            _highlightedMentionClientId = message.clientMessageId
            _markUnreadMentionViewed(message)
            _showNewMessageJumpButton = _shouldShowNewMessageJumpButton()
        }
        _highlightedMentionTimer?.cancel()
        _highlightedMentionTimer =
            _scope?.startTimer(2600.milliseconds) {
                if (!mounted || _highlightedMentionClientId != message.clientMessageId) return@startTimer
                setState { _highlightedMentionClientId = null }
            }
        _ensureMessageVisible(message)
    }

    private fun _scrollToFirstNewMessage() {
        _markNewMessagesViewed()
        if (_ensureFirstNewMessageVisible()) {
            return
        }
        if (!_hasClients) return
        val count = _activeNewMessageCount
        if (count <= 0) return
        // Dart estimated the target from the average row extent
        // (`maxScrollExtent / (messages - 1)`).
        val averageExtent = _averageExtent() + _messageListSeparator.value
        val firstIndex = _firstNewMessageIndex
        val distanceFromBottom = if (firstIndex == null) count - 1 else widget.messages.size - 1 - firstIndex
        val target = averageExtent * distanceFromBottom
        val delta = target - _distanceFromBottom()
        val scope = _scope ?: return
        scope.launch {
            try {
                _scrollController.animateScrollBy(delta * _density, tween(240, easing = Curves.easeOutCubic))
            } finally {
                if (mounted) _ensureFirstNewMessageVisible()
            }
        }
    }

    private fun _ensureFirstNewMessageVisible(): Boolean {
        val firstIndex = _firstNewMessageIndex ?: return false
        if (firstIndex < 0 || firstIndex >= widget.messages.size) return false
        val reversedIndex = widget.messages.size - 1 - firstIndex
        if (!_isRowBuilt(reversedIndex)) return false
        _ensureVisible(alignment = 0.12f) {
            val index = _firstNewMessageIndex ?: firstIndex
            _firstNewMessageAnchorRect(widget.messages.size - 1 - index)
        }
        return true
    }

    private fun _ensureMessageVisible(message: Message) {
        val chronologicalIndex = _messageIndexByClientId[message.clientMessageId] ?: return
        val reversedIndex = widget.messages.size - 1 - chronologicalIndex
        if (!_isRowBuilt(reversedIndex)) return
        _ensureVisible(alignment = 0.18f) {
            val index = _messageIndexByClientId[message.clientMessageId]
            if (index == null) null else _rowRect(widget.messages.size - 1 - index)
        }
    }

    /**
     * Dart's `Scrollable.ensureVisible(context, duration: 180ms, curve:
     * easeOutCubic, alignment:)` on the reversed list: as in Flutter's
     * `getOffsetToReveal` for `AxisDirection.up`, [alignment] places the
     * target's bottom `alignment * (viewport - target)` above the bottom.
     */
    private fun _ensureVisible(alignment: Float, target: () -> DpRect?) {
        val scope = _scope ?: return
        scope.launch {
            val list = _listRect() ?: return@launch
            val rect = target() ?: return@launch
            val delta = _revealDelta(list, rect, alignment)
            if (abs(delta) >= 0.5f) {
                _scrollController.animateScrollBy(delta * _density, tween(180, easing = Curves.easeOutCubic))
            }
            // A row that was not laid out was located from estimated heights:
            // settle on its measured position.
            if (!mounted) return@launch
            val exact = target() ?: return@launch
            val correction = _revealDelta(list, exact, alignment)
            if (abs(correction) >= 0.5f) _scrollController.scrollBy(correction * _density)
        }
    }

    private fun _revealDelta(list: DpRect, target: DpRect, alignment: Float): Float {
        val viewport = list.bottom.value - list.top.value
        val targetHeight = target.bottom.value - target.top.value
        val desiredBottom = list.bottom.value - (viewport - targetHeight) * alignment
        // Positive moves the content down (towards older messages).
        return desiredBottom - target.bottom.value
    }

    private fun _scheduleFocusedMessageJump(messageId: String?) {
        if (messageId == null || messageId.isEmpty()) return
        if (_handledFocusMessageId == messageId) return
        _addPostFrameCallback {
            if (!mounted) return@_addPostFrameCallback
            val message = _messageById(messageId) ?: return@_addPostFrameCallback
            val focusedMessageId = message.id
            _handledFocusMessageId = focusedMessageId
            setState { _highlightedMessageId = focusedMessageId }
            _ensureMessageVisible(message)
            _highlightedMessageTimer?.cancel()
            _highlightedMessageTimer =
                _scope?.startTimer(2600.milliseconds) {
                    if (!mounted || _highlightedMessageId != focusedMessageId) return@startTimer
                    setState { _highlightedMessageId = null }
                }
            widget.onFocusMessageHandled?.invoke(focusedMessageId)
        }
    }

    private fun _prepareFocusedMessageHighlight(messageId: String?) {
        if (messageId == null || messageId.isEmpty()) return
        if (_handledFocusMessageId == messageId && _highlightedMessageId == null) return
        val message = _messageById(messageId) ?: return
        _highlightedMessageId = message.id
    }

    private fun _messageById(messageId: String): Message? = widget.messages.firstOrNull { it.id == messageId }

    private fun _messageTargetsCurrentUser(message: Message): Boolean {
        if (message.pending || message.isRemoved) return false
        if (message.sender.id == widget.currentUser.id) return false
        return messageMentionsUser(
            text = message.body,
            mentions = message.mentions,
            user = _currentUserMentionIdentity,
            ownerUserId = widget.ownerUserId,
        )
    }

    private fun _messageIsHighlighted(message: Message): Boolean {
        if (message.clientMessageId == _highlightedMentionClientId) return true
        if (message.id == _highlightedMessageId) return true
        val focusMessageId = widget.focusMessageId
        return focusMessageId != null &&
            focusMessageId.isNotEmpty() &&
            _handledFocusMessageId != focusMessageId &&
            message.id == focusMessageId
    }

    private val _waitingForMentionRenderContext: Boolean
        get() {
            if (widget.mentionMembersReady) return false
            return widget.messages.any(::_messageNeedsMentionRenderContext)
        }

    private fun _messageNeedsMentionRenderContext(message: Message): Boolean {
        if (message.pending || message.isRemoved || message.type != "text") return false
        if (message.mentions.isNotEmpty()) return true
        return messageMentionRanges(message.body).isNotEmpty()
    }

    private fun _toggleDetailedTimestamps() {
        setState { _showDetailedTimestamps = !_showDetailedTimestamps }
    }

    // --- Geometry of the lazy column (window dp) -------------------------

    private fun _listRect(): DpRect? = if (_messageListKey.hasSize) _messageListKey.globalRect else null

    /** Whether the row [reversedIndex] rows above the newest one is laid out (on screen). */
    private fun _isLaidOut(reversedIndex: Int): Boolean =
        _scrollController.layoutInfo.visibleItemsInfo.any { it.index == reversedIndex }

    private fun _rowRectIfLaidOut(reversedIndex: Int): DpRect? {
        if (!_isLaidOut(reversedIndex)) return null
        val message = widget.messages.getOrNull(widget.messages.size - 1 - reversedIndex) ?: return null
        val handle = _messageRowKeys[message.clientMessageId] ?: return null
        return if (handle.hasSize) handle.globalRect else null
    }

    private fun _averageExtent(): Float = if (_messageExtents.isEmpty()) 64f else _messageExtents.values.average().toFloat()

    private fun _extentOf(reversedIndex: Int): Float {
        val message = widget.messages.getOrNull(widget.messages.size - 1 - reversedIndex) ?: return _averageExtent()
        return _messageExtents[message.clientMessageId] ?: _averageExtent()
    }

    /** The row's rectangle: measured when laid out, otherwise placed from the laid-out rows and known heights. */
    private fun _rowRect(reversedIndex: Int): DpRect? {
        _rowRectIfLaidOut(reversedIndex)?.let { return it }
        val laidOut =
            _scrollController.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                _rowRectIfLaidOut(info.index)?.let { info.index to it }
            }
        if (laidOut.isEmpty()) return null
        val separator = _messageListSeparator.value
        val extent = _extentOf(reversedIndex)
        val lowest = laidOut.minBy { it.first }
        val highest = laidOut.maxBy { it.first }
        if (reversedIndex < lowest.first) {
            // Newer than every laid-out row: below the viewport.
            var top = lowest.second.bottom.value + separator
            for (index in lowest.first - 1 downTo reversedIndex + 1) top += _extentOf(index) + separator
            return DpRect(lowest.second.left, top.dp, lowest.second.right, (top + extent).dp)
        }
        var bottom = highest.second.top.value - separator
        for (index in highest.first + 1 until reversedIndex) bottom -= _extentOf(index) + separator
        return DpRect(highest.second.left, (bottom - extent).dp, highest.second.right, bottom.dp)
    }

    /** Dart's "the row's element exists": within the list's cache extent of the viewport. */
    private fun _isRowBuilt(reversedIndex: Int): Boolean {
        if (reversedIndex < 0 || reversedIndex >= widget.messages.size) return false
        if (_rowRectIfLaidOut(reversedIndex) != null) return true
        val list = _listRect() ?: return false
        val rect = _rowRect(reversedIndex) ?: return false
        return rect.bottom.value > list.top.value - _messageListCacheExtent &&
            rect.top.value < list.bottom.value + _messageListCacheExtent
    }

    /** The unread divider (Dart's `_firstNewMessageKey`) of the row [reversedIndex] rows above the newest. */
    private fun _firstNewMessageAnchorRect(reversedIndex: Int): DpRect? {
        val message = widget.messages.getOrNull(widget.messages.size - 1 - reversedIndex) ?: return null
        val handle = _firstNewMessageKeys[message.clientMessageId]
        if (handle != null && handle.hasSize && _isLaidOut(reversedIndex)) return handle.globalRect
        val row = _rowRect(reversedIndex) ?: return null
        val height = _dividerExtents[message.clientMessageId] ?: _dividerExtents.values.firstOrNull() ?: 17f
        return DpRect(row.left, row.top, row.right, (row.top.value + height).dp)
    }

    // --- Dart's WidgetsBinding.addPostFrameCallback ------------------------

    private fun _addPostFrameCallback(callback: () -> Unit) {
        val scope = _scope
        if (scope == null) {
            _pendingPostFrameCallbacks.add(callback)
            return
        }
        // Resumes on the next frame, once this frame has been laid out.
        scope.launch {
            withFrameNanos { }
            callback()
        }
    }

    private fun _flushPendingPostFrameCallbacks() {
        if (_pendingPostFrameCallbacks.isEmpty()) return
        val callbacks = _pendingPostFrameCallbacks.toList()
        _pendingPostFrameCallbacks.clear()
        for (callback in callbacks) _addPostFrameCallback(callback)
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        _density = LocalDensity.current.density
        _flushPendingPostFrameCallbacks()
        _syncMessageRowKeys()
        val error = widget.error
        if (error != null && !widget.roomReady) {
            _listComposed = false
            FloatingNoticeEmitter(
                notices = listOf(FloatingNotice(message = error, tone = FloatingNoticeTone.error, duration = null)),
            ) {
                _CenteredState(
                    icon = Icons.Filled.ErrorOutline,
                    title = "无法加载聊天",
                    detail = "请稍后重试",
                    action = {
                        Button(
                            icon = { Icon(Icons.Filled.Refresh) },
                            onPressed = widget.onRetry,
                        ) { Text("重试") }
                    },
                    modifier = widget.modifier,
                )
            }
            return
        }

        if ((widget.loading && widget.messages.isEmpty()) || _waitingForMentionRenderContext) {
            _listComposed = false
            Box(widget.modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                _ChatProgressIndicator(size = 24.dp, strokeWidth = 2.2.dp, color = UiColors.accent)
            }
            return
        }

        if (widget.messages.isEmpty()) {
            _listComposed = false
            _CenteredState(icon = Icons.Outlined.Forum, title = "还没有消息", detail = "在下方开始对话吧", modifier = widget.modifier)
            return
        }

        _listComposed = true
        _scheduleListReady()
        _scheduleLatestButtonVisibilitySync()

        val now = widget.timestampNow
        val firstNewMessageIndex = _firstNewMessageIndex
        val showNewMessageJump = firstNewMessageIndex != null && !_newMessageJumpDismissed && _showNewMessageJumpButton
        val nextUnreadMentionIndex = _nextUnreadMentionIndex
        val showMentionJump = showNewMessageJump && nextUnreadMentionIndex != null
        val newMessageJumpTooltip =
            if (showMentionJump) "${widget.currentUserRoomDisplayName}@我" else "查看 $_newMessageLabelCount 条未读消息"
        _scheduleNewMessageJumpVisibilitySync()

        val messages = widget.messages
        val messageCount = messages.size
        val showDetailedTimestamps = _showDetailedTimestamps
        val currentUserMentionIdentity = _currentUserMentionIdentity

        LaunchedEffect(_scrollController) {
            snapshotFlow {
                val index = _scrollController.firstVisibleItemIndex
                _ScrollSnapshot(
                    index = index,
                    offset = _scrollController.firstVisibleItemScrollOffset,
                    key = _scrollController.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key,
                )
            }.collect { snapshot -> _handleScrollSnapshot(snapshot) }
        }

        Box(widget.modifier.fillMaxSize().clipToBounds()) {
            LazyColumn(
                state = _scrollController,
                reverseLayout = true,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(if (_messageListReady) 1f else 0f)
                        .renderBox(_messageListKey)
                        .testTag(chatMessageListTag),
                contentPadding =
                    PaddingValues(
                        start = _chatHorizontalPadding,
                        top = _chatMessageListTopPadding,
                        end = _chatHorizontalPadding,
                        bottom = widget.bottomInset,
                    ),
                verticalArrangement = Arrangement.spacedBy(_messageListSeparator, Alignment.Top),
            ) {
                items(count = messageCount, key = { index -> messages[messageCount - 1 - index].clientMessageId }) { index ->
                    val chronologicalIndex = messageCount - 1 - index
                    val message = messages[chronologicalIndex]
                    val systemEvent = systemMessageEvent(message)
                    val previous = if (chronologicalIndex == 0) null else messages[chronologicalIndex - 1]
                    val showTimestamp = shouldShowChatTimestamp(current = message.createdAt, previous = previous?.createdAt, now = now)
                    val timestamp =
                        if (showDetailedTimestamps) {
                            formatDetailedChatTimestamp(message.createdAt)
                        } else {
                            formatChatTimestamp(message.createdAt, now = now)
                        }
                    val showNewMessageDivider = chronologicalIndex == firstNewMessageIndex
                    val clientMessageId = message.clientMessageId
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .renderBox(_messageRowKey(clientMessageId))
                            .onSizeChanged { size -> _messageExtents[clientMessageId] = size.height / _density },
                    ) {
                        if (showNewMessageDivider) {
                            val dividerKey = _firstNewMessageKeys.getOrPut(clientMessageId) { RenderBoxHandle() }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .renderBox(dividerKey)
                                    .onSizeChanged { size -> _dividerExtents[clientMessageId] = size.height / _density },
                            ) {
                                _NewMessagesDivider()
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        if (showTimestamp) {
                            _MessageTimeDivider(label = timestamp, onTap = ::_toggleDetailedTimestamps)
                            Spacer(Modifier.height(10.dp))
                        }
                        when {
                            systemEvent != null ->
                                ChatSystemMessageContent(
                                    message = message,
                                    event = systemEvent,
                                    highlighted = _messageIsHighlighted(message),
                                    currentUser = widget.currentUser,
                                    ownerUserId = widget.ownerUserId,
                                    live = widget.live,
                                    messageActions = widget.messageActions,
                                    onResolveSenderProfile = widget.onResolveSenderProfile,
                                    onResolveRoomProfile = widget.onResolveRoomProfile,
                                    onEnterProfileRoom = widget.onEnterProfileRoom,
                                    profileActionBuilder = widget.senderProfileActionBuilder,
                                )
                            message.isRemoved ->
                                ChatRemovedMessageContent(
                                    message = message,
                                    highlighted = _messageIsHighlighted(message),
                                    currentUser = widget.currentUser,
                                    ownerUserId = widget.ownerUserId,
                                    live = widget.live,
                                    messageActions = widget.messageActions,
                                    onResolveSenderProfile = widget.onResolveSenderProfile,
                                    onResolveRoomProfile = widget.onResolveRoomProfile,
                                    onEnterProfileRoom = widget.onEnterProfileRoom,
                                    profileActionBuilder = widget.senderProfileActionBuilder,
                                )
                            else ->
                                _MessageRow(
                                    message = message,
                                    outgoing = message.sender.id == widget.currentUser.id,
                                    timestampNow = now,
                                    showDetailedTimestamps = showDetailedTimestamps,
                                    currentUser = widget.currentUser,
                                    currentUserMentionIdentity = currentUserMentionIdentity,
                                    ownerUserId = widget.ownerUserId,
                                    mentionMembers = widget.mentionMembers,
                                    transfer = widget.fileTransfers[message.clientMessageId],
                                    fileDownloads = widget.fileDownloads,
                                    downloadActions = widget.downloadActions,
                                    voicePlaybackActions = widget.voicePlaybackActions,
                                    imagePreviewActions = widget.imagePreviewActions,
                                    messageActions = widget.messageActions,
                                    mentionHighlighted = _messageIsHighlighted(message),
                                    mentionTargetsCurrentUser = _messageTargetsCurrentUser(message),
                                    inLive = _userInLive(message.sender.id),
                                    onResolveSenderProfile = widget.onResolveSenderProfile,
                                    onResolveRoomProfile = widget.onResolveRoomProfile,
                                    onEnterProfileRoom = widget.onEnterProfileRoom,
                                    profileActionBuilder = widget.senderProfileActionBuilder,
                                    onMentionUser = widget.onMentionUser,
                                    isUserInLive = ::_userInLive,
                                )
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = _chatFloatingEdgeInset, bottom = 12.dp)) {
                AnimatedVisibility(
                    visible = _showLatestButton,
                    enter = fadeIn(tween(120, easing = Curves.easeOutCubic)),
                    exit = fadeOut(tween(120, easing = Curves.easeInCubic)),
                ) {
                    _ChatJumpButton(
                        tag = chatJumpToLatestTag,
                        tooltip = "跳到最新消息",
                        icon = Icons.Rounded.KeyboardArrowDown,
                        onPressed = { _scrollToBottom(animated = true) },
                    )
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = _chatFloatingEdgeInset)) {
                AnimatedVisibility(
                    visible = showNewMessageJump,
                    enter = fadeIn(tween(120, easing = Curves.easeOutCubic)),
                    exit = fadeOut(tween(120, easing = Curves.easeInCubic)),
                ) {
                    _ChatJumpButton(
                        tag = chatJumpToFirstNewTag,
                        tooltip = newMessageJumpTooltip,
                        icon = Icons.Rounded.KeyboardArrowUp,
                        onPressed = if (showMentionJump) ::_scrollToNextUnreadMention else ::_scrollToFirstNewMessage,
                    )
                }
            }
        }
    }
}

@Composable
private fun _ChatJumpButton(tag: String, tooltip: String, icon: ImageVector, onPressed: () -> Unit) {
    PressableSurface(
        modifier = Modifier.testTag(tag),
        width = 34.dp,
        height = 34.dp,
        backgroundColor = UiColors.selected,
        selectedBackgroundColor = UiColors.selected,
        pressedBackgroundColor = Color(0xFF14211B),
        borderColor = UiColors.selectedBorder,
        selectedBorderColor = UiColors.selectedBorder,
        tooltip = tooltip,
        onPressed = onPressed,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, color = UiColors.accent, size = 18.dp)
        }
    }
}

@Composable
private fun _NewMessagesDivider() {
    val lineColor = UiColors.accent.copy(alpha = 0.58f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).decoration(lineColor))
        Text(
            "未读消息",
            modifier = Modifier.padding(EdgeInsets.symmetric(horizontal = 10.dp)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.label.copyWith(color = UiColors.accent, fontSize = 12.0, fontWeight = FontWeight.W700),
        )
        Box(Modifier.weight(1f).height(1.dp).decoration(lineColor))
    }
}

@Composable
private fun _MessageTimeDivider(label: String, onTap: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Flutter's DecoratedBox paints behind its padding without insetting it.
        Box(
            Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
                .decoration(UiColors.surfacePressed, RoundedCornerShape(999.dp), UiColors.border)
                .padding(EdgeInsets.symmetric(horizontal = 9.dp, vertical = 4.dp)),
        ) {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UiTypography.label.copyWith(color = UiColors.textMuted, fontSize = 11.0),
            )
        }
    }
}

@Composable
internal fun _CenteredState(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 320.dp).padding(EdgeInsets.all(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, color = UiColors.textMuted, size = 30.dp)
            Spacer(Modifier.height(12.dp))
            Text(title, textAlign = TextAlign.Center, style = UiTypography.title)
            Spacer(Modifier.height(7.dp))
            Text(detail, textAlign = TextAlign.Center, style = UiTypography.body.copyWith(color = UiColors.textMuted))
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

/**
 * Material's `CircularProgressIndicator(strokeWidth:, color:, value:)` as the
 * theme draws it (transparent track); a null [value] spins.
 */
@Composable
internal fun _ChatProgressIndicator(size: Dp, strokeWidth: Dp, color: Color, value: Double? = null) {
    if (value == null) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = strokeWidth,
            trackColor = Color.Transparent,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Square,
        )
    } else {
        androidx.compose.material3.CircularProgressIndicator(
            progress = { value.toFloat() },
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = strokeWidth,
            trackColor = Color.Transparent,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt,
            gapSize = 0.dp,
        )
    }
}

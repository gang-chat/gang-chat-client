package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.FileTransferState
import com.gangchat.client.app.MessagesController
import com.gangchat.client.app.RoomMessageHistoryCategory
import com.gangchat.client.app.RoomNotificationDateRange
import com.gangchat.client.app.RoomsController
import com.gangchat.client.app.apiValue
import com.gangchat.client.app.liveParticipantByUserId
import com.gangchat.client.app.roomInviteTimestampLabel
import com.gangchat.client.app.roomMemberDisplayName
import com.gangchat.client.app.roomMemberSearchRank
import com.gangchat.client.app.roomMessageHistoryCopyText
import com.gangchat.client.app.roomMessageHistoryDayEndExclusive
import com.gangchat.client.app.roomMessageHistoryDayStart
import com.gangchat.client.app.roomRoleLabel
import com.gangchat.client.app.systemMessageEvent
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessagePage
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.shell.ClipboardService
import com.gangchat.client.ui.AdaptiveHighlightedText
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.RoleBadge
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.UiCheckbox
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiContextMenuItem
import com.gangchat.client.ui.UiContextMenuSection
import com.gangchat.client.ui.UiContextMenuTriggerRegion
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.ui.showUiContextMenu
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.rawScrollbar
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.rememberScrollbarAdapter
import com.gangchat.client.widgets.showDialog
import com.gangchat.client.widgets.startTimer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

// Port of lib/src/home/home_shell_room_message_history.dart.

// Test tags of the message history pane (Dart's `ValueKey`s).
const val roomMessageHistorySearchTag = "room-message-history-search"
const val roomMessageHistoryDateFilterTag = "room-message-history-date-filter"
const val roomMessageHistoryMemberFilterTag = "room-message-history-member-filter"
const val roomMessageHistoryBatchManageTag = "room-message-history-batch-manage"
const val roomMessageHistorySelectAllTag = "room-message-history-select-all"
const val roomMessageHistoryListTag = "room-message-history-list"

fun roomMessageHistoryRowTag(messageId: String) = "room-message-history-row-$messageId"

fun roomMessageHistoryContentTag(messageId: String) = "room-message-history-content-$messageId"

fun roomMessageHistoryAvatarTag(messageId: String) = "room-message-history-avatar-$messageId"

fun roomMessageHistoryTimeTag(messageId: String) = "room-message-history-time-$messageId"

fun roomMessageHistoryJumpTag(messageId: String) = "room-message-history-jump-$messageId"

fun roomMessageHistorySelectTag(messageId: String) = "room-message-history-select-$messageId"

const val messageHistoryMemberSearchTag = "message-history-member-search"
const val messageHistoryMemberResetTag = "message-history-member-reset"
const val messageHistoryMemberApplyTag = "message-history-member-apply"

fun messageHistoryMemberTag(userId: String?) = if (userId == null) "message-history-member-all" else "message-history-member-$userId"

fun messageHistoryMemberAvatarTag(userId: String) = "message-history-member-avatar-$userId"

@Composable
internal fun _RoomMessageHistoryPane(
    room: RoomDetail,
    currentUser: CurrentUser,
    roomsController: RoomsController,
    messagesController: MessagesController,
    clipboardService: ClipboardService,
    fileDownloads: Map<String, FileTransferState>,
    downloadActions: ChatFileDownloadActions,
    voicePlaybackActions: ChatVoicePlaybackActions,
    imagePreviewActions: ChatImagePreviewActions,
    messageActions: ChatMessageActions,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
    onOpenRoom: ((PublicRoom) -> Unit)?,
    profileActionBuilder: UserProfileActionBuilder?,
    onJumpToMessage: (String) -> Unit,
) = StatefulWidget(
    _RoomMessageHistoryPaneWidget(
        room, currentUser, roomsController, messagesController, clipboardService, fileDownloads, downloadActions,
        voicePlaybackActions, imagePreviewActions, messageActions, onResolveRoomProfile, onResolveRoomUserProfile,
        onOpenRoom, profileActionBuilder, onJumpToMessage,
    ),
) { _RoomMessageHistoryPaneState() }

internal class _RoomMessageHistoryPaneWidget(
    val room: RoomDetail,
    val currentUser: CurrentUser,
    val roomsController: RoomsController,
    val messagesController: MessagesController,
    val clipboardService: ClipboardService,
    val fileDownloads: Map<String, FileTransferState>,
    val downloadActions: ChatFileDownloadActions,
    val voicePlaybackActions: ChatVoicePlaybackActions,
    val imagePreviewActions: ChatImagePreviewActions,
    val messageActions: ChatMessageActions,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onResolveRoomUserProfile: RoomUserProfileResolver?,
    val onOpenRoom: ((PublicRoom) -> Unit)?,
    val profileActionBuilder: UserProfileActionBuilder?,
    val onJumpToMessage: (String) -> Unit,
)

private const val _historyPageSize = 50

private class _RoomMessageHistoryPaneState : ViewState<_RoomMessageHistoryPaneWidget>() {
    private val _searchController = TextEditingController()
    private var _scrollState: LazyListState? = null
    private var _scope: CoroutineScope? = null
    private var _searchDebounce: CompositionTimer? = null
    private lateinit var _defaultDateRange: RoomNotificationDateRange
    private lateinit var _dateRange: RoomNotificationDateRange
    private var _category = RoomMessageHistoryCategory.all
    private var _members: List<RoomMember> = emptyList()
    private var _selectedMember: RoomMember? = null
    private var _messages: List<Message> = emptyList()
    private var _selectedMessageIds: Set<String> = emptySet()
    private var _selectionMode = false
    private var _loading = true
    private var _loadingMore = false
    private var _hasMore = false
    private var _membersLoading = true
    private var _nextBefore: String? = null
    private var _error: String? = null
    private var _loadSerial = 0

    override fun initState() {
        _defaultDateRange = _makeDefaultDateRange()
        _dateRange = _defaultDateRange
        unawaited { _loadMembers() }
        unawaited { _reload() }
    }

    override fun dispose() {
        _searchDebounce?.cancel()
        _searchController.dispose()
    }

    private fun _makeDefaultDateRange(): RoomNotificationDateRange =
        RoomNotificationDateRange.defaultFor(accountCreatedAt = widget.currentUser.createdAt, today = DateTime.now())

    private suspend fun _loadMembers() {
        try {
            val members = widget.roomsController.loadAllRoomMembers(widget.room.id)
            if (!mounted) return
            setState {
                _members = members
                _membersLoading = false
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState { _membersLoading = false }
            showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "加载房间成员失败"))
        }
    }

    private fun _handleScroll(extentAfterPx: Float, densityScale: Float) {
        if (extentAfterPx / densityScale > 240f) return
        unawaited { _loadMore() }
    }

    private fun _handleSearchChanged(@Suppress("UNUSED_PARAMETER") value: String) {
        _searchDebounce?.cancel()
        _searchDebounce =
            _scope?.startTimer(280.milliseconds) {
                if (!mounted) return@startTimer
                _clearSelection()
                unawaited { _reload() }
            }
    }

    private suspend fun _reload() {
        val serial = ++_loadSerial
        setState {
            _loading = true
            _loadingMore = false
            _error = null
            _messages = emptyList()
            _hasMore = false
            _nextBefore = null
        }
        try {
            val page = _requestPage()
            if (!mounted || serial != _loadSerial) return
            setState {
                _messages = page.messages
                _hasMore = page.hasMore
                _nextBefore = page.nextBefore
                _loading = false
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted || serial != _loadSerial) return
            setState {
                _loading = false
                _error = userFacingErrorMessage(error, fallback = "加载消息记录失败")
            }
        }
    }

    private suspend fun _loadMore() {
        if (_loading || _loadingMore || !_hasMore || _nextBefore == null) return
        val serial = _loadSerial
        setState { _loadingMore = true }
        try {
            val page = _requestPage(before = _nextBefore)
            if (!mounted || serial != _loadSerial) return
            val knownIds = _messages.map { message -> message.id }.toMutableSet()
            setState {
                _messages = _messages + page.messages.filter { message -> knownIds.add(message.id) }
                _hasMore = page.hasMore
                _nextBefore = page.nextBefore
                _loadingMore = false
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted || serial != _loadSerial) return
            setState { _loadingMore = false }
            showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "加载更多消息记录失败"))
        }
    }

    private suspend fun _requestPage(before: String? = null): MessagePage =
        widget.messagesController.loadMessageHistory(
            roomId = widget.room.id,
            query = _searchController.text,
            category = _category.apiValue,
            senderUserId = _selectedMember?.user?.id,
            startAt = roomMessageHistoryDayStart(_dateRange.startDate),
            endAt = roomMessageHistoryDayEndExclusive(_dateRange.endDate),
            limit = _historyPageSize,
            before = before,
        )

    private suspend fun _showDateFilter() {
        val result =
            showDateRangeFilterDialog(
                context,
                initialRange = _dateRange,
                defaultRange = _defaultDateRange,
                title = "筛选消息日期",
                description = "选择包含首尾日期的消息时间区间。",
            )
        if (!mounted || result == null || result == _dateRange) return
        setState { _dateRange = result }
        _clearSelection()
        _reload()
    }

    private suspend fun _showMemberFilter() {
        if (_membersLoading) return
        val result =
            showDialog<_HistoryMemberFilterResult>(context) { _ ->
                _HistoryMemberFilterDialog(
                    roomId = widget.room.id,
                    currentUser = widget.currentUser,
                    members = _members,
                    selectedMember = _selectedMember,
                    onResolveRoomProfile = widget.onResolveRoomProfile,
                    onResolveRoomUserProfile = widget.onResolveRoomUserProfile,
                    onOpenRoom = widget.onOpenRoom,
                )
            }
        if (!mounted || result == null) return
        if (result.member?.user?.id == _selectedMember?.user?.id) return
        setState { _selectedMember = result.member }
        _clearSelection()
        _reload()
    }

    private fun _changeCategory(category: RoomMessageHistoryCategory) {
        if (_category == category) return
        setState { _category = category }
        _clearSelection()
        unawaited { _reload() }
    }

    private fun _toggleSelectionMode() {
        setState {
            _selectionMode = !_selectionMode
            _selectedMessageIds = emptySet()
        }
    }

    private fun _clearSelection() {
        if (_selectedMessageIds.isEmpty()) return
        setState { _selectedMessageIds = emptySet() }
    }

    private fun _toggleMessage(messageId: String) {
        setState {
            val ids = LinkedHashSet(_selectedMessageIds)
            if (!ids.add(messageId)) ids.remove(messageId)
            _selectedMessageIds = ids
        }
    }

    private fun _toggleAllMessages() {
        if (_messages.isEmpty()) return
        val visibleIds = _messages.map { message -> message.id }.toSet()
        setState {
            _selectedMessageIds =
                if (visibleIds.all { _selectedMessageIds.contains(it) }) {
                    _selectedMessageIds - visibleIds
                } else {
                    _selectedMessageIds + visibleIds
                }
        }
    }

    private val _selectedMessages: List<Message>
        get() = _messages.filter { message -> _selectedMessageIds.contains(message.id) }

    private val _currentUserMentionIdentity: UserSummary
        get() {
            val roomName = widget.room.personalProfile.displayName?.trim()
            return widget.currentUser.toSummary().copyWith(
                roomDisplayName = if (roomName != null && roomName.isNotEmpty()) roomName else widget.currentUser.displayName,
                roomRole = widget.room.myMembership.role,
            )
        }

    private fun _resolveSender(): UserProfileResolver? {
        val resolver = widget.onResolveRoomUserProfile ?: return null
        return { user -> resolver(widget.room.id, user) }
    }

    /** Dart's `({Widget content, bool inline})`: [inline] for system and removed rows. */
    private fun _isInlineContent(message: Message): Boolean =
        systemMessageEvent(message) != null || message.isRemoved

    @Composable
    private fun _buildMessageContent(message: Message) {
        val systemEvent = systemMessageEvent(message)
        val tag = Modifier.testTag(roomMessageHistoryContentTag(message.id))
        if (systemEvent != null) {
            ChatSystemMessageContent(
                modifier = tag,
                message = message,
                event = systemEvent,
                currentUser = widget.currentUser,
                ownerUserId = widget.room.createdBy?.id,
                live = widget.room.live,
                enableContextMenu = false,
                showSurface = false,
                alignment = Alignment.CenterStart,
                wrapAlignment = Alignment.Start,
                textStyle = UiTypography.body,
                avatarSize = CompactActivityLayout.avatarSize,
                onResolveSenderProfile = _resolveSender(),
                onResolveRoomProfile = widget.onResolveRoomProfile,
                onEnterProfileRoom = widget.onOpenRoom,
                profileActionBuilder = widget.profileActionBuilder,
            )
            return
        }
        if (message.isRemoved) {
            ChatRemovedMessageContent(
                modifier = tag,
                message = message,
                currentUser = widget.currentUser,
                ownerUserId = widget.room.createdBy?.id,
                live = widget.room.live,
                messageActions = widget.messageActions,
                enableContextMenu = false,
                showSurface = false,
                alignment = Alignment.CenterStart,
                wrapAlignment = Alignment.Start,
                onResolveSenderProfile = _resolveSender(),
                onResolveRoomProfile = widget.onResolveRoomProfile,
                onEnterProfileRoom = widget.onOpenRoom,
                profileActionBuilder = widget.profileActionBuilder,
            )
            return
        }
        ChatMessageContent(
            modifier = tag,
            message = message,
            outgoing = message.sender.id == widget.currentUser.id,
            fileDownloads = widget.fileDownloads,
            downloadActions = widget.downloadActions,
            voicePlaybackActions = widget.voicePlaybackActions,
            imagePreviewActions = widget.imagePreviewActions,
            currentUser = widget.currentUser,
            currentUserMentionIdentity = _currentUserMentionIdentity,
            ownerUserId = widget.room.createdBy?.id,
            mentionMembers = _members,
            onResolveSenderProfile = _resolveSender(),
            onResolveRoomProfile = widget.onResolveRoomProfile,
            onEnterProfileRoom = widget.onOpenRoom,
            profileActionBuilder = widget.profileActionBuilder,
            isUserInLive = { userId -> liveParticipantByUserId(widget.room.live, userId) != null },
            onOpenQuote = widget.messageActions.onOpenQuote,
            onViewSharedPlaylist = widget.messageActions.onViewSharedPlaylist,
            sharedTrackPreviewController = widget.messageActions.sharedTrackPreviewController,
            loadSharedTrackPlaylists = widget.messageActions.loadSharedTrackPlaylists,
            onAddSharedTrackToPlaylist = widget.messageActions.onAddSharedTrackToPlaylist,
        )
    }

    private suspend fun _copyMessage(message: Message) {
        widget.clipboardService.writeText(roomMessageHistoryCopyText(message))
        if (mounted) showFloatingSuccessNotice(context, "已复制")
    }

    private suspend fun _deleteMessages(messages: List<Message>) {
        if (messages.isEmpty()) return
        val confirmed =
            showDialog<Boolean>(context) { dialogContext ->
                _HistoryDeleteConfirmDialog(dialogContext, messageCount = messages.size)
            }
        if (confirmed != true || !mounted) return
        try {
            widget.messagesController.hideMessageHistory(roomId = widget.room.id, messageIds = messages.map { message -> message.id })
            if (!mounted) return
            val deletedIds = messages.map { message -> message.id }.toSet()
            setState {
                _messages = _messages.filter { message -> !deletedIds.contains(message.id) }
                _selectedMessageIds = _selectedMessageIds - deletedIds
            }
            showFloatingSuccessNotice(context, if (messages.size == 1) "已删除消息记录" else "已删除 ${messages.size} 条消息记录")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) {
                showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "删除消息记录失败"))
            }
        }
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        val allSelected = _messages.isNotEmpty() && _messages.all { message -> _selectedMessageIds.contains(message.id) }
        val selectedMember = _selectedMember
        Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, bottom = 22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Input(
                    modifier = Modifier.weight(1f).testTag(roomMessageHistorySearchTag),
                    controller = _searchController,
                    hintText = "搜索消息记录",
                    prefixIcon = Icons.Filled.Search,
                    showClearButton = true,
                    onChanged = { value -> _handleSearchChanged(value) },
                )
                Spacer(Modifier.width(10.dp))
                ButtonIcon(
                    modifier = Modifier.testTag(roomMessageHistoryDateFilterTag),
                    tooltip = "筛选消息日期",
                    icon = { Icon(Icons.Outlined.CalendarMonth) },
                    selected = _dateRange != _defaultDateRange,
                    onPressed = { unawaited { _showDateFilter() } },
                    size = Input.defaultHeight,
                )
                Spacer(Modifier.width(8.dp))
                ButtonIcon(
                    modifier = Modifier.testTag(roomMessageHistoryMemberFilterTag),
                    tooltip = if (selectedMember == null) "筛选成员（所有人）" else "筛选成员：${roomMemberDisplayName(selectedMember)}",
                    icon = { Icon(Icons.Outlined.PersonSearch) },
                    selected = selectedMember != null,
                    loading = _membersLoading,
                    onPressed = if (_membersLoading) null else ({ unawaited { _showMemberFilter() } }),
                    size = Input.defaultHeight,
                )
                Spacer(Modifier.width(8.dp))
                ButtonIcon(
                    modifier = Modifier.testTag(roomMessageHistoryBatchManageTag),
                    tooltip = if (_selectionMode) "退出批量管理" else "批量管理",
                    icon = { Icon(Icons.Outlined.Checklist) },
                    selected = _selectionMode,
                    onPressed = { _toggleSelectionMode() },
                    size = Input.defaultHeight,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (_selectionMode) {
                    UiCheckbox(
                        modifier = Modifier.testTag(roomMessageHistorySelectAllTag),
                        value = allSelected,
                        onChanged = if (_messages.isEmpty()) null else ({ _ -> _toggleAllMessages() }),
                        tooltip = if (allSelected) "取消全选当前消息记录" else "全选当前消息记录",
                        semanticLabel = "全选当前消息记录",
                    )
                    Spacer(Modifier.width(8.dp))
                }
                SegmentedControl(
                    modifier = Modifier.weight(1f),
                    expanded = true,
                    value = _category,
                    onChanged = { category -> _changeCategory(category) },
                    segments =
                        listOf(
                            Segment(value = RoomMessageHistoryCategory.all, label = "全部", icon = Icons.Outlined.Inbox),
                            Segment(value = RoomMessageHistoryCategory.links, label = "链接", icon = Icons.Filled.Link),
                            Segment(value = RoomMessageHistoryCategory.voice, label = "语音", icon = Icons.Outlined.MicNone),
                            Segment(value = RoomMessageHistoryCategory.stickers, label = "表情", icon = Icons.Outlined.EmojiEmotions),
                            Segment(value = RoomMessageHistoryCategory.images, label = "图片", icon = Icons.Outlined.Image),
                            Segment(value = RoomMessageHistoryCategory.files, label = "文件", icon = Icons.Filled.AttachFile),
                            Segment(value = RoomMessageHistoryCategory.music, label = "音乐", icon = Icons.Outlined.LibraryMusic),
                            Segment(value = RoomMessageHistoryCategory.system, label = "系统", icon = Icons.Outlined.Info),
                        ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) { _buildBody() }
        }
    }

    @Composable
    private fun _buildBody() {
        if (_loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                _ChatProgressIndicator(size = 36.dp, strokeWidth = 4.dp, color = UiColors.accent)
            }
            return
        }
        val error = _error
        if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, textAlign = TextAlign.Center, style = UiTypography.body.copy(color = UiColors.danger))
                    Spacer(Modifier.height(12.dp))
                    Button(onPressed = { unawaited { _reload() } }, icon = { Icon(Icons.Filled.Refresh) }) {
                        Text("重试")
                    }
                }
            }
            return
        }
        if (_messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有符合条件的消息记录", style = UiTypography.body.copy(color = UiColors.textMuted))
            }
            return
        }
        val listState = rememberLazyListState()
        _scrollState = listState
        val density = LocalDensity.current
        // Dart's ScrollController listener: load the next page within 240
        // logical pixels of the end.
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect {
                val layout = listState.layoutInfo
                val last = layout.visibleItemsInfo.lastOrNull() ?: return@collect
                if (last.index < layout.totalItemsCount - 1) return@collect
                val extentAfter = (last.offset + last.size - layout.viewportEndOffset).coerceAtLeast(0)
                _handleScroll(extentAfter.toFloat(), density.density)
            }
        }
        val selectedMessages = _selectedMessages
        LazyColumn(
            Modifier.fillMaxSize().testTag(roomMessageHistoryListTag),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(count = _messages.size + if (_loadingMore) 1 else 0, key = { index ->
                if (index == _messages.size) "loading-more" else _messages[index].id
            }) { index ->
                if (index == _messages.size) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        _ChatProgressIndicator(size = 36.dp, strokeWidth = 4.dp, color = UiColors.accent)
                    }
                    return@items
                }
                val message = _messages[index]
                val inline = _isInlineContent(message)
                _HistoryMessageRow(
                    message = message,
                    content = { _buildMessageContent(message) },
                    inlineContent = inline,
                    senderAvatar = if (inline) null else ({ _buildSenderAvatar(message) }),
                    senderColor =
                        chatRoomUsernameColor(
                            user = message.sender,
                            currentUser = widget.currentUser,
                            ownerUserId = widget.room.createdBy?.id,
                        ),
                    selectionMode = _selectionMode,
                    selected = _selectedMessageIds.contains(message.id),
                    selectedMessages = selectedMessages,
                    onToggleSelection = { _toggleMessage(message.id) },
                    onCopy = { item -> _copyMessage(item) },
                    onDelete = { items -> _deleteMessages(items) },
                    onJump = { widget.onJumpToMessage(message.id) },
                )
            }
        }
    }

    @Composable
    private fun _buildSenderAvatar(message: Message) {
        UserHoverCard(
            user = message.sender,
            currentUser = widget.currentUser,
            onResolveProfile = _resolveSender(),
            onResolveRoomProfile = widget.onResolveRoomProfile,
            onEnterCommonRoom = widget.onOpenRoom,
            profileActionBuilder = widget.profileActionBuilder,
            inLive = liveParticipantByUserId(widget.room.live, message.sender.id) != null,
            showRoomRole = true,
        ) {
            Avatar(
                modifier = Modifier.testTag(roomMessageHistoryAvatarTag(message.id)),
                label = userAvatarLabel(message.sender),
                imageUrl = AppConfigScope.of(context).resolveAssetUrl(message.sender.avatarUrl),
                defaultAvatarKey = message.sender.defaultAvatarKey,
                size = 38.dp,
            )
        }
    }
}

/**
 * Flutter's `IgnorePointer(ignoring:)` around visible interactive content: an
 * overlay that takes the hits (without consuming them), so the content gets
 * no pointer input while the ancestors still see the gesture.
 */
@Composable
private fun _IgnorePointer(ignoring: Boolean, modifier: Modifier = Modifier, child: @Composable () -> Unit) {
    Box(modifier) {
        child()
        if (ignoring) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            )
        }
    }
}

@Composable
private fun _HistoryMessageRow(
    message: Message,
    content: @Composable () -> Unit,
    inlineContent: Boolean,
    senderAvatar: (@Composable () -> Unit)?,
    senderColor: Color,
    selectionMode: Boolean,
    selected: Boolean,
    selectedMessages: List<Message>,
    onToggleSelection: () -> Unit,
    onCopy: suspend (Message) -> Unit,
    onDelete: suspend (List<Message>) -> Unit,
    onJump: () -> Unit,
) = StatefulWidget(
    _HistoryMessageRowWidget(
        message, content, inlineContent, senderAvatar, senderColor, selectionMode, selected, selectedMessages,
        onToggleSelection, onCopy, onDelete, onJump,
    ),
) { _HistoryMessageRowState() }

private class _HistoryMessageRowWidget(
    val message: Message,
    val content: @Composable () -> Unit,
    val inlineContent: Boolean,
    val senderAvatar: (@Composable () -> Unit)?,
    val senderColor: Color,
    val selectionMode: Boolean,
    val selected: Boolean,
    val selectedMessages: List<Message>,
    val onToggleSelection: () -> Unit,
    val onCopy: suspend (Message) -> Unit,
    val onDelete: suspend (List<Message>) -> Unit,
    val onJump: () -> Unit,
)

private class _HistoryMessageRowState : ViewState<_HistoryMessageRowWidget>() {
    private var _contextMenuActive = false

    @Composable
    override fun build() {
        val message = widget.message
        val senderName = _historySenderName(message.sender)
        val highlighted = widget.selected || _contextMenuActive
        val useCompactLayout = HomeAdaptiveLayout.usesCompactLayout(context)
        val timestampColumnWidth =
            if (useCompactLayout) CompactActivityLayout.compactTimestampColumnWidth else CompactActivityLayout.timestampColumnWidth
        val timestampContentGap = if (useCompactLayout) CompactActivityLayout.compactTimestampContentGap else 8.dp
        val animation = tween<Color>(90, easing = Curves.easeOutCubic)
        val background by animateColorAsState(if (highlighted) UiColors.selected else UiColors.surface, animation, label = "historyRowBackground")
        val border by animateColorAsState(if (highlighted) UiColors.selectedBorder else UiColors.border, animation, label = "historyRowBorder")
        val shape = RoundedCornerShape(UiRadii.lg)
        val timestamp = roomInviteTimestampLabel(message.createdAt)

        val surface: @Composable () -> Unit = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .testTag(roomMessageHistoryRowTag(message.id))
                    .decoration(background, shape, border)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clipToBounds(),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(timestampColumnWidth))
                    Spacer(Modifier.width(timestampContentGap))
                    if (!widget.inlineContent) {
                        widget.senderAvatar!!()
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (widget.inlineContent) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                                _IgnorePointer(ignoring = widget.selectionMode) { widget.content() }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    senderName,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = UiTypography.label.copyWith(color = widget.senderColor, fontWeight = FontWeight.W700),
                                )
                                Spacer(Modifier.height(4.dp))
                                _IgnorePointer(ignoring = widget.selectionMode, modifier = Modifier.fillMaxWidth()) { widget.content() }
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Spacer(Modifier.size(34.dp))
                }
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier.width(timestampColumnWidth).fillMaxHeight().align(Alignment.CenterStart),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (useCompactLayout) {
                            CompactActivityTimestamp(
                                modifier = Modifier.testTag(roomMessageHistoryTimeTag(message.id)),
                                timestamp = timestamp,
                                style = UiTypography.label.copy(color = UiColors.textMuted),
                            )
                        } else {
                            Text(
                                timestamp,
                                modifier = Modifier.testTag(roomMessageHistoryTimeTag(message.id)),
                                textAlign = TextAlign.Center,
                                style = UiTypography.label.copy(color = UiColors.textMuted),
                            )
                        }
                    }
                    Box(
                        Modifier.width(34.dp).fillMaxHeight().align(Alignment.CenterEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Dart wrapped the button in `_HistoryInteractiveTarget` so the
                        // tap never also toggles the row's selection; the button's own
                        // click consumes it here.
                        ButtonIcon(
                            modifier = Modifier.testTag(roomMessageHistoryJumpTag(message.id)),
                            tooltip = "跳转到消息",
                            icon = { Icon(Icons.Rounded.LocationOn) },
                            tone = ButtonTone.primary,
                            onPressed = widget.onJump,
                            size = 34.dp,
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (widget.selectionMode) {
                UiCheckbox(
                    modifier = Modifier.testTag(roomMessageHistorySelectTag(message.id)),
                    value = widget.selected,
                    onChanged = { _ -> widget.onToggleSelection() },
                    semanticLabel = "选择这条消息记录",
                )
                Spacer(Modifier.width(8.dp))
            }
            UiContextMenuTriggerRegion(
                modifier = Modifier.weight(1f),
                onTap = if (widget.selectionMode) widget.onToggleSelection else null,
                onTriggered = { position -> _showContextMenu(position) },
            ) {
                surface()
            }
        }
    }

    private fun _showContextMenu(position: DpOffset) {
        if (widget.selectionMode && !widget.selected) return
        val items = if (widget.selectionMode) widget.selectedMessages else listOf(widget.message)
        if (items.isEmpty()) return
        setState { _contextMenuActive = true }
        val onCopy = widget.onCopy
        val onDelete = widget.onDelete
        unawaited {
            try {
                showUiContextMenu(
                    context,
                    position = position,
                    sections =
                        buildList {
                            if (items.size == 1) {
                                add(
                                    UiContextMenuSection(
                                        listOf(
                                            UiContextMenuItem(
                                                label = "复制",
                                                shortcut = "Ctrl+C",
                                                onPressed = { unawaited { onCopy(items.single()) } },
                                            ),
                                        ),
                                    ),
                                )
                            }
                            add(
                                UiContextMenuSection(
                                    listOf(UiContextMenuItem(label = "删除", onPressed = { unawaited { onDelete(items) } })),
                                ),
                            )
                        },
                )
            } finally {
                if (mounted) setState { _contextMenuActive = false }
            }
        }
    }
}

private fun _historySenderName(user: UserSummary): String {
    val roomName = user.roomDisplayName?.trim()
    if (roomName != null && roomName.isNotEmpty()) return roomName
    val displayName = user.displayName.trim()
    if (displayName.isNotEmpty()) return displayName
    return user.username
}

private class _HistoryMemberFilterResult(val member: RoomMember?)

@Composable
private fun _HistoryMemberFilterDialog(
    roomId: String,
    currentUser: CurrentUser,
    members: List<RoomMember>,
    selectedMember: RoomMember?,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
    onOpenRoom: ((PublicRoom) -> Unit)?,
) = StatefulWidget(
    _HistoryMemberFilterDialogWidget(
        roomId, currentUser, members, selectedMember, onResolveRoomProfile, onResolveRoomUserProfile, onOpenRoom,
    ),
) { _HistoryMemberFilterDialogState() }

private class _HistoryMemberFilterDialogWidget(
    val roomId: String,
    val currentUser: CurrentUser,
    val members: List<RoomMember>,
    val selectedMember: RoomMember?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onResolveRoomUserProfile: RoomUserProfileResolver?,
    val onOpenRoom: ((PublicRoom) -> Unit)?,
)

private class _HistoryMemberFilterDialogState : ViewState<_HistoryMemberFilterDialogWidget>() {
    private val _controller = TextEditingController()
    private var _selectedMember: RoomMember? = null
    private val _searchListener: () -> Unit = { setState {} }

    override fun initState() {
        _selectedMember = widget.selectedMember
        _controller.addListener(_searchListener)
    }

    override fun dispose() {
        _controller.removeListener(_searchListener)
        _controller.dispose()
    }

    private val _visibleMembers: List<RoomMember>
        get() {
            val query = _controller.text.trim()
            return widget.members
                .filter { member -> roomMemberSearchRank(member, query) < 99 }
                .sortedWith { a, b ->
                    val rank = roomMemberSearchRank(a, query).compareTo(roomMemberSearchRank(b, query))
                    if (rank != 0) {
                        rank
                    } else {
                        roomMemberDisplayName(a).lowercase().compareTo(roomMemberDisplayName(b).lowercase())
                    }
                }
        }

    @Composable
    override fun build() {
        val visibleMembers = _visibleMembers
        val viewportHeight = (MediaQuery.sizeOf(context).height * 0.58f).coerceIn(240.dp, 560.dp)
        val listState = rememberLazyListState()
        DialogFrame(
            title = "筛选消息成员",
            icon = Icons.Outlined.PersonSearch,
            maxWidth = 620.dp,
            actionBar = {
                ResponsiveDialogActionBar(
                    leadingActionCount = 1,
                    actions =
                        listOf(
                            ResponsiveDialogAction(
                                buttonKey = messageHistoryMemberResetTag,
                                label = "重置",
                                icon = Icons.Filled.RestartAlt,
                                onPressed = { Navigator.of(context).pop(_HistoryMemberFilterResult(null)) },
                            ),
                            ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                            ResponsiveDialogAction(
                                buttonKey = messageHistoryMemberApplyTag,
                                label = "应用",
                                icon = Icons.Filled.Check,
                                tone = ButtonTone.primary,
                                onPressed = { Navigator.of(context).pop(_HistoryMemberFilterResult(_selectedMember)) },
                            ),
                        ),
                )
            },
            child = {
                Column(Modifier.fillMaxWidth().height(viewportHeight)) {
                    Input(
                        modifier = Modifier.fillMaxWidth().testTag(messageHistoryMemberSearchTag),
                        controller = _controller,
                        hintText = "搜索成员",
                        prefixIcon = Icons.Filled.Search,
                        showClearButton = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .rawScrollbar(
                                rememberScrollbarAdapter(listState),
                                thickness = 7.dp,
                                radius = 999.dp,
                                thumbColor = UiColors.textMuted.copy(alpha = 0.82f),
                                interactive = true,
                            ),
                        state = listState,
                        contentPadding = PaddingValues(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(count = visibleMembers.size + 1, key = { index -> if (index == 0) "all" else visibleMembers[index - 1].user.id }) { index ->
                            if (index == 0) {
                                _HistoryMemberOption(
                                    label = "所有人",
                                    selected = _selectedMember == null,
                                    icon = Icons.Outlined.Groups,
                                    onPressed = { setState { _selectedMember = null } },
                                )
                                return@items
                            }
                            val member = visibleMembers[index - 1]
                            val resolver = widget.onResolveRoomUserProfile
                            _HistoryMemberOption(
                                label = roomMemberDisplayName(member),
                                meta = "@${member.user.username}",
                                roleLabel = roomRoleLabel(member.user),
                                selected = _selectedMember?.user?.id == member.user.id,
                                user = member.user,
                                currentUser = widget.currentUser,
                                onResolveProfile = if (resolver == null) null else ({ user -> resolver(widget.roomId, user) }),
                                onResolveRoomProfile = widget.onResolveRoomProfile,
                                onOpenRoom = widget.onOpenRoom,
                                onPressed = { setState { _selectedMember = member } },
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun _HistoryMemberOption(
    label: String,
    selected: Boolean,
    onPressed: () -> Unit,
    meta: String? = null,
    roleLabel: String? = null,
    user: UserSummary? = null,
    icon: ImageVector? = null,
    currentUser: CurrentUser? = null,
    onResolveProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onOpenRoom: ((PublicRoom) -> Unit)? = null,
) {
    val context = rememberBuildContext()
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = selected || hovered
    val animation = tween<Color>(90, easing = Curves.easeOutCubic)
    val background by animateColorAsState(if (highlighted) UiColors.selected else UiColors.surfaceLow, animation, label = "historyMemberBackground")
    val border by animateColorAsState(if (highlighted) UiColors.selectedBorder else UiColors.border, animation, label = "historyMemberBorder")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag(messageHistoryMemberTag(user?.id))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPressed)
            .decoration(background, RoundedCornerShape(UiRadii.md), border)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (user != null) {
            UserHoverCard(
                user = user,
                currentUser = currentUser,
                onResolveProfile = onResolveProfile,
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterCommonRoom = onOpenRoom,
                showRoomRole = true,
            ) {
                Avatar(
                    modifier = Modifier.testTag(messageHistoryMemberAvatarTag(user.id)),
                    label = userAvatarLabel(user),
                    imageUrl = AppConfigScope.of(context).resolveAssetUrl(user.avatarUrl),
                    defaultAvatarKey = user.defaultAvatarKey,
                    size = 36.dp,
                )
            }
        } else {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (icon != null) Icon(icon, color = UiColors.textSecondary, size = 20.dp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AdaptiveHighlightedText(
                    text = label,
                    query = "",
                    comfortableLines = 1,
                    style = UiTypography.body.copyWith(color = UiColors.text, fontWeight = FontWeight.W700),
                )
                if (meta != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = UiTypography.label.copy(color = UiColors.textMuted),
                    )
                }
            }
            if (roleLabel != null) {
                Spacer(Modifier.width(8.dp))
                RoleBadge(
                    label = roleLabel,
                    padding = EdgeInsets.symmetric(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                color = if (selected) UiColors.accent else UiColors.textMuted,
                size = 20.dp,
            )
        }
    }
}

@Composable
private fun _HistoryDeleteConfirmDialog(dialogContext: com.gangchat.client.widgets.BuildContext, messageCount: Int) {
    DialogFrame(
        title = if (messageCount == 1) "删除消息记录" else "删除选中的消息记录",
        icon = Icons.Outlined.WarningAmber,
        adaptiveActions =
            listOf(
                ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(dialogContext).pop(false) }),
                ResponsiveDialogAction(label = "删除", tone = ButtonTone.danger, onPressed = { Navigator.of(dialogContext).pop(true) }),
            ),
        child = {
            Text(
                if (messageCount == 1) {
                    "删除后仅会从当前账号看到的消息记录中移除，不会撤回或删除房间消息"
                } else {
                    "确定删除所有选中的 $messageCount 条消息记录吗？删除只对当前账号生效"
                },
                style = UiTypography.body,
            )
        },
    )
}

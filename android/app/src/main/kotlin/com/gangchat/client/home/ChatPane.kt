package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.ComposerAttachmentView
import com.gangchat.client.app.FileTransferState
import com.gangchat.client.app.MessageMentionOption
import com.gangchat.client.app.StickerPanelLoadState
import com.gangchat.client.app.StickerPanelSource
import com.gangchat.client.app.VoiceRecorderState
import com.gangchat.client.app.roomAvatarLabel
import com.gangchat.client.app.roomCardAvatarLabel
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.ChatComposerController
import com.gangchat.client.ui.ComposerSuggestionAction
import com.gangchat.client.ui.ComposerSuggestionNavigation
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.kDefaultAvatarPresetKey
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.TextInputFormatter

// Port of lib/src/home/chat_pane.dart. Its parts are ported as
// ChatHeader.kt, ChatMessages*.kt, ChatComposerDock.kt and ChatProfileCard.kt.

internal val _chatHeaderHeight: Dp = 111.dp
internal val _chatHorizontalPadding: Dp = 18.dp
internal val _chatFloatingEdgeInset: Dp = 14.dp
internal val _chatHeaderHorizontalInset: Dp = _chatFloatingEdgeInset
internal val _chatHeaderVisualTopInset: Dp = _chatHeaderHorizontalInset
internal val _chatHeaderBottomInset: Dp = 16.dp
internal val _liveHeaderCardHeight: Dp = 70.dp
internal val _headerActionButtonSize: Dp = 31.dp
internal val _headerActionGap: Dp = 8.dp
internal val _messageMaxWidth: Dp = 560.dp

// Breathing room below the last message before the composer row begins.
internal val _messageListBottomInset: Dp = 14.dp
internal val _composerHorizontalInset: Dp = _chatFloatingEdgeInset
internal val _composerBottomInset: Dp = _chatFloatingEdgeInset
internal val _outgoingBubble: Color = Color(0xFF1F352B)
internal val _incomingBubble: Color = UiColors.surface

/**
 * The room chat pane: the live-channel header card, the message list and
 * (once the room detail is loaded) the composer dock.
 *
 * Dart's `composerDropKey` (the desktop drag-and-drop target) is not ported.
 */
@Composable
fun ChatPane(
    currentUser: CurrentUser,
    timestampNow: DateTime,
    roomCard: RoomCard?,
    room: RoomDetail?,
    live: LiveState?,
    messages: List<Message>,
    newMessageCount: Int,
    fileTransfers: Map<String, FileTransferState>,
    fileDownloads: Map<String, FileTransferState>,
    downloadActions: ChatFileDownloadActions,
    voicePlaybackActions: ChatVoicePlaybackActions,
    imagePreviewActions: ChatImagePreviewActions,
    loading: Boolean,
    error: String?,
    sending: Boolean,
    sendError: String?,
    composerController: TextEditingController,
    composerPanelController: ChatComposerController,
    stickerPanel: StickerPanelLoadState,
    voiceState: VoiceRecorderState,
    composerAttachments: List<ComposerAttachmentView>,
    fileActionHighlighted: Boolean,
    onSubmit: (String) -> Unit,
    onSendSticker: (Sticker) -> Unit,
    onLoadStickers: () -> Unit,
    onRefreshStickers: () -> Unit,
    onStickerSourceChanged: (StickerPanelSource) -> Unit,
    onStartVoice: () -> Unit,
    onSendVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onPickFile: () -> Unit,
    onPasteFiles: suspend () -> Boolean,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenLiveChannel: () -> Unit,
    onOpenRoomMembers: () -> Unit,
    onOpenRoomSettings: () -> Unit,
    onViewedNewMessages: () -> Unit,
    modifier: Modifier = Modifier,
    focusMessageId: String? = null,
    onFocusMessageHandled: ((String) -> Unit)? = null,
    messageActions: ChatMessageActions = ChatMessageActions.disabled(),
    hasPendingJoinRequests: Boolean = false,
    composerQuotes: List<MessageQuote> = emptyList(),
    onRemoveComposerQuote: ((String) -> Unit)? = null,
    composerComponent: Message? = null,
    onRemoveComposerComponent: (() -> Unit)? = null,
    mentionOptions: List<MessageMentionOption> = emptyList(),
    mentionMembers: List<RoomMember> = emptyList(),
    mentionMembersReady: Boolean = true,
    mentionLoading: Boolean = false,
    mentionSelectedIndex: Int = 0,
    onSelectMention: ((MessageMentionOption) -> Unit)? = null,
    composerInputFormatters: List<TextInputFormatter>? = null,
    onNavigateMentionSelection: ((ComposerSuggestionNavigation) -> Boolean)? = null,
    onConfirmMentionSelection: ((ComposerSuggestionAction) -> Boolean)? = null,
    onHighlightMentionSelection: ((Int) -> Unit)? = null,
    onUploadFirstPersonalSticker: (() -> Unit)? = null,
    onUploadFirstRoomSticker: (() -> Unit)? = null,
    onCanPasteFiles: (suspend () -> Boolean)? = null,
    onNavigateBack: (() -> Unit)? = null,
    /**
     * Resolves a richer profile for a message sender on demand (gender, common
     * rooms). The hover card calls this lazily; when null it shows just the
     * lightweight summary carried by the message.
     */
    onResolveSenderProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    senderProfileActionBuilder: UserProfileActionBuilder? = null,
    onMentionUser: ((UserSummary) -> Unit)? = null,
) {
    val title = _roomTitle(room, roomCard)
    val avatarLabel = _roomAvatarLabel(room, roomCard)
    val avatarUrl = room?.avatarUrl ?: roomCard?.avatarUrl
    val defaultAvatarKey = room?.defaultAvatarKey ?: roomCard?.defaultAvatarKey ?: kDefaultAvatarPresetKey
    val liveParticipantCount = live?.participantCount ?: room?.live?.participantCount ?: roomCard?.liveParticipantCount
    val liveAvatarPreview =
        live?.participants?.map { it.user }?.take(5)
            ?: room?.live?.participants?.map { it.user }?.take(5)
            ?: roomCard?.liveAvatarPreview
            ?: emptyList()
    val roomReady = room != null
    val stageKey = "message-stage-${room?.id ?: roomCard?.id ?: "none"}"

    Column(modifier.fillMaxSize().background(UiColors.background)) {
        _RoomHeader(
            onNavigateBack = onNavigateBack,
            title = title,
            avatarLabel = avatarLabel,
            avatarUrl = avatarUrl,
            defaultAvatarKey = defaultAvatarKey,
            memberCount = room?.memberCount ?: roomCard?.memberCount,
            onlineMemberCount = room?.onlineMemberCount ?: roomCard?.onlineMemberCount,
            liveParticipantCount = liveParticipantCount,
            liveAvatarPreview = liveAvatarPreview,
            hasPendingJoinRequests = hasPendingJoinRequests,
            onLivePressed = onOpenLiveChannel,
            onMembersPressed = onOpenRoomMembers,
            onSettingsPressed = onOpenRoomSettings,
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            key(stageKey) {
                _MessageStage(
                    roomId = room?.id ?: roomCard?.id,
                    currentUser = currentUser,
                    currentUserRoomDisplayName = _currentUserRoomDisplayName(currentUser, room),
                    currentUserRoomRole = room?.myMembership?.role,
                    ownerUserId = room?.createdBy?.id,
                    roomReady = roomReady,
                    loading = loading,
                    error = error,
                    timestampNow = timestampNow,
                    messages = messages,
                    newMessageCount = newMessageCount,
                    focusMessageId = focusMessageId,
                    onFocusMessageHandled = onFocusMessageHandled,
                    mentionMembers = mentionMembers,
                    mentionMembersReady = mentionMembersReady,
                    fileTransfers = fileTransfers,
                    fileDownloads = fileDownloads,
                    live = live,
                    downloadActions = downloadActions,
                    voicePlaybackActions = voicePlaybackActions,
                    imagePreviewActions = imagePreviewActions,
                    messageActions = messageActions,
                    onRetry = onRetry,
                    onViewedNewMessages = onViewedNewMessages,
                    bottomInset = _messageListBottomInset,
                    onResolveSenderProfile = onResolveSenderProfile,
                    onResolveRoomProfile = onResolveRoomProfile,
                    onEnterProfileRoom = onEnterProfileRoom,
                    senderProfileActionBuilder = senderProfileActionBuilder,
                    onMentionUser = onMentionUser,
                )
            }
        }
        if (roomReady) {
            // Dart detached the dock from the app-wide SelectionArea
            // (`SelectionContainer.disabled`); the Compose app has none.
            _ComposerDock(
                controller = composerController,
                composerController = composerPanelController,
                timestampNow = timestampNow,
                sending = sending,
                sendError = sendError,
                stickerPanel = stickerPanel,
                voiceState = voiceState,
                attachments = composerAttachments,
                quotes = composerQuotes,
                onRemoveQuote = onRemoveComposerQuote,
                component = composerComponent,
                onRemoveComponent = onRemoveComposerComponent,
                imagePreviewActions = imagePreviewActions,
                fileActionHighlighted = fileActionHighlighted,
                mentionOptions = mentionOptions,
                mentionLoading = mentionLoading,
                mentionSelectedIndex = mentionSelectedIndex,
                onSelectMention = onSelectMention,
                inputFormatters = composerInputFormatters,
                onNavigateMentionSelection = onNavigateMentionSelection,
                onConfirmMentionSelection = onConfirmMentionSelection,
                onHighlightMentionSelection = onHighlightMentionSelection,
                onSubmit = onSubmit,
                onSendSticker = onSendSticker,
                onOpenStickers = onLoadStickers,
                onRefreshStickers = onRefreshStickers,
                onStickerSourceChanged = onStickerSourceChanged,
                onUploadFirstPersonalSticker = onUploadFirstPersonalSticker,
                onUploadFirstRoomSticker = onUploadFirstRoomSticker,
                onStartVoice = onStartVoice,
                onSendVoice = onSendVoice,
                onCancelVoice = onCancelVoice,
                onPickFile = onPickFile,
                onPasteFiles = onPasteFiles,
                onCanPasteFiles = onCanPasteFiles,
                onRemoveAttachment = onRemoveAttachment,
                onRetryAttachment = onRetryAttachment,
            )
        }
    }
}

private fun _currentUserRoomDisplayName(currentUser: CurrentUser, room: RoomDetail?): String {
    val roomName = room?.personalProfile?.displayName?.trim()
    if (roomName != null && roomName.isNotEmpty()) return roomName
    val displayName = currentUser.displayName.trim()
    if (displayName.isNotEmpty()) return displayName
    return currentUser.username
}

private fun _roomTitle(room: RoomDetail?, card: RoomCard?): String {
    val detailRemark = room?.remarkName?.trim()
    if (detailRemark != null && detailRemark.isNotEmpty()) return detailRemark
    val detailTitle = room?.name?.trim()
    if (detailTitle != null && detailTitle.isNotEmpty()) return detailTitle
    val cardTitle = card?.displayName?.trim()
    if (cardTitle != null && cardTitle.isNotEmpty()) return cardTitle
    return "聊天"
}

private fun _roomAvatarLabel(room: RoomDetail?, card: RoomCard?): String {
    if (room != null) return roomAvatarLabel(room)
    if (card != null) return roomCardAvatarLabel(card)
    return "聊天"
}

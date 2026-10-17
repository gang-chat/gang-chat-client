package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.ComposerAttachmentView
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.app.StickerPanelLoadState
import com.gangchat.client.app.VoiceRecorderState
import com.gangchat.client.config.AppConfig
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.AppNotificationHost
import com.gangchat.client.ui.ChatComposerController
import com.gangchat.client.ui.MediaCacheScope
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTheme
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.WindowMediaQuery
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// Fixtures of test/widget/home/chat_message_rendering_test.dart.

const val chatTestCurrentUserId = "current_user"

val chatTestCurrentUser =
    CurrentUser(
        id = chatTestCurrentUserId,
        uid = "1000000",
        username = "me",
        displayName = "Me",
        bio = "",
        gender = "secret",
        email = null,
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "blue-3",
        isSuperuser = false,
        createdAt = null,
    )

val chatTestSystemTarget = UserSummary(id = "user_target", username = "logan", displayName = "Logan", avatarUrl = null, defaultAvatarKey = "green-2")

val chatTestSystemActor = UserSummary(id = "user_actor", username = "owner", displayName = "Owner", avatarUrl = null, defaultAvatarKey = "blue-3")

val chatTestDefaultSender = UserSummary(id = "user_1", username = "logan", displayName = "Logan", avatarUrl = null, defaultAvatarKey = "blue-3")

val chatTestRoomCard =
    RoomCard(
        id = "room_1",
        name = "Test room",
        avatarUrl = null,
        defaultAvatarKey = "room-1",
        memberCount = 2,
        liveParticipantCount = 0,
        liveAvatarPreview = emptyList(),
        lastMessage = null,
        unreadCount = 0,
        updatedAt = DateTime(2026, 6, 12),
    )

fun chatTestRoomDetailFor(id: String): RoomDetail =
    RoomDetail(
        id = id,
        name = "Test room",
        avatarUrl = null,
        defaultAvatarKey = "room-1",
        memberCount = 2,
        myMembership = RoomMembership(joinedAt = DateTime.utc(2026, 6, 4), role = "member"),
        live = LiveState(roomId = id, participantCount = 0, participants = emptyList(), updatedAt = DateTime.utc(2026, 6, 4)),
        createdAt = DateTime.utc(2026, 6, 4),
        updatedAt = DateTime.utc(2026, 6, 4),
    )

val chatTestRoomDetail: RoomDetail = chatTestRoomDetailFor("room_1")

fun chatTestMessage(
    type: String,
    id: String = "message_1",
    body: String = "",
    attachments: List<MessageAttachment> = emptyList(),
    createdAt: DateTime? = null,
    clientMessageId: String = "client_1",
    pending: Boolean = false,
    isRecalled: Boolean = false,
    recalledBy: UserSummary? = null,
    isForceDeleted: Boolean = false,
    forceDeletedBy: UserSummary? = null,
    mentions: List<JsonMap> = emptyList(),
    quote: MessageQuote? = null,
    quotes: List<MessageQuote> = emptyList(),
    sender: UserSummary = chatTestDefaultSender,
): Message =
    Message(
        id = id,
        roomId = "room_1",
        sender = sender,
        clientMessageId = clientMessageId,
        type = type,
        body = body,
        createdAt = createdAt ?: DateTime.utc(2026, 6, 11),
        attachments = attachments,
        mentions = mentions,
        quote = quote,
        quotes = quotes,
        isRecalled = isRecalled,
        recalledBy = recalledBy,
        isForceDeleted = isForceDeleted,
        forceDeletedBy = forceDeletedBy,
        pending = pending,
    )

fun chatTestTextMessages(count: Int): List<Message> =
    List(count) { index ->
        chatTestMessage(
            type = "text",
            id = "message_$index",
            body = "Message $index",
            clientMessageId = "client_$index",
            createdAt = DateTime.utc(2026, 6, 11, 9).add(index.minutes),
        )
    }

fun chatTestStickerMessage(): Message =
    chatTestMessage(
        type = "sticker",
        body = "[表情] wave",
        attachments =
            listOf(
                MessageAttachment(
                    type = "sticker",
                    stickerId = "sticker_1",
                    name = "wave",
                    asset = UploadedAsset(id = "asset_sticker", url = "/stickers/wave.webp", thumbnailUrl = "/stickers/wave-thumb.webp", mimeType = "image/webp"),
                ),
            ),
    )

fun chatTestVoiceMessage(duration: Duration): Message =
    chatTestMessage(
        type = "audio",
        body = "voice_1.m4a",
        attachments =
            listOf(
                MessageAttachment(
                    type = "audio",
                    name = "voice_1.m4a",
                    durationMs = duration.inWholeMilliseconds.toInt(),
                    asset =
                        UploadedAsset(
                            id = "asset_voice",
                            url = "/uploads/voice_1.m4a",
                            thumbnailUrl = null,
                            mimeType = "audio/mp4",
                            filename = "voice_1.m4a",
                            sizeBytes = 4096,
                        ),
                ),
            ),
    )

fun chatTestDownloadActions(): ChatFileDownloadActions =
    ChatFileDownloadActions(onDownload = { _, _, _, _ -> }, onPause = {}, onResume = {}, onCancel = {}, onDismiss = {})

fun chatTestMessageActions(
    onReeditRecalledText: ((Message) -> Unit)? = null,
    canReeditRecalledText: ((Message) -> Boolean)? = null,
    canInspectRecalledText: ((Message) -> Boolean)? = null,
): ChatMessageActions =
    ChatMessageActions(
        onCopy = { _, _ -> },
        onDeleteForMe = { _, _ -> },
        onRecall = { _, _ -> },
        canRecall = { false },
        onReeditRecalledText = onReeditRecalledText ?: {},
        canReeditRecalledText = canReeditRecalledText ?: { false },
        canInspectRecalledText = canInspectRecalledText ?: { false },
    )

val chatTestAppConfig =
    AppConfig(
        apiBaseUrl = "https://api.test/api/v1",
        assetBaseUrl = "https://assets.test",
        releaseBucketUrl = "https://releases.test/gang-chat",
    )

/** Dart's `_chatPane(...)`: a [ChatPane] with the test's fixed callbacks. */
@Composable
fun ChatPaneForTest(
    controller: TextEditingController,
    messages: List<Message>,
    room: RoomDetail? = null,
    live: LiveState? = null,
    loading: Boolean = false,
    newMessageCount: Int = 0,
    focusMessageId: String? = null,
    onFocusMessageHandled: ((String) -> Unit)? = null,
    onResolveSenderProfile: UserProfileResolver? = null,
    onEnterProfileRoom: ((PublicRoom) -> Unit)? = null,
    senderProfileActionBuilder: UserProfileActionBuilder? = null,
    messageActions: ChatMessageActions = ChatMessageActions.disabled(),
    mentionMembers: List<RoomMember> = emptyList(),
    mentionMembersReady: Boolean = true,
    timestampNow: DateTime? = null,
    onViewedNewMessages: (() -> Unit)? = null,
    composerQuotes: List<MessageQuote> = emptyList(),
    onRemoveComposerQuote: ((String) -> Unit)? = null,
    composerComponent: Message? = null,
    onRemoveComposerComponent: (() -> Unit)? = null,
    imagePreviewActions: ChatImagePreviewActions = ChatImagePreviewActions.disabled(),
    roomCard: RoomCard = chatTestRoomCard,
    composerPanelController: ChatComposerController = ChatComposerController(),
    stickerPanel: StickerPanelLoadState = StickerPanelLoadState(),
    voiceState: VoiceRecorderState = VoiceRecorderState(),
    composerAttachments: List<ComposerAttachmentView> = emptyList(),
    voicePlaybackActions: ChatVoicePlaybackActions = ChatVoicePlaybackActions.disabled(),
    onNavigateBack: (() -> Unit)? = null,
    hasPendingJoinRequests: Boolean = false,
    modifier: Modifier = Modifier,
) {
    ChatPane(
        currentUser = chatTestCurrentUser,
        timestampNow = timestampNow ?: DateTime.now(),
        roomCard = roomCard,
        room = room,
        live = live,
        messages = messages,
        newMessageCount = newMessageCount,
        focusMessageId = focusMessageId,
        onFocusMessageHandled = onFocusMessageHandled,
        fileTransfers = emptyMap(),
        fileDownloads = emptyMap(),
        downloadActions = chatTestDownloadActions(),
        voicePlaybackActions = voicePlaybackActions,
        imagePreviewActions = imagePreviewActions,
        messageActions = messageActions,
        loading = loading,
        error = null,
        sending = false,
        sendError = null,
        composerController = controller,
        composerPanelController = composerPanelController,
        stickerPanel = stickerPanel,
        voiceState = voiceState,
        composerAttachments = composerAttachments,
        composerQuotes = composerQuotes,
        onRemoveComposerQuote = onRemoveComposerQuote,
        composerComponent = composerComponent,
        onRemoveComposerComponent = onRemoveComposerComponent,
        fileActionHighlighted = false,
        mentionMembers = mentionMembers,
        mentionMembersReady = mentionMembersReady,
        onSubmit = {},
        onSendSticker = {},
        onLoadStickers = {},
        onRefreshStickers = {},
        onStickerSourceChanged = {},
        onStartVoice = {},
        onSendVoice = {},
        onCancelVoice = {},
        onPickFile = {},
        onPasteFiles = { false },
        onRemoveAttachment = {},
        onRetryAttachment = {},
        onRetry = {},
        onOpenLiveChannel = {},
        onOpenRoomMembers = {},
        onOpenRoomSettings = {},
        onViewedNewMessages = onViewedNewMessages ?: {},
        onResolveSenderProfile = onResolveSenderProfile,
        onEnterProfileRoom = onEnterProfileRoom,
        senderProfileActionBuilder = senderProfileActionBuilder,
        onNavigateBack = onNavigateBack,
        hasPendingJoinRequests = hasPendingJoinRequests,
        modifier = modifier,
    )
}

/** Test tag of the page behind the chat host (Dart's `Scaffold`). */
const val chatTestPageTag = "chat-test-page"

/**
 * Dart's `_host(child, height:)`: the app theme and config around
 * `Scaffold(body: Center(child: SizedBox(width: 600, height:, child)))`.
 * [content] is the `child`; its slot is [width] wide and [height] tall (the
 * available height when null).
 */
fun ComposeContentTestRule.setChatContent(
    height: Dp? = null,
    width: Dp = 600.dp,
    cache: MediaCacheController = testMediaCacheController(),
    compact: Boolean? = null,
    content: @Composable (BuildContext) -> Unit,
) {
    setContent {
        UiTheme {
            WindowMediaQuery {
                AppConfigScope(chatTestAppConfig) {
                    MediaCacheScope(cache) {
                        AppNotificationHost {
                            RootNavigator { context ->
                                Box(
                                    Modifier.fillMaxSize().background(UiColors.background).testTag(chatTestPageTag),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val slot = Modifier.width(width).then(if (height != null) Modifier.height(height) else Modifier)
                                    // SizedBox: a tight width (and height when given).
                                    Box(slot, propagateMinConstraints = true) {
                                        if (compact == null) content(context) else HomeAdaptiveLayout(compact) { content(context) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    waitForIdle()
}

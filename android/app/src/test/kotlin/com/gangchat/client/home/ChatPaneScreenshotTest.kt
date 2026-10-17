package com.gangchat.client.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.StickerPanelLoadState
import com.gangchat.client.app.kSystemEventLiveJoined
import com.gangchat.client.app.voiceRecordingStarted
import com.gangchat.client.app.voiceRecordingTicked
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.SharedMusicPlaylist
import com.gangchat.client.protocol.SharedMusicTrack
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.solidPng
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.widgets.TextEditingController
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Duration.Companion.seconds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val out = "build/outputs/roborazzi"

/** Chat pane galleries: phone (compact) and tablet (wide) layouts, the message menu and the composer panels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class ChatPaneScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installLoader() = installTestImageLoader()

    private val now = DateTime(2026, 6, 11, 15, 30)

    private val alice = UserSummary(id = "u_alice", username = "alice", displayName = "Alice", avatarUrl = null, defaultAvatarKey = "rose-2", roomRole = "admin")
    private val david = UserSummary(id = "u_david", username = "david", displayName = "大卫", avatarUrl = null, defaultAvatarKey = "green-2", roomRole = "member")
    private val grace = UserSummary(id = "u_grace", username = "grace", displayName = "Grace", avatarUrl = null, defaultAvatarKey = "violet-2", roomRole = "owner")
    private val me = chatTestCurrentUser.toSummary()

    private val cache =
        testMediaCacheController(
            files =
                mapOf(
                    "https://assets.test/uploads/photo-thumb.png" to solidPng(320, 180, 0xFF3A6EA5.toInt()),
                    "https://assets.test/stickers/wave-thumb.webp" to solidPng(120, 120, 0xFFE0A12A.toInt()),
                    "https://assets.test/stickers/s1.png" to solidPng(64, 64, 0xFF26B36F.toInt()),
                    "https://assets.test/stickers/s2.png" to solidPng(64, 64, 0xFFE25A5A.toInt()),
                    "https://assets.test/stickers/s3.png" to solidPng(64, 64, 0xFF5CB8FF.toInt()),
                ),
        )

    private fun at(hour: Int, minute: Int) = DateTime(2026, 6, 11, hour, minute)

    private fun message(
        id: String,
        sender: UserSummary,
        type: String,
        body: String,
        createdAt: DateTime,
        attachments: List<MessageAttachment> = emptyList(),
        quote: MessageQuote? = null,
        isRecalled: Boolean = false,
    ) = Message(
        id = id,
        roomId = "room_1",
        sender = sender,
        clientMessageId = "client_$id",
        type = type,
        body = body,
        createdAt = createdAt,
        attachments = attachments,
        quote = quote,
        isRecalled = isRecalled,
    )

    private val mixedMessages: List<Message>
        get() =
            listOf(
                message("m1", alice, "text", "大家早！今天的站会改到 10:30", at(9, 2)),
                message("m2", david, "text", "收到 👍 顺便看看 https://example.com/docs", at(9, 3)),
                Message(
                    id = "m3",
                    roomId = "room_1",
                    sender = alice,
                    clientMessageId = "client_m3",
                    type = "system",
                    body = "",
                    createdAt = at(10, 5),
                    attachments = listOf(MessageAttachment(type = "system", event = kSystemEventLiveJoined, target = alice)),
                ),
                message(
                    "m4",
                    grace,
                    "file",
                    "photo.png",
                    at(11, 2),
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "file",
                                name = "photo.png",
                                asset =
                                    UploadedAsset(
                                        id = "a_photo",
                                        url = "/uploads/photo.png",
                                        thumbnailUrl = "/uploads/photo-thumb.png",
                                        mimeType = "image/png",
                                        filename = "photo.png",
                                        sizeBytes = 204800,
                                        width = 1600,
                                        height = 900,
                                    ),
                            ),
                        ),
                ),
                message(
                    "m5",
                    david,
                    "file",
                    "用户反馈汇总.csv",
                    at(11, 18),
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "file",
                                name = "用户反馈汇总.csv",
                                asset = UploadedAsset(id = "a_csv", url = "/uploads/feedback.csv", thumbnailUrl = null, mimeType = "text/csv", sizeBytes = 135),
                            ),
                        ),
                ),
                message(
                    "m6",
                    alice,
                    "audio",
                    "voice.m4a",
                    at(12, 6),
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "audio",
                                name = "voice.m4a",
                                durationMs = 8000,
                                asset = UploadedAsset(id = "a_voice", url = "/uploads/voice.m4a", thumbnailUrl = null, mimeType = "audio/mp4"),
                            ),
                        ),
                ),
                message(
                    "m7",
                    me,
                    "text",
                    "我来回复一下",
                    at(13, 15),
                    quote = MessageQuote(messageId = "m1", senderDisplayName = "Alice", body = "大家早！今天的站会改到 10:30", createdAt = at(9, 2)),
                ),
                message(
                    "m8",
                    david,
                    "sticker",
                    "[表情] wave",
                    at(13, 16),
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "sticker",
                                stickerId = "st_wave",
                                name = "wave",
                                asset = UploadedAsset(id = "a_wave", url = "/stickers/wave.webp", thumbnailUrl = "/stickers/wave-thumb.webp", mimeType = "image/webp"),
                            ),
                        ),
                ),
                message(
                    "m9",
                    grace,
                    "playlist",
                    "[歌单] 电台精选",
                    at(14, 3),
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "playlist",
                                playlistId = "pl_1",
                                playlist =
                                    SharedMusicPlaylist(
                                        id = "pl_1",
                                        name = "电台精选",
                                        description = "",
                                        itemCount = 8,
                                        createdAt = at(8, 0),
                                        creator = grace,
                                        items =
                                            listOf(
                                                PersonalMusicPlaylistItem(
                                                    id = "pi_1",
                                                    playlistId = "pl_1",
                                                    trackId = "t1",
                                                    source = "netease",
                                                    title = "平凡之路",
                                                    artists = listOf("朴树"),
                                                    durationMs = 301000,
                                                    sortOrder = 10,
                                                    createdAt = null,
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                ),
                message(
                    "m10",
                    alice,
                    "music_track",
                    "[歌曲] 平凡之路",
                    at(14, 5),
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "music_track",
                                track =
                                    SharedMusicTrack(
                                        id = "tr_1",
                                        playlistId = "pl_1",
                                        trackId = "t1",
                                        source = "netease",
                                        title = "平凡之路（电影《后会无期》主题曲）",
                                        artists = listOf("朴树"),
                                        durationMs = 301000,
                                    ),
                            ),
                        ),
                ),
                message("m11", david, "text", "这条消息被撤回了", at(14, 20), isRecalled = true),
                message("m12", alice, "text", "谁把会议室的投屏线拿走了 😂", at(15, 16)),
                message("m13", me, "text", "Electron 版本测试通过了，明天发布", at(15, 21)),
            )

    private val live =
        LiveState(
            roomId = "room_1",
            participantCount = 3,
            participants =
                listOf(alice, david, grace).mapIndexed { index, user ->
                    LiveParticipant(
                        liveSessionId = "live_$index",
                        user = user,
                        joinedAt = at(15, index),
                        micMuted = false,
                        headphonesMuted = false,
                        voiceBlocked = false,
                        cameraOn = false,
                        screenSharing = false,
                        connectionState = "connected",
                    )
                },
            updatedAt = at(15, 0),
        )

    // Distinct ids per test: the list remembers its scroll position per room
    // in a static map, like Dart.
    private fun room(id: String) = chatTestRoomDetail.let {
        com.gangchat.client.protocol.RoomDetail(
            id = id,
            name = "Gang 总部",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            memberCount = 10,
            myMembership = it.myMembership,
            live = live,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt,
            onlineMemberCount = 6,
        )
    }

    private val stickers =
        StickerPanelLoadState(
            loaded = true,
            personalPacks =
                listOf(
                    StickerPack(
                        id = "pack",
                        scope = "personal",
                        roomId = null,
                        name = "我的表情包",
                        sortOrder = 10,
                        updatedAt = null,
                        stickers =
                            (1..3).map { index ->
                                Sticker(
                                    id = "s$index",
                                    name = "表情$index",
                                    sortOrder = index * 10,
                                    asset = UploadedAsset(id = "as$index", url = "/stickers/s$index.png", thumbnailUrl = null, mimeType = "image/png"),
                                )
                            },
                    ),
                ),
        )

    private fun setChat(compact: Boolean, newMessageCount: Int = 0, voiceRecording: Boolean = false, roomId: String = "room_1") {
        val controller = TextEditingController()
        compose.setChatContent(width = if (compact) 411.dp else 1032.dp, cache = cache, compact = compact) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = room(roomId),
                live = live,
                messages = mixedMessages,
                newMessageCount = newMessageCount,
                timestampNow = now,
                imagePreviewActions = ChatImagePreviewActions.disabled(),
                stickerPanel = stickers,
                voiceState = if (voiceRecording) voiceRecordingTicked(voiceRecordingStarted(), 7.seconds) else com.gangchat.client.app.VoiceRecorderState(),
                onNavigateBack = if (compact) ({}) else null,
                hasPendingJoinRequests = true,
            )
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    @Test
    fun phoneChat() {
        setChat(compact = true, newMessageCount = 2)
        compose.onRoot().captureRoboImage("$out/chat_phone.png")
    }

    @Test
    fun phoneChatHistory() {
        setChat(compact = true, roomId = "room_history")
        compose.onNodeWithTag(chatMessageListTag).performScrollToIndex(mixedMessages.size - 1)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/chat_phone_history.png")
    }

    @Test
    fun phoneMessageMenu() {
        setChat(compact = true)
        compose.onNodeWithText("Electron 版本测试通过了，明天发布").performTouchInput { longClick(center) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/chat_phone_message_menu.png")
    }

    @Test
    fun phoneStickerPanel() {
        setChat(compact = true)
        compose.onTooltip("表情").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/chat_phone_sticker_panel.png")
    }

    @Test
    fun phoneVoicePanel() {
        setChat(compact = true, voiceRecording = true)
        compose.onTooltip("语音").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/chat_phone_voice_panel.png")
    }

    @Test
    @Config(qualifiers = "w1032dp-h800dp-mdpi")
    fun tabletChat() {
        setChat(compact = false, newMessageCount = 2)
        compose.onRoot().captureRoboImage("$out/chat_tablet.png")
    }
}

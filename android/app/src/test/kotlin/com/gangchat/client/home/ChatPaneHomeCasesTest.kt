package com.gangchat.client.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MusicTrackPreviewAsset
import com.gangchat.client.app.MusicTrackPreviewController
import com.gangchat.client.app.MusicTrackPreviewPlatform
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.SharedMusicTrack
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.isTextField
import com.gangchat.client.widgets.TextEditingController
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The cases of test/widget/shell/gang_app_shell_test_parts/home_widget_tests.dart
 * that only need the chat pane; the rest need the home shell and are ported
 * with it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class ChatPaneHomeCasesTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installLoader() = installTestImageLoader()

    @Test
    fun `sending keeps an open message hover card stable`() {
        // Dart rested the mouse on the shared song while sending; on Android the
        // card is opened by a tap and the sent message is appended below it.
        val controller = TextEditingController()
        val trackMessage =
            chatTestMessage(
                type = "music_track",
                id = "message_track",
                clientMessageId = "client_track",
                body = "[歌曲] 分享歌曲完整名称",
                createdAt = DateTime.utc(2026, 6, 11, 9, 5),
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "music_track",
                            track =
                                SharedMusicTrack(
                                    id = "shared_track_1",
                                    playlistId = "playlist_1",
                                    trackId = "track_1",
                                    source = "netease",
                                    title = "分享歌曲完整名称",
                                    artists = listOf("歌手"),
                                    durationMs = 180000,
                                ),
                        ),
                    ),
            )
        var messages by mutableStateOf(chatTestTextMessages(3) + trackMessage)
        val actions =
            ChatMessageActions(
                onCopy = { _, _ -> },
                onDeleteForMe = { _, _ -> },
                onRecall = { _, _ -> },
                canRecall = { false },
                sharedTrackPreviewController = MusicTrackPreviewController(api = _ChatSendPreviewApi(), platform = _ChatSendPreviewPlatform()),
            )
        compose.setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(controller = controller, room = chatTestRoomDetailFor("room_hover_card_send"), messages = messages, messageActions = actions)
        }

        compose.onNodeWithTag(sharedMusicTrackMessageTag("shared_track_1")).performTouchInput { click(Offset(20.dp.toPx(), centerY)) }
        compose.waitForIdle()
        compose.onNodeWithTag(musicTrackCardPreviewTag).assertExists()

        messages =
            messages +
            chatTestMessage(
                type = "text",
                id = "message_sent",
                clientMessageId = "client_sent",
                body = "悬停卡片打开时发送",
                createdAt = DateTime.utc(2026, 6, 11, 9, 6),
                sender = chatTestCurrentUser.toSummary(),
            )
        compose.waitForIdle()

        compose.onNode(isTextField and hasText("悬停卡片打开时发送")).assertExists()
        compose.onNodeWithTag(musicTrackCardPreviewTag).assertExists()
    }
}

private class _ChatSendPreviewApi : MusicTrackPreviewApi {
    override suspend fun downloadMusicTrackPreview(source: String, trackId: String): DownloadedFile =
        DownloadedFile(bytes = byteArrayOf(1, 2, 3), filename = "preview.m4a", mimeType = "audio/mp4")
}

private class _ChatSendPreviewPlatform : MusicTrackPreviewPlatform {
    private val _completed: StreamController<Unit> = StreamController.broadcast()

    override val onCompleted: Stream<Unit> get() = _completed.stream

    override suspend fun findCached(cacheKey: String): MusicTrackPreviewAsset? = null

    override suspend fun store(cacheKey: String, bytes: ByteArray): MusicTrackPreviewAsset = MusicTrackPreviewAsset(cacheKey)

    override suspend fun play(asset: MusicTrackPreviewAsset) {}

    override suspend fun stop() {}

    override suspend fun dispose() = _completed.close()
}

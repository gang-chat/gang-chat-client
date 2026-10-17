package com.gangchat.client.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.formatChatTimestamp
import com.gangchat.client.app.formatDetailedChatTimestamp
import com.gangchat.client.app.formatMessageTime
import com.gangchat.client.app.kSystemEventLiveJoined
import com.gangchat.client.app.kSystemEventRoomDescriptionChanged
import com.gangchat.client.app.kSystemEventRoomJoinPolicyChanged
import com.gangchat.client.app.kSystemEventRoomMemberJoined
import com.gangchat.client.app.kSystemEventRoomNameChanged
import com.gangchat.client.app.kSystemEventRoomRoleChanged
import com.gangchat.client.app.kSystemEventRoomVisibilityChanged
import com.gangchat.client.app.kVoiceWaveformMaxWidth
import com.gangchat.client.app.voiceWaveformWidth
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.SharedMusicPlaylist
import com.gangchat.client.protocol.SharedMusicTrack
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserCommonRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.clipboardText
import com.gangchat.client.ui.dpBounds
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.isSelectionHandle
import com.gangchat.client.ui.isTextField
import com.gangchat.client.ui.mockClipboardText
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.roleBadgeForegroundColorForLabel
import com.gangchat.client.ui.solidPng
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.ui.textColor
import com.gangchat.client.ui.textContextMenuPanelTag
import com.gangchat.client.ui.textLayout
import com.gangchat.client.widgets.IconSemanticsKey
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.showDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Port of test/widget/home/chat_message_rendering_test.dart.
 *
 * Desktop-only cases (mouse hover, right click, Windows/macOS layouts) run
 * their Android equivalent: hovers become taps and secondary clicks become
 * holds where the Android build offers the same menu; see the report for the
 * cases that were not ported.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Flutter's default widget-test surface (800 x 600).
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class ChatMessageRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installLoader() = installTestImageLoader()

    // --- helpers -------------------------------------------------------------

    private fun hasIcon(icon: ImageVector) = SemanticsMatcher.expectValue(IconSemanticsKey, icon)

    private fun surface(id: String = "message_1") = compose.onNodeWithTag(messageBubbleSurfaceTag(id))

    private fun content(id: String = "message_1") = compose.onNodeWithTag(messageBubbleContentTag(id))

    /** The bubble's fill, sampled inside its left padding. */
    private fun bubbleColor(id: String = "message_1"): Color {
        val node = surface(id)
        val height = node.dpBounds(compose.density.density).let { it.bottom - it.top }.value
        return compose.pixelIn(node, 6f, height / 2)
    }

    /** The bubble's border, sampled on its left edge. */
    private fun bubbleBorder(id: String = "message_1"): Color {
        val node = surface(id)
        val height = node.dpBounds(compose.density.density).let { it.bottom - it.top }.value
        return compose.pixelIn(node, 0.4f, height / 2)
    }

    private fun contentColor(id: String = "message_1"): Color {
        val node = content(id)
        val width = node.dpBounds(compose.density.density).let { it.right - it.left }.value
        return compose.pixelIn(node, width / 2, 0.4f)
    }

    private fun blend(foreground: Color, background: Color = UiColors.background): Color = _alphaBlend(foreground, background)

    private fun assertColor(expected: Color, actual: Color, message: String = "") {
        val tolerance = 2.5f / 255f
        val close =
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance
        assertTrue("$message expected $expected but was $actual", close)
    }

    private fun bounds(node: SemanticsNodeInteraction) = node.dpBounds(compose.density.density)

    private fun messageField(text: String) = compose.onNode(isTextField and hasText(text))

    private fun list() = compose.onNodeWithTag(chatMessageListTag)

    private fun holdOn(node: SemanticsNodeInteraction, position: (Offset, Float) -> Offset = { center, _ -> center }) {
        node.performTouchInput {
            val target = position(center, 1.dp.toPx())
            longClick(target)
        }
        compose.pump(150)
    }

    /**
     * Dart's `_showMessageTextContextMenu`: selects [selection] and shows the
     * text toolbar. A double tap starts touch selection (a hold would open the
     * message menu instead), then the field's long-click action shows the
     * toolbar for the programmatic selection.
     */
    private fun showMessageTextContextMenu(selection: TextRange) {
        val field = compose.onNode(isTextField)
        field.performTouchInput { doubleClick(Offset(left + 24.dp.toPx(), centerY)) }
        compose.waitForIdle()
        field.performTextInputSelection(selection)
        compose.waitForIdle()
        field.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        compose.waitForIdle()
        compose.pump(100)
    }

    /** Lets the media cache's I/O and Coil's decoding finish. */
    private fun settleImages() {
        repeat(30) {
            Thread.sleep(30)
            compose.waitForIdle()
        }
    }

    private fun quoteMessage(type: String = "text", body: String, quote: MessageQuote) =
        chatTestMessage(type = type, body = body, quote = quote)

    private val _imagePreviewActions = ChatImagePreviewActions.disabled()

    private fun systemMessage(
        event: String,
        body: String,
        clientMessageId: String = "client_1",
        createdAt: DateTime? = null,
        target: UserSummary? = null,
        actor: UserSummary? = null,
        user: UserSummary? = null,
        fromRole: String? = null,
        toRole: String? = null,
        oldValue: String? = null,
        newValue: String? = null,
    ) = chatTestMessage(
        type = "system",
        body = body,
        clientMessageId = clientMessageId,
        createdAt = createdAt,
        attachments =
            listOf(
                MessageAttachment(
                    type = "system",
                    event = event,
                    target = target,
                    actor = actor,
                    user = user,
                    fromRole = fromRole,
                    toRole = toRole,
                    oldValue = oldValue,
                    newValue = newValue,
                ),
            ),
    )

    // --- tests ---------------------------------------------------------------

    @Test
    fun `chat sender names use role colors except current user`() {
        val controller = TextEditingController()
        compose.setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages =
                    listOf(
                        chatTestMessage(
                            type = "text",
                            body = "from self",
                            sender = UserSummary(id = chatTestCurrentUserId, username = "me", displayName = "Self Sender", avatarUrl = null, defaultAvatarKey = "blue-3", roomRole = "owner"),
                        ),
                        chatTestMessage(
                            type = "text",
                            body = "from admin",
                            clientMessageId = "client_admin",
                            sender = UserSummary(id = "user_admin", username = "admin", displayName = "Admin Sender", avatarUrl = null, defaultAvatarKey = "blue-3", roomRole = "admin"),
                        ),
                        chatTestMessage(
                            type = "text",
                            body = "from creator",
                            clientMessageId = "client_creator",
                            sender =
                                UserSummary(id = "user_creator", username = "creator", displayName = "Creator Sender", avatarUrl = null, defaultAvatarKey = "blue-3", roomRole = "owner"),
                        ),
                        chatTestMessage(
                            type = "text",
                            body = "from superuser",
                            clientMessageId = "client_superuser",
                            sender =
                                UserSummary(
                                    id = "user_superuser",
                                    username = "superuser",
                                    displayName = "Superuser Sender",
                                    avatarUrl = null,
                                    defaultAvatarKey = "blue-3",
                                    roomRole = "member",
                                    isSuperuser = true,
                                ),
                        ),
                    ),
            )
        }

        assertEquals(UiColors.accent, compose.onNodeWithText("Self Sender").textColor())
        assertEquals(UiColors.roleAdmin, compose.onNodeWithText("Admin Sender").textColor())
        assertEquals(UiColors.roleCreator, compose.onNodeWithText("Creator Sender").textColor())
        assertEquals(UiColors.roleSuperuser, compose.onNodeWithText("Superuser Sender").textColor())
    }

    @Test
    fun `gang-bot messages carry an AI tag and no profile card`() {
        val controller = TextEditingController()
        compose.setChatContent(height = 400.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages =
                    listOf(
                        chatTestMessage(
                            type = "text",
                            body = "好啊，算我一个",
                            sender = UserSummary(id = "gang-bot", username = "gang-bot", displayName = "gang-bot", avatarUrl = null, defaultAvatarKey = "violet-2", isBot = true),
                        ),
                    ),
            )
        }

        compose.onNodeWithText("gang-bot").assertExists()
        compose.onNodeWithText("AI").assertExists()
        compose.onNode(hasIcon(Icons.Outlined.SmartToy)).assertExists()
        // The initials avatar is replaced by the robot mark.
        compose.onNodeWithText("GA").assertDoesNotExist()
        compose.onTooltip(kGangBotDescription).assertExists()
        assertTrue(kGangBotDescription.startsWith("gang-bot 是群里的 AI"))
        // No profile card on tap.
        compose.onNode(hasIcon(Icons.Outlined.SmartToy)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(hoverCardPanelTag).assertDoesNotExist()
    }

    @Test
    fun `current user message avatar stays borderless while in live`() {
        val controller = TextEditingController()
        val live =
            LiveState(
                roomId = "room_1",
                participantCount = 1,
                participants =
                    listOf(
                        LiveParticipant(
                            liveSessionId = "live_self",
                            user = chatTestCurrentUser.toSummary(),
                            joinedAt = DateTime.utc(2026, 6, 11, 9),
                            micMuted = false,
                            headphonesMuted = false,
                            voiceBlocked = false,
                            cameraOn = false,
                            screenSharing = false,
                            connectionState = "connected",
                        ),
                    ),
                updatedAt = DateTime.utc(2026, 6, 11, 9),
            )
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                live = live,
                messages = listOf(chatTestMessage(type = "text", body = "from live self", sender = chatTestCurrentUser.toSummary())),
            )
        }

        // The 32 dp message avatar (initials "ME"): no active ring, no border at all.
        val initials = compose.onNode(hasText("ME") and hasAnyAncestor(hasTestTag(chatMessageListTag)), useUnmergedTree = true)
        val center = bounds(initials).let { Offset(((it.left + it.right) / 2).value, ((it.top + it.bottom) / 2).value) }
        val page = compose.onNodeWithTag(chatTestPageTag)
        val pageBounds = bounds(page)
        val fill = compose.pixelIn(page, center.x - pageBounds.left.value - 12f, center.y - pageBounds.top.value)
        val edge = compose.pixelIn(page, center.x - pageBounds.left.value - 15.4f, center.y - pageBounds.top.value)
        assertColor(fill, edge, "edge of the avatar")
        assertNotEquals(UiColors.accent, edge)
    }

    @Test
    fun `message timestamps toggle between brief and detailed labels`() {
        val controller = TextEditingController()
        val now = DateTime.now()
        val firstAt = now.subtract(5.minutes)
        val secondAt = now.subtract(3.minutes)
        val firstBrief = formatChatTimestamp(firstAt, now = now)
        val secondBrief = formatChatTimestamp(secondAt, now = now)
        val firstDetailed = formatDetailedChatTimestamp(firstAt)
        val secondDetailed = formatDetailedChatTimestamp(secondAt)

        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                timestampNow = now,
                messages =
                    listOf(
                        chatTestMessage(type = "text", body = "hello", createdAt = firstAt),
                        chatTestMessage(type = "text", body = "world", createdAt = secondAt, clientMessageId = "client_2"),
                    ),
            )
        }

        compose.onNodeWithText(firstBrief).assertExists()
        compose.onNodeWithText(secondBrief).assertExists()
        compose.onNodeWithText(firstDetailed).assertDoesNotExist()
        compose.onNodeWithText(secondDetailed).assertDoesNotExist()

        compose.onNodeWithText(firstBrief).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(firstDetailed).assertExists()
        compose.onNodeWithText(secondDetailed).assertExists()

        compose.onNodeWithText(firstDetailed).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(firstBrief).assertExists()
        compose.onNodeWithText(secondBrief).assertExists()
        compose.onNodeWithText(firstDetailed).assertDoesNotExist()
        compose.onNodeWithText(secondDetailed).assertDoesNotExist()
    }

    @Test
    fun `quote timestamps follow the message timestamp format`() {
        val controller = TextEditingController()
        val now = DateTime(2026, 7, 14, 18)
        val messageAt = DateTime(2026, 7, 14, 17)
        val quoteAt = DateTime(2026, 7, 10, 16, 12)
        val messageBrief = formatChatTimestamp(messageAt, now = now)
        val quoteBrief = formatChatTimestamp(quoteAt, now = now)
        val quoteDetailed = formatDetailedChatTimestamp(quoteAt)

        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                timestampNow = now,
                messages =
                    listOf(
                        chatTestMessage(
                            type = "text",
                            body = "Reply body",
                            createdAt = messageAt,
                            quote = MessageQuote(messageId = "quoted_message", senderDisplayName = "Quoted user", body = "Quoted body", createdAt = quoteAt),
                        ),
                    ),
            )
        }

        compose.onNodeWithText("Quoted user  $quoteBrief").assertExists()
        compose.onNodeWithText("Quoted user  $quoteDetailed").assertDoesNotExist()

        compose.onNodeWithText(messageBrief).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Quoted user  $quoteBrief").assertDoesNotExist()
        compose.onNodeWithText("Quoted user  $quoteDetailed").assertExists()
    }

    @Test
    fun `chat pane hides the composer until the room is ready`() {
        val controller = TextEditingController()
        compose.setChatContent { _ -> ChatPaneForTest(controller = controller, messages = emptyList(), loading = true) }

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        compose.onNodeWithTag(chatComposerTag).assertDoesNotExist()
    }

    @Test
    fun `chat pane opens directly at the latest message`() {
        val controller = TextEditingController()
        compose.setChatContent(height = 420.dp) { _ ->
            ChatPaneForTest(controller = controller, room = chatTestRoomDetail, messages = chatTestTextMessages(60))
        }

        val composerRect = bounds(compose.onNodeWithTag(chatComposerTag))
        val latestRect = bounds(messageField("Message 59"))

        assertTrue(latestRect.bottom <= composerRect.top)
        compose.onNodeWithTag(chatJumpToLatestTag).assertDoesNotExist()
    }

    @Test
    fun `incoming unread messages preserve the current viewport`() {
        val controller = TextEditingController()
        var messages by mutableStateOf(chatTestTextMessages(40))
        var newMessageCount by mutableIntStateOf(0)
        var viewed = 0
        compose.setChatContent(height = 360.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetailFor("room_preserve_unread"),
                messages = messages,
                newMessageCount = newMessageCount,
                onViewedNewMessages = {
                    viewed += 1
                    newMessageCount = 0
                },
            )
        }

        val previousLatestTop = bounds(messageField("Message 39")).top

        messages = messages + chatTestMessage(type = "text", body = "Message 40", clientMessageId = "client_40", createdAt = DateTime.utc(2026, 6, 11, 9, 40))
        newMessageCount = 1
        compose.waitForIdle()

        assertEquals(previousLatestTop.value, bounds(messageField("Message 39")).top.value, 1f)
        // The view moved away from the newest message (Dart: pixels grew): the
        // new row starts below the list's bottom inset, out of view.
        val newest = compose.onAllNodes(isTextField and hasText("Message 40")).fetchSemanticsNodes()
        if (newest.isNotEmpty()) assertTrue(newest.single().positionInRoot.y / compose.density.density >= (bounds(list()).bottom - 14.dp).value)
        compose.onNodeWithTag(chatJumpToLatestTag).assertExists()
        assertEquals(0, viewed)

        list().performScrollToIndex(0)
        compose.waitForIdle()

        assertEquals(1, viewed)
    }

    @Test
    fun `chat pane can jump to the first new message`() {
        val controller = TextEditingController()
        val messages = chatTestTextMessages(80)
        var viewed = 0
        var newMessageCount by mutableIntStateOf(50)
        compose.setChatContent(height = 360.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetailFor("room_jump_to_unread"),
                messages = messages,
                newMessageCount = newMessageCount,
                onViewedNewMessages = {
                    viewed += 1
                    newMessageCount = 0
                },
            )
        }

        compose.onTooltip("查看 50 条未读消息").assertExists()
        compose.onNodeWithText("查看 50 条未读消息").assertDoesNotExist()
        compose.onNode(hasIcon(Icons.Rounded.KeyboardArrowUp), useUnmergedTree = true).assertExists()
        val button = bounds(compose.onNodeWithTag(chatJumpToFirstNewTag))
        assertEquals(34f, (button.right - button.left).value, 0.01f)
        assertEquals(34f, (button.bottom - button.top).value, 0.01f)

        compose.onNodeWithTag(chatJumpToFirstNewTag).performClick()
        compose.waitForIdle()

        assertEquals(1, viewed)
        compose.onTooltip("查看 50 条未读消息").assertDoesNotExist()
        compose.onNodeWithText("未读消息").assertExists()
    }

    @Test
    fun `chat pane shows unread divider without jump when unread messages are visible`() {
        val controller = TextEditingController()
        compose.setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetailFor("room_visible_unread"),
                messages = chatTestTextMessages(5),
                newMessageCount = 2,
            )
        }

        compose.onNodeWithText("未读消息").assertExists()
        compose.onTooltip("查看 2 条未读消息").assertDoesNotExist()
        compose.onNodeWithTag(chatJumpToFirstNewTag).assertDoesNotExist()
    }

    @Test
    fun `chat pane marks unread viewed when divider is scrolled into view`() {
        val controller = TextEditingController()
        val messages = chatTestTextMessages(80)
        var viewed = 0
        var newMessageCount by mutableIntStateOf(50)
        compose.setChatContent(height = 360.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetailFor("room_scroll_to_unread"),
                messages = messages,
                newMessageCount = newMessageCount,
                onViewedNewMessages = {
                    viewed += 1
                    newMessageCount = 0
                },
            )
        }

        compose.onTooltip("查看 50 条未读消息").assertExists()

        var dividerVisible = false
        // The first unread message is the 31st (49 rows above the newest).
        for (index in listOf(40, 45, 48, 49)) {
            list().performScrollToIndex(index)
            compose.waitForIdle()
            dividerVisible = compose.onAllNodesWithText("未读消息").fetchSemanticsNodes().isNotEmpty()
            if (dividerVisible) break
        }

        assertTrue(dividerVisible)
        assertEquals(1, viewed)
        compose.onTooltip("查看 50 条未读消息").assertDoesNotExist()
    }

    @Test
    fun `short chats start at the top of the message area`() {
        val controller = TextEditingController()
        compose.setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(controller = controller, room = chatTestRoomDetail, messages = chatTestTextMessages(2))
        }

        val listRect = bounds(list())
        val firstMessageRect = bounds(messageField("Message 0"))

        assertTrue(firstMessageRect.top < listRect.top + 110.dp)
    }

    @Test
    fun `latest message button appears away from bottom and jumps back`() {
        val controller = TextEditingController()
        val messages = chatTestTextMessages(80)
        compose.setChatContent(height = 420.dp) { _ ->
            ChatPaneForTest(controller = controller, room = chatTestRoomDetailFor("room_latest_button"), messages = messages)
        }

        list().performScrollToIndex(79)
        compose.waitForIdle()

        val latestButton = compose.onNodeWithTag(chatJumpToLatestTag)
        latestButton.assertExists()
        compose.onNodeWithText("最新消息").assertDoesNotExist()
        val size = bounds(latestButton)
        assertEquals(34f, (size.right - size.left).value, 0.01f)
        assertEquals(34f, (size.bottom - size.top).value, 0.01f)

        latestButton.performClick()
        compose.waitForIdle()

        // Back at the newest message: it sits right above the list's bottom inset.
        val newest = bounds(messageField("Message 79"))
        val content = bounds(compose.onNodeWithTag(messageBubbleSurfaceTag("message_79")))
        assertEquals((bounds(list()).bottom - 14.dp).value, content.bottom.value, 1f)
        assertTrue(newest.bottom <= content.bottom)
        compose.onNodeWithTag(chatJumpToLatestTag).assertDoesNotExist()
    }

    @Test
    fun `latest message button reappears after chat pane remounts`() {
        val controller = TextEditingController()
        val messages = chatTestTextMessages(80)
        val room = chatTestRoomDetailFor("room_restore_latest_jump")
        var visible by mutableStateOf(true)
        compose.setChatContent(height = 420.dp) { _ ->
            if (visible) ChatPaneForTest(controller = controller, room = room, messages = messages)
        }

        list().performScrollToIndex(79)
        compose.waitForIdle()
        compose.onNodeWithTag(chatJumpToLatestTag).assertExists()

        visible = false
        compose.waitForIdle()
        visible = true
        compose.waitForIdle()

        compose.onNodeWithTag(chatJumpToLatestTag).assertExists()
    }

    @Test
    fun `unread jump button reappears after chat pane remounts`() {
        val controller = TextEditingController()
        val messages = chatTestTextMessages(80).toMutableList()
        messages[30] =
            chatTestMessage(
                type = "text",
                id = "message_30",
                body = "Message 30 @Me",
                clientMessageId = "client_30",
                createdAt = DateTime.utc(2026, 6, 11, 9, 30),
                mentions = listOf(linkedMapOf("type" to "user", "user_id" to chatTestCurrentUserId, "label" to "Me")),
            )
        val room = chatTestRoomDetailFor("room_restore_unread_jump")
        var visible by mutableStateOf(true)
        compose.setChatContent(height = 360.dp) { _ ->
            if (visible) ChatPaneForTest(controller = controller, room = room, messages = messages, newMessageCount = 50)
        }

        compose.onNodeWithTag(chatJumpToFirstNewTag).assertExists()

        visible = false
        compose.waitForIdle()
        visible = true
        compose.waitForIdle()

        compose.onNodeWithTag(chatJumpToFirstNewTag).assertExists()
    }

    @Test
    fun `text message body is selectable for copy`() {
        compose.setChatContent { _ -> MessageBubbleForTest(message = chatTestMessage(type = "text", body = "copy this"), downloadActions = chatTestDownloadActions()) }

        val field = compose.onNode(isTextField)
        val config = field.fetchSemanticsNode().config
        assertEquals("copy this", config[SemanticsProperties.EditableText].text)
        // readOnly: not editable, but selectable (enableInteractiveSelection).
        assertFalse(config.getOrElse(SemanticsProperties.IsEditable) { false })
        assertTrue(config.contains(androidx.compose.ui.semantics.SemanticsActions.SetSelection))
    }

    @Test
    fun `text message bubble removes trailing blank width`() {
        // Dart ran Windows, macOS and Android; the Android case (text scale 1.3).
        compose.setChatContent(height = 620.dp) { _ ->
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                MessageBubbleForTest(message = chatTestMessage(type = "text", body = "123   \n "), downloadActions = chatTestDownloadActions())
            }
        }

        val field = compose.onNode(isTextField)
        assertEquals("123", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        val layout = field.textLayout()
        val textLayout = compose.onNodeWithTag(messageTextLayoutTag("message_1"))
        val width = textLayout.fetchSemanticsNode().size.width
        assertEquals(ceil(layout.multiParagraph.maxIntrinsicWidth).toInt(), width)

        val surfaceRect = bounds(surface())
        val contentRect = bounds(content())
        assertEquals(13f, (contentRect.left - surfaceRect.left).value, 0.01f)
        assertEquals(13f, (surfaceRect.right - contentRect.right).value, 0.01f)
    }

    @Test
    fun `text message bubble wraps at compact widths on every platform`() {
        // The Android case: 140 wide at text scale 1.3.
        val body = "A long message that must wrap without reserving editor space."
        compose.setChatContent(height = 620.dp, width = 140.dp) { _ ->
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                MessageBubbleForTest(message = chatTestMessage(type = "text", body = body), downloadActions = chatTestDownloadActions())
            }
        }

        val surfaceRect = bounds(surface())
        assertEquals(140f, (surfaceRect.right - surfaceRect.left).value, 0.01f)
        val textLayout = bounds(compose.onNodeWithTag(messageTextLayoutTag("message_1")))
        assertTrue((textLayout.bottom - textLayout.top).value > 19 * 1.3f)
    }

    @Test
    fun `text message body link is recognized as clickable`() {
        compose.setChatContent { _ ->
            MessageBubbleForTest(message = chatTestMessage(type = "text", body = "open https://example.com/docs now"), downloadActions = chatTestDownloadActions())
        }

        val field = compose.onNode(isTextField)
        val layout = field.textLayout()
        val text = layout.layoutInput.text
        val start = text.text.indexOf("https://example.com/docs")
        val linkStyle = text.spanStyles.single { it.start == start && it.end == start + "https://example.com/docs".length }.item
        assertEquals(UiColors.accent, linkStyle.color)
        assertEquals(TextDecoration.Underline, linkStyle.textDecoration)

        // The link is tappable: a tap hands it to the system browser.
        val box = layout.getBoundingBox(start + 4)
        field.performTouchInput { click(box.center) }
        compose.waitForIdle()
        val launched = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        if (launched != null) {
            assertEquals(Intent.ACTION_VIEW, launched.action)
            assertEquals("https://example.com/docs", launched.dataString)
        } else {
            // No platform bridge installed: the launcher fails with a notice.
            compose.onNodeWithText("无法打开链接").assertExists()
        }
    }

    @Test
    fun `mention text click opens the user profile card`() {
        val controller = TextEditingController()
        val mentionedUser =
            UserSummary(id = "mentioned_user", username = "target_user", displayName = "Target User", avatarUrl = null, defaultAvatarKey = "green-2", roomDisplayName = "Target", roomRole = "member")
        val mentionedMember = RoomMember(user = mentionedUser, role = "member", joinedAt = DateTime.utc(2026, 6, 11), roomDisplayName = "Target")
        val resolvedProfile =
            UserSummary(
                id = "mentioned_user",
                username = "resolved_target",
                displayName = "Resolved Target",
                avatarUrl = null,
                defaultAvatarKey = "green-2",
                roomDisplayName = "Resolved Target",
                roomRole = "member",
                uid = "2000001",
                bio = "Loaded mention profile",
            )
        val resolveCompleter = CompletableDeferred<UserSummary>()
        val resolvedIds = mutableListOf<String>()

        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages =
                    listOf(
                        chatTestMessage(
                            type = "text",
                            body = "hello @Target",
                            mentions = listOf(linkedMapOf("type" to "user", "user_id" to "mentioned_user", "label" to "Target")),
                        ),
                    ),
                mentionMembers = listOf(mentionedMember),
                onResolveSenderProfile = { sender ->
                    resolvedIds.add(sender.id)
                    resolveCompleter.await()
                },
            )
        }

        val field = messageField("hello @Target")
        val layout = field.textLayout()
        val text = layout.layoutInput.text
        val start = text.text.indexOf("@Target")
        val mentionStyle = text.spanStyles.single { it.start == start }.item
        assertEquals(UiColors.controlAccent, mentionStyle.color)

        field.performTouchInput { click(layout.getBoundingBox(start + 2).center) }
        compose.waitForIdle()

        assertEquals(listOf("mentioned_user"), resolvedIds)
        compose.onNodeWithTag(hoverCardPanelTag).assertDoesNotExist()
        compose.onNodeWithText("@target_user").assertDoesNotExist()

        resolveCompleter.complete(resolvedProfile)
        compose.waitForIdle()

        val panel = bounds(compose.onNodeWithTag(hoverCardPanelTag))
        val host = bounds(compose.onNodeWithTag(chatTestPageTag))
        assertTrue((panel.right - panel.left) < (host.right - host.left) / 2)
        assertTrue((panel.bottom - panel.top) < (host.bottom - host.top))
        assertEquals(listOf("mentioned_user"), resolvedIds)
        compose.onNodeWithText("@resolved_target").assertExists()
        compose.onNodeWithText("Loaded mention profile").assertExists()
    }

    @Test
    fun `@me message highlights bubble background only`() {
        val controller = TextEditingController()
        compose.setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages =
                    listOf(
                        chatTestMessage(
                            type = "text",
                            body = "hello @Me",
                            mentions = listOf(linkedMapOf("type" to "user", "user_id" to chatTestCurrentUserId, "label" to "Me")),
                        ),
                    ),
            )
        }

        val color = bubbleColor()
        assertNotEquals(UiColors.surface, color)
        assertNotEquals(UiColors.selected, color)
        assertColor(color, contentColor())
        assertNotEquals(UiColors.border, bubbleBorder())

        val field = messageField("hello @Me")
        val text = field.textLayout().layoutInput.text
        val mentionStyle = text.spanStyles.single { it.start == text.text.indexOf("@Me") }.item
        assertEquals(UiColors.controlAccent, mentionStyle.color)

        // The message menu (an Android hold) strengthens the mention tint, not the selection colour.
        holdOn(field)
        val contextColor = bubbleColor()
        assertNotEquals(UiColors.selected, contextColor)
        assertNotEquals(UiColors.selectedBorder, bubbleBorder())
    }

    @Test
    fun `mentioned messages wait for mention render context`() {
        val controller = TextEditingController()
        val mentionedMessage =
            chatTestMessage(
                type = "text",
                body = "hello @Me",
                mentions = listOf(linkedMapOf("type" to "user", "user_id" to chatTestCurrentUserId, "label" to "Me")),
            )
        var ready by mutableStateOf(false)
        compose.setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(controller = controller, room = chatTestRoomDetail, messages = listOf(mentionedMessage), mentionMembersReady = ready)
        }

        compose.onNode(isTextField and hasText("hello @Me")).assertDoesNotExist()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()

        ready = true
        compose.waitForIdle()

        messageField("hello @Me").assertExists()
        assertNotEquals(UiColors.surface, bubbleColor())
    }

    @Test
    fun `focused message is highlighted on first frame`() {
        val controller = TextEditingController()
        compose.mainClock.autoAdvance = false
        compose.setContentForFirstFrame(controller)
        // The list stays transparent until its first layout pass settles (Dart's
        // `_messageListReady`); the highlight has no entry animation, so the
        // first visible frame is already highlighted.
        repeat(2) { compose.mainClock.advanceTimeByFrame() }

        assertColor(blend(UiColors.selected.copy(alpha = 0.86f)), bubbleColor())
        assertNotEquals(UiColors.surface, bubbleColor())
        compose.mainClock.autoAdvance = true
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setContentForFirstFrame(controller: TextEditingController) {
        setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages = listOf(chatTestMessage(type = "text", body = "jump target")),
                focusMessageId = "message_1",
            )
        }
    }

    @Test
    fun `focused system message uses the same timed highlight`() {
        val controller = TextEditingController()
        val message = systemMessage(event = kSystemEventRoomMemberJoined, body = "Logan joined the room", target = chatTestSystemTarget)
        compose.setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(controller = controller, room = chatTestRoomDetail, messages = listOf(message), focusMessageId = message.id)
        }

        val pill = compose.onNodeWithTag(systemMessageSurfaceTag)
        val width = bounds(pill).let { it.right - it.left }.value
        assertColor(blend(UiColors.selected.copy(alpha = 0.86f)), compose.pixelIn(pill, width / 2, 3f), "highlighted fill")
        assertColor(UiColors.selectedBorder, compose.pixelIn(pill, width / 2, 0.3f), "highlighted border")

        compose.pump(2900)

        assertColor(blend(UiColors.surfacePressed.copy(alpha = 0.82f)), compose.pixelIn(pill, width / 2, 3f), "resting fill")
        assertColor(UiColors.border, compose.pixelIn(pill, width / 2, 0.3f), "resting border")
    }

    @Test
    fun `focused @me message temporarily strengthens mention color`() {
        val controller = TextEditingController()
        compose.setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages =
                    listOf(
                        chatTestMessage(
                            type = "text",
                            body = "jump to @Me",
                            mentions = listOf(linkedMapOf("type" to "user", "user_id" to chatTestCurrentUserId, "label" to "Me")),
                        ),
                    ),
                focusMessageId = "message_1",
            )
        }

        val normalColor = _alphaBlend(UiColors.amber.copy(alpha = 0.13f), UiColors.surface)
        val highlightedColor = _alphaBlend(UiColors.amber.copy(alpha = 0.22f), UiColors.surface)
        assertColor(highlightedColor, bubbleColor(), "highlighted fill")
        assertColor(_alphaBlend(UiColors.amber.copy(alpha = 0.72f), highlightedColor), bubbleBorder(), "highlighted border")

        compose.pump(2700)

        assertColor(normalColor, bubbleColor(), "resting fill")
        assertColor(_alphaBlend(UiColors.amber.copy(alpha = 0.58f), normalColor), bubbleBorder(), "resting border")
    }

    @Test
    fun `handled focused message does not replay after remount`() {
        val controller = TextEditingController()
        var focusMessageId by mutableStateOf<String?>("message_70")
        val room = chatTestRoomDetailFor("room_focus_restore_position")
        val messages = chatTestTextMessages(80)
        var visible by mutableStateOf(true)
        compose.setChatContent(height = 420.dp) { _ ->
            if (visible) {
                ChatPaneForTest(
                    controller = controller,
                    room = room,
                    messages = messages,
                    focusMessageId = focusMessageId,
                    onFocusMessageHandled = { messageId -> if (focusMessageId == messageId) focusMessageId = null },
                )
            }
        }
        compose.pump(500)
        assertNull(focusMessageId)

        list().performScrollToIndex(79)
        compose.waitForIdle()
        val previousTop = bounds(messageField("Message 0")).top

        visible = false
        compose.waitForIdle()
        visible = true
        compose.waitForIdle()

        assertEquals(previousTop.value, bounds(messageField("Message 0")).top.value, 1f)
    }

    @Test
    fun `the same focused message can be requested again`() {
        val controller = TextEditingController()
        var focusMessageId by mutableStateOf<String?>("message_1")
        var handledCount = 0
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                messages = listOf(chatTestMessage(type = "text", body = "jump target")),
                focusMessageId = focusMessageId,
                onFocusMessageHandled = { messageId ->
                    handledCount++
                    if (focusMessageId == messageId) focusMessageId = null
                },
            )
        }

        assertEquals(1, handledCount)
        assertNull(focusMessageId)

        focusMessageId = "message_1"
        compose.waitForIdle()

        assertEquals(2, handledCount)
        assertNull(focusMessageId)
    }

    @Test
    fun `selected text message context menu only copies selection`() {
        mockClipboardText(null)
        compose.setChatContent { _ -> MessageBubbleForTest(message = chatTestMessage(type = "text", body = "copy this"), downloadActions = chatTestDownloadActions()) }

        showMessageTextContextMenu(TextRange(0, 4))

        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("Ctrl+C").assertExists()
        compose.onNodeWithText("全选").assertDoesNotExist()
        compose.onNodeWithText("Select all").assertDoesNotExist()
        compose.onNodeWithText("删除").assertDoesNotExist()

        compose.onNodeWithText("复制").performClick()
        compose.waitForIdle()

        assertEquals("copy", clipboardText())
    }

    @Test
    fun `Android text message hold opens the desktop message menu`() {
        var copied = false
        var deleted = false
        compose.setChatContent { _ ->
            MessageBubbleForTest(
                message = chatTestMessage(type = "text", body = "copy this"),
                downloadActions = chatTestDownloadActions(),
                messageActions =
                    ChatMessageActions(
                        onCopy = { _, _ -> copied = true },
                        onDeleteForMe = { _, _ -> deleted = true },
                        onRecall = { _, _ -> },
                        canRecall = { false },
                    ),
            )
        }

        holdOn(compose.onNode(isTextField))
        compose.waitForIdle()

        val field = compose.onNode(isTextField).fetchSemanticsNode()
        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("删除").assertExists()
        compose.onNodeWithText("全选").assertDoesNotExist()
        assertTrue(field.config[SemanticsProperties.TextSelectionRange].collapsed)
        compose.onAllNodes(isSelectionHandle).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }

        // Dart's "unselected text message right click keeps message menu":
        // the open menu paints the bubble as selected until it closes.
        assertColor(UiColors.selected, bubbleColor())
        assertColor(UiColors.selectedBorder, bubbleBorder())
        assertColor(bubbleColor(), contentColor())

        compose.onNodeWithText("复制").performClick()
        compose.pump(100)
        assertColor(UiColors.surface, bubbleColor())
        assertTrue(copied)
        assertFalse(deleted)
    }

    @Test
    fun `Android message double tap keeps its text selection`() {
        compose.setChatContent { _ -> MessageBubbleForTest(message = chatTestMessage(type = "text", body = "select this message"), downloadActions = chatTestDownloadActions()) }

        val field = compose.onNode(isTextField)
        field.performTouchInput { doubleClick(Offset(left + 24.dp.toPx(), centerY)) }
        compose.waitForIdle()

        assertFalse(field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed)

        compose.pump(600)
        assertFalse(field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed)
        assertEquals(2, compose.onAllNodes(isSelectionHandle).fetchSemanticsNodes().size)
        compose.onNodeWithText("删除").assertDoesNotExist()

        field.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        compose.waitForIdle()
        compose.onNodeWithTag(textContextMenuPanelTag).assertExists()
    }

    @Test
    fun `message quote shows room name and time and opens source`() {
        var opened: MessageQuote? = null
        val quote = MessageQuote(messageId = "source_1", senderDisplayName = "房内用户名", body = "[文件] report.pdf", createdAt = DateTime(2026, 7, 14, 16, 12))
        compose.setChatContent { _ ->
            MessageBubbleForTest(
                message = chatTestMessage(type = "text", body = "本次回复", quote = quote),
                timestampNow = DateTime(2026, 7, 14, 18),
                downloadActions = chatTestDownloadActions(),
                messageActions =
                    ChatMessageActions(
                        onCopy = { _, _ -> },
                        onDeleteForMe = { _, _ -> },
                        onRecall = { _, _ -> },
                        canRecall = { false },
                        onOpenQuote = { _, value -> opened = value },
                    ),
            )
        }

        compose.onNodeWithText("房内用户名  16:12").assertExists()
        compose.onNodeWithText("[文件] report.pdf").assertExists()
        messageField("本次回复").assertExists()
        compose.onNodeWithTag(messageQuoteTag("source_1")).performClick()
        compose.waitForIdle()
        assertSame(quote, opened)
    }

    @Test
    fun `system message quote header shows only its timestamp`() {
        val quote = MessageQuote(messageId = "system_source", senderDisplayName = "", body = "A member joined the room", createdAt = DateTime(2026, 7, 14, 16, 12))
        compose.setChatContent { _ ->
            MessageBubbleForTest(message = chatTestMessage(type = "text", body = "Reply", quote = quote), timestampNow = DateTime(2026, 7, 14, 18), downloadActions = chatTestDownloadActions())
        }

        compose.onNodeWithText("16:12").assertExists()
        compose.onNodeWithText("A member joined the room").assertExists()
        compose.onNodeWithText("用户  16:12", substring = true).assertDoesNotExist()
    }

    @Test
    fun `composer quote row shows snapshot and can be closed`() {
        val controller = TextEditingController(text = "本次回复")
        var removed: String? = null
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                timestampNow = DateTime(2026, 7, 14, 18),
                room = chatTestRoomDetail,
                messages = emptyList(),
                composerQuotes =
                    listOf(
                        MessageQuote(messageId = "composer_source", senderDisplayName = "房内用户名", body = "原消息", createdAt = DateTime(2026, 7, 14, 16, 12)),
                        MessageQuote(messageId = "composer_source_2", senderDisplayName = "第二位房内用户", body = "[文件] second.pdf", createdAt = DateTime(2026, 7, 14, 16, 13)),
                    ),
                onRemoveComposerQuote = { messageId -> removed = messageId },
            )
        }

        compose.onNodeWithText("房内用户名  16:12").assertExists()
        compose.onNodeWithText("原消息").assertExists()
        compose.onNodeWithText("第二位房内用户  16:13").assertExists()
        compose.onNodeWithText("[文件] second.pdf").assertExists()
        val firstQuoteCard = bounds(compose.onNodeWithTag(messageQuoteTag("composer_source")))
        val firstCloseButton = compose.onNodeWithTag(composerQuoteCloseTag("composer_source"))
        val closeRect = bounds(firstCloseButton)
        assertEquals(((firstQuoteCard.top + firstQuoteCard.bottom) / 2).value, ((closeRect.top + closeRect.bottom) / 2).value, 0.01f)
        firstCloseButton.performClick()
        compose.waitForIdle()
        assertEquals("composer_source", removed)
    }

    @Test
    fun `copied music component is complete and removable in composer`() {
        val controller = TextEditingController()
        var removed = false
        val component =
            chatTestMessage(
                type = "music_track",
                body = "[歌曲] 很长但必须完整显示的歌曲名称",
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "music_track",
                            track =
                                SharedMusicTrack(
                                    id = "item_1",
                                    playlistId = "playlist_1",
                                    trackId = "track_1",
                                    source = "netease",
                                    title = "很长但必须完整显示的歌曲名称",
                                    artists = listOf("歌手甲", "歌手乙"),
                                    durationMs = 180000,
                                ),
                        ),
                    ),
            )
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages = emptyList(),
                composerComponent = component,
                onRemoveComposerComponent = { removed = true },
            )
        }

        compose.onNodeWithTag(composerMessageComponentTag).assertExists()
        compose.onNodeWithText("很长但必须完整显示的歌曲名称").assertExists()
        compose.onNodeWithText("歌手甲 / 歌手乙").assertExists()
        compose.onNodeWithTag(removeComposerMessageComponentTag).performClick()
        assertTrue(removed)
    }

    @Test
    fun `image quote shows only a clickable thumbnail`() {
        var opened: MessageQuote? = null
        val quote =
            MessageQuote(
                messageId = "image_source",
                senderDisplayName = "房内用户名",
                body = "[图片] 照片.png",
                createdAt = DateTime(2026, 7, 14, 16, 12),
                previewAttachment =
                    MessageAttachment(
                        type = "file",
                        name = "照片.png",
                        asset = UploadedAsset(id = "image_asset", url = "/images/photo.png", thumbnailUrl = "/images/photo-thumb.png", mimeType = "image/png", filename = "照片.png"),
                    ),
            )
        val cache =
            testMediaCacheController(
                files =
                    mapOf(
                        "https://assets.test/images/photo-thumb.png" to solidPng(64, 64, 0xFF3A6EA5.toInt()),
                        "https://assets.test/images/photo.png" to solidPng(200, 120, 0xFF3A6EA5.toInt()),
                    ),
            )
        compose.setChatContent(cache = cache) { _ ->
            MessageBubbleForTest(
                message = chatTestMessage(type = "text", body = "本次回复", quote = quote),
                timestampNow = DateTime(2026, 7, 14, 18),
                downloadActions = chatTestDownloadActions(),
                imagePreviewActions = ChatImagePreviewActions.disabled(),
                messageActions =
                    ChatMessageActions(
                        onCopy = { _, _ -> },
                        onDeleteForMe = { _, _ -> },
                        onRecall = { _, _ -> },
                        canRecall = { false },
                        onOpenQuote = { _, value -> opened = value },
                    ),
            )
        }

        compose.onNodeWithText("[图片] 照片.png").assertDoesNotExist()
        val thumbnail = compose.onNodeWithTag(messageQuoteThumbnailTag("image_source"), useUnmergedTree = true)
        thumbnail.assertExists()
        val indicator = compose.onNodeWithTag(messageQuoteIndicatorTag("image_source"), useUnmergedTree = true)
        indicator.assertExists()
        assertEquals(bounds(thumbnail).bottom.value, bounds(indicator).bottom.value, 0.01f)
        thumbnail.performClick()
        compose.waitForIdle()
        assertNull(opened)
        compose.pump(200)
        compose.onNodeWithTag(chatImagePreviewImageTag).assertExists()
    }

    @Test
    fun `message context menu can select a quote source`() {
        // Dart opened the menu with a right click; on Android it is the hold.
        var quoted: Message? = null
        val message = chatTestMessage(type = "text", body = "引用我")
        compose.setChatContent { _ ->
            MessageBubbleForTest(
                message = message,
                downloadActions = chatTestDownloadActions(),
                messageActions =
                    ChatMessageActions(
                        onCopy = { _, _ -> },
                        onDeleteForMe = { _, _ -> },
                        onRecall = { _, _ -> },
                        canRecall = { false },
                        onQuote = { value -> quoted = value },
                        canQuote = { true },
                    ),
            )
        }

        holdOn(compose.onNode(isTextField))
        compose.onNodeWithText("引用").assertExists()
        compose.onNodeWithText("引用").performClick()
        compose.waitForIdle()
        assertSame(message, quoted)
    }

    @Test
    fun `text message bubble padding opens message menu`() {
        // Dart's right click on the padding; the Android hold works there too.
        var deleted = false
        compose.setChatContent { _ ->
            MessageBubbleForTest(
                message = chatTestMessage(type = "text", body = "copy this"),
                downloadActions = chatTestDownloadActions(),
                messageActions =
                    ChatMessageActions(onCopy = { _, _ -> }, onDeleteForMe = { _, _ -> deleted = true }, onRecall = { _, _ -> }, canRecall = { false }),
            )
        }

        val textRect = bounds(compose.onNode(isTextField))
        val surfaceRect = bounds(surface())
        holdOn(surface()) { _, dp -> Offset((textRect.left - surfaceRect.left).value * dp - 6 * dp, ((textRect.top + textRect.bottom) / 2 - surfaceRect.top).value * dp) }

        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("删除").assertExists()
        compose.onNodeWithText("全选").assertDoesNotExist()

        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        assertTrue(deleted)
    }

    @Test
    fun `pending message shows a spinner beside the 发送中 label`() {
        compose.setChatContent { _ ->
            MessageBubbleForTest(message = chatTestMessage(type = "text", body = "sending soon", pending = true), outgoing = true, downloadActions = chatTestDownloadActions())
        }

        compose.onNodeWithText("发送中").assertExists()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test
    fun `system role message renders below a centered timestamp`() {
        val controller = TextEditingController()
        val createdAt = DateTime.now().subtract(2.minutes)
        val brief = formatChatTimestamp(createdAt, now = DateTime.now())
        val detailed = formatDetailedChatTimestamp(createdAt)
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                messages =
                    listOf(
                        systemMessage(
                            event = kSystemEventRoomRoleChanged,
                            body = "降职为管理员",
                            createdAt = createdAt,
                            target = chatTestSystemTarget,
                            actor = chatTestSystemActor,
                            fromRole = "owner",
                            toRole = "admin",
                        ),
                    ),
            )
        }

        compose.onNodeWithText(brief).assertExists()
        compose.onNodeWithText("Logan").assertExists()
        assertEquals(roleBadgeForegroundColorForLabel("管理员"), compose.onAllNodesWithText("Logan")[0].textColor())
        compose.onNodeWithText("Owner").assertDoesNotExist()
        compose.onNodeWithText("降职为").assertExists()
        compose.onNodeWithText("管理员").assertExists()

        compose.onNodeWithText(brief).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(detailed).assertExists()
    }

    @Test
    fun `system room profile change messages show original values from info buttons`() {
        val controller = TextEditingController()
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                messages =
                    listOf(
                        systemMessage(
                            event = kSystemEventRoomNameChanged,
                            body = "房间名称被Owner修改为New Room",
                            clientMessageId = "client_room_name_changed",
                            user = chatTestSystemActor,
                            actor = chatTestSystemActor,
                            oldValue = "Old Room",
                            newValue = "New Room",
                        ),
                        systemMessage(
                            event = kSystemEventRoomDescriptionChanged,
                            body = "房间简介被Owner修改为\nNew intro\nline 2",
                            clientMessageId = "client_room_description_changed",
                            user = chatTestSystemActor,
                            actor = chatTestSystemActor,
                            oldValue = "Old intro",
                            newValue = "New intro\nline 2",
                        ),
                        systemMessage(
                            event = kSystemEventRoomVisibilityChanged,
                            body = "房间可见性被Owner修改为私密",
                            clientMessageId = "client_room_visibility_changed",
                            user = chatTestSystemActor,
                            actor = chatTestSystemActor,
                            oldValue = "public",
                            newValue = "private",
                        ),
                        systemMessage(
                            event = kSystemEventRoomJoinPolicyChanged,
                            body = "房间加入方式被Owner修改为关闭",
                            clientMessageId = "client_room_join_policy_changed",
                            user = chatTestSystemActor,
                            actor = chatTestSystemActor,
                            oldValue = "approval_required",
                            newValue = "closed",
                        ),
                    ),
            )
        }

        compose.onNodeWithText("房间名称").assertExists()
        val nameInfo = compose.onNodeWithTag("system-info-client_room_name_changed-room_name_changed")
        nameInfo.assertExists()
        compose.onNodeWithText("New Room").assertExists()
        compose.onNode(hasText("New Room") and hasAnyAncestor(SemanticsMatcher.keyIsDefined(com.gangchat.client.ui.TooltipSemanticsKey))).assertDoesNotExist()

        compose.onNodeWithText("房间简介").assertExists()
        val descriptionInfo = compose.onNodeWithTag("system-info-client_room_description_changed-room_description_changed")
        descriptionInfo.assertExists()
        compose.onNodeWithText("New intro\nline 2").assertExists()

        compose.onNodeWithText("房间可见性").assertExists()
        val visibilityInfo = compose.onNodeWithTag("system-info-client_room_visibility_changed-room_visibility_changed")
        visibilityInfo.assertExists()
        compose.onNodeWithText("私密").assertExists()

        compose.onNodeWithText("房间加入方式").assertExists()
        val joinPolicyInfo = compose.onNodeWithTag("system-info-client_room_join_policy_changed-room_join_policy_changed")
        joinPolicyInfo.assertExists()
        compose.onNodeWithText("关闭").assertExists()

        // Dart hovered the first button; on Android each one opens on tap.
        nameInfo.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("原房间名称：Old Room").assertExists()

        compose.onNodeWithTag(chatTestPageTag).performTouchInput { click(Offset(2f, 2f)) }
        compose.pump(180)
        descriptionInfo.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("原房间简介：Old intro").assertExists()

        compose.onNodeWithTag(chatTestPageTag).performTouchInput { click(Offset(2f, 2f)) }
        compose.pump(180)
        visibilityInfo.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("原可见性：公开").assertExists()

        compose.onNodeWithTag(chatTestPageTag).performTouchInput { click(Offset(2f, 2f)) }
        compose.pump(180)
        joinPolicyInfo.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("原加入方式：需审批").assertExists()

        assertTrue(compose.onAllNodes(hasText("OW"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `unread divider stays above timestamp for batched system events`() {
        val controller = TextEditingController()
        val now = DateTime.now()
        val createdAt = now.subtract(2.minutes)
        val timestamp = formatChatTimestamp(createdAt, now = now)
        compose.setChatContent(height = 620.dp) { _ ->
            ChatPaneForTest(
                controller = controller,
                messages =
                    listOf(
                        systemMessage(
                            event = kSystemEventRoomNameChanged,
                            body = "房间名称修改为123456",
                            clientMessageId = "client_room_name_changed",
                            createdAt = createdAt,
                            user = chatTestSystemActor,
                            actor = chatTestSystemActor,
                            oldValue = "old",
                            newValue = "123456",
                        ),
                        systemMessage(
                            event = kSystemEventRoomDescriptionChanged,
                            body = "房间简介修改为\nintro",
                            clientMessageId = "client_room_description_changed",
                            createdAt = createdAt.add(1.milliseconds),
                            user = chatTestSystemActor,
                            actor = chatTestSystemActor,
                            oldValue = "old intro",
                            newValue = "intro",
                        ),
                    ),
                newMessageCount = 2,
            )
        }

        val unreadRect = bounds(compose.onNodeWithText("未读消息"))
        val timestampRect = bounds(compose.onNodeWithText(timestamp))
        assertTrue(unreadRect.bottom < timestampRect.top)
    }

    private fun systemAvatar(label: String) = compose.onNode(hasText(label.take(2).uppercase()) and hasAnyAncestor(hasTestTag(systemMessageSurfaceTag)), useUnmergedTree = true)

    @Test
    fun `system message user avatar opens the resolved profile card`() {
        val controller = TextEditingController()
        val resolvedIds = mutableListOf<String>()
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                messages = listOf(systemMessage(event = kSystemEventLiveJoined, body = "进入了语音频道", target = chatTestSystemTarget)),
                onResolveSenderProfile = { sender ->
                    resolvedIds.add(sender.id)
                    UserSummary(
                        id = sender.id,
                        username = "fresh_${sender.username}",
                        displayName = "Fresh ${sender.displayName}",
                        avatarUrl = sender.avatarUrl,
                        defaultAvatarKey = sender.defaultAvatarKey,
                        uid = "20001",
                        bio = "Fresh system profile",
                        isOnline = true,
                    )
                },
            )
        }

        // Dart hovered the 16 dp avatar; on Android a tap opens the card.
        val avatar = systemAvatar("Logan")
        avatar.assertExists()
        avatar.performClick()
        compose.waitForIdle()

        assertEquals(listOf("user_target"), resolvedIds)
        compose.onNodeWithText("@fresh_logan").assertExists()
        compose.onNodeWithText("Fresh system profile").assertExists()
    }

    @Test
    fun `system message user avatar can expose a profile action`() {
        val controller = TextEditingController()
        var managedUser: UserSummary? = null
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                messages = listOf(systemMessage(event = kSystemEventLiveJoined, body = "进入了语音频道", target = chatTestSystemTarget)),
                senderProfileActionBuilder = { user -> UserProfileAction(label = "管理成员", icon = Icons.Outlined.ManageAccounts, onPressed = { managedUser = user }) },
            )
        }

        systemAvatar("Logan").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("管理成员").performClick()
        compose.waitForIdle()

        assertEquals("user_target", managedUser?.id)
    }

    @Test
    fun `system and ordinary messages can share a timestamp divider`() {
        val controller = TextEditingController()
        val now = DateTime.now()
        val createdAt = DateTime(now.year, now.month, now.day, now.hour, now.minute, 5)
        val brief = formatChatTimestamp(createdAt, now = DateTime.now())
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                messages =
                    listOf(
                        systemMessage(event = kSystemEventLiveJoined, body = "进入了语音频道", createdAt = createdAt, target = chatTestSystemTarget),
                        chatTestMessage(type = "text", body = "hello after live join", createdAt = createdAt.add(10.seconds), clientMessageId = "client_after_system"),
                    ),
            )
        }

        compose.onNodeWithText(brief).assertExists()
        assertTrue(compose.onAllNodesWithText("Logan").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("进入了语音频道").assertExists()
        messageField("hello after live join").assertExists()
    }

    @Test
    fun `own recalled text message can be re-edited`() {
        val controller = TextEditingController()
        var reeditedMessage: Message? = null
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages =
                    listOf(
                        chatTestMessage(
                            type = "text",
                            body = "bring this back",
                            sender = chatTestCurrentUser.toSummary(),
                            isRecalled = true,
                            recalledBy = chatTestCurrentUser.toSummary(),
                        ),
                    ),
                messageActions = chatTestMessageActions(onReeditRecalledText = { message -> reeditedMessage = message }, canReeditRecalledText = { true }),
            )
        }

        val button = compose.onNodeWithTag("message-reedit-message_1")
        button.assertExists()
        // Dart showed the tooltip on hover; Android shows it on a hold.
        compose.onTooltip("重新编辑").assertExists()

        button.performClick()
        compose.waitForIdle()
        assertEquals("bring this back", reeditedMessage?.body)
    }

    @Test
    fun `permitted recalled text message exposes content info card`() {
        val controller = TextEditingController()
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages =
                    listOf(
                        chatTestMessage(
                            type = "text",
                            body = "moderated secret",
                            sender = UserSummary(id = "user_member", username = "member", displayName = "Member", avatarUrl = null, defaultAvatarKey = "blue-3", roomRole = "member"),
                            isRecalled = true,
                            recalledBy = UserSummary(id = "user_admin", username = "admin", displayName = "Admin", avatarUrl = null, defaultAvatarKey = "blue-3", roomRole = "admin"),
                        ),
                    ),
                messageActions = chatTestMessageActions(canInspectRecalledText = { true }),
            )
        }

        val button = compose.onNodeWithTag("message-info-message_1")
        button.assertExists()
        compose.onNodeWithText("moderated secret").assertDoesNotExist()

        button.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("moderated secret").assertExists()
    }

    @Test
    fun `own text message recalled by another user exposes info card`() {
        val controller = TextEditingController()
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages =
                    listOf(
                        chatTestMessage(
                            type = "text",
                            body = "my moderated text",
                            sender = chatTestCurrentUser.toSummary(),
                            isRecalled = true,
                            recalledBy = UserSummary(id = "user_admin", username = "admin", displayName = "Admin", avatarUrl = null, defaultAvatarKey = "blue-3", roomRole = "admin"),
                        ),
                    ),
                messageActions = chatTestMessageActions(canInspectRecalledText = { true }),
            )
        }

        compose.onNodeWithTag("message-info-message_1").assertExists()
        compose.onNodeWithTag("message-reedit-message_1").assertDoesNotExist()
    }

    @Test
    fun `android shared playlist message exposes view-only card and direct arrow`() {
        val controller = TextEditingController()
        var viewed = 0
        val playlist =
            SharedMusicPlaylist(
                id = "shared_playlist_1",
                name = "朋友的夜晚歌单",
                description = "",
                itemCount = 1,
                createdAt = DateTime.utc(2026, 8, 7, 9, 30),
                creator = UserSummary(id = "friend_1", username = "friend", displayName = "朋友", roomDisplayName = "房间里的朋友", avatarUrl = null, defaultAvatarKey = "blue-2"),
                items =
                    listOf(
                        PersonalMusicPlaylistItem(
                            id = "shared_item_1",
                            playlistId = "shared_playlist_1",
                            trackId = "track_1",
                            source = "netease",
                            title = "第一首歌",
                            artists = listOf("歌手"),
                            durationMs = 180000,
                            sortOrder = 10,
                            createdAt = null,
                        ),
                    ),
            )
        val message =
            chatTestMessage(
                type = "playlist",
                body = "[歌单] 朋友的夜晚歌单",
                attachments = listOf(MessageAttachment(type = "playlist", playlistId = playlist.id, playlist = playlist)),
            )
        val actions =
            ChatMessageActions(
                onCopy = { _, _ -> },
                onDeleteForMe = { _, _ -> },
                onRecall = { _, _ -> },
                canRecall = { false },
                onViewSharedPlaylist = { context, viewedMessage, viewedPlaylist ->
                    assertEquals(message.id, viewedMessage.id)
                    assertEquals(playlist.id, viewedPlaylist.id)
                    viewed += 1
                    showDialog<Unit>(context) { dialogContext ->
                        DialogFrame(
                            title = viewedPlaylist.name,
                            adaptiveActions = listOf(ResponsiveDialogAction(label = "完成", onPressed = { Navigator.of(dialogContext).pop() })),
                            child = {
                                Box(Modifier.testTag("shared-music-playlist-view-${viewedPlaylist.id}").fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                    Text(viewedPlaylist.items.first().title)
                                }
                            },
                        )
                    }
                },
            )
        compose.setChatContent(height = 520.dp) { _ ->
            ChatPaneForTest(controller = controller, messages = listOf(message), messageActions = actions)
        }

        val embed = compose.onNodeWithTag(sharedMusicPlaylistMessageTag("shared_playlist_1"))
        embed.assertExists()
        embed.performTouchInput { click(Offset(20.dp.toPx(), centerY)) }
        compose.waitForIdle()

        val viewAction = compose.onNodeWithTag(musicPlaylistCardViewTag)
        compose.onNodeWithText("克隆").assertDoesNotExist()
        compose.onNodeWithText("查看歌单").assertExists()
        compose.onNodeWithTag(musicPlaylistCardPlayAllTag).assertDoesNotExist()
        viewAction.assertExists()

        viewAction.performClick()
        compose.pump(300)
        compose.onNodeWithTag("shared-music-playlist-view-shared_playlist_1").assertExists()
        compose.onNodeWithText("第一首歌").assertExists()
        assertEquals(1, viewed)
        compose.onNodeWithText("完成").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(sharedMusicPlaylistOpenTag("shared_playlist_1")).performClick()
        compose.pump(300)
        assertEquals(2, viewed)
        compose.onNodeWithTag("shared-music-playlist-view-shared_playlist_1").assertExists()
    }

    @Test
    fun `android owned shared playlist exposes edit instead of view`() {
        val controller = TextEditingController()
        var viewed = 0
        var edited = 0
        val playlist =
            SharedMusicPlaylist(
                id = "owned_playlist_1",
                name = "我的可编辑歌单",
                description = "",
                itemCount = 0,
                createdAt = DateTime.utc(2026, 8, 11),
                creator = chatTestCurrentUser.toSummary(),
                items = emptyList(),
            )
        val message =
            chatTestMessage(
                type = "playlist",
                body = "[歌单] 我的可编辑歌单",
                attachments = listOf(MessageAttachment(type = "playlist", playlistId = playlist.id, playlist = playlist)),
            )
        val actions =
            ChatMessageActions(
                onCopy = { _, _ -> },
                onDeleteForMe = { _, _ -> },
                onRecall = { _, _ -> },
                canRecall = { false },
                onViewSharedPlaylist = { _, _, _ -> viewed += 1 },
                onEditOwnedSharedPlaylist = { _, editedMessage, editedPlaylist ->
                    assertEquals(message.id, editedMessage.id)
                    assertEquals(playlist.id, editedPlaylist.id)
                    edited += 1
                },
            )
        compose.setChatContent(height = 520.dp) { _ ->
            ChatPaneForTest(controller = controller, messages = listOf(message), messageActions = actions)
        }

        compose.onNodeWithTag(sharedMusicPlaylistMessageTag("owned_playlist_1")).performTouchInput { click(Offset(20.dp.toPx(), centerY)) }
        compose.waitForIdle()
        compose.onNodeWithText("编辑歌单").assertExists()
        compose.onNodeWithText("查看歌单").assertDoesNotExist()
        compose.onNodeWithTag(musicPlaylistCardViewTag).performClick()
        compose.waitForIdle()
        assertEquals(1, edited)
        assertEquals(0, viewed)

        compose.onNodeWithTag(sharedMusicPlaylistOpenTag("owned_playlist_1")).performClick()
        compose.waitForIdle()
        assertEquals(2, edited)
        assertEquals(0, viewed)
    }

    @Test
    fun `sticker bubble exposes the sticker name only as a tooltip`() {
        val cache = testMediaCacheController(files = mapOf("https://assets.test/stickers/wave-thumb.webp" to solidPng(120, 120, 0xFFE0A12A.toInt())))
        compose.setChatContent(cache = cache) { _ ->
            MessageBubbleForTest(
                message =
                    chatTestMessage(
                        type = "sticker",
                        body = "[表情] wave",
                        attachments =
                            listOf(
                                MessageAttachment(
                                    type = "sticker",
                                    name = "wave",
                                    asset = UploadedAsset(id = "asset_sticker", url = "/stickers/wave.webp", thumbnailUrl = "/stickers/wave-thumb.webp", mimeType = "image/webp"),
                                ),
                            ),
                    ),
                downloadActions = chatTestDownloadActions(),
            )
        }
        settleImages()

        val image = compose.onNodeWithTag(chatStickerImageTag, useUnmergedTree = true)
        val rect = bounds(image)
        assertEquals(132f, (rect.right - rect.left).value, 0.01f)
        assertEquals(132f, (rect.bottom - rect.top).value, 0.01f)
        // The thumbnail URL resolved against the asset base (its pixels loaded), contained.
        assertEquals(Color(0xFFE0A12A.toInt()), compose.pixelIn(image, 66f, 66f))
        compose.onNodeWithText("wave").assertDoesNotExist()
        compose.onTooltip("wave").assertExists()
    }

    private fun stickerActions(room: Boolean, onPersonal: (Message, MessageAttachment) -> Unit = { _, _ -> }, onRoom: (Message, MessageAttachment) -> Unit = { _, _ -> }) =
        ChatImagePreviewActions(
            onDownload = { _, _ -> },
            onSaveAs = { _, _ -> },
            onCopyToClipboard = { _ -> },
            onSaveSticker = { message, attachment -> onPersonal(message, attachment) },
            onSaveRoomSticker = if (room) ({ message, attachment -> onRoom(message, attachment) }) else null,
        )

    @Test
    fun `sticker context menu offers personal and room add actions`() {
        // Dart's right click; the Android hold opens the same menu.
        var savedPersonal = false
        var savedRoom = false
        compose.setChatContent { _ ->
            MessageBubbleForTest(
                message = chatTestStickerMessage(),
                downloadActions = chatTestDownloadActions(),
                imagePreviewActions =
                    stickerActions(
                        room = true,
                        onPersonal = { message, attachment -> savedPersonal = message.id == "message_1" && attachment.stickerId == "sticker_1" },
                        onRoom = { message, attachment -> savedRoom = message.id == "message_1" && attachment.stickerId == "sticker_1" },
                    ),
            )
        }

        holdOn(compose.onNodeWithTag(chatStickerImageTag, useUnmergedTree = true))

        compose.onNodeWithText("添加到我的表情包").assertExists()
        compose.onNodeWithText("添加到房间表情包").assertExists()
        val personalStyle = compose.onNodeWithText("添加到我的表情包").textLayout().layoutInput.style
        assertEquals(FontWeight.W400, personalStyle.fontWeight)
        assertEquals(TextDecoration.None, personalStyle.textDecoration)

        compose.onNodeWithText("添加到房间表情包").performClick()
        compose.pump(100)

        assertFalse(savedPersonal)
        assertTrue(savedRoom)
        compose.onNodeWithText("已添加到房间表情包").assertExists()
    }

    @Test
    fun `Android sticker hold opens the desktop sticker menu`() {
        compose.setChatContent { _ ->
            MessageBubbleForTest(message = chatTestStickerMessage(), downloadActions = chatTestDownloadActions(), imagePreviewActions = stickerActions(room = true))
        }

        holdOn(compose.onNodeWithTag(chatStickerImageTag, useUnmergedTree = true))

        compose.onNodeWithText("添加到我的表情包").assertExists()
        compose.onNodeWithText("添加到房间表情包").assertExists()
    }

    @Test
    fun `sticker context menu hides room action when unavailable`() {
        compose.setChatContent { _ ->
            MessageBubbleForTest(message = chatTestStickerMessage(), downloadActions = chatTestDownloadActions(), imagePreviewActions = stickerActions(room = false))
        }

        holdOn(compose.onNodeWithTag(chatStickerImageTag, useUnmergedTree = true))

        compose.onNodeWithText("添加到我的表情包").assertExists()
        compose.onNodeWithText("添加到房间表情包").assertDoesNotExist()
    }

    @Test
    fun `sticker bubble padding opens sticker menu`() {
        compose.setChatContent { _ ->
            MessageBubbleForTest(message = chatTestStickerMessage(), downloadActions = chatTestDownloadActions(), imagePreviewActions = stickerActions(room = false))
        }

        val imageRect = bounds(compose.onNodeWithTag(chatStickerImageTag, useUnmergedTree = true))
        val surfaceRect = bounds(surface())
        holdOn(surface()) { _, dp -> Offset((imageRect.left - surfaceRect.left).value * dp - 6 * dp, ((imageRect.top + imageRect.bottom) / 2 - surfaceRect.top).value * dp) }

        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("添加到我的表情包").assertExists()
        compose.onNodeWithText("删除").assertExists()
    }

    @Test
    fun `image file attachment renders a resolved preview image`() {
        val cache = testMediaCacheController(files = mapOf("https://assets.test/uploads/photo-thumb.png" to solidPng(1600, 900, 0xFF3A6EA5.toInt())))
        compose.setChatContent(cache = cache) { _ ->
            MessageBubbleForTest(
                message =
                    chatTestMessage(
                        type = "file",
                        body = "photo.png",
                        attachments =
                            listOf(
                                MessageAttachment(
                                    type = "file",
                                    name = "photo.png",
                                    asset =
                                        UploadedAsset(
                                            id = "asset_photo",
                                            url = "/uploads/photo.png",
                                            thumbnailUrl = "/uploads/photo-thumb.png",
                                            mimeType = "image/png",
                                            filename = "photo.png",
                                            sizeBytes = 2048,
                                            width = 1600,
                                            height = 900,
                                        ),
                                ),
                            ),
                    ),
                downloadActions = chatTestDownloadActions(),
            )
        }
        settleImages()

        val image = compose.onNodeWithTag(chatFilePreviewImageTag, useUnmergedTree = true)
        val rect = bounds(image)
        assertEquals(320f, (rect.right - rect.left).value, 0.01f)
        assertEquals(180f, (rect.bottom - rect.top).value, 0.01f)
        assertEquals(Color(0xFF3A6EA5.toInt()), compose.pixelIn(image, 160f, 90f))
    }

    @Test
    fun `non-image file attachment keeps the file tile without preview`() {
        compose.setChatContent { _ ->
            MessageBubbleForTest(
                message =
                    chatTestMessage(
                        type = "file",
                        body = "report.pdf",
                        attachments =
                            listOf(
                                MessageAttachment(
                                    type = "file",
                                    name = "report.pdf",
                                    asset =
                                        UploadedAsset(
                                            id = "asset_report",
                                            url = "/uploads/report.pdf",
                                            thumbnailUrl = null,
                                            mimeType = "application/pdf",
                                            filename = "report.pdf",
                                            sizeBytes = 2048,
                                        ),
                                ),
                            ),
                    ),
                downloadActions = chatTestDownloadActions(),
            )
        }

        compose.onNodeWithTag(chatFilePreviewImageTag, useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasIcon(Icons.Outlined.PictureAsPdf), useUnmergedTree = true).assertExists()
        compose.onTooltip("report.pdf").assertExists()
    }

    @Test
    fun `voice attachment renders a playable waveform bubble`() {
        val toggles = mutableListOf<String>()
        val message = chatTestVoiceMessage(15.seconds)
        var activeMessageId by mutableStateOf<String?>(null)
        compose.setChatContent { _ ->
            MessageBubbleForTest(
                message = message,
                downloadActions = chatTestDownloadActions(),
                voicePlaybackActions = ChatVoicePlaybackActions(activeMessageId = activeMessageId, onToggle = { messageId, resolvedUrl -> toggles.add("$messageId|$resolvedUrl") }),
            )
        }

        compose.onNodeWithText("15\"").assertExists()
        val waveform = bounds(compose.onNodeWithTag(voiceWaveformTag, useUnmergedTree = true))
        // Within a device pixel: Compose lays out on whole pixels.
        assertEquals(voiceWaveformWidth(15.seconds).toFloat(), (waveform.right - waveform.left).value, 0.5f / compose.density.density)
        val play = compose.onNode(hasIcon(Icons.Rounded.PlayArrow), useUnmergedTree = true)
        play.assertExists()
        compose.onNode(hasIcon(Icons.Outlined.AudioFile), useUnmergedTree = true).assertDoesNotExist()
        compose.onTooltip("voice_1.m4a").assertDoesNotExist()
        assertEquals(Color.White, compose.onNodeWithText("15\"").textColor())

        play.performClick()
        assertEquals(listOf("client_1|https://assets.test/uploads/voice_1.m4a"), toggles)

        activeMessageId = "client_1"
        compose.waitForIdle()

        compose.onNode(hasIcon(Icons.Rounded.Stop), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `voice waveform width follows attachment duration`() {
        var duration by mutableStateOf<Duration>(3.seconds)
        compose.setChatContent { _ -> MessageBubbleForTest(message = chatTestVoiceMessage(duration), downloadActions = chatTestDownloadActions()) }

        fun renderWidth(next: Duration): Float {
            duration = next
            compose.waitForIdle()
            return bounds(compose.onNodeWithTag(voiceWaveformTag, useUnmergedTree = true)).let { it.right - it.left }.value
        }

        val shortWidth = renderWidth(3.seconds)
        val mediumWidth = renderWidth(30.seconds)
        val longWidth = renderWidth(5.minutes)

        assertTrue(mediumWidth > shortWidth)
        assertTrue(longWidth > mediumWidth)
        assertEquals(kVoiceWaveformMaxWidth.toFloat(), longWidth, 0.01f)
    }

    @Test
    fun `voice duration labels keep a consistent trailing gap`() {
        var duration by mutableStateOf<Duration>(2.seconds)
        compose.setChatContent { _ -> MessageBubbleForTest(message = chatTestVoiceMessage(duration), downloadActions = chatTestDownloadActions()) }

        fun renderTrailingGap(next: Duration, label: String): Float {
            duration = next
            compose.waitForIdle()
            val bodyRight = bounds(compose.onNodeWithTag(voiceBodyTag, useUnmergedTree = true)).right
            val labelRight = bounds(compose.onNodeWithText(label)).right
            return (bodyRight - labelRight).value
        }

        val secondsGap = renderTrailingGap(2.seconds, "2\"")
        val minutesGap = renderTrailingGap(65.seconds, "1'05\"")

        assertEquals(minutesGap, secondsGap, 0.01f)
    }

    @Test
    fun `message sender profile can enter a common room`() {
        val controller = TextEditingController()
        var openedRoom: PublicRoom? = null
        val sender =
            UserSummary(
                id = "user_1",
                username = "logan",
                displayName = "Logan",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
                commonRooms = listOf(UserCommonRoom(id = "room_common", rid = "R200", name = "Common room")),
            )
        compose.setChatContent { _ ->
            ChatPaneForTest(
                controller = controller,
                room = chatTestRoomDetail,
                messages = listOf(chatTestMessage(type = "text", body = "hello", sender = sender)),
                onEnterProfileRoom = { room -> openedRoom = room },
            )
        }

        // Dart hovered the avatar and then the common room; on Android both open on tap.
        compose.onNode(hasText("LO"), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("CO") and hasAnyAncestor(hasTestTag(hoverCardPanelTag)), useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("进入房间").performClick()
        compose.waitForIdle()

        assertEquals("room_common", openedRoom?.id)
    }

    @Test
    fun `attachment bubbles keep sender and time outside the bubble`() {
        val messages =
            listOf(
                chatTestMessage(
                    type = "file",
                    body = "report.pdf",
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "file",
                                name = "report.pdf",
                                asset = UploadedAsset(id = "asset_report", url = "/uploads/report.pdf", thumbnailUrl = null, mimeType = "application/pdf", filename = "report.pdf", sizeBytes = 2048),
                            ),
                        ),
                ),
                chatTestVoiceMessage(15.seconds),
                chatTestMessage(
                    type = "sticker",
                    body = "[表情] wave",
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "sticker",
                                name = "wave",
                                asset = UploadedAsset(id = "asset_sticker", url = "/stickers/wave.webp", thumbnailUrl = "/stickers/wave-thumb.webp", mimeType = "image/webp"),
                            ),
                        ),
                ),
            )
        var current by mutableStateOf(messages.first())
        compose.setChatContent { _ -> MessageBubbleForTest(message = current, downloadActions = chatTestDownloadActions()) }

        for (message in messages) {
            current = message
            compose.waitForIdle()
            compose.onNodeWithText("Logan").assertDoesNotExist()
            compose.onNodeWithText(formatMessageTime(message.createdAt)).assertDoesNotExist()
        }
    }
}

package com.gangchat.client.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.gangchat.client.app.roomCreatedAtLabel
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.shell.assertDpEquals
import com.gangchat.client.shell.centerY
import com.gangchat.client.shell.rect
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.avatarInitials
import com.gangchat.client.ui.onAllTooltips
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.textLayout
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shell's Android integration and the cases deferred to the shell port:
 * the live-sync cases of settings_widget_tests.dart (task removal, settings
 * volumes and mute coupling), the shell parts of
 * room_management_widget_tests.dart (members entry, room settings header
 * refresh, the message history pane, the '管理成员' profile action), and
 * the Android system integration the Dart suite could not reach on its
 * desktop host (notification taps, lifecycle and background service,
 * system notifications, badges, system back).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class HomeShellAndroidTest : HomeShellTestBase() {
    private fun tooltipCount(message: String) = compose.onAllTooltips(message).fetchSemanticsNodes().size

    private val root = SemanticsMatcher("root") { it.parent == null }

    private fun mouseHover(tag: String) {
        compose.onAllNodes(root).onFirst().performMouseInput { moveTo(Offset(1f, 1f)) }
        tag(tag).performMouseInput { moveTo(center) }
        compose.pumpShell()
    }

    private fun mouseAway() {
        compose.onAllNodes(root).onFirst().performMouseInput { moveTo(Offset(1f, 1f)) }
        compose.settleShell()
    }

    private fun expectLiveVolumeFill(label: String, volume: Double) {
        val slider = tag("live-volume-slider:$label").rect()
        val thumb = tag("live-volume-thumb:$label").rect()
        val fill = tag("live-volume-fill:$label").rect()
        val expectedHeight = ((slider.bottom - slider.top) - (thumb.bottom - thumb.top)) * volume.toFloat()
        assertDpEquals(expectedHeight, fill.bottom - fill.top, tolerance = 1f)
    }

    // --- settings_widget_tests.dart ------------------------------------------

    @Test
    fun `Android task removal leaves live before native process termination`() {
        val events = mutableListOf<String>()
        var blockNextMicStateRequest = false
        val blockedStateRequestStarted = CompletableDeferred<Unit>()
        val releaseBlockedStateRequest = CompletableDeferred<Unit>()
        val api =
            HomeShellTestApi(
                beforeLiveStateResponse = { update ->
                    if (blockNextMicStateRequest && update["connection_state"] == null && update["mic_muted"] is Boolean) {
                        blockNextMicStateRequest = false
                        blockedStateRequestStarted.complete(Unit)
                        releaseBlockedStateRequest.await()
                    }
                },
            )
        val liveSession = FakeHomeLiveSession()
        val androidSystemService = FakeHomeAndroidSystemService(supported = true)
        try {
            compose.setHomePage(
                app = homeTestAppContext(api, onExitSessionForAppExit = { events.add("exit-session") }),
                audioDeviceStore = FakeHomeAudioDeviceStore(),
                liveSessionController = FakeHomeLiveSessionController(session = liveSession),
                realtime = RecordingRealtimeService(events),
                androidSystemService = androidSystemService,
            )
            compose.settleShell()

            openAlphaAndJoinLive()

            blockNextMicStateRequest = true
            tag("live-control:mic").performClick()
            compose.pumpShellUntil { blockedStateRequestStarted.isCompleted }

            val removal = androidSystemService.removeTask()
            compose.pumpShellUntil { removal.completed.isCompleted }
            releaseBlockedStateRequest.complete(Unit)
            compose.settleShell()

            assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/live/me"))
            assertTrue(api.liveStateUpdates.any { it["connection_state"] == "left" })
            assertEquals(1, liveSession.disconnects)
            assertEquals(listOf("realtime-stop", "exit-session"), events)
        } finally {
            releaseBlockedStateRequest.complete(Unit)
            androidSystemService.dispose()
        }
    }

    @Test
    fun `settings audio volumes sync to live channel sliders`() {
        val liveSession = FakeHomeLiveSession()
        compose.setHomePage(
            app = homeTestAppContext(),
            audioDeviceStore = FakeHomeAudioDeviceStore(inputVolume = 0.62, outputVolume = 0.27),
            liveSessionController =
                FakeHomeLiveSessionController(session = liveSession, audioDeviceStore = FakeHomeAudioDeviceStore(inputVolume = 0.35, outputVolume = 0.75)),
        )
        compose.settleShell()

        openAlphaAndJoinLive()

        assertEquals(0.35, liveSession.inputVolumes.last(), 1e-9)
        assertEquals(0.75, liveSession.outputVolumes.last(), 1e-9)

        tapTooltip("设置")
        compose.onAllNodes(hasText("语音和视频")).onFirst().performClick()
        // Device enumeration can keep the refresh spinner turning, so pump
        // frames instead of waiting for every animation to settle.
        compose.pumpShell()
        compose.pumpShell(300)

        assertEquals(1, compose.textNodes("设置"))
        assertEquals(1, compose.textNodes("输入音量"))
        assertEquals(0.62, liveSession.inputVolumes.last(), 1e-9)
        assertEquals(0.27, liveSession.outputVolumes.last(), 1e-9)
        assertFalse(liveSession.micMutes.contains(true))
        assertTrue(liveSession.outputMutes.isEmpty())

        tapTooltip("设置")
        openLiveChannelFromHeader()

        mouseHover("live-control:mic")
        expectLiveVolumeFill("麦克风输入音量", 0.62)
        mouseHover("live-control:headphones")
        expectLiveVolumeFill("语音输出音量", 0.27)
        mouseAway()
    }

    @Test
    fun `settings nonzero audio volumes clear live mute states`() {
        val api = HomeShellTestApi()
        val liveSession = FakeHomeLiveSession()
        compose.setHomePage(
            app = homeTestAppContext(api),
            audioDeviceStore = FakeHomeAudioDeviceStore(inputVolume = 0.62, outputVolume = 0.27),
            liveSessionController =
                FakeHomeLiveSessionController(session = liveSession, audioDeviceStore = FakeHomeAudioDeviceStore(inputVolume = 0.35, outputVolume = 0.75)),
        )
        compose.settleShell()

        openAlphaAndJoinLive()

        tapTag("live-control:headphones")
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.MicOff))
        assertTrue(tagHasIcon("live-control:headphones", Icons.Filled.HeadsetOff))

        api.liveStateUpdates.clear()
        liveSession.micMutes.clear()
        liveSession.outputMutes.clear()

        tapTooltip("设置")
        compose.onAllNodes(hasText("语音和视频")).onFirst().performClick()
        compose.pumpShell()
        compose.pumpShell(300)

        assertEquals(0.62, liveSession.inputVolumes.last(), 1e-9)
        assertEquals(0.27, liveSession.outputVolumes.last(), 1e-9)
        assertTrue(liveSession.micMutes.contains(false))
        assertTrue(liveSession.outputMutes.contains(false))
        assertTrue(api.liveStateUpdates.any { it["mic_muted"] == false })
        assertTrue(api.liveStateUpdates.any { it["headphones_muted"] == false })

        tapTooltip("设置")
        openLiveChannelFromHeader()

        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.Mic))
        assertTrue(tagHasIcon("live-control:headphones", Icons.Filled.Headphones))
    }

    @Test
    fun `microphone unmute clears headphones mute`() {
        val api = HomeShellTestApi()
        val liveSession = FakeHomeLiveSession()
        compose.setHomePage(
            app = homeTestAppContext(api),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession, audioDeviceStore = FakeHomeAudioDeviceStore()),
        )
        compose.settleShell()

        openAlphaAndJoinLive()

        tag("live-control:headphones").performClick()
        compose.pumpShell()
        assertEquals(0.0, liveSession.outputVolumes.last(), 0.0)
        assertEquals(0.0, liveSession.inputVolumes.last(), 0.0)
        assertTrue(liveSession.micMutes.contains(true))
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.MicOff))
        assertTrue(tagHasIcon("live-control:headphones", Icons.Filled.HeadsetOff))
        compose.settleShell()
        assertEquals(true, api.liveStateUpdates.last()["mic_muted"])
        assertEquals(true, api.liveStateUpdates.last()["headphones_muted"])

        api.liveStateUpdates.clear()
        liveSession.micMutes.clear()
        liveSession.outputMutes.clear()

        tag("live-control:mic").performClick()
        compose.pumpShell()
        assertEquals(0.35, liveSession.inputVolumes.last(), 1e-9)
        assertEquals(0.75, liveSession.outputVolumes.last(), 1e-9)
        assertTrue(liveSession.micMutes.contains(false))
        assertTrue(liveSession.outputMutes.contains(false))
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.Mic))
        assertTrue(tagHasIcon("live-control:headphones", Icons.Filled.Headphones))
        compose.settleShell()
        assertEquals(false, api.liveStateUpdates.last()["mic_muted"])
        assertEquals(false, api.liveStateUpdates.last()["headphones_muted"])
    }

    // --- room_management_widget_tests.dart (the shell's parts) ---------------

    /**
     * The shell's parts of `authenticated home shell opens room management
     * with real APIs`: the members entry and its badge, the room settings
     * save refreshing the header and sidebar, the message history pane the
     * shell builds into room settings, the room playlists pane, and back.
     * The panels themselves are covered by RoomManagementWidgetTest.
     */
    @Test
    fun `authenticated home shell opens room management with real APIs`() {
        val api = HomeShellTestApi()
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        tapText("Alpha Room")
        assertTrue(tagExists(roomMembersEntryBadgeTag))

        tapTooltip("房间成员")
        assertTrue(compose.textNodes("成员") >= 1)
        assertEquals(1, compose.textNodes("房间成员"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/members"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/join-requests"))

        compose.onAllTooltips("返回").onLast().performClick()
        compose.settleShell()

        tapTooltip("房间设置")
        assertEquals(1, compose.textNodes("房间设置"))
        for (text in listOf("个人偏好", "消息记录", "表情包", "歌单")) assertEquals(text, 1, compose.textNodes(text))
        assertTrue(compose.onNodeWithTextRect("表情包").centerX < compose.onNodeWithTextRect("歌单").centerX)
        assertEquals("server-alpha", readOnlyFieldText(roomSettingsRidTag))
        assertEquals(roomCreatedAtLabel(DateTime.parse("2026-06-01T00:00:00Z")), readOnlyFieldText(roomSettingsCreatedAtTag))

        compose.textFieldIn(roomSettingsNameInputTag).performTextReplacement("Alpha Renamed")
        compose.settleShell()
        scrollFormTo(hasText("保存房间设置") and hasClickAction())
        compose.onNode(hasText("保存房间设置") and hasClickAction()).performClick()
        compose.settleShell()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha"))
        assertEquals(1, compose.textNodes("房间信息已保存"))
        assertTrue(compose.onAllNodes(hasText("Alpha Renamed", substring = true)).fetchSemanticsNodes().isNotEmpty())

        tapText("消息记录")

        assertTrue(tagExists(roomMessageHistorySearchTag))
        assertEquals(1, compose.textNodes("Hello from Morgan"))
        assertTrue(tagExists(roomMessageHistoryDateFilterTag))
        assertTrue(tagExists(roomMessageHistoryMemberFilterTag))
        val historyRow = roomMessageHistoryRowTag("msg-1")
        val historyTime = roomMessageHistoryTimeTag("msg-1")
        val historyAvatar = roomMessageHistoryAvatarTag("msg-1")
        assertTrue(tag(historyRow).rect().let { it.bottom - it.top } >= 64.dp)
        assertTrue(compose.exists(inTag(historyRow, hasTestTag(historyTime))))
        assertTrue(tag(historyTime).rect().right < tag(historyAvatar).rect().left)
        assertTrue(compose.exists(inTag(historyAvatar, hasText(avatarInitials("Morgan Account")))))
        assertTrue(tagExists(roomMessageHistoryRowTag("msg-system")))
        assertFalse(compose.exists(inTag(roomMessageHistoryRowTag("msg-system"), hasTestTag(roomMessageHistoryAvatarTag("msg-system")))))
        assertDpEquals(38.dp, widthOf(historyAvatar))
        val timeText = tag(historyTime).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        assertFalse(timeText.contains("\n"))
        val systemJump = tag(roomMessageHistoryJumpTag("msg-system")).rect()
        val systemRow = tag(roomMessageHistoryRowTag("msg-system")).rect()
        assertTrue(systemJump.top >= systemRow.top - 0.01.dp)
        assertTrue(systemJump.bottom <= systemRow.bottom + 0.01.dp)
        assertTrue(compose.onNodeWithTextRect("链接").centerX < compose.onNodeWithTextRect("语音").centerX)
        assertTrue(compose.onNodeWithTextRect("语音").centerX < compose.onNodeWithTextRect("表情").centerX)

        for ((label, category) in listOf("图片" to "images", "文件" to "files", "音乐" to "music")) {
            tapText(label)
            assertTrue(
                label,
                api.requestedUris.any { uri ->
                    uri.path == "/api/v1/rooms/server-alpha/message-history" && uri.queryParameters["category"] == category
                },
            )
        }

        tapTag(roomMessageHistoryMemberFilterTag)
        val memberOption = messageHistoryMemberTag("user-2")
        assertTrue(tagExists(memberOption))
        assertTrue(compose.exists(inTag(memberOption, hasText("@morgan"))))
        assertFalse(compose.exists(inTag(memberOption, hasText("10000021", substring = true))))
        assertTrue(tag(messageHistoryMemberTag(null)).rect().let { it.bottom - it.top } >= 64.dp)
        assertTrue(tagHasIcon(messageHistoryMemberTag(null), Icons.Filled.RadioButtonChecked))
        assertTrue(tagHasIcon(memberOption, Icons.Filled.RadioButtonUnchecked))
        assertTrue(compose.exists(inTag(memberOption, hasText("成员"))))
        assertDpEquals(36.dp, widthOf(messageHistoryMemberAvatarTag("user-2")))
        tapTag(messageHistoryMemberAvatarTag("user-2"))
        assertTrue(tagExists(userProfileCardAvatarPreviewTag))
        assertTrue(tagHasIcon(memberOption, Icons.Filled.RadioButtonUnchecked))
        tapTag(messageHistoryMemberAvatarTag("user-2"))

        tag(memberOption).performTouchInput { click(Offset(centerX, 2.dp.toPx())) }
        compose.settleShell()
        assertTrue(tagHasIcon(memberOption, Icons.Filled.RadioButtonChecked))
        compose.onAllNodes(hasText("取消") and hasClickAction()).onLast().performClick()
        compose.settleShell()

        tapTag(roomMessageHistoryBatchManageTag)
        val row = tag(historyRow).rect()
        val selectBox = tag(roomMessageHistorySelectTag("msg-1")).rect()
        val jumpButton = tag(roomMessageHistoryJumpTag("msg-1")).rect()
        assertDpEquals(row.centerY, selectBox.centerY)
        assertDpEquals(row.centerY, jumpButton.centerY)
        fun selected(): Boolean =
            compose.exists(
                (hasTestTag(roomMessageHistorySelectTag("msg-1")) or hasAnyAncestor(hasTestTag(roomMessageHistorySelectTag("msg-1")))) and
                    SemanticsMatcher("checked") {
                        it.config.getOrNull(SemanticsProperties.ToggleableState) == androidx.compose.ui.state.ToggleableState.On
                    },
            )
        assertFalse(selected())
        tapTag(historyAvatar)
        assertTrue(tagExists(userProfileCardAvatarPreviewTag))
        assertFalse(selected())
        tapTag(historyAvatar)
        tapTag(historyRow)
        assertTrue(selected())

        tapText("歌单")
        assertEquals(1, compose.textNodes("房间歌单"))
        assertEquals(1, compose.textNodes("Alpha 房间歌单"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/music-box/playlists"))
        assertTrue(tagExists(com.gangchat.client.settings.createPersonalMusicPlaylistTag))

        compose.onAllTooltips("返回").onLast().performClick()
        compose.settleShell()

        assertEquals(1, compose.onAllNodes(hasText("Alpha Renamed", substring = true)).fetchSemanticsNodes().size.coerceAtMost(1))
        assertTrue(compose.onAllNodes(hasText("Alpha Renamed", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp-mdpi")
    fun `android message history enlarges user avatars without changing system avatars`() {
        compose.setHomePage(app = homeTestAppContext())
        compose.settleShell()

        tapText("Alpha Room")
        tapTooltip("房间设置")
        tapText("消息记录")

        assertDpEquals(38.dp, widthOf(roomMessageHistoryAvatarTag("msg-1")))
        // The system row's content avatar (Dart's 18 dp `ChatSystemMessageContent`
        // avatar) carries no tag; its initials give its size.
        val systemRow = roomMessageHistoryRowTag("msg-system")
        val systemAvatar = compose.onAllNodes(inTag(systemRow, hasText(avatarInitials("Morgan Account"))), useUnmergedTree = true)
        assertEquals(1, systemAvatar.fetchSemanticsNodes().size)
        assertEquals(18f, avatarSizeOf(systemAvatar.onFirst()), 0.01f)
        assertTrue(widthOf(roomMessageHistoryTimeTag("msg-1")) < 82.dp)
    }

    @Test
    fun `message profile can jump to member management by UID`() {
        val api = HomeShellTestApi()
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        tapText("Alpha Room")

        // Dart hovered the message avatar; Android opens its card with a tap.
        // The avatar's initials inside the message list's bounds.
        val list = tag(chatMessageListTag).fetchSemanticsNode().boundsInRoot
        val messageAvatars =
            compose.onAllNodes(hasText(avatarInitials("Morgan")), useUnmergedTree = true).fetchSemanticsNodes().filter { node ->
                list.contains(node.boundsInRoot.center)
            }
        assertEquals(1, messageAvatars.size)
        compose.onNode(SemanticsMatcher("message avatar") { it.id == messageAvatars.single().id }, useUnmergedTree = true).performClick()
        compose.settleShell()

        assertEquals(1, compose.onAllNodes(hasText("管理成员") and hasClickAction()).fetchSemanticsNodes().size)
        compose.onNode(hasText("管理成员") and hasClickAction()).performClick()
        compose.settleShell()

        assertTrue(compose.textNodes("成员") >= 1)
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/members"))
        assertTrue(compose.exists(hasSetTextAction() and hasText("uid-2")))
        assertTrue(compose.onAllNodes(hasText("Morgan", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    // --- Android system integration ------------------------------------------

    @Test
    fun `Android notification taps open their room`() {
        val api = HomeShellTestApi()
        val android = FakeHomeAndroidSystemService(supported = true, initialNotificationRoomId = "server-beta")
        try {
            compose.setHomePage(app = homeTestAppContext(api), androidSystemService = android)
            compose.settleShell()

            // The notification that launched the app opens its room once the
            // room list has loaded.
            assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-beta/messages"))
            assertTrue(isSelected(tag(homeSidebarRoomTag("server-beta"))))

            // A later tap while the app runs.
            android.selectNotificationRoom("server-alpha")
            compose.settleShell()
            assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/messages"))
            assertTrue(isSelected(tag(homeSidebarRoomTag("server-alpha"))))
            assertTrue(android.events.contains("cancel-room-notification:server-alpha"))
        } finally {
            android.dispose()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun `Android notification tap opens the room screen on phones`() {
        val api = HomeShellTestApi()
        val android = FakeHomeAndroidSystemService(supported = true)
        try {
            compose.setHomePage(app = homeTestAppContext(api), androidSystemService = android)
            compose.settleShell()
            assertTrue(tagExists(homeNarrowRoomListTag))

            android.selectNotificationRoom("server-alpha")
            compose.settleShell()

            assertTrue(tagExists(homeNarrowContentTag))
            assertEquals(1, compose.textNodes("Hello from Morgan"))
            pressBack()
            assertTrue(tagExists(homeNarrowRoomListTag))
        } finally {
            android.dispose()
        }
    }

    @Test
    fun `Android badge follows unread rooms`() {
        val android = FakeHomeAndroidSystemService(supported = true)
        try {
            compose.setHomePage(app = homeTestAppContext(HomeShellTestApi(alphaRoomUnreadCount = 3)), androidSystemService = android)
            compose.settleShell()
            assertEquals("badge:3", android.events.last { it.startsWith("badge:") })

            tapText("Alpha Room")
            assertTrue(android.events.contains("cancel-room-notification:server-alpha"))
            assertEquals("badge:0", android.events.last { it.startsWith("badge:") })
        } finally {
            android.dispose()
        }
    }

    @Test
    fun `Android lifecycle moves voice to the background service`() {
        val android = FakeHomeAndroidSystemService(supported = true)
        try {
            compose.setHomePage(app = homeTestAppContext(), androidSystemService = android)
            compose.settleShell()
            android.events.clear()

            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.settleShell()
            // onPause (inactive), then onStop (hidden, paused): the service
            // starts while the activity is still visible.
            assertEquals(listOf("enter-background", "enter-background", "enter-background"), android.events.filter { it.startsWith("enter-") })

            android.events.clear()
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.settleShell()
            assertEquals("enter-foreground", android.events.last { it.startsWith("enter-") })
        } finally {
            android.dispose()
        }
    }

    @Test
    fun `Android background room messages raise a system notification instead of a sound`() {
        val realtime = FakeHomeRealtimeService()
        val sound = RecordingMessageNotificationSoundPlayer()
        val android = FakeHomeAndroidSystemService(supported = true)

        fun update(messageId: String): Map<String, Any?> {
            val room = homeShellRoomCardJson(id = "server-alpha", name = "Alpha Room", memberCount = 2, unreadCount = 8)
            room["last_message"] =
                mapOf(
                    "id" to messageId,
                    "type" to "text",
                    "sender_display_name" to "Morgan",
                    "body_preview" to "Realtime message",
                    "created_at" to "2026-06-12T02:00:00Z",
                )
            room["update_reason"] = "message_created"
            room["updated_at"] = "2026-06-12T02:00:00Z"
            return room
        }

        try {
            compose.setHomePage(
                app = homeTestAppContext(),
                realtime = realtime,
                messageNotificationSoundPlayer = sound,
                androidSystemService = android,
            )
            compose.settleShell()

            realtime.add("room_updated", update("message-foreground"))
            compose.settleShell()
            assertEquals(1, sound.volumes.size)
            assertFalse(android.events.any { it.startsWith("show-room-message:") })

            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.settleShell()
            realtime.add("room_updated", update("message-background"))
            compose.settleShell()

            assertEquals(1, sound.volumes.size)
            val shown = android.events.filter { it.startsWith("show-room-message:") }
            assertEquals(1, shown.size)
            assertTrue(shown.single().startsWith("show-room-message:server-alpha:Alpha Room:Morgan:Realtime message:"))
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.settleShell()
        } finally {
            android.dispose()
        }
    }

    @Test
    fun `Android logout clears notifications and leaves the background service`() {
        var logoutCount = 0
        val android = FakeHomeAndroidSystemService(supported = true)
        try {
            compose.setHomePage(app = homeTestAppContext(onLogout = { logoutCount++ }), androidSystemService = android)
            compose.settleShell()
            android.events.clear()

            tapTooltip("退出登录")
            compose.onAllNodes(hasText("退出登录") and hasClickAction()).onLast().performClick()
            compose.settleShell()

            assertEquals(1, logoutCount)
            assertTrue(android.events.contains("clear-message-notifications"))
            assertTrue(android.events.contains("enter-foreground"))
            assertTrue(android.events.indexOf("clear-message-notifications") < android.events.indexOf("enter-foreground"))
        } finally {
            android.dispose()
        }
    }

    @Test
    fun `system back closes the open settings before leaving the app`() {
        compose.setHomePage(app = homeTestAppContext())
        compose.settleShell()

        tapTooltip("设置")
        assertTrue(compose.exists(inTag(com.gangchat.client.ui.settingsSideNavigationTag, hasText("用户资料"))))

        pressBack()
        assertFalse(compose.exists(inTag(com.gangchat.client.ui.settingsSideNavigationTag, hasText("用户资料"))))
        assertFalse(compose.activity.isFinishing)

        tapText("Alpha Room")
        pressBack()
        // Nothing left for the shell: the activity takes the back gesture.
        assertTrue(compose.activity.isFinishing)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithTextRect(text: String) =
        onNode(hasText(text), useUnmergedTree = true).rect()

    private val androidx.compose.ui.unit.DpRect.centerX get() = (left + right) / 2

    /** The text of the (read-only) text field carrying or inside [fieldTag]. */
    private fun readOnlyFieldText(fieldTag: String): String =
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and
                (hasTestTag(fieldTag) or hasAnyAncestor(hasTestTag(fieldTag))),
            useUnmergedTree = true,
        ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /** An avatar's size from its initials (drawn at 0.36 of the avatar size). */
    private fun avatarSizeOf(initials: androidx.compose.ui.test.SemanticsNodeInteraction): Float =
        initials.textLayout().layoutInput.style.fontSize.value / 0.36f
}

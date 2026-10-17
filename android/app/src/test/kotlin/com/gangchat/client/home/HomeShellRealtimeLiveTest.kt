package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.RealtimeConnectionStatus
import com.gangchat.client.live.liveVideoTrackRendererForTest
import com.gangchat.client.live.resetLiveVideoTrackRendererForTest
import com.gangchat.client.shell.assertDpEquals
import com.gangchat.client.shell.centerY
import com.gangchat.client.shell.rect
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.TooltipSemanticsKey
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.hasTooltip
import com.gangchat.client.ui.onAllTooltips
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.settingsSideNavigationTag
import com.gangchat.client.ui.textColor
import com.gangchat.client.widgets.EdgeInsets
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * test/widget/shell/gang_app_shell_test_parts/realtime_live_widget_tests.dart.
 *
 * Desktop-only cases not ported: `narrow close prompt stacks behavior
 * choices` (the window close prompt), `authenticated home shell keeps macOS
 * sidebar below title bar`, `windows narrow shell uses adaptive room
 * navigation` and `macOS narrow shell uses adaptive room navigation` (window
 * buttons and platform themes; Android's narrow navigation is covered by
 * `authenticated home shell uses separate narrow room list and chat screens`).
 * The message attention request of the first case is a desktop window call.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class HomeShellRealtimeLiveTest : HomeShellTestBase() {
    @After
    fun resetRenderer() = resetLiveVideoTrackRendererForTest()

    private fun tooltipCount(message: String) = compose.onAllTooltips(message).fetchSemanticsNodes().size

    private fun inUserSummary(matcher: SemanticsMatcher) = compose.exists(inTag(homeSidebarUserSummaryTag, matcher))

    private fun morganJson() = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan")

    private fun liveEvent(type: String, participantCount: Int, participants: List<Any?>, updatedAt: String? = null): Map<String, Any?> {
        val live = homeShellLiveStateJson(roomId = "server-alpha", participantCount = participantCount, participants = participants).toMutableMap()
        if (updatedAt != null) live["updated_at"] = updatedAt
        return mapOf("room_id" to "server-alpha", "participant_count" to participantCount, "preview" to emptyList<Any?>(), "live" to live)
    }

    /** The glyph colour of an icon node: its pixel farthest from the corner (background) colour. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.iconPixelColor(): Color {
        val pixels = captureToImage().toPixelMap()
        val background = pixels[0, 0]
        fun distance(color: Color) =
            kotlin.math.abs(color.red - background.red) + kotlin.math.abs(color.green - background.green) + kotlin.math.abs(color.blue - background.blue)
        var best = background
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                if (distance(color) > distance(best)) best = color
            }
        }
        return best
    }

    private fun Color.isCloseTo(other: Color, tolerance: Float = 0.06f): Boolean =
        kotlin.math.abs(red - other.red) <= tolerance &&
            kotlin.math.abs(green - other.green) <= tolerance &&
            kotlin.math.abs(blue - other.blue) <= tolerance

    private fun installBlackRenderer() {
        liveVideoTrackRendererForTest = { _, _, _ -> Box(Modifier.fillMaxSize().background(Color.Black)) }
    }

    @Test
    fun `only realtime all-policy message updates play and request attention`() {
        val realtime = FakeHomeRealtimeService()
        val sound = RecordingMessageNotificationSoundPlayer()
        compose.setHomePage(
            app = homeTestAppContext(HomeShellTestApi(alphaRoomUnreadCount = 7)),
            realtime = realtime,
            messageNotificationSoundPlayer = sound,
        )
        compose.settleShell()

        // Loading pre-existing unread messages on sign-in never produces a cue.
        assertTrue(sound.volumes.isEmpty())

        fun update(messageId: String, notificationPolicy: String = "all", reason: String? = "message_created"): Map<String, Any?> {
            val room = homeShellRoomCardJson(id = "server-alpha", name = "Alpha Room", memberCount = 2, unreadCount = 8)
            room["notification_policy"] = notificationPolicy
            room["last_message"] =
                mapOf(
                    "id" to messageId,
                    "type" to "text",
                    "sender_display_name" to "Morgan",
                    "body_preview" to "Realtime message",
                    "created_at" to "2026-06-12T02:00:00Z",
                )
            if (reason != null) room["update_reason"] = reason
            room["updated_at"] = "2026-06-12T02:00:00Z"
            return room
        }

        realtime.add("room_updated", update(messageId = "message-live-1"))
        compose.settleShell()

        assertEquals(1, sound.volumes.size)
        assertTrue(sound.volumes.single() > 0)

        // A duplicate delivery, a reconnect/history-style room snapshot and a
        // room outside the "all" policy must all remain silent.
        realtime.add("room_updated", update(messageId = "message-live-1"))
        realtime.add("room_updated", update(messageId = "message-history", reason = null))
        realtime.add("room_updated", update(messageId = "message-silent", notificationPolicy = "silent"))
        compose.settleShell()

        assertEquals(1, sound.volumes.size)
    }

    @Test
    fun `account suspension event immediately logs out every client`() {
        val realtime = FakeHomeRealtimeService()
        val liveSession = FakeHomeLiveSession()
        var logoutCalls = 0
        compose.setHomePage(
            app = homeTestAppContext(onLogout = { logoutCalls += 1 }),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            realtime = realtime,
        )
        compose.settleShell()

        realtime.add("account_suspended", mapOf("reason" to "账号已被封禁"))
        realtime.add("account_suspended", mapOf("reason" to "账号已被封禁"))
        compose.settleShell()

        assertEquals(1, logoutCalls)
        assertEquals(1, liveSession.disconnects)
    }

    @Test
    fun `authenticated home shell applies realtime live snapshots`() {
        val realtime = FakeHomeRealtimeService()
        compose.setHomePage(app = homeTestAppContext(), realtime = realtime)
        compose.settleShell()

        tapText("Alpha Room")
        assertTrue(tagExists(chatHeaderLiveButtonTag))
        // The header button carries a tooltip.
        assertTrue(
            compose.exists(
                SemanticsMatcher.keyIsDefined(TooltipSemanticsKey) and
                    (hasTestTag(chatHeaderLiveButtonTag) or hasAnyAncestor(hasTestTag(chatHeaderLiveButtonTag))),
            ) ||
                compose.exists(SemanticsMatcher.keyIsDefined(TooltipSemanticsKey) and androidx.compose.ui.test.hasAnyDescendant(hasTestTag(chatHeaderLiveButtonTag))),
        )
        assertTrue(compose.exists(inTag(chatHeaderLiveButtonTag, hasTestTag(chatHeaderRoomTitleTag))))
        val roomTitle = tag(chatHeaderRoomTitleTag)
        assertEquals("Alpha Room", roomTitle.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text })
        assertEquals(UiColors.text, roomTitle.textColor())
        assertEquals("2 名成员 · 1 人在线", tag(chatHeaderRoomMetaTag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text })
        assertFalse(compose.exists(inTag(chatHeaderLiveButtonTag, hasText("进入语音频道"))))

        openLiveChannelFromHeader()

        assertEquals(0, compose.textNodes("Riley"))
        assertEquals(1, compose.textNodes("2 名成员 · 1 人语音"))

        realtime.add(
            "live_participant_joined",
            liveEvent(
                "live_participant_joined",
                participantCount = 2,
                participants =
                    listOf(
                        homeShellLiveParticipantJson(user = morganJson(), liveSessionId = "live-session-morgan", micMuted = true),
                        homeShellLiveParticipantJson(
                            user =
                                homeShellUserJson(id = "user-3", username = "riley", displayName = "Riley").also {
                                    it["default_avatar_key"] = "green-2"
                                },
                            liveSessionId = "live-session-riley",
                        ),
                    ),
            ),
        )
        compose.settleShell()

        assertEquals(1, compose.textNodes("Riley"))
        assertEquals(1, compose.textNodes("2 名成员 · 2 人语音"))
    }

    @Test
    @Config(qualifiers = "w420dp-h740dp-xxhdpi")
    fun `Android joins voice when opening a remote screen share`() {
        val realtime = FakeHomeRealtimeService()
        val api = HomeShellTestApi()
        val liveSession = FakeHomeLiveSession()
        liveSession.testVideoTracks = listOf(liveVideoTrack(identity = "user-2", isScreenShare = true, isLocal = false))
        installBlackRenderer()
        compose.setHomePage(
            app = homeTestAppContext(api),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            realtime = realtime,
        )
        compose.settleShell()

        tapText("Alpha Room")
        openLiveChannelFromHeader()

        realtime.add(
            "live_participant_updated",
            liveEvent(
                "live_participant_updated",
                participantCount = 1,
                participants =
                    listOf(homeShellLiveParticipantJson(user = morganJson(), liveSessionId = "live-session-morgan", micMuted = true, screenSharing = true)),
            ),
        )
        compose.settleShell()

        tapTag("live-member:screen-share-thumbnail")

        assertEquals(1, liveSession.connectAttempts)
        assertEquals("live_panel", api.liveJoinRequests.single()["source"])
        assertTrue(liveSession.micMutes.contains(false))
        assertEquals(false, api.liveStateUpdates.last()["mic_muted"])
        assertEquals("user-2", liveSession.watchedScreenShareIdentity)
        assertEquals(listOf<String?>("user-2"), api.liveScreenViewUpdates)
        assertEquals(listOf("join", "state:online", "screen-view:user-2"), api.liveOperationLog)

        val volumeButton = "live-stage:screen-share-volume"
        assertTrue(tagExists(volumeButton))
        val initialScreenShareVolume = liveSession.screenShareVolume
        assertTrue(initialScreenShareVolume > 0)
        tag(volumeButton).performClick()
        compose.pumpShell()
        assertEquals(0.0, liveSession.screenShareVolumes.last(), 0.0)
        assertTrue(tagHasIcon(volumeButton, Icons.AutoMirrored.Filled.VolumeOff))

        tag(volumeButton).performClick()
        compose.pumpShell()
        assertEquals(initialScreenShareVolume, liveSession.screenShareVolumes.last(), 0.0)
        assertTrue(tagHasIcon(volumeButton, Icons.AutoMirrored.Filled.VolumeDown) || tagHasIcon(volumeButton, Icons.AutoMirrored.Filled.VolumeUp))

        tag(volumeButton).performTouchInput { longClick() }
        compose.pumpShell()
        val volumeSlider = "live-volume-slider:共享屏幕输出音量"
        assertTrue(tagExists(volumeSlider))
        tag(volumeSlider).performTouchInput { click(Offset(centerX, height - 1.dp.toPx())) }
        compose.pumpShell()
        assertEquals(0.0, liveSession.screenShareVolumes.last(), 0.02)
        assertTrue(tagHasIcon(volumeButton, Icons.AutoMirrored.Filled.VolumeOff))
        compose.pumpShell(3200)
    }

    @Test
    @Config(qualifiers = "w420dp-h740dp-xxhdpi")
    fun `leaving voice forgets the watched screen share before rejoin`() {
        val realtime = FakeHomeRealtimeService()
        val leaveStarted = CompletableDeferred<Unit>()
        val releaseLeave = CompletableDeferred<Unit>()
        val api =
            HomeShellTestApi(
                beforeLiveStateResponse = { update ->
                    if (update["connection_state"] == "left") {
                        leaveStarted.complete(Unit)
                        releaseLeave.await()
                    }
                },
            )
        val liveSession = FakeHomeLiveSession()
        liveSession.testVideoTracks = listOf(liveVideoTrack(identity = "user-2", isScreenShare = true, isLocal = false))
        installBlackRenderer()
        compose.setHomePage(
            app = homeTestAppContext(api),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            realtime = realtime,
        )
        compose.settleShell()

        tapText("Alpha Room")
        openLiveChannelFromHeader()
        realtime.add(
            "live_participant_updated",
            liveEvent(
                "live_participant_updated",
                participantCount = 1,
                participants =
                    listOf(homeShellLiveParticipantJson(user = morganJson(), liveSessionId = "live-session-morgan", micMuted = true, screenSharing = true)),
            ),
        )
        compose.settleShell()

        tapTag("live-member:screen-share-thumbnail")
        assertEquals("user-2", liveSession.watchedScreenShareIdentity)
        assertEquals(listOf<String?>("user-2"), api.liveScreenViewUpdates)

        tag("live-control:leave").performClick()
        compose.pumpShellUntil { leaveStarted.isCompleted }

        val join = "live-control:join"
        assertTrue(tagExists(join))
        fun joinEnabled() =
            compose.exists(
                (hasTestTag(join) or hasAnyAncestor(hasTestTag(join))) and hasClickAction() and
                    SemanticsMatcher("enabled") { it.config.getOrNull(SemanticsProperties.Disabled) == null },
            )
        assertFalse(joinEnabled())
        assertTrue(tagHasIcon(join, Icons.Filled.Call))
        assertFalse(compose.exists(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and hasAnyAncestor(hasTestTag(join))))

        releaseLeave.complete(Unit)
        compose.settleShell()
        assertNull(liveSession.watchedScreenShareIdentity)
        assertTrue(joinEnabled())

        tapTag(join)

        assertEquals(2, liveSession.connectAttempts)
        assertNull(liveSession.watchedScreenShareIdentity)
        assertEquals(listOf<String?>("user-2", null), api.liveScreenViewUpdates)
        assertFalse(tagExists("live-stage:screen-share-volume"))
    }

    @Test
    fun `authoritative live snapshots reconcile local audio controls`() {
        val realtime = FakeHomeRealtimeService()
        val liveSession = FakeHomeLiveSession()
        compose.setHomePage(
            app = homeTestAppContext(),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            realtime = realtime,
        )
        compose.settleShell()

        openAlphaAndJoinLive()
        tapTag("live-control:headphones")
        assertTrue(liveSession.localMicMuted)
        assertTrue(liveSession.outputMutes.last())

        realtime.add(
            "live_participant_updated",
            liveEvent(
                "live_participant_updated",
                participantCount = 2,
                updatedAt = "2099-06-05T08:00:00Z",
                participants =
                    listOf(
                        homeShellLiveParticipantJson(user = homeShellCurrentUserJson, liveSessionId = "live-session-joined"),
                        homeShellLiveParticipantJson(user = morganJson(), liveSessionId = "live-session-morgan", micMuted = true),
                    ),
            ),
        )
        compose.settleShell()

        assertFalse(liveSession.localMicMuted)
        assertFalse(liveSession.outputMutes.last())
        assertTrue(dockHasIcon(Icons.Filled.Mic))
        assertTrue(dockHasIcon(Icons.Filled.Headphones))
    }

    @Test
    fun `joined-room snapshots reconcile audio while another room is selected`() {
        val realtime = FakeHomeRealtimeService()
        val liveSession = FakeHomeLiveSession()
        compose.setHomePage(
            app = homeTestAppContext(),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            realtime = realtime,
        )
        compose.settleShell()

        openAlphaAndJoinLive()
        assertFalse(liveSession.localMicMuted)

        tapText("Beta Room")

        realtime.add(
            "live_participant_moderated",
            liveEvent(
                "live_participant_moderated",
                participantCount = 1,
                updatedAt = "2099-06-05T08:00:00Z",
                participants =
                    listOf(
                        homeShellLiveParticipantJson(
                            user = homeShellCurrentUserJson,
                            liveSessionId = "live-session-joined",
                            micMuted = true,
                            headphonesMuted = true,
                        ),
                    ),
            ),
        )
        compose.settleShell()

        assertTrue(liveSession.localMicMuted)
        assertTrue(liveSession.outputMutes.last())
    }

    @Test
    @Config(qualifiers = "w420dp-h740dp-xxhdpi")
    fun `Android keeps live state muted when microphone publication fails`() {
        val realtime = FakeHomeRealtimeService()
        val api = HomeShellTestApi()
        val liveSession = FakeHomeLiveSession(failMicUnmute = true)
        compose.setHomePage(
            app = homeTestAppContext(api),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            realtime = realtime,
        )
        compose.settleShell()

        tapText("Alpha Room")
        openLiveChannelFromHeader()
        compose.onNode(hasText("加入") and hasClickAction()).performClick()
        compose.settleShell()

        assertTrue(liveSession.micMutes.contains(false))
        assertTrue(liveSession.localMicMuted)
        assertEquals(true, api.liveStateUpdates.last()["mic_muted"])
    }

    @Test
    @Config(qualifiers = "w1180dp-h620dp-mdpi")
    fun `chat header live preview follows realtime live snapshots`() {
        val realtime = FakeHomeRealtimeService()

        fun participant(id: String, username: String, displayName: String, liveSessionId: String) =
            homeShellLiveParticipantJson(user = homeShellUserJson(id = id, username = username, displayName = displayName), liveSessionId = liveSessionId)

        compose.setHomePage(app = homeTestAppContext(), realtime = realtime)
        compose.settleShell()

        tapText("Alpha Room")

        // The preview's avatars show their initials; the count is its own node.
        fun previewAvatars(): Int =
            compose.onAllNodes(
                inTag(chatHeaderLivePreviewTag, SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)) and
                    !hasTestTag(chatHeaderLivePreviewCountTag),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size

        fun countText(): String = tag(chatHeaderLivePreviewCountTag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

        assertTrue(tagExists(chatHeaderLiveButtonTag))
        assertTrue(tagExists(chatHeaderLivePreviewTag))
        assertTrue(tagExists(chatHeaderLivePreviewIconTag))
        assertDpEquals(10.dp, tag(chatHeaderLiveButtonTag).rect().right - tag(chatHeaderLivePreviewTag).rect().right, tolerance = 1f)
        assertEquals(1, previewAvatars())
        assertTrue(tag(chatHeaderLivePreviewIconTag).rect().right < tag(chatHeaderLivePreviewCountTag).rect().left)
        assertTrue(countText().contains("1"))
        assertEquals(Color.White, tag(chatHeaderLivePreviewCountTag).textColor())

        realtime.add(
            "live_participant_joined",
            liveEvent(
                "live_participant_joined",
                participantCount = 2,
                participants =
                    listOf(
                        participant("user-2", "morgan", "Morgan", "live-session-morgan"),
                        participant("user-3", "riley", "Riley", "live-session-riley"),
                    ),
            ),
        )
        compose.settleShell()

        assertEquals(2, previewAvatars())
        assertDpEquals(10.dp, tag(chatHeaderLiveButtonTag).rect().right - tag(chatHeaderLivePreviewTag).rect().right, tolerance = 1f)
        assertTrue(countText().contains("2"))

        realtime.add(
            "live_participant_joined",
            liveEvent(
                "live_participant_joined",
                participantCount = 6,
                participants =
                    listOf(
                        participant("user-2", "morgan", "Morgan", "live-session-morgan"),
                        participant("user-3", "riley", "Riley", "live-session-riley"),
                        participant("user-4", "ivy", "Ivy", "live-session-ivy"),
                        participant("user-5", "taylor", "Taylor", "live-session-taylor"),
                        participant("user-6", "noah", "Noah", "live-session-noah"),
                        participant("user-7", "mina", "Mina", "live-session-mina"),
                    ),
            ),
        )
        compose.settleShell()

        assertEquals(5, previewAvatars())
        assertDpEquals(10.dp, tag(chatHeaderLiveButtonTag).rect().right - tag(chatHeaderLivePreviewTag).rect().right, tolerance = 1f)
        assertTrue(countText().contains("6"))
        assertEquals("2 名成员 · 1 人在线", tag(chatHeaderRoomMetaTag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text })
    }

    @Test
    fun `authenticated home shell shows realtime reconnecting marker`() {
        val realtime = FakeHomeRealtimeService()
        compose.setHomePage(app = homeTestAppContext(), realtime = realtime)
        compose.settleShell()

        assertFalse(inUserSummary(hasText("重连中", substring = true)))

        realtime.setStatus(RealtimeConnectionStatus.reconnecting)
        compose.settleShell()

        assertTrue(inUserSummary(hasText("重连中")))
        assertFalse(inUserSummary(hasText("在线")))

        realtime.setStatus(RealtimeConnectionStatus.connected)
        compose.settleShell()

        assertFalse(inUserSummary(hasText("重连中", substring = true)))
        assertTrue(inUserSummary(hasText("在线")))
    }

    @Test
    fun `authenticated home shell keeps live session on realtime reconnect`() {
        val realtime = FakeHomeRealtimeService()
        val api = HomeShellTestApi()
        val liveSession = FakeHomeLiveSession()
        compose.setHomePage(
            app = homeTestAppContext(api),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            realtime = realtime,
        )
        compose.settleShell()

        openAlphaAndJoinLive()

        assertEquals(1, liveSession.connectAttempts)
        api.liveJoinRequests.clear()
        api.liveStateUpdates.clear()

        tapTag("live-control:mic")
        assertEquals(true, api.liveStateUpdates.last()["mic_muted"])
        api.liveStateUpdates.clear()

        realtime.setStatus(RealtimeConnectionStatus.reconnecting)
        compose.settleShell()

        assertTrue(inUserSummary(hasText("重连中")))
        assertFalse(inUserSummary(hasText("语音")))

        realtime.setStatus(RealtimeConnectionStatus.connected)
        realtime.emitReconnect()
        compose.settleShell()

        assertTrue(api.liveJoinRequests.isEmpty())
        assertEquals(1, liveSession.connectAttempts)
        assertTrue(api.liveStateUpdates.isEmpty())
        assertFalse(inUserSummary(hasText("重连中", substring = true)))
        assertTrue(inUserSummary(hasText("语音")))
    }

    @Test
    fun `authenticated home shell restores live after LiveKit disconnect`() {
        val realtime = FakeHomeRealtimeService()
        val api = HomeShellTestApi()
        val liveSession = FakeHomeLiveSession()
        compose.setHomePage(
            app = homeTestAppContext(api),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            realtime = realtime,
        )
        compose.settleShell()

        openAlphaAndJoinLive()
        tapTag("live-control:mic")
        tapTag("live-control:camera")

        realtime.add(
            "live_participant_updated",
            liveEvent(
                "live_participant_updated",
                participantCount = 2,
                updatedAt = "2099-06-05T08:00:00Z",
                participants =
                    listOf(
                        homeShellLiveParticipantJson(
                            user = homeShellCurrentUserJson,
                            liveSessionId = "live-session-joined",
                            micMuted = true,
                            cameraOn = true,
                            cameraMirrored = true,
                        ),
                        homeShellLiveParticipantJson(user = morganJson(), liveSessionId = "live-session-morgan", micMuted = true),
                    ),
            ),
        )
        compose.settleShell()
        api.liveJoinRequests.clear()
        api.liveStateUpdates.clear()

        liveSession.emitUnexpectedDisconnect()
        compose.settleShell()

        assertTrue(api.liveJoinRequests.map { it["source"] }.contains("reconnect"))
        assertEquals(2, liveSession.connectAttempts)
        assertEquals(true, api.liveStateUpdates.last()["mic_muted"])
        assertEquals(false, api.liveStateUpdates.last()["headphones_muted"])
        assertEquals(true, api.liveStateUpdates.last()["camera_on"])
        assertEquals(true, api.liveStateUpdates.last()["camera_mirrored"])
        assertEquals(false, api.liveStateUpdates.last()["screen_sharing"])
        assertTrue(liveSession.cameraEnables.contains(true))
    }

    @Test
    fun `authenticated home shell footer opens settings and logs out`() {
        val api = HomeShellTestApi()
        var logoutCount = 0
        compose.setHomePage(app = homeTestAppContext(api, onLogout = { logoutCount++ }))
        compose.settleShell()

        val userSummaryRect = tag(homeSidebarUserSummaryTag).rect()
        val createRect = compose.onTooltip("创建房间").rect()
        val notificationsRect = compose.onTooltip("通知").rect()
        val settingsRect = compose.onTooltip("设置").rect()
        val logoutRect = compose.onTooltip("退出登录").rect()
        assertDpEquals(userSummaryRect.left, createRect.left)
        assertDpEquals(createRect.top, notificationsRect.top)
        assertDpEquals(createRect.bottom, notificationsRect.bottom)
        assertDpEquals(createRect.top, settingsRect.top)
        assertDpEquals(createRect.bottom, settingsRect.bottom)
        // The logout control lives inline in the top user summary bar.
        assertTrue(logoutRect.top >= userSummaryRect.top - 0.01.dp)
        assertTrue(logoutRect.bottom <= userSummaryRect.bottom + 0.01.dp)
        assertTrue(logoutRect.bottom < createRect.top)
        assertTrue(logoutRect.right <= userSummaryRect.right + 0.01.dp)

        tapTooltip("设置")

        assertEquals(1, compose.textNodes("设置"))
        assertEquals(0, compose.onAllNodes(hasText("设置 ·", substring = true)).fetchSemanticsNodes().size)
        assertTrue(compose.exists(inTag(settingsSideNavigationTag, hasText("用户资料"))))
        assertEquals(1, compose.textNodes("偏好设置"))
        assertEquals(1, tooltipCount("刷新设置"))
        assertEquals(1, compose.onAllNodes(hasSetTextAction() and hasText("Kai")).fetchSemanticsNodes().size)
        assertTrue(
            compose.exists(
                (hasTooltip("设置") or hasAnyAncestor(hasTooltip("设置"))) and
                    SemanticsMatcher("selected") { it.config.getOrNull(SemanticsProperties.Selected) == true },
            ),
        )

        val usernameField = compose.onNode(hasSetTextAction() and hasText("kai"))
        usernameField.assertExists()
        assertEquals(0, compose.onAllNodes(hasText("保存登录用户名") and hasClickAction()).fetchSemanticsNodes().size)
        assertEquals(0, compose.textNodes("合法"))

        usernameField.performTextReplacement("kai_new")
        compose.pumpShell(400)
        compose.settleShell()

        assertEquals(1, compose.textNodes("合法"))

        tapText("偏好设置")

        for (text in listOf("语言切换", "语言", "简体中文", "繁體中文", "English", "隐私和安全", "用户资料")) {
            assertEquals(text, 1, compose.textNodes(text))
        }
        tapText("English")
        val savePreferences = hasText("保存偏好设置") and hasClickAction()
        val list =
            compose.onAllNodes(
                androidx.compose.ui.test.hasScrollToNodeAction() and
                    androidx.compose.ui.test.hasAnyDescendant(hasText("语言切换")),
            ).onLast()
        scrollWithClock { list.performScrollToNode(savePreferences) }
        compose.onNode(savePreferences).performClick()
        compose.settleShell()

        assertEquals(1, api.accountUpdates.size)
        assertEquals("en", api.accountUpdates.single()["language"])
        assertEquals(1, compose.textNodes("偏好设置已保存"))

        tapTooltip("退出登录")

        assertEquals(0, logoutCount)
        assertEquals(1, compose.textNodes("确认退出当前账号？"))
        val cancelLogout = compose.onNode(hasText("取消") and hasClickAction())
        val confirmLogout = compose.onAllNodes(hasText("退出登录") and hasClickAction()).onLast()
        val cancelRect = cancelLogout.rect()
        val confirmRect = confirmLogout.rect()
        assertDpEquals(cancelRect.centerY, confirmRect.centerY)
        assertTrue(cancelRect.right < confirmRect.left)
        // The logout button stays highlighted while its dialog is open.
        fun logoutIconColor(): Color = compose.onNode(hasIcon(Icons.AutoMirrored.Filled.Logout), useUnmergedTree = true).iconPixelColor()
        fun logoutHighlighted() = logoutIconColor().isCloseTo(UiColors.accent)
        assertTrue(logoutHighlighted())

        cancelLogout.performClick()
        compose.settleShell()
        assertEquals(0, logoutCount)
        assertEquals(0, compose.textNodes("确认退出当前账号？"))
        assertFalse(logoutHighlighted())

        tapTooltip("退出登录")
        compose.onAllNodes(hasText("退出登录") and hasClickAction()).onLast().performClick()
        compose.settleShell()
        assertEquals(1, logoutCount)
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp-xxhdpi")
    fun `authenticated home shell lets sidebar scrollbar auto-hide`() {
        compose.setHomePage(app = homeTestAppContext(), size = { DpSize(360.dp, 320.dp) })
        compose.settleShell()

        val userSummaryRect = tag(homeSidebarUserSummaryTag).rect()
        val alphaCardRect = tag(homeSidebarRoomTag("server-alpha")).rect()
        assertDpEquals(0.dp, userSummaryRect.right - alphaCardRect.right)
        // The scrollbar paints over the room list: one node carries both tags
        // (the list's), so its bounds are the list's, gutter included.
        val scrollbar = homeSidebarRoomListTag
        assertTrue(tagExists(scrollbar))
        val scrollbarRect = tag(scrollbar).rect()
        assertDpEquals(8.dp, scrollbarRect.right - alphaCardRect.right)
        tag(scrollbar).performTouchInput {
            val start = Offset(width - 3.dp.toPx(), 30.dp.toPx())
            swipe(start, start + Offset(0f, 30.dp.toPx()))
        }
        compose.settleShell()
    }

    @Test
    @Config(qualifiers = "w420dp-h620dp-xxhdpi")
    fun `authenticated home shell uses separate narrow room list and chat screens`() {
        val api = HomeShellTestApi()
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        val compactSearchRect = tag(homeTitleSearchTag).rect()
        val userSummaryRect = tag(homeSidebarUserSummaryTag).rect()
        val firstRoomCardRect = tag(homeSidebarRoomTag("server-alpha")).rect()
        assertDpEquals(userSummaryRect.right - userSummaryRect.left, compactSearchRect.right - compactSearchRect.left)
        assertTrue(userSummaryRect.bottom < compactSearchRect.top)
        assertDpEquals(firstRoomCardRect.top, tag(homeSidebarRoomListTag).rect().top)
        assertEquals(1, compose.textNodes("Alpha Room"))
        assertTrue(tagExists(homeNarrowRoomListTag))
        assertFalse(tagExists(homeNarrowContentTag))
        assertEquals(0, tooltipCount("返回房间列表"))
        assertEquals(0, tooltipCount("Show servers"))
        assertEquals(0, tooltipCount("最小化"))
        assertEquals(0, tooltipCount("最大化"))
        assertEquals(0, tooltipCount("关闭"))

        enterSearch("Beta")
        assertTrue(api.requestedPaths.contains("/api/v1/search"))
        assertTrue(searchResultsOpen())
        assertTrue(tagExists(homeSidebarUserSummaryTag))

        compose.textFieldIn(homeTitleSearchTag).performTextReplacement("")
        compose.settleShell()
        assertFalse(searchResultsOpen())
        assertTrue(tagExists(homeSidebarUserSummaryTag))

        tapTooltip("设置")
        assertTrue(compose.exists(inTag(settingsSideNavigationTag, hasText("用户资料"))))
        assertFalse(tagExists(homeNarrowRoomListTag))

        pressBack()
        assertTrue(tagExists(homeNarrowRoomListTag))
        assertEquals(0, compose.textNodes("用户资料"))

        tapText("Alpha Room")
        assertEquals(0, compose.textNodes("Beta Room"))
        assertFalse(tagExists(homeNarrowRoomListTag))
        assertTrue(tagExists(homeNarrowContentTag))
        assertEquals(0, tooltipCount("Show servers"))
        assertEquals(1, tooltipCount("返回房间列表"))
        assertTrue(tagExists(chatHeaderLiveButtonTag))
        assertTrue(tagExists(chatHeaderRoomTitleTag))
        assertTrue(compose.onTooltip("返回房间列表").rect().left >= tag(chatHeaderLiveButtonTag).rect().left)
        assertTrue(compose.onTooltip("返回房间列表").rect().right < tag(chatHeaderRoomAvatarTag).rect().left)
        assertEquals(0, compose.textNodes("进入语音频道"))
        assertEquals(1, compose.textNodes("Hello from Morgan"))

        tapTooltip("返回房间列表")
        assertTrue(tagExists(homeNarrowRoomListTag))
        assertEquals(1, compose.textNodes("Beta Room"))
        assertFalse(tagExists(homeNarrowContentTag))
        assertEquals(0, tooltipCount("返回房间列表"))

        for (entry in listOf("创建房间", "通知", "设置")) {
            tapTooltip(entry)
            assertFalse(entry, tagExists(homeNarrowRoomListTag))
            assertEquals(entry, 1, tooltipCount("返回"))

            tapTooltip("返回")
            assertTrue(entry, tagExists(homeNarrowRoomListTag))
            assertFalse(entry, tagExists(homeNarrowContentTag))
            assertEquals(entry, 0, tooltipCount("返回房间列表"))
        }

        tapText("Alpha Room")
        tapTooltip("房间成员")
        assertEquals(1, tooltipCount("返回"))
        assertEquals(0, tooltipCount("返回房间列表"))

        pressBack()
        assertEquals(1, tooltipCount("返回房间列表"))
        assertEquals(0, tooltipCount("返回"))

        pressBack()
        assertTrue(tagExists(homeNarrowRoomListTag))
        assertFalse(tagExists(homeNarrowContentTag))
    }

    @Test
    @Config(qualifiers = "w1280dp-h960dp-mdpi")
    fun `Android tablet uses the persistent wide desktop layout`() {
        var pageSize by mutableStateOf(DpSize(1280.dp, 800.dp))
        compose.setHomePage(app = homeTestAppContext(), size = { pageSize })
        compose.settleShell()

        assertFalse(tagExists(homeNarrowRoomListTag))
        assertFalse(tagExists(homeNarrowContentTag))
        assertEquals(1, compose.textNodes("Alpha Room"))
        assertEquals(1, compose.textNodes("Beta Room"))

        tapText("Alpha Room")

        assertEquals(1, compose.textNodes("Beta Room"))
        assertTrue(tagExists(chatHeaderLivePreviewTag))
        assertEquals(0, tooltipCount("最小化"))
        assertEquals(0, tooltipCount("最大化"))
        assertEquals(0, tooltipCount("关闭"))

        // Dart resized the view; here the page shrinks inside the window.
        pageSize = DpSize(600.dp, 960.dp)
        compose.settleShell()
        assertTrue(tagExists(homeNarrowRoomListTag))
        assertEquals(1, compose.textNodes("Beta Room"))

        pageSize = DpSize(540.dp, 720.dp)
        compose.settleShell()
        assertTrue(tagExists(homeNarrowRoomListTag))
        assertEquals(1, compose.textNodes("Beta Room"))
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun `narrow live chat header keeps the room identity visible`() {
        compose.setHomePage(app = homeTestAppContext())
        compose.settleShell()

        tapText("Alpha Room")

        assertEquals(1, tooltipCount("返回房间列表"))
        assertTrue(tagExists(chatHeaderRoomTitleTag))
        assertTrue(tagExists(chatHeaderLivePreviewCountTag))
        assertEquals(1, compose.textNodes("Alpha Room"))
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun `android shell stays above the system navigation safe area`() {
        compose.setHomePage(app = homeTestAppContext(), viewPadding = { EdgeInsets.only(bottom = 32.dp) })
        compose.settleShell()

        assertTrue(compose.onTooltip("设置").rect().bottom <= 708.dp)
        assertTrue(tag(homeSidebarNotificationsButtonTag).rect().bottom <= 708.dp)
    }
}

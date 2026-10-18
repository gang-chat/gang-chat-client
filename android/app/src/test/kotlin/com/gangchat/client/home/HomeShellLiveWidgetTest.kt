package com.gangchat.client.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import com.gangchat.client.shell.assertDpEquals
import androidx.compose.ui.unit.dp
import com.gangchat.client.shell.rect
import com.gangchat.client.foundation.Completer
import com.gangchat.client.live.LivePresenceSound
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.onAllTooltips
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.textColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live channel, music box and presence cases of
 * test/widget/shell/gang_app_shell_test_parts/home_widget_tests.dart.
 * Mouse hovers over the live controls are Android holds.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class HomeShellLiveWidgetTest : HomeShellTestBase() {
    private fun liveControl(id: String) = tag("live-control:$id")

    private fun tooltipCount(message: String) = compose.onAllTooltips(message).fetchSemanticsNodes().size

    /** Taps the dialog button labelled [label] (the last match: dialogs sit above the page). */
    private fun tapDialogButton(label: String) {
        compose.onAllNodes(hasText(label) and hasClickAction()).onLast().performClick()
        compose.settleShell()
    }

    private val root = SemanticsMatcher("root") { it.parent == null }

    /**
     * Dart's mouse hover over [tag]. Android builds take a mouse too; the
     * touch alternative (a hold) would also tap the mic and headphones
     * controls, like a hold on Dart's raw-pointer buttons.
     */
    private fun mouseHover(tag: String) {
        compose.onAllNodes(root).onFirst().performMouseInput { moveTo(Offset(1f, 1f)) }
        tag(tag).performMouseInput { moveTo(center) }
        compose.pumpShell()
    }

    /** Dart's `hover.removePointer()`: the mouse leaves for the window corner. */
    private fun mouseAway() {
        compose.onAllNodes(root).onFirst().performMouseInput { moveTo(Offset(1f, 1f)) }
        compose.settleShell()
    }

    /**
     * Clicks 1 dp above the bottom of the volume slider tagged [slider]
     * (volume 0) with the hovering mouse: Compose ends a mouse hover when a
     * touch arrives, where Flutter's `tapAt` leaves the mouse in place.
     */
    private fun tapSliderBottom(slider: String) {
        tag(slider).performMouseInput { click(Offset(centerX, height - 1.dp.toPx())) }
        compose.settleShell()
    }

    private fun pumpPresenceDebounce(steps: Int) {
        repeat(steps) { compose.pumpShell(500) }
        compose.settleShell()
    }

    @Test
    fun `authenticated home shell opens live channel pane`() {
        val api = HomeShellTestApi(initialAlphaRoomAiVoiceAnnouncementsEnabled = true)
        val liveSession = FakeHomeLiveSession()
        val presenceSounds = RecordingLivePresenceSoundPlayer()
        val presenceSpeech = RecordingLivePresenceSpeechPlayer()
        compose.setHomePage(
            app = homeTestAppContext(api),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            livePresenceSoundPlayer = presenceSounds,
            livePresenceSpeechPlayer = presenceSpeech,
        )
        compose.settleShell()

        tapText("Alpha Room")
        openLiveChannelFromHeader()

        assertEquals(1, compose.textNodes("Morgan"))
        assertTrue(tagExists("live-control:join"))
        assertTrue(tagExists("live-control:collapse"))
        assertEquals(1, tooltipCount("收起语音频道"))
        assertEquals(0, tooltipCount("已加入语音"))

        joinLive()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/live/join"))
        assertEquals(1, liveSession.connectAttempts)
        assertEquals(listOf(LivePresenceSound.joined), presenceSounds.sounds)
        assertEquals(0.75, presenceSounds.volumes.single(), 0.001)
        assertTrue(presenceSpeech.announcements.isEmpty())

        liveSession.emitParticipantJoined()
        pumpPresenceDebounce(8)
        assertEquals(listOf(LivePresenceSound.joined, LivePresenceSound.joined), presenceSounds.sounds)
        assertEquals(listOf(listOf("成员", "Morgan", "进入了语音频道")), presenceSpeech.announcements.map { it.segments })

        // The member card names the current user in the accent colour.
        val kaiNames = compose.onAllNodes(hasText("Kai"), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(kaiNames.indices.any { index -> compose.onAllNodes(hasText("Kai"), useUnmergedTree = true)[index].textColor() == UiColors.accent })
        assertEquals(0, compose.textNodes("Kai (you)"))
        assertEquals(1, compose.textNodes("Morgan"))
        assertFalse(tagExists("live-control:join"))
        assertTrue(tagExists("live-control:mic"))
        assertTrue(tagExists("live-control:leave"))
        assertEquals(0, tooltipCount("静音"))
        assertEquals(1, tooltipCount("麦克风静音"))
        assertEquals(1, tooltipCount("耳机静音"))
        // The pane's controls and the title-bar module.
        assertEquals(2, tooltipCount("关闭麦克风"))
        assertEquals(2, tooltipCount("关闭耳机"))
        assertEquals(1, tooltipCount("共享屏幕"))
        assertEquals(1, tooltipCount("开启摄像头"))
        assertEquals(0, tooltipCount("离开"))
        assertEquals(2, tooltipCount("离开语音频道"))
        assertEquals(0, tooltipCount("已加入语音"))
        assertTrue(compose.exists(inTag(homeSidebarUserSummaryTag, hasText("语音"))))
        assertFalse(compose.exists(inTag(homeSidebarUserSummaryTag, hasText("active"))))

        val selfVoiceVolume = "live-member-status:voice-volume:user-1"
        val selfKick = "live-member-status:kick:user-1"
        val remoteVoiceVolume = "live-member-status:voice-volume:user-2"
        val remoteMic = "live-member-status:mic:user-2"
        val remoteHeadphones = "live-member-status:headphones:user-2"
        val remoteKick = "live-member-status:kick:user-2"
        assertFalse(tagExists(selfVoiceVolume))
        assertFalse(tagExists(selfKick))
        assertTrue(tagExists(remoteVoiceVolume))
        assertTrue(tagExists(remoteMic))
        assertTrue(tagExists(remoteHeadphones))
        assertTrue(tagExists(remoteKick))

        mouseHover(remoteVoiceVolume)
        val memberVolumeSlider = "live-volume-slider:Morgan语音音量"
        assertTrue(tagExists(memberVolumeSlider))
        tapSliderBottom(memberVolumeSlider)
        assertEquals("user-2:0.00", liveSession.participantVoiceVolumeWrites.last())
        mouseAway()

        tapTag(remoteMic)
        assertEquals(1, compose.textNodes("麦克风静音此用户"))
        tapDialogButton("麦克风静音")
        assertEquals("mute_mic", api.liveModerationActions.last())

        tapTag(remoteHeadphones)
        assertEquals(1, compose.textNodes("耳机静音此用户"))
        tapDialogButton("耳机静音")
        assertEquals("block_voice", api.liveModerationActions.last())

        tapTag(remoteHeadphones)
        assertTrue(compose.onAllNodes(hasText("取消耳机静音") and hasClickAction()).fetchSemanticsNodes().isNotEmpty())
        tapDialogButton("取消耳机静音")
        assertEquals("restore_headphones", api.liveModerationActions.last())

        tapTag(remoteMic)
        assertTrue(compose.onAllNodes(hasText("取消麦克风静音") and hasClickAction()).fetchSemanticsNodes().isNotEmpty())
        tapDialogButton("取消麦克风静音")
        assertEquals("restore_voice", api.liveModerationActions.last())

        tapTag(remoteKick)
        assertEquals(1, compose.textNodes("踢出语音频道"))
        tapDialogButton("踢出")
        assertEquals("kick", api.liveModerationActions.last())
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/live/participants/user-2/moderation"))
        assertEquals(0, compose.textNodes("Morgan"))
        liveSession.emitParticipantLeft(removed = true)
        pumpPresenceDebounce(3)
        assertEquals(listOf("成员", "Morgan", "被踢出了语音频道"), presenceSpeech.announcements.last().segments)

        tapTag("live-control:mic")
        assertEquals(0.0, liveSession.inputVolumes.last(), 0.0)
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.MicOff))

        tapTag("live-control:mic")
        assertEquals(0.35, liveSession.inputVolumes.last(), 1e-9)
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.Mic))

        tapTag("live-control:headphones")
        assertEquals(0.0, liveSession.outputVolumes.last(), 0.0)
        assertEquals(0.0, liveSession.inputVolumes.last(), 0.0)
        assertTrue(liveSession.micMutes.contains(true))
        assertTrue(tagHasIcon("live-control:headphones", Icons.Filled.HeadsetOff))
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.MicOff))

        tapTag("live-control:headphones")
        assertEquals(0.75, liveSession.outputVolumes.last(), 1e-9)
        assertTrue(tagHasIcon("live-control:headphones", Icons.Filled.Headphones))
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.MicOff))

        mouseHover("live-control:mic")
        val micVolumeSlider = "live-volume-slider:麦克风输入音量"
        assertTrue(tagExists(micVolumeSlider))
        val micPanel = tag("live-volume-panel:麦克风输入音量").rect()
        val micControl = liveControl("mic").rect()
        assertDpEquals(micControl.right - micControl.left, micPanel.right - micPanel.left, tolerance = 1f)
        assertTrue(micPanel.bottom - micPanel.top < 174.dp)
        assertTrue(micPanel.bottom < micControl.top)
        tapSliderBottom(micVolumeSlider)
        assertTrue(liveSession.inputVolumes.contains(0.0))
        assertTrue(liveSession.micMutes.contains(true))
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.MicOff))

        // The slider muted an already-muted mic, so unmuting falls back to the unity default.
        tapTag("live-control:mic")
        assertEquals(1.0, liveSession.inputVolumes.last(), 1e-9)
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.Mic))

        mouseHover("live-control:headphones")
        val outputVolumeSlider = "live-volume-slider:语音输出音量"
        assertTrue(tagExists(outputVolumeSlider))
        tapSliderBottom(outputVolumeSlider)
        assertTrue(liveSession.outputVolumes.contains(0.0))
        assertTrue(liveSession.inputVolumes.contains(0.0))
        assertTrue(liveSession.micMutes.contains(true))
        assertTrue(liveSession.outputMutes.containsAll(listOf(true, false)))
        assertTrue(tagHasIcon("live-control:headphones", Icons.Filled.HeadsetOff))
        assertTrue(tagHasIcon("live-control:mic", Icons.Filled.MicOff))

        tapTag("live-control:headphones")
        assertEquals(0.75, liveSession.outputVolumes.last(), 1e-9)
        assertTrue(tagHasIcon("live-control:headphones", Icons.Filled.Headphones))

        // No screen share volume while not sharing.
        mouseHover("live-control:screen-share")
        compose.pumpShell(120)
        assertFalse(tagExists("live-volume-slider:共享屏幕输出音量"))
        mouseAway()

        tapTag("live-control:camera")
        // Android has no screen source picker: the system prompt (inside the
        // live session) picks what is shared, so no source id is passed.
        tapTag("live-control:screen-share")

        assertTrue(liveSession.micMutes.contains(true))
        assertTrue(liveSession.outputMutes.containsAll(listOf(true, false)))
        assertEquals(listOf(true, false), liveSession.cameraEnables)
        assertEquals(listOf(true), liveSession.screenShareEnables)
        assertEquals(listOf<String?>(null), liveSession.screenShareSourceIds)
        assertTrue(api.requestedPaths.count { it == "/api/v1/rooms/server-alpha/live/me" } >= 3)

        val queuedPresencePlayback = Completer<Unit>()
        presenceSounds.nextPlaybackCompletion = queuedPresencePlayback
        liveSession.emitParticipantJoined()
        compose.pumpShell()
        assertEquals(LivePresenceSound.joined, presenceSounds.sounds.last())
        val soundCountBeforeExit = presenceSounds.sounds.size
        liveSession.emitParticipantLeft()
        compose.pumpShell()
        assertEquals(soundCountBeforeExit, presenceSounds.sounds.size)

        liveControl("leave").performClick()
        compose.pumpShell()
        queuedPresencePlayback.complete(Unit)
        pumpPresenceDebounce(3)

        assertEquals(1, liveSession.disconnects)
        assertEquals(soundCountBeforeExit + 1, presenceSounds.sounds.size)
        assertEquals(LivePresenceSound.left, presenceSounds.sounds.last())
        assertEquals(3, presenceSpeech.announcements.size)
        assertTrue(tagExists("live-control:join"))
        assertEquals(0, tooltipCount("已加入语音"))

        tapTag("live-control:collapse")

        assertEquals(0, compose.textNodes("Kai (you)"))
        assertEquals(1, compose.textNodes("Hello from Morgan"))
    }

    @Test
    fun `transient music box load retries automatically and restores its controls`() {
        // Fail the initial room-open load. The room-scoped timer must restore
        // the module without requiring another lifecycle action.
        val api = HomeShellTestApi(musicBoxEnabled = true, musicBoxStateTransientFailures = 1)
        compose.setHomePage(
            app = homeTestAppContext(api),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = FakeHomeLiveSession()),
        )
        compose.settleShell()

        tapText("Alpha Room")
        compose.pumpShell(2000)
        compose.settleShell()
        openLiveChannelFromHeader()

        val musicBoxStatePath = "/api/v1/rooms/server-alpha/music-box/state"
        assertEquals(2, api.requestedPaths.count { it == musicBoxStatePath })
        assertFalse(tagExists("live-control:music-inline"))

        joinLive()

        assertEquals(2, api.requestedPaths.count { it == musicBoxStatePath })
        assertTrue(tagExists("live-control:music-inline"))
    }

    @Test
    fun `room role changes refresh actor-specific music box capabilities`() {
        val api = HomeShellTestApi(musicBoxEnabled = true)
        val realtime = FakeHomeRealtimeService()
        compose.setHomePage(app = homeTestAppContext(api), realtime = realtime)
        compose.settleShell()

        tapText("Alpha Room")
        val musicBoxStatePath = "/api/v1/rooms/server-alpha/music-box/state"
        val beforeRoleChange = api.requestedPaths.count { it == musicBoxStatePath }
        assertEquals(1, beforeRoleChange)

        realtime.add("room_role_changed", mapOf("room_id" to "server-alpha", "role" to "admin"))
        compose.settleShell()

        assertEquals(beforeRoleChange + 1, api.requestedPaths.count { it == musicBoxStatePath })
    }

    @Test
    fun `same-revision music box heartbeat does not refetch capabilities`() {
        val api = HomeShellTestApi(musicBoxEnabled = true)
        val realtime = FakeHomeRealtimeService()
        compose.setHomePage(app = homeTestAppContext(api), realtime = realtime)
        compose.settleShell()

        tapText("Alpha Room")
        val musicBoxStatePath = "/api/v1/rooms/server-alpha/music-box/state"
        val beforeHeartbeat = api.requestedPaths.count { it == musicBoxStatePath }

        realtime.add(
            "music_box_changed",
            mapOf(
                "room_id" to "server-alpha",
                "enabled" to true,
                "revision" to 1,
                "playback" to
                    mapOf(
                        "state" to "stopped",
                        "current_item_id" to "",
                        "position_ms" to 0,
                        "volume" to 100,
                        "capabilities" to mapOf("can_control" to false, "can_change_mode" to false, "can_reorder" to false),
                    ),
                "queue" to emptyList<Any?>(),
                "temporary_queue" to emptyList<Any?>(),
                "temporary_playlist" to mapOf("queued_count" to 0),
                "usage" to mapOf("used_bytes" to 0, "limit_bytes" to 209715200),
                "active_source" to mapOf("type" to "temporary", "id" to "", "name" to "点歌队列"),
            ),
        )
        compose.settleShell()

        assertEquals(beforeHeartbeat, api.requestedPaths.count { it == musicBoxStatePath })
    }

    @Test
    fun `participant departure waits for authoritative reconciliation`() {
        val realtime = FakeHomeRealtimeService()
        val liveSession = FakeHomeLiveSession()
        val presenceSounds = RecordingLivePresenceSoundPlayer()
        compose.setHomePage(
            app = homeTestAppContext(),
            realtime = realtime,
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            livePresenceSoundPlayer = presenceSounds,
        )
        compose.settleShell()

        openAlphaAndJoinLive()

        val morganMic = "live-member-status:mic:user-2"
        assertTrue(tagExists(morganMic))
        val soundCountBeforeDisconnect = presenceSounds.sounds.size

        liveSession.emitParticipantLeft()
        compose.pumpShell()

        assertTrue(tagExists(morganMic))
        assertEquals(soundCountBeforeDisconnect, presenceSounds.sounds.size)

        val morgan = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan")
        realtime.add(
            "live_participant_reconnecting",
            mapOf(
                "room_id" to "server-alpha",
                "live" to
                    homeShellLiveStateJson(
                        roomId = "server-alpha",
                        participantCount = 2,
                        updatedAt = "2026-06-05T08:01:00Z",
                        participants =
                            listOf(
                                homeShellLiveParticipantJson(user = homeShellCurrentUserJson, liveSessionId = "live-session-joined"),
                                homeShellLiveParticipantJson(user = morgan, liveSessionId = "live-session-morgan", connectionState = "reconnecting"),
                            ),
                    ),
            ),
        )
        compose.settleShell()

        assertTrue(tagExists(morganMic))
        assertEquals(soundCountBeforeDisconnect, presenceSounds.sounds.size)

        realtime.add(
            "live_participants_reconciled",
            mapOf(
                "room_id" to "server-alpha",
                "live" to
                    homeShellLiveStateJson(
                        roomId = "server-alpha",
                        participantCount = 1,
                        updatedAt = "2026-06-05T08:02:00Z",
                        participants = listOf(homeShellLiveParticipantJson(user = homeShellCurrentUserJson, liveSessionId = "live-session-joined")),
                    ),
            ),
        )
        pumpPresenceDebounce(4)

        assertEquals(0, compose.textNodes("Morgan"))
        assertEquals(soundCountBeforeDisconnect + 1, presenceSounds.sounds.size)
        assertEquals(LivePresenceSound.left, presenceSounds.sounds.last())
    }

    @Test
    fun `disabling personal AI announcements keeps participant join and leave cues`() {
        val realtime = FakeHomeRealtimeService()
        val liveSession = FakeHomeLiveSession()
        val presenceSounds = RecordingLivePresenceSoundPlayer()
        val presenceSpeech = RecordingLivePresenceSpeechPlayer()
        compose.setHomePage(
            app = homeTestAppContext(),
            realtime = realtime,
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            livePresenceSoundPlayer = presenceSounds,
            livePresenceSpeechPlayer = presenceSpeech,
        )
        compose.settleShell()

        openAlphaAndJoinLive()

        val room = homeShellRoomCardJson(id = "server-alpha", name = "Alpha Room", memberCount = 2, liveParticipantCount = 2)
        room["ai_voice_announcements_enabled"] = false
        realtime.add("room_updated", room)
        compose.settleShell()

        val initialCueCount = presenceSounds.sounds.size
        liveSession.emitParticipantJoined()
        liveSession.emitParticipantLeft()
        realtime.add(
            "live_participants_reconciled",
            mapOf(
                "room_id" to "server-alpha",
                "live" to
                    homeShellLiveStateJson(
                        roomId = "server-alpha",
                        participantCount = 1,
                        updatedAt = "2026-06-05T08:01:00Z",
                        participants = listOf(homeShellLiveParticipantJson(user = homeShellCurrentUserJson, liveSessionId = "live-session-joined")),
                    ),
            ),
        )
        pumpPresenceDebounce(4)

        assertEquals(listOf(LivePresenceSound.joined, LivePresenceSound.left), presenceSounds.sounds.drop(initialCueCount))
        assertTrue(presenceSpeech.announcements.isEmpty())
    }
}

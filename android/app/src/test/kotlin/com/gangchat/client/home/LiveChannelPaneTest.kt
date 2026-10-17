package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.live.LiveVideoTrack
import com.gangchat.client.live.LiveVideoTrackFit
import com.gangchat.client.live.liveVideoTrackRendererForTest
import com.gangchat.client.live.resetLiveVideoTrackRendererForTest
import com.gangchat.client.platform.SystemUiMode
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.MusicBoxPlayback
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxQueueItemStatus
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.MusicBoxUsage
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.shell.FullScreenSystemUiController
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.avatarFallbackColor
import com.gangchat.client.ui.dpBounds
import com.gangchat.client.ui.hasTooltip
import com.gangchat.client.ui.textColor
import com.gangchat.client.ui.textLayout
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Text
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Dart's `test/widget/home/live_channel_pane_test.dart`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-mdpi", shadows = [ShadowNoOpMagnifier::class])
class LiveChannelPaneTest {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() = resetLiveVideoTrackRendererForTest()

    private val density: Float get() = compose.density.density

    private fun pump(params: LivePaneParams): LivePaneHarness {
        val harness = LivePaneHarness(params)
        compose.setLivePane(harness)
        return harness
    }

    private fun repump(harness: LivePaneHarness, update: LivePaneParams.() -> Unit) {
        harness.pump(update)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.tagCount(tag) == 1

    private fun count(matcher: SemanticsMatcher) = compose.countOf(matcher)

    private fun withinCount(tag: String, matcher: SemanticsMatcher) = compose.within(tag, matcher).fetchSemanticsNodes().size

    private fun rect(tag: String) = compose.rectOf(tag)

    private fun cardRect(userId: String) = rect(liveMemberCardTag(userId))

    private val indeterminate = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)

    private fun assertBelowTooltip(message: String) {
        // Dart also checks `preferBelow` and `verticalOffset: 24`, which the
        // tooltip's semantics do not expose (see `live buttons show hover info
        // below their targets` for the placement).
        assertEquals(message, 1, count(hasTooltip(message)))
    }

    /** Taps the window at [x], [y] dp (Dart's `tester.tapAt`). */
    private fun tapAt(x: Dp, y: Dp) {
        compose.onNodeWithTag("page").performTouchInput { click(Offset(x.toPx(), y.toPx())) }
        compose.waitForIdle()
    }

    /**
     * Clicks the window at [x], [y] dp with the mouse. Compose's test input
     * ends a mouse hover when a touch arrives, while Flutter's `tapAt` leaves
     * the hovering mouse in place, so taps made while a hover panel is open
     * use the mouse.
     */
    private fun mouseTapAt(x: Dp, y: Dp) {
        compose.onNodeWithTag("page").performMouseInput { click(Offset(x.toPx(), y.toPx())) }
        compose.waitForIdle()
    }

    /** Moves the mouse to the window corner (Dart's `removePointer`). */
    private fun mouseAway() {
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset(1f, 1f)) }
        compose.waitForIdle()
    }

    private fun hover(tag: String) {
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset(1f, 1f)) }
        compose.tag(tag).performMouseInput { moveTo(center) }
        compose.waitForIdle()
    }

    private fun installBlackRenderer(record: ((LiveVideoTrack, LiveVideoTrackFit, Boolean) -> Unit)? = null) {
        liveVideoTrackRendererForTest = { track, fit, mirror ->
            record?.invoke(track, fit, mirror)
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("live-video-renderer:${track.identity}:${track.isScreenShare}"))
        }
    }

    private val me = liveTestCurrentUser.toSummary().copyWith(roomDisplayName = "Room Me", roomRole = "member")

    @Test
    fun `empty live channel uses Chinese empty copy without header tag`() {
        pump(LivePaneParams())
        compose.onNodeWithText("语音频道里还没有人").assertExists()
        compose.onNodeWithText("No one is in live channel").assertDoesNotExist()
        compose.onNodeWithText("Live Channel").assertDoesNotExist()
    }

    @Test
    fun `screen-share control is hidden when unsupported`() {
        pump(LivePaneParams(screenShareSupported = false))
        assertFalse(exists("live-control:screen-share"))
        assertTrue(exists("live-control:camera"))
    }

    @Test
    fun `narrow join and collapse actions are matching labeled rows`() {
        pump(LivePaneParams(width = 360.0, joined = false))
        val joinRect = rect("live-control:join")
        val collapseRect = rect("live-control:collapse")
        assertEquals(1, withinCount("live-control:join", hasText("加入")))
        assertEquals(1, withinCount("live-control:collapse", hasText("收起")))
        assertEquals(joinRect.widthDp.value, collapseRect.widthDp.value, 0.01f)
        assertEquals(joinRect.left.value, collapseRect.left.value, 0.01f)
        assertTrue(collapseRect.top > joinRect.bottom)
    }

    @Test
    fun `joining voice replaces the call icon with a spinner`() {
        pump(LivePaneParams(width = 360.0, joined = false, joining = true))
        assertEquals(0, withinCount("live-control:join", hasIcon(Icons.Filled.Call)))
        assertEquals(1, withinCount("live-control:join", indeterminate))
        assertEquals(1, withinCount("live-control:join", hasText("加入")))
    }

    @Test
    fun `room loading disables join without showing joining state`() {
        pump(LivePaneParams(width = 360.0, loading = true, joined = false, joining = false))
        assertEquals(0, compose.within("live-control:join", hasClickAction() and isEnabled()).fetchSemanticsNodes().size)
        assertEquals(1, withinCount("live-control:join", hasIcon(Icons.Filled.Call)))
        assertEquals(0, withinCount("live-control:join", indeterminate))
        compose.onNodeWithText("语音频道里还没有人").assertDoesNotExist()
        assertEquals(1, count(indeterminate))
    }

    @Test
    fun `leaving voice disables join without showing joining state`() {
        pump(LivePaneParams(width = 360.0, joined = false, joining = false, leaving = true))
        assertEquals(0, compose.within("live-control:join", hasClickAction() and isEnabled()).fetchSemanticsNodes().size)
        assertEquals(1, withinCount("live-control:join", hasIcon(Icons.Filled.Call)))
        assertEquals(0, withinCount("live-control:join", indeterminate))
        compose.onNodeWithText("语音频道里还没有人").assertExists()
        assertEquals(0, count(indeterminate))
    }

    @Test
    fun `wide join and collapse actions are matching labeled buttons`() {
        pump(LivePaneParams(width = 960.0, joined = false))
        assertEquals(1, withinCount("live-control:join", hasText("加入")))
        assertEquals(1, withinCount("live-control:collapse", hasText("收起")))
        assertEquals(rect("live-control:join").widthDp.value, rect("live-control:collapse").widthDp.value, 0.01f)
    }

    @Test
    fun `narrow one-line joined controls keep collapse icon-only`() {
        pump(LivePaneParams(width = 420.0, joined = true))
        assertEquals(0, withinCount("live-control:collapse", hasText("收起")))
        val collapse = rect("live-control:collapse")
        val camera = rect("live-control:camera")
        assertEquals(camera.widthDp.value, collapse.widthDp.value, 0.01f)
        assertEquals(camera.heightDp.value, collapse.heightDp.value, 0.01f)
    }

    @Test
    fun `narrow wrapped joined controls give collapse a labeled row`() {
        pump(LivePaneParams(width = 360.0, joined = true))
        val collapse = rect("live-control:collapse")
        assertEquals(1, withinCount("live-control:collapse", hasText("收起")))
        assertTrue(collapse.widthDp > 44.dp)
        assertTrue(collapse.top > rect("live-control:leave").bottom)
    }

    @Test
    fun `current user live member card is square and shows connected statuses`() {
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_self", user = me, headphonesMuted = true))),
                headphonesMuted = true,
                speakingUserIds = setOf("current_user"),
            ),
        )
        val cardRect = cardRect("current_user")
        val avatarRect = rect(liveMemberAvatarTag("current_user"))
        val name = compose.onNodeWithText("Room Me", useUnmergedTree = true)
        val nameRect = name.dpBounds(density)
        val tagRect = rect("live-member-activity:current_user")
        val micRect = rect("live-member-status:mic:current_user")
        val headphonesRect = rect("live-member-status:headphones:current_user")

        assertEquals(42f, avatarRect.widthDp.value, 0.01f)
        assertEquals(cardRect.widthDp.value, cardRect.heightDp.value, 0.01f)
        assertEquals((cardRect.left + cardRect.right).value / 2, (avatarRect.left + avatarRect.right).value / 2, 1f)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Center, name.textLayout().layoutInput.style.textAlign)
        assertEquals(UiColors.accent, name.textColor())
        assertTrue(nameRect.top > avatarRect.bottom)
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasText("(you)", substring = true)).fetchSemanticsNodes().size)
        compose.onNodeWithText("正在说话").assertDoesNotExist()
        assertTrue(exists("live-member-activity:current_user"))
        assertEquals(1, withinCount("live-member-activity:current_user", hasIcon(Icons.Filled.Mic)))
        assertTrue(tagRect.right <= cardRect.right)
        assertTrue(tagRect.top < nameRect.top)
        assertTrue(tagRect.bottom < avatarRect.top)
        assertTrue(cardRect.bottom - headphonesRect.bottom < 14.dp)
        assertEquals(micRect.widthDp.value, micRect.heightDp.value, 0.01f)
        assertEquals(headphonesRect.widthDp.value, headphonesRect.heightDp.value, 0.01f)
        assertEquals(micRect.right.value, headphonesRect.left.value, 0.01f)
        assertFalse(exists("live-member-status:camera:current_user"))
        assertFalse(exists("live-member-status:screen-share:current_user"))
        assertFalse(exists("live-member-status:voice-volume:current_user"))
        assertFalse(exists("live-member-status:kick:current_user"))
        assertEquals(1, withinCount("live-member-status:mic:current_user", hasIcon(Icons.Filled.Mic)))
        assertEquals(1, withinCount("live-member-status:headphones:current_user", hasIcon(Icons.Filled.HeadsetOff)))
    }

    @Test
    fun `narrow live member stage fits two square cards in one row`() {
        val remote = liveUser("phabe", "Phabe", roomRole = "member").copyWith(roomDisplayName = "Room Phabe")
        val harness =
            pump(
                LivePaneParams(
                    live = liveState(listOf(liveParticipant(id = "live_self", user = me), liveParticipant(id = "live_phabe", user = remote))),
                    speakingUserIds = setOf("current_user"),
                    width = 347.0,
                    height = 720.0,
                ),
            )
        val current = cardRect("current_user")
        val remoteRect = cardRect("phabe")
        val scale = current.widthDp.value / 154f
        assertEquals(remoteRect.top.value, current.top.value, 0.01f)
        // The flat card reserves no hover-lift or base-depth band, so the
        // scaled card stays square.
        assertEquals(154f * scale, current.heightDp.value, 0.5f)
        assertEquals(154f * scale, remoteRect.heightDp.value, 0.5f)
        assertEquals(current.widthDp.value, current.heightDp.value, 0.5f)
        assertEquals(12f, (remoteRect.left - current.right).value, 0.5f)
        assertTrue(current.widthDp < 154.dp)
        assertEquals(42f * scale, rect(liveMemberAvatarTag("current_user")).widthDp.value, 0.5f)
        assertEquals(24f * scale, rect("live-member-activity:current_user").widthDp.value, 0.5f)
        // Four status buttons share the 130px row less the card's 1px border
        // on each side.
        assertEquals(32f * scale, rect("live-member-status:mic:current_user").widthDp.value, 0.5f)

        repump(harness) { width = 365.0 }
        compose.mainClock.advanceTimeBy(10)
        compose.waitForIdle()
    }

    @Test
    fun `current user member card uses local audio mute state before snapshot`() {
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_self", user = me))),
                micMuted = true,
                headphonesMuted = true,
                liveKitMicMutedByParticipantId = mapOf("current_user" to false),
            ),
        )
        assertEquals(1, withinCount("live-member-status:mic:current_user", hasIcon(Icons.Filled.MicOff)))
        assertEquals(1, withinCount("live-member-status:headphones:current_user", hasIcon(Icons.Filled.HeadsetOff)))
    }

    @Test
    fun `joining live channel hides local card until ready`() {
        val remote = liveUser("phabe", "Phabe", roomRole = "member")
        val live = liveState(listOf(liveParticipant(id = "live_self", user = me, micMuted = true), liveParticipant(id = "live_phabe", user = remote)))
        val harness = pump(LivePaneParams(live = live, joined = false, joining = true))
        compose.onNodeWithText("Room Me").assertDoesNotExist()
        compose.onNodeWithText("Phabe").assertExists()

        repump(harness) {
            joined = true
            joining = false
        }
        compose.onNodeWithText("Room Me").assertExists()
        compose.onNodeWithText("Phabe").assertExists()
    }

    @Test
    fun `remote live member cards use participant avatar preset`() {
        val remote =
            liveUser("phabe", "Phabe", roomRole = "member").copyWith(
                displayName = "Global Phabe",
                roomDisplayName = "Room Phabe",
                defaultAvatarKey = "green-2",
            )
        pump(LivePaneParams(live = liveState(listOf(liveParticipant(id = "live_phabe", user = remote, micMuted = true))), height = 600.0))
        val avatar = compose.tag(liveMemberAvatarTag("phabe"))
        assertEquals(avatarFallbackColor("green-2"), compose.pixelIn(avatar, 21f, 5f))
        compose.onNodeWithText("Room Phabe").assertExists()
        compose.onNodeWithText("Global Phabe").assertDoesNotExist()
        compose.onNodeWithText("正在收听").assertDoesNotExist()
        compose.onNodeWithText("已静音").assertDoesNotExist()
        assertFalse(exists("live-member-activity:phabe"))
        assertTrue(count(hasTooltip("正在收听")) >= 1)
        assertTrue(exists("live-member-status:voice-volume:phabe"))
        assertFalse(exists("live-member-status:kick:phabe"))
    }

    @Test
    fun `connected muted member remains visible and shows muted mic`() {
        pump(
            LivePaneParams(
                live =
                    liveState(
                        listOf(
                            liveParticipant(
                                id = "live_phabe",
                                user = liveUser("phabe", "Phabe", roomRole = "member"),
                                micMuted = true,
                                connectionState = "joining",
                            ),
                        ),
                    ),
                connectedParticipantIds = setOf("phabe"),
                liveKitMicMutedByParticipantId = mapOf("phabe" to false),
            ),
        )
        compose.onNodeWithText("Phabe").assertExists()
        assertTrue(exists("live-member-status:mic:phabe"))
        assertEquals(1, withinCount("live-member-status:mic:phabe", hasIcon(Icons.Filled.MicOff)))
        assertEquals(0, withinCount("live-member-status:mic:phabe", hasIcon(Icons.Filled.Mic)))
    }

    @Test
    fun `joining open member ignores provisional LiveKit mic mute`() {
        pump(
            LivePaneParams(
                live =
                    liveState(
                        listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Phabe", roomRole = "member"), connectionState = "joining")),
                    ),
                connectedParticipantIds = setOf("phabe"),
                liveKitMicMutedByParticipantId = mapOf("phabe" to true),
            ),
        )
        compose.onNodeWithText("Phabe").assertExists()
        assertEquals(1, withinCount("live-member-status:mic:phabe", hasIcon(Icons.Filled.Mic)))
        assertEquals(0, withinCount("live-member-status:mic:phabe", hasIcon(Icons.Filled.MicOff)))
    }

    @Test
    fun `live member avatar opens a profile card on tap and hover`() {
        val resolvedIds = mutableListOf<String>()
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Phabe", roomRole = "member")))),
                width = 347.0,
                height = 720.0,
                onResolveParticipantProfile = { user ->
                    resolvedIds.add(user.id)
                    UserSummary(
                        id = user.id,
                        username = "resolved_phabe",
                        displayName = "Resolved Phabe",
                        avatarUrl = user.avatarUrl,
                        defaultAvatarKey = user.defaultAvatarKey,
                        roomDisplayName = "Resolved Room Phabe",
                        roomRole = "admin",
                        uid = "20002",
                        bio = "Live card profile",
                        isOnline = false,
                    )
                },
            ),
        )
        assertTrue(exists(liveMemberAvatarTag("phabe")))
        compose.onNodeWithText("@resolved_phabe").assertDoesNotExist()

        compose.tag(liveMemberAvatarTag("phabe")).performClick()
        compose.waitForIdle()
        assertEquals(listOf("phabe"), resolvedIds)
        compose.onNodeWithText("@resolved_phabe").assertExists()
        compose.onNodeWithText("Resolved Room Phabe").assertExists()
        compose.onNodeWithText("Live card profile").assertExists()
        compose.onNodeWithText("语音").assertExists()
        compose.onNodeWithText("管理员").assertExists()

        tapAt(4.dp, 4.dp)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onNodeWithText("@resolved_phabe").assertDoesNotExist()

        hover(liveMemberAvatarTag("phabe"))
        compose.onNodeWithText("@resolved_phabe").assertExists()
    }

    @Test
    fun `remote live member controls adjust user volume and can kick`() {
        val volumeChanges = mutableListOf<Double>()
        val volumeToggles = mutableListOf<String>()
        val removed = mutableListOf<LiveParticipant>()
        val live = liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Phabe", roomRole = "member"))))
        val harness =
            pump(
                LivePaneParams(
                    live = live,
                    height = 600.0,
                    participantVoiceVolume = { id -> if (id == "phabe") 0.4 else 1.0 },
                    onParticipantVoiceVolumeChanged = { id, volume -> if (id == "phabe") volumeChanges.add(volume) },
                    onParticipantVoiceMuteToggled = { volumeToggles.add(it) },
                    canRemoveParticipant = { true },
                    onRemoveParticipant = { removed.add(it) },
                ),
            )
        val volumeButton = "live-member-status:voice-volume:phabe"
        val kickButton = "live-member-status:kick:phabe"
        assertTrue(exists(volumeButton))
        assertTrue(exists(kickButton))
        assertEquals(1, withinCount(kickButton, hasIcon(Icons.AutoMirrored.Filled.ExitToApp)))
        assertEquals(1, withinCount(volumeButton, hasIcon(Icons.AutoMirrored.Filled.VolumeDown)))
        assertBelowTooltip("静音Phabe")
        assertEquals(0, count(hasTooltip("静音Phabe音量")))

        hover(volumeButton)
        val slider = "live-volume-slider:Phabe语音音量"
        assertTrue(exists(slider))
        val panel = rect("live-volume-panel:Phabe语音音量")
        assertEquals(32.5f, panel.widthDp.value, 1f) // whole-pixel rounding at mdpi
        assertEquals(144f * 32.5f / 44f, panel.heightDp.value, 1f)
        var sliderRect = rect(slider)
        mouseTapAt((sliderRect.left + sliderRect.right) / 2, sliderRect.bottom - 1.dp)
        assertEquals(0.0, volumeChanges.last(), 1e-9)
        sliderRect = rect(slider)
        mouseTapAt((sliderRect.left + sliderRect.right) / 2, sliderRect.top + 1.dp)
        assertEquals(2.0, volumeChanges.last(), 0.05)
        compose.tag(volumeButton).performMouseInput { click() }
        compose.waitForIdle()
        assertEquals(listOf("phabe"), volumeToggles)

        mouseAway()
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.tag(kickButton).performClick()
        assertEquals("phabe", removed.single().user.id)

        repump(harness) {
            participantVoiceVolume = { id -> if (id == "phabe") 0.0 else 1.0 }
            onParticipantVoiceVolumeChanged = { _, _ -> }
            onParticipantVoiceMuteToggled = {}
            canRemoveParticipant = { false }
        }
        assertBelowTooltip("取消静音Phabe")
        assertEquals(0, count(hasTooltip("取消静音Phabe音量")))
    }

    @Test
    fun `remote member volume icon follows the shared 50% threshold`() {
        val live = liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Phabe", roomRole = "member"))))
        val harness = pump(LivePaneParams(live = live, height = 600.0, participantVoiceVolume = { 0.49 }))
        val button = "live-member-status:voice-volume:phabe"
        assertEquals(1, withinCount(button, hasIcon(Icons.AutoMirrored.Filled.VolumeDown)))
        repump(harness) { participantVoiceVolume = { 0.5 } }
        assertEquals(1, withinCount(button, hasIcon(Icons.AutoMirrored.Filled.VolumeUp)))
        repump(harness) { participantVoiceVolume = { 1.5 } }
        assertEquals(1, withinCount(button, hasIcon(Icons.AutoMirrored.Filled.VolumeUp)))
        repump(harness) { participantVoiceVolume = { 0.0 } }
        assertEquals(1, withinCount(button, hasIcon(Icons.AutoMirrored.Filled.VolumeOff)))
    }

    @Test
    fun `Android hold opens remote volume without toggling the mute button`() {
        val volumeToggles = mutableListOf<String>()
        val volumeChanges = mutableListOf<Double>()
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Phabe", roomRole = "member")))),
                width = 347.0,
                height = 720.0,
                participantVoiceVolume = { 0.4 },
                onParticipantVoiceVolumeChanged = { _, volume -> volumeChanges.add(volume) },
                onParticipantVoiceMuteToggled = { volumeToggles.add(it) },
            ),
        )
        val button = "live-member-status:voice-volume:phabe"
        val slider = "live-volume-slider:Phabe语音音量"
        assertFalse(exists(slider))

        compose.tag(button).performClick()
        compose.waitForIdle()
        assertEquals(listOf("phabe"), volumeToggles)
        volumeToggles.clear()

        compose.tag(button).performTouchInput { longClick() }
        compose.waitForIdle()
        assertTrue(exists(slider))
        assertTrue(volumeToggles.isEmpty())
        assertEquals(0, count(hasTooltip("静音Phabe音量")))
        val buttonRect = rect(button)
        val panelRect = rect("live-volume-panel:Phabe语音音量")
        // The panel matches the hover target, which the flat card's border
        // insets a fraction of a pixel wider than the keyed button.
        assertEquals(buttonRect.widthDp.value, panelRect.widthDp.value, 1f)
        assertEquals((buttonRect.left + buttonRect.right).value / 2, (panelRect.left + panelRect.right).value / 2, 1f)

        val sliderRect = rect(slider)
        tapAt((sliderRect.left + sliderRect.right) / 2, sliderRect.bottom - 1.dp)
        assertEquals(0.0, volumeChanges.last(), 0.02)

        compose.mainClock.advanceTimeBy(3000)
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertFalse(exists(slider))
    }

    @Test
    fun `remote live member moderation controls use danger icons`() {
        val micModerations = mutableListOf<LiveParticipant>()
        val headphonesModerations = mutableListOf<LiveParticipant>()
        pump(
            LivePaneParams(
                live =
                    liveState(
                        listOf(
                            liveParticipant(
                                id = "live_phabe",
                                user = liveUser("phabe", "Phabe", roomRole = "member"),
                                micMuted = true,
                                micBlocked = true,
                                headphonesMuted = true,
                                headphonesBlocked = true,
                                voiceBlocked = true,
                            ),
                        ),
                    ),
                canModerateParticipant = { true },
                onToggleParticipantMicModeration = { micModerations.add(it) },
                onToggleParticipantHeadphonesModeration = { headphonesModerations.add(it) },
            ),
        )
        assertFalse(exists("live-member-activity:phabe"))
        compose.onNodeWithText("已被麦克风静音").assertDoesNotExist()
        compose.onNodeWithText("已被耳机静音").assertDoesNotExist()
        assertEquals(
            1,
            withinCount("live-member-status:mic:phabe", hasIcon(Icons.Filled.MicOff) and SemanticsMatcher.expectValue(LiveIconTintKey, UiColors.danger)),
        )
        assertEquals(
            1,
            withinCount(
                "live-member-status:headphones:phabe",
                hasIcon(Icons.Filled.HeadsetOff) and SemanticsMatcher.expectValue(LiveIconTintKey, UiColors.danger),
            ),
        )
        compose.tag("live-member-status:mic:phabe").performClick()
        compose.tag("live-member-status:headphones:phabe").performClick()
        compose.waitForIdle()
        assertEquals("phabe", micModerations.single().user.id)
        assertEquals("phabe", headphonesModerations.single().user.id)
    }

    private fun assertMediaMemberCard(activityIcon: androidx.compose.ui.graphics.vector.ImageVector, stoppedThumbnailTag: String?) {
        val cardTag = liveMemberCardTag("current_user")
        val cardRect = cardRect("current_user")
        val micRect = rect("live-member-status:mic:current_user")
        val headphonesRect = rect("live-member-status:headphones:current_user")
        val nameNodes = compose.within(cardTag, hasText("Room Me")).fetchSemanticsNodes()
        assertEquals(1, nameNodes.size)
        val nameRect = compose.onNode(hasText("Room Me") and hasAnyAncestor(hasTestTag(cardTag)), useUnmergedTree = true).dpBounds(density)
        val tagRect = rect("live-member-activity:current_user")
        val previewRect =
            if (stoppedThumbnailTag == null) {
                compose.onNode(hasTestTag(liveVideoTrackViewTag) and hasAnyAncestor(hasTestTag(cardTag)), useUnmergedTree = true).dpBounds(density)
            } else {
                rect(stoppedThumbnailTag)
            }
        val scale = cardRect.widthDp.value / 154f
        assertEquals(154f * scale, cardRect.heightDp.value, 0.5f)
        assertEquals(cardRect.widthDp.value, cardRect.heightDp.value, 0.5f)
        assertEquals(if (stoppedThumbnailTag == null) 1 else 0, withinCount(cardTag, hasTestTag(liveVideoTrackViewTag)))
        if (stoppedThumbnailTag != null) assertEquals(1, withinCount(cardTag, hasTestTag(stoppedThumbnailTag)))
        assertEquals(0, withinCount(cardTag, hasTestTag(liveMemberAvatarTag("current_user"))))
        assertEquals(1, withinCount("live-member-activity:current_user", hasIcon(activityIcon)))
        assertFalse(exists("live-member-status:camera:current_user"))
        assertFalse(exists("live-member-status:screen-share:current_user"))
        assertFalse(exists("live-member-status:voice-volume:current_user"))
        assertFalse(exists("live-member-status:kick:current_user"))
        assertTrue(nameRect.left < tagRect.left)
        assertTrue(nameRect.top < previewRect.top)
        assertTrue(nameRect.bottom < micRect.top)
        assertTrue(tagRect.top < micRect.top)
        assertTrue(tagRect.right <= cardRect.right)
        assertEquals(micRect.right.value, headphonesRect.left.value, 0.01f)
        assertTrue(cardRect.bottom - headphonesRect.bottom < 14.dp)
    }

    @Test
    fun `live member media cards show top-left name and keep status controls`() {
        installBlackRenderer()
        var stageSelections = 0
        fun params(screenShare: Boolean) =
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_self", user = me, cameraOn = !screenShare, screenSharing = screenShare))),
                width = 347.0,
                height = 720.0,
                videoTracks = listOf(liveVideoTrack(identity = "current_user", isScreenShare = screenShare, isLocal = true)),
                onStageSelectionChanged = { stageSelections += 1 },
            )
        val harness = pump(params(screenShare = false))
        assertMediaMemberCard(activityIcon = Icons.Filled.Videocam, stoppedThumbnailTag = null)
        val hoverTag = "live-member:video-hover:current_user:false"
        assertEquals(1, count(hasTestTag(hoverTag) and hasLiveOpacity(0f)))
        hover("live-member:video-thumbnail:current_user:false")
        compose.mainClock.advanceTimeBy(150)
        compose.waitForIdle()
        assertEquals(1, count(hasTestTag(hoverTag) and hasLiveOpacity(1f)))
        assertEquals(1, withinCount("live-member:video-thumbnail:current_user:false", hasIcon(Icons.Filled.Search)))
        compose.tag("live-member:video-thumbnail:current_user:false").performMouseInput { exit() }
        // Android: the local camera texture's own tap target opens the stage.
        compose.tag("live-video-renderer:current_user:false").performClick()
        compose.waitForIdle()
        assertEquals(1, stageSelections)

        repump(harness) {
            val next = params(screenShare = true)
            live = next.live
            videoTracks = next.videoTracks
        }
        assertMediaMemberCard(activityIcon = Icons.AutoMirrored.Outlined.ScreenShare, stoppedThumbnailTag = "live-member:screen-share-thumbnail")
        compose.tag("live-member:screen-share-thumbnail").performClick()
        compose.waitForIdle()
        assertEquals(2, stageSelections)
    }

    @Test
    fun `camera thumbnails follow shared mirror state without SDK auto mirror`() {
        val mirrorByIdentity = mutableMapOf<String, Boolean>()
        installBlackRenderer { track, _, shouldMirror -> mirrorByIdentity[track.identity] = shouldMirror }
        pump(
            LivePaneParams(
                live =
                    liveState(
                        listOf(
                            liveParticipant(id = "live_self", user = liveTestCurrentUser.toSummary(), cameraOn = true, cameraMirrored = true),
                            liveParticipant(id = "live_phabe", user = liveUser("phabe", "Phabe", roomRole = "member"), cameraOn = true),
                        ),
                    ),
                videoTracks =
                    listOf(
                        liveVideoTrack(identity = "current_user", isScreenShare = false, isLocal = true),
                        liveVideoTrack(identity = "phabe", isScreenShare = false, isLocal = false),
                    ),
            ),
        )
        assertEquals(true, mirrorByIdentity["current_user"])
        assertEquals(false, mirrorByIdentity["phabe"])
    }

    @Test
    fun `Android local camera texture opens the same stage preview as Windows`() {
        installBlackRenderer()
        val selections = mutableListOf<LiveStageSelection?>()
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_self", user = liveTestCurrentUser.toSummary(), cameraOn = true))),
                videoTracks = listOf(liveVideoTrack(identity = "current_user", isScreenShare = false, isLocal = true)),
                onStageSelectionChanged = { selections.add(it) },
            ),
        )
        assertTrue(exists("live-member:android-local-camera-tap"))
        compose.tag("live-member:android-local-camera-tap").performClick()
        compose.waitForIdle()
        assertEquals(1, selections.size)
        assertEquals("current_user", selections.single()?.identity)
        assertEquals(false, selections.single()?.isScreenShare)
    }

    @Test
    fun `local camera stage exposes flip and shared mirror controls`() {
        val renderedMirrors = mutableListOf<Boolean>()
        installBlackRenderer { track, _, shouldMirror -> if (track.identity == "current_user") renderedMirrors.add(shouldMirror) }
        var flipCalls = 0
        val sharedMirrorChanges = mutableListOf<Boolean>()
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_self", user = liveTestCurrentUser.toSummary(), cameraOn = true))),
                videoTracks = listOf(liveVideoTrack(identity = "current_user", isScreenShare = false, isLocal = true)),
                stageSelection = LiveStageSelection.track(identity = "current_user", isScreenShare = false),
                onFlipCamera = {
                    flipCalls += 1
                    true
                },
                onSetLocalCameraMirrored = { mirrored ->
                    sharedMirrorChanges.add(mirrored)
                    true
                },
            ),
        )
        assertTrue(exists("live-stage:flip-camera"))
        assertTrue(exists("live-stage:mirror-camera"))
        assertFalse(renderedMirrors.last())

        compose.tag("live-stage:flip-camera").performClick()
        compose.waitForIdle()
        assertEquals(1, flipCalls)

        compose.tag("live-stage:mirror-camera").performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), sharedMirrorChanges)
        assertTrue(renderedMirrors.last())
    }

    @Test
    fun `local camera stage hides flip without camera capability`() {
        installBlackRenderer()
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_self", user = liveTestCurrentUser.toSummary(), cameraOn = true))),
                videoTracks = listOf(liveVideoTrack(identity = "current_user", isScreenShare = false, isLocal = true)),
                stageSelection = LiveStageSelection.track(identity = "current_user", isScreenShare = false),
            ),
        )
        assertFalse(exists("live-stage:flip-camera"))
        assertTrue(exists("live-stage:mirror-camera"))
    }

    @Test
    fun `remote camera stage hides flip and mirrors only the local viewer`() {
        val renderedMirrors = mutableListOf<Boolean>()
        installBlackRenderer { _, _, shouldMirror -> renderedMirrors.add(shouldMirror) }
        val sharedMirrorChanges = mutableListOf<Boolean>()
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Phabe", roomRole = "member"), cameraOn = true))),
                videoTracks = listOf(liveVideoTrack(identity = "phabe", isScreenShare = false, isLocal = false)),
                stageSelection = LiveStageSelection.track(identity = "phabe", isScreenShare = false),
                onFlipCamera = { true },
                onSetLocalCameraMirrored = { mirrored ->
                    sharedMirrorChanges.add(mirrored)
                    true
                },
            ),
        )
        assertFalse(exists("live-stage:flip-camera"))
        assertTrue(exists("live-stage:mirror-camera"))
        assertFalse(renderedMirrors.last())
        compose.tag("live-stage:mirror-camera").performClick()
        compose.waitForIdle()
        assertTrue(renderedMirrors.last())
        assertTrue(sharedMirrorChanges.isEmpty())
    }

    @Test
    fun `screen-share stage has no camera transform controls`() {
        installBlackRenderer()
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Phabe", roomRole = "member"), screenSharing = true))),
                videoTracks = listOf(liveVideoTrack(identity = "phabe", isScreenShare = true, isLocal = false)),
                stageSelection = LiveStageSelection.track(identity = "phabe", isScreenShare = true),
            ),
        )
        assertFalse(exists("live-stage:flip-camera"))
        assertFalse(exists("live-stage:mirror-camera"))
    }

    @Test
    fun `remote media thumbnail joins live room and selects stage`() {
        var joinCalls = 0
        val selections = mutableListOf<LiveStageSelection?>()
        fun params(screenShare: Boolean) =
            LivePaneParams(
                live =
                    liveState(
                        listOf(
                            liveParticipant(
                                id = "live_phabe",
                                user = liveUser("phabe", "Room Phabe", roomRole = "member"),
                                cameraOn = !screenShare,
                                screenSharing = screenShare,
                            ),
                        ),
                    ),
                joined = false,
                onJoin = { joinCalls += 1 },
                onStageSelectionChanged = { selections.add(it) },
            )
        val harness = pump(params(screenShare = true))
        compose.tag("live-member:screen-share-thumbnail").performClick()
        compose.waitForIdle()
        assertEquals(1, joinCalls)
        assertEquals("phabe", selections.single()?.identity)
        assertEquals(true, selections.single()?.isScreenShare)

        repump(harness) { live = params(screenShare = false).live }
        compose.tag("live-member:camera-thumbnail").performClick()
        compose.waitForIdle()
        assertEquals(2, joinCalls)
        assertEquals("phabe", selections.last()?.identity)
        assertEquals(false, selections.last()?.isScreenShare)
    }

    @Test
    fun `Android screen-share thumbnail joins voice and selects stage`() {
        var joinCalls = 0
        val selections = mutableListOf<LiveStageSelection?>()
        pump(
            LivePaneParams(
                live =
                    liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Room Phabe", roomRole = "member"), screenSharing = true))),
                joined = false,
                onJoin = { joinCalls += 1 },
                onStageSelectionChanged = { selections.add(it) },
            ),
        )
        compose.tag("live-member:screen-share-thumbnail").performClick()
        compose.waitForIdle()
        assertEquals(1, joinCalls)
        assertEquals("phabe", selections.single()?.identity)
        assertEquals(true, selections.single()?.isScreenShare)
    }

    @Test
    fun `Android screen-share thumbnail watches directly when already joined`() {
        var joinCalls = 0
        val selections = mutableListOf<LiveStageSelection?>()
        pump(
            LivePaneParams(
                live =
                    liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Room Phabe", roomRole = "member"), screenSharing = true))),
                joined = true,
                onJoin = { joinCalls += 1 },
                onStageSelectionChanged = { selections.add(it) },
            ),
        )
        compose.tag("live-member:screen-share-thumbnail").performClick()
        compose.waitForIdle()
        assertEquals(0, joinCalls)
        assertEquals("phabe", selections.single()?.identity)
        assertEquals(true, selections.single()?.isScreenShare)
    }

    @Test
    fun `focused screen share leaves member card in avatar layout`() {
        installBlackRenderer()
        pump(
            LivePaneParams(
                live =
                    liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Room Phabe", roomRole = "member"), screenSharing = true))),
                videoTracks = listOf(liveVideoTrack(identity = "phabe", isScreenShare = true, isLocal = false)),
                stageSelection = LiveStageSelection.track(identity = "phabe", isScreenShare = true),
            ),
        )
        val card = liveMemberCardTag("phabe")
        assertTrue(exists(card))
        assertEquals(0, withinCount(card, hasTestTag("live-member:screen-share-thumbnail")))
        assertEquals(1, withinCount(card, hasTestTag(liveMemberAvatarTag("phabe"))))
        assertEquals(0, withinCount(card, hasTestTag(liveVideoTrackViewTag)))
    }

    @Test
    fun `focused camera leaves member card in avatar layout`() {
        var renderedFit: LiveVideoTrackFit? = null
        installBlackRenderer { _, fit, _ -> renderedFit = fit }
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Room Phabe", roomRole = "member"), cameraOn = true))),
                videoTracks = listOf(liveVideoTrack(identity = "phabe", isScreenShare = false, isLocal = false)),
                stageSelection = LiveStageSelection.track(identity = "phabe", isScreenShare = false),
            ),
        )
        val card = liveMemberCardTag("phabe")
        assertTrue(exists(card))
        assertEquals(0, withinCount(card, hasTestTag("live-member:camera-thumbnail")))
        assertEquals(1, withinCount(card, hasTestTag(liveMemberAvatarTag("phabe"))))
        assertEquals(0, withinCount(card, hasTestTag(liveVideoTrackViewTag)))
        assertEquals(LiveVideoTrackFit.contain, renderedFit)
    }

    @Test
    fun `remote screen share stage exposes bottom-right mute control`() {
        installBlackRenderer()
        var muteToggles = 0
        val volumeChanges = mutableListOf<Double>()
        pump(
            LivePaneParams(
                live =
                    liveState(
                        listOf(
                            liveParticipant(
                                id = "live_phabe",
                                user = liveUser("phabe", "Phabe", roomRole = "member"),
                                screenSharing = true,
                                screenViewers = listOf(liveTestCurrentUser.toSummary().copyWith(roomDisplayName = "Room Kai")),
                            ),
                        ),
                    ),
                height = 620.0,
                videoTracks = listOf(liveVideoTrack(identity = "phabe", isScreenShare = true, isLocal = false)),
                stageSelection = LiveStageSelection.track(identity = "phabe", isScreenShare = true),
                screenShareVolume = 0.75,
                onScreenShareVolumeChanged = { volumeChanges.add(it) },
                onScreenShareMuteToggled = { muteToggles += 1 },
            ),
        )
        val muteButton = "live-stage:screen-share-volume"
        assertTrue(exists(muteButton))
        assertFalse(exists("live-volume-slider:共享屏幕输出音量"))

        val rendererRect = rect("live-video-renderer:phabe:true")
        val buttonRect = rect(muteButton)
        assertEquals((rendererRect.right - 8.dp).value, buttonRect.right.value, 0.01f)
        assertEquals((rendererRect.bottom - 8.dp).value, buttonRect.bottom.value, 0.01f)
        val viewerPreview = "live-stage:screen-viewers"
        assertTrue(exists(viewerPreview))
        assertEquals(1, compose.within(viewerPreview, hasText("ME")).fetchSemanticsNodes().size)
        compose.onNodeWithText("共 1 人").assertExists()
        val viewerRect = rect(viewerPreview)
        assertEquals((rendererRect.left + 8.dp).value, viewerRect.left.value, 0.01f)
        assertEquals((rendererRect.bottom - 8.dp).value, viewerRect.bottom.value, 0.01f)

        hover(muteButton)
        val slider = "live-volume-slider:共享屏幕输出音量"
        assertTrue(exists(slider))
        val sliderRect = rect(slider)
        tapAt((sliderRect.left + sliderRect.right) / 2, sliderRect.bottom - 1.dp)
        assertEquals(0.0, volumeChanges.last(), 0.02)

        compose.tag(muteButton).performClick()
        compose.waitForIdle()
        assertEquals(1, muteToggles)
    }

    @Test
    fun `Android screen share volume toggles and reflects a zero slider value`() {
        installBlackRenderer()
        var rememberedVolume = 0.75
        lateinit var harness: LivePaneHarness
        harness =
            pump(
                LivePaneParams(
                    live =
                        liveState(listOf(liveParticipant(id = "live_phabe", user = liveUser("phabe", "Phabe", roomRole = "member"), screenSharing = true))),
                    height = 620.0,
                    videoTracks = listOf(liveVideoTrack(identity = "phabe", isScreenShare = true, isLocal = false)),
                    stageSelection = LiveStageSelection.track(identity = "phabe", isScreenShare = true),
                    screenShareVolume = 0.75,
                    onScreenShareVolumeChanged = { volume ->
                        harness.pump {
                            screenShareVolume = volume
                            if (volume > 0) rememberedVolume = volume
                        }
                    },
                    onScreenShareMuteToggled = {
                        harness.pump {
                            if (screenShareVolume <= 0) {
                                screenShareVolume = rememberedVolume
                            } else {
                                rememberedVolume = screenShareVolume
                                screenShareVolume = 0.0
                            }
                        }
                    },
                ),
            )
        val button = "live-stage:screen-share-volume"
        assertEquals(1, withinCount(button, hasIcon(Icons.AutoMirrored.Filled.VolumeUp)))

        compose.tag(button).performClick()
        compose.waitForIdle()
        assertEquals(0.0, harness.params.screenShareVolume, 0.0)
        assertEquals(1, withinCount(button, hasIcon(Icons.AutoMirrored.Filled.VolumeOff)))

        compose.tag(button).performClick()
        compose.waitForIdle()
        assertEquals(0.75, harness.params.screenShareVolume, 0.0)

        compose.tag(button).performTouchInput { longClick() }
        compose.waitForIdle()
        val slider = "live-volume-slider:共享屏幕输出音量"
        assertTrue(exists(slider))
        // The long press won: it did not also toggle the mute.
        assertEquals(0.75, harness.params.screenShareVolume, 0.0)
        val sliderRect = rect(slider)
        tapAt((sliderRect.left + sliderRect.right) / 2, sliderRect.bottom - 1.dp)
        assertEquals(0.0, harness.params.screenShareVolume, 0.02)
        assertEquals(1, withinCount(button, hasIcon(Icons.AutoMirrored.Filled.VolumeOff)))
    }

    @Test
    fun `suspended stage releases its renderer while preserving the stage surface`() {
        installBlackRenderer()
        pump(
            LivePaneParams(
                height = 620.0,
                videoTracks = listOf(liveVideoTrack(identity = "phabe", isScreenShare = true, isLocal = false)),
                stageSelection = LiveStageSelection.track(identity = "phabe", isScreenShare = true),
                suspendStageVideo = true,
            ),
        )
        assertFalse(exists("live-video-renderer:phabe:true"))
        assertTrue(exists("live-stage:video-suspended"))
        assertTrue(exists("live-stage:fullscreen"))
    }

    @Test
    fun `local screen share stage opens full screen without remote-only controls`() {
        installBlackRenderer()
        var fullScreenTrack: LiveVideoTrack? = null
        pump(
            LivePaneParams(
                live = liveState(listOf(liveParticipant(id = "live_self", user = me, screenSharing = true))),
                height = 620.0,
                videoTracks = listOf(liveVideoTrack(identity = "current_user", isScreenShare = true, isLocal = true)),
                stageSelection = LiveStageSelection.track(identity = "current_user", isScreenShare = true),
                onEnterFullScreen = { fullScreenTrack = it },
            ),
        )
        assertTrue(exists("live-stage:exit"))
        assertTrue(exists("live-stage:fullscreen"))
        assertFalse(exists("live-stage:screen-share-volume"))
        assertFalse(exists("live-stage:screen-viewers"))
        val rendererRect = rect("live-video-renderer:current_user:true")
        assertEquals((rendererRect.right - 8.dp).value, rect("live-stage:fullscreen").right.value, 0.01f)

        compose.tag("live-stage:fullscreen").performClick()
        compose.waitForIdle()
        assertEquals("current_user", fullScreenTrack?.identity)
        assertEquals(true, fullScreenTrack?.isScreenShare)
        assertEquals(true, fullScreenTrack?.isLocal)
    }

    private fun revealVisible(tag: String): Boolean {
        val node = compose.tag(tag).fetchSemanticsNode()
        return node.config[LiveOpacityKey] == 1f
    }

    @Test
    fun `full screen live stage exits with Escape`() {
        installBlackRenderer()
        var exitCount = 0
        compose.setHomeContent(viewPadding = EdgeInsets(6.dp, 20.dp, 24.dp, 30.dp)) { _ ->
            LiveFullScreenStage(
                track = liveVideoTrack(identity = "phabe", isScreenShare = true, isLocal = false),
                label = "Phabe 的屏幕共享",
                screenShareViewers = listOf(liveTestCurrentUser.toSummary()),
                screenShareVolume = 0.75,
                onScreenShareVolumeChanged = {},
                onScreenShareMuteToggled = {},
                onExit = { exitCount++ },
                systemUiController = FullScreenSystemUiController(setEnabledSystemUIMode = { _, _ -> }),
            )
        }
        val label = "live-fullscreen-stage:label-reveal"
        val viewers = "live-fullscreen-stage:screen-viewers-reveal"
        val exit = "live-fullscreen-stage:exit-reveal"
        val volume = "live-fullscreen-stage:volume-reveal"
        for (tag in listOf(label, viewers, exit, volume)) assertTrue(tag, exists(tag))
        assertEquals(20f, rect(label).left.value, 0.01f)
        assertEquals(34f, rect(label).top.value, 0.01f)
        assertEquals(762f, rect(exit).right.value, 0.01f)
        assertEquals(762f, rect(volume).right.value, 0.01f)
        assertEquals(556f, rect(volume).bottom.value, 0.01f)
        assertTrue(revealVisible(label))
        assertTrue(revealVisible(viewers))

        hover(label)
        compose.mainClock.advanceTimeBy(3000)
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        for (tag in listOf(label, viewers, exit, volume)) assertFalse(tag, revealVisible(tag))

        compose.tag(viewers).performMouseInput { moveTo(center) }
        compose.waitForIdle()
        for (tag in listOf(label, viewers, exit, volume)) assertTrue(tag, revealVisible(tag))

        compose.mainClock.advanceTimeBy(2000)
        compose.tag(label).performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        assertTrue(revealVisible(label))

        compose.onNodeWithTag(liveFullScreenStageTag).performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertEquals(1, exitCount)
    }

    @Test
    fun `full screen controls appear immediately and restart timeout on touch`() {
        installBlackRenderer()
        val systemUiModes = mutableListOf<SystemUiMode>()
        val controller = FullScreenSystemUiController(setEnabledSystemUIMode = { mode, _ -> systemUiModes.add(mode) })
        var showStage by mutableStateOf(true)
        compose.setHomeContent { _ ->
            if (showStage) {
                LiveFullScreenStage(
                    track = liveVideoTrack(identity = "phabe", isScreenShare = true, isLocal = false),
                    label = "Phabe 的屏幕共享",
                    screenShareViewers = listOf(liveTestCurrentUser.toSummary()),
                    screenShareVolume = 0.75,
                    onScreenShareVolumeChanged = {},
                    onScreenShareMuteToggled = {},
                    onExit = {},
                    systemUiController = controller,
                )
            }
        }
        assertEquals(SystemUiMode.manual, systemUiModes.last())
        val label = "live-fullscreen-stage:label-reveal"

        compose.mainClock.advanceTimeBy(3000)
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        assertFalse(revealVisible(label))
        assertEquals(SystemUiMode.immersiveSticky, systemUiModes.last())

        val stage = compose.onNodeWithTag(liveFullScreenStageTag)
        stage.performTouchInput { down(center) }
        compose.waitForIdle()
        assertTrue(revealVisible(label))
        assertEquals(SystemUiMode.manual, systemUiModes.last())

        compose.mainClock.advanceTimeBy(2500)
        stage.performTouchInput { moveBy(Offset(12.dp.toPx(), 8.dp.toPx())) }
        compose.waitForIdle()
        assertTrue(revealVisible(label))

        compose.mainClock.advanceTimeBy(2500)
        compose.waitForIdle()
        assertTrue(revealVisible(label))

        stage.performTouchInput { up() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2900)
        compose.waitForIdle()
        assertTrue(revealVisible(label))
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertFalse(revealVisible(label))
        assertEquals(SystemUiMode.immersiveSticky, systemUiModes.last())

        showStage = false
        compose.waitForIdle()
        assertEquals(SystemUiMode.manual, systemUiModes.last())
    }

    @Test
    fun `full screen camera label and exit fully auto-hide`() {
        installBlackRenderer()
        compose.setHomeContent { _ ->
            LiveFullScreenStage(
                track = liveVideoTrack(identity = "phabe", isScreenShare = false, isLocal = false),
                label = "Phabe 的摄像头",
                screenShareVolume = 0.75,
                onScreenShareVolumeChanged = {},
                onScreenShareMuteToggled = {},
                onExit = {},
                systemUiController = FullScreenSystemUiController(setEnabledSystemUIMode = { _, _ -> }),
            )
        }
        val label = "live-fullscreen-stage:label-reveal"
        val exit = "live-fullscreen-stage:exit-reveal"
        assertTrue(exists(label))
        assertTrue(exists(exit))
        assertFalse(exists("live-fullscreen-stage:screen-viewers-reveal"))
        assertFalse(exists("live-fullscreen-stage:volume-reveal"))
        compose.mainClock.advanceTimeBy(3000)
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        assertFalse(revealVisible(label))
        assertFalse(revealVisible(exit))
    }

    @Test
    fun `live member names use self and room role colors`() {
        pump(
            LivePaneParams(
                live =
                    liveState(
                        listOf(
                            liveParticipant(id = "live_self", user = liveTestCurrentUser.toSummary().copyWith(roomDisplayName = "Room Self", roomRole = "member")),
                            liveParticipant(id = "live_member", user = liveUser("member", "Room Member", roomRole = "member")),
                            liveParticipant(id = "live_admin", user = liveUser("admin", "Room Admin", roomRole = "admin")),
                            liveParticipant(id = "live_owner", user = liveUser("owner", "Room Owner", roomRole = "owner")),
                            liveParticipant(id = "live_superuser", user = liveUser("superuser", "Room Root", roomRole = "member", isSuperuser = true)),
                        ),
                    ),
                height = 620.0,
            ),
        )
        assertEquals(UiColors.accent, compose.onNodeWithText("Room Self", useUnmergedTree = true).textColor())
        assertEquals(UiColors.roleMember, compose.onNodeWithText("Room Member", useUnmergedTree = true).textColor())
        assertEquals(UiColors.roleAdmin, compose.onNodeWithText("Room Admin", useUnmergedTree = true).textColor())
        assertEquals(UiColors.roleCreator, compose.onNodeWithText("Room Owner", useUnmergedTree = true).textColor())
        assertEquals(UiColors.roleSuperuser, compose.onNodeWithText("Room Root", useUnmergedTree = true).textColor())
    }

    @Test
    fun `live buttons show hover info below their targets`() {
        pump(LivePaneParams(live = liveState(listOf(liveParticipant(id = "live_self", user = me)))))
        assertBelowTooltip("关闭麦克风")
        assertBelowTooltip("关闭耳机")
        assertBelowTooltip("开启摄像头")

        hover("live-control:camera")
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val info = compose.onNode(hasText("开启摄像头"))
        info.assertExists()
        val infoTop = info.fetchSemanticsNode().positionOnScreen.y
        val cameraNode = compose.tag("live-control:camera").fetchSemanticsNode()
        val cameraBottom = cameraNode.positionOnScreen.y + cameraNode.size.height
        assertTrue("tooltip top $infoTop below camera bottom $cameraBottom", infoTop > cameraBottom)

        compose.tag("live-control:camera").performMouseInput { exit() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w960dp-h740dp-mdpi")
    fun `music box keeps one height and rises above wrapped live controls`() {
        var closeTaps = 0
        val harness =
            pump(
                LivePaneParams(
                    width = 360.0,
                    height = 740.0,
                    musicBox = emptyMusicBoxState,
                    musicBoxOpen = true,
                    onToggleMusicBox = { closeTaps += 1 },
                    compactLayout = true,
                ),
            )
        fun checkPanel(label: String): androidx.compose.ui.unit.DpRect {
            assertTrue(exists(liveMusicBoxPanelTag))
            assertTrue(exists("live-control:music-inline"))
            assertEquals(1, withinCount("live-control:music-queue", hasIcon(Icons.Filled.LibraryMusic)))
            val panel = rect(liveMusicBoxPanelTag)
            assertTrue("$label music box should extend upward instead of scrolling", panel.heightDp >= 400.dp)
            assertTrue("$label must keep the raised panel interactive", panel.top >= 0.dp)
            assertTrue("$label music box must not overlap its player", panel.bottom <= rect("live-control:music-inline").top)
            return panel
        }

        val compactPanel = checkPanel("compact")
        compose.onNode(hasTestTag("music-box-close") and hasAnyAncestor(hasTestTag(liveMusicBoxPanelTag)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, closeTaps)

        repump(harness) {
            width = 960.0
            compactLayout = false
        }
        val widePanel = checkPanel("wide")
        assertEquals("compact and wide keep the standard music box height", widePanel.heightDp.value, compactPanel.heightDp.value, 0.01f)
        assertTrue("wrapped compact controls should move the fixed-height panel upward", compactPanel.top < widePanel.top)

        repump(harness) {
            width = 320.0
            compactLayout = true
        }
        val limited = rect(liveMusicBoxPanelTag)
        assertEquals(0f, limited.top.value, 0.01f)
        assertTrue("the panel should shrink only after it reaches the screen top", limited.heightDp < compactPanel.heightDp)
        assertTrue(limited.bottom <= rect("live-control:music-inline").top)
    }

    @Test
    fun `collapsed music panel keeps the music box toggle icon`() {
        pump(LivePaneParams(musicBox = emptyMusicBoxState))
        assertEquals(1, withinCount("live-control:music-queue", hasIcon(Icons.Filled.LibraryMusic)))
    }

    @Test
    fun `inline music title moves when constrained and opens the existing song card`() {
        val longTitle = "一首需要在迷你播放模块中左右往返显示完整内容的特别长歌曲名称"
        val item =
            MusicBoxQueueItem(
                id = "inline-current",
                source = "netease",
                trackId = "inline-track",
                title = longTitle,
                artist = "测试歌手",
                durationMs = 200000,
                status = MusicBoxQueueItemStatus.ready,
                fileSizeBytes = 0,
                error = "",
                addedByUserId = "current_user",
                createdAt = null,
            )
        val musicBox =
            MusicBoxState(
                enabled = true,
                playback = MusicBoxPlayback(state = MusicBoxPlaybackState.playing, currentItemId = "inline-current", positionMs = 30000, volume = 100, updatedAt = null),
                queue = listOf(item),
                usage = MusicBoxUsage(usedBytes = 0, limitBytes = 200L * 1024 * 1024),
            )
        compose.mainClock.autoAdvance = false
        pump(LivePaneParams(width = 960.0, musicBox = musicBox))
        compose.mainClock.advanceTimeByFrame()
        val track = "live-control:music-title-marquee-track"
        assertTrue(exists(track))
        // The track sits in a clipped viewport, so read its unclipped bounds
        // (Dart reads the Transform's translation).
        val start = compose.tag(track).getUnclippedBoundsInRoot().left
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.advanceTimeBy(400)
        assertTrue("marquee track moved left", compose.tag(track).getUnclippedBoundsInRoot().left < start)

        compose.tag("live-control:music-current-card").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertTrue(exists("music-box-song-card:inline-current"))
        assertTrue(compose.onAllNodes(hasText(longTitle)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-mdpi")
    fun `Android docked music box search keeps focus when IME opens`() {
        var viewInsets by mutableStateOf(EdgeInsets.zero)
        val harness =
            LivePaneHarness(
                LivePaneParams(
                    width = 360.0,
                    height = 740.0,
                    musicBox = emptyMusicBoxState,
                    musicBoxOpen = true,
                    preserveKeyboardViewport = true,
                ),
            )
        compose.setHomeContent { _ ->
            val media = com.gangchat.client.widgets.LocalMediaQuery.current
            androidx.compose.runtime.CompositionLocalProvider(com.gangchat.client.widgets.LocalMediaQuery provides media.copy(viewInsets = viewInsets)) {
                LiveChannelPaneHost(harness.params, harness.searchController)
            }
        }
        compose.tag("music-box-search-toggle").performClick()
        compose.waitForIdle()
        val search = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("music-box-search-input")))
        search.assertExists()
        search.performClick()
        compose.waitForIdle()
        search.assertIsFocused()

        viewInsets = EdgeInsets(bottom = 300.dp)
        compose.waitForIdle()
        search.assertIsFocused()
    }

    @Test
    fun `live stage track resolution prefers the selection and falls back to a local share`() {
        // Dart exposes `resolveLiveStageTrackForTest` for this pure rule.
        val localShare = liveVideoTrack(identity = "current_user", isScreenShare = true, isLocal = true)
        val remoteCamera = liveVideoTrack(identity = "phabe", isScreenShare = false, isLocal = false)
        val tracks = listOf(remoteCamera, localShare)
        assertEquals(localShare, resolveLiveStageTrackForTest(tracks, selection = null))
        assertNull(resolveLiveStageTrackForTest(tracks, selection = LiveStageSelection.none()))
        assertEquals(remoteCamera, resolveLiveStageTrackForTest(tracks, LiveStageSelection.track("phabe", isScreenShare = false)))
        assertNull(resolveLiveStageTrackForTest(tracks, LiveStageSelection.track("phabe", isScreenShare = true)))
        assertNotNull(liveStageTrackLabel(null, remoteCamera))
        assertEquals("摄像头", liveStageTrackLabel(null, remoteCamera))
        assertEquals("屏幕共享", liveStageTrackLabel(null, localShare))
    }

    @Suppress("unused")
    private fun near(a: Float, b: Float, tolerance: Float) = abs(a - b) <= tolerance
}

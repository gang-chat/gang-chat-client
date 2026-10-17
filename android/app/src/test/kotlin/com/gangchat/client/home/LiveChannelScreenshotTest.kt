package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.gangchat.client.live.liveVideoTrackRendererForTest
import com.gangchat.client.live.resetLiveVideoTrackRendererForTest
import com.gangchat.client.protocol.MusicBoxActiveSource
import com.gangchat.client.protocol.MusicBoxPlaybackMode
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxQueueItemStatus
import com.gangchat.client.protocol.MusicBoxRequester
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.widgets.Text
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val out = "build/outputs/roborazzi"

/** Screenshots of the live channel pane (phone compact and tablet wide). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class LiveChannelScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        installTestImageLoader()
        liveVideoTrackRendererForTest = { track, _, _ ->
            val colors =
                if (track.isScreenShare) {
                    listOf(Color(0xFF1B3A5C), Color(0xFF0E1D2B))
                } else {
                    listOf(Color(0xFF5C3A1B), Color(0xFF2B1D0E))
                }
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
                Text(if (track.isScreenShare) "SCREEN · ${track.identity}" else "CAM · ${track.identity}", style = androidx.compose.ui.text.TextStyle(color = Color.White))
            }
        }
    }

    @After
    fun tearDown() = resetLiveVideoTrackRendererForTest()

    private val alice = liveUser("alice", "Alice", roomRole = "admin")
    private val bao = liveUser("bao", "鲍哥", roomRole = "member")
    private val carol = liveUser("carol", "Carol", roomRole = "owner")
    private val david = liveUser("david", "大卫", roomRole = "member")
    private val me = liveTestCurrentUser.toSummary().copyWith(roomDisplayName = "Demo", roomRole = "member")

    private fun voiceParticipants() =
        listOf(
            liveParticipant(id = "l_me", user = me),
            liveParticipant(id = "l_alice", user = alice),
            liveParticipant(id = "l_bao", user = bao, micMuted = true),
            liveParticipant(id = "l_carol", user = carol, headphonesMuted = true, headphonesBlocked = true),
            liveParticipant(id = "l_david", user = david, micBlocked = true, micMuted = true),
        )

    private fun phoneParams() =
        LivePaneParams(
            width = 411.0,
            height = 891.0,
            live = liveState(voiceParticipants()),
            speakingUserIds = setOf("current_user", "alice"),
            participantVoiceVolume = { id -> if (id == "bao") 0.0 else if (id == "david") 0.4 else 1.0 },
            canModerateParticipant = { it.user.id == "david" || it.user.id == "carol" },
            canRemoveParticipant = { it.user.id != "alice" },
            compactLayout = true,
        )

    @Test
    fun phoneVoiceStage() {
        val harness = LivePaneHarness(phoneParams())
        compose.setLivePane(harness)
        compose.onRoot().captureRoboImage("$out/live_phone_voice_stage.png")
    }

    @Test
    fun phoneNotJoined() {
        val harness = LivePaneHarness(phoneParams().apply { joined = false })
        compose.setLivePane(harness)
        compose.onRoot().captureRoboImage("$out/live_phone_not_joined.png")
    }

    @Test
    fun phoneMediaStage() {
        val harness =
            LivePaneHarness(
                phoneParams().apply {
                    live =
                        liveState(
                            listOf(
                                liveParticipant(id = "l_me", user = me, cameraOn = true),
                                liveParticipant(id = "l_alice", user = alice, cameraOn = true),
                                liveParticipant(id = "l_carol", user = carol, screenSharing = true, screenViewers = listOf(me, alice, bao)),
                                liveParticipant(id = "l_bao", user = bao, screenSharing = true),
                            ),
                        )
                    videoTracks =
                        listOf(
                            liveVideoTrack("current_user", isScreenShare = false, isLocal = true),
                            liveVideoTrack("alice", isScreenShare = false, isLocal = false),
                            liveVideoTrack("carol", isScreenShare = true, isLocal = false),
                        )
                    stageSelection = LiveStageSelection.track(identity = "carol", isScreenShare = true)
                    cameraOn = true
                    screenShareVolume = 0.4
                },
            )
        compose.setLivePane(harness)
        compose.onRoot().captureRoboImage("$out/live_phone_media_stage.png")
    }

    @Test
    fun phoneCameraStage() {
        val harness =
            LivePaneHarness(
                phoneParams().apply {
                    live = liveState(listOf(liveParticipant(id = "l_me", user = me, cameraOn = true), liveParticipant(id = "l_alice", user = alice)))
                    videoTracks = listOf(liveVideoTrack("current_user", isScreenShare = false, isLocal = true))
                    stageSelection = LiveStageSelection.track(identity = "current_user", isScreenShare = false)
                    onFlipCamera = { true }
                    cameraOn = true
                },
            )
        compose.setLivePane(harness)
        compose.onRoot().captureRoboImage("$out/live_phone_camera_stage.png")
    }

    @Test
    fun phoneMemberVolumeMenu() {
        val harness = LivePaneHarness(phoneParams())
        compose.setLivePane(harness)
        compose.tag("live-member-status:voice-volume:david").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/live_phone_member_volume.png")
    }

    @Test
    fun phoneControlVolumeMenu() {
        val harness = LivePaneHarness(phoneParams().apply { inputVolume = 0.65 })
        compose.setLivePane(harness)
        compose.tag("live-control:mic").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/live_phone_mic_volume.png")
    }

    private val requester =
        MusicBoxRequester(userId = "alice", displayName = "Alice", avatarUrl = null, defaultAvatarKey = "purple-2")

    private fun musicBoxPlaying() =
        musicState(
            playbackState = MusicBoxPlaybackState.playing,
            positionMs = 83_000,
            currentItemId = "q2",
            queue =
                listOf(
                    musicTrack("q1", title = "晴天", artist = "周杰伦", requestedBy = requester, canPlayNow = true),
                    musicTrack("q2", title = "平凡之路", artist = "朴树", requestedBy = requester),
                    musicTrack("q3", title = "夜空中最亮的星", artist = "逃跑计划", canPlayNow = true),
                    musicTrack("q4", title = "下载中的一首歌", artist = "未知", status = MusicBoxQueueItemStatus.downloading),
                    musicTrack("q5", title = "一首非常非常长的歌曲名称用来检查换行与缩小字体的效果", artist = "很长的歌手名字"),
                ),
            temporaryQueue = null,
            activeSource = MusicBoxActiveSource(),
            mode = MusicBoxPlaybackMode.repeatAll,
            canPrevious = true,
            canNext = true,
        ).let { state ->
            com.gangchat.client.protocol.MusicBoxState(
                enabled = true,
                playback = state.playback,
                queue = state.queue,
                usage = state.usage,
                temporaryQueue = state.queue,
                temporaryQueuedCount = state.queue.size,
            )
        }

    @Test
    fun phoneMusicBox() {
        val harness =
            LivePaneHarness(
                phoneParams().apply {
                    musicBox = musicBoxPlaying()
                    musicBoxOpen = true
                },
            )
        compose.setLivePane(harness)
        compose.onRoot().captureRoboImage("$out/live_phone_music_box.png")
    }

    @Test
    fun phoneMusicStrip() {
        val harness = LivePaneHarness(phoneParams().apply { musicBox = musicBoxPlaying() })
        compose.setLivePane(harness)
        compose.onRoot().captureRoboImage("$out/live_phone_music_strip.png")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun tabletWide() {
        val harness =
            LivePaneHarness(
                phoneParams().apply {
                    width = 1032.0
                    height = 800.0
                    compactLayout = false
                    live =
                        liveState(
                            voiceParticipants() +
                                listOf(liveParticipant(id = "l_eve", user = liveUser("eve", "Eve", roomRole = "member"), screenSharing = true)),
                        )
                    videoTracks = listOf(liveVideoTrack("eve", isScreenShare = true, isLocal = false))
                    stageSelection = LiveStageSelection.track(identity = "eve", isScreenShare = true)
                    musicBox = musicBoxPlaying()
                    musicBoxOpen = true
                },
            )
        compose.setHomeContent { _ ->
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.TopEnd).testTag("content")) {
                    LiveChannelPaneHost(harness.params, harness.searchController)
                }
            }
        }
        compose.onRoot().captureRoboImage("$out/live_tablet_wide.png")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun fullScreenStage() {
        compose.setHomeContent { _ ->
            LiveFullScreenStage(
                track = liveVideoTrack("carol", isScreenShare = true, isLocal = false),
                label = "Carol 的屏幕",
                screenShareViewers = listOf(me, alice),
                screenShareVolume = 0.75,
                onScreenShareVolumeChanged = {},
                onScreenShareMuteToggled = {},
                onExit = {},
                systemUiController = com.gangchat.client.shell.FullScreenSystemUiController(setEnabledSystemUIMode = { _, _ -> }),
            )
        }
        compose.onRoot().captureRoboImage("$out/live_fullscreen_stage.png")
    }
}

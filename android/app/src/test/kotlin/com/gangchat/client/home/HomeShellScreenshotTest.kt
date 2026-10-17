package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import com.gangchat.client.live.liveVideoTrackRendererForTest
import com.gangchat.client.live.resetLiveVideoTrackRendererForTest
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val out = "build/outputs/roborazzi"
private const val phone = "w411dp-h891dp-xxhdpi"
private const val tablet = "w1280dp-h800dp-xhdpi"

/**
 * Roborazzi captures of the whole home screen: the phone's room list, chat,
 * live channel, notifications and search screens, and the tablet's wide
 * layout with a chat, the live channel and room settings open.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = phone, shadows = [ShadowNoOpMagnifier::class])
class HomeShellScreenshotTest : HomeShellTestBase() {
    @After
    fun resetRenderer() = resetLiveVideoTrackRendererForTest()

    private fun capture(name: String) {
        compose.settleShell()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("$out/$name.png")
    }

    private fun pumpHome(api: HomeShellTestApi = HomeShellTestApi(pinAlphaRoom = true, includeUnreadRoomNotification = true)) {
        compose.setHomePage(
            app = homeTestAppContext(api),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = FakeHomeLiveSession()),
        )
        compose.settleShell()
    }

    private fun joinWithMorgan(realtime: FakeHomeRealtimeService) {
        openAlphaAndJoinLive()
        realtime.add(
            "live_participant_updated",
            mapOf(
                "room_id" to "server-alpha",
                "participant_count" to 2,
                "preview" to emptyList<Any?>(),
                "live" to
                    homeShellLiveStateJson(
                        roomId = "server-alpha",
                        participantCount = 2,
                        updatedAt = "2099-06-05T08:00:00Z",
                        participants =
                            listOf(
                                homeShellLiveParticipantJson(user = homeShellCurrentUserJson, liveSessionId = "live-session-joined"),
                                homeShellLiveParticipantJson(
                                    user = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan"),
                                    liveSessionId = "live-session-morgan",
                                    micMuted = true,
                                ),
                            ),
                    ),
            ),
        )
        compose.settleShell()
    }

    private fun pumpLiveHome(): FakeHomeRealtimeService {
        liveVideoTrackRendererForTest = { _, _, _ -> Box(Modifier.fillMaxSize().background(Color.Black)) }
        val realtime = FakeHomeRealtimeService()
        compose.setHomePage(
            app = homeTestAppContext(HomeShellTestApi(pinAlphaRoom = true)),
            realtime = realtime,
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = FakeHomeLiveSession()),
        )
        compose.settleShell()
        return realtime
    }

    @Test
    fun phoneRoomList() {
        pumpHome()
        capture("home_shell_phone_room_list")
    }

    @Test
    fun phoneChat() {
        pumpHome()
        tapText("Alpha Room")
        capture("home_shell_phone_chat")
    }

    @Test
    fun phoneLive() {
        val realtime = pumpLiveHome()
        joinWithMorgan(realtime)
        capture("home_shell_phone_live")
    }

    @Test
    fun phoneNotifications() {
        pumpHome()
        tapTooltip("通知")
        capture("home_shell_phone_notifications")
    }

    @Test
    fun phoneSearch() {
        pumpHome()
        enterSearch("Beta")
        capture("home_shell_phone_search")
    }

    @Test
    @Config(qualifiers = tablet)
    fun tabletChat() {
        pumpHome()
        tapText("Alpha Room")
        capture("home_shell_tablet_chat")
    }

    @Test
    @Config(qualifiers = tablet)
    fun tabletLive() {
        val realtime = pumpLiveHome()
        joinWithMorgan(realtime)
        capture("home_shell_tablet_live")
    }

    @Test
    @Config(qualifiers = tablet)
    fun tabletRoomSettings() {
        pumpHome()
        tapText("Alpha Room")
        tapTooltip("房间设置")
        capture("home_shell_tablet_room_settings")
    }

    @Test
    @Config(qualifiers = tablet)
    fun tabletSearch() {
        pumpHome()
        tapText("Alpha Room")
        enterSearch("Beta")
        capture("home_shell_tablet_search")
    }
}

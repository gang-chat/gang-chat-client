package com.gangchat.client.home

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.GlobalSearchCategory
import com.gangchat.client.app.ReleaseAsset
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Uri
import com.gangchat.client.plugins.SharedPreferences
import com.gangchat.client.shell.ShellTestLoop
import com.gangchat.client.shell.assertDpEquals
import com.gangchat.client.shell.centerX
import com.gangchat.client.shell.centerY
import com.gangchat.client.shell.rect
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.avatarInitials
import com.gangchat.client.ui.clipboardText
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.onAllTooltips
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.settingsSideNavigationTag
import com.gangchat.client.ui.textLayout
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/**
 * The home shell cases of test/widget/shell/gang_app_shell_test_parts/
 * home_widget_tests.dart, hosting [HomePage] like the Dart suite.
 *
 * The Dart suite ran on an 800 x 600 desktop surface, mostly with the
 * Windows theme; these run on Android windows of the same size, so the title
 * bar carries no window buttons and the live room module sits on the right of
 * the search field (Dart's non-macOS placement). Dart's `SizedBox(width:)`
 * around the page becomes the host's page size inside a larger window.
 * Mouse hovers and secondary clicks are taps and holds.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class HomeShellWidgetTest : HomeShellTestBase() {
    // --- home_widget_tests.dart -----------------------------------------------

    @Test
    fun `authenticated home shell renders server-only sidebar`() {
        val api = HomeShellTestApi(pinAlphaRoom = true)
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms"))
        assertEquals(0, compose.textNodes("Gang Chat"))
        assertTrue(tagExists(homeTitleSearchTag))
        // Android draws no window buttons.
        assertEquals(0, compose.onAllTooltips("最小化").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllTooltips("最大化").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllTooltips("关闭").fetchSemanticsNodes().size)
        for (text in listOf("Workspace", "Tools", "Rooms", "Activity", "People", "Files", "设置", "@kai")) {
            assertEquals(text, 0, compose.textNodes(text))
        }
        compose.onTooltip("创建房间").assertExists()
        compose.onTooltip("通知").assertExists()
        compose.onTooltip("设置").assertExists()
        compose.onTooltip("退出登录").assertExists()
        assertEquals(1, compose.textNodes("Kai"))
        assertEquals(1, compose.textNodes("在线"))
        assertEquals(1, compose.textNodes("Alpha Room"))
        assertEquals(1, compose.textNodes("Beta Room"))
        assertEquals(1, compose.textNodes("2 名成员 · 1 人语音"))
        assertEquals(1, compose.textNodes("5 名成员"))
        assertEquals(1, compose.textNodes("3"))
        assertTrue(tagExists(homeSidebarRoomLiveTag("server-alpha")))
        assertFalse(tagExists(homeSidebarRoomLiveTag("server-beta")))

        // Android places the search field left of the title-bar live room
        // module: 520 dp wide, as far right as the module's default width
        // allows (Dart's non-macOS placement).
        val searchRect = tag(homeTitleSearchTag).rect()
        assertDpEquals(28.dp, searchRect.left)
        assertDpEquals(520.dp, searchRect.right - searchRect.left)
        assertDpEquals(30.dp, searchRect.bottom - searchRect.top)

        val userSummaryRect = tag(homeSidebarUserSummaryTag).rect()
        val displayNameRect = compose.onNode(inTag(homeSidebarUserSummaryTag, hasText("Kai")), useUnmergedTree = true).rect()
        val statusRect = compose.onNode(hasText("在线"), useUnmergedTree = true).rect()
        val statusDotRect = tag(homeSidebarPresenceDotTag).rect()
        assertTrue(statusRect.top > displayNameRect.bottom)
        assertTrue(statusRect.left > userSummaryRect.left)
        assertTrue(statusRect.right < userSummaryRect.right)
        assertDpEquals(6.dp, statusDotRect.right - statusDotRect.left)
        assertDpEquals(6.dp, statusDotRect.bottom - statusDotRect.top)

        val alphaCardRect = tag(homeSidebarRoomTag("server-alpha")).rect()
        val alphaPinnedRect = tag(homeSidebarRoomPinnedTag("server-alpha")).rect()
        assertDpEquals(0.dp, userSummaryRect.right - alphaCardRect.right)
        assertDpEquals(alphaCardRect.left + 2.dp, alphaPinnedRect.left, tolerance = 1f)
        assertTrue(alphaPinnedRect.top < alphaCardRect.top + 8.dp)

        tapText("Alpha Room")

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/messages"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/live"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/read"))
        assertEquals(1, compose.textNodes("Hello from Morgan"))
        assertEquals(1, compose.textNodes("Reply from Kai"))
        assertEquals(1, compose.textNodes("未读消息"))
        assertFalse(tagExists(chatJumpToFirstNewTag))
        assertEquals(0, compose.textNodes("3"))
        assertTrue(tagExists(chatComposerTag))
        assertTrue(isSelected(tag(homeSidebarRoomTag("server-alpha"))))
    }

    @Test
    fun `detected app update opens settings update page in home shell`() {
        val update =
            AvailableAppUpdate(
                currentVersion = "0.4.0",
                latestVersion = "0.4.1",
                asset =
                    ReleaseAsset(
                        key = "releases/GangChat_v0.4.1.apk",
                        version = "0.4.1",
                        platform = AppUpdatePlatform.android,
                        releasedAt = DateTime.utc(2026, 7, 8, 1, 2),
                    ),
                downloadUrl = Uri.parse("https://os.example.test/gang-chat/releases/GangChat_v0.4.1.apk"),
            )
        var shownCount = 0
        compose.setHomePage(
            app = homeTestAppContext(),
            detectedAppUpdate = update,
            onDetectedAppUpdateShown = { shownCount += 1 },
        )
        compose.settleShell()

        assertEquals(1, shownCount)
        assertEquals(1, compose.textNodes("发现新版本"))
        assertTrue(compose.textNodes("v0.4.1") >= 1)
        assertEquals(1, compose.textNodes("版本日志"))
        assertEquals(0, compose.textNodes("继续使用"))

        tapTooltip("返回")

        assertEquals(0, compose.textNodes("发现新版本"))
        assertEquals(1, compose.textNodes("设置"))
        val aboutTile = compose.onNode(inTag(settingsSideNavigationTag, hasText("关于Gang Chat")))
        aboutTile.assertExists()
        assertTrue(isSelected(aboutTile))
        assertEquals(2, compose.textNodes("关于Gang Chat"))
        assertEquals(1, compose.textNodes("版本信息"))
    }

    @Test
    @Config(qualifiers = "w1200dp-h640dp-mdpi")
    fun `authenticated home shell keeps search in every non-narrow title bar`() {
        var pageWidth by mutableStateOf(1180.dp)
        compose.setHomePage(app = homeTestAppContext(), size = { DpSize(pageWidth, 620.dp) })
        compose.settleShell()
        val wide = tag(homeTitleSearchTag).rect()

        pageWidth = 900.dp
        compose.settleShell()
        val resized = tag(homeTitleSearchTag).rect()

        assertDpEquals(520.dp, wide.right - wide.left)
        assertDpEquals(30.dp, wide.bottom - wide.top)
        assertDpEquals(wide.right - wide.left, resized.right - resized.left)
        assertDpEquals(wide.bottom - wide.top, resized.bottom - resized.top)

        pageWidth = 760.dp
        compose.settleShell()
        val compactSearchRect = tag(homeTitleSearchTag).rect()
        val userSummaryRect = tag(homeSidebarUserSummaryTag).rect()
        assertTrue(compactSearchRect.right - compactSearchRect.left <= wide.right - wide.left)
        assertTrue(compactSearchRect.right - compactSearchRect.left >= 500.dp)
        assertDpEquals(wide.bottom - wide.top, compactSearchRect.bottom - compactSearchRect.top)
        assertTrue(compactSearchRect.bottom < userSummaryRect.top)
        // Android has no window buttons; the field stays inside the page.
        assertTrue(compactSearchRect.right <= 760.dp)

        enterSearch("Beta")

        val searchResultsRect = tag(homeTitleSearchResultsTag).rect()
        assertDpEquals(compactSearchRect.left, searchResultsRect.left)
        assertDpEquals(compactSearchRect.right - compactSearchRect.left, searchResultsRect.right - searchResultsRect.left)
    }

    @Test
    @Config(qualifiers = "w1200dp-h640dp-mdpi")
    fun `authenticated home shell title live room module controls joined voice`() {
        val api = HomeShellTestApi()
        val liveSession = FakeHomeLiveSession()
        var pageWidth by mutableStateOf(1180.dp)
        compose.setHomePage(
            app = homeTestAppContext(api),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            size = { DpSize(pageWidth, 620.dp) },
        )
        compose.settleShell()

        assertTrue(tagExists(homeTitleLiveRoomTag))
        assertTrue(dockHasText("未加入语音频道"))
        assertFalse(dockHasAvatar("Alpha Room"))
        assertFalse(dockHasIcon(Icons.AutoMirrored.Filled.VolumeUp))
        assertFalse(dockHasIcon(Icons.Filled.CallEnd))

        openAlphaAndJoinLive()

        val roomCardWidth = widthOf(homeSidebarRoomTag("server-alpha"))
        val expandedDockWidth = widthOf(homeTitleLiveRoomTag)
        val dockRect = tag(homeTitleLiveRoomTag).rect()
        val searchRect = tag(homeTitleSearchTag).rect()
        assertTrue(expandedDockWidth > roomCardWidth)
        assertDpEquals(30.dp, dockRect.bottom - dockRect.top)
        // Android: the module sits right of the search field.
        assertTrue(dockRect.left >= searchRect.right + 14.dp - 0.01.dp)
        assertTrue(dockHasText("Alpha Room"))
        assertTrue(dockHasIcon(Icons.AutoMirrored.Filled.VolumeUp))
        assertTrue(dockHasIcon(Icons.Filled.Mic))
        assertTrue(dockHasIcon(Icons.Filled.Headphones))
        assertTrue(dockHasIcon(Icons.Filled.CallEnd))

        tapTag(homeTitleLiveRoomMicTag)
        assertEquals(0.0, liveSession.inputVolumes.last(), 0.0)
        assertTrue(dockHasIcon(Icons.Filled.MicOff))

        tapTag(homeTitleLiveRoomHeadphonesTag)
        assertEquals(0.0, liveSession.outputVolumes.last(), 0.0)
        assertTrue(dockHasIcon(Icons.Filled.HeadsetOff))

        // Android keeps the module right of the search field, so it holds its
        // default width (the sidebar's, minus insets) while the search can
        // move left (900 and 850), then shrinks to the space between the
        // search and the edge (760: 760 - 10 - 640 - 14). Dart's Windows
        // widths (166, 141, 80) come from the module on the left.
        pageWidth = 900.dp
        compose.settleShell()
        assertTrue(tagExists(homeTitleLiveRoomTag))
        assertDpEquals(228.dp, widthOf(homeTitleLiveRoomTag))
        assertTrue(dockHasText("Alpha Room"))
        assertTrue(dockHasIcon(Icons.AutoMirrored.Filled.VolumeUp))
        assertTrue(dockHasAvatar("Alpha Room"))
        assertTrue(tagExists(homeTitleLiveRoomMicTag))
        assertTrue(tagExists(homeTitleLiveRoomHeadphonesTag))
        assertTrue(tagExists(homeTitleLiveRoomLeaveTag))

        pageWidth = 850.dp
        compose.settleShell()
        assertDpEquals(228.dp, widthOf(homeTitleLiveRoomTag))
        assertTrue(dockHasIcon(Icons.AutoMirrored.Filled.VolumeUp))
        assertTrue(dockHasAvatar("Alpha Room"))

        pageWidth = 760.dp
        compose.settleShell()
        assertDpEquals(96.dp, widthOf(homeTitleLiveRoomTag))
        assertFalse(dockHasText("Alpha Room"))
        assertFalse(dockHasIcon(Icons.AutoMirrored.Filled.VolumeUp))
        assertTrue(dockHasAvatar("Alpha Room"))
        assertTrue(tagExists(homeTitleLiveRoomLeaveTag))

        // Dart's 680 px step (the desktop title bar keeping the module) has
        // no Android equivalent: narrow Android windows move the module to
        // the room list's footer (see the Android narrow case).

        pageWidth = 1180.dp
        compose.settleShell()
        assertDpEquals(expandedDockWidth, widthOf(homeTitleLiveRoomTag))
        assertDpEquals(30.dp, tag(homeTitleLiveRoomTag).rect().let { it.bottom - it.top })

        tapText("Beta Room")
        assertFalse(tagExists("live-control:leave"))
        assertFalse(livePaneShown())

        val alphaDetailsBeforeLeave = api.requestedPaths.count { it == "/api/v1/rooms/server-alpha" }
        tapTag(homeTitleLiveRoomLeaveTag)

        assertEquals(1, liveSession.disconnects)
        assertTrue(tagExists(homeTitleLiveRoomTag))
        assertTrue(dockHasText("未加入语音频道"))
        assertFalse(dockHasAvatar("Alpha Room"))
        assertFalse(dockHasIcon(Icons.AutoMirrored.Filled.VolumeUp))
        assertFalse(dockHasIcon(Icons.Filled.CallEnd))
        assertFalse(tagExists("live-control:leave"))
        assertFalse(livePaneShown())
        assertTrue(compose.textNodes("Beta Room") >= 1)
        assertEquals(alphaDetailsBeforeLeave, api.requestedPaths.count { it == "/api/v1/rooms/server-alpha" })
    }

    @Test
    @Config(qualifiers = "w1180dp-h620dp-mdpi")
    fun `persistent live dock configures audio before join and remembers it after leaving`() {
        val api = HomeShellTestApi()
        val liveSession = FakeHomeLiveSession()
        compose.setHomePage(
            app = homeTestAppContext(api),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
        )
        compose.settleShell()

        assertTrue(dockHasIcon(Icons.Filled.Mic))
        assertTrue(dockHasIcon(Icons.Filled.Headphones))

        tapTag(homeTitleLiveRoomHeadphonesTag)
        assertTrue(api.liveStateUpdates.isEmpty())
        assertTrue(dockHasIcon(Icons.Filled.MicOff))
        assertTrue(dockHasIcon(Icons.Filled.HeadsetOff))

        openAlphaAndJoinLive()

        assertEquals(true, api.liveJoinRequests.single()["mic_muted"])
        assertEquals(true, api.liveJoinRequests.single()["headphones_muted"])
        assertEquals(true, api.liveStateUpdates.last()["mic_muted"])
        assertEquals(true, api.liveStateUpdates.last()["headphones_muted"])

        tapTag("live-control:leave")
        assertTrue(dockHasText("未加入语音频道"))
        assertTrue(dockHasIcon(Icons.Filled.MicOff))
        assertTrue(dockHasIcon(Icons.Filled.HeadsetOff))

        joinLive()
        assertEquals(2, liveSession.connectAttempts)
        assertEquals(true, api.liveJoinRequests.last()["mic_muted"])
        assertEquals(true, api.liveJoinRequests.last()["headphones_muted"])
        assertEquals(true, api.liveStateUpdates.last()["mic_muted"])
        assertEquals(true, api.liveStateUpdates.last()["headphones_muted"])
    }

    @Test
    fun `logging out resets persistent live audio choices`() {
        var logoutCount = 0
        val liveSession = FakeHomeLiveSession()
        compose.setHomePage(
            app = homeTestAppContext(onLogout = { logoutCount++ }),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
        )
        compose.settleShell()

        tapTag(homeTitleLiveRoomHeadphonesTag)
        assertTrue(liveSession.micMutes.last())
        assertTrue(liveSession.outputMutes.last())

        tapTooltip("退出登录")
        compose.onAllNodes(hasText("退出登录") and hasClickAction()).onLast().performClick()
        compose.settleShell()

        assertEquals(1, logoutCount)
        assertFalse(liveSession.micMutes.last())
        assertFalse(liveSession.outputMutes.last())
        assertTrue(liveSession.inputVolumes.last() > 0)
        assertTrue(liveSession.outputVolumes.last() > 0)
    }

    @Test
    @Config(qualifiers = "w1600dp-h620dp-mdpi")
    fun `title live room leaves paint safety for a long room name`() {
        val roomName = "特别长长长长长长长长长长长的房间名"
        compose.setHomePage(
            app = homeTestAppContext(HomeShellTestApi(initialAlphaRoomName = roomName)),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = FakeHomeLiveSession()),
        )
        compose.settleShell()

        tapTag(homeSidebarRoomTag("server-alpha"))
        openLiveChannelFromHeader()
        joinLive()

        val name = compose.onNode(inTag(homeTitleLiveRoomTag, hasText(roomName)), useUnmergedTree = true)
        val textWidth = name.textLayout().size.width / compose.density.density
        assertTrue(widthOf(homeTitleLiveRoomNameTag).value >= ceil(textWidth) + 2 - 0.01f)
    }

    @Test
    @Config(qualifiers = "w930dp-h620dp-mdpi")
    fun `title live room scrolls a constrained room name both ways`() {
        val roomName = "特别长长长长长长长长长长长的房间名"
        compose.setHomePage(
            app = homeTestAppContext(HomeShellTestApi(initialAlphaRoomName = roomName)),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = FakeHomeLiveSession()),
        )
        compose.settleShell()

        tapTag(homeSidebarRoomTag("server-alpha"))
        openLiveChannelFromHeader()
        joinLive()

        assertTrue(tagExists(homeTitleLiveRoomNameTag))
        val name = compose.onNode(inTag(homeTitleLiveRoomTag, hasText(roomName)), useUnmergedTree = true)
        name.assertExists()
        assertTrue(tagExists(homeTitleLiveRoomMarqueeTrackTag))
        val viewportLeft = tag(homeTitleLiveRoomNameTag).rect().left

        fun offset(): Float = (name.getUnclippedBoundsInRoot().left - viewportLeft).value

        compose.pumpShell(900)
        compose.pumpShell(400)
        val firstOffset = offset()
        compose.pumpShell(400)
        val secondOffset = offset()
        assertTrue("first $firstOffset", firstOffset < 0)
        assertTrue("second $secondOffset first $firstOffset", secondOffset < firstOffset)
        // Android's glyphs make the overflow (and so the travel time) shorter
        // than Dart's test font, so instead of fixed sample times the name is
        // followed until it reaches the trailing end and then turns back.
        val viewport = tag(homeTitleLiveRoomNameTag).rect()
        val trailingEnd = (viewport.right - viewport.left - (name.getUnclippedBoundsInRoot().let { it.right - it.left })).value
        var completedOffset = secondOffset
        var reversingOffset: Float? = null
        repeat(400) {
            if (reversingOffset != null) return@repeat
            compose.pumpShell(50)
            val current = offset()
            if (current < completedOffset) completedOffset = current else if (current > completedOffset + 1f) reversingOffset = current
        }
        assertEquals(trailingEnd, completedOffset, 1.01f)
        assertTrue(completedOffset < secondOffset)
        assertTrue(reversingOffset != null && reversingOffset!! > completedOffset)
    }

    @Test
    @Config(qualifiers = "w1180dp-h620dp-mdpi")
    fun `authenticated home shell title live room module waits for room before switching`() {
        var holdAlphaDetail = false
        val alphaDetailGate = CompletableDeferred<Unit>()
        val api =
            HomeShellTestApi(
                beforeRoomDetailResponse = { roomId ->
                    if (roomId == "server-alpha" && holdAlphaDetail) alphaDetailGate.await()
                },
            )
        compose.setHomePage(
            app = homeTestAppContext(api),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = FakeHomeLiveSession()),
        )
        compose.settleShell()

        openAlphaAndJoinLive()
        assertTrue(tagExists(homeTitleLiveRoomTag))

        tapText("Beta Room")
        assertFalse(tagExists("live-control:leave"))
        assertTrue(compose.textNodes("Beta Room") >= 1)

        val alphaDetailsBeforeOpen = api.requestedPaths.count { it == "/api/v1/rooms/server-alpha" }
        holdAlphaDetail = true
        tag(homeTitleLiveRoomTag).performClick()
        compose.pumpShell()

        assertFalse(tagExists("live-control:leave"))
        assertTrue(compose.textNodes("Beta Room") >= 1)

        alphaDetailGate.complete(Unit)
        compose.settleShell()

        assertTrue(tagExists("live-control:leave"))
        assertEquals(1, compose.textNodes("Morgan"))
        assertTrue(api.requestedPaths.count { it == "/api/v1/rooms/server-alpha" } > alphaDetailsBeforeOpen)
    }

    @Test
    @Config(qualifiers = "w1200dp-h640dp-mdpi")
    fun `authenticated Android narrow room list places live room module in footer`() {
        val liveSession = FakeHomeLiveSession()
        var pageWidth by mutableStateOf(1180.dp)
        compose.setHomePage(
            app = homeTestAppContext(),
            audioDeviceStore = FakeHomeAudioDeviceStore(),
            liveSessionController = FakeHomeLiveSessionController(session = liveSession),
            size = { DpSize(pageWidth, 620.dp) },
        )
        compose.settleShell()

        openAlphaAndJoinLive()
        assertTrue(tagExists(homeTitleLiveRoomTag))

        pageWidth = 360.dp
        compose.settleShell()
        pressBack()
        pressBack()

        val notifications = tag(homeSidebarNotificationsButtonTag).rect()
        val settings = tag(homeSidebarSettingsButtonTag).rect()
        val dock = tag(homeTitleLiveRoomTag).rect()
        assertTrue(dock.left > notifications.right)
        assertTrue(dock.right < settings.left)
        assertDpEquals(8.dp, dock.left - notifications.right)
        assertDpEquals(8.dp, settings.left - dock.right)
        assertTrue(dockHasText("Alpha Room"))
        assertTrue(dockHasAvatar("Alpha Room"))

        tapTag(homeTitleLiveRoomMicTag)
        assertEquals(0.0, liveSession.inputVolumes.last(), 0.0)
    }

    @Test
    fun `authenticated home shell search category filters sidebar rooms`() {
        val api = HomeShellTestApi()
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        enterSearch("Beta")

        assertTrue(api.requestedPaths.contains("/api/v1/search"))
        assertTrue(searchResultsOpen())
        for (text in listOf("我的房间 1", "公开房间 1", "聊天记录 1", "聊天文件 1")) {
            assertTrue(text, compose.textNodes(text) >= 1)
        }
        assertTrue(tagExists(publicRoomActionTag("server-public")))
        assertTrue(tagExists(myRoomActionTag("server-beta")))
        assertEquals(1, compose.onAllNodes(hasText("进入房间") and hasAnyAncestor(hasTestTag(homeTitleSearchResultsTag)), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasText("加入房间") and hasAnyAncestor(hasTestTag(homeTitleSearchResultsTag)), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(compose.textNodes("5 名成员") >= 1)
        assertTrue(compose.textNodes("2 名成员") >= 1)
        assertEquals(0, compose.textNodes("server-public - 2 名成员"))

        // Dart hovered the room avatar; Android opens its card with a tap.
        val myRoomAvatar = compose.onAllNodes(inTag(homeTitleSearchResultsTag, hasText(avatarInitials("Beta Room"))), useUnmergedTree = true).onFirst()
        myRoomAvatar.performClick()
        compose.settleShell()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-beta"))
        val creatorAvatar = compose.onNode(inTag(hoverCardPanelTag, hasText(avatarInitials("Morgan"))), useUnmergedTree = true)
        creatorAvatar.performClick()
        compose.settleShell()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-beta/members/user-2/profile"))
        assertEquals(1, compose.textNodes("@morgan"))
        assertEquals(1, compose.textNodes("Creator profile"))

        // The shared room's 20 dp avatar on the user card.
        val sharedRoomAvatar =
            compose.onAllNodes(inTag(hoverCardPanelTag, hasText(avatarInitials("Alpha Room"))), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, sharedRoomAvatar.size)
        compose.onAllNodes(inTag(hoverCardPanelTag, hasText(avatarInitials("Alpha Room"))), useUnmergedTree = true).onFirst().performClick()
        compose.settleShell()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha"))
        assertEquals(1, compose.textNodes("RID: server-alpha"))

        val alphaRidRect = compose.onNodeWithText("RID: server-alpha").rect()
        val enterButtons = compose.onAllNodes(hasText("进入房间") and hasClickAction()).fetchSemanticsNodes()
        val alphaEnter =
            enterButtons.single { node ->
                val bounds = node.boundsInRoot
                val density = compose.density.density
                bounds.top / density > alphaRidRect.bottom.value &&
                    kotlin.math.abs(bounds.center.x / density - alphaRidRect.centerX.value) < 160
            }
        compose.onNode(SemanticsMatcher("alpha enter") { it.id == alphaEnter.id }).performClick()
        compose.settleShell()

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/messages"))
        assertTrue(searchResultsOpen())
        assertTrue(compose.textNodes("创建者") >= 1)

        // Dart tapped outside at (740, 100) to close the cards, then
        // refocused the search field.
        tapAt(740.dp, 100.dp)
        compose.settleShell()
        compose.textFieldIn(homeTitleSearchTag).performClick()
        compose.settleShell()

        val pathsBeforeSearchRowTap = api.requestedPaths.toList()
        compose.onAllNodes(inTag(homeTitleSearchResultsTag, hasText("Beta Public")), useUnmergedTree = true).onFirst().performClick()
        compose.settleShell()
        assertEquals(pathsBeforeSearchRowTap, api.requestedPaths)

        tapTag(publicRoomActionTag("server-public"))

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-public/join"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-public"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-public/messages"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-public/live"))
        assertEquals(1, compose.textNodes("History visible immediately after join"))

        // Selecting the 我的房间 category filters the sidebar to matching rooms.
        tapTag(searchCategoryTag(GlobalSearchCategory.myRooms))
        assertTrue(compose.textNodes("Beta Room") >= 1)
        assertEquals(0, compose.textNodes("Alpha Room"))

        // The active category persists across query edits.
        enterSearch("Beta R")
        assertTrue(compose.textNodes("Beta Room") >= 1)
        assertEquals(0, compose.textNodes("Alpha Room"))

        // Re-tapping the active category clears it and restores the full sidebar.
        tapTag(searchCategoryTag(GlobalSearchCategory.myRooms))
        assertEquals(1, compose.textNodes("Alpha Room"))
        assertTrue(compose.textNodes("Beta Room") >= 1)

        // Closing the dropdown hides the results panel; reopening restores it.
        tapAt(740.dp, 100.dp)
        compose.settleShell()
        assertFalse(searchResultsOpen())

        compose.textFieldIn(homeTitleSearchTag).performClick()
        compose.settleShell()
        assertTrue(searchResultsOpen())
        assertEquals(1, compose.textNodes("Alpha Room"))
        assertTrue(compose.textNodes("Beta Room") >= 1)

        enterSearch("1")
        assertFalse(compose.exists(inTag(homeTitleSearchResultsTag, hasTestTag(myRoomActionTag("server-beta")))))
    }


    @Test
    fun `superuser global search replaces public rooms with user settings`() {
        val api = HomeShellTestApi()
        compose.setHomePage(app = homeTestAppContext(api, currentUserIsSuperuser = true))
        compose.settleShell()

        enterSearch("ri")

        assertTrue(
            api.requestedUris.any { uri ->
                uri.path == "/api/v1/search" && uri.queryParameters["categories"] == "my_rooms,messages,files"
            },
        )
        assertTrue(
            api.requestedUris.any { uri ->
                uri.path == "/api/v1/users/search" && uri.queryParameters["include_suspended"] == "true"
            },
        )
        assertTrue(compose.textNodes("用户设置 5") >= 1)
        assertEquals(0, compose.onAllNodes(hasText("公开房间", substring = true)).fetchSemanticsNodes().size)
        assertEquals(5, compose.onAllNodes(hasText("进入设置")).fetchSemanticsNodes().size)
        assertTrue(tagExists(openUserSettingsTag("user-3")))

        tapTag(openUserSettingsTag("user-3"))

        assertEquals(1, compose.textNodes("Riley 的用户设置"))
        val managedPage = "superuser-user-settings-user-3"
        assertTrue(tagExists(managedPage))
        val navigation = inTag(managedPage, hasTestTag(settingsSideNavigationTag))
        assertTrue(compose.exists(navigation))
        val securityItem = compose.onNode(hasText("隐私和安全") and hasAnyAncestor(navigation))
        securityItem.performClick()
        compose.settleShell()
        assertTrue(isSelected(securityItem))
        assertEquals(2, compose.onAllNodes(inTag(managedPage, hasText("隐私和安全"))).fetchSemanticsNodes().size)

        // Scroll the section content until the password form shows.
        val content = compose.onAllNodes(
            inTag(managedPage, SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)) and
                !hasAnyAncestor(hasTestTag(settingsSideNavigationTag)) and !hasTestTag(settingsSideNavigationTag),
        ).onFirst()
        repeat(20) {
            if (compose.textNodes("新密码") > 0) return@repeat
            content.performTouchInput { swipeUp(startY = centerY + 100f, endY = centerY - 100f) }
            compose.settleShell(3)
        }
        compose.settleShell()
        assertEquals(0, compose.textNodes("当前密码"))
        assertEquals(1, compose.textNodes("新密码"))
        assertEquals(1, compose.textNodes("确认新密码"))
        val forgotPassword = compose.onAllNodes(hasText("忘记密码")).fetchSemanticsNodes()
        assertEquals(1, forgotPassword.size)
        assertFalse(compose.exists(hasText("忘记密码") and hasClickAction() and SemanticsMatcher("enabled") { it.config.getOrNull(SemanticsProperties.Disabled) == null }))
    }

    @Test
    @Config(qualifiers = "w900dp-h640dp-mdpi")
    fun `narrow search result actions reduce horizontal width`() {
        var pageWidth by mutableStateOf(900.dp)
        var superuser by mutableStateOf(false)
        val regular = homeTestAppContext()
        val superApp = homeTestAppContext(currentUserIsSuperuser = true)
        compose.setHomeShellContent(size = { DpSize(pageWidth, 620.dp) }) {
            androidx.compose.runtime.key(superuser, pageWidth) {
                HomePage(
                    app = if (superuser) superApp else regular,
                    realtime = NoopRealtimeService(),
                    audioDeviceStore = FakeHomeAudioDeviceStore(),
                    livePresenceSoundPlayer = RecordingLivePresenceSoundPlayer(),
                    livePresenceSpeechPlayer = RecordingLivePresenceSpeechPlayer(),
                    messageNotificationSoundPlayer = RecordingMessageNotificationSoundPlayer(),
                    androidSystemService = FakeHomeAndroidSystemService(),
                )
            }
        }

        fun roomActionWidths(width: Dp): List<Dp> {
            pageWidth = width
            compose.settleShell()
            enterSearch("Beta")
            return listOf(widthOf(myRoomActionTag("server-beta")), widthOf(publicRoomActionTag("server-public")))
        }

        fun userSettingsActionWidth(width: Dp): Dp {
            pageWidth = width
            compose.settleShell()
            enterSearch("ri")
            return widthOf(openUserSettingsTag("user-3"))
        }

        val wideRoomActions = roomActionWidths(900.dp)
        val narrowRoomActions = roomActionWidths(360.dp)
        superuser = true
        val wideUserSettingsAction = userSettingsActionWidth(900.dp)
        val narrowUserSettingsAction = userSettingsActionWidth(360.dp)
        // Compact rows give the room actions their fixed 72 dp. With Android's
        // CJK glyphs the wide buttons' natural width ("进入房间" plus 10 dp
        // padding) is also 72 dp, where Dart's test font made it wider, so
        // they can only match here; the user settings action still shrinks.
        assertDpEquals(72.dp, narrowRoomActions[0])
        assertDpEquals(72.dp, narrowRoomActions[1])
        assertTrue(narrowRoomActions[0] <= wideRoomActions[0])
        assertTrue(narrowRoomActions[1] <= wideRoomActions[1])
        assertDpEquals(90.dp, narrowUserSettingsAction)
        assertTrue(narrowUserSettingsAction < wideUserSettingsAction)
    }

    @Test
    fun `authenticated home shell search scroll loads next cursor page`() {
        val api = HomeShellTestApi()
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        enterSearch("Page")

        assertTrue(compose.textNodes("聊天记录 9") >= 1)
        assertEquals(0, compose.textNodes("聊天记录 8"))
        assertFalse(compose.exists(hasText("Page result 9", substring = true)))

        tapTag(searchCategoryTag(GlobalSearchCategory.messages))

        val resultsList = tag(homeTitleSearchResultsListTag)
        resultsList.assertExists()
        // Dart dragged 360 px from the centre; with Android's row metrics that
        // stops short of the 96 dp load-more threshold, so this drag goes further.
        resultsList.performTouchInput { swipe(center, Offset(centerX, centerY - 400f * density)) }
        compose.settleShell()

        fun cursorRequests() =
            api.requestedUris.filter { uri ->
                uri.path == "/api/v1/search" && uri.queryParameters["messages_cursor"] == "message-cursor-8"
            }
        assertEquals(1, cursorRequests().size)
        val cursorRequest = cursorRequests().single()
        assertEquals("messages", cursorRequest.queryParameters["categories"])
        assertFalse(cursorRequest.queryParameters.containsKey("my_rooms_cursor"))
        assertFalse(cursorRequest.queryParameters.containsKey("public_rooms_cursor"))
        assertFalse(cursorRequest.queryParameters.containsKey("files_cursor"))
        assertTrue(compose.textNodes("聊天记录 9") >= 1)

        resultsList.performTouchInput { swipe(center, Offset(centerX, centerY - 120f * density)) }
        compose.settleShell()
        assertEquals(1, cursorRequests().size)
        assertTrue(compose.exists(hasText("Page result 9", substring = true)))
    }

    @Test
    fun `authenticated home shell creates room from footer template`() {
        val api = HomeShellTestApi()
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        tapTooltip("创建房间")
        assertTrue(buttonSelected(homeSidebarCreateRoomButtonTag))
        assertEquals(1, compose.textNodes("创建房间"))

        tapTooltip("创建房间")
        assertEquals(0, compose.textNodes("创建房间"))
        assertFalse(buttonSelected(homeSidebarCreateRoomButtonTag))

        tapTooltip("创建房间")
        assertEquals(1, compose.textNodes("创建房间"))
        assertEquals(1, compose.textNodes("房间信息"))
        assertFalse(tagExists(roomSettingsRidTag))

        compose.textFieldIn(roomSettingsNameInputTag).performTextReplacement("Project Nest")
        compose.textFieldIn(roomSettingsDescriptionInputTag).performTextReplacement("A focused room")
        compose.settleShell()
        // Dart's `ensureVisible`: the form is a lazy list on Android.
        scrollFormTo(hasText("确定") and hasClickAction())
        assertEquals(1, compose.onAllNodes(hasText("确定") and hasClickAction()).fetchSemanticsNodes().size)
        assertEquals(0, compose.textNodes("保存房间设置"))
        assertEquals(0, compose.textNodes("离开房间"))
        assertEquals(0, compose.textNodes("删除房间"))
        compose.onNode(hasText("确定") and hasClickAction()).performClick()
        compose.settleShell()

        assertEquals(1, api.roomCreations.size)
        val body = api.roomCreations.single()
        assertEquals("Project Nest", body["name"])
        assertEquals("A focused room", body["description"])
        assertEquals("public", body["visibility"])
        assertEquals("approval_required", body["join_policy"])
        assertFalse(body.containsKey("ai_voice_announcements_enabled"))
        assertEquals("blue-3", body["default_avatar_key"])
        assertTrue(compose.textNodes("Project Nest") >= 1)
        assertEquals(0, compose.textNodes("创建房间"))
    }

    @Test
    fun `authenticated home shell opens empty create room form from room settings`() {
        compose.setHomePage(app = homeTestAppContext())
        compose.settleShell()

        tapText("Alpha Room")
        tapTooltip("房间设置")

        assertEquals("Alpha Room", compose.textFieldIn(roomSettingsNameInputTag).editableText())

        tapTooltip("创建房间")

        assertEquals(1, compose.textNodes("创建房间"))
        assertEquals("", compose.textFieldIn(roomSettingsNameInputTag).editableText())
        assertEquals("", compose.textFieldIn(roomSettingsDescriptionInputTag).editableText())
        assertFalse(tagExists(roomSettingsRidTag))
    }

    @Test
    @Config(qualifiers = "w2000dp-h1000dp-mdpi")
    fun `authenticated home shell opens notifications and reviews invite`() {
        val api = HomeShellTestApi()
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        tapTooltip("通知")
        assertTrue(buttonSelected(homeSidebarNotificationsButtonTag))
        assertEquals(1, compose.textNodes("通知"))

        tapTooltip("通知")
        assertEquals(0, compose.textNodes("通知"))
        assertFalse(buttonSelected(homeSidebarNotificationsButtonTag))

        tapTooltip("通知")
        assertEquals(1, compose.textNodes("通知"))
        for (text in listOf("全部", "邀请", "申请", "房间")) assertEquals(text, 1, compose.textNodes(text))
        assertTrue(compose.textNodes("搜索通知") >= 1)
        assertTrue(compose.textNodes("邀请您加入") >= 1)
        assertEquals(1, compose.textNodes("已失效"))
        assertTrue(compose.textNodes("您已申请加入") >= 1)
        assertEquals(1, compose.textNodes("批准了您的申请"))
        assertEquals(1, compose.onAllNodes(hasText("Morgan Member", substring = true)).fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodes(hasText("成员", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(tagExists("notification-inviter-avatar-invite-alpha"))
        assertTrue(tagExists("notification-room-avatar-invite-alpha"))
        assertDpEquals(34.dp, widthOf("notification-inviter-avatar-invite-alpha"))
        for (key in listOf("notification-inviter-name-invite-alpha", "notification-application-reviewer-name-application-approved")) {
            assertEquals(key, UiTypography.label.fontSize.value, tag(key).textLayout().layoutInput.style.fontSize.value, 0.01f)
        }
        assertFalse(tagExists("notification-mobile-body-invite-alpha"))
        val rects =
            listOf(
                tag("notification-time-invite-alpha").rect(),
                tag("notification-inviter-avatar-invite-alpha").rect(),
                tag("notification-inviter-name-invite-alpha").rect(),
                tag("notification-inviter-role-invite-alpha").rect(),
                tag("notification-invite-action-invite-alpha").rect(),
                tag("notification-room-avatar-invite-alpha").rect(),
                tag("notification-room-name-invite-alpha").rect(),
                compose.onTooltip("接受邀请").rect(),
            )
        for (index in 1 until rects.size) assertTrue("rect $index", rects[index].left > rects[index - 1].left)
        assertTrue(api.requestedUris.any { it.path == "/api/v1/room-invites" && it.queryParameters["status"] == "all" })
        assertTrue(api.requestedUris.any { it.path == "/api/v1/room-applications" && it.queryParameters["status"] == "all" })

        tapTooltip("撤回申请")
        assertTrue(api.requestedUris.any { it.path == "/api/v1/room-applications/application-alpha" })

        compose.onTooltip("接受邀请").performClick()
        compose.pumpShellUntil { compose.textNodes("申请加入") > 0 }
        compose.settleShell()
        assertEquals(1, compose.textNodes("申请加入"))
        assertEquals(1, compose.textNodes("您需要等待"))
        compose.onAllTooltips("关闭").fetchSemanticsNodes().let { nodes ->
            compose.onNode(SemanticsMatcher("close") { it.id == nodes.last().id }).performClick()
        }
        compose.settleShell()
        assertEquals(0, compose.textNodes("申请加入"))

        compose.onTooltip("接受邀请").performClick()
        compose.pumpShellUntil { compose.textNodes("申请加入") > 0 }
        compose.settleShell()
        compose.onNode(hasText("取消") and hasClickAction()).performClick()
        compose.settleShell()
        assertEquals(0, compose.textNodes("申请加入"))

        compose.onTooltip("接受邀请").performClick()
        compose.pumpShellUntil { compose.textNodes("申请加入") > 0 }
        compose.settleShell()
        compose.textFieldIn(joinApplicationReasonInputTag).performTextReplacement("I was invited")
        compose.onNode(hasText("发送申请") and hasClickAction()).performClick()
        compose.settleShell()

        assertTrue(api.requestedUris.any { it.path == "/api/v1/room-invites/invite-alpha" })
    }

    @Test
    fun `room notification row dot clears on notification refresh`() {
        val api = HomeShellTestApi(includeUnreadRoomNotification = true, includeFreshRoomNotificationOnRefresh = true)
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        tapTag(homeSidebarNotificationsButtonTag)

        val marker = "notification-room-event-new-room-event-alpha"
        val freshMarker = "notification-room-event-new-room-event-fresh"
        assertTrue(tagExists(marker))
        assertFalse(tagExists(freshMarker))
        assertTrue(api.requestedPaths.contains("/api/v1/room-notifications/read"))

        tapTag(homeNotificationsRefreshButtonTag)

        assertFalse(tagExists(marker))
        assertTrue(tagExists(freshMarker))
        assertTrue(api.requestedPaths.count { it == "/api/v1/room-notifications" } >= 2)
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun `android notifications keep every inline avatar in desktop order`() {
        compose.setHomePage(app = homeTestAppContext(HomeShellTestApi(includeUnreadRoomNotification = true)))
        compose.settleShell()

        tapTooltip("通知")

        val bodyKey = "notification-mobile-body-room-event-room-event-alpha"
        val timeKey = "notification-room-event-time-room-event-alpha"
        assertTrue(tagExists(bodyKey))
        assertTrue(widthOf(timeKey) < 82.dp)
        val timeTexts = compose.onAllNodes(inTag(timeKey, SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(_notificationFixtureTimeLines(), timeTexts.map { it.config[SemanticsProperties.Text].joinToString("") { text -> text.text } })
        val roomEventCard = "notification-row-surface-room-event:room-event-alpha"
        assertTrue(tag(roomEventCard).rect().left < 22.dp)

        fun avatarTagsIn(card: String): List<String> =
            compose.onAllNodes(
                inTag(card, SemanticsMatcher("avatar") { node -> node.config.getOrNull(SemanticsProperties.TestTag)?.contains("avatar") == true }),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

        assertEquals(
            listOf("notification-inviter-avatar-invite-alpha", "notification-room-avatar-invite-alpha"),
            avatarTagsIn("notification-row-surface-invite:invite-alpha"),
        )
        assertEquals(
            listOf("notification-room-avatar-application-application-alpha"),
            avatarTagsIn("notification-row-surface-application-requested:application-alpha"),
        )
        assertEquals(
            listOf(
                "notification-application-reviewer-avatar-application-approved",
                "notification-room-avatar-application-reviewed-application-approved",
            ),
            avatarTagsIn("notification-row-surface-application-reviewed:application-approved"),
        )
        assertEquals(
            listOf("notification-room-avatar-room-event-room-event-alpha", "notification-room-event-actor-avatar-room-event-alpha"),
            avatarTagsIn(roomEventCard),
        )
        for (key in listOf(
            "notification-inviter-name-invite-alpha",
            "notification-application-reviewer-name-application-approved",
            "notification-room-event-actor-name-room-event-alpha",
        )) {
            assertEquals(key, UiTypography.label.fontSize.value, tag(key).textLayout().layoutInput.style.fontSize.value, 0.01f)
        }
        val firstMessageAvatar = tag("notification-room-avatar-room-event-room-event-alpha").rect()
        val body = tag(bodyKey).rect()
        assertTrue(firstMessageAvatar.centerX in body.left..body.right && firstMessageAvatar.centerY in body.top..body.bottom)
        val avatars =
            compose.onAllNodes(
                SemanticsMatcher("row avatar") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: return@SemanticsMatcher false
                    tag.startsWith("notification-") && tag.contains("-avatar-")
                },
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
        assertTrue(avatars.isNotEmpty())
        for (avatar in avatars) assertEquals(18f, avatar.boundsInRoot.width / compose.density.density, 0.01f)
        val cardCenterY = tag(roomEventCard).rect().centerY
        assertDpEquals(cardCenterY, tag(timeKey).rect().centerY, tolerance = 1f)
    }

    @Test
    @Config(qualifiers = "w2000dp-h900dp-mdpi")
    fun `notifications switch to compact rows before the desktop shell breakpoint`() {
        var pageWidth by mutableStateOf(2000.dp)
        compose.setHomePage(
            app = homeTestAppContext(HomeShellTestApi(includeUnreadRoomNotification = true)),
            size = { DpSize(pageWidth, 900.dp) },
        )
        compose.settleShell()

        tapTooltip("通知")

        val compactBodyKey = "notification-mobile-body-room-event-room-event-alpha"
        assertFalse(tagExists(compactBodyKey))

        // Dart's test font made the room event wrap at 1200 px; Android's
        // glyphs are narrower, so the synchronized checks run at the first
        // width where any row wraps (which must come before the 720 dp shell
        // breakpoint).
        var compactWidth: Int? = null
        for (width in listOf(1800, 1600, 1400, 1200, 1000, 900, 760)) {
            pageWidth = width.dp
            compose.settleShell()
            if (compactWidth == null && tagExists(compactBodyKey)) compactWidth = width
            if (width == compactWidth) {
                for (bodyKey in listOf(
                    "notification-mobile-body-invite-alpha",
                    "notification-mobile-body-application-application-alpha",
                    "notification-mobile-body-reviewed-application-approved",
                    "notification-mobile-body-room-event-room-event-alpha",
                )) {
                    assertTrue(bodyKey, tagExists(bodyKey))
                }
                for (timeKey in listOf(
                    "notification-time-invite-alpha",
                    "notification-application-time-application-alpha",
                    "notification-application-review-time-application-approved",
                    "notification-room-event-time-room-event-alpha",
                )) {
                    val lines = compose.onAllNodes(inTag(timeKey, SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)), useUnmergedTree = true).fetchSemanticsNodes()
                    assertEquals(timeKey, 2, lines.size)
                }
                val avatars =
                    compose.onAllNodes(
                        SemanticsMatcher("row avatar") { node ->
                            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: return@SemanticsMatcher false
                            tag.startsWith("notification-") && tag.contains("-avatar-")
                        },
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes()
                assertTrue(avatars.isNotEmpty())
                for (avatar in avatars) assertEquals(18f, avatar.boundsInRoot.width / compose.density.density, 0.01f)
            }
        }
        assertTrue(compactWidth != null)
        assertTrue(tagExists(compactBodyKey))

        pageWidth = 2000.dp
        compose.settleShell()
        assertFalse(tagExists(compactBodyKey))
    }

    @Test
    fun `room notification row dot clears after leaving notifications`() {
        compose.setHomePage(app = homeTestAppContext(HomeShellTestApi(includeUnreadRoomNotification = true)))
        compose.settleShell()

        tapTag(homeSidebarNotificationsButtonTag)
        val marker = "notification-room-event-new-room-event-alpha"
        assertTrue(tagExists(marker))

        tapText("Beta Room")
        tapTag(homeSidebarNotificationsButtonTag)

        assertFalse(tagExists(marker))
    }

    @Test
    fun `room list shows pending join request dot without unread count`() {
        val badge = homeSidebarRoomPendingJoinRequestsTag("server-alpha")
        var app by mutableStateOf(homeTestAppContext(HomeShellTestApi(alphaRoomUnreadCount = 0, alphaRoomHasPendingJoinRequests = true)))
        compose.setHomeShellContent {
            HomePage(
                app = app,
                realtime = NoopRealtimeService(),
                audioDeviceStore = FakeHomeAudioDeviceStore(),
                livePresenceSoundPlayer = RecordingLivePresenceSoundPlayer(),
                livePresenceSpeechPlayer = RecordingLivePresenceSpeechPlayer(),
                messageNotificationSoundPlayer = RecordingMessageNotificationSoundPlayer(),
                androidSystemService = FakeHomeAndroidSystemService(),
            )
        }
        compose.settleShell()
        assertTrue(tagExists(badge))

        app = homeTestAppContext(HomeShellTestApi(alphaRoomUnreadCount = 2, alphaRoomHasPendingJoinRequests = true))
        compose.settleShell()
        assertFalse(tagExists(badge))
    }

    @Test
    fun `authenticated home shell sends messages through real API path`() {
        val api = HomeShellTestApi()
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        tapText("Alpha Room")
        compose.textFieldIn(chatComposerTag).performTextReplacement("Fresh message")
        compose.settleShell()
        tapTooltip("发送")

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/messages"))
        assertEquals(1, compose.textNodes("Fresh message"))
    }

    @Test
    fun `authenticated home shell adds a message quote from its menu`() {
        compose.setHomePage(app = homeTestAppContext())
        compose.settleShell()

        tapText("Alpha Room")
        val messageBody = compose.onNode(hasText("Hello from Morgan"))
        messageBody.assertExists()
        // Dart's secondary click: Android opens the message menu with a hold.
        messageBody.performTouchInput { longClick(center) }
        compose.settleShell()

        compose.onNodeWithText("引用").performClick()
        compose.settleShell()

        assertTrue(tagExists(composerQuoteCloseTag("msg-1")))
    }

    @Test
    fun `shared song card lists personal and current room playlists`() {
        val api = HomeShellTestApi(includeSharedMusicTrackMessage = true)
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        tapText("Alpha Room")
        compose.onNode(hasText("分享歌曲完整名称")).performClick()
        compose.settleShell()
        tapTag(musicTrackCardAddToPlaylistTag)

        assertEquals(1, compose.textNodes("Kai 的个人歌单"))
        assertEquals(1, compose.textNodes("Alpha 房间歌单"))
        assertTrue(api.requestedPaths.contains("/api/v1/me/music-box/playlists"))
        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/music-box/playlists"))

        tag("music-track-playlist-target:room:server-alpha:mbp-room-alpha").performClick()
        compose.pumpShell()
        tapTag(musicTrackPlaylistTargetConfirmTag)

        assertTrue(api.requestedPaths.contains("/api/v1/rooms/server-alpha/music-box/playlists/mbp-room-alpha/items"))
    }

    @Test
    fun `copied music message pastes as a removable component`() {
        val api = HomeShellTestApi(includeSharedMusicTrackMessage = true)
        compose.setHomePage(app = homeTestAppContext(api))
        compose.settleShell()

        tapText("Alpha Room")
        compose.onNode(hasText("分享歌曲完整名称")).performTouchInput { longClick(center) }
        compose.settleShell()
        compose.onNodeWithText("复制").performClick()
        compose.settleShell()
        assertTrue(clipboardText().orEmpty().contains("[歌曲] 分享歌曲完整名称"))

        val composerField = compose.textFieldIn(chatComposerTag)

        fun pasteShortcut() {
            composerField.performClick()
            compose.pumpShell()
            composerField.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.V) } }
            compose.settleShell()
        }

        pasteShortcut()
        assertTrue(tagExists(composerMessageComponentTag))
        assertEquals("", composerField.editableText())

        tapTag(removeComposerMessageComponentTag)
        assertFalse(tagExists(composerMessageComponentTag))

        pasteShortcut()
        composerField.performTextReplacement("紧接着发送的说明")
        compose.settleShell()
        tapTooltip("发送")

        assertEquals(2, api.sentMessageRequests.size)
        assertEquals("music_track", api.sentMessageRequests.first()["type"])
        val componentAttachments = api.sentMessageRequests.first()["attachments"] as List<*>
        assertEquals(1, componentAttachments.size)
        assertEquals("msg-music-track", (componentAttachments.single() as Map<*, *>)["source_message_id"])
        assertEquals("text", api.sentMessageRequests.last()["type"] ?: "text")
        assertEquals("紧接着发送的说明", api.sentMessageRequests.last()["body"])
        assertFalse(tagExists(composerMessageComponentTag))
        assertEquals("", composerField.editableText())
    }

    private fun _notificationFixtureTimeLines(): List<String> {
        val local = Instant.parse("2026-06-07T08:00:00Z").atZone(ZoneId.systemDefault())
        fun two(value: Int) = value.toString().padStart(2, '0')
        return listOf("${local.year}/${two(local.monthValue)}/${two(local.dayOfMonth)}", "${two(local.hour)}:${two(local.minute)}")
    }
}

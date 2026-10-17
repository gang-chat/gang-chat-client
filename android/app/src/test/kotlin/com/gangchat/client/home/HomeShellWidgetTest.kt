package com.gangchat.client.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.ReleaseAsset
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Uri
import com.gangchat.client.plugins.SharedPreferences
import com.gangchat.client.shell.ShellTestLoop
import com.gangchat.client.shell.assertDpEquals
import com.gangchat.client.shell.centerX
import com.gangchat.client.shell.rect
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.onAllTooltips
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.settingsSideNavigationTag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home shell cases of test/widget/shell/gang_app_shell_test_parts/
 * home_widget_tests.dart, hosting [HomePage] like the Dart suite.
 *
 * The Dart suite ran on an 800 x 600 desktop surface, mostly with the
 * Windows theme; these run on an 800 x 600 dp Android tablet window, so the
 * title bar carries no window buttons and the live room module sits on the
 * right of the search field (Dart's non-macOS placement).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class HomeShellWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    private val loop = ShellTestLoop()

    @Before
    fun setUp() {
        SharedPreferences.setMockInitialValues(emptyMap())
        loop.start()
        installTestImageLoader()
        compose.mainClock.autoAdvance = false
    }

    @After
    fun tearDown() = loop.finish()

    private fun tag(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun tagExists(tag: String): Boolean = compose.exists(hasTestTag(tag))

    private fun isSelected(node: SemanticsNodeInteraction): Boolean =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    private fun tapText(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.settleShell()
    }

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
        val displayNameRect = compose.onNode(hasText("Kai") and hasAnyAncestor(hasTestTag(homeSidebarUserSummaryTag)), useUnmergedTree = true).rect()
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
        assertFalse(tagExists("chat-jump-to-first-new"))
        assertEquals(0, compose.textNodes("3"))
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

        compose.onTooltip("返回").performClick()
        compose.settleShell()

        assertEquals(0, compose.textNodes("发现新版本"))
        assertEquals(1, compose.textNodes("设置"))
        val aboutTile = compose.onNode(hasText("关于Gang Chat") and hasAnyAncestor(hasTestTag(settingsSideNavigationTag)))
        aboutTile.assertExists()
        assertTrue(isSelected(aboutTile))
        assertEquals(2, compose.textNodes("关于Gang Chat"))
        assertEquals(1, compose.textNodes("版本信息"))
    }
}

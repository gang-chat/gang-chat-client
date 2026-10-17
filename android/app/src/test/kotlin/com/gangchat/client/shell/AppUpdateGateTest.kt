package com.gangchat.client.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AutoUpdatePromptStore
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.ReleaseAsset
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Uri
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.setUiContent
import com.gangchat.client.ui.textLayout
import com.gangchat.client.widgets.Text
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `test/widget/shell/app_update_gate_test.dart`. Not ported: the two macOS
 * Sparkle cases (the Sparkle branch is desktop-only).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-mdpi", shadows = [ShadowNoOpMagnifier::class])
class AppUpdateGateTest {
    @get:Rule
    val compose = createComposeRule()

    private val loop = ShellTestLoop()

    @Before
    fun setUp() = loop.start()

    @After
    fun tearDown() = loop.finish()

    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun button(label: String) = compose.onNode(hasText(label) and hasClickAction())

    @Test
    fun `update gate reports update and keeps home visible`() {
        val update =
            AvailableAppUpdate(
                currentVersion = "0.5.0",
                latestVersion = "0.5.1",
                asset =
                    ReleaseAsset(
                        key = "releases/GangChat_v0.5.1.exe",
                        version = "0.5.1",
                        platform = AppUpdatePlatform.windows,
                        releasedAt = DateTime.utc(2026, 7, 8, 1, 2),
                    ),
                downloadUrl = Uri.parse("https://os.example.test/gang-chat/releases/GangChat_v0.5.1.exe"),
            )
        val reportedUpdates = mutableListOf<AvailableAppUpdate>()
        val updateService = FakeReleaseUpdateService(update)

        compose.mainClock.autoAdvance = false
        compose.setUiContent { _ ->
            AppUpdateGate(
                releaseBucketUrl = "https://os.example.test/gang-chat",
                currentVersion = "0.5.0",
                autoUpdatePromptStore = FakeAutoUpdatePromptStore(true),
                updateService = updateService,
                platformOverride = AppUpdatePlatform.windows,
                onUpdateAvailable = { reportedUpdates.add(it) },
                child = { Text("Home is still available") },
            )
        }
        compose.pumpAndSettle()

        compose.onNodeWithText("Home is still available").assertExists()
        assertFalse(exists("发现新版本"))
        assertEquals(1, reportedUpdates.size)
        assertSame(update, reportedUpdates.single())
        assertEquals(AppUpdatePlatform.windows, updateService.requestedPlatform)
    }

    @Test
    fun `update gate reports Android APK updates in the desktop form`() {
        val update =
            AvailableAppUpdate(
                currentVersion = "1.0.0",
                latestVersion = "1.0.1",
                asset =
                    ReleaseAsset(
                        key = "releases/GangChat_v1.0.1.apk",
                        version = "1.0.1",
                        platform = AppUpdatePlatform.android,
                    ),
                downloadUrl = Uri.parse("https://os.example.test/gang-chat/releases/GangChat_v1.0.1.apk"),
            )
        val updateService = FakeReleaseUpdateService(update)
        val reportedUpdates = mutableListOf<AvailableAppUpdate>()

        compose.mainClock.autoAdvance = false
        compose.setUiContent { _ ->
            AppUpdateGate(
                releaseBucketUrl = "https://os.example.test/gang-chat",
                currentVersion = "1.0.0",
                autoUpdatePromptStore = FakeAutoUpdatePromptStore(true),
                updateService = updateService,
                platformOverride = AppUpdatePlatform.android,
                onUpdateAvailable = { reportedUpdates.add(it) },
                child = { Text("Android home stays available") },
            )
        }
        compose.pumpAndSettle()

        compose.onNodeWithText("Android home stays available").assertExists()
        assertEquals(1, reportedUpdates.size)
        assertSame(update, reportedUpdates.single())
        assertEquals(AppUpdatePlatform.android, updateService.requestedPlatform)
    }

    @Test
    fun `update page shows release details and settings actions`() {
        val update =
            AvailableAppUpdate(
                currentVersion = "0.5.0",
                latestVersion = "0.5.1",
                asset =
                    ReleaseAsset(
                        key = "releases/GangChat_v0.5.1.exe",
                        version = "0.5.1",
                        platform = AppUpdatePlatform.windows,
                        releasedAt = DateTime.utc(2026, 7, 8, 1, 2),
                    ),
                downloadUrl = Uri.parse("https://os.example.test/gang-chat/releases/GangChat_v0.5.1.exe"),
            )
        var backCount = 0
        var ignoreCount = 0
        var downloadCount = 0
        var refreshCount = 0

        compose.mainClock.autoAdvance = false
        compose.setUiContent { _ ->
            AppUpdatePage(
                update = update,
                checking = false,
                downloading = false,
                downloadedBytes = 0,
                wrapInScaffold = true,
                onBack = { backCount += 1 },
                onRefresh = { refreshCount += 1 },
                onIgnoreVersion = { ignoreCount += 1 },
                onDownload = { downloadCount += 1 },
            )
        }
        compose.pumpAndSettle()

        compose.onNodeWithText("发现新版本").assertExists()
        assertTrue(exists("v0.5.1"))
        compose.onNodeWithText("发行时间").assertExists()
        compose.onNodeWithText("2026/07/08 09:02 UTC+08:00").assertExists()
        compose.onNodeWithText("版本日志").assertExists()
        compose.onNodeWithText("无").assertExists()
        assertFalse(exists("安装包来自"))
        assertFalse(compose.onAllNodesWithText("安装包来自", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertFalse(exists("English"))
        compose.onTooltip("重新检查").assertExists()
        assertFalse(exists("继续使用"))
        button("忽略此版本").assertExists()
        button("下载新版本").assertExists()

        compose.onTooltip("返回").performClick()
        compose.pumpAndSettle()
        compose.onTooltip("重新检查").performClick()
        compose.pumpAndSettle()
        button("忽略此版本").performClick()
        compose.pumpAndSettle()
        button("下载新版本").performClick()
        compose.pumpAndSettle()

        assertEquals(1, backCount)
        assertEquals(1, refreshCount)
        assertEquals(1, ignoreCount)
        assertEquals(1, downloadCount)
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun `narrow update page scales release time without ellipsis`() {
        val update =
            AvailableAppUpdate(
                currentVersion = "1.0.0",
                latestVersion = "1.0.1",
                asset =
                    ReleaseAsset(
                        key = "releases/GangChat_v1.0.1.apk",
                        version = "1.0.1",
                        platform = AppUpdatePlatform.android,
                        releasedAt = DateTime.utc(2026, 7, 29, 9, 49),
                    ),
                downloadUrl = Uri.parse("https://os.example.test/gang-chat/releases/GangChat_v1.0.1.apk"),
            )

        compose.mainClock.autoAdvance = false
        compose.setUiContent { _ ->
            AppUpdatePage(
                update = update,
                checking = false,
                downloading = false,
                downloadedBytes = 0,
                wrapInScaffold = true,
                onBack = {},
                onRefresh = {},
                onIgnoreVersion = {},
                onDownload = {},
            )
        }
        compose.pumpAndSettle()

        val value = compose.onNodeWithText("2026/07/29 17:49 UTC+08:00", useUnmergedTree = true)
        val fitted = compose.onNodeWithTag(appUpdateReleaseTimeFitTag, useUnmergedTree = true)
        value.assertExists()
        fitted.assertExists()
        val layout = value.textLayout()
        assertFalse(layout.hasVisualOverflow)
        assertFalse(layout.isLineEllipsized(0))
        val density = compose.density.density
        val textWidth = layout.size.width / density
        val fittedRect = fitted.rect()
        assertTrue(textWidth > (fittedRect.right - fittedRect.left).value)
    }

    @Test
    fun `update gate skips ignored version until a higher version appears`() {
        val ignored =
            AvailableAppUpdate(
                currentVersion = "0.5.0",
                latestVersion = "0.5.1",
                asset = ReleaseAsset(key = "releases/GangChat_v0.5.1.exe", version = "0.5.1", platform = AppUpdatePlatform.windows),
                downloadUrl = Uri.parse("https://os.example.test/gang-chat/releases/GangChat_v0.5.1.exe"),
            )
        val higher =
            AvailableAppUpdate(
                currentVersion = "0.5.0",
                latestVersion = "0.5.2",
                asset = ReleaseAsset(key = "releases/GangChat_v0.5.2.exe", version = "0.5.2", platform = AppUpdatePlatform.windows),
                downloadUrl = Uri.parse("https://os.example.test/gang-chat/releases/GangChat_v0.5.2.exe"),
            )
        val store = FakeAutoUpdatePromptStore(true, ignoredVersion = "0.5.1")
        val updateService = FakeReleaseUpdateService(ignored)
        val reportedUpdates = mutableListOf<AvailableAppUpdate>()
        var bucketUrl by mutableStateOf("https://os.example.test/gang-chat")

        compose.mainClock.autoAdvance = false
        compose.setUiContent { _ ->
            AppUpdateGate(
                releaseBucketUrl = bucketUrl,
                currentVersion = "0.5.0",
                autoUpdatePromptStore = store,
                updateService = updateService,
                platformOverride = AppUpdatePlatform.windows,
                onUpdateAvailable = { reportedUpdates.add(it) },
                child = { Text("Home is visible") },
            )
        }
        compose.pumpAndSettle()

        assertTrue(reportedUpdates.isEmpty())

        updateService.update = higher
        bucketUrl = "https://os.example.test/gang-chat?refresh=1"
        compose.pumpAndSettle()

        assertEquals(1, reportedUpdates.size)
        assertSame(higher, reportedUpdates.single())
    }

    @Test
    fun `update gate keeps child when auto prompt is disabled`() {
        compose.mainClock.autoAdvance = false
        compose.setUiContent { _ ->
            AppUpdateGate(
                releaseBucketUrl = "https://os.example.test/gang-chat",
                currentVersion = "0.5.0",
                autoUpdatePromptStore = FakeAutoUpdatePromptStore(false),
                updateService = FakeReleaseUpdateService(null),
                platformOverride = AppUpdatePlatform.windows,
                child = { Text("Home is visible") },
            )
        }
        compose.pumpAndSettle()

        assertFalse(exists("发现新版本"))
        compose.onNodeWithText("Home is visible").assertExists()
    }
}

/** Dart's `_FakeReleaseUpdateService` / `_MutableFakeReleaseUpdateService`. */
class FakeReleaseUpdateService(var update: AvailableAppUpdate?) : ReleaseUpdateService() {
    var requestedPlatform: AppUpdatePlatform? = null

    override suspend fun checkForUpdate(
        bucketUrl: String,
        currentVersion: String,
        platform: AppUpdatePlatform,
    ): AvailableAppUpdate? {
        requestedPlatform = platform
        return update
    }
}

/** Dart's `_FakeAutoUpdatePromptStore`. */
class FakeAutoUpdatePromptStore(private val enabled: Boolean, private val ignoredVersion: String? = null) : AutoUpdatePromptStore() {
    override suspend fun read(): Boolean = enabled

    override suspend fun write(enabled: Boolean) {}

    override suspend fun readIgnoredVersion(): String? = ignoredVersion

    override suspend fun writeIgnoredVersion(version: String?) {}
}

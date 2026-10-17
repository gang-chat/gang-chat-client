package com.gangchat.client.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.ReleaseAsset
import com.gangchat.client.app.SettingsSection
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.shell.ShellTestLoop
import com.gangchat.client.shell.jsonResponse
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.setUiContent
import com.gangchat.client.ui.solidPng
import com.gangchat.client.ui.testMediaCacheController
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
private const val phone = "w411dp-h891dp-xxhdpi"
private const val tablet = "w1280dp-h800dp-xhdpi"
private const val tabletPortrait = "w800dp-h1280dp-xhdpi"

/** Roborazzi captures of the settings page and its sections. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = phone, shadows = [ShadowNoOpMagnifier::class])
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val loop = ShellTestLoop()

    @Before
    fun setUp() {
        loop.start()
        installTestImageLoader()
    }

    @After
    fun tearDown() = loop.finish()

    private val baseUrl = "http://screenshot.test/api/v1"

    private val userJson: Map<String, Any?> =
        settingsCurrentUserJson +
            mapOf(
                "id" to "user-demo",
                "uid" to "100000",
                "username" to "demo",
                "display_name" to "Demo",
                "email" to "demo@example.com",
                "bio" to "今天也要好好听歌",
            )

    private val user = CurrentUser.fromJson(userJson)

    private val assetBase = "http://127.0.0.1:21116"

    private val cache =
        testMediaCacheController(
            mapOf(
                "$assetBase/assets/s0.png" to solidPng(64, 64, 0xFFE0A12A.toInt()),
                "$assetBase/assets/s1.png" to solidPng(120, 60, 0xFF26B36F.toInt()),
                "$assetBase/assets/s2.png" to solidPng(60, 120, 0xFF5CB8FF.toInt()),
                "$assetBase/assets/s3.png" to solidPng(64, 64, 0xFFB69CFF.toInt()),
            ),
        )

    private fun stickerJson(index: Int): Map<String, Any?> =
        linkedMapOf(
            "id" to "sticker_$index",
            "name" to "表情 $index",
            "sort_order" to (index + 1) * 10,
            "asset" to
                linkedMapOf(
                    "id" to "asset_$index",
                    "url" to "/assets/s${index % 4}.png",
                    "thumbnail_url" to null,
                    "mime_type" to "image/png",
                ),
        )

    private val now = DateTime.now().millisecondsSinceEpoch / 1000

    private val sessionsJson =
        listOf(
            linkedMapOf(
                "id" to "session-1",
                "user_agent" to "GangChat/1.0.0 (Android 15; Pixel 8)",
                "ip_address" to "203.0.113.10",
                "location" to "上海",
                "created_at" to now - 86400 * 9,
                "last_used_at" to now - 600,
                "expires_at" to now + 86400 * 20,
                "revoked_at" to null,
                "is_current" to true,
            ),
            linkedMapOf(
                "id" to "session-2",
                "user_agent" to "GangChat/1.0.0 (Windows NT 10.0)",
                "ip_address" to "198.51.100.4",
                "location" to "北京",
                "created_at" to now - 86400 * 20,
                "last_used_at" to now - 86400,
                "expires_at" to now + 86400 * 10,
                "revoked_at" to null,
                "is_current" to false,
            ),
        )

    private val playlistsJson =
        listOf(
            linkedMapOf("id" to "mbp_1", "name" to "夜晚", "description" to "", "revision" to 2, "item_count" to 3),
            linkedMapOf("id" to "mbp_2", "name" to "通勤路上", "description" to "", "revision" to 1, "item_count" to 18),
            linkedMapOf("id" to "mbp_3", "name" to "空歌单", "description" to "", "revision" to 1, "item_count" to 0),
        )

    private val playlistItemsJson =
        listOf(
            linkedMapOf("id" to "mbpi_1", "playlist_id" to "mbp_1", "track_id" to "t1", "source" to "netease", "title" to "晴天", "artists" to listOf("周杰伦"), "duration_ms" to 269000, "sort_order" to 10),
            linkedMapOf("id" to "mbpi_2", "playlist_id" to "mbp_1", "track_id" to "t2", "source" to "netease", "title" to "夜曲", "artists" to listOf("周杰伦"), "duration_ms" to 226000, "sort_order" to 20),
            linkedMapOf("id" to "mbpi_3", "playlist_id" to "mbp_1", "track_id" to "t3", "source" to "bilibili", "title" to "平凡之路", "artists" to listOf("朴树"), "duration_ms" to 301000, "sort_order" to 30),
        )

    private fun api(): GangApiClient =
        settingsApi(baseUrl = baseUrl) { request ->
            when (request.url.path) {
                "/api/v1/me" -> jsonResponse(userJson)
                "/api/v1/auth/sessions" -> jsonResponse(sessionsJson)
                "/api/v1/sticker-packs" ->
                    jsonResponse(
                        mapOf(
                            "packs" to
                                listOf(
                                    linkedMapOf(
                                        "id" to "pack_demo",
                                        "scope" to "personal",
                                        "name" to "我的表情",
                                        "sort_order" to 10,
                                        "stickers" to List(9) { index -> stickerJson(index) },
                                    ),
                                ),
                        ),
                    )
                "/api/v1/me/music-box/playlists" ->
                    jsonResponse(
                        mapOf(
                            "playlists" to playlistsJson,
                            "pagination" to mapOf("page" to 1, "page_size" to 50, "total" to playlistsJson.size, "has_more" to false),
                            "limits" to mapOf("max_playlists" to 50, "max_playlist_items" to 500),
                        ),
                    )
                "/api/v1/me/music-box/playlists/mbp_1" ->
                    jsonResponse(
                        mapOf(
                            "playlist" to playlistsJson[0],
                            "items" to playlistItemsJson,
                            "pagination" to mapOf("page" to 1, "page_size" to 50, "total" to playlistItemsJson.size, "has_more" to false),
                        ),
                    )
                else -> HttpResponse("unexpected request: ${request.url}", 404)
            }
        }

    private val devices =
        listOf(
            AudioDeviceInfo(deviceId = "speaker", label = "手机扬声器", kind = "audiooutput"),
            AudioDeviceInfo(deviceId = "bt-out", label = "AirPods Pro", kind = "audiooutput"),
            AudioDeviceInfo(deviceId = "mic", label = "手机麦克风", kind = "audioinput"),
            AudioDeviceInfo(deviceId = "bt-in", label = "AirPods Pro", kind = "audioinput"),
        )

    private val update =
        AvailableAppUpdate(
            currentVersion = "1.0.0",
            latestVersion = "1.2.0",
            asset =
                ReleaseAsset(
                    key = "releases/GangChat_v1.2.0.apk",
                    version = "1.2.0",
                    platform = AppUpdatePlatform.android,
                    releasedAt = DateTime.utc(2026, 7, 8, 1, 2, 3),
                ),
            downloadUrl = Uri.parse("https://releases.example.test/releases/GangChat_v1.2.0.apk"),
        )

    private fun pumpSettings(
        section: SettingsSection = SettingsSection.profile,
        initialAppUpdate: AvailableAppUpdate? = null,
        initialMusicPlaylistId: String? = null,
    ) {
        val api = api()
        compose.setSettingsContent(cache) { _ ->
            SettingsPage(
                initialSection = section,
                initialAppUpdate = initialAppUpdate,
                initialMusicPlaylistId = initialMusicPlaylistId,
                currentUser = user,
                api = api,
                apiBaseUrl = "$baseUrl/${section.name}",
                appVersion = "1.0.0",
                audioDeviceStore = FakeSettingsAudioDeviceStore(inputVolume = 0.62, outputVolume = 0.8),
                audioDeviceService = FakeSettingsAudioDeviceService(devices = devices),
                androidSystemService = FakeSettingsAndroidSystemService(supported = true, systemAllowsNotifications = true),
                autoUpdatePromptStore = FakeSettingsAutoUpdatePromptStore(),
                installInfoService = FakeSettingsInstallInfoService("2026-07-01T12:34:56"),
                closeBehaviorStore = MemorySettingsCloseBehaviorStore(),
                languageStore = MemorySettingsLanguageStore(),
                onClose = {},
            )
        }
        settle()
    }

    /** Lets the API answers, the cache's I/O and Coil's decoding finish. */
    private fun settle() {
        repeat(25) {
            Thread.sleep(20)
            compose.waitForIdle()
        }
    }

    private fun capture(name: String) {
        settle()
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    private fun selectSection(label: String) {
        val tile = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(com.gangchat.client.ui.settingsSideNavigationTag)))
        // Only the compact strip scrolls; the wide tiles all fit.
        runCatching { tile.performScrollTo() }
        tile.performClick()
        settle()
    }

    /** Scrolls the section's list (the only lazy list on the page) to its last item. */
    private fun scrollToEnd() {
        val list = compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction())
        val count = list.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo].rowCount
        list.performScrollToIndex(count - 1)
        settle()
    }

    @Test
    fun phoneHome() {
        pumpSettings()
        capture("settings_phone_home")
    }

    @Test
    fun phoneProfileAvatar() {
        pumpSettings()
        scrollToEnd()
        capture("settings_phone_profile_avatar")
    }

    @Test
    fun phonePreferences() {
        pumpSettings(SettingsSection.preferences)
        capture("settings_phone_preferences")
    }

    @Test
    fun phoneSecurity() {
        // Selecting the tile (unlike initialSection) also loads the session list, as in Dart.
        pumpSettings()
        selectSection("隐私和安全")
        capture("settings_phone_security")
        scrollToEnd()
        capture("settings_phone_security_bottom")
    }

    @Test
    fun phoneVoice() {
        pumpSettings()
        selectSection("语音和视频")
        capture("settings_phone_voice")
        scrollToEnd()
        capture("settings_phone_voice_screen_share")
    }

    /** The audio panel while a test runs, with the level meter lit. */
    @Test
    fun phoneLevelMeters() {
        compose.setUiContent(background = UiColors.surfaceLow) { _ ->
            Box(Modifier.fillMaxSize().background(UiColors.surfaceLow).padding(16.dp)) {
                _SettingsGroup(
                    title = "输入",
                    children =
                        listOf {
                            _SettingsSubPanel {
                                _AudioControlPanel(
                                    title = "输入音量",
                                    icon = Icons.Filled.GraphicEq,
                                    volume = 0.62,
                                    level = 0.55,
                                    testing = true,
                                    testTooltip = "停止输入测试",
                                    disabled = false,
                                    onVolumeChanged = {},
                                    onToggleTest = {},
                                )
                            }
                        },
                )
            }
        }
        capture("settings_phone_level_meter_active")
    }

    @Test
    fun phoneStickers() {
        pumpSettings()
        selectSection("我的表情包")
        capture("settings_phone_stickers")
        compose.onNodeWithText("批量管理").performClick()
        compose.onNodeWithTag(personalStickerItemsScrollTag).performTouchInput { click(topLeft + androidx.compose.ui.geometry.Offset(40f, 40f)) }
        capture("settings_phone_stickers_managing")
    }

    @Test
    fun phonePlaylists() {
        pumpSettings(SettingsSection.playlists)
        capture("settings_phone_playlists")
        compose.onNodeWithText("管理").performClick()
        compose.onNodeWithTag(personalMusicPlaylistCardTag("mbp_2")).performTouchInput { click(topLeft + androidx.compose.ui.geometry.Offset(6f, 6f)) }
        capture("settings_phone_playlists_managing")
    }

    @Test
    fun phonePlaylistDetail() {
        pumpSettings(SettingsSection.playlists, initialMusicPlaylistId = "mbp_1")
        capture("settings_phone_playlist_detail")
    }

    @Test
    fun phoneAbout() {
        pumpSettings(SettingsSection.about)
        capture("settings_phone_about")
    }

    @Test
    fun phoneAboutWithUpdate() {
        pumpSettings(initialAppUpdate = update)
        capture("settings_phone_about_update")
    }

    /** The update page while the APK downloads (progress bar and percentage). */
    @Test
    fun phoneUpdateDownloading() {
        val api = api()
        compose.setSettingsContent(cache) { _ ->
            SettingsPage(
                initialAppUpdate = update,
                currentUser = user,
                api = api,
                appVersion = "1.0.0",
                androidSystemService = FakeSettingsAndroidSystemService(supported = true),
                autoUpdatePromptStore = FakeSettingsAutoUpdatePromptStore(),
                installInfoService = FakeSettingsInstallInfoService(null),
                closeBehaviorStore = MemorySettingsCloseBehaviorStore(),
                languageStore = MemorySettingsLanguageStore(),
                releaseUpdateService = HangingDownloadReleaseUpdateService(receivedBytes = 37_000_000, totalBytes = 52_000_000),
                onClose = {},
            )
        }
        settle()
        compose.onNodeWithText("下载新版本").performClick()
        settle()
        compose.onAllNodes(hasText("下载新版本")).onLast().performClick()
        capture("settings_phone_update_downloading")
    }

    @Test
    fun phoneSearch() {
        pumpSettings()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(settingsSearchInputTag))).performTextReplacement("密码")
        capture("settings_phone_search")
    }

    @Test
    @Config(qualifiers = tablet)
    fun tabletHome() {
        pumpSettings()
        capture("settings_tablet_home")
    }

    @Test
    @Config(qualifiers = tablet)
    fun tabletVoice() {
        pumpSettings()
        selectSection("语音和视频")
        capture("settings_tablet_voice")
    }

    @Test
    @Config(qualifiers = tablet)
    fun tabletPlaylists() {
        pumpSettings(SettingsSection.playlists)
        capture("settings_tablet_playlists")
    }

    @Test
    @Config(qualifiers = tablet)
    fun tabletAboutWithUpdate() {
        pumpSettings(initialAppUpdate = update)
        capture("settings_tablet_about_update")
    }

    @Test
    @Config(qualifiers = tabletPortrait)
    fun tabletPortraitSecurity() {
        pumpSettings()
        selectSection("隐私和安全")
        capture("settings_tablet_portrait_security")
    }
}

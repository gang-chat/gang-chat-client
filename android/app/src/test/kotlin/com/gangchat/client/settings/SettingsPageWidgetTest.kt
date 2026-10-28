package com.gangchat.client.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.EmailVerificationController
import com.gangchat.client.app.PasswordResetController
import com.gangchat.client.app.SettingsController
import com.gangchat.client.app.SettingsSection
import com.gangchat.client.app.gangChatClientReleaseTimestamp
import com.gangchat.client.app.officialVersionTimeLabel
import com.gangchat.client.auth.AuthClient
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.live.SystemAudioDevices
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.StickerPackStore
import com.gangchat.client.shell.FakeEmailVerification
import com.gangchat.client.shell.FeedbackMailService
import com.gangchat.client.shell.ReleaseUpdateService
import com.gangchat.client.shell.ShellTestLoop
import com.gangchat.client.shell.enterText
import com.gangchat.client.shell.field
import com.gangchat.client.shell.hasToggleableState
import com.gangchat.client.shell.isToggledOn
import com.gangchat.client.shell.jsonResponse
import com.gangchat.client.shell.pump
import com.gangchat.client.shell.pumpAndSettle
import com.gangchat.client.testing.MockClient
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.hasTooltip
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.settingsSideNavigationTag
import com.gangchat.client.ui.textColor
import kotlinx.coroutines.runBlocking
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
import java.util.Locale

private const val baseUrl = "http://example.test/api/v1"

/**
 * The settings cases of `test/widget/shell/gang_app_shell_test_parts/
 * settings_widget_tests.dart` that host `SettingsPage` directly, plus the
 * login suite's `settings email verification hides current password for the
 * session` (`auth_widget_tests.dart`).
 *
 * Deferred to the home shell port (they pump `GangApp` with a live session):
 * `Android task removal leaves live before native process termination`,
 * `settings audio volumes sync to live channel sliders`, `settings nonzero
 * audio volumes clear live mute states`, `microphone unmute clears headphones
 * mute`. Skipped as desktop-only: `direct close exit keeps token while
 * leaving live and going offline` (the close-to-exit window behaviour).
 *
 * The about cases ran with `updatePlatformOverride: windows` and `.exe`
 * listings; here they use Android and `.apk` (the confirmation shows the
 * Android installer text), and feedback mail goes through the Android
 * `mailto:` bridge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h900dp-mdpi", shadows = [ShadowNoOpMagnifier::class])
class SettingsPageWidgetTest {
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

    private val currentUser: CurrentUser get() = CurrentUser.fromJson(settingsCurrentUserJson)

    /** `SettingsPage(isSubWindow: true, ...)` with the platform services faked. */
    private fun pumpSettings(
        api: GangApi? = null,
        initialSection: SettingsSection = SettingsSection.profile,
        currentUser: CurrentUser? = null,
        controller: SettingsController? = null,
        stickerPackStore: StickerPackStore? = null,
        emailVerificationController: EmailVerificationController? = null,
        passwordResetController: PasswordResetController? = null,
        appVersion: String = "0.4.0",
        audioDeviceStore: FakeSettingsAudioDeviceStore = FakeSettingsAudioDeviceStore(),
        androidSystemService: FakeSettingsAndroidSystemService = FakeSettingsAndroidSystemService(),
        autoUpdatePromptStore: FakeSettingsAutoUpdatePromptStore = FakeSettingsAutoUpdatePromptStore(),
        installInfoService: FakeSettingsInstallInfoService = FakeSettingsInstallInfoService(null),
        releaseUpdateService: ReleaseUpdateService? = null,
        feedbackMailService: FeedbackMailService? = null,
        onVolumeChanged: ((String, Double) -> Unit)? = null,
        onVolumeBoostChanged: ((String, Boolean) -> Unit)? = null,
        onScreenShareMaxHeightChanged: ((Int) -> Unit)? = null,
        onScreenShareFrameRateChanged: ((Int) -> Unit)? = null,
        generation: () -> Int = { 0 },
    ) {
        compose.mainClock.autoAdvance = false
        val systemAudioDevices = SystemAudioDevices(supported = false)
        compose.setSettingsContent { _ ->
            key(generation()) {
                SettingsPage(
                    isSubWindow = true,
                    initialSection = initialSection,
                    api = api,
                    apiBaseUrl = if (api == null) "" else baseUrl,
                    controller = controller,
                    currentUser = currentUser,
                    stickerPackStore = stickerPackStore ?: StickerPackStore(),
                    emailVerificationController = emailVerificationController,
                    passwordResetController = passwordResetController,
                    appVersion = appVersion,
                    updatePlatformOverride = AppUpdatePlatform.android,
                    systemAudioDevices = systemAudioDevices,
                    audioDeviceStore = audioDeviceStore,
                    audioDeviceService = FakeSettingsAudioDeviceService(),
                    androidSystemService = androidSystemService,
                    autoUpdatePromptStore = autoUpdatePromptStore,
                    installInfoService = installInfoService,
                    releaseUpdateService = releaseUpdateService ?: ReleaseUpdateService(httpClient = MockClient { HttpResponse("offline", 503) }),
                    feedbackMailService = feedbackMailService ?: FeedbackMailService(androidSystemService = androidSystemService),
                    closeBehaviorStore = MemorySettingsCloseBehaviorStore(),
                    languageStore = MemorySettingsLanguageStore(),
                    onVolumeChanged = onVolumeChanged,
                    onVolumeBoostChanged = onVolumeBoostChanged,
                    onScreenShareMaxHeightChanged = onScreenShareMaxHeightChanged,
                    onScreenShareFrameRateChanged = onScreenShareFrameRateChanged,
                )
            }
        }
        compose.pumpAndSettle()
    }

    private fun inSideNavigation(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(settingsSideNavigationTag))

    private fun countOf(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().size

    /** Dart's `find.widgetWithText(ui.Button, label)`. */
    private fun button(label: String) = hasText(label) and (hasClickAction() or SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))

    private fun tapButton(label: String) {
        compose.scrollSectionTo(button(label))
        compose.onAllNodes(button(label)).onFirst().performClick()
        compose.pumpAndSettle()
    }

    private fun tapLastButton(label: String) {
        compose.onAllNodes(button(label)).onLast().performClick()
        compose.pumpAndSettle()
    }

    /** Dart's `find.byKey(key)` finding something (tags inside a text field merge away in the merged tree). */
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun jsonBody(request: HttpRequest): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return jsonDecode(request.body) as Map<String, Any?>
    }

    @Test
    @Config(qualifiers = "w980dp-h820dp-mdpi")
    fun `managed user settings reuse the ordinary page with target account data`() {
        val requestedUris = mutableListOf<Uri>()
        val targetUser =
            LinkedHashMap(settingsCurrentUserJson).apply {
                put("id", "target_user")
                put("uid", "1000099")
                put("username", "target_name")
                put("display_name", "目标用户")
                put("email", "target@example.test")
                put("is_superuser", false)
            }
        val pixel =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val api =
            settingsApi(baseUrl) { request ->
                requestedUris.add(request.url)
                when (request.url.path) {
                    "/api/v1/users/target_user/settings" -> jsonResponse(mapOf("user" to targetUser))
                    "/api/v1/users/target_user/sessions" -> jsonResponse(emptyList<Any>())
                    "/api/v1/sticker-packs" ->
                        jsonResponse(
                            mapOf(
                                "packs" to
                                    listOf(
                                        mapOf(
                                            "id" to "target_pack",
                                            "scope" to "personal",
                                            "name" to "目标表情包",
                                            "sort_order" to 10,
                                            "stickers" to
                                                listOf(
                                                    mapOf(
                                                        "id" to "target_sticker",
                                                        "name" to "目标表情",
                                                        "sort_order" to 10,
                                                        "asset" to
                                                            mapOf(
                                                                "id" to "target_asset",
                                                                "url" to pixel,
                                                                "thumbnail_url" to pixel,
                                                                "mime_type" to "image/png",
                                                            ),
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        )
                    else -> HttpResponse("unexpected request: ${request.url}", 404)
                }
            }
        val stickerStore = StickerPackStore()
        pumpSettings(
            api = api,
            initialSection = SettingsSection.security,
            controller = SettingsController(api = api, apiBaseUrl = baseUrl, stickerPackStore = stickerStore, managedUserId = "target_user"),
            stickerPackStore = stickerStore,
        )

        assertTrue(exists(settingsSideNavigationTag))
        // The page title names the managed account in the settings header.
        assertEquals(1, compose.textCount("目标用户 的用户设置"))
        assertEquals(1, countOf(inSideNavigation("用户资料")))
        assertEquals(1, countOf(inSideNavigation("偏好设置")))
        assertEquals(1, countOf(inSideNavigation("隐私和安全")))
        assertEquals(1, countOf(inSideNavigation("语音和视频")))
        assertEquals(1, countOf(inSideNavigation("我的表情包")))
        assertEquals(1, countOf(inSideNavigation("关于Gang Chat")))
        // The selected section is repeated as the content header.
        assertEquals(2, compose.textCount("隐私和安全"))
        compose.scrollSectionTo(button("忘记密码"))
        assertEquals(0, compose.textCount("当前密码"))
        assertEquals(1, countOf(button("忘记密码")))
        assertFalse(compose.buttonWithTextEnabled("忘记密码"))

        compose.onNode(inSideNavigation("我的表情包")).performClick()
        compose.pumpAndSettle()
        compose.pumpUntil {
            runBlocking { stickerStore.readPersonalPacks(userId = "target_user", apiBaseUrl = baseUrl) } != null
        }
        compose.pumpAndSettle()

        assertEquals(2, compose.textCount("我的表情包"))
        assertEquals(0, compose.textCount("目标表情包"))
        assertTrue(requestedUris.any { it.path == "/api/v1/sticker-packs" && it.queryParameters["owner_user_id"] == "target_user" })
        val cached = runBlocking { stickerStore.readPersonalPacks(userId = "target_user", apiBaseUrl = baseUrl) }!!
        assertEquals("目标表情", cached.single().stickers.single().name)
    }

    @Test
    @Config(qualifiers = "w980dp-h820dp-mdpi")
    fun `managed user settings can suspend and restore the target account`() {
        val statusUpdates = mutableListOf<String>()
        val targetUser =
            LinkedHashMap(settingsCurrentUserJson).apply {
                put("id", "suspension_target")
                put("uid", "1000100")
                put("username", "suspension_target")
                put("display_name", "封禁测试用户")
                put("email", "suspension-target@example.test")
                put("is_superuser", false)
                put("status", "active")
            }
        val api =
            settingsApi(baseUrl) { request ->
                when (request.url.path) {
                    "/api/v1/users/suspension_target/settings" -> {
                        if (request.method == "PATCH") {
                            val status = jsonBody(request)["status"] as String
                            statusUpdates.add(status)
                            targetUser["status"] = status
                        }
                        jsonResponse(mapOf("user" to targetUser))
                    }
                    "/api/v1/users/suspension_target/sessions" -> jsonResponse(emptyList<Any>())
                    else -> HttpResponse("unexpected request: ${request.url}", 404)
                }
            }
        val stickerStore = StickerPackStore()
        pumpSettings(
            api = api,
            initialSection = SettingsSection.security,
            controller =
                SettingsController(api = api, apiBaseUrl = baseUrl, stickerPackStore = stickerStore, managedUserId = "suspension_target"),
            stickerPackStore = stickerStore,
        )

        compose.scrollSectionTo(hasText("账号状态正常"))
        assertEquals(1, compose.textCount("账号状态正常"))
        tapButton("封禁账号")
        assertEquals(1, compose.textCount("确认封禁账号"))
        tapLastButton("封禁账号")

        assertEquals(listOf("suspended"), statusUpdates)
        assertEquals(1, compose.textCount("账号封禁中"))
        assertEquals(UiColors.danger, compose.onNode(hasText("账号封禁中"), useUnmergedTree = true).textColor())

        tapButton("解除封禁")
        assertEquals(1, compose.textCount("确认解除封禁"))
        tapLastButton("解除封禁")

        assertEquals(listOf("suspended", "active"), statusUpdates)
        assertEquals(1, compose.textCount("账号状态正常"))
    }

    private fun accountApi(
        accountUpdates: MutableList<Map<String, Any?>>,
        searchUsers: List<Map<String, Any?>> = emptyList(),
        verifiedEmailOnSave: Boolean = false,
    ) = settingsApi(baseUrl) { request ->
        when (request.url.path) {
            "/api/v1/me" -> jsonResponse(settingsCurrentUserJson)
            "/api/v1/auth/sessions" -> jsonResponse(emptyList<Any>())
            "/api/v1/users/search" -> jsonResponse(mapOf("users" to searchUsers))
            "/api/v1/users/me/account" -> {
                val body = jsonBody(request)
                accountUpdates.add(body)
                val user = LinkedHashMap(settingsCurrentUserJson)
                if (verifiedEmailOnSave) {
                    user["email"] = body["email"]
                    user["email_verified"] = true
                } else {
                    user.putAll(body)
                }
                jsonResponse(mapOf("user" to user))
            }
            else -> HttpResponse("unexpected request: ${request.url}", 404)
        }
    }

    @Test
    fun `settings profile save submits changed login username`() {
        val accountUpdates = mutableListOf<Map<String, Any?>>()
        pumpSettings(api = accountApi(accountUpdates), currentUser = currentUser)

        assertEquals(0, countOf(button("保存登录用户名")))
        assertEquals(0, compose.textCount("合法"))
        assertEquals("kai", compose.field(settingsUsernameInputTag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)

        compose.enterText(settingsUsernameInputTag, "kai_new")
        compose.pump(400)
        compose.pumpAndSettle()

        assertEquals(1, compose.textCount("合法"))
        tapButton("保存用户资料")

        assertEquals(listOf(mapOf("username" to "kai_new")), accountUpdates)
        assertEquals(0, compose.textCount("合法"))
    }

    @Test
    fun `settings username availability marks duplicate invalid`() {
        val accountUpdates = mutableListOf<Map<String, Any?>>()
        pumpSettings(
            api =
                accountApi(
                    accountUpdates,
                    searchUsers = listOf(settingsUserJson(id = "user-2", username = "taken_name", displayName = "Taken")),
                ),
            currentUser = currentUser,
        )

        compose.enterText(settingsUsernameInputTag, "Taken_Name")
        compose.pump(400)
        compose.pumpAndSettle()

        assertEquals(1, compose.textCount("不合法"))
        assertEquals(1, compose.onAllNodes(hasTooltip("该登录用户名已被其他用户使用"), useUnmergedTree = true).fetchSemanticsNodes().size)

        tapButton("保存用户资料")
        assertTrue(accountUpdates.isEmpty())
    }

    @Test
    fun `settings email binding requires verification and keeps verified status`() {
        val accountUpdates = mutableListOf<Map<String, Any?>>()
        val emailVerification = FakeEmailVerification()
        pumpSettings(
            api = accountApi(accountUpdates, verifiedEmailOnSave = true),
            initialSection = SettingsSection.security,
            currentUser = currentUser,
            emailVerificationController = emailVerification.controller,
        )

        assertTrue(exists(settingsEmailVerifiedTag))
        assertFalse(exists(settingsEmailVerificationButtonTag))

        compose.enterText(settingsEmailInputTag, "")
        assertFalse(exists(settingsEmailVerifiedTag))
        assertFalse(exists(settingsEmailVerificationButtonTag))

        compose.enterText(settingsEmailInputTag, "new@example.test")
        assertTrue(exists(settingsEmailVerificationButtonTag))

        compose.scrollSectionTo(button("保存绑定信息"))
        compose.onNode(button("保存绑定信息")).performClick()
        compose.pump()
        assertEquals(1, compose.textCount("请先验证邮箱"))
        assertTrue(accountUpdates.isEmpty())

        compose.scrollSectionTo(hasTestTag(settingsEmailVerificationButtonTag))
        compose.pumpAndSettle()
        compose.onNodeWithTag(settingsEmailVerificationButtonTag).performClick()
        compose.pumpAndSettle()
        compose.enterText("auth-email-verification-code", "123456")
        tapLastButton("验证")
        assertTrue(exists(settingsEmailVerifiedTag))
        assertFalse(exists(settingsEmailVerificationButtonTag))

        tapButton("保存绑定信息")
        assertEquals(
            listOf(mapOf("email" to "new@example.test", "email_verification_token" to "email-verification-token")),
            accountUpdates,
        )
        assertTrue(exists(settingsEmailVerifiedTag))

        compose.scrollSectionTo(hasTestTag(settingsEmailInputTag))
        compose.pumpAndSettle()
        compose.enterText(settingsEmailInputTag, "changed-again@example.test")
        assertFalse(exists(settingsEmailVerifiedTag))
        assertTrue(exists(settingsEmailVerificationButtonTag))
        assertEquals(
            listOf(
                "available:new@example.test",
                "inspect:new@example.test",
                "start:new@example.test",
                "verify:email-challenge:123456",
            ),
            emailVerification.calls,
        )
    }

    @Test
    @Config(qualifiers = "w980dp-h760dp-mdpi")
    fun `settings about section checks updates and opens feedback mail`() {
        val autoUpdateWrites = mutableListOf<Boolean>()
        var updateCheckRequests = 0
        val api =
            settingsApi(baseUrl) { request ->
                when (request.url.path) {
                    "/api/v1/me" -> jsonResponse(settingsCurrentUserJson)
                    "/api/v1/app/version" -> jsonResponse(mapOf("latest_version" to "0.4.0", "minimum_supported_version" to "0.4.0"))
                    else -> HttpResponse("unexpected request: ${request.url}", 404)
                }
            }
        val androidSystemService = FakeSettingsAndroidSystemService(supported = true)
        pumpSettings(
            api = api,
            currentUser = currentUser,
            androidSystemService = androidSystemService,
            autoUpdatePromptStore = FakeSettingsAutoUpdatePromptStore(autoUpdateWrites, initialValue = true),
            installInfoService = FakeSettingsInstallInfoService("2026-07-01T12:34:56"),
            releaseUpdateService =
                ReleaseUpdateService(
                    httpClient =
                        MockClient {
                            updateCheckRequests += 1
                            HttpResponse(
                                """
                                <ListBucketResult>
                                  <Contents><Key>releases/GangChat_v0.4.0.apk</Key></Contents>
                                </ListBucketResult>
                                """.trimIndent(),
                                200,
                            )
                        },
                ),
        )

        compose.onAllNodesWithText("关于Gang Chat").onFirst().performClick()
        compose.pumpAndSettle()

        assertEquals(1, compose.textCount("版本信息"))
        assertEquals(1, compose.textCount("版本编号"))
        // The version appears in the field and as the about tile's summary.
        assertEquals(2, compose.textCount("0.4.0"))
        assertEquals(1, compose.textCount("发行时间"))
        assertEquals(1, compose.textCount("上次更新时间"))
        assertEquals(1, compose.textCount(officialVersionTimeLabel(gangChatClientReleaseTimestamp)))
        assertEquals(1, compose.textCount("2026/07/01 12:34"))
        assertEquals(1, compose.textCount("自动提示更新"))
        assertEquals(1, countOf(button("检查更新")))
        compose.scrollSectionTo(button("意见反馈"))
        assertEquals(1, countOf(button("意见反馈")))

        compose.onTooltip("刷新设置").performClick()
        compose.pumpAndSettle()

        assertEquals(0, updateCheckRequests)

        val autoUpdateTag = settingsToggleTag("自动提示更新")
        compose.scrollSectionTo(hasTestTag(autoUpdateTag))
        val autoUpdateSwitch = compose.onNode((hasTestTag(autoUpdateTag) or hasAnyAncestor(hasTestTag(autoUpdateTag))) and hasToggleableState)
        assertTrue(autoUpdateSwitch.isToggledOn())

        autoUpdateSwitch.performClick()
        compose.pumpAndSettle()

        assertEquals(listOf(false), autoUpdateWrites)
        assertFalse(autoUpdateSwitch.isToggledOn())

        tapButton("检查更新")

        assertEquals(1, updateCheckRequests)
        assertTrue(compose.hasTextNode("当前已是最新版本"))

        tapButton("意见反馈")

        assertEquals(1, androidSystemService.mailtoUris.size)
        val mail = androidSystemService.mailtoUris.single()
        assertEquals("kai@example.com", mail.queryParameters["from"])
        assertEquals("gang-chat@outlook.com", mail.path)
        assertTrue(mail.queryParameters["subject"]!!.contains("v0.4.0"))
    }

    @Test
    @Config(qualifiers = "w980dp-h760dp-mdpi")
    fun `settings about check opens update page for newer release`() {
        val autoUpdateStore = FakeSettingsAutoUpdatePromptStore(mutableListOf(), initialValue = true)
        pumpSettings(
            currentUser = currentUser,
            autoUpdatePromptStore = autoUpdateStore,
            installInfoService = FakeSettingsInstallInfoService("2026/07/01"),
            releaseUpdateService =
                ReleaseUpdateService(
                    httpClient =
                        MockClient {
                            HttpResponse(
                                """
                                <ListBucketResult>
                                  <Contents>
                                    <Key>releases/GangChat_v0.4.1.apk</Key>
                                    <LastModified>2026-07-08T01:02:03.000Z</LastModified>
                                  </Contents>
                                </ListBucketResult>
                                """.trimIndent(),
                                200,
                            )
                        },
                ),
        )

        compose.onAllNodesWithText("关于Gang Chat").onFirst().performClick()
        compose.pumpAndSettle()
        tapButton("检查更新")

        assertEquals(1, compose.textCount("发现新版本"))
        assertTrue(compose.hasTextNode("v0.4.1"))
        assertEquals(1, compose.textCount("发行时间"))
        assertEquals(1, compose.textCount("版本日志"))
        assertEquals(1, compose.textCount("无"))
        assertEquals(1, countOf(button("忽略此版本")))
        assertEquals(1, countOf(button("下载新版本")))
        assertEquals(0, countOf(button("继续使用")))
        compose.onTooltip("重新检查").assertExists()

        compose.onNode(button("下载新版本")).performClick()
        compose.pumpAndSettle()

        assertTrue(compose.hasTextNode("下载新版本"))
        // PORT: Dart's Windows text ("会退出当前程序并启动安装程序") → the Android installer text.
        assertEquals(1, compose.textCount("下载完成后会打开系统安装界面", substring = true))

        tapLastButton("取消")
        assertEquals(1, compose.textCount("发现新版本"))

        compose.onNode(button("忽略此版本")).performClick()
        compose.pumpAndSettle()

        assertTrue(compose.hasTextNode("忽略此版本"))
        assertEquals(1, compose.textCount("不会再主动提示该版本", substring = true))

        tapLastButton("取消")
        assertEquals(1, compose.textCount("发现新版本"))

        compose.onNode(button("忽略此版本")).performClick()
        compose.pumpAndSettle()
        tapLastButton("忽略此版本")

        assertEquals(0, compose.textCount("发现新版本"))
        assertEquals(1, compose.textCount("版本信息"))
        assertEquals(1, countOf(button("检查更新")))
        assertEquals("0.4.1", autoUpdateStore.ignoredVersion)
    }

    /** The slider below [title] (Dart's `find.byType(Slider).at(i)`). */
    private fun slider(title: String) =
        compose.onNode(
            (hasTestTag(settingsVolumeSliderTag(title)) or hasAnyAncestor(hasTestTag(settingsVolumeSliderTag(title)))) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        )

    /** Dart's `tester.widget<Slider>(...).onChanged!(value)`. */
    private fun setSlider(
        title: String,
        value: Float,
    ) {
        slider(title).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.pump()
    }

    // Dart ran this at 800×600, where Flutter's list cache extent builds both
    // panels; a taller window keeps both sliders composed in the lazy list.
    @Test
    @Config(qualifiers = "w800dp-h1200dp-mdpi")
    fun `settings audio sliders apply live mute coupling rules`() {
        val volumeChanges = mutableListOf<String>()
        pumpSettings(
            audioDeviceStore = FakeSettingsAudioDeviceStore(inputVolume = 0.62, outputVolume = 0.0),
            onVolumeChanged = { kind, volume -> volumeChanges.add("$kind:${String.format(Locale.US, "%.2f", volume)}") },
        )

        compose.onAllNodesWithText("语音和视频").onFirst().performClick()
        compose.pumpAndSettle()

        assertEquals(
            2,
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and hasAnyAncestorSlider()).fetchSemanticsNodes().size,
        )
        assertEquals(0f, slider("输入音量").sliderValue())
        assertEquals(0f, slider("输出音量").sliderValue())
        assertTrue(volumeChanges.contains("audioinput:0.00"))
        assertTrue(volumeChanges.contains("audiooutput:0.00"))

        setSlider("输入音量", 0.8f)

        // A muted output comes back at the unity default (it was Dart's 0.5).
        assertEquals(0.8f, slider("输入音量").sliderValue(), 1e-4f)
        assertEquals(1f, slider("输出音量").sliderValue(), 1e-4f)
        assertTrue(volumeChanges.contains("audioinput:0.80"))
        assertTrue(volumeChanges.contains("audiooutput:1.00"))

        setSlider("输出音量", 0f)

        assertEquals(0f, slider("输入音量").sliderValue())
        assertEquals(0f, slider("输出音量").sliderValue())
        assertTrue(volumeChanges.contains("audioinput:0.00"))
        assertTrue(volumeChanges.contains("audiooutput:0.00"))
    }

    @Test
    @Config(qualifiers = "w800dp-h1200dp-mdpi")
    fun `settings boost switches raise the shown volume and report the change`() {
        val store = FakeSettingsAudioDeviceStore(inputVolume = 1.0, outputVolume = 0.5, outputVolumeBoost = true)
        val boostChanges = mutableListOf<String>()
        pumpSettings(
            audioDeviceStore = store,
            onVolumeBoostChanged = { kind, boost -> boostChanges.add("$kind:$boost") },
        )

        compose.onAllNodesWithText("语音和视频").onFirst().performClick()
        compose.pumpAndSettle()

        // Settings reports the stored switches so a live session follows them.
        assertEquals(listOf("audioinput:false", "audiooutput:true"), boostChanges)
        val inputBoost = boostSwitch("输入音量")
        val outputBoost = boostSwitch("输出音量")
        assertFalse(inputBoost.isToggledOn())
        assertTrue(outputBoost.isToggledOn())
        assertEquals(1, compose.textCount("100%"))
        assertEquals(1, compose.textCount("75%"))
        assertEquals(2, compose.textCount("增强 +50%"))

        inputBoost.performClick()
        compose.pumpAndSettle()

        assertTrue(inputBoost.isToggledOn())
        assertEquals(listOf("audioinput:false", "audiooutput:true", "audioinput:true"), boostChanges)
        assertEquals(listOf("input-volume-boost:true"), store.writes)
        assertEquals(0, compose.textCount("100%"))
        assertEquals(1, compose.textCount("150%"))

        outputBoost.performClick()
        compose.pumpAndSettle()

        assertFalse(outputBoost.isToggledOn())
        assertEquals("audiooutput:false", boostChanges.last())
        assertEquals(listOf("input-volume-boost:true", "output-volume-boost:false"), store.writes)
        assertEquals(1, compose.textCount("50%"))
    }

    private fun boostSwitch(title: String) =
        compose.onNode(
            (hasTestTag(settingsVolumeBoostTag(title)) or hasAnyAncestor(hasTestTag(settingsVolumeBoostTag(title)))) and
                hasToggleableState,
        )

    private fun hasAnyAncestorSlider(): SemanticsMatcher =
        SemanticsMatcher("is a volume slider") { node ->
            var current: androidx.compose.ui.semantics.SemanticsNode? = node
            while (current != null) {
                val tag = current.config.getOrElseNullable(SemanticsProperties.TestTag) { null }
                if (tag != null && tag.startsWith("settings-volume-slider:")) return@SemanticsMatcher true
                current = current.parent
            }
            false
        }

    // PORT: Android caps screen sharing at 30 FPS, so the 60 FPS option the
    // Dart (desktop host) case tapped is absent; 15 FPS shows the frame rate
    // is chosen independently of the resolution.
    @Test
    @Config(qualifiers = "w900dp-h1400dp-mdpi")
    fun `screen share resolution and frame rate use independent options`() {
        val heights = mutableListOf<Int>()
        val frameRates = mutableListOf<Int>()
        pumpSettings(
            initialSection = SettingsSection.voice,
            onScreenShareMaxHeightChanged = { heights.add(it) },
            onScreenShareFrameRateChanged = { frameRates.add(it) },
        )

        compose.scrollSectionTo(hasText("30 FPS"))
        assertEquals(1, compose.textCount("屏幕共享画质"))
        assertEquals(1, compose.textCount("480p"))
        assertEquals(1, compose.textCount("720p"))
        assertEquals(1, compose.textCount("1080p"))
        assertEquals(1, compose.textCount("15 FPS"))
        assertEquals(1, compose.textCount("30 FPS"))
        assertEquals(0, compose.textCount("60 FPS"))

        compose.onNodeWithText("1080p").performClick()
        compose.pump()
        compose.onNodeWithText("15 FPS").performClick()
        compose.pump()

        assertEquals(listOf(1080), heights)
        assertEquals(listOf(15), frameRates)
    }

    @Test
    fun `settings email verification hides current password for the session`() {
        val requestedPaths = mutableListOf<String>()
        val claimAuthorizations = mutableListOf<String?>()
        val controller =
            PasswordResetController(
                apiBaseUrl = "https://api.example.test/api/v1",
                accessTokenProvider = { "access-token" },
                authClientFactory = { base ->
                    AuthClient(
                        baseUrl = base,
                        httpClient =
                            MockClient { request ->
                                requestedPaths.add(request.url.path)
                                when (request.url.path) {
                                    "/api/v1/auth/password-reset/inspect" ->
                                        jsonResponse(mapOf("can_send" to true, "masked_email" to "k***@example.com", "retry_after" to 0))
                                    "/api/v1/auth/password-reset/start" ->
                                        jsonResponse(mapOf("challenge_id" to "challenge-1", "masked_email" to "k***@example.com", "retry_after" to 60))
                                    "/api/v1/auth/password-reset/verify" -> jsonResponse(mapOf("reset_token" to "reset-token"))
                                    "/api/v1/auth/password-reset/claim" -> {
                                        claimAuthorizations.add(request.headers["authorization"] ?: request.headers["Authorization"])
                                        jsonResponse(mapOf("ok" to true))
                                    }
                                    else -> HttpResponse("unexpected request", 404)
                                }
                            },
                    )
                },
            )
        val settingsApi =
            settingsApi("https://api.example.test/api/v1") { request ->
                when (request.url.path) {
                    "/api/v1/me" -> jsonResponse(settingsCurrentUserJson)
                    "/api/v1/auth/sessions" -> jsonResponse(emptyList<Any>())
                    else -> HttpResponse("unexpected settings request", 404)
                }
            }
        var generation by mutableIntStateOf(0)
        pumpSettings(
            api = settingsApi,
            initialSection = SettingsSection.security,
            currentUser = currentUser,
            passwordResetController = controller,
            generation = { generation },
        )

        assertEquals(1, compose.textCount("当前密码"))
        tapButton("忘记密码")
        compose.pumpUntil { exists("password-reset-code-input") }
        compose.enterText("password-reset-code-input", "123456")
        compose.onNodeWithTag("password-reset-verify-button").performClick()
        compose.pumpAndSettle()

        assertEquals(0, compose.textCount("当前密码"))
        assertEquals(1, compose.textCount("新密码"))
        assertEquals(1, compose.textCount("确认新密码"))
        assertEquals(1, compose.textCount("重置密码"))
        assertEquals(
            listOf(
                "/api/v1/auth/password-reset/inspect",
                "/api/v1/auth/password-reset/start",
                "/api/v1/auth/password-reset/verify",
                "/api/v1/auth/password-reset/claim",
            ),
            requestedPaths,
        )
        assertEquals(listOf<String?>("Bearer access-token"), claimAuthorizations)

        // Dart: `pumpWidget(SizedBox.shrink())` then the page again.
        generation += 1
        compose.pumpAndSettle()
        assertEquals(0, compose.textCount("当前密码"))

        controller.clearCurrentSessionAuthorization()
        generation += 1
        compose.pumpAndSettle()
        assertEquals(1, compose.textCount("当前密码"))
    }
}

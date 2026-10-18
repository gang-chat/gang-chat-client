package com.gangchat.client.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.gangchat.client.app.AuthRequest
import com.gangchat.client.app.EmailVerificationController
import com.gangchat.client.app.LoginAccountHistoryStore
import com.gangchat.client.app.LoginAccountRecord
import com.gangchat.client.app.PasswordResetController
import com.gangchat.client.app.findLoginAccountRecord
import com.gangchat.client.app.rememberLoginAccount
import com.gangchat.client.auth.AuthClient
import com.gangchat.client.auth.EmailVerificationChallenge
import com.gangchat.client.auth.EmailVerificationInspection
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.platform.SystemIconBrightness
import com.gangchat.client.testing.MockClient
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.onAllTooltips
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.segmentedControlSegmentTag
import com.gangchat.client.ui.setUiContent
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `test/widget/shell/gang_app_shell_test_parts/auth_widget_tests.dart`.
 * Flutter's test view is 800×600 logical pixels and its default platform is
 * Android, so the Dart cases without a platform override already ran the
 * Android layout. The cases that pumped `GangApp` (two here and the four of
 * `auth_smoke_widget_tests.dart`) are in [GangAppTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-mdpi", shadows = [ShadowNoOpMagnifier::class])
class LoginPageWidgetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val loop = ShellTestLoop()

    @Before
    fun setUp() = loop.start()

    @After
    fun tearDown() {
        GangChatPlatform.detachActivity(compose.activity)
        loop.finish()
    }

    private val fixedSize: (DpSize) -> AuthSizeForMode = { size -> { _, _ -> size } }

    private fun pumpLoginPage(
        onSubmit: AuthSubmit = { _, _ -> },
        sizeForMode: AuthSizeForMode = ::authWidgetSize,
        checkUsernameAvailability: AuthUsernameAvailabilityCheck? = null,
        checkEmailAvailability: AuthEmailAvailabilityCheck? = null,
        emailVerificationController: EmailVerificationController? = null,
        passwordResetController: PasswordResetController? = null,
        accountHistoryStore: LoginAccountHistoryStore? = null,
        submitPersistsAccountHistory: Boolean = false,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setUiContent(notifications = true) { _ ->
            if (accountHistoryStore == null) {
                LoginPage(
                    onSubmit = onSubmit,
                    sizeForMode = sizeForMode,
                    checkUsernameAvailability = checkUsernameAvailability,
                    checkEmailAvailability = checkEmailAvailability,
                    emailVerificationController = emailVerificationController,
                    passwordResetController = passwordResetController,
                    submitPersistsAccountHistory = submitPersistsAccountHistory,
                )
            } else {
                LoginPage(
                    onSubmit = onSubmit,
                    sizeForMode = sizeForMode,
                    checkUsernameAvailability = checkUsernameAvailability,
                    checkEmailAvailability = checkEmailAvailability,
                    emailVerificationController = emailVerificationController,
                    passwordResetController = passwordResetController,
                    accountHistoryStore = accountHistoryStore,
                    submitPersistsAccountHistory = submitPersistsAccountHistory,
                )
            }
        }
        compose.pump()
    }

    private fun tapText(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.pump()
    }

    private fun tapTooltip(message: String) {
        compose.onTooltip(message).performClick()
        compose.pump()
    }

    private fun tapSubmit() {
        compose.onNodeWithTag(authSubmitButtonTag).performClick()
        compose.pump()
    }

    private fun tapRegisterMode() {
        compose.onNodeWithTag(segmentedControlSegmentTag(1)).performClick()
        compose.pump()
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun tooltipCount(message: String) = compose.onAllTooltips(message).fetchSemanticsNodes().size

    private fun tagged(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun rememberPassword() = compose.onNode(hasToggleableState and hasAnyAncestor(hasTestTag(authRememberPasswordHotZoneTag)))

    private fun inDropdown(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(authAccountHistoryDropdownTag)))

    // auth_widget_tests.dart

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h720dp-mdpi")
    fun `android auth keeps the registration field group centered in both modes`() {
        GangChatPlatform.attachActivity(compose.activity)
        pumpLoginPage(sizeForMode = { registering, _ -> DpSize(430.dp, if (registering) 436.dp else 368.dp) })

        val loginSurface = compose.onNodeWithTag(authSurfaceTag).rect()
        val loginFirstInput = compose.onNodeWithTag(authIdentityInputTag).rect()
        assertDpEquals(436.dp, loginSurface.bottom - loginSurface.top)
        // Scaffold(backgroundColor: surfaceLow) outside the auth surface.
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(UiColors.surfaceLow, pixels[1, 1])
        // AnnotatedRegion<SystemUiOverlayStyle>.
        val window = compose.activity.window
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        @Suppress("DEPRECATION")
        assertEquals(UiColors.surfaceLow.toArgb(), window.statusBarColor)
        @Suppress("DEPRECATION")
        assertEquals(UiColors.surfaceLow.toArgb(), window.navigationBarColor)
        assertFalse(insets.isAppearanceLightStatusBars)
        assertFalse(insets.isAppearanceLightNavigationBars)
        assertEquals(SystemIconBrightness.light, _androidAuthSystemUiOverlayStyle.statusBarIconBrightness)

        tapRegisterMode()

        val inputs = listOf(authIdentityInputTag, authEmailInputTag, authPasswordInputTag, authConfirmPasswordInputTag)
        for (tag in inputs) compose.onNodeWithTag(tag).assertExists()
        val firstInput = compose.onNodeWithTag(inputs.first()).rect()
        val lastInput = compose.onNodeWithTag(inputs.last()).rect()
        assertDpEquals(loginFirstInput.top, firstInput.top)
        assertDpEquals(360.dp, (firstInput.top + lastInput.bottom) / 2, tolerance = 1.01f)
    }

    @Test
    fun `android auth submit does not overwrite authenticated avatar metadata`() {
        val store =
            MemoryLoginAccountHistoryStore(
                listOf(LoginAccountRecord(login = "kai", password = "old-password", useCount = 2, updatedAt = DateTime.utc(2026, 1, 1))),
            )
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 436.dp)),
            accountHistoryStore = store,
            submitPersistsAccountHistory = true,
            onSubmit = { request, rememberPassword ->
                store.records =
                    rememberLoginAccount(
                        records = store.records,
                        login = request.login,
                        password = request.password,
                        rememberPassword = rememberPassword,
                        avatarUrl = "/assets/avatar-kai/custom.png",
                        defaultAvatarKey = "green-2",
                        updateAvatarMetadata = true,
                        now = DateTime.utc(2026, 7, 23),
                    )
            },
        )
        compose.pump()

        compose.enterText(authIdentityInputTag, "kai")
        compose.enterText(authPasswordInputTag, "new-password")
        tapSubmit()

        val record = findLoginAccountRecord(store.records, "kai")!!
        assertEquals("/assets/avatar-kai/custom.png", record.avatarUrl)
        assertEquals("green-2", record.defaultAvatarKey)
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun `auth surface fits a narrow mobile viewport`() {
        pumpLoginPage(sizeForMode = fixedSize(DpSize(430.dp, 500.dp)))

        val surfaceRect = compose.onNodeWithTag(authSurfaceTag).rect()
        assertRectCloseTo(surfaceRect, androidx.compose.ui.unit.DpRect(0.dp, 0.dp, 320.dp, 480.dp))
        assertTrue(compose.onNodeWithTag(authIdentityInputTag).rect().left > 0.dp)
        assertTrue(compose.onNodeWithTag(authIdentityInputTag).rect().right < 320.dp)
    }

    @Test
    fun `auth login error layout stays scrollable across retries`() {
        var attempts = 0
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(416.dp, 368.dp)),
            onSubmit = { _, _ ->
                attempts += 1
                throw Exception("登录失败")
            },
        )

        compose.enterText(authIdentityInputTag, "kai")
        compose.enterText(authPasswordInputTag, "secret123")
        tapSubmit()
        compose.pumpAndSettle()

        assertEquals(1, attempts)

        compose.onNodeWithTag(authSubmitButtonTag).performScrollTo()
        compose.pumpAndSettle()
        tapSubmit()
        compose.pumpAndSettle()

        assertEquals(2, attempts)
    }

    private fun passwordResetController(handler: suspend (String) -> HttpResponse, requestedPaths: MutableList<String>) =
        PasswordResetController(
            apiBaseUrl = "https://api.example.test/api/v1",
            authClientFactory = { baseUrl ->
                AuthClient(
                    baseUrl = baseUrl,
                    httpClient =
                        MockClient { request ->
                            requestedPaths.add(request.url.path)
                            handler(request.url.path)
                        },
                )
            },
        )

    @Test
    fun `forgot password verifies email and opens reset dialog`() {
        val requestedPaths = mutableListOf<String>()
        val controller =
            passwordResetController(
                { path ->
                    when (path) {
                        "/api/v1/auth/password-reset/inspect" ->
                            jsonResponse(mapOf("can_send" to true, "masked_email" to "k***@example.test", "retry_after" to 0))
                        "/api/v1/auth/password-reset/start" ->
                            jsonResponse(mapOf("challenge_id" to "challenge-1", "masked_email" to "k***@example.test", "retry_after" to 60))
                        "/api/v1/auth/password-reset/verify" -> jsonResponse(mapOf("reset_token" to "reset-token"))
                        "/api/v1/auth/password-reset/complete" -> jsonResponse(mapOf("ok" to true))
                        else -> HttpResponse("unexpected request", 404)
                    }
                },
                requestedPaths,
            )
        pumpLoginPage(sizeForMode = fixedSize(DpSize(430.dp, 368.dp)), passwordResetController = controller)
        compose.enterText(authIdentityInputTag, "kai")
        tapText("忘记密码？")
        compose.pumpAndSettle()

        compose.onNodeWithText("邮箱验证").assertExists()
        compose.onNodeWithText("已发送验证码到该账号绑定的邮箱 k***@example.test").assertExists()
        compose.onNodeWithText("重新发送(60)").assertExists()

        compose.enterText("password-reset-code-input", "123456")
        compose.onNodeWithTag("password-reset-verify-button").performClick()
        compose.pumpAndSettle()

        assertTrue(compose.onAllNodesWithText("重置密码").fetchSemanticsNodes().isNotEmpty())
        compose.enterText("password-reset-new-password", "new-password")
        compose.enterText("password-reset-confirm-password", "new-password")
        compose.onNodeWithTag("password-reset-submit-button").performClick()
        compose.pump()
        compose.pump(200)

        assertEquals(
            listOf(
                "/api/v1/auth/password-reset/inspect",
                "/api/v1/auth/password-reset/start",
                "/api/v1/auth/password-reset/verify",
                "/api/v1/auth/password-reset/complete",
            ),
            requestedPaths,
        )
        compose.onNodeWithText("密码已重置，请使用新密码登录").assertExists()
    }

    @Test
    fun `forgot password inspects and inherits an existing cooldown`() {
        val requestedPaths = mutableListOf<String>()
        val controller =
            passwordResetController(
                { path ->
                    if (path == "/api/v1/auth/password-reset/inspect") {
                        jsonResponse(
                            mapOf(
                                "can_send" to false,
                                "challenge_id" to "challenge-existing",
                                "masked_email" to "k***@example.test",
                                "retry_after" to 42,
                            ),
                        )
                    } else {
                        HttpResponse("must not send during cooldown", 409)
                    }
                },
                requestedPaths,
            )
        pumpLoginPage(sizeForMode = fixedSize(DpSize(430.dp, 368.dp)), passwordResetController = controller)
        compose.enterText(authIdentityInputTag, "kai")
        tapText("忘记密码？")
        compose.pumpAndSettle()

        compose.onNodeWithText("邮箱验证").assertExists()
        compose.onNodeWithText("重新发送(42)").assertExists()
        assertEquals(listOf("/api/v1/auth/password-reset/inspect"), requestedPaths)

        tapText("取消")
        compose.pumpAndSettle()
        assertFalse(exists("邮箱验证"))
    }

    @Test
    fun `forgot password opens verification when cooldown inspection omits challenge`() {
        val requestedPaths = mutableListOf<String>()
        val controller =
            passwordResetController(
                { path ->
                    when (path) {
                        "/api/v1/auth/password-reset/inspect" ->
                            jsonResponse(mapOf("can_send" to false, "masked_email" to "k***@example.test", "retry_after" to 42))
                        "/api/v1/auth/password-reset/start" ->
                            jsonResponse(mapOf("challenge_id" to "challenge-existing", "masked_email" to "k***@example.test", "retry_after" to 42))
                        else -> HttpResponse("unexpected request", 404)
                    }
                },
                requestedPaths,
            )
        pumpLoginPage(sizeForMode = fixedSize(DpSize(430.dp, 368.dp)), passwordResetController = controller)
        compose.enterText(authIdentityInputTag, "kai")
        tapText("忘记密码？")
        compose.pumpAndSettle()

        compose.onNodeWithTag("password-reset-code-input").assertExists()
        compose.onNodeWithText("重新发送(42)").assertExists()
        assertFalse(exists("验证码已发送，请在 42 秒后重试"))
        assertEquals(listOf("/api/v1/auth/password-reset/inspect"), requestedPaths)
    }

    @Test
    fun `auth account history expands, selects and deletes records`() {
        val store =
            MemoryLoginAccountHistoryStore(
                listOf(
                    LoginAccountRecord(
                        login = "kai",
                        password = "secret123",
                        avatarUrl = "/assets/avatar-kai/custom.png",
                        defaultAvatarKey = "green-2",
                        useCount = 3,
                        updatedAt = DateTime.utc(2026, 1, 1),
                    ),
                    LoginAccountRecord(login = "morgan", useCount = 1, updatedAt = DateTime.utc(2026, 1, 2)),
                ),
            )
        pumpLoginPage(sizeForMode = fixedSize(DpSize(430.dp, 368.dp)), accountHistoryStore = store)
        compose.pump()

        assertEquals("morgan", compose.field(authIdentityInputTag).fieldText())
        assertEquals("", compose.field(authPasswordInputTag).fieldText())
        assertFalse(rememberPassword().isToggledOn())
        assertEquals(1, tooltipCount("清除账号"))

        tapTooltip("展开账号记录")

        inDropdown("kai").assertExists()
        inDropdown("morgan").assertExists()
        val avatars = compose.onAllNodes(hasTestTag(authAccountHistoryAvatarTag), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(2, avatars.size)
        assertEquals("green-2", avatars.first().config[AccountHistoryAvatarDefaultKey])
        assertEquals("http://127.0.0.1:21116/assets/avatar-kai/custom.png", avatars.first().config[AccountHistoryAvatarImageUrlKey])
        assertTrue(inDropdown("kai").rect().top < inDropdown("morgan").rect().top)
        assertEquals(2, tooltipCount("删除账号记录"))

        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(8.dp.toPx(), 8.dp.toPx())) }
        compose.pump()

        assertEquals(0, tooltipCount("收起账号记录"))
        assertEquals(1, tooltipCount("展开账号记录"))

        tapTooltip("清除账号")

        assertEquals("", compose.field(authIdentityInputTag).fieldText())
        assertEquals("", compose.field(authPasswordInputTag).fieldText())
        assertFalse(rememberPassword().isToggledOn())
        assertEquals(0, tooltipCount("清除账号"))

        tapTooltip("展开账号记录")

        inDropdown("kai").performClick()
        compose.pump()

        assertEquals("kai", compose.field(authIdentityInputTag).fieldText())
        assertEquals("secret123", compose.field(authPasswordInputTag).fieldText())
        assertEquals(0, tooltipCount("收起账号记录"))

        tapTooltip("展开账号记录")
        compose.onAllTooltips("删除账号记录")[1].performClick()
        compose.pump()

        assertFalse(exists("morgan"))
        assertEquals(listOf("kai"), store.records.map { it.login })
    }

    @Test
    fun `auth account history overlays password field and scrolls itself`() {
        val store =
            MemoryLoginAccountHistoryStore(
                (0 until 6).map { index ->
                    LoginAccountRecord(login = "account-$index", useCount = index, updatedAt = DateTime.utc(2026, 1, index + 1))
                },
            )
        pumpLoginPage(sizeForMode = fixedSize(DpSize(430.dp, 368.dp)), accountHistoryStore = store)
        compose.pump()

        val passwordTopBefore = compose.field(authPasswordInputTag).rect().top

        tapTooltip("展开账号记录")

        val passwordRect = compose.field(authPasswordInputTag).rect()
        val dropdownRect = compose.onNodeWithTag(authAccountHistoryDropdownTag).rect()

        assertDpEquals(passwordTopBefore, compose.field(authPasswordInputTag).rect().top)
        assertTrue(dropdownRect.top <= passwordRect.top + 0.01.dp)
        assertTrue(dropdownRect.bottom > passwordRect.bottom)
        assertDpEquals(38.dp * 4, dropdownRect.bottom - dropdownRect.top)
        // Scrollbar(thumbVisibility: true, trackVisibility: false).
        assertEquals(1, tagged(authAccountHistoryScrollbarTag))

        compose.onNodeWithTag(authAccountHistoryDropdownTag).performTouchInput { swipeUp() }
        compose.pumpAndSettle()
        inDropdown("account-5").assertIsDisplayed()
    }

    @Test
    fun `auth fills remembered password for latest login`() {
        val store =
            MemoryLoginAccountHistoryStore(
                listOf(
                    LoginAccountRecord(login = "kai", useCount = 4, updatedAt = DateTime.utc(2026, 1, 1)),
                    LoginAccountRecord(login = "morgan", password = "moonbase", useCount = 1, updatedAt = DateTime.utc(2026, 1, 2)),
                ),
            )
        pumpLoginPage(sizeForMode = fixedSize(DpSize(430.dp, 368.dp)), accountHistoryStore = store)
        compose.pump()

        assertEquals("morgan", compose.field(authIdentityInputTag).fieldText())
        assertEquals("moonbase", compose.field(authPasswordInputTag).fieldText())
        assertTrue(rememberPassword().isToggledOn())
    }

    @Test
    fun `auth keeps password when editing the login field`() {
        val store =
            MemoryLoginAccountHistoryStore(
                listOf(LoginAccountRecord(login = "morgan", password = "moonbase", useCount = 1, updatedAt = DateTime.utc(2026, 1, 2))),
            )
        pumpLoginPage(sizeForMode = fixedSize(DpSize(430.dp, 368.dp)), accountHistoryStore = store)
        compose.pump()

        compose.enterText(authIdentityInputTag, "morgan@mail.test")
        compose.pump()

        assertEquals("moonbase", compose.field(authPasswordInputTag).fieldText())
        assertFalse(rememberPassword().isToggledOn())
    }

    @Test
    fun `remember password stores successful login locally`() {
        val store = MemoryLoginAccountHistoryStore()
        var submittedRememberPassword: Boolean? = null
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 368.dp)),
            accountHistoryStore = store,
            onSubmit = { _, rememberPassword -> submittedRememberPassword = rememberPassword },
        )

        compose.enterText(authIdentityInputTag, "kai")
        compose.enterText(authPasswordInputTag, "secret123")
        compose.onNodeWithTag(authRememberPasswordHotZoneTag).performClick()
        compose.pump()
        tapSubmit()
        compose.pumpAndSettle()

        assertEquals(true, submittedRememberPassword)
        assertEquals(1, store.records.size)
        assertEquals("kai", store.records.single().login)
        assertEquals("secret123", store.records.single().password)
    }

    @Test
    fun `unchecked remember password stays visually unchecked while busy`() {
        val submitCompleter = CompletableDeferred<Unit>()
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 368.dp)),
            onSubmit = { _, rememberPassword ->
                assertFalse(rememberPassword)
                submitCompleter.await()
            },
        )

        compose.enterText(authIdentityInputTag, "kai")
        compose.enterText(authPasswordInputTag, "secret123")
        tapSubmit()

        assertFalse(rememberPassword().isToggledOn())
        // The check glyph stays transparent: no lime fill anywhere in the box.
        val pixels = compose.onNodeWithTag(authRememberPasswordHotZoneTag).captureToImage().toPixelMap()
        var limePixels = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                if (color.green > 0.6f && color.blue < 0.4f) limePixels += 1
            }
        }
        assertEquals(0, limePixels)

        submitCompleter.complete(Unit)
        compose.pumpAndSettle()
    }

    /**
     * The Android-relevant part of the Dart case. Not ported: Ctrl+A select
     * all and the right-click (secondary mouse button) context menus, which
     * the UI kit's Input tests cover.
     */
    @Test
    fun `auth inputs support selection cursor placement and context menus`() {
        val emailVerification = FakeEmailVerification()
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 500.dp)),
            checkUsernameAvailability = { true },
            checkEmailAvailability = { true },
            emailVerificationController = emailVerification.controller,
        )

        val loginFieldTop = compose.field(authIdentityInputTag).rect().top
        val loginText = "logan@example.test"
        compose.enterText(authIdentityInputTag, loginText)

        tapText("记住密码")
        assertEquals(0, tooltipCount("记住密码"))
        assertTrue(rememberPassword().isToggledOn())

        tapRegisterMode()
        compose.pumpAndSettle()
        var emailVerificationAction = compose.onAllNodes(hasTestTag("auth-email-verification-button"))
        assertEquals(0, emailVerificationAction.fetchSemanticsNodes().size)
        assertFalse(exists("验证"))
        compose.enterText(authEmailInputTag, "register@example.test")
        compose.pump()
        val action = compose.onNodeWithTag("auth-email-verification-button")
        action.assertExists()
        compose.onNodeWithText("验证").assertExists()
        val emailFieldRect = compose.field(authEmailInputTag).rect()
        val emailActionRect = action.rect()
        assertTrue(emailActionRect.centerX > emailFieldRect.centerX)
        assertTrue(emailActionRect.right <= compose.onNodeWithTag(authEmailInputTag).rect().right)
        action.assertIsEnabled()
        assertDpEquals(loginFieldTop, compose.field(authIdentityInputTag).rect().top)
        assertEquals(loginText, compose.field(authIdentityInputTag).fieldText())

        compose.enterText(authIdentityInputTag, "logan.test")
        compose.pump()
        assertEquals(1, tagged("auth-username-invalid"))
        assertEquals(1, tooltipCount("登录用户名需为 3-32 位，只能包含英文字母、数字、下划线或连字符"))

        compose.enterText(authEmailInputTag, "invalid-email")
        compose.pump()
        action.assertIsEnabled()
        action.performClick()
        compose.pump()
        compose.onNodeWithText("请输入有效的邮箱地址").assertExists()
        assertFalse(exists("邮箱验证"))

        compose.enterText(authIdentityInputTag, "logan_01")
        compose.enterText(authEmailInputTag, "logan@example.test")
        compose.pump()
        assertEquals(1, tagged("auth-username-checking"))
        action.assertIsEnabled()

        action.performClick()
        compose.pumpAndSettle()
        compose.onNodeWithText("邮箱验证").assertExists()
        compose.onNodeWithText("已发送验证码到您的邮箱 logan@example.test").assertExists()
        compose.onNodeWithText("重新发送(60)").assertExists()
        compose.onNodeWithTag("auth-email-send-code-button").assertIsNotEnabled()
        assertEquals(listOf("inspect:logan@example.test", "start:logan@example.test"), emailVerification.calls)

        compose.enterText("auth-email-verification-code", "123456")
        assertEquals("123456", compose.field("auth-email-verification-code").fieldText())
        emailVerificationAction = compose.onAllNodes(hasTestTag("auth-email-verification-button"))
        assertEquals(1, emailVerificationAction.fetchSemanticsNodes().size)
    }

    @Test
    fun `email verification reports a duplicate before opening dialog`() {
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 500.dp)),
            checkUsernameAvailability = { true },
            checkEmailAvailability = { false },
        )
        tapRegisterMode()
        compose.enterText(authEmailInputTag, "taken@example.test")
        compose.pump()

        compose.onNodeWithTag("auth-email-verification-button").performClick()
        compose.pump()

        compose.onNodeWithText("该邮箱已被其他用户使用").assertExists()
        assertFalse(exists("邮箱验证"))
    }

    @Test
    fun `email verification shows checking icon while request is pending`() {
        val availability = CompletableDeferred<Boolean>()
        val emailVerification = FakeEmailVerification()
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 500.dp)),
            checkUsernameAvailability = { true },
            checkEmailAvailability = { availability.await() },
            emailVerificationController = emailVerification.controller,
        )
        tapRegisterMode()
        compose.pumpAndSettle()
        compose.enterText(authEmailInputTag, "logan@example.test")
        compose.pump()

        compose.onNodeWithTag("auth-email-verification-button").performClick()
        compose.pump()

        assertEquals(1, tagged("auth-email-checking"))
        assertEquals(1, tooltipCount("正在检测邮箱是否可用"))
        assertEquals(0, tagged("auth-email-verification-button"))

        availability.complete(true)
        compose.pumpAndSettle()
        assertEquals(0, tagged("auth-email-checking"))
        compose.onNodeWithText("邮箱验证").assertExists()
    }

    @Test
    fun `email verification inspects and inherits server cooldown`() {
        val emailVerification =
            FakeEmailVerification(
                onInspect = { _ ->
                    EmailVerificationInspection(canSend = false, challengeId = "existing-challenge", retryAfterSeconds = 42)
                },
            )
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 500.dp)),
            checkUsernameAvailability = { true },
            checkEmailAvailability = { true },
            emailVerificationController = emailVerification.controller,
        )
        tapRegisterMode()
        compose.pumpAndSettle()
        compose.enterText(authEmailInputTag, "first@example.test")
        compose.pump()
        compose.onNodeWithTag("auth-email-verification-button").performClick()
        compose.pumpAndSettle()

        compose.onNodeWithText("重新发送(42)").assertExists()
        assertEquals(listOf("inspect:first@example.test"), emailVerification.calls)
        compose.onNodeWithText("已发送验证码到您的邮箱 first@example.test").assertExists()
    }

    @Test
    fun `email verification opens when cooldown inspection omits challenge`() {
        val emailVerification =
            FakeEmailVerification(
                onInspect = { _ -> EmailVerificationInspection(canSend = false, retryAfterSeconds = 42) },
                onStart = { _ -> EmailVerificationChallenge(id = "existing-challenge", retryAfterSeconds = 42) },
            )
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 500.dp)),
            checkUsernameAvailability = { true },
            checkEmailAvailability = { true },
            emailVerificationController = emailVerification.controller,
        )
        tapRegisterMode()
        compose.pumpAndSettle()
        compose.enterText(authEmailInputTag, "first@example.test")
        compose.pump()
        compose.onNodeWithTag("auth-email-verification-button").performClick()
        compose.pumpAndSettle()

        compose.onNodeWithTag("auth-email-verification-code").assertExists()
        compose.onNodeWithText("重新发送(42)").assertExists()
        assertEquals(listOf("inspect:first@example.test", "start:first@example.test"), emailVerification.calls)
    }

    @Test
    fun `register checks email verification before username submit`() {
        val checkedUsernames = mutableListOf<String>()
        var submissions = 0
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 500.dp)),
            checkUsernameAvailability = { username ->
                checkedUsernames.add(username)
                username != "taken_name"
            },
            onSubmit = { _, _ -> submissions += 1 },
        )
        tapRegisterMode()

        compose.enterText(authIdentityInputTag, "taken_name")
        compose.pump(350)
        compose.pump()
        assertEquals(1, tooltipCount("该登录用户名已被其他用户使用"))

        compose.enterText(authEmailInputTag, "taken@example.test")
        compose.enterText(authPasswordInputTag, "secret123")
        compose.enterText(authConfirmPasswordInputTag, "secret123")
        tapSubmit()
        compose.pumpAndSettle()
        assertEquals(0, submissions)
        compose.onNodeWithText("请先验证邮箱").assertExists()

        compose.enterText(authIdentityInputTag, "available_name")
        compose.pump(350)
        compose.pump()
        assertEquals(1, tagged("auth-username-valid"))
        tapSubmit()
        compose.pumpAndSettle()
        assertEquals(0, submissions)
        compose.onNodeWithText("请先验证邮箱").assertExists()
        assertTrue(checkedUsernames.containsAll(listOf("taken_name", "available_name")))
    }

    private fun tapDialogVerify() {
        compose.onNode(
            hasText("验证") and hasClickAction() and hasAnyAncestor(hasTestTag(emailVerificationDialogTag)),
        ).performClick()
        compose.pumpAndSettle()
    }

    @Test
    fun `register submits the server email verification token`() {
        val emailVerification = FakeEmailVerification()
        var submitted: AuthRequest? = null
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 500.dp)),
            checkUsernameAvailability = { true },
            checkEmailAvailability = { true },
            emailVerificationController = emailVerification.controller,
            onSubmit = { request, _ -> submitted = request },
        )
        tapRegisterMode()
        compose.pumpAndSettle()
        compose.enterText(authIdentityInputTag, "verified_user")
        compose.enterText(authEmailInputTag, "verified@example.test")
        compose.pump()
        compose.enterText(authPasswordInputTag, "secret123")
        compose.enterText(authConfirmPasswordInputTag, "secret123")

        compose.onNodeWithTag("auth-email-verification-button").performClick()
        compose.pumpAndSettle()
        compose.enterText("auth-email-verification-code", "123456")
        tapDialogVerify()
        assertEquals(1, tagged("auth-email-verified"))
        assertEquals(1, tooltipCount("邮箱已验证"))
        assertEquals(0, tagged("auth-email-verification-button"))
        tapSubmit()
        compose.pumpAndSettle()

        assertNotNull(submitted)
        assertEquals("email-verification-token", submitted?.emailVerificationToken)
        assertEquals(
            listOf("inspect:verified@example.test", "start:verified@example.test", "verify:email-challenge:123456"),
            emailVerification.calls,
        )
    }

    @Test
    fun `editing a verified registration email restores verify action`() {
        val emailVerification = FakeEmailVerification()
        pumpLoginPage(
            sizeForMode = fixedSize(DpSize(430.dp, 500.dp)),
            checkUsernameAvailability = { true },
            checkEmailAvailability = { true },
            emailVerificationController = emailVerification.controller,
        )
        tapRegisterMode()
        compose.pumpAndSettle()
        compose.enterText(authEmailInputTag, "verified@example.test")
        compose.pump()
        compose.onNodeWithTag("auth-email-verification-button").performClick()
        compose.pumpAndSettle()
        compose.enterText("auth-email-verification-code", "123456")
        tapDialogVerify()
        assertEquals(1, tagged("auth-email-verified"))

        compose.enterText(authEmailInputTag, "changed@example.test")
        compose.pump()

        assertEquals(0, tagged("auth-email-verified"))
        assertEquals(1, tagged("auth-email-verification-button"))

        compose.enterText(authEmailInputTag, "")
        compose.pump()
        assertEquals(0, tagged("auth-email-verification-button"))
    }

    // Port-specific checks.

    @Test
    @Config(qualifiers = "w800dp-h600dp-420dpi")
    fun `brand icon is a 36dp vector at a density between the old PNG variants`() {
        pumpLoginPage()

        // 2.625 sat between the 2.0x and 3.0x PNGs, which Flutter scaled with
        // nearest-neighbour filtering; the vector is drawn at the exact size.
        val pixels = compose.onNodeWithTag("auth-brand-icon").captureToImage().toPixelMap()
        assertEquals(95, pixels.width)
        assertEquals(95, pixels.height)
        assertEquals(0xFFCCF43A.toInt(), pixels[2, 2].toArgb())
        // The middle level bar of the speech bubble.
        assertEquals(0xFF0A0B05.toInt(), pixels[47, 50].toArgb())
    }
}

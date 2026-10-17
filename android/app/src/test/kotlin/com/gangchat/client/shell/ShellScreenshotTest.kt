package com.gangchat.client.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.LoginAccountRecord
import com.gangchat.client.app.PasswordResetController
import com.gangchat.client.app.ReleaseAsset
import com.gangchat.client.auth.AuthClient
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.testing.MockClient
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.segmentedControlSegmentTag
import com.gangchat.client.ui.setUiContent
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
private const val tablet = "w800dp-h1280dp-xhdpi"
private const val tabletLandscape = "w1280dp-h800dp-xhdpi"

/** Roborazzi captures of the login page, its dialogs and the update page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = phone, shadows = [ShadowNoOpMagnifier::class])
class ShellScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val loop = ShellTestLoop()

    @Before
    fun setUp() = loop.start()

    @After
    fun tearDown() = loop.finish()

    private fun history() =
        MemoryLoginAccountHistoryStore(
            listOf(
                LoginAccountRecord(login = "logan", password = "secret123", defaultAvatarKey = "green-2", useCount = 5, updatedAt = DateTime.utc(2026, 7, 2)),
                LoginAccountRecord(login = "morgan@example.com", useCount = 2, updatedAt = DateTime.utc(2026, 6, 30)),
                LoginAccountRecord(login = "kai_01", password = "moonbase", useCount = 1, updatedAt = DateTime.utc(2026, 6, 1)),
            ),
        )

    private fun pumpLogin(
        emailVerification: FakeEmailVerification? = null,
        passwordReset: PasswordResetController? = null,
        store: MemoryLoginAccountHistoryStore = history(),
    ) {
        compose.mainClock.autoAdvance = false
        compose.setUiContent(notifications = true) { _ ->
            LoginPage(
                onSubmit = { _, _ -> },
                checkUsernameAvailability = { username -> username != "taken_name" },
                checkEmailAvailability = { true },
                emailVerificationController = emailVerification?.controller,
                passwordResetController = passwordReset,
                accountHistoryStore = store,
            )
        }
        compose.pump()
        compose.pump()
    }

    private fun capture(name: String) {
        compose.pump(400)
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    private fun loginAndHistory(prefix: String) {
        pumpLogin()
        capture("${prefix}_login")
        compose.onTooltip("展开账号记录").performClick()
        capture("${prefix}_login_history")
    }

    private fun register(prefix: String) {
        pumpLogin()
        compose.onNodeWithTag(segmentedControlSegmentTag(1)).performClick()
        compose.pump()
        compose.enterText(authIdentityInputTag, "logan_01")
        compose.enterText(authEmailInputTag, "logan@example.com")
        compose.enterText(authPasswordInputTag, "secret123")
        compose.pump(400)
        capture("${prefix}_register")
        compose.onNodeWithTag(authSubmitButtonTag).performClick()
        capture("${prefix}_register_error")
    }

    @Test
    fun phoneLogin() = loginAndHistory("shell_phone")

    @Test
    fun phoneRegister() = register("shell_phone")

    @Test
    @Config(qualifiers = tablet)
    fun tabletLogin() = loginAndHistory("shell_tablet")

    @Test
    @Config(qualifiers = tablet)
    fun tabletRegister() = register("shell_tablet")

    @Test
    @Config(qualifiers = tabletLandscape)
    fun tabletLandscapeLogin() = loginAndHistory("shell_tablet_landscape")

    @Test
    fun phoneEmailVerificationDialog() {
        val emailVerification = FakeEmailVerification()
        pumpLogin(emailVerification = emailVerification)
        compose.onNodeWithTag(segmentedControlSegmentTag(1)).performClick()
        compose.pump()
        compose.enterText(authIdentityInputTag, "logan_01")
        compose.enterText(authEmailInputTag, "logan@example.com")
        compose.onNodeWithTag("auth-email-verification-button").performClick()
        capture("shell_phone_email_verification")
    }

    @Test
    fun phonePasswordResetDialogs() {
        val controller =
            PasswordResetController(
                apiBaseUrl = "https://api.example.test/api/v1",
                authClientFactory = { baseUrl ->
                    AuthClient(
                        baseUrl = baseUrl,
                        httpClient =
                            MockClient { request ->
                                when (request.url.path) {
                                    "/api/v1/auth/password-reset/inspect" ->
                                        jsonResponse(mapOf("can_send" to true, "masked_email" to "l***@example.com", "retry_after" to 0))
                                    "/api/v1/auth/password-reset/start" ->
                                        jsonResponse(mapOf("challenge_id" to "challenge-1", "masked_email" to "l***@example.com", "retry_after" to 60))
                                    "/api/v1/auth/password-reset/verify" -> jsonResponse(mapOf("reset_token" to "reset-token"))
                                    else -> HttpResponse("unexpected request", 404)
                                }
                            },
                    )
                },
            )
        pumpLogin(passwordReset = controller)
        compose.onNodeWithText("忘记密码？").performClick()
        compose.pump()
        compose.enterText("password-reset-code-input", "123456")
        capture("shell_phone_password_reset_verify")
        compose.onNodeWithTag("password-reset-verify-button").performClick()
        compose.pump()
        compose.enterText("password-reset-new-password", "new-password")
        capture("shell_phone_password_reset")
    }

    private val update =
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
            releaseNotes = "修复语音频道偶发断线\n优化消息列表滚动",
        )

    private fun pumpUpdatePage(downloading: Boolean = false, error: String? = null) {
        compose.mainClock.autoAdvance = false
        compose.setUiContent { _ ->
            AppUpdatePage(
                update = update,
                checking = false,
                downloading = downloading,
                downloadedBytes = 42_000_000,
                downloadTotalBytes = if (downloading) 100_000_000 else null,
                error = error,
                wrapInScaffold = true,
                onBack = {},
                onRefresh = {},
                onIgnoreVersion = {},
                onDownload = {},
            )
        }
        compose.pump()
    }

    @Test
    fun phoneUpdatePage() {
        pumpUpdatePage()
        capture("shell_phone_update")
    }

    @Test
    fun phoneUpdatePageDownloading() {
        pumpUpdatePage(downloading = true, error = "下载或启动安装程序失败")
        capture("shell_phone_update_downloading")
    }

    @Test
    @Config(qualifiers = tablet)
    fun tabletUpdatePage() {
        pumpUpdatePage()
        capture("shell_tablet_update")
    }
}

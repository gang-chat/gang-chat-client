package com.gangchat.client.shell

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.AuthClientFactory
import com.gangchat.client.app.AuthenticatedAppContext
import com.gangchat.client.app.LoginAccountRecord
import com.gangchat.client.app.RealtimeService
import com.gangchat.client.auth.AuthClient
import com.gangchat.client.auth.TokenStore
import com.gangchat.client.config.AppConfig
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.utf8Decode
import com.gangchat.client.home.FakeHomeAudioDeviceStore
import com.gangchat.client.home.FakeHomeLiveSession
import com.gangchat.client.home.FakeHomeLiveSessionController
import com.gangchat.client.home.FakeHomeRealtimeService
import com.gangchat.client.home.HomePage
import com.gangchat.client.home.HomeShellTestApi
import com.gangchat.client.home.HomeShellTestBase
import com.gangchat.client.home.NoopRealtimeService
import com.gangchat.client.home.RecordingLivePresenceSoundPlayer
import com.gangchat.client.home.RecordingLivePresenceSpeechPlayer
import com.gangchat.client.home.RecordingMessageNotificationSoundPlayer
import com.gangchat.client.home.exists
import com.gangchat.client.home.homeShellCurrentUserJson
import com.gangchat.client.home.homeShellJsonResponse
import com.gangchat.client.home.pumpShell
import com.gangchat.client.home.settleShell
import com.gangchat.client.home.textNodes
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.settings.MemorySettingsLanguageStore
import com.gangchat.client.settings.settingsDeleteAccountInputTag
import com.gangchat.client.testing.MockClient
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.onAllTooltips
import com.gangchat.client.ui.segmentedControlSegmentTag
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val apiBaseUrl = "http://example.test/api/v1"

/**
 * The app root: the cases of `test/widget/shell/gang_app_shell_test_parts/
 * {auth_widget_tests,auth_smoke_widget_tests}.dart` that pump `GangApp`, and
 * the auth gate's flows the Dart suite left to manual testing (the stored
 * session restore and its launch screen, login, logout, session expiry,
 * account deletion and the remembered language), with a fake auth server and
 * the home shell's fake API behind the real [HomePage].
 *
 * Like the Dart suite these run on an 800 x 600 window; Flutter's default
 * test platform is Android, so the Dart cases already saw the Android
 * arguments `GangApp` gives the login page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-mdpi", shadows = [ShadowNoOpMagnifier::class])
class GangAppTest : HomeShellTestBase() {
    /** A config whose release check fails at once (nothing listens on port 9). */
    private val config = AppConfig(apiBaseUrl = apiBaseUrl, assetBaseUrl = "http://example.test", releaseBucketUrl = "http://127.0.0.1:9")

    private fun pumpApp(
        tokenStore: TokenStore = MemoryTokenStore(),
        startsAuthenticated: Boolean = false,
        languageStore: MemorySettingsLanguageStore = MemorySettingsLanguageStore(),
        historyStore: MemoryLoginAccountHistoryStore = MemoryLoginAccountHistoryStore(),
        auth: FakeAuthServer? = null,
        home: TestHome? = null,
        showInitialWindow: (() -> Unit)? = null,
    ) {
        compose.setContent {
            GangApp(
                tokenStore = tokenStore,
                config = config,
                startsAuthenticated = startsAuthenticated,
                languageStore = languageStore,
                loginAccountHistoryStore = historyStore,
                showInitialWindow = showInitialWindow,
                authClientFactory = auth?.factory,
                homePage = home?.builder,
            )
        }
        compose.settleShell()
    }

    private fun tooltipCount(message: String) = compose.onAllTooltips(message).fetchSemanticsNodes().size

    private fun exists(text: String) = compose.textNodes(text) > 0

    private fun submitButton(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(authSubmitButtonTag)))

    private fun submitBottomGap() = tag(authSurfaceTag).rect().bottom - tag(authSubmitButtonTag).rect().bottom

    /** Dart's `_expectSubmitButtonFullWidth`. */
    private fun assertSubmitButtonFullWidth() {
        val input = tag(authIdentityInputTag).rect()
        val submit = tag(authSubmitButtonTag).rect()
        assertDpEquals(input.left, submit.left)
        assertDpEquals(input.right, submit.right)
    }

    private fun tapSubmit() = tapTag(authSubmitButtonTag)

    private fun field(tag: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun enter(tag: String, text: String) {
        field(tag).performTextReplacement(text)
        compose.settleShell(frames = 2)
    }

    private fun logIn(login: String = "kai", password: String = "secret-password") {
        enter(authIdentityInputTag, login)
        enter(authPasswordInputTag, password)
        tapSubmit()
        compose.settleShell()
    }

    private fun loginPageShown() = compose.exists(hasTestTag(authSurfaceTag))

    private fun loadingPageShown() = compose.exists(hasTestTag(gangAppLoadingPageTag))

    private fun homeShown() = exists("Alpha Room")

    /** The home shell's logout button and its confirmation. */
    private fun logOutFromHome() {
        tapTooltip("退出登录")
        compose.onAllNodes(hasText("退出登录") and hasClickAction()).onLast().performClick()
        compose.settleShell()
    }

    // --- auth_widget_tests.dart ----------------------------------------------

    @Test
    fun `app renders login entrypoint on real auth gate`() {
        pumpApp()

        assertEquals(1, compose.textNodes("Gang Chat"))
        compose.onNode(hasText("登录用户名或邮箱地址")).assertExists()
        compose.onNode(hasText("密码")).assertExists()
        submitButton("登录").assertExists()
        compose.onNode(hasText("注册")).assertExists()
        compose.onNode(hasText("记住密码")).assertExists()
        compose.onNode(hasText("忘记密码？")).assertExists()
        assertEquals(1, compose.onAllNodes(hasToggleableState).fetchSemanticsNodes().size)
        assertEquals(1, tooltipCount("显示密码"))

        val authSurfaceRect = tag(authSurfaceTag).rect()
        val modeSwitchRect = tag(authModeSwitchTag).rect()
        val titleRect = compose.onNode(hasText("Gang Chat")).rect()
        assertTrue(titleRect.bottom - titleRect.top >= 20.dp)
        assertTrue(titleRect.bottom - titleRect.top <= 28.dp)
        assertTrue(tagExists("auth-brand-icon"))
        assertDpEquals(82.dp, modeSwitchRect.top - authSurfaceRect.top)
        assertSubmitButtonFullWidth()
        assertFalse(exists("请输入账号和密码后继续"))
        val normalSurfaceHeight = authSurfaceRect.bottom - authSurfaceRect.top
        val normalBottomGap = submitBottomGap()
        val normalSubmitRect = tag(authSubmitButtonTag).rect()
        val normalRememberRect = compose.onNode(hasText("记住密码")).rect()
        assertTrue(normalBottomGap >= 34.dp)

        tapText("忘记密码？")

        tapSubmit()

        assertTrue(exists("请输入账号和密码后继续"))
        val surface = tag(authSurfaceTag).rect()
        assertDpEquals(normalSurfaceHeight, surface.bottom - surface.top)
        assertDpEquals(normalBottomGap, submitBottomGap())
        assertRectCloseTo(tag(authSubmitButtonTag).rect(), normalSubmitRect)
        assertRectCloseTo(compose.onNode(hasText("记住密码")).rect(), normalRememberRect)
        val passwordRect = field(authPasswordInputTag).rect()
        val errorRect = compose.onNode(hasText("请输入账号和密码后继续")).rect()
        val rememberRect = compose.onNode(hasText("记住密码")).rect()
        assertTrue(errorRect.top > passwordRect.bottom)
        assertTrue(errorRect.bottom < rememberRect.top)
    }

    @Test
    fun `register mode exposes full auth form`() {
        pumpApp()

        assertTrue(submitBottomGap() >= 34.dp)

        tapTag(segmentedControlSegmentTag(1))

        compose.onNode(hasText("登录用户名")).assertExists()
        compose.onNode(hasText("邮箱地址")).assertExists()
        compose.onNode(hasText("密码")).assertExists()
        compose.onNode(hasText("确认密码")).assertExists()
        assertEquals(2, tooltipCount("显示密码"))
        submitButton("创建账号").assertExists()
        assertTrue(submitBottomGap() >= 34.dp)
        assertSubmitButtonFullWidth()
        assertFalse(exists("请输入账号和密码后继续"))
        val surface = tag(authSurfaceTag).rect()
        val normalSurfaceHeight = surface.bottom - surface.top

        compose.onAllTooltips("显示密码")[0].performClick()
        compose.settleShell()

        assertEquals(1, tooltipCount("隐藏密码"))
        assertEquals(1, tooltipCount("显示密码"))

        tapSubmit()

        assertTrue(exists("请输入账号和密码后继续"))
        val after = tag(authSurfaceTag).rect()
        assertDpEquals(normalSurfaceHeight, after.bottom - after.top)
    }

    // --- auth_smoke_widget_tests.dart -----------------------------------------

    /**
     * Dart pumped a second `GangApp` with a new token store and also compared
     * the theme's cursor colour; here the second configuration recomposes the
     * same root, and the cursor colour is the constant `uiTheme()` value.
     */
    @Test
    fun `auth login layout is stable across rebuilds`() {
        var generation by mutableIntStateOf(0)
        compose.setContent {
            // A new token store on every rebuild, like pumping a new GangApp.
            val tokenStore = MemoryTokenStore(label = "generation-$generation")
            GangApp(tokenStore = tokenStore, config = config)
        }
        compose.settleShell()

        val regularSurfaceRect = tag(authSurfaceTag).rect()
        val regularModeSwitchRect = tag(authModeSwitchTag).rect()
        val regularInputRect = tag(authIdentityInputTag).rect()
        val regularSubmitRect = tag(authSubmitButtonTag).rect()

        generation += 1
        compose.settleShell()

        assertRectCloseTo(tag(authSurfaceTag).rect(), regularSurfaceRect)
        assertRectCloseTo(tag(authModeSwitchTag).rect(), regularModeSwitchRect)
        assertRectCloseTo(tag(authIdentityInputTag).rect(), regularInputRect)
        assertRectCloseTo(tag(authSubmitButtonTag).rect(), regularSubmitRect)
    }

    /** Dart also checked that no app-wide `SelectionArea` exists; Android has none. */
    @Test
    fun `app renders auth entrypoint`() {
        pumpApp()

        assertTrue(compose.textNodes("Gang Chat") >= 1)
        compose.onNode(hasText("登录用户名或邮箱地址")).assertExists()
        compose.onNode(hasText("密码")).assertExists()
        assertEquals(1, tooltipCount("显示密码"))
        submitButton("登录").assertExists()

        tapTooltip("显示密码")

        assertEquals(1, tooltipCount("隐藏密码"))
    }

    @Test
    fun `switching to register reveals additional fields`() {
        pumpApp()

        tapText("注册")

        compose.onNode(hasText("登录用户名")).assertExists()
        compose.onNode(hasText("邮箱地址")).assertExists()
        compose.onNode(hasText("确认密码")).assertExists()
        assertEquals(2, tooltipCount("显示密码"))
        submitButton("创建账号").assertExists()

        compose.onAllTooltips("显示密码")[0].performClick()
        compose.settleShell()

        assertEquals(1, tooltipCount("隐藏密码"))
        assertEquals(1, tooltipCount("显示密码"))
    }

    @Test
    fun `submitting empty form surfaces inline error`() {
        pumpApp()

        tapSubmit()

        assertTrue(exists("请输入账号和密码后继续"))
    }

    // --- The auth gate (port-specific) ----------------------------------------

    @Test
    fun `fresh start shows the login page and reveals the window after restore`() {
        val tokenStore = MemoryTokenStore()
        var reveals = 0
        pumpApp(tokenStore = tokenStore, showInitialWindow = { reveals++ })

        assertTrue(loginPageShown())
        assertFalse(loadingPageShown())
        assertEquals(1, reveals)
        assertEquals(1, tokenStore.reads)

        // The 4 s safety net only exists for a stored session.
        repeat(5) { compose.settleShell() }
        assertEquals(1, reveals)
    }

    @Test
    fun `login opens home and logout returns to the login page`() {
        val auth = FakeAuthServer()
        val tokenStore = MemoryTokenStore()
        val historyStore = MemoryLoginAccountHistoryStore()
        val home = TestHome()
        pumpApp(tokenStore = tokenStore, historyStore = historyStore, auth = auth, home = home)

        compose.onNode(hasToggleableState and hasAnyAncestor(hasTestTag(authRememberPasswordHotZoneTag))).performClick()
        compose.settleShell(frames = 2)
        logIn()

        assertTrue(homeShown())
        assertFalse(loginPageShown())
        assertEquals(listOf("POST /api/v1/auth/login"), auth.requests)
        assertEquals(mapOf("login" to "kai", "password" to "secret-password"), auth.bodies.single())
        assertEquals("refresh-1", tokenStore.refreshToken)
        assertEquals(apiBaseUrl, tokenStore.apiBaseUrl)
        assertTrue(home.api.requestedPaths.contains("/api/v1/rooms"))
        val app = home.apps.last()
        assertEquals("kai", app.currentUser.username)
        assertEquals(apiBaseUrl, app.apiBaseUrl)
        // The gate remembers the account with the signed-in avatar.
        val record = historyStore.records.single()
        assertEquals("kai", record.login)
        assertEquals("secret-password", record.password)
        assertNull(record.avatarUrl)
        assertEquals("blue-3", record.defaultAvatarKey)

        logOutFromHome()

        assertTrue(loginPageShown())
        assertFalse(homeShown())
        assertNull(tokenStore.refreshToken)
        assertEquals(listOf("POST /api/v1/auth/login", "POST /api/v1/auth/logout"), auth.requests)
        assertEquals(mapOf("refresh_token" to "refresh-1"), auth.bodies.last())
        assertEquals("Bearer access-1", auth.authorizations.last())
        // The remembered account fills the login page again.
        assertEquals("kai", field(authIdentityInputTag).fieldText())
        assertEquals("secret-password", field(authPasswordInputTag).fieldText())
    }

    @Test
    fun `a rejected login stays on the login page`() {
        val auth = FakeAuthServer(loginStatus = 401)
        val tokenStore = MemoryTokenStore()
        val home = TestHome()
        pumpApp(tokenStore = tokenStore, auth = auth, home = home)

        logIn(password = "wrong")

        assertTrue(loginPageShown())
        assertTrue(exists("账号或密码不正确"))
        assertTrue(home.apps.isEmpty())
        assertNull(tokenStore.refreshToken)
    }

    @Test
    fun `stored session restores to home behind the loading page`() {
        val refresh = CompletableDeferred<Unit>()
        val auth = FakeAuthServer(beforeRefresh = { refresh.await() })
        val tokenStore = MemoryTokenStore(refreshToken = "stored-refresh")
        val historyStore =
            MemoryLoginAccountHistoryStore(
                listOf(LoginAccountRecord(login = "kai", password = null, avatarUrl = "/assets/old.png", defaultAvatarKey = "green-2", useCount = 2, updatedAt = DateTime.utc(2026))),
            )
        val home = TestHome()
        var reveals = 0
        pumpApp(
            tokenStore = tokenStore,
            startsAuthenticated = true,
            historyStore = historyStore,
            auth = auth,
            home = home,
            showInitialWindow = { reveals++ },
        )

        // Restoring: the loading page, never the login page, and the launch
        // screen stays up.
        assertTrue(loadingPageShown())
        assertTrue(exists("正在加载"))
        assertFalse(loginPageShown())
        assertEquals(0, reveals)
        assertEquals(listOf("POST /api/v1/auth/refresh"), auth.requests)
        assertEquals(mapOf("refresh_token" to "stored-refresh"), auth.bodies.single())

        refresh.complete(Unit)
        compose.settleShell()

        assertTrue(homeShown())
        assertFalse(loadingPageShown())
        assertEquals(1, reveals)
        // The rotated refresh token is stored.
        assertEquals("refresh-1", tokenStore.refreshToken)
        // Android refreshes the remembered account's avatar from the session.
        val record = historyStore.records.single()
        assertNull(record.avatarUrl)
        assertEquals("blue-3", record.defaultAvatarKey)
        assertEquals(2, record.useCount)
    }

    @Test
    fun `a restore that hangs reveals the loading page after four seconds`() {
        val never = CompletableDeferred<Unit>()
        val auth = FakeAuthServer(beforeRefresh = { never.await() })
        var reveals = 0
        var revealedAt = 0L
        val started = SystemClock.uptimeMillis()
        pumpApp(
            tokenStore = MemoryTokenStore(refreshToken = "stored-refresh"),
            startsAuthenticated = true,
            auth = auth,
            showInitialWindow = {
                reveals++
                revealedAt = SystemClock.uptimeMillis()
            },
        )
        assertTrue(loadingPageShown())

        while (reveals == 0 && SystemClock.uptimeMillis() - started < 10_000) compose.pumpShell(50)

        assertEquals(1, reveals)
        assertTrue("revealed after ${revealedAt - started} ms", revealedAt - started in 4_000L..4_100L)
        assertTrue(loadingPageShown())
        repeat(5) { compose.settleShell() }
        assertEquals(1, reveals)
    }

    @Test
    fun `a stored session the server rejects returns to the login page`() {
        val auth = FakeAuthServer(refreshStatus = 401)
        val tokenStore = MemoryTokenStore(refreshToken = "revoked")
        var reveals = 0
        pumpApp(tokenStore = tokenStore, startsAuthenticated = true, auth = auth, showInitialWindow = { reveals++ })

        assertTrue(loginPageShown())
        assertFalse(loadingPageShown())
        assertNull(tokenStore.refreshToken)
        assertEquals(1, reveals)
    }

    @Test
    fun `session expiry returns to the login page`() {
        val auth = FakeAuthServer()
        val tokenStore = MemoryTokenStore(refreshToken = "stored-refresh")
        val home = TestHome()
        pumpApp(tokenStore = tokenStore, startsAuthenticated = true, auth = auth, home = home)
        assertTrue(homeShown())

        // The refresh token has been revoked server-side: the next refresh is
        // rejected and the session ends.
        auth.refreshStatus = 401
        var refreshError: Throwable? = null
        com.gangchat.client.foundation.unawaited {
            try {
                home.apps.last().accessTokenProvider(forceRefresh = true)
            } catch (error: Exception) {
                refreshError = error
            }
        }
        compose.settleShell()

        assertTrue(refreshError is com.gangchat.client.auth.AuthException)
        assertTrue(loginPageShown())
        assertFalse(homeShown())
        assertNull(tokenStore.refreshToken)
    }

    @Test
    fun `account suspension signs out to the login page`() {
        val auth = FakeAuthServer()
        val realtime = FakeHomeRealtimeService()
        val tokenStore = MemoryTokenStore(refreshToken = "stored-refresh")
        pumpApp(tokenStore = tokenStore, startsAuthenticated = true, auth = auth, home = TestHome(realtime = realtime))
        assertTrue(homeShown())

        realtime.add("account_suspended", emptyMap())
        compose.settleShell()

        assertTrue(loginPageShown())
        assertNull(tokenStore.refreshToken)
        assertEquals("POST /api/v1/auth/logout", auth.requests.last())
    }

    @Test
    @Config(qualifiers = "w900dp-h900dp-mdpi")
    fun `account deletion returns to the login page`() {
        val auth = FakeAuthServer()
        val tokenStore = MemoryTokenStore(refreshToken = "stored-refresh")
        val home = TestHome(deletableAccount = true)
        pumpApp(tokenStore = tokenStore, startsAuthenticated = true, auth = auth, home = home)
        assertTrue(homeShown())

        tapTooltip("设置")
        tapText("隐私和安全")
        // The section's last group.
        compose.onAllNodes(hasScrollToNodeAction() and hasAnyDescendant(hasText("绑定信息"))).onLast().performScrollToIndex(3)
        compose.settleShell()
        compose.onNode(hasText("注销账号") and hasClickAction()).performClick()
        compose.settleShell()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(settingsDeleteAccountInputTag)))
            .performTextReplacement("kai")
        compose.settleShell()
        compose.onNode(hasText("确认注销") and hasClickAction()).performClick()
        compose.settleShell()

        assertEquals(1, home.deletions)
        assertTrue(loginPageShown())
        assertNull(tokenStore.refreshToken)
        assertEquals("POST /api/v1/auth/logout", auth.requests.last())
    }

    @Test
    fun `the login page follows the remembered language`() {
        val languageStore = MemorySettingsLanguageStore(language = "en")
        val auth = FakeAuthServer(userLanguage = "zh-Hans")
        pumpApp(languageStore = languageStore, auth = auth, home = TestHome())

        tapSubmit()
        assertTrue(exists("Enter your account and password to continue"))

        logIn()
        assertTrue(homeShown())
        // The signed-in account's language becomes the login page's.
        assertEquals("zh-Hans", languageStore.language)

        logOutFromHome()

        tapTag(segmentedControlSegmentTag(1))
        tapTag(segmentedControlSegmentTag(0))
        enter(authPasswordInputTag, "")
        tapSubmit()
        assertTrue(exists("请输入账号和密码后继续"))
    }

    // --- Fixtures --------------------------------------------------------------

    /**
     * The signed-in page: the real [HomePage] with the home shell suite's fake
     * API, realtime and media (Dart's `_homeTestAppContext` wiring), recording
     * every [AuthenticatedAppContext] the gate hands it.
     */
    private class TestHome(
        val realtime: RealtimeService = NoopRealtimeService(),
        deletableAccount: Boolean = false,
    ) {
        val api = HomeShellTestApi(pinAlphaRoom = true)
        val apps = mutableListOf<AuthenticatedAppContext>()
        var deletions = 0
        private val gangApi: GangApi =
            if (!deletableAccount) {
                api.client
            } else {
                object : GangApi by api.client {
                    override suspend fun deleteMyAccount(confirm: Boolean) {
                        deletions += 1
                    }
                }
            }
        private val audioDeviceStore = FakeHomeAudioDeviceStore()
        private val liveSessionController = FakeHomeLiveSessionController(session = FakeHomeLiveSession())
        private val soundPlayer = RecordingLivePresenceSoundPlayer()
        private val speechPlayer = RecordingLivePresenceSpeechPlayer()
        private val messageSoundPlayer = RecordingMessageNotificationSoundPlayer()

        val builder: GangAppHomePageBuilder = { app, languageStore, detectedAppUpdate, onDetectedAppUpdateShown ->
            apps.add(app)
            HomePage(
                app =
                    AuthenticatedAppContext(
                        session = app.session,
                        apiBaseUrl = app.apiBaseUrl,
                        accessTokenProvider = app.accessTokenProvider,
                        logout = app.logout,
                        exitSessionForAppExit = app.exitSessionForAppExit,
                        serverClock = app.serverClock,
                        emailVerificationController = app.emailVerificationController,
                        passwordResetController = app.passwordResetController,
                        onCurrentUserUpdated = app.onCurrentUserUpdated,
                        api = gangApi,
                    ),
                realtime = realtime,
                audioDeviceStore = audioDeviceStore,
                liveSessionController = liveSessionController,
                livePresenceSoundPlayer = soundPlayer,
                livePresenceSpeechPlayer = speechPlayer,
                messageNotificationSoundPlayer = messageSoundPlayer,
                languageStore = languageStore,
                detectedAppUpdate = detectedAppUpdate,
                onDetectedAppUpdateShown = onDetectedAppUpdateShown,
            )
        }
    }
}

/** Dart's `_MemoryTokenStore`, counting reads. */
class MemoryTokenStore(var refreshToken: String? = null, @Suppress("unused") val label: String = "") : TokenStore() {
    var apiBaseUrl: String? = null
    var reads = 0

    override suspend fun readRefreshToken(): String? {
        reads += 1
        return refreshToken
    }

    override suspend fun writeRefreshToken(refreshToken: String) {
        this.refreshToken = refreshToken
    }

    override suspend fun clearRefreshToken() {
        refreshToken = null
    }

    override suspend fun readApiBaseUrl(): String? = apiBaseUrl

    override suspend fun writeApiBaseUrl(baseUrl: String) {
        apiBaseUrl = baseUrl
    }
}

/**
 * The auth endpoints the gate uses: login, refresh and logout, issuing
 * numbered tokens for the home shell suite's user.
 */
class FakeAuthServer(
    private val loginStatus: Int = 200,
    var refreshStatus: Int = 200,
    private val userLanguage: String = "zh-Hans",
    private val beforeRefresh: (suspend () -> Unit)? = null,
) {
    val requests = mutableListOf<String>()
    val bodies = mutableListOf<Map<String, Any?>>()
    val authorizations = mutableListOf<String?>()
    private var issued = 0

    val factory: AuthClientFactory = { baseUrl -> AuthClient(baseUrl = baseUrl, httpClient = MockClient { request -> _handle(request) }) }

    private fun _session(): JsonMap {
        issued += 1
        val user = LinkedHashMap(homeShellCurrentUserJson)
        user["language"] = userLanguage
        return linkedMapOf(
            "access_token" to "access-$issued",
            "refresh_token" to "refresh-$issued",
            "expires_in" to 900,
            "user" to user,
        )
    }

    private fun _unauthorized(): HttpResponse =
        homeShellJsonResponse(mapOf("error" to mapOf("code" to "unauthorized", "message" to "unauthorized")), statusCode = 401)

    @Suppress("UNCHECKED_CAST")
    private suspend fun _handle(request: HttpRequest): HttpResponse {
        val path = request.url.path
        requests.add("${request.method} $path")
        val body = utf8Decode(request.bodyBytes)
        bodies.add(if (body.isEmpty()) emptyMap() else jsonDecode(body) as Map<String, Any?>)
        authorizations.add(request.headers["authorization"] ?: request.headers["Authorization"])
        return when (path) {
            "/api/v1/auth/login" -> if (loginStatus == 200) homeShellJsonResponse(_session()) else _unauthorized()
            "/api/v1/auth/refresh" -> {
                beforeRefresh?.invoke()
                if (refreshStatus == 200) homeShellJsonResponse(_session()) else _unauthorized()
            }
            "/api/v1/auth/logout" -> HttpResponse("", 204)
            else -> HttpResponse("unexpected ${request.method} $path", 404)
        }
    }
}

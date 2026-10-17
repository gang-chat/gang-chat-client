package com.gangchat.client.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.AuthClientFactory
import com.gangchat.client.app.AuthRequest
import com.gangchat.client.app.AuthRestoreStatus
import com.gangchat.client.app.AuthSessionController
import com.gangchat.client.app.AuthSessionListener
import com.gangchat.client.app.AuthenticatedAppContext
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.EmailVerificationController
import com.gangchat.client.app.LanguagePreferenceStore
import com.gangchat.client.app.LoginAccountHistoryStore
import com.gangchat.client.app.PasswordResetController
import com.gangchat.client.app.ServerClock
import com.gangchat.client.app.defaultLanguagePreference
import com.gangchat.client.app.normalizeLanguagePreference
import com.gangchat.client.app.rememberLoginAccount
import com.gangchat.client.app.updateLoginAccountAvatarMetadata
import com.gangchat.client.auth.AuthSession
import com.gangchat.client.auth.TokenStore
import com.gangchat.client.config.AppConfig
import com.gangchat.client.foundation.Timer
import com.gangchat.client.foundation.VisibleForTesting
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.home.HomePage
import com.gangchat.client.protocol.AccessTokenProvider
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.AppNotificationHost
import com.gangchat.client.ui.LocalTooltipTriggerMode
import com.gangchat.client.ui.TooltipTriggerMode
import com.gangchat.client.ui.UiTheme
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.Scaffold
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.WindowMediaQuery
import com.gangchat.client.widgets.flutterTextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Port of lib/src/shell/gang_app.dart.
//
// PORT: the desktop `DesktopWindowController` is not ported. On Android its
// calls were no-ops (`supportsWindowManagement` is false), except that the
// window stayed up showing the Flutter splash until the first frame. The one
// part kept is the reveal: the auth gate calls [GangApp]'s `showInitialWindow`
// where Dart called `windowController.showInitialWindow()`, and `MainActivity`
// keeps its launch screen until then (see `prepareGangApp`). Window locking,
// resizing and `runWithHiddenWindow` (which just runs its body without window
// management) are dropped.

/** Dart's `appWindowBackground` (`desktop_window_controller.dart`): the loading page. */
private val appWindowBackground = Color(0xFF14171D)

/**
 * The defaults Dart wrote as `const` constructor calls (canonical instances,
 * so a rebuilt app keeps passing the same stores down).
 */
private object _GangAppDefaults {
    val tokenStore: TokenStore by lazy { TokenStore() }
    val config: AppConfig by lazy { AppConfig.defaults() }
    val languageStore: LanguagePreferenceStore by lazy { LocalLanguagePreferenceStore() }
    val loginAccountHistoryStore: LoginAccountHistoryStore by lazy { LocalLoginAccountHistoryStore() }
}

/**
 * Builds the signed-in page in place of [HomePage], with the arguments
 * [GangApp] passes it (a test seam; see [GangApp]'s `homePage`).
 */
typealias GangAppHomePageBuilder =
    @Composable (
        app: AuthenticatedAppContext,
        languageStore: LanguagePreferenceStore,
        detectedAppUpdate: AvailableAppUpdate?,
        onDetectedAppUpdateShown: () -> Unit,
    ) -> Unit

/**
 * The app root (Dart's `GangApp`, an `AppConfigScope` around a
 * `MaterialApp`): the theme with its root text style, the window's media
 * query, the floating notices around the root navigator, touch tooltips off
 * (Android reserves a hold for context menus), and the auth gate as the
 * navigator's home. The gate restores a stored session through [tokenStore]
 * and shows [HomePage], or else the login page; logout, session expiry and
 * account deletion return to the login page.
 *
 * [startsAuthenticated] says a refresh token was stored at launch: the gate
 * then shows a loading page (not the login page) until the restore finishes.
 *
 * [showInitialWindow] is called once the gate has decided what to show (or
 * 4 s into a restore that has not finished), as Dart revealed the hidden
 * desktop window; `MainActivity` keeps its launch screen up until then.
 *
 * [authClientFactory] and [homePage] are test seams: the auth API client
 * (default: the real client for `config.apiBaseUrl`) and the signed-in page
 * (default: [HomePage]).
 */
@Composable
fun GangApp(
    tokenStore: TokenStore = _GangAppDefaults.tokenStore,
    config: AppConfig = _GangAppDefaults.config,
    startsAuthenticated: Boolean = false,
    languageStore: LanguagePreferenceStore = _GangAppDefaults.languageStore,
    loginAccountHistoryStore: LoginAccountHistoryStore = _GangAppDefaults.loginAccountHistoryStore,
    showInitialWindow: (() -> Unit)? = null,
    @VisibleForTesting authClientFactory: AuthClientFactory? = null,
    @VisibleForTesting homePage: GangAppHomePageBuilder? = null,
) {
    AppConfigScope(config) {
        // MaterialApp(theme: uiTheme(), scrollBehavior: GangScrollBehavior())
        // with its MediaQuery; GangScrollBehavior is a no-op on Android.
        UiTheme {
            WindowMediaQuery {
                // MaterialApp(builder:): Android reserves a hold for
                // application context menus. Disable Tooltip's default
                // touch-long-press trigger while retaining the existing
                // pointer-hover behavior unchanged.
                CompositionLocalProvider(LocalTooltipTriggerMode provides TooltipTriggerMode.manual) {
                    AppNotificationHost {
                        RootNavigator { _ ->
                            _AuthGate(
                                tokenStore = tokenStore,
                                config = config,
                                startsAuthenticated = startsAuthenticated,
                                languageStore = languageStore,
                                loginAccountHistoryStore = loginAccountHistoryStore,
                                showInitialWindow = showInitialWindow,
                                authClientFactory = authClientFactory,
                                homePage = homePage,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun _AuthGate(
    tokenStore: TokenStore,
    config: AppConfig,
    startsAuthenticated: Boolean,
    languageStore: LanguagePreferenceStore,
    loginAccountHistoryStore: LoginAccountHistoryStore,
    showInitialWindow: (() -> Unit)?,
    authClientFactory: AuthClientFactory?,
    homePage: GangAppHomePageBuilder?,
) = StatefulWidget(
    _AuthGateWidget(
        tokenStore, config, startsAuthenticated, languageStore, loginAccountHistoryStore, showInitialWindow,
        authClientFactory, homePage,
    ),
) { _AuthGateState() }

private class _AuthGateWidget(
    val tokenStore: TokenStore,
    val config: AppConfig,
    val startsAuthenticated: Boolean,
    val languageStore: LanguagePreferenceStore,
    val loginAccountHistoryStore: LoginAccountHistoryStore,
    val showInitialWindow: (() -> Unit)?,
    val authClientFactory: AuthClientFactory?,
    val homePage: GangAppHomePageBuilder?,
)

private class _AuthGateState : ViewState<_AuthGateWidget>() {
    private val _auth: AuthSessionController by lazy {
        AuthSessionController(
            tokenStore = widget.tokenStore,
            apiBaseUrl = widget.config.apiBaseUrl,
            clientUserAgent = currentGangChatClientUserAgent(),
            authClientFactory = widget.authClientFactory,
        )
    }
    private val _passwordReset: PasswordResetController by lazy {
        PasswordResetController(
            apiBaseUrl = widget.config.apiBaseUrl,
            authClientFactory = widget.authClientFactory,
            accessTokenProvider = { _auth.accessToken() },
        )
    }
    private val _emailVerification: EmailVerificationController by lazy {
        EmailVerificationController(
            apiBaseUrl = widget.config.apiBaseUrl,
            authClientFactory = widget.authClientFactory,
            accessTokenProvider = { _auth.accessToken() },
        )
    }
    private val _serverClock = ServerClock()

    // Tracks whether initial auth restore has finished so an authenticated start
    // does not briefly mount LoginPage.
    private var _initialRestoreDone = false

    // The launch screen stays up until we know which screen to show.
    private var _windowRevealed = false
    private var _exitingSessionForAppExit = false
    private var _detectedAppUpdate: AvailableAppUpdate? = null
    private var _authLanguage = defaultLanguagePreference
    private var _revealFallbackTimer: Timer? = null

    // The composition's coroutine scope, for its frame clock (Dart's
    // `WidgetsBinding.instance.endOfFrame`).
    private var _frameScope: CoroutineScope? = null

    // Callbacks handed to children, created once (Dart passed tear-offs, which
    // compare equal across rebuilds).
    private val _onAuthChanged: AuthSessionListener = { _onAuthChanged() }
    private val _onSubmit: AuthSubmit = { request, rememberPassword -> _submitAuthRequest(request, rememberPassword) }
    private val _checkUsernameAvailability: AuthUsernameAvailabilityCheck = { username -> _auth.isUsernameAvailable(username) }
    private val _checkEmailAvailability: AuthEmailAvailabilityCheck = { email -> _auth.isEmailAvailable(email) }
    private val _accessTokenProvider = AccessTokenProvider { forceRefresh -> _auth.accessToken(forceRefresh = forceRefresh) }
    private val _logoutCallback: suspend () -> Unit = { _logout() }
    private val _exitSessionCallback: suspend () -> Unit = { _exitSessionForAppExit() }
    private val _onCurrentUserUpdated: (CurrentUser) -> Unit = { user -> unawaited { _syncAndroidLoginAccountAvatarMetadata(user) } }
    private val _onUpdateAvailable: AppUpdateAvailableCallback = { update ->
        if (mounted) setState { _detectedAppUpdate = update }
    }
    private val _onDetectedAppUpdateShown: () -> Unit = {
        if (mounted && _detectedAppUpdate != null) setState { _detectedAppUpdate = null }
    }

    private val _supportsWindowManagement: Boolean get() = widget.showInitialWindow != null

    override fun initState() {
        _auth.addListener(_onAuthChanged)
        if (widget.startsAuthenticated && _supportsWindowManagement) {
            // Safety net: if auth refresh hangs, reveal the window anyway so the user
            // is not left staring at nothing.
            _revealFallbackTimer = Timer(4.seconds) { _ensureWindowVisible() }
        }
        unawaited { _loadAuthLanguage() }
        unawaited { _restoreSession() }
    }

    private fun _onAuthChanged() {
        if (_auth.session == null) {
            _passwordReset.clearCurrentSessionAuthorization()
            unawaited { _loadAuthLanguage() }
        }
        if (mounted) setState()
    }

    private suspend fun _restoreSession() {
        // AppConfig is the source of truth for server selection. A stored base URL
        // is only a stale copy of older config because there is no server picker.
        val result = _auth.restoreSession()
        if (!mounted) return

        if (result.status == AuthRestoreStatus.missingRefreshToken) {
            setState { _initialRestoreDone = true }
            _ensureWindowVisible()
            return
        }

        if (result.hasSession) {
            val session = _auth.session
            if (session != null) {
                _syncAndroidLoginAccountAvatarMetadata(session.user)
                unawaited { _rememberAuthLanguage(session.user.language) }
            }
            if (widget.startsAuthenticated) {
                setState { _initialRestoreDone = true }
                _endOfFrame()
                delayed(120.milliseconds)
                _ensureWindowVisible()
            } else {
                if (mounted) setState { _initialRestoreDone = true }
                _ensureWindowVisible()
            }
            return
        }

        setState { _initialRestoreDone = true }
        _ensureWindowVisible()
    }

    private suspend fun _endOfFrame() {
        val clock = _frameScope?.coroutineContext?.get(MonotonicFrameClock) ?: return
        clock.withFrameNanos { }
    }

    private fun _ensureWindowVisible() {
        if (!_supportsWindowManagement) return
        if (_windowRevealed) return
        _windowRevealed = true
        _revealFallbackTimer?.cancel()
        _revealFallbackTimer = null
        widget.showInitialWindow?.invoke()
    }

    private suspend fun _loadAuthLanguage() {
        try {
            val language = widget.languageStore.read()
            if (!mounted) return
            setState { _authLanguage = normalizeLanguagePreference(language) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // A missing/unavailable preference store should keep the default language.
        }
    }

    private suspend fun _rememberAuthLanguage(language: String) {
        val normalized = normalizeLanguagePreference(language)
        if (mounted && _authLanguage != normalized) {
            setState { _authLanguage = normalized }
        }
        try {
            widget.languageStore.write(normalized)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Login should not fail because a non-sensitive local preference failed.
        }
    }

    private suspend fun _logout() {
        _loadAuthLanguage()
        var session: AuthSession? = null
        if (mounted) {
            session = _auth.beginLogout()
        }
        _auth.finishLogout(session)
    }

    private fun _exitSessionForAppExit() {
        if (mounted) {
            setState { _exitingSessionForAppExit = true }
        } else {
            _exitingSessionForAppExit = true
        }
        _auth.beginLogout()
    }

    private suspend fun _submitAuthRequest(request: AuthRequest, rememberPassword: Boolean) {
        val session = _auth.authenticate(request)
        if (!request.registering) {
            _rememberLoginAccount(
                request,
                rememberPassword = rememberPassword,
                avatarUrl = session.user.avatarUrl,
                defaultAvatarKey = session.user.defaultAvatarKey,
            )
        }
        _rememberAuthLanguage(session.user.language)
        if (!mounted) return
        _auth.acceptSession(session)
    }

    private suspend fun _rememberLoginAccount(
        request: AuthRequest,
        rememberPassword: Boolean,
        avatarUrl: String?,
        defaultAvatarKey: String,
    ) {
        try {
            val records = widget.loginAccountHistoryStore.read()
            widget.loginAccountHistoryStore.write(
                rememberLoginAccount(
                    records = records,
                    login = request.login,
                    password = request.password,
                    rememberPassword = rememberPassword,
                    avatarUrl = avatarUrl,
                    defaultAvatarKey = defaultAvatarKey,
                    updateAvatarMetadata = true,
                ),
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Successful authentication should not be blocked by local history IO.
        }
    }

    private suspend fun _syncAndroidLoginAccountAvatarMetadata(user: CurrentUser) {
        if (!mounted) return
        try {
            val records = widget.loginAccountHistoryStore.read()
            widget.loginAccountHistoryStore.write(
                updateLoginAccountAvatarMetadata(
                    records = records,
                    accountAliases = listOfNotNull(user.username, user.email),
                    avatarUrl = user.avatarUrl,
                    defaultAvatarKey = user.defaultAvatarKey,
                ),
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Remembered-account decoration must never block auth or profile edits.
        }
    }

    override fun dispose() {
        _auth.removeListener(_onAuthChanged)
        _auth.dispose()
        _serverClock.dispose()
        _revealFallbackTimer?.cancel()
    }

    @Composable
    override fun build() {
        _frameScope = rememberCoroutineScope()
        val session = _auth.session
        if (_exitingSessionForAppExit) {
            _LoadingPage()
            return
        }
        if (session == null) {
            if (widget.startsAuthenticated && !_initialRestoreDone) {
                _LoadingPage()
                return
            }
            LoginPage(
                language = _authLanguage,
                onSubmit = _onSubmit,
                checkUsernameAvailability = _checkUsernameAvailability,
                checkEmailAvailability = _checkEmailAvailability,
                emailVerificationController = _emailVerification,
                passwordResetController = _passwordReset,
                accountHistoryStore = widget.loginAccountHistoryStore,
                submitPersistsAccountHistory = true,
            )
            return
        }

        val app =
            AuthenticatedAppContext(
                session = session,
                apiBaseUrl = _auth.apiBaseUrl,
                accessTokenProvider = _accessTokenProvider,
                logout = _logoutCallback,
                exitSessionForAppExit = _exitSessionCallback,
                serverClock = _serverClock,
                emailVerificationController = _emailVerification,
                passwordResetController = _passwordReset,
                onCurrentUserUpdated = _onCurrentUserUpdated,
            )

        // PORT: Dart wrapped this in `SelectionContainer.disabled` and, off
        // Android only, a `SelectionArea`. On Android the app-wide selection
        // region competes with the inputs' own long-press selection, so there
        // is none and the disabled container has nothing to disable.
        AppUpdateGate(
            releaseBucketUrl = widget.config.releaseBucketUrl,
            onUpdateAvailable = _onUpdateAvailable,
            child = {
                val homePage = widget.homePage
                if (homePage != null) {
                    homePage(app, widget.languageStore, _detectedAppUpdate, _onDetectedAppUpdateShown)
                } else {
                    HomePage(
                        app = app,
                        languageStore = widget.languageStore,
                        detectedAppUpdate = _detectedAppUpdate,
                        onDetectedAppUpdateShown = _onDetectedAppUpdateShown,
                    )
                }
            },
        )
    }
}

/** Test tag of the loading page shown while a stored session is restored. */
const val gangAppLoadingPageTag = "gang-app-loading-page"

@Composable
private fun _LoadingPage() {
    Scaffold(backgroundColor = appWindowBackground, modifier = Modifier.testTag(gangAppLoadingPageTag)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    "Gang Chat",
                    style =
                        flutterTextStyle(
                            color = Color(0xFFECEFF1),
                            fontSize = 22.0,
                            fontWeight = FontWeight.W600,
                            letterSpacing = 0.0,
                        ),
                )
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color(0xFF6FCFA6),
                    strokeWidth = 2.5.dp,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Square,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "正在加载",
                    style =
                        flutterTextStyle(
                            color = Color(0xFFB0B8C0),
                            fontSize = 13.0,
                            fontWeight = FontWeight.W500,
                            letterSpacing = 0.0,
                        ),
                )
            }
        }
    }
}

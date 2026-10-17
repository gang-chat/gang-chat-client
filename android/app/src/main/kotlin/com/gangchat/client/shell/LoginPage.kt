package com.gangchat.client.shell

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.AuthRequest
import com.gangchat.client.app.AuthSubmitState
import com.gangchat.client.app.EmailVerificationController
import com.gangchat.client.app.LoginAccountHistoryStore
import com.gangchat.client.app.LoginAccountRecord
import com.gangchat.client.app.NoopLoginAccountHistoryStore
import com.gangchat.client.app.PasswordResetController
import com.gangchat.client.app.authRequestFromForm
import com.gangchat.client.app.authSubmitFailed
import com.gangchat.client.app.authSubmitInvalid
import com.gangchat.client.app.authSubmitStarted
import com.gangchat.client.app.defaultLanguagePreference
import com.gangchat.client.app.deleteLoginAccountRecord
import com.gangchat.client.app.lastLoginAccountRecord
import com.gangchat.client.app.loginAccountsMatch
import com.gangchat.client.app.loginUsernameValidationError
import com.gangchat.client.app.registerEmailValidationError
import com.gangchat.client.app.rememberLoginAccount
import com.gangchat.client.auth.AuthException
import com.gangchat.client.auth.EmailVerificationChallenge
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.platform.SystemChrome
import com.gangchat.client.platform.SystemIconBrightness
import com.gangchat.client.platform.SystemUiOverlayStyle
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.UiCheckbox
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.BoxShadow
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.SafeArea
import com.gangchat.client.widgets.Scaffold
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TapRegion
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.listen
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.startTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

// PORT: Dart's `AuthWindowLock` and `LoginPage.consumeInitialWindowLock` /
// `lockAuthWindow` / `windowController` only size, move and decorate the
// desktop window (`DesktopWindowController`, a no-op on Android), so they are
// not ported. `sizeForMode` stays: Android lays the auth surface out with it.
typealias AuthSizeForMode = (registering: Boolean, showingError: Boolean) -> DpSize
typealias AuthSubmit = suspend (request: AuthRequest, rememberPassword: Boolean) -> Unit
typealias AuthUsernameAvailabilityCheck = suspend (username: String) -> Boolean
typealias AuthEmailAvailabilityCheck = suspend (email: String) -> Boolean

private val _authWidgetWidth = 430.dp
private val _authBottomBreathingRoom = 24.dp
private val _authBrandTitleExtraHeight = 53.dp
private val _loginWidgetHeight = 291.dp + _authBrandTitleExtraHeight + _authBottomBreathingRoom
private val _registerWidgetHeight = 359.dp + _authBrandTitleExtraHeight + _authBottomBreathingRoom

/**
 * The auth widget size `DesktopWindowController.authWidgetSize` gives
 * [LoginPage.sizeForMode] (the only part of `desktop_window_controller.dart`
 * Android uses): 430×368 for login, 430×436 for registration.
 */
@Suppress("UNUSED_PARAMETER")
fun authWidgetSize(registering: Boolean, showingError: Boolean = false): DpSize =
    if (registering) DpSize(_authWidgetWidth, _registerWidgetHeight) else DpSize(_authWidgetWidth, _loginWidgetHeight)

private enum class _AuthMode { login, register }

private val _authTitleBarTopInset = 14.dp
private val _authTitleBarHeight = 54.dp
private val _authTitleBarGap = 14.dp
private val _authBrandIconSize = 36.dp
private val _authBrandGap = 10.dp
private val _authFieldGap = 3.dp
private val _authModeGap = 10.dp
private val _authActionGap = 6.dp
private val _authErrorHeight = 11.dp
private val _authPasswordErrorGap = 4.dp
private val _authErrorRememberGap = 0.dp
private val _authRememberHeight = 28.dp
private val _authSubmitHeight = 32.dp
private val _accountHistoryItemHeight = 38.dp

// The segmented control draws its border inside its height, so its outer
// height is the height it is given.
private val _authSegmentedControlHeight = 36.dp
private val _authSegmentedControlOuterHeight = _authSegmentedControlHeight

// [Input] draws its 1px border outside its nominal height, so each field
// occupies its height plus the top and bottom border.
private val _authInputHeight = Input.defaultHeight
private val _authInputOuterHeight = _authInputHeight + 2.dp
private val _accountHistoryDropdownTop =
    _authTitleBarHeight +
        _authTitleBarGap +
        _authSegmentedControlOuterHeight +
        _authModeGap +
        _authInputOuterHeight +
        _authFieldGap
private val _authRegisterFieldsCenter =
    _authTitleBarTopInset +
        _authTitleBarHeight +
        _authTitleBarGap +
        _authSegmentedControlOuterHeight +
        _authModeGap +
        (_authInputOuterHeight * 4 + _authFieldGap * 3) / 2
private val _authTitleBarStyle =
    uiTextStyle(color = UiColors.text, fontSize = 22.0, fontWeight = FontWeight.W700, height = 1.0, letterSpacing = 0.0)

internal val _androidAuthSystemUiOverlayStyle =
    SystemUiOverlayStyle(
        statusBarColor = UiColors.surfaceLow.toArgb(),
        statusBarIconBrightness = SystemIconBrightness.light,
        systemStatusBarContrastEnforced = false,
        systemNavigationBarColor = UiColors.surfaceLow.toArgb(),
        systemNavigationBarDividerColor = UiColors.surfaceLow.toArgb(),
        systemNavigationBarIconBrightness = SystemIconBrightness.light,
        systemNavigationBarContrastEnforced = false,
    )

/** The default account history store (Dart's canonical `const NoopLoginAccountHistoryStore()`). */
private val _noopLoginAccountHistoryStore = NoopLoginAccountHistoryStore()

/** Test tag of the auth surface (Dart's `ValueKey('auth-surface')`). */
const val authSurfaceTag = "auth-surface"

/** Test tag of the submit button ("登录" / "创建账号"). */
const val authSubmitButtonTag = "auth-submit-button"

/** Test tag of the login / register mode switch. */
const val authModeSwitchTag = "auth-mode-switch"

// Test tags of the auth fields (Dart's tests find them by hint text).
/** "登录用户名或邮箱地址" while logging in, "登录用户名" while registering. */
const val authIdentityInputTag = "auth-identity-input"
const val authEmailInputTag = "auth-email-input"
const val authPasswordInputTag = "auth-password-input"
const val authConfirmPasswordInputTag = "auth-confirm-password-input"

/** Test tag of each remembered-account avatar in the account history dropdown. */
const val authAccountHistoryAvatarTag = "auth-account-history-avatar"

/** The image URL an account history avatar was given (Dart's `Avatar.imageUrl`). */
val AccountHistoryAvatarImageUrlKey = SemanticsPropertyKey<String?>("AccountHistoryAvatarImageUrl")

/** The preset key an account history avatar was given (Dart's `Avatar.defaultAvatarKey`). */
val AccountHistoryAvatarDefaultKey = SemanticsPropertyKey<String?>("AccountHistoryAvatarDefaultKey")

private var SemanticsPropertyReceiver._avatarImageUrl by AccountHistoryAvatarImageUrlKey
private var SemanticsPropertyReceiver._avatarDefaultKey by AccountHistoryAvatarDefaultKey

/**
 * The login / registration page.
 *
 * Android keeps both modes on the registration geometry ([sizeForMode] with
 * `registering = true`), so the four registration fields are centered on
 * screen and the login fields keep the same starting position when the mode
 * switches. It also styles the system bars like the page background.
 */
@Composable
fun LoginPage(
    onSubmit: AuthSubmit,
    modifier: Modifier = Modifier,
    sizeForMode: AuthSizeForMode = ::authWidgetSize,
    checkUsernameAvailability: AuthUsernameAvailabilityCheck? = null,
    checkEmailAvailability: AuthEmailAvailabilityCheck? = null,
    emailVerificationController: EmailVerificationController? = null,
    passwordResetController: PasswordResetController? = null,
    language: String = defaultLanguagePreference,
    accountHistoryStore: LoginAccountHistoryStore = _noopLoginAccountHistoryStore,
    submitPersistsAccountHistory: Boolean = false,
) = StatefulWidget(
    LoginPageWidget(
        sizeForMode, onSubmit, checkUsernameAvailability, checkEmailAvailability, emailVerificationController,
        passwordResetController, language, accountHistoryStore, submitPersistsAccountHistory, modifier,
    ),
) { _LoginPageState() }

class LoginPageWidget(
    val sizeForMode: AuthSizeForMode,
    val onSubmit: AuthSubmit,
    val checkUsernameAvailability: AuthUsernameAvailabilityCheck?,
    val checkEmailAvailability: AuthEmailAvailabilityCheck?,
    val emailVerificationController: EmailVerificationController?,
    val passwordResetController: PasswordResetController?,
    val language: String,
    val accountHistoryStore: LoginAccountHistoryStore,
    val submitPersistsAccountHistory: Boolean,
    val modifier: Modifier,
)

private class _LoginPageState : ViewState<LoginPageWidget>() {
    private val _login = TextEditingController()
    private val _email = TextEditingController()
    private val _password = TextEditingController()
    private val _confirmPassword = TextEditingController()

    private var _registering = false
    private var _obscurePassword = true
    private var _obscureConfirmPassword = true
    private var _submitState = AuthSubmitState()
    private var _accountHistory: List<LoginAccountRecord> = emptyList()
    private var _showAccountHistory = false
    private var _rememberPassword = false
    private var _registerEmailEmpty = true
    private var _verifiedEmail: String? = null
    private var _emailVerificationToken: String? = null
    private var _checkingEmailAvailability = false
    private var _checkingPasswordReset = false
    private var _selectedHistoryLogin: String? = null
    private val _accountHistoryTapRegion = Any()
    private var _usernameAvailabilityDebounce: CompositionTimer? = null
    private var _usernameAvailabilityQuery: String? = null
    private var _checkingUsernameAvailability = false
    private var _usernameAvailabilityError: String? = null

    /** The composition's scope, for the username availability debounce timer. */
    private var _scope: CoroutineScope? = null

    private val _showingError: Boolean
        get() = _submitState.error != null && _submitState.error!!.isNotEmpty()
    private val _emailVerified: Boolean
        get() = _verifiedEmail != null && _verifiedEmail == _email.text.trim().lowercase()
    private val _canShowAccountHistory: Boolean
        get() = !_registering && !_submitState.busy && _accountHistory.isNotEmpty()

    override fun initState() {
        unawaited { _loadAccountHistory() }
        // PORT: the desktop window lock after the first frame is not ported
        // (a no-op on Android).
    }

    override fun didUpdateWidget(oldWidget: LoginPageWidget) {
        if (oldWidget.accountHistoryStore !== widget.accountHistoryStore) {
            unawaited { _loadAccountHistory() }
        }
    }

    override fun dispose() {
        _login.dispose()
        _email.dispose()
        _password.dispose()
        _confirmPassword.dispose()
        _usernameAvailabilityDebounce?.cancel()
    }

    private suspend fun _loadAccountHistory() {
        try {
            val records = widget.accountHistoryStore.read()
            if (!mounted) return
            val lastLogin = lastLoginAccountRecord(records)
            setState {
                _accountHistory = records
                if (records.isEmpty()) _showAccountHistory = false
                if (!_registering &&
                    _login.text.isEmpty() &&
                    _password.text.isEmpty() &&
                    lastLogin != null
                ) {
                    _setText(_login, lastLogin.login)
                    _selectedHistoryLogin = lastLogin.login
                    if (lastLogin.remembersPassword) {
                        _setText(_password, lastLogin.password!!)
                        _rememberPassword = true
                    } else {
                        _password.clear()
                        _rememberPassword = false
                    }
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Local history is a convenience feature; the auth form stays usable.
        }
    }

    private suspend fun _submit() {
        if (_submitState.busy) return

        val result =
            authRequestFromForm(
                registering = _registering,
                username = if (_registering) _login.text else "",
                login = if (_registering) _email.text else _login.text,
                password = _password.text,
                confirmPassword = _confirmPassword.text,
                emailVerificationToken = if (_emailVerified) _emailVerificationToken ?: "" else "",
                language = widget.language,
            )
        val request = result.request
        if (request == null) {
            _showSubmitState(authSubmitInvalid(result.error))
            return
        }

        setState { _submitState = authSubmitStarted() }

        if (_registering) {
            val availabilityError = _ensureUsernameAvailable(request.username!!)
            if (!mounted) return
            if (availabilityError != null) {
                _showSubmitState(authSubmitInvalid(availabilityError))
                return
            }
        }

        try {
            widget.onSubmit(request, !_registering && _rememberPassword)
            if (!mounted) return
            if (!request.registering && !widget.submitPersistsAccountHistory) {
                _rememberSubmittedAccount(request)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            if (request.registering &&
                error is AuthException &&
                error.code == "email_verification_required"
            ) {
                _verifiedEmail = null
                _emailVerificationToken = null
            }
            _showSubmitState(authSubmitFailed(error, language = widget.language))
        }
    }

    private suspend fun _rememberSubmittedAccount(request: AuthRequest) {
        val updated =
            rememberLoginAccount(
                records = _accountHistory,
                login = request.login,
                password = request.password,
                rememberPassword = _rememberPassword,
            )
        setState {
            _accountHistory = updated
            _showAccountHistory = false
        }
        try {
            widget.accountHistoryStore.write(updated)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // The login already succeeded; failing to persist history is non-fatal.
        }
    }

    private fun _setMode(registering: Boolean) {
        if (_submitState.busy || _registering == registering) return
        if (registering) {
            _expandAndShowRegister()
            return
        }
        setState {
            _registering = false
            _resetUsernameAvailability()
            _submitState = AuthSubmitState()
            _showAccountHistory = false
        }
    }

    // PORT: Dart first awaits the desktop window resize (`_lockAuthSize`), a
    // no-op on Android.
    private fun _expandAndShowRegister() {
        if (!mounted || _submitState.busy) return
        setState {
            _registering = true
            _submitState = AuthSubmitState()
            _showAccountHistory = false
        }
        _scheduleUsernameAvailabilityCheck(_login.text)
    }

    private fun _toggleAccountHistory() {
        if (!_canShowAccountHistory) return
        setState { _showAccountHistory = !_showAccountHistory }
    }

    private fun _closeAccountHistory() {
        if (!_showAccountHistory) return
        setState { _showAccountHistory = false }
    }

    private fun _clearLoginInput() {
        if (_submitState.busy) return
        _login.clear()
        _password.clear()
        setState {
            _selectedHistoryLogin = null
            _rememberPassword = false
            _showAccountHistory = false
            _submitState = AuthSubmitState()
        }
    }

    private fun _selectAccountRecord(record: LoginAccountRecord) {
        _setText(_login, record.login)
        if (record.remembersPassword) {
            _setText(_password, record.password!!)
        } else {
            _password.clear()
        }
        setState {
            _selectedHistoryLogin = record.login
            _rememberPassword = record.remembersPassword
            _showAccountHistory = false
            _submitState = AuthSubmitState()
        }
    }

    private fun _deleteAccountRecord(record: LoginAccountRecord) {
        val updated = deleteLoginAccountRecord(records = _accountHistory, login = record.login)
        setState {
            _accountHistory = updated
            if (updated.isEmpty()) _showAccountHistory = false
            val selected = _selectedHistoryLogin
            if (selected != null && loginAccountsMatch(selected, record.login)) {
                _selectedHistoryLogin = null
            }
        }
        unawaited { _writeAccountHistory(updated) }
    }

    private suspend fun _writeAccountHistory(records: List<LoginAccountRecord>) {
        try {
            widget.accountHistoryStore.write(records)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // The in-memory UI can still continue if the convenience store fails.
        }
    }

    private fun _handleLoginChanged(value: String) {
        val selected = _selectedHistoryLogin
        if (selected == null || loginAccountsMatch(selected, value)) return
        setState {
            _selectedHistoryLogin = null
            if (_rememberPassword) _rememberPassword = false
        }
    }

    private fun _setRememberPassword(rememberPassword: Boolean) {
        if (_submitState.busy) return
        setState { _rememberPassword = rememberPassword }
    }

    /** Sets [text] with the cursor at its end (Flutter's `TextSelection.collapsed`). */
    private fun _setText(controller: TextEditingController, text: String) {
        controller.text = text
    }

    private fun _showSubmitState(state: AuthSubmitState) {
        setState { _submitState = state }
    }

    private suspend fun _showEmailVerification() {
        if (_submitState.busy || _checkingEmailAvailability) return
        val email = _email.text.trim()
        val error = registerEmailValidationError(email)
        if (error != null) {
            showFloatingErrorNotice(context, error)
            return
        }
        val checker = widget.checkEmailAvailability
        if (checker == null) {
            showFloatingErrorNotice(context, "暂时无法检测邮箱是否重复")
            return
        }
        setState { _checkingEmailAvailability = true }
        var available: Boolean?
        try {
            available = checker(email)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            available = null
        }
        if (!mounted) return
        if (!_registering || _email.text.trim() != email) {
            setState { _checkingEmailAvailability = false }
            return
        }
        if (available == null) {
            setState { _checkingEmailAvailability = false }
            showFloatingErrorNotice(context, "暂时无法检测邮箱是否重复")
            return
        }
        if (!available) {
            setState { _checkingEmailAvailability = false }
            showFloatingErrorNotice(context, "该邮箱已被其他用户使用")
            return
        }
        val controller = widget.emailVerificationController
        if (controller == null) {
            setState { _checkingEmailAvailability = false }
            showFloatingErrorNotice(context, "暂时无法使用邮箱验证功能")
            return
        }
        var challenge: EmailVerificationChallenge? = null
        try {
            challenge = controller.inspectOrStart(email)
            if (!mounted) return
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) {
                showFloatingErrorNotice(context, emailVerificationErrorMessage(error))
            }
        }
        if (!mounted) return
        setState { _checkingEmailAvailability = false }
        if (challenge == null || !_registering || _email.text.trim() != email) {
            return
        }
        val verificationToken =
            showEmailVerificationDialog(
                context = context,
                email = email,
                challenge = challenge,
                controller = controller,
            )
        if (verificationToken != null && mounted) {
            setState {
                _verifiedEmail = email.lowercase()
                _emailVerificationToken = verificationToken
            }
        }
    }

    private suspend fun _forgotPassword() {
        if (_submitState.busy || _checkingPasswordReset) return
        val controller = widget.passwordResetController
        if (controller == null) {
            showFloatingErrorNotice(context, "暂时无法使用忘记密码功能")
            return
        }
        if (_login.text.trim().isEmpty()) {
            showFloatingErrorNotice(context, "请先输入登录用户名或邮箱")
            return
        }
        setState { _checkingPasswordReset = true }
        showPasswordResetFlow(
            context = context,
            login = _login.text,
            controller = controller,
        )
        if (mounted) setState { _checkingPasswordReset = false }
    }

    private fun _handleRegisterEmailChanged(value: String) {
        val normalized = value.trim().lowercase()
        val emailEmpty = normalized.isEmpty()
        val verificationChanged = _verifiedEmail != null && _verifiedEmail != normalized
        if (_registerEmailEmpty == emailEmpty && !verificationChanged) return
        setState {
            _registerEmailEmpty = emailEmpty
            if (verificationChanged) {
                _verifiedEmail = null
                _emailVerificationToken = null
            }
        }
    }

    private fun _resetUsernameAvailability() {
        _usernameAvailabilityDebounce?.cancel()
        _usernameAvailabilityDebounce = null
        _usernameAvailabilityQuery = null
        _checkingUsernameAvailability = false
        _usernameAvailabilityError = null
    }

    private fun _handleRegisterUsernameChanged(value: String) {
        val username = value.trim()
        _usernameAvailabilityDebounce?.cancel()
        val formatError = loginUsernameValidationError(username)
        setState {
            _usernameAvailabilityQuery = null
            _checkingUsernameAvailability = false
            _usernameAvailabilityError = formatError
        }
        if (formatError != null || widget.checkUsernameAvailability == null) return
        _scheduleUsernameAvailabilityCheck(username)
    }

    private fun _scheduleUsernameAvailabilityCheck(value: String) {
        val username = value.trim()
        if (loginUsernameValidationError(username) != null ||
            widget.checkUsernameAvailability == null
        ) {
            return
        }
        _usernameAvailabilityDebounce?.cancel()
        if (mounted && _registering) {
            setState {
                _usernameAvailabilityQuery = username
                _checkingUsernameAvailability = true
                _usernameAvailabilityError = null
            }
        }
        _usernameAvailabilityDebounce =
            _scope?.startTimer(350.milliseconds) {
                unawaited { _checkUsernameAvailability(username) }
            }
    }

    private suspend fun _checkUsernameAvailability(username: String): String? {
        val checker = widget.checkUsernameAvailability ?: return null
        val normalized = username.trim()
        val formatError = loginUsernameValidationError(normalized)
        if (formatError != null) return formatError
        if (mounted) {
            setState {
                _usernameAvailabilityQuery = normalized
                _checkingUsernameAvailability = true
                _usernameAvailabilityError = null
            }
        }
        try {
            val available = checker(normalized)
            val error = if (available) null else "该登录用户名已被其他用户使用"
            if (mounted && _registering && _login.text.trim() == normalized) {
                setState {
                    _checkingUsernameAvailability = false
                    _usernameAvailabilityError = error
                }
            }
            return error
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            val error = "暂时无法检测登录用户名是否重复"
            if (mounted && _registering && _login.text.trim() == normalized) {
                setState {
                    _checkingUsernameAvailability = false
                    _usernameAvailabilityError = error
                }
            }
            return error
        }
    }

    private suspend fun _ensureUsernameAvailable(username: String): String? {
        val normalized = username.trim()
        if (_usernameAvailabilityQuery == normalized && !_checkingUsernameAvailability) {
            return _usernameAvailabilityError
        }
        _usernameAvailabilityDebounce?.cancel()
        return _checkUsernameAvailability(normalized)
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        // Android keeps both modes on the registration geometry. The four
        // registration fields are centered by that surface size, and the login
        // fields retain the same starting position when switching modes.
        val size = widget.sizeForMode(true, false)
        _AnnotatedSystemUiOverlayStyle(_androidAuthSystemUiOverlayStyle)
        Scaffold(backgroundColor = UiColors.surfaceLow, modifier = widget.modifier) {
            SafeArea {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val surfaceWidth = minOf(size.width, maxWidth)
                    val surfaceHeight = minOf(size.height, maxHeight)
                    val verticalSlack = maxHeight - surfaceHeight
                    val surfaceTop = (maxHeight / 2 - _authRegisterFieldsCenter).coerceIn(0.dp, verticalSlack)
                    Box(Modifier.fillMaxSize().padding(top = surfaceTop), contentAlignment = Alignment.TopCenter) {
                        Box(
                            Modifier
                                .testTag(authSurfaceTag)
                                .size(surfaceWidth, surfaceHeight)
                                .decoration(UiColors.surfaceLow)
                                .clipToBounds(),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(EdgeInsets.fromLTRB(24.dp, _authTitleBarTopInset, 24.dp, 7.dp)),
                            ) {
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    _buildForm(minHeight = maxHeight)
                                    if (_showAccountHistory && _canShowAccountHistory) {
                                        TapRegion(
                                            modifier = Modifier.fillMaxWidth().offset(y = _accountHistoryDropdownTop),
                                            groupId = _accountHistoryTapRegion,
                                        ) {
                                            _AccountHistoryDropdown(
                                                records = _accountHistory,
                                                onSelected = ::_selectAccountRecord,
                                                onDeleted = ::_deleteAccountRecord,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun _buildForm(minHeight: Dp) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight),
        ) {
            _buildTitleBar()
            Spacer(Modifier.height(_authTitleBarGap))
            SegmentedControl(
                expanded = true,
                height = _authSegmentedControlHeight,
                value = if (_registering) _AuthMode.register else _AuthMode.login,
                segments =
                    listOf(
                        Segment(value = _AuthMode.login, label = "登录", icon = Icons.AutoMirrored.Outlined.Login),
                        Segment(value = _AuthMode.register, label = "注册", icon = Icons.Outlined.PersonAddAlt1),
                    ),
                onChanged = { mode ->
                    if (_submitState.busy) return@SegmentedControl
                    _setMode(mode == _AuthMode.register)
                },
                modifier = Modifier.fillMaxWidth().testTag(authModeSwitchTag),
            )
            Spacer(Modifier.height(_authModeGap))
            TapRegion(
                modifier = Modifier.fillMaxWidth(),
                groupId = _accountHistoryTapRegion,
                onTapOutside = { _closeAccountHistory() },
            ) {
                _buildPrimaryIdentityInput()
            }
            if (_registering) {
                Spacer(Modifier.height(_authFieldGap))
                _buildRegisterEmailInput()
            }
            Spacer(Modifier.height(_authFieldGap))
            Input(
                modifier = Modifier.fillMaxWidth().testTag(authPasswordInputTag),
                controller = _password,
                height = _authInputHeight,
                enabled = !_submitState.busy,
                hintText = "密码",
                prefixIcon = Icons.Outlined.Lock,
                autofillHints = listOf(if (_registering) "newPassword" else "password"),
                obscureText = _obscurePassword,
                suffix = {
                    _PasswordVisibilityToggle(
                        obscure = _obscurePassword,
                        enabled = !_submitState.busy,
                        onPressed = { setState { _obscurePassword = !_obscurePassword } },
                    )
                },
                maxLines = 1,
                onSubmitted = if (_registering) null else ({ _ -> unawaited { _submit() } }),
            )
            if (!_registering) {
                _buildLoginTail()
            }
            if (_registering) {
                Spacer(Modifier.height(_authFieldGap))
                Input(
                    modifier = Modifier.fillMaxWidth().testTag(authConfirmPasswordInputTag),
                    controller = _confirmPassword,
                    height = _authInputHeight,
                    enabled = !_submitState.busy,
                    hintText = "确认密码",
                    prefixIcon = Icons.Outlined.Lock,
                    autofillHints = listOf("newPassword"),
                    obscureText = _obscureConfirmPassword,
                    suffix = {
                        _PasswordVisibilityToggle(
                            obscure = _obscureConfirmPassword,
                            enabled = !_submitState.busy,
                            onPressed = { setState { _obscureConfirmPassword = !_obscureConfirmPassword } },
                        )
                    },
                    maxLines = 1,
                    onSubmitted = { unawaited { _submit() } },
                )
                _buildRegisterTail()
            }
            Spacer(Modifier.height(_authActionGap))
            Button(
                modifier = Modifier.fillMaxWidth().testTag(authSubmitButtonTag),
                width = Dp.Infinity,
                tone = ButtonTone.primary,
                height = _authSubmitHeight,
                loading = _submitState.busy,
                onPressed = { unawaited { _submit() } },
            ) {
                Text(if (_registering) "创建账号" else "登录")
            }
        }
    }

    @Composable
    private fun _buildLoginTail() {
        val errorTop = _authPasswordErrorGap
        val rememberTop = errorTop + _authErrorHeight + _authErrorRememberGap
        val height = rememberTop + _authRememberHeight

        Box(Modifier.fillMaxWidth().height(height)) {
            Box(Modifier.fillMaxWidth().padding(top = rememberTop)) {
                _RememberPasswordRow(
                    value = _rememberPassword,
                    enabled = !_submitState.busy,
                    onChanged = ::_setRememberPassword,
                    onForgotPassword = { unawaited { _forgotPassword() } },
                    checkingPasswordReset = _checkingPasswordReset,
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = errorTop)) {
                _buildErrorSlot()
            }
        }
    }

    @Composable
    private fun _buildRegisterTail() {
        val errorTop = _authPasswordErrorGap
        val height = errorTop + _authErrorHeight

        Box(Modifier.fillMaxWidth().height(height)) {
            Box(Modifier.fillMaxWidth().padding(top = errorTop)) {
                _buildErrorSlot()
            }
        }
    }

    @Composable
    private fun _buildErrorSlot() {
        Box(
            Modifier.testTag("auth-error-slot").fillMaxWidth().height(_authErrorHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (_showingError) {
                Text(
                    _submitState.error!!,
                    // Align gives its child loose constraints: a taller line
                    // overflows the slot instead of being clipped to it.
                    modifier = Modifier.wrapContentHeight(unbounded = true),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        uiTextStyle(
                            color = UiColors.danger,
                            fontSize = 10.0,
                            height = 1.0,
                            fontWeight = FontWeight.W500,
                        ),
                )
            }
        }
    }

    @Composable
    private fun _buildPrimaryIdentityInput() {
        val suffix: (@Composable () -> Unit)? =
            when {
                _registering -> {
                    {
                        _RegisterUsernameValidityIndicator(
                            controller = _login,
                            checking = _checkingUsernameAvailability,
                            availabilityQuery = _usernameAvailabilityQuery,
                            availabilityError = _usernameAvailabilityError,
                            checksAvailability = widget.checkUsernameAvailability != null,
                        )
                    }
                }
                _canShowAccountHistory -> {
                    {
                        _AccountInputSuffix(
                            controller = _login,
                            expanded = _showAccountHistory,
                            enabled = !_submitState.busy,
                            onClear = ::_clearLoginInput,
                            onToggle = ::_toggleAccountHistory,
                        )
                    }
                }
                else -> null
            }
        Input(
            modifier = Modifier.fillMaxWidth().testTag(authIdentityInputTag),
            controller = _login,
            height = _authInputHeight,
            enabled = !_submitState.busy,
            hintText = if (_registering) "登录用户名" else "登录用户名或邮箱地址",
            prefixIcon = Icons.Filled.PersonOutline,
            suffix = suffix,
            autofillHints = if (_registering) listOf("username") else listOf("username", "email"),
            keyboardType = KeyboardType.Text,
            maxLines = 1,
            onChanged = if (_registering) ::_handleRegisterUsernameChanged else ::_handleLoginChanged,
            onSubmitted = { unawaited { _submit() } },
        )
    }

    @Composable
    private fun _buildRegisterEmailInput() {
        val suffix: (@Composable () -> Unit)? =
            when {
                _registerEmailEmpty -> null
                _checkingEmailAvailability -> {
                    { _InputCheckingIndicator(iconKey = "auth-email-checking", message = "正在检测邮箱是否可用") }
                }
                _emailVerified -> {
                    { _InputValidityIndicator(iconKey = "auth-email-verified", message = "邮箱已验证", valid = true) }
                }
                else -> {
                    {
                        EmailVerificationInputAction(
                            actionKey = "auth-email-verification-button",
                            label = "验证",
                            semanticsLabel = "验证邮箱",
                            enabled = !_submitState.busy,
                            onPressed = { unawaited { _showEmailVerification() } },
                        )
                    }
                }
            }
        Input(
            modifier = Modifier.fillMaxWidth().testTag(authEmailInputTag),
            controller = _email,
            height = _authInputHeight,
            enabled = !_submitState.busy,
            hintText = "邮箱地址",
            prefixIcon = Icons.Filled.AlternateEmail,
            suffix = suffix,
            autofillHints = listOf("email"),
            keyboardType = KeyboardType.Email,
            maxLines = 1,
            onChanged = ::_handleRegisterEmailChanged,
        )
    }

    @Composable
    private fun _buildTitleBar() {
        Box(Modifier.fillMaxWidth().height(_authTitleBarHeight), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                _AssetImage(
                    "assets/branding/auth_brand_icon.png",
                    modifier = Modifier.testTag("auth-brand-icon").size(_authBrandIconSize),
                )
                Spacer(Modifier.width(_authBrandGap))
                Text(
                    "Gang Chat",
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = _authTitleBarStyle,
                )
            }
        }
    }
}

/**
 * Flutter's `AnnotatedRegion<SystemUiOverlayStyle>` at the top of the screen:
 * the style is applied while the region is shown. Like Flutter, leaving the
 * region does not restore an earlier style.
 */
@Composable
private fun _AnnotatedSystemUiOverlayStyle(style: SystemUiOverlayStyle) {
    DisposableEffect(style) {
        SystemChrome.setSystemUIOverlayStyle(style)
        onDispose {}
    }
}

// Variants of the brand icon in `assets/branding/<ratio>x/` (Flutter's asset
// manifest for `assets/branding/auth_brand_icon.png`).
private val _assetVariantRatios = listOf(1.0, 1.25, 1.5, 1.75, 2.0, 3.0, 4.0)
private const val _kLowDprLimit = 2.0

/**
 * Flutter's `AssetImage._findBestVariant`: the exact ratio, else the nearer
 * neighbour, preferring the higher resolution below a 2.0 ratio.
 */
internal fun _assetVariantRatio(devicePixelRatio: Double): Double {
    if (_assetVariantRatios.contains(devicePixelRatio)) return devicePixelRatio
    val lower = _assetVariantRatios.lastOrNull { it < devicePixelRatio }
    val upper = _assetVariantRatios.firstOrNull { it > devicePixelRatio }
    if (lower == null) return upper!!
    if (upper == null) return lower
    return if (devicePixelRatio < _kLowDprLimit || devicePixelRatio > (lower + upper) / 2) upper else lower
}

/**
 * `Image.asset(key, fit: BoxFit.contain, filterQuality: FilterQuality.none)`
 * for a bundled asset with resolution variants.
 */
@Composable
private fun _AssetImage(key: String, modifier: Modifier = Modifier) {
    val androidContext = LocalContext.current
    val ratio = _assetVariantRatio(LocalDensity.current.density.toDouble())
    val bitmap =
        remember(key, ratio) {
            val directory = key.substringBeforeLast('/').removePrefix("assets/")
            val name = key.substringAfterLast('/')
            val path = if (ratio == 1.0) "$directory/$name" else "$directory/${_ratioDirectory(ratio)}/$name"
            try {
                androidContext.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } catch (_: java.io.IOException) {
                null
            }
        }
    if (bitmap == null) {
        Box(modifier)
        return
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}

private fun _ratioDirectory(ratio: Double): String {
    val text = if (ratio == Math.floor(ratio)) "${ratio.toInt()}.0" else ratio.toString()
    return "${text}x"
}

/** Reports mouse enter/exit (Flutter's `MouseRegion(onEnter:, onExit:)`). */
private fun Modifier._mouseHover(onHover: (Boolean) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.type == PointerType.Mouse }) continue
                when (event.type) {
                    PointerEventType.Enter -> onHover(true)
                    PointerEventType.Exit -> onHover(false)
                    else -> Unit
                }
            }
        }
    }

/** Test tag of the remember-password tap target (Dart's `ValueKey('auth-remember-password-hot-zone')`). */
const val authRememberPasswordHotZoneTag = "auth-remember-password-hot-zone"

@Composable
private fun _RememberPasswordRow(
    value: Boolean,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
    onForgotPassword: () -> Unit,
    checkingPasswordReset: Boolean,
) {
    Row(Modifier.fillMaxWidth().height(_authRememberHeight), verticalAlignment = Alignment.CenterVertically) {
        // The checkbox ignores pointers (Dart's `IgnorePointer`): a tap target
        // laid over the label and the box takes every tap.
        Box(Modifier.testTag(authRememberPasswordHotZoneTag)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "记住密码",
                    style =
                        uiTextStyle(
                            color = UiColors.textSecondary,
                            fontSize = 12.0,
                            fontWeight = FontWeight.W500,
                            letterSpacing = 0.0,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clearAndSetSemantics {}) {
                    UiCheckbox(
                        value = value,
                        enabled = enabled,
                        onChanged = if (enabled) ({ _ -> }) else null,
                    )
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .semantics {
                        role = Role.Button
                        toggleableState = ToggleableState(value)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = { onChanged(!value) },
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
        _AuthTextLink(
            label = "忘记密码？",
            enabled = enabled && !checkingPasswordReset,
            onPressed = onForgotPassword,
        )
    }
}

@Composable
private fun _RegisterUsernameValidityIndicator(
    controller: TextEditingController,
    checking: Boolean,
    availabilityQuery: String?,
    availabilityError: String?,
    checksAvailability: Boolean,
) {
    listen(controller)
    val username = controller.text.trim()
    if (username.isEmpty()) return
    val formatError = loginUsernameValidationError(username)
    val availabilityApplies = availabilityQuery == username
    val pending = formatError == null && availabilityApplies && checking
    val error = formatError ?: (if (availabilityApplies) availabilityError else null)
    val availabilityChecked = !checksAvailability || (availabilityApplies && !checking)
    val valid = error == null && availabilityChecked
    val message =
        if (pending) {
            "正在检测登录用户名是否可用"
        } else {
            error ?: (if (valid) "登录用户名可用" else "等待检测登录用户名是否可用")
        }
    if (pending || !valid && error == null) {
        _InputCheckingIndicator(iconKey = "auth-username-checking", message = message)
        return
    }
    _InputValidityIndicator(
        iconKey = if (valid) "auth-username-valid" else "auth-username-invalid",
        message = message,
        valid = valid,
    )
}

/** [iconKey] becomes the icon's test tag. */
@Composable
private fun _InputValidityIndicator(iconKey: String, message: String, valid: Boolean) {
    UiTooltip(message = message) {
        Box(Modifier.padding(horizontal = 8.dp)) {
            Icon(
                if (valid) Icons.Filled.CheckCircleOutline else Icons.Filled.ErrorOutline,
                modifier = Modifier.testTag(iconKey),
                size = 18.dp,
                color = if (valid) UiColors.accent else UiColors.danger,
            )
        }
    }
}

/** [iconKey] becomes the icon's test tag. */
@Composable
private fun _InputCheckingIndicator(iconKey: String, message: String) {
    UiTooltip(message = message) {
        Box(Modifier.padding(horizontal = 8.dp)) {
            Icon(
                Icons.Outlined.HourglassEmpty,
                modifier = Modifier.testTag(iconKey),
                size = 18.dp,
                color = UiColors.textSecondary,
            )
        }
    }
}

private class _AuthTextLinkWidget(val label: String, val enabled: Boolean, val onPressed: () -> Unit)

@Composable
private fun _AuthTextLink(label: String, enabled: Boolean, onPressed: () -> Unit) =
    StatefulWidget(_AuthTextLinkWidget(label, enabled, onPressed)) { _AuthTextLinkState() }

private class _AuthTextLinkState : ViewState<_AuthTextLinkWidget>() {
    private var _hovered = false

    private val _interactive: Boolean get() = widget.enabled

    private fun _setHovered(value: Boolean) {
        if (_hovered == value) return
        setState { _hovered = value }
    }

    private fun _handleTap() {
        if (!_interactive) return
        widget.onPressed()
    }

    @Composable
    override fun build() {
        val color =
            when {
                !_interactive -> UiColors.textMuted
                _hovered -> UiColors.controlAccent
                else -> UiColors.accent
            }
        Box(
            Modifier
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    if (!_interactive) disabled()
                }
                ._mouseHover(::_setHovered)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::_handleTap,
                )
                .padding(horizontal = 2.dp, vertical = 4.dp),
        ) {
            Text(
                widget.label,
                style = uiTextStyle(color = color, fontSize = 12.0, fontWeight = FontWeight.W500, letterSpacing = 0.0),
            )
        }
    }
}

@Composable
private fun _AccountInputSuffix(
    controller: TextEditingController,
    expanded: Boolean,
    enabled: Boolean,
    onClear: () -> Unit,
    onToggle: () -> Unit,
) {
    listen(controller)
    val canClear = enabled && controller.text.trim().isNotEmpty()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (canClear) {
            _AccountInputClearButton(enabled = enabled, onPressed = onClear)
        }
        _AccountHistoryToggle(expanded = expanded, onPressed = onToggle)
    }
}

@Composable
private fun _AccountInputClearButton(enabled: Boolean, onPressed: () -> Unit) {
    UiTooltip(message = "清除账号") {
        Box(
            Modifier
                .semantics { contentDescription = "清除账号" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onPressed,
                )
                .size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, size = 15.dp, color = UiColors.textMuted)
        }
    }
}

@Composable
private fun _AccountHistoryToggle(expanded: Boolean, onPressed: () -> Unit) {
    UiTooltip(message = if (expanded) "收起账号记录" else "展开账号记录") {
        Box(
            Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPressed,
                )
                .size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                size = 18.dp,
                color = UiColors.textMuted,
            )
        }
    }
}

/** Test tag of the account history dropdown (Dart's `ValueKey('auth-account-history-dropdown')`). */
const val authAccountHistoryDropdownTag = "auth-account-history-dropdown"

/** Test tag of the dropdown's scrollbar thumb, present while the list scrolls. */
const val authAccountHistoryScrollbarTag = "auth-account-history-scrollbar"

// The app theme's `ScrollbarThemeData` (crossAxisMargin 2, mainAxisMargin 4,
// borderStrong thumb, textMuted while dragged) with the dropdown's own
// thickness of 5 and Material's 48 minimum thumb length.
private val _scrollbarThickness = 5.dp
private val _scrollbarCrossAxisMargin = 2.dp
private val _scrollbarMainAxisMargin = 4.dp
private val _scrollbarMinThumbLength = 48.dp

private class _AccountHistoryDropdownWidget(
    val records: List<LoginAccountRecord>,
    val onSelected: (LoginAccountRecord) -> Unit,
    val onDeleted: (LoginAccountRecord) -> Unit,
)

@Composable
private fun _AccountHistoryDropdown(
    records: List<LoginAccountRecord>,
    onSelected: (LoginAccountRecord) -> Unit,
    onDeleted: (LoginAccountRecord) -> Unit,
) = StatefulWidget(_AccountHistoryDropdownWidget(records, onSelected, onDeleted)) { _AccountHistoryDropdownState() }

private class _AccountHistoryDropdownState : ViewState<_AccountHistoryDropdownWidget>() {
    private var _dragging = false

    @Composable
    override fun build() {
        val records = widget.records
        val rowCount = if (records.size > 4) 4 else records.size
        val scrollable = records.size > rowCount
        val listState = rememberLazyListState()
        val shape = RoundedCornerShape(UiRadii.md)
        Box(
            Modifier
                .testTag(authAccountHistoryDropdownTag)
                .fillMaxWidth()
                .boxShadow(
                    listOf(BoxShadow(color = Color.Black.copy(alpha = 0.26f), blurRadius = 18.dp, offset = androidx.compose.ui.unit.DpOffset(0.dp, 10.dp))),
                    shape,
                )
                .decoration(UiColors.surface, shape, UiColors.border)
                .height(_accountHistoryItemHeight * rowCount),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = if (scrollable) 7.dp else 0.dp),
                userScrollEnabled = scrollable,
            ) {
                items(records.size) { index ->
                    val record = records[index]
                    Box(Modifier.fillMaxWidth().height(_accountHistoryItemHeight)) {
                        _AccountHistoryItem(
                            record = record,
                            onSelected = { widget.onSelected(record) },
                            onDeleted = { widget.onDeleted(record) },
                        )
                    }
                }
            }
            if (scrollable) {
                _Scrollbar(listState, records.size)
            }
        }
    }

    /**
     * Material's `Scrollbar(thumbVisibility: true, interactive: true)` for a
     * fixed-extent list: an always-visible pill thumb that can be dragged.
     * Only the list's 7 dp end padding takes pointers, so the rows stay
     * tappable.
     */
    @Composable
    private fun androidx.compose.foundation.layout.BoxScope._Scrollbar(listState: LazyListState, itemCount: Int) {
        val density = LocalDensity.current
        val thumbColor = if (_dragging) UiColors.textMuted else UiColors.borderStrong
        Box(
            Modifier
                .matchParentSize()
                .testTag(authAccountHistoryScrollbarTag)
                .drawWithContent {
                    drawContent()
                    val geometry = _thumbGeometry(listState, itemCount, size.height, density.density) ?: return@drawWithContent
                    val thickness = _scrollbarThickness.toPx()
                    val left = size.width - thickness - _scrollbarCrossAxisMargin.toPx()
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(left, geometry.first),
                        size = Size(thickness, geometry.second),
                        cornerRadius = CornerRadius(thickness / 2),
                    )
                },
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(7.dp)
                .fillMaxHeight()
                .pointerInput(listState, itemCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val geometry =
                            _thumbGeometry(listState, itemCount, size.height.toFloat(), density.density)
                                ?: return@awaitEachGesture
                        val onThumb = down.position.y >= geometry.first && down.position.y <= geometry.first + geometry.second
                        if (!onThumb) return@awaitEachGesture
                        down.consume()
                        setState { _dragging = true }
                        val itemExtent = _accountHistoryItemHeight.toPx()
                        val maxScroll = itemExtent * itemCount - size.height
                        val track = size.height - _scrollbarMainAxisMargin.toPx() * 2
                        val scrollPerPixel = if (track > geometry.second) maxScroll / (track - geometry.second) else 0f
                        drag(down.id) { change ->
                            val delta = change.position.y - change.previousPosition.y
                            listState.dispatchRawDelta(delta * scrollPerPixel)
                            change.consume()
                        }
                        setState { _dragging = false }
                    }
                },
        )
    }

    /** The thumb's top and length in pixels (Flutter's `ScrollbarPainter`). */
    private fun _thumbGeometry(listState: LazyListState, itemCount: Int, viewport: Float, density: Float): Pair<Float, Float>? {
        val itemExtent = _accountHistoryItemHeight.value * density
        val content = itemExtent * itemCount
        val maxScroll = content - viewport
        if (maxScroll <= 0f) return null
        val mainAxisMargin = _scrollbarMainAxisMargin.value * density
        val pixels = listState.firstVisibleItemIndex * itemExtent + listState.firstVisibleItemScrollOffset
        val trackExtent = viewport - mainAxisMargin * 2
        val fractionVisible = ((viewport - mainAxisMargin * 2) / (content - mainAxisMargin * 2)).coerceIn(0f, 1f)
        val thumbExtent = max(min(trackExtent, _scrollbarMinThumbLength.value * density), trackExtent * fractionVisible)
        val scrollFraction = (pixels / maxScroll).coerceIn(0f, 1f)
        val thumbOffset = scrollFraction * (trackExtent - thumbExtent) + mainAxisMargin
        return thumbOffset to thumbExtent
    }
}

private class _AccountHistoryItemWidget(
    val record: LoginAccountRecord,
    val onSelected: () -> Unit,
    val onDeleted: () -> Unit,
)

@Composable
private fun _AccountHistoryItem(record: LoginAccountRecord, onSelected: () -> Unit, onDeleted: () -> Unit) =
    StatefulWidget(_AccountHistoryItemWidget(record, onSelected, onDeleted)) { _AccountHistoryItemState() }

private class _AccountHistoryItemState : ViewState<_AccountHistoryItemWidget>() {
    private var _hovered = false

    private fun _setHovered(hovered: Boolean) {
        if (_hovered == hovered) return
        setState { _hovered = hovered }
    }

    @Composable
    override fun build() {
        val background by animateColorAsState(
            if (_hovered) UiColors.selected else Color.Transparent,
            tween(90),
            label = "accountHistoryItem",
        )
        val imageUrl = AppConfigScope.of(context).resolveAssetUrl(widget.record.avatarUrl)
        Row(
            Modifier
                ._mouseHover(::_setHovered)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { widget.onSelected() },
                )
                .fillMaxWidth()
                .height(_accountHistoryItemHeight)
                .decoration(background)
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .testTag(authAccountHistoryAvatarTag)
                    .semantics {
                        _avatarImageUrl = imageUrl
                        _avatarDefaultKey = widget.record.defaultAvatarKey
                    },
            ) {
                _AccountHistoryAvatar(
                    label = widget.record.login,
                    imageUrl = imageUrl,
                    defaultAvatarKey = widget.record.defaultAvatarKey,
                    size = 22.dp,
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                widget.record.login,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = uiTextStyle(color = UiColors.text, fontSize = 13.0, fontWeight = FontWeight.W500, letterSpacing = 0.0),
            )
            if (widget.record.remembersPassword) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.Lock, size = 13.dp, color = UiColors.textMuted)
            }
            Spacer(Modifier.width(6.dp))
            _AccountHistoryDeleteButton(onPressed = widget.onDeleted)
        }
    }
}

private class _AccountHistoryDeleteButtonWidget(val onPressed: () -> Unit)

@Composable
private fun _AccountHistoryDeleteButton(onPressed: () -> Unit) =
    StatefulWidget(_AccountHistoryDeleteButtonWidget(onPressed)) { _AccountHistoryDeleteButtonState() }

private class _AccountHistoryDeleteButtonState : ViewState<_AccountHistoryDeleteButtonWidget>() {
    private var _hovered = false

    @Composable
    override fun build() {
        UiTooltip(message = "删除账号记录") {
            Box(
                Modifier
                    ._mouseHover { hovered -> setState { _hovered = hovered } }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { widget.onPressed() },
                    )
                    .size(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, size = 15.dp, color = if (_hovered) UiColors.danger else UiColors.textMuted)
            }
        }
    }
}

@Composable
private fun _PasswordVisibilityToggle(obscure: Boolean, enabled: Boolean, onPressed: () -> Unit) {
    val color = if (enabled) UiColors.textSecondary else UiColors.textMuted
    val label = if (obscure) "显示密码" else "隐藏密码"
    UiTooltip(message = label) {
        Box(
            Modifier
                .semantics { contentDescription = label }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onPressed,
                )
                .size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (obscure) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                size = 17.dp,
                color = color,
            )
        }
    }
}

/**
 * TODO(port): stand-in for `ui.Avatar(label:, imageUrl:, defaultAvatarKey:,
 * size:)`, which is being ported separately. Replace this call with
 * `com.gangchat.client.ui.Avatar` (same arguments) once it lands. Until then
 * it draws Avatar's fallback (initials on a neutral circle) and ignores
 * [imageUrl] and [defaultAvatarKey].
 */
@Suppress("UNUSED_PARAMETER")
@Composable
private fun _AccountHistoryAvatar(label: String, imageUrl: String?, defaultAvatarKey: String?, size: Dp) {
    val trimmed = label.trim()
    val initials = if (trimmed.isEmpty()) "?" else trimmed.take(2).uppercase()
    Box(
        Modifier
            .size(size)
            .decoration(UiColors.surface, CircleShape, Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = uiTextStyle(color = Color.White, fontSize = size.value * 0.36, fontWeight = FontWeight.W700),
        )
    }
}

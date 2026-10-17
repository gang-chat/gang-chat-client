package com.gangchat.client.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.PasswordResetController
import com.gangchat.client.auth.AuthException
import com.gangchat.client.auth.PasswordResetChallenge
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.ui.uiTheme
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.FilteringTextInputFormatter
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.LengthLimitingTextInputFormatter
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.showDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Test tag of the password reset verification dialog's frame. */
const val passwordResetVerificationDialogTag = "password-reset-verification-dialog"

/** Test tag of the new password dialog's frame. */
const val resetPasswordDialogTag = "password-reset-dialog"

suspend fun showPasswordResetFlow(
    context: BuildContext,
    login: String,
    controller: PasswordResetController,
): Boolean {
    val normalizedLogin = login.trim()
    if (normalizedLogin.isEmpty()) {
        showFloatingErrorNotice(context, "请先输入用户名或邮箱")
        return false
    }

    val resetToken =
        verifyPasswordResetEmail(
            context = context,
            login = normalizedLogin,
            controller = controller,
        )
    if (resetToken == null || !context.mounted) return false

    val completed =
        showDialog<Boolean>(context, barrierDismissible = false) { _ ->
            _ResetPasswordDialog(resetToken = resetToken, controller = controller)
        }
    if (completed == true && context.mounted) {
        showFloatingSuccessNotice(context, "密码已重置，请使用新密码登录")
        return true
    }
    return false
}

suspend fun verifyPasswordResetEmail(
    context: BuildContext,
    login: String,
    controller: PasswordResetController,
): String? {
    val normalizedLogin = login.trim()
    if (normalizedLogin.isEmpty()) {
        showFloatingErrorNotice(context, "请先输入用户名或邮箱")
        return null
    }
    var challenge: PasswordResetChallenge?
    val maskedEmail: String
    val retryAfterSeconds: Int
    try {
        val inspection = controller.inspect(normalizedLogin)
        challenge = inspection.reusableChallenge
        if (challenge == null && inspection.canSend) {
            challenge = controller.start(normalizedLogin)
        }
        maskedEmail = challenge?.maskedEmail ?: inspection.maskedEmail
        retryAfterSeconds = challenge?.retryAfterSeconds ?: inspection.retryAfterSeconds
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (context.mounted) {
            showFloatingErrorNotice(context, _passwordResetErrorMessage(error))
        }
        return null
    }
    if (!context.mounted) return null
    return showDialog<String>(context, barrierDismissible = false) { _ ->
        _PasswordResetVerificationDialog(
            login = normalizedLogin,
            challenge = challenge,
            maskedEmail = maskedEmail,
            retryAfterSeconds = retryAfterSeconds,
            controller = controller,
        )
    }
}

private fun _passwordResetErrorMessage(error: Any): String {
    if (error is AuthException) return error.message
    return "暂时无法完成密码重置，请稍后重试"
}

private class _PasswordResetVerificationDialogWidget(
    val login: String,
    val challenge: PasswordResetChallenge?,
    val maskedEmail: String,
    val retryAfterSeconds: Int,
    val controller: PasswordResetController,
)

@Composable
private fun _PasswordResetVerificationDialog(
    login: String,
    challenge: PasswordResetChallenge?,
    maskedEmail: String,
    retryAfterSeconds: Int,
    controller: PasswordResetController,
) = StatefulWidget(
    _PasswordResetVerificationDialogWidget(login, challenge, maskedEmail, retryAfterSeconds, controller),
) { _PasswordResetVerificationDialogState() }

private class _PasswordResetVerificationDialogState : ViewState<_PasswordResetVerificationDialogWidget>() {
    private val _code = TextEditingController()
    private var _timer: CompositionTimer? = null
    private var _challenge: PasswordResetChallenge? = null
    private lateinit var _maskedEmail: String
    private lateinit var _resendAvailableAt: DateTime
    private var _remainingSeconds = 0
    private var _resending = false
    private var _verifying = false
    private var _scope: CoroutineScope? = null

    // PORT: Dart starts the timer in initState; the composition's scope
    // exists from the first build, so the start waits for it.
    private var _timerPending = false

    override fun initState() {
        val challenge = widget.challenge
        _challenge = challenge
        _maskedEmail = challenge?.maskedEmail ?: widget.maskedEmail
        _applyCooldown(challenge?.retryAfterSeconds ?: widget.retryAfterSeconds)
    }

    private fun _applyChallenge(challenge: PasswordResetChallenge) {
        _challenge = challenge
        _maskedEmail = challenge.maskedEmail
        _applyCooldown(challenge.retryAfterSeconds)
    }

    private fun _applyCooldown(retryAfterSeconds: Int) {
        val seconds = if (retryAfterSeconds < 0) 0 else retryAfterSeconds
        _resendAvailableAt = DateTime.now().add(seconds.seconds)
        _remainingSeconds = seconds
        _startTimer()
    }

    private fun _startTimer() {
        _timer?.cancel()
        if (_remainingSeconds <= 0) return
        val scope = _scope
        if (scope == null) {
            _timerPending = true
            return
        }
        _timer =
            scope.startPeriodicTimer(1.seconds) {
                val remaining = _resendAvailableAt.difference(DateTime.now())
                val seconds =
                    if (remaining <= Duration.ZERO) 0 else ceil(remaining.inWholeMilliseconds / 1000.0).toInt()
                if (!mounted) return@startPeriodicTimer
                if (seconds == 0) _timer?.cancel()
                if (_remainingSeconds != seconds) {
                    setState { _remainingSeconds = seconds }
                }
            }
    }

    private suspend fun _resend() {
        if (_resending || _verifying || _remainingSeconds > 0) return
        setState { _resending = true }
        try {
            val currentChallenge = _challenge
            val challenge =
                if (currentChallenge == null) {
                    widget.controller.start(widget.login)
                } else {
                    widget.controller.resend(currentChallenge.id)
                }
            if (!mounted) return
            setState {
                _resending = false
                _applyChallenge(challenge)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState { _resending = false }
            showFloatingErrorNotice(context, _passwordResetErrorMessage(error))
        }
    }

    private suspend fun _verify() {
        if (_verifying || _resending) return
        val challenge = _challenge
        if (challenge == null) {
            showFloatingErrorNotice(context, "请先发送验证码")
            return
        }
        val code = _code.text.trim()
        if (!Regex("^\\d{6}$").containsMatchIn(code)) {
            showFloatingErrorNotice(context, "请输入 6 位数字验证码")
            return
        }
        setState { _verifying = true }
        try {
            val token = widget.controller.verify(challengeId = challenge.id, code = code)
            if (!mounted) return
            Navigator.of(context).pop(token)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState { _verifying = false }
            showFloatingErrorNotice(context, _passwordResetErrorMessage(error))
        }
    }

    override fun dispose() {
        _timer?.cancel()
        _code.dispose()
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            if (_timerPending) {
                _timerPending = false
                _startTimer()
            }
        }
        DialogFrame(
            title = "邮箱验证",
            icon = Icons.Outlined.MarkEmailRead,
            maxWidth = 420.dp,
            modifier = Modifier.testTag(passwordResetVerificationDialogTag),
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(
                        label = "取消",
                        onPressed = if (_verifying) null else ({ Navigator.of(context).pop() }),
                    ),
                    ResponsiveDialogAction(
                        label = "验证",
                        buttonKey = "password-reset-verify-button",
                        onPressed = if (_verifying || _resending || _challenge == null) null else ({ unawaited { _verify() } }),
                        loading = _verifying,
                        tone = ButtonTone.primary,
                    ),
                ),
            child = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "已发送验证码到该账号绑定的邮箱 $_maskedEmail",
                        modifier = Modifier.fillMaxWidth().testTag("password-reset-email-message"),
                        style = UiTypography.body.copyWith(color = UiColors.textSecondary),
                    )
                    Spacer(Modifier.height(14.dp))
                    Input(
                        modifier = Modifier.fillMaxWidth().testTag("password-reset-code-input"),
                        controller = _code,
                        hintText = "请输入验证码",
                        prefixIcon = Icons.Outlined.Password,
                        suffix = {
                            _InlineAction(
                                actionKey = "password-reset-resend-button",
                                label = if (_remainingSeconds > 0) "重新发送($_remainingSeconds)" else "发送验证码",
                                enabled = !_resending && !_verifying && _remainingSeconds <= 0,
                                loading = _resending,
                                onPressed = { unawaited { _resend() } },
                            )
                        },
                        keyboardType = KeyboardType.Number,
                        textInputAction = ImeAction.Done,
                        inputFormatters =
                            listOf(
                                FilteringTextInputFormatter.digitsOnly,
                                LengthLimitingTextInputFormatter(6),
                            ),
                        maxLines = 1,
                        onSubmitted = { unawaited { _verify() } },
                    )
                }
            },
        )
    }
}

private class _ResetPasswordDialogWidget(
    val resetToken: String,
    val controller: PasswordResetController,
)

@Composable
private fun _ResetPasswordDialog(resetToken: String, controller: PasswordResetController) =
    StatefulWidget(_ResetPasswordDialogWidget(resetToken, controller)) { _ResetPasswordDialogState() }

private class _ResetPasswordDialogState : ViewState<_ResetPasswordDialogWidget>() {
    private val _password = TextEditingController()
    private val _confirmation = TextEditingController()
    private var _obscurePassword = true
    private var _obscureConfirmation = true
    private var _submitting = false

    private suspend fun _submit() {
        if (_submitting) return
        val password = _password.text
        if (password.isEmpty() || _confirmation.text.isEmpty()) {
            showFloatingErrorNotice(context, "请完整填写新密码和确认密码")
            return
        }
        if (password.length < 8) {
            showFloatingErrorNotice(context, "新密码至少需要 8 个字符")
            return
        }
        if (password != _confirmation.text) {
            showFloatingErrorNotice(context, "两次输入的新密码不一致")
            return
        }
        setState { _submitting = true }
        try {
            widget.controller.complete(resetToken = widget.resetToken, newPassword = password)
            if (!mounted) return
            Navigator.of(context).pop(true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState { _submitting = false }
            showFloatingErrorNotice(context, _passwordResetErrorMessage(error))
        }
    }

    override fun dispose() {
        _password.dispose()
        _confirmation.dispose()
    }

    @Composable
    override fun build() {
        DialogFrame(
            title = "重置密码",
            icon = Icons.Outlined.LockReset,
            maxWidth = 420.dp,
            modifier = Modifier.testTag(resetPasswordDialogTag),
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(
                        label = "取消",
                        onPressed = if (_submitting) null else ({ Navigator.of(context).pop() }),
                    ),
                    ResponsiveDialogAction(
                        label = "重置密码",
                        buttonKey = "password-reset-submit-button",
                        onPressed = if (_submitting) null else ({ unawaited { _submit() } }),
                        loading = _submitting,
                        tone = ButtonTone.primary,
                    ),
                ),
            child = {
                Column(Modifier.fillMaxWidth()) {
                    Input(
                        modifier = Modifier.fillMaxWidth().testTag("password-reset-new-password"),
                        controller = _password,
                        hintText = "新密码",
                        prefixIcon = Icons.Outlined.Lock,
                        obscureText = _obscurePassword,
                        suffix = {
                            _PasswordVisibilityAction(
                                obscure = _obscurePassword,
                                onPressed = { setState { _obscurePassword = !_obscurePassword } },
                            )
                        },
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(10.dp))
                    Input(
                        modifier = Modifier.fillMaxWidth().testTag("password-reset-confirm-password"),
                        controller = _confirmation,
                        hintText = "确认密码",
                        prefixIcon = Icons.Outlined.Lock,
                        obscureText = _obscureConfirmation,
                        suffix = {
                            _PasswordVisibilityAction(
                                obscure = _obscureConfirmation,
                                onPressed = { setState { _obscureConfirmation = !_obscureConfirmation } },
                            )
                        },
                        maxLines = 1,
                        textInputAction = ImeAction.Done,
                        onSubmitted = { unawaited { _submit() } },
                    )
                }
            },
        )
    }
}

@Composable
private fun _InlineAction(
    actionKey: String,
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onPressed: () -> Unit,
) {
    Box(
        Modifier
            .testTag(actionKey)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onPressed,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = uiTheme().progressIndicatorColor,
                strokeWidth = 1.6.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Square,
            )
        } else {
            Text(label, style = UiTypography.label.copyWith(color = if (enabled) UiColors.accent else UiColors.textMuted))
        }
    }
}

@Composable
private fun _PasswordVisibilityAction(obscure: Boolean, onPressed: () -> Unit) {
    UiTooltip(message = if (obscure) "显示密码" else "隐藏密码") {
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
                if (obscure) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                size = 17.dp,
                color = UiColors.textSecondary,
            )
        }
    }
}

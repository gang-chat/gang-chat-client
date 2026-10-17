package com.gangchat.client.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Password
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.EmailVerificationController
import com.gangchat.client.auth.AuthException
import com.gangchat.client.auth.EmailVerificationChallenge
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.showDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Test tag of the email verification dialog's frame. */
const val emailVerificationDialogTag = "auth-email-verification-dialog"

suspend fun showEmailVerificationDialog(
    context: BuildContext,
    email: String,
    challenge: EmailVerificationChallenge,
    controller: EmailVerificationController,
): String? =
    showDialog<String>(context) { _ ->
        _EmailVerificationDialog(email = email, challenge = challenge, controller = controller)
    }

fun emailVerificationErrorMessage(error: Any): String {
    if (error is AuthException) return error.message
    return "邮箱验证失败，请稍后重试"
}

/**
 * The text action shown inside a verification code field. [actionKey]
 * becomes the action's test tag (Dart's `ValueKey` on its `GestureDetector`).
 */
@Composable
fun EmailVerificationInputAction(
    actionKey: String,
    label: String,
    semanticsLabel: String,
    enabled: Boolean,
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .testTag(actionKey)
            .semantics { contentDescription = semanticsLabel }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onPressed,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(label, style = UiTypography.label.copyWith(color = if (enabled) UiColors.accent else UiColors.textMuted))
    }
}

/**
 * Dart's `Timer.periodic(period, callback)` on a composition's coroutine
 * scope: like [com.gangchat.client.widgets.startTimer], it follows the
 * composition clock, so UI tests drive it with `mainClock`.
 */
internal fun CoroutineScope.startPeriodicTimer(period: Duration, callback: () -> Unit): CompositionTimer =
    CompositionTimer(
        launch {
            while (true) {
                delay(period)
                callback()
            }
        },
    )

private class _EmailVerificationDialogWidget(
    val email: String,
    val challenge: EmailVerificationChallenge,
    val controller: EmailVerificationController,
)

@Composable
private fun _EmailVerificationDialog(
    email: String,
    challenge: EmailVerificationChallenge,
    controller: EmailVerificationController,
) = StatefulWidget(_EmailVerificationDialogWidget(email, challenge, controller)) { _EmailVerificationDialogState() }

private class _EmailVerificationDialogState : ViewState<_EmailVerificationDialogWidget>() {
    private val _code = TextEditingController()
    private var _cooldownTimer: CompositionTimer? = null
    private lateinit var _challenge: EmailVerificationChallenge
    private lateinit var _resendAvailableAt: DateTime
    private var _remainingSeconds = 0
    private var _sending = false
    private var _verifying = false
    private var _scope: CoroutineScope? = null

    // PORT: Dart starts the cooldown timer in initState; the composition's
    // scope exists from the first build, so the start waits for it.
    private var _cooldownTimerPending = false

    private val _codeChanged: () -> Unit = {
        if (mounted) setState()
    }

    override fun initState() {
        _code.addListener(_codeChanged)
        _applyChallenge(widget.challenge)
        _startCooldownTimer()
    }

    private fun _applyChallenge(challenge: EmailVerificationChallenge) {
        _challenge = challenge
        _remainingSeconds = challenge.retryAfterSeconds
        _resendAvailableAt = DateTime.now().add(challenge.retryAfterSeconds.seconds)
    }

    private fun _currentRemainingSeconds(): Int {
        val milliseconds = _resendAvailableAt.difference(DateTime.now()).inWholeMilliseconds
        if (milliseconds <= 0) return 0
        return ceil(milliseconds / 1000.0).toInt()
    }

    private suspend fun _sendVerificationCode() {
        if (_remainingSeconds > 0 || _sending || _verifying) return
        setState { _sending = true }
        try {
            val challenge = widget.controller.resend(_challenge.id)
            if (!mounted) return
            setState {
                _sending = false
                _applyChallenge(challenge)
            }
            _startCooldownTimer()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState { _sending = false }
            showFloatingErrorNotice(context, emailVerificationErrorMessage(error))
        }
    }

    private suspend fun _verify() {
        if (_verifying || _sending) return
        val code = _code.text.trim()
        if (!Regex("^\\d{6}$").containsMatchIn(code)) {
            showFloatingErrorNotice(context, "请输入 6 位数字验证码")
            return
        }
        setState { _verifying = true }
        try {
            val token = widget.controller.verify(challengeId = _challenge.id, code = code)
            if (!mounted) return
            Navigator.of(context).pop(token)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState { _verifying = false }
            showFloatingErrorNotice(context, emailVerificationErrorMessage(error))
        }
    }

    private fun _startCooldownTimer() {
        _cooldownTimer?.cancel()
        if (_remainingSeconds <= 0) return
        val scope = _scope
        if (scope == null) {
            _cooldownTimerPending = true
            return
        }
        _cooldownTimer =
            scope.startPeriodicTimer(1.seconds) {
                val remaining = _currentRemainingSeconds()
                if (!mounted) return@startPeriodicTimer
                if (remaining <= 0) _cooldownTimer?.cancel()
                if (remaining != _remainingSeconds) {
                    setState { _remainingSeconds = remaining }
                }
            }
    }

    override fun dispose() {
        _cooldownTimer?.cancel()
        _code.removeListener(_codeChanged)
        _code.dispose()
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            if (_cooldownTimerPending) {
                _cooldownTimerPending = false
                _startCooldownTimer()
            }
        }
        DialogFrame(
            title = "邮箱验证",
            icon = Icons.Outlined.MarkEmailRead,
            maxWidth = 420.dp,
            modifier = Modifier.testTag(emailVerificationDialogTag),
            adaptiveActions =
                listOf(
                    ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                    ResponsiveDialogAction(
                        label = "验证",
                        onPressed = if (_verifying) null else ({ unawaited { _verify() } }),
                        tone = ButtonTone.primary,
                        loading = _verifying,
                    ),
                ),
            child = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "已发送验证码到您的邮箱 ${widget.email}",
                        modifier = Modifier.fillMaxWidth(),
                        style = UiTypography.body.copyWith(color = UiColors.textSecondary),
                    )
                    Spacer(Modifier.height(14.dp))
                    Input(
                        modifier = Modifier.fillMaxWidth().testTag("auth-email-verification-code"),
                        controller = _code,
                        hintText = "请输入验证码",
                        prefixIcon = Icons.Outlined.Password,
                        suffix = {
                            EmailVerificationInputAction(
                                actionKey = "auth-email-send-code-button",
                                label =
                                    when {
                                        _remainingSeconds > 0 -> "重新发送($_remainingSeconds)"
                                        _sending -> "发送中"
                                        else -> "发送验证码"
                                    },
                                semanticsLabel = if (_remainingSeconds > 0) "验证码发送冷却中" else "发送验证码",
                                enabled = _remainingSeconds <= 0 && !_sending && !_verifying,
                                onPressed = { unawaited { _sendVerificationCode() } },
                            )
                        },
                        keyboardType = KeyboardType.Number,
                        textInputAction = ImeAction.Done,
                        onSubmitted = { unawaited { _verify() } },
                        maxLines = 1,
                    )
                }
            },
        )
    }
}

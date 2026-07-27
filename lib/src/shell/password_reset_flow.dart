import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../app/password_reset_controller.dart';
import '../auth/auth_client.dart';
import '../ui/ui.dart';

Future<bool> showPasswordResetFlow({
  required BuildContext context,
  required String login,
  required PasswordResetController controller,
}) async {
  final normalizedLogin = login.trim();
  if (normalizedLogin.isEmpty) {
    showFloatingErrorNotice(context, '请先输入用户名或邮箱');
    return false;
  }

  final resetToken = await verifyPasswordResetEmail(
    context: context,
    login: normalizedLogin,
    controller: controller,
  );
  if (resetToken == null || !context.mounted) return false;

  final completed = await showDialog<bool>(
    context: context,
    barrierDismissible: false,
    builder: (context) =>
        _ResetPasswordDialog(resetToken: resetToken, controller: controller),
  );
  if (completed == true && context.mounted) {
    showFloatingSuccessNotice(context, '密码已重置，请使用新密码登录');
    return true;
  }
  return false;
}

Future<String?> verifyPasswordResetEmail({
  required BuildContext context,
  required String login,
  required PasswordResetController controller,
}) async {
  final normalizedLogin = login.trim();
  if (normalizedLogin.isEmpty) {
    showFloatingErrorNotice(context, '请先输入用户名或邮箱');
    return null;
  }
  PasswordResetChallenge? challenge;
  late String maskedEmail;
  late int retryAfterSeconds;
  try {
    final inspection = await controller.inspect(normalizedLogin);
    challenge = inspection.reusableChallenge;
    if (challenge == null && inspection.canSend) {
      challenge = await controller.start(normalizedLogin);
    }
    maskedEmail = challenge?.maskedEmail ?? inspection.maskedEmail;
    retryAfterSeconds =
        challenge?.retryAfterSeconds ?? inspection.retryAfterSeconds;
  } catch (error) {
    if (context.mounted) {
      showFloatingErrorNotice(context, _passwordResetErrorMessage(error));
    }
    return null;
  }
  if (!context.mounted) return null;
  return showDialog<String>(
    context: context,
    barrierDismissible: false,
    builder: (context) => _PasswordResetVerificationDialog(
      login: normalizedLogin,
      challenge: challenge,
      maskedEmail: maskedEmail,
      retryAfterSeconds: retryAfterSeconds,
      controller: controller,
    ),
  );
}

String _passwordResetErrorMessage(Object error) {
  if (error is AuthException) return error.message;
  return '暂时无法完成密码重置，请稍后重试';
}

class _PasswordResetVerificationDialog extends StatefulWidget {
  const _PasswordResetVerificationDialog({
    required this.login,
    required this.challenge,
    required this.maskedEmail,
    required this.retryAfterSeconds,
    required this.controller,
  });

  final String login;
  final PasswordResetChallenge? challenge;
  final String maskedEmail;
  final int retryAfterSeconds;
  final PasswordResetController controller;

  @override
  State<_PasswordResetVerificationDialog> createState() =>
      _PasswordResetVerificationDialogState();
}

class _PasswordResetVerificationDialogState
    extends State<_PasswordResetVerificationDialog> {
  final _code = TextEditingController();
  Timer? _timer;
  PasswordResetChallenge? _challenge;
  late String _maskedEmail;
  late DateTime _resendAvailableAt;
  int _remainingSeconds = 0;
  bool _resending = false;
  bool _verifying = false;

  @override
  void initState() {
    super.initState();
    final challenge = widget.challenge;
    _challenge = challenge;
    _maskedEmail = challenge?.maskedEmail ?? widget.maskedEmail;
    _applyCooldown(challenge?.retryAfterSeconds ?? widget.retryAfterSeconds);
  }

  void _applyChallenge(PasswordResetChallenge challenge) {
    _challenge = challenge;
    _maskedEmail = challenge.maskedEmail;
    _applyCooldown(challenge.retryAfterSeconds);
  }

  void _applyCooldown(int retryAfterSeconds) {
    final seconds = retryAfterSeconds < 0 ? 0 : retryAfterSeconds;
    _resendAvailableAt = DateTime.now().add(Duration(seconds: seconds));
    _remainingSeconds = seconds;
    _startTimer();
  }

  void _startTimer() {
    _timer?.cancel();
    if (_remainingSeconds <= 0) return;
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      final remaining = _resendAvailableAt.difference(DateTime.now());
      final seconds = remaining <= Duration.zero
          ? 0
          : (remaining.inMilliseconds / Duration.millisecondsPerSecond).ceil();
      if (!mounted) return;
      if (seconds == 0) _timer?.cancel();
      if (_remainingSeconds != seconds) {
        setState(() => _remainingSeconds = seconds);
      }
    });
  }

  Future<void> _resend() async {
    if (_resending || _verifying || _remainingSeconds > 0) return;
    setState(() => _resending = true);
    try {
      final currentChallenge = _challenge;
      final challenge = currentChallenge == null
          ? await widget.controller.start(widget.login)
          : await widget.controller.resend(currentChallenge.id);
      if (!mounted) return;
      setState(() {
        _resending = false;
        _applyChallenge(challenge);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _resending = false);
      showFloatingErrorNotice(context, _passwordResetErrorMessage(error));
    }
  }

  Future<void> _verify() async {
    if (_verifying || _resending) return;
    final challenge = _challenge;
    if (challenge == null) {
      showFloatingErrorNotice(context, '请先发送验证码');
      return;
    }
    final code = _code.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      showFloatingErrorNotice(context, '请输入 6 位数字验证码');
      return;
    }
    setState(() => _verifying = true);
    try {
      final token = await widget.controller.verify(
        challengeId: challenge.id,
        code: code,
      );
      if (!mounted) return;
      Navigator.of(context).pop(token);
    } catch (error) {
      if (!mounted) return;
      setState(() => _verifying = false);
      showFloatingErrorNotice(context, _passwordResetErrorMessage(error));
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _code.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: '邮箱验证',
      icon: Icons.mark_email_read_outlined,
      maxWidth: 420,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: _verifying ? null : () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          label: '验证',
          buttonKey: const ValueKey('password-reset-verify-button'),
          onPressed: _verifying || _resending || _challenge == null
              ? null
              : _verify,
          loading: _verifying,
          tone: ButtonTone.primary,
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            '已发送验证码到该账号绑定的邮箱 $_maskedEmail',
            key: const ValueKey('password-reset-email-message'),
            style: UiTypography.body.copyWith(color: UiColors.textSecondary),
          ),
          const SizedBox(height: 14),
          Input(
            key: const ValueKey('password-reset-code-input'),
            controller: _code,
            hintText: '请输入验证码',
            prefixIcon: Icons.password_outlined,
            suffix: _InlineAction(
              actionKey: const ValueKey('password-reset-resend-button'),
              label: _remainingSeconds > 0
                  ? '重新发送($_remainingSeconds)'
                  : '发送验证码',
              enabled: !_resending && !_verifying && _remainingSeconds <= 0,
              loading: _resending,
              onPressed: _resend,
            ),
            keyboardType: TextInputType.number,
            textInputAction: TextInputAction.done,
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              LengthLimitingTextInputFormatter(6),
            ],
            maxLines: 1,
            onSubmitted: (_) => _verify(),
          ),
        ],
      ),
    );
  }
}

class _ResetPasswordDialog extends StatefulWidget {
  const _ResetPasswordDialog({
    required this.resetToken,
    required this.controller,
  });

  final String resetToken;
  final PasswordResetController controller;

  @override
  State<_ResetPasswordDialog> createState() => _ResetPasswordDialogState();
}

class _ResetPasswordDialogState extends State<_ResetPasswordDialog> {
  final _password = TextEditingController();
  final _confirmation = TextEditingController();
  bool _obscurePassword = true;
  bool _obscureConfirmation = true;
  bool _submitting = false;

  Future<void> _submit() async {
    if (_submitting) return;
    final password = _password.text;
    if (password.isEmpty || _confirmation.text.isEmpty) {
      showFloatingErrorNotice(context, '请完整填写新密码和确认密码');
      return;
    }
    if (password.length < 8) {
      showFloatingErrorNotice(context, '新密码至少需要 8 个字符');
      return;
    }
    if (password != _confirmation.text) {
      showFloatingErrorNotice(context, '两次输入的新密码不一致');
      return;
    }
    setState(() => _submitting = true);
    try {
      await widget.controller.complete(
        resetToken: widget.resetToken,
        newPassword: password,
      );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (error) {
      if (!mounted) return;
      setState(() => _submitting = false);
      showFloatingErrorNotice(context, _passwordResetErrorMessage(error));
    }
  }

  @override
  void dispose() {
    _password.dispose();
    _confirmation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: '重置密码',
      icon: Icons.lock_reset_outlined,
      maxWidth: 420,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: _submitting ? null : () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          label: '重置密码',
          buttonKey: const ValueKey('password-reset-submit-button'),
          onPressed: _submitting ? null : _submit,
          loading: _submitting,
          tone: ButtonTone.primary,
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Input(
            key: const ValueKey('password-reset-new-password'),
            controller: _password,
            hintText: '新密码',
            prefixIcon: Icons.lock_outline,
            obscureText: _obscurePassword,
            suffix: _PasswordVisibilityAction(
              obscure: _obscurePassword,
              onPressed: () =>
                  setState(() => _obscurePassword = !_obscurePassword),
            ),
            maxLines: 1,
          ),
          const SizedBox(height: 10),
          Input(
            key: const ValueKey('password-reset-confirm-password'),
            controller: _confirmation,
            hintText: '确认密码',
            prefixIcon: Icons.lock_outline,
            obscureText: _obscureConfirmation,
            suffix: _PasswordVisibilityAction(
              obscure: _obscureConfirmation,
              onPressed: () =>
                  setState(() => _obscureConfirmation = !_obscureConfirmation),
            ),
            maxLines: 1,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _submit(),
          ),
        ],
      ),
    );
  }
}

class _InlineAction extends StatelessWidget {
  const _InlineAction({
    required this.actionKey,
    required this.label,
    required this.enabled,
    required this.loading,
    required this.onPressed,
  });

  final Key actionKey;
  final String label;
  final bool enabled;
  final bool loading;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      child: GestureDetector(
        key: actionKey,
        behavior: HitTestBehavior.opaque,
        onTap: enabled ? onPressed : null,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
          child: loading
              ? const SizedBox.square(
                  dimension: 14,
                  child: CircularProgressIndicator(strokeWidth: 1.6),
                )
              : Text(
                  label,
                  style: UiTypography.label.copyWith(
                    color: enabled ? UiColors.accent : UiColors.textMuted,
                  ),
                ),
        ),
      ),
    );
  }
}

class _PasswordVisibilityAction extends StatelessWidget {
  const _PasswordVisibilityAction({
    required this.obscure,
    required this.onPressed,
  });

  final bool obscure;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: obscure ? '显示密码' : '隐藏密码',
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onPressed,
        child: SizedBox.square(
          dimension: 24,
          child: Icon(
            obscure ? Icons.visibility_off_outlined : Icons.visibility_outlined,
            size: 17,
            color: UiColors.textSecondary,
          ),
        ),
      ),
    );
  }
}

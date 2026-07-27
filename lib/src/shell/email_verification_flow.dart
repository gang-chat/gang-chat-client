import 'dart:async';

import 'package:flutter/material.dart';

import '../app/email_verification_controller.dart';
import '../auth/auth_client.dart';
import '../ui/ui.dart';

Future<String?> showEmailVerificationDialog({
  required BuildContext context,
  required String email,
  required EmailVerificationChallenge challenge,
  required EmailVerificationController controller,
}) {
  return showDialog<String>(
    context: context,
    builder: (context) => _EmailVerificationDialog(
      email: email,
      challenge: challenge,
      controller: controller,
    ),
  );
}

String emailVerificationErrorMessage(Object error) {
  if (error is AuthException) return error.message;
  return '邮箱验证失败，请稍后重试';
}

class EmailVerificationInputAction extends StatelessWidget {
  const EmailVerificationInputAction({
    super.key,
    required this.actionKey,
    required this.label,
    required this.semanticsLabel,
    required this.enabled,
    required this.onPressed,
  });

  final Key actionKey;
  final String label;
  final String semanticsLabel;
  final bool enabled;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      enabled: enabled,
      label: semanticsLabel,
      onTap: enabled ? onPressed : null,
      child: MouseRegion(
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        child: GestureDetector(
          key: actionKey,
          behavior: HitTestBehavior.opaque,
          onTap: enabled ? onPressed : null,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
            child: Text(
              label,
              style: UiTypography.label.copyWith(
                color: enabled ? UiColors.accent : UiColors.textMuted,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _EmailVerificationDialog extends StatefulWidget {
  const _EmailVerificationDialog({
    required this.email,
    required this.challenge,
    required this.controller,
  });

  final String email;
  final EmailVerificationChallenge challenge;
  final EmailVerificationController controller;

  @override
  State<_EmailVerificationDialog> createState() =>
      _EmailVerificationDialogState();
}

class _EmailVerificationDialogState extends State<_EmailVerificationDialog> {
  final _code = TextEditingController();
  Timer? _cooldownTimer;
  late EmailVerificationChallenge _challenge;
  late DateTime _resendAvailableAt;
  int _remainingSeconds = 0;
  bool _sending = false;
  bool _verifying = false;

  @override
  void initState() {
    super.initState();
    _code.addListener(_codeChanged);
    _applyChallenge(widget.challenge);
    _startCooldownTimer();
  }

  void _codeChanged() {
    if (mounted) setState(() {});
  }

  void _applyChallenge(EmailVerificationChallenge challenge) {
    _challenge = challenge;
    _remainingSeconds = challenge.retryAfterSeconds;
    _resendAvailableAt = DateTime.now().add(
      Duration(seconds: challenge.retryAfterSeconds),
    );
  }

  int _currentRemainingSeconds() {
    final milliseconds = _resendAvailableAt
        .difference(DateTime.now())
        .inMilliseconds;
    if (milliseconds <= 0) return 0;
    return (milliseconds / 1000).ceil();
  }

  Future<void> _sendVerificationCode() async {
    if (_remainingSeconds > 0 || _sending || _verifying) return;
    setState(() => _sending = true);
    try {
      final challenge = await widget.controller.resend(_challenge.id);
      if (!mounted) return;
      setState(() {
        _sending = false;
        _applyChallenge(challenge);
      });
      _startCooldownTimer();
    } catch (error) {
      if (!mounted) return;
      setState(() => _sending = false);
      showFloatingErrorNotice(context, emailVerificationErrorMessage(error));
    }
  }

  Future<void> _verify() async {
    if (_verifying || _sending) return;
    final code = _code.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      showFloatingErrorNotice(context, '请输入 6 位数字验证码');
      return;
    }
    setState(() => _verifying = true);
    try {
      final token = await widget.controller.verify(
        challengeId: _challenge.id,
        code: code,
      );
      if (!mounted) return;
      Navigator.of(context).pop(token);
    } catch (error) {
      if (!mounted) return;
      setState(() => _verifying = false);
      showFloatingErrorNotice(context, emailVerificationErrorMessage(error));
    }
  }

  void _startCooldownTimer() {
    _cooldownTimer?.cancel();
    if (_remainingSeconds <= 0) return;
    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      final remaining = _currentRemainingSeconds();
      if (!mounted) return;
      if (remaining <= 0) _cooldownTimer?.cancel();
      if (remaining != _remainingSeconds) {
        setState(() => _remainingSeconds = remaining);
      }
    });
  }

  @override
  void dispose() {
    _cooldownTimer?.cancel();
    _code.removeListener(_codeChanged);
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
          onPressed: () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          label: '验证',
          onPressed: _verifying ? null : _verify,
          tone: ButtonTone.primary,
          loading: _verifying,
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            '已发送验证码到您的邮箱 ${widget.email}',
            style: UiTypography.body.copyWith(color: UiColors.textSecondary),
          ),
          const SizedBox(height: 14),
          Input(
            key: const ValueKey('auth-email-verification-code'),
            controller: _code,
            hintText: '请输入验证码',
            prefixIcon: Icons.password_outlined,
            suffix: EmailVerificationInputAction(
              actionKey: const ValueKey('auth-email-send-code-button'),
              label: _remainingSeconds > 0
                  ? '重新发送($_remainingSeconds)'
                  : _sending
                  ? '发送中'
                  : '发送验证码',
              semanticsLabel: _remainingSeconds > 0 ? '验证码发送冷却中' : '发送验证码',
              enabled: _remainingSeconds <= 0 && !_sending && !_verifying,
              onPressed: _sendVerificationCode,
            ),
            keyboardType: TextInputType.number,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _verify(),
            maxLines: 1,
          ),
        ],
      ),
    );
  }
}

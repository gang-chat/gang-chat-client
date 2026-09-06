part of 'settings_page.dart';

class _DeleteAccountDialog extends StatefulWidget {
  const _DeleteAccountDialog({required this.spec});

  final account_display.AccountDeletionConfirmationSpec spec;

  @override
  State<_DeleteAccountDialog> createState() => _DeleteAccountDialogState();
}

class _DeleteAccountDialogState extends State<_DeleteAccountDialog> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();
  final _undoController = UndoHistoryController();
  late final EditableTextContextMenuBuilder _contextMenuBuilder =
      _buildContextMenu;

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    _undoController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final spec = widget.spec;
    final matches = matchesConfirmationText(
      _controller.text,
      spec.expectedText,
    );
    return Dialog(
      backgroundColor: _primaryDarkLow,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(UiRadii.lg)),
        side: BorderSide(color: Color(0xFF3A2A2E)),
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(22, 20, 22, 18),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                spec.title,
                style: const TextStyle(
                  color: _danger,
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                spec.body,
                style: const TextStyle(
                  color: _textSecondary,
                  fontSize: 13,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 14),
              TextFieldEditingShortcuts(
                controller: _controller,
                focusNode: _focusNode,
                undoController: _undoController,
                child: TextField(
                  controller: _controller,
                  focusNode: _focusNode,
                  autofocus: true,
                  onChanged: (_) => setState(() {}),
                  cursorColor: _textSecondary,
                  undoController: _undoController,
                  contextMenuBuilder: _contextMenuBuilder,
                  style: const TextStyle(
                    color: _textPrimary,
                    fontWeight: FontWeight.w500,
                  ),
                  decoration: InputDecoration(
                    isDense: true,
                    hintText: spec.inputHint,
                  ),
                ),
              ),
              const SizedBox(height: 18),
              ResponsiveDialogActionBar(
                actions: [
                  ResponsiveDialogAction(
                    label: '取消',
                    onPressed: () => Navigator.of(context).pop(false),
                  ),
                  ResponsiveDialogAction(
                    label: spec.confirmLabel,
                    icon: Icons.delete_outline,
                    tone: ButtonTone.danger,
                    onPressed: matches
                        ? () => Navigator.of(context).pop(true)
                        : null,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildContextMenu(
    BuildContext context,
    EditableTextState editableTextState,
  ) {
    return buildTextFieldContextMenu(
      context,
      editableTextState,
      undoController: _undoController,
    );
  }
}

class _PasswordVisibilityToggle extends StatelessWidget {
  const _PasswordVisibilityToggle({
    required this.obscure,
    required this.enabled,
    required this.onPressed,
  });

  final bool obscure;
  final bool enabled;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final color = enabled ? _textSecondary : _textMuted;
    return UiTooltip(
      message: obscure ? '显示密码' : '隐藏密码',
      child: Semantics(
        button: true,
        enabled: enabled,
        label: obscure ? '显示密码' : '隐藏密码',
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: enabled ? onPressed : null,
          child: SizedBox(
            width: 32,
            height: 22,
            child: Icon(
              obscure
                  ? Icons.visibility_off_outlined
                  : Icons.visibility_outlined,
              size: 17,
              color: color,
            ),
          ),
        ),
      ),
    );
  }
}

class _UsernameValidityIndicator extends StatelessWidget {
  const _UsernameValidityIndicator({
    required this.error,
    required this.checking,
    required this.enabled,
  });

  final String? error;
  final bool checking;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final valid = error == null && !checking;
    final color = !enabled
        ? _textMuted
        : checking
        ? _textSecondary
        : valid
        ? _cyan
        : _danger;
    final label = checking ? '检测中' : (valid ? '合法' : '不合法');
    final message = checking ? '正在检测登录用户名是否可用' : (valid ? '登录用户名可用' : error!);
    return UiTooltip(
      message: message,
      child: Semantics(
        label: checking ? '正在检测登录用户名是否可用' : (valid ? '登录用户名可用' : '登录用户名不合法'),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              checking
                  ? Icons.hourglass_empty_outlined
                  : valid
                  ? Icons.check_circle_outline
                  : Icons.error_outline,
              size: 16,
              color: color,
            ),
            const SizedBox(width: 5),
            Text(
              label,
              style: TextStyle(
                color: color,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmailVerificationStatusIndicator extends StatelessWidget {
  const _EmailVerificationStatusIndicator({required this.checking});

  final bool checking;

  @override
  Widget build(BuildContext context) {
    final message = checking ? '正在检测邮箱是否可用' : '邮箱已验证';
    return UiTooltip(
      message: message,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Icon(
          checking
              ? Icons.hourglass_empty_outlined
              : Icons.check_circle_outline,
          key: ValueKey(
            checking ? 'settings-email-checking' : 'settings-email-verified',
          ),
          size: 18,
          color: checking ? _textSecondary : UiColors.accent,
        ),
      ),
    );
  }
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: const TextStyle(
        color: _textMuted,
        fontSize: 12,
        fontWeight: FontWeight.w600,
      ),
    );
  }
}

class _SettingsEmptyState extends StatelessWidget {
  const _SettingsEmptyState({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 74,
      child: Center(
        child: Text(
          text,
          textAlign: TextAlign.center,
          style: const TextStyle(color: _textMuted, fontSize: 13),
        ),
      ),
    );
  }
}

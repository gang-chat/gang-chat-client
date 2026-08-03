part of 'settings_page.dart';

class _SettingsNavigation extends StatelessWidget {
  const _SettingsNavigation({required this.selected, required this.onChanged});

  final SettingsSection selected;
  final ValueChanged<SettingsSection> onChanged;

  @override
  Widget build(BuildContext context) {
    return SegmentedControl<SettingsSection>(
      expanded: true,
      value: selected,
      onChanged: onChanged,
      segments: const [
        Segment(
          value: SettingsSection.profile,
          label: '用户资料',
          icon: Icons.badge_outlined,
        ),
        Segment(
          value: SettingsSection.preferences,
          label: '偏好设置',
          icon: Icons.tune_outlined,
        ),
        Segment(
          value: SettingsSection.security,
          label: '隐私和安全',
          icon: Icons.shield_outlined,
        ),
        Segment(
          value: SettingsSection.voice,
          label: '语音和视频',
          icon: Icons.graphic_eq,
        ),
        Segment(
          value: SettingsSection.stickers,
          label: '我的表情包',
          icon: Icons.emoji_emotions_outlined,
        ),
        Segment(
          value: SettingsSection.playlists,
          label: '我的歌单',
          icon: Icons.queue_music_outlined,
        ),
        Segment(
          value: SettingsSection.about,
          label: '关于Gang Chat',
          icon: Icons.info_outline,
        ),
      ],
    );
  }
}

class _SettingsGroup extends StatelessWidget {
  const _SettingsGroup({
    required this.title,
    required this.children,
    this.titleWidget,
    this.trailing,
    this.danger = false,
    this.spacing = 0,
  });

  final String title;
  final List<Widget> children;
  final Widget? titleWidget;
  final Widget? trailing;
  final bool danger;
  final double spacing;

  @override
  Widget build(BuildContext context) {
    // 默认保留既有手写间距；需要统一间距的页面可显式开启。
    return SettingsCard(
      title: title,
      titleWidget: titleWidget,
      trailing: trailing,
      danger: danger,
      spacing: spacing,
      children: children,
    );
  }
}

class _SettingsSubPanel extends StatefulWidget {
  const _SettingsSubPanel({
    super.key,
    required this.child,
    this.hoverable = false,
    this.highlighted = false,
    this.mouseCursor,
  });

  final Widget child;
  final bool hoverable;
  final bool highlighted;
  final MouseCursor? mouseCursor;

  @override
  State<_SettingsSubPanel> createState() => _SettingsSubPanelState();
}

class _SettingsSubPanelState extends State<_SettingsSubPanel> {
  bool _hovered = false;

  void _setHovered(bool hovered) {
    if (!widget.hoverable || _hovered == hovered) return;
    setState(() => _hovered = hovered);
  }

  @override
  Widget build(BuildContext context) {
    final highlighted = widget.highlighted || (widget.hoverable && _hovered);
    final panel = AnimatedContainer(
      duration: const Duration(milliseconds: 90),
      curve: Curves.easeOutCubic,
      decoration: BoxDecoration(
        color: highlighted ? UiColors.selected : _primaryDark,
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(
          color: highlighted ? UiColors.selectedBorder : _borderColor,
        ),
      ),
      child: Padding(padding: const EdgeInsets.all(14), child: widget.child),
    );
    if (!widget.hoverable) return panel;
    return MouseRegion(
      cursor: widget.mouseCursor ?? MouseCursor.defer,
      onEnter: (_) => _setHovered(true),
      onExit: (_) => _setHovered(false),
      child: panel,
    );
  }
}

class _LabeledTextField extends StatelessWidget {
  const _LabeledTextField({
    super.key,
    required this.label,
    required this.controller,
    this.enabled = true,
    this.maxLines = 1,
    this.obscureText = false,
    this.keyboardType,
    this.helperText,
    this.suffix,
    this.onChanged,
    this.onTogglePasswordVisibility,
  });

  final String label;
  final TextEditingController controller;
  final bool enabled;
  final int maxLines;
  final bool obscureText;
  final TextInputType? keyboardType;
  final String? helperText;
  final Widget? suffix;
  final ValueChanged<String>? onChanged;
  final VoidCallback? onTogglePasswordVisibility;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _FieldLabel(label),
        const SizedBox(height: 8),
        Input(
          controller: controller,
          hintText: '',
          enabled: enabled,
          obscureText: obscureText,
          keyboardType: keyboardType,
          minLines: 1,
          maxLines: obscureText ? 1 : maxLines,
          onChanged: onChanged,
          suffix: onTogglePasswordVisibility == null
              ? suffix
              : _PasswordVisibilityToggle(
                  obscure: obscureText,
                  enabled: enabled,
                  onPressed: onTogglePasswordVisibility!,
                ),
        ),
        if (helperText != null) ...[
          const SizedBox(height: 6),
          Text(
            helperText!,
            style: const TextStyle(color: _textMuted, fontSize: 12),
          ),
        ],
      ],
    );
  }
}

class _CopyableField extends StatelessWidget {
  const _CopyableField({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _FieldLabel(label),
        const SizedBox(height: 8),
        ReadOnlyTextBox(
          value: value,
          backgroundColor: _primaryDark,
          borderColor: _borderColor,
          style: const TextStyle(
            color: _textSecondary,
            fontSize: 14,
            fontWeight: FontWeight.w500,
          ),
        ),
      ],
    );
  }
}

class _ReadOnlyLine extends StatelessWidget {
  const _ReadOnlyLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(width: 110, child: _FieldLabel(label)),
        Expanded(
          child: Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: _textSecondary,
              fontSize: 14,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      ],
    );
  }
}

class _ToggleSetting extends StatelessWidget {
  const _ToggleSetting({
    required this.label,
    required this.value,
    required this.onChanged,
    this.enabled = true,
  });

  final String label;
  final bool value;
  final ValueChanged<bool> onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(child: _FieldLabel(label)),
        IgnorePointer(
          ignoring: !enabled,
          child: Opacity(
            opacity: enabled ? 1 : 0.55,
            child: UiSwitch(value: value, onChanged: onChanged),
          ),
        ),
      ],
    );
  }
}

class _SegmentOption {
  const _SegmentOption({required this.value, required this.label});

  final String value;
  final String label;
}

class _SegmentedSetting extends StatelessWidget {
  const _SegmentedSetting({
    super.key,
    required this.label,
    required this.value,
    required this.options,
    required this.onChanged,
    this.enabled = true,
  });

  final String label;
  final String value;
  final List<_SegmentOption> options;
  final ValueChanged<String> onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _FieldLabel(label),
        const SizedBox(height: 8),
        IgnorePointer(
          ignoring: !enabled,
          child: Opacity(
            opacity: enabled ? 1 : 0.55,
            child: SegmentedControl<String>(
              expanded: true,
              value: value,
              onChanged: onChanged,
              segments: [
                for (final option in options)
                  Segment(value: option.value, label: option.label),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _SessionList extends StatelessWidget {
  const _SessionList({required this.sessions, required this.loading});

  final List<UserSession> sessions;
  final bool loading;

  @override
  Widget build(BuildContext context) {
    final bodyState = account_display.sessionListBodyState(
      loading: loading,
      sessions: sessions,
    );
    return switch (bodyState) {
      account_display.SessionListBodyState.loading => const SizedBox(
        height: 82,
        child: Center(child: CircularProgressIndicator(color: _cyan)),
      ),
      account_display.SessionListBodyState.empty => const _SettingsEmptyState(
        text: '暂无近期账号活动',
      ),
      account_display.SessionListBodyState.results => Column(
        children: [
          for (final session in sessions)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: _primaryDark,
                  borderRadius: BorderRadius.circular(UiRadii.md),
                  border: Border.all(color: _borderColor),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        children: [
                          Icon(
                            session.isCurrent
                                ? Icons.radio_button_checked
                                : Icons.devices_other,
                            color: session.isCurrent ? _cyan : _textMuted,
                            size: 16,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              account_display.sessionDeviceLabel(session),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: _textPrimary,
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            account_display.sessionStateText(session),
                            style: TextStyle(
                              color: session.isActive ? _cyan : _textMuted,
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        account_display.sessionDetailText(session),
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: _textMuted,
                          fontSize: 12,
                          height: 1.35,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    };
  }
}

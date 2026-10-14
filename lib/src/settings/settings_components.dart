part of 'settings_page.dart';

const _settingsSectionSegments = [
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
  Segment(value: SettingsSection.voice, label: '语音和视频', icon: Icons.graphic_eq),
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
];

/// One searchable setting: where it lives and the words that find it.
class SettingsSearchEntry {
  const SettingsSearchEntry(
    this.section,
    this.label,
    this.keywords, {
    this.androidOnly = false,
    this.desktopOnly = false,
  });

  final SettingsSection section;
  final String label;
  final String keywords;
  final bool androidOnly;
  final bool desktopOnly;
}

const _settingsSearchIndex = <SettingsSearchEntry>[
  SettingsSearchEntry(SettingsSection.profile, '个人 UID', 'uid id 账号 编号'),
  SettingsSearchEntry(SettingsSection.profile, '登录用户名', 'username 账号 登录'),
  SettingsSearchEntry(SettingsSection.profile, '用户名', '昵称 名称 name 显示'),
  SettingsSearchEntry(SettingsSection.profile, '性别', 'gender 男 女 保密'),
  SettingsSearchEntry(SettingsSection.profile, '头像', 'avatar 上传 图片 照片'),
  SettingsSearchEntry(SettingsSection.profile, '签名', 'bio 简介 个性签名 介绍'),
  SettingsSearchEntry(
    SettingsSection.preferences,
    '关闭方式',
    'close 托盘 最小化 退出 窗口',
    desktopOnly: true,
  ),
  SettingsSearchEntry(
    SettingsSection.preferences,
    '系统通知',
    'notification 通知 消息 角标',
    androidOnly: true,
  ),
  SettingsSearchEntry(
    SettingsSection.preferences,
    '语言切换',
    'language 语言 中文 繁體 english',
  ),
  SettingsSearchEntry(SettingsSection.security, '邮箱绑定', 'email 邮件 验证'),
  SettingsSearchEntry(SettingsSection.security, '公开邮箱', 'email 隐私 公开'),
  SettingsSearchEntry(SettingsSection.security, '手机号绑定', 'phone 电话 手机'),
  SettingsSearchEntry(SettingsSection.security, '公开手机号', 'phone 隐私 公开'),
  SettingsSearchEntry(SettingsSection.security, '重置密码', 'password 密码 修改 更新'),
  SettingsSearchEntry(SettingsSection.security, '忘记密码', 'password 密码 找回'),
  SettingsSearchEntry(SettingsSection.security, '账号活动', 'session 登录 设备 会话 记录'),
  SettingsSearchEntry(SettingsSection.security, '注销账号', 'delete 删除 注销'),
  SettingsSearchEntry(SettingsSection.voice, '输入源', 'mic 麦克风 设备 输入'),
  SettingsSearchEntry(SettingsSection.voice, '输入音量', 'mic 麦克风 音量 测试'),
  SettingsSearchEntry(SettingsSection.voice, '输出源', 'speaker 耳机 扬声器 设备'),
  SettingsSearchEntry(SettingsSection.voice, '输出音量', 'volume 音量 声音 测试'),
  SettingsSearchEntry(
    SettingsSection.voice,
    '屏幕共享',
    'screen share 分辨率 帧率 画质 直播',
  ),
  SettingsSearchEntry(SettingsSection.stickers, '本地上传', 'sticker 表情 表情包 上传 图片'),
  SettingsSearchEntry(
    SettingsSection.stickers,
    '批量管理',
    'sticker 表情 删除 下载 置顶 筛选',
  ),
  SettingsSearchEntry(
    SettingsSection.playlists,
    '我的歌单',
    'playlist music 歌单 音乐 歌曲 导入',
  ),
  SettingsSearchEntry(SettingsSection.about, '版本信息', 'version 版本 发行 更新时间'),
  SettingsSearchEntry(SettingsSection.about, '自动提示更新', 'update 更新 提示'),
  SettingsSearchEntry(SettingsSection.about, '检查更新', 'update 更新 升级 下载'),
  SettingsSearchEntry(SettingsSection.about, '意见反馈', 'feedback 反馈 邮件 bug'),
];

/// Settings matching [query] by label, keyword or section name.
List<SettingsSearchEntry> settingsSearchMatches(
  String query, {
  required bool android,
}) {
  final needle = query.trim().toLowerCase();
  if (needle.isEmpty) return const [];
  String sectionLabel(SettingsSection section) {
    for (final segment in _settingsSectionSegments) {
      if (segment.value == section) return segment.label;
    }
    return '';
  }

  return [
    for (final entry in _settingsSearchIndex)
      if (!(entry.androidOnly && !android) &&
          !(entry.desktopOnly && android) &&
          ('${entry.label} ${entry.keywords} ${sectionLabel(entry.section)}')
              .toLowerCase()
              .contains(needle))
        entry,
  ];
}

class _SettingsSearchResultRow extends StatelessWidget {
  const _SettingsSearchResultRow({
    super.key,
    required this.entry,
    required this.query,
    required this.onPressed,
  });

  final SettingsSearchEntry entry;
  final String query;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final segment = _settingsSectionSegments.firstWhere(
      (segment) => segment.value == entry.section,
    );
    final index = _settingsSectionSegments.indexOf(segment) + 1;
    return PressableSurface(
      height: 54,
      onPressed: onPressed,
      backgroundColor: UiColors.card,
      hoverBackgroundColor: UiColors.surfaceRaised,
      borderColor: UiColors.border,
      borderRadius: UiRadii.md,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Row(
        children: [
          Container(
            width: 30,
            height: 30,
            decoration: BoxDecoration(
              color: UiColors.selected,
              borderRadius: BorderRadius.circular(UiRadii.sm),
            ),
            child: Icon(segment.icon, size: 16, color: UiColors.accent),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                HighlightedText(
                  text: entry.label,
                  query: query,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.body.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '${index.toString().padLeft(2, '0')} · ${segment.label}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.mono.copyWith(fontSize: 10.5),
                ),
              ],
            ),
          ),
          const Icon(
            Icons.arrow_forward_rounded,
            size: 16,
            color: UiColors.textMuted,
          ),
        ],
      ),
    );
  }
}

/// A settings group laid out as a form row: on wide pages the title sits in
/// its own left column beside the controls' panel; narrow pages stack them.
class _SettingsGroup extends StatelessWidget {
  const _SettingsGroup({
    required this.title,
    required this.children,
    this.trailing,
    this.danger = false,
  });

  final String title;
  final List<Widget> children;
  final Widget? trailing;
  final bool danger;

  static const double _wideMinWidth = 700;
  static const double _titleColumnWidth = 188;

  @override
  Widget build(BuildContext context) {
    final accent = danger ? UiColors.danger : UiColors.accent;
    final titleText = Text(
      title,
      style: UiTypography.subtitle.copyWith(
        color: danger ? UiColors.danger : UiColors.text,
      ),
    );
    final panel = DecoratedBox(
      decoration: BoxDecoration(
        color: danger ? const Color(0xFF1A1012) : UiColors.card,
        borderRadius: BorderRadius.circular(UiRadii.lg),
        border: Border.all(
          color: danger ? UiColors.dangerBorder : UiColors.border,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: children,
        ),
      ),
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < _wideMinWidth) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(2, 0, 2, 10),
                child: Row(
                  children: [
                    Container(
                      width: 3,
                      height: 14,
                      decoration: BoxDecoration(
                        color: accent,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(child: titleText),
                    ?trailing,
                  ],
                ),
              ),
              panel,
            ],
          );
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: _titleColumnWidth,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(2, 14, 18, 0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 18,
                      height: 3,
                      decoration: BoxDecoration(
                        color: accent,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(height: 10),
                    titleText,
                    if (trailing != null) ...[
                      const SizedBox(height: 10),
                      trailing!,
                    ],
                  ],
                ),
              ),
            ),
            Expanded(child: panel),
          ],
        );
      },
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

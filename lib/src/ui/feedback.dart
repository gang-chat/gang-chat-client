import 'dart:async';

import 'package:flutter/material.dart';

import 'button.dart';
import 'tokens.dart';

const floatingNoticeVisibleDuration = Duration(seconds: 3);
const _floatingNoticeAnimationDuration = Duration(milliseconds: 180);
const _floatingNoticeTopInset = 56.0;

enum FloatingNoticeTone { info, success, error }

class FloatingNotice {
  const FloatingNotice({
    required this.message,
    this.tone = FloatingNoticeTone.info,
    this.duration = floatingNoticeVisibleDuration,
    this.eventKey,
  });

  final String message;
  final FloatingNoticeTone tone;
  final Duration? duration;
  final Object? eventKey;

  FloatingNotice? get normalized {
    final trimmed = message.trim();
    if (trimmed.isEmpty) return null;
    if (trimmed == message) return this;
    return FloatingNotice(
      message: trimmed,
      tone: tone,
      duration: duration,
      eventKey: eventKey,
    );
  }

  @override
  bool operator ==(Object other) {
    return other is FloatingNotice &&
        other.message == message &&
        other.tone == tone &&
        other.duration == duration &&
        other.eventKey == eventKey;
  }

  @override
  int get hashCode => Object.hash(message, tone, duration, eventKey);
}

class AppNotificationController {
  AppNotificationController._();

  _AppNotificationHostState? _host;

  bool get isAttached => _host != null;

  void show(
    String message, {
    FloatingNoticeTone tone = FloatingNoticeTone.info,
    Duration? duration = floatingNoticeVisibleDuration,
  }) {
    final notice = FloatingNotice(
      message: message,
      tone: tone,
      duration: duration,
    ).normalized;
    if (notice == null) return;
    _host?._show(notice);
  }
}

class AppNotificationHost extends StatefulWidget {
  const AppNotificationHost({super.key, required this.child});

  final Widget child;

  static AppNotificationController? maybeOf(BuildContext context) {
    final scope = context
        .getElementForInheritedWidgetOfExactType<_AppNotificationScope>()
        ?.widget;
    return scope is _AppNotificationScope ? scope.controller : null;
  }

  static AppNotificationController of(BuildContext context) {
    final controller = maybeOf(context);
    assert(controller != null, 'No AppNotificationHost found in context.');
    return controller!;
  }

  @override
  State<AppNotificationHost> createState() => _AppNotificationHostState();
}

class _AppNotificationHostState extends State<AppNotificationHost> {
  static const _maxVisibleNotices = 3;

  late final AppNotificationController _controller =
      AppNotificationController._().._host = this;
  final List<_FloatingNoticeEntry> _notices = <_FloatingNoticeEntry>[];
  int _nextNoticeId = 0;

  void _show(FloatingNotice notice) {
    if (!mounted) return;
    setState(() {
      _notices.insert(
        0,
        _FloatingNoticeEntry(id: _nextNoticeId++, notice: notice),
      );
      if (_notices.length > _maxVisibleNotices) {
        _notices.removeRange(_maxVisibleNotices, _notices.length);
      }
    });
  }

  void _remove(int id) {
    if (!mounted) return;
    setState(() => _notices.removeWhere((entry) => entry.id == id));
  }

  @override
  void dispose() {
    _controller._host = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _AppNotificationScope(
      controller: _controller,
      child: Stack(
        fit: StackFit.expand,
        children: [
          widget.child,
          if (_notices.isNotEmpty)
            _FloatingNoticeStack(
              entries: List.unmodifiable(_notices),
              onDismissed: _remove,
            ),
        ],
      ),
    );
  }
}

class _FloatingNoticeEntry {
  const _FloatingNoticeEntry({required this.id, required this.notice});

  final int id;
  final FloatingNotice notice;
}

class _AppNotificationScope extends InheritedWidget {
  const _AppNotificationScope({required this.controller, required super.child});

  final AppNotificationController controller;

  @override
  bool updateShouldNotify(_AppNotificationScope oldWidget) {
    return !identical(controller, oldWidget.controller);
  }
}

class FloatingNoticeEmitter extends StatefulWidget {
  const FloatingNoticeEmitter({
    super.key,
    required this.notices,
    required this.child,
  });

  final List<FloatingNotice> notices;
  final Widget child;

  @override
  State<FloatingNoticeEmitter> createState() => _FloatingNoticeEmitterState();
}

class _FloatingNoticeEmitterState extends State<FloatingNoticeEmitter> {
  Set<FloatingNotice> _activeNotices = const {};

  @override
  void initState() {
    super.initState();
    _syncNotices();
  }

  @override
  void didUpdateWidget(FloatingNoticeEmitter oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncNotices();
  }

  void _syncNotices() {
    final next = <FloatingNotice>{
      for (final notice in widget.notices) ?notice.normalized,
    };
    final fresh = next.difference(_activeNotices);
    _activeNotices = next;
    if (fresh.isEmpty) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      for (final notice in fresh) {
        showFloatingNotice(
          context,
          notice.message,
          tone: notice.tone,
          duration: notice.duration,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

void showFloatingNotice(
  BuildContext context,
  String message, {
  FloatingNoticeTone tone = FloatingNoticeTone.info,
  Duration? duration = floatingNoticeVisibleDuration,
}) {
  final notice = FloatingNotice(
    message: message,
    tone: tone,
    duration: duration,
  ).normalized;
  if (notice == null) return;
  final controller = AppNotificationHost.maybeOf(context);
  if (controller != null && controller.isAttached) {
    controller.show(
      notice.message,
      tone: notice.tone,
      duration: notice.duration,
    );
    return;
  }
  _showStandaloneFloatingNotice(context, notice);
}

void showFloatingSuccessNotice(
  BuildContext context,
  String message, {
  Duration? duration = floatingNoticeVisibleDuration,
}) {
  showFloatingNotice(
    context,
    message,
    tone: FloatingNoticeTone.success,
    duration: duration,
  );
}

void showFloatingErrorNotice(
  BuildContext context,
  String message, {
  Duration? duration = floatingNoticeVisibleDuration,
}) {
  showFloatingNotice(
    context,
    message,
    tone: FloatingNoticeTone.error,
    duration: duration,
  );
}

void _showStandaloneFloatingNotice(
  BuildContext context,
  FloatingNotice notice,
) {
  final overlay = Overlay.maybeOf(context, rootOverlay: true);
  if (overlay == null) return;
  late final OverlayEntry entry;
  entry = OverlayEntry(
    builder: (context) => _StandaloneFloatingNotice(
      notice: notice,
      onFinished: () => entry.remove(),
    ),
  );
  overlay.insert(entry);
}

class _StandaloneFloatingNotice extends StatefulWidget {
  const _StandaloneFloatingNotice({
    required this.notice,
    required this.onFinished,
  });

  final FloatingNotice notice;
  final VoidCallback onFinished;

  @override
  State<_StandaloneFloatingNotice> createState() =>
      _StandaloneFloatingNoticeState();
}

class _StandaloneFloatingNoticeState extends State<_StandaloneFloatingNotice> {
  @override
  Widget build(BuildContext context) {
    return _FloatingNoticeStack(
      entries: [_FloatingNoticeEntry(id: 0, notice: widget.notice)],
      onDismissed: (_) => widget.onFinished(),
    );
  }
}

class _FloatingNoticeStack extends StatelessWidget {
  const _FloatingNoticeStack({
    required this.entries,
    required this.onDismissed,
  });

  final List<_FloatingNoticeEntry> entries;
  final ValueChanged<int> onDismissed;

  @override
  Widget build(BuildContext context) {
    final top = MediaQuery.paddingOf(context).top + _floatingNoticeTopInset;
    return Positioned(
      top: top,
      left: 16,
      right: 16,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (final entry in entries) ...[
            _AnimatedFloatingNotice(
              key: ValueKey(entry.id),
              notice: entry.notice,
              onDismissed: () => onDismissed(entry.id),
            ),
            if (entry != entries.last) const SizedBox(height: 8),
          ],
        ],
      ),
    );
  }
}

class _AnimatedFloatingNotice extends StatefulWidget {
  const _AnimatedFloatingNotice({
    super.key,
    required this.notice,
    required this.onDismissed,
  });

  final FloatingNotice notice;
  final VoidCallback onDismissed;

  @override
  State<_AnimatedFloatingNotice> createState() =>
      _AnimatedFloatingNoticeState();
}

class _AnimatedFloatingNoticeState extends State<_AnimatedFloatingNotice>
    with SingleTickerProviderStateMixin {
  late final AnimationController _animation = AnimationController(
    vsync: this,
    duration: _floatingNoticeAnimationDuration,
    reverseDuration: _floatingNoticeAnimationDuration,
  );
  late final Animation<double> _opacity = CurvedAnimation(
    parent: _animation,
    curve: Curves.easeOutCubic,
    reverseCurve: Curves.easeInCubic,
  );
  late final Animation<Offset> _offset =
      Tween<Offset>(begin: const Offset(0, -0.16), end: Offset.zero).animate(
        CurvedAnimation(
          parent: _animation,
          curve: Curves.easeOutCubic,
          reverseCurve: Curves.easeInCubic,
        ),
      );

  Timer? _visibleTimer;
  bool _closing = false;

  @override
  void initState() {
    super.initState();
    _animation.forward().whenComplete(() {
      if (!mounted || _closing) return;
      final duration = widget.notice.duration;
      if (duration != null) {
        _visibleTimer = Timer(duration, () => _close(animated: true));
      }
    });
  }

  void _close({required bool animated}) {
    if (_closing) return;
    _visibleTimer?.cancel();
    _visibleTimer = null;
    _closing = true;
    if (!animated) {
      widget.onDismissed();
      return;
    }
    _animation.reverse().whenComplete(() {
      if (mounted) widget.onDismissed();
    });
  }

  @override
  void dispose() {
    _visibleTimer?.cancel();
    _animation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: _opacity,
      child: SlideTransition(
        position: _offset,
        child: Toast(
          message: widget.notice.message,
          tone: widget.notice.tone,
          icon: _noticeIcon(widget.notice.tone),
          onClose: () => _close(animated: false),
        ),
      ),
    );
  }
}

IconData _noticeIcon(FloatingNoticeTone tone) {
  return switch (tone) {
    FloatingNoticeTone.success => Icons.check_circle_outline,
    FloatingNoticeTone.error => Icons.error_outline,
    FloatingNoticeTone.info => Icons.info_outline,
  };
}

Color _noticeAccent(FloatingNoticeTone tone) {
  return switch (tone) {
    FloatingNoticeTone.success => UiColors.accent,
    FloatingNoticeTone.error => UiColors.danger,
    FloatingNoticeTone.info => UiColors.controlAccent,
  };
}

class Toast extends StatelessWidget {
  const Toast({
    super.key,
    required this.message,
    this.icon,
    this.tone = FloatingNoticeTone.info,
    this.onClose,
  });

  final String message;
  final IconData? icon;
  final FloatingNoticeTone tone;
  final VoidCallback? onClose;

  @override
  Widget build(BuildContext context) {
    final accent = _noticeAccent(tone);
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 520),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: UiColors.surfaceRaised,
          borderRadius: BorderRadius.circular(UiRadii.lg),
          border: Border.all(color: UiColors.borderStrong),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.36),
              blurRadius: 24,
              offset: const Offset(0, 12),
            ),
          ],
        ),
        child: Padding(
          padding: EdgeInsets.fromLTRB(
            UiSpacing.md,
            UiSpacing.sm,
            onClose == null ? UiSpacing.md : UiSpacing.xs,
            UiSpacing.sm,
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              if (icon != null) ...[
                Icon(icon, size: 16, color: accent),
                const SizedBox(width: 8),
              ],
              Flexible(
                child: Text(
                  message,
                  style: UiTypography.label.copyWith(
                    color: UiColors.text,
                    decoration: TextDecoration.none,
                    decorationColor: Colors.transparent,
                    fontFamily: kClientFontFamily,
                    fontFamilyFallback: kClientFontFamilyFallback,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              if (onClose != null) ...[
                const SizedBox(width: UiSpacing.sm),
                _NoticeCloseButton(onPressed: onClose!),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _NoticeCloseButton extends StatefulWidget {
  const _NoticeCloseButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  State<_NoticeCloseButton> createState() => _NoticeCloseButtonState();
}

class _NoticeCloseButtonState extends State<_NoticeCloseButton> {
  bool _hovered = false;
  bool _pressed = false;

  void _setHovered(bool hovered) {
    if (_hovered == hovered) return;
    setState(() => _hovered = hovered);
  }

  void _setPressed(bool pressed) {
    if (_pressed == pressed) return;
    setState(() => _pressed = pressed);
  }

  @override
  Widget build(BuildContext context) {
    final active = _hovered || _pressed;
    return Semantics(
      button: true,
      label: '关闭',
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => _setHovered(true),
        onExit: (_) {
          _setHovered(false);
          _setPressed(false);
        },
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: (_) => _setPressed(true),
          onTapCancel: () => _setPressed(false),
          onTapUp: (_) => _setPressed(false),
          onTap: widget.onPressed,
          child: AnimatedContainer(
            key: const ValueKey('floating-notice-close-button'),
            duration: const Duration(milliseconds: 95),
            curve: Curves.easeOutCubic,
            width: 26,
            height: 26,
            decoration: BoxDecoration(
              color: _pressed
                  ? UiColors.surfacePressed
                  : active
                  ? UiColors.surface
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(UiRadii.md),
              border: Border.all(
                color: active ? UiColors.border : Colors.transparent,
              ),
            ),
            child: Icon(
              Icons.close,
              size: 15,
              color: active ? UiColors.textSecondary : UiColors.textMuted,
            ),
          ),
        ),
      ),
    );
  }
}

class ResponsiveDialogAction {
  const ResponsiveDialogAction({
    required this.label,
    required this.onPressed,
    this.buttonKey,
    this.icon,
    this.tone = ButtonTone.neutral,
    this.loading = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final Key? buttonKey;
  final IconData? icon;
  final ButtonTone tone;
  final bool loading;
}

class ResponsiveDialogActionBar extends StatelessWidget {
  const ResponsiveDialogActionBar({
    super.key,
    required this.actions,
    this.leadingActionCount = 0,
    this.expanded = false,
  });

  static const double _gap = 10;

  final List<ResponsiveDialogAction> actions;
  final int leadingActionCount;
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    if (actions.isEmpty) return const SizedBox.shrink();
    return LayoutBuilder(
      builder: (context, constraints) {
        final actionWidths = [
          for (final action in actions)
            Button.minimumWidthForLabel(
              context,
              label: action.label,
              hasIcon: action.icon != null,
            ),
        ];
        final requiredWithIcons =
            actionWidths.fold<double>(0, (sum, width) => sum + width) +
            (_gap * (actions.length - 1));
        final actionWidthsWithoutIcons = [
          for (final action in actions)
            Button.minimumWidthForLabel(context, label: action.label),
        ];
        final requiredWithoutIcons =
            actionWidthsWithoutIcons.fold<double>(
              0,
              (sum, width) => sum + width,
            ) +
            (_gap * (actions.length - 1));
        final showIcons =
            !constraints.maxWidth.isFinite ||
            requiredWithIcons <= constraints.maxWidth;
        final compact = !showIcons && constraints.maxWidth.isFinite;
        final stack =
            constraints.maxWidth.isFinite &&
            requiredWithoutIcons > constraints.maxWidth;
        final splitIndex = leadingActionCount.clamp(0, actions.length);

        Widget button(ResponsiveDialogAction action) {
          return Button(
            key: action.buttonKey,
            onPressed: action.onPressed,
            tone: action.tone,
            loading: action.loading,
            icon: showIcons && action.icon != null ? Icon(action.icon) : null,
            width: compact || expanded || stack ? double.infinity : null,
            child: Text(action.label),
          );
        }

        if (stack) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final entry in actions.indexed) ...[
                if (entry.$1 > 0) const SizedBox(height: _gap),
                button(entry.$2),
              ],
            ],
          );
        }

        if (compact || expanded) {
          if (constraints.maxWidth.isFinite) {
            // Use each action's real content width on every platform. Equal
            // widths can truncate a long label even when the row has enough
            // total space. Give every action its measured minimum first, then
            // share the spare width so a fitting row still fills the dialog.
            final widths = showIcons ? actionWidths : actionWidthsWithoutIcons;
            final buttonSpace =
                constraints.maxWidth - (_gap * (actions.length - 1));
            final naturalWidth = widths.fold<double>(
              0,
              (sum, width) => sum + width,
            );
            final extraPerButton =
                ((buttonSpace - naturalWidth) / actions.length).clamp(
                  0.0,
                  double.infinity,
                );
            return Row(
              children: [
                for (final entry in actions.indexed) ...[
                  if (entry.$1 > 0) const SizedBox(width: _gap),
                  SizedBox(
                    width: widths[entry.$1] + extraPerButton,
                    child: button(entry.$2),
                  ),
                ],
              ],
            );
          }
          return Row(
            children: [
              for (final entry in actions.indexed) ...[
                if (entry.$1 > 0) const SizedBox(width: _gap),
                Expanded(child: button(entry.$2)),
              ],
            ],
          );
        }

        final leading = actions.take(splitIndex).toList(growable: false);
        final trailing = actions.skip(splitIndex).toList(growable: false);
        return Row(
          mainAxisAlignment: splitIndex == 0
              ? MainAxisAlignment.end
              : MainAxisAlignment.start,
          children: [
            for (final entry in leading.indexed) ...[
              if (entry.$1 > 0) const SizedBox(width: _gap),
              button(entry.$2),
            ],
            if (leading.isNotEmpty && trailing.isNotEmpty) const Spacer(),
            for (final entry in trailing.indexed) ...[
              if (entry.$1 > 0) const SizedBox(width: _gap),
              button(entry.$2),
            ],
          ],
        );
      },
    );
  }
}

class DialogFrame extends StatelessWidget {
  const DialogFrame({
    super.key,
    required this.title,
    required this.child,
    this.icon,
    this.actions = const [],
    this.adaptiveActions = const [],
    this.actionBar,
    this.maxWidth = 480,
  }) : assert(actions.length == 0 || adaptiveActions.length == 0),
       assert(actions.length == 0 || actionBar == null),
       assert(adaptiveActions.length == 0 || actionBar == null);

  final String title;
  final IconData? icon;
  final Widget child;
  final List<Widget> actions;

  /// Standard dialog actions that adapt to the available width.
  ///
  /// Each label is measured with the active text scale. Icons are removed first
  /// when needed, then actions stack only if the complete labels still cannot
  /// fit on one row.
  final List<ResponsiveDialogAction> adaptiveActions;
  final Widget? actionBar;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final resolvedActionBar = adaptiveActions.isNotEmpty
        ? ResponsiveDialogActionBar(expanded: true, actions: adaptiveActions)
        : actionBar;
    final resolvedActions = adaptiveActions.isNotEmpty
        ? [
            for (final action in adaptiveActions)
              Button(
                key: action.buttonKey,
                onPressed: action.onPressed,
                tone: action.tone,
                loading: action.loading,
                icon: action.icon == null ? null : Icon(action.icon),
                child: Text(action.label),
              ),
          ]
        : actions;
    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: UiColors.surface,
            borderRadius: BorderRadius.circular(UiRadii.lg),
            border: Border.all(color: UiColors.border),
          ),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    if (icon != null) ...[
                      Icon(icon, size: 18, color: UiColors.accent),
                      const SizedBox(width: 8),
                    ],
                    Expanded(child: Text(title, style: UiTypography.title)),
                  ],
                ),
                const SizedBox(height: 14),
                child,
                if (resolvedActions.isNotEmpty ||
                    resolvedActionBar != null) ...[
                  const SizedBox(height: 18),
                  if (resolvedActionBar != null)
                    resolvedActionBar
                  else
                    Wrap(
                      alignment: WrapAlignment.end,
                      spacing: 10,
                      runSpacing: 10,
                      children: resolvedActions,
                    ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

Future<void> showUiDialog(
  BuildContext context, {
  required String title,
  required Widget child,
  IconData? icon,
}) {
  return showDialog<void>(
    context: context,
    builder: (context) => DialogFrame(
      title: title,
      icon: icon,
      actions: [
        Button(
          onPressed: () => Navigator.of(context).pop(),
          tone: ButtonTone.primary,
          child: const Text('完成'),
        ),
      ],
      child: child,
    ),
  );
}

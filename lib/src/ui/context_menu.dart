import 'package:flutter/material.dart';

import 'platform_gestures.dart';
import 'tokens.dart';

const double _contextMenuScreenPadding = 8;
const double _contextMenuWidth = 184;
const double _contextMenuMinItemHeight = 32;
const double _contextMenuHorizontalPadding = 12;

class UiContextMenuSection {
  const UiContextMenuSection(this.items);

  final List<UiContextMenuItem> items;
}

class UiContextMenuItem {
  const UiContextMenuItem({
    required this.label,
    this.icon,
    this.shortcut,
    this.selected = false,
    this.danger = false,
    required this.onPressed,
  });

  final String label;
  final IconData? icon;
  final String? shortcut;
  final bool selected;
  final bool danger;
  final VoidCallback? onPressed;
}

/// Gives application context-menu targets one platform-consistent trigger.
///
/// Secondary mouse click remains available on every platform. Android also
/// maps a touch/stylus hold to the same callback. The raw pointer tracker is a
/// fallback for targets containing editable text, whose native long-press
/// recognizer can otherwise win the gesture arena.
class UiContextMenuTriggerRegion extends StatefulWidget {
  const UiContextMenuTriggerRegion({
    super.key,
    required this.onTriggered,
    required this.child,
    this.onTap,
    this.behavior = HitTestBehavior.translucent,
  });

  final ValueChanged<Offset> onTriggered;
  final VoidCallback? onTap;
  final HitTestBehavior behavior;
  final Widget child;

  @override
  State<UiContextMenuTriggerRegion> createState() =>
      _UiContextMenuTriggerRegionState();
}

class _UiContextMenuTriggerRegionState
    extends State<UiContextMenuTriggerRegion> {
  late final UiAndroidLongPressTracker _androidLongPressTracker =
      UiAndroidLongPressTracker(onLongPress: _triggerAndroidLongPress);
  bool _androidLongPressEnabled = false;
  bool _triggeredForCurrentPointer = false;

  @override
  void dispose() {
    _androidLongPressTracker.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _androidLongPressEnabled =
        Theme.of(context).platform == TargetPlatform.android;
    return Listener(
      onPointerDown: (event) {
        _triggeredForCurrentPointer = false;
        _androidLongPressTracker.handlePointerDown(
          event,
          enabled: _androidLongPressEnabled,
        );
      },
      onPointerMove: _androidLongPressTracker.handlePointerMove,
      onPointerUp: (event) {
        _androidLongPressTracker.handlePointerUp(event);
        _triggeredForCurrentPointer = false;
      },
      onPointerCancel: (event) {
        _androidLongPressTracker.handlePointerCancel(event);
        _triggeredForCurrentPointer = false;
      },
      child: GestureDetector(
        behavior: widget.behavior,
        onTap: widget.onTap,
        onSecondaryTapDown: (details) =>
            _trigger(details.globalPosition, androidLongPress: false),
        onLongPressStart: _androidLongPressEnabled
            ? (details) =>
                  _trigger(details.globalPosition, androidLongPress: true)
            : null,
        child: widget.child,
      ),
    );
  }

  void _triggerAndroidLongPress(Offset globalPosition) {
    if (!mounted || !_androidLongPressEnabled) return;
    _trigger(globalPosition, androidLongPress: true);
  }

  void _trigger(Offset globalPosition, {required bool androidLongPress}) {
    if (androidLongPress) {
      if (_triggeredForCurrentPointer) return;
      _triggeredForCurrentPointer = true;
      ContextMenuController.removeAny();
    }
    widget.onTriggered(globalPosition);
  }
}

Future<void> showUiContextMenu(
  BuildContext context, {
  required Offset position,
  required List<UiContextMenuSection> sections,
}) {
  final visibleSections = [
    for (final section in sections)
      if (section.items.isNotEmpty) section,
  ];
  if (visibleSections.isEmpty) return Future.value();

  return Navigator.of(
    context,
    rootNavigator: true,
  ).push(_UiContextMenuRoute(position: position, sections: visibleSections));
}

class _UiContextMenuRoute extends PopupRoute<void> {
  _UiContextMenuRoute({required this.position, required this.sections});

  final Offset position;
  final List<UiContextMenuSection> sections;

  @override
  Color? get barrierColor => Colors.transparent;

  @override
  bool get barrierDismissible => true;

  @override
  String? get barrierLabel => '关闭菜单';

  @override
  Duration get transitionDuration => const Duration(milliseconds: 80);

  @override
  Duration get reverseTransitionDuration => const Duration(milliseconds: 60);

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) {
    final paddingAbove =
        MediaQuery.paddingOf(context).top + _contextMenuScreenPadding;
    final localAdjustment = Offset(_contextMenuScreenPadding, paddingAbove);
    return Padding(
      padding: EdgeInsets.fromLTRB(
        _contextMenuScreenPadding,
        paddingAbove,
        _contextMenuScreenPadding,
        _contextMenuScreenPadding,
      ),
      child: CustomSingleChildLayout(
        delegate: _UiContextMenuLayoutDelegate(position - localAdjustment),
        child: FadeTransition(
          opacity: animation,
          child: _UiContextMenuPanel(sections: sections),
        ),
      ),
    );
  }
}

class _UiContextMenuLayoutDelegate extends SingleChildLayoutDelegate {
  const _UiContextMenuLayoutDelegate(this.anchor);

  final Offset anchor;

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) {
    return BoxConstraints.loose(constraints.biggest);
  }

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    final maxX = size.width - childSize.width;
    final maxY = size.height - childSize.height;
    return Offset(anchor.dx.clamp(0, maxX), anchor.dy.clamp(0, maxY));
  }

  @override
  bool shouldRelayout(_UiContextMenuLayoutDelegate oldDelegate) {
    return oldDelegate.anchor != anchor;
  }
}

class _UiContextMenuPanel extends StatelessWidget {
  const _UiContextMenuPanel({required this.sections});

  final List<UiContextMenuSection> sections;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints.tightFor(width: _contextMenuWidth),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: UiColors.surfaceRaised,
          borderRadius: BorderRadius.circular(UiRadii.md),
          border: Border.all(color: UiColors.borderStrong),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.34),
              blurRadius: 18,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(UiRadii.md),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 5),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (
                  var sectionIndex = 0;
                  sectionIndex < sections.length;
                  sectionIndex++
                ) ...[
                  if (sectionIndex > 0) const _UiContextMenuDivider(),
                  for (final item in sections[sectionIndex].items)
                    _UiContextMenuItemWidget(item: item),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _UiContextMenuItemWidget extends StatefulWidget {
  const _UiContextMenuItemWidget({required this.item});

  final UiContextMenuItem item;

  @override
  State<_UiContextMenuItemWidget> createState() =>
      _UiContextMenuItemWidgetState();
}

class _UiContextMenuItemWidgetState extends State<_UiContextMenuItemWidget> {
  bool _hovered = false;

  bool get _enabled => widget.item.onPressed != null;

  @override
  Widget build(BuildContext context) {
    final foreground = !_enabled
        ? UiColors.textMuted
        : widget.item.danger
        ? UiColors.danger
        : widget.item.selected
        ? UiColors.accent
        : UiColors.text;
    final shortcut = widget.item.shortcut?.trim() ?? '';
    return MouseRegion(
      cursor: _enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      onEnter: (_) => _setHovered(true),
      onExit: (_) => _setHovered(false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: _enabled
            ? () async {
                final onPressed = widget.item.onPressed!;
                await Navigator.of(context).maybePop();
                onPressed();
              }
            : null,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 70),
          curve: Curves.easeOutCubic,
          constraints: const BoxConstraints(
            minHeight: _contextMenuMinItemHeight,
          ),
          padding: const EdgeInsets.symmetric(
            horizontal: _contextMenuHorizontalPadding,
            vertical: 6,
          ),
          color: _enabled && (_hovered || widget.item.selected)
              ? UiColors.selected
              : Colors.transparent,
          child: Row(
            children: [
              if (widget.item.icon case final icon?) ...[
                Icon(icon, size: 18, color: foreground),
                const SizedBox(width: 10),
              ],
              Expanded(
                child: Text(
                  widget.item.label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.body.copyWith(
                    inherit: false,
                    color: foreground,
                    fontFamily: kClientFontFamily,
                    fontFamilyFallback: kClientFontFamilyFallback,
                    fontSize: 13,
                    fontWeight: FontWeight.w400,
                    height: 1.2,
                    decoration: TextDecoration.none,
                  ),
                ),
              ),
              if (shortcut.isNotEmpty) ...[
                const SizedBox(width: 16),
                Text(
                  shortcut,
                  maxLines: 1,
                  style: UiTypography.label.copyWith(
                    inherit: false,
                    color: _enabled
                        ? UiColors.textSecondary
                        : UiColors.textMuted.withValues(alpha: 0.68),
                    fontFamily: kClientFontFamily,
                    fontFamilyFallback: kClientFontFamilyFallback,
                    fontSize: 12,
                    fontWeight: FontWeight.w400,
                    height: 1.2,
                    decoration: TextDecoration.none,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  void _setHovered(bool hovered) {
    if (_hovered == hovered) return;
    setState(() => _hovered = hovered);
  }
}

class _UiContextMenuDivider extends StatelessWidget {
  const _UiContextMenuDivider();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: SizedBox(
        width: double.infinity,
        height: 2,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: UiColors.accent.withValues(alpha: 0.78),
          ),
        ),
      ),
    );
  }
}

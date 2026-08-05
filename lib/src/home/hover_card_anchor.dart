import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

import '../ui/ui.dart';

const double hoverCardDefaultWidth = 248;
const double hoverCardDefaultGap = 10;

typedef HoverCardBuilder = Widget Function(BuildContext context);

EdgeInsets hoverCardOverlaySafeInsets(BuildContext context) {
  return Theme.of(context).platform == TargetPlatform.android
      ? MediaQuery.viewPaddingOf(context)
      : EdgeInsets.zero;
}

double hoverInfoCardWidth(
  BuildContext context,
  String message, {
  TextStyle? style,
  double horizontalPadding = 24,
  double minWidth = 132,
  double maxWidth = 360,
}) {
  const editableTextLayoutSlack = 18.0;
  final direction = Directionality.maybeOf(context) ?? TextDirection.ltr;
  final textStyle =
      style ?? UiTypography.body.copyWith(fontSize: 12, height: 1.38);
  final lines = message.trimRight().split('\n');
  var longestLineWidth = 0.0;
  for (final line in lines) {
    final painter = TextPainter(
      text: TextSpan(text: line.isEmpty ? ' ' : line, style: textStyle),
      textDirection: direction,
      maxLines: 1,
    )..layout();
    longestLineWidth = math.max(longestLineWidth, painter.width);
  }
  return (longestLineWidth + horizontalPadding + editableTextLayoutSlack).clamp(
    minWidth,
    maxWidth,
  );
}

class HoverCardTapRegionScope extends InheritedWidget {
  const HoverCardTapRegionScope({
    super.key,
    required this.tapRegionGroup,
    this.onOverlayActivityChanged,
    required super.child,
  });

  final Object tapRegionGroup;
  final ValueChanged<bool>? onOverlayActivityChanged;

  static HoverCardTapRegionScope? maybeOf(BuildContext context) {
    return context
        .dependOnInheritedWidgetOfExactType<HoverCardTapRegionScope>();
  }

  @override
  bool updateShouldNotify(HoverCardTapRegionScope oldWidget) {
    return tapRegionGroup != oldWidget.tapRegionGroup ||
        onOverlayActivityChanged != oldWidget.onOverlayActivityChanged;
  }
}

/// Selectable identity/detail text that keeps its owning hover card open while
/// the shared text context menu or Android selection handles are active.
///
/// User, room, and song cards use the same wrapper so right-click selection,
/// copy shortcuts, and touch selection cannot drift between card types.
class HoverCardSelectableText extends StatelessWidget {
  const HoverCardSelectableText({
    super.key,
    required this.value,
    required this.style,
    this.copyStartOffset = 0,
    this.maxLines = 1,
    this.textAlign = TextAlign.start,
  });

  final String value;
  final TextStyle style;
  final int copyStartOffset;
  final int maxLines;
  final TextAlign textAlign;

  @override
  Widget build(BuildContext context) {
    final hoverScope = HoverCardTapRegionScope.maybeOf(context);
    final start = copyStartOffset.clamp(0, value.length);
    return ReadOnlySelectableText(
      value: value,
      style: style,
      maxLines: maxLines,
      textAlign: textAlign,
      secondaryClickSelection: TextSelection(
        baseOffset: start,
        extentOffset: value.length,
      ),
      showSelectAllInContextMenu: false,
      contextMenuTapRegionGroupId: hoverScope?.tapRegionGroup,
      onContextMenuOpenChanged: hoverScope?.onOverlayActivityChanged,
    );
  }
}

/// Shared hover/tap shell for small profile cards anchored to an avatar.
class HoverCardAnchor extends StatefulWidget {
  const HoverCardAnchor({
    super.key,
    required this.child,
    required this.cardBuilder,
    this.onBeforeOpen,
    this.resetKey,
    this.cardWidth = hoverCardDefaultWidth,
    this.gap = hoverCardDefaultGap,
    this.closeDelay = const Duration(milliseconds: 120),
  });

  final Widget child;
  final HoverCardBuilder cardBuilder;
  final Future<void> Function()? onBeforeOpen;
  final Object? resetKey;
  final double cardWidth;
  final double gap;
  final Duration closeDelay;

  @override
  State<HoverCardAnchor> createState() => _HoverCardAnchorState();
}

class _HoverCardAnchorState extends State<HoverCardAnchor> {
  final GlobalKey _anchorKey = GlobalKey();
  final GlobalKey _cardKey = GlobalKey();
  final OverlayPortalController _portal = OverlayPortalController();
  final Object _rootTapRegionGroup = Object();
  final Object _overlayActivityToken = Object();

  late final _HoverCardCoordinator _coordinator;
  _HoverCardCoordinator? _parentCoordinator;
  _HoverCardAnchorState? _parentAnchor;
  Object? _inheritedTapRegionGroup;
  bool _overAnchor = false;
  bool _overCard = false;
  bool _pinned = false;
  bool _portalVisible = false;
  bool _advertisedActiveToParent = false;
  int? _deferredOutsidePointer;
  Offset? _deferredOutsideStart;
  bool _deferredOutsideMoved = false;
  Timer? _closeTimer;
  Future<void>? _openFuture;

  Object get _tapRegionGroup => _inheritedTapRegionGroup ?? _rootTapRegionGroup;

  bool get _wantsOpen =>
      _pinned || _overAnchor || _overCard || _coordinator.hasActiveDescendants;

  bool get _keepsParentOpen =>
      _wantsOpen || _portalVisible || _openFuture != null;

  @override
  void initState() {
    super.initState();
    _coordinator = _HoverCardCoordinator(_handleDescendantActivityChanged);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final chainScope = _HoverCardChainScope.maybeOf(context);
    final nextParent = chainScope?.coordinator;
    if (nextParent != _parentCoordinator) {
      if (_advertisedActiveToParent) {
        _parentCoordinator?.setDescendantActive(this, false);
        _advertisedActiveToParent = false;
      }
      _parentCoordinator?.releaseOpenChild(this);
      _parentCoordinator = nextParent;
    }
    _parentAnchor = chainScope?.anchor;
    final tapRegionScope = HoverCardTapRegionScope.maybeOf(context);
    _inheritedTapRegionGroup =
        chainScope?.tapRegionGroup ?? tapRegionScope?.tapRegionGroup;
    _syncParentActivity();
  }

  @override
  void didUpdateWidget(covariant HoverCardAnchor oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.resetKey == widget.resetKey) return;
    _dismiss();
  }

  @override
  void dispose() {
    if (_advertisedActiveToParent) {
      _parentCoordinator?.setDescendantActive(this, false);
      _advertisedActiveToParent = false;
    }
    _parentCoordinator?.releaseOpenChild(this);
    _clearDeferredOutsidePointerRoute();
    _closeTimer?.cancel();
    super.dispose();
  }

  void _enterAnchor() {
    _overAnchor = true;
    _open();
    _syncParentActivity();
  }

  void _exitAnchor() {
    _overAnchor = false;
    _syncParentActivity();
    _scheduleClose();
  }

  void _enterCard() {
    _overCard = true;
    _closeTimer?.cancel();
    _syncParentActivity();
  }

  void _exitCard() {
    _overCard = false;
    _syncParentActivity();
    _scheduleClose();
  }

  void _open() {
    _closeTimer?.cancel();
    final beforeOpen = widget.onBeforeOpen;
    if (beforeOpen == null) {
      _showPortal();
      return;
    }
    final existing = _openFuture;
    if (existing != null) return;

    final future = Future<void>.sync(beforeOpen);
    _openFuture = future;
    _syncParentActivity();
    unawaited(
      future.catchError((_) {}).whenComplete(() {
        if (!mounted || _openFuture != future) return;
        _openFuture = null;
        if (_wantsOpen) _showPortal();
        _syncParentActivity();
      }),
    );
  }

  void _showPortal() {
    _parentCoordinator?.showOnlyChild(this);
    if (_portalVisible && _portal.isShowing) return;
    _portalVisible = true;
    _showPortalController();
    _syncParentActivity();
  }

  void _pinOpen() {
    if (_pinned) {
      _pinned = false;
      _closeTimer?.cancel();
      if (_overAnchor || _overCard) {
        _syncParentActivity();
      } else {
        _hidePortal();
      }
      return;
    }
    _pinned = true;
    _open();
    _syncParentActivity();
  }

  void _hidePortal() {
    _coordinator.dismissOpenChild();
    _portalVisible = false;
    _parentCoordinator?.releaseOpenChild(this);
    _hidePortalController();
    _syncParentActivity();
  }

  void _showPortalController() {
    _mutatePortalWhenAllowed(() {
      if (!mounted || !_portalVisible || _portal.isShowing) return;
      _portal.show();
    });
  }

  void _hidePortalController() {
    _mutatePortalWhenAllowed(() {
      if (!mounted || _portalVisible || !_portal.isShowing) return;
      _portal.hide();
    });
  }

  void _mutatePortalWhenAllowed(VoidCallback mutation) {
    if (SchedulerBinding.instance.schedulerPhase ==
        SchedulerPhase.persistentCallbacks) {
      SchedulerBinding.instance.addPostFrameCallback((_) => mutation());
      return;
    }
    mutation();
  }

  void _dismiss() {
    _pinned = false;
    _overAnchor = false;
    _overCard = false;
    _closeTimer?.cancel();
    _hidePortal();
  }

  void _dismissFromPeer() {
    _pinned = false;
    _overAnchor = false;
    _overCard = false;
    _closeTimer?.cancel();
    _hidePortal();
  }

  void _scheduleClose() {
    if (_pinned) return;
    _closeTimer?.cancel();
    _closeTimer = Timer(widget.closeDelay, () {
      if (!mounted || _wantsOpen) return;
      _portalVisible = false;
      _hidePortalController();
      _syncParentActivity();
    });
  }

  void _handleDescendantActivityChanged() {
    if (!mounted) return;
    if (_coordinator.hasActiveDescendants) {
      _closeTimer?.cancel();
    } else {
      _scheduleClose();
    }
    _syncParentActivity();
  }

  void _handleOverlayActivityChanged(bool active) {
    _coordinator.setDescendantActive(_overlayActivityToken, active);
    if (active) {
      _closeTimer?.cancel();
    } else {
      _scheduleClose();
    }
    _syncParentActivity();
  }

  void _syncParentActivity() {
    final parent = _parentCoordinator;
    if (parent == null) return;
    final active = _keepsParentOpen;
    if (active == _advertisedActiveToParent) return;
    parent.setDescendantActive(this, active);
    _advertisedActiveToParent = active;
  }

  void _handleTapInside(PointerDownEvent event) {
    if (_parentAnchor != null) return;
    if (_openAnchorChainContains(event.position)) return;

    final target = _deepestOpenCardAt(event.position);
    if (target == null) {
      if (_coordinator.hasActiveDescendants) return;
      _dismiss();
      return;
    }
    _dismissChainAfter(target);
  }

  void _handleTapOutside(PointerDownEvent event) {
    final isAndroid = Theme.of(context).platform == TargetPlatform.android;
    if (!isAndroid || !_coordinator.hasActiveDescendants) {
      _dismiss();
      return;
    }

    // Android text selection handles live in a separate Overlay. Their pointer
    // events therefore look like outside taps to this card even though they
    // belong to an active text-selection interaction. Wait for this pointer to
    // finish: a drag belongs to the handle and keeps the card mounted, while a
    // regular outside tap still dismisses the card with one interaction.
    _trackDeferredOutsidePointer(event);
  }

  void _trackDeferredOutsidePointer(PointerDownEvent event) {
    _clearDeferredOutsidePointerRoute();
    _deferredOutsidePointer = event.pointer;
    _deferredOutsideStart = event.position;
    _deferredOutsideMoved = false;
    GestureBinding.instance.pointerRouter.addRoute(
      event.pointer,
      _handleDeferredOutsidePointer,
    );
  }

  void _handleDeferredOutsidePointer(PointerEvent event) {
    final pointer = _deferredOutsidePointer;
    if (pointer == null || event.pointer != pointer) return;
    if (event is PointerMoveEvent) {
      final start = _deferredOutsideStart;
      if (start != null && (event.position - start).distance >= kTouchSlop) {
        _deferredOutsideMoved = true;
      }
      return;
    }
    if (event is! PointerUpEvent && event is! PointerCancelEvent) return;

    final keepOpen = _deferredOutsideMoved;
    _clearDeferredOutsidePointerRoute();
    if (!keepOpen && mounted && _portalVisible) _dismiss();
  }

  void _clearDeferredOutsidePointerRoute() {
    final pointer = _deferredOutsidePointer;
    if (pointer != null) {
      GestureBinding.instance.pointerRouter.removeRoute(
        pointer,
        _handleDeferredOutsidePointer,
      );
    }
    _deferredOutsidePointer = null;
    _deferredOutsideStart = null;
    _deferredOutsideMoved = false;
  }

  void _dismissChainAfter(_HoverCardAnchorState target) {
    if (this == target) {
      _coordinator.dismissOpenChild();
      return;
    }
    final child = _coordinator.openChild;
    if (child == null) return;
    child._dismissChainAfter(target);
  }

  _HoverCardAnchorState? _deepestOpenCardAt(Offset globalPosition) {
    final child = _coordinator.openChild?._deepestOpenCardAt(globalPosition);
    if (child != null) return child;
    if (_portalVisible &&
        _cardRectInGlobal()?.contains(globalPosition) == true) {
      return this;
    }
    return null;
  }

  bool _openAnchorChainContains(Offset globalPosition) {
    if (_anchorRectInGlobal()?.contains(globalPosition) == true) return true;
    return _coordinator.openChild?._openAnchorChainContains(globalPosition) ??
        false;
  }

  Rect? _anchorRectInOverlay() {
    final anchorBox = _anchorKey.currentContext?.findRenderObject();
    final overlayBox = Overlay.maybeOf(context)?.context.findRenderObject();
    if (anchorBox is! RenderBox ||
        overlayBox is! RenderBox ||
        !anchorBox.hasSize ||
        !overlayBox.hasSize) {
      return null;
    }
    final topLeft = anchorBox.localToGlobal(Offset.zero, ancestor: overlayBox);
    return topLeft & anchorBox.size;
  }

  Rect? _anchorRectInGlobal() {
    final anchorBox = _anchorKey.currentContext?.findRenderObject();
    if (anchorBox is! RenderBox || !anchorBox.hasSize) return null;
    return anchorBox.localToGlobal(Offset.zero) & anchorBox.size;
  }

  Rect? _cardRectInGlobal() {
    final cardBox = _cardKey.currentContext?.findRenderObject();
    if (cardBox is! RenderBox || !cardBox.hasSize) return null;
    return cardBox.localToGlobal(Offset.zero) & cardBox.size;
  }

  @override
  Widget build(BuildContext context) {
    return OverlayPortal(
      controller: _portal,
      overlayChildBuilder: (context) {
        return Positioned.fill(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final anchorRect = _anchorRectInOverlay();
              if (anchorRect == null) return const SizedBox.shrink();
              final safeInsets = hoverCardOverlaySafeInsets(context);
              return CustomSingleChildLayout(
                delegate: _HoverCardLayoutDelegate(
                  anchorRect: anchorRect,
                  gap: widget.gap,
                  cardWidth: widget.cardWidth,
                  safeInsets: safeInsets,
                ),
                child: TapRegion(
                  groupId: _tapRegionGroup,
                  onTapOutside: _handleTapOutside,
                  onTapInside: _handleTapInside,
                  child: MouseRegion(
                    onEnter: (_) => _enterCard(),
                    onExit: (_) => _exitCard(),
                    child: AnchoredPanel(
                      key: _cardKey,
                      width: widget.cardWidth,
                      child: _HoverCardChainScope(
                        anchor: this,
                        coordinator: _coordinator,
                        tapRegionGroup: _tapRegionGroup,
                        child: HoverCardTapRegionScope(
                          tapRegionGroup: _tapRegionGroup,
                          onOverlayActivityChanged:
                              _handleOverlayActivityChanged,
                          child: Builder(builder: widget.cardBuilder),
                        ),
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        );
      },
      child: TapRegion(
        groupId: _tapRegionGroup,
        child: GestureDetector(
          behavior: HitTestBehavior.translucent,
          onTap: _pinOpen,
          // On Android a hold is reserved for the desktop-equivalent context
          // menu. Registering it here also prevents a long hold from falling
          // through to the tap-to-pin behavior when no context menu exists.
          onLongPress: Theme.of(context).platform == TargetPlatform.android
              ? () {}
              : null,
          child: MouseRegion(
            onEnter: (_) => _enterAnchor(),
            onExit: (_) => _exitAnchor(),
            child: KeyedSubtree(key: _anchorKey, child: widget.child),
          ),
        ),
      ),
    );
  }
}

class _HoverCardCoordinator {
  _HoverCardCoordinator(this.onChanged);

  final VoidCallback onChanged;
  final Set<Object> _activeDescendants = <Object>{};
  _HoverCardAnchorState? _openChild;

  bool get hasActiveDescendants => _activeDescendants.isNotEmpty;
  _HoverCardAnchorState? get openChild => _openChild;

  void setDescendantActive(Object token, bool active) {
    final changed = active
        ? _activeDescendants.add(token)
        : _activeDescendants.remove(token);
    if (changed) onChanged();
  }

  void showOnlyChild(_HoverCardAnchorState child) {
    final previous = _openChild;
    if (previous != null && previous != child) {
      previous._dismissFromPeer();
    }
    _openChild = child;
  }

  void releaseOpenChild(_HoverCardAnchorState child) {
    if (_openChild == child) _openChild = null;
  }

  void dismissOpenChild() {
    final child = _openChild;
    if (child == null) return;
    _openChild = null;
    child._dismissFromPeer();
  }
}

class _HoverCardChainScope extends InheritedWidget {
  const _HoverCardChainScope({
    required this.anchor,
    required this.coordinator,
    required this.tapRegionGroup,
    required super.child,
  });

  final _HoverCardAnchorState anchor;
  final _HoverCardCoordinator coordinator;
  final Object tapRegionGroup;

  static _HoverCardChainScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<_HoverCardChainScope>();
  }

  @override
  bool updateShouldNotify(_HoverCardChainScope oldWidget) {
    return anchor != oldWidget.anchor ||
        coordinator != oldWidget.coordinator ||
        tapRegionGroup != oldWidget.tapRegionGroup;
  }
}

class _HoverCardLayoutDelegate extends SingleChildLayoutDelegate {
  const _HoverCardLayoutDelegate({
    required this.anchorRect,
    required this.gap,
    required this.cardWidth,
    required this.safeInsets,
  });

  final Rect anchorRect;
  final double gap;
  final double cardWidth;
  final EdgeInsets safeInsets;

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) {
    final availableWidth = math.max(
      0.0,
      constraints.maxWidth - safeInsets.horizontal,
    );
    final availableHeight = math.max(
      0.0,
      constraints.maxHeight - safeInsets.vertical,
    );
    final effectiveCardWidth = math.min(cardWidth, availableWidth);
    return BoxConstraints(
      minWidth: effectiveCardWidth,
      maxWidth: effectiveCardWidth,
      maxHeight: availableHeight,
    );
  }

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    final minLeft = safeInsets.left.clamp(0.0, size.width).toDouble();
    final safeRight = math.max(minLeft, size.width - safeInsets.right);
    final spaceRight = safeRight - anchorRect.right - gap;
    final placeRight = spaceRight >= childSize.width;
    final rawLeft = placeRight
        ? anchorRect.right + gap
        : anchorRect.left - gap - childSize.width;
    final maxLeft = math.max(minLeft, safeRight - childSize.width);
    final left = rawLeft.clamp(minLeft, maxLeft).toDouble();

    final minTop = safeInsets.top.clamp(0.0, size.height).toDouble();
    final safeBottom = math.max(minTop, size.height - safeInsets.bottom);
    final maxTop = math.max(minTop, safeBottom - childSize.height);
    final top = anchorRect.top.clamp(minTop, maxTop).toDouble();
    return Offset(left, top);
  }

  @override
  bool shouldRelayout(_HoverCardLayoutDelegate oldDelegate) {
    return oldDelegate.anchorRect != anchorRect ||
        oldDelegate.gap != gap ||
        oldDelegate.cardWidth != cardWidth ||
        oldDelegate.safeInsets != safeInsets;
  }
}

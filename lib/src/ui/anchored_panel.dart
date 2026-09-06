import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'tokens.dart';

class AnchoredPanel extends StatelessWidget {
  const AnchoredPanel({
    super.key,
    required this.child,
    this.width = 320,
    this.backgroundColor = UiColors.surfaceRaised,
    this.borderColor = UiColors.borderStrong,
  });

  final Widget child;
  final double width;
  final Color backgroundColor;
  final Color borderColor;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: backgroundColor,
          borderRadius: BorderRadius.circular(UiRadii.lg),
          border: Border.all(color: borderColor),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.36),
              blurRadius: 24,
              offset: const Offset(0, 12),
            ),
          ],
        ),
        child: child,
      ),
    );
  }
}

class AnchoredPanelAnchor extends StatefulWidget {
  const AnchoredPanelAnchor({
    super.key,
    required this.anchor,
    required this.panel,
    this.width = 320,
    this.gap = 8,
    this.backgroundColor = UiColors.surfaceRaised,
    this.borderColor = UiColors.borderStrong,
  });

  final Widget Function(BuildContext context, bool open, VoidCallback toggle)
  anchor;
  final Widget panel;
  final double width;
  final double gap;
  final Color backgroundColor;
  final Color borderColor;

  @override
  State<AnchoredPanelAnchor> createState() => _AnchoredPanelAnchorState();
}

class _AnchoredPanelAnchorState extends State<AnchoredPanelAnchor> {
  final GlobalKey _anchorKey = GlobalKey();
  final Object _tapRegionGroup = Object();
  final OverlayPortalController _portal = OverlayPortalController();

  bool get _open => _portal.isShowing;

  void _toggle() => _open ? _close() : _openPanel();

  void _openPanel() {
    if (_portal.isShowing) return;
    _portal.show();
    setState(() {});
  }

  void _close() {
    if (!_portal.isShowing) return;
    _portal.hide();
    if (mounted) setState(() {});
  }

  Rect? _anchorRectInOverlay() {
    final anchorContext = _anchorKey.currentContext;
    final overlay = Overlay.maybeOf(context);
    final anchorBox = anchorContext?.findRenderObject();
    final overlayBox = overlay?.context.findRenderObject();
    if (anchorBox is! RenderBox ||
        overlayBox is! RenderBox ||
        !anchorBox.hasSize ||
        !overlayBox.hasSize) {
      return null;
    }

    final topLeft = anchorBox.localToGlobal(Offset.zero, ancestor: overlayBox);
    return topLeft & anchorBox.size;
  }

  @override
  Widget build(BuildContext context) {
    // Own semantics boundary: portal traversal identifiers must not merge
    // with other portals' anchors (see HoverCardAnchor).
    return Semantics(
      container: true,
      child: OverlayPortal(
        controller: _portal,
        overlayChildBuilder: (context) {
          return Positioned.fill(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final anchorRect = _anchorRectInOverlay();
                if (anchorRect == null) return const SizedBox.shrink();

                return CustomSingleChildLayout(
                  delegate: _AnchoredPanelLayoutDelegate(
                    anchorRect: anchorRect,
                    gap: widget.gap,
                    panelWidth: widget.width,
                  ),
                  child: SizedBox(
                    width: widget.width,
                    child: TapRegion(
                      groupId: _tapRegionGroup,
                      child: AnchoredPanel(
                        width: widget.width,
                        backgroundColor: widget.backgroundColor,
                        borderColor: widget.borderColor,
                        child: widget.panel,
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
          onTapOutside: (_) => _close(),
          child: KeyedSubtree(
            key: _anchorKey,
            child: widget.anchor(context, _open, _toggle),
          ),
        ),
      ),
    );
  }
}

class _AnchoredPanelLayoutDelegate extends SingleChildLayoutDelegate {
  const _AnchoredPanelLayoutDelegate({
    required this.anchorRect,
    required this.gap,
    required this.panelWidth,
  });

  final Rect anchorRect;
  final double gap;
  final double panelWidth;

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) {
    return BoxConstraints.loose(
      constraints.biggest,
    ).copyWith(minWidth: panelWidth, maxWidth: panelWidth);
  }

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    final placeBelow = _shouldPlaceBelow(
      anchorRect: anchorRect,
      overlaySize: size,
      panelSize: childSize,
      gap: gap,
    );
    final maxLeft = math.max(0.0, size.width - childSize.width);
    final left = (anchorRect.center.dx - childSize.width / 2)
        .clamp(0.0, maxLeft)
        .toDouble();
    final maxTop = math.max(0.0, size.height - childSize.height);
    final top = placeBelow
        ? (anchorRect.bottom + gap).clamp(0.0, maxTop).toDouble()
        : (anchorRect.top - gap - childSize.height)
              .clamp(0.0, maxTop)
              .toDouble();

    return Offset(left, top);
  }

  @override
  bool shouldRelayout(_AnchoredPanelLayoutDelegate oldDelegate) {
    return oldDelegate.anchorRect != anchorRect ||
        oldDelegate.gap != gap ||
        oldDelegate.panelWidth != panelWidth;
  }
}

bool _shouldPlaceBelow({
  required Rect anchorRect,
  required Size overlaySize,
  required Size panelSize,
  required double gap,
}) {
  final aboveTop = anchorRect.top - gap - panelSize.height;
  if (aboveTop >= 0) return false;

  final belowBottom = anchorRect.bottom + gap + panelSize.height;
  if (belowBottom <= overlaySize.height) return true;

  final spaceAbove = anchorRect.top;
  final spaceBelow = overlaySize.height - anchorRect.bottom;
  return spaceBelow > spaceAbove;
}

import 'package:flutter/widgets.dart';

/// Keeps the Android live music-box surface anchored while the IME overlays
/// the room. Other home inputs retain Flutter's normal resize behavior.
bool shouldResizeHomeForKeyboard({
  required TargetPlatform platform,
  required bool liveMusicBoxVisible,
}) {
  return platform != TargetPlatform.android || !liveMusicBoxVisible;
}

/// Preserves the pre-keyboard home geometry while Android animates the IME.
///
/// Android devices can still deliver progressively shorter root constraints
/// during `adjustResize`, even when the surrounding Scaffold opts out of inset
/// resizing. The cached viewport prevents those intermediate constraints from
/// repeatedly re-laying out the live room and music box. Current keyboard
/// insets are retained so focused text fields can still keep themselves
/// visible. The cache is refreshed as soon as the keyboard is fully closed.
class HomeKeyboardOverlayViewport extends StatefulWidget {
  const HomeKeyboardOverlayViewport({
    super.key,
    required this.enabled,
    required this.child,
  });

  final bool enabled;
  final Widget child;

  @override
  State<HomeKeyboardOverlayViewport> createState() =>
      _HomeKeyboardOverlayViewportState();
}

class _HomeKeyboardOverlayViewportState
    extends State<HomeKeyboardOverlayViewport> {
  Size? _stableSize;
  EdgeInsets? _stablePadding;
  EdgeInsets? _stableViewPadding;

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final keyboardVisible = mediaQuery.viewInsets.bottom > 0;
    return LayoutBuilder(
      builder: (context, constraints) {
        final currentSize = Size(constraints.maxWidth, constraints.maxHeight);
        if (!keyboardVisible) {
          _stableSize = currentSize;
          _stablePadding = mediaQuery.padding;
          _stableViewPadding = mediaQuery.viewPadding;
        }

        final stableSize = _stableSize;
        if (!widget.enabled || !keyboardVisible || stableSize == null) {
          return widget.child;
        }

        final stableMediaQuery = mediaQuery.copyWith(
          size: stableSize,
          padding: _stablePadding,
          viewPadding: _stableViewPadding,
        );
        return OverflowBox(
          alignment: Alignment.topLeft,
          minWidth: stableSize.width,
          maxWidth: stableSize.width,
          minHeight: stableSize.height,
          maxHeight: stableSize.height,
          child: SizedBox.fromSize(
            size: stableSize,
            child: MediaQuery(data: stableMediaQuery, child: widget.child),
          ),
        );
      },
    );
  }
}

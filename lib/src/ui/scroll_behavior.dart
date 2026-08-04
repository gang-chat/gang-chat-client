import 'package:flutter/material.dart';

/// Keeps desktop scrollbars in a dedicated trailing gutter instead of
/// painting their thumbs over list cards and action buttons.
///
/// Android keeps Flutter's native touch scrolling unchanged. Lists that opt
/// into their own scrollbar (such as the room sidebar) disable automatic
/// scrollbars through [ScrollConfiguration] and therefore remain unaffected.
class GangScrollBehavior extends MaterialScrollBehavior {
  const GangScrollBehavior();

  static const double verticalScrollbarGutter = 8;

  @override
  Widget buildScrollbar(
    BuildContext context,
    Widget child,
    ScrollableDetails details,
  ) {
    final platform = Theme.of(context).platform;
    final usesDesktopScrollbar =
        platform == TargetPlatform.windows ||
        platform == TargetPlatform.macOS ||
        platform == TargetPlatform.linux;
    if (!usesDesktopScrollbar ||
        axisDirectionToAxis(details.direction) != Axis.vertical) {
      return super.buildScrollbar(context, child, details);
    }

    return super.buildScrollbar(
      context,
      Padding(
        padding: const EdgeInsets.only(right: verticalScrollbarGutter),
        child: child,
      ),
      details,
    );
  }
}

import 'package:flutter/material.dart';

const _macOsSidebarTopOffset = 18.0;
// The native traffic lights end near x=69 after MainFlutterWindow recenters
// them. Keep a small interaction gap before Flutter title-bar content.
const _macOsTitleBarLeadingControlsSafeInset = 80.0;

class WindowChromeInsets {
  const WindowChromeInsets({
    required this.sidebarTopOffset,
    required this.titleBarLeadingControlsSafeInset,
  });

  final double sidebarTopOffset;
  final double titleBarLeadingControlsSafeInset;

  static WindowChromeInsets of(BuildContext context) {
    final platform = Theme.of(context).platform;
    final viewPadding =
        MediaQuery.maybeOf(context)?.viewPadding ?? EdgeInsets.zero;

    return WindowChromeInsets(
      sidebarTopOffset: switch (platform) {
        TargetPlatform.macOS => _macOsSidebarTopOffset,
        TargetPlatform.iOS || TargetPlatform.android => viewPadding.top,
        _ => 0,
      },
      titleBarLeadingControlsSafeInset: switch (platform) {
        TargetPlatform.macOS => _macOsTitleBarLeadingControlsSafeInset,
        _ => 0,
      },
    );
  }
}

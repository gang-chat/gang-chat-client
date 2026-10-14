import 'package:flutter/material.dart';

const _macOsSidebarTopOffset = 18.0;
// MainFlutterWindow centres the native traffic lights in the 52px header band
// with an even 19px inset, so they end near x=73. Leave the same 19px gap
// before Flutter title-bar content so the lights never crowd the brand.
const _macOsTitleBarLeadingControlsSafeInset = 92.0;

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

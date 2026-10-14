import 'package:flutter/material.dart';

const String kClientFontFamily = 'Microsoft YaHei';
const List<String> kClientFontFamilyFallback = [
  'Microsoft YaHei UI',
  'PingFang SC',
  'Noto Sans SC',
  'Noto Sans CJK SC',
];

/// Latin display face for headings and numerals (CJK falls back to the
/// platform font).
const String kDisplayFontFamily = 'Space Grotesk';

/// Monospace face for labels, tags, timestamps and counters.
const String kMonoFontFamily = 'JetBrains Mono';

/// Dot-matrix face for the wordmark and big numerals.
const String kDotFontFamily = 'Doto';

/// "Signal" palette: true-black canvas, graphite layers and a single acid-lime
/// signal colour. Lime means *live or actionable* (primary actions, voice,
/// speaking, focus). White pills mean *selected*. Everything else stays in
/// greys so the signal colour always reads.
abstract final class UiColors {
  // Neutral layers, darkest to lightest.
  static const surfacePressed = Color(0xFF000000);
  static const background = Color(0xFF060607);
  static const surfaceLow = Color(0xFF0D0D0F);

  /// Grouping cards that sit on a [surfaceLow] panel.
  static const card = Color(0xFF131316);
  static const surface = Color(0xFF17171A);
  static const surfaceRaised = Color(0xFF1E1E22);
  static const surfaceHover = Color(0xFF27272C);

  static const selected = Color(0xFF1C220C);
  static const border = Color(0xFF222226);
  static const borderStrong = Color(0xFF323238);
  static const accentBorder = Color(0xFF465418);
  static const selectedBorder = Color(0xFF9FC72A);
  static const dangerBorder = Color(0xFF5E1F21);
  static const disabledSurface = Color(0xFF111113);
  static const disabledBorder = Color(0xFF1C1C1F);

  /// Text-field wells sit slightly below the panel they live on.
  static const fieldFill = Color(0xFF09090A);
  static const fieldBorder = Color(0xFF26262B);
  static const fieldBorderHover = Color(0xFF3A3A41);

  static const text = Color(0xFFF4F4F1);
  static const textSecondary = Color(0xFFA3A3A8);
  static const textMuted = Color(0xFF66666D);
  static const onAccent = Color(0xFF0A0B05);

  /// Inverted "selected" pill (white surface, black text).
  static const inverse = Color(0xFFF4F4F1);
  static const onInverse = Color(0xFF0A0A0B);

  /// Acid-lime signal colour for text and icons on dark surfaces.
  static const accent = Color(0xFFD4FF3F);

  /// Lime used for filled controls (primary buttons, toggles).
  static const accentStrong = Color(0xFFCCF43A);
  static const accentSoft = Color(0xFF151A07);
  static const controlAccent = Color(0xFF5CE1FF);
  static const violet = Color(0xFFB69CFF);
  static const amber = Color(0xFFFFB224);
  static const danger = Color(0xFFFF4D4F);
  static const dangerSurface = Color(0xFF250D0E);
  static const success = Color(0xFFD4FF3F);
  static const successSurface = Color(0xFF161C07);
  static const successBorder = Color(0xFF465418);

  static const presenceOnline = Color(0xFF4ADE80);
  static const presenceOnlineSurface = Color(0xFF0D2015);
  static const presenceOnlineBorder = Color(0xFF1E5534);
  static const presenceOffline = Color(0xFF7C7C84);
  static const presenceOfflineSurface = Color(0xFF18181B);
  static const presenceOfflineBorder = Color(0xFF34343A);
  static const presenceVoice = Color(0xFFD4FF3F);
  static const presenceVoiceSurface = Color(0xFF161C07);
  static const presenceVoiceBorder = Color(0xFF55661B);
  static const presenceReconnecting = Color(0xFFFFB224);

  static const roleMember = Color(0xFF5CB8FF);
  static const roleMemberSurface = Color(0xFF0E1D2B);
  static const roleMemberBorder = Color(0xFF24527A);
  static const roleAdmin = Color(0xFFFFD84D);
  static const roleAdminSurface = Color(0xFF261F08);
  static const roleAdminBorder = Color(0xFF7A6420);
  static const roleCreator = Color(0xFFFF8A3D);
  static const roleCreatorSurface = Color(0xFF2A160A);
  static const roleCreatorBorder = Color(0xFF8A4820);
  static const roleSuperuser = Color(0xFFFF4D6A);
  static const roleSuperuserSurface = Color(0xFF2B0D13);
  static const roleSuperuserBorder = Color(0xFF8A2638);
}

/// Interaction tints shared by every pressable control so hover and press
/// feedback reads identically across buttons, rows and cards.
abstract final class UiStates {
  static Color hover(Color base) {
    if (base.a == 0) return Colors.white.withValues(alpha: 0.05);
    return Color.lerp(base, Colors.white, 0.055)!;
  }

  static Color pressed(Color base) {
    if (base.a == 0) return Colors.white.withValues(alpha: 0.03);
    return Color.lerp(base, Colors.black, 0.18)!;
  }
}

abstract final class UiSpacing {
  static const xxs = 4.0;
  static const xs = 6.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 20.0;
  static const xxl = 24.0;
  static const xxxl = 32.0;
}

abstract final class UiRadii {
  static const xs = 5.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 18.0;
  static const xl = 26.0;
  static const pill = 999.0;
}

abstract final class UiMotion {
  static const fast = Duration(milliseconds: 120);
  static const standard = Duration(milliseconds: 180);
  static const slow = Duration(milliseconds: 260);
  static const curve = Curves.easeOutCubic;
}

abstract final class UiShadows {
  static const raised = [
    BoxShadow(color: Color(0x40000000), blurRadius: 10, offset: Offset(0, 3)),
  ];

  static const popover = [
    BoxShadow(color: Color(0x80000000), blurRadius: 32, offset: Offset(0, 14)),
    BoxShadow(color: Color(0x33000000), blurRadius: 4, offset: Offset(0, 1)),
  ];

  static const dialog = [
    BoxShadow(color: Color(0x99000000), blurRadius: 56, offset: Offset(0, 24)),
  ];

  static List<BoxShadow> glow(Color color, {double strength = 0.32}) => [
    BoxShadow(
      color: color.withValues(alpha: strength),
      blurRadius: 18,
      spreadRadius: -2,
    ),
  ];
}

abstract final class UiTypography {
  /// Big display text (hero greetings, page titles).
  static const display = TextStyle(
    color: UiColors.text,
    fontFamily: kDisplayFontFamily,
    fontFamilyFallback: [kClientFontFamily, ...kClientFontFamilyFallback],
    fontSize: 30,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.6,
    height: 1.15,
  );

  static const headline = TextStyle(
    color: UiColors.text,
    fontFamily: kDisplayFontFamily,
    fontFamilyFallback: [kClientFontFamily, ...kClientFontFamilyFallback],
    fontSize: 21,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.3,
    height: 1.25,
  );

  static const title = TextStyle(
    color: UiColors.text,
    fontFamily: kDisplayFontFamily,
    fontFamilyFallback: [kClientFontFamily, ...kClientFontFamilyFallback],
    fontSize: 17,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.2,
    height: 1.3,
  );

  static const subtitle = TextStyle(
    color: UiColors.text,
    fontFamily: kDisplayFontFamily,
    fontFamilyFallback: [kClientFontFamily, ...kClientFontFamilyFallback],
    fontSize: 15,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.1,
    height: 1.35,
  );

  static const body = TextStyle(
    color: UiColors.text,
    fontSize: 14,
    height: 1.4,
  );

  static const label = TextStyle(
    color: UiColors.textSecondary,
    fontSize: 12,
    fontWeight: FontWeight.w500,
    height: 1.35,
  );

  static const caption = TextStyle(
    color: UiColors.textMuted,
    fontSize: 11.5,
    fontWeight: FontWeight.w500,
    height: 1.35,
  );

  /// Monospace uppercase label ("tag" look) for section headers, counters
  /// and timestamps.
  static const overline = TextStyle(
    color: UiColors.textMuted,
    fontFamily: kMonoFontFamily,
    fontFamilyFallback: [kClientFontFamily, ...kClientFontFamilyFallback],
    fontSize: 10.5,
    fontWeight: FontWeight.w700,
    letterSpacing: 1.2,
    height: 1.3,
  );

  static const mono = TextStyle(
    color: UiColors.textMuted,
    fontFamily: kMonoFontFamily,
    fontFamilyFallback: [kClientFontFamily, ...kClientFontFamilyFallback],
    fontSize: 11,
    fontWeight: FontWeight.w500,
    letterSpacing: 0.2,
    height: 1.3,
  );

  /// Dot-matrix numerals and wordmark.
  static const dot = TextStyle(
    color: UiColors.text,
    fontFamily: kDotFontFamily,
    fontFamilyFallback: [kMonoFontFamily, kClientFontFamily],
    fontSize: 28,
    fontWeight: FontWeight.w800,
    height: 1.0,
  );
}

ThemeData uiTheme() {
  return ThemeData(
    brightness: Brightness.dark,
    colorScheme: const ColorScheme.dark(
      primary: UiColors.accentStrong,
      onPrimary: UiColors.onAccent,
      secondary: UiColors.accent,
      surface: UiColors.surfaceLow,
      onSurface: UiColors.text,
      error: UiColors.danger,
    ),
    fontFamily: kClientFontFamily,
    fontFamilyFallback: kClientFontFamilyFallback,
    scaffoldBackgroundColor: UiColors.background,
    canvasColor: UiColors.background,
    dividerColor: UiColors.border,
    dividerTheme: const DividerThemeData(
      color: UiColors.border,
      thickness: 1,
      space: 1,
    ),
    // The app styles its own flat focus/hover/selected states. Suppress
    // Material's default interaction overlays so they never stack on top of
    // the kit's own tints.
    splashFactory: NoSplash.splashFactory,
    highlightColor: Colors.transparent,
    hoverColor: Colors.transparent,
    focusColor: Colors.transparent,
    textSelectionTheme: TextSelectionThemeData(
      cursorColor: UiColors.accent,
      selectionColor: UiColors.accentStrong.withValues(alpha: 0.3),
      selectionHandleColor: UiColors.accent,
    ),
    tooltipTheme: TooltipThemeData(
      decoration: BoxDecoration(
        color: UiColors.surfaceHover,
        borderRadius: BorderRadius.circular(UiRadii.sm),
        border: Border.all(color: UiColors.borderStrong),
        boxShadow: UiShadows.raised,
      ),
      textStyle: const TextStyle(
        color: UiColors.text,
        fontSize: 12,
        fontWeight: FontWeight.w500,
        fontFamily: kClientFontFamily,
        fontFamilyFallback: kClientFontFamilyFallback,
      ),
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      waitDuration: const Duration(milliseconds: 380),
    ),
    scrollbarTheme: ScrollbarThemeData(
      thickness: const WidgetStatePropertyAll(6),
      radius: const Radius.circular(UiRadii.pill),
      thumbColor: WidgetStateProperty.resolveWith(
        (states) =>
            states.contains(WidgetState.hovered) ||
                states.contains(WidgetState.dragged)
            ? UiColors.textMuted
            : UiColors.borderStrong,
      ),
      crossAxisMargin: 2,
      mainAxisMargin: 4,
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: UiColors.accent,
      linearTrackColor: UiColors.surfaceRaised,
      circularTrackColor: Colors.transparent,
    ),
    dialogTheme: const DialogThemeData(
      backgroundColor: UiColors.surfaceLow,
      surfaceTintColor: Colors.transparent,
    ),
    popupMenuTheme: PopupMenuThemeData(
      color: UiColors.surfaceRaised,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(UiRadii.md),
        side: const BorderSide(color: UiColors.borderStrong),
      ),
    ),
  );
}

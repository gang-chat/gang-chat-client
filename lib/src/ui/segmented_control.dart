import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import 'badge_dot.dart';
import 'context_menu.dart';
import 'surface.dart';
import 'tokens.dart';

class Segment<T> {
  const Segment({
    required this.value,
    required this.label,
    this.icon,
    this.showBadge = false,
    this.badgeKey,
  });

  final T value;
  final String label;
  final IconData? icon;
  final bool showBadge;
  final Key? badgeKey;
}

class SegmentedControl<T> extends StatefulWidget {
  const SegmentedControl({
    super.key,
    required this.segments,
    required this.value,
    required this.onChanged,
    this.expanded = false,
    this.height = 36,
  });

  final List<Segment<T>> segments;
  final T value;
  final ValueChanged<T> onChanged;
  final bool expanded;

  /// Height of each segment. Defaults to 36; shrink for denser layouts.
  final double height;

  /// The narrowest width that can show every segment without entering the
  /// overflow/scroll layout.
  static double minimumWidthFor<T>(
    BuildContext context,
    List<Segment<T>> segments,
  ) {
    return _sum(
      segments.map(
        (segment) =>
            _SegmentedControlState.measureSegmentContentWidth(
              context,
              segment,
            ) +
            (_SegmentedControlState._minimumSegmentPadding * 2),
      ),
    );
  }

  @override
  State<SegmentedControl<T>> createState() => _SegmentedControlState<T>();
}

class _SegmentedControlState<T> extends State<SegmentedControl<T>> {
  static const double _segmentPadding = 10;
  static const double _minimumSegmentPadding = 6;
  static const double _iconSize = 15;
  static const double _iconGap = 6;
  static const double _overflowControlGap = 6;
  // TextPainter and RenderParagraph can differ by a few subpixels across
  // platform font fallbacks. Keep a small buffer so a fully measured label is
  // never forced back into clipping at a fractional-width boundary.
  static const double _contentWidthSafety = 6;
  static const double _thumbInset = 3;
  static const Duration _duration = Duration(milliseconds: 180);
  static const TextStyle _segmentTextStyle = TextStyle(
    fontFamily: kClientFontFamily,
    fontFamilyFallback: kClientFontFamilyFallback,
    fontSize: 13,
    fontWeight: FontWeight.w600,
  );

  final ScrollController _scrollController = ScrollController();
  final GlobalKey _menuAnchorKey = GlobalKey();

  int? _hoveredIndex;
  int? _pressedIndex;
  bool _showJumpToStart = false;
  bool _menuOpen = false;
  bool _overflowLayoutLastBuild = false;
  int? _selectedIndexLastBuild;
  double? _overflowAvailableWidthLastBuild;
  bool _centerSelectedScheduled = false;
  List<double> _overflowSegmentWidths = const [];

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_handleScrollChanged);
  }

  @override
  void dispose() {
    _scrollController
      ..removeListener(_handleScrollChanged)
      ..dispose();
    super.dispose();
  }

  int get _selectedIndex {
    final index = widget.segments.indexWhere(
      (segment) => segment.value == widget.value,
    );
    return index < 0 ? 0 : index;
  }

  @override
  Widget build(BuildContext context) {
    if (widget.segments.isEmpty) return const SizedBox.shrink();

    return LayoutBuilder(
      builder: (context, constraints) {
        final contentWidths = [
          for (final segment in widget.segments)
            measureSegmentContentWidth(context, segment),
        ];
        final preferredWidths = [
          for (final width in contentWidths) width + (_segmentPadding * 2),
        ];
        final minimumWidths = [
          for (final width in contentWidths)
            width + (_minimumSegmentPadding * 2),
        ];
        final preferredWidth = _sum(preferredWidths);
        final minimumWidth = _sum(minimumWidths);
        final constrained = constraints.maxWidth.isFinite;
        final availableWidth = constrained
            ? constraints.maxWidth.clamp(0.0, double.infinity).toDouble()
            : preferredWidth;
        if (availableWidth <= 0) return const SizedBox.shrink();

        final selectedIndex = _selectedIndex.clamp(
          0,
          widget.segments.length - 1,
        );
        final usesOverflowLayout = constrained && minimumWidth > availableWidth;
        if (usesOverflowLayout) {
          final overflowLayoutChanged =
              !_overflowLayoutLastBuild ||
              _overflowAvailableWidthLastBuild == null ||
              (_overflowAvailableWidthLastBuild! - availableWidth).abs() >
                  0.5 ||
              _segmentWidthsDiffer(_overflowSegmentWidths, preferredWidths);
          _overflowSegmentWidths = preferredWidths;
          _scheduleSelectedCenterIfNeeded(
            selectedIndex,
            overflowLayoutChanged: overflowLayoutChanged,
          );
          _overflowLayoutLastBuild = true;
          _overflowAvailableWidthLastBuild = availableWidth;
          _selectedIndexLastBuild = selectedIndex;
          return SizedBox(
            width: constrained ? double.infinity : preferredWidth,
            height: widget.height,
            child: _buildOverflowLayout(
              context,
              segmentWidths: preferredWidths,
              selectedIndex: selectedIndex,
            ),
          );
        }

        _overflowLayoutLastBuild = false;
        _overflowAvailableWidthLastBuild = null;
        _selectedIndexLastBuild = selectedIndex;
        final targetWidth = widget.expanded && constrained
            ? availableWidth
            : constrained && preferredWidth > availableWidth
            ? availableWidth
            : preferredWidth;
        final fit = _fitSegmentWidths(
          contentWidths: contentWidths,
          preferredWidths: preferredWidths,
          minimumWidth: minimumWidth,
          preferredWidth: preferredWidth,
          targetWidth: targetWidth,
        );
        return SizedBox(
          width: widget.expanded && constrained ? double.infinity : targetWidth,
          height: widget.height,
          child: _buildTrack(
            segmentWidths: fit.widths,
            horizontalPadding: fit.horizontalPadding,
            selectedIndex: selectedIndex,
          ),
        );
      },
    );
  }

  Widget _buildOverflowLayout(
    BuildContext context, {
    required List<double> segmentWidths,
    required int selectedIndex,
  }) {
    final iconButtonSize = widget.height;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_showJumpToStart) ...[
          _SegmentControlIconButton(
            tooltip: '回到开头',
            icon: Icons.keyboard_double_arrow_left_rounded,
            size: iconButtonSize,
            onPressed: _scrollToStart,
          ),
          const SizedBox(width: _overflowControlGap),
        ],
        Expanded(
          child: ClipRect(
            child: Listener(
              onPointerSignal: _handlePointerSignal,
              child: ScrollConfiguration(
                behavior: ScrollConfiguration.of(context).copyWith(
                  scrollbars: false,
                  dragDevices: {
                    ...ScrollConfiguration.of(context).dragDevices,
                    PointerDeviceKind.mouse,
                  },
                ),
                child: SingleChildScrollView(
                  key: const ValueKey('segmented-control-scroll-view'),
                  controller: _scrollController,
                  scrollDirection: Axis.horizontal,
                  physics: const ClampingScrollPhysics(),
                  child: SizedBox(
                    width: _sum(segmentWidths),
                    height: widget.height,
                    child: _buildTrack(
                      segmentWidths: segmentWidths,
                      horizontalPadding: _segmentPadding,
                      selectedIndex: selectedIndex,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
        const SizedBox(width: _overflowControlGap),
        _SegmentControlIconButton(
          key: _menuAnchorKey,
          tooltip: '选择选项',
          icon: Icons.menu_rounded,
          size: iconButtonSize,
          selected: _menuOpen,
          onPressed: () => unawaited(_showSelectionMenu()),
        ),
      ],
    );
  }

  Widget _buildTrack({
    required List<double> segmentWidths,
    required double horizontalPadding,
    required int selectedIndex,
  }) {
    final selectedLeft = _sum(segmentWidths.take(selectedIndex));
    final selectedPressed = _pressedIndex == selectedIndex;
    return Stack(
      clipBehavior: Clip.none,
      children: [
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: UiColors.fieldFill,
              borderRadius: BorderRadius.circular(UiRadii.pill),
              border: Border.all(color: UiColors.border),
            ),
          ),
        ),
        AnimatedPositioned(
          duration: _duration,
          curve: Curves.easeOutCubic,
          left: selectedLeft + _thumbInset,
          top: _thumbInset,
          width: (segmentWidths[selectedIndex] - _thumbInset * 2)
              .clamp(0.0, double.infinity)
              .toDouble(),
          height: (widget.height - _thumbInset * 2)
              .clamp(0.0, double.infinity)
              .toDouble(),
          child: _SegmentThumb(pressed: selectedPressed),
        ),
        Positioned.fill(
          child: Row(
            children: [
              for (var index = 0; index < widget.segments.length; index++)
                SizedBox(
                  width: segmentWidths[index],
                  height: widget.height,
                  child: _SegmentHitTarget(
                    segment: widget.segments[index],
                    selected: index == selectedIndex,
                    hovered: index == _hoveredIndex,
                    height: widget.height,
                    horizontalPadding: horizontalPadding,
                    onHoverChanged: (hovered) {
                      setState(() {
                        _hoveredIndex = hovered ? index : null;
                        if (!hovered && _pressedIndex == index) {
                          _pressedIndex = null;
                        }
                      });
                    },
                    onPressedChanged: (pressed) {
                      setState(() => _pressedIndex = pressed ? index : null);
                    },
                    onTap: () => _selectSegment(index),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }

  _SegmentWidthFit _fitSegmentWidths({
    required List<double> contentWidths,
    required List<double> preferredWidths,
    required double minimumWidth,
    required double preferredWidth,
    required double targetWidth,
  }) {
    if (targetWidth >= preferredWidth) {
      final scale = preferredWidth <= 0 ? 1.0 : targetWidth / preferredWidth;
      return _SegmentWidthFit(
        widths: [for (final width in preferredWidths) width * scale],
        horizontalPadding: _segmentPadding,
      );
    }

    final contentWidth = _sum(contentWidths);
    final padding = widget.segments.isEmpty
        ? _minimumSegmentPadding
        : ((targetWidth - contentWidth) / (widget.segments.length * 2))
              .clamp(_minimumSegmentPadding, _segmentPadding)
              .toDouble();
    final widths = [for (final width in contentWidths) width + (padding * 2)];
    final roundingDelta = targetWidth - _sum(widths);
    if (widths.isNotEmpty && targetWidth >= minimumWidth) {
      widths[widths.length - 1] += roundingDelta;
    }
    return _SegmentWidthFit(widths: widths, horizontalPadding: padding);
  }

  static double measureSegmentContentWidth<T>(
    BuildContext context,
    Segment<T> segment,
  ) {
    final scale = MediaQuery.textScalerOf(context);
    final painter = TextPainter(
      text: TextSpan(text: segment.label, style: _segmentTextStyle),
      maxLines: 1,
      textDirection: Directionality.of(context),
      textScaler: scale,
    )..layout();
    final iconWidth = segment.icon == null ? 0.0 : _iconSize + _iconGap;
    final badgeWidth = segment.showBadge ? 8.0 : 0.0;
    return painter.width + iconWidth + badgeWidth + _contentWidthSafety;
  }

  void _selectSegment(int index) {
    widget.onChanged(widget.segments[index].value);
    if (_overflowLayoutLastBuild) {
      _scheduleCenterSelectedSegment(index);
    }
  }

  void _handleScrollChanged() {
    if (!_scrollController.hasClients) return;
    final show = _scrollController.offset > 1;
    if (show == _showJumpToStart || !mounted) return;
    setState(() => _showJumpToStart = show);
  }

  void _handlePointerSignal(PointerSignalEvent event) {
    if (event is! PointerScrollEvent || !_scrollController.hasClients) return;
    GestureBinding.instance.pointerSignalResolver.register(event, (
      resolvedEvent,
    ) {
      if (resolvedEvent is! PointerScrollEvent ||
          !_scrollController.hasClients) {
        return;
      }
      final horizontalDelta = resolvedEvent.scrollDelta.dx;
      final delta = horizontalDelta.abs() > 0
          ? horizontalDelta
          : resolvedEvent.scrollDelta.dy;
      if (delta == 0) return;
      final position = _scrollController.position;
      final target = (_scrollController.offset + delta)
          .clamp(position.minScrollExtent, position.maxScrollExtent)
          .toDouble();
      _scrollController.jumpTo(target);
    });
  }

  void _scrollToStart() {
    if (!_scrollController.hasClients) return;
    unawaited(
      _scrollController.animateTo(
        0,
        duration: _duration,
        curve: Curves.easeOutCubic,
      ),
    );
  }

  void _scheduleSelectedCenterIfNeeded(
    int selectedIndex, {
    required bool overflowLayoutChanged,
  }) {
    if (!overflowLayoutChanged && _selectedIndexLastBuild == selectedIndex) {
      return;
    }
    _scheduleCenterSelectedSegment(selectedIndex);
  }

  void _scheduleCenterSelectedSegment(int index) {
    if (_centerSelectedScheduled) return;
    _centerSelectedScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _centerSelectedScheduled = false;
      if (!mounted || !_overflowLayoutLastBuild) return;
      _centerSegment(index);
    });
  }

  void _centerSegment(int index) {
    if (!_scrollController.hasClients ||
        index < 0 ||
        index >= _overflowSegmentWidths.length) {
      return;
    }
    final position = _scrollController.position;
    final start = _sum(_overflowSegmentWidths.take(index));
    final end = start + _overflowSegmentWidths[index];
    final futureJumpButtonSpace = _showJumpToStart || start <= 0
        ? 0.0
        : widget.height + _overflowControlGap;
    final viewportWidth = (position.viewportDimension - futureJumpButtonSpace)
        .clamp(0.0, double.infinity)
        .toDouble();
    final target = (((start + end) / 2) - (viewportWidth / 2))
        .clamp(position.minScrollExtent, position.maxScrollExtent)
        .toDouble();
    if ((target - position.pixels).abs() < 0.5) return;
    unawaited(_animateToCenteredPosition(target, index));
  }

  Future<void> _animateToCenteredPosition(double target, int index) async {
    try {
      await _scrollController.animateTo(
        target,
        duration: _duration,
        curve: Curves.easeOutCubic,
      );
    } catch (_) {
      return;
    }
    if (mounted && _overflowLayoutLastBuild) {
      // Showing the left jump button reduces the viewport after the first
      // scroll frame. A second pass uses the final viewport and reaches the
      // exact center, or the nearest scroll edge when centering is impossible.
      _scheduleCenterSelectedSegment(index);
    }
  }

  Future<void> _showSelectionMenu() async {
    if (_menuOpen || widget.segments.isEmpty) return;
    final anchorBox = _menuAnchorKey.currentContext?.findRenderObject();
    if (anchorBox is! RenderBox || !anchorBox.hasSize) return;
    final position = anchorBox.localToGlobal(Offset(0, anchorBox.size.height));
    setState(() => _menuOpen = true);
    try {
      await showUiContextMenu(
        context,
        position: position,
        sections: [
          UiContextMenuSection([
            for (var index = 0; index < widget.segments.length; index++)
              UiContextMenuItem(
                label: widget.segments[index].label,
                icon: widget.segments[index].icon,
                selected: index == _selectedIndex,
                onPressed: () => _selectSegment(index),
              ),
          ]),
        ],
      );
    } finally {
      if (mounted) setState(() => _menuOpen = false);
    }
  }
}

class _SegmentWidthFit {
  const _SegmentWidthFit({
    required this.widths,
    required this.horizontalPadding,
  });

  final List<double> widths;
  final double horizontalPadding;
}

class _SegmentControlIconButton extends StatelessWidget {
  const _SegmentControlIconButton({
    super.key,
    required this.tooltip,
    required this.icon,
    required this.size,
    required this.onPressed,
    this.selected = false,
  });

  final String tooltip;
  final IconData icon;
  final double size;
  final VoidCallback onPressed;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return PressableSurface(
      tooltip: tooltip,
      onPressed: onPressed,
      selected: selected,
      width: size,
      height: size,
      borderRadius: UiRadii.pill,
      backgroundColor: UiColors.fieldFill,
      selectedBackgroundColor: UiColors.selected,
      borderColor: UiColors.border,
      selectedBorderColor: UiColors.accentBorder,
      child: Icon(
        icon,
        size: _SegmentedControlState._iconSize + 2,
        color: selected ? UiColors.accent : UiColors.textSecondary,
      ),
    );
  }
}

double _sum(Iterable<double> values) {
  return values.fold<double>(0, (sum, value) => sum + value);
}

bool _segmentWidthsDiffer(List<double> left, List<double> right) {
  if (left.length != right.length) return true;
  for (var index = 0; index < left.length; index++) {
    if ((left[index] - right[index]).abs() > 0.5) return true;
  }
  return false;
}

class _SegmentThumb extends StatelessWidget {
  const _SegmentThumb({required this.pressed});

  final bool pressed;

  @override
  Widget build(BuildContext context) {
    final color = pressed ? const Color(0xFFD9D9D6) : UiColors.inverse;
    return AnimatedContainer(
      duration: UiMotion.fast,
      curve: UiMotion.curve,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(UiRadii.pill),
        boxShadow: UiShadows.raised,
      ),
    );
  }
}

class _SegmentHitTarget<T> extends StatelessWidget {
  const _SegmentHitTarget({
    required this.segment,
    required this.selected,
    required this.hovered,
    required this.height,
    required this.horizontalPadding,
    required this.onHoverChanged,
    required this.onPressedChanged,
    required this.onTap,
  });

  final Segment<T> segment;
  final bool selected;
  final bool hovered;
  final double height;
  final double horizontalPadding;
  final ValueChanged<bool> onHoverChanged;
  final ValueChanged<bool> onPressedChanged;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final foreground = selected
        ? UiColors.onInverse
        : hovered
        ? UiColors.text
        : UiColors.textSecondary;
    final iconColor = foreground;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => onHoverChanged(true),
      onExit: (_) => onHoverChanged(false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: (_) => onPressedChanged(true),
        onTapUp: (_) => onPressedChanged(false),
        onTapCancel: () => onPressedChanged(false),
        onTap: onTap,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Container(
              alignment: Alignment.center,
              height: height,
              padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
              child: Center(
                child: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (segment.icon != null) ...[
                            Icon(
                              segment.icon,
                              size: _SegmentedControlState._iconSize,
                              color: iconColor,
                            ),
                            const SizedBox(
                              width: _SegmentedControlState._iconGap,
                            ),
                          ],
                          Text(
                            segment.label,
                            maxLines: 1,
                            softWrap: false,
                            overflow: TextOverflow.visible,
                            style: _SegmentedControlState._segmentTextStyle
                                .copyWith(
                                  color: foreground,
                                  fontWeight: selected
                                      ? FontWeight.w600
                                      : FontWeight.w500,
                                ),
                          ),
                        ],
                      ),
                    ),
                    if (segment.showBadge)
                      Positioned(
                        key: segment.badgeKey,
                        top: -3,
                        right: -6,
                        child: const BadgeDot(size: 7),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

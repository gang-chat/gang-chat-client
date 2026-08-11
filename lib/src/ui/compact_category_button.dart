import 'package:flutter/material.dart';

import 'tokens.dart';

/// Compact, flat category selector shared by search and dense filter rows.
class CompactCategoryButton extends StatefulWidget {
  const CompactCategoryButton({
    super.key,
    required this.label,
    required this.selected,
    required this.onPressed,
    this.count,
    this.height = 29,
  });

  final String label;
  final int? count;
  final bool selected;
  final VoidCallback onPressed;
  final double height;

  @override
  State<CompactCategoryButton> createState() => _CompactCategoryButtonState();
}

class _CompactCategoryButtonState extends State<CompactCategoryButton> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final active = widget.selected || _hovered;
    final count = widget.count;
    final text = count == null ? widget.label : '${widget.label} $count';
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onPressed,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 90),
          height: widget.height,
          padding: const EdgeInsets.symmetric(horizontal: 8),
          decoration: BoxDecoration(
            color: widget.selected
                ? UiColors.selected
                : active
                ? UiColors.surface
                : UiColors.surfaceLow,
            borderRadius: BorderRadius.circular(UiRadii.sm),
            border: Border.all(
              color: widget.selected
                  ? UiColors.selectedBorder
                  : UiColors.border,
            ),
          ),
          child: Center(
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: UiTypography.label.copyWith(
                color: widget.selected ? UiColors.accent : UiColors.text,
                fontSize: 11,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

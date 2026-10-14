import 'package:flutter/material.dart';

import 'surface.dart';
import 'tokens.dart';

class Sidebar extends StatelessWidget {
  const Sidebar({
    super.key,
    required this.groups,
    this.selectedId,
    this.onItemSelected,
    this.header,
    this.footer,
    this.width = 248,
    this.height,
    this.padding = const EdgeInsets.fromLTRB(12, 18, 12, 14),
    this.backgroundColor = UiColors.background,
    this.borderColor = UiColors.border,
  });

  final List<SidebarGroup> groups;
  final String? selectedId;
  final ValueChanged<String>? onItemSelected;
  final Widget? header;
  final Widget? footer;
  final double width;
  final double? height;
  final EdgeInsetsGeometry padding;
  final Color backgroundColor;
  final Color borderColor;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      height: height,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: backgroundColor,
          border: Border(right: BorderSide(color: borderColor)),
        ),
        child: Padding(
          padding: padding,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final content = _SidebarGroups(
                groups: groups,
                selectedId: selectedId,
                onItemSelected: onItemSelected,
              );

              if (!constraints.maxHeight.isFinite) {
                return Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (header != null) ...[
                      header!,
                      const SizedBox(height: 20),
                    ],
                    content,
                    if (footer != null) ...[
                      const SizedBox(height: 20),
                      footer!,
                    ],
                  ],
                );
              }

              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (header != null) ...[header!, const SizedBox(height: 20)],
                  Expanded(child: SingleChildScrollView(child: content)),
                  if (footer != null) ...[const SizedBox(height: 16), footer!],
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class SidebarGroup {
  const SidebarGroup({this.label, required this.items});

  final String? label;
  final List<SidebarItem> items;
}

class SidebarItem {
  const SidebarItem({
    required this.id,
    required this.label,
    required this.icon,
    this.badge,
    this.enabled = true,
    this.tooltip,
  });

  final String id;
  final String label;
  final IconData icon;
  final String? badge;
  final bool enabled;
  final String? tooltip;
}

class _SidebarGroups extends StatelessWidget {
  const _SidebarGroups({
    required this.groups,
    required this.selectedId,
    required this.onItemSelected,
  });

  final List<SidebarGroup> groups;
  final String? selectedId;
  final ValueChanged<String>? onItemSelected;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var groupIndex = 0; groupIndex < groups.length; groupIndex++) ...[
          if (groupIndex > 0) const SizedBox(height: 14),
          _SidebarGroupBlock(
            group: groups[groupIndex],
            selectedId: selectedId,
            onItemSelected: onItemSelected,
          ),
        ],
      ],
    );
  }
}

class _SidebarGroupBlock extends StatelessWidget {
  const _SidebarGroupBlock({
    required this.group,
    required this.selectedId,
    required this.onItemSelected,
  });

  final SidebarGroup group;
  final String? selectedId;
  final ValueChanged<String>? onItemSelected;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (group.label != null) ...[
          Padding(
            padding: const EdgeInsets.only(left: 10, bottom: 6),
            child: Text(
              group.label!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: UiTypography.overline,
            ),
          ),
        ],
        for (
          var itemIndex = 0;
          itemIndex < group.items.length;
          itemIndex++
        ) ...[
          if (itemIndex > 0) const SizedBox(height: 2),
          _SidebarItemButton(
            item: group.items[itemIndex],
            selected: group.items[itemIndex].id == selectedId,
            onPressed: onItemSelected == null
                ? null
                : () => onItemSelected!(group.items[itemIndex].id),
          ),
        ],
      ],
    );
  }
}

class _SidebarItemButton extends StatelessWidget {
  const _SidebarItemButton({
    required this.item,
    required this.selected,
    required this.onPressed,
  });

  final SidebarItem item;
  final bool selected;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final enabled = item.enabled && onPressed != null;
    final foreground = !item.enabled
        ? UiColors.textMuted
        : selected
        ? UiColors.onInverse
        : UiColors.textSecondary;
    final accent = selected ? UiColors.onInverse : UiColors.textMuted;

    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : null;
        return PressableSurface(
          width: width,
          height: 36,
          onPressed: enabled ? onPressed : null,
          tooltip: item.tooltip,
          enabled: item.enabled,
          selected: selected,
          backgroundColor: Colors.transparent,
          hoverBackgroundColor: UiColors.surfaceRaised,
          selectedBackgroundColor: UiColors.inverse,
          disabledBackgroundColor: Colors.transparent,
          borderColor: Colors.transparent,
          selectedBorderColor: Colors.transparent,
          disabledBorderColor: Colors.transparent,
          borderRadius: UiRadii.pill,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: IconTheme.merge(
            data: IconThemeData(color: accent, size: 18),
            child: DefaultTextStyle.merge(
              style: TextStyle(
                color: foreground,
                fontSize: 13,
                fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                letterSpacing: 0,
              ),
              child: Row(
                children: [
                  Icon(item.icon),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      item.label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (item.badge != null) ...[
                    const SizedBox(width: 8),
                    _SidebarBadge(label: item.badge!, selected: selected),
                  ],
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _SidebarBadge extends StatelessWidget {
  const _SidebarBadge({required this.label, required this.selected});

  final String label;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final color = selected ? UiColors.onAccent : UiColors.textSecondary;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: selected ? UiColors.accentStrong : UiColors.surfaceHover,
        borderRadius: BorderRadius.circular(UiRadii.pill),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        child: Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            color: color,
            fontSize: 11,
            fontWeight: FontWeight.w600,
            letterSpacing: 0,
          ),
        ),
      ),
    );
  }
}

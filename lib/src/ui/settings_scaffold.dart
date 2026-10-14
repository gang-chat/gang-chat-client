import 'package:flutter/material.dart';

import 'badge_dot.dart';
import 'button.dart';
import 'segmented_control.dart';
import 'surface.dart';
import 'title_bar.dart';
import 'tokens.dart';

/// Section navigation for [SettingsScaffold]. Wide pages render it as a
/// vertical list in a side column; narrow pages render it as a scrolling row
/// of pills above the content.
abstract class SettingsNavigationBase {
  const SettingsNavigationBase();

  String? get selectedLabel;
  IconData? get selectedIcon;

  Widget buildVertical(BuildContext context);
  Widget buildHorizontal(BuildContext context);
}

class SettingsNavigation<T> extends SettingsNavigationBase {
  const SettingsNavigation({
    required this.segments,
    required this.value,
    required this.onChanged,
  });

  final List<Segment<T>> segments;
  final T value;
  final ValueChanged<T> onChanged;

  Segment<T>? get _selected {
    for (final segment in segments) {
      if (segment.value == value) return segment;
    }
    return segments.isEmpty ? null : segments.first;
  }

  @override
  String? get selectedLabel => _selected?.label;

  @override
  IconData? get selectedIcon => _selected?.icon;

  @override
  Widget buildVertical(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final segment in segments) ...[
          _SettingsNavTile(
            segment: segment,
            selected: segment.value == value,
            onPressed: () => onChanged(segment.value),
          ),
          const SizedBox(height: 2),
        ],
      ],
    );
  }

  @override
  Widget buildHorizontal(BuildContext context) {
    return SizedBox(
      height: 34,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: segments.length,
        separatorBuilder: (context, index) => const SizedBox(width: 6),
        itemBuilder: (context, index) {
          final segment = segments[index];
          return _SettingsNavPill(
            segment: segment,
            selected: segment.value == value,
            onPressed: () => onChanged(segment.value),
          );
        },
      ),
    );
  }
}

class _SettingsNavTile<T> extends StatelessWidget {
  const _SettingsNavTile({
    required this.segment,
    required this.selected,
    required this.onPressed,
  });

  final Segment<T> segment;
  final bool selected;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final icon = segment.icon;
    return PressableSurface(
      height: 38,
      selected: selected,
      onPressed: onPressed,
      backgroundColor: Colors.transparent,
      hoverBackgroundColor: UiColors.surface,
      selectedBackgroundColor: UiColors.inverse,
      borderColor: Colors.transparent,
      selectedBorderColor: Colors.transparent,
      borderRadius: UiRadii.pill,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Row(
        children: [
          if (icon != null) ...[
            Icon(
              icon,
              size: 17,
              color: selected ? UiColors.onInverse : UiColors.textMuted,
            ),
            const SizedBox(width: 10),
          ],
          Expanded(
            child: Text(
              segment.label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: UiTypography.body.copyWith(
                fontSize: 13.5,
                color: selected ? UiColors.onInverse : UiColors.textSecondary,
                fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
              ),
            ),
          ),
          if (segment.showBadge)
            BadgeDot(
              key: segment.badgeKey,
              size: 7,
              borderColor: Colors.transparent,
            ),
        ],
      ),
    );
  }
}

class _SettingsNavPill<T> extends StatelessWidget {
  const _SettingsNavPill({
    required this.segment,
    required this.selected,
    required this.onPressed,
  });

  final Segment<T> segment;
  final bool selected;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final icon = segment.icon;
    return PressableSurface(
      height: 34,
      selected: selected,
      onPressed: onPressed,
      // The pills live in a horizontally scrolling row: a touch swipe must
      // scroll it instead of opening the section under the finger.
      cancelTouchPressOnDrag: true,
      backgroundColor: UiColors.surface,
      selectedBackgroundColor: UiColors.inverse,
      borderColor: Colors.transparent,
      selectedBorderColor: Colors.transparent,
      borderRadius: UiRadii.pill,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(
              icon,
              size: 15,
              color: selected ? UiColors.onInverse : UiColors.textMuted,
            ),
            const SizedBox(width: 6),
          ],
          Text(
            segment.label,
            style: UiTypography.label.copyWith(
              fontSize: 13,
              color: selected ? UiColors.onInverse : UiColors.textSecondary,
              fontWeight: FontWeight.w600,
            ),
          ),
          if (segment.showBadge) ...[
            const SizedBox(width: 6),
            BadgeDot(
              key: segment.badgeKey,
              size: 7,
              borderColor: Colors.transparent,
            ),
          ],
        ],
      ),
    );
  }
}

/// 统一的设置类页面外壳。
///
/// 有分区导航时，宽屏采用"左侧竖向导航 + 右侧内容"的双栏布局，窄屏退化为
/// 顶部胶囊导航；没有分区导航时是带标题的单栏页面。调用方只需提供图标、
/// 标题、内容和可选的右侧操作，版式由模版统一管理。
class SettingsScaffold extends StatelessWidget {
  const SettingsScaffold({
    super.key,
    required this.icon,
    required this.title,
    required this.body,
    this.headerAction,
    this.iconColor,
    this.onBack,
    this.pinned,
    this.navigation,
    this.showTitle = true,
  });

  /// 页面图标。
  final IconData icon;

  /// 页面标题。
  final String title;

  /// 右侧额外操作(如刷新按钮),可空。
  final Widget? headerAction;

  /// 图标颜色;为空时使用默认强调色。
  final Color? iconColor;

  /// 返回回调;为空则不显示返回按钮。
  final VoidCallback? onBack;

  /// 内容上方的固定区域(如搜索与筛选),不随内容滚动。
  final Widget? pinned;

  /// 分区导航。
  final SettingsNavigationBase? navigation;

  /// 页面已处在带标题的上下文里(如房间内的标签页)时可隐藏页面标题。
  final bool showTitle;

  /// 主体内容,通常是 [SettingsList] 或自定义滚动视图。
  final Widget body;

  static const double _sideNavWidth = 232;
  static const double _twoPaneMinWidth = 720;
  static const double _contentTopGap = 16;
  static const double _horizontalPadding = 28;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: UiColors.surfaceLow,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final navigation = this.navigation;
          if (navigation != null && constraints.maxWidth >= _twoPaneMinWidth) {
            return _buildTwoPane(context, navigation);
          }
          return _buildSinglePane(context, navigation);
        },
      ),
    );
  }

  Widget _buildTwoPane(
    BuildContext context,
    SettingsNavigationBase navigation,
  ) {
    final sectionTitle = navigation.selectedLabel ?? title;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          key: const ValueKey('settings-side-navigation'),
          width: _sideNavWidth,
          decoration: const BoxDecoration(
            color: UiColors.background,
            border: Border(right: BorderSide(color: UiColors.border)),
          ),
          padding: const EdgeInsets.fromLTRB(12, 18, 12, 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (showTitle) ...[
                Padding(
                  padding: const EdgeInsets.only(left: 6, right: 6),
                  child: Row(
                    children: [
                      _TitleIcon(icon: icon, color: iconColor),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: UiTypography.title,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 18),
              ],
              Expanded(
                child: SingleChildScrollView(
                  child: navigation.buildVertical(context),
                ),
              ),
              if (onBack != null) ...[
                const SizedBox(height: 10),
                _BackLink(onPressed: onBack!),
              ],
            ],
          ),
        ),
        Expanded(
          child: Column(
            children: [
              SizedBox(
                height: 72,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(
                    _horizontalPadding,
                    18,
                    _horizontalPadding - 4,
                    0,
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          sectionTitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: UiTypography.headline,
                        ),
                      ),
                      ?headerAction,
                    ],
                  ),
                ),
              ),
              if (pinned != null) ...[
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: _horizontalPadding,
                  ),
                  child: pinned,
                ),
                const SizedBox(height: _contentTopGap),
              ],
              Expanded(
                child: _SettingsBodyInsets(
                  horizontal: _horizontalPadding,
                  child: body,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildSinglePane(
    BuildContext context,
    SettingsNavigationBase? navigation,
  ) {
    final showHeader = showTitle || onBack != null || headerAction != null;
    final column = Column(
      children: [
        if (showHeader)
          Container(
            height: 16 + titleBarHeight + 48,
            padding: const EdgeInsets.fromLTRB(
              _horizontalPadding - 8,
              titleBarHeight + 16,
              _horizontalPadding - 8,
              0,
            ),
            child: Row(
              children: [
                if (onBack != null) ...[
                  ButtonIcon(
                    tooltip: '返回',
                    icon: const Icon(Icons.arrow_back_rounded),
                    onPressed: onBack,
                    size: 36,
                    ghost: true,
                  ),
                  const SizedBox(width: 8),
                ] else
                  const SizedBox(width: 8),
                if (showTitle) ...[
                  _TitleIcon(icon: icon, color: iconColor),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      title,
                      overflow: TextOverflow.ellipsis,
                      style: UiTypography.headline,
                    ),
                  ),
                ] else
                  const Spacer(),
                ?headerAction,
              ],
            ),
          ),
        if (navigation != null) ...[
          const SizedBox(height: _contentTopGap),
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: _horizontalPadding - 8,
            ),
            child: navigation.buildHorizontal(context),
          ),
        ],
        if (pinned != null) ...[
          const SizedBox(height: _contentTopGap),
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: _horizontalPadding - 8,
            ),
            child: pinned,
          ),
        ],
        const SizedBox(height: _contentTopGap),
        Expanded(
          child: _SettingsBodyInsets(
            horizontal: _horizontalPadding - 8,
            child: body,
          ),
        ),
      ],
    );
    return navigation == null ? _ReadableWidth(child: column) : column;
  }
}

/// Keeps single-column pages at a readable width on wide windows.
class _ReadableWidth extends StatelessWidget {
  const _ReadableWidth({required this.child});

  static const double maxWidth = 860;

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth <= maxWidth) return child;
        return Align(
          alignment: Alignment.topCenter,
          child: SizedBox(width: maxWidth, child: child),
        );
      },
    );
  }
}

/// Publishes the page's horizontal inset so [SettingsList] and
/// [SettingsFixedHeaderCard] align with the scaffold's header.
class _SettingsBodyInsets extends InheritedWidget {
  const _SettingsBodyInsets({required this.horizontal, required super.child});

  final double horizontal;

  static double of(BuildContext context, double fallback) {
    return context
            .dependOnInheritedWidgetOfExactType<_SettingsBodyInsets>()
            ?.horizontal ??
        fallback;
  }

  @override
  bool updateShouldNotify(_SettingsBodyInsets oldWidget) =>
      horizontal != oldWidget.horizontal;
}

class _TitleIcon extends StatelessWidget {
  const _TitleIcon({required this.icon, this.color});

  final IconData icon;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final color = this.color ?? UiColors.accent;
    return Container(
      width: 32,
      height: 32,
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(UiRadii.md),
      ),
      child: Icon(icon, color: color, size: 18),
    );
  }
}

class _BackLink extends StatelessWidget {
  const _BackLink({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return PressableSurface(
      key: const ValueKey('settings-back-button'),
      height: 36,
      tooltip: '返回',
      onPressed: onPressed,
      backgroundColor: Colors.transparent,
      hoverBackgroundColor: UiColors.surface,
      borderColor: UiColors.border,
      borderRadius: UiRadii.md - 2,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      child: Row(
        children: [
          const Icon(
            Icons.arrow_back_rounded,
            size: 16,
            color: UiColors.textSecondary,
          ),
          const SizedBox(width: 8),
          Text(
            '返回',
            style: UiTypography.label.copyWith(
              color: UiColors.textSecondary,
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }
}

/// 设置内容列表:在各 item 之间自动插入固定间距,避免手写 `SizedBox`。
class SettingsList extends StatelessWidget {
  const SettingsList({
    super.key,
    required this.children,
    this.padding,
    this.spacing = 14,
    this.physics,
  });

  /// 列表项。相邻项之间会自动插入 [spacing] 的间距。
  final List<Widget> children;

  /// 列表外边距。为空时与外层 [SettingsScaffold] 的水平边距对齐。
  final EdgeInsetsGeometry? padding;

  /// 相邻项之间的固定间距。
  final double spacing;

  /// Optional scroll physics for surfaces that need stricter edge behavior.
  final ScrollPhysics? physics;

  @override
  Widget build(BuildContext context) {
    final inset = _SettingsBodyInsets.of(context, 24);
    final padding = this.padding ?? EdgeInsets.fromLTRB(inset, 0, inset, 24);
    if (children.isEmpty) {
      return ListView(padding: padding, physics: physics);
    }
    return ListView.separated(
      padding: padding,
      physics: physics,
      itemCount: children.length,
      separatorBuilder: (_, _) => SizedBox(height: spacing),
      itemBuilder: (_, index) => children[index],
    );
  }
}

/// 带卡片边框与小标题的分区。内部 children 之间自动插入固定间距。
class SettingsCard extends StatelessWidget {
  const SettingsCard({
    super.key,
    required this.title,
    required this.children,
    this.titleWidget,
    this.trailing,
    this.danger = false,
    this.spacing = 12,
  });

  /// 分区小标题。
  final String title;

  /// 自定义分区标题。为空时使用 [title] 与统一的标题文字样式。
  final Widget? titleWidget;

  /// 分区内容。相邻项之间会自动插入 [spacing] 的间距。
  final List<Widget> children;

  /// 标题右侧的额外元素(如加载指示器),可空。
  final Widget? trailing;

  /// 危险分区(如删除操作),标题与边框使用警示色。
  final bool danger;

  /// 相邻内容项之间的固定间距。
  final double spacing;

  @override
  Widget build(BuildContext context) {
    final items = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) items.add(SizedBox(height: spacing));
      items.add(children[i]);
    }
    return DecoratedBox(
      decoration: BoxDecoration(
        color: danger ? const Color(0xFF1C1418) : UiColors.card,
        borderRadius: BorderRadius.circular(UiRadii.lg),
        border: Border.all(
          color: danger ? UiColors.dangerBorder : UiColors.border,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child:
                      titleWidget ??
                      Text(
                        title,
                        style: UiTypography.subtitle.copyWith(
                          color: danger ? UiColors.danger : UiColors.text,
                        ),
                      ),
                ),
                ?trailing,
              ],
            ),
            if (items.isNotEmpty) ...[const SizedBox(height: 14), ...items],
          ],
        ),
      ),
    );
  }
}

/// A settings card whose title and controls stay visible while only [body]
/// scrolls. The parent must provide bounded height, as [SettingsScaffold]
/// does for its body.
class SettingsFixedHeaderCard extends StatelessWidget {
  const SettingsFixedHeaderCard({
    super.key,
    required this.title,
    required this.body,
    this.headerChildren = const [],
    this.titleWidget,
    this.trailing,
    this.spacing = 10,
    this.padding,
  });

  final String title;
  final Widget body;
  final List<Widget> headerChildren;
  final Widget? titleWidget;
  final Widget? trailing;
  final double spacing;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    final inset = _SettingsBodyInsets.of(context, 24);
    return Padding(
      padding: padding ?? EdgeInsets.fromLTRB(inset, 0, inset, 24),
      child: SettingsCard(
        title: title,
        titleWidget: titleWidget,
        trailing: trailing,
        spacing: spacing,
        children: [
          ...headerChildren,
          Expanded(child: body),
        ],
      ),
    );
  }
}

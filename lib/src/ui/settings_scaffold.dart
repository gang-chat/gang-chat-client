import 'package:flutter/material.dart';

import 'button.dart';
import 'title_bar.dart';
import 'tokens.dart';

/// 统一的设置类页面外壳:固定标题栏 + 可选导航区 + 内容区。
///
/// 标题栏布局由模版统一管理,调用方只需提供图标、标题、可选的右侧额外
/// 元素与返回回调,无需再手动处理对齐与间距,从根本上避免各页面标题栏
/// padding 不一致的问题。
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
  });

  /// 标题栏左侧图标。
  final IconData icon;

  /// 标题文本。
  final String title;

  /// 标题栏右侧额外元素(如刷新按钮),可空。
  final Widget? headerAction;

  /// 标题栏左侧图标颜色;为空时使用默认强调色。
  final Color? iconColor;

  /// 返回回调;为空则不显示返回按钮。
  final VoidCallback? onBack;

  /// 标题栏下方的固定区域(如设置页的分段导航),不随内容滚动。
  final Widget? pinned;

  /// 主体内容,通常是 [SettingsList] 或自定义滚动视图。
  final Widget body;

  /// 标题栏中内容(按钮/图标/标题)所占的高度,与窗口预留高度无关。
  static const double _headerContentHeight = 48;

  /// 标题栏与下方内容之间的固定间距:既用于标题到分类导航,也用于分类
  /// 导航到正文,以及无分类时标题到正文。所有设置类页面共用此间距,确保
  /// 标题与内容的留白始终一致。
  static const double _contentTopGap = 14;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: UiColors.surfaceLow,
      child: Column(
        children: [
          Container(
            height: titleBarHeight + 16 + _headerContentHeight,
            padding: const EdgeInsets.fromLTRB(22, titleBarHeight + 16, 22, 0),
            child: Row(
              children: [
                if (onBack != null) ...[
                  ButtonIcon(
                    tooltip: '返回',
                    icon: const Icon(Icons.arrow_back),
                    onPressed: onBack,
                    size: 38,
                  ),
                  const SizedBox(width: 16),
                ],
                Icon(icon, color: iconColor ?? UiColors.accent, size: 19),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    title,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: UiColors.text,
                      fontSize: 20,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                ?headerAction,
              ],
            ),
          ),
          if (pinned != null) ...[
            const SizedBox(height: _contentTopGap),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 22),
              child: pinned,
            ),
          ],
          const SizedBox(height: _contentTopGap),
          Expanded(child: body),
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
    this.padding = const EdgeInsets.fromLTRB(22, 0, 22, 22),
    this.spacing = 14,
    this.physics,
  });

  /// 列表项。相邻项之间会自动插入 [spacing] 的间距。
  final List<Widget> children;

  /// 列表外边距。
  final EdgeInsetsGeometry padding;

  /// 相邻项之间的固定间距。
  final double spacing;

  /// Optional scroll physics for surfaces that need stricter edge behavior.
  final ScrollPhysics? physics;

  @override
  Widget build(BuildContext context) {
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
        borderRadius: BorderRadius.circular(UiRadii.lg),
        border: Border.all(
          color: danger ? UiColors.dangerBorder : UiColors.border,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
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
                        style: TextStyle(
                          color: danger ? UiColors.danger : UiColors.text,
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
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

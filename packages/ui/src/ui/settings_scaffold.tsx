import { createContext, useContext, useState, type ReactNode } from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
  type LayoutChangeEvent,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

import { EdgeInsets, paddingStyle, type EdgeInsetsValue } from '../lib/edge_insets';
import { BadgeDot } from './badge_dot';
import { ButtonIcon } from './button';
import { Icon, Icons, type IconData } from './icon';
import type { Segment } from './segmented_control';
import { PressableSurface } from './surface';
import { titleBarHeight } from './title_bar';
import { UiColors, UiRadii, UiTypography, argb, withAlpha } from './tokens';

/**
 * Section navigation for [SettingsScaffold]. Wide pages render it as a
 * vertical list in a side column; narrow pages render it as a scrolling row
 * of pills above the content.
 */
export abstract class SettingsNavigationBase {
  abstract get selectedLabel(): string | null;
  abstract get selectedIcon(): IconData | null;

  abstract buildVertical(): ReactNode;
  abstract buildHorizontal(): ReactNode;
}

export class SettingsNavigation<T> extends SettingsNavigationBase {
  constructor(opts: { segments: readonly Segment<T>[]; value: T; onChanged: (value: T) => void }) {
    super();
    this.segments = opts.segments;
    this.value = opts.value;
    this.onChanged = opts.onChanged;
  }

  readonly segments: readonly Segment<T>[];
  readonly value: T;
  readonly onChanged: (value: T) => void;

  private get _selected(): Segment<T> | null {
    for (const segment of this.segments) {
      if (segment.value === this.value) return segment;
    }
    return this.segments.length === 0 ? null : this.segments[0]!;
  }

  override get selectedLabel(): string | null {
    return this._selected?.label ?? null;
  }

  override get selectedIcon(): IconData | null {
    return this._selected?.icon ?? null;
  }

  override buildVertical(): ReactNode {
    return (
      <View style={styles.stretchColumn}>
        {this.segments.map((segment, index) => (
          <View key={index}>
            <_SettingsNavTile
              segment={segment}
              selected={segment.value === this.value}
              onPressed={() => this.onChanged(segment.value)}
            />
            <View style={{ height: 2 }} />
          </View>
        ))}
      </View>
    );
  }

  override buildHorizontal(): ReactNode {
    return (
      <View style={{ height: 34 }}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
          {this.segments.map((segment, index) => (
            <View key={index} style={styles.row}>
              {index > 0 ? <View style={{ width: 6 }} /> : null}
              <_SettingsNavPill
                segment={segment}
                selected={segment.value === this.value}
                onPressed={() => this.onChanged(segment.value)}
              />
            </View>
          ))}
        </ScrollView>
      </View>
    );
  }
}

/**
 * Section navigation drawn as a strip of numbered tiles, each carrying a
 * one-line summary of the section's current values. Used by the app
 * settings page ("control room" layout).
 */
export class SettingsTileNavigation<T> extends SettingsNavigation<T> {
  constructor(opts: {
    segments: readonly Segment<T>[];
    value: T;
    onChanged: (value: T) => void;
    summaries?: ReadonlyMap<T, string>;
  }) {
    super(opts);
    this.summaries = opts.summaries ?? new Map<T, string>();
  }

  /** Current-value summaries keyed by section. */
  readonly summaries: ReadonlyMap<T, string>;

  get selectedIndex(): number {
    for (let i = 0; i < this.segments.length; i++) {
      if (this.segments[i]!.value === this.value) return i;
    }
    return 0;
  }

  buildTiles(opts: { compact: boolean; horizontalPadding: number; maxWidth: number }): ReactNode {
    const { compact, horizontalPadding, maxWidth } = opts;
    const gap = 8;
    const count = this.segments.length;
    const minTileWidth = 112;
    const available = maxWidth - horizontalPadding * 2;
    const fits = available >= count * minTileWidth + (count - 1) * gap;
    const tile = (index: number, width?: number) => {
      const segment = this.segments[index]!;
      return (
        <_SettingsTile
          index={index}
          segment={segment}
          summary={compact ? null : (this.summaries.get(segment.value) ?? null)}
          selected={segment.value === this.value}
          compact={compact}
          width={width}
          onPressed={() => this.onChanged(segment.value)}
        />
      );
    };

    if (fits) {
      return (
        <View style={[styles.row, styles.fill, { paddingHorizontal: horizontalPadding }]}>
          {this.segments.map((_, i) => (
            <View key={i} style={[styles.row, styles.expanded]}>
              {i > 0 ? <View style={{ width: gap }} /> : null}
              <View style={styles.expanded}>{tile(i)}</View>
            </View>
          ))}
        </View>
      );
    }
    // Only a handful of tiles: build them all so every section stays
    // reachable (and findable) even while scrolled out of view.
    return (
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.fill}
        contentContainerStyle={[styles.row, { paddingHorizontal: horizontalPadding }]}
      >
        {this.segments.map((_, i) => (
          <View key={i} style={styles.row}>
            {i > 0 ? <View style={{ width: gap }} /> : null}
            {tile(i, minTileWidth + 12)}
          </View>
        ))}
      </ScrollView>
    );
  }
}

interface _SettingsTileProps<T> {
  index: number;
  segment: Segment<T>;
  summary: string | null;
  selected: boolean;
  compact: boolean;
  width?: number;
  onPressed: () => void;
}

function _SettingsTile<T>({ index, segment, summary, selected, compact, width, onPressed }: _SettingsTileProps<T>) {
  const foreground = selected ? UiColors.onInverse : UiColors.text;
  const muted = selected ? withAlpha(UiColors.onInverse, 0.62) : UiColors.textMuted;
  const icon = segment.icon;
  return (
    <PressableSurface
      width={width}
      height={compact ? 64 : 84}
      selected={selected}
      onPressed={onPressed}
      // Tiles can live in a horizontally scrolling strip: a touch swipe must
      // scroll it instead of opening the section under the finger.
      cancelTouchPressOnDrag
      backgroundColor={UiColors.card}
      hoverBackgroundColor={UiColors.surfaceRaised}
      selectedBackgroundColor={UiColors.inverse}
      borderColor={UiColors.border}
      selectedBorderColor={UiColors.inverse}
      borderRadius={UiRadii.lg}
      padding={EdgeInsets.fromLTRB(12, compact ? 9 : 11, 10, compact ? 9 : 11)}
    >
      <View style={[styles.fill, { justifyContent: 'space-between' }]}>
        <View style={[styles.row, styles.centerRow]}>
          <Text
            numberOfLines={1}
            style={[UiTypography.dot, { fontSize: 13, lineHeight: 13, color: selected ? UiColors.onInverse : UiColors.accent }]}
          >
            {String(index + 1).padStart(2, '0')}
          </Text>
          <View style={styles.expanded} />
          {segment.showBadge ? (
            <>
              <BadgeDot size={7} borderColor={UiColors.transparent} />
              <View style={{ width: 6 }} />
            </>
          ) : null}
          {icon != null ? <Icon icon={icon} size={15} color={muted} /> : null}
        </View>
        <View>
          <Text
            numberOfLines={1}
            style={[UiTypography.body, { color: foreground, fontSize: 13, fontWeight: '700', lineHeight: 13 * 1.25 }]}
          >
            {segment.label}
          </Text>
          {summary != null && summary.length > 0 ? (
            <>
              <View style={{ height: 2 }} />
              <Text numberOfLines={1} style={[UiTypography.mono, { color: muted, fontSize: 10, lineHeight: 10 * 1.3 }]}>
                {summary}
              </Text>
            </>
          ) : null}
        </View>
      </View>
    </PressableSurface>
  );
}

interface _SettingsNavItemProps<T> {
  segment: Segment<T>;
  selected: boolean;
  onPressed: () => void;
}

function _SettingsNavTile<T>({ segment, selected, onPressed }: _SettingsNavItemProps<T>) {
  const icon = segment.icon;
  return (
    <PressableSurface
      height={38}
      selected={selected}
      onPressed={onPressed}
      backgroundColor={UiColors.transparent}
      hoverBackgroundColor={UiColors.surface}
      selectedBackgroundColor={UiColors.inverse}
      borderColor={UiColors.transparent}
      selectedBorderColor={UiColors.transparent}
      borderRadius={UiRadii.pill}
      padding={EdgeInsets.symmetric({ horizontal: 14 })}
    >
      <View style={[styles.row, styles.centerRow, styles.fill]}>
        {icon != null ? (
          <>
            <Icon icon={icon} size={17} color={selected ? UiColors.onInverse : UiColors.textMuted} />
            <View style={{ width: 10 }} />
          </>
        ) : null}
        <Text
          numberOfLines={1}
          style={[
            UiTypography.body,
            styles.expanded,
            {
              fontSize: 13.5,
              lineHeight: 13.5 * 1.4,
              color: selected ? UiColors.onInverse : UiColors.textSecondary,
              fontWeight: selected ? '700' : '500',
            },
          ]}
        >
          {segment.label}
        </Text>
        {segment.showBadge ? <BadgeDot size={7} borderColor={UiColors.transparent} /> : null}
      </View>
    </PressableSurface>
  );
}

function _SettingsNavPill<T>({ segment, selected, onPressed }: _SettingsNavItemProps<T>) {
  const icon = segment.icon;
  return (
    <PressableSurface
      height={34}
      selected={selected}
      onPressed={onPressed}
      // The pills live in a horizontally scrolling row: a touch swipe must
      // scroll it instead of opening the section under the finger.
      cancelTouchPressOnDrag
      backgroundColor={UiColors.surface}
      selectedBackgroundColor={UiColors.inverse}
      borderColor={UiColors.transparent}
      selectedBorderColor={UiColors.transparent}
      borderRadius={UiRadii.pill}
      padding={EdgeInsets.symmetric({ horizontal: 14 })}
    >
      <View style={[styles.row, styles.centerRow, styles.fill]}>
        {icon != null ? (
          <>
            <Icon icon={icon} size={15} color={selected ? UiColors.onInverse : UiColors.textMuted} />
            <View style={{ width: 6 }} />
          </>
        ) : null}
        <Text
          numberOfLines={1}
          style={[
            UiTypography.label,
            { fontSize: 13, lineHeight: 13 * 1.35, color: selected ? UiColors.onInverse : UiColors.textSecondary, fontWeight: '600' },
          ]}
        >
          {segment.label}
        </Text>
        {segment.showBadge ? (
          <>
            <View style={{ width: 6 }} />
            <BadgeDot size={7} borderColor={UiColors.transparent} />
          </>
        ) : null}
      </View>
    </PressableSurface>
  );
}

export interface SettingsScaffoldProps {
  /** Search field shown in the header of the tile ("control room") layout. */
  search?: ReactNode;

  /**
   * Replaces the tile layout's section title and its `01 / 07` counter,
   * e.g. while search results are shown.
   */
  sectionTitleOverride?: string | null;
  sectionMetaOverride?: string | null;

  /** 页面图标。 */
  icon: IconData;

  /** 页面标题。 */
  title: string;

  /** 右侧额外操作(如刷新按钮),可空。 */
  headerAction?: ReactNode;

  /** 图标颜色;为空时使用默认强调色。 */
  iconColor?: string | null;

  /** 返回回调;为空则不显示返回按钮。 */
  onBack?: (() => void) | null;

  /** 内容上方的固定区域(如搜索与筛选),不随内容滚动。 */
  pinned?: ReactNode;

  /** 分区导航。 */
  navigation?: SettingsNavigationBase | null;

  /** 页面已处在带标题的上下文里(如房间内的标签页)时可隐藏页面标题。 */
  showTitle?: boolean;

  /** 主体内容,通常是 [SettingsList] 或自定义滚动视图。 */
  body: ReactNode;
}

const _sideNavWidth = 232;
const _singlePaneHeaderContentHeight = 48;
const _singlePaneContentTopGap = 14;
const _twoPaneMinWidth = 720;
const _contentTopGap = 16;
const _horizontalPadding = 28;

/**
 * 统一的设置类页面外壳。
 *
 * 有分区导航时，宽屏采用"左侧竖向导航 + 右侧内容"的双栏布局，窄屏退化为
 * 顶部胶囊导航；没有分区导航时是带标题的单栏页面。调用方只需提供图标、
 * 标题、内容和可选的右侧操作，版式由模版统一管理。
 */
export function SettingsScaffold(props: SettingsScaffoldProps) {
  const window = useWindowDimensions();
  // LayoutBuilder: the window width stands in until the first layout pass.
  const [measuredWidth, setMeasuredWidth] = useState<number | null>(null);
  const maxWidth = measuredWidth ?? window.width;
  const onLayout = (event: LayoutChangeEvent) => {
    const width = event.nativeEvent.layout.width;
    if (width !== measuredWidth) setMeasuredWidth(width);
  };
  const navigation = props.navigation ?? null;
  let content: ReactNode;
  if (navigation instanceof SettingsTileNavigation) {
    content = _buildTileLayout(props, navigation as SettingsTileNavigation<unknown>, maxWidth);
  } else if (navigation != null && maxWidth >= _twoPaneMinWidth) {
    content = _buildTwoPane(props, navigation);
  } else {
    content = _buildSinglePane(props, navigation);
  }
  return (
    <View style={styles.scaffold} onLayout={onLayout}>
      {content}
    </View>
  );
}

/**
 * Control-room layout: a header (back, wordmark, search, action), a strip
 * of numbered section tiles with live summaries, the section title, then
 * the section body.
 */
function _buildTileLayout(props: SettingsScaffoldProps, navigation: SettingsTileNavigation<unknown>, maxWidth: number): ReactNode {
  const { onBack = null, search = null, showTitle = true, title, headerAction = null, pinned = null, body } = props;
  const compact = maxWidth < _twoPaneMinWidth;
  const padding = compact ? 16 : _horizontalPadding;
  const sectionTitle = props.sectionTitleOverride ?? navigation.selectedLabel ?? title;
  const index = navigation.selectedIndex + 1;
  const total = navigation.segments.length;
  const sectionMeta = props.sectionMetaOverride ?? `${String(index).padStart(2, '0')} / ${String(total).padStart(2, '0')}`;
  return (
    <View style={styles.fill}>
      <View
        style={[
          styles.row,
          styles.centerRow,
          {
            paddingLeft: onBack == null ? padding : padding - 8,
            paddingTop: compact ? 14 : 20,
            paddingRight: padding - 4,
          },
        ]}
      >
        {onBack != null ? (
          <>
            <ButtonIcon
              tooltip="返回"
              icon={<Icon icon={Icons.arrow_back_rounded} />}
              onPressed={onBack}
              size={36}
              ghost
            />
            <View style={{ width: 6 }} />
          </>
        ) : null}
        <View style={{ flexShrink: 1, minWidth: 0 }}>
          <Text
            numberOfLines={1}
            style={[UiTypography.dot, { fontSize: compact ? 18 : 22, lineHeight: (compact ? 18 : 22) * 1.05, letterSpacing: 1 }]}
          >
            SETTINGS
          </Text>
          {showTitle ? (
            <>
              <View style={{ height: 3 }} />
              <Text numberOfLines={1} style={[UiTypography.label, { color: UiColors.textMuted }]}>
                {title}
              </Text>
            </>
          ) : null}
        </View>
        <View style={{ width: 16 }} />
        <View style={[styles.expanded, { alignItems: 'flex-end' }]}>
          {search == null || compact ? null : <View style={{ maxWidth: 340, width: '100%' }}>{search}</View>}
        </View>
        {headerAction != null ? (
          <>
            <View style={{ width: 8 }} />
            {headerAction}
          </>
        ) : null}
      </View>
      {search != null && compact ? (
        <>
          <View style={{ height: 12 }} />
          <View style={{ paddingHorizontal: padding }}>{search}</View>
        </>
      ) : null}
      <View style={{ height: compact ? 14 : 18 }} />
      <View testID="settings-side-navigation" style={{ height: compact ? 64 : 84 }}>
        {navigation.buildTiles({ compact, horizontalPadding: padding, maxWidth })}
      </View>
      <View
        style={[
          styles.row,
          { alignItems: 'flex-end', paddingLeft: padding, paddingTop: compact ? 16 : 22, paddingRight: padding, paddingBottom: 14 },
        ]}
      >
        <Text numberOfLines={1} style={[compact ? UiTypography.title : UiTypography.headline, styles.expanded]}>
          {sectionTitle}
        </Text>
        <View style={{ width: 12 }} />
        <Text style={UiTypography.overline}>{sectionMeta}</Text>
      </View>
      {pinned != null ? (
        <>
          <View style={{ paddingHorizontal: padding }}>{pinned}</View>
          <View style={{ height: _contentTopGap }} />
        </>
      ) : null}
      <_SettingsBodyInsets horizontal={padding}>{body}</_SettingsBodyInsets>
    </View>
  );
}

function _buildTwoPane(props: SettingsScaffoldProps, navigation: SettingsNavigationBase): ReactNode {
  const { showTitle = true, icon, iconColor = null, title, onBack = null, headerAction = null, pinned = null, body } = props;
  const sectionTitle = navigation.selectedLabel ?? title;
  return (
    <View style={[styles.row, styles.fill, { alignItems: 'stretch' }]}>
      <View testID="settings-side-navigation" style={styles.sideNav}>
        {showTitle ? (
          <>
            <View style={[styles.row, styles.centerRow, { paddingHorizontal: 6 }]}>
              <_TitleIcon icon={icon} color={iconColor} />
              <View style={{ width: 10 }} />
              <Text numberOfLines={1} style={[UiTypography.title, styles.expanded]}>
                {title}
              </Text>
            </View>
            <View style={{ height: 18 }} />
          </>
        ) : null}
        <ScrollView style={styles.expanded}>{navigation.buildVertical()}</ScrollView>
        {onBack != null ? (
          <>
            <View style={{ height: 10 }} />
            <_BackLink onPressed={onBack} />
          </>
        ) : null}
      </View>
      <View style={[styles.expanded, { minWidth: 0 }]}>
        <View style={{ height: 72 }}>
          <View
            style={[
              styles.row,
              styles.centerRow,
              styles.fill,
              { paddingLeft: _horizontalPadding, paddingTop: 18, paddingRight: _horizontalPadding - 4 },
            ]}
          >
            <Text numberOfLines={1} style={[UiTypography.headline, styles.expanded]}>
              {sectionTitle}
            </Text>
            {headerAction}
          </View>
        </View>
        {pinned != null ? (
          <>
            <View style={{ paddingHorizontal: _horizontalPadding }}>{pinned}</View>
            <View style={{ height: _contentTopGap }} />
          </>
        ) : null}
        <_SettingsBodyInsets horizontal={_horizontalPadding}>{body}</_SettingsBodyInsets>
      </View>
    </View>
  );
}

/**
 * Single-column page: header (back, icon, title, action), optional
 * pinned area, then the full-width body. With a [navigation] the section
 * pills sit under the header.
 */
function _buildSinglePane(props: SettingsScaffoldProps, navigation: SettingsNavigationBase | null): ReactNode {
  const { onBack = null, showTitle = true, icon, iconColor = null, title, headerAction = null, pinned = null, body } = props;
  const horizontalPadding = 22;
  return (
    <View style={styles.fill}>
      <View
        style={[
          styles.row,
          styles.centerRow,
          {
            height: titleBarHeight + 16 + _singlePaneHeaderContentHeight,
            paddingLeft: horizontalPadding,
            paddingTop: titleBarHeight + 16,
            paddingRight: horizontalPadding,
          },
        ]}
      >
        {onBack != null ? (
          <>
            <ButtonIcon tooltip="返回" icon={<Icon icon={Icons.arrow_back} />} onPressed={onBack} size={38} />
            <View style={{ width: 16 }} />
          </>
        ) : null}
        {showTitle ? (
          <>
            <Icon icon={icon} color={iconColor ?? UiColors.accent} size={19} />
            <View style={{ width: 8 }} />
            <Text numberOfLines={1} style={[UiTypography.headline, styles.expanded, { fontSize: 20, lineHeight: 20 * 1.25 }]}>
              {title}
            </Text>
          </>
        ) : (
          <View style={styles.expanded} />
        )}
        {headerAction}
      </View>
      {navigation != null ? (
        <>
          <View style={{ height: _singlePaneContentTopGap }} />
          <View style={{ paddingHorizontal: horizontalPadding }}>{navigation.buildHorizontal()}</View>
        </>
      ) : null}
      {pinned != null ? (
        <>
          <View style={{ height: _singlePaneContentTopGap }} />
          <View style={{ paddingHorizontal: horizontalPadding }}>{pinned}</View>
        </>
      ) : null}
      <View style={{ height: _singlePaneContentTopGap }} />
      <_SettingsBodyInsets horizontal={horizontalPadding}>{body}</_SettingsBodyInsets>
    </View>
  );
}

/**
 * Publishes the page's horizontal inset so [SettingsList] and
 * [SettingsFixedHeaderCard] align with the scaffold's header.
 */
const _SettingsBodyInsetsContext = createContext<number | null>(null);

function _SettingsBodyInsets({ horizontal, children }: { horizontal: number; children: ReactNode }) {
  return (
    <_SettingsBodyInsetsContext.Provider value={horizontal}>
      <View style={styles.body}>{children}</View>
    </_SettingsBodyInsetsContext.Provider>
  );
}

/** `_SettingsBodyInsets.of(context, fallback)`. */
function _useSettingsBodyInset(fallback: number): number {
  return useContext(_SettingsBodyInsetsContext) ?? fallback;
}

function _TitleIcon({ icon, color }: { icon: IconData; color?: string | null }) {
  const resolved = color ?? UiColors.accent;
  return (
    <View
      style={{
        width: 32,
        height: 32,
        backgroundColor: withAlpha(resolved, 0.14),
        borderRadius: UiRadii.md,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon icon={icon} color={resolved} size={18} />
    </View>
  );
}

function _BackLink({ onPressed }: { onPressed: () => void }) {
  return (
    <PressableSurface
      height={36}
      tooltip="返回"
      onPressed={onPressed}
      backgroundColor={UiColors.transparent}
      hoverBackgroundColor={UiColors.surface}
      borderColor={UiColors.border}
      borderRadius={UiRadii.md - 2}
      padding={EdgeInsets.symmetric({ horizontal: 10 })}
    >
      <View testID="settings-back-button" style={[styles.row, styles.centerRow, styles.fill]}>
        <Icon icon={Icons.arrow_back_rounded} size={16} color={UiColors.textSecondary} />
        <View style={{ width: 8 }} />
        <Text style={[UiTypography.label, { color: UiColors.textSecondary, fontSize: 13, lineHeight: 13 * 1.35 }]}>返回</Text>
      </View>
    </PressableSurface>
  );
}

export interface SettingsListProps {
  /** 列表项。相邻项之间会自动插入 [spacing] 的间距。 */
  children: ReactNode[];

  /** 列表外边距。为空时与外层 [SettingsScaffold] 的水平边距对齐。 */
  padding?: EdgeInsetsValue | null;

  /** 相邻项之间的固定间距。 */
  spacing?: number;

  /**
   * Optional scroll physics for surfaces that need stricter edge behavior.
   * PORT: Flutter `ScrollPhysics`; only `'clamping'` (`ClampingScrollPhysics`,
   * no overscroll bounce) is meaningful in React Native.
   */
  physics?: 'clamping' | null;
}

/** 设置内容列表:在各 item 之间自动插入固定间距,避免手写 `SizedBox`。 */
export function SettingsList({ children, padding = null, spacing = 14, physics = null }: SettingsListProps) {
  const inset = _useSettingsBodyInset(24);
  const resolvedPadding = padding ?? EdgeInsets.fromLTRB(inset, 0, inset, 24);
  const items = children.filter((child) => child != null && child !== false);
  return (
    <ScrollView
      style={styles.expanded}
      contentContainerStyle={paddingStyle(resolvedPadding)}
      bounces={physics !== 'clamping'}
      overScrollMode={physics === 'clamping' ? 'never' : 'auto'}
    >
      {items.map((child, index) => (
        <View key={index}>
          {index > 0 ? <View style={{ height: spacing }} /> : null}
          {child}
        </View>
      ))}
    </ScrollView>
  );
}

export interface SettingsCardProps {
  /** 分区小标题。 */
  title: string;

  /** 自定义分区标题。为空时使用 [title] 与统一的标题文字样式。 */
  titleWidget?: ReactNode;

  /** 分区内容。相邻项之间会自动插入 [spacing] 的间距。 */
  children: ReactNode[];

  /** 标题右侧的额外元素(如加载指示器),可空。 */
  trailing?: ReactNode;

  /** 危险分区(如删除操作),标题与边框使用警示色。 */
  danger?: boolean;

  /** 相邻内容项之间的固定间距。 */
  spacing?: number;

  /** Extra style for the card (e.g. `flex: 1` when a child must fill it). */
  style?: StyleProp<ViewStyle>;
}

/** 带卡片边框与小标题的分区。内部 children 之间自动插入固定间距。 */
export function SettingsCard({ title, titleWidget = null, children, trailing = null, danger = false, spacing = 12, style }: SettingsCardProps) {
  const items: ReactNode[] = [];
  children.forEach((child, i) => {
    if (i > 0) items.push(<View key={`gap-${i}`} style={{ height: spacing }} />);
    items.push(<_SettingsCardChild key={`item-${i}`}>{child}</_SettingsCardChild>);
  });
  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: danger ? argb(0xff1c1418) : UiColors.card,
          borderColor: danger ? UiColors.dangerBorder : UiColors.border,
        },
        style,
      ]}
    >
      <View style={[styles.row, styles.centerRow]}>
        <View style={styles.expanded}>
          {titleWidget ?? <Text style={[UiTypography.subtitle, { color: danger ? UiColors.danger : UiColors.text }]}>{title}</Text>}
        </View>
        {trailing}
      </View>
      {items.length > 0 ? (
        <>
          <View style={{ height: 14 }} />
          {items}
        </>
      ) : null}
    </View>
  );
}

/** Marks a [SettingsCard] child that fills the remaining height (Flutter `Expanded`). */
const _ExpandedChildMarker = Symbol('SettingsCardExpanded');

interface _ExpandedCardChild {
  [_ExpandedChildMarker]: true;
  child: ReactNode;
}

function _isExpandedCardChild(child: unknown): child is _ExpandedCardChild {
  return typeof child === 'object' && child != null && _ExpandedChildMarker in child;
}

function _SettingsCardChild({ children }: { children: ReactNode }) {
  if (_isExpandedCardChild(children)) return <View style={[styles.expanded, { minHeight: 0 }]}>{children.child}</View>;
  return <>{children}</>;
}

export interface SettingsFixedHeaderCardProps {
  title: string;
  body: ReactNode;
  headerChildren?: ReactNode[];
  titleWidget?: ReactNode;
  trailing?: ReactNode;
  spacing?: number;
  padding?: EdgeInsetsValue | null;
}

/**
 * A settings card whose title and controls stay visible while only [body]
 * scrolls. The parent must provide bounded height, as [SettingsScaffold]
 * does for its body.
 */
export function SettingsFixedHeaderCard({
  title,
  body,
  headerChildren = [],
  titleWidget = null,
  trailing = null,
  spacing = 10,
  padding = null,
}: SettingsFixedHeaderCardProps) {
  const inset = _useSettingsBodyInset(24);
  const expandedBody: _ExpandedCardChild = { [_ExpandedChildMarker]: true, child: body };
  return (
    <View style={[styles.expanded, paddingStyle(padding ?? EdgeInsets.fromLTRB(inset, 0, inset, 24))]}>
      <SettingsCard
        title={title}
        titleWidget={titleWidget}
        trailing={trailing}
        spacing={spacing}
        style={styles.expanded}
        // eslint-disable-next-line react/no-children-prop
        children={[...headerChildren, expandedBody as unknown as ReactNode]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  scaffold: { flex: 1, minHeight: 0, backgroundColor: UiColors.surfaceLow },
  fill: { flex: 1, minHeight: 0 },
  expanded: { flex: 1, minWidth: 0 },
  row: { flexDirection: 'row' },
  centerRow: { alignItems: 'center' },
  stretchColumn: { alignItems: 'stretch' },
  body: { flex: 1, minHeight: 0 },
  sideNav: {
    width: _sideNavWidth,
    backgroundColor: UiColors.background,
    borderRightWidth: 1,
    borderRightColor: UiColors.border,
    paddingLeft: 12,
    paddingTop: 18,
    paddingRight: 12,
    paddingBottom: 14,
  },
  card: {
    borderRadius: UiRadii.lg,
    borderWidth: 1,
    // Flutter paints the DecoratedBox border over the 18/16/18/18 padding, so
    // the content inset from the outer edge stays the same here.
    paddingLeft: 17,
    paddingTop: 15,
    paddingRight: 17,
    paddingBottom: 17,
  },
});

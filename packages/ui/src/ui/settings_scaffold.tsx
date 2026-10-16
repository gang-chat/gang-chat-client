import { createContext, useContext, useRef, type CSSProperties, type ReactNode } from 'react';

import { EdgeInsets, paddingStyle, type EdgeInsetsValue } from '../lib/edge_insets';
import { Column, Expanded, Row, SizedBox, Spacer, Text } from '../lib/layout';
import { useElementSize } from '../lib/use_element_size';
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
      <Column crossAxisAlignment="stretch">
        {this.segments.map((segment, index) => (
          <Column key={index} crossAxisAlignment="stretch">
            <_SettingsNavTile segment={segment} selected={segment.value === this.value} onPressed={() => this.onChanged(segment.value)} />
            <SizedBox height={2} />
          </Column>
        ))}
      </Column>
    );
  }

  override buildHorizontal(): ReactNode {
    return (
      <div style={{ height: 34, overflowX: 'auto', overflowY: 'hidden', scrollbarWidth: 'none' }}>
        <Row mainAxisSize="min" gap={6} style={{ height: '100%' }}>
          {this.segments.map((segment, index) => (
            <_SettingsNavPill
              key={index}
              segment={segment}
              selected={segment.value === this.value}
              onPressed={() => this.onChanged(segment.value)}
            />
          ))}
        </Row>
      </div>
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
        <Row gap={gap} crossAxisAlignment="stretch" style={{ height: '100%', padding: `0 ${horizontalPadding}px`, boxSizing: 'border-box' }}>
          {this.segments.map((_, i) => (
            <Expanded key={i}>{tile(i)}</Expanded>
          ))}
        </Row>
      );
    }
    // Only a handful of tiles: build them all so every section stays
    // reachable (and findable) even while scrolled out of view.
    return (
      <div style={{ height: '100%', overflowX: 'auto', overflowY: 'hidden', scrollbarWidth: 'none' }}>
        <Row mainAxisSize="min" gap={gap} style={{ height: '100%', padding: `0 ${horizontalPadding}px` }}>
          {this.segments.map((_, i) => (
            <div key={i}>{tile(i, minTileWidth + 12)}</div>
          ))}
        </Row>
      </div>
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
      <Column mainAxisAlignment="spaceBetween" crossAxisAlignment="start" style={{ flex: '1 1 0' }}>
        <Row>
          <Text style={{ ...UiTypography.dot, fontSize: 13, lineHeight: 1, color: selected ? UiColors.onInverse : UiColors.accent }}>
            {String(index + 1).padStart(2, '0')}
          </Text>
          <Spacer />
          {segment.showBadge ? (
            <>
              <BadgeDot size={7} borderColor={UiColors.transparent} />
              <SizedBox width={6} />
            </>
          ) : null}
          {icon != null ? <Icon icon={icon} size={15} color={muted} /> : null}
        </Row>
        <Column crossAxisAlignment="start" mainAxisSize="min" style={{ alignSelf: 'stretch' }}>
          <Text maxLines={1} style={{ ...UiTypography.body, color: foreground, fontSize: 13, fontWeight: 700, lineHeight: 1.25, maxWidth: '100%' }}>
            {segment.label}
          </Text>
          {summary != null && summary.length > 0 ? (
            <>
              <SizedBox height={2} />
              <Text maxLines={1} style={{ ...UiTypography.mono, color: muted, fontSize: 10, maxWidth: '100%' }}>
                {summary}
              </Text>
            </>
          ) : null}
        </Column>
      </Column>
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
      <Row>
        {icon != null ? (
          <>
            <Icon icon={icon} size={17} color={selected ? UiColors.onInverse : UiColors.textMuted} />
            <SizedBox width={10} />
          </>
        ) : null}
        <Expanded>
          <Text
            maxLines={1}
            style={{
              ...UiTypography.body,
              fontSize: 13.5,
              color: selected ? UiColors.onInverse : UiColors.textSecondary,
              fontWeight: selected ? 700 : 500,
            }}
          >
            {segment.label}
          </Text>
        </Expanded>
        {segment.showBadge ? <BadgeDot size={7} borderColor={UiColors.transparent} /> : null}
      </Row>
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
      <Row mainAxisSize="min">
        {icon != null ? (
          <>
            <Icon icon={icon} size={15} color={selected ? UiColors.onInverse : UiColors.textMuted} />
            <SizedBox width={6} />
          </>
        ) : null}
        <Text
          softWrap={false}
          style={{ ...UiTypography.label, fontSize: 13, color: selected ? UiColors.onInverse : UiColors.textSecondary, fontWeight: 600 }}
        >
          {segment.label}
        </Text>
        {segment.showBadge ? (
          <>
            <SizedBox width={6} />
            <BadgeDot size={7} borderColor={UiColors.transparent} />
          </>
        ) : null}
      </Row>
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
  const ref = useRef<HTMLDivElement | null>(null);
  // LayoutBuilder: the window width stands in until the first measurement.
  const maxWidth = useElementSize(ref)?.width ?? window.innerWidth;
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
    <div ref={ref} style={_scaffoldStyle}>
      {content}
    </div>
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
    <Column crossAxisAlignment="stretch" style={_fillStyle}>
      <Row
        style={{
          paddingLeft: onBack == null ? padding : padding - 8,
          paddingTop: compact ? 14 : 20,
          paddingRight: padding - 4,
          boxSizing: 'border-box',
        }}
      >
        {onBack != null ? (
          <>
            <ButtonIcon
              data-testid="settings-back-button"
              tooltip="返回"
              icon={<Icon icon={Icons.arrow_back_rounded} />}
              onPressed={onBack}
              size={36}
              ghost
            />
            <SizedBox width={6} />
          </>
        ) : null}
        <Column crossAxisAlignment="start" mainAxisSize="min" style={{ flex: '0 1 auto', minWidth: 0 }}>
          <Text maxLines={1} overflow="clip" style={{ ...UiTypography.dot, fontSize: compact ? 18 : 22, lineHeight: 1.05, letterSpacing: 1 }}>
            SETTINGS
          </Text>
          {showTitle ? (
            <>
              <SizedBox height={3} />
              <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.textMuted, maxWidth: '100%' }}>
                {title}
              </Text>
            </>
          ) : null}
        </Column>
        <SizedBox width={16} />
        <Expanded style={{ alignItems: 'flex-end' }}>
          {search == null || compact ? null : <div style={{ maxWidth: 340, width: '100%' }}>{search}</div>}
        </Expanded>
        {headerAction != null ? (
          <>
            <SizedBox width={8} />
            {headerAction}
          </>
        ) : null}
      </Row>
      {search != null && compact ? (
        <>
          <SizedBox height={12} />
          <div style={{ padding: `0 ${padding}px` }}>{search}</div>
        </>
      ) : null}
      <SizedBox height={compact ? 14 : 18} />
      <SizedBox data-testid="settings-side-navigation" height={compact ? 64 : 84}>
        {navigation.buildTiles({ compact, horizontalPadding: padding, maxWidth })}
      </SizedBox>
      <Row
        crossAxisAlignment="end"
        style={{ padding: `${compact ? 16 : 22}px ${padding}px 14px`, boxSizing: 'border-box' }}
      >
        <Expanded>
          <Text maxLines={1} style={compact ? UiTypography.title : UiTypography.headline}>
            {sectionTitle}
          </Text>
        </Expanded>
        <SizedBox width={12} />
        <Text softWrap={false} style={UiTypography.overline}>
          {sectionMeta}
        </Text>
      </Row>
      {pinned != null ? (
        <>
          <div style={{ padding: `0 ${padding}px` }}>{pinned}</div>
          <SizedBox height={_contentTopGap} />
        </>
      ) : null}
      <_SettingsBodyInsets horizontal={padding}>{body}</_SettingsBodyInsets>
    </Column>
  );
}

function _buildTwoPane(props: SettingsScaffoldProps, navigation: SettingsNavigationBase): ReactNode {
  const { showTitle = true, icon, iconColor = null, title, onBack = null, headerAction = null, pinned = null, body } = props;
  const sectionTitle = navigation.selectedLabel ?? title;
  return (
    <Row crossAxisAlignment="stretch" style={_fillStyle}>
      <Column data-testid="settings-side-navigation" crossAxisAlignment="stretch" style={_sideNavStyle}>
        {showTitle ? (
          <>
            <Row style={{ padding: '0 6px' }}>
              <_TitleIcon icon={icon} color={iconColor} />
              <SizedBox width={10} />
              <Expanded>
                <Text maxLines={1} style={UiTypography.title}>
                  {title}
                </Text>
              </Expanded>
            </Row>
            <SizedBox height={18} />
          </>
        ) : null}
        <div style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto' }}>{navigation.buildVertical()}</div>
        {onBack != null ? (
          <>
            <SizedBox height={10} />
            <_BackLink onPressed={onBack} />
          </>
        ) : null}
      </Column>
      <Expanded>
        <SizedBox height={72}>
          <Row
            style={{
              height: '100%',
              padding: `18px ${_horizontalPadding - 4}px 0 ${_horizontalPadding}px`,
              boxSizing: 'border-box',
            }}
          >
            <Expanded>
              <Text maxLines={1} style={UiTypography.headline}>
                {sectionTitle}
              </Text>
            </Expanded>
            {headerAction}
          </Row>
        </SizedBox>
        {pinned != null ? (
          <>
            <div style={{ padding: `0 ${_horizontalPadding}px` }}>{pinned}</div>
            <SizedBox height={_contentTopGap} />
          </>
        ) : null}
        <_SettingsBodyInsets horizontal={_horizontalPadding}>{body}</_SettingsBodyInsets>
      </Expanded>
    </Row>
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
    <Column crossAxisAlignment="stretch" style={_fillStyle}>
      <Row
        style={{
          height: titleBarHeight + 16 + _singlePaneHeaderContentHeight,
          padding: `${titleBarHeight + 16}px ${horizontalPadding}px 0`,
          boxSizing: 'border-box',
          flexShrink: 0,
        }}
      >
        {onBack != null ? (
          <>
            <ButtonIcon tooltip="返回" icon={<Icon icon={Icons.arrow_back} />} onPressed={onBack} size={38} />
            <SizedBox width={16} />
          </>
        ) : null}
        {showTitle ? (
          <>
            <Icon icon={icon} color={iconColor ?? UiColors.accent} size={19} />
            <SizedBox width={8} />
            <Expanded>
              <Text maxLines={1} style={{ ...UiTypography.headline, fontSize: 20 }}>
                {title}
              </Text>
            </Expanded>
          </>
        ) : (
          <Spacer />
        )}
        {headerAction}
      </Row>
      {navigation != null ? (
        <>
          <SizedBox height={_singlePaneContentTopGap} />
          <div style={{ padding: `0 ${horizontalPadding}px` }}>{navigation.buildHorizontal()}</div>
        </>
      ) : null}
      {pinned != null ? (
        <>
          <SizedBox height={_singlePaneContentTopGap} />
          <div style={{ padding: `0 ${horizontalPadding}px` }}>{pinned}</div>
        </>
      ) : null}
      <SizedBox height={_singlePaneContentTopGap} />
      <_SettingsBodyInsets horizontal={horizontalPadding}>{body}</_SettingsBodyInsets>
    </Column>
  );
}

/**
 * Publishes the page's horizontal inset so [SettingsList] and
 * [SettingsFixedHeaderCard] align with the scaffold's header.
 */
const _SettingsBodyInsetsContext = createContext<number | null>(null);

/** Port of `_SettingsBodyInsets` (wrapped in the `Expanded` the scaffold puts around the body). */
function _SettingsBodyInsets({ horizontal, children }: { horizontal: number; children: ReactNode }) {
  return (
    <_SettingsBodyInsetsContext.Provider value={horizontal}>
      <Expanded style={{ alignItems: 'stretch' }}>{children}</Expanded>
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
    <div
      style={{
        width: 32,
        height: 32,
        flexShrink: 0,
        backgroundColor: withAlpha(resolved, 0.14),
        borderRadius: UiRadii.md,
        // Container hands the Icon tight 32×32 constraints, which centre the glyph.
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon icon={icon} color={resolved} size={18} />
    </div>
  );
}

function _BackLink({ onPressed }: { onPressed: () => void }) {
  return (
    <PressableSurface
      data-testid="settings-back-button"
      height={36}
      tooltip="返回"
      onPressed={onPressed}
      backgroundColor={UiColors.transparent}
      hoverBackgroundColor={UiColors.surface}
      borderColor={UiColors.border}
      borderRadius={UiRadii.md - 2}
      padding={EdgeInsets.symmetric({ horizontal: 10 })}
    >
      <Row>
        <Icon icon={Icons.arrow_back_rounded} size={16} color={UiColors.textSecondary} />
        <SizedBox width={8} />
        <Text softWrap={false} style={{ ...UiTypography.label, color: UiColors.textSecondary, fontSize: 13 }}>
          返回
        </Text>
      </Row>
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
   * PORT: Flutter `ScrollPhysics`; `'clamping'` (`ClampingScrollPhysics`)
   * keeps the scroll from chaining past its edges.
   */
  physics?: 'clamping' | null;
}

/** 设置内容列表:在各 item 之间自动插入固定间距,避免手写 `SizedBox`。 */
export function SettingsList({ children, padding = null, spacing = 14, physics = null }: SettingsListProps) {
  const inset = _useSettingsBodyInset(24);
  const resolvedPadding = padding ?? EdgeInsets.fromLTRB(inset, 0, inset, 24);
  return (
    <div
      style={{
        flex: '1 1 0',
        minHeight: 0,
        overflowY: 'auto',
        overscrollBehavior: physics === 'clamping' ? 'contain' : undefined,
        boxSizing: 'border-box',
        ...paddingStyle(resolvedPadding),
      }}
    >
      {children.map((child, index) => (
        <div key={index} style={{ display: 'flex', flexDirection: 'column', marginTop: index > 0 ? spacing : 0 }}>
          {child}
        </div>
      ))}
    </div>
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

  /** DOM addition: outer style (e.g. `flex: 1` when an `Expanded` child must fill the card). */
  style?: CSSProperties;
}

/** 带卡片边框与小标题的分区。内部 children 之间自动插入固定间距。 */
export function SettingsCard({ title, titleWidget = null, children, trailing = null, danger = false, spacing = 12, style }: SettingsCardProps) {
  const items: ReactNode[] = [];
  children.forEach((child, i) => {
    if (i > 0) items.push(<SizedBox key={`gap-${i}`} height={spacing} />);
    items.push(<_Keyed key={`item-${i}`}>{child}</_Keyed>);
  });
  return (
    <Column
      crossAxisAlignment="stretch"
      style={{
        backgroundColor: danger ? argb(0xff1c1418) : UiColors.card,
        borderRadius: UiRadii.lg,
        border: `1px solid ${danger ? UiColors.dangerBorder : UiColors.border}`,
        // Flutter paints the DecoratedBox border over the 18/16/18/18 padding,
        // so the content inset from the outer edge stays the same here.
        padding: '15px 17px 17px',
        boxSizing: 'border-box',
        ...style,
      }}
    >
      <Row>
        <Expanded>
          {titleWidget ?? <Text style={{ ...UiTypography.subtitle, color: danger ? UiColors.danger : UiColors.text }}>{title}</Text>}
        </Expanded>
        {trailing}
      </Row>
      {items.length > 0 ? (
        <>
          <SizedBox height={14} />
          {items}
        </>
      ) : null}
    </Column>
  );
}

function _Keyed({ children }: { children: ReactNode }) {
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
  return (
    <Column
      crossAxisAlignment="stretch"
      style={{ flex: '1 1 0', minHeight: 0, boxSizing: 'border-box', ...paddingStyle(padding ?? EdgeInsets.fromLTRB(inset, 0, inset, 24)) }}
    >
      <SettingsCard title={title} titleWidget={titleWidget} trailing={trailing} spacing={spacing} style={{ flex: '1 1 0', minHeight: 0 }}>
        {[...headerChildren, <Expanded key="body">{body}</Expanded>]}
      </SettingsCard>
    </Column>
  );
}

const _scaffoldStyle: CSSProperties = {
  flex: '1 1 0',
  minWidth: 0,
  minHeight: 0,
  display: 'flex',
  flexDirection: 'column',
  backgroundColor: UiColors.surfaceLow,
};

const _fillStyle: CSSProperties = { flex: '1 1 0', minHeight: 0, minWidth: 0 };

const _sideNavStyle: CSSProperties = {
  width: _sideNavWidth,
  flexShrink: 0,
  backgroundColor: UiColors.background,
  borderRight: `1px solid ${UiColors.border}`,
  padding: '18px 12px 14px',
  boxSizing: 'border-box',
};

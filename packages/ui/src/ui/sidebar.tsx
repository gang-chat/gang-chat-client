import type { CSSProperties, ReactNode } from 'react';

import { EdgeInsets, paddingStyle, type EdgeInsetsValue } from '../lib/edge_insets';
import { Text } from '../lib/layout';
import { Icon, IconTheme, type IconData } from './icon';
import { PressableSurface } from './surface';
import { UiColors, UiRadii, UiTypography } from './tokens';

export class SidebarGroup {
  constructor(opts: { label?: string | null; items: readonly SidebarItem[] }) {
    this.label = opts.label ?? null;
    this.items = opts.items;
  }

  readonly label: string | null;
  readonly items: readonly SidebarItem[];
}

export class SidebarItem {
  constructor(opts: { id: string; label: string; icon: IconData; badge?: string | null; enabled?: boolean; tooltip?: string | null }) {
    this.id = opts.id;
    this.label = opts.label;
    this.icon = opts.icon;
    this.badge = opts.badge ?? null;
    this.enabled = opts.enabled ?? true;
    this.tooltip = opts.tooltip ?? null;
  }

  readonly id: string;
  readonly label: string;
  readonly icon: IconData;
  readonly badge: string | null;
  readonly enabled: boolean;
  readonly tooltip: string | null;
}

export interface SidebarProps {
  groups: readonly SidebarGroup[];
  selectedId?: string | null;
  onItemSelected?: ((id: string) => void) | null;
  header?: ReactNode;
  footer?: ReactNode;
  width?: number;
  height?: number | null;
  padding?: EdgeInsetsValue;
  backgroundColor?: string;
  borderColor?: string;
  style?: CSSProperties;
}

/**
 * PORT: Flutter picked between a shrink-wrapped column (unbounded height) and
 * a scrolling middle section (bounded height) with a LayoutBuilder. The CSS
 * column below does both: the groups scroll once the sidebar is height
 * constrained. The bounded layout's 16px footer gap is used.
 */
export function Sidebar({
  groups,
  selectedId,
  onItemSelected,
  header,
  footer,
  width = 248,
  height,
  padding = EdgeInsets.fromLTRB(12, 18, 12, 14),
  backgroundColor = UiColors.background,
  borderColor = UiColors.border,
  style,
}: SidebarProps) {
  return (
    <div
      style={{
        width,
        height: height ?? undefined,
        flexShrink: 0,
        boxSizing: 'border-box',
        backgroundColor,
        // `Border(right:)` paints inside the box like a DecoratedBox.
        boxShadow: `inset -1px 0 0 ${borderColor}`,
        display: 'flex',
        flexDirection: 'column',
        minHeight: 0,
        ...style,
      }}
    >
      <div
        style={{
          ...paddingStyle(padding),
          boxSizing: 'border-box',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'stretch',
          flex: '1 1 auto',
          minHeight: 0,
        }}
      >
        {header != null ? (
          <>
            {header}
            <div style={{ height: 20, flexShrink: 0 }} />
          </>
        ) : null}
        <div style={{ flex: '1 1 auto', minHeight: 0, overflowY: 'auto' }}>
          <_SidebarGroups groups={groups} selectedId={selectedId ?? null} onItemSelected={onItemSelected ?? null} />
        </div>
        {footer != null ? (
          <>
            <div style={{ height: 16, flexShrink: 0 }} />
            {footer}
          </>
        ) : null}
      </div>
    </div>
  );
}

function _SidebarGroups({
  groups,
  selectedId,
  onItemSelected,
}: {
  groups: readonly SidebarGroup[];
  selectedId: string | null;
  onItemSelected: ((id: string) => void) | null;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}>
      {groups.map((group, groupIndex) => (
        <div key={groupIndex} style={{ display: 'contents' }}>
          {groupIndex > 0 ? <div style={{ height: 14 }} /> : null}
          <_SidebarGroupBlock group={group} selectedId={selectedId} onItemSelected={onItemSelected} />
        </div>
      ))}
    </div>
  );
}

function _SidebarGroupBlock({
  group,
  selectedId,
  onItemSelected,
}: {
  group: SidebarGroup;
  selectedId: string | null;
  onItemSelected: ((id: string) => void) | null;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}>
      {group.label != null ? (
        <div style={{ paddingLeft: 10, paddingBottom: 6 }}>
          <Text maxLines={1} style={UiTypography.overline}>
            {group.label}
          </Text>
        </div>
      ) : null}
      {group.items.map((item, itemIndex) => (
        <div key={item.id} style={{ display: 'contents' }}>
          {itemIndex > 0 ? <div style={{ height: 2 }} /> : null}
          <_SidebarItemButton
            item={item}
            selected={item.id === selectedId}
            onPressed={onItemSelected == null ? null : () => onItemSelected(item.id)}
          />
        </div>
      ))}
    </div>
  );
}

function _SidebarItemButton({ item, selected, onPressed }: { item: SidebarItem; selected: boolean; onPressed: (() => void) | null }) {
  const enabled = item.enabled && onPressed != null;
  const foreground = !item.enabled ? UiColors.textMuted : selected ? UiColors.onInverse : UiColors.textSecondary;
  const accent = selected ? UiColors.onInverse : UiColors.textMuted;
  return (
    <PressableSurface
      height={36}
      onPressed={enabled ? onPressed : null}
      tooltip={item.tooltip}
      enabled={item.enabled}
      selected={selected}
      backgroundColor={UiColors.transparent}
      hoverBackgroundColor={UiColors.surfaceRaised}
      selectedBackgroundColor={UiColors.inverse}
      disabledBackgroundColor={UiColors.transparent}
      borderColor={UiColors.transparent}
      selectedBorderColor={UiColors.transparent}
      disabledBorderColor={UiColors.transparent}
      borderRadius={UiRadii.pill}
      padding={EdgeInsets.symmetric({ horizontal: 12 })}
      style={{ alignSelf: 'stretch' }}
    >
      <IconTheme data={{ color: accent, size: 18 }}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            minWidth: 0,
            color: foreground,
            fontSize: 13,
            fontWeight: selected ? 600 : 500,
            letterSpacing: 0,
          }}
        >
          <Icon icon={item.icon} />
          <div style={{ width: 10, flexShrink: 0 }} />
          <Text maxLines={1} style={{ flex: '1 1 0' }}>
            {item.label}
          </Text>
          {item.badge != null ? (
            <>
              <div style={{ width: 8, flexShrink: 0 }} />
              <_SidebarBadge label={item.badge} selected={selected} />
            </>
          ) : null}
        </div>
      </IconTheme>
    </PressableSurface>
  );
}

function _SidebarBadge({ label, selected }: { label: string; selected: boolean }) {
  const color = selected ? UiColors.onAccent : UiColors.textSecondary;
  return (
    <div
      style={{
        backgroundColor: selected ? UiColors.accentStrong : UiColors.surfaceHover,
        borderRadius: UiRadii.pill,
        padding: '2px 6px',
        flexShrink: 0,
        maxWidth: '100%',
        minWidth: 0,
      }}
    >
      <Text maxLines={1} style={{ color, fontSize: 11, fontWeight: 600, letterSpacing: 0 }}>
        {label}
      </Text>
    </div>
  );
}

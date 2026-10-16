import {
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react';

import { HostPlatform } from '@gang-chat/core/config/host_platform';

import { SystemMouseCursors } from '../lib/mouse_cursors';
import { Icon, type IconData } from './icon';
import { pushRoute, useRoute } from './overlay';
import { UiAndroidLongPressTracker, type WindowPoint } from './platform_gestures';
import { UiColors, UiCurves, UiMotion, UiRadii, UiShadows, UiTypography, boxShadow, kClientFontStack, withAlpha } from './tokens';

export type { WindowPoint } from './platform_gestures';

const _contextMenuScreenPadding = 8;
const _contextMenuWidth = 196;
const _contextMenuMinItemHeight = 32;
const _contextMenuHorizontalPadding = 10;
const _transitionDuration = 110;

export class UiContextMenuSection {
  constructor(readonly items: readonly UiContextMenuItem[]) {}
}

export class UiContextMenuItem {
  constructor(opts: {
    label: string;
    icon?: IconData | null;
    shortcut?: string | null;
    selected?: boolean;
    danger?: boolean;
    onPressed: (() => void) | null;
  }) {
    this.label = opts.label;
    this.icon = opts.icon ?? null;
    this.shortcut = opts.shortcut ?? null;
    this.selected = opts.selected ?? false;
    this.danger = opts.danger ?? false;
    this.onPressed = opts.onPressed;
  }

  readonly label: string;
  readonly icon: IconData | null;
  readonly shortcut: string | null;
  readonly selected: boolean;
  readonly danger: boolean;
  readonly onPressed: (() => void) | null;
}

export interface UiContextMenuTriggerRegionProps {
  onTriggered: (position: WindowPoint) => void;
  children?: ReactNode;
  onTap?: (() => void) | null;
  /** Kept for call-site parity (Flutter `HitTestBehavior`). */
  behavior?: 'translucent' | 'opaque' | 'deferToChild';
  /**
   * Style for the wrapper. By default it is `display: contents` (no box), so
   * the region never changes the child's layout.
   */
  style?: CSSProperties;
}

/**
 * Gives application context-menu targets one platform-consistent trigger.
 *
 * Secondary mouse click remains available on every platform. Android also
 * maps a touch/stylus hold to the same callback. The raw pointer tracker is a
 * fallback for targets containing editable text, whose native long-press
 * can otherwise win.
 *
 * Nested regions behave like Flutter's gesture arena: the innermost region
 * handles the secondary click (it stops the event there).
 */
export function UiContextMenuTriggerRegion({ onTriggered, children, onTap, style }: UiContextMenuTriggerRegionProps) {
  const onTriggeredRef = useRef(onTriggered);
  onTriggeredRef.current = onTriggered;
  const androidLongPressEnabled = HostPlatform.isAndroid;
  const triggeredForCurrentPointer = useRef(false);
  const tracker = useRef<UiAndroidLongPressTracker | null>(null);
  tracker.current ??= new UiAndroidLongPressTracker({
    onLongPress: (position) => {
      if (!HostPlatform.isAndroid) return;
      trigger(position, true);
    },
  });

  const trigger = (position: WindowPoint, androidLongPress: boolean) => {
    if (androidLongPress) {
      if (triggeredForCurrentPointer.current) return;
      triggeredForCurrentPointer.current = true;
      // PORT: Flutter also called `ContextMenuController.removeAny()` here to
      // drop a text-selection toolbar; text menus close on outside pointer
      // downs in this port.
    }
    onTriggeredRef.current(position);
  };

  const onContextMenu = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (event.defaultPrevented) return;
    event.preventDefault();
    event.stopPropagation();
    const pointerType = (event.nativeEvent as PointerEvent).pointerType;
    // A touch hold only opens the menu on Android (desktop keeps it for the
    // secondary click), and Android's hold goes through the tracker.
    if (pointerType === 'touch' || pointerType === 'pen') return;
    trigger({ x: event.clientX, y: event.clientY }, false);
  };

  return (
    <div
      onContextMenu={onContextMenu}
      onClick={
        onTap == null
          ? undefined
          : (event) => {
              if (event.button !== 0) return;
              onTap();
            }
      }
      onPointerDown={(event: ReactPointerEvent<HTMLDivElement>) => {
        triggeredForCurrentPointer.current = false;
        tracker.current!.handlePointerDown(event, { enabled: androidLongPressEnabled });
      }}
      onPointerMove={(event) => tracker.current!.handlePointerMove(event)}
      onPointerUp={(event) => {
        tracker.current!.handlePointerUp(event);
        triggeredForCurrentPointer.current = false;
      }}
      onPointerCancel={(event) => {
        tracker.current!.handlePointerCancel(event);
        triggeredForCurrentPointer.current = false;
      }}
      style={style ?? { display: 'contents' }}
    >
      {children}
    </div>
  );
}

/**
 * Shows the app's context menu at [position] (window coordinates) and
 * resolves when it closes. Port of `showUiContextMenu(context, …)`; the
 * `BuildContext` argument is dropped.
 */
export function showUiContextMenu(opts: { position: WindowPoint; sections: readonly UiContextMenuSection[] }): Promise<void> {
  const visibleSections = opts.sections.filter((section) => section.items.length > 0);
  if (visibleSections.length === 0) return Promise.resolve();
  const route = pushRoute<void>(() => <_UiContextMenuRoute position={opts.position} sections={visibleSections} />, {
    barrierColor: UiColors.transparent,
    barrierDismissible: true,
  });
  return route.result.then(() => undefined);
}

/** Port of `_UiContextMenuLayoutDelegate` inside the screen padding. */
export function _contextMenuPosition(
  position: WindowPoint,
  childSize: { width: number; height: number },
  windowSize: { width: number; height: number },
): WindowPoint {
  const pad = _contextMenuScreenPadding;
  // MediaQuery padding (system insets) is zero on desktop windows.
  const areaWidth = windowSize.width - pad * 2;
  const areaHeight = windowSize.height - pad * 2;
  const anchorX = position.x - pad;
  const anchorY = position.y - pad;
  const maxX = areaWidth - childSize.width;
  const maxY = areaHeight - childSize.height;
  // Dart `num.clamp(0, max)` with max < 0 throws; keep the menu at the origin then.
  const x = Math.max(0, Math.min(anchorX, Math.max(0, maxX)));
  const y = Math.max(0, Math.min(anchorY, Math.max(0, maxY)));
  return { x: pad + x, y: pad + y };
}

function _UiContextMenuRoute({ position, sections }: { position: WindowPoint; sections: readonly UiContextMenuSection[] }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const [placed, setPlaced] = useState<WindowPoint | null>(null);

  useLayoutEffect(() => {
    const node = ref.current;
    if (node == null) return;
    const next = _contextMenuPosition(
      position,
      { width: node.offsetWidth, height: node.offsetHeight },
      { width: window.innerWidth, height: window.innerHeight },
    );
    if (placed == null || placed.x !== next.x || placed.y !== next.y) setPlaced(next);
  });

  useLayoutEffect(() => {
    const node = ref.current;
    if (node == null || placed == null || typeof node.animate !== 'function') return;
    // FadeTransition over the (linear) route animation + ScaleTransition
    // 0.96 → 1 from the top-left with the kit curve.
    node.animate([{ opacity: 0 }, { opacity: 1 }], { duration: _transitionDuration, easing: 'linear' });
    node.animate([{ transform: 'scale(0.96)' }, { transform: 'scale(1)' }], {
      duration: _transitionDuration,
      easing: UiMotion.curve,
    });
    // Only on first placement.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [placed == null]);

  return (
    <div
      ref={ref}
      role="menu"
      onContextMenu={(event) => event.preventDefault()}
      style={{
        position: 'fixed',
        left: placed?.x ?? 0,
        top: placed?.y ?? 0,
        visibility: placed == null ? 'hidden' : 'visible',
        pointerEvents: 'auto',
        transformOrigin: 'top left',
        width: _contextMenuWidth,
        boxSizing: 'border-box',
        backgroundColor: UiColors.surfaceRaised,
        borderRadius: UiRadii.lg - 2,
        border: `1px solid ${UiColors.borderStrong}`,
        boxShadow: boxShadow(UiShadows.popover),
        overflow: 'hidden',
      }}
    >
      <div style={{ padding: 5, display: 'flex', flexDirection: 'column' }}>
        {sections.map((section, sectionIndex) => (
          <div key={sectionIndex} style={{ display: 'contents' }}>
            {sectionIndex > 0 ? <_UiContextMenuDivider /> : null}
            {section.items.map((item, itemIndex) => (
              <_UiContextMenuItemWidget key={itemIndex} item={item} />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

function _UiContextMenuItemWidget({ item }: { item: UiContextMenuItem }) {
  const [hovered, setHovered] = useState(false);
  const route = useRoute<void>();
  const enabled = item.onPressed != null;
  const foreground = !enabled
    ? UiColors.textMuted
    : item.danger
      ? UiColors.danger
      : item.selected
        ? UiColors.accent
        : UiColors.text;
  const shortcut = item.shortcut?.trim() ?? '';
  const background = !enabled
    ? UiColors.transparent
    : hovered
      ? item.danger
        ? withAlpha(UiColors.danger, 0.14)
        : UiColors.surfaceHover
      : item.selected
        ? UiColors.selected
        : UiColors.transparent;
  return (
    <div
      role="menuitem"
      aria-disabled={!enabled}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={(event) => {
        if (event.button !== 0 || !enabled) return;
        const onPressed = item.onPressed!;
        route.close();
        onPressed();
      }}
      style={{
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        minHeight: _contextMenuMinItemHeight,
        boxSizing: 'border-box',
        padding: `6px ${_contextMenuHorizontalPadding}px`,
        backgroundColor: background,
        borderRadius: UiRadii.sm,
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        transition: `background-color 70ms ${UiCurves.easeOutCubic}`,
      }}
    >
      {item.icon != null ? (
        <>
          <Icon
            icon={item.icon}
            size={17}
            color={item.danger || item.selected || !enabled ? foreground : UiColors.textSecondary}
          />
          <div style={{ width: 10, flexShrink: 0 }} />
        </>
      ) : null}
      <span
        style={{
          flex: '1 1 0',
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          color: foreground,
          fontFamily: kClientFontStack,
          fontSize: 13,
          fontWeight: 400,
          lineHeight: 1.2,
          textDecoration: 'none',
        }}
      >
        {item.label}
      </span>
      {shortcut.length > 0 ? (
        <>
          <div style={{ width: 16, flexShrink: 0 }} />
          <span
            style={{
              ...UiTypography.label,
              whiteSpace: 'nowrap',
              color: enabled ? UiColors.textSecondary : withAlpha(UiColors.textMuted, 0.68),
              fontFamily: kClientFontStack,
              fontSize: 12,
              fontWeight: 400,
              lineHeight: 1.2,
              textDecoration: 'none',
            }}
          >
            {shortcut}
          </span>
        </>
      ) : null}
    </div>
  );
}

function _UiContextMenuDivider() {
  return (
    <div style={{ padding: '4px 6px' }}>
      <div style={{ width: '100%', height: 1, backgroundColor: UiColors.borderStrong }} />
    </div>
  );
}

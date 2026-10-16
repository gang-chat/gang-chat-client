import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

import { useTapOutside } from '../lib/tap_region';
import { insertOverlay, type OverlayEntryHandle } from './overlay';
import { _contentRect } from './platform_gestures';
import { UiColors, UiRadii, UiShadows, boxShadow } from './tokens';

export interface AnchoredPanelProps {
  children?: ReactNode;
  width?: number;
  backgroundColor?: string;
  borderColor?: string;
  style?: CSSProperties;
}

export function AnchoredPanel({
  children,
  width = 320,
  backgroundColor = UiColors.surfaceRaised,
  borderColor = UiColors.borderStrong,
  style,
}: AnchoredPanelProps) {
  return (
    <div
      style={{
        width,
        boxSizing: 'border-box',
        backgroundColor,
        borderRadius: UiRadii.lg,
        border: `1px solid ${borderColor}`,
        boxShadow: boxShadow(UiShadows.popover),
        ...style,
      }}
    >
      {children}
    </div>
  );
}

export interface AnchoredPanelAnchorProps {
  anchor: (open: boolean, toggle: () => void) => ReactNode;
  panel?: ReactNode;
  /**
   * Builds the panel with a callback that closes it, for panels whose
   * actions should dismiss them. Takes precedence over [panel].
   */
  panelBuilder?: ((close: () => void) => ReactNode) | null;
  width?: number;
  gap?: number;
  backgroundColor?: string;
  borderColor?: string;
}

interface Rect {
  left: number;
  top: number;
  right: number;
  bottom: number;
  width: number;
  height: number;
}

/**
 * Anchors a floating [AnchoredPanel] to a trigger. The panel opens centred
 * below the anchor (or above when it only fits there), stays inside the
 * window, and closes on a pointer down outside both the anchor and the panel.
 *
 * The anchor wrapper is `display: contents`, so it does not affect layout.
 */
export function AnchoredPanelAnchor(props: AnchoredPanelAnchorProps) {
  const anchorRef = useRef<HTMLDivElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const entry = useRef<OverlayEntryHandle | null>(null);
  const [open, setOpen] = useState(false);
  const propsRef = useRef(props);
  propsRef.current = props;

  const close = () => {
    if (entry.current == null) return;
    entry.current.remove();
    entry.current = null;
    setOpen(false);
  };

  const openPanel = () => {
    if (entry.current != null) return;
    entry.current = insertOverlay(() => (
      <_AnchoredPanelLayer
        anchorRef={anchorRef}
        panelRef={panelRef}
        width={propsRef.current.width ?? 320}
        gap={propsRef.current.gap ?? 8}
        backgroundColor={propsRef.current.backgroundColor ?? UiColors.surfaceRaised}
        borderColor={propsRef.current.borderColor ?? UiColors.borderStrong}
      >
        {propsRef.current.panelBuilder?.(close) ?? propsRef.current.panel ?? null}
      </_AnchoredPanelLayer>
    ));
    setOpen(true);
  };

  const toggle = () => (entry.current != null ? close() : openPanel());

  // The overlay content follows this widget's rebuilds (OverlayPortal).
  useLayoutEffect(() => {
    entry.current?.markNeedsBuild();
  });
  useEffect(() => () => entry.current?.remove(), []);

  useTapOutside([anchorRef, panelRef], () => close(), open);

  return (
    <div ref={anchorRef} style={{ display: 'contents' }}>
      {props.anchor(open, toggle)}
    </div>
  );
}

function _AnchoredPanelLayer({
  anchorRef,
  panelRef,
  width,
  gap,
  backgroundColor,
  borderColor,
  children,
}: {
  anchorRef: { current: HTMLDivElement | null };
  panelRef: { current: HTMLDivElement | null };
  width: number;
  gap: number;
  backgroundColor: string;
  borderColor: string;
  children?: ReactNode;
}) {
  const [position, setPosition] = useState<{ left: number; top: number } | null>(null);
  const [, setTick] = useState(0);

  useEffect(() => {
    const onResize = () => setTick((tick) => tick + 1);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useLayoutEffect(() => {
    const panel = panelRef.current;
    const anchor = _contentRect(anchorRef.current);
    if (panel == null || anchor == null) return;
    const next = _anchoredPanelPosition({
      anchorRect: anchor,
      overlaySize: { width: window.innerWidth, height: window.innerHeight },
      panelSize: { width, height: panel.offsetHeight },
      gap,
    });
    if (position == null || Math.abs(position.left - next.left) > 0.5 || Math.abs(position.top - next.top) > 0.5) {
      setPosition(next);
    }
  });

  if (anchorRef.current == null) return null;
  return (
    <div
      ref={(node) => {
        panelRef.current = node;
      }}
      style={{
        position: 'fixed',
        left: position?.left ?? 0,
        top: position?.top ?? 0,
        width,
        visibility: position == null ? 'hidden' : 'visible',
        pointerEvents: 'auto',
      }}
    >
      <AnchoredPanel width={width} backgroundColor={backgroundColor} borderColor={borderColor}>
        {children}
      </AnchoredPanel>
    </div>
  );
}

/** Port of `_AnchoredPanelLayoutDelegate.getPositionForChild`. */
export function _anchoredPanelPosition(opts: {
  anchorRect: Rect;
  overlaySize: { width: number; height: number };
  panelSize: { width: number; height: number };
  gap: number;
}): { left: number; top: number } {
  const { anchorRect, overlaySize: size, panelSize: childSize, gap } = opts;
  const placeBelow = _shouldPlaceBelow({ anchorRect, overlaySize: size, panelSize: childSize, gap });
  const maxLeft = Math.max(0, size.width - childSize.width);
  const centerX = anchorRect.left + anchorRect.width / 2;
  const left = Math.min(Math.max(centerX - childSize.width / 2, 0), maxLeft);
  const maxTop = Math.max(0, size.height - childSize.height);
  const top = placeBelow
    ? Math.min(Math.max(anchorRect.bottom + gap, 0), maxTop)
    : Math.min(Math.max(anchorRect.top - gap - childSize.height, 0), maxTop);
  return { left, top };
}

function _shouldPlaceBelow(opts: {
  anchorRect: Rect;
  overlaySize: { width: number; height: number };
  panelSize: { width: number; height: number };
  gap: number;
}): boolean {
  const { anchorRect, overlaySize, panelSize, gap } = opts;
  const aboveTop = anchorRect.top - gap - panelSize.height;
  if (aboveTop >= 0) return false;

  const belowBottom = anchorRect.bottom + gap + panelSize.height;
  if (belowBottom <= overlaySize.height) return true;

  const spaceAbove = anchorRect.top;
  const spaceBelow = overlaySize.height - anchorRect.bottom;
  return spaceBelow > spaceAbove;
}

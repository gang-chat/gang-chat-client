import { useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react';

import { marginStyle, paddingStyle, type EdgeInsetsValue } from '../lib/edge_insets';
import { SystemMouseCursors, kTouchSlop, type MouseCursor } from '../lib/mouse_cursors';
import { UiTooltip } from './tooltip';
import { UiColors, UiMotion, UiRadii, UiStates, colorAlpha, lerpColor } from './tokens';

export enum SurfaceCorner {
  topLeft = 'topLeft',
  topRight = 'topRight',
  bottomLeft = 'bottomLeft',
  bottomRight = 'bottomRight',
}

export interface SurfaceSize {
  width: number;
  height: number;
}

export interface PressableSurfaceProps {
  children?: ReactNode;
  /** Height of the surface; `Infinity` fills the parent (Dart `double.infinity`). */
  height: number;
  /** Explicit width; `null` / non-finite lets the parent decide, like Dart. */
  width?: number | null;
  padding?: EdgeInsetsValue;
  margin?: EdgeInsetsValue;
  onPressed?: (() => void) | null;
  tooltip?: string | null;
  /** CSS cursor name, see `SystemMouseCursors`. */
  mouseCursor?: MouseCursor | null;
  enabled?: boolean;
  interactive?: boolean;
  loading?: boolean;
  pressRequiresHover?: boolean;
  /**
   * Cancels [onPressed] when a touch-like pointer moves beyond Flutter's
   * normal touch slop. Opt in for surfaces nested in a scrollable whose raw
   * pointer handling would otherwise bypass the gesture arena.
   */
  cancelTouchPressOnDrag?: boolean;
  selected?: boolean;
  backgroundColor?: string;
  /** Background while hovered. Defaults to a light tint of the resting color. */
  hoverBackgroundColor?: string | null;
  selectedBackgroundColor?: string;
  /** Background while pressed. Defaults to a darkened resting color. */
  pressedBackgroundColor?: string | null;
  disabledBackgroundColor?: string;
  borderColor?: string;
  selectedBorderColor?: string;
  disabledBorderColor?: string;
  borderRadius?: number;
  hoverEffect?: boolean;
  pressEffect?: boolean;
  cornerCut?: SurfaceSize;
  cutCorner?: SurfaceCorner | null;

  // DOM additions (no Dart equivalent).
  /** Outer box style (flex / alignSelf / position): Flutter got these from parent constraints. */
  style?: CSSProperties;
  /** Style of the decorated layer that holds [children]. */
  contentStyle?: CSSProperties;
  /** Reports hover changes (Flutter callers wrapped the surface in a MouseRegion). */
  onHoverChanged?: (hovered: boolean) => void;
  'aria-label'?: string;
  'data-testid'?: string;
}

/**
 * The kit's single pressable primitive: a flat rounded surface that tints on
 * hover and press. Buttons, rows, cards and segmented items all build on it so
 * interaction feedback is identical everywhere.
 *
 * The decorated layer is a flex column that fills the fixed [height] with its
 * content centred vertically: Flutter hands the child a tight height, so a
 * `Row` / `Center` child ends up centred; a child with `flex: 1` fills it.
 */
export function PressableSurface(props: PressableSurfaceProps) {
  const {
    children,
    height,
    width,
    padding = 0,
    margin = 0,
    onPressed,
    tooltip,
    mouseCursor,
    enabled = true,
    interactive: interactiveProp = false,
    loading = false,
    pressRequiresHover = false,
    cancelTouchPressOnDrag = false,
    selected = false,
    backgroundColor = UiColors.surface,
    hoverBackgroundColor,
    selectedBackgroundColor = UiColors.selected,
    pressedBackgroundColor,
    disabledBackgroundColor = UiColors.disabledSurface,
    borderColor = UiColors.border,
    selectedBorderColor = UiColors.selectedBorder,
    disabledBorderColor = UiColors.disabledBorder,
    borderRadius = UiRadii.md,
    hoverEffect = true,
    pressEffect = true,
    cornerCut,
    cutCorner,
    style,
    contentStyle,
    onHoverChanged,
  } = props;

  const [hovered, setHovered] = useState(false);
  const [pressedState, setPressedState] = useState(false);
  const tracking = useRef<{
    pointerId: number;
    originX: number;
    originY: number;
    trackingTouchDrag: boolean;
    touchDragCancelled: boolean;
  } | null>(null);
  const hoveredRef = useRef(false);

  const interactive = enabled && (interactiveProp || onPressed != null);
  const latest = useRef({ interactive, pressEffect, pressRequiresHover, enabled, loading, onPressed });
  latest.current = { interactive, pressEffect, pressRequiresHover, enabled, loading, onPressed };

  // didUpdateWidget: a surface that stops being interactive drops its tints.
  useEffect(() => {
    if (!interactive && (hoveredRef.current || pressedState)) {
      hoveredRef.current = false;
      setHovered(false);
      setPressedState(false);
    }
  }, [interactive, pressedState]);

  const setHover = (next: boolean) => {
    if (hoveredRef.current === next) return;
    hoveredRef.current = next;
    setHovered(next);
    if (!next && tracking.current == null) setPressedState(false);
    onHoverChanged?.(next);
  };

  const setPressed = (next: boolean) => {
    const l = latest.current;
    if (!l.interactive || !l.pressEffect) return;
    if (next && l.pressRequiresHover && !hoveredRef.current) return;
    setPressedState(next);
  };

  const handleTap = () => {
    const l = latest.current;
    if (!l.enabled || l.loading) return;
    l.onPressed?.();
  };

  const hitTarget = useRef<HTMLDivElement | null>(null);
  const detachRef = useRef<(() => void) | null>(null);
  useEffect(() => () => detachRef.current?.(), []);

  const clearPointerTracking = () => {
    tracking.current = null;
    detachRef.current?.();
    detachRef.current = null;
  };

  // Like the Dart Listener, the press is followed with raw pointer events on
  // the window (not pointer capture), so nested surfaces each see the whole
  // gesture exactly as they did in Flutter's hit-test path.
  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    // PORT: only the primary button presses (the Flutter Listener also reacted
    // to secondary buttons, which the DOM routes to context menus instead).
    if (event.button !== 0) return;
    const l = latest.current;
    if (!l.interactive || l.loading || tracking.current != null) return;
    const pointerId = event.pointerId;
    tracking.current = {
      pointerId,
      originX: event.clientX,
      originY: event.clientY,
      trackingTouchDrag: cancelTouchPressOnDrag && event.pointerType !== 'mouse',
      touchDragCancelled: false,
    };
    const onMove = (e: PointerEvent) => {
      const t = tracking.current;
      if (t == null || t.pointerId !== e.pointerId) return;
      if (t.trackingTouchDrag && !t.touchDragCancelled) {
        const dx = e.clientX - t.originX;
        const dy = e.clientY - t.originY;
        if (dx * dx + dy * dy > kTouchSlop * kTouchSlop) {
          t.touchDragCancelled = true;
          setPressed(false);
          return;
        }
      }
      if (t.touchDragCancelled) return;
      // Keep the pressed effect for the whole hold, even if the pointer wanders
      // outside the hit target. Whether the tap actually fires is decided at
      // pointer-up from the release position.
      setPressed(true);
    };
    const onUp = (e: PointerEvent) => {
      const t = tracking.current;
      if (t == null || t.pointerId !== e.pointerId) return;
      const rect = hitTarget.current?.getBoundingClientRect();
      const inside =
        rect != null && e.clientX >= rect.left && e.clientX <= rect.right && e.clientY >= rect.top && e.clientY <= rect.bottom;
      const shouldPress = !t.touchDragCancelled && inside;
      clearPointerTracking();
      setPressed(false);
      if (shouldPress) handleTap();
    };
    const onCancel = (e: PointerEvent) => {
      const t = tracking.current;
      if (t == null || t.pointerId !== e.pointerId) return;
      clearPointerTracking();
      setPressed(false);
    };
    window.addEventListener('pointermove', onMove, true);
    window.addEventListener('pointerup', onUp, true);
    window.addEventListener('pointercancel', onCancel, true);
    detachRef.current = () => {
      window.removeEventListener('pointermove', onMove, true);
      window.removeEventListener('pointerup', onUp, true);
      window.removeEventListener('pointercancel', onCancel, true);
    };
    setPressed(true);
  };

  const radius = borderRadius <= 0 ? 0 : borderRadius;
  const resting = enabled ? (selected ? selectedBackgroundColor : backgroundColor) : disabledBackgroundColor;
  const restingBorder = enabled ? (selected ? selectedBorderColor : borderColor) : disabledBorderColor;
  const pressed = loading || (pressedState && pressEffect);
  const isHovered = hoverEffect && hovered && interactive;
  let background: string;
  if (pressed) {
    background = pressedBackgroundColor == null ? UiStates.pressed(resting) : lerpColor(resting, pressedBackgroundColor, 0.4);
  } else if (isHovered) {
    background = selected ? UiStates.hover(resting) : (hoverBackgroundColor ?? UiStates.hover(resting));
  } else {
    background = resting;
  }
  const border = isHovered && visibleBorder(restingBorder) ? lerpColor(restingBorder, UiColors.white, 0.07) : restingBorder;
  const explicitWidth = width != null && Number.isFinite(width) ? width : null;
  const cursor = mouseCursor ?? (interactive ? SystemMouseCursors.click : SystemMouseCursors.basic);
  const cut = cornerCut != null && cornerCut.width > 0 && cornerCut.height > 0 && cutCorner != null;

  let result: ReactNode = (
    <div
      role={onPressed != null ? 'button' : undefined}
      aria-disabled={onPressed != null ? !interactive : undefined}
      aria-pressed={selected ? true : undefined}
      aria-label={props['aria-label'] ?? undefined}
      data-testid={props['data-testid']}
      onPointerEnter={(event) => {
        if (event.pointerType === 'mouse') setHover(true);
      }}
      onPointerLeave={(event) => {
        if (event.pointerType === 'mouse') setHover(false);
      }}
      ref={hitTarget}
      onPointerDown={onPointerDown}
      style={{
        ...marginStyle(margin),
        boxSizing: 'content-box',
        display: 'flex',
        flexDirection: 'column',
        flexShrink: 0,
        height: Number.isFinite(height) ? height : '100%',
        width: explicitWidth ?? undefined,
        cursor,
        ...style,
      }}
    >
      <div
        style={{
          flex: '1 1 0',
          minHeight: 0,
          minWidth: 0,
          boxSizing: 'border-box',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          backgroundColor: background,
          borderRadius: radius,
          border: visibleBorder(border) ? `1px solid ${border}` : 'none',
          overflow: 'hidden',
          ...paddingStyle(padding),
          clipPath: cut ? surfaceClipPath(cornerCut!, cutCorner!) : undefined,
          transition: `background-color ${UiMotion.fast}ms ${UiMotion.curve}, border-color ${UiMotion.fast}ms ${UiMotion.curve}`,
          ...contentStyle,
        }}
      >
        {children}
      </div>
    </div>
  );

  if (tooltip != null && tooltip.length > 0) {
    result = <UiTooltip message={tooltip}>{result}</UiTooltip>;
  }
  return <>{result}</>;
}

function visibleBorder(color: string): boolean {
  return colorAlpha(color) > 0;
}

/**
 * CSS polygon equivalent of the Dart `_surfacePath`: a rectangular notch cut
 * out of one corner (the clipped shape has square corners, like the Dart path).
 */
function surfaceClipPath(cornerCut: SurfaceSize, cutCorner: SurfaceCorner): string {
  const cw = `${cornerCut.width}px`;
  const ch = `${cornerCut.height}px`;
  switch (cutCorner) {
    case SurfaceCorner.topLeft:
      return `polygon(${cw} 0, 100% 0, 100% 100%, 0 100%, 0 ${ch}, ${cw} ${ch})`;
    case SurfaceCorner.topRight:
      return `polygon(0 0, calc(100% - ${cw}) 0, calc(100% - ${cw}) ${ch}, 100% ${ch}, 100% 100%, 0 100%)`;
    case SurfaceCorner.bottomRight:
      return `polygon(0 0, 100% 0, 100% calc(100% - ${ch}), calc(100% - ${cw}) calc(100% - ${ch}), calc(100% - ${cw}) 100%, 0 100%)`;
    case SurfaceCorner.bottomLeft:
      return `polygon(0 0, 100% 0, 100% 100%, ${cw} 100%, ${cw} calc(100% - ${ch}), 0 calc(100% - ${ch}))`;
  }
}

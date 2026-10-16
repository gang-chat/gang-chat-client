import { useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from 'react';

import { SystemMouseCursors } from '../lib/mouse_cursors';
import { useElementSize } from '../lib/use_element_size';
import { UiColors, UiMotion, UiRadii, UiTypography, argb, boxShadow, withAlpha, type BoxShadowSpec } from './tokens';

// A flat slider: a thin rounded rail, an accent fill, and a round white knob
// that grows slightly while hovered or dragged. Supports both orientations;
// vertical fills from the bottom (min) up to the top (max).
const _trackThickness = 4;
const _handleLong = 14;
const _handleShort = 14;
const _hoverLabelWidth = 36;
const _hoverLabelHeight = 18;
const _hoverLabelGap = 4;

export enum Axis {
  horizontal = 'horizontal',
  vertical = 'vertical',
}

export interface UiSliderProps {
  /** Current value, clamped into [min]..[max] for display. */
  value: number;
  /** Fired continuously while dragging or on a track tap. Null disables. */
  onChanged: ((value: number) => void) | null;
  /** Fired once when an interaction begins (pointer down on the track). */
  onChangeStart?: ((value: number) => void) | null;
  /** Fired once when an interaction ends (pointer up or cancel). */
  onChangeEnd?: ((value: number) => void) | null;
  min?: number;
  max?: number;
  enabled?: boolean;
  /** Layout orientation. Vertical sliders run low→high bottom→top. */
  axis?: Axis;
  /**
   * Optional value text shown above the current thumb while a desktop pointer
   * hovers the slider. Touch behavior remains unchanged.
   */
  hoverLabel?: string | null;
  /** Outer box style (DOM addition). */
  style?: CSSProperties;
  'aria-label'?: string;
}

export function UiSlider({
  value,
  onChanged,
  onChangeStart,
  onChangeEnd,
  min = 0,
  max = 1,
  enabled = true,
  axis = Axis.horizontal,
  hoverLabel,
  style,
  'aria-label': ariaLabel,
}: UiSliderProps) {
  const trackRef = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(trackRef);
  const [pointer, setPointer] = useState<number | null>(null);
  const [hovered, setHovered] = useState(false);
  const latestValue = useRef(value);
  latestValue.current = value;

  const interactive = enabled && onChanged != null;
  const vertical = axis === Axis.vertical;
  const span = max - min <= 0 ? 1 : max - min;
  const fraction = Math.min(Math.max((value - min) / span, 0), 1);
  // didUpdateWidget: a disabled slider drops its drag.
  const activePointer = interactive ? pointer : null;

  const emitFromPosition = (clientX: number, clientY: number) => {
    const node = trackRef.current;
    if (node == null) return;
    const rect = node.getBoundingClientRect();
    let next: number;
    if (vertical) {
      const travel = rect.height - _handleLong;
      if (travel <= 0) return;
      // Bottom is min, so invert the y reading.
      next = Math.min(Math.max(1 - (clientY - rect.top - _handleLong / 2) / travel, 0), 1);
    } else {
      const travel = rect.width - _handleLong;
      if (travel <= 0) return;
      next = Math.min(Math.max((clientX - rect.left - _handleLong / 2) / travel, 0), 1);
    }
    onChanged?.(min + next * span);
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!interactive || activePointer != null || event.button !== 0) return;
    setPointer(event.pointerId);
    try {
      event.currentTarget.setPointerCapture(event.pointerId);
    } catch {
      // Best effort.
    }
    onChangeStart?.(latestValue.current);
    emitFromPosition(event.clientX, event.clientY);
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.pointerId !== activePointer) return;
    emitFromPosition(event.clientX, event.clientY);
  };

  const onPointerEnd = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.pointerId !== activePointer) return;
    setPointer(null);
    onChangeEnd?.(latestValue.current);
  };

  const grooveColor = interactive ? argb(0xff2a2a2f) : UiColors.disabledSurface;
  const fillColor = interactive ? UiColors.accentStrong : UiColors.textMuted;
  const thumbColor = interactive ? UiColors.inverse : UiColors.textMuted;
  const active = interactive && (hovered || activePointer != null);

  // Center the track along the cross axis, leaving room for the handle that
  // overhangs it.
  const crossInset = (_handleShort - _trackThickness) / 2;

  const label = hoverLabel?.trim();
  const showHoverLabel = !vertical && label != null && label.length > 0 && (hovered || activePointer != null);

  const extent = (vertical ? size?.height : size?.width) ?? 0;
  const travel = Math.max(extent - _handleLong, 0);
  const handleStart = travel * fraction;
  const fillExtent = handleStart + _handleLong / 2;
  const hoverLabelLeft = Math.min(
    Math.max(handleStart + _handleLong / 2 - _hoverLabelWidth / 2, 0),
    Math.max(extent - _hoverLabelWidth, 0),
  );

  const knobSize = active ? _handleShort : _handleShort - 2;
  const knobShadows: BoxShadowSpec[] = [{ color: argb(0x66000000), blurRadius: 4, offsetY: 1 }];
  if (active) knobShadows.push({ color: withAlpha(UiColors.accentStrong, 0.35), blurRadius: 0, spreadRadius: 4 });

  return (
    <div
      ref={trackRef}
      role="slider"
      aria-label={ariaLabel}
      aria-valuemin={min}
      aria-valuemax={max}
      aria-valuenow={value}
      aria-orientation={vertical ? 'vertical' : 'horizontal'}
      aria-disabled={!interactive}
      onPointerEnter={(event) => {
        if (event.pointerType === 'mouse') setHovered(true);
      }}
      onPointerLeave={(event) => {
        if (event.pointerType === 'mouse') setHovered(false);
      }}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerEnd}
      onPointerCancel={onPointerEnd}
      style={{
        position: 'relative',
        flexShrink: 0,
        touchAction: 'none',
        cursor: interactive ? SystemMouseCursors.click : SystemMouseCursors.basic,
        ...(vertical ? { width: _handleShort, height: '100%' } : { height: _handleShort, width: '100%' }),
        ...style,
      }}
    >
      {/* The recessed track, spanning the full main-axis extent. */}
      <div
        style={{
          position: 'absolute',
          left: vertical ? crossInset : 0,
          right: vertical ? crossInset : 0,
          top: vertical ? 0 : crossInset,
          bottom: vertical ? 0 : crossInset,
          backgroundColor: grooveColor,
          borderRadius: _trackThickness / 2,
        }}
      />
      {/* The accent fill from the low end up to the handle. */}
      <div
        style={
          vertical
            ? {
                position: 'absolute',
                left: crossInset,
                right: crossInset,
                bottom: 0,
                height: fillExtent,
                backgroundColor: fillColor,
                borderRadius: _trackThickness / 2,
              }
            : {
                position: 'absolute',
                left: 0,
                top: crossInset,
                width: fillExtent,
                height: _trackThickness,
                backgroundColor: fillColor,
                borderRadius: _trackThickness / 2,
              }
        }
      />
      {/* A round knob straddling the track. */}
      <div
        style={{
          position: 'absolute',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          ...(vertical
            ? { left: 0, right: 0, bottom: handleStart, height: _handleLong }
            : { left: handleStart, top: 0, width: _handleLong, height: _handleShort }),
        }}
      >
        <div
          style={{
            width: knobSize,
            height: knobSize,
            flexShrink: 0,
            borderRadius: '50%',
            backgroundColor: thumbColor,
            boxShadow: boxShadow(knobShadows),
            transition: `width ${UiMotion.fast}ms ${UiMotion.curve}, height ${UiMotion.fast}ms ${UiMotion.curve}, box-shadow ${UiMotion.fast}ms ${UiMotion.curve}, background-color ${UiMotion.fast}ms ${UiMotion.curve}`,
          }}
        />
      </div>
      {showHoverLabel ? (
        <div
          data-testid="ui-slider-hover-label"
          style={{
            position: 'absolute',
            left: hoverLabelLeft,
            bottom: _handleShort + _hoverLabelGap,
            width: _hoverLabelWidth,
            height: _hoverLabelHeight,
            boxSizing: 'border-box',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: UiColors.surfaceHover,
            borderRadius: UiRadii.sm,
            border: `1px solid ${UiColors.borderStrong}`,
            pointerEvents: 'none',
          }}
        >
          <span style={{ ...UiTypography.label, color: UiColors.text, fontSize: 10, fontWeight: 600, whiteSpace: 'nowrap' }}>
            {label}
          </span>
        </div>
      ) : null}
    </div>
  );
}

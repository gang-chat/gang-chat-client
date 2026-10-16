import type { ReactNode } from 'react';

import { SystemMouseCursors } from '../lib/mouse_cursors';
import { UiTooltip } from './tooltip';
import { UiColors, UiCurves, UiRadii, UiShadows, argb, boxShadow, withAlpha } from './tokens';

const _switchWidth = 42;
const _switchHeight = 24;
const _switchThumbSize = 18;
const _switchHorizontalInset = 3;
const _switchDuration = 160;

export interface UiSwitchProps {
  value: boolean;
  onChanged: ((value: boolean) => void) | null;
  enabled?: boolean;
  tooltip?: string | null;
  'aria-label'?: string;
}

export function UiSwitch({ value, onChanged, enabled = true, tooltip, 'aria-label': ariaLabel }: UiSwitchProps) {
  const interactive = enabled && onChanged != null;
  const active = value;
  const trackColor = !interactive
    ? active
      ? withAlpha(UiColors.accentStrong, 0.35)
      : UiColors.disabledSurface
    : active
      ? UiColors.accentStrong
      : argb(0xff2a2a2f);
  const trackBorder = active || !interactive ? UiColors.transparent : UiColors.borderStrong;
  const thumbColor = !interactive ? UiColors.textMuted : active ? UiColors.onAccent : argb(0xff8e8e95);
  const thumbTop = (_switchHeight - _switchThumbSize) / 2;
  const transition = (property: string) => `${property} ${_switchDuration}ms ${UiCurves.easeOutCubic}`;

  let result: ReactNode = (
    <div
      role="switch"
      aria-checked={value}
      aria-disabled={!interactive}
      aria-label={ariaLabel ?? tooltip ?? undefined}
      // The Dart Listener toggles on a primary press released inside, which
      // is a DOM click.
      onClick={(event) => {
        if (event.button !== 0 || !interactive) return;
        onChanged?.(!value);
      }}
      style={{
        position: 'relative',
        width: _switchWidth,
        height: _switchHeight,
        flexShrink: 0,
        cursor: interactive ? SystemMouseCursors.click : SystemMouseCursors.basic,
      }}
    >
      <div
        style={{
          position: 'absolute',
          inset: 0,
          boxSizing: 'border-box',
          backgroundColor: trackColor,
          borderRadius: UiRadii.pill,
          border: `1px solid ${trackBorder}`,
          transition: `${transition('background-color')}, ${transition('border-color')}`,
        }}
      />
      <div
        style={{
          position: 'absolute',
          left: active ? _switchWidth - _switchThumbSize - _switchHorizontalInset : _switchHorizontalInset,
          top: thumbTop,
          width: _switchThumbSize,
          height: _switchThumbSize,
          borderRadius: '50%',
          backgroundColor: thumbColor,
          boxShadow: interactive ? boxShadow(UiShadows.raised) : 'none',
          transition: `${transition('left')}, ${transition('background-color')}, ${transition('box-shadow')}`,
        }}
      />
    </div>
  );

  if (tooltip != null && tooltip.length > 0) {
    result = <UiTooltip message={tooltip}>{result}</UiTooltip>;
  }
  return <>{result}</>;
}

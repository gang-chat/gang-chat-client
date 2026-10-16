import type { ReactNode } from 'react';

import { SystemMouseCursors } from '../lib/mouse_cursors';
import { Icon, Icons } from './icon';
import { PressableSurface } from './surface';
import { UiTooltip } from './tooltip';
import { UiColors, UiRadii, withAlpha } from './tokens';

const _checkboxSize = 18;

export interface UiCheckboxProps {
  value: boolean;
  onChanged: ((value: boolean) => void) | null;
  enabled?: boolean;
  tooltip?: string | null;
  semanticLabel?: string | null;
}

export function UiCheckbox({ value, onChanged, enabled = true, tooltip, semanticLabel }: UiCheckboxProps) {
  const interactive = enabled && onChanged != null;
  const toggle = () => {
    if (!interactive) return;
    onChanged?.(!value);
  };
  const active = value;
  const background = !active ? UiColors.fieldFill : interactive ? UiColors.accentStrong : withAlpha(UiColors.accentStrong, 0.35);
  const border = active ? UiColors.transparent : interactive ? UiColors.borderStrong : UiColors.disabledBorder;

  let result: ReactNode = (
    <div
      role="checkbox"
      aria-checked={value}
      aria-disabled={!interactive}
      aria-label={semanticLabel ?? tooltip ?? undefined}
      style={{ display: 'contents' }}
    >
      <PressableSurface
        width={_checkboxSize}
        height={_checkboxSize}
        enabled={interactive}
        interactive={interactive}
        onPressed={toggle}
        borderRadius={UiRadii.xs + 1}
        padding={0}
        mouseCursor={interactive ? SystemMouseCursors.click : SystemMouseCursors.basic}
        backgroundColor={background}
        selectedBackgroundColor={background}
        disabledBackgroundColor={background}
        borderColor={border}
        selectedBorderColor={border}
        disabledBorderColor={border}
        contentStyle={{ alignItems: 'center' }}
      >
        <Icon
          icon={Icons.check_rounded}
          size={14}
          color={!active ? UiColors.transparent : interactive ? UiColors.onAccent : UiColors.textSecondary}
        />
      </PressableSurface>
    </div>
  );

  if (tooltip != null && tooltip.length > 0) {
    result = <UiTooltip message={tooltip}>{result}</UiTooltip>;
  }
  return <>{result}</>;
}

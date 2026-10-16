import { isValidElement, type CSSProperties, type ReactNode } from 'react';

import { EdgeInsets, horizontalInsets, type EdgeInsetsValue } from '../lib/edge_insets';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { measureTextWidth } from '../lib/text_measure';
import { Icon, IconTheme, useIconTheme, type IconData } from './icon';
import { PressableSurface } from './surface';
import { UiTooltip } from './tooltip';
import { UiColors, UiRadii, UiStates, argb, kClientFontStack, lerpColor } from './tokens';

export enum ButtonTone {
  neutral = 'neutral',
  primary = 'primary',
  danger = 'danger',
}

/** An icon prop: a ready element (`<Icon icon={…} />`) or bare `IconData`. */
export type ButtonIconValue = ReactNode | IconData;

function isIconData(value: unknown): value is IconData {
  return (
    value != null &&
    typeof value === 'object' &&
    !isValidElement(value) &&
    typeof (value as IconData).codePoint === 'number' &&
    typeof (value as IconData).fontFamily === 'string'
  );
}

function renderIcon(value: ButtonIconValue): ReactNode {
  return isIconData(value) ? <Icon icon={value} /> : value;
}

const _labelStyle: CSSProperties = {
  fontSize: 13,
  fontWeight: 600,
  letterSpacing: 0,
};
const _iconSize = 18;
const _iconGap = 8;
const _measurementSafety = 2;

export interface ButtonProps {
  children?: ReactNode;
  onPressed?: (() => void) | null;
  icon?: ButtonIconValue;
  showIcon?: boolean;
  tooltip?: string | null;
  tone?: ButtonTone;
  selected?: boolean;
  toggleValue?: boolean | null;
  onToggleChanged?: ((value: boolean) => void) | null;
  /** Fixed width; `Infinity` fills the available width (Dart `double.infinity`). */
  width?: number | null;
  height?: number;
  padding?: EdgeInsetsValue;
  mainAxisSize?: 'min' | 'max';
  loading?: boolean;
  /** Outer box style (DOM addition). */
  style?: CSSProperties;
  'data-testid'?: string;
}

function ButtonImpl({
  children,
  onPressed,
  icon,
  showIcon = true,
  tooltip,
  tone = ButtonTone.neutral,
  selected = false,
  toggleValue,
  onToggleChanged,
  width,
  height = 40,
  padding = EdgeInsets.symmetric({ horizontal: 16 }),
  mainAxisSize = 'min',
  loading = false,
  style,
  'data-testid': testId,
}: ButtonProps) {
  const toggleMode = toggleValue != null || onToggleChanged != null;
  const toggled = toggleValue ?? false;
  const visuallyEnabled = onPressed != null || onToggleChanged != null || loading;
  const effectiveOnPressed = loading
    ? null
    : toggleMode && onToggleChanged != null
      ? () => onToggleChanged(!toggled)
      : onPressed;
  const active = selected || toggled || tone === ButtonTone.primary;
  const colors = _colorsFor(tone, visuallyEnabled, { active });
  const fillsWidth = width != null && !Number.isFinite(width);
  const content = (
    <_ButtonContent
      colors={colors}
      icon={loading && icon != null ? <_ButtonLoadingIndicator size={_iconSize} /> : showIcon && icon != null ? renderIcon(icon) : null}
      // A button with a definite width lays its row out across that width.
      bounded={width != null || mainAxisSize === 'max'}
    >
      {children}
    </_ButtonContent>
  );

  return (
    <PressableSurface
      onPressed={effectiveOnPressed}
      tooltip={tooltip}
      enabled={visuallyEnabled}
      loading={loading}
      selected={active}
      width={fillsWidth ? null : width}
      height={height}
      padding={padding}
      borderRadius={UiRadii.pill}
      backgroundColor={colors.background}
      selectedBackgroundColor={colors.background}
      hoverBackgroundColor={colors.hoverBackground}
      pressedBackgroundColor={colors.pressedBackground}
      borderColor={colors.border}
      selectedBorderColor={colors.border}
      disabledBackgroundColor={colors.background}
      disabledBorderColor={colors.border}
      data-testid={testId}
      style={{
        // Flutter's Button fills a bounded parent (its row has
        // `MainAxisSize.max` under a bounded LayoutBuilder) and shrink-wraps in
        // a Row. `align-self: stretch` gives the same split in CSS: it fills
        // columns and blocks, and is a no-op for the fixed-height cross axis
        // of a row.
        alignSelf: 'stretch',
        minWidth: 0,
        ...(fillsWidth ? { width: '100%', boxSizing: 'border-box' } : null),
        ...style,
      }}
    >
      {content}
    </PressableSurface>
  );
}

/**
 * Minimum width needed to show [label] without truncating it (Dart
 * `Button.minimumWidthForLabel(context, label:, hasIcon:, padding:)`).
 */
function minimumWidthForLabel(opts: { label: string; hasIcon?: boolean; padding?: EdgeInsetsValue }): number {
  const { label, hasIcon = false, padding = EdgeInsets.symmetric({ horizontal: 16 }) } = opts;
  const width = measureTextWidth(label, { ..._labelStyle, fontFamily: kClientFontStack });
  return width + horizontalInsets(padding) + (hasIcon ? _iconSize + _iconGap : 0) + _measurementSafety;
}

/**
 * PORT: Dart's `Button.withoutIcon()` copied a widget instance; React
 * elements are immutable, so callers pass `showIcon={false}` instead
 * (ResponsiveDialogActionBar does).
 */
export const Button = Object.assign(ButtonImpl, { minimumWidthForLabel });

export interface ButtonIconProps {
  icon: ButtonIconValue;
  onPressed?: (() => void) | null;
  onToggleChanged?: ((value: boolean) => void) | null;
  /** Width of a key-shaped button; defaults to [size] (a circle). */
  width?: number | null;
  radius?: number;
  iconSize?: number | null;
  tooltip?: string | null;
  tone?: ButtonTone;
  selected?: boolean;
  toggleValue?: boolean | null;
  size?: number;
  loading?: boolean;
  /**
   * Borderless toolbar style: transparent at rest, tinted on hover. Active
   * (selected/toggled) ghost buttons still show their tone colours.
   */
  ghost?: boolean;
  backgroundColor?: string | null;
  borderColor?: string | null;
  /** Outer box style (DOM addition). */
  style?: CSSProperties;
  'aria-label'?: string;
  'data-testid'?: string;
}

export function ButtonIcon({
  icon,
  tooltip,
  onPressed,
  tone = ButtonTone.neutral,
  selected = false,
  toggleValue,
  onToggleChanged,
  size = 40,
  loading = false,
  ghost = false,
  backgroundColor,
  borderColor,
  width,
  radius = UiRadii.pill,
  iconSize,
  style,
  'aria-label': ariaLabel,
  'data-testid': testId,
}: ButtonIconProps) {
  const toggleMode = toggleValue != null || onToggleChanged != null;
  const toggled = toggleValue ?? false;
  const visuallyEnabled = onPressed != null || onToggleChanged != null || loading;
  const effectiveOnPressed = loading
    ? null
    : toggleMode && onToggleChanged != null
      ? () => onToggleChanged(!toggled)
      : onPressed;
  const active = selected || toggled || tone === ButtonTone.primary;
  const colors = _colorsFor(tone, visuallyEnabled, { active, ghost, icon: true });
  const background = backgroundColor ?? colors.background;
  const border = borderColor ?? colors.border;
  const resolvedIconSize = iconSize ?? size * 0.46;

  return (
    <PressableSurface
      onPressed={effectiveOnPressed}
      tooltip={tooltip}
      enabled={visuallyEnabled}
      loading={loading}
      selected={active}
      width={width ?? size}
      height={size}
      padding={0}
      borderRadius={radius}
      backgroundColor={background}
      selectedBackgroundColor={background}
      hoverBackgroundColor={backgroundColor == null ? colors.hoverBackground : null}
      pressedBackgroundColor={colors.pressedBackground}
      borderColor={border}
      selectedBorderColor={border}
      disabledBackgroundColor={background}
      disabledBorderColor={border}
      aria-label={ariaLabel ?? tooltip ?? undefined}
      data-testid={testId}
      style={style}
      contentStyle={{ alignItems: 'center' }}
    >
      <IconTheme data={{ color: colors.foreground, size: resolvedIconSize }}>
        {loading ? <_ButtonLoadingIndicator size={resolvedIconSize} /> : renderIcon(icon)}
      </IconTheme>
    </PressableSurface>
  );
}

export interface ButtonIconPlainProps {
  icon: ButtonIconValue;
  onPressed: (() => void) | null;
  tooltip?: string | null;
  width?: number;
  height?: number;
  iconSize?: number;
  style?: CSSProperties;
}

/**
 * 无背景、无边框的图标按钮，适用于标题行内的返回或导航操作。
 *
 * [width] 和 [height] 定义完整点击热区；图标本身保持较小尺寸，避免为了
 * 扩大热区而破坏标题行的视觉对齐。
 */
export function ButtonIconPlain({ icon, onPressed, tooltip, width = 30, height = 36, iconSize = 20, style }: ButtonIconPlainProps) {
  const enabled = onPressed != null;
  let result: ReactNode = (
    <div
      role="button"
      aria-disabled={!enabled}
      aria-label={tooltip ?? undefined}
      onClick={(event) => {
        if (event.button !== 0) return;
        onPressed?.();
      }}
      style={{
        width,
        height,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        ...style,
      }}
    >
      <IconTheme data={{ color: enabled ? UiColors.textSecondary : UiColors.textMuted, size: iconSize }}>
        {renderIcon(icon)}
      </IconTheme>
    </div>
  );
  if (tooltip != null && tooltip.length > 0) {
    result = <UiTooltip message={tooltip}>{result}</UiTooltip>;
  }
  return <>{result}</>;
}

function _ButtonLoadingIndicator({ size }: { size: number }) {
  const theme = useIconTheme();
  return (
    <div style={{ width: size, height: size, flexShrink: 0 }}>
      <CircularProgressIndicator size={size} strokeWidth={2} color={theme.color ?? UiColors.accent} />
    </div>
  );
}

function _ButtonContent({
  colors,
  icon,
  bounded,
  children,
}: {
  colors: _ButtonColors;
  icon: ReactNode;
  bounded: boolean;
  children?: ReactNode;
}) {
  return (
    <IconTheme data={{ color: colors.foreground, size: _iconSize }}>
      <div
        style={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          minWidth: 0,
          width: bounded ? '100%' : undefined,
          ..._labelStyle,
          color: colors.foreground,
        }}
      >
        {icon != null ? (
          <>
            {icon}
            <div style={{ width: _iconGap, flexShrink: 0 }} />
          </>
        ) : null}
        <span
          className="gang-button-label"
          style={{
            display: 'block',
            minWidth: 0,
            flex: '0 1 auto',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {children}
        </span>
      </div>
    </IconTheme>
  );
}

interface _ButtonColors {
  background: string;
  hoverBackground: string;
  pressedBackground: string;
  border: string;
  foreground: string;
}

const _neutralButtonSurface = argb(0xff1a1a1d);
const _neutralButtonBorder = argb(0xff2e2e33);
const _neutralIconForeground = argb(0xffc9c9cd);

function _colorsFor(
  tone: ButtonTone,
  enabled: boolean,
  opts: { active?: boolean; ghost?: boolean; icon?: boolean } = {},
): _ButtonColors {
  const { active = false, ghost = false, icon = false } = opts;
  if (!enabled) {
    return {
      background: ghost ? UiColors.transparent : UiColors.disabledSurface,
      hoverBackground: ghost ? UiColors.transparent : UiColors.disabledSurface,
      pressedBackground: ghost ? UiColors.transparent : UiColors.disabledSurface,
      border: ghost ? UiColors.transparent : UiColors.disabledBorder,
      foreground: UiColors.textMuted,
    };
  }

  switch (tone) {
    case ButtonTone.neutral:
      if (active) {
        // Selected / toggled-on controls invert to a white pill.
        return {
          background: UiColors.inverse,
          hoverBackground: argb(0xffffffff),
          pressedBackground: argb(0xffd9d9d6),
          border: UiColors.transparent,
          foreground: UiColors.onInverse,
        };
      }
      if (ghost) {
        return {
          background: UiColors.transparent,
          hoverBackground: UiColors.surfaceHover,
          pressedBackground: UiColors.surfaceRaised,
          border: UiColors.transparent,
          foreground: _neutralIconForeground,
        };
      }
      return {
        background: _neutralButtonSurface,
        hoverBackground: UiStates.hover(_neutralButtonSurface),
        pressedBackground: UiStates.pressed(_neutralButtonSurface),
        border: _neutralButtonBorder,
        foreground: icon ? _neutralIconForeground : UiColors.text,
      };
    case ButtonTone.primary:
      return {
        background: UiColors.accentStrong,
        hoverBackground: UiColors.accent,
        pressedBackground: lerpColor(UiColors.accentStrong, UiColors.black, 0.12),
        border: UiColors.transparent,
        foreground: UiColors.onAccent,
      };
    case ButtonTone.danger:
      return {
        background: ghost ? UiColors.transparent : UiColors.dangerSurface,
        hoverBackground: UiStates.hover(UiColors.dangerSurface),
        pressedBackground: UiStates.pressed(UiColors.dangerSurface),
        border: ghost ? UiColors.transparent : UiColors.dangerBorder,
        foreground: UiColors.danger,
      };
  }
}

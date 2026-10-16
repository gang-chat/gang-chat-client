import { useState, type CSSProperties, type ReactNode } from 'react';

import type { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';

import { Row, SizedBox } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { Icon, Icons, type IconData } from '../ui/icon';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiCurves, UiRadii, argb } from '../ui/tokens';

export const appWindowControlsWidth = 134.0;
export const appWindowCompactControlsWidth = 94.0;
export const appWindowControlWidth = 34.0;
export const appWindowControlHeight = 28.0;
export const appWindowControlGap = 6.0;

export interface AppWindowDragRegionProps {
  windowController: DesktopWindowController;
  children?: ReactNode;
  onDoubleTap?: (() => void) | null;
  /** Outer box style (DOM addition): Flutter got its size from the parent constraints. */
  style?: CSSProperties;
  className?: string;
}

/**
 * Port of `AppWindowDragRegion`.
 *
 * PORT: Flutter started a native drag on pan start
 * (`windowController.startDragging()`). Electron drags frameless windows
 * through the CSS `-webkit-app-region: drag` region (`gang-drag-region` in the
 * renderer's global.css), so the region is a real box with that class.
 * Clickable descendants must opt out with `className="gang-no-drag"`
 * ([AppWindowControls] does). The OS consumes pointer events inside a drag
 * region, so [onDoubleTap] only fires where the platform still delivers the
 * double click (Windows maximizes a maximizable window natively instead).
 */
export function AppWindowDragRegion({ windowController: _windowController, children, onDoubleTap, style, className }: AppWindowDragRegionProps) {
  return (
    <div
      className={className == null ? 'gang-drag-region' : `gang-drag-region ${className}`}
      onDoubleClick={onDoubleTap == null ? undefined : () => onDoubleTap()}
      style={style}
    >
      {children}
    </div>
  );
}

export interface AppWindowControlsProps {
  maximized?: boolean;
  onMinimize: () => void;
  onToggleMaximize?: (() => void) | null;
  onClose: () => void;
  showMaximize?: boolean;
}

export function AppWindowControls({
  maximized = false,
  onMinimize,
  onToggleMaximize,
  onClose,
  showMaximize = true,
}: AppWindowControlsProps) {
  if (showMaximize && onToggleMaximize == null) {
    throw new Error('AppWindowControls: showMaximize requires onToggleMaximize.');
  }
  return (
    <SizedBox
      className="gang-no-drag"
      width={showMaximize ? appWindowControlsWidth : appWindowCompactControlsWidth}
      style={{ paddingLeft: 10, paddingRight: 10 }}
    >
      <Row mainAxisAlignment="end">
        <AppWindowControlButton tooltip="最小化" icon={Icons.remove} onPressed={onMinimize} />
        {showMaximize ? (
          <>
            <SizedBox width={appWindowControlGap} />
            <AppWindowControlButton
              tooltip={maximized ? '还原' : '最大化'}
              icon={maximized ? Icons.filter_none : Icons.crop_square}
              onPressed={onToggleMaximize!}
            />
          </>
        ) : null}
        <SizedBox width={appWindowControlGap} />
        <AppWindowControlButton tooltip="关闭" icon={Icons.close} danger onPressed={onClose} />
      </Row>
    </SizedBox>
  );
}

export interface AppWindowControlButtonProps {
  tooltip: string;
  icon: IconData;
  onPressed: () => void;
  danger?: boolean;
}

export function AppWindowControlButton({ tooltip, icon, onPressed, danger = false }: AppWindowControlButtonProps) {
  const [hovered, setHoveredState] = useState(false);
  const [pressed, setPressedState] = useState(false);

  const setHovered = (value: boolean) => {
    if (hovered === value) return;
    setHoveredState(value);
  };
  const setPressed = (value: boolean) => {
    if (pressed === value) return;
    setPressedState(value);
  };

  const active = hovered || pressed;
  const background = active ? (danger ? argb(0xff332126) : UiColors.surface) : UiColors.transparent;
  const border = active ? (danger ? UiColors.dangerBorder : UiColors.border) : UiColors.transparent;
  const foreground = active && danger ? UiColors.danger : UiColors.textSecondary;
  const transition = (property: string) => `${property} 90ms ${UiCurves.easeOutCubic}`;

  return (
    <UiTooltip message={tooltip}>
      <div
        role="button"
        aria-label={tooltip}
        className="gang-no-drag"
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => {
          setHovered(false);
          setPressed(false);
        }}
        onMouseDown={(event) => {
          if (event.button !== 0) return;
          setPressed(true);
        }}
        onMouseUp={() => setPressed(false)}
        onClick={(event) => {
          if (event.button !== 0) return;
          onPressed();
        }}
        style={{
          width: appWindowControlWidth,
          height: appWindowControlHeight,
          flexShrink: 0,
          boxSizing: 'border-box',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: SystemMouseCursors.click,
          backgroundColor: background,
          borderRadius: UiRadii.sm,
          border: `1px solid ${border}`,
          transition: `${transition('background-color')}, ${transition('border-color')}`,
        }}
      >
        <Icon icon={icon} size={16} color={foreground} />
      </div>
    </UiTooltip>
  );
}

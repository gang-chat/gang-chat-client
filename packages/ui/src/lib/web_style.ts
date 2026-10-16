import { Platform, type TextStyle, type ViewStyle } from 'react-native';

/**
 * CSS-only style properties that react-native-web understands but React
 * Native's style types do not declare (or declare more narrowly). [webStyle]
 * applies them on the web and drops them on native, so shared components can
 * keep Flutter's mouse cursors, hover transitions and selection rules without
 * leaking DOM styles into the native renderer.
 */
export interface WebOnlyStyle {
  cursor?: string;
  userSelect?: 'none' | 'text' | 'auto' | 'all';
  outlineStyle?: 'none' | 'solid';
  outlineWidth?: number;
  caretColor?: string;
  transitionProperty?: string;
  transitionDuration?: string;
  transitionTimingFunction?: string;
  transitionDelay?: string;
  clipPath?: string;
  touchAction?: string;
  overscrollBehavior?: string;
  scrollbarWidth?: 'none' | 'thin' | 'auto';
  willChange?: string;
  pointerEvents?: 'none' | 'auto' | 'box-none' | 'box-only';
  backgroundImage?: string;
  backgroundSize?: string;
  backgroundPosition?: string;
  whiteSpace?: string;
  wordBreak?: string;
  overflowWrap?: string;
  textOverflow?: string;
  resize?: string;
  position?: 'fixed' | 'sticky';
}

const isWeb = Platform.OS === 'web';

/** Returns [style] on the web and an empty style on native. */
export function webStyle(style: WebOnlyStyle): ViewStyle & TextStyle {
  if (!isWeb) return {};
  return style as unknown as ViewStyle & TextStyle;
}

/**
 * Port of Flutter's `SystemMouseCursors`, as CSS cursor names. Pass the value
 * to a `mouseCursor` prop (or `webStyle({ cursor })`).
 */
export const SystemMouseCursors = {
  basic: 'default',
  click: 'pointer',
  text: 'text',
  forbidden: 'not-allowed',
  grab: 'grab',
  grabbing: 'grabbing',
  move: 'move',
  none: 'none',
  precise: 'crosshair',
  help: 'help',
  wait: 'wait',
  progress: 'progress',
  contextMenu: 'context-menu',
  copy: 'copy',
  zoomIn: 'zoom-in',
  zoomOut: 'zoom-out',
  resizeColumn: 'col-resize',
  resizeRow: 'row-resize',
  resizeLeftRight: 'ew-resize',
  resizeUpDown: 'ns-resize',
  resizeLeft: 'w-resize',
  resizeRight: 'e-resize',
  resizeUp: 'n-resize',
  resizeDown: 's-resize',
  resizeUpLeftDownRight: 'nwse-resize',
  resizeUpRightDownLeft: 'nesw-resize',
} as const;

export type MouseCursor = string;

/** CSS transition for implicit (AnimatedContainer-style) animations on the web. */
export function webTransition(properties: readonly string[], durationMs: number, curve: string): ViewStyle {
  return webStyle({
    transitionProperty: properties.join(', '),
    transitionDuration: `${durationMs}ms`,
    transitionTimingFunction: curve,
  });
}

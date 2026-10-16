/**
 * Port of Flutter's `SystemMouseCursors`, as CSS cursor names. Components
 * that take a Dart `mouseCursor` accept one of these values.
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

/** A CSS `cursor` value (Flutter `MouseCursor`). */
export type MouseCursor = string;

/** Flutter's `kTouchSlop`: movement (px) after which a touch counts as a drag. */
export const kTouchSlop = 18;

/** Flutter's `kLongPressTimeout` in milliseconds. */
export const kLongPressTimeout = 500;

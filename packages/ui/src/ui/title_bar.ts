import { ValueNotifier } from '@gang-chat/core/lib/change_notifier';

/**
 * Legacy layout offsets from the old in-app custom title bar.
 *
 * The native title bar is hidden while native window buttons stay visible, so
 * app content should not reserve an extra in-content strip for window controls.
 */
export const titleBarHeight = 0;

export const windowDragHeight = 0;

export const windowControlsInset = 0;

export const windowControlsWidth = 0;

/**
 * Kept for the existing full-screen screen-share state path. Native system
 * controls are not painted by the renderer, so toggling this is a no-op.
 */
export const windowControlsHidden = new ValueNotifier<boolean>(false);

/** @deprecated Native system title bars are used instead. */
export function WindowControls(): null {
  return null;
}

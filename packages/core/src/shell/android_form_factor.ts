import type { Size } from './desktop_window_controller';

/**
 * Android's standard large-screen boundary. Keeping this in one platform
 * module prevents phone/tablet checks from drifting between features.
 */
export const androidTabletShortestSide = 600;

export function isAndroidTabletLogicalSize(logicalSize: Size): boolean {
  return Math.min(Math.abs(logicalSize.width), Math.abs(logicalSize.height)) >= androidTabletShortestSide;
}

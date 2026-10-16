import { HostPlatform } from '@gang-chat/core/config/host_platform';

export const sidebarWidth = 248;
export const sidebarHorizontalPadding = 14;
export const sidebarRoomCardWidth = sidebarWidth - sidebarHorizontalPadding * 2;
export const narrowBreakpoint = 720;

/**
 * Whether the home shell uses the compact (phone-width) layout at [width].
 *
 * PORT: Flutter switched any window narrower than [narrowBreakpoint] to the
 * compact layout. Only the web build still does (phone browsers); the
 * desktop window has a minimum size (`appWindowMinSize`, 960×600) and always
 * keeps the wide layout, even while it is being resized.
 */
export function usesNarrowHomeLayout(width: number): boolean {
  return HostPlatform.isWeb && width < narrowBreakpoint;
}

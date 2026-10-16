/**
 * PORT: the Flutter version patched the `flutter/keyevent` channel to work
 * around a Flutter Windows raw-key edge case (a bare Alt key-down arriving
 * with `modifiers: 0` crashed the debug runner). Chromium's keyboard events
 * have no such bug, so this is a no-op kept for the startup call site.
 */
export function installWindowsAltKeyEventGuard(): void {}

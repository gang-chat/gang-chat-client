import { HostPlatform } from '@gang-chat/core/config/host_platform';

const _macOsSidebarTopOffset = 18;
// The native traffic lights end near x=69 after the window recenters them.
// Keep a small interaction gap before title-bar content.
const _macOsTitleBarLeadingControlsSafeInset = 80;

export class WindowChromeInsets {
  constructor(opts: { sidebarTopOffset: number; titleBarLeadingControlsSafeInset: number }) {
    this.sidebarTopOffset = opts.sidebarTopOffset;
    this.titleBarLeadingControlsSafeInset = opts.titleBarLeadingControlsSafeInset;
  }

  readonly sidebarTopOffset: number;
  readonly titleBarLeadingControlsSafeInset: number;

  /**
   * Port of `WindowChromeInsets.of(context)`.
   *
   * PORT: the mobile `viewPadding.top` branch has no DOM equivalent here and
   * resolves to 0; a future mobile app reads safe-area insets itself.
   */
  static of(): WindowChromeInsets {
    // A browser reports the visitor's OS (often macOS) but the page has no
    // native window chrome, so there is nothing to keep clear.
    if (HostPlatform.isWeb) {
      return new WindowChromeInsets({ sidebarTopOffset: 0, titleBarLeadingControlsSafeInset: 0 });
    }
    return new WindowChromeInsets({
      sidebarTopOffset: HostPlatform.isMacOS ? _macOsSidebarTopOffset : 0,
      titleBarLeadingControlsSafeInset: HostPlatform.isMacOS ? _macOsTitleBarLeadingControlsSafeInset : 0,
    });
  }
}

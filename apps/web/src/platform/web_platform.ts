import type { AppInfo, NotificationBridge, Platform } from '@gang-chat/core/platform/platform';
import { createBrowserSpeech } from '@gang-chat/ui/platform/browser_speech';

import { FetchHttpClient } from './fetch_http_client';
import { createWebClipboard } from './web_clipboard';
import { createWebFiles } from './web_files';
import { WebPreferences, WebSecureStorage } from './web_storage';

/**
 * The browser implementation of `Platform` (`@gang-chat/core`), the
 * counterpart of the Flutter web build's plugins and
 * `lib/src/shell/browser/browser_bridge_web.dart`.
 *
 * `os` is `web` (`kIsWeb`): the shared code then skips everything a browser
 * tab cannot do — the window, tray and title-bar controls, in-app updates,
 * install info, native screen-source picking (the browser shows its own
 * `getDisplayMedia` picker), copied file paths.
 */
export function createWebPlatform(opts: { version: string }): Platform {
  const app: AppInfo = {
    name: 'Gang Chat',
    version: opts.version,
    buildNumber: opts.version,
    installedAt: null,
    isPackaged: !__DEV__,
  };

  const notifications: NotificationBridge = {
    isSupported: () => typeof Notification !== 'undefined',
    async show(options, onClick) {
      if (typeof Notification === 'undefined') return;
      if (Notification.permission === 'default') await Notification.requestPermission();
      if (Notification.permission !== 'granted') return;
      const notification = new Notification(options.title, {
        body: options.body,
        silent: options.silent,
        tag: options.tag,
        icon: options.iconUrl ?? undefined,
      });
      notification.onclick = () => {
        window.focus();
        notification.close();
        onClick?.();
      };
    },
  };

  return {
    os: 'web',
    isDesktop: false,
    isMobile: _isMobileBrowser(),
    isWeb: true,
    app,
    http: new FetchHttpClient(),
    secureStorage: new WebSecureStorage(),
    preferences: new WebPreferences(),
    files: createWebFiles(),
    clipboard: createWebClipboard(),
    notifications,
    window: null,
    screenCapture: null,
    process: null,
    speech: createBrowserSpeech(),
    updater: null,
    sparkle: null,
    mediaAccess: null,
    async openExternal(url) {
      // Like url_launcher on the web: mailto: in place, everything else in a
      // new tab.
      if (/^mailto:/i.test(url)) {
        window.location.href = url;
        return true;
      }
      // With noopener the browser returns no handle, even on success.
      window.open(url, '_blank', 'noopener,noreferrer');
      return true;
    },
    cachedMediaUrl: (url) => url,
  };
}

/** Phone and tablet browsers (no `getDisplayMedia`, touch-first layout). */
function _isMobileBrowser(): boolean {
  const data = (navigator as Navigator & { userAgentData?: { mobile?: boolean } }).userAgentData;
  if (typeof data?.mobile === 'boolean') return data.mobile;
  const agent = navigator.userAgent;
  if (/Android|iPhone|iPod|Mobile/i.test(agent)) return true;
  // iPadOS reports itself as a Mac; touch points give it away.
  return /Macintosh/.test(agent) && navigator.maxTouchPoints > 1;
}

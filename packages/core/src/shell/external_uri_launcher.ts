import { hasPlatform, platform } from '../platform/platform';

/**
 * Opens links with the system handler.
 *
 * PORT: the Flutter client spawned `rundll32 url.dll,FileProtocolHandler` /
 * `open` / `xdg-open` itself, called the Android bridge, or `window.open` on
 * the web; every host now does that behind `platform().openExternal`.
 */
export class ExternalUriLauncher {
  async open(uri: string): Promise<void> {
    if (!hasPlatform() || !(await platform().openExternal(uri))) {
      throw new Error('当前平台不支持打开链接');
    }
  }
}

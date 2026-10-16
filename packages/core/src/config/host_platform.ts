import { hasPlatform, platform } from '../platform/platform';

/**
 * Port of `HostPlatform`: the operating system the app runs on, answered by
 * the installed platform bridge. Every `is*` check is false before a bridge is
 * installed (unit tests) and on the web.
 */
export const HostPlatform = {
  get isWindows(): boolean {
    return hasPlatform() && platform().os === 'windows';
  },
  get isMacOS(): boolean {
    return hasPlatform() && platform().os === 'macos';
  },
  get isLinux(): boolean {
    return hasPlatform() && platform().os === 'linux';
  },
  get isAndroid(): boolean {
    return hasPlatform() && platform().os === 'android';
  },
  get isIOS(): boolean {
    return hasPlatform() && platform().os === 'ios';
  },
  /** Port of `kIsWeb`. */
  get isWeb(): boolean {
    return hasPlatform() && platform().os === 'web';
  },
  get isDesktop(): boolean {
    return hasPlatform() && platform().isDesktop;
  },
  get operatingSystem(): string {
    return hasPlatform() ? platform().os : 'web';
  },
  get pathSeparator(): string {
    return hasPlatform() && platform().os === 'windows' ? '\\' : '/';
  },
};

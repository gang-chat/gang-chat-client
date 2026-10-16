import { app, ipcMain, session } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

import { IpcChannels, kMediaScheme, type BootSnapshot, type HostOsName } from '../shared/ipc';
import { registerClipboard } from './clipboard';
import { registerFiles } from './files';
import { registerMediaAccess } from './media_access';
import { handleMediaProtocol, registerMediaScheme } from './media_protocol';
import { registerNetBridge } from './net_bridge';
import { installDisplayMediaHandler, registerScreenCapture } from './screen_capture';
import { loadStorage, preferencesSnapshot, readSecure, registerStorage } from './storage';
import { clearNotifications, registerSystem } from './system';
import { registerUpdater } from './updater';
import {
  createMainWindow,
  getMainWindow,
  isQuitting,
  markQuitting,
  quitApplication,
  registerWindowIpc,
  requestExitFromRenderer,
} from './window';

declare const __GANG_CONFIG_OVERRIDES__: BootSnapshot['configOverrides'];

const kRefreshTokenKey = 'gang.refreshToken';
const kInstallInfoFileName = 'gang_chat_install_info.txt';

// Same application identity as the Flutter builds so notifications and the
// taskbar group under "Gang Chat".
app.setName('Gang Chat');
if (process.platform === 'win32') app.setAppUserModelId('com.gangchat.client');

if (process.platform === 'darwin') {
  // ScreenCaptureKit system-audio loopback for screen share (macOS 14.2+).
  app.commandLine.appendSwitch('enable-features', 'MacLoopbackAudioForScreenShare,MacSckSystemAudioLoopbackOverride');
}
// The voice channel keeps playing while the window is hidden in the tray.
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.commandLine.appendSwitch('disable-background-timer-throttling');

registerMediaScheme();

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    const window = getMainWindow();
    if (!window) return;
    window.setSkipTaskbar(false);
    if (window.isMinimized()) window.restore();
    window.show();
    window.focus();
  });
}

function hostOs(): HostOsName {
  if (process.platform === 'win32') return 'windows';
  if (process.platform === 'darwin') return 'macos';
  return 'linux';
}

function readAppConfig(): Record<string, unknown> | null {
  const candidates = app.isPackaged
    ? [path.join(process.resourcesPath, 'app_config.json')]
    : [path.join(__dirname, '../../../../assets/config/app_config.json')];
  for (const file of candidates) {
    try {
      return JSON.parse(fs.readFileSync(file, 'utf8')) as Record<string, unknown>;
    } catch {
      // try next
    }
  }
  return null;
}

function readInstalledAt(): string | null {
  try {
    const file = path.join(path.dirname(app.getPath('exe')), kInstallInfoFileName);
    return fs.readFileSync(file, 'utf8');
  } catch {
    return null;
  }
}

function hostsOf(config: Record<string, unknown> | null): string[] {
  const overrides = __GANG_CONFIG_OVERRIDES__;
  const urls = [
    overrides.apiBaseUrl || config?.['api_base_url'],
    overrides.assetBaseUrl || config?.['asset_base_url'],
    overrides.releaseBucketUrl || config?.['release_bucket_url'],
  ];
  const hosts = new Set<string>();
  for (const url of urls) {
    if (typeof url !== 'string') continue;
    try {
      hosts.add(new URL(url).hostname.toLowerCase());
    } catch {
      // ignore
    }
  }
  return [...hosts];
}

/**
 * Proxy behaviour matches the Flutter client: dart:io ignored the OS proxy
 * settings, used `http(s)_proxy` environment variables for other hosts, and
 * always went DIRECT to the configured Gang Chat hosts (and LiveKit).
 */
async function configureProxy(config: Record<string, unknown> | null): Promise<void> {
  const envProxy = process.env.HTTPS_PROXY || process.env.https_proxy || process.env.HTTP_PROXY || process.env.http_proxy;
  if (!envProxy) {
    await session.defaultSession.setProxy({ mode: 'direct' });
    return;
  }
  const noProxy = (process.env.NO_PROXY || process.env.no_proxy || '')
    .split(',')
    .map((h) => h.trim())
    .filter(Boolean);
  await session.defaultSession.setProxy({
    mode: 'fixed_servers',
    proxyRules: envProxy.replace(/^https?:\/\//, '').replace(/\/$/, ''),
    proxyBypassRules: ['<local>', ...hostsOf(config), ...noProxy].join(','),
  });
}

function releaseBucketUrl(config: Record<string, unknown> | null): string {
  const override = __GANG_CONFIG_OVERRIDES__.releaseBucketUrl;
  if (override) return override;
  const value = config?.['release_bucket_url'];
  return typeof value === 'string' && value.length > 0 ? value : 'https://os.ky-z.com:9000/gang-chat';
}

let bootSnapshot: BootSnapshot | null = null;

function buildBootSnapshot(config: Record<string, unknown> | null): BootSnapshot {
  const preferences = preferencesSnapshot();
  let downloads: string | null = null;
  try {
    downloads = app.getPath('downloads');
  } catch {
    downloads = null;
  }
  return {
    os: hostOs(),
    appName: 'Gang Chat',
    appVersion: app.getVersion(),
    buildNumber: '1',
    isPackaged: app.isPackaged,
    installedAt: readInstalledAt(),
    preferences,
    hasStoredSession: (readSecure(kRefreshTokenKey) ?? '').length > 0,
    appConfig: config,
    configOverrides: __GANG_CONFIG_OVERRIDES__,
    paths: {
      temporary: app.getPath('temp'),
      applicationSupport: app.getPath('userData'),
      applicationCache: path.join(app.getPath('userData'), 'cache'),
      downloads,
      roamingAppData: process.platform === 'win32' ? app.getPath('appData') : null,
    },
    mediaScheme: kMediaScheme,
  };
}

app.whenReady().then(async () => {
  loadStorage();
  const config = readAppConfig();
  await configureProxy(config);

  registerStorage();
  registerNetBridge();
  registerFiles();
  registerClipboard();
  registerScreenCapture();
  registerMediaAccess();
  registerSystem();
  registerWindowIpc();
  registerUpdater(releaseBucketUrl(config));
  handleMediaProtocol();
  installDisplayMediaHandler();

  // Microphone / camera / screen permissions are requested by the OS; the app
  // itself grants its own renderer every media permission it asks for.
  session.defaultSession.setPermissionRequestHandler((_webContents, permission, callback) => {
    callback(['media', 'display-capture', 'notifications', 'clipboard-read', 'clipboard-sanitized-write', 'speaker-selection', 'fullscreen'].includes(permission));
  });
  session.defaultSession.setPermissionCheckHandler((_webContents, permission) =>
    ['media', 'display-capture', 'notifications', 'clipboard-read', 'clipboard-sanitized-write', 'speaker-selection', 'fullscreen'].includes(permission),
  );

  bootSnapshot = buildBootSnapshot(config);
  ipcMain.on(IpcChannels.bootSnapshot, (event) => {
    // Refresh the preference snapshot for reloads during development.
    event.returnValue = { ...bootSnapshot!, preferences: preferencesSnapshot() };
  });

  const preload = path.join(__dirname, '../preload/index.js');
  const devUrl = process.env.ELECTRON_RENDERER_URL;
  createMainWindow({
    authenticated: bootSnapshot.hasStoredSession,
    preload,
    loadUrl: devUrl,
    loadFile: devUrl ? undefined : path.join(__dirname, '../renderer/index.html'),
  });

  app.on('activate', () => {
    const window = getMainWindow();
    if (window) {
      window.show();
      window.focus();
    }
  });
});

app.on('before-quit', (event) => {
  // Cmd+Q / system shutdown: let the renderer leave the voice channel and run
  // its shutdown hooks first (the Flutter shell intercepted the same path),
  // with a hard deadline in case the renderer is wedged.
  if (!isQuitting() && requestExitFromRenderer()) {
    event.preventDefault();
    setTimeout(() => quitApplication(), 3000).unref();
    return;
  }
  markQuitting();
  clearNotifications();
});

app.on('window-all-closed', () => {
  quitApplication();
});

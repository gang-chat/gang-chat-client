import { app, ipcMain, safeStorage } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

import { IpcChannels, type PreferenceValue } from '../shared/ipc';

/**
 * Local persistence for the desktop app.
 *
 * - Preferences replace `shared_preferences`: a JSON file in userData. The
 *   renderer receives the whole map synchronously at boot and writes through.
 * - Secure storage replaces `flutter_secure_storage` (Keychain / Credential
 *   Manager): values are encrypted with Electron `safeStorage`, which uses the
 *   macOS Keychain / Windows DPAPI under the hood, and stored in userData.
 */

const prefsFile = () => path.join(app.getPath('userData'), 'preferences.json');
const secureFile = () => path.join(app.getPath('userData'), 'secure-storage.json');

let preferences: Record<string, PreferenceValue> = {};
let secure: Record<string, string> = {};
let prefsWriteTimer: NodeJS.Timeout | null = null;

function readJson<T>(file: string, fallback: T): T {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8')) as T;
  } catch {
    return fallback;
  }
}

function writeJsonAtomic(file: string, value: unknown): void {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const temp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(temp, JSON.stringify(value), { mode: 0o600 });
  fs.renameSync(temp, file);
}

function schedulePrefsWrite(): void {
  if (prefsWriteTimer) return;
  prefsWriteTimer = setTimeout(() => {
    prefsWriteTimer = null;
    writeJsonAtomic(prefsFile(), preferences);
  }, 50);
}

export function flushPreferences(): void {
  if (prefsWriteTimer) {
    clearTimeout(prefsWriteTimer);
    prefsWriteTimer = null;
  }
  try {
    writeJsonAtomic(prefsFile(), preferences);
  } catch {
    // best-effort on shutdown
  }
}

export function loadStorage(): void {
  preferences = readJson<Record<string, PreferenceValue>>(prefsFile(), {});
  secure = readJson<Record<string, string>>(secureFile(), {});
}

export function preferencesSnapshot(): Record<string, PreferenceValue> {
  return { ...preferences };
}

function encrypt(value: string): string {
  if (safeStorage.isEncryptionAvailable()) {
    return `enc:${safeStorage.encryptString(value).toString('base64')}`;
  }
  // PORT: without an OS keyring (some Linux sessions) safeStorage cannot
  // encrypt; keep the value obfuscated-only rather than failing login.
  return `raw:${Buffer.from(value, 'utf8').toString('base64')}`;
}

function decrypt(stored: string): string | null {
  try {
    if (stored.startsWith('enc:')) {
      return safeStorage.decryptString(Buffer.from(stored.slice(4), 'base64'));
    }
    if (stored.startsWith('raw:')) {
      return Buffer.from(stored.slice(4), 'base64').toString('utf8');
    }
  } catch {
    return null;
  }
  return null;
}

export function readSecure(key: string): string | null {
  const stored = secure[key];
  return stored == null ? null : decrypt(stored);
}

export function registerStorage(): void {
  ipcMain.handle(IpcChannels.secureRead, (_event, key: string) => readSecure(key));
  ipcMain.handle(IpcChannels.secureWrite, (_event, key: string, value: string) => {
    secure[key] = encrypt(value);
    writeJsonAtomic(secureFile(), secure);
  });
  ipcMain.handle(IpcChannels.secureDelete, (_event, key: string) => {
    if (!(key in secure)) return;
    delete secure[key];
    writeJsonAtomic(secureFile(), secure);
  });
  ipcMain.handle(IpcChannels.prefsSet, (_event, key: string, value: PreferenceValue) => {
    preferences[key] = value;
    schedulePrefsWrite();
  });
  ipcMain.handle(IpcChannels.prefsRemove, (_event, key: string) => {
    delete preferences[key];
    schedulePrefsWrite();
  });
}

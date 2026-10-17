import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import { describe, expect, it } from 'vitest';

import { WebPreferences, WebSecureStorage } from '../src/platform/web_storage';

/** localStorage stand-in. */
class MemoryStorage implements Storage {
  private readonly _items = new Map<string, string>();
  get length(): number {
    return this._items.size;
  }
  clear(): void {
    this._items.clear();
  }
  getItem(key: string): string | null {
    return this._items.get(key) ?? null;
  }
  key(index: number): string | null {
    return [...this._items.keys()][index] ?? null;
  }
  removeItem(key: string): void {
    this._items.delete(key);
  }
  setItem(key: string, value: string): void {
    this._items.set(key, String(value));
  }
}

/** What flutter_secure_storage_web 2.x writes for `write(key, value)` (AES-GCM-256, 12-byte IV). */
function flutterSecureStorageWrite(storage: Storage, key: string, value: string): void {
  let raw = storage.getItem('FlutterSecureStorage');
  if (raw == null) {
    raw = randomBytes(32).toString('base64');
    storage.setItem('FlutterSecureStorage', raw);
  }
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', Buffer.from(raw, 'base64'), iv);
  const encrypted = Buffer.concat([cipher.update(value, 'utf8'), cipher.final(), cipher.getAuthTag()]);
  storage.setItem(`FlutterSecureStorage.${key}`, `${iv.toString('base64')}.${encrypted.toString('base64')}`);
}

function flutterSecureStorageRead(storage: Storage, key: string): string {
  const [iv, payload] = storage.getItem(`FlutterSecureStorage.${key}`)!.split('.');
  const bytes = Buffer.from(payload!, 'base64');
  const decipher = createDecipheriv('aes-256-gcm', Buffer.from(storage.getItem('FlutterSecureStorage')!, 'base64'), Buffer.from(iv!, 'base64'));
  decipher.setAuthTag(bytes.subarray(bytes.length - 16));
  return Buffer.concat([decipher.update(bytes.subarray(0, bytes.length - 16)), decipher.final()]).toString('utf8');
}

describe('WebSecureStorage', () => {
  it('reads the refresh token the Flutter web build stored', async () => {
    const storage = new MemoryStorage();
    flutterSecureStorageWrite(storage, 'gang.refreshToken', 'rt_from_flutter_web');
    expect(await new WebSecureStorage(storage).read('gang.refreshToken')).toBe('rt_from_flutter_web');
  });

  it('writes values the Flutter plugin can read back', async () => {
    const storage = new MemoryStorage();
    const secure = new WebSecureStorage(storage);
    await secure.write('gang.refreshToken', 'rt_新的');
    expect(storage.getItem('FlutterSecureStorage.gang.refreshToken')).toMatch(/^[A-Za-z0-9+/=]+\.[A-Za-z0-9+/=]+$/);
    expect(flutterSecureStorageRead(storage, 'gang.refreshToken')).toBe('rt_新的');
    expect(await new WebSecureStorage(storage).read('gang.refreshToken')).toBe('rt_新的');
  });

  it('answers null for a missing or unreadable value and deletes', async () => {
    const storage = new MemoryStorage();
    const secure = new WebSecureStorage(storage);
    expect(await secure.read('missing')).toBeNull();
    await secure.write('k', 'v');
    storage.setItem('FlutterSecureStorage.k', 'garbage');
    expect(await secure.read('k')).toBeNull();
    await secure.delete('k');
    expect(storage.getItem('FlutterSecureStorage.k')).toBeNull();
  });
});

describe('WebPreferences', () => {
  it('reads shared_preferences_web values and ignores other keys', () => {
    const storage = new MemoryStorage();
    storage.setItem('flutter.gang.apiBaseUrl', JSON.stringify('https://ky-z.com/gang-chat/api/v1'));
    storage.setItem('flutter.gang.notifications', 'true');
    storage.setItem('flutter.gang.volume', '0.5');
    storage.setItem('flutter.gang.count', '3');
    storage.setItem('flutter.gang.accounts', JSON.stringify(['demo', 'alice']));
    storage.setItem('flutter.broken', '{not json');
    storage.setItem('FlutterSecureStorage', 'key');
    const prefs = new WebPreferences(storage);
    expect(prefs.getString('gang.apiBaseUrl')).toBe('https://ky-z.com/gang-chat/api/v1');
    expect(prefs.getBool('gang.notifications')).toBe(true);
    expect(prefs.getDouble('gang.volume')).toBe(0.5);
    expect(prefs.getInt('gang.count')).toBe(3);
    expect(prefs.getStringList('gang.accounts')).toEqual(['demo', 'alice']);
    expect(prefs.getKeys().sort()).toEqual(['gang.accounts', 'gang.apiBaseUrl', 'gang.count', 'gang.notifications', 'gang.volume']);
  });

  it('writes JSON under the flutter. prefix', async () => {
    const storage = new MemoryStorage();
    const prefs = new WebPreferences(storage);
    await prefs.setString('gang.language', 'zh');
    await prefs.setStringList('gang.loginAccounts', ['demo']);
    await prefs.setBool('gang.flag', false);
    expect(storage.getItem('flutter.gang.language')).toBe('"zh"');
    expect(storage.getItem('flutter.gang.loginAccounts')).toBe('["demo"]');
    expect(storage.getItem('flutter.gang.flag')).toBe('false');
    await prefs.remove('gang.flag');
    expect(storage.getItem('flutter.gang.flag')).toBeNull();
    expect(prefs.containsKey('gang.flag')).toBe(false);
  });
});

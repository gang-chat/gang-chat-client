import type { PreferencesStore, SecureStorage } from '@gang-chat/core/platform/platform';

/**
 * Browser storage, laid out exactly like the Flutter web build's plugins so
 * that moving https://ky-z.com/gang-chat/app/ from Flutter to this build
 * keeps everyone signed in with their settings (same origin, same
 * localStorage):
 *
 * - preferences: `shared_preferences_web` — `flutter.<key>` holding the
 *   JSON-encoded value;
 * - secure storage: `flutter_secure_storage_web` with the default
 *   `WebOptions` — an AES-GCM-256 key (raw, base64) under
 *   `FlutterSecureStorage`, and each value under `FlutterSecureStorage.<key>`
 *   as `base64(iv).base64(ciphertext)`.
 *
 * Like the Flutter plugin this is obfuscation rather than protection (the
 * key sits next to the values); browsers offer nothing stronger.
 */

type PreferenceValue = string | number | boolean | string[];

function localStorageOrNull(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    // Storage blocked (privacy settings, sandboxed iframe).
    return null;
  }
}

export class WebPreferences implements PreferencesStore {
  private static readonly _prefix = 'flutter.';
  private readonly _values = new Map<string, PreferenceValue>();

  constructor(private readonly _storage: Storage | null = localStorageOrNull()) {
    const storage = this._storage;
    if (storage == null) return;
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index);
      if (key == null || !key.startsWith(WebPreferences._prefix)) continue;
      const value = WebPreferences._decode(storage.getItem(key));
      if (value != null) this._values.set(key.substring(WebPreferences._prefix.length), value);
    }
  }

  private static _decode(raw: string | null): PreferenceValue | null {
    if (raw == null) return null;
    try {
      const value: unknown = JSON.parse(raw);
      if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value;
      if (Array.isArray(value)) return value.map((item) => String(item));
      return null;
    } catch {
      return null;
    }
  }

  getString(key: string): string | null {
    const value = this._values.get(key);
    return typeof value === 'string' ? value : null;
  }
  getBool(key: string): boolean | null {
    const value = this._values.get(key);
    return typeof value === 'boolean' ? value : null;
  }
  getInt(key: string): number | null {
    const value = this._values.get(key);
    return typeof value === 'number' ? Math.trunc(value) : null;
  }
  getDouble(key: string): number | null {
    const value = this._values.get(key);
    return typeof value === 'number' ? value : null;
  }
  getStringList(key: string): string[] | null {
    const value = this._values.get(key);
    return Array.isArray(value) ? [...value] : null;
  }
  containsKey(key: string): boolean {
    return this._values.has(key);
  }
  getKeys(): string[] {
    return [...this._values.keys()];
  }

  private async _set(key: string, value: PreferenceValue): Promise<void> {
    this._values.set(key, value);
    try {
      this._storage?.setItem(WebPreferences._prefix + key, JSON.stringify(value));
    } catch {
      // Quota exceeded or storage blocked: keep the value for this session.
    }
  }
  setString(key: string, value: string) {
    return this._set(key, value);
  }
  setBool(key: string, value: boolean) {
    return this._set(key, value);
  }
  setInt(key: string, value: number) {
    return this._set(key, Math.trunc(value));
  }
  setDouble(key: string, value: number) {
    return this._set(key, value);
  }
  setStringList(key: string, value: string[]) {
    return this._set(key, [...value]);
  }
  async remove(key: string): Promise<void> {
    this._values.delete(key);
    try {
      this._storage?.removeItem(WebPreferences._prefix + key);
    } catch {
      // storage blocked
    }
  }
}

function base64Encode(bytes: Uint8Array): string {
  let binary = '';
  for (let index = 0; index < bytes.length; index += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(index, index + 0x8000));
  }
  return btoa(binary);
}

function base64Decode(text: string): Uint8Array<ArrayBuffer> {
  const binary = atob(text);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

export class WebSecureStorage implements SecureStorage {
  private static readonly _publicKey = 'FlutterSecureStorage';
  /** Used where the page is not a secure context (no WebCrypto): this session only. */
  private readonly _memory = new Map<string, string>();
  private _key: Promise<CryptoKey> | null = null;

  constructor(private readonly _storage: Storage | null = localStorageOrNull()) {}

  private get _subtle(): SubtleCrypto | null {
    return typeof crypto !== 'undefined' && crypto.subtle != null ? crypto.subtle : null;
  }

  private _encryptionKey(subtle: SubtleCrypto, storage: Storage): Promise<CryptoKey> {
    this._key ??= (async () => {
      const stored = storage.getItem(WebSecureStorage._publicKey);
      if (stored != null) {
        return subtle.importKey('raw', base64Decode(stored), { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
      }
      const key = await subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt']);
      const raw = new Uint8Array(await subtle.exportKey('raw', key));
      storage.setItem(WebSecureStorage._publicKey, base64Encode(raw));
      return key;
    })();
    // A failed import (corrupt key) must not poison later attempts.
    this._key.catch(() => {
      this._key = null;
    });
    return this._key;
  }

  async read(key: string): Promise<string | null> {
    const subtle = this._subtle;
    const storage = this._storage;
    if (subtle == null || storage == null) return this._memory.get(key) ?? null;
    const encoded = storage.getItem(`${WebSecureStorage._publicKey}.${key}`);
    if (encoded == null) return null;
    try {
      const [iv, cipher] = encoded.split('.');
      if (iv == null || cipher == null) return null;
      const plain = await subtle.decrypt(
        { name: 'AES-GCM', iv: base64Decode(iv) },
        await this._encryptionKey(subtle, storage),
        base64Decode(cipher),
      );
      return new TextDecoder().decode(plain);
    } catch {
      // The Flutter plugin also answered null for values it could not decrypt.
      return null;
    }
  }

  async write(key: string, value: string): Promise<void> {
    const subtle = this._subtle;
    const storage = this._storage;
    if (subtle == null || storage == null) {
      this._memory.set(key, value);
      return;
    }
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const cipher = new Uint8Array(
      await subtle.encrypt({ name: 'AES-GCM', iv }, await this._encryptionKey(subtle, storage), new TextEncoder().encode(value)),
    );
    storage.setItem(`${WebSecureStorage._publicKey}.${key}`, `${base64Encode(iv)}.${base64Encode(cipher)}`);
  }

  async delete(key: string): Promise<void> {
    this._memory.delete(key);
    try {
      this._storage?.removeItem(`${WebSecureStorage._publicKey}.${key}`);
    } catch {
      // storage blocked
    }
  }
}

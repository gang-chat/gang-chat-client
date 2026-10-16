import { HostPlatform } from '../config/host_platform';
import { JsonFormatException, isJsonMap } from '../lib/json';
import { utf8Decode, utf8Encode } from '../platform/http';
import { platform } from '../platform/platform';
import { StickerPack } from './models';

export class StickerPackStore {
  private static readonly _version = 1;
  private static readonly _filePrefix = 'gang-sticker-packs-v1';

  private static readonly _memoryCache = new Map<string, StickerPack[]>();

  async readPersonalPacks(opts: { userId: string; apiBaseUrl: string }): Promise<StickerPack[] | null> {
    const { userId, apiBaseUrl } = opts;
    if (userId.length === 0 || apiBaseUrl.length === 0) return null;
    const account = this._accountKey(userId, apiBaseUrl);
    const memory = StickerPackStore._memoryCache.get(account);
    if (memory != null) return memory;
    try {
      const file = await this._cacheFile(account);
      const raw = utf8Decode(await platform().files.readFile(file));
      if (raw.length === 0) return null;
      const decoded: unknown = JSON.parse(raw);
      if (!isJsonMap(decoded)) return null;
      if (decoded['version'] !== StickerPackStore._version) return null;
      const packs = decoded['packs'];
      if (!Array.isArray(packs)) return null;
      // Like Dart's `cast<Map<String, Object?>>()`, a malformed entry
      // invalidates the whole cache file instead of being skipped.
      const parsed = packs.map((pack: unknown) => {
        if (!isJsonMap(pack)) throw new JsonFormatException('Expected sticker pack object');
        return StickerPack.fromJson(pack);
      });
      StickerPackStore._memoryCache.set(account, parsed);
      return parsed;
    } catch {
      return null;
    }
  }

  async writePersonalPacks(opts: { userId: string; apiBaseUrl: string; packs: StickerPack[] }): Promise<void> {
    const { userId, apiBaseUrl, packs } = opts;
    if (userId.length === 0 || apiBaseUrl.length === 0) return;
    const account = this._accountKey(userId, apiBaseUrl);
    StickerPackStore._memoryCache.set(account, packs);
    try {
      const files = platform().files;
      const file = await this._cacheFile(account);
      await files.ensureDirectory(_parentPath(file));
      const tmp = `${file}.tmp`;
      await files.writeFile(
        tmp,
        utf8Encode(
          JSON.stringify({
            version: StickerPackStore._version,
            saved_at: new Date().toISOString(),
            packs: packs.map((pack) => pack.toJson()),
          }),
        ),
      );
      if (await files.exists(file)) await files.deleteFile(file);
      await files.rename(tmp, file);
    } catch {
      // The cache is a convenience path. Network data should remain usable even
      // when the platform cache directory is unavailable.
    }
  }

  async clearPersonalPacks(opts: { userId: string; apiBaseUrl: string }): Promise<void> {
    const { userId, apiBaseUrl } = opts;
    if (userId.length === 0 || apiBaseUrl.length === 0) return;
    const account = this._accountKey(userId, apiBaseUrl);
    StickerPackStore._memoryCache.delete(account);
    try {
      const files = platform().files;
      const file = await this._cacheFile(account);
      if (await files.exists(file)) await files.deleteFile(file);
    } catch {
      // Best effort, like the Dart `catch (_) {}`.
    }
  }

  private _accountKey(userId: string, apiBaseUrl: string): string {
    return _base64UrlEncode(utf8Encode(`${apiBaseUrl}\n${userId}`)).replaceAll('=', '');
  }

  /** Absolute path of the account's cache file (Dart returned a `File`). */
  private async _cacheFile(account: string): Promise<string> {
    const dir = (await platform().files.directories()).applicationCache;
    const separator = HostPlatform.pathSeparator;
    return `${dir}${separator}sticker-packs${separator}${StickerPackStore._filePrefix}-${account}.json`;
  }
}

/** Dart `file.parent.path` for a path built by `_cacheFile`. */
function _parentPath(path: string): string {
  const index = path.lastIndexOf(HostPlatform.pathSeparator);
  return index <= 0 ? path : path.substring(0, index);
}

const _base64UrlAlphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

/** Dart `base64Url.encode` (padded; the caller strips `=`). */
function _base64UrlEncode(bytes: Uint8Array): string {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i]!;
    const b1 = i + 1 < bytes.length ? bytes[i + 1]! : 0;
    const b2 = i + 2 < bytes.length ? bytes[i + 2]! : 0;
    const triple = (b0 << 16) | (b1 << 8) | b2;
    out += _base64UrlAlphabet[(triple >> 18) & 63]!;
    out += _base64UrlAlphabet[(triple >> 12) & 63]!;
    out += i + 1 < bytes.length ? _base64UrlAlphabet[(triple >> 6) & 63]! : '=';
    out += i + 2 < bytes.length ? _base64UrlAlphabet[triple & 63]! : '=';
  }
  return out;
}

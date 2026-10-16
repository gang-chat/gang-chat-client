import { ZipDecoder } from '../lib/zip';

export const stickerImageExtensions: ReadonlySet<string> = new Set(['png', 'jpg', 'jpeg', 'webp', 'gif']);
export const maxStickerUploadsPerBatch = 500;
export const maxStickerImageBytes = 25 * 1024 * 1024;

export type StickerBytesReader = () => Promise<Uint8Array>;
export type StickerImageDecoder = (bytes: Uint8Array) => Promise<StickerImageDimensions>;

export class StickerUploadSource {
  constructor(opts: { filename: string; readBytes: StickerBytesReader }) {
    this.filename = opts.filename;
    this.readBytes = opts.readBytes;
  }

  readonly filename: string;
  readonly readBytes: StickerBytesReader;
}

export class StickerUploadItem {
  constructor(opts: { filename: string; bytes: Uint8Array }) {
    this.filename = opts.filename;
    this.bytes = opts.bytes;
  }

  readonly filename: string;
  readonly bytes: Uint8Array;
}

export class StickerImageDimensions {
  constructor(opts: { width: number; height: number }) {
    this.width = opts.width;
    this.height = opts.height;
  }

  readonly width: number;
  readonly height: number;

  toString(): string {
    return `${this.width}x${this.height}`;
  }
}

export function stickerPackRequiresServerMessage(): string {
  return '表情包需要登录后从服务端读取';
}

export function stickerPickerOpenFailureMessage(error: unknown): string {
  return '无法打开文件选择器';
}

export function stickerNoUploadableImagesMessage(): string {
  return '没有找到可上传的图片';
}

export async function stickerUploadItemsFromFiles(
  files: Iterable<StickerUploadSource>,
  opts: { decodeImageDimensions: StickerImageDecoder },
): Promise<StickerUploadItem[]> {
  const { decodeImageDimensions } = opts;
  const items: StickerUploadItem[] = [];
  for (const file of files) {
    const filename = _basename(file.filename);
    if (_isZipFilename(filename)) {
      items.push(...(await _stickerUploadItemsFromZip(file, { decodeImageDimensions })));
    } else if (_isStickerImageFilename(filename)) {
      items.push(await _stickerUploadItemFromBytes(filename, await file.readBytes(), { decodeImageDimensions }));
    }
    if (items.length > maxStickerUploadsPerBatch) {
      throw new Error(`一次最多上传 ${maxStickerUploadsPerBatch} 个表情`);
    }
  }
  return items;
}

export function stickerUploadFilename(originalName: string, index: number): string {
  const cleaned = originalName
    .trim()
    .replace(/[^A-Za-z0-9._-]+/g, '-')
    .replace(/-+/g, '-');
  const extensionMatch = /\.([A-Za-z0-9]+)$/.exec(cleaned);
  const extension = extensionMatch == null ? 'png' : extensionMatch[1]!.toLowerCase();
  const stem = cleaned.replace(/\.[A-Za-z0-9]+$/, '');
  const safeStem = stem.length === 0 ? 'sticker' : stem;
  return `${safeStem}-${Date.now()}-${index}.${extension}`;
}

export function stickerNameFromFilename(filename: string): string {
  const stem = filename.trim().replace(/\.[^.]+$/, '').trim();
  if (stem.length === 0) return 'sticker';
  // Code points, like Dart's `runes.take(32)`.
  const chars = Array.from(stem).slice(0, 32).join('');
  return chars.length === 0 ? 'sticker' : chars;
}

async function _stickerUploadItemsFromZip(
  file: StickerUploadSource,
  opts: { decodeImageDimensions: StickerImageDecoder },
): Promise<StickerUploadItem[]> {
  const bytes = await file.readBytes();
  if (bytes.length === 0) throw new Error(`${file.filename} 文件为空`);

  // PORT: package:archive's ZipDecoder is replaced by the pure-TS reader in
  // lib/zip (stored + deflate entries), which also runs on React Native.
  const archive = new ZipDecoder().decodeBytes(bytes);
  const items: StickerUploadItem[] = [];
  for (const entry of archive.files) {
    const entryName = entry.name;
    if (!entry.isFile || _isIgnoredZipEntry(entryName) || !_isStickerImageFilename(entryName)) {
      continue;
    }
    if (entry.size > maxStickerImageBytes) {
      throw new Error(`${_basename(entryName)} 超过 25MB`);
    }
    const content = entry.readBytes();
    if (content == null) continue;
    items.push(await _stickerUploadItemFromBytes(_basename(entryName), content, opts));
    if (items.length > maxStickerUploadsPerBatch) {
      throw new Error(`一次最多上传 ${maxStickerUploadsPerBatch} 个表情`);
    }
  }
  return items;
}

async function _stickerUploadItemFromBytes(
  filename: string,
  bytes: Uint8Array,
  opts: { decodeImageDimensions: StickerImageDecoder },
): Promise<StickerUploadItem> {
  if (bytes.length === 0) throw new Error(`${filename} 文件为空`);
  if (bytes.length > maxStickerImageBytes) {
    throw new Error(`${filename} 超过 25MB`);
  }
  try {
    await opts.decodeImageDimensions(bytes);
  } catch {
    throw new Error(`${filename} 不是可识别的图片`);
  }
  return new StickerUploadItem({ filename, bytes });
}

function _isStickerImageFilename(filename: string): boolean {
  return stickerImageExtensions.has(_extensionOf(filename));
}

function _isZipFilename(filename: string): boolean {
  return _extensionOf(filename) === 'zip';
}

function _isIgnoredZipEntry(name: string): boolean {
  const normalized = name.replaceAll('\\', '/');
  const parts = normalized.split('/').filter((part) => part.length > 0);
  if (parts.some((part) => part === '__MACOSX')) return true;
  return _basename(normalized).startsWith('.');
}

function _basename(path: string): string {
  const normalized = path.replaceAll('\\', '/');
  const parts = normalized.split('/').filter((part) => part.length > 0);
  const name = parts.length === 0 ? '' : parts[parts.length - 1]!;
  return name.length === 0 ? 'sticker' : name;
}

function _extensionOf(filename: string): string {
  const name = _basename(filename).toLowerCase();
  const index = name.lastIndexOf('.');
  if (index < 0 || index === name.length - 1) return '';
  return name.substring(index + 1);
}

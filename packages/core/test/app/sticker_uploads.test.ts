import { constants, deflateRawSync } from 'node:zlib';

import { describe, expect, it } from 'vitest';

import {
  StickerImageDimensions,
  StickerUploadSource,
  maxStickerImageBytes,
  stickerNameFromFilename,
  stickerNoUploadableImagesMessage,
  stickerPackRequiresServerMessage,
  stickerPickerOpenFailureMessage,
  stickerUploadFilename,
  stickerUploadItemsFromFiles,
} from '../../src/app/sticker_uploads';
import { ZipDecoder, inflateRaw } from '../../src/lib/zip';

const _pngBytes = Uint8Array.from(
  Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
    'base64',
  ),
);

const _decodeOk = async () => new StickerImageDimensions({ width: 1, height: 1 });

describe('sticker uploads', () => {
  it('stickerUploadItemsFromFiles extracts valid images from zip files', async () => {
    const zipBytes = _zip([
      { name: 'stickers/hello.png', data: _pngBytes, method: 'deflate' },
      { name: '__MACOSX/ignored.png', data: _pngBytes },
      { name: 'notes.txt', data: new TextEncoder().encode('notes') },
    ]);

    const items = await stickerUploadItemsFromFiles(
      [new StickerUploadSource({ filename: 'pack.zip', readBytes: async () => zipBytes })],
      { decodeImageDimensions: _decodeOk },
    );

    expect(items).toHaveLength(1);
    expect(items[0]!.filename).toBe('hello.png');
    expect(Array.from(items[0]!.bytes)).toEqual(Array.from(_pngBytes));
  });

  it('skips directories and hidden zip entries, keeps loose images', async () => {
    const zipBytes = _zip([
      { name: 'stickers/', data: new Uint8Array(0) },
      { name: 'stickers/.hidden.png', data: _pngBytes },
      { name: 'stickers\\wave.GIF', data: _pngBytes, method: 'deflate' },
    ]);

    const items = await stickerUploadItemsFromFiles(
      [
        new StickerUploadSource({ filename: 'C:\\uploads\\pack.ZIP', readBytes: async () => zipBytes }),
        new StickerUploadSource({ filename: '/tmp/loose.webp', readBytes: async () => _pngBytes }),
        new StickerUploadSource({ filename: 'readme.md', readBytes: async () => _pngBytes }),
      ],
      { decodeImageDimensions: _decodeOk },
    );

    expect(items.map((item) => item.filename)).toEqual(['wave.GIF', 'loose.webp']);
  });

  it('reports empty, undecodable and oversized files with Chinese copy', async () => {
    await expect(
      stickerUploadItemsFromFiles([new StickerUploadSource({ filename: 'a.png', readBytes: async () => new Uint8Array(0) })], {
        decodeImageDimensions: _decodeOk,
      }),
    ).rejects.toThrow('a.png 文件为空');
    await expect(
      stickerUploadItemsFromFiles([new StickerUploadSource({ filename: 'pack.zip', readBytes: async () => new Uint8Array(0) })], {
        decodeImageDimensions: _decodeOk,
      }),
    ).rejects.toThrow('pack.zip 文件为空');
    await expect(
      stickerUploadItemsFromFiles([new StickerUploadSource({ filename: 'b.png', readBytes: async () => _pngBytes })], {
        decodeImageDimensions: async () => {
          throw new Error('bad image');
        },
      }),
    ).rejects.toThrow('b.png 不是可识别的图片');
    await expect(
      stickerUploadItemsFromFiles(
        [new StickerUploadSource({ filename: 'c.png', readBytes: async () => new Uint8Array(maxStickerImageBytes + 1) })],
        { decodeImageDimensions: _decodeOk },
      ),
    ).rejects.toThrow('c.png 超过 25MB');
  });

  it('caps one batch at 500 stickers', async () => {
    const sources = Array.from(
      { length: 501 },
      (_, index) => new StickerUploadSource({ filename: `s${index}.png`, readBytes: async () => _pngBytes }),
    );

    await expect(stickerUploadItemsFromFiles(sources, { decodeImageDimensions: _decodeOk })).rejects.toThrow(
      '一次最多上传 500 个表情',
    );
  });

  it('sticker upload helpers produce stable names for UI callers', () => {
    expect(stickerNameFromFilename('hello-world.webp')).toBe('hello-world');
    expect(stickerNameFromFilename('.png')).toBe('sticker');
    expect(stickerNameFromFilename(`${'表'.repeat(40)}.png`)).toBe('表'.repeat(32));

    const filename = stickerUploadFilename('hello world.WEBP', 3);
    expect(filename.startsWith('hello-world-')).toBe(true);
    expect(filename.endsWith('-3.webp')).toBe(true);
    expect(stickerUploadFilename('.png', 0)).toMatch(/^sticker-\d+-0\.png$/);
    expect(stickerUploadFilename('表情', 1)).toMatch(/^--\d+-1\.png$/);
  });

  it('sticker upload error copy stays outside UI', () => {
    expect(stickerPackRequiresServerMessage()).toBe('表情包需要登录后从服务端读取');
    expect(stickerPickerOpenFailureMessage('denied')).toBe('无法打开文件选择器');
    expect(stickerNoUploadableImagesMessage()).toBe('没有找到可上传的图片');
  });
});

describe('zip decoder', () => {
  it('inflates stored, fixed and dynamic deflate blocks', () => {
    const text = new TextEncoder().encode('gang chat stickers '.repeat(4000));
    const random = new Uint8Array(70000);
    let seed = 7;
    for (let i = 0; i < random.length; i += 1) {
      seed = (seed * 1103515245 + 12345) >>> 0;
      random[i] = seed >>> 24;
    }

    for (const input of [text, random, _pngBytes, new Uint8Array(0)]) {
      expect(Buffer.from(inflateRaw(Uint8Array.from(deflateRawSync(input))))).toEqual(Buffer.from(input));
      expect(Buffer.from(inflateRaw(Uint8Array.from(deflateRawSync(input, { level: 0 }))))).toEqual(Buffer.from(input));
      expect(
        Buffer.from(inflateRaw(Uint8Array.from(deflateRawSync(input, { strategy: constants.Z_FIXED })))),
      ).toEqual(Buffer.from(input));
    }
  });

  it('lists entries with sizes and file flags', () => {
    const archive = new ZipDecoder().decodeBytes(
      _zip([
        { name: 'dir/', data: new Uint8Array(0) },
        { name: 'dir/a.png', data: _pngBytes, method: 'deflate' },
      ]),
    );

    expect(archive.files.map((file) => [file.name, file.isFile, file.size])).toEqual([
      ['dir/', false, 0],
      ['dir/a.png', true, _pngBytes.length],
    ]);
    expect(Array.from(archive.files[1]!.readBytes()!)).toEqual(Array.from(_pngBytes));
  });

  it('rejects data that is not a zip archive', () => {
    expect(() => new ZipDecoder().decodeBytes(_pngBytes)).toThrow('End of Central Directory');
  });
});

interface ZipFixtureEntry {
  name: string;
  data: Uint8Array;
  method?: 'store' | 'deflate';
}

/** Minimal ZIP writer for fixtures (stands in for package:archive's ZipEncoder). */
function _zip(entries: ZipFixtureEntry[]): Uint8Array {
  const encoder = new TextEncoder();
  const locals: Buffer[] = [];
  const centrals: Buffer[] = [];
  let offset = 0;
  for (const entry of entries) {
    const name = Buffer.from(encoder.encode(entry.name));
    const deflate = entry.method === 'deflate';
    const payload = deflate ? deflateRawSync(entry.data) : Buffer.from(entry.data);
    const crc = _crc32(entry.data);

    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(deflate ? 8 : 0, 8);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(payload.length, 18);
    local.writeUInt32LE(entry.data.length, 22);
    local.writeUInt16LE(name.length, 26);
    locals.push(local, name, payload);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(deflate ? 8 : 0, 10);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(payload.length, 20);
    central.writeUInt32LE(entry.data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt32LE(offset, 42);
    centrals.push(central, name);

    offset += local.length + name.length + payload.length;
  }
  const centralSize = centrals.reduce((sum, part) => sum + part.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralSize, 12);
  end.writeUInt32LE(offset, 16);
  return Uint8Array.from(Buffer.concat([...locals, ...centrals, end]));
}

function _crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) crc = crc & 1 ? (crc >>> 1) ^ 0xedb88320 : crc >>> 1;
  }
  return (crc ^ 0xffffffff) >>> 0;
}

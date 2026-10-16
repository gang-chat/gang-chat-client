import { app, net, protocol } from 'electron';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { Readable } from 'node:stream';

import { kMediaScheme } from '../shared/ipc';

/**
 * `gang-media://cache/?u=<url>` serves remote images, stickers and avatars
 * from a disk cache, downloading them on first use.
 *
 * The Flutter client kept its own disk cache (`MediaCacheController` +
 * path_provider) and drew images from cached files; this keeps the same
 * behaviour for every `<Image>` the shared UI renders, without the renderer
 * holding image bytes in JS memory.
 */

const maxCacheBytes = 512 * 1024 * 1024;

const cacheDir = () => path.join(app.getPath('userData'), 'media-cache');

interface Meta {
  contentType: string | null;
  size: number;
}

function keyFor(url: string): string {
  return createHash('sha256').update(url).digest('hex');
}

const inflight = new Map<string, Promise<void>>();

async function download(url: string, file: string, metaFile: string): Promise<void> {
  const response = await net.fetch(url, { cache: 'no-store' });
  if (!response.ok || !response.body) {
    throw new Error(`media download failed: ${response.status}`);
  }
  const temp = `${file}.${process.pid}.${Date.now()}.part`;
  await fsp.mkdir(path.dirname(file), { recursive: true });
  const out = fs.createWriteStream(temp);
  let size = 0;
  try {
    for await (const chunk of Readable.fromWeb(response.body as import('node:stream/web').ReadableStream<Uint8Array>)) {
      size += (chunk as Buffer).length;
      if (!out.write(chunk)) await new Promise((r) => out.once('drain', r));
    }
    await new Promise<void>((resolve, reject) => out.end((error?: Error | null) => (error ? reject(error) : resolve())));
    await fsp.rename(temp, file);
    const meta: Meta = { contentType: response.headers.get('content-type'), size };
    await fsp.writeFile(metaFile, JSON.stringify(meta));
  } catch (error) {
    out.destroy();
    await fsp.rm(temp, { force: true });
    throw error;
  }
}

async function ensureCached(url: string): Promise<{ file: string; meta: Meta }> {
  const key = keyFor(url);
  const dir = path.join(cacheDir(), key.slice(0, 2));
  const file = path.join(dir, key);
  const metaFile = `${file}.json`;
  try {
    const meta = JSON.parse(await fsp.readFile(metaFile, 'utf8')) as Meta;
    await fsp.access(file);
    // Touch so the LRU trim keeps recently used entries.
    const now = new Date();
    fsp.utimes(file, now, now).catch(() => {});
    return { file, meta };
  } catch {
    // not cached yet
  }
  let job = inflight.get(key);
  if (!job) {
    job = download(url, file, metaFile).finally(() => inflight.delete(key));
    inflight.set(key, job);
    scheduleTrim();
  }
  await job;
  const meta = JSON.parse(await fsp.readFile(metaFile, 'utf8')) as Meta;
  return { file, meta };
}

let trimTimer: NodeJS.Timeout | null = null;

function scheduleTrim() {
  if (trimTimer) return;
  trimTimer = setTimeout(() => {
    trimTimer = null;
    void trimCache();
  }, 30_000);
}

async function trimCache(): Promise<void> {
  const entries: Array<{ file: string; size: number; mtime: number }> = [];
  let total = 0;
  for (const bucket of await fsp.readdir(cacheDir()).catch(() => [] as string[])) {
    const bucketDir = path.join(cacheDir(), bucket);
    for (const name of await fsp.readdir(bucketDir).catch(() => [] as string[])) {
      if (name.endsWith('.json') || name.endsWith('.part')) continue;
      const file = path.join(bucketDir, name);
      const stat = await fsp.stat(file).catch(() => null);
      if (!stat) continue;
      entries.push({ file, size: stat.size, mtime: stat.mtimeMs });
      total += stat.size;
    }
  }
  if (total <= maxCacheBytes) return;
  entries.sort((a, b) => a.mtime - b.mtime);
  for (const entry of entries) {
    if (total <= maxCacheBytes * 0.8) break;
    await fsp.rm(entry.file, { force: true });
    await fsp.rm(`${entry.file}.json`, { force: true });
    total -= entry.size;
  }
}

/** Must run before `app.ready`. */
export function registerMediaScheme(): void {
  protocol.registerSchemesAsPrivileged([
    {
      scheme: kMediaScheme,
      privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true },
    },
  ]);
}

export function handleMediaProtocol(): void {
  protocol.handle(kMediaScheme, async (request) => {
    const target = new URL(request.url).searchParams.get('u');
    if (!target || !/^https?:\/\//i.test(target)) {
      return new Response('bad media url', { status: 400 });
    }
    try {
      const { file, meta } = await ensureCached(target);
      const stat = await fsp.stat(file);
      const headers = new Headers({
        'content-type': meta.contentType ?? 'application/octet-stream',
        'access-control-allow-origin': '*',
        'cache-control': 'max-age=31536000, immutable',
        'accept-ranges': 'bytes',
      });
      const range = request.headers.get('range');
      const match = range ? /bytes=(\d*)-(\d*)/.exec(range) : null;
      if (match) {
        const start = match[1] ? Number(match[1]) : 0;
        const end = match[2] ? Math.min(Number(match[2]), stat.size - 1) : stat.size - 1;
        headers.set('content-range', `bytes ${start}-${end}/${stat.size}`);
        headers.set('content-length', String(end - start + 1));
        const stream = fs.createReadStream(file, { start, end });
        return new Response(Readable.toWeb(stream) as ReadableStream, { status: 206, headers });
      }
      headers.set('content-length', String(stat.size));
      return new Response(Readable.toWeb(fs.createReadStream(file)) as ReadableStream, { status: 200, headers });
    } catch (error) {
      return new Response(String(error), { status: 502 });
    }
  });
}

export async function clearMediaCache(): Promise<void> {
  await fsp.rm(cacheDir(), { recursive: true, force: true });
}

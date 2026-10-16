// Generated media so seeded images, stickers, voice clips and files render
// without network access: a tiny PNG encoder, a WAV synthesizer, a store-only
// ZIP writer and a one-page PDF. Also MIME sniffing and image-size detection
// for uploaded files.
import { deflateSync } from 'node:zlib';

// ---------------------------------------------------------------------------
// CRC32 (PNG chunks and ZIP entries)

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

export function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

// ---------------------------------------------------------------------------
// PNG

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

function pngChunk(type, data) {
  const header = Buffer.alloc(8);
  header.writeUInt32BE(data.length, 0);
  header.write(type, 4, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([header.subarray(4), data])), 0);
  return Buffer.concat([header, data, crc]);
}

/** Encodes an RGBA image; `shade(x, y)` returns `[r, g, b, a]` (0-255). */
export function encodePng(width, height, shade) {
  const stride = width * 4 + 1;
  const raw = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    raw[y * stride] = 0; // filter: none
    for (let x = 0; x < width; x++) {
      const [r, g, b, a = 255] = shade(x, y);
      const offset = y * stride + 1 + x * 4;
      raw[offset] = r;
      raw[offset + 1] = g;
      raw[offset + 2] = b;
      raw[offset + 3] = a;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  return Buffer.concat([
    PNG_SIGNATURE,
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', deflateSync(raw, { level: 6 })),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

export function hexToRgb(hex) {
  const value = Number.parseInt(hex.replace('#', ''), 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

const mix = (a, b, t) => Math.round(a + (b - a) * t);
const smooth = (edge0, edge1, x) => {
  const t = Math.min(1, Math.max(0, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
};

/** A diagonal gradient "photo" with a few soft bokeh circles. */
export function photoPng({ width, height, from, to, seed = 1 }) {
  const a = hexToRgb(from);
  const b = hexToRgb(to);
  const circles = Array.from({ length: 5 }, (_, i) => ({
    x: ((seed * 37 + i * 211) % 100) / 100,
    y: ((seed * 53 + i * 127) % 100) / 100,
    r: 0.08 + (((seed + i) * 17) % 12) / 100,
  }));
  return encodePng(width, height, (x, y) => {
    const u = x / width;
    const v = y / height;
    const t = (u + v) / 2;
    let light = 0;
    for (const circle of circles) {
      const dx = (u - circle.x) * (width / height);
      const dy = v - circle.y;
      const d = Math.sqrt(dx * dx + dy * dy);
      light += (1 - smooth(circle.r * 0.6, circle.r, d)) * 0.35;
    }
    light = Math.min(0.6, light);
    return [mix(mix(a[0], b[0], t), 255, light), mix(mix(a[1], b[1], t), 255, light), mix(mix(a[2], b[2], t), 255, light)];
  });
}

/** A round avatar-style image: radial gradient of `color`, transparent corners. */
export function avatarPng({ size = 128, color }) {
  const [r, g, b] = hexToRgb(color);
  const c = size / 2;
  return encodePng(size, size, (x, y) => {
    const d = Math.hypot(x + 0.5 - c, y + 0.5 - c) / c;
    const alpha = Math.round(255 * (1 - smooth(0.96, 1, d)));
    const lift = 0.35 * (1 - d);
    return [mix(r, 255, lift), mix(g, 255, lift), mix(b, 255, lift), alpha];
  });
}

/** A simple emoji-like face sticker. `mood`: smile | grin | wow | meh | wink. */
export function stickerPng({ size = 160, color, mood = 'smile' }) {
  const [r, g, b] = hexToRgb(color);
  const c = size / 2;
  const radius = size * 0.46;
  const eyeY = c - size * 0.1;
  const eyeDx = size * 0.15;
  const eyeR = size * 0.055;
  return encodePng(size, size, (x, y) => {
    const px = x + 0.5;
    const py = y + 0.5;
    const d = Math.hypot(px - c, py - c);
    if (d > radius + 1.5) return [0, 0, 0, 0];
    const edge = 1 - smooth(radius - 1.5, radius + 1.5, d);
    const ink = [40, 32, 48, Math.round(255 * edge)];
    if (d > radius - size * 0.025) return ink; // outline
    const leftEye = Math.hypot(px - (c - eyeDx), py - eyeY) < eyeR;
    const rightEye =
      mood === 'wink'
        ? Math.abs(py - eyeY) < size * 0.015 && Math.abs(px - (c + eyeDx)) < eyeR
        : Math.hypot(px - (c + eyeDx), py - eyeY) < eyeR;
    if (leftEye || rightEye) return ink;
    const mouthY = c + size * 0.14;
    const mx = (px - c) / (size * 0.22);
    let mouth = false;
    if (mood === 'wow') mouth = Math.hypot(px - c, py - mouthY) < size * 0.07;
    else if (mood === 'meh') mouth = Math.abs(mx) < 1 && Math.abs(py - mouthY) < size * 0.018;
    else if (Math.abs(mx) < 1) {
      const curve = mouthY - size * 0.05 + (1 - mx * mx) * size * (mood === 'grin' ? 0.1 : 0.07);
      mouth = mood === 'grin' ? py > mouthY - size * 0.05 && py < curve : Math.abs(py - curve) < size * 0.02;
    }
    if (mouth) return ink;
    const shine = 0.25 * (1 - Math.min(1, Math.hypot(px - c * 0.75, py - c * 0.7) / radius));
    return [mix(r, 255, shine), mix(g, 255, shine), mix(b, 255, shine), 255];
  });
}

// ---------------------------------------------------------------------------
// WAV (voice messages and music previews)

/** 16-bit mono PCM. `style: 'voice'` adds a syllable envelope; `'music'` an arpeggio. */
export function encodeWav({ durationMs, sampleRate = 16_000, pitch = 220, style = 'voice' }) {
  const samples = Math.max(1, Math.round((durationMs / 1000) * sampleRate));
  const data = Buffer.alloc(samples * 2);
  const notes = [1, 1.25, 1.5, 2];
  for (let i = 0; i < samples; i++) {
    const t = i / sampleRate;
    let value;
    if (style === 'music') {
      const note = notes[Math.floor(t * 4) % notes.length];
      const local = (t * 4) % 1;
      value = Math.sin(2 * Math.PI * pitch * note * t) * Math.exp(-3 * local) * 0.5;
    } else {
      const syllable = Math.abs(Math.sin(Math.PI * 3.2 * t)) ** 0.6;
      const vibrato = 1 + 0.03 * Math.sin(2 * Math.PI * 5 * t);
      value =
        (Math.sin(2 * Math.PI * pitch * vibrato * t) * 0.6 + Math.sin(2 * Math.PI * pitch * 2.01 * t) * 0.25) *
        syllable *
        0.6;
    }
    const fade = Math.min(1, t * 20, (samples - i) / (sampleRate / 20));
    data.writeInt16LE(Math.round(value * fade * 32767), i * 2);
  }
  const header = Buffer.alloc(44);
  header.write('RIFF', 0, 'ascii');
  header.writeUInt32LE(36 + data.length, 4);
  header.write('WAVE', 8, 'ascii');
  header.write('fmt ', 12, 'ascii');
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20); // PCM
  header.writeUInt16LE(1, 22); // mono
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(sampleRate * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write('data', 36, 'ascii');
  header.writeUInt32LE(data.length, 40);
  return Buffer.concat([header, data]);
}

// ---------------------------------------------------------------------------
// ZIP (store only) and PDF

/** Builds an uncompressed ZIP archive from `[{ name, data }]`. */
export function buildZip(entries) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const entry of entries) {
    const name = Buffer.from(entry.name, 'utf8');
    const data = Buffer.isBuffer(entry.data) ? entry.data : Buffer.from(entry.data);
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6); // UTF-8 names
    local.writeUInt16LE(0, 8); // stored
    local.writeUInt16LE(0, 10);
    local.writeUInt16LE(0x21, 12); // 1980-01-01
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(0, 10);
    central.writeUInt16LE(0, 12);
    central.writeUInt16LE(0x21, 14);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(data.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt32LE(offset, 42);
    locals.push(local, name, data);
    centrals.push(central, name);
    offset += local.length + name.length + data.length;
  }
  const centralSize = centrals.reduce((sum, part) => sum + part.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralSize, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, ...centrals, end]);
}

/** A valid single-page PDF showing `lines` (ASCII) in Helvetica. */
export function buildPdf(lines) {
  const escape = (text) => text.replace(/[\\()]/g, (ch) => `\\${ch}`);
  const content = ['BT', '/F1 22 Tf', '72 770 Td', '28 TL', ...lines.map((line) => `(${escape(line)}) Tj T*`), 'ET'].join(
    '\n',
  );
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let pdf = '%PDF-1.4\n';
  const offsets = [];
  objects.forEach((body, index) => {
    offsets.push(Buffer.byteLength(pdf));
    pdf += `${index + 1} 0 obj\n${body}\nendobj\n`;
  });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets) pdf += `${String(offset).padStart(10, '0')} 00000 n \n`;
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(pdf, 'latin1');
}

// ---------------------------------------------------------------------------
// MIME sniffing and image dimensions for uploads

const EXTENSION_MIME = {
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  webp: 'image/webp',
  bmp: 'image/bmp',
  svg: 'image/svg+xml',
  heic: 'image/heic',
  m4a: 'audio/mp4',
  mp3: 'audio/mpeg',
  wav: 'audio/wav',
  ogg: 'audio/ogg',
  opus: 'audio/ogg',
  aac: 'audio/aac',
  mp4: 'video/mp4',
  mov: 'video/quicktime',
  webm: 'video/webm',
  pdf: 'application/pdf',
  zip: 'application/zip',
  txt: 'text/plain',
  md: 'text/markdown',
  csv: 'text/csv',
  json: 'application/json',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
};

export function mimeFromFilename(filename) {
  const ext = String(filename ?? '').toLowerCase().split('.').pop();
  return EXTENSION_MIME[ext] ?? null;
}

function sniffMagic(buffer) {
  const ascii = (start, end) => buffer.subarray(start, end).toString('latin1');
  if (buffer.length >= 8 && buffer.subarray(0, 8).equals(PNG_SIGNATURE)) return 'image/png';
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) return 'image/jpeg';
  if (ascii(0, 4) === 'GIF8') return 'image/gif';
  if (ascii(0, 4) === 'RIFF' && ascii(8, 12) === 'WEBP') return 'image/webp';
  if (ascii(0, 4) === 'RIFF' && ascii(8, 12) === 'WAVE') return 'audio/wav';
  if (ascii(0, 4) === '%PDF') return 'application/pdf';
  if (ascii(0, 4) === 'PK\x03\x04') return null; // zip container: trust the extension (docx, xlsx, ...)
  if (ascii(0, 4) === 'OggS') return 'audio/ogg';
  if (ascii(0, 3) === 'ID3') return 'audio/mpeg';
  if (ascii(4, 8) === 'ftyp') return /^(M4A |M4B )/.test(ascii(8, 12)) ? 'audio/mp4' : null;
  return null;
}

/**
 * Picks the stored MIME type: the part's declared type unless it is the
 * generic octet-stream Dart sends by default, then magic bytes, then the
 * file extension.
 */
export function resolveMimeType({ declared, filename, data }) {
  const normalized = String(declared ?? '').split(';')[0].trim().toLowerCase();
  if (normalized && normalized !== 'application/octet-stream') return normalized;
  return sniffMagic(data) ?? mimeFromFilename(filename) ?? 'application/octet-stream';
}

/** Returns `{ width, height }` for PNG, GIF, JPEG and WebP data, else null. */
export function imageSize(buffer, mimeType) {
  try {
    if (mimeType === 'image/png' && buffer.length >= 24) {
      return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) };
    }
    if (mimeType === 'image/gif' && buffer.length >= 10) {
      return { width: buffer.readUInt16LE(6), height: buffer.readUInt16LE(8) };
    }
    if (mimeType === 'image/jpeg') return jpegSize(buffer);
    if (mimeType === 'image/webp') return webpSize(buffer);
  } catch {
    // Truncated or malformed image: report no dimensions.
  }
  return null;
}

function jpegSize(buffer) {
  let offset = 2;
  while (offset + 9 < buffer.length) {
    if (buffer[offset] !== 0xff) return null;
    const marker = buffer[offset + 1];
    const length = buffer.readUInt16BE(offset + 2);
    const isFrame = marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker);
    if (isFrame) return { height: buffer.readUInt16BE(offset + 5), width: buffer.readUInt16BE(offset + 7) };
    offset += 2 + length;
  }
  return null;
}

function webpSize(buffer) {
  const chunk = buffer.subarray(12, 16).toString('latin1');
  if (chunk === 'VP8X') return { width: 1 + buffer.readUIntLE(24, 3), height: 1 + buffer.readUIntLE(27, 3) };
  if (chunk === 'VP8 ') return { width: buffer.readUInt16LE(26) & 0x3fff, height: buffer.readUInt16LE(28) & 0x3fff };
  if (chunk === 'VP8L') {
    const bits = buffer.readUInt32LE(21);
    return { width: (bits & 0x3fff) + 1, height: ((bits >> 14) & 0x3fff) + 1 };
  }
  return null;
}

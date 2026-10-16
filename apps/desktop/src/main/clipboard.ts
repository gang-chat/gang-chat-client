import { clipboard, ipcMain, nativeImage } from 'electron';
import { execFile } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { IpcChannels } from '../shared/ipc';

/**
 * System clipboard (Flutter `Clipboard` + the native Windows/macOS file-path
 * channels of `ClipboardService`).
 */

function readWindowsFilePaths(): string[] {
  // CF_HDROP is exposed to Electron as the "FileNameW" format: UTF-16LE,
  // NUL-separated. Explorer only ever puts one path into FileNameW, so fall
  // back to an empty list when it is absent.
  const buffer = clipboard.readBuffer('FileNameW');
  if (!buffer || buffer.length === 0) return [];
  return buffer
    .toString('utf16le')
    .split('\u0000')
    .map((p) => p.trim())
    .filter((p) => p.length > 0);
}

function readMacFilePaths(): string[] {
  const plist = clipboard.read('NSFilenamesPboardType');
  if (plist) {
    const paths = [...plist.matchAll(/<string>([^<]+)<\/string>/g)].map((m) => decodeXml(m[1]!));
    if (paths.length > 0) return paths;
  }
  const url = clipboard.read('public.file-url');
  if (url && url.startsWith('file://')) {
    try {
      return [fileURLToPath(url)];
    } catch {
      return [];
    }
  }
  return [];
}

function decodeXml(value: string): string {
  return value
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

function encodeXml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function writeMacFilePaths(paths: string[]): boolean {
  const plist =
    '<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n' +
    `<plist version="1.0"><array>${paths.map((p) => `<string>${encodeXml(p)}</string>`).join('')}</array></plist>`;
  clipboard.writeBuffer('NSFilenamesPboardType', Buffer.from(plist, 'utf8'));
  if (paths.length === 1) {
    clipboard.writeBuffer('public.file-url', Buffer.from(pathToFileURL(paths[0]!).toString(), 'utf8'));
  }
  return true;
}

function writeWindowsFilePaths(paths: string[]): Promise<boolean> {
  // Explorer pastes CF_HDROP, which Electron cannot write directly;
  // PowerShell's Set-Clipboard -LiteralPath produces it.
  const quoted = paths.map((p) => `'${p.replace(/'/g, "''")}'`).join(',');
  return new Promise((resolve) => {
    execFile(
      'powershell.exe',
      ['-NoProfile', '-NonInteractive', '-Command', `Set-Clipboard -LiteralPath ${quoted}`],
      { windowsHide: true, timeout: 10_000 },
      (error) => resolve(error == null),
    );
  });
}

const handlers: Record<string, (...args: never[]) => unknown> = {
  readText() {
    const text = clipboard.readText();
    return text.length > 0 ? text : null;
  },
  writeText(text: string) {
    clipboard.writeText(text);
  },
  readFilePaths() {
    if (process.platform === 'win32') return readWindowsFilePaths();
    if (process.platform === 'darwin') return readMacFilePaths();
    return [];
  },
  async writeFilePaths(paths: string[]) {
    if (paths.length === 0) return false;
    if (process.platform === 'darwin') return writeMacFilePaths(paths);
    if (process.platform === 'win32') return writeWindowsFilePaths(paths);
    clipboard.writeText(paths.join('\n'));
    return true;
  },
  readImage() {
    const image = clipboard.readImage();
    if (image.isEmpty()) return null;
    const png = image.toPNG();
    return { bytes: new Uint8Array(png.buffer, png.byteOffset, png.byteLength), mimeType: 'image/png', filename: 'clipboard.png' };
  },
  writeImage(bytes: Uint8Array) {
    const image = nativeImage.createFromBuffer(Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength));
    if (image.isEmpty()) return false;
    clipboard.writeImage(image);
    return true;
  },
};

export function registerClipboard(): void {
  ipcMain.handle(IpcChannels.clipboardInvoke, async (_event, op: string, ...args: unknown[]) => {
    const handler = handlers[op];
    if (!handler) throw new Error(`Unknown clipboard operation: ${op}`);
    return (handler as (...rest: unknown[]) => unknown)(...args);
  });
}

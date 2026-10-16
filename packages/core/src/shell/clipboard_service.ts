import { HostPlatform } from '../config/host_platform';
import { platform } from '../platform/platform';
import type { SelectedFile } from './file_selection_service';

export class ClipboardImageFile {
  constructor(opts: { bytes: Uint8Array; filename?: string; mimeType?: string }) {
    this.bytes = opts.bytes;
    this.filename = opts.filename ?? 'clipboard-image.png';
    this.mimeType = opts.mimeType ?? 'image/png';
  }

  readonly bytes: Uint8Array;
  readonly filename: string;
  readonly mimeType: string;
}

export type WebPasteHandler = (files: SelectedFile[], text: string | null) => boolean;

let _webPasteHandler: WebPasteHandler | null = null;

/**
 * Clipboard access through `platform().clipboard` (the Flutter client used
 * `Clipboard` for text plus the `gang_chat/clipboard` method channel on
 * Windows/macOS for files and images).
 *
 * PORT: the Android branches (`AndroidSystemService.readClipboardImage` /
 * `writeClipboardImage`) are gone; Android stays on Flutter. On the web the
 * Dart code read/wrote browser clipboard images itself and re-encoded
 * non-PNG images on a canvas; here the host's `ClipboardBridge` does both.
 */
export class ClipboardService {
  private get _supportsFiles(): boolean {
    return !HostPlatform.isWeb && (HostPlatform.isWindows || HostPlatform.isMacOS);
  }

  private get _supportsImages(): boolean {
    return HostPlatform.isWeb || HostPlatform.isWindows || HostPlatform.isMacOS;
  }

  async readFilePaths(): Promise<string[]> {
    if (!this._supportsFiles) return [];
    return (await platform().clipboard.readFilePaths()) ?? [];
  }

  async readImageFile(): Promise<ClipboardImageFile | null> {
    // The browser may ask for clipboard permission the first time.
    if (!this._supportsImages) return null;
    const image = await platform().clipboard.readImage();
    if (image == null) return null;
    const bytes = image.bytes;
    if (!(bytes instanceof Uint8Array) || bytes.length === 0) return null;
    return new ClipboardImageFile({
      bytes,
      filename: typeof image.filename === 'string' && image.filename.length > 0 ? image.filename : 'clipboard-image.png',
      mimeType: typeof image.mimeType === 'string' && image.mimeType.length > 0 ? image.mimeType : 'image/png',
    });
  }

  /**
   * DOM hosts only. Browsers (and so the Electron renderer) paste straight
   * into the focused text field, bypassing the composer's paste action, so
   * the page's paste events are offered to [handler] instead: it receives the
   * pasted files and plain text and returns true to consume the paste. Pass
   * null to stop.
   *
   * PORT: the Dart version subscribed to the page itself on web only. Core
   * has no DOM, so the paste listener lives in `@gang-chat/ui` (a `*.web.tsx`
   * file) and forwards each paste to [dispatchWebPaste]. It is not gated on
   * `kIsWeb` because Electron's text fields receive DOM paste events too.
   */
  setWebPasteHandler(handler: WebPasteHandler | null): void {
    _webPasteHandler = handler;
  }

  /** Whether a paste handler is installed (the web paste listener checks it). */
  static get hasWebPasteHandler(): boolean {
    return _webPasteHandler != null;
  }

  /**
   * Called by the DOM paste listener with the pasted files and text; returns
   * true when the installed handler consumed the paste (the listener then
   * calls `preventDefault()`).
   */
  static dispatchWebPaste(files: SelectedFile[], text: string | null): boolean {
    const handler = _webPasteHandler;
    if (handler == null) return false;
    return handler(files, text);
  }

  async readText(): Promise<string | null> {
    const text = await platform().clipboard.readText();
    return text == null || text.length === 0 ? null : text;
  }

  writeText(text: string): Promise<void> {
    return platform().clipboard.writeText(text);
  }

  /**
   * Writes file paths to the system clipboard so they can be pasted as files
   * in supported desktop apps. Returns false on unsupported platforms.
   */
  async writeFilePaths(paths: string[]): Promise<boolean> {
    if (!this._supportsFiles) return false;
    const normalized = paths.map((path) => path.trim()).filter((path) => path.length > 0);
    if (normalized.length === 0) return false;
    return (await platform().clipboard.writeFilePaths(normalized)) ?? false;
  }

  /**
   * Writes raw image [bytes] (with the given [mimeType]) to the system
   * clipboard so it can be pasted into other apps. No-op on platforms without
   * a handler. Returns true on success.
   */
  async writeImage(bytes: Uint8Array, opts: { mimeType?: string } = {}): Promise<boolean> {
    const { mimeType = 'image/png' } = opts;
    if (!this._supportsImages) return false;
    if (bytes.length === 0) return false;
    // Callers may pass JPEG bytes with the default PNG type, so check the
    // data itself; the host converts non-PNG images before writing.
    const actualType = _sniffImageMimeType(bytes) ?? mimeType;
    return (await platform().clipboard.writeImage(bytes, actualType)) ?? false;
  }
}

export function _isPng(bytes: Uint8Array): boolean {
  const signature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  if (bytes.length < signature.length) return false;
  for (let index = 0; index < signature.length; index++) {
    if (bytes[index] !== signature[index]) return false;
  }
  return true;
}

function _sniffImageMimeType(bytes: Uint8Array): string | null {
  if (_isPng(bytes)) return 'image/png';
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg';
  if (bytes.length >= 6 && bytes[0] === 0x47 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x38) {
    return 'image/gif';
  }
  if (
    bytes.length >= 12 &&
    bytes[0] === 0x52 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x46 &&
    bytes[8] === 0x57 &&
    bytes[9] === 0x45 &&
    bytes[10] === 0x42 &&
    bytes[11] === 0x50
  ) {
    return 'image/webp';
  }
  return null;
}

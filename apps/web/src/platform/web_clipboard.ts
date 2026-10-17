import type { ClipboardBridge, ClipboardImage } from '@gang-chat/core/platform/platform';

/**
 * The async Clipboard API, as the Flutter web build used it
 * (`browser_bridge_web.dart`): text both ways, images read from and written
 * as PNG (the only image type browsers accept for writing; anything else is
 * re-encoded on a canvas first). Browsers expose no copied file paths.
 *
 * Reading may make the browser ask for clipboard permission; keyboard pastes
 * never read here (see `browser_paste_listener.ts`), only the context menu's
 * 粘贴 does.
 */
export function createWebClipboard(): ClipboardBridge {
  return {
    async readText() {
      try {
        return await navigator.clipboard.readText();
      } catch {
        return null;
      }
    },
    async writeText(text) {
      try {
        await navigator.clipboard.writeText(text);
      } catch {
        _copyTextWithSelection(text);
      }
    },
    readFilePaths: async () => [],
    writeFilePaths: async () => false,
    async readImage(): Promise<ClipboardImage | null> {
      if (navigator.clipboard?.read == null) return null;
      try {
        for (const item of await navigator.clipboard.read()) {
          const type = item.types.find((candidate) => candidate.startsWith('image/'));
          if (type == null) continue;
          const blob = await item.getType(type);
          const bytes = new Uint8Array(await blob.arrayBuffer());
          if (bytes.length === 0) continue;
          const extension = type === 'image/jpeg' ? 'jpg' : (type.split('/')[1] ?? 'png');
          return { bytes, mimeType: type, filename: `clipboard-image.${extension}` };
        }
      } catch {
        // Permission denied or nothing readable.
      }
      return null;
    },
    async writeImage(bytes, mimeType = 'image/png') {
      if (typeof ClipboardItem === 'undefined' || navigator.clipboard?.write == null) return false;
      try {
        // Safari requires the write to start inside the user gesture, so the
        // item takes a promise of the PNG rather than awaiting it first.
        const png = mimeType === 'image/png' ? Promise.resolve(new Blob([bytes as Uint8Array<ArrayBuffer>], { type: 'image/png' })) : _reencodeAsPng(bytes, mimeType);
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': png })]);
        return true;
      } catch {
        return false;
      }
    },
  };
}

async function _reencodeAsPng(bytes: Uint8Array, mimeType: string): Promise<Blob> {
  const bitmap = await createImageBitmap(new Blob([bytes as Uint8Array<ArrayBuffer>], { type: mimeType }));
  try {
    const canvas = document.createElement('canvas');
    canvas.width = bitmap.width;
    canvas.height = bitmap.height;
    const context = canvas.getContext('2d');
    if (context == null) throw new Error('Canvas 2D is unavailable');
    context.drawImage(bitmap, 0, 0);
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((blob) => (blob == null ? reject(new Error('PNG encoding failed')) : resolve(blob)), 'image/png'),
    );
  } finally {
    bitmap.close();
  }
}

/** Fallback for pages where the async clipboard is unavailable (insecure origins). */
function _copyTextWithSelection(text: string): void {
  const area = document.createElement('textarea');
  area.value = text;
  area.setAttribute('readonly', '');
  area.style.position = 'fixed';
  area.style.opacity = '0';
  document.body.append(area);
  area.select();
  try {
    document.execCommand('copy');
  } finally {
    area.remove();
  }
}

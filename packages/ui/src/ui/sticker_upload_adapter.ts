import { StickerImageDimensions, StickerUploadSource } from '@gang-chat/core/app/sticker_uploads';

/** The parts of a picked file (`SelectedFile` / `PlatformFile`) the adapter reads. */
export interface StickerSelectedFile {
  readonly name: string;
  readAsBytes(): Promise<Uint8Array>;
}

export function stickerUploadSourcesFromSelectedFiles(files: Iterable<StickerSelectedFile>): StickerUploadSource[] {
  return Array.from(files, (file) => new StickerUploadSource({ filename: file.name, readBytes: () => file.readAsBytes() }));
}

/** Decodes an image's pixel size (Dart: `ui.instantiateImageCodec`). */
export async function decodeStickerImageDimensions(bytes: Uint8Array): Promise<StickerImageDimensions> {
  const blob = new Blob([bytes as BlobPart]);
  if (typeof createImageBitmap === 'function') {
    const bitmap = await createImageBitmap(blob);
    try {
      return new StickerImageDimensions({ width: bitmap.width, height: bitmap.height });
    } finally {
      bitmap.close();
    }
  }
  const url = URL.createObjectURL(blob);
  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    return new StickerImageDimensions({ width: image.naturalWidth, height: image.naturalHeight });
  } finally {
    URL.revokeObjectURL(url);
  }
}

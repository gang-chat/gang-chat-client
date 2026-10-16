import { MediaCacheRequest } from '@gang-chat/core/app/media_cache_controller';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { HttpClientHelpers } from '@gang-chat/core/platform/http';
import { platform } from '@gang-chat/core/platform/platform';
import type { Message, MessageAttachment } from '@gang-chat/core/protocol/models';

import { ChatImagePreviewActions, ImagePreviewActionCancelled } from './chat_image_preview';
import type { HomeShellState } from './home_shell';

/**
 * Dart's `Exception(message)`: `toString()` keeps the `Exception: ` prefix, so
 * the preview overlay's `userFacingErrorMessage` shows exactly what Flutter did.
 */
class _Exception extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'Exception';
  }

  override toString(): string {
    return `Exception: ${this.message}`;
  }
}

/** `PlatformException.code`, read structurally from a bridge error. */
function _platformErrorCode(error: unknown): string | null {
  if (error == null || typeof error !== 'object' || !('code' in error)) return null;
  const code = (error as { code: unknown }).code;
  return typeof code === 'string' ? code : null;
}

/**
 * Actions backing the full-screen image preview overlay (see
 * [ChatImagePreviewActions]): download to the Downloads folder, save-as via a
 * picker, copy the image to the clipboard, and add a sticker into the user's
 * personal stickers.
 */
export const HomeShellImagePreview = {
  get _imagePreviewActions(): ChatImagePreviewActions {
    const self = this as unknown as HomeShellState;
    // The "添加到房间表情包" action only makes sense when the current user can
    // administer the room whose messages are on screen.
    const room = self._selectedRoom;
    const canManageRoomStickers = room != null && (room.isAdmin || self._currentUser.isSuperuser);
    return new ChatImagePreviewActions({
      onDownload: (url: string, suggestedName: string) => self._previewDownloadToDownloads(url, suggestedName),
      onSaveAs: (url: string, suggestedName: string) => self._previewSaveAs(url, suggestedName),
      onCopyToClipboard: (url: string) => self._previewCopyToClipboard(url),
      onSaveSticker: (message: Message, attachment: MessageAttachment) => self._previewSaveSticker(message, attachment),
      onSaveRoomSticker: canManageRoomStickers
        ? (message: Message, attachment: MessageAttachment) => self._previewSaveRoomSticker(message, attachment)
        : null,
      mediaCache: self._mediaCacheController,
    });
  },

  /**
   * Fetch the bytes for an image at [url]. Throws a user-facing message on a
   * non-2xx response or transport failure.
   */
  async _fetchImageBytes(this: HomeShellState, url: string): Promise<Uint8Array> {
    const request = MediaCacheRequest.tryFromUrl({ url });
    if (request != null) {
      const bytes = await this._mediaCacheController.readBytes({ request });
      return Uint8Array.from(bytes);
    }

    let uri: string;
    try {
      uri = new URL(url).href;
    } catch {
      throw new _Exception('图片地址无效');
    }
    // PORT: Dart created and closed a dedicated `http.Client`; the shared
    // platform client is used (and stays open) instead.
    const response = await new HttpClientHelpers(platform().http).get(uri);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw new _Exception(`下载失败 (${response.statusCode})`);
    }
    return response.bodyBytes;
  },

  async _previewDownloadToDownloads(this: HomeShellState, url: string, suggestedName: string): Promise<void> {
    const bytes = await this._fetchImageBytes(url);
    if (this._fileSelectionService.savesAsBrowserDownload) {
      await this._fileSelectionService.saveBytesAsBrowserDownload({
        bytes,
        filename: suggestedName,
      });
      return;
    }
    if (HostPlatform.isAndroid) {
      try {
        await this._fileSelectionService.saveBytesToDownloads({
          bytes,
          filename: suggestedName,
        });
      } catch (error) {
        if (_platformErrorCode(error) !== 'downloads_unavailable') throw error;
        const location = await this._fileSelectionService.getSaveLocation({ suggestedName });
        if (location == null) throw new ImagePreviewActionCancelled();
        await this._fileSelectionService.saveBytesToLocation({
          bytes,
          location,
          filename: suggestedName,
        });
      }
      return;
    }
    const directory = await this._resolveDownloadsDirectory();
    const path = await this._uniqueDestinationPath({
      directory,
      filename: suggestedName,
    });
    await this._fileSelectionService.saveBytesToPath({
      bytes,
      path,
      filename: suggestedName,
    });
  },

  async _previewSaveAs(this: HomeShellState, url: string, suggestedName: string): Promise<void> {
    // Fetch first so a transport failure surfaces before the picker opens.
    const bytes = await this._fetchImageBytes(url);
    if (this._fileSelectionService.savesAsBrowserDownload) {
      // Browsers have no save dialog of their own to open; where the file
      // goes (or whether to ask) is the browser's download setting.
      await this._fileSelectionService.saveBytesAsBrowserDownload({
        bytes,
        filename: suggestedName,
      });
      return;
    }
    const location = await this._fileSelectionService.getSaveLocation({ suggestedName });
    if (location == null) {
      // User cancelled the picker; treat as a silent no-op.
      throw new ImagePreviewActionCancelled();
    }
    await this._fileSelectionService.saveBytesToLocation({
      bytes,
      location,
      filename: suggestedName,
    });
  },

  async _previewCopyToClipboard(this: HomeShellState, url: string): Promise<void> {
    const bytes = await this._fetchImageBytes(url);
    const ok = await this._clipboardService.writeImage(bytes);
    if (!ok) {
      throw new _Exception('当前平台不支持复制图片到剪贴板');
    }
  },

  async _previewSaveSticker(this: HomeShellState, message: Message, attachment: MessageAttachment): Promise<void> {
    const stickerId = attachment.stickerId;
    if (stickerId == null || stickerId.length === 0) {
      throw new _Exception('该表情无法添加');
    }
    await this._stickerPacksController.saveSticker({
      roomId: message.roomId,
      stickerId,
      sourceMessageId: message.id,
      targetScope: 'personal',
      userId: this._currentUser.id,
    });
  },

  async _previewSaveRoomSticker(this: HomeShellState, message: Message, attachment: MessageAttachment): Promise<void> {
    const stickerId = attachment.stickerId;
    if (stickerId == null || stickerId.length === 0) {
      throw new _Exception('该表情无法添加');
    }
    const room = this._selectedRoom;
    if (room == null || (!room.isAdmin && !this._currentUser.isSuperuser)) {
      throw new _Exception('没有权限管理房间表情');
    }
    await this._stickerPacksController.saveSticker({
      roomId: room.id,
      stickerId,
      sourceMessageId: message.id,
      targetScope: 'room',
      userId: this._currentUser.id,
    });
  },

  /**
   * The directory new downloads land in. Prefers the platform Downloads
   * folder, falling back to the app data directory when unavailable.
   *
   * PORT: Dart fell back to `getApplicationDocumentsDirectory()`; the
   * platform bridge exposes no documents directory, so the application
   * support directory is used instead.
   */
  async _resolveDownloadsDirectory(this: HomeShellState): Promise<string> {
    try {
      const downloads = (await platform().files.directories()).downloads;
      if (downloads != null) return downloads;
    } catch {
      // Fall through to the app directory.
    }
    return (await platform().files.directories()).applicationSupport;
  },

  /**
   * Builds a destination path inside [directory] for [filename], appending a
   * " (n)" suffix before the extension when the name already exists so a
   * download never silently overwrites an existing file.
   *
   * PORT: `File.existsSync` has no synchronous bridge equivalent, so this is
   * async.
   */
  async _uniqueDestinationPath(this: HomeShellState, opts: { directory: string; filename: string }): Promise<string> {
    const { directory, filename } = opts;
    const files = platform().files;
    const separator = HostPlatform.pathSeparator;
    const base = directory.endsWith(separator) ? directory.substring(0, directory.length - 1) : directory;
    const candidate = (name: string): string => `${base}${separator}${name}`;

    if (!(await files.exists(candidate(filename)))) return candidate(filename);

    const dot = filename.lastIndexOf('.');
    const stem = dot <= 0 ? filename : filename.substring(0, dot);
    const ext = dot <= 0 ? '' : filename.substring(dot);
    for (let i = 1; i < 1000; i++) {
      const name = `${stem} (${i})${ext}`;
      if (!(await files.exists(candidate(name)))) return candidate(name);
    }
    return candidate(filename);
  },
};

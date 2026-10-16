import * as file_display from '@gang-chat/core/app/file_display';
import { DownloadCancelledException, type FileDownloadStatePatch } from '@gang-chat/core/app/file_downloads_controller';
import { MediaCacheCancelledException, MediaCacheRequest } from '@gang-chat/core/app/media_cache_controller';
import type { Message, MessageAttachment } from '@gang-chat/core/protocol/models';
import type { SaveFileLocation } from '@gang-chat/core/shell/file_selection_service';

import { FloatingNoticeTone, showFloatingNotice } from '../ui/feedback';
import type { HomeShellState } from './home_shell';

/**
 * Download orchestration for file attachments shown in the chat.
 *
 * Kept deliberately thin: the streaming/IO lives in [FileDownloadsController]
 * and all of the "what state is this transfer in" decisions live in
 * `file_display`. This table only wires the two to the widget tree and the
 * local [_fileDownloads] map, and surfaces user-facing notices.
 */
export const HomeShellDownloads = {
  /**
   * Start saving [attachment] to a user-picked location. [resolvedUrl] is the
   * absolute asset URL (resolved by the widget layer against [AppConfig]).
   */
  async _downloadAttachment(
    this: HomeShellState,
    opts: {
      message: Message;
      attachment: MessageAttachment;
      index: number;
      resolvedUrl: string;
    },
  ): Promise<void> {
    const { message, attachment, index, resolvedUrl } = opts;
    const downloadKey = file_display.fileDownloadKey(message, attachment, index);
    if (
      !this._fileDownloadsController.canStartDownload({
        downloads: this._fileDownloads,
        downloadKey,
      })
    ) {
      return;
    }

    const uri = file_display.fileDownloadUri(resolvedUrl);
    if (uri == null) {
      this._showDownloadNotice(file_display.fileDownloadUnavailableMessage(), { tone: FloatingNoticeTone.error });
      return;
    }

    const suggestedName = file_display.fileAttachmentTitle(attachment);
    if (this._fileSelectionService.savesAsBrowserDownload) {
      await this._downloadAttachmentInBrowser({
        downloadKey,
        attachment,
        resolvedUrl,
        suggestedName,
      });
      return;
    }
    let location: SaveFileLocation | null;
    try {
      location = await this._fileSelectionService.getSaveLocation({ suggestedName });
    } catch (error) {
      if (!this.mounted) return;
      this._showDownloadNotice(file_display.filePickerOpenFailureMessage(error), { tone: FloatingNoticeTone.error });
      return;
    }
    if (!this.mounted || location == null) return;
    const savedLocation = location;

    const transfer = this._fileDownloadsController.createDownload({
      totalBytes: attachment.asset?.sizeBytes ?? 0,
      destinationPath: savedLocation.path,
    });
    this._setHomeState(() =>
      this._applyDownloadPatch(
        this._fileDownloadsController.patchStartedDownload({
          downloads: this._fileDownloads,
          downloadKey,
          transfer,
        }),
      ),
    );

    const applyProgress = (_progress: { sentBytes: number; totalBytes: number }): void => {
      if (!this.mounted) return;
      const patch = this._fileDownloadsController.patchDownloadProgress({
        downloads: this._fileDownloads,
        downloadKey,
        transfer,
      });
      if (patch == null) return;
      this._setHomeState(() => this._applyDownloadPatch(patch));
    };

    try {
      const cacheRequest = MediaCacheRequest.tryFromUrl({
        url: resolvedUrl,
        filename: suggestedName,
        mimeType: attachment.asset?.mimeType ?? null,
        expectedBytes: attachment.asset?.sizeBytes ?? null,
      });
      if (cacheRequest == null) {
        await this._fileDownloadsController.downloadToFile({
          uri,
          transfer,
          onProgress: applyProgress,
        });
      } else {
        const cached = await this._mediaCacheController.getOrDownload({
          request: cacheRequest,
          transfer,
          onProgress: applyProgress,
        });
        await this._mediaCacheController.copyFileToPath({
          source: cached,
          destinationPath: savedLocation.path,
          transfer,
          onProgress: applyProgress,
        });
      }
      if (
        !this._fileDownloadsController.canCompleteDownload({
          downloads: this._fileDownloads,
          downloadKey,
          transfer,
        })
      ) {
        return;
      }
      await this._fileSelectionService.commitLocation(savedLocation);
      if (!this.mounted) return;
      this._setHomeState(() =>
        this._applyDownloadPatch(
          this._fileDownloadsController.patchCompletedDownload({
            downloads: this._fileDownloads,
            downloadKey,
          }),
        ),
      );
      this._showDownloadNotice(file_display.fileDownloadedNotice(), { tone: FloatingNoticeTone.success });
    } catch (error) {
      if (error instanceof MediaCacheCancelledException || error instanceof DownloadCancelledException) {
        // Cancellation already removed the entry via [_cancelDownload]; nothing
        // to report.
      } else {
        if (!this.mounted) return;
        this._setHomeState(() =>
          this._applyDownloadPatch(
            this._fileDownloadsController.patchFailedDownload({
              downloads: this._fileDownloads,
              transfer,
              failure: error,
            }),
          ),
        );
      }
    } finally {
      try {
        await this._fileSelectionService.discardLocation(savedLocation);
      } catch {
        // Best-effort cleanup. A completed Android commit already removed the
        // staging file; cleanup failures must not overwrite download state.
      }
    }
  },

  /**
   * The web has no save dialog or file system: fetch the attachment into
   * memory with the usual progress, pause and cancel, then hand it to the
   * browser as a download.
   */
  async _downloadAttachmentInBrowser(
    this: HomeShellState,
    opts: {
      downloadKey: string;
      attachment: MessageAttachment;
      resolvedUrl: string;
      suggestedName: string;
    },
  ): Promise<void> {
    const { downloadKey, attachment, resolvedUrl, suggestedName } = opts;
    const cacheRequest = MediaCacheRequest.tryFromUrl({
      url: resolvedUrl,
      filename: suggestedName,
      mimeType: attachment.asset?.mimeType ?? null,
      expectedBytes: attachment.asset?.sizeBytes ?? null,
    });
    if (cacheRequest == null) {
      this._showDownloadNotice(file_display.fileDownloadUnavailableMessage(), { tone: FloatingNoticeTone.error });
      return;
    }

    // Nothing is written to disk, so there is never a partial file to clean
    // up; the name only identifies the transfer.
    const transfer = this._fileDownloadsController.createDownload({
      totalBytes: attachment.asset?.sizeBytes ?? 0,
      destinationPath: suggestedName,
    });
    this._setHomeState(() =>
      this._applyDownloadPatch(
        this._fileDownloadsController.patchStartedDownload({
          downloads: this._fileDownloads,
          downloadKey,
          transfer,
        }),
      ),
    );

    const applyProgress = (_progress: { sentBytes: number; totalBytes: number }): void => {
      if (!this.mounted) return;
      const patch = this._fileDownloadsController.patchDownloadProgress({
        downloads: this._fileDownloads,
        downloadKey,
        transfer,
      });
      if (patch == null) return;
      this._setHomeState(() => this._applyDownloadPatch(patch));
    };

    try {
      const bytes = await this._mediaCacheController.getOrDownloadBytes({
        request: cacheRequest,
        transfer,
        onProgress: applyProgress,
      });
      if (
        !this._fileDownloadsController.canCompleteDownload({
          downloads: this._fileDownloads,
          downloadKey,
          transfer,
        })
      ) {
        return;
      }
      await this._fileSelectionService.saveBytesAsBrowserDownload({
        bytes,
        filename: suggestedName,
        mimeType: attachment.asset?.mimeType ?? null,
      });
      if (!this.mounted) return;
      this._setHomeState(() =>
        this._applyDownloadPatch(
          this._fileDownloadsController.patchCompletedDownload({
            downloads: this._fileDownloads,
            downloadKey,
          }),
        ),
      );
      this._showDownloadNotice(file_display.fileDownloadedNotice(), { tone: FloatingNoticeTone.success });
    } catch (error) {
      if (error instanceof MediaCacheCancelledException) {
        // Cancellation already removed the entry via [_cancelDownload]; nothing
        // to report.
        return;
      }
      if (!this.mounted) return;
      this._setHomeState(() =>
        this._applyDownloadPatch(
          this._fileDownloadsController.patchFailedDownload({
            downloads: this._fileDownloads,
            transfer,
            failure: error,
          }),
        ),
      );
    }
  },

  _pauseDownload(this: HomeShellState, downloadKey: string): void {
    const patch = this._fileDownloadsController.patchPausedDownload({
      downloads: this._fileDownloads,
      downloadKey,
    });
    if (patch == null) return;
    this._setHomeState(() => this._applyDownloadPatch(patch));
  },

  _resumeDownload(this: HomeShellState, downloadKey: string): void {
    const patch = this._fileDownloadsController.patchResumedDownload({
      downloads: this._fileDownloads,
      downloadKey,
    });
    if (patch == null) return;
    this._setHomeState(() => this._applyDownloadPatch(patch));
  },

  /**
   * Cancel an in-flight download and drop it from the map. The streaming loop
   * in [FileDownloadsController.downloadToFile] sees the cancellation, cleans
   * up the partial file, and throws [DownloadCancelledException].
   */
  _cancelDownload(this: HomeShellState, downloadKey: string): void {
    const transfer = this._fileDownloads.get(downloadKey);
    if (transfer == null) return;
    this._fileDownloadsController.cancel(transfer);
    this._setHomeState(() =>
      this._applyDownloadPatch(
        this._fileDownloadsController.patchRemovedDownload({
          downloads: this._fileDownloads,
          downloadKey,
        }),
      ),
    );
  },

  /**
   * Clear a finished-but-failed download entry so the tile returns to its
   * idle, re-downloadable state.
   */
  _dismissDownload(this: HomeShellState, downloadKey: string): void {
    if (!this._fileDownloads.has(downloadKey)) return;
    this._setHomeState(() =>
      this._applyDownloadPatch(
        this._fileDownloadsController.patchRemovedDownload({
          downloads: this._fileDownloads,
          downloadKey,
        }),
      ),
    );
  },

  /**
   * Cancel every active download (used on dispose) so no http client or sink
   * is left dangling after the shell goes away.
   */
  _cancelActiveDownloads(this: HomeShellState): void {
    for (const transfer of this._fileDownloads.values()) {
      this._fileDownloadsController.cancel(transfer);
    }
  },

  _applyDownloadPatch(this: HomeShellState, patch: FileDownloadStatePatch): void {
    this._fileDownloads = patch.downloads;
  },

  _showDownloadNotice(this: HomeShellState, message: string, opts: { tone?: FloatingNoticeTone } = {}): void {
    const { tone = FloatingNoticeTone.info } = opts;
    if (!this.mounted) return;
    showFloatingNotice(message, { tone });
  },
};

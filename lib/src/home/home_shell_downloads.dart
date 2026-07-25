part of 'home_shell.dart';

/// Download orchestration for file attachments shown in the chat.
///
/// Kept deliberately thin: the streaming/IO lives in [FileDownloadsController]
/// and all of the "what state is this transfer in" decisions live in
/// `file_display.dart`. This extension only wires the two to the widget tree
/// and the local [_fileDownloads] map, and surfaces user-facing notices.
extension _HomeShellDownloads on _HomeShellState {
  /// Start saving [attachment] to a user-picked location. [resolvedUrl] is the
  /// absolute asset URL (resolved by the widget layer against [AppConfig]).
  Future<void> _downloadAttachment({
    required Message message,
    required MessageAttachment attachment,
    required int index,
    required String resolvedUrl,
  }) async {
    final downloadKey = file_display.fileDownloadKey(
      message,
      attachment,
      index,
    );
    if (!_fileDownloadsController.canStartDownload(
      downloads: _fileDownloads,
      downloadKey: downloadKey,
    )) {
      return;
    }

    final uri = file_display.fileDownloadUri(resolvedUrl);
    if (uri == null) {
      _showDownloadNotice(
        file_display.fileDownloadUnavailableMessage(),
        tone: FloatingNoticeTone.error,
      );
      return;
    }

    final suggestedName = file_display.fileAttachmentTitle(attachment);
    SaveFileLocation? location;
    try {
      location = await _fileSelectionService.getSaveLocation(
        suggestedName: suggestedName,
      );
    } catch (error) {
      if (!mounted) return;
      _showDownloadNotice(
        file_display.filePickerOpenFailureMessage(error),
        tone: FloatingNoticeTone.error,
      );
      return;
    }
    if (!mounted || location == null) return;

    final transfer = _fileDownloadsController.createDownload(
      totalBytes: attachment.asset?.sizeBytes ?? 0,
      destinationPath: location.path,
    );
    _setHomeState(
      () => _applyDownloadPatch(
        _fileDownloadsController.patchStartedDownload(
          downloads: _fileDownloads,
          downloadKey: downloadKey,
          transfer: transfer,
        ),
      ),
    );

    void applyProgress({required int sentBytes, required int totalBytes}) {
      if (!mounted) return;
      final patch = _fileDownloadsController.patchDownloadProgress(
        downloads: _fileDownloads,
        downloadKey: downloadKey,
        transfer: transfer,
      );
      if (patch == null) return;
      _setHomeState(() => _applyDownloadPatch(patch));
    }

    try {
      final cacheRequest = MediaCacheRequest.tryFromUrl(
        url: resolvedUrl,
        filename: suggestedName,
        mimeType: attachment.asset?.mimeType,
        expectedBytes: attachment.asset?.sizeBytes,
      );
      if (cacheRequest == null) {
        await _fileDownloadsController.downloadToFile(
          uri: uri,
          transfer: transfer,
          onProgress: applyProgress,
        );
      } else {
        final cached = await _mediaCacheController.getOrDownload(
          request: cacheRequest,
          transfer: transfer,
          onProgress: applyProgress,
        );
        await _mediaCacheController.copyFileToPath(
          source: cached,
          destinationPath: location.path,
          transfer: transfer,
          onProgress: applyProgress,
        );
      }
      if (!_fileDownloadsController.canCompleteDownload(
        downloads: _fileDownloads,
        downloadKey: downloadKey,
        transfer: transfer,
      )) {
        return;
      }
      await _fileSelectionService.commitLocation(location);
      if (!mounted) return;
      _setHomeState(
        () => _applyDownloadPatch(
          _fileDownloadsController.patchCompletedDownload(
            downloads: _fileDownloads,
            downloadKey: downloadKey,
          ),
        ),
      );
      _showDownloadNotice(
        file_display.fileDownloadedNotice(),
        tone: FloatingNoticeTone.success,
      );
    } on MediaCacheCancelledException catch (_) {
      // Cancellation already removed the entry via [_cancelDownload]; nothing
      // to report.
    } on DownloadCancelledException {
      // Cancellation already removed the entry via [_cancelDownload]; nothing
      // to report.
    } catch (error) {
      if (!mounted) return;
      _setHomeState(
        () => _applyDownloadPatch(
          _fileDownloadsController.patchFailedDownload(
            downloads: _fileDownloads,
            transfer: transfer,
            failure: error,
          ),
        ),
      );
    } finally {
      try {
        await _fileSelectionService.discardLocation(location);
      } catch (_) {
        // Best-effort cleanup. A completed Android commit already removed the
        // staging file; cleanup failures must not overwrite download state.
      }
    }
  }

  void _pauseDownload(String downloadKey) {
    final patch = _fileDownloadsController.patchPausedDownload(
      downloads: _fileDownloads,
      downloadKey: downloadKey,
    );
    if (patch == null) return;
    _setHomeState(() => _applyDownloadPatch(patch));
  }

  void _resumeDownload(String downloadKey) {
    final patch = _fileDownloadsController.patchResumedDownload(
      downloads: _fileDownloads,
      downloadKey: downloadKey,
    );
    if (patch == null) return;
    _setHomeState(() => _applyDownloadPatch(patch));
  }

  /// Cancel an in-flight download and drop it from the map. The streaming loop
  /// in [FileDownloadsController.downloadToFile] sees the cancellation, cleans
  /// up the partial file, and throws [DownloadCancelledException].
  void _cancelDownload(String downloadKey) {
    final transfer = _fileDownloads[downloadKey];
    if (transfer == null) return;
    _fileDownloadsController.cancel(transfer);
    _setHomeState(
      () => _applyDownloadPatch(
        _fileDownloadsController.patchRemovedDownload(
          downloads: _fileDownloads,
          downloadKey: downloadKey,
        ),
      ),
    );
  }

  /// Clear a finished-but-failed download entry so the tile returns to its
  /// idle, re-downloadable state.
  void _dismissDownload(String downloadKey) {
    if (!_fileDownloads.containsKey(downloadKey)) return;
    _setHomeState(
      () => _applyDownloadPatch(
        _fileDownloadsController.patchRemovedDownload(
          downloads: _fileDownloads,
          downloadKey: downloadKey,
        ),
      ),
    );
  }

  /// Cancel every active download (used on dispose) so no http client or sink
  /// is left dangling after the shell goes away.
  void _cancelActiveDownloads() {
    for (final transfer in _fileDownloads.values) {
      _fileDownloadsController.cancel(transfer);
    }
  }

  void _applyDownloadPatch(FileDownloadStatePatch patch) {
    _fileDownloads = patch.downloads;
  }

  void _showDownloadNotice(
    String message, {
    FloatingNoticeTone tone = FloatingNoticeTone.info,
  }) {
    if (!mounted) return;
    showFloatingNotice(context, message, tone: tone);
  }
}

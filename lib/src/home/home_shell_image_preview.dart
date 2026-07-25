part of 'home_shell.dart';

/// Actions backing the full-screen image preview overlay (see
/// [ChatImagePreviewActions]): download to the Downloads folder, save-as via a
/// picker, copy the image to the clipboard, and add a sticker into the user's
/// personal stickers.
extension _HomeShellImagePreview on _HomeShellState {
  ChatImagePreviewActions get _imagePreviewActions {
    // The "添加到房间表情包" action only makes sense when the current user can
    // administer the room whose messages are on screen.
    final room = _selectedRoom;
    final canManageRoomStickers =
        room != null && (room.isAdmin || _currentUser.isSuperuser);
    return ChatImagePreviewActions(
      onDownload: _previewDownloadToDownloads,
      onSaveAs: _previewSaveAs,
      onCopyToClipboard: _previewCopyToClipboard,
      onSaveSticker: _previewSaveSticker,
      onSaveRoomSticker: canManageRoomStickers ? _previewSaveRoomSticker : null,
      mediaCache: _mediaCacheController,
    );
  }

  /// Fetch the bytes for an image at [url]. Throws a user-facing message on a
  /// non-2xx response or transport failure.
  Future<Uint8List> _fetchImageBytes(String url) async {
    final request = MediaCacheRequest.tryFromUrl(url: url);
    if (request != null) {
      final bytes = await _mediaCacheController.readBytes(request: request);
      return Uint8List.fromList(bytes);
    }

    final uri = Uri.tryParse(url);
    if (uri == null) {
      throw Exception('图片地址无效');
    }
    final client = http.Client();
    try {
      final response = await client.get(uri);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw Exception('下载失败 (${response.statusCode})');
      }
      return response.bodyBytes;
    } finally {
      client.close();
    }
  }

  Future<void> _previewDownloadToDownloads(
    String url,
    String suggestedName,
  ) async {
    final bytes = await _fetchImageBytes(url);
    if (Platform.isAndroid) {
      try {
        await _fileSelectionService.saveBytesToDownloads(
          bytes: bytes,
          filename: suggestedName,
        );
      } on PlatformException catch (error) {
        if (error.code != 'downloads_unavailable') rethrow;
        final location = await _fileSelectionService.getSaveLocation(
          suggestedName: suggestedName,
        );
        if (location == null) throw const ImagePreviewActionCancelled();
        await _fileSelectionService.saveBytesToLocation(
          bytes: bytes,
          location: location,
          filename: suggestedName,
        );
      }
      return;
    }
    final directory = await _resolveDownloadsDirectory();
    final path = _uniqueDestinationPath(
      directory: directory.path,
      filename: suggestedName,
    );
    await _fileSelectionService.saveBytesToPath(
      bytes: bytes,
      path: path,
      filename: suggestedName,
    );
  }

  Future<void> _previewSaveAs(String url, String suggestedName) async {
    // Fetch first so a transport failure surfaces before the picker opens.
    final bytes = await _fetchImageBytes(url);
    final location = await _fileSelectionService.getSaveLocation(
      suggestedName: suggestedName,
    );
    if (location == null) {
      // User cancelled the picker; treat as a silent no-op.
      throw const ImagePreviewActionCancelled();
    }
    await _fileSelectionService.saveBytesToLocation(
      bytes: bytes,
      location: location,
      filename: suggestedName,
    );
  }

  Future<void> _previewCopyToClipboard(String url) async {
    final bytes = await _fetchImageBytes(url);
    final ok = await _clipboardService.writeImage(bytes);
    if (!ok) {
      throw Exception('当前平台不支持复制图片到剪贴板');
    }
  }

  Future<void> _previewSaveSticker(
    Message message,
    MessageAttachment attachment,
  ) async {
    final stickerId = attachment.stickerId;
    if (stickerId == null || stickerId.isEmpty) {
      throw Exception('该表情无法添加');
    }
    await _stickerPacksController.saveSticker(
      roomId: message.roomId,
      stickerId: stickerId,
      sourceMessageId: message.id,
      targetScope: 'personal',
      userId: _currentUser.id,
    );
  }

  Future<void> _previewSaveRoomSticker(
    Message message,
    MessageAttachment attachment,
  ) async {
    final stickerId = attachment.stickerId;
    if (stickerId == null || stickerId.isEmpty) {
      throw Exception('该表情无法添加');
    }
    final room = _selectedRoom;
    if (room == null || (!room.isAdmin && !_currentUser.isSuperuser)) {
      throw Exception('没有权限管理房间表情');
    }
    await _stickerPacksController.saveSticker(
      roomId: room.id,
      stickerId: stickerId,
      sourceMessageId: message.id,
      targetScope: 'room',
      userId: _currentUser.id,
    );
  }

  /// The directory new downloads land in. Prefers the platform Downloads
  /// folder, falling back to the app documents directory when unavailable.
  Future<Directory> _resolveDownloadsDirectory() async {
    try {
      final downloads = await getDownloadsDirectory();
      if (downloads != null) return downloads;
    } catch (_) {
      // Fall through to the documents directory.
    }
    return getApplicationDocumentsDirectory();
  }

  /// Builds a destination path inside [directory] for [filename], appending a
  /// " (n)" suffix before the extension when the name already exists so a
  /// download never silently overwrites an existing file.
  String _uniqueDestinationPath({
    required String directory,
    required String filename,
  }) {
    final separator = Platform.pathSeparator;
    final base = directory.endsWith(separator)
        ? directory.substring(0, directory.length - 1)
        : directory;
    String candidate(String name) => '$base$separator$name';

    if (!File(candidate(filename)).existsSync()) return candidate(filename);

    final dot = filename.lastIndexOf('.');
    final stem = dot <= 0 ? filename : filename.substring(0, dot);
    final ext = dot <= 0 ? '' : filename.substring(dot);
    for (var i = 1; i < 1000; i++) {
      final name = '$stem ($i)$ext';
      if (!File(candidate(name)).existsSync()) return candidate(name);
    }
    return candidate(filename);
  }
}

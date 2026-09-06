import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'tooltip.dart';

import '../app/file_display.dart' as file_display;
import '../app/sticker_display.dart';
import '../app/media_cache_controller.dart';
import '../app/sticker_management.dart';
import '../app/sticker_ordering.dart' as sticker_ordering;
import '../app/sticker_uploads.dart';
import '../protocol/api_client.dart';
import '../protocol/models.dart';
import '../shell/file_selection_service.dart';
import 'app_config_scope.dart';
import 'button.dart';
import 'cached_asset_image.dart';
import 'feedback.dart';
import 'input.dart';
import 'media_cache_scope.dart';
import 'settings_scaffold.dart';
import 'sticker_upload_adapter.dart';
import 'surface.dart';
import 'tokens.dart';

typedef StickerImagePreviewOpener =
    Future<void> Function(
      BuildContext context, {
      required String imageUrl,
      required String suggestedName,
      bool forceSquare,
    });

const _stickerFileTypeGroups = [
  FileTypeGroup(
    label: '图片和 ZIP',
    extensions: ['png', 'jpg', 'jpeg', 'webp', 'gif', 'zip'],
  ),
];

/// 表情包管理的数据来源。个人/房间各自实现,面板只负责编排与展示。
///
/// 业务逻辑(排序、筛选、上传项解析等)统一复用 `lib/src/app/sticker_*`
/// 中的纯函数,这里仅暴露最小的副作用接口。
abstract class StickerManagerBackend {
  /// 用于文案区分个人/房间。
  StickerManagementScope get scope;

  /// API 是否可用(未登录等情况下为 false)。
  bool get hasApi;

  /// 当前数据源允许的操作能力。个人表情默认完整可管理。
  StickerManagementCapabilities get capabilities =>
      const StickerManagementCapabilities();

  /// 读取当前作用域下的全部表情包。
  Future<List<StickerPack>> loadPacks();

  /// 没有可用表情包时创建一个默认包。
  Future<StickerPack> createDefaultPack({int? sortOrder});

  /// 上传图片资源,返回资源 id。
  Future<String> uploadImageAsset({
    required Uint8List bytes,
    required String filename,
    required String purpose,
  });

  /// 向表情包追加一张表情。
  Future<void> addSticker({
    required String packId,
    required String assetId,
    required String name,
    int? sortOrder,
  });

  /// 删除一张表情。
  Future<void> deleteSticker({
    required String packId,
    required String stickerId,
  });

  /// 重命名表情,返回服务端确认后的名称(失败返回 null)。
  Future<String?> renameSticker({
    required String packId,
    required String stickerId,
    required String name,
  });

  /// 重新排序表情包内的表情。
  Future<void> reorderStickers({
    required String packId,
    required List<String> stickerIds,
  });

  /// 下载选中的表情,返回文件数据。
  Future<DownloadedFile> downloadStickers({required List<String> stickerIds});

  /// “设为头像”动作。返回 null 表示该作用域不支持(房间)。
  Future<void> Function(ManagedSticker item)? get onSetAvatar => null;
}

/// 通用表情包管理面板。个人设置与房间设置共用同一套交互与视觉。
class StickerManagerPanel extends StatefulWidget {
  const StickerManagerPanel({
    super.key,
    required this.backend,
    this.fileSelectionService = const FileSelectionService(),
    this.imagePreviewOpener,
    this.title = '表情包管理',
    this.unavailableText = '表情包需要登录后从服务端读取',
  });

  final StickerManagerBackend backend;
  final FileSelectionService fileSelectionService;
  final StickerImagePreviewOpener? imagePreviewOpener;
  final String title;
  final String unavailableText;

  @override
  State<StickerManagerPanel> createState() => _StickerManagerPanelState();
}

class _StickerManagerPanelState extends State<StickerManagerPanel> {
  List<StickerPack> _packs = const [];
  List<String> _selectedStickerIds = <String>[];
  bool _loading = true;
  bool _uploading = false;
  bool _deleting = false;
  bool _savingOrder = false;
  bool _downloading = false;
  bool _managing = false;
  String _filterKeyword = '';
  String _filterMimeType = '';
  String? _error;
  String? _notice;
  int _floatingNoticeSerial = 0;
  final Map<String, int> _floatingNoticeEventKeys = {};

  StickerManagementScope get _scope => widget.backend.scope;
  StickerManagementCapabilities get _capabilities =>
      widget.backend.capabilities;

  @override
  void initState() {
    super.initState();
    if (widget.backend.hasApi) {
      unawaited(_load());
    } else {
      _loading = false;
    }
  }

  List<ManagedSticker> get _allItems => managedStickerItems(_packs);

  List<ManagedSticker> get _filteredItems => filteredManagedStickerItems(
    _allItems,
    keyword: _filterKeyword,
    mimeType: _filterMimeType,
  );

  bool get _filterActive =>
      stickerFilterActive(keyword: _filterKeyword, mimeType: _filterMimeType);

  bool get _busy => stickerManagementBusy(
    uploading: _uploading,
    deleting: _deleting,
    savingOrder: _savingOrder,
    downloading: _downloading,
  );

  Map<String, int> _selectionNumbers() =>
      stickerSelectionNumbers(_selectedStickerIds);

  void _applyLoadPatch(StickerPackLoadPatch patch) {
    _packs = patch.packs;
    _selectedStickerIds = patch.selectedStickerIds;
    _loading = patch.loading;
    _error = patch.error;
    _markFloatingNoticeEvent('error', _error);
  }

  void _applySelectionPatch(StickerSelectionPatch patch) {
    _managing = patch.managing;
    _filterKeyword = patch.filterKeyword;
    _filterMimeType = patch.filterMimeType;
    _selectedStickerIds = patch.selectedStickerIds;
  }

  void _applyActionPatch(StickerActionPatch patch) {
    _uploading = patch.uploading;
    _deleting = patch.deleting;
    _savingOrder = patch.savingOrder;
    _downloading = patch.downloading;
    _selectedStickerIds = patch.selectedStickerIds;
    _error = patch.error;
    _notice = patch.notice;
    _markFloatingNoticeEvent('error', _error);
    _markFloatingNoticeEvent('notice', _notice);
  }

  void _markFloatingNoticeEvent(String channel, String? message) {
    if (message == null || message.trim().isEmpty) return;
    _floatingNoticeEventKeys[channel] = ++_floatingNoticeSerial;
  }

  Object? _floatingNoticeEventKey(String channel) {
    return _floatingNoticeEventKeys[channel];
  }

  Future<void> _load() async {
    setState(
      () => _applyLoadPatch(
        stickerPacksLoadStarted(
          packs: _packs,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      final packs = await widget.backend.loadPacks();
      if (!mounted) return;
      setState(
        () => _applyLoadPatch(
          stickerPacksLoadSucceeded(
            packs: packs,
            selectedStickerIds: _selectedStickerIds,
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyLoadPatch(
          stickerPacksLoadFailed(
            packs: _packs,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<StickerPack> _ensurePack() async {
    if (_packs.isNotEmpty) return _packs.first;
    final created = await widget.backend.createDefaultPack(sortOrder: 10);
    if (mounted) {
      setState(
        () => _applyLoadPatch(
          stickerPackUpserted(
            packs: _packs,
            selectedStickerIds: _selectedStickerIds,
            pack: created,
            loading: _loading,
            error: _error,
          ),
        ),
      );
    }
    return created;
  }

  Future<void> _upload() async {
    if (!widget.backend.hasApi || _busy || !_capabilities.canUpload) return;
    List<SelectedFile> files;
    try {
      files = await widget.fileSelectionService.openFiles(
        acceptedTypeGroups: _stickerFileTypeGroups,
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyActionPatch(
          stickerActionErrorShown(
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            failure: stickerPickerOpenFailureMessage(e),
            notice: _notice,
          ),
        ),
      );
      return;
    }
    if (files.isEmpty) return;
    setState(
      () => _applyActionPatch(
        stickerActionStarted(
          action: StickerActionKind.upload,
          uploading: _uploading,
          deleting: _deleting,
          savingOrder: _savingOrder,
          downloading: _downloading,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    var uploadedCount = 0;
    try {
      final uploadItems = await stickerUploadItemsFromFiles(
        stickerUploadSourcesFromSelectedFiles(files),
        decodeImageDimensions: decodeStickerImageDimensions,
      );
      if (uploadItems.isEmpty) {
        throw StateError(stickerNoUploadableImagesMessage());
      }
      final pack = await _ensurePack();
      final uploadSortOrders = sticker_ordering.stickerSortOrdersBeforeExisting(
        pack,
        uploadItems.length,
      );
      for (final entry in uploadItems.asMap().entries) {
        final item = entry.value;
        final assetId = await widget.backend.uploadImageAsset(
          bytes: item.bytes,
          filename: stickerUploadFilename(item.filename, entry.key),
          purpose: 'sticker',
        );
        await widget.backend.addSticker(
          packId: pack.id,
          assetId: assetId,
          name: stickerNameFromFilename(item.filename),
          sortOrder: uploadSortOrders[entry.key],
        );
        uploadedCount += 1;
      }
      await _load();
      if (!mounted) return;
      setState(
        () => _applyActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.upload,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            error: _error,
            notice: stickerUploadNotice(scope: _scope, count: uploadedCount),
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyActionPatch(
          stickerActionFailed(
            action: StickerActionKind.upload,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<bool> _deleteItem(ManagedSticker item) async {
    if (!widget.backend.hasApi || _busy || !_capabilities.canDelete) {
      return false;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: stickerDeleteDialogTitle(_scope),
        body: stickerSingleDeleteConfirmBody(
          scope: _scope,
          stickerName: item.sticker.name,
        ),
        confirmLabel: '删除',
        confirmIcon: Icons.delete_outline,
        danger: true,
      ),
    );
    if (confirmed != true || !mounted) return false;
    setState(
      () => _applyActionPatch(
        stickerActionStarted(
          action: StickerActionKind.delete,
          uploading: _uploading,
          deleting: _deleting,
          savingOrder: _savingOrder,
          downloading: _downloading,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      await widget.backend.deleteSticker(
        packId: item.pack.id,
        stickerId: item.sticker.id,
      );
      await _load();
      if (!mounted) return false;
      setState(
        () => _applyActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.delete,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            error: _error,
            notice: stickerDeletedNotice(scope: _scope),
          ),
        ),
      );
      return true;
    } catch (e) {
      if (!mounted) return false;
      setState(
        () => _applyActionPatch(
          stickerActionFailed(
            action: StickerActionKind.delete,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
      return false;
    }
  }

  Future<String?> _renameItem(ManagedSticker item, String name) async {
    final trimmed = stickerRenameName(name);
    if (!widget.backend.hasApi || trimmed == null || !_capabilities.canRename) {
      return null;
    }
    try {
      final actual = await widget.backend.renameSticker(
        packId: item.pack.id,
        stickerId: item.sticker.id,
        name: trimmed,
      );
      await _load();
      return actual;
    } catch (e) {
      if (!mounted) return null;
      setState(
        () => _applyActionPatch(
          stickerActionErrorShown(
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
            notice: _notice,
          ),
        ),
      );
      return null;
    }
  }

  Future<sticker_ordering.StickerPlacementData?> _moveItem(
    ManagedSticker item,
    int delta,
  ) async {
    final placement = _placement(item.sticker.id);
    if (_filterActive) return placement;
    if (!widget.backend.hasApi ||
        placement == null ||
        _busy ||
        !_capabilities.canMove) {
      return placement;
    }
    final ids = sticker_ordering.movedStickerOrder(
      placement.pack,
      item.sticker.id,
      delta,
    );
    if (ids == null) return placement;
    setState(
      () => _applyActionPatch(
        stickerActionStarted(
          action: StickerActionKind.order,
          uploading: _uploading,
          deleting: _deleting,
          savingOrder: _savingOrder,
          downloading: _downloading,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      await widget.backend.reorderStickers(
        packId: placement.pack.id,
        stickerIds: ids,
      );
      await _load();
      if (!mounted) return placement;
      setState(
        () => _applyActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.order,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            error: _error,
            notice: stickerMoveNotice(scope: _scope, delta: delta),
          ),
        ),
      );
      return _placement(item.sticker.id);
    } catch (e) {
      if (!mounted) return placement;
      setState(
        () => _applyActionPatch(
          stickerActionFailed(
            action: StickerActionKind.order,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
      return placement;
    }
  }

  Future<sticker_ordering.StickerPlacementData?> _pinItem(
    ManagedSticker item,
  ) async {
    final placement = _placement(item.sticker.id);
    if (!widget.backend.hasApi ||
        placement == null ||
        placement.index == 0 ||
        _busy ||
        !_capabilities.canPin) {
      return placement;
    }
    final ids = sticker_ordering.pinnedStickerOrder(
      placement.pack,
      item.sticker.id,
    );
    if (ids == null) return placement;
    setState(
      () => _applyActionPatch(
        stickerActionStarted(
          action: StickerActionKind.order,
          uploading: _uploading,
          deleting: _deleting,
          savingOrder: _savingOrder,
          downloading: _downloading,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      await widget.backend.reorderStickers(
        packId: placement.pack.id,
        stickerIds: ids,
      );
      await _load();
      if (!mounted) return placement;
      setState(
        () => _applyActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.order,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            error: _error,
            notice: stickerPinnedNotice(scope: _scope),
          ),
        ),
      );
      return _placement(item.sticker.id);
    } catch (e) {
      if (!mounted) return placement;
      setState(
        () => _applyActionPatch(
          stickerActionFailed(
            action: StickerActionKind.order,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
      return placement;
    }
  }

  Future<void> _downloadIds(List<String> stickerIds) async {
    if (!canStartStickerSelectionAction(
      busy: _busy,
      selectedStickerIds: stickerIds,
      allowed: _capabilities.canDownload,
    )) {
      return;
    }
    setState(
      () => _applyActionPatch(
        stickerActionStarted(
          action: StickerActionKind.download,
          uploading: _uploading,
          deleting: _deleting,
          savingOrder: _savingOrder,
          downloading: _downloading,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      final file = await widget.backend.downloadStickers(
        stickerIds: stickerIds,
      );
      final location = await widget.fileSelectionService.getSaveLocation(
        suggestedName: file.filename,
        acceptedTypeGroups: _stickerFileTypeGroups,
        confirmButtonText: '保存',
      );
      if (location == null) {
        if (!mounted) return;
        setState(
          () => _applyActionPatch(
            stickerActionCancelled(
              action: StickerActionKind.download,
              uploading: _uploading,
              deleting: _deleting,
              savingOrder: _savingOrder,
              downloading: _downloading,
              selectedStickerIds: _selectedStickerIds,
            ),
          ),
        );
        return;
      }
      await widget.fileSelectionService.saveBytesToLocation(
        bytes: file.bytes,
        location: location,
        filename: file.filename,
        mimeType: file.mimeType,
      );
      if (!mounted) return;
      setState(
        () => _applyActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.download,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            error: _error,
            notice: stickerDownloadNotice(
              scope: _scope,
              count: stickerIds.length,
            ),
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyActionPatch(
          stickerActionFailed(
            action: StickerActionKind.download,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _deleteSelected() async {
    final selectedIds = _selectedStickerIds;
    if (!canStartStickerSelectionAction(
      busy: _busy,
      selectedStickerIds: selectedIds,
      allowed: _capabilities.canDelete,
    )) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: stickerDeleteDialogTitle(_scope),
        body: stickerBulkDeleteConfirmBody(
          scope: _scope,
          count: selectedIds.length,
        ),
        confirmLabel: '删除',
        confirmIcon: Icons.delete_outline,
        danger: true,
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(
      () => _applyActionPatch(
        stickerActionStarted(
          action: StickerActionKind.delete,
          uploading: _uploading,
          deleting: _deleting,
          savingOrder: _savingOrder,
          downloading: _downloading,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    var deletedCount = 0;
    try {
      for (final id in selectedIds) {
        final item = managedStickerById(_allItems, id);
        if (item == null) continue;
        await widget.backend.deleteSticker(
          packId: item.pack.id,
          stickerId: item.sticker.id,
        );
        deletedCount += 1;
      }
      await _load();
      if (!mounted) return;
      setState(
        () => _applyActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.delete,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            error: _error,
            notice: stickerDeletedNotice(scope: _scope, count: deletedCount),
            clearSelection: true,
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyActionPatch(
          stickerActionFailed(
            action: StickerActionKind.delete,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _pinSelected() async {
    final selectedIds = _selectedStickerIds;
    if (!canStartStickerSelectionAction(
          busy: _busy,
          selectedStickerIds: selectedIds,
          allowed: _capabilities.canPin,
        ) ||
        !sticker_ordering.stickerSelectionWouldChangePinnedOrder(
          packs: _packs,
          selectedStickerIds: selectedIds,
        )) {
      return;
    }
    final selectedByPack = <String, List<String>>{};
    for (final id in selectedIds) {
      final pack = sticker_ordering.stickerPackForSticker(_packs, id);
      if (pack == null) continue;
      (selectedByPack[pack.id] ??= <String>[]).add(id);
    }
    if (selectedByPack.isEmpty) {
      setState(
        () => _applyActionPatch(
          stickerActionNoticeShown(
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            error: _error,
            notice: stickerNoOrderChangeNotice(_scope),
          ),
        ),
      );
      return;
    }
    setState(
      () => _applyActionPatch(
        stickerActionStarted(
          action: StickerActionKind.order,
          uploading: _uploading,
          deleting: _deleting,
          savingOrder: _savingOrder,
          downloading: _downloading,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      for (final pack in _packs) {
        final selectedInPack = selectedByPack[pack.id];
        if (selectedInPack == null || selectedInPack.isEmpty) continue;
        final nextOrder = sticker_ordering
            .stickerOrderWithStickerIdsPinnedToFront(pack, selectedInPack);
        if (nextOrder == null) continue;
        await widget.backend.reorderStickers(
          packId: pack.id,
          stickerIds: nextOrder,
        );
      }
      await _load();
      if (!mounted) return;
      setState(
        () => _applyActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.order,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            error: _error,
            notice: stickerPinnedNotice(
              scope: _scope,
              count: selectedIds.length,
            ),
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyActionPatch(
          stickerActionFailed(
            action: StickerActionKind.order,
            uploading: _uploading,
            deleting: _deleting,
            savingOrder: _savingOrder,
            downloading: _downloading,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _setAvatar(ManagedSticker item) async {
    final onSetAvatar = widget.backend.onSetAvatar;
    if (onSetAvatar == null || !_capabilities.canSetAvatar) return;
    await onSetAvatar(item);
    if (mounted) await _load();
  }

  void _toggleManageMode() {
    if (!canUseStickerManagementControl(
      busy: _busy,
      allowed: _capabilities.canBatchManage,
    )) {
      return;
    }
    setState(
      () => _applySelectionPatch(
        stickerManagementModeToggled(
          managing: _managing,
          filterKeyword: _filterKeyword,
          filterMimeType: _filterMimeType,
        ),
      ),
    );
  }

  void _toggleSelection(String stickerId) {
    setState(
      () => _applySelectionPatch(
        stickerSelectionToggled(
          managing: _managing,
          filterKeyword: _filterKeyword,
          filterMimeType: _filterMimeType,
          selectedStickerIds: _selectedStickerIds,
          stickerId: stickerId,
        ),
      ),
    );
  }

  void _selectAllVisible(List<ManagedSticker> items) {
    if (!canSelectVisibleStickers(
      busy: _busy,
      visibleItems: items,
      allowed: _capabilities.canSelectAll,
    )) {
      return;
    }
    setState(
      () => _applySelectionPatch(
        stickerVisibleSelectionToggled(
          managing: _managing,
          busy: _busy,
          filterKeyword: _filterKeyword,
          filterMimeType: _filterMimeType,
          selectedStickerIds: _selectedStickerIds,
          visibleItems: items,
        ),
      ),
    );
  }

  Future<void> _openFilter() async {
    if (!canUseStickerManagementControl(
      busy: _busy,
      allowed: _capabilities.canFilter,
    )) {
      return;
    }
    final value = await showDialog<StickerFilterDraft>(
      context: context,
      builder: (context) => StickerFilterDialog(
        keyword: _filterKeyword,
        mimeType: _filterMimeType,
      ),
    );
    if (value == null || !mounted) return;
    setState(
      () => _applySelectionPatch(
        stickerFilterApplied(
          managing: _managing,
          keyword: value.keyword,
          mimeType: value.mimeType,
        ),
      ),
    );
  }

  sticker_ordering.StickerPlacementData? _placement(String stickerId) {
    return sticker_ordering.stickerPlacement(_packs, stickerId);
  }

  void _preview(ManagedSticker item) {
    final imageUrl = AppConfigScope.of(
      context,
    ).resolveAssetUrl(item.sticker.asset.url);
    if (imageUrl == null) return;
    final placement = _placement(item.sticker.id);
    unawaited(
      showDialog<void>(
        context: context,
        builder: (context) => StickerPreviewDialog(
          item: item,
          imageUrl: imageUrl,
          canMoveUp:
              _capabilities.canMove &&
              !_filterActive &&
              (placement?.canMoveUp ?? false),
          canMoveDown:
              _capabilities.canMove &&
              !_filterActive &&
              (placement?.canMoveDown ?? false),
          canPin: _capabilities.canPin && (placement?.canPin ?? false),
          canRename: _capabilities.canRename,
          canDownload: _capabilities.canDownload,
          canDelete: _capabilities.canDelete,
          onRename: (name) => _renameItem(item, name),
          onSetAvatar:
              widget.backend.onSetAvatar == null || !_capabilities.canSetAvatar
              ? null
              : () => _setAvatar(item),
          onDownload: () => _downloadIds([item.sticker.id]),
          onDelete: () => _deleteItem(item),
          onMoveUp: () => _moveItem(item, -1),
          onMoveDown: () => _moveItem(item, 1),
          onPin: () => _pinItem(item),
          imagePreviewOpener: widget.imagePreviewOpener,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final items = _filteredItems;
    final totalCount = _allItems.length;
    final selectionNumbers = _selectionNumbers();
    final busy = _busy;
    final capabilities = _capabilities;
    final allVisibleSelected = stickerAllVisibleSelected(
      selectedStickerIds: _selectedStickerIds,
      visibleItems: items,
    );
    final canPinSelection =
        canStartStickerSelectionAction(
          busy: busy,
          selectedStickerIds: _selectedStickerIds,
          allowed: capabilities.canPin,
        ) &&
        sticker_ordering.stickerSelectionWouldChangePinnedOrder(
          packs: _packs,
          selectedStickerIds: _selectedStickerIds,
        );
    final Widget stickerBody;
    if (_loading && _packs.isEmpty) {
      stickerBody = const Center(
        child: CircularProgressIndicator(color: UiColors.accent),
      );
    } else if (totalCount == 0) {
      stickerBody = const Align(
        alignment: Alignment.topCenter,
        child: SizedBox(
          width: double.infinity,
          child: StickerEmptyState(text: '暂无表情,点击本地上传会自动创建'),
        ),
      );
    } else if (items.isEmpty) {
      stickerBody = const Align(
        alignment: Alignment.topCenter,
        child: SizedBox(
          width: double.infinity,
          child: StickerEmptyState(text: '没有匹配的表情'),
        ),
      );
    } else {
      stickerBody = StickerGrid(
        key: const ValueKey('sticker-manager-items-scroll'),
        items: items,
        managing: _managing,
        selectionNumbers: selectionNumbers,
        busy: busy,
        scrollable: true,
        onTap: (item) {
          if (_managing) {
            _toggleSelection(item.sticker.id);
          } else {
            _preview(item);
          }
        },
      );
    }

    return FloatingNoticeEmitter(
      notices: [
        if (_notice != null)
          FloatingNotice(
            message: _notice!,
            tone: FloatingNoticeTone.success,
            eventKey: _floatingNoticeEventKey('notice'),
          ),
        if (_error != null)
          FloatingNotice(
            message: _error!,
            tone: FloatingNoticeTone.error,
            duration: null,
            eventKey: _floatingNoticeEventKey('error'),
          ),
      ],
      child: !widget.backend.hasApi
          ? SettingsList(
              children: [StickerEmptyState(text: widget.unavailableText)],
            )
          : SettingsFixedHeaderCard(
              title: widget.title,
              spacing: 10,
              trailing: Text(
                stickerManagementCountText(
                  filterActive: _filterActive,
                  visibleCount: items.length,
                  totalCount: totalCount,
                ),
                style: const TextStyle(
                  color: UiColors.textMuted,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
              headerChildren: [
                StickerActionGrid(
                  actions: [
                    StickerActionGridEntry(
                      label: _managing ? '删除' : '本地上传',
                      button: Button(
                        onPressed:
                            canStartStickerPrimaryAction(
                              busy: busy,
                              allowed: _managing
                                  ? capabilities.canDelete
                                  : capabilities.canUpload,
                            )
                            ? _managing
                                  ? _deleteSelected
                                  : _upload
                            : null,
                        loading: _managing ? _deleting : _uploading,
                        tone: _managing
                            ? ButtonTone.danger
                            : ButtonTone.primary,
                        icon: Icon(
                          _managing ? Icons.delete_outline : Icons.upload_file,
                        ),
                        width: double.infinity,
                        child: Text(_managing ? '删除' : '本地上传'),
                      ),
                    ),
                    StickerActionGridEntry(
                      label: _managing ? '取消管理' : '批量管理',
                      button: Button(
                        onPressed:
                            canUseStickerManagementControl(
                              busy: busy,
                              allowed: capabilities.canBatchManage,
                            )
                            ? _toggleManageMode
                            : null,
                        selected: _managing,
                        tone: _managing
                            ? ButtonTone.primary
                            : ButtonTone.neutral,
                        icon: Icon(
                          _managing ? Icons.close : Icons.checklist_rtl,
                        ),
                        width: double.infinity,
                        child: Text(_managing ? '取消管理' : '批量管理'),
                      ),
                    ),
                    StickerActionGridEntry(
                      label: '筛选',
                      button: Button(
                        onPressed:
                            canUseStickerManagementControl(
                              busy: busy,
                              allowed: capabilities.canFilter,
                            )
                            ? _openFilter
                            : null,
                        selected: _filterActive,
                        tone: _filterActive
                            ? ButtonTone.primary
                            : ButtonTone.neutral,
                        icon: const Icon(Icons.filter_alt_outlined),
                        width: double.infinity,
                        child: const Text('筛选'),
                      ),
                    ),
                    if (_managing) ...[
                      StickerActionGridEntry(
                        label: '下载',
                        button: Button(
                          onPressed:
                              canStartStickerSelectionAction(
                                busy: busy,
                                selectedStickerIds: _selectedStickerIds,
                                allowed: capabilities.canDownload,
                              )
                              ? () => _downloadIds(_selectedStickerIds)
                              : null,
                          loading: _downloading,
                          icon: const Icon(Icons.download_outlined),
                          width: double.infinity,
                          child: const Text('下载'),
                        ),
                      ),
                      StickerActionGridEntry(
                        label: '置顶',
                        button: Button(
                          onPressed: canPinSelection ? _pinSelected : null,
                          loading: _savingOrder,
                          icon: const Icon(Icons.vertical_align_top),
                          width: double.infinity,
                          child: const Text('置顶'),
                        ),
                      ),
                      StickerActionGridEntry(
                        label: stickerVisibleSelectionButtonText(
                          selectedStickerIds: _selectedStickerIds,
                          visibleItems: items,
                        ),
                        button: Button(
                          onPressed:
                              canSelectVisibleStickers(
                                busy: busy,
                                visibleItems: items,
                                allowed: capabilities.canSelectAll,
                              )
                              ? () => _selectAllVisible(items)
                              : null,
                          selected: allVisibleSelected,
                          icon: Icon(
                            allVisibleSelected
                                ? Icons.check_box
                                : Icons.check_box_outline_blank,
                          ),
                          width: double.infinity,
                          child: Text(
                            stickerVisibleSelectionButtonText(
                              selectedStickerIds: _selectedStickerIds,
                              visibleItems: items,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ],
              body: stickerBody,
            ),
    );
  }
}

// 表情包专用配色:这两个颜色不在通用 token 中,保留本地常量以维持原视觉。
const _stickerAccent = Color(0xFF6FCFA6);
const _stickerDangerBorder = Color(0xFF3A2A2E);

class StickerActionGridEntry {
  const StickerActionGridEntry({required this.label, required this.button});

  final String label;
  final Button button;
}

/// Keeps sticker actions at three columns while every icon and label fits,
/// then switches the complete action set to two columns.
class StickerActionGrid extends StatelessWidget {
  const StickerActionGrid({super.key, required this.actions});

  static const double _gap = 10;

  final List<StickerActionGridEntry> actions;

  @override
  Widget build(BuildContext context) {
    if (actions.isEmpty) return const SizedBox.shrink();
    return LayoutBuilder(
      builder: (context, constraints) {
        final buttonWidthsWithIcons = actions
            .map(
              (action) => Button.minimumWidthForLabel(
                context,
                label: action.label,
                hasIcon: action.button.icon != null,
                padding: action.button.padding,
              ),
            )
            .toList(growable: false);
        final maximumButtonWidth = buttonWidthsWithIcons.reduce(math.max);
        final threeColumnWidth = (maximumButtonWidth * 3) + (_gap * 2);
        final columns =
            actions.length > 2 &&
                constraints.maxWidth.isFinite &&
                constraints.maxWidth < threeColumnWidth
            ? 2
            : math.min(3, actions.length);
        final availableWidth = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : threeColumnWidth;
        final itemWidth = (availableWidth - (_gap * (columns - 1))) / columns;
        final hideIcons =
            columns == 2 &&
            buttonWidthsWithIcons.any(
              (minimumWidth) => minimumWidth > itemWidth,
            );
        return Wrap(
          key: const ValueKey('sticker-action-grid'),
          spacing: _gap,
          runSpacing: _gap,
          children: [
            for (final action in actions)
              SizedBox(
                width: itemWidth,
                child: hideIcons ? action.button.withoutIcon() : action.button,
              ),
          ],
        );
      },
    );
  }
}

/// 空状态文案。
class StickerEmptyState extends StatelessWidget {
  const StickerEmptyState({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 74,
      child: Center(
        child: Text(
          text,
          textAlign: TextAlign.center,
          style: const TextStyle(color: UiColors.textMuted, fontSize: 13),
        ),
      ),
    );
  }
}

class _StickerFieldLabel extends StatelessWidget {
  const _StickerFieldLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: const TextStyle(
        color: UiColors.textMuted,
        fontSize: 12,
        fontWeight: FontWeight.w600,
      ),
    );
  }
}

/// 表情网格。
class StickerGrid extends StatelessWidget {
  const StickerGrid({
    super.key,
    required this.items,
    required this.managing,
    required this.selectionNumbers,
    required this.busy,
    required this.onTap,
    this.scrollable = false,
  });

  final List<ManagedSticker> items;
  final bool managing;
  final Map<String, int> selectionNumbers;
  final bool busy;
  final ValueChanged<ManagedSticker> onTap;
  final bool scrollable;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : 360.0;
        final columns = (width / 92).floor().clamp(3, 9);
        return GridView.builder(
          shrinkWrap: !scrollable,
          physics: scrollable ? null : const NeverScrollableScrollPhysics(),
          itemCount: items.length,
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            mainAxisSpacing: 10,
            crossAxisSpacing: 10,
            childAspectRatio: 1,
          ),
          itemBuilder: (context, index) {
            final item = items[index];
            return _StickerGridTile(
              item: item,
              managing: managing,
              selectionNumber: selectionNumbers[item.sticker.id],
              busy: busy,
              onTap: () => onTap(item),
            );
          },
        );
      },
    );
  }
}

class _StickerGridTile extends StatelessWidget {
  const _StickerGridTile({
    required this.item,
    required this.managing,
    required this.selectionNumber,
    required this.busy,
    required this.onTap,
  });

  final ManagedSticker item;
  final bool managing;
  final int? selectionNumber;
  final bool busy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final selected = selectionNumber != null;
    return LayoutBuilder(
      builder: (context, constraints) {
        final tileHeight = constraints.maxHeight.isFinite
            ? math.max(54.0, constraints.maxHeight - 6)
            : 86.0;
        return UiTooltip(
          message: item.sticker.name,
          child: PressableSurface(
            onPressed: busy ? null : onTap,
            selected: selected,
            height: tileHeight,
            padding: const EdgeInsets.all(7),
            backgroundColor: UiColors.background,
            selectedBackgroundColor: UiColors.selected,
            pressedBackgroundColor: UiColors.surfaceLow,
            borderColor: selected ? UiColors.selectedBorder : UiColors.border,
            selectedBorderColor: UiColors.selectedBorder,
            hoverLift: 2,
            baseDepth: 4,
            child: Stack(
              fit: StackFit.expand,
              children: [
                Center(
                  child: _StickerThumbnail(
                    sticker: item.sticker,
                    size: math.min(62, tileHeight - 18),
                  ),
                ),
                if (managing && selected)
                  Positioned.fill(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.24),
                      ),
                      child: Align(
                        alignment: Alignment.topRight,
                        child: Container(
                          margin: const EdgeInsets.all(5),
                          width: 28,
                          height: 28,
                          decoration: BoxDecoration(
                            color: _stickerAccent,
                            border: Border.all(
                              color: UiColors.background,
                              width: 2,
                            ),
                          ),
                          child: Center(
                            child: Text(
                              '$selectionNumber',
                              maxLines: 1,
                              overflow: TextOverflow.clip,
                              style: const TextStyle(
                                color: UiColors.background,
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _StickerThumbnail extends StatelessWidget {
  const _StickerThumbnail({required this.sticker, required this.size});

  final Sticker sticker;
  final double size;

  @override
  Widget build(BuildContext context) {
    final asset = sticker.asset;
    final imageUrl = AppConfigScope.of(
      context,
    ).resolveAssetUrl(asset.thumbnailUrl ?? asset.url);
    final fallback = ColoredBox(
      color: UiColors.surfaceLow,
      child: Center(
        child: Icon(
          Icons.image_not_supported_outlined,
          color: UiColors.textMuted,
          size: size * 0.38,
        ),
      ),
    );
    return SizedBox.square(
      dimension: size,
      child: DecoratedBox(
        decoration: BoxDecoration(border: Border.all(color: UiColors.border)),
        child: ClipRect(
          child: imageUrl == null
              ? fallback
              : CachedAssetImage(
                  url: imageUrl,
                  filename: asset.filename,
                  mimeType: asset.mimeType,
                  expectedBytes: asset.sizeBytes,
                  fit: BoxFit.contain,
                  errorBuilder: (_, _, _) => fallback,
                ),
        ),
      ),
    );
  }
}

class _StickerMimeFilter {
  const _StickerMimeFilter(this.mimeType, this.label);

  final String mimeType;
  final String label;
}

/// 表情筛选对话框。
class StickerFilterDialog extends StatefulWidget {
  const StickerFilterDialog({
    super.key,
    required this.keyword,
    required this.mimeType,
  });

  final String keyword;
  final String mimeType;

  @override
  State<StickerFilterDialog> createState() => _StickerFilterDialogState();
}

class _StickerFilterDialogState extends State<StickerFilterDialog> {
  late final TextEditingController _keywordController;
  final FocusNode _keywordFocusNode = FocusNode();
  late String _mimeType;

  static const _filters = [
    _StickerMimeFilter('', '全部'),
    _StickerMimeFilter('image/png', 'PNG'),
    _StickerMimeFilter('image/jpeg', 'JPG'),
    _StickerMimeFilter('image/webp', 'WebP'),
    _StickerMimeFilter('image/gif', 'GIF'),
  ];

  @override
  void initState() {
    super.initState();
    _keywordController = TextEditingController(text: widget.keyword);
    _mimeType = widget.mimeType;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _keywordFocusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _keywordController.dispose();
    _keywordFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: UiColors.surfaceLow,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(UiRadii.lg)),
        side: BorderSide(color: UiColors.border),
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(22, 20, 22, 18),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                '筛选',
                style: TextStyle(
                  color: UiColors.text,
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 16),
              const _StickerFieldLabel('名称关键字'),
              const SizedBox(height: 8),
              Input(
                controller: _keywordController,
                focusNode: _keywordFocusNode,
                hintText: '',
                showClearButton: true,
                minLines: 1,
                maxLines: 1,
              ),
              const SizedBox(height: 16),
              const _StickerFieldLabel('图片类型'),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final filter in _filters)
                    SizedBox(
                      width: 72,
                      child: PressableSurface(
                        onPressed: () =>
                            setState(() => _mimeType = filter.mimeType),
                        selected: _mimeType == filter.mimeType,
                        height: 36,
                        padding: EdgeInsets.zero,
                        backgroundColor: UiColors.background,
                        selectedBackgroundColor: UiColors.selected,
                        pressedBackgroundColor: UiColors.surfaceLow,
                        borderColor: _mimeType == filter.mimeType
                            ? UiColors.selectedBorder
                            : UiColors.border,
                        selectedBorderColor: UiColors.selectedBorder,
                        child: Center(
                          child: Text(
                            filter.label,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: _mimeType == filter.mimeType
                                  ? _stickerAccent
                                  : UiColors.textSecondary,
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 18),
              ResponsiveDialogActionBar(
                expanded: true,
                actions: [
                  ResponsiveDialogAction(
                    label: '重置',
                    icon: Icons.restart_alt,
                    onPressed: () {
                      _keywordController.clear();
                      setState(() => _mimeType = '');
                    },
                  ),
                  ResponsiveDialogAction(
                    label: '取消',
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                  ResponsiveDialogAction(
                    label: '确认',
                    icon: Icons.check,
                    tone: ButtonTone.primary,
                    onPressed: () => Navigator.of(context).pop(
                      StickerFilterDraft(
                        keyword: _keywordController.text.trim(),
                        mimeType: _mimeType,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 二次确认对话框。
class StickerConfirmDialog extends StatelessWidget {
  const StickerConfirmDialog({
    super.key,
    required this.title,
    required this.body,
    required this.confirmLabel,
    required this.confirmIcon,
    this.danger = false,
  });

  final String title;
  final String body;
  final String confirmLabel;
  final IconData confirmIcon;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: UiColors.surfaceLow,
      shape: RoundedRectangleBorder(
        borderRadius: const BorderRadius.all(Radius.circular(UiRadii.lg)),
        side: BorderSide(
          color: danger ? _stickerDangerBorder : UiColors.border,
        ),
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(22, 20, 22, 18),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                title,
                style: TextStyle(
                  color: danger ? UiColors.danger : UiColors.text,
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                body,
                style: const TextStyle(
                  color: UiColors.textSecondary,
                  fontSize: 13,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 18),
              ResponsiveDialogActionBar(
                actions: [
                  ResponsiveDialogAction(
                    label: '取消',
                    onPressed: () => Navigator.of(context).pop(false),
                  ),
                  ResponsiveDialogAction(
                    label: confirmLabel,
                    icon: confirmIcon,
                    tone: danger ? ButtonTone.danger : ButtonTone.primary,
                    onPressed: () => Navigator.of(context).pop(true),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StickerPreviewImage extends StatefulWidget {
  const _StickerPreviewImage({
    required this.imageUrl,
    required this.asset,
    required this.onOpenPreview,
  });

  final String imageUrl;
  final UploadedAsset asset;
  final VoidCallback? onOpenPreview;

  @override
  State<_StickerPreviewImage> createState() => _StickerPreviewImageState();
}

class _StickerPreviewImageState extends State<_StickerPreviewImage> {
  ImageStream? _stream;
  ImageStreamListener? _listener;
  MediaCacheController? _cache;
  Size? _resolvedSize;
  bool _resolving = false;
  bool _failed = false;
  int _resolveSerial = 0;
  bool _hovered = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final cache = MediaCacheScope.of(context);
    if (!identical(_cache, cache)) {
      _cache = cache;
      _resolvedSize = _assetImageSize();
      _failed = false;
      _resolveIfNeeded();
    }
  }

  @override
  void didUpdateWidget(_StickerPreviewImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.asset != widget.asset ||
        oldWidget.imageUrl != widget.imageUrl) {
      _resolvedSize = _assetImageSize();
      _failed = false;
      _hovered = false;
      _resolveIfNeeded();
    }
  }

  @override
  void dispose() {
    _removeListener();
    super.dispose();
  }

  Size? _assetImageSize() {
    final width = widget.asset.width;
    final height = widget.asset.height;
    if (width == null || height == null || width <= 0 || height <= 0) {
      return null;
    }
    return Size(width.toDouble(), height.toDouble());
  }

  void _resolveIfNeeded() {
    final assetSize = _assetImageSize();
    if (assetSize != null) {
      _removeListener();
      if (_resolvedSize != assetSize || _resolving || _failed) {
        setState(() {
          _resolvedSize = assetSize;
          _resolving = false;
          _failed = false;
        });
      }
      return;
    }

    _removeListener();
    final cache = _cache;
    if (cache == null || _resolving || _failed) return;
    final request = MediaCacheRequest.tryFromUrl(
      url: widget.imageUrl,
      filename: widget.asset.filename,
      mimeType: widget.asset.mimeType,
      expectedBytes: widget.asset.sizeBytes,
    );
    if (request == null) {
      setState(() => _failed = true);
      return;
    }

    final serial = ++_resolveSerial;
    setState(() {
      _resolving = true;
      _failed = false;
    });
    cache
        .getOrDownload(request: request)
        .then((file) {
          if (!mounted || serial != _resolveSerial) return;
          _listenForDimensions(file);
        })
        .catchError((_) {
          if (!mounted || serial != _resolveSerial) return;
          setState(() {
            _resolving = false;
            _failed = true;
          });
        });
  }

  void _listenForDimensions(File file) {
    _removeListener();
    final stream = FileImage(file).resolve(ImageConfiguration.empty);
    final listener = ImageStreamListener(
      (image, _) {
        if (!mounted) return;
        setState(() {
          _resolvedSize = Size(
            image.image.width.toDouble(),
            image.image.height.toDouble(),
          );
          _resolving = false;
          _failed = false;
        });
      },
      onError: (_, _) {
        if (!mounted) return;
        setState(() {
          _resolving = false;
          _failed = true;
        });
      },
    );
    _stream = stream;
    _listener = listener;
    stream.addListener(listener);
  }

  void _removeListener() {
    final stream = _stream;
    final listener = _listener;
    if (stream != null && listener != null) {
      stream.removeListener(listener);
    }
    _stream = null;
    _listener = null;
  }

  @override
  Widget build(BuildContext context) {
    final canOpen = widget.onOpenPreview != null;
    return SizedBox(
      key: const ValueKey('sticker-preview-image'),
      height: 320,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final viewport = Size(constraints.maxWidth, constraints.maxHeight);
          final sourceSize = _resolvedSize;
          if (sourceSize == null) {
            return _buildPlaceholder();
          }
          final imageRect = _imageRectFor(viewport, sourceSize);
          final image = CachedAssetImage(
            url: widget.imageUrl,
            filename: widget.asset.filename,
            mimeType: widget.asset.mimeType,
            expectedBytes: widget.asset.sizeBytes,
            width: imageRect.width,
            height: imageRect.height,
            fit: BoxFit.contain,
            loadingBuilder: (_) => _buildPlaceholder(),
            errorBuilder: (_, _, _) => _buildPlaceholder(error: true),
          );

          return Stack(
            fit: StackFit.expand,
            children: [
              Positioned.fromRect(
                rect: imageRect,
                child: MouseRegion(
                  cursor: canOpen
                      ? SystemMouseCursors.click
                      : MouseCursor.defer,
                  onEnter: (_) {
                    if (canOpen) setState(() => _hovered = true);
                  },
                  onExit: (_) {
                    if (canOpen) setState(() => _hovered = false);
                  },
                  child: GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTap: widget.onOpenPreview,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        image,
                        IgnorePointer(
                          child: AnimatedOpacity(
                            opacity: _hovered ? 1 : 0,
                            duration: const Duration(milliseconds: 120),
                            child: Center(
                              child: Icon(
                                Icons.search,
                                color: Colors.white.withValues(alpha: 0.92),
                                size: 36,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Rect _imageRectFor(Size viewport, Size sourceSize) {
    final fitted = applyBoxFit(BoxFit.contain, sourceSize, viewport);
    return Alignment.center.inscribe(
      fitted.destination,
      Offset.zero & viewport,
    );
  }

  Widget _buildPlaceholder({bool error = false}) {
    return Center(
      child: Icon(
        error ? Icons.broken_image_outlined : Icons.image_outlined,
        color: UiColors.textMuted,
        size: 42,
      ),
    );
  }
}

/// 表情预览/编辑对话框。[onSetAvatar] 为空时隐藏“设为头像”(房间场景)。
class StickerPreviewDialog extends StatefulWidget {
  const StickerPreviewDialog({
    super.key,
    required this.item,
    required this.imageUrl,
    required this.canMoveUp,
    required this.canMoveDown,
    required this.canPin,
    required this.canRename,
    required this.canDownload,
    required this.canDelete,
    required this.onRename,
    required this.onSetAvatar,
    required this.onDownload,
    required this.onDelete,
    required this.onMoveUp,
    required this.onMoveDown,
    required this.onPin,
    this.imagePreviewOpener,
  });

  final ManagedSticker item;
  final String imageUrl;
  final bool canMoveUp;
  final bool canMoveDown;
  final bool canPin;
  final bool canRename;
  final bool canDownload;
  final bool canDelete;
  final Future<String?> Function(String name) onRename;
  final Future<void> Function()? onSetAvatar;
  final Future<void> Function() onDownload;
  final Future<bool> Function() onDelete;
  final Future<sticker_ordering.StickerPlacementData?> Function() onMoveUp;
  final Future<sticker_ordering.StickerPlacementData?> Function() onMoveDown;
  final Future<sticker_ordering.StickerPlacementData?> Function() onPin;
  final StickerImagePreviewOpener? imagePreviewOpener;

  @override
  State<StickerPreviewDialog> createState() => _StickerPreviewDialogState();
}

class _StickerPreviewDialogState extends State<StickerPreviewDialog> {
  late final TextEditingController _nameController;
  late StickerPreviewState _previewState;
  int _errorEventKey = 0;

  Sticker get _sticker => widget.item.sticker;
  bool get _busy => _previewState.busy;
  bool get _canMoveUp => _previewState.canMoveUp;
  bool get _canMoveDown => _previewState.canMoveDown;
  bool get _canPin => _previewState.canPin;
  bool get _savingName => _previewState.savingName;
  bool get _settingAvatar => _previewState.settingAvatar;
  bool get _downloading => _previewState.downloading;
  bool get _deleting => _previewState.deleting;
  bool get _movingUp => _previewState.movingUp;
  bool get _movingDown => _previewState.movingDown;
  bool get _pinning => _previewState.pinning;
  String? get _error => _previewState.error;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: _sticker.name);
    _previewState = StickerPreviewState.initial(
      canMoveUp: widget.canMoveUp,
      canMoveDown: widget.canMoveDown,
      canPin: widget.canPin,
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _saveName() async {
    final name = stickerRenameName(_nameController.text);
    if (name == null || !widget.canRename) return;
    await _runPreviewAction<String?>(
      actionKind: StickerPreviewActionKind.rename,
      name: _nameController.text,
      action: () => widget.onRename(name),
      onSuccess: (actualName) {
        if (actualName == null) {
          setState(() {
            _previewState = stickerPreviewActionFailed(
              state: _previewState,
              action: StickerPreviewActionKind.rename,
              failure: '名称保存失败',
            );
            _errorEventKey++;
          });
        } else {
          _nameController.text = actualName;
          Navigator.of(context).pop();
        }
      },
    );
  }

  Future<void> _setAvatar() async {
    final onSetAvatar = widget.onSetAvatar;
    if (onSetAvatar == null) return;
    await _runPreviewAction<bool>(
      actionKind: StickerPreviewActionKind.setAvatar,
      action: () async {
        await onSetAvatar();
        return true;
      },
    );
  }

  Future<void> _download() async {
    await _runPreviewAction<bool>(
      actionKind: StickerPreviewActionKind.download,
      action: () async {
        await widget.onDownload();
        return true;
      },
    );
  }

  Future<void> _openImagePreview() async {
    final opener = widget.imagePreviewOpener;
    if (opener == null) return;
    await opener(
      context,
      imageUrl: widget.imageUrl,
      suggestedName: _previewSuggestedName,
    );
  }

  String get _previewSuggestedName {
    final asset = _sticker.asset;
    final filename = asset.filename?.trim();
    if (filename != null && filename.isNotEmpty) return filename;
    final rawName = _sticker.name.trim();
    final safeName = rawName
        .replaceAll(RegExp(r'[\\/:*?"<>|\s]+'), '_')
        .replaceAll(RegExp(r'_+'), '_');
    final stem = safeName.isEmpty || safeName == '_' ? 'sticker' : safeName;
    return '$stem.${file_display.imageExtensionForMimeType(asset.mimeType)}';
  }

  Future<void> _delete() async {
    await _runPreviewAction<bool>(
      actionKind: StickerPreviewActionKind.delete,
      action: widget.onDelete,
      onSuccess: (deleted) {
        if (deleted && mounted) Navigator.of(context).pop();
      },
    );
  }

  Future<void> _move({
    required Future<sticker_ordering.StickerPlacementData?> Function() action,
    required StickerPreviewActionKind actionKind,
  }) async {
    await _runPreviewAction<sticker_ordering.StickerPlacementData?>(
      actionKind: actionKind,
      action: action,
      onSuccess: (placement) {
        if (placement == null) return;
        setState(
          () => _previewState = stickerPreviewMoveSucceeded(
            state: _previewState,
            action: actionKind,
            canMoveUp: placement.canMoveUp,
            canMoveDown: placement.canMoveDown,
            canPin: placement.canPin,
          ),
        );
      },
    );
  }

  Future<T?> _runPreviewAction<T>({
    required StickerPreviewActionKind actionKind,
    String? name,
    required Future<T> Function() action,
    void Function(T result)? onSuccess,
  }) async {
    final started = stickerPreviewActionRequested(
      state: _previewState,
      action: actionKind,
      name: name,
    );
    if (started == null) return null;
    setState(() => _previewState = started);
    try {
      final result = await action();
      if (!mounted) return result;
      onSuccess?.call(result);
      return result;
    } catch (e) {
      if (mounted) {
        setState(() {
          _previewState = stickerPreviewActionFailed(
            state: _previewState,
            action: actionKind,
            failure: e,
          );
          _errorEventKey++;
        });
      }
      return null;
    } finally {
      if (mounted) {
        setState(
          () => _previewState = stickerPreviewActionFinished(
            state: _previewState,
            action: actionKind,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final asset = _sticker.asset;
    final showSetAvatar = widget.onSetAvatar != null;
    return Dialog(
      backgroundColor: UiColors.surfaceLow,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(UiRadii.lg)),
        side: BorderSide(color: UiColors.border),
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 640),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(22, 20, 22, 18),
          child: FloatingNoticeEmitter(
            notices: [
              if (_error != null)
                FloatingNotice(
                  message: _error!,
                  tone: FloatingNoticeTone.error,
                  duration: null,
                  eventKey: _errorEventKey,
                ),
            ],
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    const Expanded(
                      child: Text(
                        '表情预览',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: UiColors.text,
                          fontSize: 18,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    ButtonIcon(
                      onPressed: () => Navigator.of(context).pop(),
                      tooltip: '关闭预览',
                      icon: const Icon(Icons.close),
                      size: 32,
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Flexible(
                  child: _StickerPreviewImage(
                    imageUrl: widget.imageUrl,
                    asset: asset,
                    onOpenPreview: widget.imagePreviewOpener == null
                        ? null
                        : () => unawaited(_openImagePreview()),
                  ),
                ),
                const SizedBox(height: 14),
                Input(
                  controller: _nameController,
                  hintText: '名称',
                  enabled: widget.canRename,
                  minLines: 1,
                  maxLines: 1,
                  onSubmitted: widget.canRename
                      ? (_) => unawaited(_saveName())
                      : null,
                ),
                const SizedBox(height: 8),
                _StickerDimensionsLine(asset: asset, imageUrl: widget.imageUrl),
                const SizedBox(height: 16),
                StickerActionGrid(
                  actions: [
                    StickerActionGridEntry(
                      label: '下载',
                      button: Button(
                        onPressed: _busy || !widget.canDownload
                            ? null
                            : _download,
                        loading: _downloading,
                        icon: const Icon(Icons.download_outlined),
                        width: double.infinity,
                        child: const Text('下载'),
                      ),
                    ),
                    if (showSetAvatar)
                      StickerActionGridEntry(
                        label: '设为头像',
                        button: Button(
                          onPressed: _busy ? null : _setAvatar,
                          loading: _settingAvatar,
                          icon: const Icon(Icons.account_circle_outlined),
                          width: double.infinity,
                          child: const Text('设为头像'),
                        ),
                      ),
                    StickerActionGridEntry(
                      label: '保存名称',
                      button: Button(
                        onPressed:
                            canStartStickerRename(
                              busy: _busy,
                              name: _nameController.text,
                              allowed: widget.canRename,
                            )
                            ? _saveName
                            : null,
                        loading: _savingName,
                        tone: ButtonTone.primary,
                        icon: const Icon(Icons.save_outlined),
                        width: double.infinity,
                        child: const Text('保存名称'),
                      ),
                    ),
                    StickerActionGridEntry(
                      label: '置顶',
                      button: Button(
                        onPressed: _busy || !_canPin
                            ? null
                            : () => _move(
                                action: widget.onPin,
                                actionKind: StickerPreviewActionKind.pin,
                              ),
                        loading: _pinning,
                        icon: const Icon(Icons.vertical_align_top),
                        width: double.infinity,
                        child: const Text('置顶'),
                      ),
                    ),
                    StickerActionGridEntry(
                      label: '上移一位',
                      button: Button(
                        onPressed: _busy || !_canMoveUp
                            ? null
                            : () => _move(
                                action: widget.onMoveUp,
                                actionKind: StickerPreviewActionKind.moveUp,
                              ),
                        loading: _movingUp,
                        icon: const Icon(Icons.arrow_upward),
                        width: double.infinity,
                        child: const Text('上移一位'),
                      ),
                    ),
                    StickerActionGridEntry(
                      label: '下移一位',
                      button: Button(
                        onPressed: _busy || !_canMoveDown
                            ? null
                            : () => _move(
                                action: widget.onMoveDown,
                                actionKind: StickerPreviewActionKind.moveDown,
                              ),
                        loading: _movingDown,
                        icon: const Icon(Icons.arrow_downward),
                        width: double.infinity,
                        child: const Text('下移一位'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Button(
                  onPressed: _busy || !widget.canDelete ? null : _delete,
                  loading: _deleting,
                  tone: ButtonTone.danger,
                  icon: const Icon(Icons.delete_outline),
                  width: double.infinity,
                  child: const Text('删除'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _StickerDimensionsLine extends StatefulWidget {
  const _StickerDimensionsLine({required this.asset, required this.imageUrl});

  final UploadedAsset asset;
  final String imageUrl;

  @override
  State<_StickerDimensionsLine> createState() => _StickerDimensionsLineState();
}

class _StickerDimensionsLineState extends State<_StickerDimensionsLine> {
  ImageStream? _stream;
  ImageStreamListener? _listener;
  MediaCacheController? _cache;
  StickerImageDimensions? _resolvedDimensions;
  bool _resolving = false;
  bool _failed = false;
  int _resolveSerial = 0;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final cache = MediaCacheScope.of(context);
    if (!identical(_cache, cache)) {
      _cache = cache;
      _resolvedDimensions = null;
      _failed = false;
      _resolveIfNeeded();
    }
  }

  @override
  void didUpdateWidget(_StickerDimensionsLine oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.asset != widget.asset ||
        oldWidget.imageUrl != widget.imageUrl) {
      _resolvedDimensions = null;
      _failed = false;
      _resolveIfNeeded();
    }
  }

  @override
  void dispose() {
    _removeListener();
    super.dispose();
  }

  void _resolveIfNeeded() {
    final cache = _cache;
    if (widget.asset.width != null && widget.asset.height != null) {
      _removeListener();
      if (_resolving || _resolvedDimensions != null || _failed) {
        setState(() {
          _resolving = false;
          _resolvedDimensions = null;
          _failed = false;
        });
      }
      return;
    }
    _removeListener();
    if (cache == null) return;
    final request = MediaCacheRequest.tryFromUrl(
      url: widget.imageUrl,
      filename: widget.asset.filename,
      mimeType: widget.asset.mimeType,
      expectedBytes: widget.asset.sizeBytes,
    );
    if (request == null) {
      setState(() {
        _resolving = false;
        _failed = true;
      });
      return;
    }
    final serial = ++_resolveSerial;
    setState(() {
      _resolving = true;
      _failed = false;
    });
    cache
        .getOrDownload(request: request)
        .then((file) {
          if (!mounted || serial != _resolveSerial) return;
          _listenForDimensions(file);
        })
        .catchError((_) {
          if (!mounted || serial != _resolveSerial) return;
          setState(() {
            _resolving = false;
            _failed = true;
          });
        });
  }

  void _listenForDimensions(File file) {
    _removeListener();
    final stream = FileImage(file).resolve(ImageConfiguration.empty);
    final listener = ImageStreamListener(
      (image, _) {
        if (!mounted) return;
        setState(() {
          _resolvedDimensions = StickerImageDimensions(
            width: image.image.width,
            height: image.image.height,
          );
          _resolving = false;
          _failed = false;
        });
      },
      onError: (_, _) {
        if (!mounted) return;
        setState(() {
          _resolving = false;
          _failed = true;
        });
      },
    );
    _stream = stream;
    _listener = listener;
    stream.addListener(listener);
  }

  void _removeListener() {
    final stream = _stream;
    final listener = _listener;
    if (stream != null && listener != null) {
      stream.removeListener(listener);
    }
    _stream = null;
    _listener = null;
  }

  @override
  Widget build(BuildContext context) {
    final dimensions = stickerDimensionsText(
      widget.asset,
      resolved: _resolvedDimensions,
      resolving: _resolving,
      failed: _failed,
    );
    return Text(
      '${widget.asset.mimeType} · $dimensions',
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: const TextStyle(
        color: UiColors.textMuted,
        fontSize: 12,
        fontWeight: FontWeight.w500,
      ),
    );
  }
}

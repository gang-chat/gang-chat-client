import { cloneElement, useEffect, useRef, type ReactElement, type ReactNode } from 'react';

import { imageExtensionForMimeType } from '@gang-chat/core/app/file_display';
import { MediaCacheRequest, type MediaCacheController } from '@gang-chat/core/app/media_cache_controller';
import { stickerDimensionsText } from '@gang-chat/core/app/sticker_display';
import {
  StickerActionKind,
  StickerFilterDraft,
  StickerManagementCapabilities,
  StickerPreviewActionKind,
  StickerPreviewState,
  canSelectVisibleStickers,
  canStartStickerPrimaryAction,
  canStartStickerRename,
  canStartStickerSelectionAction,
  canUseStickerManagementControl,
  filteredManagedStickerItems,
  managedStickerById,
  managedStickerItems,
  stickerActionCancelled,
  stickerActionErrorShown,
  stickerActionFailed,
  stickerActionNoticeShown,
  stickerActionStarted,
  stickerActionSucceeded,
  stickerAllVisibleSelected,
  stickerBulkDeleteConfirmBody,
  stickerDeleteDialogTitle,
  stickerDeletedNotice,
  stickerDownloadNotice,
  stickerFilterActive,
  stickerFilterApplied,
  stickerManagementBusy,
  stickerManagementCountText,
  stickerManagementModeToggled,
  stickerMoveNotice,
  stickerNoOrderChangeNotice,
  stickerPackUpserted,
  stickerPacksLoadFailed,
  stickerPacksLoadStarted,
  stickerPacksLoadSucceeded,
  stickerPinnedNotice,
  stickerPreviewActionFailed,
  stickerPreviewActionFinished,
  stickerPreviewActionRequested,
  stickerPreviewMoveSucceeded,
  stickerRenameName,
  stickerSelectionNumbers,
  stickerSelectionToggled,
  stickerSingleDeleteConfirmBody,
  stickerUploadNotice,
  stickerVisibleSelectionButtonText,
  stickerVisibleSelectionToggled,
  type ManagedSticker,
  type StickerActionPatch,
  type StickerManagementScope,
  type StickerPackLoadPatch,
  type StickerSelectionPatch,
} from '@gang-chat/core/app/sticker_management';
import * as sticker_ordering from '@gang-chat/core/app/sticker_ordering';
import {
  StickerImageDimensions,
  stickerNameFromFilename,
  stickerNoUploadableImagesMessage,
  stickerPickerOpenFailureMessage,
  stickerUploadFilename,
  stickerUploadItemsFromFiles,
} from '@gang-chat/core/app/sticker_uploads';
import { unawaited } from '@gang-chat/core/lib/async';
import type { DownloadedFile } from '@gang-chat/core/protocol/api_client';
import type { Sticker, StickerPack, UploadedAsset } from '@gang-chat/core/protocol/models';
import { FileSelectionService, FileTypeGroup, type SelectedFile } from '@gang-chat/core/shell/file_selection_service';
import type { AppConfig } from '@gang-chat/core/config/app_config';

import { EdgeInsets } from '../lib/edge_insets';
import { Center, Column, Expanded, Positioned, Row, SizedBox, Stack, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { FocusNode, TextEditingController } from '../lib/text_editing';
import { useElementSize } from '../lib/use_element_size';
import { ViewState, useViewState } from '../lib/view_state';
import { useAppConfig } from './app_config_scope';
import { Button, ButtonIcon, ButtonTone, type ButtonProps } from './button';
import { BoxFit, CachedAssetImage, cachedMediaImageProvider } from './cached_asset_image';
import {
  FloatingNotice,
  FloatingNoticeEmitter,
  FloatingNoticeTone,
  ResponsiveDialogAction,
  ResponsiveDialogActionBar,
} from './feedback';
import { Icon, Icons, type IconData } from './icon';
import { Input } from './input';
import { useMediaCache } from './media_cache_scope';
import { showDialog, useRoute } from './overlay';
import { SettingsFixedHeaderCard, SettingsList } from './settings_scaffold';
import { decodeStickerImageDimensions, stickerUploadSourcesFromSelectedFiles } from './sticker_upload_adapter';
import { PressableSurface } from './surface';
import { UiTooltip } from './tooltip';
import { UiColors, UiRadii, withAlpha } from './tokens';

/**
 * Opens the full-size viewer for a sticker (Dart `StickerImagePreviewOpener`;
 * the `BuildContext` argument is dropped).
 */
export type StickerImagePreviewOpener = (opts: { imageUrl: string; suggestedName: string; forceSquare?: boolean }) => Promise<void>;

const _stickerFileTypeGroups = [
  new FileTypeGroup({
    label: '图片和 ZIP',
    extensions: ['png', 'jpg', 'jpeg', 'webp', 'gif', 'zip'],
  }),
];

/**
 * 表情包管理的数据来源。个人/房间各自实现,面板只负责编排与展示。
 *
 * 业务逻辑(排序、筛选、上传项解析等)统一复用 `@gang-chat/core/app/sticker_*`
 * 中的纯函数,这里仅暴露最小的副作用接口。
 */
export abstract class StickerManagerBackend {
  /** 用于文案区分个人/房间。 */
  abstract get scope(): StickerManagementScope;

  /** API 是否可用(未登录等情况下为 false)。 */
  abstract get hasApi(): boolean;

  /** 当前数据源允许的操作能力。个人表情默认完整可管理。 */
  get capabilities(): StickerManagementCapabilities {
    return new StickerManagementCapabilities();
  }

  /** 读取当前作用域下的全部表情包。 */
  abstract loadPacks(): Promise<StickerPack[]>;

  /** 没有可用表情包时创建一个默认包。 */
  abstract createDefaultPack(opts?: { sortOrder?: number | null }): Promise<StickerPack>;

  /** 上传图片资源,返回资源 id。 */
  abstract uploadImageAsset(opts: { bytes: Uint8Array; filename: string; purpose: string }): Promise<string>;

  /** 向表情包追加一张表情。 */
  abstract addSticker(opts: { packId: string; assetId: string; name: string; sortOrder?: number | null }): Promise<void>;

  /** 删除一张表情。 */
  abstract deleteSticker(opts: { packId: string; stickerId: string }): Promise<void>;

  /** 重命名表情,返回服务端确认后的名称(失败返回 null)。 */
  abstract renameSticker(opts: { packId: string; stickerId: string; name: string }): Promise<string | null>;

  /** 重新排序表情包内的表情。 */
  abstract reorderStickers(opts: { packId: string; stickerIds: string[] }): Promise<void>;

  /** 下载选中的表情,返回文件数据。 */
  abstract downloadStickers(opts: { stickerIds: string[] }): Promise<DownloadedFile>;

  /** “设为头像”动作。返回 null 表示该作用域不支持(房间)。 */
  get onSetAvatar(): ((item: ManagedSticker) => Promise<void>) | null {
    return null;
  }
}

export interface StickerManagerPanelProps {
  backend: StickerManagerBackend;
  fileSelectionService?: FileSelectionService;
  imagePreviewOpener?: StickerImagePreviewOpener | null;
  title?: string;
  unavailableText?: string;
}

let _defaultFileSelectionService: FileSelectionService | null = null;

function _fileSelectionServiceDefault(): FileSelectionService {
  _defaultFileSelectionService ??= new FileSelectionService();
  return _defaultFileSelectionService;
}

class _StickerManagerPanelState extends ViewState<StickerManagerPanelProps> {
  _packs: StickerPack[] = [];
  _selectedStickerIds: string[] = [];
  _loading = true;
  _uploading = false;
  _deleting = false;
  _savingOrder = false;
  _downloading = false;
  _managing = false;
  _filterKeyword = '';
  _filterMimeType = '';
  _error: string | null = null;
  _notice: string | null = null;
  _floatingNoticeSerial = 0;
  readonly _floatingNoticeEventKeys = new Map<string, number>();
  /** `AppConfigScope.of(context)`; refreshed on every build. */
  _appConfig: AppConfig | null = null;

  get _scope(): StickerManagementScope {
    return this.widget.backend.scope;
  }

  get _capabilities(): StickerManagementCapabilities {
    return this.widget.backend.capabilities;
  }

  get _fileSelectionService(): FileSelectionService {
    return this.widget.fileSelectionService ?? _fileSelectionServiceDefault();
  }

  constructor(props: StickerManagerPanelProps) {
    super();
    // The first build runs before initState, so the no-API state is set up
    // front (Dart set it in initState, before its first build).
    if (!props.backend.hasApi) this._loading = false;
  }

  override initState(): void {
    if (this.widget.backend.hasApi) {
      unawaited(this._load());
    } else {
      this._loading = false;
    }
  }

  get _allItems(): ManagedSticker[] {
    return managedStickerItems(this._packs);
  }

  get _filteredItems(): ManagedSticker[] {
    return filteredManagedStickerItems(this._allItems, { keyword: this._filterKeyword, mimeType: this._filterMimeType });
  }

  get _filterActive(): boolean {
    return stickerFilterActive({ keyword: this._filterKeyword, mimeType: this._filterMimeType });
  }

  get _busy(): boolean {
    return stickerManagementBusy({
      uploading: this._uploading,
      deleting: this._deleting,
      savingOrder: this._savingOrder,
      downloading: this._downloading,
    });
  }

  _selectionNumbers(): Map<string, number> {
    return stickerSelectionNumbers(this._selectedStickerIds);
  }

  _applyLoadPatch(patch: StickerPackLoadPatch): void {
    this._packs = patch.packs;
    this._selectedStickerIds = patch.selectedStickerIds;
    this._loading = patch.loading;
    this._error = patch.error;
    this._markFloatingNoticeEvent('error', this._error);
  }

  _applySelectionPatch(patch: StickerSelectionPatch): void {
    this._managing = patch.managing;
    this._filterKeyword = patch.filterKeyword;
    this._filterMimeType = patch.filterMimeType;
    this._selectedStickerIds = patch.selectedStickerIds;
  }

  _applyActionPatch(patch: StickerActionPatch): void {
    this._uploading = patch.uploading;
    this._deleting = patch.deleting;
    this._savingOrder = patch.savingOrder;
    this._downloading = patch.downloading;
    this._selectedStickerIds = patch.selectedStickerIds;
    this._error = patch.error;
    this._notice = patch.notice;
    this._markFloatingNoticeEvent('error', this._error);
    this._markFloatingNoticeEvent('notice', this._notice);
  }

  _markFloatingNoticeEvent(channel: string, message: string | null): void {
    if (message == null || message.trim().length === 0) return;
    this._floatingNoticeEventKeys.set(channel, ++this._floatingNoticeSerial);
  }

  _floatingNoticeEventKey(channel: string): number | null {
    return this._floatingNoticeEventKeys.get(channel) ?? null;
  }

  /** The action flags every action patch carries over. */
  private get _actionFlags() {
    return {
      uploading: this._uploading,
      deleting: this._deleting,
      savingOrder: this._savingOrder,
      downloading: this._downloading,
      selectedStickerIds: this._selectedStickerIds,
    };
  }

  async _load(): Promise<void> {
    this.setState(() =>
      this._applyLoadPatch(stickerPacksLoadStarted({ packs: this._packs, selectedStickerIds: this._selectedStickerIds })),
    );
    try {
      const packs = await this.widget.backend.loadPacks();
      if (!this.mounted) return;
      this.setState(() =>
        this._applyLoadPatch(stickerPacksLoadSucceeded({ packs, selectedStickerIds: this._selectedStickerIds })),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyLoadPatch(
          stickerPacksLoadFailed({ packs: this._packs, selectedStickerIds: this._selectedStickerIds, failure: e }),
        ),
      );
    }
  }

  async _ensurePack(): Promise<StickerPack> {
    if (this._packs.length > 0) return this._packs[0]!;
    const created = await this.widget.backend.createDefaultPack({ sortOrder: 10 });
    if (this.mounted) {
      this.setState(() =>
        this._applyLoadPatch(
          stickerPackUpserted({
            packs: this._packs,
            selectedStickerIds: this._selectedStickerIds,
            pack: created,
            loading: this._loading,
            error: this._error,
          }),
        ),
      );
    }
    return created;
  }

  _upload = async (): Promise<void> => {
    if (!this.widget.backend.hasApi || this._busy || !this._capabilities.canUpload) return;
    let files: SelectedFile[];
    try {
      files = await this._fileSelectionService.openFiles({ acceptedTypeGroups: _stickerFileTypeGroups });
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyActionPatch(
          stickerActionErrorShown({ ...this._actionFlags, failure: stickerPickerOpenFailureMessage(e), notice: this._notice }),
        ),
      );
      return;
    }
    if (files.length === 0) return;
    this.setState(() => this._applyActionPatch(stickerActionStarted({ action: StickerActionKind.upload, ...this._actionFlags })));
    let uploadedCount = 0;
    try {
      const uploadItems = await stickerUploadItemsFromFiles(stickerUploadSourcesFromSelectedFiles(files), {
        decodeImageDimensions: decodeStickerImageDimensions,
      });
      if (uploadItems.length === 0) {
        throw new Error(stickerNoUploadableImagesMessage());
      }
      const pack = await this._ensurePack();
      const uploadSortOrders = sticker_ordering.stickerSortOrdersBeforeExisting(pack, uploadItems.length);
      for (const [index, item] of uploadItems.entries()) {
        const assetId = await this.widget.backend.uploadImageAsset({
          bytes: item.bytes,
          filename: stickerUploadFilename(item.filename, index),
          purpose: 'sticker',
        });
        await this.widget.backend.addSticker({
          packId: pack.id,
          assetId,
          name: stickerNameFromFilename(item.filename),
          sortOrder: uploadSortOrders[index],
        });
        uploadedCount += 1;
      }
      await this._load();
      if (!this.mounted) return;
      this.setState(() =>
        this._applyActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.upload,
            ...this._actionFlags,
            error: this._error,
            notice: stickerUploadNotice({ scope: this._scope, count: uploadedCount }),
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyActionPatch(stickerActionFailed({ action: StickerActionKind.upload, ...this._actionFlags, failure: e })),
      );
    }
  };

  _deleteItem = async (item: ManagedSticker): Promise<boolean> => {
    if (!this.widget.backend.hasApi || this._busy || !this._capabilities.canDelete) {
      return false;
    }
    const confirmed = await showDialog<boolean>(() => (
      <StickerConfirmDialog
        title={stickerDeleteDialogTitle(this._scope)}
        body={stickerSingleDeleteConfirmBody({ scope: this._scope, stickerName: item.sticker.name })}
        confirmLabel="删除"
        confirmIcon={Icons.delete_outline}
        danger
      />
    ));
    if (confirmed !== true || !this.mounted) return false;
    this.setState(() => this._applyActionPatch(stickerActionStarted({ action: StickerActionKind.delete, ...this._actionFlags })));
    try {
      await this.widget.backend.deleteSticker({ packId: item.pack.id, stickerId: item.sticker.id });
      await this._load();
      if (!this.mounted) return false;
      this.setState(() =>
        this._applyActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.delete,
            ...this._actionFlags,
            error: this._error,
            notice: stickerDeletedNotice({ scope: this._scope }),
          }),
        ),
      );
      return true;
    } catch (e) {
      if (!this.mounted) return false;
      this.setState(() =>
        this._applyActionPatch(stickerActionFailed({ action: StickerActionKind.delete, ...this._actionFlags, failure: e })),
      );
      return false;
    }
  };

  _renameItem = async (item: ManagedSticker, name: string): Promise<string | null> => {
    const trimmed = stickerRenameName(name);
    if (!this.widget.backend.hasApi || trimmed == null || !this._capabilities.canRename) {
      return null;
    }
    try {
      const actual = await this.widget.backend.renameSticker({ packId: item.pack.id, stickerId: item.sticker.id, name: trimmed });
      await this._load();
      return actual;
    } catch (e) {
      if (!this.mounted) return null;
      this.setState(() =>
        this._applyActionPatch(stickerActionErrorShown({ ...this._actionFlags, failure: e, notice: this._notice })),
      );
      return null;
    }
  };

  _moveItem = async (item: ManagedSticker, delta: number): Promise<sticker_ordering.StickerPlacementData | null> => {
    const placement = this._placement(item.sticker.id);
    if (this._filterActive) return placement;
    if (!this.widget.backend.hasApi || placement == null || this._busy || !this._capabilities.canMove) {
      return placement;
    }
    const ids = sticker_ordering.movedStickerOrder(placement.pack, item.sticker.id, delta);
    if (ids == null) return placement;
    this.setState(() => this._applyActionPatch(stickerActionStarted({ action: StickerActionKind.order, ...this._actionFlags })));
    try {
      await this.widget.backend.reorderStickers({ packId: placement.pack.id, stickerIds: ids });
      await this._load();
      if (!this.mounted) return placement;
      this.setState(() =>
        this._applyActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.order,
            ...this._actionFlags,
            error: this._error,
            notice: stickerMoveNotice({ scope: this._scope, delta }),
          }),
        ),
      );
      return this._placement(item.sticker.id);
    } catch (e) {
      if (!this.mounted) return placement;
      this.setState(() =>
        this._applyActionPatch(stickerActionFailed({ action: StickerActionKind.order, ...this._actionFlags, failure: e })),
      );
      return placement;
    }
  };

  _pinItem = async (item: ManagedSticker): Promise<sticker_ordering.StickerPlacementData | null> => {
    const placement = this._placement(item.sticker.id);
    if (!this.widget.backend.hasApi || placement == null || placement.index === 0 || this._busy || !this._capabilities.canPin) {
      return placement;
    }
    const ids = sticker_ordering.pinnedStickerOrder(placement.pack, item.sticker.id);
    if (ids == null) return placement;
    this.setState(() => this._applyActionPatch(stickerActionStarted({ action: StickerActionKind.order, ...this._actionFlags })));
    try {
      await this.widget.backend.reorderStickers({ packId: placement.pack.id, stickerIds: ids });
      await this._load();
      if (!this.mounted) return placement;
      this.setState(() =>
        this._applyActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.order,
            ...this._actionFlags,
            error: this._error,
            notice: stickerPinnedNotice({ scope: this._scope }),
          }),
        ),
      );
      return this._placement(item.sticker.id);
    } catch (e) {
      if (!this.mounted) return placement;
      this.setState(() =>
        this._applyActionPatch(stickerActionFailed({ action: StickerActionKind.order, ...this._actionFlags, failure: e })),
      );
      return placement;
    }
  };

  _downloadIds = async (stickerIds: string[]): Promise<void> => {
    if (!canStartStickerSelectionAction({ busy: this._busy, selectedStickerIds: stickerIds, allowed: this._capabilities.canDownload })) {
      return;
    }
    this.setState(() => this._applyActionPatch(stickerActionStarted({ action: StickerActionKind.download, ...this._actionFlags })));
    try {
      const file = await this.widget.backend.downloadStickers({ stickerIds });
      const location = await this._fileSelectionService.getSaveLocation({
        suggestedName: file.filename,
        acceptedTypeGroups: _stickerFileTypeGroups,
        confirmButtonText: '保存',
      });
      if (location == null) {
        if (!this.mounted) return;
        this.setState(() =>
          this._applyActionPatch(stickerActionCancelled({ action: StickerActionKind.download, ...this._actionFlags })),
        );
        return;
      }
      await this._fileSelectionService.saveBytesToLocation({
        bytes: file.bytes,
        location,
        filename: file.filename,
        mimeType: file.mimeType,
      });
      if (!this.mounted) return;
      this.setState(() =>
        this._applyActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.download,
            ...this._actionFlags,
            error: this._error,
            notice: stickerDownloadNotice({ scope: this._scope, count: stickerIds.length }),
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyActionPatch(stickerActionFailed({ action: StickerActionKind.download, ...this._actionFlags, failure: e })),
      );
    }
  };

  _deleteSelected = async (): Promise<void> => {
    const selectedIds = this._selectedStickerIds;
    if (!canStartStickerSelectionAction({ busy: this._busy, selectedStickerIds: selectedIds, allowed: this._capabilities.canDelete })) {
      return;
    }
    const confirmed = await showDialog<boolean>(() => (
      <StickerConfirmDialog
        title={stickerDeleteDialogTitle(this._scope)}
        body={stickerBulkDeleteConfirmBody({ scope: this._scope, count: selectedIds.length })}
        confirmLabel="删除"
        confirmIcon={Icons.delete_outline}
        danger
      />
    ));
    if (confirmed !== true || !this.mounted) return;
    this.setState(() => this._applyActionPatch(stickerActionStarted({ action: StickerActionKind.delete, ...this._actionFlags })));
    let deletedCount = 0;
    try {
      for (const id of selectedIds) {
        const item = managedStickerById(this._allItems, id);
        if (item == null) continue;
        await this.widget.backend.deleteSticker({ packId: item.pack.id, stickerId: item.sticker.id });
        deletedCount += 1;
      }
      await this._load();
      if (!this.mounted) return;
      this.setState(() =>
        this._applyActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.delete,
            ...this._actionFlags,
            error: this._error,
            notice: stickerDeletedNotice({ scope: this._scope, count: deletedCount }),
            clearSelection: true,
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyActionPatch(stickerActionFailed({ action: StickerActionKind.delete, ...this._actionFlags, failure: e })),
      );
    }
  };

  _pinSelected = async (): Promise<void> => {
    const selectedIds = this._selectedStickerIds;
    if (
      !canStartStickerSelectionAction({ busy: this._busy, selectedStickerIds: selectedIds, allowed: this._capabilities.canPin }) ||
      !sticker_ordering.stickerSelectionWouldChangePinnedOrder({ packs: this._packs, selectedStickerIds: selectedIds })
    ) {
      return;
    }
    const selectedByPack = new Map<string, string[]>();
    for (const id of selectedIds) {
      const pack = sticker_ordering.stickerPackForSticker(this._packs, id);
      if (pack == null) continue;
      const list = selectedByPack.get(pack.id) ?? [];
      list.push(id);
      selectedByPack.set(pack.id, list);
    }
    if (selectedByPack.size === 0) {
      this.setState(() =>
        this._applyActionPatch(
          stickerActionNoticeShown({ ...this._actionFlags, error: this._error, notice: stickerNoOrderChangeNotice(this._scope) }),
        ),
      );
      return;
    }
    this.setState(() => this._applyActionPatch(stickerActionStarted({ action: StickerActionKind.order, ...this._actionFlags })));
    try {
      for (const pack of this._packs) {
        const selectedInPack = selectedByPack.get(pack.id);
        if (selectedInPack == null || selectedInPack.length === 0) continue;
        const nextOrder = sticker_ordering.stickerOrderWithStickerIdsPinnedToFront(pack, selectedInPack);
        if (nextOrder == null) continue;
        await this.widget.backend.reorderStickers({ packId: pack.id, stickerIds: nextOrder });
      }
      await this._load();
      if (!this.mounted) return;
      this.setState(() =>
        this._applyActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.order,
            ...this._actionFlags,
            error: this._error,
            notice: stickerPinnedNotice({ scope: this._scope, count: selectedIds.length }),
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyActionPatch(stickerActionFailed({ action: StickerActionKind.order, ...this._actionFlags, failure: e })),
      );
    }
  };

  _setAvatar = async (item: ManagedSticker): Promise<void> => {
    const onSetAvatar = this.widget.backend.onSetAvatar;
    if (onSetAvatar == null || !this._capabilities.canSetAvatar) return;
    await onSetAvatar(item);
    if (this.mounted) await this._load();
  };

  _toggleManageMode = (): void => {
    if (!canUseStickerManagementControl({ busy: this._busy, allowed: this._capabilities.canBatchManage })) {
      return;
    }
    this.setState(() =>
      this._applySelectionPatch(
        stickerManagementModeToggled({
          managing: this._managing,
          filterKeyword: this._filterKeyword,
          filterMimeType: this._filterMimeType,
        }),
      ),
    );
  };

  _toggleSelection(stickerId: string): void {
    this.setState(() =>
      this._applySelectionPatch(
        stickerSelectionToggled({
          managing: this._managing,
          filterKeyword: this._filterKeyword,
          filterMimeType: this._filterMimeType,
          selectedStickerIds: this._selectedStickerIds,
          stickerId,
        }),
      ),
    );
  }

  _selectAllVisible(items: ManagedSticker[]): void {
    if (!canSelectVisibleStickers({ busy: this._busy, visibleItems: items, allowed: this._capabilities.canSelectAll })) {
      return;
    }
    this.setState(() =>
      this._applySelectionPatch(
        stickerVisibleSelectionToggled({
          managing: this._managing,
          busy: this._busy,
          filterKeyword: this._filterKeyword,
          filterMimeType: this._filterMimeType,
          selectedStickerIds: this._selectedStickerIds,
          visibleItems: items,
        }),
      ),
    );
  }

  _openFilter = async (): Promise<void> => {
    if (!canUseStickerManagementControl({ busy: this._busy, allowed: this._capabilities.canFilter })) {
      return;
    }
    const value = await showDialog<StickerFilterDraft>(() => (
      <StickerFilterDialog keyword={this._filterKeyword} mimeType={this._filterMimeType} />
    ));
    if (value == null || !this.mounted) return;
    this.setState(() =>
      this._applySelectionPatch(stickerFilterApplied({ managing: this._managing, keyword: value.keyword, mimeType: value.mimeType })),
    );
  };

  _placement(stickerId: string): sticker_ordering.StickerPlacementData | null {
    return sticker_ordering.stickerPlacement(this._packs, stickerId);
  }

  _preview(item: ManagedSticker): void {
    const imageUrl = this._appConfig?.resolveAssetUrl(item.sticker.asset.url) ?? null;
    if (imageUrl == null) return;
    const placement = this._placement(item.sticker.id);
    const capabilities = this._capabilities;
    const filterActive = this._filterActive;
    const onSetAvatar = this.widget.backend.onSetAvatar;
    const imagePreviewOpener = this.widget.imagePreviewOpener ?? null;
    unawaited(
      showDialog<void>(() => (
        <StickerPreviewDialog
          item={item}
          imageUrl={imageUrl}
          canMoveUp={capabilities.canMove && !filterActive && (placement?.canMoveUp ?? false)}
          canMoveDown={capabilities.canMove && !filterActive && (placement?.canMoveDown ?? false)}
          canPin={capabilities.canPin && (placement?.canPin ?? false)}
          canRename={capabilities.canRename}
          canDownload={capabilities.canDownload}
          canDelete={capabilities.canDelete}
          onRename={(name) => this._renameItem(item, name)}
          onSetAvatar={onSetAvatar == null || !capabilities.canSetAvatar ? null : () => this._setAvatar(item)}
          onDownload={() => this._downloadIds([item.sticker.id])}
          onDelete={() => this._deleteItem(item)}
          onMoveUp={() => this._moveItem(item, -1)}
          onMoveDown={() => this._moveItem(item, 1)}
          onPin={() => this._pinItem(item)}
          imagePreviewOpener={imagePreviewOpener}
        />
      )),
    );
  }
}

/** 通用表情包管理面板。个人设置与房间设置共用同一套交互与视觉。 */
export function StickerManagerPanel(props: StickerManagerPanelProps) {
  const state = useViewState(() => new _StickerManagerPanelState(props), props);
  state._appConfig = useAppConfig();
  const { backend, title = '表情包管理', unavailableText = '表情包需要登录后从服务端读取' } = props;

  const items = state._filteredItems;
  const totalCount = state._allItems.length;
  const selectionNumbers = state._selectionNumbers();
  const busy = state._busy;
  const capabilities = state._capabilities;
  const managing = state._managing;
  const selectedStickerIds = state._selectedStickerIds;
  const filterActive = state._filterActive;
  const allVisibleSelected = stickerAllVisibleSelected({ selectedStickerIds, visibleItems: items });
  const canPinSelection =
    canStartStickerSelectionAction({ busy, selectedStickerIds, allowed: capabilities.canPin }) &&
    sticker_ordering.stickerSelectionWouldChangePinnedOrder({ packs: state._packs, selectedStickerIds });

  let stickerBody: ReactNode;
  if (state._loading && state._packs.length === 0) {
    stickerBody = (
      <Center style={{ flex: '1 1 0' }}>
        <CircularProgressIndicator color={UiColors.accent} />
      </Center>
    );
  } else if (totalCount === 0) {
    stickerBody = <StickerEmptyState text="暂无表情,点击本地上传会自动创建" />;
  } else if (items.length === 0) {
    stickerBody = <StickerEmptyState text="没有匹配的表情" />;
  } else {
    stickerBody = (
      <StickerGrid
        items={items}
        managing={managing}
        selectionNumbers={selectionNumbers}
        busy={busy}
        scrollable
        onTap={(item) => {
          if (state._managing) {
            state._toggleSelection(item.sticker.id);
          } else {
            state._preview(item);
          }
        }}
      />
    );
  }

  const actions: StickerActionGridEntry[] = [
    new StickerActionGridEntry({
      label: managing ? '删除' : '本地上传',
      button: (
        <Button
          onPressed={
            canStartStickerPrimaryAction({ busy, allowed: managing ? capabilities.canDelete : capabilities.canUpload })
              ? managing
                ? () => void state._deleteSelected()
                : () => void state._upload()
              : null
          }
          loading={managing ? state._deleting : state._uploading}
          tone={managing ? ButtonTone.danger : ButtonTone.primary}
          icon={<Icon icon={managing ? Icons.delete_outline : Icons.upload_file} />}
          width={Number.POSITIVE_INFINITY}
        >
          {managing ? '删除' : '本地上传'}
        </Button>
      ),
    }),
    new StickerActionGridEntry({
      label: managing ? '取消管理' : '批量管理',
      button: (
        <Button
          onPressed={canUseStickerManagementControl({ busy, allowed: capabilities.canBatchManage }) ? state._toggleManageMode : null}
          selected={managing}
          tone={managing ? ButtonTone.primary : ButtonTone.neutral}
          icon={<Icon icon={managing ? Icons.close : Icons.checklist_rtl} />}
          width={Number.POSITIVE_INFINITY}
        >
          {managing ? '取消管理' : '批量管理'}
        </Button>
      ),
    }),
    new StickerActionGridEntry({
      label: '筛选',
      button: (
        <Button
          onPressed={canUseStickerManagementControl({ busy, allowed: capabilities.canFilter }) ? () => void state._openFilter() : null}
          selected={filterActive}
          tone={filterActive ? ButtonTone.primary : ButtonTone.neutral}
          icon={<Icon icon={Icons.filter_alt_outlined} />}
          width={Number.POSITIVE_INFINITY}
        >
          筛选
        </Button>
      ),
    }),
  ];
  if (managing) {
    actions.push(
      new StickerActionGridEntry({
        label: '下载',
        button: (
          <Button
            onPressed={
              canStartStickerSelectionAction({ busy, selectedStickerIds, allowed: capabilities.canDownload })
                ? () => void state._downloadIds(state._selectedStickerIds)
                : null
            }
            loading={state._downloading}
            icon={<Icon icon={Icons.download_outlined} />}
            width={Number.POSITIVE_INFINITY}
          >
            下载
          </Button>
        ),
      }),
      new StickerActionGridEntry({
        label: '置顶',
        button: (
          <Button
            onPressed={canPinSelection ? () => void state._pinSelected() : null}
            loading={state._savingOrder}
            icon={<Icon icon={Icons.vertical_align_top} />}
            width={Number.POSITIVE_INFINITY}
          >
            置顶
          </Button>
        ),
      }),
      new StickerActionGridEntry({
        label: stickerVisibleSelectionButtonText({ selectedStickerIds, visibleItems: items }),
        button: (
          <Button
            onPressed={
              canSelectVisibleStickers({ busy, visibleItems: items, allowed: capabilities.canSelectAll })
                ? () => state._selectAllVisible(items)
                : null
            }
            selected={allVisibleSelected}
            icon={<Icon icon={allVisibleSelected ? Icons.check_box : Icons.check_box_outline_blank} />}
            width={Number.POSITIVE_INFINITY}
          >
            {stickerVisibleSelectionButtonText({ selectedStickerIds, visibleItems: items })}
          </Button>
        ),
      }),
    );
  }

  const notices: FloatingNotice[] = [];
  if (state._notice != null) {
    notices.push(
      new FloatingNotice({
        message: state._notice,
        tone: FloatingNoticeTone.success,
        eventKey: state._floatingNoticeEventKey('notice'),
      }),
    );
  }
  if (state._error != null) {
    notices.push(
      new FloatingNotice({
        message: state._error,
        tone: FloatingNoticeTone.error,
        duration: null,
        eventKey: state._floatingNoticeEventKey('error'),
      }),
    );
  }

  return (
    <FloatingNoticeEmitter notices={notices}>
      {!backend.hasApi ? (
        <SettingsList>{[<StickerEmptyState key="unavailable" text={unavailableText} />]}</SettingsList>
      ) : (
        <SettingsFixedHeaderCard
          title={title}
          spacing={10}
          trailing={
            <Text softWrap={false} style={{ color: UiColors.textMuted, fontSize: 12, fontWeight: 600 }}>
              {stickerManagementCountText({ filterActive, visibleCount: items.length, totalCount })}
            </Text>
          }
          headerChildren={[<StickerActionGrid key="actions" actions={actions} />]}
          body={stickerBody}
        />
      )}
    </FloatingNoticeEmitter>
  );
}

// 表情包专用配色:这两个颜色不在通用 token 中,保留本地常量以维持原视觉。
const _stickerAccent = UiColors.accentStrong;
const _stickerDangerBorder = UiColors.dangerBorder;

export class StickerActionGridEntry {
  constructor(opts: { label: string; button: ReactElement<ButtonProps> }) {
    this.label = opts.label;
    this.button = opts.button;
  }

  readonly label: string;
  readonly button: ReactElement<ButtonProps>;
}

/**
 * Port of `Button.withoutIcon()`: the same button state and behaviour without
 * its leading icon.
 */
function _buttonWithoutIcon(button: ReactElement<ButtonProps>): ReactElement<ButtonProps> {
  if (button.props.icon == null || button.props.showIcon === false) return button;
  return cloneElement(button, { showIcon: false });
}

const _stickerActionGridGap = 10;

/**
 * Keeps sticker actions at three columns while every icon and label fits,
 * then switches the complete action set to two columns.
 */
export function StickerActionGrid({ actions }: { actions: readonly StickerActionGridEntry[] }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  if (actions.length === 0) return null;
  const buttonWidthsWithIcons = actions.map((action) =>
    Button.minimumWidthForLabel({
      label: action.label,
      hasIcon: action.button.props.icon != null,
      padding: action.button.props.padding,
    }),
  );
  const maximumButtonWidth = Math.max(...buttonWidthsWithIcons);
  const threeColumnWidth = maximumButtonWidth * 3 + _stickerActionGridGap * 2;
  const maxWidth = size?.width ?? Number.POSITIVE_INFINITY;
  const columns = actions.length > 2 && Number.isFinite(maxWidth) && maxWidth < threeColumnWidth ? 2 : Math.min(3, actions.length);
  const availableWidth = Number.isFinite(maxWidth) ? maxWidth : threeColumnWidth;
  const itemWidth = (availableWidth - _stickerActionGridGap * (columns - 1)) / columns;
  const hideIcons = columns === 2 && buttonWidthsWithIcons.some((minimumWidth) => minimumWidth > itemWidth);
  return (
    <div
      ref={ref}
      data-testid="sticker-action-grid"
      style={{ display: 'flex', flexWrap: 'wrap', gap: _stickerActionGridGap, alignSelf: 'stretch' }}
    >
      {actions.map((action, index) => (
        <div key={index} style={{ width: itemWidth, flexShrink: 0 }}>
          {hideIcons ? _buttonWithoutIcon(action.button) : action.button}
        </div>
      ))}
    </div>
  );
}

/** 空状态文案。 */
export function StickerEmptyState({ text }: { text: string }) {
  return (
    <SizedBox height={74} style={{ alignSelf: 'stretch' }}>
      <Center style={{ height: '100%' }}>
        <Text textAlign="center" style={{ color: UiColors.textMuted, fontSize: 13 }}>
          {text}
        </Text>
      </Center>
    </SizedBox>
  );
}

function _StickerFieldLabel({ text }: { text: string }) {
  return (
    <Text maxLines={1} style={{ color: UiColors.textMuted, fontSize: 12, fontWeight: 600 }}>
      {text}
    </Text>
  );
}

export interface StickerGridProps {
  items: readonly ManagedSticker[];
  managing: boolean;
  selectionNumbers: ReadonlyMap<string, number>;
  busy: boolean;
  onTap: (item: ManagedSticker) => void;
  scrollable?: boolean;
}

/** 表情网格。 */
export function StickerGrid({ items, managing, selectionNumbers, busy, onTap, scrollable = false }: StickerGridProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const width = size != null && Number.isFinite(size.width) && size.width > 0 ? size.width : 360;
  const columns = Math.min(9, Math.max(3, Math.floor(width / 92)));
  const spacing = 10;
  const tileSize = Math.max(0, (width - spacing * (columns - 1)) / columns);
  return (
    <div
      ref={ref}
      data-testid="sticker-manager-items-scroll"
      style={scrollable ? { flex: '1 1 0', minHeight: 0, overflowY: 'auto', alignSelf: 'stretch' } : { alignSelf: 'stretch' }}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
          gridAutoRows: tileSize,
          gap: spacing,
        }}
      >
        {items.map((item) => (
          <_StickerGridTile
            key={item.sticker.id}
            item={item}
            managing={managing}
            selectionNumber={selectionNumbers.get(item.sticker.id) ?? null}
            busy={busy}
            tileExtent={tileSize}
            onTap={() => onTap(item)}
          />
        ))}
      </div>
    </div>
  );
}

interface _StickerGridTileProps {
  item: ManagedSticker;
  managing: boolean;
  selectionNumber: number | null;
  busy: boolean;
  /** The grid cell's extent (the LayoutBuilder's `maxHeight`). */
  tileExtent: number;
  onTap: () => void;
}

function _StickerGridTile({ item, managing, selectionNumber, busy, tileExtent, onTap }: _StickerGridTileProps) {
  const selected = selectionNumber != null;
  const tileHeight = Number.isFinite(tileExtent) ? Math.max(54, tileExtent) : 86;
  return (
    <UiTooltip message={item.sticker.name}>
      <PressableSurface
        onPressed={busy ? null : onTap}
        selected={selected}
        height={tileHeight}
        padding={EdgeInsets.all(7)}
        backgroundColor={UiColors.background}
        selectedBackgroundColor={UiColors.selected}
        borderColor={selected ? UiColors.selectedBorder : UiColors.border}
        selectedBorderColor={UiColors.selectedBorder}
      >
        <Stack style={{ flex: '1 1 0', minHeight: 0 }}>
          <Positioned fill>
            <Center style={{ width: '100%', height: '100%' }}>
              <_StickerThumbnail sticker={item.sticker} size={Math.min(62, tileHeight - 18)} />
            </Center>
          </Positioned>
          {managing && selected ? (
            <Positioned fill style={{ backgroundColor: withAlpha(UiColors.black, 0.24) }}>
              <div
                style={{
                  position: 'absolute',
                  top: 5,
                  right: 5,
                  width: 28,
                  height: 28,
                  boxSizing: 'border-box',
                  backgroundColor: _stickerAccent,
                  border: `2px solid ${UiColors.background}`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Text maxLines={1} overflow="clip" style={{ color: UiColors.background, fontSize: 12, fontWeight: 600 }}>
                  {String(selectionNumber)}
                </Text>
              </div>
            </Positioned>
          ) : null}
        </Stack>
      </PressableSurface>
    </UiTooltip>
  );
}

function _StickerThumbnail({ sticker, size }: { sticker: Sticker; size: number }) {
  const appConfig = useAppConfig();
  const asset = sticker.asset;
  const imageUrl = appConfig.resolveAssetUrl(asset.thumbnailUrl ?? asset.url);
  const fallback = (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        backgroundColor: UiColors.surfaceLow,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon icon={Icons.image_not_supported_outlined} color={UiColors.textMuted} size={size * 0.38} />
    </div>
  );
  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0, overflow: 'hidden' }}>
      {/* DecoratedBox paints its border behind the child, so the image (or the
          fallback fill) covers it wherever it is drawn. */}
      <div style={{ position: 'absolute', inset: 0, border: `1px solid ${UiColors.border}`, boxSizing: 'border-box' }} />
      {imageUrl == null ? (
        fallback
      ) : (
        <div style={{ position: 'absolute', inset: 0 }}>
          <CachedAssetImage
            url={imageUrl}
            filename={asset.filename}
            mimeType={asset.mimeType}
            expectedBytes={asset.sizeBytes}
            fit={BoxFit.contain}
            errorBuilder={() => fallback}
          />
        </div>
      )}
    </div>
  );
}

interface _StickerMimeFilter {
  readonly mimeType: string;
  readonly label: string;
}

export interface StickerFilterDialogProps {
  keyword: string;
  mimeType: string;
}

class _StickerFilterDialogState extends ViewState<StickerFilterDialogProps> {
  readonly _keywordController: TextEditingController;
  readonly _keywordFocusNode = new FocusNode();
  _mimeType: string;

  static readonly _filters: readonly _StickerMimeFilter[] = [
    { mimeType: '', label: '全部' },
    { mimeType: 'image/png', label: 'PNG' },
    { mimeType: 'image/jpeg', label: 'JPG' },
    { mimeType: 'image/webp', label: 'WebP' },
    { mimeType: 'image/gif', label: 'GIF' },
  ];

  constructor(props: StickerFilterDialogProps) {
    super();
    // Built before initState (the first build runs before useViewState mounts).
    this._keywordController = new TextEditingController({ text: props.keyword });
    this._mimeType = props.mimeType;
  }

  override initState(): void {
    // addPostFrameCallback.
    requestAnimationFrame(() => {
      if (this.mounted) this._keywordFocusNode.requestFocus();
    });
  }

  override dispose(): void {
    this._keywordController.dispose();
    this._keywordFocusNode.dispose();
  }
}

/** 表情筛选对话框。 */
export function StickerFilterDialog(props: StickerFilterDialogProps) {
  const state = useViewState(() => new _StickerFilterDialogState(props), props);
  const route = useRoute<StickerFilterDraft>();
  return (
    <_StickerDialogShell maxWidth={420}>
      <Text style={{ color: UiColors.text, fontSize: 18, fontWeight: 600 }}>筛选</Text>
      <SizedBox height={16} />
      <_StickerFieldLabel text="名称关键字" />
      <SizedBox height={8} />
      <Input
        controller={state._keywordController}
        focusNode={state._keywordFocusNode}
        hintText=""
        showClearButton
        minLines={1}
        maxLines={1}
      />
      <SizedBox height={16} />
      <_StickerFieldLabel text="图片类型" />
      <SizedBox height={8} />
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {_StickerFilterDialogState._filters.map((filter) => {
          const selected = state._mimeType === filter.mimeType;
          return (
            <SizedBox key={filter.mimeType} width={72}>
              <PressableSurface
                onPressed={() =>
                  state.setState(() => {
                    state._mimeType = filter.mimeType;
                  })
                }
                selected={selected}
                height={36}
                padding={EdgeInsets.zero}
                backgroundColor={UiColors.background}
                selectedBackgroundColor={UiColors.selected}
                pressedBackgroundColor={UiColors.surfaceLow}
                borderColor={selected ? UiColors.selectedBorder : UiColors.border}
                selectedBorderColor={UiColors.selectedBorder}
              >
                <Center>
                  <Text
                    maxLines={1}
                    style={{ color: selected ? _stickerAccent : UiColors.textSecondary, fontSize: 13, fontWeight: 600 }}
                  >
                    {filter.label}
                  </Text>
                </Center>
              </PressableSurface>
            </SizedBox>
          );
        })}
      </div>
      <SizedBox height={18} />
      <ResponsiveDialogActionBar
        expanded
        actions={[
          new ResponsiveDialogAction({
            label: '重置',
            icon: Icons.restart_alt,
            onPressed: () => {
              state._keywordController.clear();
              state.setState(() => {
                state._mimeType = '';
              });
            },
          }),
          new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close() }),
          new ResponsiveDialogAction({
            label: '确认',
            icon: Icons.check,
            tone: ButtonTone.primary,
            onPressed: () =>
              route.close(new StickerFilterDraft({ keyword: state._keywordController.text.trim(), mimeType: state._mimeType })),
          }),
        ]}
      />
    </_StickerDialogShell>
  );
}

export interface StickerConfirmDialogProps {
  title: string;
  body: string;
  confirmLabel: string;
  confirmIcon: IconData;
  danger?: boolean;
}

/** 二次确认对话框。 */
export function StickerConfirmDialog({ title, body, confirmLabel, confirmIcon, danger = false }: StickerConfirmDialogProps) {
  const route = useRoute<boolean>();
  return (
    <_StickerDialogShell maxWidth={420} borderColor={danger ? _stickerDangerBorder : UiColors.border}>
      <Text style={{ color: danger ? UiColors.danger : UiColors.text, fontSize: 18, fontWeight: 600 }}>{title}</Text>
      <SizedBox height={10} />
      <Text style={{ color: UiColors.textSecondary, fontSize: 13, lineHeight: 1.5 }}>{body}</Text>
      <SizedBox height={18} />
      <ResponsiveDialogActionBar
        actions={[
          new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close(false) }),
          new ResponsiveDialogAction({
            label: confirmLabel,
            icon: confirmIcon,
            tone: danger ? ButtonTone.danger : ButtonTone.primary,
            onPressed: () => route.close(true),
          }),
        ]}
      />
    </_StickerDialogShell>
  );
}

/**
 * Port of the Material `Dialog` the sticker dialogs sat in: [UiColors.surfaceLow]
 * with an `lg` rounded border, 40 / 24 inset from the window edges and at
 * least 280 wide.
 */
function _StickerDialogShell({
  maxWidth,
  borderColor = UiColors.border,
  children,
}: {
  maxWidth: number;
  borderColor?: string;
  children: ReactNode;
}) {
  return (
    <Column
      crossAxisAlignment="stretch"
      style={{
        width: maxWidth,
        minWidth: 280,
        maxWidth: 'calc(100vw - 80px)',
        maxHeight: 'calc(100vh - 48px)',
        margin: '0 16px',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: UiRadii.lg,
        border: `1px solid ${borderColor}`,
        // Material paints the shape's side over the child, so the content
        // inset stays 22 / 20 / 22 / 18 from the outer edge.
        padding: '19px 21px 17px',
        boxSizing: 'border-box',
      }}
    >
      {children}
    </Column>
  );
}

// ---------------------------------------------------------------------------
// Image size resolution shared by the preview image and the dimensions line
// (Flutter: `cachedMediaImageProvider(...).resolve(...)` + an
// `ImageStreamListener`). Resolving loads the cached image once to read its
// natural size.

interface _ImageSizeListener {
  cancel(): void;
}

function _listenForImageSize(
  src: string,
  onSize: (size: { width: number; height: number }) => void,
  onError: () => void,
): _ImageSizeListener {
  const image = new Image();
  let active = true;
  image.onload = () => {
    if (active) onSize({ width: image.naturalWidth, height: image.naturalHeight });
  };
  image.onerror = () => {
    if (active) onError();
  };
  image.src = src;
  return {
    cancel() {
      active = false;
      image.onload = null;
      image.onerror = null;
    },
  };
}

interface _StickerPreviewImageProps {
  imageUrl: string;
  asset: UploadedAsset;
  onOpenPreview: (() => void) | null;
}

class _StickerPreviewImageState extends ViewState<_StickerPreviewImageProps> {
  _listener: _ImageSizeListener | null = null;
  _cache: MediaCacheController | null = null;
  _resolvedSize: { width: number; height: number } | null = null;
  _resolving = false;
  _failed = false;
  _resolveSerial = 0;
  _hovered = false;

  constructor(props: _StickerPreviewImageProps) {
    super();
    // Dart resolved this in didChangeDependencies, before the first build.
    this.props = props;
    this._resolvedSize = this._assetImageSize();
  }

  didChangeDependencies(cache: MediaCacheController): void {
    if (this._cache !== cache) {
      this._cache = cache;
      this._resolvedSize = this._assetImageSize();
      this._failed = false;
      this._resolveIfNeeded();
    }
  }

  override didUpdateProps(oldWidget: _StickerPreviewImageProps): void {
    if (oldWidget.asset !== this.widget.asset || oldWidget.imageUrl !== this.widget.imageUrl) {
      this._resolvedSize = this._assetImageSize();
      this._failed = false;
      this._hovered = false;
      this._resolveIfNeeded();
    }
  }

  override dispose(): void {
    this._removeListener();
  }

  _assetImageSize(): { width: number; height: number } | null {
    const width = this.widget.asset.width;
    const height = this.widget.asset.height;
    if (width == null || height == null || width <= 0 || height <= 0) {
      return null;
    }
    return { width, height };
  }

  _resolveIfNeeded(): void {
    const assetSize = this._assetImageSize();
    if (assetSize != null) {
      this._removeListener();
      const current = this._resolvedSize;
      const sameSize = current != null && current.width === assetSize.width && current.height === assetSize.height;
      if (!sameSize || this._resolving || this._failed) {
        this.setState(() => {
          this._resolvedSize = assetSize;
          this._resolving = false;
          this._failed = false;
        });
      }
      return;
    }

    this._removeListener();
    const cache = this._cache;
    if (cache == null || this._resolving || this._failed) return;
    const request = MediaCacheRequest.tryFromUrl({
      url: this.widget.imageUrl,
      filename: this.widget.asset.filename,
      mimeType: this.widget.asset.mimeType,
      expectedBytes: this.widget.asset.sizeBytes,
    });
    if (request == null) {
      this.setState(() => {
        this._failed = true;
      });
      return;
    }

    const serial = ++this._resolveSerial;
    this.setState(() => {
      this._resolving = true;
      this._failed = false;
    });
    cachedMediaImageProvider(cache, request)
      .then((provider) => {
        if (!this.mounted || serial !== this._resolveSerial) return;
        this._listenForDimensions(provider);
      })
      .catch(() => {
        if (!this.mounted || serial !== this._resolveSerial) return;
        this.setState(() => {
          this._resolving = false;
          this._failed = true;
        });
      });
  }

  _listenForDimensions(provider: string): void {
    this._removeListener();
    this._listener = _listenForImageSize(
      provider,
      (size) => {
        if (!this.mounted) return;
        this.setState(() => {
          this._resolvedSize = size;
          this._resolving = false;
          this._failed = false;
        });
      },
      () => {
        if (!this.mounted) return;
        this.setState(() => {
          this._resolving = false;
          this._failed = true;
        });
      },
    );
  }

  _removeListener(): void {
    this._listener?.cancel();
    this._listener = null;
  }
}

function _StickerPreviewImage(props: _StickerPreviewImageProps) {
  const state = useViewState(() => new _StickerPreviewImageState(props), props);
  const cache = useMediaCache();
  useEffect(() => state.didChangeDependencies(cache));
  const ref = useRef<HTMLDivElement | null>(null);
  const viewport = useElementSize(ref);
  const canOpen = props.onOpenPreview != null;
  const sourceSize = state._resolvedSize;

  let content: ReactNode;
  if (sourceSize == null || viewport == null) {
    content = <_StickerPreviewPlaceholder />;
  } else {
    const imageRect = _imageRectFor(viewport, sourceSize);
    content = (
      <Positioned
        left={imageRect.left}
        top={imageRect.top}
        width={imageRect.width}
        height={imageRect.height}
        onMouseEnter={() => {
          if (canOpen) {
            state.setState(() => {
              state._hovered = true;
            });
          }
        }}
        onMouseLeave={() => {
          if (canOpen) {
            state.setState(() => {
              state._hovered = false;
            });
          }
        }}
        onClick={props.onOpenPreview ?? undefined}
        style={{ cursor: canOpen ? SystemMouseCursors.click : undefined }}
      >
        <CachedAssetImage
          url={props.imageUrl}
          filename={props.asset.filename}
          mimeType={props.asset.mimeType}
          expectedBytes={props.asset.sizeBytes}
          width={imageRect.width}
          height={imageRect.height}
          fit={BoxFit.contain}
          loadingBuilder={() => <_StickerPreviewPlaceholder />}
          errorBuilder={() => <_StickerPreviewPlaceholder error />}
        />
        <Positioned
          fill
          style={{
            pointerEvents: 'none',
            opacity: state._hovered ? 1 : 0,
            transition: 'opacity 120ms linear',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Icon icon={Icons.search} color={withAlpha(UiColors.white, 0.92)} size={36} />
        </Positioned>
      </Positioned>
    );
  }

  return (
    // Flexible(SizedBox(height: 320)): the preview yields height when the
    // dialog cannot fit it.
    <div
      ref={ref}
      data-testid="sticker-preview-image"
      style={{ position: 'relative', height: 320, flex: '0 1 320px', minHeight: 0, alignSelf: 'stretch' }}
    >
      {content}
    </div>
  );
}

/** `applyBoxFit(BoxFit.contain, …)` + `Alignment.center.inscribe`. */
function _imageRectFor(
  viewport: { width: number; height: number },
  sourceSize: { width: number; height: number },
): { left: number; top: number; width: number; height: number } {
  if (sourceSize.width <= 0 || sourceSize.height <= 0) return { left: 0, top: 0, width: 0, height: 0 };
  const scale = Math.min(viewport.width / sourceSize.width, viewport.height / sourceSize.height);
  const width = sourceSize.width * scale;
  const height = sourceSize.height * scale;
  return { left: (viewport.width - width) / 2, top: (viewport.height - height) / 2, width, height };
}

function _StickerPreviewPlaceholder({ error = false }: { error?: boolean }) {
  return (
    <Center style={{ position: 'absolute', inset: 0 }}>
      <Icon icon={error ? Icons.broken_image_outlined : Icons.image_outlined} color={UiColors.textMuted} size={42} />
    </Center>
  );
}

export interface StickerPreviewDialogProps {
  item: ManagedSticker;
  imageUrl: string;
  canMoveUp: boolean;
  canMoveDown: boolean;
  canPin: boolean;
  canRename: boolean;
  canDownload: boolean;
  canDelete: boolean;
  onRename: (name: string) => Promise<string | null>;
  onSetAvatar: (() => Promise<void>) | null;
  onDownload: () => Promise<void>;
  onDelete: () => Promise<boolean>;
  onMoveUp: () => Promise<sticker_ordering.StickerPlacementData | null>;
  onMoveDown: () => Promise<sticker_ordering.StickerPlacementData | null>;
  onPin: () => Promise<sticker_ordering.StickerPlacementData | null>;
  imagePreviewOpener?: StickerImagePreviewOpener | null;
}

class _StickerPreviewDialogState extends ViewState<StickerPreviewDialogProps> {
  readonly _nameController: TextEditingController;
  _previewState: StickerPreviewState;
  _errorEventKey = 0;
  /** `Navigator.of(context).pop`; refreshed on every build. */
  _close: () => void = () => {};

  constructor(props: StickerPreviewDialogProps) {
    super();
    // Built before initState (the first build runs before useViewState mounts).
    this._nameController = new TextEditingController({ text: props.item.sticker.name });
    this._previewState = StickerPreviewState.initial({
      canMoveUp: props.canMoveUp,
      canMoveDown: props.canMoveDown,
      canPin: props.canPin,
    });
  }

  get _sticker(): Sticker {
    return this.widget.item.sticker;
  }
  get _busy(): boolean {
    return this._previewState.busy;
  }
  get _canMoveUp(): boolean {
    return this._previewState.canMoveUp;
  }
  get _canMoveDown(): boolean {
    return this._previewState.canMoveDown;
  }
  get _canPin(): boolean {
    return this._previewState.canPin;
  }
  get _savingName(): boolean {
    return this._previewState.savingName;
  }
  get _settingAvatar(): boolean {
    return this._previewState.settingAvatar;
  }
  get _downloading(): boolean {
    return this._previewState.downloading;
  }
  get _deleting(): boolean {
    return this._previewState.deleting;
  }
  get _movingUp(): boolean {
    return this._previewState.movingUp;
  }
  get _movingDown(): boolean {
    return this._previewState.movingDown;
  }
  get _pinning(): boolean {
    return this._previewState.pinning;
  }
  get _error(): string | null {
    return this._previewState.error;
  }

  override dispose(): void {
    this._nameController.dispose();
  }

  _saveName = async (): Promise<void> => {
    const name = stickerRenameName(this._nameController.text);
    if (name == null || !this.widget.canRename) return;
    await this._runPreviewAction<string | null>({
      actionKind: StickerPreviewActionKind.rename,
      name: this._nameController.text,
      action: () => this.widget.onRename(name),
      onSuccess: (actualName) => {
        if (actualName == null) {
          this.setState(() => {
            this._previewState = stickerPreviewActionFailed({
              state: this._previewState,
              action: StickerPreviewActionKind.rename,
              failure: '名称保存失败',
            });
            this._errorEventKey++;
          });
        } else {
          this._nameController.text = actualName;
          this._close();
        }
      },
    });
  };

  _setAvatar = async (): Promise<void> => {
    const onSetAvatar = this.widget.onSetAvatar;
    if (onSetAvatar == null) return;
    await this._runPreviewAction<boolean>({
      actionKind: StickerPreviewActionKind.setAvatar,
      action: async () => {
        await onSetAvatar();
        return true;
      },
    });
  };

  _download = async (): Promise<void> => {
    await this._runPreviewAction<boolean>({
      actionKind: StickerPreviewActionKind.download,
      action: async () => {
        await this.widget.onDownload();
        return true;
      },
    });
  };

  _openImagePreview = async (): Promise<void> => {
    const opener = this.widget.imagePreviewOpener;
    if (opener == null) return;
    await opener({ imageUrl: this.widget.imageUrl, suggestedName: this._previewSuggestedName });
  };

  get _previewSuggestedName(): string {
    const asset = this._sticker.asset;
    const filename = asset.filename?.trim();
    if (filename != null && filename.length > 0) return filename;
    const rawName = this._sticker.name.trim();
    const safeName = rawName.replace(/[\\/:*?"<>|\s]+/g, '_').replace(/_+/g, '_');
    const stem = safeName.length === 0 || safeName === '_' ? 'sticker' : safeName;
    return `${stem}.${imageExtensionForMimeType(asset.mimeType)}`;
  }

  _delete = async (): Promise<void> => {
    await this._runPreviewAction<boolean>({
      actionKind: StickerPreviewActionKind.delete,
      action: this.widget.onDelete,
      onSuccess: (deleted) => {
        if (deleted && this.mounted) this._close();
      },
    });
  };

  async _move(opts: {
    action: () => Promise<sticker_ordering.StickerPlacementData | null>;
    actionKind: StickerPreviewActionKind;
  }): Promise<void> {
    const { action, actionKind } = opts;
    await this._runPreviewAction<sticker_ordering.StickerPlacementData | null>({
      actionKind,
      action,
      onSuccess: (placement) => {
        if (placement == null) return;
        this.setState(() => {
          this._previewState = stickerPreviewMoveSucceeded({
            state: this._previewState,
            action: actionKind,
            canMoveUp: placement.canMoveUp,
            canMoveDown: placement.canMoveDown,
            canPin: placement.canPin,
          });
        });
      },
    });
  }

  async _runPreviewAction<T>(opts: {
    actionKind: StickerPreviewActionKind;
    name?: string | null;
    action: () => Promise<T>;
    onSuccess?: (result: T) => void;
  }): Promise<T | null> {
    const { actionKind, name, action, onSuccess } = opts;
    const started = stickerPreviewActionRequested({ state: this._previewState, action: actionKind, name });
    if (started == null) return null;
    this.setState(() => {
      this._previewState = started;
    });
    try {
      const result = await action();
      if (!this.mounted) return result;
      onSuccess?.(result);
      return result;
    } catch (e) {
      if (this.mounted) {
        this.setState(() => {
          this._previewState = stickerPreviewActionFailed({ state: this._previewState, action: actionKind, failure: e });
          this._errorEventKey++;
        });
      }
      return null;
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._previewState = stickerPreviewActionFinished({ state: this._previewState, action: actionKind });
        });
      }
    }
  }
}

/** 表情预览/编辑对话框。[onSetAvatar] 为空时隐藏“设为头像”(房间场景)。 */
export function StickerPreviewDialog(props: StickerPreviewDialogProps) {
  const state = useViewState(() => new _StickerPreviewDialogState(props), props);
  const route = useRoute<void>();
  state._close = () => route.close();
  const asset = state._sticker.asset;
  const showSetAvatar = props.onSetAvatar != null;
  const busy = state._busy;

  const actions: StickerActionGridEntry[] = [
    new StickerActionGridEntry({
      label: '下载',
      button: (
        <Button
          onPressed={busy || !props.canDownload ? null : () => void state._download()}
          loading={state._downloading}
          icon={<Icon icon={Icons.download_outlined} />}
          width={Number.POSITIVE_INFINITY}
        >
          下载
        </Button>
      ),
    }),
  ];
  if (showSetAvatar) {
    actions.push(
      new StickerActionGridEntry({
        label: '设为头像',
        button: (
          <Button
            onPressed={busy ? null : () => void state._setAvatar()}
            loading={state._settingAvatar}
            icon={<Icon icon={Icons.account_circle_outlined} />}
            width={Number.POSITIVE_INFINITY}
          >
            设为头像
          </Button>
        ),
      }),
    );
  }
  actions.push(
    new StickerActionGridEntry({
      label: '保存名称',
      button: (
        <Button
          onPressed={
            canStartStickerRename({ busy, name: state._nameController.text, allowed: props.canRename })
              ? () => void state._saveName()
              : null
          }
          loading={state._savingName}
          tone={ButtonTone.primary}
          icon={<Icon icon={Icons.save_outlined} />}
          width={Number.POSITIVE_INFINITY}
        >
          保存名称
        </Button>
      ),
    }),
    new StickerActionGridEntry({
      label: '置顶',
      button: (
        <Button
          onPressed={
            busy || !state._canPin ? null : () => void state._move({ action: props.onPin, actionKind: StickerPreviewActionKind.pin })
          }
          loading={state._pinning}
          icon={<Icon icon={Icons.vertical_align_top} />}
          width={Number.POSITIVE_INFINITY}
        >
          置顶
        </Button>
      ),
    }),
    new StickerActionGridEntry({
      label: '上移一位',
      button: (
        <Button
          onPressed={
            busy || !state._canMoveUp
              ? null
              : () => void state._move({ action: props.onMoveUp, actionKind: StickerPreviewActionKind.moveUp })
          }
          loading={state._movingUp}
          icon={<Icon icon={Icons.arrow_upward} />}
          width={Number.POSITIVE_INFINITY}
        >
          上移一位
        </Button>
      ),
    }),
    new StickerActionGridEntry({
      label: '下移一位',
      button: (
        <Button
          onPressed={
            busy || !state._canMoveDown
              ? null
              : () => void state._move({ action: props.onMoveDown, actionKind: StickerPreviewActionKind.moveDown })
          }
          loading={state._movingDown}
          icon={<Icon icon={Icons.arrow_downward} />}
          width={Number.POSITIVE_INFINITY}
        >
          下移一位
        </Button>
      ),
    }),
  );

  const notices: FloatingNotice[] = [];
  if (state._error != null) {
    notices.push(
      new FloatingNotice({ message: state._error, tone: FloatingNoticeTone.error, duration: null, eventKey: state._errorEventKey }),
    );
  }

  return (
    <_StickerDialogShell maxWidth={640}>
      <FloatingNoticeEmitter notices={notices}>
        <Column crossAxisAlignment="stretch" style={{ flex: '0 1 auto', minHeight: 0 }}>
          <Row>
            <Expanded>
              <Text maxLines={1} style={{ color: UiColors.text, fontSize: 18, fontWeight: 600 }}>
                表情预览
              </Text>
            </Expanded>
            <ButtonIcon onPressed={() => route.close()} tooltip="关闭预览" icon={<Icon icon={Icons.close} />} size={32} />
          </Row>
          <SizedBox height={16} />
          <_StickerPreviewImage
            imageUrl={props.imageUrl}
            asset={asset}
            onOpenPreview={props.imagePreviewOpener == null ? null : () => unawaited(state._openImagePreview())}
          />
          <SizedBox height={14} />
          <Input
            controller={state._nameController}
            hintText="名称"
            enabled={props.canRename}
            minLines={1}
            maxLines={1}
            onSubmitted={props.canRename ? () => unawaited(state._saveName()) : null}
          />
          <SizedBox height={8} />
          <_StickerDimensionsLine asset={asset} imageUrl={props.imageUrl} />
          <SizedBox height={16} />
          <StickerActionGrid actions={actions} />
          <SizedBox height={10} />
          <Button
            onPressed={busy || !props.canDelete ? null : () => void state._delete()}
            loading={state._deleting}
            tone={ButtonTone.danger}
            icon={<Icon icon={Icons.delete_outline} />}
            width={Number.POSITIVE_INFINITY}
          >
            删除
          </Button>
        </Column>
      </FloatingNoticeEmitter>
    </_StickerDialogShell>
  );
}

interface _StickerDimensionsLineProps {
  asset: UploadedAsset;
  imageUrl: string;
}

class _StickerDimensionsLineState extends ViewState<_StickerDimensionsLineProps> {
  _listener: _ImageSizeListener | null = null;
  _cache: MediaCacheController | null = null;
  _resolvedDimensions: StickerImageDimensions | null = null;
  _resolving = false;
  _failed = false;
  _resolveSerial = 0;

  didChangeDependencies(cache: MediaCacheController): void {
    if (this._cache !== cache) {
      this._cache = cache;
      this._resolvedDimensions = null;
      this._failed = false;
      this._resolveIfNeeded();
    }
  }

  override didUpdateProps(oldWidget: _StickerDimensionsLineProps): void {
    if (oldWidget.asset !== this.widget.asset || oldWidget.imageUrl !== this.widget.imageUrl) {
      this._resolvedDimensions = null;
      this._failed = false;
      this._resolveIfNeeded();
    }
  }

  override dispose(): void {
    this._removeListener();
  }

  _resolveIfNeeded(): void {
    const cache = this._cache;
    if (this.widget.asset.width != null && this.widget.asset.height != null) {
      this._removeListener();
      if (this._resolving || this._resolvedDimensions != null || this._failed) {
        this.setState(() => {
          this._resolving = false;
          this._resolvedDimensions = null;
          this._failed = false;
        });
      }
      return;
    }
    this._removeListener();
    if (cache == null) return;
    const request = MediaCacheRequest.tryFromUrl({
      url: this.widget.imageUrl,
      filename: this.widget.asset.filename,
      mimeType: this.widget.asset.mimeType,
      expectedBytes: this.widget.asset.sizeBytes,
    });
    if (request == null) {
      this.setState(() => {
        this._resolving = false;
        this._failed = true;
      });
      return;
    }
    const serial = ++this._resolveSerial;
    this.setState(() => {
      this._resolving = true;
      this._failed = false;
    });
    cachedMediaImageProvider(cache, request)
      .then((provider) => {
        if (!this.mounted || serial !== this._resolveSerial) return;
        this._listenForDimensions(provider);
      })
      .catch(() => {
        if (!this.mounted || serial !== this._resolveSerial) return;
        this.setState(() => {
          this._resolving = false;
          this._failed = true;
        });
      });
  }

  _listenForDimensions(provider: string): void {
    this._removeListener();
    this._listener = _listenForImageSize(
      provider,
      (size) => {
        if (!this.mounted) return;
        this.setState(() => {
          this._resolvedDimensions = new StickerImageDimensions({ width: size.width, height: size.height });
          this._resolving = false;
          this._failed = false;
        });
      },
      () => {
        if (!this.mounted) return;
        this.setState(() => {
          this._resolving = false;
          this._failed = true;
        });
      },
    );
  }

  _removeListener(): void {
    this._listener?.cancel();
    this._listener = null;
  }
}

function _StickerDimensionsLine(props: _StickerDimensionsLineProps) {
  const state = useViewState(() => new _StickerDimensionsLineState(), props);
  const cache = useMediaCache();
  useEffect(() => state.didChangeDependencies(cache));
  const dimensions = stickerDimensionsText(props.asset, {
    resolved: state._resolvedDimensions,
    resolving: state._resolving,
    failed: state._failed,
  });
  return (
    <Text maxLines={1} style={{ color: UiColors.textMuted, fontSize: 12, fontWeight: 500 }}>
      {`${props.asset.mimeType} · ${dimensions}`}
    </Text>
  );
}

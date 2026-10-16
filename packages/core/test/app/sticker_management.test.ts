import { describe, expect, it } from 'vitest';

import {
  StickerActionKind,
  StickerFilterDraft,
  StickerManagementCapabilities,
  StickerManagementScope,
  StickerPreviewActionKind,
  StickerPreviewState,
  canSelectVisibleStickers,
  canStartStickerPreviewAction,
  canStartStickerPrimaryAction,
  canStartStickerRename,
  canStartStickerSelectionAction,
  canUseStickerManagementControl,
  defaultStickerPackName,
  filteredManagedStickerItems,
  managedStickerById,
  managedStickerItems,
  retainedStickerSelection,
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
  stickerFilterDraftReset,
  stickerFilterMimeTypeChanged,
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
  stickerPreviewActionStarted,
  stickerPreviewMoveSucceeded,
  stickerRenameName,
  stickerSelectionForVisibleItems,
  stickerSelectionNumbers,
  stickerSelectionToggled,
  stickerSingleDeleteConfirmBody,
  stickerUploadNotice,
  stickerVisibleSelectionButtonText,
  stickerVisibleSelectionToggled,
  toggledStickerSelection,
} from '../../src/app/sticker_management';
import { Sticker, StickerPack, UploadedAsset } from '../../src/protocol/models';

describe('sticker management', () => {
  it('managedStickerItems expands packs and honors draft order', () => {
    const packs = [_pack('pack_1', ['a', 'b', 'c'])];

    const items = managedStickerItems(packs, { orderForPack: () => ['c', 'a'] });

    expect(items.map((item) => item.sticker.id)).toEqual(['c', 'a', 'b']);
    expect(new Set(items.map((item) => item.pack.id))).toEqual(new Set(['pack_1']));
  });

  it('filteredManagedStickerItems matches keyword and mime type', () => {
    const items = managedStickerItems([
      _pack('pack_1', ['happy', 'sad', 'wave'], { mimeTypes: { happy: 'image/gif', wave: 'image/webp' } }),
    ]);

    expect(filteredManagedStickerItems(items, { keyword: ' HAP ', mimeType: '' }).map((item) => item.sticker.id)).toEqual(
      ['happy'],
    );
    expect(
      filteredManagedStickerItems(items, { keyword: '', mimeType: 'image/webp' }).map((item) => item.sticker.id),
    ).toEqual(['wave']);
    expect(filteredManagedStickerItems(items, { keyword: 'sad', mimeType: 'image/gif' })).toEqual([]);
  });

  it('selection helpers keep selection behavior reusable', () => {
    const items = managedStickerItems([_pack('pack_1', ['a', 'b', 'c'])]);
    const firstTwo = items.slice(0, 2);

    expect(stickerFilterActive({ keyword: ' ', mimeType: '' })).toBe(false);
    expect(stickerFilterActive({ keyword: '', mimeType: 'image/png' })).toBe(true);
    expect(stickerSelectionNumbers(['b', 'a'])).toEqual(
      new Map([
        ['b', 1],
        ['a', 2],
      ]),
    );
    expect(toggledStickerSelection(['a'], 'b')).toEqual(['a', 'b']);
    expect(toggledStickerSelection(['a', 'b'], 'a')).toEqual(['b']);
    expect(stickerSelectionForVisibleItems([], firstTwo)).toEqual(['b', 'a']);
    expect(stickerAllVisibleSelected({ selectedStickerIds: ['b', 'a'], visibleItems: firstTwo })).toBe(true);
    expect(stickerVisibleSelectionButtonText({ selectedStickerIds: ['b', 'a'], visibleItems: firstTwo })).toBe('全选');
    expect(stickerSelectionForVisibleItems(['a', 'b'], firstTwo)).toEqual([]);
    expect(stickerAllVisibleSelected({ selectedStickerIds: ['a'], visibleItems: firstTwo })).toBe(false);
    expect(stickerVisibleSelectionButtonText({ selectedStickerIds: ['a'], visibleItems: firstTwo })).toBe('全选');
  });

  it('management state helpers describe counts and action gates', () => {
    const items = managedStickerItems([_pack('pack_1', ['a', 'b'])]);

    expect(stickerManagementBusy({ uploading: false, deleting: false, savingOrder: false, downloading: false })).toBe(false);
    expect(stickerManagementBusy({ uploading: false, deleting: true, savingOrder: false, downloading: false })).toBe(true);
    expect(stickerManagementCountText({ filterActive: false, visibleCount: 1, totalCount: 2 })).toBe('2 个');
    expect(stickerManagementCountText({ filterActive: true, visibleCount: 1, totalCount: 2 })).toBe('1 / 2 个');
    expect(canStartStickerPrimaryAction({ busy: false })).toBe(true);
    expect(canStartStickerPrimaryAction({ busy: true })).toBe(false);
    expect(canStartStickerPrimaryAction({ busy: false, allowed: false })).toBe(false);
    expect(canStartStickerSelectionAction({ busy: false, selectedStickerIds: ['a'] })).toBe(true);
    expect(canStartStickerSelectionAction({ busy: false, selectedStickerIds: ['a'], allowed: false })).toBe(false);
    expect(canStartStickerSelectionAction({ busy: false, selectedStickerIds: [] })).toBe(false);
    expect(canStartStickerSelectionAction({ busy: true, selectedStickerIds: ['a'] })).toBe(false);
    expect(canSelectVisibleStickers({ busy: false, visibleItems: items })).toBe(true);
    expect(canSelectVisibleStickers({ busy: false, visibleItems: [] })).toBe(false);
    expect(canUseStickerManagementControl({ busy: false })).toBe(true);
    expect(canUseStickerManagementControl({ busy: true })).toBe(false);
    expect(canUseStickerManagementControl({ busy: false, allowed: false })).toBe(false);
    const roomReadOnly = StickerManagementCapabilities.readOnlyDownloads();
    expect(roomReadOnly.canUpload).toBe(false);
    expect(roomReadOnly.canBatchManage).toBe(true);
    expect(roomReadOnly.canFilter).toBe(true);
    expect(roomReadOnly.canDownload).toBe(true);
    expect(roomReadOnly.canSelectAll).toBe(true);
    expect(roomReadOnly.canDelete).toBe(false);
    expect(roomReadOnly.canPin).toBe(false);
    expect(roomReadOnly.canRename).toBe(false);
    expect(roomReadOnly.canMove).toBe(false);
    expect(new StickerManagementCapabilities().canSetAvatar).toBe(true);
  });

  it('sticker rename helpers normalize and gate empty names', () => {
    expect(stickerRenameName('  wave  ')).toBe('wave');
    expect(stickerRenameName('   ')).toBeNull();
    expect(canStartStickerRename({ busy: false, name: ' wave ' })).toBe(true);
    expect(canStartStickerRename({ busy: false, name: '   ' })).toBe(false);
    expect(canStartStickerRename({ busy: true, name: 'wave' })).toBe(false);
    expect(canStartStickerRename({ busy: false, name: 'wave', allowed: false })).toBe(false);
  });

  it('sticker preview state gates actions and tracks busy flags', () => {
    const initial = StickerPreviewState.initial({ canMoveUp: true, canMoveDown: false, canPin: true });

    expect(initial.busy).toBe(false);
    expect(canStartStickerPreviewAction({ state: initial, action: StickerPreviewActionKind.rename, name: ' wave ' })).toBe(
      true,
    );
    expect(canStartStickerPreviewAction({ state: initial, action: StickerPreviewActionKind.rename, name: '   ' })).toBe(
      false,
    );

    const requested = stickerPreviewActionRequested({
      state: initial,
      action: StickerPreviewActionKind.rename,
      name: ' wave ',
    });
    expect(requested).not.toBeNull();
    expect(requested!.savingName).toBe(true);
    expect(requested!.error).toBeNull();
    expect(
      stickerPreviewActionRequested({ state: initial, action: StickerPreviewActionKind.rename, name: '   ' }),
    ).toBeNull();

    const downloading = stickerPreviewActionStarted({ state: initial, action: StickerPreviewActionKind.download });

    expect(downloading.busy).toBe(true);
    expect(downloading.downloading).toBe(true);
    expect(downloading.error).toBeNull();
    expect(canStartStickerPreviewAction({ state: downloading, action: StickerPreviewActionKind.delete })).toBe(false);
    expect(stickerPreviewActionRequested({ state: downloading, action: StickerPreviewActionKind.delete })).toBeNull();

    const failed = stickerPreviewActionFailed({
      state: downloading,
      action: StickerPreviewActionKind.download,
      failure: new Error('download failed'),
    });

    expect(failed.busy).toBe(false);
    expect(failed.downloading).toBe(false);
    expect(failed.error).toBe('操作失败，请稍后重试');

    // Finishing keeps the previous error (Dart's unchanged sentinel).
    expect(stickerPreviewActionFinished({ state: failed, action: StickerPreviewActionKind.download }).error).toBe(
      '操作失败，请稍后重试',
    );

    const renaming = stickerPreviewActionStarted({ state: failed, action: StickerPreviewActionKind.rename });

    expect(renaming.savingName).toBe(true);
    expect(renaming.error).toBeNull();

    const finished = stickerPreviewActionFinished({ state: renaming, action: StickerPreviewActionKind.rename });

    expect(finished.savingName).toBe(false);
    expect(finished.error).toBeNull();
  });

  it('sticker preview move success updates placement controls', () => {
    const initial = StickerPreviewState.initial({ canMoveUp: true, canMoveDown: true, canPin: true });
    const moving = stickerPreviewActionStarted({ state: initial, action: StickerPreviewActionKind.pin });

    const succeeded = stickerPreviewMoveSucceeded({
      state: moving,
      action: StickerPreviewActionKind.pin,
      canMoveUp: false,
      canMoveDown: true,
      canPin: false,
    });

    expect(succeeded.pinning).toBe(false);
    expect(succeeded.busy).toBe(false);
    expect(succeeded.canMoveUp).toBe(false);
    expect(succeeded.canMoveDown).toBe(true);
    expect(succeeded.canPin).toBe(false);
    expect(succeeded.error).toBeNull();
  });

  it('personal sticker management copy stays reusable outside UI', () => {
    const scope = StickerManagementScope.personal;
    expect(defaultStickerPackName(scope)).toBe('我的表情包');
    expect(stickerUploadNotice({ scope, count: 3 })).toBe('已添加 3 个表情');
    expect(stickerDeleteDialogTitle(scope)).toBe('删除表情');
    expect(stickerSingleDeleteConfirmBody({ scope, stickerName: 'wave' })).toBe(
      '将从服务端删除「wave」，删除后不会再出现在你的表情包里。',
    );
    expect(stickerBulkDeleteConfirmBody({ scope, count: 2 })).toBe(
      '将从服务端删除选中的 2 个表情，删除后不会再出现在你的表情包里。',
    );
    expect(stickerDeletedNotice({ scope })).toBe('表情已删除');
    expect(stickerDeletedNotice({ scope, count: 2 })).toBe('已删除 2 个表情');
    expect(stickerDownloadNotice({ scope, count: 1 })).toBe('表情已下载');
    expect(stickerDownloadNotice({ scope, count: 2 })).toBe('表情压缩包已下载');
    expect(stickerNoOrderChangeNotice(scope)).toBe('表情排序没有变化');
    expect(stickerPinnedNotice({ scope })).toBe('表情已置顶');
    expect(stickerPinnedNotice({ scope, count: 2 })).toBe('已置顶 2 个表情');
    expect(stickerMoveNotice({ scope, delta: -1 })).toBe('表情已上移一位');
    expect(stickerMoveNotice({ scope, delta: 1 })).toBe('表情已下移一位');
  });

  it('room sticker management copy stays reusable outside UI', () => {
    const scope = StickerManagementScope.room;
    expect(defaultStickerPackName(scope)).toBe('房间表情包');
    expect(stickerUploadNotice({ scope, count: 3 })).toBe('已添加 3 个房间表情');
    expect(stickerDeleteDialogTitle(scope)).toBe('删除房间表情');
    expect(stickerSingleDeleteConfirmBody({ scope, stickerName: 'wave' })).toBe('将从这个房间的表情包中删除「wave」。');
    expect(stickerBulkDeleteConfirmBody({ scope, count: 2 })).toBe('将从这个房间的表情包中删除选中的 2 个表情。');
    expect(stickerDeletedNotice({ scope })).toBe('房间表情已删除');
    expect(stickerDeletedNotice({ scope, count: 2 })).toBe('已删除 2 个房间表情');
    expect(stickerDownloadNotice({ scope, count: 1 })).toBe('房间表情已下载');
    expect(stickerDownloadNotice({ scope, count: 2 })).toBe('房间表情压缩包已下载');
    expect(stickerNoOrderChangeNotice(scope)).toBe('房间表情排序没有变化');
    expect(stickerPinnedNotice({ scope })).toBe('房间表情已置顶');
    expect(stickerPinnedNotice({ scope, count: 2 })).toBe('已置顶 2 个房间表情');
    expect(stickerMoveNotice({ scope, delta: -1 })).toBe('房间表情已上移一位');
    expect(stickerMoveNotice({ scope, delta: 1 })).toBe('房间表情已下移一位');
  });

  it('sticker pack load patches retain current selection safely', () => {
    const currentPacks = [_pack('pack_1', ['a', 'b'])];
    const nextPacks = [_pack('pack_2', ['b', 'c'])];

    const started = stickerPacksLoadStarted({ packs: currentPacks, selectedStickerIds: ['b'] });

    expect(started.packs.map((pack) => pack.id)).toEqual(['pack_1']);
    expect(started.selectedStickerIds).toEqual(['b']);
    expect(started.loading).toBe(true);
    expect(started.error).toBeNull();

    const succeeded = stickerPacksLoadSucceeded({ packs: nextPacks, selectedStickerIds: ['missing', 'b', 'a'] });

    expect(succeeded.packs.map((pack) => pack.id)).toEqual(['pack_2']);
    expect(succeeded.selectedStickerIds).toEqual(['b']);
    expect(succeeded.loading).toBe(false);
    expect(succeeded.error).toBeNull();

    const failed = stickerPacksLoadFailed({
      packs: currentPacks,
      selectedStickerIds: ['a'],
      failure: new Error('network failed'),
    });

    expect(failed.packs.map((pack) => pack.id)).toEqual(['pack_1']);
    expect(failed.selectedStickerIds).toEqual(['a']);
    expect(failed.loading).toBe(false);
    expect(failed.error).toBe('网络连接失败，请检查网络后重试');
  });

  it('sticker pack upsert patch preserves load state and valid selection', () => {
    const currentPacks = [_pack('pack_1', ['a', 'b']), _pack('pack_2', ['c'])];

    const inserted = stickerPackUpserted({
      packs: currentPacks,
      selectedStickerIds: ['missing', 'a', 'd'],
      pack: _pack('pack_3', ['d']),
      loading: true,
      error: 'previous',
    });

    expect(inserted.packs.map((pack) => pack.id)).toEqual(['pack_1', 'pack_2', 'pack_3']);
    expect(inserted.selectedStickerIds).toEqual(['a', 'd']);
    expect(inserted.loading).toBe(true);
    expect(inserted.error).toBe('previous');

    const replaced = stickerPackUpserted({
      packs: currentPacks,
      selectedStickerIds: ['b', 'c', 'x'],
      pack: _pack('pack_1', ['x']),
      loading: false,
      error: null,
    });

    expect(replaced.packs.map((pack) => pack.id)).toEqual(['pack_1', 'pack_2']);
    expect(replaced.packs[0]!.stickers.map((sticker) => sticker.id)).toEqual(['x']);
    expect(replaced.selectedStickerIds).toEqual(['c', 'x']);
    expect(replaced.loading).toBe(false);
    expect(replaced.error).toBeNull();

    const ordered = stickerPackUpserted({
      packs: [_pack('pack_low', ['a'], { sortOrder: 5 }), _pack('pack_high', ['b'], { sortOrder: 30 })],
      selectedStickerIds: [],
      pack: _pack('pack_mid', ['c'], { sortOrder: 20 }),
      loading: false,
      error: null,
    });
    expect(ordered.packs.map((pack) => pack.id)).toEqual(['pack_low', 'pack_mid', 'pack_high']);
  });

  it('sticker selection patches keep selection state reusable', () => {
    const items = managedStickerItems([_pack('pack_1', ['a', 'b', 'c'])]);

    const manageMode = stickerManagementModeToggled({ managing: false, filterKeyword: 'gif', filterMimeType: 'image/gif' });

    expect(manageMode.managing).toBe(true);
    expect(manageMode.filterKeyword).toBe('gif');
    expect(manageMode.filterMimeType).toBe('image/gif');
    expect(manageMode.selectedStickerIds).toEqual([]);

    const ignoredSelection = stickerSelectionToggled({
      managing: false,
      filterKeyword: '',
      filterMimeType: '',
      selectedStickerIds: ['a'],
      stickerId: 'b',
    });

    expect(ignoredSelection.selectedStickerIds).toEqual(['a']);

    const toggledSelection = stickerSelectionToggled({
      managing: true,
      filterKeyword: '',
      filterMimeType: '',
      selectedStickerIds: ['a'],
      stickerId: 'b',
    });

    expect(toggledSelection.selectedStickerIds).toEqual(['a', 'b']);

    const ignoredVisibleSelection = stickerVisibleSelectionToggled({
      managing: true,
      busy: true,
      filterKeyword: '',
      filterMimeType: '',
      selectedStickerIds: ['a'],
      visibleItems: items,
    });

    expect(ignoredVisibleSelection.selectedStickerIds).toEqual(['a']);

    const visibleSelection = stickerVisibleSelectionToggled({
      managing: true,
      busy: false,
      filterKeyword: '',
      filterMimeType: '',
      selectedStickerIds: [],
      visibleItems: items.slice(0, 2),
    });

    expect(visibleSelection.selectedStickerIds).toEqual(['b', 'a']);

    const filter = stickerFilterApplied({ managing: true, keyword: 'webp', mimeType: 'image/webp' });

    expect(filter.managing).toBe(true);
    expect(filter.filterKeyword).toBe('webp');
    expect(filter.filterMimeType).toBe('image/webp');
    expect(filter.selectedStickerIds).toEqual([]);
  });

  it('sticker filter draft patches are shared by filter dialogs', () => {
    const draft = new StickerFilterDraft({ keyword: 'happy', mimeType: 'image/gif' });

    expect(draft.keyword).toBe('happy');
    expect(draft.mimeType).toBe('image/gif');
    expect(stickerFilterMimeTypeChanged({ mimeType: 'image/webp' }).mimeType).toBe('image/webp');
    expect(stickerFilterDraftReset().mimeType).toBe('');
  });

  it('sticker action patches update one busy flag and transient copy', () => {
    const started = stickerActionStarted({
      action: StickerActionKind.upload,
      uploading: false,
      deleting: false,
      savingOrder: true,
      downloading: false,
      selectedStickerIds: ['a'],
    });

    expect(started.uploading).toBe(true);
    expect(started.deleting).toBe(false);
    expect(started.savingOrder).toBe(true);
    expect(started.downloading).toBe(false);
    expect(started.selectedStickerIds).toEqual(['a']);
    expect(started.error).toBeNull();
    expect(started.notice).toBeNull();

    const succeeded = stickerActionSucceeded({
      action: StickerActionKind.delete,
      uploading: false,
      deleting: true,
      savingOrder: false,
      downloading: false,
      selectedStickerIds: ['a', 'b'],
      error: null,
      notice: 'deleted',
      clearSelection: true,
    });

    expect(succeeded.uploading).toBe(false);
    expect(succeeded.deleting).toBe(false);
    expect(succeeded.selectedStickerIds).toEqual([]);
    expect(succeeded.error).toBeNull();
    expect(succeeded.notice).toBe('deleted');

    const failed = stickerActionFailed({
      action: StickerActionKind.order,
      uploading: false,
      deleting: false,
      savingOrder: true,
      downloading: false,
      selectedStickerIds: ['b'],
      failure: new Error('sort failed'),
    });

    expect(failed.savingOrder).toBe(false);
    expect(failed.selectedStickerIds).toEqual(['b']);
    expect(failed.error).toBe('操作失败，请稍后重试');
    expect(failed.notice).toBeNull();
  });

  it('sticker action notice/error patches preserve independent state', () => {
    const cancelled = stickerActionCancelled({
      action: StickerActionKind.download,
      uploading: false,
      deleting: false,
      savingOrder: false,
      downloading: true,
      selectedStickerIds: ['a'],
    });

    expect(cancelled.downloading).toBe(false);
    expect(cancelled.selectedStickerIds).toEqual(['a']);
    expect(cancelled.error).toBeNull();
    expect(cancelled.notice).toBeNull();

    const notice = stickerActionNoticeShown({
      uploading: false,
      deleting: false,
      savingOrder: false,
      downloading: false,
      selectedStickerIds: ['a'],
      error: 'previous',
      notice: 'nothing changed',
    });

    expect(notice.error).toBe('previous');
    expect(notice.notice).toBe('nothing changed');
    expect(notice.selectedStickerIds).toEqual(['a']);

    const error = stickerActionErrorShown({
      uploading: false,
      deleting: false,
      savingOrder: false,
      downloading: false,
      selectedStickerIds: ['a'],
      failure: 'picker failed',
      notice: 'previous notice',
    });

    expect(error.error).toBe('操作失败，请稍后重试');
    expect(error.notice).toBe('previous notice');
    expect(error.selectedStickerIds).toEqual(['a']);
  });

  it('retainedStickerSelection and managedStickerById use current packs', () => {
    const packs = [_pack('pack_1', ['a']), _pack('pack_2', ['b'])];
    const items = managedStickerItems(packs);

    expect(retainedStickerSelection(['missing', 'b', 'a'], packs)).toEqual(['b', 'a']);
    expect(managedStickerById(items, 'b')?.pack.id).toBe('pack_2');
    expect(managedStickerById(items, 'missing')).toBeNull();
  });
});

function _pack(
  id: string,
  stickerIds: string[],
  opts: { mimeTypes?: Record<string, string>; sortOrder?: number } = {},
): StickerPack {
  const mimeTypes = opts.mimeTypes ?? {};
  return new StickerPack({
    id,
    scope: 'personal',
    roomId: null,
    name: id,
    sortOrder: opts.sortOrder ?? 10,
    updatedAt: new Date(Date.UTC(2026, 5, 4)),
    stickers: stickerIds.map(
      (stickerId, index) =>
        new Sticker({
          id: stickerId,
          name: stickerId,
          sortOrder: (index + 1) * 10,
          asset: new UploadedAsset({
            id: `asset_${stickerId}`,
            url: `/assets/${stickerId}.png`,
            thumbnailUrl: null,
            mimeType: mimeTypes[stickerId] ?? 'image/png',
          }),
        }),
    ),
  });
}

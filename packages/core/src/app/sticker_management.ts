import type { Sticker, StickerPack } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';
import * as sticker_ordering from './sticker_ordering';

export enum StickerManagementScope {
  personal = 'personal',
  room = 'room',
}

export enum StickerActionKind {
  upload = 'upload',
  delete = 'delete',
  order = 'order',
  download = 'download',
}

export enum StickerPreviewActionKind {
  rename = 'rename',
  setAvatar = 'setAvatar',
  download = 'download',
  delete = 'delete',
  moveUp = 'moveUp',
  moveDown = 'moveDown',
  pin = 'pin',
}

export class StickerManagementCapabilities {
  constructor(
    opts: {
      canUpload?: boolean;
      canBatchManage?: boolean;
      canFilter?: boolean;
      canDownload?: boolean;
      canSelectAll?: boolean;
      canDelete?: boolean;
      canPin?: boolean;
      canRename?: boolean;
      canMove?: boolean;
      canSetAvatar?: boolean;
    } = {},
  ) {
    this.canUpload = opts.canUpload ?? true;
    this.canBatchManage = opts.canBatchManage ?? true;
    this.canFilter = opts.canFilter ?? true;
    this.canDownload = opts.canDownload ?? true;
    this.canSelectAll = opts.canSelectAll ?? true;
    this.canDelete = opts.canDelete ?? true;
    this.canPin = opts.canPin ?? true;
    this.canRename = opts.canRename ?? true;
    this.canMove = opts.canMove ?? true;
    this.canSetAvatar = opts.canSetAvatar ?? true;
  }

  static readOnlyDownloads(): StickerManagementCapabilities {
    return new StickerManagementCapabilities({
      canUpload: false,
      canBatchManage: true,
      canFilter: true,
      canDownload: true,
      canSelectAll: true,
      canDelete: false,
      canPin: false,
      canRename: false,
      canMove: false,
      canSetAvatar: false,
    });
  }

  readonly canUpload: boolean;
  readonly canBatchManage: boolean;
  readonly canFilter: boolean;
  readonly canDownload: boolean;
  readonly canSelectAll: boolean;
  readonly canDelete: boolean;
  readonly canPin: boolean;
  readonly canRename: boolean;
  readonly canMove: boolean;
  readonly canSetAvatar: boolean;
}

export class ManagedSticker {
  constructor(opts: { pack: StickerPack; sticker: Sticker }) {
    this.pack = opts.pack;
    this.sticker = opts.sticker;
  }

  readonly pack: StickerPack;
  readonly sticker: Sticker;
}

export class StickerPackLoadPatch {
  constructor(opts: { packs: StickerPack[]; selectedStickerIds: string[]; loading: boolean; error: string | null }) {
    this.packs = opts.packs;
    this.selectedStickerIds = opts.selectedStickerIds;
    this.loading = opts.loading;
    this.error = opts.error;
  }

  readonly packs: StickerPack[];
  readonly selectedStickerIds: string[];
  readonly loading: boolean;
  readonly error: string | null;
}

export class StickerSelectionPatch {
  constructor(opts: { managing: boolean; filterKeyword: string; filterMimeType: string; selectedStickerIds: string[] }) {
    this.managing = opts.managing;
    this.filterKeyword = opts.filterKeyword;
    this.filterMimeType = opts.filterMimeType;
    this.selectedStickerIds = opts.selectedStickerIds;
  }

  readonly managing: boolean;
  readonly filterKeyword: string;
  readonly filterMimeType: string;
  readonly selectedStickerIds: string[];
}

export class StickerFilterDraft {
  constructor(opts: { keyword: string; mimeType: string }) {
    this.keyword = opts.keyword;
    this.mimeType = opts.mimeType;
  }

  readonly keyword: string;
  readonly mimeType: string;
}

export class StickerFilterDraftPatch {
  constructor(opts: { mimeType: string }) {
    this.mimeType = opts.mimeType;
  }

  readonly mimeType: string;
}

export class StickerActionPatch {
  constructor(opts: {
    uploading: boolean;
    deleting: boolean;
    savingOrder: boolean;
    downloading: boolean;
    selectedStickerIds: string[];
    error: string | null;
    notice: string | null;
  }) {
    this.uploading = opts.uploading;
    this.deleting = opts.deleting;
    this.savingOrder = opts.savingOrder;
    this.downloading = opts.downloading;
    this.selectedStickerIds = opts.selectedStickerIds;
    this.error = opts.error;
    this.notice = opts.notice;
  }

  readonly uploading: boolean;
  readonly deleting: boolean;
  readonly savingOrder: boolean;
  readonly downloading: boolean;
  readonly selectedStickerIds: string[];
  readonly error: string | null;
  readonly notice: string | null;
}

export class StickerPreviewState {
  constructor(opts: {
    canMoveUp: boolean;
    canMoveDown: boolean;
    canPin: boolean;
    savingName?: boolean;
    settingAvatar?: boolean;
    downloading?: boolean;
    deleting?: boolean;
    movingUp?: boolean;
    movingDown?: boolean;
    pinning?: boolean;
    error?: string | null;
  }) {
    this.canMoveUp = opts.canMoveUp;
    this.canMoveDown = opts.canMoveDown;
    this.canPin = opts.canPin;
    this.savingName = opts.savingName ?? false;
    this.settingAvatar = opts.settingAvatar ?? false;
    this.downloading = opts.downloading ?? false;
    this.deleting = opts.deleting ?? false;
    this.movingUp = opts.movingUp ?? false;
    this.movingDown = opts.movingDown ?? false;
    this.pinning = opts.pinning ?? false;
    this.error = opts.error ?? null;
  }

  static initial(opts: { canMoveUp: boolean; canMoveDown: boolean; canPin: boolean }): StickerPreviewState {
    return new StickerPreviewState({
      canMoveUp: opts.canMoveUp,
      canMoveDown: opts.canMoveDown,
      canPin: opts.canPin,
    });
  }

  readonly canMoveUp: boolean;
  readonly canMoveDown: boolean;
  readonly canPin: boolean;
  readonly savingName: boolean;
  readonly settingAvatar: boolean;
  readonly downloading: boolean;
  readonly deleting: boolean;
  readonly movingUp: boolean;
  readonly movingDown: boolean;
  readonly pinning: boolean;
  readonly error: string | null;

  get busy(): boolean {
    return (
      this.savingName ||
      this.settingAvatar ||
      this.downloading ||
      this.deleting ||
      this.movingUp ||
      this.movingDown ||
      this.pinning
    );
  }

  /** `error: undefined` (or omitted) keeps the current error; `null` clears it. */
  copyWith(
    opts: {
      canMoveUp?: boolean;
      canMoveDown?: boolean;
      canPin?: boolean;
      savingName?: boolean;
      settingAvatar?: boolean;
      downloading?: boolean;
      deleting?: boolean;
      movingUp?: boolean;
      movingDown?: boolean;
      pinning?: boolean;
      error?: string | null;
    } = {},
  ): StickerPreviewState {
    return new StickerPreviewState({
      canMoveUp: opts.canMoveUp ?? this.canMoveUp,
      canMoveDown: opts.canMoveDown ?? this.canMoveDown,
      canPin: opts.canPin ?? this.canPin,
      savingName: opts.savingName ?? this.savingName,
      settingAvatar: opts.settingAvatar ?? this.settingAvatar,
      downloading: opts.downloading ?? this.downloading,
      deleting: opts.deleting ?? this.deleting,
      movingUp: opts.movingUp ?? this.movingUp,
      movingDown: opts.movingDown ?? this.movingDown,
      pinning: opts.pinning ?? this.pinning,
      error: opts.error === undefined ? this.error : opts.error,
    });
  }
}

export function managedStickerItems(
  packs: readonly StickerPack[],
  opts: { orderForPack?: sticker_ordering.StickerOrderForPack | null } = {},
): ManagedSticker[] {
  const items: ManagedSticker[] = [];
  for (const pack of packs) {
    for (const sticker of sticker_ordering.orderedStickers(pack, { order: opts.orderForPack?.(pack) })) {
      items.push(new ManagedSticker({ pack, sticker }));
    }
  }
  return items;
}

export function filteredManagedStickerItems(
  items: Iterable<ManagedSticker>,
  opts: { keyword: string; mimeType: string },
): ManagedSticker[] {
  const normalizedKeyword = opts.keyword.trim().toLowerCase();
  const result: ManagedSticker[] = [];
  for (const item of items) {
    if (_matchesStickerFilter(item.sticker, { keyword: normalizedKeyword, mimeType: opts.mimeType })) {
      result.push(item);
    }
  }
  return result;
}

export function stickerFilterActive(opts: { keyword: string; mimeType: string }): boolean {
  return opts.keyword.trim().length > 0 || opts.mimeType.length > 0;
}

export function stickerManagementBusy(opts: {
  uploading: boolean;
  deleting: boolean;
  savingOrder: boolean;
  downloading: boolean;
}): boolean {
  return opts.uploading || opts.deleting || opts.savingOrder || opts.downloading;
}

export function stickerManagementCountText(opts: {
  filterActive: boolean;
  visibleCount: number;
  totalCount: number;
}): string {
  return opts.filterActive ? `${opts.visibleCount} / ${opts.totalCount} 个` : `${opts.totalCount} 个`;
}

export function canStartStickerPrimaryAction(opts: { busy: boolean; allowed?: boolean }): boolean {
  const { busy, allowed = true } = opts;
  return allowed && !busy;
}

export function canStartStickerSelectionAction(opts: {
  busy: boolean;
  selectedStickerIds: Iterable<string>;
  allowed?: boolean;
}): boolean {
  const { busy, selectedStickerIds, allowed = true } = opts;
  return allowed && !busy && !_isEmpty(selectedStickerIds);
}

export function canSelectVisibleStickers(opts: {
  busy: boolean;
  visibleItems: Iterable<ManagedSticker>;
  allowed?: boolean;
}): boolean {
  const { busy, visibleItems, allowed = true } = opts;
  return allowed && !busy && !_isEmpty(visibleItems);
}

export function stickerAllVisibleSelected(opts: {
  selectedStickerIds: Iterable<string>;
  visibleItems: readonly ManagedSticker[];
}): boolean {
  if (opts.visibleItems.length === 0) return false;
  const selectedSet = new Set(opts.selectedStickerIds);
  return opts.visibleItems.every((item) => selectedSet.has(item.sticker.id));
}

export function stickerVisibleSelectionButtonText(opts: {
  selectedStickerIds: Iterable<string>;
  visibleItems: readonly ManagedSticker[];
}): string {
  return '全选';
}

export function canUseStickerManagementControl(opts: { busy: boolean; allowed?: boolean }): boolean {
  const { busy, allowed = true } = opts;
  return allowed && !busy;
}

export function stickerRenameName(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

export function canStartStickerRename(opts: { busy: boolean; name: string; allowed?: boolean }): boolean {
  const { busy, name, allowed = true } = opts;
  return allowed && !busy && stickerRenameName(name) != null;
}

export function canStartStickerPreviewAction(opts: {
  state: StickerPreviewState;
  action: StickerPreviewActionKind;
  name?: string | null;
}): boolean {
  const { state, action, name } = opts;
  if (action === StickerPreviewActionKind.rename) {
    return canStartStickerRename({ busy: state.busy, name: name ?? '' });
  }
  return !state.busy;
}

export function stickerPreviewActionRequested(opts: {
  state: StickerPreviewState;
  action: StickerPreviewActionKind;
  name?: string | null;
}): StickerPreviewState | null {
  const { state, action, name } = opts;
  if (!canStartStickerPreviewAction({ state, action, name })) {
    return null;
  }
  return stickerPreviewActionStarted({ state, action });
}

export function defaultStickerPackName(scope: StickerManagementScope): string {
  return scope === StickerManagementScope.room ? '房间表情包' : '我的表情包';
}

export function stickerUploadNotice(opts: { scope: StickerManagementScope; count: number }): string {
  return opts.scope === StickerManagementScope.room ? `已添加 ${opts.count} 个房间表情` : `已添加 ${opts.count} 个表情`;
}

export function stickerDeleteDialogTitle(scope: StickerManagementScope): string {
  return scope === StickerManagementScope.room ? '删除房间表情' : '删除表情';
}

export function stickerSingleDeleteConfirmBody(opts: { scope: StickerManagementScope; stickerName: string }): string {
  return opts.scope === StickerManagementScope.room
    ? `将从这个房间的表情包中删除「${opts.stickerName}」。`
    : `将从服务端删除「${opts.stickerName}」，删除后不会再出现在你的表情包里。`;
}

export function stickerBulkDeleteConfirmBody(opts: { scope: StickerManagementScope; count: number }): string {
  return opts.scope === StickerManagementScope.room
    ? `将从这个房间的表情包中删除选中的 ${opts.count} 个表情。`
    : `将从服务端删除选中的 ${opts.count} 个表情，删除后不会再出现在你的表情包里。`;
}

export function stickerDeletedNotice(opts: { scope: StickerManagementScope; count?: number | null }): string {
  const { scope, count } = opts;
  if (scope === StickerManagementScope.room) {
    return count == null ? '房间表情已删除' : `已删除 ${count} 个房间表情`;
  }
  return count == null ? '表情已删除' : `已删除 ${count} 个表情`;
}

export function stickerDownloadNotice(opts: { scope: StickerManagementScope; count: number }): string {
  const { scope, count } = opts;
  if (scope === StickerManagementScope.room) {
    return count === 1 ? '房间表情已下载' : '房间表情压缩包已下载';
  }
  return count === 1 ? '表情已下载' : '表情压缩包已下载';
}

export function stickerNoOrderChangeNotice(scope: StickerManagementScope): string {
  return scope === StickerManagementScope.room ? '房间表情排序没有变化' : '表情排序没有变化';
}

export function stickerPinnedNotice(opts: { scope: StickerManagementScope; count?: number | null }): string {
  const { scope, count } = opts;
  if (scope === StickerManagementScope.room) {
    return count == null ? '房间表情已置顶' : `已置顶 ${count} 个房间表情`;
  }
  return count == null ? '表情已置顶' : `已置顶 ${count} 个表情`;
}

export function stickerMoveNotice(opts: { scope: StickerManagementScope; delta: number }): string {
  const direction = opts.delta < 0 ? '上' : '下';
  return opts.scope === StickerManagementScope.room ? `房间表情已${direction}移一位` : `表情已${direction}移一位`;
}

export function stickerActionStarted(opts: {
  action: StickerActionKind;
  uploading: boolean;
  deleting: boolean;
  savingOrder: boolean;
  downloading: boolean;
  selectedStickerIds: Iterable<string>;
}): StickerActionPatch {
  return _stickerActionPatch({
    action: opts.action,
    actionBusy: true,
    uploading: opts.uploading,
    deleting: opts.deleting,
    savingOrder: opts.savingOrder,
    downloading: opts.downloading,
    selectedStickerIds: [...opts.selectedStickerIds],
    error: null,
    notice: null,
  });
}

export function stickerActionSucceeded(opts: {
  action: StickerActionKind;
  uploading: boolean;
  deleting: boolean;
  savingOrder: boolean;
  downloading: boolean;
  selectedStickerIds: Iterable<string>;
  error: string | null;
  notice: string;
  clearSelection?: boolean;
}): StickerActionPatch {
  const { clearSelection = false } = opts;
  return _stickerActionPatch({
    action: opts.action,
    actionBusy: false,
    uploading: opts.uploading,
    deleting: opts.deleting,
    savingOrder: opts.savingOrder,
    downloading: opts.downloading,
    selectedStickerIds: clearSelection ? [] : [...opts.selectedStickerIds],
    error: opts.error,
    notice: opts.notice,
  });
}

export function stickerActionFailed(opts: {
  action: StickerActionKind;
  uploading: boolean;
  deleting: boolean;
  savingOrder: boolean;
  downloading: boolean;
  selectedStickerIds: Iterable<string>;
  failure: unknown;
}): StickerActionPatch {
  return _stickerActionPatch({
    action: opts.action,
    actionBusy: false,
    uploading: opts.uploading,
    deleting: opts.deleting,
    savingOrder: opts.savingOrder,
    downloading: opts.downloading,
    selectedStickerIds: [...opts.selectedStickerIds],
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function stickerActionCancelled(opts: {
  action: StickerActionKind;
  uploading: boolean;
  deleting: boolean;
  savingOrder: boolean;
  downloading: boolean;
  selectedStickerIds: Iterable<string>;
}): StickerActionPatch {
  return _stickerActionPatch({
    action: opts.action,
    actionBusy: false,
    uploading: opts.uploading,
    deleting: opts.deleting,
    savingOrder: opts.savingOrder,
    downloading: opts.downloading,
    selectedStickerIds: [...opts.selectedStickerIds],
    error: null,
    notice: null,
  });
}

export function stickerActionNoticeShown(opts: {
  uploading: boolean;
  deleting: boolean;
  savingOrder: boolean;
  downloading: boolean;
  selectedStickerIds: Iterable<string>;
  error: string | null;
  notice: string;
}): StickerActionPatch {
  return new StickerActionPatch({
    uploading: opts.uploading,
    deleting: opts.deleting,
    savingOrder: opts.savingOrder,
    downloading: opts.downloading,
    selectedStickerIds: [...opts.selectedStickerIds],
    error: opts.error,
    notice: opts.notice,
  });
}

export function stickerActionErrorShown(opts: {
  uploading: boolean;
  deleting: boolean;
  savingOrder: boolean;
  downloading: boolean;
  selectedStickerIds: Iterable<string>;
  failure: unknown;
  notice?: string | null;
}): StickerActionPatch {
  return new StickerActionPatch({
    uploading: opts.uploading,
    deleting: opts.deleting,
    savingOrder: opts.savingOrder,
    downloading: opts.downloading,
    selectedStickerIds: [...opts.selectedStickerIds],
    error: userFacingErrorMessage(opts.failure),
    notice: opts.notice ?? null,
  });
}

export function stickerPreviewActionStarted(opts: {
  state: StickerPreviewState;
  action: StickerPreviewActionKind;
}): StickerPreviewState {
  return _stickerPreviewActionState(opts.state, { action: opts.action, active: true, error: null });
}

export function stickerPreviewActionFinished(opts: {
  state: StickerPreviewState;
  action: StickerPreviewActionKind;
}): StickerPreviewState {
  return _stickerPreviewActionState(opts.state, { action: opts.action, active: false });
}

export function stickerPreviewActionFailed(opts: {
  state: StickerPreviewState;
  action: StickerPreviewActionKind;
  failure: unknown;
}): StickerPreviewState {
  return _stickerPreviewActionState(opts.state, {
    action: opts.action,
    active: false,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function stickerPreviewMoveSucceeded(opts: {
  state: StickerPreviewState;
  action: StickerPreviewActionKind;
  canMoveUp: boolean;
  canMoveDown: boolean;
  canPin: boolean;
}): StickerPreviewState {
  return _stickerPreviewActionState(opts.state, { action: opts.action, active: false }).copyWith({
    canMoveUp: opts.canMoveUp,
    canMoveDown: opts.canMoveDown,
    canPin: opts.canPin,
  });
}

export function stickerPacksLoadStarted(opts: {
  packs: Iterable<StickerPack>;
  selectedStickerIds: Iterable<string>;
}): StickerPackLoadPatch {
  return new StickerPackLoadPatch({
    packs: [...opts.packs],
    selectedStickerIds: [...opts.selectedStickerIds],
    loading: true,
    error: null,
  });
}

export function stickerPacksLoadSucceeded(opts: {
  packs: Iterable<StickerPack>;
  selectedStickerIds: Iterable<string>;
}): StickerPackLoadPatch {
  const nextPacks = [...opts.packs];
  return new StickerPackLoadPatch({
    packs: nextPacks,
    selectedStickerIds: retainedStickerSelection([...opts.selectedStickerIds], nextPacks),
    loading: false,
    error: null,
  });
}

export function stickerPacksLoadFailed(opts: {
  packs: Iterable<StickerPack>;
  selectedStickerIds: Iterable<string>;
  failure: unknown;
}): StickerPackLoadPatch {
  return new StickerPackLoadPatch({
    packs: [...opts.packs],
    selectedStickerIds: [...opts.selectedStickerIds],
    loading: false,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function stickerPackUpserted(opts: {
  packs: Iterable<StickerPack>;
  selectedStickerIds: Iterable<string>;
  pack: StickerPack;
  loading: boolean;
  error: string | null;
}): StickerPackLoadPatch {
  const nextPacks = _upsertStickerPack([...opts.packs], opts.pack);
  return new StickerPackLoadPatch({
    packs: nextPacks,
    selectedStickerIds: retainedStickerSelection([...opts.selectedStickerIds], nextPacks),
    loading: opts.loading,
    error: opts.error,
  });
}

export function stickerManagementModeToggled(opts: {
  managing: boolean;
  filterKeyword: string;
  filterMimeType: string;
}): StickerSelectionPatch {
  return new StickerSelectionPatch({
    managing: !opts.managing,
    filterKeyword: opts.filterKeyword,
    filterMimeType: opts.filterMimeType,
    selectedStickerIds: [],
  });
}

export function stickerSelectionToggled(opts: {
  managing: boolean;
  filterKeyword: string;
  filterMimeType: string;
  selectedStickerIds: Iterable<string>;
  stickerId: string;
}): StickerSelectionPatch {
  return new StickerSelectionPatch({
    managing: opts.managing,
    filterKeyword: opts.filterKeyword,
    filterMimeType: opts.filterMimeType,
    selectedStickerIds: opts.managing
      ? toggledStickerSelection([...opts.selectedStickerIds], opts.stickerId)
      : [...opts.selectedStickerIds],
  });
}

export function stickerVisibleSelectionToggled(opts: {
  managing: boolean;
  busy: boolean;
  filterKeyword: string;
  filterMimeType: string;
  selectedStickerIds: Iterable<string>;
  visibleItems: ManagedSticker[];
}): StickerSelectionPatch {
  const { managing, busy, visibleItems } = opts;
  return new StickerSelectionPatch({
    managing,
    filterKeyword: opts.filterKeyword,
    filterMimeType: opts.filterMimeType,
    selectedStickerIds:
      managing && canSelectVisibleStickers({ busy, visibleItems })
        ? stickerSelectionForVisibleItems([...opts.selectedStickerIds], visibleItems)
        : [...opts.selectedStickerIds],
  });
}

export function stickerFilterApplied(opts: { managing: boolean; keyword: string; mimeType: string }): StickerSelectionPatch {
  return new StickerSelectionPatch({
    managing: opts.managing,
    filterKeyword: opts.keyword,
    filterMimeType: opts.mimeType,
    selectedStickerIds: [],
  });
}

export function stickerFilterMimeTypeChanged(opts: { mimeType: string }): StickerFilterDraftPatch {
  return new StickerFilterDraftPatch({ mimeType: opts.mimeType });
}

export function stickerFilterDraftReset(): StickerFilterDraftPatch {
  return new StickerFilterDraftPatch({ mimeType: '' });
}

export function stickerSelectionNumbers(selectedStickerIds: readonly string[]): Map<string, number> {
  const numbers = new Map<string, number>();
  selectedStickerIds.forEach((id, index) => numbers.set(id, index + 1));
  return numbers;
}

export function toggledStickerSelection(selectedStickerIds: readonly string[], stickerId: string): string[] {
  const next = [...selectedStickerIds];
  const index = next.indexOf(stickerId);
  if (index >= 0) {
    next.splice(index, 1);
  } else {
    next.push(stickerId);
  }
  return next;
}

export function stickerSelectionForVisibleItems(
  selectedStickerIds: string[],
  visibleItems: readonly ManagedSticker[],
): string[] {
  if (visibleItems.length === 0) return selectedStickerIds;
  const visibleIds = visibleItems.map((item) => item.sticker.id);
  const visibleSet = new Set(visibleIds);
  const allVisibleSelected = stickerAllVisibleSelected({ selectedStickerIds, visibleItems });
  return allVisibleSelected ? selectedStickerIds.filter((id) => !visibleSet.has(id)) : [...visibleIds].reverse();
}

export function retainedStickerSelection(selectedStickerIds: readonly string[], packs: readonly StickerPack[]): string[] {
  const stickerIds = new Set<string>();
  for (const pack of packs) {
    for (const sticker of pack.stickers) stickerIds.add(sticker.id);
  }
  return selectedStickerIds.filter((id) => stickerIds.has(id));
}

export function managedStickerById(items: Iterable<ManagedSticker>, stickerId: string): ManagedSticker | null {
  for (const item of items) {
    if (item.sticker.id === stickerId) return item;
  }
  return null;
}

function _upsertStickerPack(packs: StickerPack[], pack: StickerPack): StickerPack[] {
  const next: StickerPack[] = [];
  let replaced = false;
  for (const current of packs) {
    if (current.id === pack.id) {
      next.push(pack);
      replaced = true;
    } else {
      next.push(current);
    }
  }
  if (!replaced) {
    const insertionIndex = next.findIndex((current) => current.sortOrder > pack.sortOrder);
    if (insertionIndex < 0) {
      next.push(pack);
    } else {
      next.splice(insertionIndex, 0, pack);
    }
  }
  return next;
}

function _matchesStickerFilter(sticker: Sticker, opts: { keyword: string; mimeType: string }): boolean {
  const { keyword, mimeType } = opts;
  if (keyword.length > 0 && !sticker.name.toLowerCase().includes(keyword)) {
    return false;
  }
  if (mimeType.length > 0 && sticker.asset.mimeType !== mimeType) {
    return false;
  }
  return true;
}

function _stickerActionPatch(opts: {
  action: StickerActionKind;
  actionBusy: boolean;
  uploading: boolean;
  deleting: boolean;
  savingOrder: boolean;
  downloading: boolean;
  selectedStickerIds: string[];
  error: string | null;
  notice: string | null;
}): StickerActionPatch {
  const { action, actionBusy } = opts;
  return new StickerActionPatch({
    uploading: action === StickerActionKind.upload ? actionBusy : opts.uploading,
    deleting: action === StickerActionKind.delete ? actionBusy : opts.deleting,
    savingOrder: action === StickerActionKind.order ? actionBusy : opts.savingOrder,
    downloading: action === StickerActionKind.download ? actionBusy : opts.downloading,
    selectedStickerIds: opts.selectedStickerIds,
    error: opts.error,
    notice: opts.notice,
  });
}

/** `error: undefined` (or omitted) keeps the state's error, like Dart's unchanged sentinel. */
function _stickerPreviewActionState(
  state: StickerPreviewState,
  opts: { action: StickerPreviewActionKind; active: boolean; error?: string | null },
): StickerPreviewState {
  const { action, active, error } = opts;
  switch (action) {
    case StickerPreviewActionKind.rename:
      return state.copyWith({ savingName: active, error });
    case StickerPreviewActionKind.setAvatar:
      return state.copyWith({ settingAvatar: active, error });
    case StickerPreviewActionKind.download:
      return state.copyWith({ downloading: active, error });
    case StickerPreviewActionKind.delete:
      return state.copyWith({ deleting: active, error });
    case StickerPreviewActionKind.moveUp:
      return state.copyWith({ movingUp: active, error });
    case StickerPreviewActionKind.moveDown:
      return state.copyWith({ movingDown: active, error });
    case StickerPreviewActionKind.pin:
      return state.copyWith({ pinning: active, error });
  }
}

function _isEmpty(values: Iterable<unknown>): boolean {
  for (const _ of values) return false;
  return true;
}

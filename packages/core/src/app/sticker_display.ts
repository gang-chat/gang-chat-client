import type { Sticker, StickerPack, UploadedAsset } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';
import type { StickerImageDimensions } from './sticker_uploads';

export enum StickerPanelSource {
  personal = 'personal',
  room = 'room',
}

export enum StickerPanelBodyState {
  loading = 'loading',
  error = 'error',
  empty = 'empty',
  results = 'results',
}

export class StickerPanelLoadState {
  constructor(
    opts: {
      source?: StickerPanelSource;
      personalPacks?: StickerPack[];
      roomPacks?: StickerPack[];
      loading?: boolean;
      loaded?: boolean;
      error?: string | null;
    } = {},
  ) {
    this.source = opts.source ?? StickerPanelSource.personal;
    this.personalPacks = opts.personalPacks ?? [];
    this.roomPacks = opts.roomPacks ?? [];
    this.loading = opts.loading ?? false;
    this.loaded = opts.loaded ?? false;
    this.error = opts.error ?? null;
  }

  readonly source: StickerPanelSource;
  readonly personalPacks: StickerPack[];
  readonly roomPacks: StickerPack[];
  readonly loading: boolean;
  readonly loaded: boolean;
  readonly error: string | null;

  /** `error: undefined` (or omitted) keeps the current error; `null` clears it. */
  copyWith(
    opts: {
      source?: StickerPanelSource;
      personalPacks?: StickerPack[];
      roomPacks?: StickerPack[];
      loading?: boolean;
      loaded?: boolean;
      error?: string | null;
    } = {},
  ): StickerPanelLoadState {
    return new StickerPanelLoadState({
      source: opts.source ?? this.source,
      personalPacks: opts.personalPacks ?? this.personalPacks,
      roomPacks: opts.roomPacks ?? this.roomPacks,
      loading: opts.loading ?? this.loading,
      loaded: opts.loaded ?? this.loaded,
      error: opts.error === undefined ? this.error : opts.error,
    });
  }
}

export function stickerPanelStickers(opts: {
  source: StickerPanelSource;
  personalPacks: Iterable<StickerPack>;
  roomPacks: Iterable<StickerPack>;
}): Sticker[] {
  const packs = opts.source === StickerPanelSource.personal ? opts.personalPacks : opts.roomPacks;
  const stickers: Sticker[] = [];
  for (const pack of packs) stickers.push(...pack.stickers);
  return stickers;
}

export function stickerPanelBodyState(opts: {
  loading: boolean;
  error: string | null;
  stickers: readonly Sticker[];
}): StickerPanelBodyState {
  if (opts.stickers.length > 0) return StickerPanelBodyState.results;
  if (opts.loading) return StickerPanelBodyState.loading;
  if (opts.error != null) return StickerPanelBodyState.error;
  return StickerPanelBodyState.empty;
}

export function stickerPanelEmptyText(source: StickerPanelSource): string {
  return source === StickerPanelSource.personal ? '暂无个人表情' : '暂无房间表情';
}

export function stickerPanelReset(opts: { source?: StickerPanelSource } = {}): StickerPanelLoadState {
  const { source = StickerPanelSource.personal } = opts;
  return new StickerPanelLoadState({ source });
}

export function stickerPanelSourceChanged(state: StickerPanelLoadState, source: StickerPanelSource): StickerPanelLoadState {
  return state.copyWith({ source });
}

export function shouldLoadStickerPanel(opts: { state: StickerPanelLoadState; forceReload: boolean }): boolean {
  return !opts.state.loading && (opts.forceReload || !opts.state.loaded);
}

export function stickerPanelLoadStarted(state: StickerPanelLoadState): StickerPanelLoadState {
  return state.copyWith({ loading: true, error: null });
}

export function stickerPanelCachedPersonalApplied(opts: {
  state: StickerPanelLoadState;
  packs: Iterable<StickerPack>;
}): StickerPanelLoadState {
  return opts.state.copyWith({ personalPacks: [...opts.packs] });
}

export function stickerPanelLoadSucceeded(opts: {
  state: StickerPanelLoadState;
  personalPacks: Iterable<StickerPack>;
  roomPacks: Iterable<StickerPack>;
}): StickerPanelLoadState {
  return opts.state.copyWith({
    personalPacks: [...opts.personalPacks],
    roomPacks: [...opts.roomPacks],
    loading: false,
    loaded: true,
    error: null,
  });
}

export function stickerPanelLoadFailed(opts: { state: StickerPanelLoadState; failure: unknown }): StickerPanelLoadState {
  return opts.state.copyWith({
    loading: false,
    error: userFacingErrorMessage(opts.failure, { fallback: '加载表情包失败' }),
  });
}

export function stickerPanelLoadFinished(state: StickerPanelLoadState): StickerPanelLoadState {
  return state.copyWith({ loading: false });
}

export function stickerDimensionsText(
  asset: UploadedAsset,
  opts: { resolved?: StickerImageDimensions | null; resolving: boolean; failed: boolean },
): string {
  const width = asset.width ?? opts.resolved?.width;
  const height = asset.height ?? opts.resolved?.height;
  if (width != null && height != null) return `${width}x${height}`;
  if (opts.resolving) return '正在读取尺寸';
  if (opts.failed) return '尺寸读取失败';
  return '未知尺寸';
}

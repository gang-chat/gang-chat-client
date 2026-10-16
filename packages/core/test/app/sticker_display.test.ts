import { describe, expect, it } from 'vitest';

import {
  StickerPanelBodyState,
  StickerPanelLoadState,
  StickerPanelSource,
  shouldLoadStickerPanel,
  stickerDimensionsText,
  stickerPanelBodyState,
  stickerPanelCachedPersonalApplied,
  stickerPanelEmptyText,
  stickerPanelLoadFailed,
  stickerPanelLoadFinished,
  stickerPanelLoadStarted,
  stickerPanelLoadSucceeded,
  stickerPanelReset,
  stickerPanelSourceChanged,
  stickerPanelStickers,
} from '../../src/app/sticker_display';
import { StickerImageDimensions } from '../../src/app/sticker_uploads';
import { Sticker, StickerPack, UploadedAsset } from '../../src/protocol/models';

describe('sticker display', () => {
  it('stickerPanelStickers flattens packs from the selected source', () => {
    const personal = [_pack('personal_pack', ['p1', 'p2'])];
    const room = [_pack('room_pack', ['r1'])];

    expect(
      stickerPanelStickers({ source: StickerPanelSource.personal, personalPacks: personal, roomPacks: room }).map(
        (sticker) => sticker.id,
      ),
    ).toEqual(['p1', 'p2']);
    expect(
      stickerPanelStickers({ source: StickerPanelSource.room, personalPacks: personal, roomPacks: room }).map(
        (sticker) => sticker.id,
      ),
    ).toEqual(['r1']);
  });

  it('stickerPanelBodyState prioritizes result loading error and empty', () => {
    const stickers = _pack('pack', ['s1']).stickers;

    expect(stickerPanelBodyState({ loading: true, error: 'network', stickers })).toBe(StickerPanelBodyState.results);
    expect(stickerPanelBodyState({ loading: true, error: 'network', stickers: [] })).toBe(StickerPanelBodyState.loading);
    expect(stickerPanelBodyState({ loading: false, error: 'network', stickers: [] })).toBe(StickerPanelBodyState.error);
    expect(stickerPanelBodyState({ loading: false, error: null, stickers: [] })).toBe(StickerPanelBodyState.empty);
  });

  it('stickerPanelEmptyText describes the selected source', () => {
    expect(stickerPanelEmptyText(StickerPanelSource.personal)).toBe('暂无个人表情');
    expect(stickerPanelEmptyText(StickerPanelSource.room)).toBe('暂无房间表情');
  });

  it('sticker panel load state covers reset source and load gates', () => {
    const initial = new StickerPanelLoadState();

    expect(initial.source).toBe(StickerPanelSource.personal);
    expect(initial.personalPacks).toEqual([]);
    expect(initial.roomPacks).toEqual([]);
    expect(initial.loading).toBe(false);
    expect(initial.loaded).toBe(false);
    expect(initial.error).toBeNull();
    expect(shouldLoadStickerPanel({ state: initial, forceReload: false })).toBe(true);

    const roomSource = stickerPanelSourceChanged(initial, StickerPanelSource.room);
    const started = stickerPanelLoadStarted(roomSource);

    expect(started.source).toBe(StickerPanelSource.room);
    expect(started.loading).toBe(true);
    expect(started.error).toBeNull();
    expect(shouldLoadStickerPanel({ state: started, forceReload: true })).toBe(false);

    const reset = stickerPanelReset({ source: started.source });

    expect(reset.source).toBe(StickerPanelSource.room);
    expect(reset.loaded).toBe(false);
    expect(reset.loading).toBe(false);
    expect(stickerPanelReset().source).toBe(StickerPanelSource.personal);
  });

  it('sticker panel load patches keep cached and loaded packs reusable', () => {
    const initial = new StickerPanelLoadState({ error: 'previous' });
    const cached = [_pack('cached_pack', ['cached'])];
    const room = [_pack('room_pack', ['room'])];

    const started = stickerPanelLoadStarted(initial);
    expect(started.error).toBeNull();
    const cachedApplied = stickerPanelCachedPersonalApplied({ state: started, packs: cached });

    expect(cachedApplied.personalPacks.map((pack) => pack.id)).toEqual(['cached_pack']);
    expect(cachedApplied.loading).toBe(true);

    const succeeded = stickerPanelLoadSucceeded({ state: cachedApplied, personalPacks: cached, roomPacks: room });

    expect(succeeded.personalPacks.map((pack) => pack.id)).toEqual(['cached_pack']);
    expect(succeeded.roomPacks.map((pack) => pack.id)).toEqual(['room_pack']);
    expect(succeeded.loading).toBe(false);
    expect(succeeded.loaded).toBe(true);
    expect(succeeded.error).toBeNull();
    expect(shouldLoadStickerPanel({ state: succeeded, forceReload: false })).toBe(false);
    expect(shouldLoadStickerPanel({ state: succeeded, forceReload: true })).toBe(true);

    const failed = stickerPanelLoadFailed({
      state: stickerPanelLoadStarted(succeeded),
      failure: new Error('network failed'),
    });

    expect(failed.personalPacks.map((pack) => pack.id)).toEqual(['cached_pack']);
    expect(failed.roomPacks.map((pack) => pack.id)).toEqual(['room_pack']);
    expect(failed.loading).toBe(false);
    expect(failed.loaded).toBe(true);
    expect(failed.error).toBe('网络连接失败，请检查网络后重试');

    const finished = stickerPanelLoadFinished(failed);

    expect(finished.loading).toBe(false);
    expect(finished.error).toBe(failed.error);
  });

  it('stickerDimensionsText prefers asset dimensions', () => {
    expect(
      stickerDimensionsText(_asset({ width: 80, height: 64 }), {
        resolved: new StickerImageDimensions({ width: 10, height: 10 }),
        resolving: false,
        failed: false,
      }),
    ).toBe('80x64');
  });

  it('stickerDimensionsText uses resolved dimensions and states', () => {
    expect(
      stickerDimensionsText(_asset(), {
        resolved: new StickerImageDimensions({ width: 20, height: 30 }),
        resolving: false,
        failed: false,
      }),
    ).toBe('20x30');
    expect(stickerDimensionsText(_asset(), { resolving: true, failed: false })).toBe('正在读取尺寸');
    expect(stickerDimensionsText(_asset(), { resolving: false, failed: true })).toBe('尺寸读取失败');
    expect(stickerDimensionsText(_asset(), { resolving: false, failed: false })).toBe('未知尺寸');
  });
});

function _pack(id: string, stickerIds: string[]): StickerPack {
  return new StickerPack({
    id,
    scope: 'personal',
    roomId: null,
    name: id,
    sortOrder: 10,
    updatedAt: new Date(Date.UTC(2026, 5, 5)),
    stickers: stickerIds.map(
      (stickerId, index) => new Sticker({ id: stickerId, name: stickerId, sortOrder: (index + 1) * 10, asset: _asset() }),
    ),
  });
}

function _asset(opts: { width?: number | null; height?: number | null } = {}): UploadedAsset {
  return new UploadedAsset({
    id: 'asset_1',
    url: '/stickers/asset.png',
    thumbnailUrl: null,
    mimeType: 'image/png',
    filename: 'asset.png',
    sizeBytes: 128,
    width: opts.width ?? null,
    height: opts.height ?? null,
  });
}

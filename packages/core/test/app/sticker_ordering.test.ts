import { describe, expect, it } from 'vitest';

import {
  movedStickerOrder,
  orderedStickers,
  pinnedStickerOrder,
  selectedStickerIdsByPack,
  stickerOrderWithStickerIdsPinnedToFront,
  stickerPackById,
  stickerPackForSticker,
  stickerPlacement,
  stickerSelectionWouldChangePinnedOrder,
  stickerSortOrdersBeforeExisting,
} from '../../src/app/sticker_ordering';
import { Sticker, StickerPack, UploadedAsset } from '../../src/protocol/models';

describe('sticker ordering', () => {
  it('orderedStickers honors draft order and keeps missing stickers', () => {
    const pack = _pack('pack_1', ['a', 'b', 'c']);

    const ordered = orderedStickers(pack, { order: ['c', 'missing', 'a'] });

    expect(ordered.map((sticker) => sticker.id)).toEqual(['c', 'a', 'b']);
    expect(orderedStickers(pack).map((sticker) => sticker.id)).toEqual(['a', 'b', 'c']);
  });

  it('batch upload sort orders place the whole batch before existing', () => {
    const pack = _pack('pack_1', ['a', 'b', 'c']);

    expect(stickerSortOrdersBeforeExisting(pack, 3)).toEqual([-20, -10, 0]);
    expect(stickerSortOrdersBeforeExisting(_pack('empty', []), 3)).toEqual([10, 20, 30]);
    expect(stickerSortOrdersBeforeExisting(pack, 0)).toEqual([]);
  });

  it('move and pin return null when order does not change', () => {
    const pack = _pack('pack_1', ['a', 'b', 'c']);

    expect(movedStickerOrder(pack, 'b', 1)).toEqual(['a', 'c', 'b']);
    expect(movedStickerOrder(pack, 'a', -1)).toBeNull();
    expect(movedStickerOrder(pack, 'missing', 1)).toBeNull();
    expect(pinnedStickerOrder(pack, 'c')).toEqual(['c', 'a', 'b']);
    expect(pinnedStickerOrder(pack, 'a')).toBeNull();
  });

  it('stickerPlacement exposes reusable move and pin state', () => {
    const packs = [_pack('pack_1', ['a', 'b', 'c'])];

    const first = stickerPlacement(packs, 'a');
    expect(first?.index).toBe(0);
    expect(first?.total).toBe(3);
    expect(first?.canMoveUp).toBe(false);
    expect(first?.canMoveDown).toBe(true);
    expect(first?.canPin).toBe(false);

    const middle = stickerPlacement(packs, 'b');
    expect(middle?.pack.id).toBe('pack_1');
    expect(middle?.sticker.id).toBe('b');
    expect(middle?.canMoveUp).toBe(true);
    expect(middle?.canMoveDown).toBe(true);
    expect(middle?.canPin).toBe(true);

    expect(stickerPlacement(packs, 'missing')).toBeNull();
    expect(stickerPlacement(packs, 'c', { orderForPack: () => ['c', 'a', 'b'] })?.index).toBe(0);
  });

  it('selectedStickerIdsByPack groups visible selection by owning pack', () => {
    const packs = [_pack('pack_1', ['a', 'b']), _pack('pack_2', ['c'])];

    const grouped = selectedStickerIdsByPack(packs, ['c', 'missing', 'a']);

    expect([...grouped.entries()]).toEqual([
      ['pack_2', ['c']],
      ['pack_1', ['a']],
    ]);
    expect(stickerPackById(packs, 'pack_2')?.id).toBe('pack_2');
    expect(stickerPackById(packs, 'missing')).toBeNull();
    expect(stickerPackForSticker(packs, 'b')?.id).toBe('pack_1');
  });

  it('batch pin is available only when selected order would change', () => {
    const packs = [_pack('pack_1', ['a', 'b', 'c']), _pack('pack_2', ['d', 'e'])];

    expect(stickerSelectionWouldChangePinnedOrder({ packs, selectedStickerIds: ['a', 'b'] })).toBe(false);
    expect(stickerSelectionWouldChangePinnedOrder({ packs, selectedStickerIds: ['b'] })).toBe(true);
    expect(stickerSelectionWouldChangePinnedOrder({ packs, selectedStickerIds: ['b', 'a'] })).toBe(true);
    expect(
      stickerSelectionWouldChangePinnedOrder({
        packs,
        selectedStickerIds: ['d'],
        orderByPack: new Map([['pack_2', ['e', 'd']]]),
      }),
    ).toBe(true);
    expect(stickerSelectionWouldChangePinnedOrder({ packs, selectedStickerIds: ['missing'] })).toBe(false);
    expect(stickerOrderWithStickerIdsPinnedToFront(packs[0]!, [])).toBeNull();
  });
});

function _pack(id: string, stickerIds: string[]): StickerPack {
  return new StickerPack({
    id,
    scope: 'personal',
    roomId: null,
    name: id,
    sortOrder: 10,
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
            mimeType: 'image/png',
          }),
        }),
    ),
  });
}

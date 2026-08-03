import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/sticker_ordering.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  test('orderedStickers honors draft order and keeps missing stickers', () {
    final pack = _pack('pack_1', ['a', 'b', 'c']);

    final ordered = orderedStickers(pack, order: ['c', 'missing', 'a']);

    expect(ordered.map((sticker) => sticker.id), ['c', 'a', 'b']);
  });

  test('batch upload sort orders place the whole batch before existing', () {
    final pack = _pack('pack_1', ['a', 'b', 'c']);

    expect(stickerSortOrdersBeforeExisting(pack, 3), [-20, -10, 0]);
    expect(stickerSortOrdersBeforeExisting(_pack('empty', []), 3), [
      10,
      20,
      30,
    ]);
    expect(stickerSortOrdersBeforeExisting(pack, 0), isEmpty);
  });

  test('move and pin return null when order does not change', () {
    final pack = _pack('pack_1', ['a', 'b', 'c']);

    expect(movedStickerOrder(pack, 'b', 1), ['a', 'c', 'b']);
    expect(movedStickerOrder(pack, 'a', -1), isNull);
    expect(pinnedStickerOrder(pack, 'c'), ['c', 'a', 'b']);
    expect(pinnedStickerOrder(pack, 'a'), isNull);
  });

  test('stickerPlacement exposes reusable move and pin state', () {
    final packs = [
      _pack('pack_1', ['a', 'b', 'c']),
    ];

    final first = stickerPlacement(packs, 'a');
    expect(first?.index, 0);
    expect(first?.total, 3);
    expect(first?.canMoveUp, isFalse);
    expect(first?.canMoveDown, isTrue);
    expect(first?.canPin, isFalse);

    final middle = stickerPlacement(packs, 'b');
    expect(middle?.pack.id, 'pack_1');
    expect(middle?.sticker.id, 'b');
    expect(middle?.canMoveUp, isTrue);
    expect(middle?.canMoveDown, isTrue);
    expect(middle?.canPin, isTrue);

    expect(stickerPlacement(packs, 'missing'), isNull);
  });

  test('selectedStickerIdsByPack groups visible selection by owning pack', () {
    final packs = [
      _pack('pack_1', ['a', 'b']),
      _pack('pack_2', ['c']),
    ];

    final grouped = selectedStickerIdsByPack(packs, ['c', 'missing', 'a']);

    expect(grouped, {
      'pack_2': ['c'],
      'pack_1': ['a'],
    });
  });

  test('batch pin is available only when selected order would change', () {
    final packs = [
      _pack('pack_1', ['a', 'b', 'c']),
      _pack('pack_2', ['d', 'e']),
    ];

    expect(
      stickerSelectionWouldChangePinnedOrder(
        packs: packs,
        selectedStickerIds: ['a', 'b'],
      ),
      isFalse,
    );
    expect(
      stickerSelectionWouldChangePinnedOrder(
        packs: packs,
        selectedStickerIds: ['b'],
      ),
      isTrue,
    );
    expect(
      stickerSelectionWouldChangePinnedOrder(
        packs: packs,
        selectedStickerIds: ['b', 'a'],
      ),
      isTrue,
    );
    expect(
      stickerSelectionWouldChangePinnedOrder(
        packs: packs,
        selectedStickerIds: ['d'],
        orderByPack: const {
          'pack_2': ['e', 'd'],
        },
      ),
      isTrue,
    );
    expect(
      stickerSelectionWouldChangePinnedOrder(
        packs: packs,
        selectedStickerIds: ['missing'],
      ),
      isFalse,
    );
  });
}

StickerPack _pack(String id, List<String> stickerIds) {
  return StickerPack(
    id: id,
    scope: 'personal',
    roomId: null,
    name: id,
    sortOrder: 10,
    updatedAt: DateTime.utc(2026, 6, 4),
    stickers: [
      for (final entry in stickerIds.asMap().entries)
        Sticker(
          id: entry.value,
          name: entry.value,
          sortOrder: (entry.key + 1) * 10,
          asset: UploadedAsset(
            id: 'asset_${entry.value}',
            url: '/assets/${entry.value}.png',
            thumbnailUrl: null,
            mimeType: 'image/png',
          ),
        ),
    ],
  );
}

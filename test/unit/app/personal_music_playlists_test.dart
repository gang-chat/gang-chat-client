import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/personal_music_playlists.dart';

void main() {
  test('playlist names are trimmed and limited to 64 characters', () {
    expect(normalizedPersonalPlaylistName('  夜晚  '), '夜晚');
    expect(normalizedPersonalPlaylistName('   '), isNull);
    expect(normalizedPersonalPlaylistName(List.filled(65, '歌').join()), isNull);
  });

  test('playlist item ids are normalized without changing order', () {
    expect(
      uniquePersonalPlaylistItemIds([' item_2 ', '', 'item_1', 'item_2']),
      ['item_2', 'item_1'],
    );
  });

  test('selection toggle is immutable', () {
    final original = {'item_1'};
    final added = toggledPersonalPlaylistSelection(original, 'item_2');
    final removed = toggledPersonalPlaylistSelection(added, 'item_1');

    expect(original, {'item_1'});
    expect(added, {'item_1', 'item_2'});
    expect(removed, {'item_2'});
  });

  test('playlist filters and labels share one presentation rule', () {
    expect(personalPlaylistFilterActive(keyword: '晴天', source: ''), isTrue);
    expect(
      personalPlaylistFilterActive(keyword: ' ', source: 'netease'),
      isTrue,
    );
    expect(personalPlaylistFilterActive(keyword: ' ', source: ''), isFalse);
    expect(personalPlaylistArtistsLabel([' 周杰伦 ', '']), '周杰伦');
    expect(personalPlaylistArtistsLabel(const []), '未知歌手');
    expect(personalPlaylistSourceLabel('netease'), '网易云');
  });
}

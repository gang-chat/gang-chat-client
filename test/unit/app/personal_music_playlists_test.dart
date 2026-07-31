import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/personal_music_playlists.dart';
import 'package:client/src/protocol/models.dart';

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

  test('playlist list filters names and song-count ranges', () {
    const playlists = [
      PersonalMusicPlaylist(
        id: 'empty',
        name: '空歌单',
        description: '',
        revision: 1,
        itemCount: 0,
        createdAt: null,
        updatedAt: null,
      ),
      PersonalMusicPlaylist(
        id: 'short',
        name: '夜晚精选',
        description: '',
        revision: 1,
        itemCount: 10,
        createdAt: null,
        updatedAt: null,
      ),
      PersonalMusicPlaylist(
        id: 'long',
        name: '夜晚长单',
        description: '',
        revision: 1,
        itemCount: 101,
        createdAt: null,
        updatedAt: null,
      ),
    ];

    expect(
      filteredPersonalMusicPlaylists(
        playlists,
        keyword: ' 夜晚 ',
        countFilter: personalPlaylistCount1To10,
      ).map((playlist) => playlist.id),
      ['short'],
    );
    expect(
      filteredPersonalMusicPlaylists(
        playlists,
        keyword: '',
        countFilter: personalPlaylistCountOver100,
      ).map((playlist) => playlist.id),
      ['long'],
    );
    expect(
      personalPlaylistListFilterActive(
        keyword: ' ',
        countFilter: personalPlaylistCountAll,
      ),
      isFalse,
    );
  });

  test('visible playlist selection preserves hidden selections and order', () {
    const visible = [
      PersonalMusicPlaylist(
        id: 'visible_1',
        name: '一',
        description: '',
        revision: 1,
        itemCount: 1,
        createdAt: null,
        updatedAt: null,
      ),
      PersonalMusicPlaylist(
        id: 'visible_2',
        name: '二',
        description: '',
        revision: 1,
        itemCount: 2,
        createdAt: null,
        updatedAt: null,
      ),
    ];
    final original = ['hidden'];
    final selected = toggledVisiblePersonalPlaylistSelection(
      selectedPlaylistIds: original,
      visiblePlaylists: visible,
    );
    final cleared = toggledVisiblePersonalPlaylistSelection(
      selectedPlaylistIds: selected,
      visiblePlaylists: visible,
    );

    expect(original, ['hidden']);
    expect(selected, ['hidden', 'visible_1', 'visible_2']);
    expect(personalPlaylistSelectionNumbers(selected), {
      'hidden': 1,
      'visible_1': 2,
      'visible_2': 3,
    });
    expect(cleared, ['hidden']);
  });
}

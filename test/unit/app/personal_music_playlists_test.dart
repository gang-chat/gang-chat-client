import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/personal_music_playlists.dart';
import 'package:client/src/protocol/api_client.dart';
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

  test('batch pin follows selection order and preserves unselected order', () {
    const playlists = [
      PersonalMusicPlaylist(
        id: 'first',
        name: '一',
        description: '',
        revision: 1,
        itemCount: 0,
        createdAt: null,
        updatedAt: null,
      ),
      PersonalMusicPlaylist(
        id: 'second',
        name: '二',
        description: '',
        revision: 1,
        itemCount: 0,
        createdAt: null,
        updatedAt: null,
      ),
      PersonalMusicPlaylist(
        id: 'third',
        name: '三',
        description: '',
        revision: 1,
        itemCount: 0,
        createdAt: null,
        updatedAt: null,
      ),
    ];

    expect(
      personalPlaylistOrderWithSelectionPinnedToFront(
        playlists: playlists,
        selectedPlaylistIds: ['third', 'second'],
      ),
      ['third', 'second', 'first'],
    );
    expect(
      personalPlaylistOrderWithSelectionPinnedToFront(
        playlists: playlists,
        selectedPlaylistIds: ['first', 'second'],
      ),
      isNull,
    );
    expect(
      personalPlaylistOrderWithSelectionPinnedToFront(
        playlists: playlists,
        selectedPlaylistIds: ['missing', ' third ', 'third'],
      ),
      ['third', 'first', 'second'],
    );
  });

  test('batch item pin follows selection order', () {
    const items = [
      PersonalMusicPlaylistItem(
        id: 'first',
        playlistId: 'playlist',
        trackId: 'track_1',
        source: 'netease',
        title: '一',
        artists: [],
        durationMs: 0,
        sortOrder: 10,
        createdAt: null,
      ),
      PersonalMusicPlaylistItem(
        id: 'second',
        playlistId: 'playlist',
        trackId: 'track_2',
        source: 'netease',
        title: '二',
        artists: [],
        durationMs: 0,
        sortOrder: 20,
        createdAt: null,
      ),
      PersonalMusicPlaylistItem(
        id: 'third',
        playlistId: 'playlist',
        trackId: 'track_3',
        source: 'netease',
        title: '三',
        artists: [],
        durationMs: 0,
        sortOrder: 30,
        createdAt: null,
      ),
    ];

    expect(
      personalPlaylistItemOrderWithSelectionPinnedToFront(
        items: items,
        selectedItemIds: ['third', 'second'],
      ),
      ['third', 'second', 'first'],
    );
    expect(
      personalPlaylistItemOrderWithSelectionPinnedToFront(
        items: items,
        selectedItemIds: ['first', 'second'],
      ),
      isNull,
    );
  });

  test(
    'item pin loads every page before submitting the complete order',
    () async {
      final api = _PagingPlaylistApi();
      final controller = PersonalMusicPlaylistsController(api);

      await controller.pinItems(
        playlistId: 'playlist',
        selectedItemIds: const ['third', 'second'],
      );

      expect(api.requestedPages, [1, 2]);
      expect(api.reorderedItemIds, ['third', 'second', 'first']);
    },
  );

  test(
    'playlist merge validates input and preserves selection order',
    () async {
      final api = _MergePlaylistApi();
      final controller = PersonalMusicPlaylistsController(api);

      expect(controller.canMergePlaylists, isTrue);
      expect(
        await controller.mergePlaylists(
          name: '  合并结果  ',
          playlistIds: const [' second ', 'first'],
        ),
        isNotNull,
      );
      expect(api.mergeName, '合并结果');
      expect(api.mergePlaylistIds, ['second', 'first']);

      api.mergeName = null;
      expect(
        await controller.mergePlaylists(
          name: '无效',
          playlistIds: const ['first', 'first'],
        ),
        isNull,
      );
      expect(api.mergeName, isNull);
      expect(
        PersonalMusicPlaylistsController(
          _PagingPlaylistApi(),
        ).canMergePlaylists,
        isFalse,
      );
    },
  );
}

class _MergePlaylistApi extends _PagingPlaylistApi
    implements PersonalMusicPlaylistMergeApi {
  String? mergeName;
  List<String>? mergePlaylistIds;

  @override
  Future<PersonalMusicPlaylistMergeResult> mergePersonalMusicPlaylists({
    required String name,
    required List<String> playlistIds,
  }) async {
    mergeName = name;
    mergePlaylistIds = List<String>.of(playlistIds);
    const playlist = PersonalMusicPlaylist(
      id: 'merged',
      name: '合并结果',
      description: '',
      revision: 1,
      itemCount: 2,
      createdAt: null,
      updatedAt: null,
    );
    return const PersonalMusicPlaylistMergeResult(
      playlist: playlist,
      sourceItemCount: 2,
      uniqueItemCount: 2,
      duplicateCount: 0,
      itemCount: 2,
      omittedCount: 0,
      deletedPlaylistCount: 2,
      retainedPlaylistCount: 0,
      consumedSourceItemCount: 2,
      truncated: false,
    );
  }
}

class _PagingPlaylistApi implements PersonalMusicPlaylistApi {
  static const playlist = PersonalMusicPlaylist(
    id: 'playlist',
    name: '测试歌单',
    description: '',
    revision: 1,
    itemCount: 3,
    createdAt: null,
    updatedAt: null,
  );

  static const items = [
    PersonalMusicPlaylistItem(
      id: 'first',
      playlistId: 'playlist',
      trackId: 'track_1',
      source: 'netease',
      title: '一',
      artists: [],
      durationMs: 0,
      sortOrder: 10,
      createdAt: null,
    ),
    PersonalMusicPlaylistItem(
      id: 'second',
      playlistId: 'playlist',
      trackId: 'track_2',
      source: 'netease',
      title: '二',
      artists: [],
      durationMs: 0,
      sortOrder: 20,
      createdAt: null,
    ),
    PersonalMusicPlaylistItem(
      id: 'third',
      playlistId: 'playlist',
      trackId: 'track_3',
      source: 'netease',
      title: '三',
      artists: [],
      durationMs: 0,
      sortOrder: 30,
      createdAt: null,
    ),
  ];

  final List<int> requestedPages = [];
  List<String>? reorderedItemIds;

  @override
  Future<PersonalMusicPlaylistItemsPage> getPersonalMusicPlaylist({
    required String playlistId,
    int page = 1,
    int pageSize = 50,
    String? keyword,
    String? source,
  }) async {
    requestedPages.add(page);
    final pageItems = page == 1
        ? items.take(2).toList()
        : items.skip(2).toList();
    return PersonalMusicPlaylistItemsPage(
      playlist: playlist,
      items: pageItems,
      page: page,
      pageSize: 2,
      total: items.length,
      hasMore: page == 1,
    );
  }

  @override
  Future<void> reorderPersonalMusicPlaylistItems({
    required String playlistId,
    required List<String> itemIds,
  }) async {
    reorderedItemIds = List<String>.of(itemIds);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

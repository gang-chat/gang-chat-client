import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:client/src/app/settings_controller.dart';
import 'package:client/src/app/settings_shell_state.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/protocol/sticker_pack_store.dart';
import 'package:client/src/settings/settings_page.dart';
import 'package:client/src/ui/ui.dart' as ui;

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  for (final size in [const Size(360, 800), const Size(1100, 800)]) {
    testWidgets(
      'personal playlist settings remain usable at ${size.width.toInt()} px',
      (tester) async {
        await tester.binding.setSurfaceSize(size);
        addTearDown(() => tester.binding.setSurfaceSize(null));
        final api = _FakePersonalPlaylistApi();

        await tester.pumpWidget(
          MaterialApp(
            theme: ui.uiTheme(),
            home: SettingsPage(
              initialSection: SettingsSection.playlists,
              api: api,
              controller: const SettingsController(
                api: null,
                apiBaseUrl: '',
                stickerPackStore: StickerPackStore(),
              ),
              onClose: () {},
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('我的歌单'), findsWidgets);
        expect(find.text('歌单管理'), findsOneWidget);
        expect(find.text('夜晚'), findsOneWidget);
        expect(find.text('新建歌单'), findsOneWidget);
        expect(find.text('管理'), findsOneWidget);
        expect(find.text('筛选'), findsOneWidget);
        expect(find.text('删除'), findsNothing);
        expect(
          find.byKey(const ValueKey('create-personal-music-playlist')),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);

        await tester.tap(find.text('夜晚'));
        await tester.pumpAndSettle();

        expect(find.text('搜索添加'), findsOneWidget);
        expect(find.text('管理歌曲'), findsOneWidget);
        expect(find.text('晴天'), findsOneWidget);
        expect(find.textContaining('周杰伦'), findsWidgets);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'track search follows typing, highlights matches, and ignores stale results',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(720, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final firstSearch = Completer<List<MusicBoxSearchResult>>();
      final secondSearch = Completer<List<MusicBoxSearchResult>>();
      final api = _FakePersonalPlaylistApi(
        onSearch: (keyword, source) {
          return switch (keyword) {
            '旧搜索' => firstSearch.future,
            '新搜索' => secondSearch.future,
            _ => Future.value(const <MusicBoxSearchResult>[]),
          };
        },
      );

      await _pumpPlaylistSettings(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.text('夜晚'));
      await tester.pumpAndSettle();

      final searchField = find.byWidgetPredicate(
        (widget) => widget is ui.Input && widget.hintText == '搜索歌曲添加到歌单',
      );
      expect(searchField, findsOneWidget);

      await tester.enterText(searchField, '旧搜索');
      await tester.pump(const Duration(milliseconds: 360));
      expect(api.searchRequests, ['netease:旧搜索']);

      await tester.enterText(searchField, '新搜索');
      await tester.pump(const Duration(milliseconds: 360));
      expect(api.searchRequests, ['netease:旧搜索', 'netease:新搜索']);

      secondSearch.complete(const [
        MusicBoxSearchResult(
          trackId: 'new_track',
          name: '新搜索结果',
          artists: ['新搜索歌手'],
          source: 'netease',
        ),
      ]);
      await tester.pump();

      expect(find.text('新搜索结果'), findsOneWidget);
      final highlighted = tester.widgetList<ui.HighlightedText>(
        find.byType(ui.HighlightedText),
      );
      expect(
        highlighted.any(
          (widget) => widget.text == '新搜索结果' && widget.query == '新搜索',
        ),
        isTrue,
      );

      firstSearch.complete(const [
        MusicBoxSearchResult(
          trackId: 'old_track',
          name: '旧搜索结果',
          artists: ['旧歌手'],
          source: 'netease',
        ),
      ]);
      await tester.pump();

      expect(find.text('新搜索结果'), findsOneWidget);
      expect(find.text('旧搜索结果'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('changing source reruns the current track search immediately', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(720, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakePersonalPlaylistApi();

    await _pumpPlaylistSettings(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.text('夜晚'));
    await tester.pumpAndSettle();

    final searchField = find.byWidgetPredicate(
      (widget) => widget is ui.Input && widget.hintText == '搜索歌曲添加到歌单',
    );
    await tester.enterText(searchField, '晴天');
    await tester.pump(const Duration(milliseconds: 360));
    await tester.pumpAndSettle();
    expect(api.searchRequests.last, 'netease:晴天');

    await tester.tap(find.text('哔哩哔哩').first);
    await tester.pumpAndSettle();

    expect(api.searchRequests.last, 'bilibili:晴天');
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'playlist batch mode selects cards instead of opening and filters by count',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = _FakePersonalPlaylistApi();

      await _pumpPlaylistSettings(tester, api);
      await tester.pumpAndSettle();

      await tester.tap(find.text('管理'));
      await tester.pumpAndSettle();
      expect(find.text('取消管理'), findsOneWidget);
      expect(find.text('删除'), findsOneWidget);
      expect(find.text('分享'), findsOneWidget);
      expect(find.text('置顶'), findsOneWidget);
      expect(find.text('全选'), findsOneWidget);
      expect(find.byTooltip('上移'), findsOneWidget);
      expect(find.byTooltip('下移'), findsOneWidget);
      expect(find.byTooltip('删除'), findsOneWidget);
      expect(
        tester
            .widget<ui.Button>(
              find.byKey(const ValueKey('share-personal-music-playlists')),
            )
            .onPressed,
        isNull,
      );

      await tester.tap(find.text('夜晚'));
      await tester.pumpAndSettle();
      expect(find.text('搜索添加'), findsNothing);

      final deleteButton = tester.widget<ui.Button>(
        find.byKey(const ValueKey('delete-selected-personal-music-playlists')),
      );
      expect(deleteButton.onPressed, isNotNull);

      await tester.tap(find.byTooltip('删除'));
      await tester.pumpAndSettle();
      expect(find.text('删除歌单'), findsOneWidget);
      await tester.tap(find.text('取消'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('筛选'));
      await tester.pumpAndSettle();
      expect(find.text('歌曲数量'), findsOneWidget);
      expect(
        find.byWidgetPredicate(
          (widget) => widget is ui.Input && widget.hintText == '',
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('空歌单'));
      await tester.tap(find.text('确认'));
      await tester.pumpAndSettle();

      expect(find.text('没有符合筛选条件的歌单'), findsOneWidget);
      expect(find.textContaining('筛选 0 / 1'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('batch pin follows card selection order', (tester) async {
    await tester.binding.setSurfaceSize(const Size(720, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakePersonalPlaylistApi(
      playlists: const [
        _FakePersonalPlaylistApi.playlist,
        PersonalMusicPlaylist(
          id: 'mbp_2',
          name: '白天',
          description: '',
          revision: 1,
          itemCount: 2,
          createdAt: null,
          updatedAt: null,
        ),
        PersonalMusicPlaylist(
          id: 'mbp_3',
          name: '清晨',
          description: '',
          revision: 1,
          itemCount: 3,
          createdAt: null,
          updatedAt: null,
        ),
      ],
    );

    await _pumpPlaylistSettings(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.text('管理'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('清晨'));
    await tester.tap(find.text('白天'));
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('pin-selected-personal-music-playlists')),
    );
    await tester.pumpAndSettle();

    expect(api.pinRequests, [
      ['mbp_3', 'mbp_2'],
    ]);
    expect(api.playlists.map((playlist) => playlist.id), [
      'mbp_3',
      'mbp_2',
      'mbp_1',
    ]);
    expect(
      tester.getTopLeft(find.text('清晨')).dy,
      lessThan(tester.getTopLeft(find.text('白天')).dy),
    );
    expect(
      tester.getTopLeft(find.text('白天')).dy,
      lessThan(tester.getTopLeft(find.text('夜晚')).dy),
    );

    await tester.tap(find.byTooltip('下移').first);
    await tester.pumpAndSettle();
    expect(api.moveRequests, ['mbp_3:down']);
    expect(api.playlists.map((playlist) => playlist.id), [
      'mbp_2',
      'mbp_3',
      'mbp_1',
    ]);
    expect(tester.takeException(), isNull);
  });
}

Future<void> _pumpPlaylistSettings(
  WidgetTester tester,
  _FakePersonalPlaylistApi api,
) {
  return tester.pumpWidget(
    MaterialApp(
      theme: ui.uiTheme(),
      home: SettingsPage(
        initialSection: SettingsSection.playlists,
        api: api,
        controller: const SettingsController(
          api: null,
          apiBaseUrl: '',
          stickerPackStore: StickerPackStore(),
        ),
        onClose: () {},
      ),
    ),
  );
}

class _FakePersonalPlaylistApi implements GangApi, PersonalMusicPlaylistApi {
  _FakePersonalPlaylistApi({
    this.onSearch,
    List<PersonalMusicPlaylist>? playlists,
  }) : playlists = List<PersonalMusicPlaylist>.of(
         playlists ?? const [playlist],
       );

  static const playlist = PersonalMusicPlaylist(
    id: 'mbp_1',
    name: '夜晚',
    description: '',
    revision: 2,
    itemCount: 1,
    createdAt: null,
    updatedAt: null,
  );

  final Future<List<MusicBoxSearchResult>> Function(
    String keyword,
    String source,
  )?
  onSearch;
  final List<String> searchRequests = [];
  final List<PersonalMusicPlaylist> playlists;
  final List<List<String>> pinRequests = [];
  final List<String> moveRequests = [];

  @override
  Future<PersonalMusicPlaylistPage> listPersonalMusicPlaylists({
    int page = 1,
    int pageSize = 50,
  }) async {
    return PersonalMusicPlaylistPage(
      playlists: List<PersonalMusicPlaylist>.of(playlists),
      page: 1,
      pageSize: 50,
      total: playlists.length,
      hasMore: false,
      maxPlaylists: 50,
      maxPlaylistItems: 500,
    );
  }

  @override
  Future<PersonalMusicPlaylistItemsPage> getPersonalMusicPlaylist({
    required String playlistId,
    int page = 1,
    int pageSize = 50,
    String? keyword,
    String? source,
  }) async {
    return const PersonalMusicPlaylistItemsPage(
      playlist: playlist,
      items: [
        PersonalMusicPlaylistItem(
          id: 'mbpi_1',
          playlistId: 'mbp_1',
          trackId: 'track_1',
          source: 'netease',
          title: '晴天',
          artists: ['周杰伦'],
          durationMs: 269000,
          sortOrder: 10,
          createdAt: null,
        ),
      ],
      page: 1,
      pageSize: 50,
      total: 1,
      hasMore: false,
    );
  }

  @override
  Future<PersonalMusicPlaylist> createPersonalMusicPlaylist({
    required String name,
  }) async {
    return playlist;
  }

  @override
  Future<void> deletePersonalMusicPlaylist(String playlistId) async {
    playlists.removeWhere((playlist) => playlist.id == playlistId);
  }

  @override
  Future<void> pinPersonalMusicPlaylists({
    required List<String> playlistIds,
  }) async {
    pinRequests.add(List<String>.of(playlistIds));
    final selected = <PersonalMusicPlaylist>[];
    for (final playlistId in playlistIds) {
      final index = playlists.indexWhere(
        (playlist) => playlist.id == playlistId,
      );
      if (index >= 0) selected.add(playlists[index]);
    }
    final selectedIDs = playlistIds.toSet();
    playlists
      ..removeWhere((playlist) => selectedIDs.contains(playlist.id))
      ..insertAll(0, selected);
  }

  @override
  Future<void> movePersonalMusicPlaylist({
    required String playlistId,
    required String direction,
  }) async {
    moveRequests.add('$playlistId:$direction');
    final from = playlists.indexWhere((playlist) => playlist.id == playlistId);
    final delta = direction == 'up' ? -1 : 1;
    final to = from + delta;
    if (from < 0 || to < 0 || to >= playlists.length) return;
    final moving = playlists.removeAt(from);
    playlists.insert(to, moving);
  }

  @override
  Future<List<MusicBoxSearchResult>> searchPersonalMusicPlaylistTracks({
    required String keyword,
    String? source,
    int count = 20,
    int page = 1,
  }) async {
    final normalizedSource = source ?? '';
    searchRequests.add('$normalizedSource:$keyword');
    final handler = onSearch;
    if (handler != null) return handler(keyword, normalizedSource);
    return const [];
  }

  @override
  Future<PersonalMusicPlaylistItem> addPersonalMusicPlaylistItem({
    required String playlistId,
    required MusicBoxSearchResult track,
    int? durationMs,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<void> deletePersonalMusicPlaylistItem({
    required String playlistId,
    required String itemId,
  }) async {}

  @override
  Future<void> deletePersonalMusicPlaylistItems({
    required String playlistId,
    required List<String> itemIds,
  }) async {}

  @override
  Future<void> movePersonalMusicPlaylistItem({
    required String playlistId,
    required String itemId,
    required String direction,
  }) async {}

  @override
  Future<void> reorderPersonalMusicPlaylistItems({
    required String playlistId,
    required List<String> itemIds,
  }) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

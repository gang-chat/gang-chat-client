import 'dart:async';
import 'dart:typed_data';
import 'dart:ui' show PointerDeviceKind;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderParagraph, RendererBinding;
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:client/src/app/settings_controller.dart';
import 'package:client/src/app/settings_shell_state.dart';
import 'package:client/src/app/personal_music_playlists.dart';
import 'package:client/src/app/music_track_preview.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/shell/music_track_preview_service.dart';
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
        expect(find.text('管理歌曲'), findsNothing);
        expect(find.text('管理'), findsOneWidget);
        expect(find.text('筛选'), findsOneWidget);
        expect(find.text('晴天'), findsOneWidget);
        expect(find.textContaining('周杰伦'), findsWidgets);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('empty playlist opens on the first tap while summaries refresh', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(720, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakePersonalPlaylistApi(
      playlists: const [
        _FakePersonalPlaylistApi.playlist,
        _FakePersonalPlaylistApi.emptyPlaylist,
      ],
    );

    await _pumpPlaylistSettings(tester, api);
    await tester.pumpAndSettle();

    await tester.tap(find.text('夜晚'));
    await tester.pumpAndSettle();
    expect(find.text('晴天'), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('back-to-personal-music-playlists')),
    );
    await tester.pumpAndSettle();

    final pendingRefresh = Completer<PersonalMusicPlaylistPage>();
    api.nextPlaylistListResponse = pendingRefresh;
    await tester.tap(find.byTooltip('刷新设置'));
    await tester.pump();
    await tester.pump();
    expect(api.listRequestCount, 2);
    expect(find.text('空歌单'), findsOneWidget);

    await tester.tap(find.text('空歌单'));
    await tester.pump();
    expect(find.text('搜索添加'), findsOneWidget);
    expect(find.text('歌单还是空的'), findsOneWidget);

    pendingRefresh.complete(api.playlistPage());
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('the whole playlist card opens on the first tap', (tester) async {
    await tester.binding.setSurfaceSize(const Size(720, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakePersonalPlaylistApi(
      playlists: const [_FakePersonalPlaylistApi.emptyPlaylist],
    );

    await _pumpPlaylistSettings(tester, api);
    await tester.pumpAndSettle();

    final card = find.byKey(
      const ValueKey('personal-music-playlist-card-mbp_empty'),
    );
    final cardRect = tester.getRect(card);
    await tester.tapAt(cardRect.topLeft + const Offset(3, 3));
    await tester.pumpAndSettle();

    expect(find.text('搜索添加'), findsOneWidget);
    expect(find.text('歌单还是空的'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('playlist detail header places back, icon, and name in one row', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakePersonalPlaylistApi();

    await _pumpPlaylistSettings(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.text('夜晚'));
    await tester.pumpAndSettle();

    final header = find.byKey(const ValueKey('personal-music-playlist-header'));
    final back = find.descendant(
      of: header,
      matching: find.byKey(const ValueKey('back-to-personal-music-playlists')),
    );
    final playlistIcon = find.descendant(
      of: header,
      matching: find.byIcon(Icons.queue_music_outlined),
    );
    final playlistName = find.descendant(of: header, matching: find.text('夜晚'));

    expect(header, findsOneWidget);
    expect(back, findsOneWidget);
    expect(tester.widget(back), isA<ui.ButtonIconPlain>());
    expect(playlistIcon, findsOneWidget);
    expect(playlistName, findsOneWidget);
    expect(
      tester.getCenter(back).dx,
      lessThan(tester.getCenter(playlistIcon).dx),
    );
    expect(
      tester.getCenter(playlistIcon).dx,
      lessThan(tester.getCenter(playlistName).dx),
    );
    expect(find.text('返回歌单列表'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('playlist list and detail keep controls while content scrolls', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(720, 600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final playlists = List.generate(
      16,
      (index) => PersonalMusicPlaylist(
        id: index == 0 ? 'mbp_1' : 'sticky_playlist_$index',
        name: '列表 $index',
        description: '',
        revision: 1,
        itemCount: 16,
        createdAt: null,
        updatedAt: null,
      ),
    );
    final playlistItems = List.generate(
      16,
      (index) => PersonalMusicPlaylistItem(
        id: 'sticky_item_$index',
        playlistId: 'mbp_1',
        trackId: 'track_$index',
        source: 'netease',
        title: '歌曲 $index',
        artists: const ['歌手'],
        durationMs: 180000,
        sortOrder: (index + 1) * 10,
        createdAt: null,
      ),
    );
    final api = _FakePersonalPlaylistApi(
      playlists: playlists,
      playlistItems: playlistItems,
    );

    await _pumpPlaylistSettings(tester, api);
    await tester.pumpAndSettle();

    final playlistScroll = find.byKey(
      const ValueKey('personal-music-playlists-scroll'),
    );
    final createButton = find.byKey(
      const ValueKey('create-personal-music-playlist'),
    );
    final listTitleTop = tester.getTopLeft(find.text('歌单管理')).dy;
    final createButtonTop = tester.getTopLeft(createButton).dy;
    await tester.drag(playlistScroll, const Offset(0, -300));
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(find.text('歌单管理')).dy, listTitleTop);
    expect(tester.getTopLeft(createButton).dy, createButtonTop);

    final playlistScrollable = find.descendant(
      of: playlistScroll,
      matching: find.byType(Scrollable),
    );
    expect(
      tester.state<ScrollableState>(playlistScrollable).position.pixels,
      greaterThan(0),
    );
    tester.state<ScrollableState>(playlistScrollable).position.jumpTo(0);
    await tester.pumpAndSettle();
    await tester.tap(find.text('列表 0'));
    await tester.pumpAndSettle();

    final detailHeader = find.byKey(
      const ValueKey('personal-music-playlist-header'),
    );
    final searchButton = find.byKey(
      const ValueKey('search-add-personal-music-playlist-item'),
    );
    final itemScroll = find.byKey(
      const ValueKey('personal-music-playlist-items-scroll'),
    );
    final detailHeaderTop = tester.getTopLeft(detailHeader).dy;
    final searchButtonTop = tester.getTopLeft(searchButton).dy;
    await tester.drag(itemScroll, const Offset(0, -300));
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(detailHeader).dy, detailHeaderTop);
    expect(tester.getTopLeft(searchButton).dy, searchButtonTop);
    final itemScrollable = find.descendant(
      of: itemScroll,
      matching: find.byType(Scrollable),
    );
    expect(
      tester.state<ScrollableState>(itemScrollable).position.pixels,
      greaterThan(0),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'desktop mouse opens an empty playlist after visiting another playlist',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(720, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = _FakePersonalPlaylistApi(
        playlists: const [
          _FakePersonalPlaylistApi.playlist,
          _FakePersonalPlaylistApi.emptyPlaylist,
        ],
      );

      await _pumpPlaylistSettings(tester, api, selectable: true);
      await tester.pumpAndSettle();
      final mouse = await tester.createGesture(
        pointer: 91,
        kind: PointerDeviceKind.mouse,
      );
      addTearDown(mouse.removePointer);
      final firstNameRect = tester.getRect(find.text('夜晚'));
      await mouse.addPointer(location: firstNameRect.centerLeft);
      await mouse.down(firstNameRect.centerLeft);
      await mouse.moveTo(firstNameRect.centerRight);
      await mouse.up();
      await tester.pump();
      await mouse.down(firstNameRect.center);
      await mouse.up();
      await tester.pumpAndSettle();
      expect(find.text('晴天'), findsOneWidget);

      final back = find.byKey(
        const ValueKey('back-to-personal-music-playlists'),
      );
      await mouse.moveTo(tester.getCenter(back));
      await mouse.down(tester.getCenter(back));
      await mouse.up();
      await tester.pumpAndSettle();

      final emptyCard = find.byKey(
        const ValueKey('personal-music-playlist-card-mbp_empty'),
      );
      await mouse.moveTo(tester.getCenter(emptyCard));
      await mouse.down(tester.getCenter(emptyCard));
      await mouse.up();
      await tester.pumpAndSettle();

      expect(find.text('搜索添加'), findsOneWidget);
      expect(find.text('歌单还是空的'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

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
      await tester.tap(
        find.byKey(const ValueKey('search-add-personal-music-playlist-item')),
      );
      await tester.pumpAndSettle();

      final searchField = find.byWidgetPredicate(
        (widget) => widget is ui.Input && widget.hintText == '搜索歌曲添加到歌单',
      );
      expect(searchField, findsOneWidget);
      expect(find.widgetWithText(ui.Button, '搜索'), findsNothing);

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
    await tester.tap(
      find.byKey(const ValueKey('search-add-personal-music-playlist-item')),
    );
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
    'search result uses icon-only add and opens the shared preview card',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      const result = MusicBoxSearchResult(
        trackId: 'search_track',
        name: '搜索结果歌曲',
        artists: ['搜索结果歌手'],
        source: 'netease',
      );
      final api = _PreviewPersonalPlaylistApi(
        onSearch: (_, _) async => const [result],
      );
      final previewFactory = _FakePreviewPlatformFactory();

      await _pumpPlaylistSettings(
        tester,
        api,
        previewPlatformFactory: previewFactory,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('夜晚'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('search-add-personal-music-playlist-item')),
      );
      await tester.pumpAndSettle();

      final searchField = find.byWidgetPredicate(
        (widget) => widget is ui.Input && widget.hintText == '搜索歌曲添加到歌单',
      );
      await tester.enterText(searchField, '搜索');
      await tester.pump(const Duration(milliseconds: 360));
      await tester.pumpAndSettle();

      final resultTile = find.byKey(
        const ValueKey('music-playlist-search-result:netease:search_track'),
      );
      final directAdd = find.byKey(
        const ValueKey('music-playlist-search-result-add:netease:search_track'),
      );
      expect(resultTile, findsOneWidget);
      expect(
        find.descendant(of: resultTile, matching: find.text('搜索结果歌手')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: resultTile, matching: find.text('网易云')),
        findsNothing,
      );
      expect(
        find.descendant(of: resultTile, matching: find.text('添加')),
        findsNothing,
      );
      expect(directAdd, findsOneWidget);

      await tester.tap(directAdd);
      await tester.pumpAndSettle();
      expect(api.addRequests, ['mbp_1:netease:search_track']);
      expect(find.text('试听'), findsNothing);

      await tester.tap(
        find.descendant(of: resultTile, matching: find.text('搜索结果歌曲')),
      );
      await tester.pumpAndSettle();
      expect(find.text('试听'), findsOneWidget);
      expect(find.text('添加到歌单'), findsOneWidget);

      await tester.tap(
        find.byKey(const ValueKey<String>('music-track-card-preview')),
      );
      await tester.pump();
      api.completePreview();
      await tester.pumpAndSettle();
      expect(find.text('取消试听'), findsOneWidget);
      expect(previewFactory.platform.playCalls, 1);

      await tester.tap(
        find.byKey(const ValueKey<String>('music-track-card-add-to-playlist')),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(
          const ValueKey<String>('music-track-playlist-target-add:mbp_1'),
        ),
      );
      await tester.pumpAndSettle();
      expect(api.addRequests, [
        'mbp_1:netease:search_track',
        'mbp_1:netease:search_track',
      ]);
      expect(find.text('取消试听'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'playlist item actions use dialogs and management-only card controls',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = _FakePersonalPlaylistApi(
        playlistItems: const [
          _FakePersonalPlaylistApi.playlistItem,
          PersonalMusicPlaylistItem(
            id: 'mbpi_2',
            playlistId: 'mbp_1',
            trackId: 'track_2',
            source: 'netease',
            title: '夜曲',
            artists: ['周杰伦'],
            durationMs: 226000,
            sortOrder: 20,
            createdAt: null,
          ),
        ],
      );

      await _pumpPlaylistSettings(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.text('夜晚'));
      await tester.pumpAndSettle();

      final item = find.byKey(
        const ValueKey('personal-music-playlist-item-mbpi_1'),
      );
      expect(
        find.byWidgetPredicate(
          (widget) => widget is ui.Input && widget.hintText == '筛选歌名或歌手',
        ),
        findsNothing,
      );
      expect(
        find.descendant(of: item, matching: find.byTooltip('上移')),
        findsNothing,
      );

      await tester.tap(
        find.byKey(const ValueKey('filter-personal-music-playlist-items')),
      );
      await tester.pumpAndSettle();
      expect(find.text('筛选歌曲'), findsOneWidget);
      expect(find.text('歌曲来源'), findsOneWidget);
      await tester.tap(find.text('取消'));
      await tester.pumpAndSettle();

      await tester.tap(
        find.byKey(const ValueKey('manage-personal-music-playlist-items')),
      );
      await tester.pumpAndSettle();
      expect(find.text('取消管理'), findsOneWidget);
      expect(find.text('分享'), findsOneWidget);
      expect(find.text('置顶'), findsOneWidget);
      expect(find.text('全选已加载'), findsOneWidget);
      expect(
        find.descendant(of: item, matching: find.byTooltip('上移')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: item, matching: find.byTooltip('下移')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: item, matching: find.byTooltip('删除')),
        findsOneWidget,
      );
      expect(
        tester
            .widget<ui.Button>(
              find.byKey(const ValueKey('share-personal-music-playlist-items')),
            )
            .onPressed,
        isNull,
      );

      final pinButton = find.byKey(
        const ValueKey('pin-selected-personal-music-playlist-items'),
      );
      final blankPoint = tester.getTopLeft(item) + const Offset(2, 2);
      await tester.tapAt(blankPoint);
      await tester.pumpAndSettle();
      expect(tester.widget<ui.Button>(pinButton).onPressed, isNull);
      final selectionNumber = find.byKey(
        const ValueKey('personal-music-playlist-item-selection-number-mbpi_1'),
      );
      expect(selectionNumber, findsOneWidget);
      expect(
        find.descendant(of: selectionNumber, matching: find.text('1')),
        findsOneWidget,
      );

      await tester.tapAt(blankPoint);
      await tester.pumpAndSettle();
      expect(tester.widget<ui.Button>(pinButton).onPressed, isNull);
      expect(selectionNumber, findsNothing);

      await tester.tap(
        find.descendant(of: item, matching: find.byTooltip('上移')),
      );
      await tester.pumpAndSettle();
      expect(tester.widget<ui.Button>(pinButton).onPressed, isNull);

      final secondItem = find.byKey(
        const ValueKey('personal-music-playlist-item-mbpi_2'),
      );
      await tester.tapAt(tester.getTopLeft(secondItem) + const Offset(2, 2));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<ui.Button>(
              find.byKey(
                const ValueKey('pin-selected-personal-music-playlist-items'),
              ),
            )
            .onPressed,
        isNotNull,
      );
      expect(tester.takeException(), isNull);
    },
  );

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
      expect(find.byTooltip('重命名'), findsOneWidget);
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

      final selectedCard = find.byKey(
        const ValueKey('personal-music-playlist-card-mbp_1'),
      );
      await tester.tapAt(tester.getTopLeft(selectedCard) + const Offset(2, 2));
      await tester.pumpAndSettle();
      expect(find.text('搜索添加'), findsNothing);
      final selectedPanel = tester.widget<AnimatedContainer>(
        find
            .descendant(
              of: selectedCard,
              matching: find.byType(AnimatedContainer),
            )
            .first,
      );
      expect(
        (selectedPanel.decoration as BoxDecoration).color,
        ui.UiColors.selected,
      );

      await tester.tap(
        find.descendant(of: selectedCard, matching: find.byTooltip('上移')),
      );
      await tester.pumpAndSettle();
      final panelAfterDisabledAction = tester.widget<AnimatedContainer>(
        find
            .descendant(
              of: selectedCard,
              matching: find.byType(AnimatedContainer),
            )
            .first,
      );
      expect(
        (panelAfterDisabledAction.decoration as BoxDecoration).color,
        ui.UiColors.selected,
      );

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

  testWidgets('playlist rename dialog starts with the current name', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(720, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakePersonalPlaylistApi();

    await _pumpPlaylistSettings(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.text('管理'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('重命名'));
    await tester.pumpAndSettle();

    expect(find.text('重命名歌单'), findsOneWidget);
    final nameInput = find.byKey(
      const ValueKey('personal-music-playlist-name-input'),
    );
    expect(tester.widget<ui.Input>(nameInput).controller?.text, '夜晚');

    await tester.enterText(nameInput, '夜间精选');
    await tester.tap(find.text('重命名'));
    await tester.pumpAndSettle();

    expect(api.renameRequests, ['mbp_1:夜间精选']);
    expect(find.text('夜间精选'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('playlist names move every control row before using two lines', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(600, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    const mediumName = '需要先让所有管理按钮换行但仍可单行显示的歌单';
    const longName = '需要先让所有管理按钮换行并且按钮换行以后名称仍然需要使用两行显示的超长歌单名称';
    final api = _FakePersonalPlaylistApi(
      playlists: const [
        PersonalMusicPlaylist(
          id: 'mbp_1',
          name: mediumName,
          description: '',
          revision: 1,
          itemCount: 1,
          createdAt: null,
          updatedAt: null,
        ),
        PersonalMusicPlaylist(
          id: 'mbp_2',
          name: longName,
          description: '',
          revision: 1,
          itemCount: 2,
          createdAt: null,
          updatedAt: null,
        ),
      ],
    );

    await _pumpPlaylistSettings(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.text('管理'));
    await tester.pumpAndSettle();

    for (final id in ['mbp_1', 'mbp_2']) {
      final information = find.byKey(
        ValueKey('personal-music-playlist-information-$id'),
      );
      final controls = find.byKey(
        ValueKey('personal-music-playlist-controls-$id'),
      );
      expect(information, findsOneWidget);
      expect(controls, findsOneWidget);
      expect(
        tester.getRect(controls).top,
        greaterThan(tester.getRect(information).bottom),
      );
    }
    expect(tester.widget<Text>(find.text(mediumName)).maxLines, 1);
    expect(tester.widget<Text>(find.text(longName)).maxLines, 2);
    expect(
      tester
          .renderObject<RenderParagraph>(find.text(longName))
          .didExceedMaxLines,
      isFalse,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('playlist cards use hover feedback and consistent spacing', (
    tester,
  ) async {
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
      ],
    );

    await _pumpPlaylistSettings(tester, api);
    await tester.pumpAndSettle();

    final firstCard = find.byKey(
      const ValueKey('personal-music-playlist-card-mbp_1'),
    );
    final secondCard = find.byKey(
      const ValueKey('personal-music-playlist-card-mbp_2'),
    );
    expect(firstCard, findsOneWidget);
    expect(secondCard, findsOneWidget);
    expect(
      tester.getRect(secondCard).top - tester.getRect(firstCard).bottom,
      greaterThanOrEqualTo(9),
    );

    final animatedPanel = find
        .descendant(of: firstCard, matching: find.byType(AnimatedContainer))
        .first;
    final before = tester.widget<AnimatedContainer>(animatedPanel);
    final beforeDecoration = before.decoration as BoxDecoration;
    expect(beforeDecoration.color, isNot(ui.UiColors.selected));

    const mouseDevice = 1;
    final mouse = await tester.createGesture(
      pointer: 42,
      kind: PointerDeviceKind.mouse,
    );
    addTearDown(mouse.removePointer);
    await mouse.addPointer(location: tester.getCenter(firstCard));
    await tester.pumpAndSettle();
    expect(
      RendererBinding.instance.mouseTracker.debugDeviceActiveCursor(
        mouseDevice,
      ),
      SystemMouseCursors.click,
    );

    final hovered = tester.widget<AnimatedContainer>(animatedPanel);
    final hoveredDecoration = hovered.decoration as BoxDecoration;
    expect(hoveredDecoration.color, ui.UiColors.selected);
    expect(
      (hoveredDecoration.border! as Border).top.color,
      ui.UiColors.selectedBorder,
    );
    await mouse.moveTo(tester.getTopLeft(firstCard) + const Offset(2, 2));
    await tester.pump();
    expect(
      RendererBinding.instance.mouseTracker.debugDeviceActiveCursor(
        mouseDevice,
      ),
      SystemMouseCursors.click,
    );
    expect(tester.takeException(), isNull);
  });

  for (final platform in [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      'room playlist panel is readable but not editable by members on ${platform.name}',
      (tester) async {
        final size = platform == TargetPlatform.android
            ? const Size(360, 800)
            : const Size(720, 800);
        await tester.binding.setSurfaceSize(size);
        addTearDown(() => tester.binding.setSurfaceSize(null));
        final api = _FakeRoomPlaylistApi();
        final controller = PersonalMusicPlaylistsController.room(
          roomApi: api,
          roomId: 'room_1',
          canManage: false,
          searchTracks: ({required keyword, required source}) async => const [],
        );

        await tester.pumpWidget(
          MaterialApp(
            theme: ui.uiTheme().copyWith(platform: platform),
            home: Scaffold(
              body: MusicPlaylistsPanel(
                controller: controller,
                title: '房间歌单',
                unavailableMessage: '房间歌单暂不可用',
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('房间歌单'), findsOneWidget);
        expect(find.text('房间精选'), findsOneWidget);
        expect(
          tester
              .widget<ui.Button>(
                find.byKey(const ValueKey('create-personal-music-playlist')),
              )
              .onPressed,
          isNull,
        );
        expect(
          tester
              .widget<ui.Button>(
                find.byKey(const ValueKey('manage-personal-music-playlists')),
              )
              .onPressed,
          isNull,
        );

        await tester.tap(find.text('房间精选'));
        await tester.pumpAndSettle();
        expect(find.text('房间歌曲'), findsOneWidget);
        expect(
          tester
              .widget<ui.Button>(
                find.byKey(
                  const ValueKey('search-add-personal-music-playlist-item'),
                ),
              )
              .onPressed,
          isNull,
        );
        expect(
          tester
              .widget<ui.Button>(
                find.byKey(
                  const ValueKey('manage-personal-music-playlist-items'),
                ),
              )
              .onPressed,
          isNull,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }
}

Future<void> _pumpPlaylistSettings(
  WidgetTester tester,
  _FakePersonalPlaylistApi api, {
  bool selectable = false,
  MusicTrackPreviewPlatformFactory? previewPlatformFactory,
}) {
  final settings = SettingsPage(
    initialSection: SettingsSection.playlists,
    api: api,
    controller: const SettingsController(
      api: null,
      apiBaseUrl: '',
      stickerPackStore: StickerPackStore(),
    ),
    onClose: () {},
    musicTrackPreviewPlatformFactory:
        previewPlatformFactory ??
        const DefaultMusicTrackPreviewPlatformFactory(),
  );
  return tester.pumpWidget(
    MaterialApp(
      theme: ui.uiTheme(),
      home: selectable ? SelectionArea(child: settings) : settings,
    ),
  );
}

class _FakePersonalPlaylistApi implements GangApi, PersonalMusicPlaylistApi {
  _FakePersonalPlaylistApi({
    this.onSearch,
    List<PersonalMusicPlaylist>? playlists,
    List<PersonalMusicPlaylistItem>? playlistItems,
  }) : playlists = List<PersonalMusicPlaylist>.of(
         playlists ?? const [playlist],
       ),
       playlistItems = List<PersonalMusicPlaylistItem>.of(
         playlistItems ?? const [playlistItem],
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

  static const emptyPlaylist = PersonalMusicPlaylist(
    id: 'mbp_empty',
    name: '空歌单',
    description: '',
    revision: 1,
    itemCount: 0,
    createdAt: null,
    updatedAt: null,
  );

  static const playlistItem = PersonalMusicPlaylistItem(
    id: 'mbpi_1',
    playlistId: 'mbp_1',
    trackId: 'track_1',
    source: 'netease',
    title: '晴天',
    artists: ['周杰伦'],
    durationMs: 269000,
    sortOrder: 10,
    createdAt: null,
  );

  final Future<List<MusicBoxSearchResult>> Function(
    String keyword,
    String source,
  )?
  onSearch;
  final List<String> searchRequests = [];
  final List<PersonalMusicPlaylist> playlists;
  final List<PersonalMusicPlaylistItem> playlistItems;
  final List<List<String>> pinRequests = [];
  final List<String> moveRequests = [];
  final List<String> renameRequests = [];
  final List<String> addRequests = [];
  int listRequestCount = 0;
  Completer<PersonalMusicPlaylistPage>? nextPlaylistListResponse;

  PersonalMusicPlaylistPage playlistPage() {
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
  Future<PersonalMusicPlaylistPage> listPersonalMusicPlaylists({
    int page = 1,
    int pageSize = 50,
  }) async {
    listRequestCount += 1;
    final pending = nextPlaylistListResponse;
    if (pending != null) {
      nextPlaylistListResponse = null;
      return pending.future;
    }
    return playlistPage();
  }

  @override
  Future<PersonalMusicPlaylistItemsPage> getPersonalMusicPlaylist({
    required String playlistId,
    int page = 1,
    int pageSize = 50,
    String? keyword,
    String? source,
  }) async {
    if (playlistId == emptyPlaylist.id) {
      return const PersonalMusicPlaylistItemsPage(
        playlist: emptyPlaylist,
        items: [],
        page: 1,
        pageSize: 50,
        total: 0,
        hasMore: false,
      );
    }
    final currentPlaylist = PersonalMusicPlaylist(
      id: playlist.id,
      name: playlist.name,
      description: playlist.description,
      revision: playlist.revision,
      itemCount: playlistItems.length,
      createdAt: playlist.createdAt,
      updatedAt: playlist.updatedAt,
    );
    return PersonalMusicPlaylistItemsPage(
      playlist: currentPlaylist,
      items: List<PersonalMusicPlaylistItem>.of(playlistItems),
      page: 1,
      pageSize: 50,
      total: playlistItems.length,
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
  Future<PersonalMusicPlaylist> renamePersonalMusicPlaylist({
    required String playlistId,
    required String name,
  }) async {
    renameRequests.add('$playlistId:$name');
    final index = playlists.indexWhere((playlist) => playlist.id == playlistId);
    if (index < 0) return playlist;
    final current = playlists[index];
    final renamed = PersonalMusicPlaylist(
      id: current.id,
      name: name,
      description: current.description,
      revision: current.revision + 1,
      itemCount: current.itemCount,
      createdAt: current.createdAt,
      updatedAt: current.updatedAt,
    );
    playlists[index] = renamed;
    return renamed;
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
  }) async {
    addRequests.add('$playlistId:${track.source}:${track.trackId}');
    final item = PersonalMusicPlaylistItem(
      id: 'added_${addRequests.length}',
      playlistId: playlistId,
      trackId: track.trackId,
      source: track.source,
      title: track.name,
      artists: track.artists,
      durationMs: durationMs ?? 0,
      sortOrder: (playlistItems.length + 1) * 10,
      createdAt: null,
    );
    playlistItems.add(item);
    return item;
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

class _PreviewPersonalPlaylistApi extends _FakePersonalPlaylistApi
    implements MusicTrackPreviewApi {
  _PreviewPersonalPlaylistApi({super.onSearch});

  final Completer<DownloadedFile> _preview = Completer<DownloadedFile>();

  void completePreview() {
    if (_preview.isCompleted) return;
    _preview.complete(
      DownloadedFile(
        bytes: Uint8List.fromList([0, 0, 0, 24, 102, 116, 121, 112]),
        filename: 'preview.m4a',
        mimeType: 'audio/mp4',
      ),
    );
  }

  @override
  Future<DownloadedFile> downloadMusicTrackPreview({
    required String source,
    required String trackId,
  }) => _preview.future;
}

class _FakePreviewPlatformFactory implements MusicTrackPreviewPlatformFactory {
  final _FakePreviewPlatform platform = _FakePreviewPlatform();

  @override
  MusicTrackPreviewPlatform create() => platform;
}

class _FakePreviewPlatform implements MusicTrackPreviewPlatform {
  final StreamController<void> _completed = StreamController<void>.broadcast();
  int playCalls = 0;

  @override
  Stream<void> get onCompleted => _completed.stream;

  @override
  Future<MusicTrackPreviewAsset?> findCached(String cacheKey) async => null;

  @override
  Future<MusicTrackPreviewAsset> store(
    String cacheKey,
    Uint8List bytes,
  ) async => MusicTrackPreviewAsset(cacheKey);

  @override
  Future<void> play(MusicTrackPreviewAsset asset) async {
    playCalls += 1;
  }

  @override
  Future<void> stop() async {}

  @override
  Future<void> dispose() async {
    await _completed.close();
  }
}

class _FakeRoomPlaylistApi implements RoomMusicPlaylistApi {
  static const playlist = PersonalMusicPlaylist(
    id: 'mbp_room_1',
    name: '房间精选',
    description: '',
    revision: 1,
    itemCount: 1,
    createdAt: null,
    updatedAt: null,
  );

  static const item = PersonalMusicPlaylistItem(
    id: 'mbpi_room_1',
    playlistId: 'mbp_room_1',
    trackId: 'track_room_1',
    source: 'netease',
    title: '房间歌曲',
    artists: ['歌手'],
    durationMs: 180000,
    sortOrder: 10,
    createdAt: null,
  );

  @override
  Future<PersonalMusicPlaylistPage> listRoomMusicPlaylists({
    required String roomId,
    int page = 1,
    int pageSize = 50,
  }) async {
    expect(roomId, 'room_1');
    return const PersonalMusicPlaylistPage(
      playlists: [playlist],
      page: 1,
      pageSize: 50,
      total: 1,
      hasMore: false,
      maxPlaylists: 50,
      maxPlaylistItems: 500,
    );
  }

  @override
  Future<PersonalMusicPlaylistItemsPage> getRoomMusicPlaylist({
    required String roomId,
    required String playlistId,
    int page = 1,
    int pageSize = 50,
    String? keyword,
    String? source,
  }) async {
    expect(roomId, 'room_1');
    expect(playlistId, playlist.id);
    return const PersonalMusicPlaylistItemsPage(
      playlist: playlist,
      items: [item],
      page: 1,
      pageSize: 50,
      total: 1,
      hasMore: false,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

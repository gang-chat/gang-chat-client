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
        expect(find.text('夜晚'), findsOneWidget);
        expect(
          find.byKey(const ValueKey('create-personal-music-playlist')),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);

        await tester.tap(find.text('管理'));
        await tester.pumpAndSettle();

        expect(find.text('搜索添加'), findsOneWidget);
        expect(find.text('管理歌曲'), findsOneWidget);
        expect(find.text('晴天'), findsOneWidget);
        expect(find.textContaining('周杰伦'), findsWidgets);
        expect(tester.takeException(), isNull);
      },
    );
  }
}

class _FakePersonalPlaylistApi implements GangApi, PersonalMusicPlaylistApi {
  static const playlist = PersonalMusicPlaylist(
    id: 'mbp_1',
    name: '夜晚',
    description: '',
    revision: 2,
    itemCount: 1,
    createdAt: null,
    updatedAt: null,
  );

  @override
  Future<PersonalMusicPlaylistPage> listPersonalMusicPlaylists({
    int page = 1,
    int pageSize = 50,
  }) async {
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
  Future<void> deletePersonalMusicPlaylist(String playlistId) async {}

  @override
  Future<List<MusicBoxSearchResult>> searchPersonalMusicPlaylistTracks({
    required String keyword,
    String? source,
    int count = 20,
    int page = 1,
  }) async {
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

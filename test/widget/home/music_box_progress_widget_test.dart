import 'dart:async';
import 'dart:typed_data';

import 'package:client/src/app/music_box_controller.dart';
import 'package:client/src/app/music_track_preview.dart';
import 'package:client/src/home/hover_card_anchor.dart';
import 'package:client/src/home/live_channel_pane.dart';
import 'package:client/src/home/music_playlist_profile_card.dart';
import 'package:client/src/home/room_profile_card.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/ui.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// Widget-level coverage for the server-authoritative progress bar: it renders
/// the snapshot's reported position verbatim and updates only when a fresh
/// snapshot arrives — no local stepping, no client clock.

const _playlistCurrentUser = CurrentUser(
  id: 'playlist-current-user',
  uid: '100001',
  username: 'playlist_owner',
  displayName: '歌单创建人',
  bio: '',
  gender: 'secret',
  email: null,
  emailPublic: false,
  phoneNumber: null,
  phoneNumberPublic: false,
  avatarUrl: null,
  defaultAvatarKey: 'green-2',
  isSuperuser: false,
  createdAt: null,
);

const _playlistRoom = PublicRoom(
  id: 'room-1',
  rid: 'R10001',
  name: '音乐房间',
  avatarUrl: null,
  defaultAvatarKey: 'room-2',
  visibility: 'public',
  joinPolicy: 'open',
  memberCount: 8,
  liveParticipantCount: 2,
  joined: true,
  joinState: 'joined',
);

Widget _host(
  MusicBoxState state,
  TextEditingController searchController, {
  TargetPlatform? platform,
  double? height,
  bool resizeToAvoidBottomInset = true,
  List<MusicBoxSearchResult> searchResults = const [],
  String source = 'netease',
  double volume = 1,
  ValueChanged<double>? onVolumeChanged,
  MusicBoxController? musicBoxController,
  String? roomId,
  ValueChanged<MusicBoxState>? onStateChanged,
  ValueChanged<MusicBoxSearchResult>? onQueueResult,
  CurrentUser? currentUser,
  PublicRoom? room,
  UserProfileResolver? onResolveUserProfile,
  RoomProfileResolver? onResolveRoomProfile,
  UserProfileActionBuilder? userProfileActionBuilder,
  MusicTrackPreviewPlatformFactory? previewPlatformFactory,
  VoidCallback? onCreateFirstRoomPlaylist,
  VoidCallback? onCreateFirstPersonalPlaylist,
}) {
  return MaterialApp(
    theme: uiTheme().copyWith(platform: platform),
    home: Scaffold(
      resizeToAvoidBottomInset: resizeToAvoidBottomInset,
      body: SizedBox(
        width: 360,
        height: height,
        child: LiveMusicBoxPanel(
          state: state,
          searchController: searchController,
          searchResults: searchResults,
          searching: false,
          searchError: null,
          source: source,
          controller: musicBoxController,
          roomId: roomId,
          room: room,
          onStateChanged: onStateChanged,
          currentUser: currentUser,
          onResolveUserProfile: onResolveUserProfile,
          onResolveRoomProfile: onResolveRoomProfile,
          userProfileActionBuilder: userProfileActionBuilder,
          onCreateFirstRoomPlaylist: onCreateFirstRoomPlaylist,
          onCreateFirstPersonalPlaylist: onCreateFirstPersonalPlaylist,
          previewPlatformFactory: previewPlatformFactory,
          onTogglePlayback: () {},
          onSkip: () {},
          onQueueResult: onQueueResult ?? (_) {},
          onRemoveItem: (_) {},
          onSourceChanged: (_) {},
          onClose: () {},
          volume: volume,
          onVolumeChanged: onVolumeChanged ?? (_) {},
        ),
      ),
    ),
  );
}

class _MusicBoxApiFake implements GangApi {
  _MusicBoxApiFake(this.state);

  final MusicBoxState state;
  String? action;
  String? itemId;
  String? commandId;
  int? expectedRevision;
  MusicBoxActiveSourceType? activatedSourceType;

  @override
  Future<MusicBoxState> controlMusicBox({
    required String roomId,
    required String action,
    String? itemId,
    String? mode,
    String? commandId,
    int? expectedRevision,
  }) async {
    this.action = action;
    this.itemId = itemId;
    this.commandId = commandId;
    this.expectedRevision = expectedRevision;
    return state;
  }

  @override
  Future<MusicBoxState> activateMusicBoxPlaylist({
    required String roomId,
    required MusicBoxActiveSourceType sourceType,
    String? playlistId,
    bool startPlay = true,
  }) async {
    activatedSourceType = sourceType;
    return state;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _RoomPlaylistApiFake extends _MusicBoxApiFake
    implements RoomMusicPlaylistApi, PersonalMusicPlaylistApi {
  _RoomPlaylistApiFake(
    super.state, {
    required this.playlist,
    required this.items,
    this.personalPlaylists = const [],
  });

  final PersonalMusicPlaylist playlist;
  final List<PersonalMusicPlaylistItem> items;
  final List<PersonalMusicPlaylist> personalPlaylists;

  @override
  Future<PersonalMusicPlaylistPage> listRoomMusicPlaylists({
    required String roomId,
    int page = 1,
    int pageSize = 50,
  }) async {
    return PersonalMusicPlaylistPage(
      playlists: [playlist],
      page: page,
      pageSize: pageSize,
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
    return PersonalMusicPlaylistItemsPage(
      playlist: playlist,
      items: items,
      page: page,
      pageSize: pageSize,
      total: items.length,
      hasMore: false,
    );
  }

  @override
  Future<PersonalMusicPlaylistPage> listPersonalMusicPlaylists({
    int page = 1,
    int pageSize = 50,
  }) async {
    return PersonalMusicPlaylistPage(
      playlists: personalPlaylists,
      page: page,
      pageSize: pageSize,
      total: personalPlaylists.length,
      hasMore: false,
      maxPlaylists: 50,
      maxPlaylistItems: 500,
    );
  }
}

class _EmptyPlaylistApiFake extends _MusicBoxApiFake
    implements RoomMusicPlaylistApi, PersonalMusicPlaylistApi {
  _EmptyPlaylistApiFake(super.state);

  PersonalMusicPlaylistPage _emptyPage(int page, int pageSize) {
    return PersonalMusicPlaylistPage(
      playlists: const [],
      page: page,
      pageSize: pageSize,
      total: 0,
      hasMore: false,
      maxPlaylists: 50,
      maxPlaylistItems: 500,
    );
  }

  @override
  Future<PersonalMusicPlaylistPage> listRoomMusicPlaylists({
    required String roomId,
    int page = 1,
    int pageSize = 50,
  }) async => _emptyPage(page, pageSize);

  @override
  Future<PersonalMusicPlaylistPage> listPersonalMusicPlaylists({
    int page = 1,
    int pageSize = 50,
  }) async => _emptyPage(page, pageSize);
}

class _CloneablePlaylistApiFake extends _RoomPlaylistApiFake
    implements MusicBoxActivePlaylistCloneApi, MusicTrackPreviewApi {
  _CloneablePlaylistApiFake(super.state)
    : super(
        playlist: const PersonalMusicPlaylist(
          id: 'unused',
          name: 'unused',
          description: '',
          revision: 1,
          itemCount: 0,
          createdAt: null,
          updatedAt: null,
        ),
        items: const [],
      );

  String? clonedPlaylistId;
  String? clonedSnapshotId;

  @override
  Future<PersonalMusicPlaylist> cloneActiveMusicBoxPlaylist({
    required String roomId,
    required String playlistId,
    required String snapshotId,
  }) async {
    clonedPlaylistId = playlistId;
    clonedSnapshotId = snapshotId;
    return const PersonalMusicPlaylist(
      id: 'cloned-playlist',
      name: '朋友的歌单 · 夜晚精选',
      description: '',
      revision: 1,
      itemCount: 2,
      createdAt: null,
      updatedAt: null,
    );
  }

  @override
  Future<DownloadedFile> downloadMusicTrackPreview({
    required String source,
    required String trackId,
  }) async {
    return DownloadedFile(
      bytes: Uint8List.fromList([1, 2, 3]),
      filename: 'preview.ogg',
      mimeType: 'audio/ogg',
    );
  }
}

class _MusicBoxPreviewFactory implements MusicTrackPreviewPlatformFactory {
  @override
  MusicTrackPreviewPlatform create() => _MusicBoxPreviewPlatform();
}

class _MusicBoxPreviewPlatform implements MusicTrackPreviewPlatform {
  final StreamController<void> _completed = StreamController<void>.broadcast();

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
  Future<void> play(MusicTrackPreviewAsset asset) async {}

  @override
  Future<void> stop() async {}

  @override
  Future<void> dispose() => _completed.close();
}

MusicBoxState _state({
  required MusicBoxPlaybackState playbackState,
  required int positionMs,
  String currentItemId = 'a',
  List<MusicBoxQueueItem>? queue,
  List<MusicBoxQueueItem>? temporaryQueue,
  MusicBoxActiveSource activeSource = const MusicBoxActiveSource(),
  int revision = 0,
  bool hasRevision = false,
}) {
  final activeQueue =
      queue ??
      [
        MusicBoxQueueItem(
          id: currentItemId,
          source: 'netease',
          trackId: 'track-$currentItemId',
          title: 'Song',
          artist: '',
          durationMs: 200000,
          status: MusicBoxQueueItemStatus.ready,
          fileSizeBytes: 0,
          error: '',
          addedByUserId: 'user',
          createdAt: null,
        ),
      ];
  return MusicBoxState(
    enabled: true,
    playback: MusicBoxPlayback(
      state: playbackState,
      currentItemId: currentItemId,
      positionMs: positionMs,
      volume: 100,
      updatedAt: null,
    ),
    queue: activeQueue,
    usage: const MusicBoxUsage(usedBytes: 0, limitBytes: 0),
    revision: revision,
    hasRevision: hasRevision,
    activeSource: activeSource,
    temporaryQueuedCount: temporaryQueue?.length ?? 0,
    temporaryQueue: temporaryQueue ?? const <MusicBoxQueueItem>[],
  );
}

Future<void> _toggleAddSources(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey<String>('music-box-add-toggle')));
  await tester.pump();
}

void main() {
  testWidgets('now playing title moves only when it overflows', (tester) async {
    const longTitle = '一首需要在音乐盒顶部左右往返显示完整内容的特别长歌曲名称';
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final item = MusicBoxQueueItem(
      id: 'marquee-current',
      source: 'netease',
      trackId: 'marquee-track',
      title: longTitle,
      artist: '测试歌手',
      durationMs: 200000,
      status: MusicBoxQueueItemStatus.ready,
      fileSizeBytes: 0,
      error: '',
      addedByUserId: 'user',
      createdAt: null,
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.playing,
          positionMs: 30000,
          currentItemId: item.id,
          queue: [item],
        ),
        controller,
      ),
    );
    await tester.pump();

    final track = find.byKey(
      const ValueKey<String>('music-box-now-playing:title-marquee-track'),
    );
    expect(track, findsOneWidget);
    await tester.pump(const Duration(milliseconds: 900));
    await tester.pump(const Duration(milliseconds: 400));
    final firstOffset = tester.widget<Transform>(track).transform[12];
    await tester.pump(const Duration(milliseconds: 400));
    final secondOffset = tester.widget<Transform>(track).transform[12];
    expect(firstOffset, lessThan(0));
    expect(secondOffset, lessThan(firstOffset));
    expect(find.text(longTitle), findsWidgets);

    const shortItem = MusicBoxQueueItem(
      id: 'short-current',
      source: 'netease',
      trackId: 'short-track',
      title: '短歌名',
      artist: '测试歌手',
      durationMs: 200000,
      status: MusicBoxQueueItemStatus.ready,
      fileSizeBytes: 0,
      error: '',
      addedByUserId: 'user',
      createdAt: null,
    );
    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.playing,
          positionMs: 31000,
          currentItemId: shortItem.id,
          queue: const [shortItem],
        ),
        controller,
      ),
    );
    await tester.pump();
    expect(track, findsNothing);
    expect(find.text('短歌名'), findsWidgets);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'opening the music panel centers the currently playing queue item once',
    (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      final queue = List<MusicBoxQueueItem>.generate(20, (index) {
        return MusicBoxQueueItem(
          id: 'queue-$index',
          source: 'netease',
          trackId: 'track-$index',
          title: index.isEven
              ? '第 $index 首歌'
              : '第 $index 首需要多行显示以验证自适应高度定位的很长歌曲名称',
          artist: '歌手 $index',
          durationMs: 200000,
          status: MusicBoxQueueItemStatus.ready,
          fileSizeBytes: 0,
          error: '',
          addedByUserId: 'user',
          createdAt: null,
        );
      });
      final state = _state(
        playbackState: MusicBoxPlaybackState.playing,
        positionMs: 30000,
        currentItemId: 'queue-10',
        queue: queue,
      );

      await tester.pumpWidget(_host(state, controller, height: 500));
      await tester.pump();
      await tester.pump();

      final viewport = find.byKey(
        const ValueKey<String>('music-box-results-viewport'),
      );
      final currentTile = find.byKey(
        const ValueKey<String>('music-box-queue-tile:queue-10'),
      );
      expect(currentTile, findsOneWidget);
      expect(
        (tester.getCenter(currentTile).dy - tester.getCenter(viewport).dy)
            .abs(),
        lessThan(1.0),
      );

      final queueList = find.byKey(
        const ValueKey<String>('music-box-queue-list'),
      );
      await tester.drag(queueList, const Offset(0, -60));
      await tester.pump(const Duration(milliseconds: 300));
      final manuallyShiftedCenter = tester.getCenter(currentTile).dy;
      await tester.pumpWidget(
        _host(
          _state(
            playbackState: MusicBoxPlaybackState.playing,
            positionMs: 31000,
            currentItemId: 'queue-10',
            queue: queue,
            revision: 1,
            hasRevision: true,
          ),
          controller,
          height: 500,
        ),
      );
      await tester.pump();
      await tester.pump();
      expect(
        tester.getCenter(currentTile).dy,
        closeTo(manuallyShiftedCenter, 1),
      );
      expect(tester.takeException(), isNull);
    },
  );

  for (final boundary in const [('top', 0), ('bottom', 11)]) {
    testWidgets(
      'opening the music panel keeps the ${boundary.$1} current item at its boundary',
      (tester) async {
        final controller = TextEditingController();
        addTearDown(controller.dispose);
        final queue = List<MusicBoxQueueItem>.generate(12, (index) {
          return MusicBoxQueueItem(
            id: 'boundary-$index',
            source: 'netease',
            trackId: 'track-$index',
            title: '第 $index 首歌',
            artist: '歌手',
            durationMs: 200000,
            status: MusicBoxQueueItemStatus.ready,
            fileSizeBytes: 0,
            error: '',
            addedByUserId: 'user',
            createdAt: null,
          );
        });
        final currentIndex = boundary.$2;
        await tester.pumpWidget(
          _host(
            _state(
              playbackState: MusicBoxPlaybackState.playing,
              positionMs: 30000,
              currentItemId: 'boundary-$currentIndex',
              queue: queue,
            ),
            controller,
            height: 500,
          ),
        );
        await tester.pump();
        await tester.pump();

        final viewportRect = tester.getRect(
          find.byKey(const ValueKey<String>('music-box-results-viewport')),
        );
        final tileRect = tester.getRect(
          find.byKey(
            ValueKey<String>('music-box-queue-tile:boundary-$currentIndex'),
          ),
        );
        if (currentIndex == 0) {
          expect(tileRect.top, closeTo(viewportRect.top, 1));
        } else {
          expect(tileRect.bottom, closeTo(viewportRect.bottom, 1));
        }
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'source picker hides QQ Music and normalizes a legacy selection',
    (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        _host(
          _state(playbackState: MusicBoxPlaybackState.stopped, positionMs: 0),
          controller,
          source: 'tencent',
        ),
      );
      await _toggleAddSources(tester);

      expect(find.text('QQ音乐'), findsNothing);
      expect(find.text('网易云'), findsOneWidget);
      expect(find.text('哔哩哔哩'), findsOneWidget);
      expect(
        tester
            .widget<SegmentedControl<String>>(
              find.byType(SegmentedControl<String>),
            )
            .value,
        'netease',
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'empty room and personal playlists expose their create-first actions',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      final state = _state(
        playbackState: MusicBoxPlaybackState.stopped,
        positionMs: 0,
      );
      final api = _EmptyPlaylistApiFake(state);
      var roomCreateCount = 0;
      var personalCreateCount = 0;

      await tester.pumpWidget(
        _host(
          state,
          searchController,
          height: 500,
          roomId: 'room-1',
          musicBoxController: MusicBoxController(api: api),
          onCreateFirstRoomPlaylist: () => roomCreateCount += 1,
          onCreateFirstPersonalPlaylist: () => personalCreateCount += 1,
        ),
      );
      await _toggleAddSources(tester);

      await tester.tap(find.text('房间歌单'));
      await tester.pumpAndSettle();
      expect(find.text('还没有房间歌单'), findsOneWidget);
      final roomAction = find.byKey(
        const ValueKey<String>('music-box-create-first-room-playlist'),
      );
      expect(roomAction, findsOneWidget);
      expect(find.text('新建第一个歌单'), findsOneWidget);
      await tester.tap(roomAction);
      expect(roomCreateCount, 1);
      expect(personalCreateCount, 0);

      await tester.tap(find.text('我的歌单'));
      await tester.pumpAndSettle();
      expect(find.text('还没有个人歌单'), findsOneWidget);
      final personalAction = find.byKey(
        const ValueKey<String>('music-box-create-first-personal-playlist'),
      );
      expect(personalAction, findsOneWidget);
      await tester.tap(personalAction);
      expect(roomCreateCount, 1);
      expect(personalCreateCount, 1);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'room create-first playlist action stays hidden without permission',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      final state = _state(
        playbackState: MusicBoxPlaybackState.stopped,
        positionMs: 0,
      );

      await tester.pumpWidget(
        _host(
          state,
          searchController,
          height: 500,
          roomId: 'room-1',
          musicBoxController: MusicBoxController(
            api: _EmptyPlaylistApiFake(state),
          ),
          onCreateFirstPersonalPlaylist: () {},
        ),
      );
      await _toggleAddSources(tester);
      await tester.tap(find.text('房间歌单'));
      await tester.pumpAndSettle();

      expect(find.text('还没有房间歌单'), findsOneWidget);
      expect(find.text('新建第一个歌单'), findsNothing);
      expect(
        find.byKey(
          const ValueKey<String>('music-box-create-first-room-playlist'),
        ),
        findsNothing,
      );
      expect(tester.takeException(), isNull);
    },
  );

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} defaults to current queue and plus toggles add sources',
      (tester) async {
        final controller = TextEditingController();
        addTearDown(controller.dispose);
        const requestedItem = MusicBoxQueueItem(
          id: 'requested-track',
          source: 'netease',
          trackId: 'track-requested',
          title: '点播歌曲',
          artist: '歌手',
          durationMs: 180000,
          status: MusicBoxQueueItemStatus.ready,
          fileSizeBytes: 1024,
          error: '',
          addedByUserId: 'requester',
          createdAt: null,
          requestedBy: MusicBoxRequester(
            userId: 'requester',
            displayName: '房间专属名',
            avatarLabel: '点歌用户',
            avatarUrl: null,
            defaultAvatarKey: 'blue-3',
          ),
        );

        await tester.pumpWidget(
          _host(
            _state(
              playbackState: MusicBoxPlaybackState.stopped,
              positionMs: 0,
              queue: const [requestedItem],
              temporaryQueue: const [requestedItem],
            ),
            controller,
            platform: platform,
            height: 500,
          ),
        );

        expect(find.textContaining('当前：'), findsNothing);
        expect(find.text('点歌队列'), findsOneWidget);
        expect(find.text('点播歌曲'), findsOneWidget);
        expect(find.textContaining('由 '), findsNothing);
        final queueTile = find.byKey(
          const ValueKey<String>('music-box-queue-tile:requested-track'),
        );
        expect(
          find.descendant(of: queueTile, matching: find.text('网易云')),
          findsNothing,
        );
        expect(
          find.descendant(of: queueTile, matching: find.text('3:00')),
          findsNothing,
        );
        expect(find.text('搜索添加'), findsNothing);
        await tester.tap(
          find.byKey(const ValueKey<String>('music-box-current-queue-header')),
        );
        await tester.pump();
        expect(find.byType(MusicPlaylistHoverCard), findsNothing);
        expect(find.text('创建日期'), findsNothing);
        final queueAction = tester.widget<ButtonIcon>(
          find.byKey(const ValueKey<String>('music-box-queue-context-action')),
        );
        expect(queueAction.tooltip, '清空点歌队列');

        await tester.tap(find.text('点播歌曲'));
        await tester.pump();
        expect(
          find.byKey(
            const ValueKey<String>('music-box-song-card:requested-track'),
          ),
          findsOneWidget,
        );
        expect(find.text('时长'), findsOneWidget);
        expect(find.text('3:00'), findsWidgets);
        expect(find.text('网易云'), findsOneWidget);
        expect(find.text('点歌人'), findsOneWidget);
        expect(find.text('房间专属名'), findsOneWidget);
        final requesterAvatar = tester.widget<Avatar>(find.byType(Avatar).last);
        expect(requesterAvatar.label, '点歌用户');

        await tester.tapAt(const Offset(355, 495));
        await tester.pump();

        await _toggleAddSources(tester);

        expect(find.text('点歌队列'), findsNothing);
        expect(find.text('点播歌曲'), findsNothing);
        expect(
          find.byKey(const ValueKey<String>('music-box-queue-context-action')),
          findsNothing,
        );
        expect(find.text('搜索添加'), findsOneWidget);
        expect(find.text('房间歌单'), findsOneWidget);
        expect(find.text('我的歌单'), findsOneWidget);
        expect(find.byType(Input), findsOneWidget);

        await _toggleAddSources(tester);

        expect(find.text('点歌队列'), findsOneWidget);
        expect(find.text('点播歌曲'), findsOneWidget);
        expect(
          find.byKey(const ValueKey<String>('music-box-queue-context-action')),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('priority play uses the shared floating success notice', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    const item = MusicBoxQueueItem(
      id: 'priority-track',
      source: 'netease',
      trackId: 'track-priority',
      title: '优先歌曲',
      artist: '歌手',
      durationMs: 180000,
      status: MusicBoxQueueItemStatus.ready,
      fileSizeBytes: 1024,
      error: '',
      addedByUserId: 'requester',
      createdAt: null,
      canPlayNow: true,
    );
    final state = _state(
      playbackState: MusicBoxPlaybackState.playing,
      positionMs: 0,
      currentItemId: 'another-track',
      queue: const [item],
    );

    await tester.pumpWidget(
      _host(
        state,
        searchController,
        height: 500,
        musicBoxController: MusicBoxController(api: _MusicBoxApiFake(state)),
        roomId: 'room-1',
      ),
    );

    await tester.tap(find.text('优先歌曲'));
    await tester.pump();
    await tester.tap(find.text('优先播放'));
    await tester.pump();

    expect(find.text('已优先播放'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('saved source shows its queue and a route back to 点歌队列', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    const requestedItem = MusicBoxQueueItem(
      id: 'request',
      source: 'netease',
      trackId: 'request-track',
      title: '待点歌曲',
      artist: '',
      durationMs: 0,
      status: MusicBoxQueueItemStatus.pending,
      fileSizeBytes: 0,
      error: '',
      addedByUserId: 'requester',
      createdAt: null,
    );

    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
      temporaryQueue: const [requestedItem],
      activeSource: const MusicBoxActiveSource(
        type: MusicBoxActiveSourceType.roomPlaylist,
        id: 'room-list',
        name: '房间收藏',
      ),
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: PersonalMusicPlaylist(
        id: 'room-list',
        name: '房间收藏',
        description: '',
        revision: 2,
        itemCount: 1,
        createdAt: DateTime(2026, 7, 28, 9, 15),
        updatedAt: null,
      ),
      items: const [],
    );

    await tester.pumpWidget(
      _host(
        state,
        controller,
        height: 500,
        musicBoxController: MusicBoxController(api: api),
        roomId: 'room-1',
        currentUser: _playlistCurrentUser,
        room: _playlistRoom,
      ),
    );

    expect(find.textContaining('当前：'), findsNothing);
    expect(find.text('房间收藏'), findsOneWidget);
    expect(find.text('Song'), findsWidgets);
    expect(find.text('待点歌曲'), findsNothing);
    expect(find.textContaining('切回点歌队列'), findsNothing);
    final currentHeader = find.byKey(
      const ValueKey<String>('music-box-current-queue-header'),
    );
    await tester.tap(currentHeader);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey<String>('music-playlist-card:room-list')),
      findsOneWidget,
    );
    expect(find.text('正在播放'), findsOneWidget);
    expect(find.text('2026-07-28 09:15'), findsOneWidget);
    expect(find.text('音乐房间'), findsOneWidget);
    final currentPlaylistAction = tester.widget<Button>(
      find.byKey(const ValueKey<String>('music-playlist-card-play-all')),
    );
    expect(currentPlaylistAction.onPressed, isNull);
    expect(api.activatedSourceType, isNull);
    await tester.tap(currentHeader);
    await tester.pump();
    final queueAction = tester.widget<ButtonIcon>(
      find.byKey(const ValueKey<String>('music-box-queue-context-action')),
    );
    expect(queueAction.tooltip, '切回点歌队列');

    await tester.tap(
      find.byKey(const ValueKey<String>('music-box-queue-context-action')),
    );
    await tester.pump();
    expect(api.activatedSourceType, MusicBoxActiveSourceType.temporary);

    await tester.tap(
      find.descendant(
        of: find.byKey(const ValueKey<String>('music-box-queue-list')),
        matching: find.text('Song'),
      ),
    );
    await tester.pump();
    expect(find.text('歌单'), findsOneWidget);
    final playlistAttribution = find.byKey(
      const ValueKey<String>('music-box-song-playlist-attribution'),
    );
    expect(
      find.descendant(of: playlistAttribution, matching: find.text('房间收藏')),
      findsOneWidget,
    );
    expect(find.text('房间歌单 · 房间收藏'), findsNothing);
    expect(find.text('点歌人'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} views and clones another user playlist snapshot',
      (tester) async {
        final searchController = TextEditingController();
        addTearDown(searchController.dispose);
        const tracks = [
          MusicBoxQueueItem(
            id: 'snapshot-track-1',
            source: 'netease',
            trackId: 'track-1',
            title: '第一首完整歌曲',
            artist: '歌手甲',
            durationMs: 180000,
            status: MusicBoxQueueItemStatus.ready,
            fileSizeBytes: 100,
            error: '',
            addedByUserId: 'other-user',
            createdAt: null,
          ),
          MusicBoxQueueItem(
            id: 'snapshot-track-2',
            source: 'bilibili',
            trackId: 'BV1snapshot',
            title: '第二首完整歌曲',
            artist: '歌手乙',
            durationMs: 210000,
            status: MusicBoxQueueItemStatus.ready,
            fileSizeBytes: 100,
            error: '',
            addedByUserId: 'other-user',
            createdAt: null,
          ),
        ];
        final state = _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 1000,
          queue: tracks,
          currentItemId: 'snapshot-track-1',
          activeSource: MusicBoxActiveSource(
            type: MusicBoxActiveSourceType.userPlaylist,
            id: 'other-playlist',
            name: '夜晚精选',
            ownerUserId: 'other-user',
            snapshotId: 'snapshot-1',
            createdAt: DateTime(2026, 8, 5, 14, 30),
            owner: MusicBoxRequester(
              userId: 'other-user',
              username: 'friend',
              displayName: '朋友',
              avatarUrl: null,
              defaultAvatarKey: 'blue-2',
            ),
          ),
        );
        final api = _CloneablePlaylistApiFake(state);

        await tester.pumpWidget(
          _host(
            state,
            searchController,
            platform: platform,
            height: 620,
            musicBoxController: MusicBoxController(api: api),
            roomId: 'room-1',
            currentUser: _playlistCurrentUser,
            room: _playlistRoom,
            previewPlatformFactory: _MusicBoxPreviewFactory(),
          ),
        );

        final header = find.byKey(
          const ValueKey<String>('music-box-current-queue-header'),
        );
        await tester.tap(header);
        await tester.pumpAndSettle();
        expect(find.text('2026-08-05 14:30'), findsOneWidget);
        await tester.tap(
          find.byKey(const ValueKey<String>('music-playlist-card-view')),
        );
        await tester.pumpAndSettle();

        expect(
          find.byKey(const ValueKey<String>('active-music-playlist-dialog')),
          findsOneWidget,
        );
        final dialog = find.byKey(
          const ValueKey<String>('active-music-playlist-dialog'),
        );
        expect(
          find.descendant(of: dialog, matching: find.text('第一首完整歌曲')),
          findsOneWidget,
        );
        expect(
          find.descendant(of: dialog, matching: find.text('第二首完整歌曲')),
          findsOneWidget,
        );
        expect(
          find.descendant(
            of: dialog,
            matching: find.byType(MusicPlaylistTrackSurface),
          ),
          findsNWidgets(2),
        );

        await tester.tap(
          find.byKey(
            const ValueKey<String>(
              'active-music-playlist-track:snapshot-track-1',
            ),
          ),
        );
        await tester.pump();
        expect(
          find.byKey(
            const ValueKey<String>('music-track-card:snapshot-track-1'),
          ),
          findsOneWidget,
        );
        await tester.tap(
          find.byKey(
            const ValueKey<String>(
              'active-music-playlist-track:snapshot-track-1',
            ),
          ),
        );
        await tester.pump();

        await tester.tap(
          find.byKey(const ValueKey<String>('active-music-playlist-clone')),
        );
        await tester.pumpAndSettle();
        expect(api.clonedPlaylistId, 'other-playlist');
        expect(api.clonedSnapshotId, 'snapshot-1');
        expect(find.text('已克隆到我的歌单'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} clears the request queue only after confirmation',
      (tester) async {
        final searchController = TextEditingController();
        addTearDown(searchController.dispose);
        const item = MusicBoxQueueItem(
          id: 'request-to-clear',
          source: 'netease',
          trackId: 'track-to-clear',
          title: '待清空歌曲',
          artist: '歌手',
          durationMs: 180000,
          status: MusicBoxQueueItemStatus.ready,
          fileSizeBytes: 1024,
          error: '',
          addedByUserId: 'requester',
          createdAt: null,
        );
        final state = _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          queue: const [item],
          temporaryQueue: const [item],
          revision: 17,
          hasRevision: true,
        );
        final api = _MusicBoxApiFake(state);

        await tester.pumpWidget(
          _host(
            state,
            searchController,
            platform: platform,
            height: 500,
            musicBoxController: MusicBoxController(api: api),
            roomId: 'room-1',
          ),
        );

        await tester.tap(
          find.byKey(const ValueKey<String>('music-box-queue-context-action')),
        );
        await tester.pumpAndSettle();
        expect(find.text('清空点歌队列'), findsOneWidget);
        expect(api.action, isNull);

        await tester.tap(find.text('确认清空'));
        await tester.pumpAndSettle();
        expect(api.action, 'clear_temporary_playlist');
        expect(api.itemId, isNull);
        expect(api.commandId, isNotEmpty);
        expect(api.expectedRevision, 17);
        expect(find.text('已清空点歌队列'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('empty request queue disables its clear button', (tester) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
      queue: const [],
      temporaryQueue: const [],
    );

    await tester.pumpWidget(
      _host(
        state,
        searchController,
        height: 500,
        musicBoxController: MusicBoxController(api: _MusicBoxApiFake(state)),
        roomId: 'room-1',
      ),
    );

    final button = tester.widget<ButtonIcon>(
      find.byKey(const ValueKey<String>('music-box-queue-context-action')),
    );
    expect(button.tooltip, '清空点歌队列');
    expect(button.onPressed, isNull);
  });

  testWidgets('downloading queue item replaces its leading music icon', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    const item = MusicBoxQueueItem(
      id: 'downloading-track',
      source: 'netease',
      trackId: 'track-downloading',
      title: '正在下载的歌曲',
      artist: '歌手',
      durationMs: 180000,
      status: MusicBoxQueueItemStatus.downloading,
      fileSizeBytes: 0,
      error: '',
      addedByUserId: 'requester',
      createdAt: null,
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          queue: const [item],
          temporaryQueue: const [item],
        ),
        searchController,
        height: 500,
      ),
    );

    final loadingFinder = find.byKey(
      const ValueKey<String>(
        'music-box-queue-leading-loading:downloading-track',
      ),
    );
    expect(loadingFinder, findsOneWidget);
    expect(
      find.byKey(
        const ValueKey<String>(
          'music-box-queue-leading-icon:downloading-track',
        ),
      ),
      findsNothing,
    );
    expect(
      tester.getCenter(loadingFinder).dx,
      lessThan(tester.getTopLeft(find.text('正在下载的歌曲')).dx),
    );
    expect(tester.takeException(), isNull);
  });

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} search result uses a song card and an explicit queue action',
      (tester) async {
        final controller = TextEditingController(text: '绝不认输');
        final queued = <MusicBoxSearchResult>[];
        addTearDown(controller.dispose);
        const result = MusicBoxSearchResult(
          trackId: 'BV1xx411c7mD',
          name: '《Hi-Res无损音质》｜《绝不认输》完整歌曲标题',
          artists: ['VV音乐局'],
          source: 'bilibili',
        );

        await tester.pumpWidget(
          _host(
            _state(playbackState: MusicBoxPlaybackState.stopped, positionMs: 0),
            controller,
            platform: platform,
            height: 500,
            searchResults: const [result],
            onQueueResult: queued.add,
          ),
        );
        await _toggleAddSources(tester);
        expect(tester.takeException(), isNull);

        final tile = find.byKey(
          const ValueKey<String>('music-box-search-tile:bilibili:BV1xx411c7mD'),
        );
        final addButton = find.byKey(
          const ValueKey<String>('music-box-search-add:bilibili:BV1xx411c7mD'),
        );
        expect(tile, findsOneWidget);
        expect(
          find.descendant(of: tile, matching: find.byIcon(Icons.music_note)),
          findsOneWidget,
        );
        expect(
          find.descendant(of: tile, matching: find.text('哔哩哔哩')),
          findsNothing,
        );
        expect(addButton, findsOneWidget);
        expect(tester.widget<ButtonIcon>(addButton).tooltip, '加入点歌队列');

        await tester.tap(addButton);
        await tester.pump();
        expect(queued, const [result]);
        expect(tester.takeException(), isNull);

        await tester.tap(tile);
        await tester.pump();
        expect(tester.takeException(), isNull);

        expect(
          find.byKey(
            const ValueKey<String>(
              'music-box-song-card:search:bilibili:BV1xx411c7mD',
            ),
          ),
          findsOneWidget,
        );
        expect(find.text('点歌人'), findsNothing);
        expect(find.text('歌单'), findsNothing);
        expect(find.text('点歌队列'), findsOneWidget);
        expect(find.text('添加到歌单'), findsOneWidget);
        expect(find.text('作者'), findsOneWidget);
        expect(find.text('详情'), findsOneWidget);
        expect(find.text('BV1xx411c7mD'), findsOneWidget);

        await tester.tap(find.text('点歌队列'));
        await tester.pump();
        expect(queued, const [result, result]);
        expect(tester.takeException(), isNull);
      },
    );
  }

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} queued catalog track keeps plus action informative and disables the card action',
      (tester) async {
        final controller = TextEditingController(text: '已点歌曲');
        final queued = <MusicBoxSearchResult>[];
        addTearDown(controller.dispose);
        const result = MusicBoxSearchResult(
          trackId: 'already-queued-track',
          name: '已点歌曲',
          artists: ['歌手'],
          source: 'netease',
        );
        const queueItem = MusicBoxQueueItem(
          id: 'queued-item',
          source: 'netease',
          trackId: 'already-queued-track',
          title: '已点歌曲',
          artist: '歌手',
          durationMs: 120000,
          status: MusicBoxQueueItemStatus.ready,
          fileSizeBytes: 0,
          error: '',
          addedByUserId: 'user',
          createdAt: null,
        );

        await tester.pumpWidget(
          _host(
            _state(
              playbackState: MusicBoxPlaybackState.stopped,
              positionMs: 0,
              temporaryQueue: const [queueItem],
            ),
            controller,
            platform: platform,
            height: 500,
            searchResults: const [result],
            onQueueResult: queued.add,
          ),
        );
        await _toggleAddSources(tester);

        final tile = find.byKey(
          const ValueKey<String>(
            'music-box-search-tile:netease:already-queued-track',
          ),
        );
        final addButton = find.byKey(
          const ValueKey<String>(
            'music-box-search-add:netease:already-queued-track',
          ),
        );
        expect(tester.widget<ButtonIcon>(addButton).tooltip, '加入点歌队列');

        await tester.tap(addButton);
        await tester.pump();
        expect(queued, isEmpty);
        expect(find.text('已在队列中'), findsOneWidget);

        await tester.tap(tile);
        await tester.pump();
        final card = find.byKey(
          const ValueKey<String>(
            'music-box-song-card:search:netease:already-queued-track',
          ),
        );
        expect(card, findsOneWidget);
        expect(
          find.descendant(of: card, matching: find.text('已在队列中')),
          findsOneWidget,
        );
        final queuedButton = find.descendant(
          of: card,
          matching: find.widgetWithText(Button, '已在队列中'),
        );
        expect(tester.widget<Button>(queuedButton).onPressed, isNull);
        expect(tester.takeException(), isNull);
      },
    );
  }

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} playlist song reuses the catalog row and song card',
      (tester) async {
        final searchController = TextEditingController();
        final queued = <MusicBoxSearchResult>[];
        addTearDown(searchController.dispose);
        final playlist = PersonalMusicPlaylist(
          id: 'playlist-1',
          name: '666',
          description: '',
          revision: 1,
          itemCount: 1,
          createdAt: DateTime(2026, 7, 30, 20, 34),
          updatedAt: null,
        );
        const playlistItem = PersonalMusicPlaylistItem(
          id: 'playlist-item-1',
          playlistId: 'playlist-1',
          trackId: 'BV1ab411c7mD',
          source: 'bilibili',
          title: '你的微笑',
          artists: ['F.I.R.'],
          durationMs: 267000,
          sortOrder: 0,
          createdAt: null,
        );
        final state = _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
        );
        final api = _RoomPlaylistApiFake(
          state,
          playlist: playlist,
          items: const [playlistItem],
        );

        await tester.pumpWidget(
          _host(
            state,
            searchController,
            platform: platform,
            height: 500,
            musicBoxController: MusicBoxController(api: api),
            roomId: 'room-1',
            currentUser: _playlistCurrentUser,
            room: _playlistRoom,
            onQueueResult: queued.add,
          ),
        );
        await _toggleAddSources(tester);
        await tester.tap(find.text('房间歌单'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('666'));
        await tester.pump();
        final playlistCard = find.byKey(
          const ValueKey<String>('music-playlist-card:playlist-1'),
        );
        expect(playlistCard, findsOneWidget);
        expect(find.text('歌曲数量'), findsOneWidget);
        expect(
          find.descendant(of: playlistCard, matching: find.text('1 首')),
          findsOneWidget,
        );
        expect(find.text('房间'), findsOneWidget);
        expect(find.text('音乐房间'), findsOneWidget);
        expect(find.text('创建日期'), findsOneWidget);
        expect(find.text('2026-07-30 20:34'), findsOneWidget);
        await tester.tap(
          find.byKey(const ValueKey<String>('music-playlist-card-view')),
        );
        await tester.pumpAndSettle();

        final playlistHeader = find.byKey(
          const ValueKey<String>('music-box-playlist-header:playlist-1'),
        );
        expect(playlistHeader, findsOneWidget);
        expect(
          find.descendant(
            of: playlistHeader,
            matching: find.byKey(
              const ValueKey<String>('music-box-playlist-header-icon'),
            ),
          ),
          findsOneWidget,
        );
        expect(
          find.descendant(of: playlistHeader, matching: find.text('666')),
          findsOneWidget,
        );
        await tester.tap(playlistHeader);
        await tester.pump();
        expect(
          find.byKey(const ValueKey<String>('music-playlist-card:playlist-1')),
          findsNothing,
        );

        final tile = find.byKey(
          const ValueKey<String>(
            'music-box-playlist-tile:bilibili:BV1ab411c7mD',
          ),
        );
        final addButton = find.byKey(
          const ValueKey<String>(
            'music-box-playlist-add:bilibili:BV1ab411c7mD',
          ),
        );
        expect(tile, findsOneWidget);
        expect(
          find.descendant(of: tile, matching: find.text('你的微笑')),
          findsOneWidget,
        );
        expect(
          find.descendant(of: tile, matching: find.text('F.I.R.')),
          findsOneWidget,
        );
        expect(
          find.descendant(of: tile, matching: find.text('哔哩哔哩')),
          findsNothing,
        );
        expect(addButton, findsOneWidget);
        expect(tester.widget<ButtonIcon>(addButton).tooltip, '加入点歌队列');

        await tester.tap(addButton);
        await tester.pump();
        expect(queued, hasLength(1));
        expect(queued.single.trackId, 'BV1ab411c7mD');

        await tester.tap(tile);
        await tester.pump();
        expect(
          find.byKey(
            const ValueKey<String>(
              'music-box-song-card:search:bilibili:BV1ab411c7mD',
            ),
          ),
          findsOneWidget,
        );
        expect(find.text('点歌人'), findsNothing);
        expect(find.text('歌单'), findsNothing);
        expect(find.text('4:27'), findsOneWidget);
        expect(find.text('详情'), findsOneWidget);
        expect(find.text('BV1ab411c7mD'), findsOneWidget);
        expect(find.text('点歌队列'), findsOneWidget);
        expect(find.text('添加到歌单'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('Bilibili queue card shows its BV details after attribution', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    const item = MusicBoxQueueItem(
      id: 'bilibili-request',
      source: 'bilibili',
      trackId: 'BV17x411w7KC',
      title: '点歌的哔哩哔哩歌曲',
      artist: '歌手',
      durationMs: 123000,
      status: MusicBoxQueueItemStatus.ready,
      fileSizeBytes: 1024,
      error: '',
      addedByUserId: 'requester',
      createdAt: null,
      requestedBy: MusicBoxRequester(
        userId: 'requester',
        displayName: '点歌用户',
        avatarLabel: '点歌用户',
        avatarUrl: null,
        defaultAvatarKey: 'blue-3',
      ),
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          queue: const [item],
          temporaryQueue: const [item],
        ),
        searchController,
        height: 500,
      ),
    );
    await tester.tap(find.text('点歌的哔哩哔哩歌曲'));
    await tester.pump();

    final attribution = find.text('点歌人');
    final details = find.text('详情');
    expect(attribution, findsOneWidget);
    expect(details, findsOneWidget);
    expect(find.text('BV17x411w7KC'), findsOneWidget);
    expect(
      tester.getTopLeft(details).dy,
      greaterThan(tester.getTopLeft(attribution).dy),
    );
    expect(tester.takeException(), isNull);
  });

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets('${platform.name} song card metadata supports text selection', (
      tester,
    ) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);

      await tester.pumpWidget(
        _host(
          _state(playbackState: MusicBoxPlaybackState.stopped, positionMs: 0),
          searchController,
          platform: platform,
          height: 500,
        ),
      );
      await tester.tap(
        find.descendant(
          of: find.byKey(const ValueKey<String>('music-box-queue-list')),
          matching: find.text('Song'),
        ),
      );
      await tester.pump();

      final titleField = find.byWidgetPredicate(
        (widget) => widget is EditableText && widget.controller.text == 'Song',
      );
      expect(titleField, findsOneWidget);
      expect(find.byType(HoverCardSelectableText), findsWidgets);

      if (platform == TargetPlatform.android) {
        final editableTextState = tester.state<EditableTextState>(titleField);
        editableTextState.userUpdateTextEditingValue(
          editableTextState.textEditingValue.copyWith(
            selection: const TextSelection(baseOffset: 0, extentOffset: 4),
          ),
          SelectionChangedCause.toolbar,
        );
        await tester.pump();
        expect(editableTextState.showToolbar(), isTrue);
      } else {
        await tester.tap(titleField, buttons: kSecondaryMouseButton);
      }
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(
        find.byKey(const ValueKey('text-context-menu-panel')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey<String>('music-box-song-card:a')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });
  }

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} playlist picker shows complete adaptive names and scopes',
      (tester) async {
        final searchController = TextEditingController();
        addTearDown(searchController.dispose);
        const roomName = '一个需要自适应换行完整显示的非常长的房间歌单名称';
        const personalName = '个人歌单';
        final roomPlaylist = PersonalMusicPlaylist(
          id: 'room-playlist-long',
          name: roomName,
          description: '',
          revision: 1,
          itemCount: 0,
          createdAt: DateTime(2026, 7, 30, 20, 34),
          updatedAt: null,
        );
        final personalPlaylist = PersonalMusicPlaylist(
          id: 'personal-playlist-long',
          name: personalName,
          description: '',
          revision: 1,
          itemCount: 0,
          createdAt: DateTime(2026, 7, 29, 16, 0),
          updatedAt: null,
        );
        final state = _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
        );
        final api = _RoomPlaylistApiFake(
          state,
          playlist: roomPlaylist,
          items: const [],
          personalPlaylists: [personalPlaylist],
        );

        await tester.pumpWidget(
          _host(
            state,
            searchController,
            platform: platform,
            height: 500,
            musicBoxController: MusicBoxController(api: api),
            roomId: 'room-1',
            currentUser: _playlistCurrentUser,
            room: _playlistRoom,
          ),
        );
        await tester.tap(
          find.descendant(
            of: find.byKey(const ValueKey<String>('music-box-queue-list')),
            matching: find.text('Song'),
          ),
        );
        await tester.pump();
        await tester.tap(find.text('添加到歌单'));
        await tester.pumpAndSettle();

        final roomTarget = find.byKey(
          const ValueKey<String>(
            'music-track-playlist-target:room:room-1:room-playlist-long',
          ),
        );
        final personalTarget = find.byKey(
          const ValueKey<String>(
            'music-track-playlist-target:personal-playlist-long',
          ),
        );
        expect(roomTarget, findsOneWidget);
        expect(personalTarget, findsOneWidget);
        expect(find.text(roomName), findsOneWidget);
        expect(find.text(personalName), findsOneWidget);
        expect(find.text('0 首歌曲'), findsNWidgets(2));
        expect(find.textContaining('房间 ·'), findsNothing);
        expect(find.textContaining('我的 ·'), findsNothing);
        expect(tester.getSize(roomTarget).height, greaterThan(64));
        expect(tester.getSize(personalTarget).height, greaterThanOrEqualTo(64));
        expect(find.byIcon(Icons.person_outline), findsNothing);
        expect(find.byIcon(Icons.meeting_room_outlined), findsNothing);
        expect(
          find.descendant(
            of: roomTarget,
            matching: find.byIcon(Icons.meeting_room),
          ),
          findsOneWidget,
        );
        expect(
          find.descendant(
            of: personalTarget,
            matching: find.byIcon(Icons.person),
          ),
          findsOneWidget,
        );
        for (final name in [roomName, personalName]) {
          final text = tester.widget<Text>(find.text(name));
          expect(text.maxLines, isNull);
          expect(text.overflow, isNull);
        }
        final confirmButton = find.ancestor(
          of: find.text('确认添加'),
          matching: find.byType(Button),
        );
        expect(tester.widget<Button>(confirmButton).onPressed, isNull);
        await tester.tapAt(tester.getTopLeft(roomTarget) + const Offset(3, 3));
        await tester.pump();
        expect(tester.widget<Button>(confirmButton).onPressed, isNotNull);
        expect(
          find.descendant(
            of: roomTarget,
            matching: find.byIcon(Icons.radio_button_checked),
          ),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} personal playlist card nests its creator profile',
      (tester) async {
        final searchController = TextEditingController();
        addTearDown(searchController.dispose);
        final playlist = PersonalMusicPlaylist(
          id: 'personal-card',
          name: '完整个人歌单',
          description: '',
          revision: 1,
          itemCount: 27,
          createdAt: DateTime(2026, 7, 29, 16),
          updatedAt: null,
        );
        final state = _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
        );
        final api = _RoomPlaylistApiFake(
          state,
          playlist: playlist,
          items: const [],
          personalPlaylists: [playlist],
        );

        await tester.pumpWidget(
          _host(
            state,
            searchController,
            platform: platform,
            height: 500,
            musicBoxController: MusicBoxController(api: api),
            roomId: 'room-1',
            currentUser: _playlistCurrentUser,
            room: _playlistRoom,
            onResolveUserProfile: (user) async =>
                user.copyWith(roomDisplayName: '房间专用名'),
          ),
        );
        await _toggleAddSources(tester);
        await tester.tap(find.text('我的歌单'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('完整个人歌单'));
        await tester.pumpAndSettle();

        final playlistCard = find.byKey(
          const ValueKey<String>('music-playlist-card:personal-card'),
        );
        expect(playlistCard, findsOneWidget);
        expect(
          find.descendant(of: playlistCard, matching: find.text('创建人')),
          findsOneWidget,
        );
        expect(
          find.descendant(of: playlistCard, matching: find.text('27 首')),
          findsOneWidget,
        );
        expect(
          find.descendant(
            of: playlistCard,
            matching: find.text('2026-07-29 16:00'),
          ),
          findsOneWidget,
        );
        expect(find.text('正在播放'), findsNothing);
        expect(find.text('播放全部'), findsOneWidget);
        expect(
          tester
              .widget<Button>(
                find.byKey(
                  const ValueKey<String>('music-playlist-card-play-all'),
                ),
              )
              .onPressed,
          isNotNull,
        );

        final creatorName = tester.widget<Text>(
          find.descendant(of: playlistCard, matching: find.text('房间专用名')),
        );
        expect(creatorName.maxLines, 1);
        expect(creatorName.softWrap, isFalse);
        expect(creatorName.textAlign, TextAlign.right);
        expect(creatorName.style?.fontSize, UiTypography.label.fontSize);
        expect(
          tester
              .widget<Text>(
                find.descendant(of: playlistCard, matching: find.text('27 首')),
              )
              .textAlign,
          TextAlign.right,
        );
        expect(
          tester
              .widget<Text>(
                find.descendant(of: playlistCard, matching: find.text('27 首')),
              )
              .style
              ?.fontSize,
          UiTypography.label.fontSize,
        );
        expect(
          tester
              .widget<HoverCardSelectableText>(
                find.descendant(
                  of: playlistCard,
                  matching: find.byWidgetPredicate(
                    (widget) =>
                        widget is HoverCardSelectableText &&
                        widget.value == '2026-07-29 16:00',
                  ),
                ),
              )
              .textAlign,
          TextAlign.right,
        );
        final songCountRight = tester
            .getRect(
              find.descendant(of: playlistCard, matching: find.text('27 首')),
            )
            .right;
        final creatorRight = tester.getRect(find.text('房间专用名')).right;
        final createdAtRight = tester
            .getRect(
              find.descendant(
                of: playlistCard,
                matching: find.byWidgetPredicate(
                  (widget) =>
                      widget is HoverCardSelectableText &&
                      widget.value == '2026-07-29 16:00',
                ),
              ),
            )
            .right;
        expect(creatorRight, closeTo(songCountRight, 0.5));
        expect(createdAtRight, closeTo(songCountRight, 0.5));

        await tester.tap(
          find.descendant(of: playlistCard, matching: find.text('房间专用名')),
        );
        await tester.pump();
        expect(find.text('@playlist_owner'), findsOneWidget);
        expect(playlistCard, findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('long queue rows grow and keep every title and artist line', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    const longTitle =
        '[Hi-Res lossless] | A very long song title that must remain complete '
        'across as many lines as the narrow queue needs';
    const item = MusicBoxQueueItem(
      id: 'long-queue-item',
      source: 'netease',
      trackId: 'long-queue-track',
      title: longTitle,
      artist: 'An equally long artist name that must wrap without truncation',
      durationMs: 240000,
      status: MusicBoxQueueItemStatus.ready,
      fileSizeBytes: 0,
      error: '',
      addedByUserId: 'requester',
      createdAt: null,
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          queue: const [item],
          temporaryQueue: const [item],
        ),
        controller,
        height: 500,
      ),
    );

    final tile = find.byKey(
      const ValueKey<String>('music-box-queue-tile:long-queue-item'),
    );
    final title = tester.widget<Text>(
      find.descendant(of: tile, matching: find.text(longTitle)),
    );
    expect(tester.getSize(tile).height, greaterThan(82));
    expect(title.maxLines, isNull);
    expect(title.overflow, isNull);
    expect(title.style?.fontSize, lessThan(13));
    expect(tester.takeException(), isNull);
  });

  testWidgets('playlist summaries grow to show their complete names', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    const longPlaylistName =
        'A complete playlist name that needs several lines in the narrow music box';
    const playlist = PersonalMusicPlaylist(
      id: 'long-playlist-summary',
      name: longPlaylistName,
      description: '',
      revision: 1,
      itemCount: 27,
      createdAt: null,
      updatedAt: null,
    );
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: playlist,
      items: const [],
    );

    await tester.pumpWidget(
      _host(
        state,
        controller,
        height: 500,
        musicBoxController: MusicBoxController(api: api),
        roomId: 'room-1',
      ),
    );
    await _toggleAddSources(tester);
    await tester.tap(find.text('房间歌单'));
    await tester.pumpAndSettle();

    final tile = find.byKey(
      const ValueKey<String>(
        'music-box-playlist-summary:long-playlist-summary',
      ),
    );
    final name = tester.widget<Text>(
      find.descendant(of: tile, matching: find.text(longPlaylistName)),
    );
    expect(tester.getSize(tile).height, greaterThan(50));
    expect(name.maxLines, isNull);
    expect(name.overflow, isNull);
    expect(
      find.descendant(of: tile, matching: find.text('27 首')),
      findsNothing,
    );
    final openButton = find.byKey(
      const ValueKey<String>('music-box-playlist-open:long-playlist-summary'),
    );
    expect(openButton, findsOneWidget);
    expect(tester.getSize(openButton).width, 34);
    final tileSurface = tester.widget<PressableSurface>(tile);
    expect(tester.getSize(openButton).height, closeTo(tileSurface.height, 0.5));
    expect(tester.takeException(), isNull);
  });

  testWidgets('song requester avatar stays next to the right-aligned name', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    const item = MusicBoxQueueItem(
      id: 'requester-position',
      source: 'netease',
      trackId: 'requester-position-track',
      title: 'Song',
      artist: 'Artist',
      durationMs: 180000,
      status: MusicBoxQueueItemStatus.ready,
      fileSizeBytes: 0,
      error: '',
      addedByUserId: 'requester',
      createdAt: null,
      requestedBy: MusicBoxRequester(
        userId: 'requester',
        displayName: 'testxxxx',
        avatarLabel: 'testxxxx',
        avatarUrl: null,
        defaultAvatarKey: 'blue-3',
      ),
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          queue: const [item],
          temporaryQueue: const [item],
        ),
        controller,
        height: 500,
        onResolveUserProfile: (_) async => const UserSummary(
          id: 'requester',
          username: 'requester_handle',
          displayName: 'testxxxx',
          avatarUrl: null,
          defaultAvatarKey: 'blue-3',
          uid: '10001',
          roomRole: 'admin',
        ),
        userProfileActionBuilder: (user) => UserProfileAction(
          label: '管理成员',
          icon: Icons.manage_accounts_outlined,
          onPressed: () {},
        ),
      ),
    );
    await tester.tap(
      find.descendant(
        of: find.byKey(
          const ValueKey<String>('music-box-queue-tile:requester-position'),
        ),
        matching: find.text('Song'),
      ),
    );
    await tester.pump();

    final group = find.byKey(
      const ValueKey<String>('music-box-song-attribution-value-group'),
    );
    final avatar = find.byKey(
      const ValueKey<String>('music-box-song-attribution-avatar'),
    );
    final name = find.descendant(
      of: group,
      matching: find.byKey(
        const ValueKey<String>('music-box-song-attribution-name'),
      ),
    );
    final avatarRect = tester.getRect(avatar);
    final nameRect = tester.getRect(name);
    final groupRect = tester.getRect(group);
    expect(nameRect.left, greaterThanOrEqualTo(avatarRect.right));
    expect(nameRect.left - avatarRect.right, lessThanOrEqualTo(8));
    expect(nameRect.right, closeTo(groupRect.right, 0.1));
    expect(nameRect.height, lessThan(20));
    expect(
      find.descendant(of: name, matching: find.byType(EditableText)),
      findsNothing,
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(avatar));
    await tester.pumpAndSettle();

    expect(find.text('@requester_handle'), findsOneWidget);
    expect(find.text('管理员'), findsOneWidget);
    expect(find.text('管理成员'), findsOneWidget);
    expect(
      find.byKey(
        const ValueKey<String>('music-box-song-card:requester-position'),
      ),
      findsOneWidget,
    );
    expect(group, findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'playlist attribution shows a playlist icon and opens its playlist card',
    (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      const item = MusicBoxQueueItem(
        id: 'playlist-owner-width',
        source: 'netease',
        trackId: 'playlist-owner-width-track',
        title: 'Song',
        artist: 'Artist',
        durationMs: 180000,
        status: MusicBoxQueueItemStatus.ready,
        fileSizeBytes: 0,
        error: '',
        addedByUserId: 'playlist-owner',
        createdAt: null,
      );

      await tester.pumpWidget(
        _host(
          _state(
            playbackState: MusicBoxPlaybackState.stopped,
            positionMs: 0,
            queue: const [item],
            activeSource: const MusicBoxActiveSource(
              type: MusicBoxActiveSourceType.userPlaylist,
              id: 'playlist-123',
              name: '123',
              ownerUserId: 'playlist-owner',
              owner: MusicBoxRequester(
                userId: 'playlist-owner',
                displayName: 'testxxx',
                avatarLabel: 'TE',
                avatarUrl: null,
                defaultAvatarKey: 'blue-3',
              ),
            ),
          ),
          controller,
          height: 500,
        ),
      );
      await tester.tap(find.text('Song'));
      await tester.pump();

      final attribution = find.byKey(
        const ValueKey<String>('music-box-song-playlist-attribution'),
      );
      expect(attribution, findsOneWidget);
      expect(
        find.descendant(
          of: attribution,
          matching: find.byKey(
            const ValueKey<String>('music-box-song-playlist-icon'),
          ),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(of: attribution, matching: find.text('123')),
        findsOneWidget,
      );
      final attributionIcon = find.descendant(
        of: attribution,
        matching: find.byKey(
          const ValueKey<String>('music-box-song-playlist-icon'),
        ),
      );
      expect(
        tester.getCenter(attributionIcon).dy,
        closeTo(tester.getCenter(attribution).dy, 0.1),
      );
      expect(find.text('testxxx的歌单'), findsNothing);
      expect(find.text(' · 123'), findsNothing);

      final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await gesture.addPointer(location: Offset.zero);
      addTearDown(gesture.removePointer);
      await gesture.moveTo(tester.getCenter(attribution));
      await tester.pumpAndSettle();
      final playlistCard = find.byKey(
        const ValueKey<String>('music-playlist-card:playlist-123'),
      );
      expect(playlistCard, findsOneWidget);
      expect(
        find.descendant(of: playlistCard, matching: find.text('testxxx')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('long music titles wrap fully and shrink without ellipses', (
    tester,
  ) async {
    final controller = TextEditingController(text: '标题');
    addTearDown(controller.dispose);
    const longTitle =
        '这是一个用于验证歌曲名片完整显示能力的非常非常长的歌曲标题'
        '它需要自动缩小字体并显示为三行四行甚至更多行且不能出现省略号';
    const result = MusicBoxSearchResult(
      trackId: 'long-title',
      name: longTitle,
      artists: ['一位名字同样很长但必须完整显示的歌手'],
      source: 'netease',
    );

    await tester.pumpWidget(
      _host(
        _state(playbackState: MusicBoxPlaybackState.stopped, positionMs: 0),
        controller,
        platform: TargetPlatform.windows,
        height: 500,
        searchResults: const [result],
      ),
    );
    await _toggleAddSources(tester);
    expect(tester.takeException(), isNull);
    final tile = find.byKey(
      const ValueKey<String>('music-box-search-tile:netease:long-title'),
    );
    expect(tester.getSize(tile).height, greaterThan(50));

    await tester.tap(tile);
    await tester.pump();
    expect(tester.takeException(), isNull);

    final adaptiveTitle = find.byKey(
      const ValueKey<String>('music-box-song-card-title'),
    );
    final selectableTitle = find.descendant(
      of: adaptiveTitle,
      matching: find.byWidgetPredicate(
        (widget) =>
            widget is ReadOnlySelectableText && widget.value == longTitle,
      ),
    );
    final titleText = tester.widget<ReadOnlySelectableText>(selectableTitle);
    expect(titleText.value, longTitle);
    expect(titleText.maxLines, greaterThan(2));
    expect(titleText.style.fontSize, lessThan(16));
    expect(tester.getSize(adaptiveTitle).height, greaterThan(38));
    expect(find.textContaining('...'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('personal playlist song card shows its playlist identity', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          activeSource: const MusicBoxActiveSource(
            type: MusicBoxActiveSourceType.userPlaylist,
            id: 'personal-list',
            name: '通勤歌单',
            ownerUserId: 'owner',
            owner: MusicBoxRequester(
              userId: 'owner',
              displayName: '用户名称',
              avatarLabel: '全局名称',
              avatarUrl: '/avatar.png',
              defaultAvatarKey: 'green-2',
            ),
          ),
        ),
        controller,
        platform: TargetPlatform.android,
        height: 500,
      ),
    );

    await tester.tap(
      find.descendant(
        of: find.byKey(const ValueKey<String>('music-box-queue-list')),
        matching: find.text('Song'),
      ),
    );
    await tester.pump();

    expect(find.text('歌单'), findsOneWidget);
    expect(find.text('用户名称的歌单'), findsNothing);
    expect(find.text(' · 通勤歌单'), findsNothing);
    expect(find.text('点歌人'), findsNothing);
    final attribution = find.byKey(
      const ValueKey<String>('music-box-song-playlist-attribution'),
    );
    expect(
      find.descendant(of: attribution, matching: find.text('通勤歌单')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: attribution,
        matching: find.byKey(
          const ValueKey<String>('music-box-song-playlist-icon'),
        ),
      ),
      findsOneWidget,
    );

    await tester.tapAt(tester.getCenter(attribution));
    await tester.pump();
    final playlistCard = find.byKey(
      const ValueKey<String>('music-playlist-card:personal-list'),
    );
    expect(playlistCard, findsOneWidget);
    expect(
      find.descendant(of: playlistCard, matching: find.text('用户名称')),
      findsOneWidget,
    );
    final ownerAvatar = tester.widget<Avatar>(
      find.descendant(of: playlistCard, matching: find.byType(Avatar)),
    );
    expect(ownerAvatar.label, '全局名称');
    expect(ownerAvatar.imageUrl, endsWith('/avatar.png'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('music box volume uses a flat surface', (tester) async {
    final controller = TextEditingController();
    final volumeChanges = <double>[];
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _state(playbackState: MusicBoxPlaybackState.stopped, positionMs: 0),
        controller,
        onVolumeChanged: volumeChanges.add,
      ),
    );

    final volume = find.byKey(
      const ValueKey<String>('music-box-volume-control'),
    );
    expect(volume, findsOneWidget);
    expect(
      find.ancestor(of: volume, matching: find.byType(PressableSurface)),
      findsNothing,
    );
    expect(
      find.descendant(of: volume, matching: find.byType(UiSlider)),
      findsOneWidget,
    );
    expect(tester.widget<UiSlider>(find.byType(UiSlider)).hoverLabel, '100%');

    await tester.tap(
      find.descendant(of: volume, matching: find.byIcon(Icons.volume_up)),
    );
    await tester.pump();
    expect(volumeChanges, [0]);
    expect(tester.takeException(), isNull);
  });

  testWidgets('music box volume follows externally restored mute state', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );

    await tester.pumpWidget(_host(state, controller, volume: 1));
    expect(tester.widget<UiSlider>(find.byType(UiSlider)).value, 1);

    await tester.pumpWidget(_host(state, controller, volume: 0));
    await tester.pump();

    final slider = tester.widget<UiSlider>(find.byType(UiSlider));
    expect(slider.value, 0);
    expect(slider.hoverLabel, '0%');
    expect(find.byIcon(Icons.volume_off), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('height pressure shrinks only the search results viewport', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );

    await tester.pumpWidget(_host(state, controller, height: 400));
    await _toggleAddSources(tester);
    final searchField = find.byType(Input);
    final sourcePicker = find.byType(SegmentedControl<String>);
    final resultsViewport = find.byKey(
      const ValueKey<String>('music-box-results-viewport'),
    );
    final comfortableSearchSize = tester.getSize(searchField);
    final comfortableSourceSize = tester.getSize(sourcePicker);
    final comfortableResultsHeight = tester.getSize(resultsViewport).height;
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(_host(state, controller, height: 370));
    await _toggleAddSources(tester);

    expect(tester.getSize(searchField), comfortableSearchSize);
    expect(tester.getSize(sourcePicker), comfortableSourceSize);
    expect(
      tester.getSize(resultsViewport).height,
      lessThan(comfortableResultsHeight),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('empty music box keeps the whole panel static', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          queue: const [],
        ),
        controller,
        platform: TargetPlatform.android,
        height: 400,
      ),
    );
    expect(find.text('当前队列为空，点击 + 添加歌曲'), findsOneWidget);

    final verticalPanelScrollViews = tester
        .widgetList<SingleChildScrollView>(
          find.descendant(
            of: find.byType(LiveMusicBoxPanel),
            matching: find.byType(SingleChildScrollView),
          ),
        )
        .where((view) => view.scrollDirection == Axis.vertical);
    expect(verticalPanelScrollViews, isEmpty);
    expect(
      find.byKey(const ValueKey<String>('music-box-search-results-list')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('only overflowing search results scroll vertically', (
    tester,
  ) async {
    final controller = TextEditingController(text: 'song');
    addTearDown(controller.dispose);
    final results = List<MusicBoxSearchResult>.generate(
      12,
      (index) => MusicBoxSearchResult(
        trackId: 'track-$index',
        name: 'Song $index',
        artists: const ['Artist'],
        source: 'netease',
      ),
    );

    await tester.pumpWidget(
      _host(
        _state(playbackState: MusicBoxPlaybackState.stopped, positionMs: 0),
        controller,
        platform: TargetPlatform.android,
        height: 400,
        searchResults: results,
      ),
    );
    await _toggleAddSources(tester);

    final resultsList = find.byKey(
      const ValueKey<String>('music-box-search-results-list'),
    );
    expect(resultsList, findsOneWidget);
    final scrollable = find.descendant(
      of: resultsList,
      matching: find.byType(Scrollable),
    );
    expect(scrollable, findsOneWidget);
    expect(
      tester.state<ScrollableState>(scrollable).position.maxScrollExtent,
      greaterThan(0),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Android keyboard overlays the room while an already visible search stays put',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 740);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetViewInsets);
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      final state = _state(
        playbackState: MusicBoxPlaybackState.stopped,
        positionMs: 0,
      );

      await tester.pumpWidget(
        _host(
          state,
          controller,
          platform: TargetPlatform.android,
          resizeToAvoidBottomInset: false,
        ),
      );
      await _toggleAddSources(tester);
      final search = find.byType(TextField);
      final beforeKeyboard = tester.getRect(search);

      await tester.tap(search);
      tester.view.viewInsets = const FakeViewPadding(bottom: 300);
      await tester.pump();

      final withKeyboard = tester.getRect(search);
      expect(withKeyboard, beforeKeyboard);
      expect(withKeyboard.bottom, lessThan(740 - 300));
      expect(tester.widget<TextField>(search).focusNode!.hasFocus, isTrue);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'android search keeps focus while the panel reaches its minimum height',
    (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      final state = _state(
        playbackState: MusicBoxPlaybackState.stopped,
        positionMs: 0,
      );

      await tester.pumpWidget(
        _host(state, controller, platform: TargetPlatform.android, height: 460),
      );
      await _toggleAddSources(tester);
      await tester.tap(find.byType(TextField));
      await tester.pump();
      expect(
        tester.widget<TextField>(find.byType(TextField)).focusNode!.hasFocus,
        isTrue,
      );
      expect(tester.testTextInput.isVisible, isTrue);

      await tester.pumpWidget(
        _host(state, controller, platform: TargetPlatform.android, height: 400),
      );
      await tester.pump();

      expect(
        tester.widget<TextField>(find.byType(TextField)).focusNode!.hasFocus,
        isTrue,
      );
      expect(tester.testTextInput.isVisible, isTrue);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('renders the server-reported position', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _state(playbackState: MusicBoxPlaybackState.playing, positionMs: 5000),
        controller,
      ),
    );

    expect(find.text('0:05'), findsOneWidget);
  });

  testWidgets('does not advance the position without a fresh snapshot', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _state(playbackState: MusicBoxPlaybackState.playing, positionMs: 5000),
        controller,
      ),
    );
    expect(find.text('0:05'), findsOneWidget);

    // No local ticker: pumping a frame must not move the position. The server
    // is the only thing that advances it, via a new snapshot.
    await tester.pump(const Duration(seconds: 5));
    expect(find.text('0:05'), findsOneWidget);
  });

  testWidgets('updates when a fresh snapshot reports a new position', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _state(playbackState: MusicBoxPlaybackState.playing, positionMs: 5000),
        controller,
      ),
    );
    expect(find.text('0:05'), findsOneWidget);

    await tester.pumpWidget(
      _host(
        _state(playbackState: MusicBoxPlaybackState.playing, positionMs: 6000),
        controller,
      ),
    );
    expect(find.text('0:06'), findsOneWidget);
    expect(find.text('0:05'), findsNothing);
  });
}

import 'package:client/src/app/music_box_controller.dart';
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

/// Widget-level coverage for the music box panel: the server-authoritative
/// progress bar (renders the snapshot position verbatim, never self-advances),
/// the now-playing strip, and the flat body list (点歌队列 always open with an
/// inline search panel; 房间歌单 / 我的歌单 as collapsed groups whose playlists
/// unfold their tracks in place).

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

const _roomPlaylist = PersonalMusicPlaylist(
  id: 'room-list',
  name: '房间收藏',
  description: '',
  revision: 1,
  itemCount: 1,
  createdAt: null,
  updatedAt: null,
);

const _roomPlaylistItem = PersonalMusicPlaylistItem(
  id: 'room-list-item',
  playlistId: 'room-list',
  trackId: 'track-1',
  source: 'netease',
  title: '房间歌曲',
  artists: ['Artist'],
  durationMs: 180000,
  sortOrder: 0,
  createdAt: null,
);

const _roomPlaylistSource = MusicBoxActiveSource(
  type: MusicBoxActiveSourceType.roomPlaylist,
  id: 'room-list',
  name: '房间收藏',
);

Finder _key(String value) => find.byKey(ValueKey<String>(value));

/// The accessible label of an in-list action. List actions carry no Material
/// tooltip (Windows accessibility-bridge workaround); the label lives on the
/// wrapping [Semantics] node instead.
String _actionLabel(WidgetTester tester, String key) {
  final semantics = find.descendant(
    of: _key(key),
    matching: find.byWidgetPredicate(
      (widget) => widget is Semantics && widget.properties.label != null,
    ),
  );
  return tester.widget<Semantics>(semantics.first).properties.label!;
}

/// Whether an in-list action is enabled.
bool _actionEnabled(WidgetTester tester, String key) {
  return tester
          .widget<ButtonIcon>(
            find.descendant(of: _key(key), matching: find.byType(ButtonIcon)),
          )
          .onPressed !=
      null;
}

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
  int playlistsRevision = 0,
  ValueChanged<MusicBoxState>? onStateChanged,
  ValueChanged<MusicBoxSearchResult>? onQueueResult,
  ValueChanged<MusicBoxQueueItem>? onRemoveItem,
  CurrentUser? currentUser,
  PublicRoom? room,
  UserProfileResolver? onResolveUserProfile,
  RoomProfileResolver? onResolveRoomProfile,
  UserProfileActionBuilder? userProfileActionBuilder,
  VoidCallback? onCreateFirstRoomPlaylist,
  VoidCallback? onCreateFirstPersonalPlaylist,
  MusicPlaylistEditCallback? onEditRoomPlaylist,
  MusicPlaylistEditCallback? onEditPersonalPlaylist,
  VoidCallback? onTogglePlayback,
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
          playlistsRevision: playlistsRevision,
          room: room,
          onStateChanged: onStateChanged,
          currentUser: currentUser,
          onResolveUserProfile: onResolveUserProfile,
          onResolveRoomProfile: onResolveRoomProfile,
          userProfileActionBuilder: userProfileActionBuilder,
          onCreateFirstRoomPlaylist: onCreateFirstRoomPlaylist,
          onCreateFirstPersonalPlaylist: onCreateFirstPersonalPlaylist,
          onEditRoomPlaylist: onEditRoomPlaylist,
          onEditPersonalPlaylist: onEditPersonalPlaylist,
          onTogglePlayback: onTogglePlayback ?? () {},
          onSkip: () {},
          onQueueResult: onQueueResult ?? (_) {},
          onRemoveItem: onRemoveItem ?? (_) {},
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
  String? activatedPlaylistId;
  String? activatedStartItemId;

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
    String? startItemId,
  }) async {
    activatedSourceType = sourceType;
    activatedPlaylistId = playlistId;
    activatedStartItemId = startItemId;
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
    this.roomPlaylists,
    this.personalPlaylists = const [],
  });

  PersonalMusicPlaylist playlist;
  List<PersonalMusicPlaylistItem> items;
  List<PersonalMusicPlaylist>? roomPlaylists;
  List<PersonalMusicPlaylist> personalPlaylists;
  int roomListCalls = 0;
  int personalListCalls = 0;
  int roomPlaylistLoads = 0;

  @override
  Future<PersonalMusicPlaylistPage> listRoomMusicPlaylists({
    required String roomId,
    int page = 1,
    int pageSize = 50,
  }) async {
    roomListCalls += 1;
    final playlists = roomPlaylists ?? [playlist];
    return PersonalMusicPlaylistPage(
      playlists: playlists,
      page: page,
      pageSize: pageSize,
      total: playlists.length,
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
    roomPlaylistLoads += 1;
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
    personalListCalls += 1;
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

MusicBoxQueueItem _track(
  String id, {
  String title = 'Song',
  String artist = '歌手',
  String source = 'netease',
  String? trackId,
  MusicBoxQueueItemStatus status = MusicBoxQueueItemStatus.ready,
  bool canPlayNow = false,
  bool canRemove = true,
  MusicBoxRequester? requestedBy,
}) {
  return MusicBoxQueueItem(
    id: id,
    source: source,
    trackId: trackId ?? 'track-$id',
    title: title,
    artist: artist,
    durationMs: 180000,
    status: status,
    fileSizeBytes: 0,
    error: '',
    addedByUserId: 'user',
    createdAt: null,
    canPlayNow: canPlayNow,
    canRemove: canRemove,
    requestedBy: requestedBy,
  );
}

MusicBoxState _state({
  required MusicBoxPlaybackState playbackState,
  required int positionMs,
  String currentItemId = 'a',
  List<MusicBoxQueueItem>? queue,
  List<MusicBoxQueueItem>? temporaryQueue,
  MusicBoxActiveSource activeSource = const MusicBoxActiveSource(),
  MusicBoxCapabilities capabilities = const MusicBoxCapabilities(),
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
      capabilities: capabilities,
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

Finder _groupHeader(MusicBoxActiveSourceType type) =>
    _key('music-box-group-header:${musicBoxActiveSourceTypeValue(type)}');

Finder _playlistRow(MusicBoxActiveSourceType type, String id) =>
    _key('music-box-playlist-row:${musicBoxActiveSourceTypeValue(type)}:$id');

/// Scrolls the flat body list until [finder] is inside its viewport.
Future<void> _reveal(WidgetTester tester, Finder finder) async {
  await tester.dragUntilVisible(
    finder,
    _key('music-box-list'),
    const Offset(0, -80),
  );
  await tester.pump();
}

/// Taps the group header of [type] and lets its playlist load resolve.
Future<void> _openGroup(WidgetTester tester, MusicBoxActiveSourceType type) async {
  await _reveal(tester, _groupHeader(type));
  await tester.tap(_groupHeader(type));
  await tester.pump();
  await tester.pump();
}

/// Taps a playlist row so its tracks unfold under it, and lets the template
/// load resolve.
Future<void> _unfoldPlaylist(
  WidgetTester tester,
  MusicBoxActiveSourceType type,
  String id,
) async {
  await _reveal(tester, _playlistRow(type, id));
  await tester.tap(_playlistRow(type, id));
  await tester.pump();
  await tester.pump();
}

/// Unfolds the inline search panel under the queue header.
Future<void> _openSearch(WidgetTester tester) async {
  await _reveal(tester, _key('music-box-search-toggle'));
  await tester.tap(_key('music-box-search-toggle'));
  await tester.pump();
}

/// Taps the header's active-source chip and lets its unfold + scroll settle.
Future<void> _showActiveSource(WidgetTester tester) async {
  await tester.tap(_key('music-box-active-source-chip'));
  await tester.pump();
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 200));
}

/// The queue section header row, located by its status title.
Finder _queueHeaderRow(String title) => find
    .ancestor(
      of: find.text(title, skipOffstage: false),
      matching: find.byType(Row),
    )
    .first;

bool _within(Rect inner, Rect outer) =>
    inner.top >= outer.top - 0.5 && inner.bottom <= outer.bottom + 0.5;

void main() {
  testWidgets('music box icon controls expose stable hover tooltips', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final item = _track('tooltip-track', title: '提示测试歌曲');

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.playing,
          positionMs: 1000,
          currentItemId: item.id,
          queue: [item],
          temporaryQueue: [item],
        ),
        controller,
        height: 600,
      ),
    );
    await tester.pump();

    String tooltip(String key) => tester.widget<ButtonIcon>(_key(key)).tooltip!;
    expect(tooltip('music-box-transport-previous'), '上一首');
    expect(tooltip('music-box-primary-playback'), '暂停');
    expect(tooltip('music-box-transport-next'), '下一首');
    expect(tooltip('music-box-transport-mode'), '播放模式：顺序播放');
    expect(tooltip('music-box-close'), '关闭音乐盒');
    expect(tester.takeException(), isNull);
  });

  testWidgets('now playing title moves only when it overflows', (tester) async {
    const longTitle = '一首需要在音乐盒顶部左右往返显示完整内容的特别长歌曲名称';
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final item = _track('marquee-current', title: longTitle, artist: '测试歌手');

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

    final track = _key('music-box-now-playing:title-marquee-track');
    expect(track, findsOneWidget);
    await tester.pump(const Duration(milliseconds: 900));
    await tester.pump(const Duration(milliseconds: 400));
    final firstOffset = tester.widget<Transform>(track).transform[12];
    await tester.pump(const Duration(milliseconds: 400));
    final secondOffset = tester.widget<Transform>(track).transform[12];
    expect(firstOffset, lessThan(0));
    expect(secondOffset, lessThan(firstOffset));
    expect(find.text(longTitle), findsWidgets);

    final shortItem = _track('short-current', title: '短歌名', artist: '测试歌手');
    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.playing,
          positionMs: 31000,
          currentItemId: shortItem.id,
          queue: [shortItem],
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
        return _track(
          'queue-$index',
          title: index.isEven
              ? '第 $index 首歌'
              : '第 $index 首需要多行显示以验证自适应高度定位的很长歌曲名称',
          artist: '歌手 $index',
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

      final list = _key('music-box-list');
      final currentTile = _key('music-box-queue-tile:queue-10');
      expect(currentTile, findsOneWidget);
      expect(
        (tester.getCenter(currentTile).dy - tester.getCenter(list).dy).abs(),
        lessThan(1.0),
      );

      await tester.drag(list, const Offset(0, -60));
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
        final queue = List<MusicBoxQueueItem>.generate(
          12,
          (index) => _track('boundary-$index', title: '第 $index 首歌'),
        );
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

        final listRect = tester.getRect(_key('music-box-list'));
        final tileRect = tester.getRect(
          _key('music-box-queue-tile:boundary-$currentIndex'),
        );
        final position = tester
            .state<ScrollableState>(
              find.descendant(
                of: _key('music-box-list'),
                matching: find.byType(Scrollable),
              ),
            )
            .position;
        expect(_within(tileRect, listRect), isTrue);
        if (currentIndex == 0) {
          expect(position.pixels, 0);
        } else {
          expect(position.pixels, closeTo(position.maxScrollExtent, 1));
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
      await _openSearch(tester);

      expect(find.text('QQ音乐'), findsNothing);
      expect(find.text('网易云'), findsOneWidget);
      expect(find.text('哔哩哔哩'), findsOneWidget);
      expect(
        tester
            .widget<SegmentedControl<String>>(_key('music-box-search-source'))
            .value,
        'netease',
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('members without control see a locked transport line', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.playing,
          positionMs: 1000,
          capabilities: const MusicBoxCapabilities(canControl: false),
        ),
        controller,
        height: 500,
      ),
    );

    expect(_key('music-box-transport-locked'), findsOneWidget);
    expect(find.text('播放由房间管理员控制'), findsOneWidget);
    expect(_key('music-box-primary-playback'), findsNothing);
    expect(_key('music-box-transport-previous'), findsNothing);
    expect(_key('music-box-transport-next'), findsNothing);
    expect(_key('music-box-transport-mode'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('now playing names the requester only for the request queue', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final item = _track(
      'req',
      requestedBy: const MusicBoxRequester(
        userId: 'requester',
        displayName: '点歌用户',
        avatarUrl: null,
        defaultAvatarKey: 'blue-3',
      ),
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.playing,
          positionMs: 0,
          currentItemId: item.id,
          queue: [item],
        ),
        controller,
        height: 500,
      ),
    );
    final chip = _key('music-box-now-playing:requester');
    expect(chip, findsOneWidget);
    expect(
      find.descendant(of: chip, matching: find.text('点歌用户')),
      findsOneWidget,
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.playing,
          positionMs: 0,
          currentItemId: item.id,
          queue: [item],
          activeSource: _roomPlaylistSource,
        ),
        controller,
        height: 500,
      ),
    );
    expect(chip, findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('group header of the active scope shows the playing state', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final wave = find.byIcon(Icons.graphic_eq);
    final queueHeader = _queueHeaderRow('点歌队列 · 1 首 · 正在播放');
    final roomHeader = _groupHeader(MusicBoxActiveSourceType.roomPlaylist);
    final myHeader = _groupHeader(MusicBoxActiveSourceType.userPlaylist);

    await tester.pumpWidget(
      _host(
        _state(playbackState: MusicBoxPlaybackState.playing, positionMs: 0),
        controller,
        height: 500,
      ),
    );
    expect(find.descendant(of: queueHeader, matching: wave), findsOneWidget);
    expect(find.descendant(of: roomHeader, matching: wave), findsNothing);
    expect(find.descendant(of: myHeader, matching: wave), findsNothing);

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.paused,
          positionMs: 0,
          activeSource: _roomPlaylistSource,
        ),
        controller,
        height: 500,
      ),
    );
    expect(find.descendant(of: roomHeader, matching: wave), findsOneWidget);
    expect(
      find.descendant(
        of: _queueHeaderRow('点歌队列 · 0 首 · 未播放'),
        matching: wave,
      ),
      findsNothing,
    );
    expect(find.descendant(of: myHeader, matching: wave), findsNothing);

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          activeSource: _roomPlaylistSource,
        ),
        controller,
        height: 500,
      ),
    );
    expect(wave, findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('playlist groups stay collapsed and unloaded until opened', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [_roomPlaylistItem],
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
    await tester.pump();
    await tester.pump();

    expect(_key('music-box-queue-tile:a'), findsOneWidget);
    expect(_groupHeader(MusicBoxActiveSourceType.roomPlaylist), findsOneWidget);
    expect(_groupHeader(MusicBoxActiveSourceType.userPlaylist), findsOneWidget);
    for (final header in [
      _groupHeader(MusicBoxActiveSourceType.roomPlaylist),
      _groupHeader(MusicBoxActiveSourceType.userPlaylist),
    ]) {
      expect(
        find.descendant(of: header, matching: find.byIcon(Icons.chevron_right)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: header, matching: find.byIcon(Icons.expand_more)),
        findsNothing,
      );
    }
    expect(_playlistRow(MusicBoxActiveSourceType.roomPlaylist, 'room-list'), findsNothing);
    expect(api.roomListCalls, 0);
    expect(api.personalListCalls, 0);

    await _openGroup(tester, MusicBoxActiveSourceType.roomPlaylist);
    expect(api.roomListCalls, 1);
    expect(api.personalListCalls, 0);
    expect(
      find.descendant(
        of: _groupHeader(MusicBoxActiveSourceType.roomPlaylist),
        matching: find.byIcon(Icons.expand_more),
      ),
      findsOneWidget,
    );
    expect(_playlistRow(MusicBoxActiveSourceType.roomPlaylist, 'room-list'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('room playlist group lists playlists and unfolds one in place', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [_roomPlaylistItem],
      roomPlaylists: const [
        _roomPlaylist,
        PersonalMusicPlaylist(
          id: 'room-list-2',
          name: '房间第二单',
          description: '',
          revision: 1,
          itemCount: 3,
          createdAt: null,
          updatedAt: null,
        ),
      ],
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

    await _openGroup(tester, MusicBoxActiveSourceType.roomPlaylist);
    final header = _groupHeader(MusicBoxActiveSourceType.roomPlaylist);
    expect(find.descendant(of: header, matching: find.text('2')), findsOneWidget);
    final row = _playlistRow(MusicBoxActiveSourceType.roomPlaylist, 'room-list');
    expect(
      find.descendant(of: row, matching: find.text('房间收藏')),
      findsOneWidget,
    );
    expect(
      _playlistRow(MusicBoxActiveSourceType.roomPlaylist, 'room-list-2'),
      findsOneWidget,
    );
    expect(_key('music-box-queue-tile:a'), findsOneWidget);
    expect(api.roomPlaylistLoads, 0);

    await _unfoldPlaylist(tester, MusicBoxActiveSourceType.roomPlaylist, 'room-list');
    expect(api.roomPlaylistLoads, 1);
    final tracks = _key('music-box-playlist-tracks:room-list');
    final track = _key('music-box-playlist-track:netease:track-1');
    expect(find.descendant(of: tracks, matching: track), findsOneWidget);
    final listRect = tester.getRect(_key('music-box-list'));
    expect(_within(tester.getRect(row), listRect), isTrue);
    expect(tester.getTopLeft(track).dy, greaterThan(tester.getTopLeft(row).dy));
    expect(
      tester.getTopLeft(_playlistRow(MusicBoxActiveSourceType.roomPlaylist, 'room-list-2')).dy,
      greaterThan(tester.getTopLeft(track).dy),
    );
    expect(api.activatedSourceType, isNull);

    await tester.tap(row);
    await tester.pump();
    expect(tracks, findsNothing);
    expect(track, findsNothing);
    expect(row, findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a song inside a playlist activates the playlist from itself', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [_roomPlaylistItem],
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
    await _openGroup(tester, MusicBoxActiveSourceType.roomPlaylist);

    // The playlist row itself carries no play action.
    expect(_key('music-box-playlist-play-all:room_playlist:room-list'), findsNothing);
    expect(api.activatedSourceType, isNull);

    await _unfoldPlaylist(tester, MusicBoxActiveSourceType.roomPlaylist, 'room-list');
    final trackPlay = _key('music-box-playlist-track-play:netease:track-1');
    expect(_actionLabel(tester, 'music-box-playlist-track-play:netease:track-1'), '从这首歌开始播放歌单');
    await tester.tap(trackPlay);
    await tester.pump();
    expect(api.activatedSourceType, MusicBoxActiveSourceType.roomPlaylist);
    expect(api.activatedPlaylistId, 'room-list');
    expect(api.activatedStartItemId, 'room-list-item');
    expect(tester.takeException(), isNull);
  });

  testWidgets('active source chip unfolds the playing playlist in its group', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final snapshot = [
      _track('snap-1', title: '快照第一首'),
      _track('snap-2', title: '快照第二首'),
    ];
    final state = _state(
      playbackState: MusicBoxPlaybackState.playing,
      positionMs: 1000,
      currentItemId: 'snap-1',
      queue: snapshot,
      temporaryQueue: [_track('req-1', title: '待点歌曲')],
      activeSource: _roomPlaylistSource,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [_roomPlaylistItem],
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
    expect(_key('music-box-queue-tile:req-1'), findsOneWidget);
    expect(_key('music-box-queue-tile:snap-1'), findsNothing);

    await _showActiveSource(tester);

    expect(_key('music-box-search-panel'), findsNothing);
    expect(_key('music-box-queue-tile:req-1'), findsOneWidget);
    expect(
      find.descendant(
        of: _groupHeader(MusicBoxActiveSourceType.roomPlaylist),
        matching: find.byIcon(Icons.expand_more),
      ),
      findsOneWidget,
    );
    final row = _playlistRow(MusicBoxActiveSourceType.roomPlaylist, 'room-list');
    expect(row, findsOneWidget);
    final tracks = _key('music-box-playlist-tracks:room-list');
    expect(
      find.descendant(of: tracks, matching: _key('music-box-queue-tile:snap-1')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: tracks, matching: _key('music-box-queue-tile:snap-2')),
      findsOneWidget,
    );
    expect(tester.getTopLeft(_key('music-box-queue-tile:snap-1')).dy, greaterThan(tester.getTopLeft(row).dy));
    expect(_key('music-box-queue-leading-playing:snap-1'), findsOneWidget);
    expect(_key('music-box-queue-remove:snap-1'), findsNothing);
    expect(_key('music-box-playlist-track:netease:track-1'), findsNothing);
    expect(api.roomPlaylistLoads, 0);
    // Nothing plays a playlist as a whole; songs inside carry playback.
    expect(_key('music-box-playlist-play-all:room_playlist:room-list'), findsNothing);
    expect(api.activatedSourceType, isNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('active source chip scrolls back to the request queue', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final queue = List<MusicBoxQueueItem>.generate(
      15,
      (index) => _track('req-$index', title: '点歌 $index'),
    );
    final state = _state(
      playbackState: MusicBoxPlaybackState.playing,
      positionMs: 0,
      currentItemId: 'req-0',
      queue: queue,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [_roomPlaylistItem],
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
    await tester.pump();
    await tester.pump();
    await _openGroup(tester, MusicBoxActiveSourceType.roomPlaylist);
    await _unfoldPlaylist(tester, MusicBoxActiveSourceType.roomPlaylist, 'room-list');
    final listRect = tester.getRect(_key('music-box-list'));
    final queueHeader = find.text('点歌队列 · 15 首 · 正在播放', skipOffstage: false);
    expect(tester.getRect(queueHeader).top, lessThan(listRect.top));
    expect(_key('music-box-playlist-track:netease:track-1'), findsOneWidget);

    await _showActiveSource(tester);

    expect(_within(tester.getRect(queueHeader), listRect), isTrue);
    expect(_key('music-box-queue-tile:req-0'), findsOneWidget);
    // Revealing the queue does not fold what the user opened below it.
    expect(
      find.byKey(
        const ValueKey<String>('music-box-playlist-tracks:room-list'),
        skipOffstage: false,
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('a queue song switches the room back to the request queue', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.playing,
      positionMs: 0,
      currentItemId: 'snap-1',
      queue: [_track('snap-1', title: '快照歌曲')],
      temporaryQueue: [
        _track('req-1', title: '第一首点歌', canPlayNow: false),
        _track('req-2', title: '第二首点歌', canPlayNow: false),
      ],
      activeSource: _roomPlaylistSource,
    );
    final api = _MusicBoxApiFake(state);

    await tester.pumpWidget(
      _host(
        state,
        controller,
        height: 500,
        musicBoxController: MusicBoxController(api: api),
        roomId: 'room-1',
      ),
    );

    expect(find.text('点歌队列 · 2 首 · 未播放'), findsOneWidget);
    expect(_key('music-box-queue-tile:snap-1'), findsNothing);
    // No queue-level play: the unit of playback is a song. While a playlist
    // is active every request offers to switch the room back from itself,
    // regardless of the per-item play-now permission.
    expect(_key('music-box-queue-play-all'), findsNothing);
    final play = _key('music-box-queue-play-now:req-2');
    expect(_actionLabel(tester, 'music-box-queue-play-now:req-2'), '从这首歌开始播放点歌队列');
    await tester.tap(play);
    await tester.pump();
    expect(api.activatedSourceType, MusicBoxActiveSourceType.temporary);
    expect(api.activatedPlaylistId, isNull);
    expect(api.activatedStartItemId, 'req-2');
    expect(api.action, isNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('queue row play-now jumps to that request', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.playing,
      positionMs: 0,
      currentItemId: 'cur',
      queue: [
        _track('cur', title: '正在播放的歌', canPlayNow: true),
        _track('next', title: '插队歌曲', canPlayNow: true),
      ],
    );
    final api = _MusicBoxApiFake(state);

    await tester.pumpWidget(
      _host(
        state,
        controller,
        height: 500,
        musicBoxController: MusicBoxController(api: api),
        roomId: 'room-1',
      ),
    );

    expect(find.text('点歌队列 · 2 首 · 正在播放'), findsOneWidget);
    expect(_key('music-box-queue-play-all'), findsNothing);
    expect(_key('music-box-queue-play-now:cur'), findsNothing);
    await tester.tap(_key('music-box-queue-play-now:next'));
    await tester.pump();

    expect(api.action, 'play_now');
    expect(api.itemId, 'next');
    expect(find.text('已优先播放'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('priority play from the song card uses the floating notice', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.playing,
      positionMs: 0,
      currentItemId: 'another-track',
      queue: [_track('priority-track', title: '优先歌曲', canPlayNow: true)],
    );

    await tester.pumpWidget(
      _host(
        state,
        controller,
        height: 500,
        musicBoxController: MusicBoxController(api: _MusicBoxApiFake(state)),
        roomId: 'room-1',
      ),
    );

    await tester.tap(find.text('优先歌曲'));
    await tester.pump();
    await tester.tap(find.widgetWithText(Button, '优先播放'));
    await tester.pump();

    expect(find.text('已优先播放'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('queue row remove button appears only on removable requests', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final removed = <MusicBoxQueueItem>[];

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          currentItemId: '',
          queue: [
            _track('removable', title: '可删除'),
            _track('locked', title: '不可删除', canRemove: false),
          ],
        ),
        controller,
        height: 500,
        onRemoveItem: removed.add,
      ),
    );

    final remove = _key('music-box-queue-remove:removable');
    expect(_actionLabel(tester, 'music-box-queue-remove:removable'), '从点歌队列删除');
    await tester.tap(remove);
    await tester.pump();
    expect(removed.map((item) => item.id), ['removable']);

    expect(_key('music-box-queue-remove:locked'), findsNothing);
    expect(_key('music-box-queue-tile:locked'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('clearing the request queue asks first then clears', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
      currentItemId: '',
      queue: [_track('req', title: '点歌队列歌曲')],
    );
    final api = _MusicBoxApiFake(state);

    await tester.pumpWidget(
      _host(
        state,
        controller,
        height: 500,
        musicBoxController: MusicBoxController(api: api),
        roomId: 'room-1',
      ),
    );

    await tester.tap(_key('music-box-queue-clear'));
    await tester.pumpAndSettle();
    expect(find.text('确认清空当前点歌队列？此操作不会删除已保存的歌单。'), findsOneWidget);
    expect(api.action, isNull);
    await tester.tap(_key('music-box-confirm-clear-temporary-queue'));
    await tester.pumpAndSettle();
    expect(api.action, 'clear_temporary_playlist');
    expect(tester.takeException(), isNull);
  });

  testWidgets('search toggle unfolds a panel whose query replaces the list', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final queued = <MusicBoxSearchResult>[];
    const fresh = MusicBoxSearchResult(
      trackId: 'qingtian',
      name: '晴天',
      artists: ['周杰伦'],
      source: 'netease',
    );
    const alreadyQueued = MusicBoxSearchResult(
      trackId: 'already-queued-track',
      name: '已点歌曲',
      artists: ['歌手'],
      source: 'netease',
    );
    final queuedItem = _track(
      'queued-item',
      trackId: 'already-queued-track',
      title: '已点歌曲',
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          currentItemId: '',
          queue: [queuedItem],
          temporaryQueue: [queuedItem],
        ),
        controller,
        height: 500,
        searchResults: const [fresh, alreadyQueued],
        onQueueResult: queued.add,
      ),
    );
    final roomHeader = _groupHeader(MusicBoxActiveSourceType.roomPlaylist);
    final myHeader = _groupHeader(MusicBoxActiveSourceType.userPlaylist);
    final toggle = _key('music-box-search-toggle');
    expect(_key('music-box-search-panel'), findsNothing);
    expect(_key('music-box-search-input'), findsNothing);
    expect(_actionLabel(tester, 'music-box-search-toggle'), '搜索歌曲点歌');

    await _openSearch(tester);
    expect(_key('music-box-search-panel'), findsOneWidget);
    expect(_actionLabel(tester, 'music-box-search-toggle'), '收起搜索');
    expect(
      tester.widget<TextField>(find.byType(TextField)).focusNode!.hasFocus,
      isTrue,
    );
    expect(_key('music-box-queue-tile:queued-item'), findsOneWidget);
    expect(_key('music-box-search-results-list'), findsNothing);
    expect(roomHeader, findsOneWidget);

    await tester.enterText(_key('music-box-search-input'), '晴天');
    await tester.pump();

    expect(_key('music-box-search-results-list'), findsOneWidget);
    expect(find.text('搜索结果 · 网易云 · 2 首'), findsOneWidget);
    expect(_key('music-box-queue-tile:queued-item'), findsNothing);
    expect(roomHeader, findsNothing);
    expect(myHeader, findsNothing);

    final add = _key('music-box-search-add:netease:qingtian');
    expect(_actionLabel(tester, 'music-box-search-add:netease:qingtian'), '加入点歌队列');
    await tester.tap(add);
    await tester.pump();
    expect(queued, const [fresh]);

    final queuedAdd = _key('music-box-search-add:netease:already-queued-track');
    expect(_actionLabel(tester, 'music-box-search-add:netease:already-queued-track'), '已在点歌队列');
    expect(_actionEnabled(tester, 'music-box-search-add:netease:already-queued-track'), isFalse);
    await tester.tap(queuedAdd);
    await tester.pump();
    expect(queued, const [fresh]);

    await tester.tap(find.byTooltip('清空搜索'));
    await tester.pump();
    expect(controller.text, isEmpty);
    expect(_key('music-box-search-panel'), findsOneWidget);
    expect(_key('music-box-search-results-list'), findsNothing);
    expect(_key('music-box-queue-tile:queued-item'), findsOneWidget);
    expect(roomHeader, findsOneWidget);

    await tester.enterText(_key('music-box-search-input'), '晴天');
    await tester.pump();
    expect(_key('music-box-search-results-list'), findsOneWidget);

    await tester.tap(toggle);
    await tester.pump();
    expect(controller.text, isEmpty);
    expect(_key('music-box-search-panel'), findsNothing);
    expect(_key('music-box-search-results-list'), findsNothing);
    expect(_key('music-box-queue-tile:queued-item'), findsOneWidget);
    expect(roomHeader, findsOneWidget);
    expect(myHeader, findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('empty playlist groups offer the matching first-playlist action', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    var personalCreateCount = 0;
    var roomCreateCount = 0;
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [],
      roomPlaylists: const [],
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
        onCreateFirstPersonalPlaylist: () => personalCreateCount += 1,
        onCreateFirstRoomPlaylist: () => roomCreateCount += 1,
      ),
    );

    await _openGroup(tester, MusicBoxActiveSourceType.userPlaylist);
    expect(find.text('还没有个人歌单'), findsOneWidget);
    final createPersonal = _key('music-box-create-first-personal-playlist');
    await _reveal(tester, createPersonal);
    await tester.tap(createPersonal);
    await tester.pump();
    expect(personalCreateCount, 1);
    expect(roomCreateCount, 0);

    await _openGroup(tester, MusicBoxActiveSourceType.roomPlaylist);
    expect(find.text('还没有房间歌单'), findsOneWidget);
    expect(find.text('还没有个人歌单', skipOffstage: false), findsOneWidget);
    final createRoom = _key('music-box-create-first-room-playlist');
    await _reveal(tester, createRoom);
    await tester.tap(createRoom);
    await tester.pump();
    expect(personalCreateCount, 1);
    expect(roomCreateCount, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('my playlists pins an active playlist the viewer cannot list', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    const mine = PersonalMusicPlaylist(
      id: 'mine',
      name: '我的收藏',
      description: '',
      revision: 1,
      itemCount: 5,
      createdAt: null,
      updatedAt: null,
    );
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
      currentItemId: '',
      queue: [_track('snap-1'), _track('snap-2')],
      activeSource: const MusicBoxActiveSource(
        type: MusicBoxActiveSourceType.userPlaylist,
        id: 'other-playlist',
        name: '夜晚精选',
        ownerUserId: 'other-user',
      ),
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [],
      personalPlaylists: const [mine],
    );

    await tester.pumpWidget(
      _host(
        state,
        controller,
        height: 620,
        musicBoxController: MusicBoxController(api: api),
        roomId: 'room-1',
        currentUser: _playlistCurrentUser,
        room: _playlistRoom,
      ),
    );
    await _openGroup(tester, MusicBoxActiveSourceType.userPlaylist);

    expect(
      find.descendant(
        of: _groupHeader(MusicBoxActiveSourceType.userPlaylist),
        matching: find.text('2'),
      ),
      findsOneWidget,
    );
    final pinned = _playlistRow(MusicBoxActiveSourceType.userPlaylist, 'other-playlist');
    final own = _playlistRow(MusicBoxActiveSourceType.userPlaylist, 'mine');
    expect(
      find.descendant(of: pinned, matching: find.text('夜晚精选')),
      findsOneWidget,
    );
    expect(find.descendant(of: pinned, matching: find.text('2 首')), findsOneWidget);
    expect(find.descendant(of: own, matching: find.text('5 首')), findsOneWidget);
    expect(tester.getTopLeft(pinned).dy, lessThan(tester.getTopLeft(own).dy));
    expect(tester.takeException(), isNull);
  });

  testWidgets('playlist row card views a read-only playlist in place', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [_roomPlaylistItem],
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
    await _openGroup(tester, MusicBoxActiveSourceType.roomPlaylist);

    await tester.tap(_key('music-box-playlist-card-anchor:room-list'));
    await tester.pump();
    expect(_key('music-playlist-card:room-list'), findsOneWidget);
    expect(find.text('查看歌单'), findsOneWidget);
    expect(find.text('编辑歌单'), findsNothing);
    expect(_key('music-box-playlist-tracks:room-list'), findsNothing);

    await tester.tap(_key('music-playlist-card-view'));
    await tester.pump();
    await tester.pump();
    await tester.pump();
    final row = _playlistRow(MusicBoxActiveSourceType.roomPlaylist, 'room-list');
    expect(row, findsOneWidget);
    expect(
      find.descendant(
        of: _key('music-box-playlist-tracks:room-list'),
        matching: _key('music-box-playlist-track:netease:track-1'),
      ),
      findsOneWidget,
    );
    expect(api.activatedSourceType, isNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('preset track song card offers no queue action', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [_roomPlaylistItem],
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
    await _openGroup(tester, MusicBoxActiveSourceType.roomPlaylist);
    await _unfoldPlaylist(tester, MusicBoxActiveSourceType.roomPlaylist, 'room-list');

    await tester.tap(
      find.descendant(
        of: _key('music-box-playlist-track:netease:track-1'),
        matching: find.text('房间歌曲'),
      ),
    );
    await tester.pump();
    final card = _key('music-box-song-card:search:netease:track-1');
    expect(card, findsOneWidget);
    expect(
      find.descendant(of: card, matching: find.text('点歌队列')),
      findsNothing,
    );
    expect(
      find.descendant(of: card, matching: find.text('已在队列中')),
      findsNothing,
    );
    expect(
      find.descendant(of: card, matching: find.text('添加到歌单')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('downloading queue item shows a spinner as its leading glyph', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final item = _track(
      'downloading-track',
      title: '正在下载的歌曲',
      status: MusicBoxQueueItemStatus.downloading,
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          queue: [item],
          temporaryQueue: [item],
        ),
        controller,
        height: 500,
      ),
    );

    final loading = _key('music-box-queue-leading-loading:downloading-track');
    expect(loading, findsOneWidget);
    expect(_key('music-box-queue-leading-index:downloading-track'), findsNothing);
    expect(
      tester.getCenter(loading).dx,
      lessThan(tester.getTopLeft(find.text('正在下载的歌曲')).dx),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('search result opens a song card with an explicit queue action', (
    tester,
  ) async {
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
        height: 500,
        searchResults: const [result],
        onQueueResult: queued.add,
      ),
    );
    await _openSearch(tester);

    final tile = _key('music-box-search-tile:bilibili:BV1xx411c7mD');
    expect(tile, findsOneWidget);
    expect(
      find.descendant(of: tile, matching: find.byIcon(Icons.music_note)),
      findsOneWidget,
    );
    expect(
      find.descendant(of: tile, matching: find.text('哔哩哔哩')),
      findsNothing,
    );

    await tester.tap(tile);
    await tester.pump();
    final songCard = _key('music-box-song-card:search:bilibili:BV1xx411c7mD');
    expect(songCard, findsOneWidget);
    expect(find.text('点歌人'), findsNothing);
    expect(
      find.descendant(of: songCard, matching: find.text('歌单')),
      findsNothing,
    );
    expect(find.text('作者'), findsOneWidget);
    expect(find.text('详情'), findsOneWidget);
    expect(find.text('BV1xx411c7mD'), findsOneWidget);
    final queueAction = find.descendant(
      of: songCard,
      matching: find.text('点歌队列'),
    );
    await tester.tap(queueAction);
    await tester.pump();
    expect(queued, const [result]);
    expect(tester.takeException(), isNull);
  });

  testWidgets('queued catalog track disables the song card queue action', (
    tester,
  ) async {
    final controller = TextEditingController(text: '已点歌曲');
    final queued = <MusicBoxSearchResult>[];
    addTearDown(controller.dispose);
    const result = MusicBoxSearchResult(
      trackId: 'already-queued-track',
      name: '已点歌曲',
      artists: ['歌手'],
      source: 'netease',
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          temporaryQueue: [
            _track('queued-item', trackId: 'already-queued-track', title: '已点歌曲'),
          ],
        ),
        controller,
        height: 500,
        searchResults: const [result],
        onQueueResult: queued.add,
      ),
    );
    await _openSearch(tester);

    await tester.tap(_key('music-box-search-tile:netease:already-queued-track'));
    await tester.pump();
    final card = _key('music-box-song-card:search:netease:already-queued-track');
    expect(card, findsOneWidget);
    final queuedButton = find.descendant(
      of: card,
      matching: find.widgetWithText(Button, '已在队列中'),
    );
    expect(tester.widget<Button>(queuedButton).onPressed, isNull);
    expect(queued, isEmpty);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Bilibili queue card shows its BV details after attribution', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final item = _track(
      'bilibili-request',
      source: 'bilibili',
      trackId: 'BV17x411w7KC',
      title: '点歌的哔哩哔哩歌曲',
      requestedBy: const MusicBoxRequester(
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
          queue: [item],
          temporaryQueue: [item],
        ),
        controller,
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
      final controller = TextEditingController();
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        _host(
          _state(playbackState: MusicBoxPlaybackState.stopped, positionMs: 0),
          controller,
          platform: platform,
          height: 500,
        ),
      );
      await tester.tap(
        find.descendant(
          of: _key('music-box-queue-list'),
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
      expect(_key('music-box-song-card:a'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets(
      '${platform.name} playlist picker shows complete adaptive names and scopes',
      (tester) async {
        final controller = TextEditingController();
        addTearDown(controller.dispose);
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
            controller,
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
            of: _key('music-box-queue-list'),
            matching: find.text('Song'),
          ),
        );
        await tester.pump();
        await tester.tap(find.text('添加到歌单'));
        await tester.pumpAndSettle();

        final roomTarget = _key(
          'music-track-playlist-target:room:room-1:room-playlist-long',
        );
        final personalTarget = _key(
          'music-track-playlist-target:personal-playlist-long',
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

  testWidgets('long queue rows grow and keep every title and artist line', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    const longTitle =
        '[Hi-Res lossless] | A very long song title that must remain complete '
        'across as many lines as the narrow queue needs';
    final item = _track(
      'long-queue-item',
      title: longTitle,
      artist: 'An equally long artist name that must wrap without truncation',
    );

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          queue: [item],
          temporaryQueue: [item],
        ),
        controller,
        height: 500,
      ),
    );

    final tile = _key('music-box-queue-tile:long-queue-item');
    final title = tester.widget<Text>(
      find.descendant(of: tile, matching: find.text(longTitle)),
    );
    expect(tester.getSize(tile).height, greaterThan(82));
    expect(title.maxLines, isNull);
    expect(title.overflow, isNull);
    expect(title.style?.fontSize, lessThan(13));
    expect(tester.takeException(), isNull);
  });

  testWidgets('song requester avatar stays next to the right-aligned name', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final item = _track(
      'requester-position',
      artist: 'Artist',
      requestedBy: const MusicBoxRequester(
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
          queue: [item],
          temporaryQueue: [item],
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
        of: _key('music-box-queue-tile:requester-position'),
        matching: find.text('Song'),
      ),
    );
    await tester.pump();

    final group = _key('music-box-song-attribution-value-group');
    final avatar = _key('music-box-song-attribution-avatar');
    final name = find.descendant(
      of: group,
      matching: _key('music-box-song-attribution-name'),
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
    expect(_key('music-box-song-card:requester-position'), findsOneWidget);
    expect(group, findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'playlist attribution shows a playlist icon and opens its playlist card',
    (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      final state = _state(
        playbackState: MusicBoxPlaybackState.stopped,
        positionMs: 0,
        queue: [_track('playlist-owner-width', artist: 'Artist')],
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
      );
      final api = _RoomPlaylistApiFake(
        state,
        playlist: _roomPlaylist,
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
      await _showActiveSource(tester);
      await tester.tap(
        find.descendant(
          of: _key('music-box-playlist-tracks:playlist-123'),
          matching: find.text('Song'),
        ),
      );
      await tester.pump();

      final attribution = _key('music-box-song-playlist-attribution');
      expect(attribution, findsOneWidget);
      final attributionIcon = find.descendant(
        of: attribution,
        matching: _key('music-box-song-playlist-icon'),
      );
      expect(attributionIcon, findsOneWidget);
      expect(
        find.descendant(of: attribution, matching: find.text('123')),
        findsOneWidget,
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
      final playlistCard = _key('music-playlist-card:playlist-123');
      expect(playlistCard, findsOneWidget);
      expect(
        find.descendant(of: playlistCard, matching: find.text('testxxx')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('owned playlist card edits the exact playlist', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    String? editedPlaylistId;
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
      queue: [_track('owned-playlist-track', title: 'Owned song')],
      activeSource: const MusicBoxActiveSource(
        type: MusicBoxActiveSourceType.userPlaylist,
        id: 'owned-playlist',
        name: 'Owned playlist',
        ownerUserId: 'playlist-current-user',
      ),
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [],
    );

    await tester.pumpWidget(
      _host(
        state,
        controller,
        height: 620,
        musicBoxController: MusicBoxController(api: api),
        roomId: 'room-1',
        currentUser: _playlistCurrentUser,
        room: _playlistRoom,
        onEditPersonalPlaylist: (playlistId) async {
          editedPlaylistId = playlistId;
        },
      ),
    );
    await _showActiveSource(tester);
    await tester.tap(find.text('Owned song'));
    await tester.pump();
    final attribution = _key('music-box-song-playlist-attribution');
    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(attribution));
    await tester.pumpAndSettle();

    expect(find.text('编辑歌单'), findsOneWidget);
    expect(find.text('查看歌单'), findsNothing);
    await tester.tap(_key('music-playlist-card-view'));
    await tester.pumpAndSettle();

    expect(editedPlaylistId, 'owned-playlist');
    expect(tester.takeException(), isNull);
  });

  testWidgets('manageable room playlist card uses the room editor', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    String? editedPlaylistId;
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
      queue: [_track('room-playlist-track', title: 'Room song')],
      activeSource: _roomPlaylistSource,
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [],
    );

    await tester.pumpWidget(
      _host(
        state,
        controller,
        height: 620,
        musicBoxController: MusicBoxController(api: api),
        roomId: 'room-1',
        currentUser: _playlistCurrentUser,
        room: _playlistRoom,
        onEditRoomPlaylist: (playlistId) async {
          editedPlaylistId = playlistId;
        },
      ),
    );
    await _showActiveSource(tester);
    await tester.tap(find.text('Room song'));
    await tester.pump();
    final attribution = _key('music-box-song-playlist-attribution');
    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(attribution));
    await tester.pumpAndSettle();

    expect(find.text('编辑歌单'), findsOneWidget);
    await tester.tap(_key('music-playlist-card-view'));
    await tester.pumpAndSettle();

    expect(editedPlaylistId, 'room-list');
    expect(tester.takeException(), isNull);
  });

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
    await _openSearch(tester);
    expect(tester.takeException(), isNull);
    final tile = _key('music-box-search-tile:netease:long-title');
    expect(tester.getSize(tile).height, greaterThan(50));

    await tester.tap(tile);
    await tester.pump();
    expect(tester.takeException(), isNull);

    final adaptiveTitle = _key('music-box-song-card-title');
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
    final state = _state(
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
    );
    final api = _RoomPlaylistApiFake(
      state,
      playlist: _roomPlaylist,
      items: const [],
    );

    await tester.pumpWidget(
      _host(
        state,
        controller,
        platform: TargetPlatform.android,
        height: 500,
        musicBoxController: MusicBoxController(api: api),
        roomId: 'room-1',
      ),
    );

    await _showActiveSource(tester);
    await tester.tap(
      find.descendant(
        of: _key('music-box-playlist-tracks:personal-list'),
        matching: find.text('Song'),
      ),
    );
    await tester.pump();

    final songCard = _key('music-box-song-card:a');
    expect(songCard, findsOneWidget);
    expect(
      find.descendant(of: songCard, matching: find.text('歌单')),
      findsOneWidget,
    );
    expect(find.text('用户名称的歌单'), findsNothing);
    expect(find.text(' · 通勤歌单'), findsNothing);
    expect(find.text('点歌人'), findsNothing);
    final attribution = _key('music-box-song-playlist-attribution');
    expect(
      find.descendant(of: attribution, matching: find.text('通勤歌单')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: attribution,
        matching: _key('music-box-song-playlist-icon'),
      ),
      findsOneWidget,
    );

    await tester.tapAt(tester.getCenter(attribution));
    await tester.pump();
    final playlistCard = _key('music-playlist-card:personal-list');
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

    final volume = _key('music-box-volume-control');
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

  testWidgets('height pressure shrinks only the body list', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final state = _state(
      playbackState: MusicBoxPlaybackState.stopped,
      positionMs: 0,
    );

    await tester.pumpWidget(_host(state, controller, height: 400));
    await _openSearch(tester);
    final searchField = _key('music-box-search-input');
    final sourcePicker = _key('music-box-search-source');
    final volume = _key('music-box-volume-control');
    final transport = _key('music-box-primary-playback');
    final list = _key('music-box-list');
    final comfortableSearchSize = tester.getSize(searchField);
    final comfortableSourceSize = tester.getSize(sourcePicker);
    final comfortableVolumeSize = tester.getSize(volume);
    final comfortableTransportSize = tester.getSize(transport);
    final comfortableListHeight = tester.getSize(list).height;
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(_host(state, controller, height: 370));
    await _openSearch(tester);

    expect(tester.getSize(searchField), comfortableSearchSize);
    expect(tester.getSize(sourcePicker), comfortableSourceSize);
    expect(tester.getSize(volume), comfortableVolumeSize);
    expect(tester.getSize(transport), comfortableTransportSize);
    expect(tester.getSize(list).height, closeTo(comfortableListHeight - 30, 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('empty request queue points at the search toggle', (
    tester,
  ) async {
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
    expect(find.text('点歌队列为空'), findsOneWidget);
    expect(find.text('点击右上角的搜索按钮点歌'), findsOneWidget);
    expect(_key('music-box-search-toggle'), findsOneWidget);
    expect(_key('music-box-queue-list'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Android keyboard overlays the room while the search field stays put',
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
      await _openSearch(tester);
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
      await _openSearch(tester);
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

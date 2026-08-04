import 'package:client/src/home/live_channel_pane.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// Widget-level coverage for the server-authoritative progress bar: it renders
/// the snapshot's reported position verbatim and updates only when a fresh
/// snapshot arrives — no local stepping, no client clock.

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
          onTogglePlayback: () {},
          onSkip: () {},
          onQueueResult: (_) {},
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

MusicBoxState _state({
  required MusicBoxPlaybackState playbackState,
  required int positionMs,
  String currentItemId = 'a',
  List<MusicBoxQueueItem>? queue,
  List<MusicBoxQueueItem>? temporaryQueue,
  MusicBoxActiveSource activeSource = const MusicBoxActiveSource(),
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
        expect(find.textContaining('网易云'), findsOneWidget);
        expect(find.text('搜索添加'), findsNothing);

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
        expect(find.text('点歌人'), findsOneWidget);
        expect(find.text('房间专属名'), findsOneWidget);
        final requesterAvatar = tester.widget<Avatar>(find.byType(Avatar).last);
        expect(requesterAvatar.label, '点歌用户');

        await tester.tapAt(const Offset(355, 495));
        await tester.pump();

        await _toggleAddSources(tester);

        expect(find.text('点歌队列'), findsNothing);
        expect(find.text('点播歌曲'), findsNothing);
        expect(find.text('搜索添加'), findsOneWidget);
        expect(find.text('房间歌单'), findsOneWidget);
        expect(find.text('我的歌单'), findsOneWidget);
        expect(find.byType(Input), findsOneWidget);

        await _toggleAddSources(tester);

        expect(find.text('点歌队列'), findsOneWidget);
        expect(find.text('点播歌曲'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }

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

    await tester.pumpWidget(
      _host(
        _state(
          playbackState: MusicBoxPlaybackState.stopped,
          positionMs: 0,
          temporaryQueue: const [requestedItem],
          activeSource: const MusicBoxActiveSource(
            type: MusicBoxActiveSourceType.roomPlaylist,
            id: 'room-list',
            name: '房间收藏',
          ),
        ),
        controller,
        height: 500,
      ),
    );

    expect(find.textContaining('当前：'), findsNothing);
    expect(find.text('房间收藏'), findsOneWidget);
    expect(find.text('Song'), findsWidgets);
    expect(find.text('待点歌曲'), findsNothing);
    expect(find.text('切回点歌队列（1）'), findsOneWidget);
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

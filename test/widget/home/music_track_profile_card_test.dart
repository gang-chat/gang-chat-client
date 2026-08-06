import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/music_track_preview.dart';
import 'package:client/src/home/music_track_profile_card.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/ui.dart' as ui;

void main() {
  for (final platform in [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      'song card previews and stops when closed on ${platform.name}',
      (tester) async {
        final api = _PreviewApi();
        final player = _PreviewPlatform();
        final controller = MusicTrackPreviewController(
          api: api,
          platform: player,
        );
        PersonalMusicPlaylist? addedToPlaylist;
        addTearDown(controller.dispose);

        await tester.pumpWidget(
          MaterialApp(
            theme: ui.uiTheme().copyWith(platform: platform),
            home: Scaffold(
              body: Align(
                alignment: Alignment.topLeft,
                child: MusicTrackHoverCard(
                  data: const MusicTrackCardData(
                    id: 'item-1',
                    source: 'netease',
                    trackId: 'track-1',
                    title: '完整歌曲名称',
                    artists: ['歌手'],
                    durationMs: 180000,
                  ),
                  previewController: controller,
                  playlists: const [
                    PersonalMusicPlaylist(
                      id: 'playlist-1',
                      name: '完整歌单名称',
                      description: '',
                      revision: 1,
                      itemCount: 2,
                      createdAt: null,
                      updatedAt: null,
                    ),
                  ],
                  onAddToPlaylist: (playlist) async {
                    addedToPlaylist = playlist;
                  },
                  child: const SizedBox(
                    key: ValueKey<String>('track-row'),
                    width: 240,
                    height: 56,
                    child: Text('完整歌曲名称'),
                  ),
                ),
              ),
            ),
          ),
        );

        await tester.tap(find.byKey(const ValueKey<String>('track-row')));
        await tester.pumpAndSettle();
        expect(find.text('试听'), findsOneWidget);
        expect(find.text('添加到歌单'), findsOneWidget);
        expect(find.text('3:00'), findsOneWidget);

        await tester.tap(
          find.byKey(const ValueKey<String>('music-track-card-preview')),
        );
        await tester.pump();
        expect(find.byType(CircularProgressIndicator), findsOneWidget);

        api.complete();
        await tester.pumpAndSettle();
        expect(find.text('取消试听'), findsOneWidget);
        expect(player.playCalls, 1);

        await tester.tap(
          find.byKey(
            const ValueKey<String>('music-track-card-add-to-playlist'),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('选择歌单'), findsOneWidget);
        expect(find.text('完整歌单名称'), findsOneWidget);
        await tester.tap(
          find.byKey(
            const ValueKey<String>(
              'music-track-playlist-target-add:playlist-1',
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(addedToPlaylist?.id, 'playlist-1');
        expect(find.text('取消试听'), findsOneWidget);

        await tester.tapAt(const Offset(700, 500));
        await tester.pump(const Duration(milliseconds: 200));
        expect(find.text('取消试听'), findsNothing);
        expect(player.stopCalls, greaterThanOrEqualTo(2));
      },
    );
  }
}

class _PreviewApi implements MusicTrackPreviewApi {
  final Completer<DownloadedFile> _download = Completer<DownloadedFile>();

  void complete() {
    _download.complete(
      DownloadedFile(
        bytes: Uint8List.fromList([1, 2, 3]),
        filename: 'preview.m4a',
        mimeType: 'audio/mp4',
      ),
    );
  }

  @override
  Future<DownloadedFile> downloadMusicTrackPreview({
    required String source,
    required String trackId,
  }) => _download.future;
}

class _PreviewPlatform implements MusicTrackPreviewPlatform {
  final StreamController<void> _completed = StreamController<void>.broadcast(
    sync: true,
  );
  int playCalls = 0;
  int stopCalls = 0;

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
  Future<void> stop() async {
    stopCalls += 1;
  }

  @override
  Future<void> dispose() => _completed.close();
}

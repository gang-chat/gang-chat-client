import 'dart:async';
import 'dart:typed_data';

import 'package:client/src/app/music_track_preview.dart';
import 'package:client/src/home/music_playlist_snapshot_dialog.dart';
import 'package:client/src/home/music_track_profile_card.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  for (final platform in const [
    TargetPlatform.windows,
    TargetPlatform.macOS,
    TargetPlatform.android,
  ]) {
    testWidgets(
      '${platform.name} playlist snapshot exposes clone and interactive songs',
      (tester) async {
        var cloned = 0;
        var added = 0;
        const targetPlaylist = PersonalMusicPlaylist(
          id: 'personal-1',
          name: '我的收藏',
          description: '',
          revision: 1,
          itemCount: 0,
          createdAt: null,
          updatedAt: null,
        );

        await tester.pumpWidget(
          MaterialApp(
            theme: uiTheme().copyWith(platform: platform),
            home: Scaffold(
              body: Builder(
                builder: (context) => TextButton(
                  onPressed: () => showDialog<void>(
                    context: context,
                    builder: (context) => MusicPlaylistSnapshotDialog(
                      title: '共享歌单',
                      tracks: const [
                        MusicTrackCardData(
                          id: 'shared-track-1',
                          source: 'netease',
                          trackId: 'track-1',
                          title: '第一首完整歌曲',
                          artists: ['歌手'],
                          durationMs: 180000,
                        ),
                      ],
                      previewApi: _PreviewApi(),
                      previewPlatformFactory: _PreviewFactory(),
                      loadPersonalPlaylists: () async =>
                          const PersonalMusicPlaylistPage(
                            playlists: [targetPlaylist],
                            page: 1,
                            pageSize: 50,
                            total: 1,
                            hasMore: false,
                            maxPlaylists: 50,
                            maxPlaylistItems: 500,
                          ),
                      onAddToPlaylist: (_, playlist) async {
                        expect(playlist.id, targetPlaylist.id);
                        added += 1;
                      },
                      onClone: () async {
                        cloned += 1;
                        return const PersonalMusicPlaylist(
                          id: 'clone-1',
                          name: '共享歌单',
                          description: '',
                          revision: 1,
                          itemCount: 1,
                          createdAt: null,
                          updatedAt: null,
                        );
                      },
                      contentKey: const ValueKey<String>(
                        'shared-playlist-dialog',
                      ),
                      cloneButtonKey: const ValueKey<String>(
                        'shared-playlist-clone',
                      ),
                    ),
                  ),
                  child: const Text('打开'),
                ),
              ),
            ),
          ),
        );

        await tester.tap(find.text('打开'));
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey<String>('shared-playlist-dialog')),
          findsOneWidget,
        );
        expect(find.text('第一首完整歌曲'), findsOneWidget);
        expect(find.text('克隆到我的歌单'), findsOneWidget);

        await tester.tap(
          find.byKey(const ValueKey<String>('shared-playlist-clone')),
        );
        await tester.pumpAndSettle();
        expect(cloned, 1);
        expect(find.text('已克隆到我的歌单'), findsOneWidget);

        await tester.tap(
          find.byKey(
            const ValueKey<String>(
              'music-playlist-snapshot-track:shared-track-1',
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey<String>('music-track-card:shared-track-1')),
          findsOneWidget,
        );
        expect(
          find.byKey(const ValueKey<String>('music-track-card-preview')),
          findsOneWidget,
        );
        await tester.tap(
          find.byKey(
            const ValueKey<String>('music-track-card-add-to-playlist'),
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(
          find.byKey(
            const ValueKey<String>('music-track-playlist-target:personal-1'),
          ),
        );
        await tester.pump();
        await tester.tap(
          find.byKey(
            const ValueKey<String>('music-track-playlist-target-confirm'),
          ),
        );
        await tester.pumpAndSettle();
        expect(added, 1);
        expect(tester.takeException(), isNull);
      },
    );
  }
}

class _PreviewApi implements MusicTrackPreviewApi {
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

class _PreviewFactory implements MusicTrackPreviewPlatformFactory {
  @override
  MusicTrackPreviewPlatform create() => _PreviewPlatform();
}

class _PreviewPlatform implements MusicTrackPreviewPlatform {
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

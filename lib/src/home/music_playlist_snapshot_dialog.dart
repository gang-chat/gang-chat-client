import 'dart:async';

import 'package:flutter/material.dart';

import '../app/error_display.dart';
import '../app/music_box_display.dart' as music_box_display;
import '../app/music_track_preview.dart';
import '../protocol/api_client.dart';
import '../protocol/models.dart';
import '../ui/ui.dart';
import 'music_track_profile_card.dart';

typedef MusicPlaylistSnapshotLoader =
    Future<PersonalMusicPlaylistPage> Function();
typedef MusicPlaylistSnapshotAddTrack =
    Future<void> Function(
      MusicTrackCardData track,
      PersonalMusicPlaylist playlist,
    );
typedef MusicPlaylistSnapshotClone = Future<PersonalMusicPlaylist> Function();

/// A reusable, immutable playlist snapshot viewer.
///
/// Both the active music-box playlist and playlists embedded in chat use this
/// surface so song-card interaction, preview lifecycle and clone limits remain
/// consistent regardless of where the playlist was opened.
class MusicPlaylistSnapshotDialog extends StatefulWidget {
  const MusicPlaylistSnapshotDialog({
    super.key,
    required this.title,
    required this.tracks,
    required this.loadPersonalPlaylists,
    required this.onAddToPlaylist,
    required this.previewPlatformFactory,
    this.previewApi,
    this.onClone,
    this.contentKey,
    this.trackListKey,
    this.cloneButtonKey,
    this.doneButtonKey,
    this.cloneSuccessMessage,
    this.cloneErrorMessage,
    this.cloneErrorFallback = '克隆歌单失败，请重试',
  });

  final String title;
  final List<MusicTrackCardData> tracks;
  final MusicPlaylistSnapshotLoader loadPersonalPlaylists;
  final MusicPlaylistSnapshotAddTrack onAddToPlaylist;
  final MusicTrackPreviewApi? previewApi;
  final MusicTrackPreviewPlatformFactory? previewPlatformFactory;
  final MusicPlaylistSnapshotClone? onClone;
  final Key? contentKey;
  final Key? trackListKey;
  final Key? cloneButtonKey;
  final Key? doneButtonKey;
  final String Function(PersonalMusicPlaylist playlist)? cloneSuccessMessage;
  final String Function(Object error)? cloneErrorMessage;
  final String cloneErrorFallback;

  @override
  State<MusicPlaylistSnapshotDialog> createState() =>
      _MusicPlaylistSnapshotDialogState();
}

class _MusicPlaylistSnapshotDialogState
    extends State<MusicPlaylistSnapshotDialog> {
  MusicTrackPreviewController? _previewController;
  List<PersonalMusicPlaylist> _personalPlaylists = const [];
  int _maxPlaylists = 50;
  bool _loadingPlaylists = true;
  bool _cloning = false;
  PersonalMusicPlaylist? _clonedPlaylist;

  bool get _playlistLimitReached =>
      !_loadingPlaylists && _personalPlaylists.length >= _maxPlaylists;

  @override
  void initState() {
    super.initState();
    _createPreviewController();
    unawaited(_loadPlaylists());
  }

  @override
  void didUpdateWidget(covariant MusicPlaylistSnapshotDialog oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.previewApi != widget.previewApi ||
        oldWidget.previewPlatformFactory != widget.previewPlatformFactory) {
      final previous = _previewController;
      _previewController = null;
      if (previous != null) unawaited(previous.dispose());
      _createPreviewController();
    }
  }

  void _createPreviewController() {
    final api = widget.previewApi;
    final factory = widget.previewPlatformFactory;
    if (api != null && factory != null) {
      _previewController = MusicTrackPreviewController(
        api: api,
        platform: factory.create(),
      );
    }
  }

  @override
  void dispose() {
    final preview = _previewController;
    if (preview != null) unawaited(preview.dispose());
    super.dispose();
  }

  Future<void> _loadPlaylists() async {
    try {
      final page = await widget.loadPersonalPlaylists();
      if (!mounted) return;
      setState(() {
        _personalPlaylists = page.playlists;
        _maxPlaylists = page.maxPlaylists;
        _loadingPlaylists = false;
      });
    } catch (_) {
      if (mounted) setState(() => _loadingPlaylists = false);
    }
  }

  Future<void> _clone() async {
    final clone = widget.onClone;
    if (clone == null ||
        _cloning ||
        _loadingPlaylists ||
        _clonedPlaylist != null ||
        _playlistLimitReached) {
      return;
    }
    setState(() => _cloning = true);
    try {
      final playlist = await clone();
      if (!mounted) return;
      setState(() {
        _clonedPlaylist = playlist;
        _personalPlaylists = [..._personalPlaylists, playlist];
      });
      final notice =
          widget.cloneSuccessMessage?.call(playlist) ??
          '已克隆到我的歌单 - ${playlist.name}';
      showFloatingSuccessNotice(context, notice);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          widget.cloneErrorMessage?.call(error) ??
              userFacingErrorMessage(
                error,
                fallback: widget.cloneErrorFallback,
              ),
        );
      }
    } finally {
      if (mounted) setState(() => _cloning = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final canClone =
        widget.onClone != null &&
        !_loadingPlaylists &&
        !_playlistLimitReached &&
        _clonedPlaylist == null;
    final cloneLabel = _clonedPlaylist != null
        ? '已克隆到我的歌单'
        : _loadingPlaylists
        ? '正在检查我的歌单'
        : _playlistLimitReached
        ? '我的歌单已满'
        : '克隆到我的歌单';
    final height = (MediaQuery.sizeOf(context).height * 0.68).clamp(
      260.0,
      620.0,
    );
    return DialogFrame(
      title: widget.title,
      icon: Icons.queue_music,
      maxWidth: 760,
      adaptiveActions: [
        if (widget.onClone != null)
          ResponsiveDialogAction(
            buttonKey: widget.cloneButtonKey,
            label: cloneLabel,
            icon: Icons.library_add,
            tone: ButtonTone.primary,
            loading: _cloning,
            onPressed: canClone ? () => unawaited(_clone()) : null,
          ),
        ResponsiveDialogAction(
          buttonKey: widget.doneButtonKey,
          label: '完成',
          icon: Icons.check,
          onPressed: () => Navigator.of(context).pop(),
        ),
      ],
      child: SizedBox(
        key: widget.contentKey,
        height: height,
        child: widget.tracks.isEmpty
            ? Center(
                child: Text(
                  '这个歌单还没有歌曲',
                  style: UiTypography.body.copyWith(color: UiColors.textMuted),
                ),
              )
            : ListView.separated(
                key: widget.trackListKey,
                padding: EdgeInsets.zero,
                itemCount: widget.tracks.length,
                separatorBuilder: (_, _) => const SizedBox(height: 10),
                itemBuilder: (context, index) {
                  final track = widget.tracks[index];
                  return MusicPlaylistSnapshotTrackTile(
                    track: track,
                    previewController: _previewController,
                    playlists: _personalPlaylists,
                    onAddToPlaylist: (playlist) =>
                        widget.onAddToPlaylist(track, playlist),
                  );
                },
              ),
      ),
    );
  }
}

class MusicPlaylistSnapshotTrackTile extends StatelessWidget {
  const MusicPlaylistSnapshotTrackTile({
    super.key,
    required this.track,
    required this.previewController,
    required this.playlists,
    required this.onAddToPlaylist,
    this.surfaceKey,
  });

  final MusicTrackCardData track;
  final MusicTrackPreviewController? previewController;
  final List<PersonalMusicPlaylist> playlists;
  final Future<void> Function(PersonalMusicPlaylist playlist) onAddToPlaylist;
  final Key? surfaceKey;

  @override
  Widget build(BuildContext context) {
    final subtitle = [
      if (track.artists.isNotEmpty) track.artists.join('、'),
      music_box_display.musicBoxSourceLabel(track.source),
    ].join(' · ');
    final surface = LayoutBuilder(
      builder: (context, constraints) {
        const titleStyle = TextStyle(
          color: UiColors.text,
          fontSize: 13,
          fontWeight: FontWeight.w600,
        );
        const subtitleStyle = TextStyle(
          color: UiColors.textMuted,
          fontSize: 12,
        );
        final textWidth = (constraints.maxWidth - 58).clamp(
          40.0,
          double.infinity,
        );
        return MusicPlaylistTrackSurface(
          key:
              surfaceKey ??
              ValueKey<String>('music-playlist-snapshot-track:${track.id}'),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(
              children: [
                const Icon(Icons.music_note, size: 19, color: UiColors.accent),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        track.title,
                        style: _adaptiveListTextStyle(
                          context,
                          text: track.title,
                          baseStyle: titleStyle,
                          width: textWidth,
                        ),
                        softWrap: true,
                      ),
                      const SizedBox(height: 3),
                      Text(
                        subtitle,
                        style: _adaptiveListTextStyle(
                          context,
                          text: subtitle,
                          baseStyle: subtitleStyle,
                          width: textWidth,
                        ),
                        softWrap: true,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
    final preview = previewController;
    if (preview == null) return surface;
    return MusicTrackHoverCard(
      data: track,
      previewController: preview,
      playlists: playlists,
      onAddToPlaylist: onAddToPlaylist,
      child: surface,
    );
  }
}

TextStyle _adaptiveListTextStyle(
  BuildContext context, {
  required String text,
  required TextStyle baseStyle,
  required double width,
}) {
  final painter = TextPainter(
    text: TextSpan(text: text.isEmpty ? ' ' : text, style: baseStyle),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout(maxWidth: width);
  final lineCount = painter.computeLineMetrics().length;
  if (lineCount <= 2) return baseStyle;
  final baseSize = baseStyle.fontSize ?? 12;
  return baseStyle.copyWith(
    fontSize: (baseSize - (lineCount - 2).clamp(1, 2)).clamp(10, baseSize),
  );
}

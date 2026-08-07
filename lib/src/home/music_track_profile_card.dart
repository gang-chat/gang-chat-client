import 'dart:async';

import 'package:flutter/material.dart';

import '../app/error_display.dart';
import '../app/music_box_display.dart' as music_box_display;
import '../app/music_track_preview.dart';
import '../protocol/models.dart';
import '../ui/ui.dart';
import 'hover_card_anchor.dart';

class MusicTrackCardData {
  const MusicTrackCardData({
    required this.id,
    required this.source,
    required this.trackId,
    required this.title,
    required this.artists,
    required this.durationMs,
  });

  final String id;
  final String source;
  final String trackId;
  final String title;
  final List<String> artists;
  final int durationMs;

  MusicTrackPreviewTrack get previewTrack =>
      MusicTrackPreviewTrack(source: source, trackId: trackId);
}

enum MusicTrackPlaylistTargetScope { personal, room }

/// A writable playlist shown by a song card.
///
/// Keeping the scope beside the playlist prevents room playlists from being
/// accidentally submitted through the personal-playlist API when a picker
/// contains both kinds of target.
class MusicTrackPlaylistTarget {
  const MusicTrackPlaylistTarget.personal(this.playlist)
    : scope = MusicTrackPlaylistTargetScope.personal,
      roomId = null;

  const MusicTrackPlaylistTarget.room({
    required this.playlist,
    required this.roomId,
  }) : scope = MusicTrackPlaylistTargetScope.room;

  final PersonalMusicPlaylist playlist;
  final MusicTrackPlaylistTargetScope scope;
  final String? roomId;

  bool get roomScoped => scope == MusicTrackPlaylistTargetScope.room;

  String get key => '${scope.name}:${roomId ?? ''}:${playlist.id}';
}

/// Song profile card for saved-playlist rows. Preview is local-only: it never
/// changes the room queue or authoritative music-box playback state.
class MusicTrackHoverCard extends StatefulWidget {
  const MusicTrackHoverCard({
    super.key,
    required this.data,
    required this.previewController,
    required this.child,
    this.playlists = const [],
    this.loadPlaylists,
    this.playlistsRoomScoped = false,
    this.onAddToPlaylist,
    this.playlistTargets = const [],
    this.loadPlaylistTargets,
    this.playlistTargetsLoadKey,
    this.onAddToPlaylistTarget,
  });

  final MusicTrackCardData data;
  final MusicTrackPreviewController previewController;
  final Widget child;
  final List<PersonalMusicPlaylist> playlists;
  final Future<List<PersonalMusicPlaylist>> Function()? loadPlaylists;
  final bool playlistsRoomScoped;
  final Future<void> Function(PersonalMusicPlaylist playlist)? onAddToPlaylist;
  final List<MusicTrackPlaylistTarget> playlistTargets;
  final Future<List<MusicTrackPlaylistTarget>> Function()? loadPlaylistTargets;
  final Object? playlistTargetsLoadKey;
  final Future<void> Function(MusicTrackPlaylistTarget target)?
  onAddToPlaylistTarget;

  @override
  State<MusicTrackHoverCard> createState() => _MusicTrackHoverCardState();
}

class _MusicTrackHoverCardState extends State<MusicTrackHoverCard> {
  bool _showPlaylistPicker = false;
  String? _addingPlaylistTargetKey;
  List<MusicTrackPlaylistTarget>? _loadedPlaylistTargets;
  bool _loadingPlaylists = false;

  @override
  void didUpdateWidget(covariant MusicTrackHoverCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.data.previewTrack.key != widget.data.previewTrack.key ||
        oldWidget.previewController != widget.previewController) {
      unawaited(
        oldWidget.previewController.stopIf(oldWidget.data.previewTrack.key),
      );
      _showPlaylistPicker = false;
      _addingPlaylistTargetKey = null;
    }
    final keyedLoader = widget.playlistTargetsLoadKey != null;
    final loaderChanged = keyedLoader
        ? oldWidget.playlistTargetsLoadKey != widget.playlistTargetsLoadKey
        : oldWidget.loadPlaylists != widget.loadPlaylists ||
              oldWidget.loadPlaylistTargets != widget.loadPlaylistTargets;
    if (loaderChanged) {
      _loadedPlaylistTargets = null;
      _loadingPlaylists = false;
    }
  }

  Future<void> _ensurePlaylistsLoaded() async {
    final loadTargets = widget.loadPlaylistTargets;
    final loadLegacy = widget.loadPlaylists;
    if ((loadTargets == null && loadLegacy == null) ||
        _loadedPlaylistTargets != null ||
        _loadingPlaylists) {
      return;
    }
    setState(() => _loadingPlaylists = true);
    try {
      final targets = loadTargets != null
          ? await loadTargets()
          : [
              for (final playlist in await loadLegacy!())
                _legacyPlaylistTarget(playlist),
            ];
      if (mounted) setState(() => _loadedPlaylistTargets = targets);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          userFacingErrorMessage(error, fallback: '加载歌单失败，请重试'),
        );
      }
    } finally {
      if (mounted) setState(() => _loadingPlaylists = false);
    }
  }

  MusicTrackPlaylistTarget _legacyPlaylistTarget(
    PersonalMusicPlaylist playlist,
  ) {
    return widget.playlistsRoomScoped
        ? MusicTrackPlaylistTarget.room(playlist: playlist, roomId: '')
        : MusicTrackPlaylistTarget.personal(playlist);
  }

  List<MusicTrackPlaylistTarget> get _playlistTargets {
    final loaded = _loadedPlaylistTargets;
    if (loaded != null) return loaded;
    if (widget.playlistTargets.isNotEmpty) return widget.playlistTargets;
    return [
      for (final playlist in widget.playlists) _legacyPlaylistTarget(playlist),
    ];
  }

  @override
  void dispose() {
    unawaited(widget.previewController.stopIf(widget.data.previewTrack.key));
    super.dispose();
  }

  Future<void> _togglePreview() async {
    try {
      await widget.previewController.toggle(widget.data.previewTrack);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          userFacingErrorMessage(error, fallback: '试听失败，请重试'),
        );
      }
    }
  }

  Future<void> _addToPlaylist(MusicTrackPlaylistTarget target) async {
    final addTarget = widget.onAddToPlaylistTarget;
    final addLegacy = widget.onAddToPlaylist;
    if ((addTarget == null && addLegacy == null) ||
        _addingPlaylistTargetKey != null) {
      return;
    }
    setState(() => _addingPlaylistTargetKey = target.key);
    try {
      if (addTarget != null) {
        await addTarget(target);
      } else {
        await addLegacy!(target.playlist);
      }
      if (!mounted) return;
      setState(() => _showPlaylistPicker = false);
      showFloatingSuccessNotice(context, '已添加到「${target.playlist.name}」');
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          userFacingErrorMessage(error, fallback: '添加失败，请检查歌单权限'),
        );
      }
    } finally {
      if (mounted) setState(() => _addingPlaylistTargetKey = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final trackKey = widget.data.previewTrack.key;
    return HoverCardAnchor(
      cardWidth: 304,
      resetKey: Object.hash(widget.data.id, trackKey),
      onVisibilityChanged: (visible) {
        if (visible) unawaited(_ensurePlaylistsLoaded());
        if (!visible) {
          unawaited(widget.previewController.stopIf(trackKey));
          if (_showPlaylistPicker && mounted) {
            setState(() => _showPlaylistPicker = false);
          }
        }
      },
      cardBuilder: (context) => StreamBuilder<MusicTrackPreviewSnapshot>(
        stream: widget.previewController.changes,
        initialData: widget.previewController.snapshot,
        builder: (context, snapshot) {
          final preview = snapshot.data ?? widget.previewController.snapshot;
          return _MusicTrackProfileCard(
            data: widget.data,
            loading: preview.isLoading(trackKey),
            playing: preview.isPlaying(trackKey),
            onTogglePreview: _togglePreview,
            playlistTargets: _playlistTargets,
            loadingPlaylists: _loadingPlaylists,
            showPlaylistPicker: _showPlaylistPicker,
            addingPlaylistTargetKey: _addingPlaylistTargetKey,
            onOpenPlaylistPicker:
                widget.onAddToPlaylist == null &&
                    widget.onAddToPlaylistTarget == null
                ? null
                : () => setState(() => _showPlaylistPicker = true),
            onClosePlaylistPicker: () =>
                setState(() => _showPlaylistPicker = false),
            onAddToPlaylistTarget: _addToPlaylist,
          );
        },
      ),
      child: widget.child,
    );
  }
}

class _MusicTrackProfileCard extends StatelessWidget {
  const _MusicTrackProfileCard({
    required this.data,
    required this.loading,
    required this.playing,
    required this.onTogglePreview,
    required this.playlistTargets,
    required this.loadingPlaylists,
    required this.showPlaylistPicker,
    required this.addingPlaylistTargetKey,
    required this.onOpenPlaylistPicker,
    required this.onClosePlaylistPicker,
    required this.onAddToPlaylistTarget,
  });

  final MusicTrackCardData data;
  final bool loading;
  final bool playing;
  final VoidCallback onTogglePreview;
  final List<MusicTrackPlaylistTarget> playlistTargets;
  final bool loadingPlaylists;
  final bool showPlaylistPicker;
  final String? addingPlaylistTargetKey;
  final VoidCallback? onOpenPlaylistPicker;
  final VoidCallback onClosePlaylistPicker;
  final ValueChanged<MusicTrackPlaylistTarget> onAddToPlaylistTarget;

  @override
  Widget build(BuildContext context) {
    final artists = data.artists
        .map((value) => value.trim())
        .where((value) => value.isNotEmpty)
        .join('、');
    return Padding(
      key: ValueKey<String>('music-track-card:${data.id}'),
      padding: const EdgeInsets.all(UiSpacing.lg),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.only(top: 2),
                child: Icon(Icons.music_note, color: UiColors.accent, size: 24),
              ),
              const SizedBox(width: UiSpacing.sm),
              Expanded(
                child: HoverCardSelectableText(
                  value: data.title,
                  maxLines: 12,
                  style: UiTypography.title.copyWith(fontSize: 16),
                ),
              ),
            ],
          ),
          const SizedBox(height: UiSpacing.md),
          _MusicTrackDetailRow(
            label: '时长',
            value: data.durationMs > 0
                ? music_box_display.musicBoxFormatDuration(data.durationMs)
                : '未知',
          ),
          _MusicTrackDetailRow(
            label: '歌手',
            value: artists.isEmpty ? '未知歌手' : artists,
          ),
          _MusicTrackDetailRow(
            label: '来源',
            value: music_box_display.musicBoxSourceLabel(data.source),
          ),
          if (data.source.trim().toLowerCase() == 'bilibili')
            _MusicTrackDetailRow(
              label: '详情',
              value: _bilibiliTrackID(data.trackId),
            ),
          const SizedBox(height: UiSpacing.md),
          if (!showPlaylistPicker)
            ResponsiveDialogActionBar(
              expanded: true,
              actions: [
                ResponsiveDialogAction(
                  label: playing ? '取消试听' : '试听',
                  buttonKey: const ValueKey<String>('music-track-card-preview'),
                  icon: playing ? Icons.stop_rounded : Icons.play_arrow,
                  tone: playing ? ButtonTone.danger : ButtonTone.primary,
                  loading: loading,
                  onPressed: loading ? null : onTogglePreview,
                ),
                if (onOpenPlaylistPicker != null)
                  ResponsiveDialogAction(
                    label: '添加到歌单',
                    buttonKey: const ValueKey<String>(
                      'music-track-card-add-to-playlist',
                    ),
                    icon: Icons.playlist_add,
                    tone: ButtonTone.primary,
                    onPressed: onOpenPlaylistPicker,
                  ),
              ],
            )
          else ...[
            Row(
              children: [
                ButtonIcon(
                  key: const ValueKey<String>(
                    'music-track-card-playlist-picker-back',
                  ),
                  icon: const Icon(Icons.arrow_back),
                  tooltip: '返回歌曲信息',
                  size: 30,
                  onPressed: addingPlaylistTargetKey == null
                      ? onClosePlaylistPicker
                      : null,
                ),
                const SizedBox(width: UiSpacing.sm),
                const Text('选择歌单', style: UiTypography.label),
              ],
            ),
            const SizedBox(height: UiSpacing.sm),
            if (loadingPlaylists)
              const SizedBox(
                height: 54,
                child: Center(child: CircularProgressIndicator()),
              )
            else if (playlistTargets.isEmpty)
              const SizedBox(
                height: 54,
                child: Center(
                  child: Text(
                    '还没有可用歌单',
                    style: TextStyle(color: UiColors.textMuted, fontSize: 11),
                  ),
                ),
              )
            else
              ConstrainedBox(
                constraints: const BoxConstraints(maxHeight: 180),
                child: ListView.separated(
                  padding: EdgeInsets.zero,
                  shrinkWrap: true,
                  itemCount: playlistTargets.length,
                  separatorBuilder: (_, _) =>
                      const SizedBox(height: UiSpacing.sm),
                  itemBuilder: (context, index) {
                    final target = playlistTargets[index];
                    return _MusicTrackPlaylistTargetButton(
                      target: target,
                      loading: addingPlaylistTargetKey == target.key,
                      enabled: addingPlaylistTargetKey == null,
                      onAdd: () => onAddToPlaylistTarget(target),
                    );
                  },
                ),
              ),
          ],
        ],
      ),
    );
  }
}

class _MusicTrackPlaylistTargetButton extends StatelessWidget {
  const _MusicTrackPlaylistTargetButton({
    required this.target,
    required this.loading,
    required this.enabled,
    required this.onAdd,
  });

  final MusicTrackPlaylistTarget target;
  final bool loading;
  final bool enabled;
  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    final playlist = target.playlist;
    return LayoutBuilder(
      builder: (context, constraints) {
        const style = TextStyle(
          color: UiColors.text,
          fontSize: 12,
          fontWeight: FontWeight.w600,
          height: 1.25,
        );
        final textWidth = (constraints.maxWidth - 76).clamp(
          48.0,
          double.infinity,
        );
        final painter = TextPainter(
          text: TextSpan(text: playlist.name, style: style),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(maxWidth: textWidth);
        final height = (painter.height + 20).clamp(46.0, double.infinity);
        return PressableSurface(
          key: ValueKey<String>('music-track-playlist-target:${playlist.id}'),
          width: double.infinity,
          height: height,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          backgroundColor: UiColors.surfaceLow,
          pressedBackgroundColor: UiColors.surfacePressed,
          borderColor: UiColors.border,
          borderRadius: UiRadii.md,
          child: Row(
            children: [
              Icon(
                target.roomScoped ? Icons.meeting_room : Icons.person,
                color: UiColors.accent,
                size: 17,
              ),
              const SizedBox(width: 7),
              Expanded(
                child: Text(playlist.name, style: style, softWrap: true),
              ),
              const SizedBox(width: 8),
              ButtonIcon(
                key: ValueKey<String>(
                  'music-track-playlist-target-add:${target.roomScoped ? target.key : playlist.id}',
                ),
                icon: const Icon(Icons.add),
                tooltip: '添加到歌单',
                tone: ButtonTone.primary,
                loading: loading,
                onPressed: enabled ? onAdd : null,
                size: 32,
              ),
            ],
          ),
        );
      },
    );
  }
}

class _MusicTrackDetailRow extends StatelessWidget {
  const _MusicTrackDetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 38,
            child: HoverCardSelectableText(
              value: label,
              style: UiTypography.label.copyWith(color: UiColors.textMuted),
            ),
          ),
          Expanded(
            child: HoverCardSelectableText(
              value: value,
              textAlign: TextAlign.right,
              maxLines: 12,
              style: UiTypography.label.copyWith(color: UiColors.text),
            ),
          ),
        ],
      ),
    );
  }
}

final RegExp _bvidPattern = RegExp(r'BV[0-9A-Za-z]+', caseSensitive: false);

String _bilibiliTrackID(String trackId) {
  final raw = trackId.trim();
  final match = _bvidPattern.firstMatch(raw)?.group(0);
  if (match == null || match.length <= 2) return raw.isEmpty ? '未知' : raw;
  return 'BV${match.substring(2)}';
}

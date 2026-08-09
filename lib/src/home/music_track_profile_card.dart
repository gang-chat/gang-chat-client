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
  @override
  void didUpdateWidget(covariant MusicTrackHoverCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.data.previewTrack.key != widget.data.previewTrack.key ||
        oldWidget.previewController != widget.previewController) {
      unawaited(
        oldWidget.previewController.stopIf(oldWidget.data.previewTrack.key),
      );
    }
  }

  Future<List<MusicTrackPlaylistTarget>> _loadPlaylistTargets() async {
    final loadTargets = widget.loadPlaylistTargets;
    final loadLegacy = widget.loadPlaylists;
    if (loadTargets != null) return loadTargets();
    if (loadLegacy != null) {
      return [
        for (final playlist in await loadLegacy())
          _legacyPlaylistTarget(playlist),
      ];
    }
    if (widget.playlistTargets.isNotEmpty) return widget.playlistTargets;
    return [
      for (final playlist in widget.playlists) _legacyPlaylistTarget(playlist),
    ];
  }

  MusicTrackPlaylistTarget _legacyPlaylistTarget(
    PersonalMusicPlaylist playlist,
  ) {
    return widget.playlistsRoomScoped
        ? MusicTrackPlaylistTarget.room(playlist: playlist, roomId: '')
        : MusicTrackPlaylistTarget.personal(playlist);
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
    if (addTarget != null) {
      await addTarget(target);
    } else if (addLegacy != null) {
      await addLegacy(target.playlist);
    }
  }

  Future<void> _openPlaylistPicker() async {
    final target = await showMusicTrackPlaylistTargetDialog(
      context,
      loadTargets: _loadPlaylistTargets,
      onAdd: _addToPlaylist,
    );
    if (!mounted || target == null) return;
    showFloatingSuccessNotice(context, '已添加到「${target.playlist.name}」');
  }

  @override
  Widget build(BuildContext context) {
    final trackKey = widget.data.previewTrack.key;
    return HoverCardAnchor(
      cardWidth: 304,
      resetKey: Object.hash(widget.data.id, trackKey),
      onVisibilityChanged: (visible) {
        if (!visible) {
          unawaited(widget.previewController.stopIf(trackKey));
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
            onOpenPlaylistPicker:
                widget.onAddToPlaylist == null &&
                    widget.onAddToPlaylistTarget == null
                ? null
                : () => unawaited(_openPlaylistPicker()),
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
    required this.onOpenPlaylistPicker,
  });

  final MusicTrackCardData data;
  final bool loading;
  final bool playing;
  final VoidCallback onTogglePreview;
  final VoidCallback? onOpenPlaylistPicker;

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
          ),
        ],
      ),
    );
  }
}

enum _MusicTrackPlaylistTargetFilter { all, personal, room }

Future<MusicTrackPlaylistTarget?> showMusicTrackPlaylistTargetDialog(
  BuildContext context, {
  required Future<List<MusicTrackPlaylistTarget>> Function() loadTargets,
  required Future<void> Function(MusicTrackPlaylistTarget target) onAdd,
}) {
  return showDialog<MusicTrackPlaylistTarget>(
    context: context,
    builder: (context) =>
        _MusicTrackPlaylistTargetDialog(loadTargets: loadTargets, onAdd: onAdd),
  );
}

class _MusicTrackPlaylistTargetDialog extends StatefulWidget {
  const _MusicTrackPlaylistTargetDialog({
    required this.loadTargets,
    required this.onAdd,
  });

  final Future<List<MusicTrackPlaylistTarget>> Function() loadTargets;
  final Future<void> Function(MusicTrackPlaylistTarget target) onAdd;

  @override
  State<_MusicTrackPlaylistTargetDialog> createState() =>
      _MusicTrackPlaylistTargetDialogState();
}

class _MusicTrackPlaylistTargetDialogState
    extends State<_MusicTrackPlaylistTargetDialog> {
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocusNode = FocusNode();
  final ScrollController _scrollController = ScrollController();
  List<MusicTrackPlaylistTarget> _targets = const [];
  _MusicTrackPlaylistTargetFilter _filter = _MusicTrackPlaylistTargetFilter.all;
  String? _selectedKey;
  String? _error;
  bool _loading = true;
  bool _adding = false;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  @override
  void dispose() {
    _searchController.dispose();
    _searchFocusNode.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final targets = await widget.loadTargets();
      if (!mounted) return;
      setState(() {
        _targets = targets;
        _loading = false;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _searchFocusNode.requestFocus();
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = userFacingErrorMessage(error, fallback: '加载歌单失败，请重试');
      });
    }
  }

  List<MusicTrackPlaylistTarget> get _visibleTargets {
    final query = _searchController.text.trim().toLowerCase();
    return _targets
        .where((target) {
          final matchesScope = switch (_filter) {
            _MusicTrackPlaylistTargetFilter.all => true,
            _MusicTrackPlaylistTargetFilter.personal => !target.roomScoped,
            _MusicTrackPlaylistTargetFilter.room => target.roomScoped,
          };
          return matchesScope &&
              (query.isEmpty ||
                  target.playlist.name.toLowerCase().contains(query));
        })
        .toList(growable: false);
  }

  MusicTrackPlaylistTarget? get _selectedTarget {
    for (final target in _targets) {
      if (target.key == _selectedKey) return target;
    }
    return null;
  }

  Future<void> _confirm() async {
    final target = _selectedTarget;
    if (target == null || _adding) return;
    setState(() => _adding = true);
    try {
      await widget.onAdd(target);
      if (mounted) Navigator.of(context).pop(target);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          userFacingErrorMessage(error, fallback: '添加失败，请检查歌单权限'),
        );
      }
    } finally {
      if (mounted) setState(() => _adding = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final visibleTargets = _visibleTargets;
    final height = (MediaQuery.sizeOf(context).height * 0.58).clamp(
      240.0,
      560.0,
    );
    return DialogFrame(
      title: '添加到歌单',
      icon: Icons.playlist_add,
      maxWidth: 620,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: _adding ? null : () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          buttonKey: const ValueKey<String>(
            'music-track-playlist-target-confirm',
          ),
          label: '确认添加',
          icon: Icons.playlist_add,
          tone: ButtonTone.primary,
          loading: _adding,
          onPressed: _selectedTarget == null || _adding ? null : _confirm,
        ),
      ],
      child: SizedBox(
        height: height,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Input(
              key: const ValueKey<String>('music-track-playlist-target-search'),
              controller: _searchController,
              focusNode: _searchFocusNode,
              prefixIcon: Icons.search,
              hintText: '搜索歌单',
              showClearButton: true,
              onChanged: (_) => setState(() {}),
              textInputAction: TextInputAction.search,
            ),
            const SizedBox(height: UiSpacing.sm),
            SegmentedControl<_MusicTrackPlaylistTargetFilter>(
              expanded: true,
              value: _filter,
              onChanged: (value) => setState(() => _filter = value),
              segments: const [
                Segment(
                  value: _MusicTrackPlaylistTargetFilter.all,
                  label: '全部',
                  icon: Icons.library_music_outlined,
                ),
                Segment(
                  value: _MusicTrackPlaylistTargetFilter.personal,
                  label: '我的歌单',
                  icon: Icons.person_outline,
                ),
                Segment(
                  value: _MusicTrackPlaylistTargetFilter.room,
                  label: '房间歌单',
                  icon: Icons.meeting_room_outlined,
                ),
              ],
            ),
            if (_error != null) ...[
              const SizedBox(height: UiSpacing.sm),
              Text(
                _error!,
                style: UiTypography.label.copyWith(color: UiColors.danger),
              ),
            ],
            const SizedBox(height: UiSpacing.sm),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : visibleTargets.isEmpty
                  ? Center(
                      child: Text(
                        _targets.isEmpty ? '还没有可用歌单' : '没有匹配的歌单',
                        style: UiTypography.body.copyWith(
                          color: UiColors.textMuted,
                        ),
                      ),
                    )
                  : RawScrollbar(
                      controller: _scrollController,
                      interactive: true,
                      radius: const Radius.circular(999),
                      thickness: 7,
                      thumbColor: UiColors.textMuted.withValues(alpha: 0.82),
                      child: ListView.separated(
                        controller: _scrollController,
                        primary: false,
                        padding: const EdgeInsets.only(right: 10),
                        itemCount: visibleTargets.length,
                        separatorBuilder: (_, _) =>
                            const SizedBox(height: UiSpacing.sm),
                        itemBuilder: (context, index) {
                          final target = visibleTargets[index];
                          return _MusicTrackPlaylistTargetOption(
                            target: target,
                            selected: target.key == _selectedKey,
                            onPressed: _adding
                                ? null
                                : () =>
                                      setState(() => _selectedKey = target.key),
                          );
                        },
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MusicTrackPlaylistTargetOption extends StatelessWidget {
  const _MusicTrackPlaylistTargetOption({
    required this.target,
    required this.selected,
    required this.onPressed,
  });

  final MusicTrackPlaylistTarget target;
  final bool selected;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: selected,
      onTap: onPressed,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onPressed,
        child: AnimatedContainer(
          key: ValueKey<String>(
            'music-track-playlist-target:'
            '${target.roomScoped ? target.key : target.playlist.id}',
          ),
          duration: const Duration(milliseconds: 90),
          constraints: const BoxConstraints(minHeight: 64),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: selected ? UiColors.selected : UiColors.surfaceLow,
            borderRadius: BorderRadius.circular(UiRadii.md),
            border: Border.all(
              color: selected ? UiColors.selectedBorder : UiColors.border,
            ),
          ),
          child: Row(
            children: [
              Icon(
                target.roomScoped ? Icons.meeting_room : Icons.person,
                color: UiColors.accent,
                size: 20,
              ),
              const SizedBox(width: UiSpacing.md),
              Expanded(
                child: Text(
                  target.playlist.name,
                  style: UiTypography.body.copyWith(
                    color: UiColors.text,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              const SizedBox(width: UiSpacing.md),
              Icon(
                selected ? Icons.radio_button_checked : Icons.radio_button_off,
                color: selected ? UiColors.accent : UiColors.textMuted,
                size: 20,
              ),
            ],
          ),
        ),
      ),
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

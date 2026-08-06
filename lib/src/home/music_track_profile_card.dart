import 'dart:async';

import 'package:flutter/material.dart';

import '../app/error_display.dart';
import '../app/music_box_display.dart' as music_box_display;
import '../app/music_track_preview.dart';
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

/// Song profile card for saved-playlist rows. Preview is local-only: it never
/// changes the room queue or authoritative music-box playback state.
class MusicTrackHoverCard extends StatefulWidget {
  const MusicTrackHoverCard({
    super.key,
    required this.data,
    required this.previewController,
    required this.child,
  });

  final MusicTrackCardData data;
  final MusicTrackPreviewController previewController;
  final Widget child;

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
  });

  final MusicTrackCardData data;
  final bool loading;
  final bool playing;
  final VoidCallback onTogglePreview;

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
          Button(
            key: const ValueKey<String>('music-track-card-preview'),
            tone: playing ? ButtonTone.danger : ButtonTone.primary,
            loading: loading,
            icon: Icon(playing ? Icons.stop_rounded : Icons.play_arrow),
            onPressed: loading ? null : onTogglePreview,
            width: double.infinity,
            child: Text(playing ? '取消试听' : '试听'),
          ),
        ],
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

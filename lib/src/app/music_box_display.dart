import '../protocol/models.dart';

/// Pure presentation logic for the room music box. No Flutter imports: every
/// rendering decision (progress, status text, delete permission, formatting)
/// lives here so it can be unit-tested without a widget tree, mirroring
/// `live_display.dart`.

/// The live playback position. Fully server-authoritative: the client renders
/// the snapshot's [MusicBoxPlayback.positionMs] as-is and never extrapolates
/// locally. The server pushes a fresh snapshot every second (the
/// `music_box_changed` event), so the bar advances purely by re-rendering each
/// snapshot — no client clock is involved, so there's nothing to drift.
class MusicBoxProgress {
  const MusicBoxProgress({required this.positionMs, required this.durationMs});

  final int positionMs;
  final int durationMs;

  /// 0.0–1.0 for a progress bar; 0 when the duration is unknown.
  double get fraction {
    if (durationMs <= 0) return 0;
    final ratio = positionMs / durationMs;
    if (ratio.isNaN) return 0;
    return ratio.clamp(0.0, 1.0);
  }
}

/// The position to render for [state]. Reads the server-reported position
/// straight from the snapshot, floored to whole seconds for a steady
/// per-second display, and clamped to the track duration.
MusicBoxProgress musicBoxProgress(MusicBoxState state) {
  final current = state.currentItem;
  final durationMs = current?.durationMs ?? 0;
  var positionMs = state.playback.positionMs;
  if (positionMs < 0) positionMs = 0;
  // Floor to whole seconds so the bar and the time label move in second steps.
  positionMs -= positionMs % 1000;
  if (durationMs > 0 && positionMs > durationMs) positionMs = durationMs;
  return MusicBoxProgress(positionMs: positionMs, durationMs: durationMs);
}

/// The play/pause toggle the primary transport button should drive, given the
/// current state. `play` resumes from pause, starts a stopped queue, and is a
/// no-op target only when the queue is empty.
enum MusicBoxTransportAction { play, pause, resume }

MusicBoxTransportAction musicBoxPrimaryTransport(MusicBoxState state) {
  return switch (state.playback.state) {
    MusicBoxPlaybackState.playing => MusicBoxTransportAction.pause,
    MusicBoxPlaybackState.paused => MusicBoxTransportAction.resume,
    MusicBoxPlaybackState.stopped => MusicBoxTransportAction.play,
  };
}

/// User-facing label for the room's direct-request queue. The wire protocol
/// intentionally keeps the stable `temporary` identifier for old clients and
/// rolling server deployments, while every UI surface uses the product name.
const String musicBoxRequestQueueLabel = '点歌队列';

/// Stable identity for a concrete catalog track. Sources are protocol enums
/// and therefore case-insensitive; external track ids keep their original case
/// because providers may treat them as case-sensitive.
String musicBoxTrackLinkKey({required String source, required String trackId}) {
  return '${source.trim().toLowerCase()}:${trackId.trim()}';
}

/// Whether [source]/[trackId] already exists in the room's direct-request
/// queue. Saved-playlist snapshots deliberately do not count as requests.
bool musicBoxRequestQueueContainsTrack(
  List<MusicBoxQueueItem> queue, {
  required String source,
  required String trackId,
}) {
  final target = musicBoxTrackLinkKey(source: source, trackId: trackId);
  return queue.any(
    (item) =>
        musicBoxTrackLinkKey(source: item.source, trackId: item.trackId) ==
        target,
  );
}

String musicBoxActiveSourceLabel(MusicBoxActiveSource source) {
  if (source.type == MusicBoxActiveSourceType.temporary) {
    return musicBoxRequestQueueLabel;
  }
  final name = source.name.trim();
  if (name.isNotEmpty) return name;
  return source.type == MusicBoxActiveSourceType.roomPlaylist ? '房间歌单' : '个人歌单';
}

/// Stable source ids stay on the wire; every song surface uses the same
/// user-facing source label. Unknown sources remain visible for forward
/// compatibility instead of being mislabeled as the default source.
String musicBoxSourceLabel(String value) {
  final source = value.trim();
  for (final candidate in musicBoxSources) {
    if (candidate.id == source) return candidate.label;
  }
  return source.isEmpty ? '未知来源' : source;
}

/// Bilibili catalog entries describe their creator as an uploader/author,
/// while music providers expose the same wire field as an artist. Keeping the
/// source-specific wording here makes every song card use the same label.
String musicBoxArtistFieldLabel(String source) {
  return source.trim().toLowerCase() == 'bilibili' ? '作者' : '歌手';
}

String musicBoxTransportApiAction(MusicBoxTransportAction action) {
  return switch (action) {
    MusicBoxTransportAction.play => 'play',
    MusicBoxTransportAction.pause => 'pause',
    MusicBoxTransportAction.resume => 'resume',
  };
}

/// Whether the spinning vinyl should rotate: it spins while playing and freezes
/// otherwise, giving an at-a-glance read of playback.
bool musicBoxRecordSpinning(MusicBoxState state) {
  return state.playback.state == MusicBoxPlaybackState.playing;
}

/// A short status label for a queue item, reflecting its download/transcode
/// lifecycle. Returns null for [MusicBoxQueueItemStatus.ready], which renders
/// normally with no badge.
String? musicBoxQueueStatusLabel(MusicBoxQueueItem item) {
  return switch (item.status) {
    MusicBoxQueueItemStatus.pending => '排队中，等待下载',
    MusicBoxQueueItemStatus.downloading => '下载中',
    MusicBoxQueueItemStatus.failed => musicBoxQueueErrorLabel(item),
    MusicBoxQueueItemStatus.ready => null,
  };
}

/// The error line for a failed item, falling back to a generic message when the
/// server didn't supply one.
String musicBoxQueueErrorLabel(MusicBoxQueueItem item) {
  final error = item.error.trim();
  return error.isEmpty ? '处理失败' : error;
}

/// A spinning vinyl is only meaningful once a track is current; tiles still in
/// the queue show artwork without rotation.
bool musicBoxIsCurrent(MusicBoxState state, MusicBoxQueueItem item) {
  return state.playback.currentItemId == item.id && item.id.isNotEmpty;
}

/// A near-limit hint for the usage meter, or null when there's comfortable
/// headroom. Triggers at 90%. Over the limit, new songs aren't rejected — they
/// queue as `pending` and download once earlier tracks finish playing.
String? musicBoxUsageHint(MusicBoxUsage usage) {
  if (usage.limitBytes <= 0) return null;
  final ratio = usage.usedBytes / usage.limitBytes;
  if (ratio < 0.9) return null;
  if (ratio >= 1.0) return '空间已满，新歌将排队等待下载';
  return '空间已接近上限';
}

double musicBoxUsageFraction(MusicBoxUsage usage) {
  if (usage.limitBytes <= 0) return 0;
  return (usage.usedBytes / usage.limitBytes).clamp(0.0, 1.0);
}

/// Joins a search hit's artist list for display, e.g. `林俊杰、孙燕姿`.
String musicBoxArtistsLabel(List<String> artists) {
  return artists.where((a) => a.trim().isNotEmpty).join('、');
}

/// `mm:ss` (or `h:mm:ss`) for a millisecond duration; em dash when unknown.
String musicBoxFormatDuration(int milliseconds) {
  if (milliseconds <= 0) return '--:--';
  final totalSeconds = milliseconds ~/ 1000;
  final hours = totalSeconds ~/ 3600;
  final minutes = (totalSeconds % 3600) ~/ 60;
  final seconds = totalSeconds % 60;
  final mm = minutes.toString().padLeft(hours > 0 ? 2 : 1, '0');
  final ss = seconds.toString().padLeft(2, '0');
  return hours > 0 ? '$hours:$mm:$ss' : '$mm:$ss';
}

/// Human-readable byte size, e.g. `3.7 MB`.
String musicBoxFormatBytes(int bytes) {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  var value = bytes.toDouble();
  var unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  final text = unit == 0 || value >= 100
      ? value.toStringAsFixed(0)
      : value.toStringAsFixed(1);
  // Drop a trailing `.0` so whole values read `5 MB`, not `5.0 MB`.
  final trimmed = text.endsWith('.0')
      ? text.substring(0, text.length - 2)
      : text;
  return '$trimmed ${units[unit]}';
}

String musicBoxUsageLabel(MusicBoxUsage usage) {
  return '${musicBoxFormatBytes(usage.usedBytes)} / '
      '${musicBoxFormatBytes(usage.limitBytes)}';
}

/// A selectable music search source. netease and bilibili go through the GD
/// music API; tencent routes to the self-hosted QQ Music service on the server.
class MusicBoxSource {
  const MusicBoxSource({required this.id, required this.label});

  /// The `source` value the server routes on.
  final String id;

  /// Short display name for the source picker.
  final String label;
}

/// Temporarily keeps QQ Music out of the client without removing its routing
/// implementation. Re-enable it here once the source is ready for users again.
const bool musicBoxTencentSourceEnabled = false;

/// The default source id (netease).
const String musicBoxDefaultSource = 'netease';

/// The sources offered in the search picker, in display order.
const List<MusicBoxSource> musicBoxSources = [
  MusicBoxSource(id: 'netease', label: '网易云'),
  MusicBoxSource(id: 'bilibili', label: '哔哩哔哩'),
  if (musicBoxTencentSourceEnabled)
    MusicBoxSource(id: 'tencent', label: 'QQ音乐'),
];

bool musicBoxSourceEnabled(String source) {
  return musicBoxSources.any((candidate) => candidate.id == source);
}

String normalizedMusicBoxSource(String source) {
  return musicBoxSourceEnabled(source) ? source : musicBoxDefaultSource;
}

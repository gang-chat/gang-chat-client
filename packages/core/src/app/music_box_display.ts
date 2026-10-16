import {
  MusicBoxActiveSourceType,
  MusicBoxPlaybackState,
  MusicBoxQueueItemStatus,
  musicBoxActiveSourceTypeValue,
} from '../protocol/models';
import type { MusicBoxActiveSource, MusicBoxQueueItem, MusicBoxState, MusicBoxUsage } from '../protocol/models';

/// Pure presentation logic for the room music box. No UI imports: every
/// rendering decision (progress, status text, delete permission, formatting)
/// lives here so it can be unit-tested without a widget tree, mirroring
/// `live_display.ts`.

/// The live playback position. Fully server-authoritative: the client renders
/// the snapshot's [MusicBoxPlayback.positionMs] as-is and never extrapolates
/// locally. The server pushes a fresh snapshot every second (the
/// `music_box_changed` event), so the bar advances purely by re-rendering each
/// snapshot — no client clock is involved, so there's nothing to drift.
export class MusicBoxProgress {
  constructor(opts: { positionMs: number; durationMs: number }) {
    this.positionMs = opts.positionMs;
    this.durationMs = opts.durationMs;
  }

  readonly positionMs: number;
  readonly durationMs: number;

  /// 0.0–1.0 for a progress bar; 0 when the duration is unknown.
  get fraction(): number {
    if (this.durationMs <= 0) return 0;
    const ratio = this.positionMs / this.durationMs;
    if (Number.isNaN(ratio)) return 0;
    return Math.min(Math.max(ratio, 0.0), 1.0);
  }
}

/// The position to render for [state]. Reads the server-reported position
/// straight from the snapshot, floored to whole seconds for a steady
/// per-second display, and clamped to the track duration.
export function musicBoxProgress(state: MusicBoxState): MusicBoxProgress {
  const current = state.currentItem;
  const durationMs = current?.durationMs ?? 0;
  let positionMs = state.playback.positionMs;
  if (positionMs < 0) positionMs = 0;
  // Floor to whole seconds so the bar and the time label move in second steps.
  positionMs -= positionMs % 1000;
  if (durationMs > 0 && positionMs > durationMs) positionMs = durationMs;
  return new MusicBoxProgress({ positionMs, durationMs });
}

/// The play/pause toggle the primary transport button should drive, given the
/// current state. `play` resumes from pause, starts a stopped queue, and is a
/// no-op target only when the queue is empty.
export enum MusicBoxTransportAction {
  play = 'play',
  pause = 'pause',
  resume = 'resume',
}

export function musicBoxPrimaryTransport(state: MusicBoxState): MusicBoxTransportAction {
  switch (state.playback.state) {
    case MusicBoxPlaybackState.playing:
      return MusicBoxTransportAction.pause;
    case MusicBoxPlaybackState.paused:
      return MusicBoxTransportAction.resume;
    case MusicBoxPlaybackState.stopped:
      return MusicBoxTransportAction.play;
  }
}

/// User-facing label for the room's direct-request queue. The wire protocol
/// intentionally keeps the stable `temporary` identifier for old clients and
/// rolling server deployments, while every UI surface uses the product name.
export const musicBoxRequestQueueLabel = '点歌队列';

/// The sections of the flat music-box list, in display order: the request
/// queue (always open) and the two saved-playlist groups (collapsed until
/// opened). "What is playing" is marked on the section that owns the active
/// source; "what is open" is the user's choice and realtime never changes it.
export enum MusicBoxSection {
  queue = 'queue',
  roomPlaylists = 'roomPlaylists',
  myPlaylists = 'myPlaylists',
}

/// The section that lists [type]. A personal playlist owned by someone else
/// is not listable by the viewer, but it still lives under 我的歌单 so the
/// now-playing chip has a deterministic home for its snapshot view.
export function musicBoxSectionForSource(type: MusicBoxActiveSourceType): MusicBoxSection {
  switch (type) {
    case MusicBoxActiveSourceType.temporary:
      return MusicBoxSection.queue;
    case MusicBoxActiveSourceType.roomPlaylist:
      return MusicBoxSection.roomPlaylists;
    case MusicBoxActiveSourceType.userPlaylist:
      return MusicBoxSection.myPlaylists;
  }
}

/// Stable key for one saved playlist inside the flat list.
export function musicBoxPlaylistKey(type: MusicBoxActiveSourceType, id: string): string {
  return `${musicBoxActiveSourceTypeValue(type)}:${id}`;
}

/// Whether the saved playlist [type]/[id] (or the request queue when [type]
/// is temporary) is the room's active source. Activation is server-owned, so
/// this is a pure comparison against the authoritative snapshot.
export function musicBoxSourceIsActive(state: MusicBoxState, type: MusicBoxActiveSourceType, id = ''): boolean {
  const active = state.activeSource;
  if (type === MusicBoxActiveSourceType.temporary) {
    return active.type === MusicBoxActiveSourceType.temporary;
  }
  return active.type === type && active.id === id;
}

/// Whether a saved-playlist template row describes the track the room is
/// currently playing. Template item ids differ from the activation snapshot's
/// queue item ids, so the match is by catalog identity instead.
export function musicBoxTrackIsCurrent(state: MusicBoxState, opts: { source: string; trackId: string }): boolean {
  const current = state.currentItem;
  if (current == null) return false;
  return (
    musicBoxTrackLinkKey({ source: current.source, trackId: current.trackId }) ===
    musicBoxTrackLinkKey({ source: opts.source, trackId: opts.trackId })
  );
}

/// Stable identity for a concrete catalog track. Sources are protocol enums
/// and therefore case-insensitive; external track ids keep their original case
/// because providers may treat them as case-sensitive.
export function musicBoxTrackLinkKey(opts: { source: string; trackId: string }): string {
  return `${opts.source.trim().toLowerCase()}:${opts.trackId.trim()}`;
}

/// Whether [source]/[trackId] already exists in the room's direct-request
/// queue. Saved-playlist snapshots deliberately do not count as requests.
export function musicBoxRequestQueueContainsTrack(
  queue: readonly MusicBoxQueueItem[],
  opts: { source: string; trackId: string },
): boolean {
  const target = musicBoxTrackLinkKey(opts);
  return queue.some((item) => musicBoxTrackLinkKey({ source: item.source, trackId: item.trackId }) === target);
}

export function musicBoxActiveSourceLabel(source: MusicBoxActiveSource): string {
  if (source.type === MusicBoxActiveSourceType.temporary) {
    return musicBoxRequestQueueLabel;
  }
  const name = source.name.trim();
  if (name.length > 0) return name;
  return source.type === MusicBoxActiveSourceType.roomPlaylist ? '房间歌单' : '个人歌单';
}

/// Stable source ids stay on the wire; every song surface uses the same
/// user-facing source label. Unknown sources remain visible for forward
/// compatibility instead of being mislabeled as the default source.
export function musicBoxSourceLabel(value: string): string {
  const source = value.trim();
  for (const candidate of musicBoxSources) {
    if (candidate.id === source) return candidate.label;
  }
  return source.length === 0 ? '未知来源' : source;
}

/// Bilibili catalog entries describe their creator as an uploader/author,
/// while music providers expose the same wire field as an artist. Keeping the
/// source-specific wording here makes every song card use the same label.
export function musicBoxArtistFieldLabel(source: string): string {
  return source.trim().toLowerCase() === 'bilibili' ? '作者' : '歌手';
}

export function musicBoxTransportApiAction(action: MusicBoxTransportAction): string {
  switch (action) {
    case MusicBoxTransportAction.play:
      return 'play';
    case MusicBoxTransportAction.pause:
      return 'pause';
    case MusicBoxTransportAction.resume:
      return 'resume';
  }
}

/// Whether the spinning vinyl should rotate: it spins while playing and freezes
/// otherwise, giving an at-a-glance read of playback.
export function musicBoxRecordSpinning(state: MusicBoxState): boolean {
  return state.playback.state === MusicBoxPlaybackState.playing;
}

/// Whether the room currently has a resumable playback context.
///
/// [MusicBoxActiveSource] is retained after playback stops so the user can keep
/// browsing the last source. That source is not "playing" until the server also
/// reports a current item and a playing/paused transport state.
export function musicBoxHasActivePlayback(state: MusicBoxState): boolean {
  return state.currentItem != null && state.playback.state !== MusicBoxPlaybackState.stopped;
}

/// A short status label for a queue item, reflecting its download/transcode
/// lifecycle. Returns null for [MusicBoxQueueItemStatus.ready], which renders
/// normally with no badge.
export function musicBoxQueueStatusLabel(item: MusicBoxQueueItem): string | null {
  switch (item.status) {
    case MusicBoxQueueItemStatus.pending:
      return '排队中，等待下载';
    case MusicBoxQueueItemStatus.downloading:
      return '下载中';
    case MusicBoxQueueItemStatus.failed:
      return musicBoxQueueErrorLabel(item);
    case MusicBoxQueueItemStatus.ready:
      return null;
  }
}

/// The error line for a failed item, falling back to a generic message when the
/// server didn't supply one.
export function musicBoxQueueErrorLabel(item: MusicBoxQueueItem): string {
  const error = item.error.trim();
  return error.length === 0 ? '处理失败' : error;
}

/// A spinning vinyl is only meaningful once a track is current; tiles still in
/// the queue show artwork without rotation.
export function musicBoxIsCurrent(state: MusicBoxState, item: MusicBoxQueueItem): boolean {
  return state.playback.currentItemId === item.id && item.id.length > 0;
}

/// A near-limit hint for the usage meter, or null when there's comfortable
/// headroom. Triggers at 90%. Over the limit, new songs aren't rejected — they
/// queue as `pending` and download once earlier tracks finish playing.
export function musicBoxUsageHint(usage: MusicBoxUsage): string | null {
  if (usage.limitBytes <= 0) return null;
  const ratio = usage.usedBytes / usage.limitBytes;
  if (ratio < 0.9) return null;
  if (ratio >= 1.0) return '空间已满，新歌将排队等待下载';
  return '空间已接近上限';
}

export function musicBoxUsageFraction(usage: MusicBoxUsage): number {
  if (usage.limitBytes <= 0) return 0;
  return Math.min(Math.max(usage.usedBytes / usage.limitBytes, 0.0), 1.0);
}

/// Joins a search hit's artist list for display, e.g. `林俊杰、孙燕姿`.
export function musicBoxArtistsLabel(artists: readonly string[]): string {
  return artists.filter((a) => a.trim().length > 0).join('、');
}

/// `mm:ss` (or `h:mm:ss`) for a millisecond duration; em dash when unknown.
export function musicBoxFormatDuration(milliseconds: number): string {
  if (milliseconds <= 0) return '--:--';
  const totalSeconds = Math.trunc(milliseconds / 1000);
  const hours = Math.trunc(totalSeconds / 3600);
  const minutes = Math.trunc((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const mm = String(minutes).padStart(hours > 0 ? 2 : 1, '0');
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

/// Human-readable byte size, e.g. `3.7 MB`.
export function musicBoxFormatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  const text = unit === 0 || value >= 100 ? value.toFixed(0) : value.toFixed(1);
  // Drop a trailing `.0` so whole values read `5 MB`, not `5.0 MB`.
  const trimmed = text.endsWith('.0') ? text.substring(0, text.length - 2) : text;
  return `${trimmed} ${units[unit]}`;
}

export function musicBoxUsageLabel(usage: MusicBoxUsage): string {
  return `${musicBoxFormatBytes(usage.usedBytes)} / ${musicBoxFormatBytes(usage.limitBytes)}`;
}

/// A selectable music search source. netease and bilibili go through the GD
/// music API; tencent routes to the self-hosted QQ Music service on the server.
export class MusicBoxSource {
  constructor(opts: { id: string; label: string }) {
    this.id = opts.id;
    this.label = opts.label;
  }

  /// The `source` value the server routes on.
  readonly id: string;

  /// Short display name for the source picker.
  readonly label: string;
}

/// Temporarily keeps QQ Music out of the client without removing its routing
/// implementation. Re-enable it here once the source is ready for users again.
export const musicBoxTencentSourceEnabled: boolean = false;

/// The default source id (netease).
export const musicBoxDefaultSource = 'netease';

/// The sources offered in the search picker, in display order.
export const musicBoxSources: readonly MusicBoxSource[] = [
  new MusicBoxSource({ id: 'netease', label: '网易云' }),
  new MusicBoxSource({ id: 'bilibili', label: '哔哩哔哩' }),
  ...(musicBoxTencentSourceEnabled ? [new MusicBoxSource({ id: 'tencent', label: 'QQ音乐' })] : []),
];

export function musicBoxSourceEnabled(source: string): boolean {
  return musicBoxSources.some((candidate) => candidate.id === source);
}

export function normalizedMusicBoxSource(source: string): string {
  return musicBoxSourceEnabled(source) ? source : musicBoxDefaultSource;
}

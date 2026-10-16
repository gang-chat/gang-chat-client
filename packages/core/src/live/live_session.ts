/**
 * Port of `lib/src/live/live_session.dart` onto livekit-client (the LiveKit
 * JavaScript SDK). `@livekit/react-native` wraps the same SDK, so this file
 * is shared with a future React Native app; the browser-only audio pieces
 * live behind `./live_audio_platform`.
 *
 * Flutter SDK -> livekit-client mapping used throughout:
 *  - `lk.Room(roomOptions:)` / `room.events.listen` -> `new Room(options)` /
 *    `room.on(RoomEvent.*)`, adapted into [LiveRoomEvent]s (see
 *    [_coalesceLiveRoomEventBatch] for the reconnect ordering difference);
 *  - `RoomResumingEvent` -> `RoomEvent.SignalReconnecting`,
 *    `RoomReconnectingEvent` -> `RoomEvent.Reconnecting`,
 *    `RoomReconnectedEvent` -> `RoomEvent.Reconnected`;
 *  - `getTrackPublicationBySource` -> `getTrackPublication`, `pub.muted` ->
 *    `pub.isMuted`, `pub.subscribed` -> `pub.isSubscribed`,
 *    `pub.subscribe()/unsubscribe()` -> `pub.setSubscribed(true/false)`;
 *  - `TrackSource.screenShareVideo` -> `Track.Source.ScreenShare`;
 *  - `track.setVolume` -> `RemoteAudioTrack.setVolume` (the room uses
 *    `webAudioMix` so per-user volume above 100% works);
 *  - `rtc.Helper.setLocalAudioInputVolume` -> a Web Audio gain processor on
 *    the published microphone track (`LiveInputGain`);
 *  - `rtc.Helper.selectAudioOutput` -> `room.switchActiveDevice('audiooutput')`.
 */
import {
  ConnectionState,
  DisconnectReason,
  LocalAudioTrack,
  LocalVideoTrack,
  RemoteAudioTrack,
  Room,
  RoomEvent,
  Track,
  facingModeFromLocalTrack,
  isVideoTrack,
  type AudioCaptureOptions,
  type LocalParticipant,
  type Participant,
  type RemoteParticipant,
  type RemoteTrack,
  type RemoteTrackPublication,
  type RoomEventCallbacks,
  type RoomOptions,
  type TrackPublication,
  type TrackPublishOptions,
  type VideoEncoding,
  type VideoSenderStats,
  type VideoTrack,
} from 'livekit-client';
import { Completer, delay, unawaited, withTimeout, type Milliseconds } from '../lib/async';
import { ChangeNotifier } from '../lib/change_notifier';
import { StreamController, type EventStream, type StreamSubscription } from '../lib/event_stream';
import { HostPlatform } from '../config/host_platform';
import { hasPlatform, platform } from '../platform/platform';
import { defaultAudioVolume, type AudioDeviceStore } from '../app/audio_device_store';
import {
  defaultParticipantVoiceVolume,
  normalizedAudioVolume,
  normalizedParticipantVoiceVolume,
} from '../app/audio_levels';
import { musicBoxBotIdentity } from '../protocol/models';
import { AudioDeviceRebinder, preferredLiveAudioDevices } from './audio_device_rebinder';
import { LiveAudioDeviceService } from './audio_device_service';
import type { AudioInputRebinder } from './audio_input_rebinder';
import type { AudioOutputRebinder } from './audio_output_rebinder';
import { ensureCameraAccess } from './camera_access';
import { CameraDeviceReconciler } from './camera_device_reconciler';
import {
  createLiveInputGain,
  createLiveRemoteAudioRenderer,
  type LiveInputGain,
  type LiveRemoteAudioRenderer,
} from './live_audio_platform';
import { LiveHardware, defaultMediaDevices } from './live_hardware';
import { ScreenAudioPublisher, type ScreenAudioTokenProvider } from './screen_audio_publisher';
import {
  ScreenShareQuality,
  ScreenShareQualityCheckResult,
  ScreenShareResolution,
  androidScreenShareFrameRateCap,
  defaultScreenShareFrameRate,
  defaultScreenShareMaxHeight,
  desktopScreenShareFrameRateCap,
  normalizedScreenShareFrameRate,
  normalizedScreenShareMaxHeight,
  screenShareCappedOutputResolution,
  screenShareQualityCheckResult,
  screenShareQualityForHeight,
  screenShareScaleDownBy,
  screenShareSourceScaleForSample,
} from './screen_share_quality';
import { SystemAudioDevices } from './system_audio_devices';

export type CameraVideoInputCountProvider = () => Promise<number>;

async function _defaultCameraVideoInputCountProvider(): Promise<number> {
  const mediaDevices = defaultMediaDevices();
  if (mediaDevices == null) return 0;
  const devices = await mediaDevices.enumerateDevices();
  return new Set(
    devices
      .filter((device) => device.kind === 'videoinput')
      .map((device) => device.deviceId)
      .filter((deviceId) => deviceId.length > 0),
  ).size;
}

/**
 * Resolves an Android camera-device probe without treating an unavailable
 * pre-permission list as confirmed single-camera hardware.
 */
export function cameraFlipAvailabilityFromVideoInputCount(inputCount: number): boolean | null {
  if (inputCount <= 0) return null;
  return inputCount > 1;
}

/**
 * Suffix of the hidden screen-audio aux participant identity
 * (`<ownerId>--screen-audio`). These participants publish screen-share audio
 * from a separate LiveKit room connection and must be filtered out of the UI
 * (they are never real users). On the owner's own receiver, their audio must
 * also be muted to avoid self-echo. The `--` separator is URL-safe (unlike
 * `#`, which is truncated as a URL fragment in the LiveKit WebSocket signal
 * path, causing the aux identity to collide with the main room participant).
 */
const _screenAudioAuxSuffix = '--screen-audio';

/** Whether [identity] is a hidden screen-audio aux participant. */
function _isScreenAudioAux(identity: string): boolean {
  return identity.endsWith(_screenAudioAuxSuffix);
}

/**
 * Whether [identity] is the LOCAL user's own screen-audio aux participant
 * (the one whose audio would echo back if played). [localIdentity] is the
 * main room's local participant identity (== userID).
 */
function _isOwnScreenAudioAux(identity: string, localIdentity: string | null | undefined): boolean {
  if (localIdentity == null || localIdentity.length === 0) return false;
  return identity === `${localIdentity}${_screenAudioAuxSuffix}`;
}

export function isLivePresenceSoundParticipantIdentity(identity: string): boolean {
  return identity.length > 0 && !_isScreenAudioAux(identity) && identity !== musicBoxBotIdentity;
}

/**
 * Port of flutter_webrtc's `AndroidAudioConfiguration` (enum values by name).
 *
 * PORT: only meaningful on Android, which stays on Flutter for now; a React
 * Native app would hand these to `@livekit/react-native`'s `AudioSession`.
 */
export interface AndroidLiveAudioConfiguration {
  manageAudioFocus: boolean;
  androidAudioMode: 'normal' | 'inCommunication';
  androidAudioFocusMode: 'gain';
  androidAudioStreamType: 'music' | 'voiceCall';
  androidAudioAttributesUsageType: 'media' | 'voiceCommunication';
  androidAudioAttributesContentType: 'music' | 'speech';
  forceHandleAudioRouting: boolean;
}

/**
 * Android WebRTC uses one native playback device for the complete room mix.
 * The device is created with media audio attributes during app startup. Use
 * normal/media mode while the music-box track is present so Android binds the
 * actual playback and hardware volume keys to media volume, then restore the
 * communication preset when the track disappears. Software WebRTC AEC stays
 * enabled in both modes.
 */
export function androidLiveAudioConfiguration(opts: { musicBoxActive: boolean }): AndroidLiveAudioConfiguration {
  if (!opts.musicBoxActive) {
    // flutter_webrtc `AndroidAudioConfiguration.communication`.
    return {
      manageAudioFocus: true,
      androidAudioMode: 'inCommunication',
      androidAudioFocusMode: 'gain',
      androidAudioStreamType: 'voiceCall',
      androidAudioAttributesUsageType: 'voiceCommunication',
      androidAudioAttributesContentType: 'speech',
      forceHandleAudioRouting: true,
    };
  }
  return {
    manageAudioFocus: true,
    androidAudioMode: 'normal',
    androidAudioFocusMode: 'gain',
    androidAudioStreamType: 'music',
    androidAudioAttributesUsageType: 'media',
    androidAudioAttributesContentType: 'music',
    forceHandleAudioRouting: true,
  };
}

export class LiveReconnectPresenceChanges {
  constructor(opts: { joined: ReadonlySet<string>; left: ReadonlySet<string> }) {
    this.joined = opts.joined;
    this.left = opts.left;
  }

  readonly joined: ReadonlySet<string>;
  readonly left: ReadonlySet<string>;
}

/**
 * Computes real presence changes which happened while LiveKit callbacks were
 * suppressed during a reconnect. A full transport restart removes and then
 * recreates every remote participant, so replaying those raw SDK events would
 * produce false leave/join sounds for users who never left the room.
 */
export function liveReconnectPresenceChanges(opts: {
  before: ReadonlySet<string>;
  after: ReadonlySet<string>;
}): LiveReconnectPresenceChanges {
  const { before, after } = opts;
  return new LiveReconnectPresenceChanges({
    joined: new Set([...after].filter((identity) => !before.has(identity))),
    left: new Set([...before].filter((identity) => !after.has(identity))),
  });
}

/**
 * A publication object can survive signal recovery even when its underlying
 * publisher SDP has fallen back to `recvonly`. In that state mute/unmute is
 * insufficient because it never creates a new sender or renegotiates media.
 */
export function shouldRepublishLocalMicrophoneAfterReconnect(opts: {
  resumedSignalConnection: boolean;
  hadPublicationBeforeReconnect: boolean;
  canPublish: boolean;
}): boolean {
  return opts.resumedSignalConnection && opts.hadPublicationBeforeReconnect && opts.canPublish;
}

const _liveReconnectMicrophoneRetryDelays: readonly Milliseconds[] = [0, 400, 1200];

/**
 * Retries a reconnect-only microphone publication after the LiveKit
 * publisher transport has reported itself connected.
 *
 * WebRTC teardown can finish slightly after the reconnected event. A first
 * publication attempt may therefore race a closing sender even though the
 * subscriber path is already usable. Keep retry policy independent from the
 * platform implementation so every platform recovers with identical bounded
 * semantics.
 */
export async function retryLiveReconnectMicrophoneRecovery(opts: {
  attempt: () => Promise<boolean>;
  isCurrent: () => boolean;
  retryDelays?: readonly Milliseconds[];
  wait?: (duration: Milliseconds) => Promise<void>;
}): Promise<boolean> {
  const { attempt, isCurrent, retryDelays = _liveReconnectMicrophoneRetryDelays } = opts;
  const waitFor = opts.wait ?? delay;
  for (const retryDelay of retryDelays) {
    if (!isCurrent()) return false;
    if (retryDelay > 0) await waitFor(retryDelay);
    if (!isCurrent()) return false;
    try {
      if (await attempt()) return true;
    } catch {
      // The caller owns diagnostics and the final unavailable-state update.
    }
  }
  return false;
}

export enum LiveParticipantDepartureKind {
  left = 'left',
  removed = 'removed',
}

export function liveParticipantDepartureKind(reason: DisconnectReason | null | undefined): LiveParticipantDepartureKind {
  return reason === DisconnectReason.PARTICIPANT_REMOVED
    ? LiveParticipantDepartureKind.removed
    : LiveParticipantDepartureKind.left;
}

/**
 * Whether a room disconnect is an admin removal (kick or room deletion).
 *
 * The Flutter SDK folded the protocol's `ROOM_CLOSED` into `roomDeleted`;
 * livekit-client reports the protocol value directly, so both count here.
 */
export function _isForcedRemovalDisconnectReason(reason: DisconnectReason | null | undefined): boolean {
  return (
    reason === DisconnectReason.PARTICIPANT_REMOVED ||
    reason === DisconnectReason.ROOM_DELETED ||
    reason === DisconnectReason.ROOM_CLOSED
  );
}

/**
 * Whether a room disconnect is a recoverable failure the app should heal with
 * a fresh join. Our own leave and a duplicate-identity takeover by another
 * device are not.
 */
export function _shouldRequestReconnectForDisconnectReason(reason: DisconnectReason | null | undefined): boolean {
  if (_isForcedRemovalDisconnectReason(reason)) return false;
  return reason !== DisconnectReason.CLIENT_INITIATED && reason !== DisconnectReason.DUPLICATE_IDENTITY;
}

/** The participant surface the event handlers read (real SDK participants satisfy it). */
export interface LiveParticipantLike {
  readonly identity: string;
  getTrackPublication(source: Track.Source): { readonly isMuted: boolean } | undefined;
}

/**
 * Returns LiveKit's microphone mute state only after a publication exists.
 *
 * During reconnect LiveKit can restore the participant before restoring its
 * microphone publication. Treating a missing publication as muted would
 * temporarily override the app server's authoritative logical mute state.
 */
export function liveParticipantMicPublicationMuted(participant: Pick<LiveParticipantLike, 'getTrackPublication'>): boolean | null {
  return participant.getTrackPublication(Track.Source.Microphone)?.isMuted ?? null;
}

/**
 * Serializes remote-video subscription reconciliation and makes newer
 * selections supersede work that has not started yet.
 *
 * LiveKit's subscribe/unsubscribe methods only enqueue signal messages; they
 * do not wait for the server to confirm the resulting state. Keeping each
 * media kind on a single ordered tail ensures that a stale unsubscribe cannot
 * be sent after the user's newer selection.
 */
export class LatestLiveSubscriptionReconciler {
  private _tail: Promise<void> = Promise.resolve();
  private _revision = 0;

  schedule(reconcile: (isCurrent: () => boolean) => Promise<void>): Promise<void> {
    const revision = ++this._revision;
    const previous = this._tail;
    const next = (async () => {
      try {
        await previous;
      } catch {
        // Ordering only.
      }
      if (revision !== this._revision) return;
      await reconcile(() => revision === this._revision);
    })();
    this._tail = next.catch(() => {});
    return next;
  }

  invalidate(): void {
    this._revision += 1;
  }
}

export function shouldApplyLivePublishPermissionUpdate(opts: {
  currentCanPublish: boolean;
  oldCanPublish: boolean;
  nextCanPublish: boolean;
}): boolean {
  const { currentCanPublish, oldCanPublish, nextCanPublish } = opts;
  if (oldCanPublish === nextCanPublish) return false;
  return currentCanPublish !== nextCanPublish;
}

function _screenShareAudioOwnerIdentity(identity: string): string {
  if (!_isScreenAudioAux(identity)) return identity;
  return identity.substring(0, identity.length - _screenAudioAuxSuffix.length);
}

/**
 * A capturable desktop source (a whole screen or a single window), used to
 * populate the screen-share picker. Wraps the host's capture source so the UI
 * doesn't depend on the platform types directly.
 *
 * PORT: [thumbnail] (and every [thumbnailUpdates] event) is now a PNG data
 * URL string from Electron's `desktopCapturer`, not raw `Uint8List` bytes;
 * [thumbnailDataUrl] is an alias that names the new type.
 */
export class ScreenSource {
  constructor(opts: {
    id: string;
    name: string;
    thumbnail: string | null;
    isWindow: boolean;
    thumbnailKey?: string | null;
    thumbnailUpdates?: EventStream<string> | null;
  }) {
    this.id = opts.id;
    this.name = opts.name;
    this.thumbnail = opts.thumbnail;
    this.isWindow = opts.isWindow;
    this.thumbnailKey = opts.thumbnailKey ?? opts.id;
    this.thumbnailUpdates = opts.thumbnailUpdates ?? null;
  }

  readonly id: string;
  readonly name: string;
  readonly thumbnail: string | null;
  readonly isWindow: boolean;
  readonly thumbnailKey: string;
  readonly thumbnailUpdates: EventStream<string> | null;

  get thumbnailDataUrl(): string | null {
    return this.thumbnail;
  }
}

interface _ScreenSourceThumbnailUpdate {
  sourceId: string;
  thumbnail: string;
}

const _screenSourceWhitespacePattern = /\s+/g;
const _screenSourceThumbnailCache = new Map<string, string>();
const _screenSourceThumbnailUpdateController = new StreamController<_ScreenSourceThumbnailUpdate>();

const _ignoredShareWindowNameParts: readonly string[] = ['nvidia geforce overlay', 'nvidia overlay', 'geforce overlay'];

/**
 * PORT: Dart top-level getters become functions (`supportsDesktopScreenShare`
 * -> `supportsDesktopScreenShare()`).
 */
export function supportsDesktopScreenShare(): boolean {
  return !HostPlatform.isWeb && (HostPlatform.isWindows || HostPlatform.isMacOS || HostPlatform.isLinux);
}

export function supportsLocalScreenShare(): boolean {
  return supportsDesktopScreenShare() || (!HostPlatform.isWeb && HostPlatform.isAndroid) || supportsBrowserScreenShare();
}

/**
 * Desktop browsers share through their own getDisplayMedia picker. Mobile
 * browsers can't share a screen.
 */
export function supportsBrowserScreenShare(): boolean {
  return HostPlatform.isWeb && !(hasPlatform() && platform().isMobile);
}

export function shouldRequestScreenShareAudio(opts: {
  sourceId: string | null;
  isDesktopSourcePickerPlatform: boolean;
  isWindowsDesktop: boolean;
  isAndroidPlatform: boolean;
}): boolean {
  // Screen audio is published by ScreenAudioPublisher through a separate aux
  // participant. getDisplayMedia still receives audio so platform capture can
  // be configured, but the main room publishes the video only.
  return opts.isDesktopSourcePickerPlatform || opts.isAndroidPlatform;
}

export function filterScreenSourcesForPicker(sources: Iterable<ScreenSource>): ScreenSource[] {
  const visible: ScreenSource[] = [];
  const seenIds = new Set<string>();

  for (const source of sources) {
    const normalizedName = _normalizeScreenSourceName(source.name);
    if (source.isWindow) {
      if (_isIgnoredShareWindowName(normalizedName)) continue;
    }
    if (seenIds.has(source.thumbnailKey)) continue;
    seenIds.add(source.thumbnailKey);
    visible.push(source);
  }

  return visible;
}

function _normalizeScreenSourceName(name: string): string {
  return name.trim().toLowerCase().replace(_screenSourceWhitespacePattern, ' ');
}

function _isIgnoredShareWindowName(normalizedName: string): boolean {
  return _ignoredShareWindowNameParts.some((part) => normalizedName.includes(part));
}

function _thumbnailKeyForSource(source: { id: string; isWindow: boolean }): string {
  const type = source.isWindow ? 'window' : 'screen';
  return `${type}:${source.id}`;
}

function _rememberScreenSourceThumbnail(opts: { sourceId: string; thumbnail: string | null }): string | null {
  const { sourceId, thumbnail } = opts;
  if (thumbnail != null && thumbnail.length > 0) {
    _screenSourceThumbnailCache.set(sourceId, thumbnail);
    return thumbnail;
  }
  return _screenSourceThumbnailCache.get(sourceId) ?? null;
}

function _rememberAndEmitScreenSourceThumbnail(opts: { sourceId: string; thumbnail: string | null }): void {
  const remembered = _rememberScreenSourceThumbnail(opts);
  if (remembered == null || remembered.length === 0) return;
  _screenSourceThumbnailUpdateController.add({ sourceId: opts.sourceId, thumbnail: remembered });
}

function _cacheScreenSourceThumbnailUpdates(sourceId: string, updates: EventStream<string>): EventStream<string> {
  return {
    listen(onData, options) {
      return updates.listen(
        (thumbnail) => {
          _rememberAndEmitScreenSourceThumbnail({ sourceId, thumbnail });
          onData(thumbnail);
        },
        options,
      );
    },
  };
}

function _screenSourceThumbnailUpdates(sourceId: string): EventStream<string> {
  return {
    listen(onData, options) {
      return _screenSourceThumbnailUpdateController.stream.listen((update) => {
        if (update.sourceId === sourceId) onData(update.thumbnail);
      }, options);
    },
  };
}

export function cachedScreenSourceThumbnailForTest(sourceId: string): string | null {
  return _screenSourceThumbnailCache.get(sourceId) ?? null;
}

export function resetScreenSourceThumbnailCacheForTest(): void {
  _screenSourceThumbnailCache.clear();
}

export function cacheScreenSourceThumbnailUpdatesForTest(sourceId: string, updates: EventStream<string>): EventStream<string> {
  return _cacheScreenSourceThumbnailUpdates(sourceId, updates);
}

export function screenSourceThumbnailUpdatesForTest(sourceId: string): EventStream<string> {
  return _screenSourceThumbnailUpdates(sourceId);
}

/**
 * A single video track currently published in the live room, tagged with
 * whose it is and whether it's a camera or a screen share. The UI uses these
 * to render participant video tiles.
 *
 * PORT: [track] is a livekit-client `VideoTrack`
 * (`LocalVideoTrack | RemoteVideoTrack`); render it with
 * `track.attach(videoElement)`.
 */
export class LiveVideoTrack {
  constructor(opts: { identity: string; track: VideoTrack; isScreenShare: boolean; isLocal: boolean }) {
    this.identity = opts.identity;
    this.track = opts.track;
    this.isScreenShare = opts.isScreenShare;
    this.isLocal = opts.isLocal;
  }

  readonly identity: string;
  readonly track: VideoTrack;
  readonly isScreenShare: boolean;
  readonly isLocal: boolean;
}

/** Track lifecycle events that rebuild the rendered track list. */
export type LiveTrackLifecycleEvent =
  | 'trackSubscribed'
  | 'trackUnsubscribed'
  | 'localTrackPublished'
  | 'localTrackUnpublished'
  | 'trackPublished'
  | 'trackUnpublished'
  | 'trackMuted'
  | 'trackUnmuted';

/**
 * The room events [LiveSession] reacts to — the TypeScript stand-in for the
 * Flutter SDK's `lk.RoomEvent` subclasses, built from livekit-client
 * `RoomEvent` callbacks (and fed directly by [LiveSession.debugHandleRoomEvent]
 * in tests).
 *
 *  - `reconnecting`: `RoomReconnectingEvent` (full transport restart).
 *    [restartDepartures] lists remote identities livekit-client unwound just
 *    before announcing the restart (see [_coalesceLiveRoomEventBatch]).
 *  - `resuming`: `RoomResumingEvent` (signal resume).
 *  - `participantPermissionsUpdated`: `ParticipantPermissionsUpdatedEvent`,
 *    reduced to the publish flag the session reads.
 */
export type LiveRoomEvent =
  | { type: 'reconnecting'; restartDepartures?: readonly string[] }
  | { type: 'resuming' }
  | { type: 'reconnected' }
  | { type: 'activeSpeakersChanged'; speakers: readonly { readonly identity: string }[] }
  | { type: 'participantConnected'; participant: LiveParticipantLike }
  | { type: 'participantDisconnected'; participant: LiveParticipantLike; reason?: DisconnectReason | null }
  | {
      type: 'participantPermissionsUpdated';
      participant: { readonly identity: string };
      canPublish: boolean;
      oldCanPublish: boolean;
    }
  | { type: 'localTrackSubscribed'; trackSid: string }
  | { type: 'trackLifecycle'; event: LiveTrackLifecycleEvent; participantIdentity: string; isLocal: boolean }
  | { type: 'disconnected'; reason?: DisconnectReason | null };

/**
 * Restores the Flutter SDK's event order for a full reconnect.
 *
 * PORT: livekit-client's restart handler first unwinds every remote
 * participant (emitting `TrackUnpublished`/`TrackUnsubscribed` and
 * `ParticipantDisconnected` for each) and only then emits `Reconnecting`, all
 * synchronously. The Flutter SDK emitted `RoomReconnectingEvent` first and
 * removed the tracks silently. Events are therefore delivered in
 * microtask-sized batches; when a batch contains `reconnecting` preceded by
 * remote departures, the departures are reported on the `reconnecting` event
 * (so the presence snapshot still contains them), `reconnecting` is moved to
 * the front, and the remote unpublish/unsubscribe lifecycle events of that
 * unwind are dropped (they would otherwise clear the watched share/camera).
 */
export function _coalesceLiveRoomEventBatch(batch: readonly LiveRoomEvent[]): LiveRoomEvent[] {
  const reconnectIndex = batch.findIndex((event) => event.type === 'reconnecting');
  if (reconnectIndex <= 0) return [...batch];
  const before = batch.slice(0, reconnectIndex);
  const departures = new Set<string>();
  for (const event of before) {
    if (event.type === 'participantDisconnected') departures.add(event.participant.identity);
  }
  if (departures.size === 0) return [...batch];
  const reconnecting = batch[reconnectIndex]!;
  const previous = reconnecting.type === 'reconnecting' ? (reconnecting.restartDepartures ?? []) : [];
  const kept = before.filter(
    (event) =>
      !(
        event.type === 'trackLifecycle' &&
        !event.isLocal &&
        departures.has(event.participantIdentity) &&
        (event.event === 'trackUnpublished' || event.event === 'trackUnsubscribed')
      ),
  );
  return [
    { type: 'reconnecting', restartDepartures: [...previous, ...departures] },
    ...kept,
    ...batch.slice(reconnectIndex + 1),
  ];
}

interface _ScreenShareCapture {
  /** The `getDisplayMedia` video track (owned by the published LocalVideoTrack). */
  video: MediaStreamTrack;
  /** Loopback/system audio from the same `getDisplayMedia` call, owned by the session. */
  audio: MediaStreamTrack | null;
}

export interface LiveSessionOptions {
  audioDeviceRebinderFactory?: ((session: LiveSession) => AudioDeviceRebinder | null) | null;
  inputRebinderFactory?: ((session: LiveSession) => AudioInputRebinder | null) | null;
  outputRebinderFactory?: ((session: LiveSession) => AudioOutputRebinder | null) | null;
  cameraDeviceReconcilerFactory?: (() => CameraDeviceReconciler | null) | null;
  audioDeviceStore?: AudioDeviceStore | null;
  screenAudioTokenProvider?: ScreenAudioTokenProvider | null;
  cameraVideoInputCountProvider?: CameraVideoInputCountProvider | null;
}

/**
 * Wraps a LiveKit [Room] and exposes a small UI-friendly snapshot of who is
 * currently speaking, whose microphone is muted, and which video tracks are
 * live.
 *
 * The server signs LiveKit tokens with `identity == user.id`, so a LiveKit
 * participant's identity matches `UserSummary.id` from the protocol layer.
 * The UI uses that as the join key.
 */
export class LiveSession extends ChangeNotifier {
  /**
   * [audioDeviceRebinderFactory] builds the production recovery hook that
   * resolves and rebinds input/output together after a desktop device change.
   *
   * The separate input/output factories remain injectable for focused legacy
   * tests. Supplying either disables the combined production factory so tests
   * cannot accidentally start real platform listeners.
   */
  constructor(opts: LiveSessionOptions = {}) {
    super();
    const audioDeviceStore = opts.audioDeviceStore ?? null;
    const inputRebinderFactory = opts.inputRebinderFactory ?? null;
    const outputRebinderFactory = opts.outputRebinderFactory ?? null;
    const audioDeviceRebinderFactory = opts.audioDeviceRebinderFactory ?? null;
    this._audioDeviceRebinderFactory =
      audioDeviceRebinderFactory ??
      (inputRebinderFactory == null && outputRebinderFactory == null
        ? (session) => _defaultAudioDeviceRebinderFactory(session, { audioDeviceStore })
        : null);
    this._inputRebinderFactory = inputRebinderFactory;
    this._outputRebinderFactory = outputRebinderFactory;
    this._cameraDeviceReconcilerFactory =
      opts.cameraDeviceReconcilerFactory ??
      (audioDeviceRebinderFactory == null && inputRebinderFactory == null && outputRebinderFactory == null
        ? _defaultCameraDeviceReconcilerFactory
        : null);
    this._screenAudioTokenProvider = opts.screenAudioTokenProvider ?? null;
    this._cameraVideoInputCountProvider = opts.cameraVideoInputCountProvider ?? _defaultCameraVideoInputCountProvider;
    this._inputGain = createLiveInputGain();
    this._remoteAudio = createLiveRemoteAudioRenderer();
  }

  private readonly _audioDeviceRebinderFactory: ((session: LiveSession) => AudioDeviceRebinder | null) | null;
  private readonly _inputRebinderFactory: ((session: LiveSession) => AudioInputRebinder | null) | null;
  private readonly _outputRebinderFactory: ((session: LiveSession) => AudioOutputRebinder | null) | null;
  private readonly _cameraDeviceReconcilerFactory: (() => CameraDeviceReconciler | null) | null;
  private _audioDeviceRebinder: AudioDeviceRebinder | null = null;
  private _inputRebinder: AudioInputRebinder | null = null;
  private _outputRebinder: AudioOutputRebinder | null = null;
  private _cameraDeviceReconciler: CameraDeviceReconciler | null = null;
  private readonly _screenAudioTokenProvider: ScreenAudioTokenProvider | null;
  private readonly _cameraVideoInputCountProvider: CameraVideoInputCountProvider;
  private _screenAudioPublisher: ScreenAudioPublisher | null = null;
  private readonly _inputGain: LiveInputGain;
  private readonly _remoteAudio: LiveRemoteAudioRenderer;

  private _room: Room | null = null;
  private _cancelEvents: (() => void) | null = null;
  private _outputSelectionSubscription: StreamSubscription | null = null;
  private _roomName: string | null = null;
  private _connecting = false;
  private _screenSharing = false;
  private _screenShareCapture: _ScreenShareCapture | null = null;
  private _cameraFlipAvailable: boolean | null = null;
  private _localVideoTransitionTail: Promise<void> = Promise.resolve();
  private _localMicrophonePublishUnavailable = false;
  private _microphonePublishInFlight: Promise<void> | null = null;
  private _resolvedLiveKitUrl: string | null = null;
  private _canPublish = true;
  private _presenceCallbacksEnabled = false;
  private _participantIdentitiesBeforeReconnect: Set<string> | null = null;
  private _microphonePublishedBeforeReconnect: boolean | null = null;
  private _resumedSignalConnection = false;
  private _reconnectRecoveryRevision = 0;
  private _reconnectRecoveryTail: Promise<void> = Promise.resolve();
  private _suppressAutomaticMicReconcile = false;
  private _inputVolume = defaultAudioVolume;
  private _outputVolume = defaultAudioVolume;
  private _musicBoxVolume = defaultAudioVolume;
  private _androidMusicMediaRouteApplied: boolean | null = null;
  private _screenShareVolume = defaultAudioVolume;
  private _watchedScreenShareIdentity: string | null = null;
  private _watchedCameraIdentity: string | null = null;
  private readonly _screenShareSubscriptionReconciler = new LatestLiveSubscriptionReconciler();
  private readonly _cameraSubscriptionReconciler = new LatestLiveSubscriptionReconciler();
  private readonly _participantVoiceVolumes = new Map<string, number>();
  private _outputMuted = false;
  // Local mic mute (Option A): muting keeps the capture running and only
  // zeroes the outgoing audio (the Web Audio gain stage), so it never tears
  // down the OS input. Tearing it down on macOS lets a Bluetooth headset
  // renegotiate HFP<->A2DP and corrupts the output sample rate. This is the
  // source of truth for the local mute state, since the LiveKit track is
  // intentionally never mute()d.
  private _micMuted = false;
  // The deviceId to capture the mic from (a `MediaDeviceInfo.deviceId`). Null
  // means the system default. Applied when the mic track is published; a
  // change while connected restarts the capture on the new device.
  private _inputDeviceId: string | null = null;

  // Target max height (px) for the local screen share — one of
  // [screenShareHeightOptions]. Defaults to balanced 720p. Applied at the
  // next share; a change while sharing re-scales the live publication.
  private _screenShareMaxHeight = defaultScreenShareMaxHeight;
  private _screenShareFrameRate = defaultScreenShareFrameRate;
  private _screenShareSourceWidth: number | null = null;
  private _screenShareSourceHeight: number | null = null;
  private _screenShareAnnouncedResolution: ScreenShareResolution | null = null;
  private _screenShareQualityRevision = 0;
  private _screenShareQualityParameterTail: Promise<void> = Promise.resolve();
  private _screenShareQualityLastCheck = ScreenShareQualityCheckResult.retry;
  private _screenShareQualityLastDiagnostic = 'not-attempted';

  private readonly _speakingIdentities = new Set<string>();
  private readonly _micMutedByIdentity = new Map<string, boolean>();

  /**
   * Fired when the server force-disconnects the local participant from
   * LiveKit (an admin `kick` -> `RemoveParticipant`). The UI uses this to drop
   * its joined state and exit the voice panel without auto-reconnecting. Only
   * raised for involuntary removals, not for our own [disconnect].
   */
  onForciblyRemoved: (() => void) | null = null;

  /**
   * Fired after LiveKit exhausts its own reconnect path for a recoverable
   * media/signalling failure. The app can request a fresh token and rebuild
   * the session even when its ordinary realtime/SSE connection stayed online.
   */
  onUnexpectedlyDisconnected: (() => void) | null = null;

  /**
   * Fired when LiveKit reports the local participant's publish permission
   * changed (an admin `mute_mic` -> canPublish=false, or `restore_voice` ->
   * canPublish=true). [canPublish] is the new value. The UI reconciles the
   * mic button: disabled while false, re-enabled (still muted) when restored.
   */
  onPublishPermissionChanged: ((canPublish: boolean) => void) | null = null;

  /**
   * Fired for real remote users after the initial room population is done.
   * Hidden screen-audio and music-box participants are excluded.
   */
  onParticipantJoined: ((participantIdentity: string) => void) | null = null;

  /** Fired when a real remote user leaves while the room is fully connected. */
  onParticipantLeft: ((participantIdentity: string, departureKind: LiveParticipantDepartureKind) => void) | null = null;

  get isConnected(): boolean {
    return this._room?.state === ConnectionState.Connected;
  }

  get isConnecting(): boolean {
    return this._connecting;
  }

  get isScreenSharing(): boolean {
    return this._screenSharing;
  }

  /**
   * Whether the current Android device is known to have another camera.
   *
   * A null/unknown probe remains usable instead of hiding the action: camera
   * capture itself is the permission-requesting operation, so the probe only
   * runs after a successful capture. Only a confirmed single-camera result
   * disables the action.
   */
  get canFlipCamera(): boolean {
    return this._cameraFlipAvailable !== false;
  }

  get roomName(): string | null {
    return this._roomName;
  }

  isAttachedToRoom(roomName: string): boolean {
    if (this._roomName !== roomName) return false;
    if (this._connecting) return true;
    const state = this._room?.state;
    return (
      state === ConnectionState.Connected ||
      state === ConnectionState.Connecting ||
      state === ConnectionState.Reconnecting ||
      state === ConnectionState.SignalReconnecting
    );
  }

  /**
   * Whether LiveKit currently grants the local participant publish rights.
   * False once an admin `mute_mic` revokes it; LiveKit is the source of
   * truth here, not any locally-tracked flag.
   */
  get canPublish(): boolean {
    return this._canPublish;
  }

  get inputVolume(): number {
    return this._inputVolume;
  }

  get outputVolume(): number {
    return this._outputVolume;
  }

  /**
   * Per-remote-user relative volume for ordinary voice audio. Defaults to
   * 100%, then multiplies with [outputVolume]. Screen-share audio is excluded
   * and continues to use [screenShareVolume].
   */
  participantVoiceVolume(userId: string): number {
    return this._participantVoiceVolumes.get(userId) ?? defaultParticipantVoiceVolume;
  }

  /**
   * Local listening volume applied only to the music box bot's audio track,
   * independent of [outputVolume] (which covers every other remote speaker).
   */
  get musicBoxVolume(): number {
    return this._musicBoxVolume;
  }

  /**
   * Local listening volume applied only to remote screen-share audio tracks,
   * independent of [outputVolume] (which covers ordinary voice tracks).
   */
  get screenShareVolume(): number {
    return this._screenShareVolume;
  }

  /**
   * The screen-share owner whose remote share is currently being watched.
   * Screen-share audio is muted unless it belongs to this identity.
   */
  get watchedScreenShareIdentity(): string | null {
    return this._watchedScreenShareIdentity;
  }

  /** The camera owner whose remote camera is currently being watched. */
  get watchedCameraIdentity(): string | null {
    return this._watchedCameraIdentity;
  }

  get outputMuted(): boolean {
    return this._outputMuted;
  }

  /** Target max height (px) for the local screen share. */
  get screenShareMaxHeight(): number {
    return this._screenShareMaxHeight;
  }

  get screenShareFrameRate(): number {
    return this._screenShareFrameRate;
  }

  private get _platformScreenShareFrameRateCap(): number {
    return !HostPlatform.isWeb && HostPlatform.isAndroid ? androidScreenShareFrameRateCap : desktopScreenShareFrameRateCap;
  }

  private get _selectedScreenShareQuality(): ScreenShareQuality {
    return screenShareQualityForHeight(this._screenShareMaxHeight, {
      frameRate: this._screenShareFrameRate,
      maxFrameRate: this._platformScreenShareFrameRateCap,
    });
  }

  /**
   * Screen share publishes a single full-quality layer. Without a lower
   * simulcast layer every viewer starts on the selected profile instead of a
   * blurry fallback, and the publisher encodes one stream instead of two; a
   * viewer whose bandwidth cannot fit it has the video paused by the SFU
   * rather than downgraded. The camera keeps the room's simulcast default.
   */
  private _screenSharePublishOptions(quality: ScreenShareQuality): TrackPublishOptions {
    return {
      source: Track.Source.ScreenShare,
      simulcast: false,
      screenShareEncoding: LiveSession._screenShareEncoding(quality),
    };
  }

  /**
   * The full-quality encoding for the selected profile. Screen share gets
   * bitrate priority over the camera on the same connection
   * (livekit-client applies `priority` as both bitrate and network priority).
   */
  static _screenShareEncoding(quality: ScreenShareQuality): VideoEncoding {
    return {
      maxFramerate: quality.maxFrameRate,
      maxBitrate: quality.maxBitrate,
      priority: 'high',
    };
  }

  /**
   * Whether the local microphone is effectively muted. With Option A muting
   * keeps a successfully published LiveKit track live and silences outgoing
   * audio through app-local gain. A missing/failed publication must still be
   * reported as muted so the local UI and server snapshot cannot claim that
   * other participants can hear a track which does not exist.
   */
  get localMicMuted(): boolean {
    const local = this._room?.localParticipant ?? null;
    if (local == null || !this._canPublish || this._shouldMuteLocalMicrophone || this._localMicrophonePublishUnavailable) {
      return true;
    }
    const publication = local.getTrackPublication(Track.Source.Microphone);
    return publication == null || publication.isMuted;
  }

  get speakingIdentities(): ReadonlySet<string> {
    return new Set(this._speakingIdentities);
  }

  get connectedParticipantIdentities(): ReadonlySet<string> {
    const room = this._room;
    if (room == null) return new Set<string>();
    const identities = new Set<string>();
    const localIdentity = room.localParticipant?.identity;
    if (localIdentity != null && localIdentity.length > 0) {
      identities.add(localIdentity);
    }
    for (const participant of room.remoteParticipants.values()) {
      if (_isScreenAudioAux(participant.identity)) continue;
      identities.add(participant.identity);
    }
    return identities;
  }

  get micMutedByIdentity(): ReadonlyMap<string, boolean> {
    return new Map(this._micMutedByIdentity);
  }

  isSpeaking(userId: string): boolean {
    return this._speakingIdentities.has(userId);
  }

  /**
   * All live video tracks (local + remote, camera + screen share) that the UI
   * can currently render. Rebuilt on demand from the room's participants.
   */
  get videoTracks(): LiveVideoTrack[] {
    const room = this._room;
    if (room == null) return [];
    const result: LiveVideoTrack[] = [];

    const collect = (participant: Participant, isLocal: boolean): void => {
      for (const pub of participant.videoTrackPublications.values()) {
        const track = pub.track;
        if (track == null) continue;
        // A muted publication still has a (now-stopped) track attached.
        // livekit mutes rather than unpublishes the camera when it's turned
        // off, so rendering it would paint a black rectangle instead of
        // letting the UI fall back to the avatar. Skip muted tracks entirely.
        if (pub.isMuted) continue;
        if (!isLocal && !pub.isSubscribed) continue;
        if (!isVideoTrack(track)) continue;
        result.push(
          new LiveVideoTrack({
            identity: participant.identity,
            track,
            isScreenShare: pub.source === Track.Source.ScreenShare,
            isLocal,
          }),
        );
      }
    };

    collect(room.localParticipant, true);
    for (const remote of room.remoteParticipants.values()) {
      if (_isScreenAudioAux(remote.identity)) continue;
      collect(remote, false);
    }
    return result;
  }

  /**
   * The first screen-share track for [userId], if any. Returns null when that
   * participant isn't sharing (or the track isn't subscribed yet).
   */
  screenShareFor(userId: string): LiveVideoTrack | null {
    for (const t of this.videoTracks) {
      if (t.identity === userId && t.isScreenShare) return t;
    }
    return null;
  }

  /** The first camera track for [userId], if any. */
  cameraFor(userId: string): LiveVideoTrack | null {
    for (const t of this.videoTracks) {
      if (t.identity === userId && !t.isScreenShare) return t;
    }
    return null;
  }

  /**
   * Connect to a LiveKit room. If already in [roomName], aligns mic state
   * and returns. Otherwise disconnects from the current room first.
   */
  async connect(opts: { url: string; token: string; roomName: string; micMuted: boolean }): Promise<void> {
    const { url, token, roomName, micMuted } = opts;
    if (this._room != null && this._roomName === roomName && this.isConnected) {
      await this.setMicMuted(micMuted);
      return;
    }
    // A click on a remote share selects it before joining. Preserve that
    // desired target while replacing the previous LiveKit room so the new
    // room's initial auto-subscription is not immediately cancelled.
    await this._disconnect({ clearWatchedTargets: false });

    this._connecting = true;
    this.notifyListeners();

    const initialScreenShareQuality = this._selectedScreenShareQuality;
    const selectedOutput = LiveHardware.instance.selectedAudioOutput?.deviceId;
    const roomOptions: RoomOptions = {
      // Adaptive stream sizes the subscribed quality to the rendering
      // element's pixel size. When a screen share is viewed in the small
      // (non-fullscreen) stage, that requests a low-quality layer; combined
      // with dynacast (which pauses layers nobody subscribes to at full
      // quality on the sender), the publisher drops to its lowest temporal
      // layer and the share renders at a few fps — choppy until you go
      // fullscreen and the renderer grows enough to request full quality.
      // Keep subscriptions at full quality so the share is smooth at any
      // tile size; the camera-tile bandwidth saving isn't worth a degraded
      // screen share for a small group.
      adaptiveStream: false,
      dynacast: true,
      // PORT: remote audio is mixed through Web Audio so RemoteAudioTrack
      // volume can exceed 1.0 (per-user voice volume goes up to 200%); plain
      // media-element volume is capped at 1.0.
      webAudioMix: true,
      audioCaptureDefaults: this._inputDeviceId == null ? {} : { deviceId: this._inputDeviceId },
      audioOutput: selectedOutput == null ? undefined : { deviceId: selectedOutput },
      publishDefaults: {
        // Don't stop the OS audio capture when the mic is muted: stopping
        // tears down the shared input device and can cut audio for other apps
        // using the same mic (e.g. WeChat voice). Muting only stops sending
        // audio; the device stays open and unmute is instant.
        stopMicTrackOnMute: false,
        // The capture rate only sets how fast the grabber samples the
        // screen. What viewers actually receive is governed by the *publish*
        // encoding, which otherwise falls back to LiveKit's h1080fps15 preset,
        // hard capped at 15fps. Set it explicitly so the encoder isn't the
        // bottleneck. maxBitrate must scale with the frame rate or the
        // encoder starves frames to stay under budget. Screen share itself
        // always publishes with [_screenSharePublishOptions].
        screenShareEncoding: LiveSession._screenShareEncoding(initialScreenShareQuality),
      },
    };
    const room = new Room(roomOptions);
    this._room = room;
    this._roomName = roomName;
    this._resolvedLiveKitUrl = url;
    this._invalidateScreenShareQualityApplication({ resetSource: true });
    this._screenSharing = false;
    this._localMicrophonePublishUnavailable = false;
    this._canPublish = true;
    this._presenceCallbacksEnabled = false;
    this._participantIdentitiesBeforeReconnect = null;
    this._microphonePublishedBeforeReconnect = null;
    this._resumedSignalConnection = false;
    this._reconnectRecoveryRevision += 1;
    this._suppressAutomaticMicReconcile = false;
    this._cancelEvents = this._listenRoomEvents(room);

    try {
      // PORT: Dart forced a DIRECT proxy for the LiveKit connection
      // (`HttpOverrides.runZoned`); in Electron the main process owns the
      // proxy bypass for the LiveKit hosts.
      await room.connect(url, token);
      // Seed publish permission from the token LiveKit just validated: a
      // voice-banned user joins with canPublish=false, and we must not try to
      // publish the mic below (LiveKit would reject it).
      this._canPublish = room.localParticipant.permissions?.canPublish ?? true;
      this._micMuted = micMuted;
      // Publish the microphone track. The OS prompts for permission on the
      // first publish. Skip it when publishing is blocked, and when joining
      // muted: the one unavoidable capture start is deferred to the first
      // unmute, which lazily publishes. Once published, muting is app-local
      // gain 0 after capture; it never writes system input gain or disables
      // the WebRTC track.
      if (this._canPublish && !this._shouldMuteLocalMicrophone) {
        await this._enableMicrophone(room.localParticipant);
      }
      await this._applyLocalMicrophoneState();
      await this._applyOutputVolume();
      await this._applyMusicBoxVolume();
      await this._applyScreenShareSubscriptions();
      await this._applyCameraSubscriptions();
      await this._applyScreenShareVolume();
      this._refreshAllMicStates();
      this._startAudioDeviceRebinder();
      this._startFollowingAudioOutputSelection();
    } catch (error) {
      this._cancelEvents?.();
      this._cancelEvents = null;
      this._stopFollowingAudioOutputSelection();
      await this._stopAudioDeviceRebinderAndWait();
      try {
        await room.disconnect();
      } catch {
        // Best-effort teardown.
      }
      this._remoteAudio.detachAll();
      this._room = null;
      this._roomName = null;
      this._connecting = false;
      this._invalidateScreenShareQualityApplication({ resetSource: true });
      this._screenSharing = false;
      this._localMicrophonePublishUnavailable = false;
      this._watchedScreenShareIdentity = null;
      this._watchedCameraIdentity = null;
      this._presenceCallbacksEnabled = false;
      this._participantIdentitiesBeforeReconnect = null;
      this._microphonePublishedBeforeReconnect = null;
      this._resumedSignalConnection = false;
      this._reconnectRecoveryRevision += 1;
      this._suppressAutomaticMicReconcile = false;
      unawaited(this._stopScreenAudioPublisher());
      this._releaseScreenShareCapture();
      this._speakingIdentities.clear();
      this._micMutedByIdentity.clear();
      unawaited(this._resetLocalInputVolume());
      unawaited(this._reconcileAndroidMusicBoxAudioRoute());
      this.notifyListeners();
      throw new LiveSessionConnectException({ url, cause: error });
    }

    this._presenceCallbacksEnabled = true;
    this._connecting = false;
    this.notifyListeners();
  }

  /**
   * Mute/unmute the local microphone. Safe to call when not connected.
   *
   * Muting is app-local gain 0 after capture: the shared OS input device and
   * WebRTC track stay live, and we never write the system input volume. A 0
   * input-volume slider is treated as the same local mute.
   */
  async setMicMuted(muted: boolean): Promise<void> {
    this._micMuted = muted;
    if (!muted) {
      this._localMicrophonePublishUnavailable = false;
    }
    await this._applyLocalMicrophoneState();
    this._refreshAllMicStates();
    this.notifyListeners();
  }

  async setInputVolume(volume: number): Promise<void> {
    const wasMutedByVolume = this._inputVolume <= 0;
    this._inputVolume = normalizedAudioVolume(volume);
    if (wasMutedByVolume && this._inputVolume > 0) {
      this._localMicrophonePublishUnavailable = false;
    }
    await this._applyLocalMicrophoneState();
  }

  /**
   * Select the microphone capture device ([deviceId] is a
   * `MediaDeviceInfo.deviceId`), or null for the system default. Takes effect
   * at the next publish; when already publishing, restarts the capture on the
   * new device, preserving the current mute state.
   */
  async setInputDeviceId(deviceId: string | null): Promise<void> {
    if (this._inputDeviceId === deviceId) return;
    this._inputDeviceId = deviceId;
    this._localMicrophonePublishUnavailable = false;
    const local = this._room?.localParticipant ?? null;
    if (local == null) return;
    // Restart only if a mic track exists; otherwise the new id is picked up
    // by the next publish (connect or first unmute).
    const hasTrack = local.getTrackPublication(Track.Source.Microphone) != null;
    if (!hasTrack) return;
    // Switching capture device unavoidably stops and restarts the input (one
    // HFP transition on macOS). That is acceptable here: it only happens on an
    // explicit device change, not on a mute toggle.
    await this._restartLocalMicrophoneCapture(local, { markUnavailableOnFailure: false });
  }

  /** Device-hotplug recovery entry point used by the audio-device rebinder. */
  async _rebindInputDeviceId(deviceId: string | null): Promise<void> {
    this._inputDeviceId = deviceId;
    this._localMicrophonePublishUnavailable = false;
    const local = this._room?.localParticipant ?? null;
    if (local == null) return;
    const hasTrack = local.getTrackPublication(Track.Source.Microphone) != null;
    if (!hasTrack) {
      await this._applyLocalMicrophoneState();
      this._refreshAllMicStates();
      this.notifyListeners();
      return;
    }
    await this._restartLocalMicrophoneCapture(local, { markUnavailableOnFailure: true });
  }

  /**
   * PORT: Dart toggled `setMicrophoneEnabled(false/true)` so the native ADM
   * re-opened the input. livekit-client restarts the capture of the existing
   * track in place (`restartTrack`), which swaps the sender's track without
   * unpublishing and keeps the input-gain processor attached.
   */
  private async _restartLocalMicrophoneCapture(
    local: LocalParticipant,
    opts: { markUnavailableOnFailure: boolean },
  ): Promise<void> {
    if (!this._canPublish) return;
    try {
      await this._applyLocalInputVolume();
      const track = local.getTrackPublication(Track.Source.Microphone)?.audioTrack;
      if (track == null) return;
      await this._restartMicrophoneTrack(track);
      if (this._room?.localParticipant !== local) return;
      this._localMicrophonePublishUnavailable = false;
      await this._applyLocalMicrophoneState();
      this._refreshAllMicStates();
      this.notifyListeners();
    } catch {
      if (opts.markUnavailableOnFailure) {
        this._markLocalMicrophonePublishUnavailable();
      }
      // Capture recovery is best-effort; keep the room connected on failure.
    }
  }

  private async _restartMicrophoneTrack(track: LocalAudioTrack): Promise<void> {
    const { deviceId: _previousDeviceId, ...constraints } = track.constraints;
    const deviceId = this._inputDeviceId;
    try {
      await track.restartTrack({
        ...constraints,
        deviceId: deviceId == null ? { ideal: 'default' } : { exact: deviceId },
      });
    } catch (error) {
      if (deviceId == null) throw error;
      // The exact endpoint is gone (or busy): keep the mic alive on the
      // closest available device rather than leaving the capture stopped.
      await track.restartTrack({ ...constraints, deviceId: { ideal: deviceId } });
    }
  }

  async setOutputVolume(volume: number): Promise<void> {
    this._outputVolume = normalizedAudioVolume(volume);
    await this._applyOutputVolume();
  }

  async setParticipantVoiceVolume(userId: string, volume: number): Promise<void> {
    const normalized = normalizedParticipantVoiceVolume(volume);
    if (normalized === defaultParticipantVoiceVolume) {
      this._participantVoiceVolumes.delete(userId);
    } else {
      this._participantVoiceVolumes.set(userId, normalized);
    }
    await this._applyParticipantVoiceVolume(userId);
  }

  async setMusicBoxVolume(volume: number): Promise<void> {
    this._musicBoxVolume = normalizedAudioVolume(volume);
    await this._applyMusicBoxVolume();
  }

  async setScreenShareVolume(volume: number): Promise<void> {
    this._screenShareVolume = normalizedAudioVolume(volume);
    await this._applyScreenShareVolume();
  }

  async setWatchedScreenShareIdentity(identity: string | null): Promise<void> {
    const next = identity?.trim();
    const normalized = next == null || next.length === 0 ? null : next;
    const changed = this._watchedScreenShareIdentity !== normalized;
    this._watchedScreenShareIdentity = normalized;
    await this._applyScreenShareSubscriptions();
    await this._applyScreenShareVolume();
    if (changed) this.notifyListeners();
  }

  async setWatchedCameraIdentity(identity: string | null): Promise<void> {
    const next = identity?.trim();
    const normalized = next == null || next.length === 0 ? null : next;
    const changed = this._watchedCameraIdentity !== normalized;
    this._watchedCameraIdentity = normalized;
    await this._applyCameraSubscriptions();
    if (changed) this.notifyListeners();
  }

  async setOutputMuted(muted: boolean): Promise<void> {
    this._outputMuted = muted;
    await this._applyOutputVolume();
  }

  /**
   * Start or stop sharing the screen. [sourceId] is a desktop capture source
   * id (see [listScreenSources]); when null the host's default is used.
   * Returns the resulting share state. Throws if capture fails (e.g. the user
   * cancels the OS picker) — the caller is responsible for surfacing that.
   */
  setScreenShareEnabled(enabled: boolean, opts: { sourceId?: string | null } = {}): Promise<boolean> {
    const sourceId = opts.sourceId ?? null;
    if (HostPlatform.isWeb || !HostPlatform.isAndroid) {
      return this._setScreenShareEnabled(enabled, { sourceId });
    }
    return this._serializeLocalVideoTransition(() => this._setScreenShareEnabled(enabled, { sourceId }));
  }

  private async _setScreenShareEnabled(enabled: boolean, opts: { sourceId?: string | null } = {}): Promise<boolean> {
    const sourceId = opts.sourceId ?? null;
    const local = this._room?.localParticipant ?? null;
    if (local == null) return false;
    if (enabled) {
      this._invalidateScreenShareQualityApplication({ resetSource: true });
      await local.setCameraEnabled(false);
      // The capture constraints bound the frames Chromium grabs to the
      // selected profile; the publisher encoding (and the room's
      // publishDefaults) still governs what viewers receive, so the encoding
      // is the cross-platform source of truth for output quality.
      const quality = this._selectedScreenShareQuality;
      const captureScreenAudio = shouldRequestScreenShareAudio({
        sourceId,
        isDesktopSourcePickerPlatform: supportsDesktopScreenShare(),
        isWindowsDesktop: !HostPlatform.isWeb && HostPlatform.isWindows,
        isAndroidPlatform: !HostPlatform.isWeb && HostPlatform.isAndroid,
      });
      // Web: the browser returns tab/system audio in the same getDisplayMedia
      // stream and it is published with the video in the main room; the aux
      // publisher is not used there.
      const publishAudioInMainRoom = HostPlatform.isWeb;
      // The Flutter SDK only unmuted an existing share publication; keep the
      // running capture instead of opening a second one.
      if (local.getTrackPublication(Track.Source.ScreenShare)?.track == null) {
        const capture = await this._captureScreenShare({
          sourceId,
          quality,
          requestAudio: captureScreenAudio || publishAudioInMainRoom,
        });
        const videoTrack = new LocalVideoTrack(capture.video, undefined, false);
        videoTrack.source = Track.Source.ScreenShare;
        try {
          // Preserve the room's configured codec choice. The screen-share
          // encoding budget and single-layer publish belong to this setting.
          await local.publishTrack(videoTrack, this._screenSharePublishOptions(quality));
          if (publishAudioInMainRoom && capture.audio != null) {
            const audioTrack = new LocalAudioTrack(capture.audio, undefined, false);
            audioTrack.source = Track.Source.ScreenShareAudio;
            capture.audio = null;
            await local.publishTrack(audioTrack, { source: Track.Source.ScreenShareAudio });
          }
        } catch (error) {
          videoTrack.stop();
          capture.audio?.stop();
          throw error;
        }
        this._releaseScreenShareCapture();
        this._screenShareCapture = capture;
      }
      this._screenSharing = true;
      // Do not delay the share UI while the first outbound frame becomes
      // available. The initial publish already has the selected FPS/bitrate;
      // this bounded background pass discovers source dimensions, applies
      // scaling, then verifies the actual outbound resolution.
      unawaited(this._applyScreenShareQualityWithRetries());
      // Start the screen-audio aux publisher in the background so it never
      // blocks the screen-share video. If the aux room fails to connect (ICE,
      // token, etc.) the video share still works — just without independent
      // audio. Errors are logged, not surfaced: the user didn't ask to "share
      // audio", they asked to "share screen", and audio is best-effort.
      if (captureScreenAudio) {
        unawaited(
          this._startScreenAudioPublisher().catch((error: unknown) => {
            console.debug(`screen-audio publisher failed: ${String(error)}`);
          }),
        );
      }
    } else {
      this._invalidateScreenShareQualityApplication({ resetSource: true });
      if (!HostPlatform.isWeb && HostPlatform.isAndroid) {
        // Playback capture uses the active MediaProjection. Cancel and tear
        // down its auxiliary publisher before revoking that projection, or an
        // in-flight publish can create an audio track against an invalid token.
        this._screenSharing = false;
        try {
          await this._stopScreenAudioPublisher();
        } catch (error) {
          console.debug(`screen-audio publisher stop failed: ${String(error)}`);
        }
        await local.setScreenShareEnabled(false);
      } else {
        await local.setScreenShareEnabled(false);
        this._screenSharing = false;
        await this._stopScreenAudioPublisher();
      }
      this._releaseScreenShareCapture();
      await this._awaitAndroidScreenCaptureRelease();
    }
    this.notifyListeners();
    return this._screenSharing;
  }

  /**
   * PORT: replaces flutter_webrtc's `ScreenShareCaptureOptions(sourceId,
   * captureScreenAudio)`. The desktop host pre-selects the picked source and
   * whether loopback audio is attached (`platform().screenCapture
   * .selectSource`), then a single `getDisplayMedia` call returns the video
   * and, when available, the system-audio track. The video constraints bound
   * the captured frames to the selected profile (height cap + frame-rate
   * cap); width follows the source aspect ratio.
   */
  private async _captureScreenShare(opts: {
    sourceId: string | null;
    quality: ScreenShareQuality;
    requestAudio: boolean;
  }): Promise<_ScreenShareCapture> {
    const { sourceId, quality, requestAudio } = opts;
    const mediaDevices = typeof navigator === 'undefined' ? undefined : (navigator as Partial<Navigator>).mediaDevices;
    if (mediaDevices?.getDisplayMedia == null) {
      throw new Error('getDisplayMedia not supported');
    }
    const screenCapture = hasPlatform() ? platform().screenCapture : null;
    if (screenCapture != null && sourceId != null) {
      await screenCapture.selectSource(sourceId, { audio: requestAudio });
    }
    const stream = await mediaDevices.getDisplayMedia({
      video: _screenShareVideoConstraints(quality),
      audio: requestAudio ? _screenShareAudioConstraints() : false,
    });
    const video = stream.getVideoTracks()[0];
    if (video == null) {
      for (const track of stream.getTracks()) track.stop();
      throw new Error('no video track found');
    }
    video.contentHint = _screenShareContentHint(quality);
    const audio = stream.getAudioTracks()[0] ?? null;
    return { video, audio };
  }

  /** Stops the session-owned loopback audio of the current/last share. */
  private _releaseScreenShareCapture(): void {
    const capture = this._screenShareCapture;
    this._screenShareCapture = null;
    capture?.audio?.stop();
  }

  private async _awaitAndroidScreenCaptureRelease(): Promise<void> {
    if (HostPlatform.isWeb || !HostPlatform.isAndroid) return;
    // LiveKit awaits unpublishing, but Android's MediaProjection/virtual
    // display teardown crosses a native handler thread. Give that thread a
    // bounded opportunity to remove the publication, followed by a short
    // platform-only grace period before opening camera capture.
    for (let attempt = 0; attempt < 10 && this._localHasScreenShare(); attempt++) {
      await delay(16);
    }
    await delay(160);
  }

  /**
   * Set the target max height for the local screen share. A running share is
   * updated through capture constraints and sender parameters without
   * restarting capture.
   */
  async setScreenShareMaxHeight(height: number): Promise<void> {
    const normalized = normalizedScreenShareMaxHeight(height);
    if (this._screenShareMaxHeight === normalized) return;
    this._screenShareMaxHeight = normalized;
    if (this._screenSharing) {
      await this._applyScreenShareQualityWithRetries({ refreshDesktopCapture: true });
    }
    this.notifyListeners();
  }

  /** Set the output frame-rate cap independently from resolution. */
  async setScreenShareFrameRate(frameRate: number): Promise<void> {
    const normalized = normalizedScreenShareFrameRate(frameRate, {
      maxFrameRate: this._platformScreenShareFrameRateCap,
    });
    if (this._screenShareFrameRate === normalized) return;
    this._screenShareFrameRate = normalized;
    if (this._screenSharing) {
      await this._applyScreenShareQualityWithRetries({ refreshDesktopCapture: true });
    }
    this.notifyListeners();
  }

  private _invalidateScreenShareQualityApplication(opts: { resetSource: boolean }): void {
    this._screenShareQualityRevision += 1;
    this._screenShareQualityLastCheck = ScreenShareQualityCheckResult.retry;
    this._screenShareQualityLastDiagnostic = 'not-attempted';
    if (opts.resetSource) {
      this._screenShareSourceWidth = null;
      this._screenShareSourceHeight = null;
      this._screenShareAnnouncedResolution = null;
    }
  }

  /**
   * Applies and verifies the selected quality without interrupting capture.
   *
   * Source dimensions and sender stats are not guaranteed to exist on the
   * first event-loop turn after publishing. Retry for a bounded period so
   * 480p/720p do not silently remain at the display's native resolution.
   */
  private async _applyScreenShareQualityWithRetries(opts: { refreshDesktopCapture?: boolean } = {}): Promise<void> {
    const { refreshDesktopCapture = false } = opts;
    const revision = ++this._screenShareQualityRevision;
    if (refreshDesktopCapture && supportsDesktopScreenShare()) {
      // Prime the sender first, then re-constrain the capture so the encoder
      // already carries the requested caps when the new frame size arrives.
      await this._serializeScreenShareQualityAttempt(() => this._applyScreenShareQualityAttempt(revision));
      if (revision !== this._screenShareQualityRevision || !this._screenSharing) return;
      await this._serializeScreenShareQualityAttempt(async () => {
        await this._refreshDesktopScreenShareCapture(revision);
        return ScreenShareQualityCheckResult.retry;
      });
      if (revision !== this._screenShareQualityRevision || !this._screenSharing) return;
    }
    // No attempt at zero: the SDK publishes the selected profile already, and
    // an attempt before the first outbound frame can only churn the encoder
    // (nothing to verify yet). The first probe lands once frames should flow.
    const delays: readonly Milliseconds[] = [300, 450, 850, 1400, 2200];
    for (const wait of delays) {
      if (revision !== this._screenShareQualityRevision || !this._screenSharing) return;
      if (wait !== 0) await delay(wait);
      if (revision !== this._screenShareQualityRevision || !this._screenSharing) return;
      const check = await this._serializeScreenShareQualityAttempt(() => this._applyScreenShareQualityAttempt(revision));
      if (check === ScreenShareQualityCheckResult.verified) {
        const quality = this._selectedScreenShareQuality;
        console.debug(
          `screen-share quality caps verified: ${quality.maxHeight}p/${quality.maxFrameRate}fps ` +
            `(${this._screenShareQualityLastDiagnostic})`,
        );
        return;
      }
    }
    if (revision === this._screenShareQualityRevision && this._screenSharing) {
      const quality = this._selectedScreenShareQuality;
      const profile = `${quality.maxHeight}p/${quality.maxFrameRate}fps`;
      if (this._screenShareQualityLastCheck === ScreenShareQualityCheckResult.awaitingOutboundFrames) {
        console.debug(
          'screen-share quality caps ready; outbound verification ' +
            `will resume when a viewer subscribes: ${profile} (${this._screenShareQualityLastDiagnostic})`,
        );
      } else {
        console.debug(
          `screen-share quality cap verification still pending: ${profile} (${this._screenShareQualityLastDiagnostic})`,
        );
      }
    }
  }

  /**
   * Re-bounds the live desktop capture while retaining the same LiveKit
   * publication/sender.
   *
   * PORT: Dart rebuilt the capture track (`replaceScreenShareCapture`)
   * because flutter-webrtc's desktop capturers ignored live constraint
   * changes. Chromium applies new constraints to the running capture, so the
   * same `getDisplayMedia` track is re-constrained in place (no picker, no
   * sender swap) and its content hint follows the selected frame rate.
   */
  private async _refreshDesktopScreenShareCapture(revision: number): Promise<void> {
    if (revision !== this._screenShareQualityRevision || !this._screenSharing || !supportsDesktopScreenShare()) {
      return;
    }
    const local = this._room?.localParticipant ?? null;
    const track = local?.getTrackPublication(Track.Source.ScreenShare)?.videoTrack;
    if (track == null) return;

    const quality = this._selectedScreenShareQuality;
    const mediaStreamTrack = track.mediaStreamTrack;
    try {
      mediaStreamTrack.contentHint = _screenShareContentHint(quality);
      await mediaStreamTrack.applyConstraints(_screenShareVideoConstraints(quality));
    } catch (error) {
      // The old constraints remain active when the capture rejects the new
      // ones, so sender-parameter enforcement can still proceed.
      console.debug(`screen-share desktop capture refresh failed: ${String(error)}`);
      return;
    }
    if (revision !== this._screenShareQualityRevision || !this._screenSharing) return;
    this._screenShareSourceWidth = null;
    this._screenShareSourceHeight = null;
    this._screenShareAnnouncedResolution = null;
    console.debug(`screen-share desktop capture refreshed: ${quality.maxHeight}p/${quality.maxFrameRate}fps`);
  }

  private _serializeScreenShareQualityAttempt(
    operation: () => Promise<ScreenShareQualityCheckResult>,
  ): Promise<ScreenShareQualityCheckResult> {
    const completer = new Completer<ScreenShareQualityCheckResult>();
    const previous = this._screenShareQualityParameterTail;
    this._screenShareQualityParameterTail = (async () => {
      try {
        await previous;
      } catch {
        // Ordering only.
      }
      try {
        completer.complete(await operation());
      } catch (error) {
        console.debug(`screen-share quality apply failed: ${String(error)}`);
        completer.complete(ScreenShareQualityCheckResult.retry);
      }
    })();
    return completer.promise;
  }

  private async _applyScreenShareQualityAttempt(revision: number): Promise<ScreenShareQualityCheckResult> {
    if (revision !== this._screenShareQualityRevision || !this._screenSharing) {
      return ScreenShareQualityCheckResult.verified;
    }
    const local = this._room?.localParticipant ?? null;
    if (local == null) return ScreenShareQualityCheckResult.retry;
    const pub = local.getTrackPublication(Track.Source.ScreenShare);
    const track = pub?.videoTrack;
    if (pub == null || track == null) {
      return ScreenShareQualityCheckResult.retry;
    }
    const sender = track.sender;
    if (sender == null) return ScreenShareQualityCheckResult.retry;

    const encodings = sender.getParameters().encodings;
    if (encodings == null || encodings.length === 0) {
      return ScreenShareQualityCheckResult.retry;
    }
    const quality = this._selectedScreenShareQuality;
    const encodingScale = (encoding: RTCRtpEncodingParameters): number => {
      const scale = encoding.scaleResolutionDownBy;
      return scale == null || scale < 1 ? 1.0 : scale;
    };

    // Simulcast encodings are ordered low-to-high in LiveKit, but do not rely
    // on that implementation detail. The smallest scale is the highest layer.
    const currentHighQualityScale = encodings.map(encodingScale).reduce((left, right) => (left < right ? left : right));

    // Read the capture size from the track settings when available; outbound
    // stats below remain the portable fallback.
    if (this._screenShareSourceHeight == null) {
      try {
        const settings = track.mediaStreamTrack.getSettings();
        const settingsWidth = _positiveScreenShareDimension(settings.width);
        const settingsHeight = _positiveScreenShareDimension(settings.height);
        if (settingsHeight != null) {
          this._screenShareSourceWidth = settingsWidth;
          this._screenShareSourceHeight = settingsHeight;
        }
      } catch {
        // Settings unavailable on this platform.
      }
    }

    let encodedWidth: number | null = null;
    let encodedHeight: number | null = null;
    let observedSourceWidth: number | null = null;
    let observedSourceHeight: number | null = null;
    let outboundFrameRate: number | null = null;
    const outboundSamples: Array<{ height: number; scale: number }> = [];
    try {
      const stats: VideoSenderStats[] = await withTimeout(track.getSenderStats(), 1000);
      for (const s of stats) {
        const h = _positiveScreenShareDimension(s.frameHeight);
        if (h != null) {
          const value = h;
          if (encodedHeight == null || value > encodedHeight) {
            encodedHeight = value;
            encodedWidth = _positiveScreenShareDimension(s.frameWidth);
          }
          // PORT: browser stats carry the RID but not a per-encoding SSRC on
          // `RTCRtpEncodingParameters`, so the Dart SSRC match is dropped.
          let matchingEncoding: RTCRtpEncodingParameters | null = null;
          const rid = s.rid;
          if (rid) {
            matchingEncoding = encodings.find((encoding) => encoding.rid === rid) ?? null;
          }
          if (matchingEncoding == null && encodings.length === 1) {
            matchingEncoding = encodings[0]!;
          }
          const sourceScale = screenShareSourceScaleForSample({
            encodedHeight: value,
            targetHeight: quality.maxHeight,
            encodingScales: encodings.map(encodingScale),
            matchedScale: matchingEncoding == null ? null : encodingScale(matchingEncoding),
            knownSourceHeight: this._screenShareSourceHeight,
          });
          outboundSamples.push({ height: value, scale: sourceScale });
          const sourceCandidate = Math.round(value * sourceScale);
          const width = _positiveScreenShareDimension(s.frameWidth);
          if (observedSourceHeight == null || sourceCandidate > observedSourceHeight) {
            observedSourceHeight = sourceCandidate;
            observedSourceWidth = width == null ? null : Math.round(width * sourceScale);
          }
        }
        const fps = typeof s.framesPerSecond === 'number' && Number.isFinite(s.framesPerSecond) ? s.framesPerSecond : null;
        if (fps != null && (outboundFrameRate == null || fps > outboundFrameRate)) {
          outboundFrameRate = fps;
        }
      }
    } catch {
      // Stats are best-effort; the next attempt retries.
    }

    // Lock the source size once per capture session. Reconstructing it
    // repeatedly from delayed frames after changing scale can multiply the
    // old output by the new scale and make a 1080p display look like a 4K
    // source.
    if (this._screenShareSourceHeight == null && observedSourceHeight != null) {
      this._screenShareSourceWidth = observedSourceWidth;
      this._screenShareSourceHeight = observedSourceHeight;
    } else if (this._screenShareSourceWidth == null && observedSourceWidth != null) {
      this._screenShareSourceWidth = observedSourceWidth;
    }

    if (revision !== this._screenShareQualityRevision || !this._screenSharing) {
      return ScreenShareQualityCheckResult.verified;
    }
    const sourceHeight = this._screenShareSourceHeight;
    // Desktop captures already emit frames bounded to the selected profile
    // (see [_captureScreenShare]). Scaling their highest RTP layer again
    // would turn a 720p -> 480p switch into a transient 320p stream. Keep the
    // highest layer at 1x and preserve only the relative simulcast scales.
    // Android keeps its native MediaProjection frames and therefore still
    // needs sender scaling.
    const desiredHighQualityScale = supportsDesktopScreenShare()
      ? 1.0
      : sourceHeight == null
        ? currentHighQualityScale
        : screenShareScaleDownBy({ sourceHeight, targetHeight: quality.maxHeight });

    // Screen share publishes a single layer (see [_screenSharePublishOptions]),
    // so every encoding is held to the selected profile.
    let parametersMatch = true;
    for (const encoding of encodings) {
      const existingScale = encodingScale(encoding);
      const relativeScale = existingScale / currentHighQualityScale;
      const desiredScale = desiredHighQualityScale * relativeScale;
      if (
        Math.abs(existingScale - desiredScale) > 0.001 ||
        encoding.maxFramerate !== quality.maxFrameRate ||
        encoding.maxBitrate !== quality.maxBitrate
      ) {
        parametersMatch = false;
      }
    }

    let resolutionVerified = false;
    if (sourceHeight != null && outboundSamples.length > 0) {
      let mismatchedSample = false;
      for (const sample of outboundSamples) {
        const expectedHeight = Math.round(sourceHeight / sample.scale);
        const proportionalTolerance = Math.round(expectedHeight * 0.05);
        const tolerance = proportionalTolerance > 4 ? proportionalTolerance : 4;
        if (Math.abs(sample.height - expectedHeight) > tolerance) {
          mismatchedSample = true;
        }
      }
      resolutionVerified = !mismatchedSample;
    }
    const frameRateCapVerified = outboundFrameRate == null || outboundFrameRate <= quality.maxFrameRate + 1;
    if (parametersMatch) {
      this._syncScreenShareTrackDescription({ track, quality, publicationOptions: pub });
    }
    let check = screenShareQualityCheckResult({
      parametersReady: parametersMatch,
      hasOutboundFrames: outboundSamples.length > 0,
      resolutionVerified,
      frameRateCapVerified,
    });
    this._screenShareQualityLastCheck = check;
    this._screenShareQualityLastDiagnostic =
      `source=${sourceHeight ?? 'unknown'}, ` +
      `outbound=${encodedWidth ?? '?'}x${encodedHeight ?? 'none'}, ` +
      `observedFps=${outboundFrameRate?.toFixed(1) ?? 'none'}, ` +
      `parameters=${parametersMatch ? 'matched' : 'pending'}`;
    if (check !== ScreenShareQualityCheckResult.retry) return check;

    // Re-read right before writing: `setParameters` must carry the latest
    // transaction id, and dynacast may have toggled `active` meanwhile.
    const params = sender.getParameters();
    const writable = params.encodings;
    let applied = false;
    if (writable != null && writable.length === encodings.length) {
      writable.forEach((encoding, index) => {
        const existingScale = encodingScale(encodings[index]!);
        const relativeScale = existingScale / currentHighQualityScale;
        encoding.scaleResolutionDownBy = desiredHighQualityScale * relativeScale;
        encoding.maxFramerate = quality.maxFrameRate;
        encoding.maxBitrate = quality.maxBitrate;
        // `active` is left as read: dynacast owns it.
      });
      applied = await withTimeout(
        sender.setParameters(params).then(
          () => true,
          () => false,
        ),
        1000,
        () => false,
      );
    }
    if (!applied) {
      console.debug('screen-share sender rejected quality parameters');
    } else {
      this._syncScreenShareTrackDescription({ track, quality, publicationOptions: pub });
    }
    check = screenShareQualityCheckResult({
      parametersReady: parametersMatch || applied,
      hasOutboundFrames: outboundSamples.length > 0,
      // These samples were collected before setParameters. Never treat them
      // as proof that a newly-applied scale/FPS cap has reached the encoder.
      resolutionVerified: parametersMatch && resolutionVerified,
      frameRateCapVerified: parametersMatch && frameRateCapVerified,
    });
    this._screenShareQualityLastCheck = check;
    this._screenShareQualityLastDiagnostic =
      `source=${sourceHeight ?? 'unknown'}, ` +
      `outbound=${encodedWidth ?? '?'}x${encodedHeight ?? 'none'}, ` +
      `observedFps=${outboundFrameRate?.toFixed(1) ?? 'none'}, ` +
      `parameters=${applied ? 'applied' : 'rejected'}`;
    // A later attempt verifies frames produced after the encoder reconfigures.
    return check;
  }

  /**
   * Keeps livekit-client's own record of the publish options in step with the
   * applied quality, so a full-reconnect republish (`republishAllTracks`)
   * comes back with the current profile instead of the one at share start.
   *
   * PORT: the Flutter fork also re-announced the capped output dimensions to
   * the SFU (`updatePublishedVideoTrackDimensions`). livekit-client has no
   * public equivalent; with a single non-simulcast layer and adaptive stream
   * off the SFU does not select layers by those dimensions.
   */
  private _syncScreenShareTrackDescription(opts: {
    track: LocalVideoTrack;
    quality: ScreenShareQuality;
    publicationOptions: { options?: TrackPublishOptions };
  }): void {
    const { track, quality, publicationOptions } = opts;
    const output = screenShareCappedOutputResolution({
      sourceWidth: this._screenShareSourceWidth,
      sourceHeight: this._screenShareSourceHeight,
      targetHeight: quality.maxHeight,
    });
    const publishOptions = this._screenSharePublishOptions(quality);
    track.publishOptions = { ...track.publishOptions, ...publishOptions };
    publicationOptions.options = { ...publicationOptions.options, ...publishOptions };
    if (this._screenShareAnnouncedResolution?.equals(output)) return;
    this._screenShareAnnouncedResolution = output;
  }

  /**
   * Start or stop the local camera. Returns the resulting state. Throws if
   * capture fails (no camera, permission denied) — the caller surfaces it.
   */
  setCameraEnabled(enabled: boolean): Promise<boolean> {
    const operation = async (): Promise<boolean> => {
      const local = this._room?.localParticipant ?? null;
      if (local == null) return false;
      // Before stopping a screen share, so a refused camera leaves it running.
      if (enabled) await ensureCameraAccess();
      if (enabled && this._screenSharing) {
        await this._setScreenShareEnabled(false);
      }
      await local.setCameraEnabled(enabled);
      if (enabled) {
        // setCameraEnabled is intentionally first: on Android it owns the OS
        // camera-permission prompt. Never interpret a pre-permission empty
        // device list as proof that the phone has only one camera.
        await this._refreshCameraFlipAvailability();
      } else {
        this._cameraFlipAvailable = null;
      }
      this.notifyListeners();
      return enabled;
    };

    if (HostPlatform.isWeb || !HostPlatform.isAndroid) return operation();
    return this._serializeLocalVideoTransition(operation);
  }

  /**
   * Switches the active local camera between the front and rear positions.
   * Returns false when there is no published camera track.
   *
   * PORT: `CameraCaptureOptions.cameraPosition` -> the capture's
   * `facingMode` (`user` / `environment`), restarted in place.
   */
  flipCamera(): Promise<boolean> {
    const operation = async (): Promise<boolean> => {
      const local = this._room?.localParticipant ?? null;
      if (local == null) return false;
      let cameraTrack: LocalVideoTrack | null = null;
      for (const publication of local.videoTrackPublications.values()) {
        if (publication.source !== Track.Source.Camera) continue;
        const track = publication.videoTrack;
        if (track != null) {
          cameraTrack = track;
          break;
        }
      }
      if (cameraTrack == null) return false;
      const current = facingModeFromLocalTrack(cameraTrack).facingMode;
      const nextFacingMode = current === 'user' ? 'environment' : 'user';
      await cameraTrack.restartTrack({ facingMode: nextFacingMode });
      this.notifyListeners();
      return true;
    };

    if (HostPlatform.isWeb || !HostPlatform.isAndroid) return operation();
    return this._serializeLocalVideoTransition(operation);
  }

  private async _refreshCameraFlipAvailability(): Promise<void> {
    if (HostPlatform.isWeb || !HostPlatform.isAndroid) {
      this._cameraFlipAvailable = false;
      return;
    }
    try {
      const inputCount = await this._cameraVideoInputCountProvider();
      this._cameraFlipAvailable = cameraFlipAvailabilityFromVideoInputCount(inputCount);
    } catch {
      // Enumeration can still be unavailable on vendor Android builds after
      // the permission prompt. Keep the action available so a transient probe
      // failure is not mistaken for confirmed single-camera hardware.
      this._cameraFlipAvailable = null;
    }
  }

  private _serializeLocalVideoTransition<T>(operation: () => Promise<T>): Promise<T> {
    const result = new Completer<T>();
    this._localVideoTransitionTail = this._localVideoTransitionTail.then(async () => {
      try {
        result.complete(await operation());
      } catch (error) {
        result.completeError(error);
      }
    });
    return result.promise;
  }

  /**
   * Enumerate capturable desktop sources (screens and windows) for the
   * share picker through `platform().screenCapture` (Electron
   * `desktopCapturer`). Best-effort: returns an empty list if enumeration
   * fails or the host cannot capture.
   */
  static async listScreenSources(): Promise<ScreenSource[]> {
    try {
      const bridge = hasPlatform() ? platform().screenCapture : null;
      if (bridge == null) return [];
      const sources = await bridge.listSources({ thumbnailWidth: 640, thumbnailHeight: 360 });
      const screenSources: ScreenSource[] = [];
      for (const s of sources) {
        const isWindow = s.isWindow;
        const name = s.name.trim();
        const thumbnailKey = _thumbnailKeyForSource(s);
        const thumbnail = _rememberScreenSourceThumbnail({ sourceId: thumbnailKey, thumbnail: s.thumbnailDataUrl });
        screenSources.push(
          new ScreenSource({
            id: s.id,
            name: name.length === 0 ? (isWindow ? '窗口' : '屏幕') : name,
            thumbnail,
            isWindow,
            thumbnailKey,
            thumbnailUpdates: _screenSourceThumbnailUpdates(thumbnailKey),
          }),
        );
      }
      return filterScreenSourcesForPicker(screenSources);
    } catch {
      return [];
    }
  }

  /**
   * Refreshes source thumbnails for an open picker; updates arrive through
   * each [ScreenSource.thumbnailUpdates].
   *
   * PORT: flutter_webrtc delivered Windows thumbnails asynchronously
   * (`onThumbnailChanged` after `updateSources`) and macOS screen thumbnails
   * came from a native CGDisplay capture. Electron's `desktopCapturer`
   * returns current thumbnails with every enumeration, so a refresh simply
   * re-enumerates and re-emits them.
   */
  static async refreshScreenSourceThumbnails(): Promise<void> {
    try {
      const bridge = hasPlatform() ? platform().screenCapture : null;
      if (bridge == null) return;
      const sources = await bridge.listSources({ thumbnailWidth: 640, thumbnailHeight: 360 });
      for (const s of sources) {
        _rememberAndEmitScreenSourceThumbnail({ sourceId: _thumbnailKeyForSource(s), thumbnail: s.thumbnailDataUrl });
      }
    } catch {
      // Best-effort.
    }
  }

  disconnect(): Promise<void> {
    return this._disconnect();
  }

  private async _disconnect(opts: { clearWatchedTargets?: boolean } = {}): Promise<void> {
    const { clearWatchedTargets = true } = opts;
    const room = this._room;
    const cancel = this._cancelEvents;
    this._cancelEvents = null;
    this._stopFollowingAudioOutputSelection();
    this._screenShareSubscriptionReconciler.invalidate();
    this._cameraSubscriptionReconciler.invalidate();
    await this._stopAudioDeviceRebinderAndWait();
    this._room = null;
    this._roomName = null;
    this._resolvedLiveKitUrl = null;
    this._connecting = false;
    this._invalidateScreenShareQualityApplication({ resetSource: true });
    this._screenSharing = false;
    this._cameraFlipAvailable = null;
    if (clearWatchedTargets) {
      this._watchedScreenShareIdentity = null;
      this._watchedCameraIdentity = null;
    }
    this._localMicrophonePublishUnavailable = false;
    this._canPublish = true;
    this._presenceCallbacksEnabled = false;
    this._participantIdentitiesBeforeReconnect = null;
    this._microphonePublishedBeforeReconnect = null;
    this._resumedSignalConnection = false;
    this._reconnectRecoveryRevision += 1;
    this._suppressAutomaticMicReconcile = false;
    this._speakingIdentities.clear();
    this._micMutedByIdentity.clear();
    await this._resetLocalInputVolume();
    await this._reconcileAndroidMusicBoxAudioRoute();
    await this._stopScreenAudioPublisher();
    this._releaseScreenShareCapture();
    if (cancel != null) {
      try {
        cancel();
      } catch {
        // Listener teardown is best-effort.
      }
    }
    if (room != null) {
      try {
        await room.disconnect();
      } catch {
        // Best-effort teardown.
      }
      this._remoteAudio.detachAll();
      // After teardown, recover the Bluetooth A2DP sample rate the call may
      // have dragged down to HFP. Done last so the restored rate isn't
      // immediately re-overridden by a still-active capture stream.
      await this._resetAudioOnLeave();
    }
    this.notifyListeners();
  }

  override dispose(): void {
    const room = this._room;
    const cancel = this._cancelEvents;
    this._cancelEvents = null;
    this._stopFollowingAudioOutputSelection();
    this._screenShareSubscriptionReconciler.invalidate();
    this._cameraSubscriptionReconciler.invalidate();
    this._stopAudioDeviceRebinder();
    this._room = null;
    this._roomName = null;
    this._invalidateScreenShareQualityApplication({ resetSource: true });
    this._screenSharing = false;
    this._cameraFlipAvailable = null;
    this._watchedScreenShareIdentity = null;
    this._watchedCameraIdentity = null;
    this._localMicrophonePublishUnavailable = false;
    this._presenceCallbacksEnabled = false;
    this._participantIdentitiesBeforeReconnect = null;
    this._microphonePublishedBeforeReconnect = null;
    this._resumedSignalConnection = false;
    this._reconnectRecoveryRevision += 1;
    this._suppressAutomaticMicReconcile = false;
    unawaited(this._stopScreenAudioPublisher());
    this._releaseScreenShareCapture();
    this._speakingIdentities.clear();
    this._micMutedByIdentity.clear();
    unawaited(this._resetLocalInputVolume());
    unawaited(this._reconcileAndroidMusicBoxAudioRoute());
    cancel?.();
    if (room != null) {
      // Best-effort async cleanup; swallow errors.
      room
        .disconnect()
        .catch(() => {})
        .finally(() => {
          this._remoteAudio.detachAll();
          unawaited(this._resetAudioOnLeave());
        });
    }
    super.dispose();
  }

  /**
   * PORT: no-op. The Flutter macOS fork reset the Bluetooth output's nominal
   * sample rate after a call (`Helper.gcResetAudioOnLeave`); Chromium's audio
   * service releases the input itself and exposes no such control.
   */
  private async _resetAudioOnLeave(): Promise<void> {}

  // ---- Event handling -------------------------------------------------------

  /**
   * Subscribes to [room] and feeds [_onEvent] with [LiveRoomEvent]s in
   * microtask batches (see [_coalesceLiveRoomEventBatch]). Remote audio is
   * attached/detached immediately so playback never waits for a batch.
   */
  private _listenRoomEvents(room: Room): () => void {
    const offs: Array<() => void> = [];
    let cancelled = false;
    let scheduled = false;
    const queue: LiveRoomEvent[] = [];

    const flush = (): void => {
      scheduled = false;
      const batch = queue.splice(0, queue.length);
      if (cancelled) return;
      for (const event of _coalesceLiveRoomEventBatch(batch)) {
        if (cancelled) return;
        try {
          this._onEvent(event);
        } catch (error) {
          console.error('LiveSession room event handler threw', error);
        }
      }
    };
    const push = (event: LiveRoomEvent): void => {
      if (cancelled) return;
      queue.push(event);
      if (scheduled) return;
      scheduled = true;
      queueMicrotask(flush);
    };
    const on = <E extends keyof RoomEventCallbacks>(event: E, handler: RoomEventCallbacks[E]): void => {
      room.on(event, handler);
      offs.push(() => {
        room.off(event, handler);
      });
    };
    const lifecycle = (event: LiveTrackLifecycleEvent, participant: { identity: string; isLocal: boolean }): void => {
      push({ type: 'trackLifecycle', event, participantIdentity: participant.identity, isLocal: participant.isLocal });
    };

    on(RoomEvent.Reconnecting, () => push({ type: 'reconnecting' }));
    on(RoomEvent.SignalReconnecting, () => push({ type: 'resuming' }));
    on(RoomEvent.Reconnected, () => push({ type: 'reconnected' }));
    on(RoomEvent.ActiveSpeakersChanged, (speakers) => push({ type: 'activeSpeakersChanged', speakers: [...speakers] }));
    on(RoomEvent.ParticipantConnected, (participant) => push({ type: 'participantConnected', participant }));
    on(RoomEvent.ParticipantDisconnected, (participant, reason) =>
      push({ type: 'participantDisconnected', participant, reason: reason ?? null }),
    );
    on(RoomEvent.ParticipantPermissionsChanged, (prevPermissions, participant) =>
      push({
        type: 'participantPermissionsUpdated',
        participant,
        canPublish: participant.permissions?.canPublish ?? false,
        oldCanPublish: prevPermissions?.canPublish ?? this._canPublish,
      }),
    );
    on(RoomEvent.LocalTrackSubscribed, (publication) => push({ type: 'localTrackSubscribed', trackSid: publication.trackSid }));
    on(RoomEvent.TrackSubscribed, (track, publication, participant) => {
      this._attachRemoteAudio(track, publication.source, participant.identity);
      lifecycle('trackSubscribed', participant);
    });
    on(RoomEvent.TrackUnsubscribed, (track, _publication, participant) => {
      this._detachRemoteAudio(track);
      lifecycle('trackUnsubscribed', participant);
    });
    on(RoomEvent.LocalTrackPublished, (_publication, participant) => lifecycle('localTrackPublished', participant));
    on(RoomEvent.LocalTrackUnpublished, (_publication, participant) => lifecycle('localTrackUnpublished', participant));
    on(RoomEvent.TrackPublished, (_publication, participant) => lifecycle('trackPublished', participant));
    on(RoomEvent.TrackUnpublished, (_publication, participant) => lifecycle('trackUnpublished', participant));
    on(RoomEvent.TrackMuted, (_publication, participant) => lifecycle('trackMuted', participant));
    on(RoomEvent.TrackUnmuted, (_publication, participant) => lifecycle('trackUnmuted', participant));
    on(RoomEvent.Disconnected, (reason) => push({ type: 'disconnected', reason: reason ?? null }));

    return () => {
      cancelled = true;
      queue.length = 0;
      for (const off of offs) off();
    };
  }

  private _attachRemoteAudio(track: RemoteTrack, source: Track.Source, participantIdentity: string): void {
    if (!(track instanceof RemoteAudioTrack)) return;
    // Start at the right level: the batched volume pass runs a microtask
    // later, and an own screen-audio aux track must never be heard at all.
    _setRemoteAudioTrackVolume(track, this._targetRemoteAudioVolume(participantIdentity, source));
    try {
      this._remoteAudio.attach(track);
    } catch (error) {
      console.debug(`live: remote audio attach failed: ${String(error)}`);
    }
  }

  /** The volume the `_apply*Volume` passes give a remote audio publication. */
  private _targetRemoteAudioVolume(participantIdentity: string, source: Track.Source): number {
    if (source === Track.Source.ScreenShareAudio) {
      const isOwnAux = _isOwnScreenAudioAux(participantIdentity, this._room?.localParticipant.identity);
      const isWatched =
        this._watchedScreenShareIdentity != null &&
        _screenShareAudioOwnerIdentity(participantIdentity) === this._watchedScreenShareIdentity;
      return isOwnAux || !isWatched ? 0.0 : this._screenShareVolume;
    }
    if (participantIdentity === musicBoxBotIdentity) return this._musicBoxVolume;
    const baseVolume = this._outputMuted ? 0.0 : this._outputVolume;
    return normalizedParticipantVoiceVolume(baseVolume * this.participantVoiceVolume(participantIdentity));
  }

  private _detachRemoteAudio(track: RemoteTrack): void {
    if (!(track instanceof RemoteAudioTrack)) return;
    try {
      this._remoteAudio.detach(track);
    } catch {
      // Already detached.
    }
  }

  private _onEvent(event: LiveRoomEvent): void {
    switch (event.type) {
      case 'reconnecting':
      case 'resuming': {
        this._androidMusicMediaRouteApplied = null;
        const room = this._room;
        if (this._participantIdentitiesBeforeReconnect == null) {
          const before = room == null ? new Set<string>() : this._presenceParticipantIdentities(room);
          if (event.type === 'reconnecting') {
            for (const identity of event.restartDepartures ?? []) {
              if (isLivePresenceSoundParticipantIdentity(identity)) before.add(identity);
            }
          }
          this._participantIdentitiesBeforeReconnect = before;
        }
        this._microphonePublishedBeforeReconnect ??=
          room?.localParticipant.getTrackPublication(Track.Source.Microphone) != null;
        if (event.type === 'resuming') {
          this._resumedSignalConnection = true;
        }
        this._reconnectRecoveryRevision += 1;
        this._suppressAutomaticMicReconcile = true;
        this._presenceCallbacksEnabled = false;
        // Speaking and per-publication mute state are media-connection facts,
        // not durable room state. A reconnect can replace every publication
        // without emitting a matching "stopped speaking" event, so drop the
        // remote cache immediately and let the server snapshot remain the
        // authoritative fallback until LiveKit has restored its publications.
        this._speakingIdentities.clear();
        this._micMutedByIdentity.clear();
        const local = this._room?.localParticipant ?? null;
        if (local != null) {
          this._micMutedByIdentity.set(local.identity, this.localMicMuted);
        }
        this.notifyListeners();
        return;
      }
      case 'reconnected': {
        // A full reconnect may restore already-published tracks without
        // replaying every participant/track event that normally applies these
        // local preferences. Reconcile them serially so WebRTC operations
        // cannot overlap one another or continue into a newer room connection.
        const room = this._room;
        if (room != null) {
          this._scheduleReconnectRecovery(room, {
            revision: this._reconnectRecoveryRevision,
            participantsBefore: this._participantIdentitiesBeforeReconnect ?? this._presenceParticipantIdentities(room),
            hadMicrophonePublication:
              this._microphonePublishedBeforeReconnect ??
              room.localParticipant.getTrackPublication(Track.Source.Microphone) != null,
            resumedSignalConnection: this._resumedSignalConnection,
          });
        }
        this.notifyListeners();
        return;
      }
      case 'activeSpeakersChanged': {
        this._speakingIdentities.clear();
        for (const p of event.speakers) {
          if (!_isScreenAudioAux(p.identity)) this._speakingIdentities.add(p.identity);
        }
        this.notifyListeners();
        return;
      }
      case 'participantConnected': {
        const identity = event.participant.identity;
        if (_isScreenAudioAux(identity)) {
          // An aux participant joining still means a new screen-audio track
          // may arrive; apply the volume so the track plays at the right level.
          unawaited(this._applyScreenShareVolume());
          return;
        }
        const micMuted = liveParticipantMicPublicationMuted(event.participant);
        if (micMuted == null) {
          this._micMutedByIdentity.delete(identity);
        } else {
          this._micMutedByIdentity.set(identity, micMuted);
        }
        unawaited(this._applyOutputVolume());
        unawaited(this._applyMusicBoxVolume());
        unawaited(this._applyScreenShareSubscriptions());
        unawaited(this._applyCameraSubscriptions());
        unawaited(this._applyScreenShareVolume());
        this.notifyListeners();
        if (this._presenceCallbacksEnabled && isLivePresenceSoundParticipantIdentity(identity)) {
          this.onParticipantJoined?.(identity);
        }
        return;
      }
      case 'participantDisconnected': {
        const identity = event.participant.identity;
        if (_isScreenAudioAux(identity)) {
          unawaited(this._applyScreenShareVolume());
          return;
        }
        this._micMutedByIdentity.delete(identity);
        this._speakingIdentities.delete(identity);
        unawaited(this._reconcileAndroidMusicBoxAudioRoute());
        this.notifyListeners();
        if (this._presenceCallbacksEnabled && isLivePresenceSoundParticipantIdentity(identity)) {
          this.onParticipantLeft?.(identity, liveParticipantDepartureKind(event.reason));
        }
        return;
      }
      case 'participantPermissionsUpdated': {
        // An admin mute_mic / restore_voice flips the local participant's
        // publish permission server-side. Headphone mute only changes
        // canSubscribe, so ignore permission events where canPublish did not
        // actually change.
        const local = this._room?.localParticipant ?? null;
        if (local != null && event.participant.identity === local.identity) {
          const next = event.canPublish;
          if (
            shouldApplyLivePublishPermissionUpdate({
              currentCanPublish: this._canPublish,
              oldCanPublish: event.oldCanPublish,
              nextCanPublish: next,
            })
          ) {
            this._canPublish = next;
            unawaited(this._applyLocalMicrophoneState());
            this.notifyListeners();
            this.onPublishPermissionChanged?.(next);
          }
        }
        return;
      }
      case 'localTrackSubscribed': {
        const publication = this._room?.localParticipant.trackPublications.get(event.trackSid);
        if (this._screenSharing && publication?.source === Track.Source.ScreenShare) {
          // With dynacast, an unobserved local share may produce no outbound
          // frames. The first subscriber activates the encoder, which is the
          // earliest reliable point for measuring and enforcing scaling.
          unawaited(this._applyScreenShareQualityWithRetries());
        }
        return;
      }
      case 'trackLifecycle': {
        // Video subscription / publish lifecycle: rebuild the rendered track
        // list and re-apply local audio preferences to new tracks.
        this._androidMusicMediaRouteApplied = null;
        if (!this._suppressAutomaticMicReconcile) {
          unawaited(this._applyLocalMicrophoneState());
        }
        unawaited(this._applyOutputVolume());
        unawaited(this._applyMusicBoxVolume());
        unawaited(this._applyScreenShareVolume());
        this._refreshAllMicStates();
        // A local screen-share track can be stopped from the OS (the shared
        // window closing, a system "stop sharing" control); keep our flag
        // honest.
        const wasScreenSharing = this._screenSharing;
        this._screenSharing = this._localHasScreenShare();
        if (wasScreenSharing && !this._screenSharing) {
          this._invalidateScreenShareQualityApplication({ resetSource: true });
          unawaited(this._stopScreenAudioPublisher());
          // A reconnect republish briefly unpublishes the share while its
          // capture keeps running; only an ended capture releases the audio.
          if (this._screenShareCapture?.video.readyState === 'ended') {
            this._releaseScreenShareCapture();
          }
        }
        if (
          this._watchedScreenShareIdentity != null &&
          !this._remoteScreenSharePublicationExists(this._watchedScreenShareIdentity)
        ) {
          this._watchedScreenShareIdentity = null;
        }
        if (this._watchedCameraIdentity != null && !this._remoteCameraPublicationExists(this._watchedCameraIdentity)) {
          this._watchedCameraIdentity = null;
        }
        unawaited(this._applyScreenShareSubscriptions());
        unawaited(this._applyCameraSubscriptions());
        unawaited(this._applyScreenShareVolume());
        this.notifyListeners();
        return;
      }
      case 'disconnected': {
        this._presenceCallbacksEnabled = false;
        this._participantIdentitiesBeforeReconnect = null;
        this._microphonePublishedBeforeReconnect = null;
        this._resumedSignalConnection = false;
        this._reconnectRecoveryRevision += 1;
        this._suppressAutomaticMicReconcile = false;
        this._invalidateScreenShareQualityApplication({ resetSource: true });
        this._screenSharing = false;
        this._watchedScreenShareIdentity = null;
        this._watchedCameraIdentity = null;
        this._localMicrophonePublishUnavailable = false;
        unawaited(this._stopScreenAudioPublisher());
        this._releaseScreenShareCapture();
        this._remoteAudio.detachAll();
        this._speakingIdentities.clear();
        this._micMutedByIdentity.clear();
        this.notifyListeners();
        // An admin kick force-disconnects us via RemoveParticipant; LiveKit
        // reports it as PARTICIPANT_REMOVED. Distinguish it from our own
        // disconnect() (which cancels the room listener before the event can
        // fire, so this handler never runs for a voluntary leave) and from
        // transient network drops, so the UI can exit the voice panel without
        // auto-reconnecting into a room we were just removed from.
        if (_isForcedRemovalDisconnectReason(event.reason)) {
          this.onForciblyRemoved?.();
        } else if (_shouldRequestReconnectForDisconnectReason(event.reason)) {
          // Do not fight another device that intentionally replaced this
          // account's LiveKit identity. Ordinary network/server failures are
          // recoverable with a fresh app-level join.
          this.onUnexpectedlyDisconnected?.();
        }
        return;
      }
    }
  }

  private _localHasScreenShare(): boolean {
    const local = this._room?.localParticipant ?? null;
    if (local == null) return false;
    for (const pub of local.videoTrackPublications.values()) {
      if (pub.source === Track.Source.ScreenShare && pub.track != null) return true;
    }
    return false;
  }

  private _presenceParticipantIdentities(room: Room): Set<string> {
    const identities = new Set<string>();
    for (const participant of room.remoteParticipants.values()) {
      if (isLivePresenceSoundParticipantIdentity(participant.identity)) identities.add(participant.identity);
    }
    return identities;
  }

  private _scheduleReconnectRecovery(
    reconnectedRoom: Room,
    opts: {
      revision: number;
      participantsBefore: ReadonlySet<string>;
      hadMicrophonePublication: boolean;
      resumedSignalConnection: boolean;
    },
  ): void {
    const { revision, participantsBefore, hadMicrophonePublication, resumedSignalConnection } = opts;
    const previous = this._reconnectRecoveryTail;
    const recovery = (async () => {
      try {
        await previous;
      } catch {
        // Ordering only.
      }
      if (this._room !== reconnectedRoom || revision !== this._reconnectRecoveryRevision) {
        return;
      }
      await this._reconcileMediaAfterReconnect(reconnectedRoom, { hadMicrophonePublication, resumedSignalConnection });
      if (this._room !== reconnectedRoom || revision !== this._reconnectRecoveryRevision) {
        return;
      }

      const changes = liveReconnectPresenceChanges({
        before: participantsBefore,
        after: this._presenceParticipantIdentities(reconnectedRoom),
      });
      this._participantIdentitiesBeforeReconnect = null;
      this._microphonePublishedBeforeReconnect = null;
      this._resumedSignalConnection = false;
      this._presenceCallbacksEnabled = true;
      this._suppressAutomaticMicReconcile = false;
      for (const identity of changes.left) {
        this.onParticipantLeft?.(identity, LiveParticipantDepartureKind.left);
      }
      for (const identity of changes.joined) {
        this.onParticipantJoined?.(identity);
      }
      this._refreshAllMicStates();
      this.notifyListeners();
    })();
    this._reconnectRecoveryTail = recovery.catch(() => {
      if (this._room === reconnectedRoom && revision === this._reconnectRecoveryRevision) {
        this._participantIdentitiesBeforeReconnect = null;
        this._microphonePublishedBeforeReconnect = null;
        this._resumedSignalConnection = false;
        this._presenceCallbacksEnabled = true;
        this._suppressAutomaticMicReconcile = false;
        this._refreshAllMicStates();
        this.notifyListeners();
      }
    });
  }

  private async _reconcileMediaAfterReconnect(
    reconnectedRoom: Room,
    opts: { hadMicrophonePublication: boolean; resumedSignalConnection: boolean },
  ): Promise<void> {
    if (this._room !== reconnectedRoom) return;
    const local = reconnectedRoom.localParticipant;
    const previousCanPublish = this._canPublish;
    this._canPublish = local.permissions?.canPublish ?? this._canPublish;
    if (previousCanPublish !== this._canPublish) {
      this.onPublishPermissionChanged?.(this._canPublish);
    }
    if (
      shouldRepublishLocalMicrophoneAfterReconnect({
        resumedSignalConnection: opts.resumedSignalConnection,
        hadPublicationBeforeReconnect: opts.hadMicrophonePublication,
        canPublish: this._canPublish,
      })
    ) {
      const microphoneRecovered = await this._republishLocalMicrophoneAfterReconnect(reconnectedRoom, local);
      if (
        !microphoneRecovered &&
        this._room === reconnectedRoom &&
        reconnectedRoom.localParticipant === local &&
        this._canPublish
      ) {
        // The subscriber can recover while the publisher remains wedged. A
        // bounded republish failure is not a usable voice connection: replace
        // the whole LiveKit session so the app-level reconnect obtains a new
        // token, participant SID and publisher PeerConnection.
        await this._disconnect({ clearWatchedTargets: false });
        this.onUnexpectedlyDisconnected?.();
        return;
      }
    } else {
      await this._applyLocalMicrophoneState();
    }
    if (this._room !== reconnectedRoom) return;
    this._screenSharing = this._localHasScreenShare();
    if (this._screenSharing) {
      unawaited(this._applyScreenShareQualityWithRetries());
    }
    if (this._screenSharing && this._screenAudioPublisher?.isPublishing !== true) {
      try {
        await this._stopScreenAudioPublisher();
        if (this._room === reconnectedRoom) await this._startScreenAudioPublisher();
      } catch {
        // The video share remains usable when isolated system-audio recovery
        // fails. A later explicit share can retry with a fresh aux session.
      }
    }
    if (this._room !== reconnectedRoom) return;
    await this._applyOutputVolume();
    if (this._room !== reconnectedRoom) return;
    await this._applyMusicBoxVolume();
    if (this._room !== reconnectedRoom) return;
    await this._applyScreenShareSubscriptions();
    if (this._room !== reconnectedRoom) return;
    await this._applyCameraSubscriptions();
    if (this._room !== reconnectedRoom) return;
    await this._applyScreenShareVolume();
  }

  /**
   * PORT: Dart removed the publication and published a freshly captured
   * track. livekit-client can republish the *same* LocalAudioTrack, which
   * still creates a new sender/transceiver and renegotiates (the point of the
   * recovery) without re-opening the microphone or dropping the gain stage;
   * a fresh capture is used only when the old one has ended.
   */
  private async _republishLocalMicrophoneAfterReconnect(
    reconnectedRoom: Room,
    local: LocalParticipant,
  ): Promise<boolean> {
    let attemptNumber = 0;
    const recovered = await retryLiveReconnectMicrophoneRecovery({
      isCurrent: () => this._room === reconnectedRoom && reconnectedRoom.localParticipant === local && this._canPublish,
      attempt: async () => {
        attemptNumber += 1;
        try {
          const publication = local.getTrackPublication(Track.Source.Microphone);
          const previousTrack = publication?.audioTrack ?? null;
          const reusable = previousTrack != null && previousTrack.mediaStreamTrack.readyState !== 'ended';
          if (previousTrack != null) {
            await local.unpublishTrack(previousTrack, !reusable);
          }
          if (this._room !== reconnectedRoom || reconnectedRoom.localParticipant !== local || !this._canPublish) {
            return false;
          }
          if (reusable) {
            await this._inputGain.apply(previousTrack, this._effectiveInputVolume);
            await local.publishTrack(previousTrack, publication?.options ?? { source: Track.Source.Microphone });
          } else {
            await this._enableMicrophone(local);
          }
          if (this._room !== reconnectedRoom || reconnectedRoom.localParticipant !== local) {
            return false;
          }
          const recoveredPublication = local.getTrackPublication(Track.Source.Microphone);
          if (recoveredPublication == null || recoveredPublication.track == null) {
            return false;
          }
          this._localMicrophonePublishUnavailable = false;
          await this._applyLocalInputVolume();
          return true;
        } catch (error) {
          console.debug(`live reconnect: microphone recovery attempt ${attemptNumber} failed: ${String(error)}`);
          return false;
        }
      },
    });
    if (!recovered && this._room === reconnectedRoom) {
      this._markLocalMicrophonePublishUnavailable();
      this.notifyListeners();
    }
    return recovered;
  }

  private _refreshAllMicStates(): void {
    const room = this._room;
    if (room == null) return;
    const refreshed = new Map<string, boolean>();
    const local = room.localParticipant;
    refreshed.set(local.identity, this.localMicMuted);
    for (const p of room.remoteParticipants.values()) {
      if (_isScreenAudioAux(p.identity)) continue;
      const micMuted = liveParticipantMicPublicationMuted(p);
      if (micMuted != null) refreshed.set(p.identity, micMuted);
    }
    this._micMutedByIdentity.clear();
    for (const [identity, muted] of refreshed) this._micMutedByIdentity.set(identity, muted);
  }

  private get _shouldMuteLocalMicrophone(): boolean {
    return this._micMuted || this._inputVolume <= 0;
  }

  private get _effectiveInputVolume(): number {
    return !this._canPublish || this._shouldMuteLocalMicrophone ? 0.0 : this._inputVolume;
  }

  private _audioCaptureOptions(): AudioCaptureOptions {
    // A fresh object every call: livekit-client normalizes (mutates) the
    // capture options it is handed.
    return this._inputDeviceId == null ? {} : { deviceId: this._inputDeviceId };
  }

  private _markLocalMicrophonePublishUnavailable(): void {
    this._localMicrophonePublishUnavailable = true;
  }

  /**
   * Port of `local.setMicrophoneEnabled(true, audioCaptureOptions: ...)`.
   *
   * An existing publication is unmuted (Dart semantics). A new microphone is
   * captured first, given the input-gain stage at the current effective
   * volume, and only then published, so a muted user can never leak the raw
   * capture for the moment between publish and gain (Dart applied the global
   * input volume before publishing for the same reason). Concurrent calls
   * share one publish.
   */
  private _enableMicrophone(local: LocalParticipant): Promise<void> {
    if (local.getTrackPublication(Track.Source.Microphone) != null) {
      return local.setMicrophoneEnabled(true, this._audioCaptureOptions()).then(() => {});
    }
    const inFlight = this._microphonePublishInFlight;
    if (inFlight != null) return inFlight;
    const operation = (async () => {
      const tracks = await local.createTracks({ audio: this._audioCaptureOptions() });
      let microphone: LocalAudioTrack | null = null;
      for (const track of tracks) {
        if (microphone == null && track instanceof LocalAudioTrack) {
          microphone = track;
        } else {
          track.stop();
        }
      }
      if (microphone == null) throw new Error('microphone capture returned no audio track');
      try {
        await this._inputGain.apply(microphone, this._effectiveInputVolume);
        if (this._room?.localParticipant !== local) {
          microphone.stop();
          return;
        }
        await local.publishTrack(microphone, { source: Track.Source.Microphone });
      } catch (error) {
        microphone.stop();
        throw error;
      }
    })();
    this._microphonePublishInFlight = operation;
    const clear = (): void => {
      if (this._microphonePublishInFlight === operation) this._microphonePublishInFlight = null;
    };
    operation.then(clear, clear);
    return operation;
  }

  /**
   * Start the hidden screen-audio aux participant. The aux room publishes
   * the screen-share loopback audio as its own participant, so screen audio
   * never shares a publication with the mic. Requires the main room to be
   * connected (for the resolved LiveKit URL + room name), a token provider to
   * have been injected, and the capture to have returned an audio track.
   */
  private async _startScreenAudioPublisher(): Promise<void> {
    // Browsers publish screen audio with the getDisplayMedia stream itself.
    if (HostPlatform.isWeb) return;
    if (this._screenAudioPublisher != null) return;
    const provider = this._screenAudioTokenProvider;
    const url = this._resolvedLiveKitUrl;
    const name = this._roomName;
    const audioTrack = this._screenShareCapture?.audio ?? null;
    console.debug(
      `screen-audio: _startScreenAudioPublisher provider=${provider != null} url=${url} roomName=${name} ` +
        `audio=${audioTrack != null}`,
    );
    // PORT: the host may return no loopback track (no system-audio support,
    // or the user picked a source without audio): then there is nothing to
    // publish and the share stays video-only.
    if (provider == null || url == null || name == null || audioTrack == null || audioTrack.readyState === 'ended') return;
    const publisher = new ScreenAudioPublisher({ tokenProvider: provider, audioTrack });
    this._screenAudioPublisher = publisher;
    try {
      await publisher.start({ liveKitUrl: url, roomName: name });
    } catch (error) {
      console.debug(`screen-audio: publisher start failed: ${String(error)}`);
      if (this._screenAudioPublisher === publisher) {
        this._screenAudioPublisher = null;
      }
      await publisher.stop();
      throw error;
    }
  }

  private async _stopScreenAudioPublisher(): Promise<void> {
    const publisher = this._screenAudioPublisher;
    this._screenAudioPublisher = null;
    if (publisher != null) {
      await publisher.stop();
    }
  }

  private async _applyLocalMicrophoneState(): Promise<void> {
    const local = this._room?.localParticipant ?? null;
    await this._applyLocalInputVolume();
    if (local == null) return;

    let microphonePublication = local.getTrackPublication(Track.Source.Microphone);
    if (
      microphonePublication == null &&
      !this._shouldMuteLocalMicrophone &&
      this._canPublish &&
      !this._localMicrophonePublishUnavailable
    ) {
      try {
        await this._enableMicrophone(local);
        microphonePublication = local.getTrackPublication(Track.Source.Microphone);
      } catch {
        this._markLocalMicrophonePublishUnavailable();
        return;
      }
    }
    if (microphonePublication == null) return;

    // Keep the LiveKit track enabled. Local mute is represented by the
    // app-level live state plus capture gain 0, not by muting the track.
    if (this._canPublish && microphonePublication.isMuted) {
      try {
        await local.setMicrophoneEnabled(true, this._audioCaptureOptions());
      } catch {
        // Best-effort.
      }
    }
    await this._applyLocalInputVolume();
  }

  private async _applyLocalInputVolume(): Promise<void> {
    const track = this._room?.localParticipant.getTrackPublication(Track.Source.Microphone)?.audioTrack;
    if (track == null) return;
    try {
      await this._inputGain.apply(track, this._effectiveInputVolume);
    } catch {
      // Best-effort.
    }
  }

  /**
   * PORT: no-op. Dart reset the process-wide native input gain to 1.0 after
   * a call; the gain stage here belongs to the call's own microphone track
   * and is destroyed with it.
   */
  private async _resetLocalInputVolume(): Promise<void> {}

  private async _applyOutputVolume(): Promise<void> {
    const room = this._room;
    if (room == null) return;
    const participants = [...room.remoteParticipants.values()];
    for (const participant of participants) {
      // The music box bot has its own independent volume knob; leave it to
      // _applyMusicBoxVolume so the two don't fight over the same track.
      if (participant.identity === musicBoxBotIdentity) continue;
      await this._applyParticipantVoiceVolumeToParticipant(participant);
    }
  }

  private async _applyParticipantVoiceVolume(userId: string): Promise<void> {
    const participant = this._room?.remoteParticipants.get(userId);
    if (participant == null || participant.identity === musicBoxBotIdentity) {
      return;
    }
    await this._applyParticipantVoiceVolumeToParticipant(participant);
  }

  private async _applyParticipantVoiceVolumeToParticipant(participant: RemoteParticipant): Promise<void> {
    const baseVolume = this._outputMuted ? 0.0 : this._outputVolume;
    const volume = normalizedParticipantVoiceVolume(baseVolume * this.participantVoiceVolume(participant.identity));
    for (const pub of participant.audioTrackPublications.values()) {
      if (pub.source === Track.Source.ScreenShareAudio) continue;
      _setRemoteAudioVolume(pub, volume);
    }
  }

  private async _applyMusicBoxVolume(): Promise<void> {
    await this._reconcileAndroidMusicBoxAudioRoute();
    const room = this._room;
    if (room == null) return;
    const participants = [...room.remoteParticipants.values()];
    for (const participant of participants) {
      if (participant.identity !== musicBoxBotIdentity) continue;
      for (const pub of participant.audioTrackPublications.values()) {
        _setRemoteAudioVolume(pub, this._musicBoxVolume);
      }
    }
  }

  /**
   * PORT: Android-only on Flutter (switches the native WebRTC playback
   * between communication and media routing while the music box plays, see
   * [androidLiveAudioConfiguration]). Desktop Chromium has no such routing;
   * a React Native app would apply the configuration via
   * `@livekit/react-native`'s `AudioSession` here.
   */
  private _reconcileAndroidMusicBoxAudioRoute(): Promise<void> {
    if (HostPlatform.isWeb || !HostPlatform.isAndroid) return Promise.resolve();
    this._androidMusicMediaRouteApplied = this._hasActiveMusicBoxAudioTrack();
    return Promise.resolve();
  }

  private _hasActiveMusicBoxAudioTrack(): boolean {
    const participant = this._room?.remoteParticipants.get(musicBoxBotIdentity);
    if (participant == null) return false;
    for (const publication of participant.audioTrackPublications.values()) {
      if (publication.track != null && !publication.isMuted) return true;
    }
    return false;
  }

  private async _applyScreenShareVolume(): Promise<void> {
    const room = this._room;
    if (room == null) return;
    const localIdentity = room.localParticipant.identity;
    const participants = [...room.remoteParticipants.values()];
    for (const participant of participants) {
      // Self-echo suppression: the local user's own screen-audio aux
      // participant (`<ownId>--screen-audio`) is publishing audio the user
      // is already hearing locally (it's their own screen). Mute it to 0 so
      // it never plays back to them. Other users' aux tracks play at the
      // screen-share volume.
      const isOwnAux = _isOwnScreenAudioAux(participant.identity, localIdentity);
      const ownerIdentity = _screenShareAudioOwnerIdentity(participant.identity);
      const isWatched = this._watchedScreenShareIdentity != null && ownerIdentity === this._watchedScreenShareIdentity;
      for (const pub of participant.audioTrackPublications.values()) {
        if (pub.source !== Track.Source.ScreenShareAudio) continue;
        _setRemoteAudioVolume(pub, isOwnAux || !isWatched ? 0.0 : this._screenShareVolume);
      }
    }
  }

  private _applyScreenShareSubscriptions(): Promise<void> {
    return this._screenShareSubscriptionReconciler.schedule((isCurrent) =>
      this._reconcileVideoSubscriptions({
        source: Track.Source.ScreenShare,
        watchedIdentity: this._watchedScreenShareIdentity,
        isCurrent,
      }),
    );
  }

  private _applyCameraSubscriptions(): Promise<void> {
    return this._cameraSubscriptionReconciler.schedule((isCurrent) =>
      this._reconcileVideoSubscriptions({
        source: Track.Source.Camera,
        watchedIdentity: this._watchedCameraIdentity,
        isCurrent,
      }),
    );
  }

  private async _reconcileVideoSubscriptions(opts: {
    source: Track.Source;
    watchedIdentity: string | null;
    isCurrent: () => boolean;
  }): Promise<void> {
    const { source, watchedIdentity, isCurrent } = opts;
    const room = this._room;
    if (room == null) return;
    const participants = [...room.remoteParticipants.values()];
    for (const participant of participants) {
      if (!isCurrent() || this._room !== room) return;
      if (_isScreenAudioAux(participant.identity)) continue;
      for (const pub of participant.videoTrackPublications.values()) {
        if (!isCurrent() || this._room !== room) return;
        if (pub.source !== source) continue;
        const shouldSubscribe = watchedIdentity != null && participant.identity === watchedIdentity;
        try {
          // `setSubscribed` always sends a signal message; skip publications
          // already in the desired state.
          if (pub.isDesired !== shouldSubscribe) pub.setSubscribed(shouldSubscribe);
        } catch {
          // Best-effort.
        }
      }
    }
  }

  private _remoteScreenSharePublicationExists(identity: string): boolean {
    const participant = this._room?.remoteParticipants.get(identity);
    if (participant == null) return false;
    for (const pub of participant.videoTrackPublications.values()) {
      if (pub.source === Track.Source.ScreenShare) return true;
    }
    return false;
  }

  private _remoteCameraPublicationExists(identity: string): boolean {
    const participant = this._room?.remoteParticipants.get(identity);
    if (participant == null) return false;
    for (const pub of participant.videoTrackPublications.values()) {
      if (pub.source === Track.Source.Camera) return true;
    }
    return false;
  }

  /**
   * Re-apply every track volume after the output endpoint changed. Invoked
   * by the output rebinder after the OS swaps the default output (the
   * Bluetooth A2DP/HFP profile flip).
   */
  async _reapplyAudioRouting(): Promise<void> {
    if (this._room == null) return;
    await this._applyOutputVolume();
    await this._applyMusicBoxVolume();
    await this._applyScreenShareVolume();
  }

  /**
   * Routes room playback to [deviceId]. Port of `rtc.Helper.selectAudioOutput`;
   * re-selecting the same id re-binds playout after an endpoint rebuild.
   */
  async _selectAudioOutputDevice(deviceId: string): Promise<void> {
    const room = this._room;
    if (room == null) return;
    await room.switchActiveDevice('audiooutput', deviceId);
  }

  /** Follows Settings' output picker (`LiveHardware.selectAudioOutput`) while connected. */
  private _startFollowingAudioOutputSelection(): void {
    this._stopFollowingAudioOutputSelection();
    this._outputSelectionSubscription = LiveHardware.instance.audioOutputSelections.listen((device) => {
      if (device == null) return;
      unawaited(
        this._selectAudioOutputDevice(device.deviceId).catch((error: unknown) => {
          console.debug(`live: audio output switch failed: ${String(error)}`);
        }),
      );
    });
  }

  private _stopFollowingAudioOutputSelection(): void {
    this._outputSelectionSubscription?.cancel();
    this._outputSelectionSubscription = null;
  }

  private _startAudioDeviceRebinder(): void {
    this._stopAudioDeviceRebinder();
    const cameraReconciler = this._cameraDeviceReconcilerFactory?.() ?? null;
    this._cameraDeviceReconciler = cameraReconciler;
    cameraReconciler?.start();
    const combinedFactory = this._audioDeviceRebinderFactory;
    if (combinedFactory != null) {
      const rebinder = combinedFactory(this);
      this._audioDeviceRebinder = rebinder;
      rebinder?.start();
      return;
    }
    // Compatibility path for focused tests that inject the former split
    // coordinators. Production always uses the combined coordinator.
    this._startInputRebinder();
    this._startOutputRebinder();
  }

  private _stopAudioDeviceRebinder(): void {
    unawaited(this._stopAudioDeviceRebinderAndWait());
  }

  private async _stopAudioDeviceRebinderAndWait(): Promise<void> {
    const rebinder = this._audioDeviceRebinder;
    this._audioDeviceRebinder = null;
    const inputRebinder = this._inputRebinder;
    this._inputRebinder = null;
    const outputRebinder = this._outputRebinder;
    this._outputRebinder = null;
    const cameraReconciler = this._cameraDeviceReconciler;
    this._cameraDeviceReconciler = null;
    await Promise.all([rebinder?.stop(), inputRebinder?.stop(), outputRebinder?.stop(), cameraReconciler?.stop()]);
  }

  private _startInputRebinder(): void {
    this._stopInputRebinder();
    const rebinder = this._inputRebinderFactory?.(this) ?? null;
    this._inputRebinder = rebinder;
    rebinder?.start();
  }

  private _stopInputRebinder(): void {
    const rebinder = this._inputRebinder;
    this._inputRebinder = null;
    if (rebinder != null) unawaited(rebinder.stop());
  }

  private _startOutputRebinder(): void {
    this._stopOutputRebinder();
    const rebinder = this._outputRebinderFactory?.(this) ?? null;
    this._outputRebinder = rebinder;
    rebinder?.start();
  }

  private _stopOutputRebinder(): void {
    const rebinder = this._outputRebinder;
    this._outputRebinder = null;
    if (rebinder != null) unawaited(rebinder.stop());
  }

  /**
   * Drives the rebinder lifecycle without a live LiveKit connection, so the
   * device-recovery wiring can be exercised in tests. Production code reaches
   * these through [connect]/[disconnect].
   */
  debugStartInputRebinder(): void {
    this._startInputRebinder();
  }

  debugStopInputRebinder(): void {
    this._stopInputRebinder();
  }

  debugStartOutputRebinder(): void {
    this._startOutputRebinder();
  }

  debugStopOutputRebinder(): void {
    this._stopOutputRebinder();
  }

  debugStartAudioDeviceRebinder(): void {
    this._startAudioDeviceRebinder();
  }

  debugStopAudioDeviceRebinder(): void {
    this._stopAudioDeviceRebinder();
  }

  /**
   * Exercises reconnect/event cache reconciliation without a real LiveKit
   * transport. Production events reach the same handler through [connect].
   */
  debugHandleRoomEvent(event: LiveRoomEvent): void {
    this._onEvent(event);
  }
}

/**
 * PORT: livekit-client rebuilds a remote track's Web Audio gain node whenever
 * it re-wires the room's AudioContext (e.g. when a microphone is acquired)
 * and only re-applies the stored volume when it is truthy, so a plain 0
 * would snap back to full volume. Silence is therefore applied as an
 * inaudible (-100 dB) gain.
 */
const _silentRemoteAudioVolume = 1e-5;

function _setRemoteAudioVolume(publication: RemoteTrackPublication | TrackPublication, volume: number): void {
  const track = publication.track;
  if (!(track instanceof RemoteAudioTrack)) return;
  _setRemoteAudioTrackVolume(track, volume);
}

function _setRemoteAudioTrackVolume(track: RemoteAudioTrack, volume: number): void {
  try {
    track.setVolume(volume > 0 ? volume : _silentRemoteAudioVolume);
  } catch {
    // Best-effort.
  }
}

function _positiveScreenShareDimension(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) return Math.round(value);
  if (typeof value === 'string') {
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }
  return null;
}

/**
 * Capture constraints for the selected profile: the height is capped (the
 * width follows the source aspect ratio, like
 * [screenShareCappedOutputResolution]) and the capture rate is capped at the
 * selected frame rate.
 */
function _screenShareVideoConstraints(quality: ScreenShareQuality): MediaTrackConstraints {
  return {
    height: { max: quality.maxHeight },
    frameRate: { ideal: quality.maxFrameRate, max: quality.maxFrameRate },
  };
}

/**
 * System audio is music/game/video sound: no voice processing. Chromium's
 * `restrictOwnAudio` asks the capture to exclude this app's own playback
 * (other participants' voices), like the Flutter Windows process-loopback
 * exclusion; hosts that cannot honour it ignore the constraint.
 */
function _screenShareAudioConstraints(): MediaTrackConstraints {
  const constraints: MediaTrackConstraints & { restrictOwnAudio?: boolean } = {
    echoCancellation: false,
    noiseSuppression: false,
    autoGainControl: false,
    restrictOwnAudio: true,
  };
  return constraints;
}

/**
 * PORT: flutter_webrtc set no content hint. Chromium uses it to pick the
 * encoder's degradation policy: `detail` keeps resolution (text, slides,
 * code at 15/30 FPS), `motion` keeps frame rate (the 60 FPS profile for
 * games/video).
 */
function _screenShareContentHint(quality: ScreenShareQuality): 'detail' | 'motion' {
  return quality.maxFrameRate >= 60 ? 'motion' : 'detail';
}

// Default desktop recovery for endpoint hotplug/profile flips. One device
// change signal resolves one input/output snapshot and applies both
// directions serially. On Windows LiveAudioDeviceService uses
// SystemAudioDevices only.
function _defaultAudioDeviceRebinderFactory(
  session: LiveSession,
  opts: { audioDeviceStore: AudioDeviceStore | null },
): AudioDeviceRebinder | null {
  if (HostPlatform.isWeb || !(HostPlatform.isMacOS || HostPlatform.isWindows)) return null;
  const systemAudio = new SystemAudioDevices();
  const audioDevices = new LiveAudioDeviceService();
  return new AudioDeviceRebinder({
    deviceChanges: HostPlatform.isWindows
      ? _voidStream(systemAudio.changes)
      : _voidStream(LiveHardware.instance.onDeviceChange),
    resolvePreferredDevices: () =>
      preferredLiveAudioDevices({ audioDeviceStore: opts.audioDeviceStore, audioDevices, systemAudio }),
    rebindInput: (deviceId) => session._rebindInputDeviceId(deviceId),
    selectOutput: (deviceId) => session._selectAudioOutputDevice(deviceId),
    onOutputRebound: () => session._reapplyAudioRouting(),
  });
}

function _defaultCameraDeviceReconcilerFactory(): CameraDeviceReconciler | null {
  if (HostPlatform.isWeb || !HostPlatform.isWindows) return null;
  const systemAudio = new SystemAudioDevices();
  return new CameraDeviceReconciler({
    deviceChanges: systemAudio.videoChanges,
    refreshDevices: async () => {
      await LiveHardware.instance.refreshDevices();
    },
  });
}

/** Dart `stream.map((_) {})`. */
function _voidStream<T>(source: EventStream<T>): EventStream<void> {
  return {
    listen(onData, options) {
      return source.listen(() => onData(), options);
    },
  };
}

export class LiveSessionConnectException extends Error {
  constructor(opts: { url: string; cause: unknown }) {
    super('无法连接语音服务');
    this.name = 'LiveSessionConnectException';
    this.url = opts.url;
    this.cause = opts.cause;
  }

  readonly url: string;
  override readonly cause: unknown;

  override toString(): string {
    return '无法连接语音服务';
  }
}

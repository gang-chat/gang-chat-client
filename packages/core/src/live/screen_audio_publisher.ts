/**
 * Port of `lib/src/live/screen_audio_publisher.dart`.
 */
import { LocalAudioTrack, Room, Track } from 'livekit-client';
import { withTimeout } from '../lib/async';
import type { ScreenAudioToken } from '../protocol/models';

/**
 * Fetches a publish-only LiveKit token for the hidden screen-audio aux
 * participant. Implemented by the app layer (wired to
 * `GangApi.issueScreenAudioToken`), keeping this class decoupled from the API
 * client.
 */
export type ScreenAudioTokenProvider = (roomId: string) => Promise<ScreenAudioToken>;

/** Test seam (`@visibleForTesting` in Dart). */
export interface ScreenAudioPublisherBackend {
  connect(opts: { liveKitUrl: string; token: string }): Promise<void>;

  publish(): Promise<void>;

  stop(): Promise<void>;
}

export type ScreenAudioPublisherBackendFactory = () => ScreenAudioPublisherBackend;

/**
 * Publishes screen-share audio as an independent `Track.Source.ScreenShareAudio`
 * track through a *second* LiveKit [Room].
 *
 * The aux participant joins with identity `<ownerId>--screen-audio`, is
 * publish-only (`canSubscribe=false`), and never appears in the roster (no
 * `live_participants` row is created for it). Receivers merge its audio into
 * the owner's `screenShareVolume` via `LiveSession._applyScreenShareVolume`,
 * which iterates all `screenShareAudio` publications across all remote
 * participants.
 *
 * PORT: on Flutter the aux room ran on an isolated native WebRTC factory fed
 * by ScreenCaptureKit / WASAPI loopback, because sharing the microphone
 * factory's AudioState crashed WebRTC (`audio_send_stream.cc:393`). Chromium
 * has no such shared state; the aux participant is kept so receivers (and
 * the mixed Flutter/Electron fleet) see the same room topology, and its
 * audio is the loopback track returned by the screen-share `getDisplayMedia`
 * call ([audioTrack]). The publisher publishes a clone, so stopping it never
 * ends the capture the session still owns.
 */
export class ScreenAudioPublisher {
  constructor(opts: {
    tokenProvider: ScreenAudioTokenProvider;
    audioTrack?: MediaStreamTrack | null;
    backendFactory?: ScreenAudioPublisherBackendFactory;
  }) {
    this.tokenProvider = opts.tokenProvider;
    const audioTrack = opts.audioTrack ?? null;
    this._backendFactory = opts.backendFactory ?? (() => new _LiveKitScreenAudioPublisherBackend(audioTrack));
  }

  readonly tokenProvider: ScreenAudioTokenProvider;
  private readonly _backendFactory: ScreenAudioPublisherBackendFactory;

  private _backend: ScreenAudioPublisherBackend | null = null;
  private _startOperation: Promise<void> | null = null;
  private _publishing = false;
  private _stopRequested = false;

  static readonly _inFlightStopWait = 800;

  get isPublishing(): boolean {
    return this._publishing;
  }

  /**
   * Connects the aux room and publishes the screen-audio track.
   *
   * [liveKitUrl] is the resolved LiveKit server URL (same as the main room).
   * [roomName] is the LiveKit room name (== roomID). The aux token is fetched
   * on demand so it is always fresh regardless of how long the user has been
   * in the call.
   */
  start(opts: { liveKitUrl: string; roomName: string }): Promise<void> {
    if (this._stopRequested) return Promise.resolve();
    return (this._startOperation ??= this._runStart(opts));
  }

  private async _runStart(opts: { liveKitUrl: string; roomName: string }): Promise<void> {
    const { liveKitUrl, roomName } = opts;
    console.debug(`screen-audio: starting publisher (url=${liveKitUrl}, room=${roomName})`);
    const tokenResult = await this.tokenProvider(roomName);
    console.debug(`screen-audio: token acquired (identity=${tokenResult.identity})`);
    if (this._stopRequested) return;

    const backend = this._backendFactory();
    this._backend = backend;
    try {
      console.debug('screen-audio: connecting aux room...');
      await backend.connect({ liveKitUrl, token: tokenResult.token });
      console.debug('screen-audio: aux room connected');
      if (this._stopRequested) {
        await this._releaseBackend(backend);
        return;
      }

      console.debug('screen-audio: publishing track...');
      await backend.publish();
      if (this._stopRequested) {
        await this._releaseBackend(backend);
        return;
      }
      this._publishing = true;
      console.debug('screen-audio: track published successfully');
    } catch (error) {
      await this._releaseBackend(backend);
      if (!this._stopRequested) {
        throw error;
      }
    }
  }

  private async _releaseBackend(backend: ScreenAudioPublisherBackend): Promise<void> {
    if (this._backend !== backend) return;
    this._backend = null;
    this._publishing = false;
    await backend.stop();
  }

  /**
   * Cancels any in-flight start before releasing the aux room and track.
   *
   * Token acquisition can be arbitrarily slow, so a stop requested before a
   * backend exists returns immediately; `_runStart` observes the cancellation
   * before allocating resources. Once a backend exists, give the
   * connect/publish operation a bounded opportunity to reach a cancellation
   * checkpoint. If it takes longer, `_runStart` owns the eventual cleanup and
   * cannot mark the publisher active after this method returns.
   */
  async stop(): Promise<void> {
    this._stopRequested = true;
    const startOperation = this._startOperation;
    if (this._backend != null && startOperation != null) {
      try {
        await withTimeout(startOperation, ScreenAudioPublisher._inFlightStopWait);
      } catch {
        // TimeoutException: the in-flight operation stops its backend at the
        // next checkpoint. Any other error: a failed start already performed
        // its own best-effort cleanup.
      }
    }

    const backend = this._backend;
    this._startOperation = null;
    this._publishing = false;
    if (backend != null) {
      await this._releaseBackend(backend);
    }
  }
}

class _LiveKitScreenAudioPublisherBackend implements ScreenAudioPublisherBackend {
  constructor(private readonly _sourceTrack: MediaStreamTrack | null) {}

  private _room: Room | null = null;
  private _track: LocalAudioTrack | null = null;

  async connect(opts: { liveKitUrl: string; token: string }): Promise<void> {
    // PORT: Dart forced a DIRECT proxy for the signal connection
    // (`HttpOverrides.runZoned`). In Electron the main process owns the
    // session proxy configuration for the LiveKit hosts.
    const room = new Room({ adaptiveStream: false, dynacast: false });
    this._room = room;
    // Publish-only: the token denies subscribe anyway; do not even try.
    await room.connect(opts.liveKitUrl, opts.token, { autoSubscribe: false });
  }

  async publish(): Promise<void> {
    const local = this._room?.localParticipant;
    if (local == null) {
      throw new Error('screen-audio aux room connected without a local participant');
    }
    const source = this._sourceTrack;
    if (source == null || source.readyState === 'ended') {
      throw new Error('screen-share capture returned no audio track');
    }
    // A clone owned by this backend: livekit-client stops it on unpublish,
    // while the screen share's own capture keeps running.
    const track = new LocalAudioTrack(source.clone(), undefined, false);
    track.source = Track.Source.ScreenShareAudio;
    this._track = track;
    await local.publishTrack(track, { source: Track.Source.ScreenShareAudio, name: 'screen_share_audio' });
  }

  async stop(): Promise<void> {
    const room = this._room;
    const track = this._track;
    this._room = null;
    this._track = null;

    if (room != null) {
      try {
        await room.disconnect();
      } catch {
        // Best-effort teardown.
      }
    }
    if (track != null) {
      try {
        track.stop();
      } catch {
        // Already stopped by the room disconnect.
      }
    }
  }
}

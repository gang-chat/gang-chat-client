import type { Milliseconds } from '../lib/async';
import { ValueNotifier } from '../lib/change_notifier';
import type { StreamSubscription } from '../lib/event_stream';
import { PlayerState, UrlSource, createAudioPlayer } from './audio_player';
import type { AudioPlayer } from './audio_player';

export class VoicePlaybackSnapshot {
  constructor(
    opts: {
      activeMessageId?: string | null;
      playing?: boolean;
      position?: Milliseconds;
      duration?: Milliseconds;
    } = {},
  ) {
    this.activeMessageId = opts.activeMessageId ?? null;
    this.playing = opts.playing ?? false;
    this.position = opts.position ?? 0;
    this.duration = opts.duration ?? 0;
  }

  /**
   * The canonical `const VoicePlaybackSnapshot()`: reusing one instance keeps
   * [ValueNotifier] from notifying when the state is already idle, like Dart's
   * canonicalized const.
   */
  static readonly idle = new VoicePlaybackSnapshot();

  readonly activeMessageId: string | null;
  readonly playing: boolean;
  readonly position: Milliseconds;
  readonly duration: Milliseconds;

  isPlaying(messageId: string): boolean {
    return this.playing && this.activeMessageId === messageId;
  }
}

export class VoicePlaybackService {
  constructor(opts: { player?: AudioPlayer } = {}) {
    this._player = opts.player ?? createAudioPlayer();
    this._stateSubscription = this._player.onPlayerStateChanged.listen((s) => this._handleState(s));
    this._positionSubscription = this._player.onPositionChanged.listen((p) => this._handlePosition(p));
    this._durationSubscription = this._player.onDurationChanged.listen((d) => this._handleDuration(d));
    this._completeSubscription = this._player.onPlayerComplete.listen(() => {
      this.state.value = VoicePlaybackSnapshot.idle;
    });
  }

  private readonly _player: AudioPlayer;
  readonly state = new ValueNotifier<VoicePlaybackSnapshot>(VoicePlaybackSnapshot.idle);
  private _stateSubscription: StreamSubscription | null;
  private _positionSubscription: StreamSubscription | null;
  private _durationSubscription: StreamSubscription | null;
  private _completeSubscription: StreamSubscription | null;

  async toggle(opts: { messageId: string; resolvedUrl: string }): Promise<void> {
    if (this.state.value.isPlaying(opts.messageId)) {
      await this.stop();
      return;
    }
    await this.play(opts);
  }

  async play(opts: { messageId: string; resolvedUrl: string }): Promise<void> {
    const { messageId, resolvedUrl } = opts;
    await this._player.stop();
    this.state.value = new VoicePlaybackSnapshot({
      activeMessageId: messageId,
      playing: true,
      position: 0,
      duration: 0,
    });
    try {
      await this._player.play(new UrlSource(resolvedUrl));
    } catch (error) {
      if (this.state.value.activeMessageId === messageId) {
        this.state.value = VoicePlaybackSnapshot.idle;
      }
      throw error;
    }
  }

  async stop(): Promise<void> {
    await this._player.stop();
    this.state.value = VoicePlaybackSnapshot.idle;
  }

  async dispose(): Promise<void> {
    this._stateSubscription?.cancel();
    this._positionSubscription?.cancel();
    this._durationSubscription?.cancel();
    this._completeSubscription?.cancel();
    this._stateSubscription = null;
    this._positionSubscription = null;
    this._durationSubscription = null;
    this._completeSubscription = null;
    this.state.dispose();
    await this._player.dispose();
  }

  private _handlePosition(position: Milliseconds): void {
    const current = this.state.value;
    if (current.activeMessageId == null) return;
    this.state.value = new VoicePlaybackSnapshot({
      activeMessageId: current.activeMessageId,
      playing: current.playing,
      position,
      duration: current.duration,
    });
  }

  private _handleDuration(duration: Milliseconds): void {
    const current = this.state.value;
    if (current.activeMessageId == null) return;
    this.state.value = new VoicePlaybackSnapshot({
      activeMessageId: current.activeMessageId,
      playing: current.playing,
      position: current.position,
      duration,
    });
  }

  private _handleState(playerState: PlayerState): void {
    if (
      playerState === PlayerState.completed ||
      playerState === PlayerState.stopped ||
      playerState === PlayerState.disposed
    ) {
      this.state.value = VoicePlaybackSnapshot.idle;
    }
  }
}

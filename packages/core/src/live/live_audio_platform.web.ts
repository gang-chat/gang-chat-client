/**
 * Electron / browser implementation of the live audio seam (see
 * `live_audio_platform_types.ts`). React Native resolves
 * `./live_audio_platform` to the native `live_audio_platform.ts` instead.
 */
import type { AudioProcessorOptions, LocalAudioTrack, RemoteAudioTrack, Track, TrackProcessor } from 'livekit-client';
import type { LiveInputGain, LiveRemoteAudioRenderer, LiveTonePlayer } from './live_audio_platform_types';

export type { LiveInputGain, LiveRemoteAudioRenderer, LiveTonePlayer } from './live_audio_platform_types';

const _gainProcessorName = 'gang-chat-input-gain';

/**
 * Web Audio gain stage installed as a livekit-client [TrackProcessor]:
 * `MediaStreamAudioSourceNode -> GainNode -> MediaStreamAudioDestinationNode`.
 * The destination track is what the sender publishes. A capture restart
 * (device switch) reconnects a new source into the same graph, so the
 * published track never changes identity.
 */
class _InputGainProcessor implements TrackProcessor<Track.Kind.Audio, AudioProcessorOptions> {
  constructor(private _gain: number) {}

  readonly name = _gainProcessorName;
  processedTrack?: MediaStreamTrack;

  private _context: AudioContext | null = null;
  private _source: MediaStreamAudioSourceNode | null = null;
  private _gainNode: GainNode | null = null;
  private _destination: MediaStreamAudioDestinationNode | null = null;

  async init(opts: AudioProcessorOptions): Promise<void> {
    const context = opts.audioContext;
    if (context == null) throw new Error('input gain needs an AudioContext');
    this._context = context;
    this._gainNode = context.createGain();
    this._gainNode.gain.value = this._gain;
    this._destination = context.createMediaStreamDestination();
    this._gainNode.connect(this._destination);
    this._connectSource(opts.track);
    this.processedTrack = this._destination.stream.getAudioTracks()[0];
    await _resume(context);
  }

  async restart(opts: AudioProcessorOptions): Promise<void> {
    if (this._context == null || this._gainNode == null) {
      await this.init(opts);
      return;
    }
    this._connectSource(opts.track);
    await _resume(this._context);
  }

  async destroy(): Promise<void> {
    this._source?.disconnect();
    this._gainNode?.disconnect();
    this.processedTrack?.stop();
    this._source = null;
    this._gainNode = null;
    this._destination = null;
    this._context = null;
    this.processedTrack = undefined;
  }

  setGain(gain: number): void {
    this._gain = gain;
    const node = this._gainNode;
    const context = this._context;
    if (node == null || context == null) return;
    // Set immediately: a mute must not leak the next few hundred ms of audio.
    node.gain.cancelScheduledValues(context.currentTime);
    node.gain.setValueAtTime(gain, context.currentTime);
    if (context.state === 'suspended') void _resume(context);
  }

  private _connectSource(track: MediaStreamTrack): void {
    const context = this._context!;
    this._source?.disconnect();
    this._source = context.createMediaStreamSource(new MediaStream([track]));
    this._source.connect(this._gainNode!);
  }
}

async function _resume(context: AudioContext): Promise<void> {
  if (context.state !== 'suspended') return;
  try {
    await context.resume();
  } catch {
    // Autoplay policy: the next user gesture / gain change retries.
  }
}

class _WebInputGain implements LiveInputGain {
  private readonly _pending = new WeakMap<LocalAudioTrack, Promise<_InputGainProcessor | null>>();

  async apply(track: LocalAudioTrack, gain: number): Promise<void> {
    const processor = await this._ensureProcessor(track, gain);
    if (processor != null) {
      processor.setGain(gain);
      track.mediaStreamTrack.enabled = true;
      return;
    }
    // No Web Audio graph (no AudioContext): fall back to sending silence via
    // the track's enabled flag. Fractional gain is lost, mute still works.
    track.mediaStreamTrack.enabled = gain > 0;
  }

  private _ensureProcessor(track: LocalAudioTrack, gain: number): Promise<_InputGainProcessor | null> {
    const existing = track.getProcessor();
    if (existing instanceof _InputGainProcessor && existing.processedTrack != null) {
      return Promise.resolve(existing);
    }
    const pending = this._pending.get(track);
    if (pending != null) return pending;
    const next = (async () => {
      const processor = new _InputGainProcessor(gain);
      try {
        await track.setProcessor(processor);
        return processor;
      } catch {
        return null;
      } finally {
        this._pending.delete(track);
      }
    })();
    this._pending.set(track, next);
    return next;
  }
}

export function createLiveInputGain(): LiveInputGain {
  return new _WebInputGain();
}

/**
 * livekit-client never plays remote audio by itself: each subscribed audio
 * track is attached to a hidden `<audio>` element (routed through the room's
 * Web Audio mix when `webAudioMix` is on, which also allows >100% volume).
 */
class _WebRemoteAudioRenderer implements LiveRemoteAudioRenderer {
  private readonly _elements = new Map<RemoteAudioTrack, HTMLMediaElement>();

  attach(track: RemoteAudioTrack): void {
    if (this._elements.has(track)) return;
    const element = track.attach();
    element.style.display = 'none';
    if (typeof document !== 'undefined' && element.parentElement == null) {
      document.body.appendChild(element);
    }
    this._elements.set(track, element);
  }

  detach(track: RemoteAudioTrack): void {
    const element = this._elements.get(track);
    if (element == null) return;
    this._elements.delete(track);
    try {
      track.detach(element);
    } catch {
      // The track may already be stopped.
    }
    element.remove();
  }

  detachAll(): void {
    for (const track of [...this._elements.keys()]) this.detach(track);
  }
}

export function createLiveRemoteAudioRenderer(): LiveRemoteAudioRenderer {
  return new _WebRemoteAudioRenderer();
}

type _SinkAudioElement = HTMLAudioElement & { setSinkId?: (sinkId: string) => Promise<void> };

class _WebTonePlayer implements LiveTonePlayer {
  private _audio: _SinkAudioElement | null = null;
  private _objectUrl: string | null = null;

  async play(
    bytes: Uint8Array,
    opts: { mimeType: string; volume: number; loop: boolean; sinkId: string | null },
  ): Promise<void> {
    await this.dispose();
    const blob = new Blob([bytes.slice().buffer], { type: opts.mimeType });
    const url = URL.createObjectURL(blob);
    const audio: _SinkAudioElement = new Audio();
    this._audio = audio;
    this._objectUrl = url;
    audio.loop = opts.loop;
    audio.volume = Math.min(1, Math.max(0, opts.volume));
    if (opts.sinkId != null && opts.sinkId.length > 0 && audio.setSinkId != null) {
      try {
        await audio.setSinkId(opts.sinkId);
      } catch {
        // Unknown device: play through the default output instead.
      }
    }
    audio.src = url;
    await audio.play();
  }

  async setVolume(volume: number): Promise<void> {
    if (this._audio != null) this._audio.volume = Math.min(1, Math.max(0, volume));
  }

  async dispose(): Promise<void> {
    const audio = this._audio;
    const url = this._objectUrl;
    this._audio = null;
    this._objectUrl = null;
    if (audio != null) {
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
    }
    if (url != null) URL.revokeObjectURL(url);
  }
}

export function createLiveTonePlayer(): LiveTonePlayer {
  return new _WebTonePlayer();
}

/**
 * React Native version of the live audio seam (see
 * `live_audio_platform_types.ts`); Electron resolves `./live_audio_platform`
 * to `live_audio_platform.web.ts`.
 *
 * PORT: react-native-webrtc has no Web Audio, so:
 *  - input gain is binary: `MediaStreamTrack.enabled = false` sends silence
 *    while the capture keeps running (no LiveKit mute is signalled, matching
 *    the Dart "Option A" semantics); fractional gain needs a native module;
 *  - remote audio is played by the native WebRTC stack once subscribed, so
 *    attaching is a no-op;
 *  - the speaker-test tone is silent until a native player is wired in.
 */
import type { LocalAudioTrack, RemoteAudioTrack } from 'livekit-client';
import type { LiveInputGain, LiveRemoteAudioRenderer, LiveTonePlayer } from './live_audio_platform_types';

export type { LiveInputGain, LiveRemoteAudioRenderer, LiveTonePlayer } from './live_audio_platform_types';

class _NativeInputGain implements LiveInputGain {
  async apply(track: LocalAudioTrack, gain: number): Promise<void> {
    track.mediaStreamTrack.enabled = gain > 0;
  }
}

export function createLiveInputGain(): LiveInputGain {
  return new _NativeInputGain();
}

class _NativeRemoteAudioRenderer implements LiveRemoteAudioRenderer {
  attach(_track: RemoteAudioTrack): void {}
  detach(_track: RemoteAudioTrack): void {}
  detachAll(): void {}
}

export function createLiveRemoteAudioRenderer(): LiveRemoteAudioRenderer {
  return new _NativeRemoteAudioRenderer();
}

class _SilentTonePlayer implements LiveTonePlayer {
  async play(
    _bytes: Uint8Array,
    _opts: { mimeType: string; volume: number; loop: boolean; sinkId: string | null },
  ): Promise<void> {}

  async setVolume(_volume: number): Promise<void> {}

  async dispose(): Promise<void> {}
}

export function createLiveTonePlayer(): LiveTonePlayer {
  return new _SilentTonePlayer();
}

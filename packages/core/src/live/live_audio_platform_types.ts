/**
 * Browser-only audio pieces of the live layer, behind one platform seam.
 *
 * PORT: on Flutter the native WebRTC stack played remote audio by itself,
 * applied the app's microphone gain inside the audio-device module
 * (`Helper.setLocalAudioInputVolume`) and played the output-test tone through
 * audioplayers. In Chromium those need DOM / Web Audio APIs, which core only
 * touches from `*.web.ts` platform files: `live_audio_platform.web.ts`
 * implements these interfaces for Electron, `live_audio_platform.ts` is the
 * React Native version (`@livekit/react-native` plays remote audio natively).
 * The shared types live here because a `.web.ts` file cannot import its own
 * `.ts` sibling.
 */
import type { LocalAudioTrack, RemoteAudioTrack } from 'livekit-client';

/**
 * App-local microphone gain applied after capture (Dart "Option A" mute):
 * the capture device and the published LiveKit track stay live, only the
 * outgoing samples are scaled.
 */
export interface LiveInputGain {
  /** Applies [gain] (0..1) to the outgoing audio of [track], installing the gain stage when needed. */
  apply(track: LocalAudioTrack, gain: number): Promise<void>;
}

/** Plays subscribed remote audio tracks. */
export interface LiveRemoteAudioRenderer {
  attach(track: RemoteAudioTrack): void;
  detach(track: RemoteAudioTrack): void;
  detachAll(): void;
}

/** A looping test-tone player for the speaker test. */
export interface LiveTonePlayer {
  play(bytes: Uint8Array, opts: { mimeType: string; volume: number; loop: boolean; sinkId: string | null }): Promise<void>;
  setVolume(volume: number): Promise<void>;
  dispose(): Promise<void>;
}

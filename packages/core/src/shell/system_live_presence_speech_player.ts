import type { LivePresenceAnnouncement, LivePresenceSpeechPlayer } from '../app/live_presence_announcement';
import { delay } from '../lib/async';
import type { Milliseconds } from '../lib/async';
import { hasPlatform, platform } from '../platform/platform';
import type { SpeechBridge } from '../platform/platform';

/**
 * Free local TTS backed by the operating system's installed voices.
 *
 * It intentionally lives behind an app-layer protocol so room/live business
 * logic never depends on process APIs or a particular speech engine.
 *
 * PORT: the Flutter client drove each OS directly (Windows: PowerShell +
 * SAPI.SpVoice preferring a zh-CN/zh-TW voice (LCID 804/404), macOS:
 * `/usr/bin/say -v Ting-Ting` falling back to the default voice, Linux:
 * `spd-say --wait`, web: speechSynthesis, Android: a native channel). Here
 * every segment goes through `platform().speech` with a `zh-CN` language hint;
 * the host picks the voice. The pause between segments stays in this class.
 * See lib/src/shell/system_live_presence_speech_player.dart for the original
 * PowerShell script if the desktop host wants to reuse it.
 */
export class SystemLivePresenceSpeechPlayer implements LivePresenceSpeechPlayer {
  constructor(opts: { pause?: Milliseconds; speech?: SpeechBridge | null } = {}) {
    this.pause = opts.pause ?? 280;
    this._speech = opts.speech;
  }

  readonly pause: Milliseconds;
  /** `undefined` = resolve `platform().speech` lazily. */
  private readonly _speech: SpeechBridge | null | undefined;
  private _disposed = false;

  private get _bridge(): SpeechBridge | null {
    if (this._speech !== undefined) return this._speech;
    return hasPlatform() ? platform().speech : null;
  }

  async speak(announcement: LivePresenceAnnouncement, opts: { volume: number }): Promise<void> {
    const { volume } = opts;
    if (this._disposed || !(volume > 0)) return;
    const segments = announcement.segments.map((segment) => segment.trim()).filter((segment) => segment.length > 0);
    if (segments.length === 0) return;

    try {
      const speech = this._bridge;
      if (speech == null) return;
      const normalizedVolume = Math.min(1, Math.max(0, volume));
      for (let index = 0; index < segments.length; index += 1) {
        if (this._disposed) return;
        await speech.speak(segments[index]!, { volume: normalizedVolume, language: 'zh-CN' });
        if (index < segments.length - 1) await delay(this.pause);
      }
    } catch {
      // Presence speech is best-effort and must never interrupt live controls.
    }
  }

  async dispose(): Promise<void> {
    this._disposed = true;
  }
}

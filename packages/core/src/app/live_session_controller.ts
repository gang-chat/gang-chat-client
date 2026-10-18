import { Completer, Timer, delay } from '../lib/async';
import { restoreStoredAudioDevices } from '../live/audio_device_restorer';
import { resolveLiveKitServerUrl } from '../live/livekit_url';
import { LiveSession } from '../live/live_session';
import type { LiveParticipantDepartureKind, LiveVideoTrack, ScreenSource } from '../live/live_session';
import type { ScreenAudioTokenProvider } from '../live/screen_audio_publisher';
import { SystemAudioDevices } from '../live/system_audio_devices';
import type { LiveJoinResult } from '../protocol/models';
import { restoredAudioVolume } from './audio_device_state';
import { defaultInputVolume, defaultOutputVolume, type AudioDeviceStore } from './audio_device_store';
import { defaultParticipantVoiceVolume, normalizedParticipantVoiceVolume } from './audio_levels';

export type AudioDeviceRestorer = (store: AudioDeviceStore) => Promise<string | null>;

export class LiveSessionController {
  constructor(opts: {
    apiBaseUrl: string;
    audioDeviceStore: AudioDeviceStore;
    session?: LiveSession | null;
    audioDeviceRestorer?: AudioDeviceRestorer | null;
    screenAudioTokenProvider?: ScreenAudioTokenProvider | null;
  }) {
    this.apiBaseUrl = opts.apiBaseUrl;
    this.audioDeviceStore = opts.audioDeviceStore;
    this.session =
      opts.session ??
      new LiveSession({
        audioDeviceStore: opts.audioDeviceStore,
        screenAudioTokenProvider: opts.screenAudioTokenProvider ?? undefined,
      });
    this._audioDeviceRestorer =
      opts.audioDeviceRestorer ??
      (async (store) => {
        // On desktop the OS-selected mic is known via the native bridge;
        // pass it so a room join follows the system default when the user
        // has not pinned a device. Unsupported platforms return null.
        const systemAudio = new SystemAudioDevices();
        let systemDefaultInputId: string | null = null;
        let systemDefaultOutputId: string | null = null;
        try {
          const ids = await Promise.all([systemAudio.currentInputDeviceId(), systemAudio.currentOutputDeviceId()]);
          systemDefaultInputId = ids[0];
          systemDefaultOutputId = ids[1];
        } finally {
          await systemAudio.dispose();
        }
        const restored = await restoreStoredAudioDevices(store, {
          systemDefaultInputId,
          systemDefaultOutputId,
        });
        // The device the published mic should capture from: the resolved
        // input (stored preference or system default), else null to leave
        // LiveKit on the current device.
        return restored.input?.deviceId ?? systemDefaultInputId;
      });
  }

  readonly apiBaseUrl: string;
  readonly audioDeviceStore: AudioDeviceStore;
  readonly session: LiveSession;
  private readonly _audioDeviceRestorer: AudioDeviceRestorer;
  private readonly _restoredParticipantVoiceVolumeIds = new Set<string>();
  private readonly _restoringParticipantVoiceVolumeIds = new Set<string>();
  private readonly _participantVoiceVolumeBeforeMute = new Map<string, number>();

  listScreenSources(): Promise<ScreenSource[]> {
    return LiveSession.listScreenSources();
  }

  refreshScreenSourceThumbnails(): Promise<void> {
    return LiveSession.refreshScreenSourceThumbnails();
  }

  get isScreenSharing(): boolean {
    return this.session.isScreenSharing;
  }

  get canFlipCamera(): boolean {
    return this.session.canFlipCamera;
  }

  get isConnected(): boolean {
    return this.session.isConnected;
  }

  get localMicMuted(): boolean {
    return this.session.localMicMuted;
  }

  get roomName(): string | null {
    return this.session.roomName;
  }

  isAttachedToRoom(roomName: string): boolean {
    return this.session.isAttachedToRoom(roomName);
  }

  get speakingIdentities(): ReadonlySet<string> {
    return this.session.speakingIdentities;
  }

  get connectedParticipantIdentities(): ReadonlySet<string> {
    return this.session.connectedParticipantIdentities;
  }

  get micMutedByIdentity(): ReadonlyMap<string, boolean> {
    return this.session.micMutedByIdentity;
  }

  get videoTracks(): LiveVideoTrack[] {
    return this.session.videoTracks;
  }

  cameraFor(userId: string): LiveVideoTrack | null {
    return this.session.cameraFor(userId);
  }

  attachSessionCallbacks(opts: {
    onChanged: () => void;
    onForciblyRemoved: () => void;
    onUnexpectedlyDisconnected: () => void;
    onPublishPermissionChanged: (canPublish: boolean) => void;
    onParticipantJoined: (participantIdentity: string) => void;
    onParticipantLeft: (participantIdentity: string, departureKind: LiveParticipantDepartureKind) => void;
  }): void {
    const session = this.session;
    session.addListener(opts.onChanged);
    session.onForciblyRemoved = opts.onForciblyRemoved;
    session.onUnexpectedlyDisconnected = opts.onUnexpectedlyDisconnected;
    session.onPublishPermissionChanged = opts.onPublishPermissionChanged;
    session.onParticipantJoined = opts.onParticipantJoined;
    session.onParticipantLeft = opts.onParticipantLeft;
  }

  detachSessionCallbacks(opts: { onChanged: () => void }): void {
    const session = this.session;
    session.removeListener(opts.onChanged);
    session.onForciblyRemoved = null;
    session.onUnexpectedlyDisconnected = null;
    session.onPublishPermissionChanged = null;
    session.onParticipantJoined = null;
    session.onParticipantLeft = null;
  }

  setMicMuted(muted: boolean): Promise<void> {
    return this.session.setMicMuted(muted);
  }

  async setCameraEnabled(enabled: boolean): Promise<void> {
    await this.session.setCameraEnabled(enabled);
  }

  flipCamera(): Promise<boolean> {
    return this.session.flipCamera();
  }

  async setScreenShareEnabled(enabled: boolean, opts: { sourceId?: string | null } = {}): Promise<void> {
    await this.session.setScreenShareEnabled(enabled, { sourceId: opts.sourceId ?? undefined });
  }

  get inputVolume(): number {
    return this.session.inputVolume;
  }

  /**
   * Applies the microphone gain. Mute controls pass [persist] as false so a
   * transient muted gain does not become the next process' startup volume.
   */
  async setInputVolume(volume: number, opts: { persist?: boolean } = {}): Promise<void> {
    const { persist = true } = opts;
    await this.session.setInputVolume(volume);
    if (!persist) return;
    try {
      await this.audioDeviceStore.writeInputVolume(this.session.inputVolume);
    } catch {
      // A failed persist shouldn't undo the live change.
    }
  }

  /**
   * Pin the microphone capture device. Keeps [LiveSession]'s tracked input id
   * in sync so a later mute/unmute republish stays on the chosen device. The
   * system device is routed separately by the Settings picker's
   * selectAudioInput; this only keeps the LiveKit republish path consistent.
   * Persisted so a future room join restores the same device.
   */
  async setInputDeviceId(deviceId: string | null): Promise<void> {
    await this.session.setInputDeviceId(deviceId);
    try {
      if (deviceId != null) {
        await this.audioDeviceStore.writeInputDeviceId(deviceId);
      }
    } catch {
      // A failed persist shouldn't undo the live change.
    }
  }

  get outputVolume(): number {
    return this.session.outputVolume;
  }

  get inputVolumeBoost(): boolean {
    return this.session.inputVolumeBoost;
  }

  get outputVolumeBoost(): boolean {
    return this.session.outputVolumeBoost;
  }

  /** The 增强 switch on the input volume (Settings persists it itself, [persist] false). */
  async setInputVolumeBoost(boost: boolean, opts: { persist?: boolean } = {}): Promise<void> {
    const { persist = true } = opts;
    await this.session.setInputVolumeBoost(boost);
    if (!persist) return;
    try {
      await this.audioDeviceStore.writeInputVolumeBoost(boost);
    } catch {
      // A failed persist shouldn't undo the live change.
    }
  }

  /** The 增强 switch on the output volume. */
  async setOutputVolumeBoost(boost: boolean, opts: { persist?: boolean } = {}): Promise<void> {
    const { persist = true } = opts;
    await this.session.setOutputVolumeBoost(boost);
    if (!persist) return;
    try {
      await this.audioDeviceStore.writeOutputVolumeBoost(boost);
    } catch {
      // A failed persist shouldn't undo the live change.
    }
  }

  participantVoiceVolume(userId: string): number {
    return this.session.participantVoiceVolume(userId);
  }

  async restoreParticipantVoiceVolume(userId: string): Promise<boolean> {
    if (
      userId.length === 0 ||
      this._restoredParticipantVoiceVolumeIds.has(userId) ||
      this._restoringParticipantVoiceVolumeIds.has(userId)
    ) {
      return false;
    }
    this._restoringParticipantVoiceVolumeIds.add(userId);
    const before = this.session.participantVoiceVolume(userId);
    try {
      const volume = await this.audioDeviceStore.readParticipantVoiceVolume(userId);
      if (this._restoredParticipantVoiceVolumeIds.has(userId)) return false;
      await this.session.setParticipantVoiceVolume(userId, volume);
      const restored = this.session.participantVoiceVolume(userId);
      if (restored > 0) this._participantVoiceVolumeBeforeMute.set(userId, restored);
      this._restoredParticipantVoiceVolumeIds.add(userId);
      return restored !== before;
    } catch {
      this._restoredParticipantVoiceVolumeIds.add(userId);
      return false;
    } finally {
      this._restoringParticipantVoiceVolumeIds.delete(userId);
    }
  }

  /**
   * Applies the playout gain. Headphone mute is transient and must not
   * overwrite the user's last non-zero volume preference.
   */
  async setOutputVolume(volume: number, opts: { persist?: boolean } = {}): Promise<void> {
    const { persist = true } = opts;
    await this.session.setOutputVolume(volume);
    if (!persist) return;
    try {
      await this.audioDeviceStore.writeOutputVolume(this.session.outputVolume);
    } catch {
      // A failed persist shouldn't undo the live change.
    }
  }

  async setParticipantVoiceVolume(userId: string, volume: number): Promise<void> {
    await this.session.setParticipantVoiceVolume(userId, normalizedParticipantVoiceVolume(volume));
    const normalized = this.session.participantVoiceVolume(userId);
    if (normalized > 0) this._participantVoiceVolumeBeforeMute.set(userId, normalized);
    this._restoredParticipantVoiceVolumeIds.add(userId);
    try {
      await this.audioDeviceStore.writeParticipantVoiceVolume(userId, normalized);
    } catch {
      // A failed persist shouldn't undo the live change; it just won't survive
      // the next launch.
    }
  }

  toggleParticipantVoiceMuted(userId: string): Promise<void> {
    const current = this.session.participantVoiceVolume(userId);
    if (current <= 0) {
      return this.setParticipantVoiceVolume(
        userId,
        this._participantVoiceVolumeBeforeMute.get(userId) ?? defaultParticipantVoiceVolume,
      );
    }
    this._participantVoiceVolumeBeforeMute.set(userId, current);
    return this.setParticipantVoiceVolume(userId, 0);
  }

  get musicBoxVolume(): number {
    return this.session.musicBoxVolume;
  }

  async setMusicBoxVolume(volume: number): Promise<void> {
    await this.session.setMusicBoxVolume(volume);
    try {
      await this.audioDeviceStore.writeMusicBoxVolume(volume);
    } catch {
      // A failed persist shouldn't undo the live volume change; it just won't
      // survive the next launch.
    }
  }

  get screenShareVolume(): number {
    return this.session.screenShareVolume;
  }

  get watchedScreenShareIdentity(): string | null {
    return this.session.watchedScreenShareIdentity;
  }

  get watchedCameraIdentity(): string | null {
    return this.session.watchedCameraIdentity;
  }

  async setScreenShareVolume(volume: number): Promise<void> {
    await this.session.setScreenShareVolume(volume);
    try {
      await this.audioDeviceStore.writeScreenShareVolume(this.session.screenShareVolume);
    } catch {
      // A failed persist shouldn't undo the live change.
    }
  }

  setWatchedScreenShareIdentity(identity: string | null): Promise<void> {
    return this.session.setWatchedScreenShareIdentity(identity);
  }

  setWatchedCameraIdentity(identity: string | null): Promise<void> {
    return this.session.setWatchedCameraIdentity(identity);
  }

  get screenShareMaxHeight(): number {
    return this.session.screenShareMaxHeight;
  }

  get screenShareFrameRate(): number {
    return this.session.screenShareFrameRate;
  }

  /**
   * Apply and persist the screen-share resolution cap. Takes effect on the
   * next share, and re-scales the current share live when one is running.
   */
  async setScreenShareMaxHeight(height: number): Promise<void> {
    await this.session.setScreenShareMaxHeight(height);
    try {
      await this.audioDeviceStore.writeScreenShareMaxHeight(height);
    } catch {
      // A failed persist shouldn't undo the live change; it just won't survive
      // the next launch.
    }
  }

  async setScreenShareFrameRate(frameRate: number): Promise<void> {
    await this.session.setScreenShareFrameRate(frameRate);
    try {
      // Persist the platform-normalized value (Android currently caps capture
      // at 30 FPS) rather than an unsupported caller request.
      await this.audioDeviceStore.writeScreenShareFrameRate(this.session.screenShareFrameRate);
    } catch {
      // A failed persist shouldn't undo the live change.
    }
  }

  setOutputMuted(muted: boolean): Promise<void> {
    return this.session.setOutputMuted(muted);
  }

  async connectWithRetry(
    result: LiveJoinResult,
    opts: { isCancelled?: (() => boolean) | null; initialMicMuted?: boolean | null } = {},
  ): Promise<void> {
    const { isCancelled, initialMicMuted } = opts;
    let lastError: unknown = null;
    for (let attempt = 0; attempt < 2; attempt += 1) {
      if (attempt > 0) {
        await delay(650);
      }
      // Thrown as a plain string, like the Dart client.
      if (isCancelled?.() ?? false) throw '已取消加入直播';
      try {
        const liveKitUrl = resolveLiveKitServerUrl({
          serverUrl: result.liveKit.serverUrl,
          apiBaseUrl: this.apiBaseUrl,
        });
        await this.restoreStoredAudioSettings();
        await this.session.connect({
          url: liveKitUrl,
          token: result.liveKit.token,
          roomName: result.liveKit.roomName,
          // The join response can briefly contain the previous process'
          // participant snapshot. Prefer the state chosen for this join so an
          // old muted snapshot cannot suppress the initial mic publication.
          micMuted: initialMicMuted ?? result.participant.micMuted,
        });
        return;
      } catch (e) {
        lastError = e;
        await this.disconnect();
      }
    }
    throw lastError ?? 'LiveKit 连接失败';
  }

  async restoreStoredAudioSettings(): Promise<void> {
    try {
      const stored = await this.audioDeviceStore.read();
      // Older clients persisted the temporary zero gain used for mute/double
      // mute. The next process starts with mute controls open, so restoring
      // that zero would make the real media state contradict the UI. Heal the
      // legacy value once and keep persistence reserved for usable gains.
      const inputVolume = restoredAudioVolume(stored.inputVolume, defaultInputVolume);
      const outputVolume = restoredAudioVolume(stored.outputVolume, defaultOutputVolume);
      await this.session.setInputVolume(inputVolume);
      await this.session.setOutputVolume(outputVolume);
      await this.session.setInputVolumeBoost(stored.inputVolumeBoost);
      await this.session.setOutputVolumeBoost(stored.outputVolumeBoost);
      if (inputVolume !== stored.inputVolume) {
        try {
          await this.audioDeviceStore.writeInputVolume(inputVolume);
        } catch {
          // Runtime recovery must continue even if the one-time migration
          // cannot be persisted. A later join can safely retry it.
        }
      }
      if (outputVolume !== stored.outputVolume) {
        try {
          await this.audioDeviceStore.writeOutputVolume(outputVolume);
        } catch {
          // Keep restoring the remaining audio settings and selected devices.
        }
      }
      await this.session.setMusicBoxVolume(stored.musicBoxVolume);
      await this.session.setScreenShareVolume(stored.screenShareVolume);
      await this.session.setScreenShareMaxHeight(stored.screenShareMaxHeight);
      await this.session.setScreenShareFrameRate(stored.screenShareFrameRate);
      // Capture the published mic from the device the restorer resolved (the
      // user's pinned device, or the macOS system default). Null leaves LiveKit
      // on the current device. Without this the publish path ignores the
      // picker entirely, since the default capture options carry no deviceId.
      const inputDeviceId = await this._audioDeviceRestorer(this.audioDeviceStore);
      await this.session.setInputDeviceId(inputDeviceId);
    } catch {
      // Joining voice should still work with LiveKit's current/default device
      // if a stored local preference cannot be applied.
    }
  }

  async disconnect(opts: { timeout?: number | null } = {}): Promise<void> {
    const timeout = opts.timeout ?? null;
    const disconnect = this.session.disconnect().catch(() => {});
    if (timeout == null) {
      await disconnect;
      return;
    }
    const timeoutCompleter = new Completer<void>();
    const timeoutTimer = new Timer(timeout, () => {
      if (!timeoutCompleter.isCompleted) timeoutCompleter.complete();
    });
    try {
      await Promise.race([disconnect, timeoutCompleter.promise]);
    } catch {
      // ignored, like Dart's `catch (_) {}`
    }
    timeoutTimer.cancel();
  }

  dispose(): void {
    this.session.dispose();
  }
}

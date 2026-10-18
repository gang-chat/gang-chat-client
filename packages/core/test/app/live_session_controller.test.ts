import { describe, expect, it } from 'vitest';

import {
  StoredAudioDevices,
  defaultAudioVolume,
  defaultInputVolume,
  defaultOutputVolume,
} from '../../src/app/audio_device_preferences';
import { AudioDeviceStore } from '../../src/app/audio_device_store';
import { LiveSessionController } from '../../src/app/live_session_controller';
import { ChangeNotifier } from '../../src/lib/change_notifier';
import { LiveParticipantDepartureKind, type LiveSession } from '../../src/live/live_session';
import { LiveJoinResult, LiveKitConnectionInfo, LiveParticipant, LiveState, UserSummary } from '../../src/protocol/models';

class FakeAudioDeviceStore extends AudioDeviceStore {
  override async read(): Promise<StoredAudioDevices> {
    return new StoredAudioDevices({ inputVolume: 0.35, outputVolume: 0.75 });
  }
}

class RecordingAudioDeviceStore extends AudioDeviceStore {
  constructor(
    opts: {
      storedScreenShareMaxHeight?: number;
      storedScreenShareFrameRate?: number;
      storedInputVolume?: number;
      storedOutputVolume?: number;
      failVolumeWrites?: boolean;
      storedParticipantVoiceVolumes?: Record<string, number>;
    } = {},
  ) {
    super();
    this.storedScreenShareMaxHeight = opts.storedScreenShareMaxHeight ?? 720;
    this.storedScreenShareFrameRate = opts.storedScreenShareFrameRate ?? 30;
    this.storedInputVolume = opts.storedInputVolume ?? 0.35;
    this.storedOutputVolume = opts.storedOutputVolume ?? 0.75;
    this.failVolumeWrites = opts.failVolumeWrites ?? false;
    this._storedParticipantVoiceVolumes = new Map(Object.entries(opts.storedParticipantVoiceVolumes ?? {}));
  }

  readonly storedScreenShareMaxHeight: number;
  readonly storedScreenShareFrameRate: number;
  readonly storedInputVolume: number;
  readonly storedOutputVolume: number;
  readonly failVolumeWrites: boolean;
  private readonly _storedParticipantVoiceVolumes: Map<string, number>;
  readonly inputVolumeWrites: number[] = [];
  readonly outputVolumeWrites: number[] = [];
  readonly participantVoiceVolumeWrites: string[] = [];
  readonly screenShareVolumeWrites: number[] = [];
  readonly screenShareWrites: number[] = [];
  readonly screenShareFrameRateWrites: number[] = [];

  override async read(): Promise<StoredAudioDevices> {
    return new StoredAudioDevices({
      inputVolume: this.storedInputVolume,
      outputVolume: this.storedOutputVolume,
      screenShareVolume: 1.0,
      screenShareMaxHeight: this.storedScreenShareMaxHeight,
      screenShareFrameRate: this.storedScreenShareFrameRate,
    });
  }

  override async writeInputVolume(volume: number): Promise<void> {
    if (this.failVolumeWrites) throw new Error('input volume write failed');
    this.inputVolumeWrites.push(volume);
  }

  override async writeOutputVolume(volume: number): Promise<void> {
    if (this.failVolumeWrites) throw new Error('output volume write failed');
    this.outputVolumeWrites.push(volume);
  }

  override async readParticipantVoiceVolume(userId: string): Promise<number> {
    return this._storedParticipantVoiceVolumes.get(userId) ?? 1.0;
  }

  override async writeParticipantVoiceVolume(userId: string, volume: number): Promise<void> {
    this._storedParticipantVoiceVolumes.set(userId, volume);
    this.participantVoiceVolumeWrites.push(`${userId}:${volume.toFixed(2)}`);
  }

  override async writeScreenShareVolume(volume: number): Promise<void> {
    this.screenShareVolumeWrites.push(volume);
  }

  override async writeScreenShareMaxHeight(height: number): Promise<void> {
    this.screenShareWrites.push(height);
  }

  override async writeScreenShareFrameRate(frameRate: number): Promise<void> {
    this.screenShareFrameRateWrites.push(frameRate);
  }
}

/**
 * Port of the Dart `_FakeLiveSession extends LiveSession`: only the members
 * LiveSessionController touches exist, so it is handed over as a LiveSession.
 */
class FakeLiveSession extends ChangeNotifier {
  constructor(opts: { failFirstConnect?: boolean; hangDisconnect?: boolean } = {}) {
    super();
    this.failFirstConnect = opts.failFirstConnect ?? false;
    this.hangDisconnect = opts.hangDisconnect ?? false;
  }

  readonly failFirstConnect: boolean;
  readonly hangDisconnect: boolean;
  connectAttempts = 0;
  disconnects = 0;
  readonly inputVolumes: number[] = [];
  readonly outputVolumes: number[] = [];
  readonly inputVolumeBoosts: boolean[] = [];
  readonly outputVolumeBoosts: boolean[] = [];
  readonly participantVoiceVolumeWrites: string[] = [];
  readonly screenShareVolumes: number[] = [];
  readonly outputMutes: boolean[] = [];
  readonly micMutes: boolean[] = [];
  readonly cameraEnables: boolean[] = [];
  cameraFlips = 0;
  readonly screenShareEnables: boolean[] = [];
  readonly screenShareSourceIds: (string | null)[] = [];
  readonly screenShareMaxHeights: number[] = [];
  readonly screenShareFrameRates: number[] = [];
  readonly inputDeviceIds: (string | null)[] = [];
  connectedUrl: string | null = null;
  connectedRoomName: string | null = null;
  connectedMicMuted: boolean | null = null;
  musicBoxVolume = defaultAudioVolume;
  private _inputVolume = defaultInputVolume;
  private _outputVolume = defaultOutputVolume;
  inputVolumeBoost = false;
  outputVolumeBoost = false;
  private _screenShareVolume = defaultAudioVolume;
  private _screenShareFrameRate = 30;
  private readonly _participantVoiceVolumes = new Map<string, number>();

  onForciblyRemoved: (() => void) | null = null;
  onUnexpectedlyDisconnected: (() => void) | null = null;
  onPublishPermissionChanged: ((canPublish: boolean) => void) | null = null;
  onParticipantJoined: ((participantIdentity: string) => void) | null = null;
  onParticipantLeft: ((participantIdentity: string, departureKind: LiveParticipantDepartureKind) => void) | null = null;

  emitChange(): void {
    this.notifyListeners();
  }

  get inputVolume(): number {
    return this._inputVolume;
  }

  get outputVolume(): number {
    return this._outputVolume;
  }

  participantVoiceVolume(userId: string): number {
    return this._participantVoiceVolumes.get(userId) ?? 1.0;
  }

  get screenShareVolume(): number {
    return this._screenShareVolume;
  }

  get screenShareFrameRate(): number {
    return this._screenShareFrameRate;
  }

  async connect(opts: { url: string; token: string; roomName: string; micMuted: boolean }): Promise<void> {
    this.connectAttempts += 1;
    if (this.failFirstConnect && this.connectAttempts === 1) {
      throw new Error('first connect failed');
    }
    this.connectedUrl = opts.url;
    this.connectedRoomName = opts.roomName;
    this.connectedMicMuted = opts.micMuted;
  }

  disconnect(): Promise<void> {
    this.disconnects += 1;
    if (this.hangDisconnect) return new Promise<void>(() => {});
    return Promise.resolve();
  }

  async setInputVolume(volume: number): Promise<void> {
    this._inputVolume = volume;
    this.inputVolumes.push(volume);
  }

  async setOutputVolume(volume: number): Promise<void> {
    this._outputVolume = volume;
    this.outputVolumes.push(volume);
  }

  async setInputVolumeBoost(boost: boolean): Promise<void> {
    this.inputVolumeBoost = boost;
    this.inputVolumeBoosts.push(boost);
  }

  async setOutputVolumeBoost(boost: boolean): Promise<void> {
    this.outputVolumeBoost = boost;
    this.outputVolumeBoosts.push(boost);
  }

  async setParticipantVoiceVolume(userId: string, volume: number): Promise<void> {
    this._participantVoiceVolumes.set(userId, volume);
    this.participantVoiceVolumeWrites.push(`${userId}:${volume.toFixed(2)}`);
  }

  async setMusicBoxVolume(volume: number): Promise<void> {
    this.musicBoxVolume = volume;
  }

  async setScreenShareVolume(volume: number): Promise<void> {
    this._screenShareVolume = volume;
    this.screenShareVolumes.push(volume);
  }

  async setOutputMuted(muted: boolean): Promise<void> {
    this.outputMutes.push(muted);
  }

  async setMicMuted(muted: boolean): Promise<void> {
    this.micMutes.push(muted);
  }

  async setCameraEnabled(enabled: boolean): Promise<boolean> {
    this.cameraEnables.push(enabled);
    return enabled;
  }

  async flipCamera(): Promise<boolean> {
    this.cameraFlips += 1;
    return true;
  }

  async setScreenShareEnabled(enabled: boolean, opts: { sourceId?: string | null } = {}): Promise<boolean> {
    this.screenShareEnables.push(enabled);
    this.screenShareSourceIds.push(opts.sourceId ?? null);
    return enabled;
  }

  async setScreenShareMaxHeight(height: number): Promise<void> {
    this.screenShareMaxHeights.push(height);
  }

  async setScreenShareFrameRate(frameRate: number): Promise<void> {
    this._screenShareFrameRate = frameRate;
    this.screenShareFrameRates.push(frameRate);
  }

  async setInputDeviceId(deviceId: string | null): Promise<void> {
    this.inputDeviceIds.push(deviceId);
  }

  asSession(): LiveSession {
    return this as unknown as LiveSession;
  }
}

function controllerFor(
  session: FakeLiveSession,
  opts: { store?: AudioDeviceStore; restorer?: (store: AudioDeviceStore) => Promise<string | null> } = {},
): LiveSessionController {
  return new LiveSessionController({
    apiBaseUrl: 'https://api.example.test/api/v1',
    session: session.asSession(),
    audioDeviceStore: opts.store ?? new FakeAudioDeviceStore(),
    audioDeviceRestorer: opts.restorer ?? (async () => null),
  });
}

describe('LiveSessionController', () => {
  it('connectWithRetry restores audio settings and retries LiveKit connect', async () => {
    const session = new FakeLiveSession({ failFirstConnect: true });
    let restoredDevices = 0;
    const controller = controllerFor(session, {
      restorer: async () => {
        restoredDevices += 1;
        return null;
      },
    });

    await controller.connectWithRetry(_liveJoinResult);

    expect(session.connectAttempts).toBe(2);
    expect(session.disconnects).toBe(1);
    expect(session.inputVolumes).toEqual([0.35, 0.35]);
    expect(session.outputVolumes).toEqual([0.75, 0.75]);
    expect(session.screenShareVolumes).toEqual([0.5, 0.5]);
    expect(restoredDevices).toBe(2);
    expect(session.connectedUrl).toBe('wss://live.example.test');
    expect(session.connectedRoomName).toBe('room_live_1');
    expect(session.connectedMicMuted).toBe(true);
  });

  it('connectWithRetry rethrows the last failure and honors cancellation', async () => {
    const session = new FakeLiveSession();
    session.connect = async () => {
      throw new Error('livekit down');
    };
    const controller = controllerFor(session);

    await expect(controller.connectWithRetry(_liveJoinResult)).rejects.toThrow('livekit down');
    expect(session.disconnects).toBe(2);

    await expect(controller.connectWithRetry(_liveJoinResult, { isCancelled: () => true })).rejects.toBe(
      '已取消加入直播',
    );
  });

  it('disconnect respects timeout when LiveKit teardown hangs', async () => {
    const session = new FakeLiveSession({ hangDisconnect: true });
    const controller = controllerFor(session);

    await controller.disconnect({ timeout: 1 });

    expect(session.disconnects).toBe(1);
  });

  it('live session callbacks attach through controller boundary', () => {
    const session = new FakeLiveSession();
    const controller = controllerFor(session);
    let changes = 0;
    let removals = 0;
    let unexpectedDisconnects = 0;
    const joins: string[] = [];
    const leaves: string[] = [];
    const publishPermissions: boolean[] = [];
    const onChanged = () => {
      changes += 1;
    };

    controller.attachSessionCallbacks({
      onChanged,
      onForciblyRemoved: () => (removals += 1),
      onUnexpectedlyDisconnected: () => (unexpectedDisconnects += 1),
      onPublishPermissionChanged: (canPublish) => publishPermissions.push(canPublish),
      onParticipantJoined: (identity) => joins.push(identity),
      onParticipantLeft: (identity, kind) => leaves.push(`${identity}:${kind}`),
    });
    session.emitChange();
    session.onForciblyRemoved?.();
    session.onUnexpectedlyDisconnected?.();
    session.onPublishPermissionChanged?.(false);
    session.onParticipantJoined?.('user-2');
    session.onParticipantLeft?.('user-3', LiveParticipantDepartureKind.removed);

    expect(changes).toBe(1);
    expect(removals).toBe(1);
    expect(unexpectedDisconnects).toBe(1);
    expect(publishPermissions).toEqual([false]);
    expect(joins).toEqual(['user-2']);
    expect(leaves).toEqual(['user-3:removed']);

    controller.detachSessionCallbacks({ onChanged });
    session.emitChange();
    expect(changes).toBe(1);
    expect(session.onForciblyRemoved).toBeNull();
    expect(session.onUnexpectedlyDisconnected).toBeNull();
    expect(session.onPublishPermissionChanged).toBeNull();
    expect(session.onParticipantJoined).toBeNull();
    expect(session.onParticipantLeft).toBeNull();
  });

  it('live session media controls proxy through controller', async () => {
    const session = new FakeLiveSession();
    const controller = controllerFor(session);

    await controller.setMicMuted(true);
    await controller.setCameraEnabled(true);
    await controller.flipCamera();
    await controller.setScreenShareEnabled(true, { sourceId: 'screen_1' });
    await controller.setOutputMuted(true);
    await controller.setInputVolume(0.4);
    await controller.setOutputVolume(0.6);
    await controller.setParticipantVoiceVolume('user_2', 0.25);
    await controller.setScreenShareVolume(0.8);

    expect(session.micMutes).toEqual([true]);
    expect(session.cameraEnables).toEqual([true]);
    expect(session.cameraFlips).toBe(1);
    expect(session.screenShareEnables).toEqual([true]);
    expect(session.screenShareSourceIds).toEqual(['screen_1']);
    expect(session.outputMutes).toEqual([true]);
    expect(session.inputVolumes).toEqual([0.4]);
    expect(session.outputVolumes).toEqual([0.6]);
    expect(session.participantVoiceVolumeWrites).toEqual(['user_2:0.25']);
    expect(controller.participantVoiceVolume('user_2')).toBe(0.25);
    expect(session.screenShareVolumes).toEqual([0.8]);
  });

  it('live session volume controls persist through the local store', async () => {
    const session = new FakeLiveSession();
    const store = new RecordingAudioDeviceStore();
    const controller = controllerFor(session, { store });

    await controller.setInputVolume(0.4);
    await controller.setOutputVolume(0.6);
    await controller.setParticipantVoiceVolume('user_2', 1.5);
    await controller.setScreenShareVolume(0.8);

    expect(store.inputVolumeWrites).toEqual([0.4]);
    expect(store.outputVolumeWrites).toEqual([0.6]);
    expect(store.participantVoiceVolumeWrites).toEqual(['user_2:1.50']);
    expect(store.screenShareVolumeWrites).toEqual([0.8]);
  });

  it('transient mute gains do not overwrite stored volumes', async () => {
    const session = new FakeLiveSession();
    const store = new RecordingAudioDeviceStore();
    const controller = controllerFor(session, { store });

    await controller.setInputVolume(0, { persist: false });
    await controller.setOutputVolume(0, { persist: false });

    expect(session.inputVolumes).toEqual([0]);
    expect(session.outputVolumes).toEqual([0]);
    expect(store.inputVolumeWrites).toEqual([]);
    expect(store.outputVolumeWrites).toEqual([]);
  });

  it('stored legacy mute gains are healed before joining', async () => {
    const session = new FakeLiveSession();
    const store = new RecordingAudioDeviceStore({ storedInputVolume: 0, storedOutputVolume: 0 });
    const controller = controllerFor(session, { store });

    await controller.restoreStoredAudioSettings();

    expect(session.inputVolumes).toEqual([defaultInputVolume]);
    expect(session.outputVolumes).toEqual([defaultOutputVolume]);
    expect(store.inputVolumeWrites).toEqual([defaultInputVolume]);
    expect(store.outputVolumeWrites).toEqual([defaultOutputVolume]);
  });

  it('legacy gain migration failure does not stop remaining restore', async () => {
    const session = new FakeLiveSession();
    const store = new RecordingAudioDeviceStore({
      storedInputVolume: 0,
      storedOutputVolume: 0,
      storedScreenShareMaxHeight: 480,
      failVolumeWrites: true,
    });
    let restoredDevices = 0;
    const controller = controllerFor(session, {
      store,
      restorer: async () => {
        restoredDevices += 1;
        return 'mic-2';
      },
    });

    await controller.restoreStoredAudioSettings();

    expect(session.inputVolumes).toEqual([defaultInputVolume]);
    expect(session.outputVolumes).toEqual([defaultOutputVolume]);
    expect(session.screenShareMaxHeights).toEqual([480]);
    expect(session.screenShareFrameRates).toEqual([30]);
    expect(restoredDevices).toBe(1);
    expect(session.inputDeviceIds).toEqual(['mic-2']);
  });

  it('current join mic choice overrides a stale muted snapshot', async () => {
    const session = new FakeLiveSession();
    const controller = controllerFor(session);

    await controller.connectWithRetry(_liveJoinResult, { initialMicMuted: false });

    expect(_liveJoinResult.participant.micMuted).toBe(true);
    expect(session.connectedMicMuted).toBe(false);
  });

  it('participant voice volume restores and toggles local mute', async () => {
    const session = new FakeLiveSession();
    const store = new RecordingAudioDeviceStore({ storedParticipantVoiceVolumes: { user_2: 1.5 } });
    const controller = controllerFor(session, { store });

    expect(await controller.restoreParticipantVoiceVolume('user_2')).toBe(true);
    expect(await controller.restoreParticipantVoiceVolume('user_2')).toBe(false);
    expect(controller.participantVoiceVolume('user_2')).toBe(1.5);

    await controller.toggleParticipantVoiceMuted('user_2');
    expect(controller.participantVoiceVolume('user_2')).toBe(0);

    await controller.toggleParticipantVoiceMuted('user_2');
    expect(controller.participantVoiceVolume('user_2')).toBe(1.5);
    expect(session.participantVoiceVolumeWrites).toEqual(['user_2:1.50', 'user_2:0.00', 'user_2:1.50']);
    expect(store.participantVoiceVolumeWrites).toEqual(['user_2:0.00', 'user_2:1.50']);
  });

  it('setScreenShareMaxHeight applies to the session and persists', async () => {
    const session = new FakeLiveSession();
    const store = new RecordingAudioDeviceStore();
    const controller = controllerFor(session, { store });

    await controller.setScreenShareMaxHeight(720);

    expect(session.screenShareMaxHeights).toEqual([720]);
    expect(store.screenShareWrites).toEqual([720]);
  });

  it('setScreenShareFrameRate applies to the session and persists', async () => {
    const session = new FakeLiveSession();
    const store = new RecordingAudioDeviceStore();
    const controller = controllerFor(session, { store });

    await controller.setScreenShareFrameRate(30);

    expect(session.screenShareFrameRates).toEqual([30]);
    expect(store.screenShareFrameRateWrites).toEqual([30]);
  });

  it('connectWithRetry restores the stored screen-share height', async () => {
    const session = new FakeLiveSession();
    const store = new RecordingAudioDeviceStore({ storedScreenShareMaxHeight: 480, storedScreenShareFrameRate: 15 });
    const controller = controllerFor(session, { store });

    await controller.connectWithRetry(_liveJoinResult);

    expect(session.screenShareMaxHeights).toEqual([480]);
    expect(session.screenShareFrameRates).toEqual([15]);
  });
});

const _sender = new UserSummary({
  id: 'user_1',
  username: 'alice',
  displayName: 'Alice',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
});

const _participant = new LiveParticipant({
  liveSessionId: 'live_1',
  user: _sender,
  joinedAt: new Date(Date.UTC(2026, 5, 1)),
  micMuted: true,
  headphonesMuted: false,
  voiceBlocked: false,
  cameraOn: false,
  screenSharing: false,
  connectionState: 'joined',
});

const _liveJoinResult = new LiveJoinResult({
  liveKit: new LiveKitConnectionInfo({
    serverUrl: 'wss://live.example.test',
    token: 'token',
    tokenExpiresAt: new Date(Date.UTC(2026, 5, 1, 1)),
    roomName: 'room_live_1',
  }),
  participant: _participant,
  live: new LiveState({
    roomId: 'room_1',
    participantCount: 1,
    participants: [_participant],
    updatedAt: new Date(Date.UTC(2026, 5, 1)),
  }),
});

import { DisconnectReason, Track } from 'livekit-client';
import { describe, expect, test } from 'vitest';

import { Completer, delay } from '../../src/lib/async';
import { StreamController } from '../../src/lib/event_stream';
import { AudioDeviceRebinder, PreferredLiveAudioDevices } from '../../src/live/audio_device_rebinder';
import { AudioInputRebinder } from '../../src/live/audio_input_rebinder';
import { AudioOutputRebinder } from '../../src/live/audio_output_rebinder';
import { CameraDeviceReconciler } from '../../src/live/camera_device_reconciler';
import {
  LatestLiveSubscriptionReconciler,
  LiveParticipantDepartureKind,
  LiveSession,
  LiveSessionConnectException,
  ScreenSource,
  _coalesceLiveRoomEventBatch,
  _isForcedRemovalDisconnectReason,
  _shouldRequestReconnectForDisconnectReason,
  androidLiveAudioConfiguration,
  cacheScreenSourceThumbnailUpdatesForTest,
  cachedScreenSourceThumbnailForTest,
  cameraFlipAvailabilityFromVideoInputCount,
  filterScreenSourcesForPicker,
  isLivePresenceSoundParticipantIdentity,
  liveParticipantDepartureKind,
  liveParticipantMicPublicationMuted,
  liveReconnectPresenceChanges,
  resetScreenSourceThumbnailCacheForTest,
  retryLiveReconnectMicrophoneRecovery,
  screenSourceThumbnailUpdatesForTest,
  shouldApplyLivePublishPermissionUpdate,
  shouldRepublishLocalMicrophoneAfterReconnect,
  shouldRequestScreenShareAudio,
  type LiveParticipantLike,
  type LiveRoomEvent,
} from '../../src/live/live_session';
import { ScreenShareQuality } from '../../src/live/screen_share_quality';

// Port of test/unit/live/live_session_test.dart.

function participant(identity: string, microphone: { isMuted: boolean } | null): LiveParticipantLike {
  return {
    identity,
    getTrackPublication: (source) => (source === Track.Source.Microphone ? (microphone ?? undefined) : undefined),
  };
}

test('camera flip capability distinguishes unknown and single-camera', () => {
  expect(cameraFlipAvailabilityFromVideoInputCount(0)).toBeNull();
  expect(cameraFlipAvailabilityFromVideoInputCount(1)).toBe(false);
  expect(cameraFlipAvailabilityFromVideoInputCount(2)).toBe(true);
});

test('Android music box switches playback to media mode and volume', () => {
  const media = androidLiveAudioConfiguration({ musicBoxActive: true });
  expect(media.androidAudioMode).toBe('normal');
  expect(media.androidAudioStreamType).toBe('music');
  expect(media.androidAudioAttributesUsageType).toBe('media');
  expect(media.androidAudioAttributesContentType).toBe('music');
  expect(media.forceHandleAudioRouting).toBe(true);

  const voice = androidLiveAudioConfiguration({ musicBoxActive: false });
  expect(voice.androidAudioMode).toBe('inCommunication');
  expect(voice.androidAudioStreamType).toBe('voiceCall');
  expect(voice.androidAudioAttributesUsageType).toBe('voiceCommunication');
});

test('participant removal maps to the kicked announcement kind', () => {
  expect(liveParticipantDepartureKind(DisconnectReason.PARTICIPANT_REMOVED)).toBe(LiveParticipantDepartureKind.removed);
  expect(liveParticipantDepartureKind(DisconnectReason.CLIENT_INITIATED)).toBe(LiveParticipantDepartureKind.left);
  expect(liveParticipantDepartureKind(null)).toBe(LiveParticipantDepartureKind.left);
});

test('missing microphone publication is unknown during participant reconnect', () => {
  expect(liveParticipantMicPublicationMuted(participant('p', null))).toBeNull();
  expect(liveParticipantMicPublicationMuted(participant('p', { isMuted: true }))).toBe(true);
  expect(liveParticipantMicPublicationMuted(participant('p', { isMuted: false }))).toBe(false);
});

test('media reconnect clears stale speaking highlights immediately', () => {
  const session = new LiveSession({ inputRebinderFactory: () => null, outputRebinderFactory: () => null });

  session.debugHandleRoomEvent({ type: 'activeSpeakersChanged', speakers: [participant('speaker', null)] });
  expect([...session.speakingIdentities]).toEqual(['speaker']);

  session.debugHandleRoomEvent({ type: 'reconnecting' });

  expect(session.speakingIdentities.size).toBe(0);
  expect(session.micMutedByIdentity.size).toBe(0);
  session.dispose();
});

test('speaking state ignores hidden screen-audio participants', () => {
  const session = new LiveSession({ inputRebinderFactory: () => null, outputRebinderFactory: () => null });

  session.debugHandleRoomEvent({
    type: 'activeSpeakersChanged',
    speakers: [{ identity: 'user-1' }, { identity: 'user-1--screen-audio' }],
  });

  expect([...session.speakingIdentities]).toEqual(['user-1']);
  expect(session.isSpeaking('user-1')).toBe(true);
  session.dispose();
});

test('only recoverable room disconnects request an app-level reconnect', () => {
  const session = new LiveSession({ inputRebinderFactory: () => null, outputRebinderFactory: () => null });
  let reconnects = 0;
  let removals = 0;
  session.onUnexpectedlyDisconnected = () => reconnects++;
  session.onForciblyRemoved = () => removals++;

  // livekit-client reports an exhausted reconnect without a reason.
  session.debugHandleRoomEvent({ type: 'disconnected', reason: null });
  session.debugHandleRoomEvent({ type: 'disconnected', reason: DisconnectReason.DUPLICATE_IDENTITY });
  session.debugHandleRoomEvent({ type: 'disconnected', reason: DisconnectReason.PARTICIPANT_REMOVED });

  expect(reconnects).toBe(1);
  expect(removals).toBe(1);
  session.dispose();
});

// Port of test/unit/live/livekit_disconnect_reason_compatibility_test.dart:
// the Flutter SDK mapped protocol reasons onto its own enum; livekit-client
// reports protocol values directly, so the session's classification must
// stay total and keep the same semantics.
describe('disconnect reason compatibility', () => {
  const reasons = Object.values(DisconnectReason).filter((value): value is DisconnectReason => typeof value === 'number');

  test('every protocol disconnect reason has a safe classification', () => {
    expect(reasons.length).toBeGreaterThan(10);
    for (const reason of reasons) {
      expect(() => _isForcedRemovalDisconnectReason(reason)).not.toThrow();
      expect(() => liveParticipantDepartureKind(reason)).not.toThrow();
      // Never both a forced removal and a reconnect request.
      expect(_isForcedRemovalDisconnectReason(reason) && _shouldRequestReconnectForDisconnectReason(reason)).toBe(false);
    }
  });

  test('mobile process timeout remains a normal participant departure', () => {
    expect(liveParticipantDepartureKind(DisconnectReason.CONNECTION_TIMEOUT)).toBe(LiveParticipantDepartureKind.left);
    expect(_shouldRequestReconnectForDisconnectReason(DisconnectReason.CONNECTION_TIMEOUT)).toBe(true);
  });

  test('new room and signaling reasons retain their existing semantics', () => {
    // ROOM_CLOSED was folded into roomDeleted.
    expect(_isForcedRemovalDisconnectReason(DisconnectReason.ROOM_CLOSED)).toBe(true);
    expect(_isForcedRemovalDisconnectReason(DisconnectReason.ROOM_DELETED)).toBe(true);
    // SIGNAL_CLOSE was signalingConnectionFailure: recoverable.
    expect(_shouldRequestReconnectForDisconnectReason(DisconnectReason.SIGNAL_CLOSE)).toBe(true);
    expect(_shouldRequestReconnectForDisconnectReason(DisconnectReason.CLIENT_INITIATED)).toBe(false);
  });
});

test('presence cues ignore hidden audio participants', () => {
  expect(isLivePresenceSoundParticipantIdentity('user-2')).toBe(true);
  expect(isLivePresenceSoundParticipantIdentity('user-2--screen-audio')).toBe(false);
  expect(isLivePresenceSoundParticipantIdentity('__musicbox__')).toBe(false);
  expect(isLivePresenceSoundParticipantIdentity('')).toBe(false);
});

test('reconnect presence diff ignores users restored on both sides', () => {
  const changes = liveReconnectPresenceChanges({ before: new Set(['stayed', 'left']), after: new Set(['stayed', 'joined']) });

  expect([...changes.left]).toEqual(['left']);
  expect([...changes.joined]).toEqual(['joined']);
});

test('reconnect republishes only an established permitted microphone', () => {
  expect(
    shouldRepublishLocalMicrophoneAfterReconnect({
      resumedSignalConnection: true,
      hadPublicationBeforeReconnect: true,
      canPublish: true,
    }),
  ).toBe(true);
  expect(
    shouldRepublishLocalMicrophoneAfterReconnect({
      resumedSignalConnection: true,
      hadPublicationBeforeReconnect: false,
      canPublish: true,
    }),
  ).toBe(false);
  expect(
    shouldRepublishLocalMicrophoneAfterReconnect({
      resumedSignalConnection: true,
      hadPublicationBeforeReconnect: true,
      canPublish: false,
    }),
  ).toBe(false);
  expect(
    shouldRepublishLocalMicrophoneAfterReconnect({
      resumedSignalConnection: false,
      hadPublicationBeforeReconnect: true,
      canPublish: true,
    }),
  ).toBe(false);
});

test('reconnect microphone recovery retries after native teardown races', async () => {
  let attempts = 0;
  const waits: number[] = [];

  const recovered = await retryLiveReconnectMicrophoneRecovery({
    attempt: async () => {
      attempts += 1;
      return attempts === 3;
    },
    isCurrent: () => true,
    retryDelays: [0, 10, 20],
    wait: async (duration) => {
      waits.push(duration);
    },
  });

  expect(recovered).toBe(true);
  expect(attempts).toBe(3);
  expect(waits).toEqual([10, 20]);
});

test('reconnect microphone recovery stops when its room is superseded', async () => {
  let current = true;
  let attempts = 0;

  const recovered = await retryLiveReconnectMicrophoneRecovery({
    attempt: async () => {
      attempts += 1;
      current = false;
      return false;
    },
    isCurrent: () => current,
    retryDelays: [0, 0],
  });

  expect(recovered).toBe(false);
  expect(attempts).toBe(1);
});

test('latest subscription reconciliation runs last', async () => {
  const reconciler = new LatestLiveSubscriptionReconciler();
  const firstStarted = new Completer<void>();
  const releaseFirst = new Completer<void>();
  const applied: string[] = [];

  const first = reconciler.schedule(async (isCurrent) => {
    firstStarted.complete();
    await releaseFirst.promise;
    if (isCurrent()) applied.push('first');
  });
  await firstStarted.promise;

  const second = reconciler.schedule(async (isCurrent) => {
    if (isCurrent()) applied.push('second');
  });
  releaseFirst.complete();
  await Promise.all([first, second]);

  expect(applied).toEqual(['second']);
});

test('invalidated subscription reconciliation does not start', async () => {
  const reconciler = new LatestLiveSubscriptionReconciler();
  const blocker = new Completer<void>();
  const blockingTask = reconciler.schedule(() => blocker.promise);
  let ranInvalidatedTask = false;
  const invalidatedTask = reconciler.schedule(async () => {
    ranInvalidatedTask = true;
  });

  reconciler.invalidate();
  blocker.complete();
  await Promise.all([blockingTask, invalidatedTask]);

  expect(ranInvalidatedTask).toBe(false);
});

const thumbnail = (n: number): string => `data:image/png;base64,${'A'.repeat(n)}`;

test('screen-share source picker keeps screens without thumbnails', () => {
  const sources = filterScreenSourcesForPicker([
    new ScreenSource({ id: 'screen:0', name: 'Screen 1', thumbnail: null, isWindow: false }),
  ]);

  expect(sources.map((s) => s.id)).toEqual(['screen:0']);
});

test('screen-share source picker filters known invisible overlay windows', () => {
  const sources = filterScreenSourcesForPicker([
    new ScreenSource({ id: 'window:overlay', name: 'NVIDIA GeForce Overlay', thumbnail: thumbnail(64), isWindow: true }),
    new ScreenSource({ id: 'window:editor', name: 'Code', thumbnail: thumbnail(64), isWindow: true }),
  ]);

  expect(sources.map((s) => s.id)).toEqual(['window:editor']);
});

test('screen-share source picker keeps windows without thumbnails', () => {
  const sources = filterScreenSourcesForPicker([
    new ScreenSource({ id: 'window:browser', name: 'Browser', thumbnail: null, isWindow: true }),
  ]);

  expect(sources.map((s) => s.id)).toEqual(['window:browser']);
});

test('screen-share source picker keeps non-overlay system windows', () => {
  const sources = filterScreenSourcesForPicker([
    new ScreenSource({ id: 'window:input', name: 'Microsoft Text Input Application', thumbnail: null, isWindow: true }),
    new ScreenSource({ id: 'window:program-manager', name: 'Program Manager', thumbnail: null, isWindow: true }),
  ]);

  expect(sources.map((s) => s.id)).toEqual(['window:input', 'window:program-manager']);
});

test('screen-share source picker keeps same raw id across types', () => {
  const sources = filterScreenSourcesForPicker([
    new ScreenSource({ id: '1', name: 'Screen 1', thumbnail: null, isWindow: false, thumbnailKey: 'screen:1' }),
    new ScreenSource({ id: '1', name: 'Window 1', thumbnail: null, isWindow: true, thumbnailKey: 'window:1' }),
  ]);

  expect(sources.map((s) => s.thumbnailKey)).toEqual(['screen:1', 'window:1']);
});

test('screen sources expose the thumbnail as a data URL', () => {
  const source = new ScreenSource({ id: 'screen:0', name: 'Screen', thumbnail: thumbnail(4), isWindow: false });
  expect(source.thumbnailDataUrl).toBe(source.thumbnail);
  expect(source.thumbnailKey).toBe('screen:0');
  expect(source.thumbnailUpdates).toBeNull();
});

test('screen-share requests audio for desktop and Android capture', () => {
  expect(
    shouldRequestScreenShareAudio({
      sourceId: 'screen-primary',
      isDesktopSourcePickerPlatform: true,
      isWindowsDesktop: false,
      isAndroidPlatform: false,
    }),
  ).toBe(true);
  expect(
    shouldRequestScreenShareAudio({
      sourceId: null,
      isDesktopSourcePickerPlatform: true,
      isWindowsDesktop: false,
      isAndroidPlatform: false,
    }),
  ).toBe(true);
  expect(
    shouldRequestScreenShareAudio({
      sourceId: 'screen-primary',
      isDesktopSourcePickerPlatform: false,
      isWindowsDesktop: false,
      isAndroidPlatform: false,
    }),
  ).toBe(false);
  expect(
    shouldRequestScreenShareAudio({
      sourceId: null,
      isDesktopSourcePickerPlatform: false,
      isWindowsDesktop: false,
      isAndroidPlatform: true,
    }),
  ).toBe(true);
});

test('screen-share requests aux audio on Windows desktop', () => {
  expect(
    shouldRequestScreenShareAudio({
      sourceId: 'screen-primary',
      isDesktopSourcePickerPlatform: true,
      isWindowsDesktop: true,
      isAndroidPlatform: false,
    }),
  ).toBe(true);
});

test('screen-share thumbnail updates are cached for reopened picker', async () => {
  resetScreenSourceThumbnailCacheForTest();
  const controller = new StreamController<string>();

  const observed: string[] = [];
  const subscription = cacheScreenSourceThumbnailUpdatesForTest('screen:0', controller.stream).listen((t) =>
    observed.push(t),
  );
  const sourceIdUpdates: string[] = [];
  const sourceIdSubscription = screenSourceThumbnailUpdatesForTest('screen:0').listen((t) => sourceIdUpdates.push(t));

  const image = thumbnail(3);
  controller.add(image);
  await delay(0);

  expect(observed).toEqual([image]);
  expect(sourceIdUpdates).toEqual([image]);
  expect(cachedScreenSourceThumbnailForTest('screen:0')).toBe(image);
  subscription.cancel();
  sourceIdSubscription.cancel();
  controller.close();
  resetScreenSourceThumbnailCacheForTest();
});

test('screen share publishes one high-priority layer at the selected budget', () => {
  // Port of the intent of livekit_simulcast_priority_test.dart: livekit-client
  // applies `priority` to both bitrate and network priority of the single
  // encoding it computes when simulcast is off.
  const encoding = LiveSession._screenShareEncoding(
    new ScreenShareQuality({ maxHeight: 720, maxFrameRate: 30, maxBitrate: 3_000_000 }),
  );
  expect(encoding).toEqual({ maxFramerate: 30, maxBitrate: 3_000_000, priority: 'high' });
});

test('session starts and stops the combined audio-device rebinder', async () => {
  const changes = new StreamController<void>();
  let resolutions = 0;

  const session = new LiveSession({
    audioDeviceRebinderFactory: () =>
      new AudioDeviceRebinder({
        deviceChanges: changes.stream,
        resolvePreferredDevices: async () => {
          resolutions += 1;
          return new PreferredLiveAudioDevices();
        },
        rebindInput: async () => {},
        selectOutput: async () => {},
        onOutputRebound: async () => {},
        debounce: 10,
      }),
  });

  session.debugStartAudioDeviceRebinder();
  changes.add();
  await delay(30);
  expect(resolutions).toBe(1);

  session.debugStopAudioDeviceRebinder();
  changes.add();
  await delay(30);
  expect(resolutions).toBe(1);
  session.dispose();
});

test('session starts and stops the Windows camera reconciler', async () => {
  const changes = new StreamController<void>();
  let refreshes = 0;

  const session = new LiveSession({
    audioDeviceRebinderFactory: () => null,
    cameraDeviceReconcilerFactory: () =>
      new CameraDeviceReconciler({
        deviceChanges: changes.stream,
        refreshDevices: async () => {
          refreshes += 1;
        },
        debounce: 10,
      }),
  });

  session.debugStartAudioDeviceRebinder();
  changes.add();
  await delay(30);
  expect(refreshes).toBe(1);

  session.debugStopAudioDeviceRebinder();
  changes.add();
  await delay(30);
  expect(refreshes).toBe(1);
  session.dispose();
});

test('session starts the output rebinder while connected', async () => {
  const changes = new StreamController<void>();
  const selected: string[] = [];

  let built: AudioOutputRebinder | null = null;
  const session = new LiveSession({
    outputRebinderFactory: () => {
      built = new AudioOutputRebinder({
        deviceChanges: changes.stream,
        currentOutputDeviceId: async () => 'speaker_1',
        selectOutput: async (id) => {
          selected.push(id);
        },
        onRebound: async () => {},
        debounce: 10,
      });
      return built;
    },
  });

  session.debugStartOutputRebinder();
  changes.add();
  await delay(30);

  // The rebinder built by the factory is live and reacting to device flips.
  expect(built).not.toBeNull();
  expect(selected).toEqual(['speaker_1']);
  session.dispose();
});

test('session starts the input rebinder while connected', async () => {
  const changes = new StreamController<void>();
  const rebound: Array<string | null> = [];

  let built: AudioInputRebinder | null = null;
  const session = new LiveSession({
    inputRebinderFactory: () => {
      built = new AudioInputRebinder({
        deviceChanges: changes.stream,
        currentInputDeviceId: async () => 'mic_1',
        rebindInput: async (id) => {
          rebound.push(id);
        },
        debounce: 10,
      });
      return built;
    },
    outputRebinderFactory: () => null,
  });

  session.debugStartInputRebinder();
  changes.add();
  await delay(30);

  expect(built).not.toBeNull();
  expect(rebound).toEqual(['mic_1']);
  session.dispose();
});

test('session stops the output rebinder so flips stop rebinding', async () => {
  const changes = new StreamController<void>();
  let selects = 0;

  const session = new LiveSession({
    outputRebinderFactory: () =>
      new AudioOutputRebinder({
        deviceChanges: changes.stream,
        currentOutputDeviceId: async () => 'speaker_1',
        selectOutput: async () => {
          selects += 1;
        },
        onRebound: async () => {},
        debounce: 10,
      }),
  });

  session.debugStartOutputRebinder();
  session.debugStopOutputRebinder();
  changes.add();
  await delay(30);

  expect(selects).toBe(0);
  session.dispose();
});

test('session stops the input rebinder so flips stop rebinding', async () => {
  const changes = new StreamController<void>();
  let rebounds = 0;

  const session = new LiveSession({
    inputRebinderFactory: () =>
      new AudioInputRebinder({
        deviceChanges: changes.stream,
        currentInputDeviceId: async () => 'mic_1',
        rebindInput: async () => {
          rebounds += 1;
        },
        debounce: 10,
      }),
    outputRebinderFactory: () => null,
  });

  session.debugStartInputRebinder();
  session.debugStopInputRebinder();
  changes.add();
  await delay(30);

  expect(rebounds).toBe(0);
  session.dispose();
});

test('a null factory disables output rebinding (non-macOS)', () => {
  const session = new LiveSession({ outputRebinderFactory: () => null });

  // Should be a no-op rather than throwing when there's nothing to rebind.
  session.debugStartOutputRebinder();
  session.debugStopOutputRebinder();
  session.dispose();
});

test('a null factory disables input rebinding (non-desktop)', () => {
  const session = new LiveSession({ inputRebinderFactory: () => null, outputRebinderFactory: () => null });

  session.debugStartInputRebinder();
  session.debugStopInputRebinder();
  session.dispose();
});

test('publish permission updates ignore headphone-only permission events', () => {
  expect(shouldApplyLivePublishPermissionUpdate({ currentCanPublish: true, oldCanPublish: true, nextCanPublish: true })).toBe(
    false,
  );
  expect(shouldApplyLivePublishPermissionUpdate({ currentCanPublish: true, oldCanPublish: true, nextCanPublish: false })).toBe(
    true,
  );
  expect(
    shouldApplyLivePublishPermissionUpdate({ currentCanPublish: false, oldCanPublish: false, nextCanPublish: true }),
  ).toBe(true);
  expect(
    shouldApplyLivePublishPermissionUpdate({ currentCanPublish: true, oldCanPublish: false, nextCanPublish: false }),
  ).toBe(false);
});

test('a disconnected session reports the Dart defaults', () => {
  const session = new LiveSession({ inputRebinderFactory: () => null, outputRebinderFactory: () => null });

  expect(session.isConnected).toBe(false);
  expect(session.isConnecting).toBe(false);
  expect(session.localMicMuted).toBe(true);
  expect(session.canPublish).toBe(true);
  expect(session.canFlipCamera).toBe(true);
  expect(session.videoTracks).toEqual([]);
  expect(session.connectedParticipantIdentities.size).toBe(0);
  expect(session.screenShareFor('u1')).toBeNull();
  expect(session.cameraFor('u1')).toBeNull();
  // Unity, not Dart's 0.5: the old default halved the microphone and playback.
  expect(session.inputVolume).toBe(1);
  expect(session.outputVolume).toBe(1);
  expect(session.inputVolumeBoost).toBe(false);
  expect(session.outputVolumeBoost).toBe(false);
  expect(session.participantVoiceVolume('u1')).toBe(1);
  expect(session.screenShareMaxHeight).toBe(720);
  expect(session.screenShareFrameRate).toBe(30);
  expect(session.isAttachedToRoom('room-1')).toBe(false);
  session.dispose();
});

test('volume, watched-target and quality setters normalize without a room', async () => {
  const session = new LiveSession({ inputRebinderFactory: () => null, outputRebinderFactory: () => null });
  let notifications = 0;
  session.addListener(() => notifications++);

  await session.setOutputVolume(3);
  await session.setInputVolume(-1);
  await session.setMusicBoxVolume(0.25);
  await session.setScreenShareVolume(0.75);
  await session.setParticipantVoiceVolume('u1', 5);
  await session.setParticipantVoiceVolume('u2', 1);
  await session.setWatchedScreenShareIdentity('  sharer  ');
  await session.setWatchedCameraIdentity('   ');
  await session.setScreenShareMaxHeight(999);
  await session.setScreenShareFrameRate(60);

  expect(session.outputVolume).toBe(1);
  expect(session.inputVolume).toBe(0);
  expect(session.musicBoxVolume).toBe(0.25);
  expect(session.screenShareVolume).toBe(0.75);
  expect(session.participantVoiceVolume('u1')).toBe(2);
  expect(session.participantVoiceVolume('u2')).toBe(1);
  expect(session.watchedScreenShareIdentity).toBe('sharer');
  expect(session.watchedCameraIdentity).toBeNull();
  expect(session.screenShareMaxHeight).toBe(720);
  expect(session.screenShareFrameRate).toBe(60);
  // watched share changed, quality frame rate changed.
  expect(notifications).toBe(2);
  session.dispose();
});

test('screen share and camera are no-ops without a room', async () => {
  const session = new LiveSession({ inputRebinderFactory: () => null, outputRebinderFactory: () => null });

  await expect(session.setScreenShareEnabled(true, { sourceId: 'screen:0' })).resolves.toBe(false);
  await expect(session.setCameraEnabled(true)).resolves.toBe(false);
  await expect(session.flipCamera()).resolves.toBe(false);
  session.dispose();
});

test('connect failures surface as LiveSessionConnectException', () => {
  const error = new LiveSessionConnectException({ url: 'wss://live.example.test', cause: new Error('boom') });
  expect(error.toString()).toBe('无法连接语音服务');
  expect(error.message).toBe('无法连接语音服务');
  expect(error.url).toBe('wss://live.example.test');
  expect(error.cause).toBeInstanceOf(Error);
});

describe('_coalesceLiveRoomEventBatch', () => {
  const departed = (identity: string): LiveRoomEvent => ({
    type: 'participantDisconnected',
    participant: participant(identity, null),
    reason: null,
  });
  const lifecycle = (event: 'trackUnpublished' | 'trackUnsubscribed' | 'localTrackUnpublished', identity: string, isLocal = false): LiveRoomEvent => ({
    type: 'trackLifecycle',
    event,
    participantIdentity: identity,
    isLocal,
  });

  test('passes ordinary batches through', () => {
    const departedA = departed('a');
    const batch: LiveRoomEvent[] = [departedA, lifecycle('trackUnpublished', 'a'), { type: 'reconnected' }];
    expect(_coalesceLiveRoomEventBatch(batch)).toEqual(batch);
    expect(_coalesceLiveRoomEventBatch([{ type: 'reconnecting' }, departedA])).toEqual([
      { type: 'reconnecting' },
      departedA,
    ]);
  });

  test('moves a full-restart reconnect ahead of the participant unwind', () => {
    const departedA = departed('a');
    const departedB = departed('b');
    const batch: LiveRoomEvent[] = [
      lifecycle('trackUnsubscribed', 'a'),
      lifecycle('trackUnpublished', 'a'),
      departedA,
      lifecycle('trackUnpublished', 'b'),
      departedB,
      lifecycle('localTrackUnpublished', 'me', true),
      { type: 'reconnecting' },
      { type: 'activeSpeakersChanged', speakers: [] },
    ];

    expect(_coalesceLiveRoomEventBatch(batch)).toEqual([
      { type: 'reconnecting', restartDepartures: ['a', 'b'] },
      departedA,
      departedB,
      lifecycle('localTrackUnpublished', 'me', true),
      { type: 'activeSpeakersChanged', speakers: [] },
    ]);
  });

  test('a restart unwind does not fire leave callbacks or clear watched targets', async () => {
    const session = new LiveSession({ inputRebinderFactory: () => null, outputRebinderFactory: () => null });
    const left: string[] = [];
    session.onParticipantLeft = (identity) => left.push(identity);
    await session.setWatchedScreenShareIdentity('a');

    for (const event of _coalesceLiveRoomEventBatch([
      lifecycle('trackUnpublished', 'a'),
      departed('a'),
      { type: 'reconnecting' },
    ])) {
      session.debugHandleRoomEvent(event);
    }

    expect(left).toEqual([]);
    expect(session.watchedScreenShareIdentity).toBe('a');
    session.dispose();
  });
});

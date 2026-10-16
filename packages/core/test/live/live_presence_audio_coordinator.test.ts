import { expect, test } from 'vitest';

import {
  LivePresenceAnnouncement,
  LivePresenceAnnouncementAction,
  type LivePresenceSpeechPlayer,
} from '../../src/app/live_presence_announcement';
import { LivePresenceAudioCoordinator } from '../../src/live/live_presence_audio_coordinator';
import { LivePresenceSound, type LivePresenceSoundPlayer } from '../../src/live/live_presence_sound_service';

// Port of test/unit/live/live_presence_audio_coordinator_test.dart.
class _SoundPlayer implements LivePresenceSoundPlayer {
  constructor(readonly events: string[]) {}

  async play(sound: LivePresenceSound, _opts: { volume: number }): Promise<void> {
    this.events.push(`sound:${sound}`);
  }

  async dispose(): Promise<void> {}
}

class _SpeechPlayer implements LivePresenceSpeechPlayer {
  constructor(readonly events: string[]) {}

  async speak(announcement: LivePresenceAnnouncement, _opts: { volume: number }): Promise<void> {
    this.events.push(`speech:${announcement.segments.join('|')}`);
  }

  async dispose(): Promise<void> {}
}

test('presence audio keeps cue and speech items strictly ordered', async () => {
  const events: string[] = [];
  const coordinator = new LivePresenceAudioCoordinator({
    soundPlayer: new _SoundPlayer(events),
    speechPlayer: new _SpeechPlayer(events),
    cueDuration: 0,
    itemGap: 0,
  });
  const joined = new LivePresenceAnnouncement({
    roleLabel: '管理员',
    roomDisplayName: '小林',
    action: LivePresenceAnnouncementAction.joined,
  });
  const removed = new LivePresenceAnnouncement({
    roleLabel: '成员',
    roomDisplayName: '小周',
    action: LivePresenceAnnouncementAction.removed,
  });

  await Promise.all([
    coordinator.play(LivePresenceSound.joined, { volume: 0.7, announcement: joined }),
    coordinator.play(LivePresenceSound.left, { volume: 0.7, announcement: removed }),
  ]);

  expect(events).toEqual([
    'sound:joined',
    'speech:管理员|小林|进入了语音频道',
    'sound:left',
    'speech:成员|小周|被踢出了语音频道',
  ]);
});

test('presence audio without announcement only plays the cue', async () => {
  const events: string[] = [];
  const coordinator = new LivePresenceAudioCoordinator({
    soundPlayer: new _SoundPlayer(events),
    speechPlayer: new _SpeechPlayer(events),
    cueDuration: 0,
    itemGap: 0,
  });

  await coordinator.play(LivePresenceSound.joined, { volume: 0.7 });

  expect(events).toEqual(['sound:joined']);
});

test('muted or disposed presence audio plays nothing', async () => {
  const events: string[] = [];
  const coordinator = new LivePresenceAudioCoordinator({
    soundPlayer: new _SoundPlayer(events),
    speechPlayer: new _SpeechPlayer(events),
    cueDuration: 0,
    itemGap: 0,
  });

  await coordinator.play(LivePresenceSound.joined, { volume: 0 });
  coordinator.dispose();
  await coordinator.play(LivePresenceSound.left, { volume: 1 });

  expect(events).toEqual([]);
});

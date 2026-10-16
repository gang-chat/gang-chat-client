import { expect, test } from 'vitest';

import { BytesSource, PlayerMode } from '../../src/shell/audio_player_types';
import {
  LivePresenceSound,
  LivePresenceSoundService,
  buildLivePresenceTone,
  livePresenceSoundNoteFrequencies,
} from '../../src/live/live_presence_sound_service';
import { FakeAudioPlayer } from '../support/fake_audio_player';

// Port of test/unit/live/live_presence_sound_service_test.dart.
test('join cue rises and leave cue falls', () => {
  const joined = livePresenceSoundNoteFrequencies(LivePresenceSound.joined);
  const left = livePresenceSoundNoteFrequencies(LivePresenceSound.left);

  expect(joined[0]!).toBeLessThan(joined[joined.length - 1]!);
  expect(left[0]!).toBeGreaterThan(left[left.length - 1]!);
  expect(left).toEqual([...joined].reverse());
});

test('presence cues are valid mono PCM WAV data', () => {
  for (const sound of Object.values(LivePresenceSound)) {
    const bytes = buildLivePresenceTone(sound);
    const data = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);

    expect(String.fromCharCode(...bytes.subarray(0, 4))).toBe('RIFF');
    expect(String.fromCharCode(...bytes.subarray(8, 12))).toBe('WAVE');
    expect(data.getUint16(20, true)).toBe(1);
    expect(data.getUint16(22, true)).toBe(1);
    expect(data.getUint32(24, true)).toBe(44100);
    expect(data.getUint16(34, true)).toBe(16);
    expect(data.getUint32(40, true)).toBe(bytes.length - 44);
    expect(bytes.length).toBeGreaterThan(20000);
  }
});

test('plays the cue on its own player with clamped volume and low latency', async () => {
  const joinedPlayer = new FakeAudioPlayer();
  const leftPlayer = new FakeAudioPlayer();
  const service = new LivePresenceSoundService({ joinedPlayer, leftPlayer });

  await service.play(LivePresenceSound.left, { volume: 2 });
  await service.play(LivePresenceSound.joined, { volume: 0 });

  expect(joinedPlayer.played).toHaveLength(0);
  expect(leftPlayer.played).toHaveLength(1);
  const played = leftPlayer.played[0]!;
  expect(played.source).toBeInstanceOf(BytesSource);
  expect((played.source as BytesSource).mimeType).toBe('audio/wav');
  expect(played.volume).toBe(1);
  expect(played.mode).toBe(PlayerMode.lowLatency);

  await service.dispose();
  expect(joinedPlayer.disposed).toBe(true);
  expect(leftPlayer.disposed).toBe(true);
});

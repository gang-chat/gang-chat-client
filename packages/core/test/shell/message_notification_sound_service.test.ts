import { expect, test } from 'vitest';
import { BytesSource, PlayerMode } from '../../src/shell/audio_player_types';
import {
  MessageNotificationSoundService,
  buildMessageNotificationTone,
  messageNotificationSoundFrequencies,
} from '../../src/shell/message_notification_sound_service';
import { FakeAudioPlayer } from '../support/fake_audio_player';

test('message notification cue uses a short rising phrase', () => {
  const frequencies = messageNotificationSoundFrequencies();

  expect(frequencies).toHaveLength(2);
  expect(frequencies[0]!).toBeLessThan(frequencies[1]!);
});

test('message notification cue is valid mono PCM WAV data', () => {
  const bytes = buildMessageNotificationTone();
  const data = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const ascii = (start: number, end: number) => String.fromCharCode(...bytes.subarray(start, end));

  expect(ascii(0, 4)).toBe('RIFF');
  expect(ascii(8, 12)).toBe('WAVE');
  expect(data.getUint16(20, true)).toBe(1);
  expect(data.getUint16(22, true)).toBe(1);
  expect(data.getUint32(24, true)).toBe(44100);
  expect(data.getUint16(34, true)).toBe(16);
  expect(data.getUint32(40, true)).toBe(bytes.length - 44);
  expect(bytes.length).toBeGreaterThan(15000);
});

test('play restarts the cue with a clamped volume and skips silence', async () => {
  const player = new FakeAudioPlayer();
  const service = new MessageNotificationSoundService({ player });

  await service.play({ volume: 0 });
  expect(player.calls).toEqual([]);

  await service.play({ volume: 3 });
  expect(player.calls).toEqual(['stop', 'play']);
  const played = player.played[0]!;
  expect(played.volume).toBe(1);
  expect(played.mode).toBe(PlayerMode.lowLatency);
  expect(played.source).toBeInstanceOf(BytesSource);
  expect((played.source as BytesSource).mimeType).toBe('audio/wav');

  await service.dispose();
  await service.play({ volume: 1 });
  expect(player.calls).toEqual(['stop', 'play', 'dispose']);
});

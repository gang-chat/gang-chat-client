import { expect, test } from 'vitest';
import { UrlSource } from '../../src/shell/audio_player_types';
import { VoicePlaybackService, VoicePlaybackSnapshot } from '../../src/shell/voice_playback_service';
import { FakeAudioPlayer } from '../support/fake_audio_player';

test('toggle plays, tracks position/duration and stops the active message', async () => {
  const player = new FakeAudioPlayer();
  const service = new VoicePlaybackService({ player });

  await service.toggle({ messageId: 'm1', resolvedUrl: 'https://a/voice.m4a' });
  expect(service.state.value.isPlaying('m1')).toBe(true);
  expect((player.played[0]!.source as UrlSource).url).toBe('https://a/voice.m4a');

  player.emitDuration(4000);
  player.emitPosition(1500);
  expect(service.state.value.duration).toBe(4000);
  expect(service.state.value.position).toBe(1500);

  await service.toggle({ messageId: 'm1', resolvedUrl: 'https://a/voice.m4a' });
  expect(service.state.value).toBe(VoicePlaybackSnapshot.idle);
});

test('completion resets to idle and play failures are rethrown', async () => {
  const player = new FakeAudioPlayer();
  const service = new VoicePlaybackService({ player });
  let notifications = 0;
  service.state.addListener(() => notifications++);

  await service.play({ messageId: 'm1', resolvedUrl: 'u' });
  player.complete();
  expect(service.state.value).toBe(VoicePlaybackSnapshot.idle);

  player.playError = new Error('decode failed');
  await expect(service.play({ messageId: 'm2', resolvedUrl: 'u' })).rejects.toThrow('decode failed');
  expect(service.state.value).toBe(VoicePlaybackSnapshot.idle);

  // An already idle state does not notify again (Dart const snapshot equality).
  const before = notifications;
  player.emitPosition(10);
  expect(notifications).toBe(before);

  await service.dispose();
  expect(player.disposed).toBe(true);
});

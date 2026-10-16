import { expect, test, vi } from 'vitest';
import type { LivePresenceAnnouncement } from '../../src/app/live_presence_announcement';
import { SystemLivePresenceSpeechPlayer } from '../../src/shell/system_live_presence_speech_player';
import { FakeSpeech } from '../support/fake_platform';

const announcement = (segments: string[]) => ({ segments }) as unknown as LivePresenceAnnouncement;

test('speaks trimmed segments with a pause between them', async () => {
  vi.useFakeTimers();
  try {
    const speech = new FakeSpeech();
    const player = new SystemLivePresenceSpeechPlayer({ speech });
    const done = player.speak(announcement([' 管理员 ', '', '大厅', '进入了语音频道']), { volume: 2 });
    await vi.advanceTimersByTimeAsync(280 * 2);
    await done;
    expect(speech.spoken).toEqual([
      { text: '管理员', volume: 1, language: 'zh-CN' },
      { text: '大厅', volume: 1, language: 'zh-CN' },
      { text: '进入了语音频道', volume: 1, language: 'zh-CN' },
    ]);
  } finally {
    vi.useRealTimers();
  }
});

test('muted, disposed or failing speech is a silent no-op', async () => {
  const speech = new FakeSpeech();
  const player = new SystemLivePresenceSpeechPlayer({ speech, pause: 0 });
  await player.speak(announcement(['a']), { volume: 0 });
  speech.failWith = new Error('no voice');
  await expect(player.speak(announcement(['a']), { volume: 1 })).resolves.toBeUndefined();
  speech.failWith = null;
  await player.dispose();
  await player.speak(announcement(['a']), { volume: 1 });
  expect(speech.spoken).toEqual([]);
  await expect(new SystemLivePresenceSpeechPlayer({ speech: null }).speak(announcement(['a']), { volume: 1 })).resolves.toBeUndefined();
});

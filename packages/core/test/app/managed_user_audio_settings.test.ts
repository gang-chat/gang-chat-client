import { expect, test } from 'vitest';

import { ManagedUserAudioSettingsStore } from '../../src/app/managed_user_audio_settings';
import { GangApiClient } from '../../src/protocol/api_client';
import { MockClient, type MockRequest, jsonResponse } from '../support/fake_http_client';

test('managed audio store reads and writes the target cloud settings', async () => {
  const requests: MockRequest[] = [];
  const api = new GangApiClient({
    baseUrl: 'http://example.test/api/v1',
    accessTokenProvider: async () => 'token',
    httpClient: new MockClient((request) => {
      requests.push(request);
      const settings =
        request.method === 'PATCH'
          ? (JSON.parse(request.body) as Record<string, unknown>)
          : {
              default_audio_input_volume: 25,
              default_audio_output_volume: 75,
              live_mic_input_volume: 60,
              live_voice_output_volume: 70,
              live_screen_share_output_volume: 80,
              live_music_output_volume: 90,
            };
      return jsonResponse({ audio_settings: settings });
    }),
  });
  const store = new ManagedUserAudioSettingsStore({ api, userId: 'target_user' });

  const stored = await store.read();
  await store.writeInputVolume(0.42);

  expect(stored.inputVolume).toBe(0.25);
  expect(stored.outputVolume).toBe(0.75);
  expect(stored.musicBoxVolume).toBe(0.9);
  expect(stored.screenShareVolume).toBe(0.8);
  expect(requests.map((request) => request.uri.pathname)).toEqual([
    '/api/v1/users/target_user/audio-settings',
    '/api/v1/users/target_user/audio-settings',
  ]);
  expect(JSON.parse(requests[requests.length - 1]!.body)).toEqual({
    default_audio_input_volume: 42,
    default_audio_output_volume: 75,
    live_mic_input_volume: 60,
    live_voice_output_volume: 70,
    live_screen_share_output_volume: 80,
    live_music_output_volume: 90,
  });
  api.close();
});

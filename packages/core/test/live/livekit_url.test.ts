import { describe, expect, test } from 'vitest';

import { resolveLiveKitServerUrl } from '../../src/live/livekit_url';

// Port of test/unit/protocol/livekit_url_test.dart.
describe('resolveLiveKitServerUrl', () => {
  test('rewrites loopback LiveKit host when API host is remote', () => {
    expect(
      resolveLiveKitServerUrl({
        serverUrl: 'http://127.0.0.1:7880',
        apiBaseUrl: 'http://198.44.178.206:21116/api/v1',
      }),
    ).toBe('ws://198.44.178.206:7880');
  });

  test('keeps loopback LiveKit host for a local API', () => {
    expect(
      resolveLiveKitServerUrl({
        serverUrl: 'http://127.0.0.1:7880',
        apiBaseUrl: 'http://localhost:21116/api/v1',
      }),
    ).toBe('ws://127.0.0.1:7880');
  });

  test('keeps already-public LiveKit host', () => {
    expect(
      resolveLiveKitServerUrl({
        serverUrl: 'wss://voice.example.com',
        apiBaseUrl: 'http://198.44.178.206:21116/api/v1',
      }),
    ).toBe('wss://voice.example.com');
  });

  test('normalizes public HTTP LiveKit URL to WebSocket scheme', () => {
    expect(
      resolveLiveKitServerUrl({
        serverUrl: 'http://198.44.178.206:7880',
        apiBaseUrl: 'http://198.44.178.206:21116/api/v1',
      }),
    ).toBe('ws://198.44.178.206:7880');
  });

  test('normalizes HTTPS LiveKit URL to secure WebSocket scheme', () => {
    expect(
      resolveLiveKitServerUrl({
        serverUrl: 'https://voice.example.com',
        apiBaseUrl: 'https://api.example.com/api/v1',
      }),
    ).toBe('wss://voice.example.com');
  });

  test('keeps the path and returns unparseable input unchanged', () => {
    expect(
      resolveLiveKitServerUrl({
        serverUrl: 'https://voice.example.com/livekit',
        apiBaseUrl: 'https://api.example.com/api/v1',
      }),
    ).toBe('wss://voice.example.com/livekit');
    expect(resolveLiveKitServerUrl({ serverUrl: 'not a url', apiBaseUrl: 'https://api.example.com' })).toBe(
      'not a url',
    );
  });
});

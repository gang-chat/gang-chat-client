import { expect, test } from 'vitest';

import { Completer, delay } from '../../src/lib/async';
import { ScreenAudioToken } from '../../src/protocol/models';
import { ScreenAudioPublisher, type ScreenAudioPublisherBackend } from '../../src/live/screen_audio_publisher';

// Port of test/unit/live/screen_audio_publisher_test.dart.
class _FakeScreenAudioPublisherBackend implements ScreenAudioPublisherBackend {
  constructor(
    readonly events: string[],
    private readonly _onPublish: () => Promise<void>,
    private readonly _onConnect: (() => Promise<void>) | null = null,
  ) {}

  stopCount = 0;

  async connect(_opts: { liveKitUrl: string; token: string }): Promise<void> {
    this.events.push('connect');
    await this._onConnect?.();
  }

  async publish(): Promise<void> {
    this.events.push('publish');
    await this._onPublish();
  }

  async stop(): Promise<void> {
    this.stopCount++;
    this.events.push('stop');
  }
}

function _screenAudioToken(roomId: string): ScreenAudioToken {
  return new ScreenAudioToken({
    serverUrl: 'wss://live.example.test',
    token: 'screen-audio-token',
    tokenExpiresAt: new Date(Date.UTC(2026, 6, 29, 1)),
    roomName: roomId,
    identity: 'user-1--screen-audio',
  });
}

async function _tokenProvider(roomId: string): Promise<ScreenAudioToken> {
  return _screenAudioToken(roomId);
}

test('stop cancels before token acquisition completes', async () => {
  const tokenRequested = new Completer<void>();
  const releaseToken = new Completer<void>();
  const events: string[] = [];
  const backend = new _FakeScreenAudioPublisherBackend(events, async () => {});
  const publisher = new ScreenAudioPublisher({
    tokenProvider: async (roomId) => {
      tokenRequested.complete();
      await releaseToken.promise;
      return _screenAudioToken(roomId);
    },
    backendFactory: () => backend,
  });

  const start = publisher.start({ liveKitUrl: 'wss://live.example.test', roomName: 'room-1' });
  await tokenRequested.promise;

  await publisher.stop();
  expect(events).toEqual([]);

  releaseToken.complete();
  await start;

  expect(events).toEqual([]);
  expect(backend.stopCount).toBe(0);
  expect(publisher.isPublishing).toBe(false);
});

test('stop waits for an in-flight publish before releasing the backend', async () => {
  const publishStarted = new Completer<void>();
  const releasePublish = new Completer<void>();
  const events: string[] = [];
  const backend = new _FakeScreenAudioPublisherBackend(events, async () => {
    publishStarted.complete();
    await releasePublish.promise;
  });
  const publisher = new ScreenAudioPublisher({ tokenProvider: _tokenProvider, backendFactory: () => backend });

  const start = publisher.start({ liveKitUrl: 'wss://live.example.test', roomName: 'room-1' });
  await publishStarted.promise;

  let stopCompleted = false;
  const stop = publisher.stop().then(() => {
    stopCompleted = true;
  });
  await delay(0);

  expect(stopCompleted).toBe(false);
  expect(events).toEqual(['connect', 'publish']);

  releasePublish.complete();
  await Promise.all([start, stop]);

  expect(events).toEqual(['connect', 'publish', 'stop']);
  expect(backend.stopCount).toBe(1);
  expect(publisher.isPublishing).toBe(false);
});

test('stop prevents publish after an in-flight connect', async () => {
  const connectStarted = new Completer<void>();
  const releaseConnect = new Completer<void>();
  const events: string[] = [];
  const backend = new _FakeScreenAudioPublisherBackend(
    events,
    async () => {},
    async () => {
      connectStarted.complete();
      await releaseConnect.promise;
    },
  );
  const publisher = new ScreenAudioPublisher({ tokenProvider: _tokenProvider, backendFactory: () => backend });

  const start = publisher.start({ liveKitUrl: 'wss://live.example.test', roomName: 'room-1' });
  await connectStarted.promise;

  const stop = publisher.stop();
  releaseConnect.complete();
  await Promise.all([start, stop]);

  expect(events).toEqual(['connect', 'stop']);
  expect(backend.stopCount).toBe(1);
  expect(publisher.isPublishing).toBe(false);
});

test('a failed publish releases the connected backend', async () => {
  const events: string[] = [];
  const backend = new _FakeScreenAudioPublisherBackend(events, async () => {
    throw new Error('publish failed');
  });
  const publisher = new ScreenAudioPublisher({ tokenProvider: _tokenProvider, backendFactory: () => backend });

  await expect(publisher.start({ liveKitUrl: 'wss://live.example.test', roomName: 'room-1' })).rejects.toThrow(
    'publish failed',
  );

  expect(events).toEqual(['connect', 'publish', 'stop']);
  expect(backend.stopCount).toBe(1);
  expect(publisher.isPublishing).toBe(false);

  await publisher.stop();
  expect(backend.stopCount).toBe(1);
});

test('a successful start marks the publisher active until stopped', async () => {
  const events: string[] = [];
  const backend = new _FakeScreenAudioPublisherBackend(events, async () => {});
  const publisher = new ScreenAudioPublisher({ tokenProvider: _tokenProvider, backendFactory: () => backend });

  await publisher.start({ liveKitUrl: 'wss://live.example.test', roomName: 'room-1' });
  expect(publisher.isPublishing).toBe(true);

  await publisher.stop();
  expect(events).toEqual(['connect', 'publish', 'stop']);
  expect(publisher.isPublishing).toBe(false);
});

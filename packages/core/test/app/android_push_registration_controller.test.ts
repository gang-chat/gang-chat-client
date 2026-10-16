import { expect, test } from 'vitest';

import { AndroidPushRegistrationController } from '../../src/app/android_push_registration_controller';
import { delay } from '../../src/lib/async';
import { type EventStream, StreamController } from '../../src/lib/event_stream';
import type { GangApi } from '../../src/protocol/api_client';
import { AndroidPushRegistration, AndroidSystemService } from '../../src/shell/android_system_service';

class _FakeAndroidSystemService extends AndroidSystemService {
  constructor(public registration: AndroidPushRegistration | null) {
    super();
  }

  private readonly _changes = new StreamController<AndroidPushRegistration>();

  override get isSupported(): boolean {
    return true;
  }

  override get pushRegistrationChanges(): EventStream<AndroidPushRegistration> {
    return this._changes.stream;
  }

  override async pushRegistration(): Promise<AndroidPushRegistration | null> {
    return this.registration;
  }

  emit(value: AndroidPushRegistration): void {
    this.registration = value;
    this._changes.add(value);
  }

  dispose(): void {
    this._changes.close();
  }
}

class _RecordingGangApi {
  readonly upserts: string[] = [];
  readonly deletes: string[] = [];

  async upsertPushDevice(opts: { provider: string; installationId: string; token: string; enabled: boolean }) {
    this.upserts.push(`${opts.provider}/${opts.installationId}/${opts.token}/${opts.enabled}`);
  }

  async deletePushDevice(opts: { provider: string; installationId: string }) {
    this.deletes.push(`${opts.provider}/${opts.installationId}`);
  }
}

function _asApi(api: _RecordingGangApi): GangApi {
  // Only the push-device endpoints are exercised (Dart used `noSuchMethod`).
  return api as unknown as GangApi;
}

test('registers initial token and refreshed token in order', async () => {
  const android = new _FakeAndroidSystemService(
    new AndroidPushRegistration({ provider: 'fcm', installationId: 'install-1', token: 'token-1', enabled: true }),
  );
  const api = new _RecordingGangApi();
  const controller = new AndroidPushRegistrationController({ api: _asApi(api), androidSystemService: android });

  await controller.start();
  android.emit(
    new AndroidPushRegistration({ provider: 'fcm', installationId: 'install-1', token: 'token-2', enabled: false }),
  );
  await delay(0);
  await controller.synchronize();

  expect(api.upserts).toEqual(['fcm/install-1/token-1/true', 'fcm/install-1/token-2/false', 'fcm/install-1/token-2/false']);

  await controller.dispose();
  android.dispose();
});

test('dispose keeps registration while explicit unregister deletes it', async () => {
  const android = new _FakeAndroidSystemService(
    new AndroidPushRegistration({ provider: 'fcm', installationId: 'install-2', token: 'token-2', enabled: true }),
  );
  const api = new _RecordingGangApi();
  const controller = new AndroidPushRegistrationController({ api: _asApi(api), androidSystemService: android });
  await controller.start();

  await controller.unregister();
  expect(api.deletes).toEqual(['fcm/install-2']);

  await controller.dispose();
  expect(api.deletes).toHaveLength(1);
  android.dispose();
});

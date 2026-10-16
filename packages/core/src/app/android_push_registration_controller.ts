import type { StreamSubscription } from '../lib/event_stream';
import { unawaited } from '../lib/async';
import type { GangApi } from '../protocol/api_client';
import type { AndroidPushRegistration, AndroidSystemService } from '../shell/android_system_service';

/**
 * Keeps the Android push token associated with the authenticated API session.
 *
 * Registering a device is intentionally independent from the realtime
 * connection: it must not make an offline account appear online. Disposing
 * this controller also keeps the registration intact so process death or
 * removing the app from Recents behaves like a mobile app going offline, not
 * like an explicit logout.
 */
export class AndroidPushRegistrationController {
  constructor(opts: { api: GangApi; androidSystemService: AndroidSystemService }) {
    this.api = opts.api;
    this.androidSystemService = opts.androidSystemService;
  }

  readonly api: GangApi;
  readonly androidSystemService: AndroidSystemService;

  private _changes: StreamSubscription | null = null;
  private _lastRegistration: AndroidPushRegistration | null = null;
  private _operationTail: Promise<void> = Promise.resolve();
  private _disposed = false;

  async start(): Promise<void> {
    if (this._disposed || !this.androidSystemService.isSupported || this._changes != null) {
      return;
    }
    this._changes = this.androidSystemService.pushRegistrationChanges.listen((registration) => {
      unawaited(this._enqueue(() => this._upsert(registration)).catch(() => {}));
    });
    await this.synchronize();
  }

  synchronize(): Promise<void> {
    return this._enqueue(async () => {
      const registration = await this.androidSystemService.pushRegistration();
      if (registration == null) return;
      await this._upsert(registration);
    });
  }

  unregister(): Promise<void> {
    return this._enqueue(async () => {
      const registration = this._lastRegistration ?? (await this.androidSystemService.pushRegistration());
      if (registration == null) return;
      await this.api.deletePushDevice({
        provider: registration.provider,
        installationId: registration.installationId,
      });
      this._lastRegistration = null;
    });
  }

  private async _upsert(registration: AndroidPushRegistration): Promise<void> {
    if (this._disposed) return;
    await this.api.upsertPushDevice({
      provider: registration.provider,
      installationId: registration.installationId,
      token: registration.token,
      enabled: registration.enabled,
    });
    this._lastRegistration = registration;
  }

  private _enqueue(operation: () => Promise<void>): Promise<void> {
    if (this._disposed) return Promise.resolve();
    const next = this._operationTail.then(() => operation());
    this._operationTail = next.then(
      () => {},
      () => {},
    );
    return next;
  }

  async dispose(): Promise<void> {
    if (this._disposed) return;
    this._disposed = true;
    const changes = this._changes;
    this._changes = null;
    if (changes != null) changes.cancel();
  }
}

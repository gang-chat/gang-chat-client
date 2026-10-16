import { Completer } from '../lib/async';
import type { Milliseconds } from '../lib/async';
import type { EventStream } from '../lib/event_stream';
import { StreamController } from '../lib/event_stream';
import { JsonFormatException } from '../lib/json';

/**
 * Android-only operating-system capabilities used by the shell adapters.
 *
 * PORT: typed no-op stub. Android stays on Flutter, so the
 * `gang_chat/android_system` channel has no TypeScript host: [isSupported] is
 * always false and every method returns the same harmless default the Dart
 * code returned on non-Android platforms. Streams never emit. Kept so callers
 * ported from Dart (home shell, settings, push registration) keep their
 * `if (androidSystemService.isSupported)` branches unchanged.
 */
export class AndroidSystemService {
  private static readonly _selectedRooms = new StreamController<string>();
  private static readonly _pushRegistrationChanges = new StreamController<AndroidPushRegistration>();
  private static readonly _taskRemovalRequests = new StreamController<AndroidTaskRemovalRequest>();

  get isSupported(): boolean {
    return false;
  }

  get selectedNotificationRoomIds(): EventStream<string> {
    return AndroidSystemService._selectedRooms.stream;
  }

  get pushRegistrationChanges(): EventStream<AndroidPushRegistration> {
    return AndroidSystemService._pushRegistrationChanges.stream;
  }

  /** Emitted when Android removes the app from its recent-task list. */
  get taskRemovalRequests(): EventStream<AndroidTaskRemovalRequest> {
    return AndroidSystemService._taskRemovalRequests.stream;
  }

  async openUri(_uri: string): Promise<void> {}

  async openMailto(_uri: string): Promise<void> {}

  async installedAt(): Promise<string | null> {
    return null;
  }

  async createDocument(_opts: { suggestedName: string; mimeType?: string | null }): Promise<AndroidDocumentLocation | null> {
    return null;
  }

  async writeDocumentBytes(_opts: { uri: string; bytes: Uint8Array }): Promise<void> {}

  async commitDocumentFile(_opts: { uri: string; stagingPath: string }): Promise<void> {}

  async discardDocumentFile(_stagingPath: string): Promise<void> {}

  async saveToDownloads(_opts: { filename: string; bytes: Uint8Array; mimeType?: string | null }): Promise<string | null> {
    return null;
  }

  async readClipboardImage(): Promise<AndroidClipboardImage | null> {
    return null;
  }

  async writeClipboardImage(_opts: { bytes: Uint8Array; mimeType: string }): Promise<boolean> {
    return false;
  }

  async enterBackground(): Promise<void> {}

  async enterForeground(): Promise<void> {}

  async showRoomMessage(_opts: {
    roomId: string;
    roomName: string;
    sender: string;
    body: string;
    unreadCount: number;
    messageId?: string | null;
  }): Promise<void> {}

  async cancelRoomNotification(_roomId: string): Promise<void> {}

  async syncBadge(_unreadCount: number): Promise<void> {}

  async clearMessageNotifications(): Promise<void> {}

  async notificationsEnabled(): Promise<boolean> {
    return false;
  }

  async notificationPreferenceEnabled(): Promise<boolean> {
    return false;
  }

  async setNotificationPreferenceEnabled(_enabled: boolean): Promise<boolean> {
    return false;
  }

  async requestNotificationPermission(): Promise<boolean> {
    return false;
  }

  async requestUpdateNotificationPermission(): Promise<boolean> {
    return false;
  }

  async setUpdateDownloadActive(_active: boolean): Promise<void> {}

  async pushRegistration(): Promise<AndroidPushRegistration | null> {
    return null;
  }

  async requestBluetoothConnectPermission(): Promise<boolean> {
    return false;
  }

  async openNotificationSettings(): Promise<void> {}

  async takeInitialNotificationRoomId(): Promise<string | null> {
    return null;
  }

  async speak(_opts: { segments: string[]; volume: number; pause: Milliseconds }): Promise<void> {}

  async disposeSpeech(): Promise<void> {}
}

export class AndroidTaskRemovalRequest {
  private readonly _completion = new Completer<void>();

  get completed(): Promise<void> {
    return this._completion.promise;
  }

  complete(): void {
    if (!this._completion.isCompleted) this._completion.complete();
  }
}

export class AndroidPushRegistration {
  constructor(opts: { provider: string; installationId: string; token: string; enabled: boolean }) {
    this.provider = opts.provider;
    this.installationId = opts.installationId;
    this.token = opts.token;
    this.enabled = opts.enabled;
  }

  readonly provider: string;
  readonly installationId: string;
  readonly token: string;
  readonly enabled: boolean;

  static fromMap(value: Record<string, unknown>): AndroidPushRegistration {
    const registration = AndroidPushRegistration.tryFromMap(value);
    if (registration == null) {
      throw new JsonFormatException('Android 推送注册信息不完整');
    }
    return registration;
  }

  static tryFromMap(value: Record<string, unknown>): AndroidPushRegistration | null {
    const text = (key: string): string => {
      const raw = value[key];
      return raw == null ? '' : String(raw).trim();
    };
    const provider = text('provider');
    const installationId = text('installationId');
    const token = text('token');
    if (provider.length === 0 || installationId.length === 0 || token.length === 0) {
      return null;
    }
    const enabled = value['enabled'];
    return new AndroidPushRegistration({
      provider,
      installationId,
      token,
      enabled: typeof enabled === 'boolean' ? enabled : true,
    });
  }
}

export class AndroidDocumentLocation {
  constructor(opts: { uri: string; stagingPath: string }) {
    this.uri = opts.uri;
    this.stagingPath = opts.stagingPath;
  }

  readonly uri: string;
  readonly stagingPath: string;
}

export class AndroidClipboardImage {
  constructor(opts: { bytes: Uint8Array; filename: string; mimeType: string }) {
    this.bytes = opts.bytes;
    this.filename = opts.filename;
    this.mimeType = opts.mimeType;
  }

  readonly bytes: Uint8Array;
  readonly filename: string;
  readonly mimeType: string;
}

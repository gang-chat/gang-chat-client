import type { JsonMap } from '../lib/json';
import { StreamController, type EventStream, type StreamSubscription } from '../lib/event_stream';
import { LiveStreamClient, StreamStatus } from '../live/live_stream_client';
import type { AccessTokenProvider } from '../protocol/api_client';

export class RealtimeEvent {
  constructor(opts: { type: string; data: JsonMap }) {
    this.type = opts.type;
    this.data = opts.data;
  }

  readonly type: string;
  readonly data: JsonMap;
}

export enum RealtimeConnectionStatus {
  connecting = 'connecting',
  connected = 'connected',
  reconnecting = 'reconnecting',
  offline = 'offline',
}

export abstract class RealtimeService {
  onReconnect: (() => void) | null = null;

  abstract get events(): EventStream<RealtimeEvent>;

  abstract get status(): RealtimeConnectionStatus;

  abstract get statusChanges(): EventStream<RealtimeConnectionStatus>;

  abstract start(): Promise<void>;

  abstract stop(): Promise<void>;

  abstract dispose(): void;
}

export class RealtimeController implements RealtimeService {
  constructor(opts: { apiBaseUrl: string; accessTokenProvider: AccessTokenProvider }) {
    this._client = new LiveStreamClient({
      apiBaseUrl: opts.apiBaseUrl,
      accessTokenProvider: opts.accessTokenProvider,
    });
    this._status = _connectionStatusFromStreamStatus(this._client.status);
    this._client.addListener(this._handleClientStatusChanged);
    this._client.onReconnect = () => this.onReconnect?.();
    this._sourceSubscription = this._client.events.listen((event) => {
      if (this._events.isClosed) return;
      this._events.add(new RealtimeEvent({ type: event.type, data: event.data }));
    });
  }

  private readonly _client: LiveStreamClient;
  private readonly _events = new StreamController<RealtimeEvent>();
  private readonly _statusChanges = new StreamController<RealtimeConnectionStatus>();
  private readonly _sourceSubscription: StreamSubscription;
  private _status: RealtimeConnectionStatus;

  onReconnect: (() => void) | null = null;

  get events(): EventStream<RealtimeEvent> {
    return this._events.stream;
  }

  get status(): RealtimeConnectionStatus {
    return this._status;
  }

  get statusChanges(): EventStream<RealtimeConnectionStatus> {
    return this._statusChanges.stream;
  }

  start(): Promise<void> {
    return this._client.start();
  }

  stop(): Promise<void> {
    return this._client.stop();
  }

  private readonly _handleClientStatusChanged = (): void => {
    const next = _connectionStatusFromStreamStatus(this._client.status);
    if (this._status === next) return;
    this._status = next;
    if (!this._statusChanges.isClosed) this._statusChanges.add(next);
  };

  dispose(): void {
    this._client.removeListener(this._handleClientStatusChanged);
    this._sourceSubscription.cancel();
    this._client.dispose();
    this._events.close();
    this._statusChanges.close();
  }
}

function _connectionStatusFromStreamStatus(status: StreamStatus): RealtimeConnectionStatus {
  switch (status) {
    case StreamStatus.connecting:
      return RealtimeConnectionStatus.connecting;
    case StreamStatus.connected:
      return RealtimeConnectionStatus.connected;
    case StreamStatus.reconnecting:
      return RealtimeConnectionStatus.reconnecting;
    case StreamStatus.offline:
      return RealtimeConnectionStatus.offline;
  }
}

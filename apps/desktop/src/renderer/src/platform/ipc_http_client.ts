import {
  ClientException,
  type HttpClient,
  type HttpRequest,
  type StreamedResponse,
  utf8Encode,
} from '@gang-chat/core/platform/http';

import { IpcChannels, type HttpResponseEvent } from '../../../shared/ipc';
import type { DesktopBridge } from './desktop_bridge';

/**
 * `HttpClient` backed by the main-process network bridge
 * (`src/main/net_bridge.ts`): request bodies are streamed chunk by chunk, the
 * response body arrives as an async iterable, and aborting the signal tears
 * the request down in the main process.
 */
interface Pending {
  resolveResponse: (response: StreamedResponse) => void;
  rejectResponse: (error: unknown) => void;
  responded: boolean;
  queue: Uint8Array[];
  done: boolean;
  error: unknown;
  wake: (() => void) | null;
}

export class IpcHttpClient implements HttpClient {
  private readonly pending = new Map<number, Pending>();
  private readonly early = new Map<number, Array<{ kind: string; payload: unknown }>>();

  constructor(private readonly bridge: DesktopBridge) {
    bridge.on(IpcChannels.httpResponse, (payload) => this.route('response', payload));
    bridge.on(IpcChannels.httpData, (payload) => this.route('data', payload));
    bridge.on(IpcChannels.httpDone, (payload) => this.route('done', payload));
    bridge.on(IpcChannels.httpError, (payload) => this.route('error', payload));
  }

  private route(kind: string, payload: unknown) {
    const id = (payload as { id: number }).id;
    const entry = this.pending.get(id);
    if (!entry) {
      // Events can race ahead of the invoke() reply that tells us the id.
      const list = this.early.get(id) ?? [];
      list.push({ kind, payload });
      this.early.set(id, list);
      return;
    }
    this.apply(id, entry, kind, payload);
  }

  private apply(id: number, entry: Pending, kind: string, payload: unknown) {
    switch (kind) {
      case 'response': {
        const event = payload as HttpResponseEvent;
        entry.responded = true;
        entry.resolveResponse({
          statusCode: event.statusCode,
          headers: event.headers,
          contentLength: event.contentLength,
          stream: this.bodyStream(id, entry),
        });
        break;
      }
      case 'data': {
        const chunk = (payload as { chunk: Uint8Array }).chunk;
        entry.queue.push(chunk instanceof Uint8Array ? chunk : new Uint8Array(chunk as ArrayBufferLike));
        entry.wake?.();
        break;
      }
      case 'done':
        entry.done = true;
        entry.wake?.();
        this.pending.delete(id);
        break;
      case 'error': {
        const { message, aborted } = payload as { message: string; aborted?: boolean };
        const error = aborted ? abortError() : new ClientException(message);
        entry.error = error;
        entry.done = true;
        if (!entry.responded) entry.rejectResponse(error);
        entry.wake?.();
        this.pending.delete(id);
        break;
      }
    }
  }

  private bodyStream(id: number, entry: Pending): AsyncIterable<Uint8Array> {
    const bridge = this.bridge;
    return {
      [Symbol.asyncIterator]() {
        return {
          async next(): Promise<IteratorResult<Uint8Array>> {
            for (;;) {
              const chunk = entry.queue.shift();
              if (chunk) return { value: chunk, done: false };
              if (entry.error) throw entry.error;
              if (entry.done) return { value: undefined, done: true };
              await new Promise<void>((resolve) => {
                entry.wake = () => {
                  entry.wake = null;
                  resolve();
                };
              });
            }
          },
          async return(): Promise<IteratorResult<Uint8Array>> {
            // The consumer stopped reading (e.g. SSE teardown): abort upstream.
            if (!entry.done) bridge.send(IpcChannels.httpAbort, id);
            entry.done = true;
            return { value: undefined, done: true };
          },
        };
      },
    };
  }

  async send(request: HttpRequest): Promise<StreamedResponse> {
    if (request.signal?.aborted) throw abortError();
    const body = request.body;
    const hasBody = body != null;
    let contentLength: number | null = request.contentLength ?? null;
    let bytes: Uint8Array | null = null;
    if (typeof body === 'string') bytes = utf8Encode(body);
    else if (body instanceof Uint8Array) bytes = body;
    if (bytes) contentLength = bytes.length;

    const id = (await this.bridge.invoke(IpcChannels.httpOpen, {
      method: request.method,
      url: request.url,
      headers: request.headers ?? {},
      hasBody,
      contentLength,
    })) as number;

    let resolveResponse!: (response: StreamedResponse) => void;
    let rejectResponse!: (error: unknown) => void;
    const responsePromise = new Promise<StreamedResponse>((resolve, reject) => {
      resolveResponse = resolve;
      rejectResponse = reject;
    });
    const entry: Pending = {
      resolveResponse,
      rejectResponse,
      responded: false,
      queue: [],
      done: false,
      error: null,
      wake: null,
    };
    this.pending.set(id, entry);
    for (const early of this.early.get(id) ?? []) this.apply(id, entry, early.kind, early.payload);
    this.early.delete(id);

    const onAbort = () => {
      this.bridge.send(IpcChannels.httpAbort, id);
    };
    request.signal?.addEventListener('abort', onAbort, { once: true });

    if (hasBody) {
      try {
        if (bytes) {
          // Large bodies go in slices so a single IPC message stays small.
          const slice = 256 * 1024;
          for (let offset = 0; offset < bytes.length; offset += slice) {
            await this.bridge.invoke(IpcChannels.httpWrite, id, bytes.subarray(offset, offset + slice));
          }
        } else if (body && typeof body !== 'string') {
          for await (const chunk of body as AsyncIterable<Uint8Array>) {
            if (request.signal?.aborted) throw abortError();
            await this.bridge.invoke(IpcChannels.httpWrite, id, chunk);
          }
        }
        await this.bridge.invoke(IpcChannels.httpEnd, id);
      } catch (error) {
        this.bridge.send(IpcChannels.httpAbort, id);
        this.pending.delete(id);
        throw error;
      }
    }
    return responsePromise;
  }
}

function abortError(): Error {
  const error = new Error('The request was aborted');
  error.name = 'AbortError';
  return error;
}

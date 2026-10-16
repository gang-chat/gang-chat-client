import { ipcMain, net, type ClientRequest, type IncomingMessage, type WebContents } from 'electron';

import { IpcChannels, type HttpOpenRequest, type HttpResponseEvent } from '../shared/ipc';

/**
 * Runs the renderer's HTTP requests in the main process.
 *
 * The Flutter client used `dart:io`'s HttpClient, which never had a browser
 * origin, so the server's API, uploads, SSE stream and release bucket were
 * reached without CORS. Routing requests through Chromium's network stack in
 * the main process (`net.request`) keeps that property, streams request bodies
 * (upload progress / pause / cancel) and response bodies (SSE, downloads with
 * progress), and uses the session proxy configuration set in `index.ts`.
 */
interface ActiveRequest {
  request: ClientRequest;
  sender: WebContents;
  response: IncomingMessage | null;
  finished: boolean;
}

let nextId = 1;
const active = new Map<number, ActiveRequest>();

// Headers Chromium manages itself; setting them on net.request throws.
const forbiddenHeaders = new Set(['host', 'connection', 'content-length', 'transfer-encoding', 'keep-alive', 'upgrade']);

function normalizeHeaders(headers: Record<string, string | string[]>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(headers)) {
    out[key.toLowerCase()] = Array.isArray(value) ? value.join(', ') : value;
  }
  return out;
}

function send(entry: ActiveRequest, channel: string, payload: unknown) {
  if (entry.sender.isDestroyed()) return;
  entry.sender.send(channel, payload);
}

function finish(id: number) {
  const entry = active.get(id);
  if (!entry) return;
  entry.finished = true;
  active.delete(id);
}

export function registerNetBridge(): void {
  ipcMain.handle(IpcChannels.httpOpen, (event, spec: HttpOpenRequest) => {
    const id = nextId++;
    const request = net.request({
      method: spec.method,
      url: spec.url,
      redirect: 'follow',
      useSessionCookies: false,
      cache: 'no-store',
    });
    for (const [key, value] of Object.entries(spec.headers)) {
      if (forbiddenHeaders.has(key.toLowerCase())) continue;
      request.setHeader(key, value);
    }
    if (spec.hasBody) {
      if (spec.contentLength != null) {
        request.setHeader('Content-Length', String(spec.contentLength));
      } else {
        request.chunkedEncoding = true;
      }
    }
    const entry: ActiveRequest = { request, sender: event.sender, response: null, finished: false };
    active.set(id, entry);

    request.on('response', (response) => {
      entry.response = response;
      const headers = normalizeHeaders(response.headers);
      const lengthHeader = headers['content-length'];
      const contentLength = lengthHeader != null && /^\d+$/.test(lengthHeader) ? Number(lengthHeader) : null;
      const payload: HttpResponseEvent = { id, statusCode: response.statusCode, headers, contentLength };
      send(entry, IpcChannels.httpResponse, payload);
      response.on('data', (chunk: Buffer) => {
        send(entry, IpcChannels.httpData, { id, chunk: new Uint8Array(chunk.buffer, chunk.byteOffset, chunk.byteLength) });
      });
      response.on('end', () => {
        send(entry, IpcChannels.httpDone, { id });
        finish(id);
      });
      response.on('error', (error: Error) => {
        send(entry, IpcChannels.httpError, { id, message: error.message });
        finish(id);
      });
      response.on('aborted', () => {
        send(entry, IpcChannels.httpError, { id, message: 'Connection closed while receiving data' });
        finish(id);
      });
    });
    request.on('error', (error) => {
      if (entry.finished) return;
      send(entry, IpcChannels.httpError, { id, message: error.message });
      finish(id);
    });
    request.on('abort', () => {
      if (entry.finished) return;
      send(entry, IpcChannels.httpError, { id, message: 'Request aborted', aborted: true });
      finish(id);
    });
    event.sender.once('destroyed', () => {
      if (!entry.finished) {
        try {
          request.abort();
        } catch {
          // already closed
        }
        finish(id);
      }
    });
    if (!spec.hasBody) request.end();
    return id;
  });

  ipcMain.handle(IpcChannels.httpWrite, (_event, id: number, chunk: Uint8Array) => {
    const entry = active.get(id);
    if (!entry) throw new Error('Request is no longer active');
    return new Promise<void>((resolve) => {
      entry.request.write(Buffer.from(chunk.buffer, chunk.byteOffset, chunk.byteLength), () => resolve());
    });
  });

  ipcMain.handle(IpcChannels.httpEnd, (_event, id: number) => {
    const entry = active.get(id);
    if (!entry) return;
    entry.request.end();
  });

  ipcMain.on(IpcChannels.httpAbort, (_event, id: number) => {
    const entry = active.get(id);
    if (!entry) return;
    try {
      entry.request.abort();
    } catch {
      // already closed
    }
  });
}

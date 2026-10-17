import {
  ClientException,
  readAllBytes,
  type HttpClient,
  type HttpRequest,
  type StreamedResponse,
} from '@gang-chat/core/platform/http';

// Headers the browser manages itself; fetch() would drop them anyway.
const forbiddenHeaders = new Set(['host', 'connection', 'content-length', 'transfer-encoding', 'keep-alive', 'upgrade']);

/**
 * `HttpClient` on `fetch`, the counterpart of `package:http`'s
 * `BrowserClient` that the Flutter web build used.
 *
 * Response bodies stream (the SSE event stream, downloads with progress).
 * Request bodies do not: browsers only stream them over HTTP/2 and only in
 * Chromium, so a chunk stream is collected first and sent whole, as
 * `BrowserClient` did — an upload's progress therefore reaches 100% before
 * the request goes out. The API is served from the app's own origin, so the
 * requests need no CORS; media and the release bucket send
 * `Access-Control-Allow-Origin: *`.
 */
export class FetchHttpClient implements HttpClient {
  async send(request: HttpRequest): Promise<StreamedResponse> {
    if (request.signal?.aborted) throw abortError();
    const headers = new Headers();
    for (const [key, value] of Object.entries(request.headers ?? {})) {
      if (forbiddenHeaders.has(key.toLowerCase())) continue;
      headers.set(key, value);
    }
    const body = request.body;
    let payload: BodyInit | undefined;
    if (typeof body === 'string') payload = body;
    else if (body instanceof Uint8Array) payload = body as Uint8Array<ArrayBuffer>;
    else if (body != null) payload = (await readAllBytes(body)) as Uint8Array<ArrayBuffer>;
    if (request.signal?.aborted) throw abortError();

    let response: Response;
    try {
      response = await fetch(request.url, {
        method: request.method,
        headers,
        body: payload,
        signal: request.signal,
        redirect: 'follow',
        cache: 'no-store',
        credentials: 'omit',
      });
    } catch (error) {
      if (request.signal?.aborted || (error as Error | null)?.name === 'AbortError') throw abortError();
      throw new ClientException(error instanceof Error ? error.message : String(error), request.url, error);
    }

    const responseHeaders: Record<string, string> = {};
    response.headers.forEach((value, key) => {
      responseHeaders[key.toLowerCase()] = value;
    });
    // The body stream yields decoded bytes, so a compressed response's
    // content-length would not match what arrives.
    const encoding = (responseHeaders['content-encoding'] ?? 'identity').trim().toLowerCase();
    const lengthHeader = responseHeaders['content-length'];
    const contentLength =
      encoding === 'identity' && lengthHeader != null && /^\d+$/.test(lengthHeader) ? Number(lengthHeader) : null;

    return {
      statusCode: response.status,
      headers: responseHeaders,
      contentLength,
      stream: bodyStream(response, request),
    };
  }
}

function bodyStream(response: Response, request: HttpRequest): AsyncIterable<Uint8Array> {
  return {
    [Symbol.asyncIterator]() {
      const reader = response.body?.getReader() ?? null;
      let finished = reader == null;
      return {
        async next(): Promise<IteratorResult<Uint8Array>> {
          if (finished || reader == null) return { value: undefined, done: true };
          try {
            const { value, done } = await reader.read();
            if (done) {
              finished = true;
              return { value: undefined, done: true };
            }
            return { value, done: false };
          } catch (error) {
            finished = true;
            if (request.signal?.aborted || (error as Error | null)?.name === 'AbortError') throw abortError();
            throw new ClientException('Connection closed while receiving data', request.url, error);
          }
        },
        async return(): Promise<IteratorResult<Uint8Array>> {
          // The consumer stopped reading (e.g. SSE teardown): close upstream.
          if (!finished) {
            finished = true;
            void reader?.cancel().catch(() => {});
          }
          return { value: undefined, done: true };
        },
      };
    },
  };
}

function abortError(): Error {
  const error = new Error('The request was aborted');
  error.name = 'AbortError';
  return error;
}

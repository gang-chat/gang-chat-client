/**
 * Streaming HTTP transport, the TypeScript stand-in for Dart's `http.Client`.
 *
 * Core code (API client, auth client, SSE stream, release downloads) only
 * talks to this interface. Each app supplies an implementation:
 *  - desktop: requests run in the Electron main process (no CORS, proxy
 *    bypass for the Gang Chat hosts, streamed request/response bodies);
 *  - tests: an in-memory fake;
 *  - a future React Native app: a fetch-based client.
 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS';

export type HttpHeaders = Record<string, string>;

/** Request body: text, bytes, or a pull-based chunk stream (uploads with progress / pause). */
export type HttpBody = string | Uint8Array | AsyncIterable<Uint8Array>;

export interface HttpRequest {
  method: HttpMethod;
  url: string;
  headers?: HttpHeaders;
  body?: HttpBody;
  /** Total body length when [body] is a stream; sent as content-length. */
  contentLength?: number;
  /** Abort the request (and any in-flight response stream). */
  signal?: AbortSignal;
}

export interface StreamedResponse {
  statusCode: number;
  /** Lower-cased header names. */
  headers: HttpHeaders;
  contentLength: number | null;
  /** Response body chunks. Iterate once. */
  stream: AsyncIterable<Uint8Array>;
}

export interface HttpResponse {
  statusCode: number;
  headers: HttpHeaders;
  bodyBytes: Uint8Array;
}

export interface HttpClient {
  send(request: HttpRequest): Promise<StreamedResponse>;
  close?(): void;
}

export class ClientException extends Error {
  constructor(
    message: string,
    readonly uri?: string,
    override readonly cause?: unknown,
  ) {
    super(message);
    this.name = 'ClientException';
  }
}

const textDecoder = new TextDecoder('utf-8');
const textEncoder = new TextEncoder();

export function utf8Encode(value: string): Uint8Array {
  return textEncoder.encode(value);
}

export function utf8Decode(bytes: Uint8Array): string {
  return textDecoder.decode(bytes);
}

export async function readAllBytes(stream: AsyncIterable<Uint8Array>): Promise<Uint8Array> {
  const chunks: Uint8Array[] = [];
  let total = 0;
  for await (const chunk of stream) {
    chunks.push(chunk);
    total += chunk.length;
  }
  if (chunks.length === 1) return chunks[0]!;
  const out = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    out.set(chunk, offset);
    offset += chunk.length;
  }
  return out;
}

/** Port of `http.Response.fromStream`. */
export async function responseFromStream(streamed: StreamedResponse): Promise<HttpResponse> {
  return {
    statusCode: streamed.statusCode,
    headers: streamed.headers,
    bodyBytes: await readAllBytes(streamed.stream),
  };
}

export function responseBodyText(response: HttpResponse): string {
  return utf8Decode(response.bodyBytes);
}

/** Convenience wrappers matching `http.Client.get/post/put/patch/delete`. */
export class HttpClientHelpers {
  constructor(readonly client: HttpClient) {}

  async request(
    method: HttpMethod,
    url: string,
    options: { headers?: HttpHeaders; body?: HttpBody; signal?: AbortSignal } = {},
  ): Promise<HttpResponse> {
    const streamed = await this.client.send({ method, url, headers: options.headers, body: options.body, signal: options.signal });
    return responseFromStream(streamed);
  }

  get(url: string, options: { headers?: HttpHeaders; signal?: AbortSignal } = {}): Promise<HttpResponse> {
    return this.request('GET', url, options);
  }

  post(url: string, options: { headers?: HttpHeaders; body?: HttpBody; signal?: AbortSignal } = {}): Promise<HttpResponse> {
    return this.request('POST', url, options);
  }

  put(url: string, options: { headers?: HttpHeaders; body?: HttpBody; signal?: AbortSignal } = {}): Promise<HttpResponse> {
    return this.request('PUT', url, options);
  }

  patch(url: string, options: { headers?: HttpHeaders; body?: HttpBody; signal?: AbortSignal } = {}): Promise<HttpResponse> {
    return this.request('PATCH', url, options);
  }

  delete(url: string, options: { headers?: HttpHeaders; body?: HttpBody; signal?: AbortSignal } = {}): Promise<HttpResponse> {
    return this.request('DELETE', url, options);
  }
}

/** Builds a URL with query parameters, like `Uri.replace(queryParameters:)`. */
export function buildUrl(base: string, query?: Record<string, string | null | undefined>): string {
  if (!query) return base;
  const entries = Object.entries(query).filter((entry): entry is [string, string] => entry[1] != null);
  if (entries.length === 0) return base;
  const url = new URL(base);
  for (const [key, value] of entries) url.searchParams.set(key, value);
  return url.toString();
}

/** A multipart/form-data body builder (port of `http.MultipartRequest`). */
export interface MultipartFile {
  field: string;
  filename: string;
  contentType?: string;
  /** Either the whole file or a chunk stream (with [length]). */
  bytes?: Uint8Array;
  stream?: AsyncIterable<Uint8Array>;
  length: number;
}

export interface MultipartBody {
  contentType: string;
  contentLength: number;
  body: AsyncIterable<Uint8Array>;
}

export function buildMultipartBody(fields: Record<string, string>, files: MultipartFile[]): MultipartBody {
  const boundary = `dart-http-boundary-${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`;
  const crlf = '\r\n';
  const parts: Array<Uint8Array | MultipartFile> = [];
  for (const [name, value] of Object.entries(fields)) {
    parts.push(
      utf8Encode(
        `--${boundary}${crlf}content-disposition: form-data; name="${escapeMultipart(name)}"${crlf}${crlf}${value}${crlf}`,
      ),
    );
  }
  for (const file of files) {
    parts.push(
      utf8Encode(
        `--${boundary}${crlf}content-type: ${file.contentType ?? 'application/octet-stream'}${crlf}` +
          `content-disposition: form-data; name="${escapeMultipart(file.field)}"; filename="${escapeMultipart(file.filename)}"${crlf}${crlf}`,
      ),
    );
    parts.push(file);
    parts.push(utf8Encode(crlf));
  }
  const closing = utf8Encode(`--${boundary}--${crlf}`);
  let contentLength = closing.length;
  for (const part of parts) contentLength += part instanceof Uint8Array ? part.length : part.length;

  async function* generate(): AsyncIterable<Uint8Array> {
    for (const part of parts) {
      if (part instanceof Uint8Array) {
        yield part;
      } else if (part.bytes) {
        yield part.bytes;
      } else if (part.stream) {
        for await (const chunk of part.stream) yield chunk;
      }
    }
    yield closing;
  }

  return { contentType: `multipart/form-data; boundary=${boundary}`, contentLength, body: generate() };
}

function escapeMultipart(value: string): string {
  return value.replace(/\r\n|\r|\n/g, '%0D%0A').replace(/"/g, '%22');
}

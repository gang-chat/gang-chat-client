/**
 * Test double for [HttpClient], the port of `package:http/testing.dart`'s
 * `MockClient`: every request body is read fully before the handler runs, the
 * request is recorded, and the handler's canned response is streamed back.
 * A handler may throw (for example a `ClientException`) to simulate a
 * transport failure.
 */
import {
  readAllBytes,
  utf8Decode,
  utf8Encode,
  type HttpBody,
  type HttpClient,
  type HttpHeaders,
  type HttpMethod,
  type HttpRequest,
  type StreamedResponse,
} from '../../src/platform/http';

export interface MockRequest {
  readonly method: HttpMethod;
  /** The full request URL. */
  readonly url: string;
  /** [url] parsed; use `uri.pathname` / `uri.searchParams` like Dart's `request.url`. */
  readonly uri: URL;
  /** Request headers with lower-cased names. */
  readonly headers: HttpHeaders;
  readonly bodyBytes: Uint8Array;
  /** [bodyBytes] decoded as UTF-8. */
  readonly body: string;
  /** Query parameters as a plain object (Dart `request.url.queryParameters`). */
  readonly queryParameters: Record<string, string>;
  readonly contentLength: number | undefined;
}

export interface MockResponse {
  statusCode: number;
  body?: string | Uint8Array;
  headers?: HttpHeaders;
}

export type MockHandler = (request: MockRequest) => MockResponse | Promise<MockResponse>;

/** Port of `http.Response(body, statusCode, headers: ...)` (the body is UTF-8 encoded). */
export function mockResponse(body: string, statusCode: number, headers: HttpHeaders = {}): MockResponse {
  return { statusCode, body, headers };
}

/** Port of `http.Response.bytes(bytes, statusCode, headers: ...)`. */
export function mockBytesResponse(
  bytes: Uint8Array | readonly number[],
  statusCode: number,
  headers: HttpHeaders = {},
): MockResponse {
  return { statusCode, body: bytes instanceof Uint8Array ? bytes : Uint8Array.from(bytes), headers };
}

/** `http.Response(jsonEncode(value), statusCode, headers: ...)`. */
export function jsonResponse(value: unknown, statusCode = 200, headers: HttpHeaders = {}): MockResponse {
  return mockResponse(JSON.stringify(value), statusCode, headers);
}

export class MockClient implements HttpClient {
  readonly requests: MockRequest[] = [];
  closed = false;

  constructor(private readonly _handler: MockHandler) {}

  async send(request: HttpRequest): Promise<StreamedResponse> {
    const bodyBytes = await _readBody(request.body);
    const uri = new URL(request.url);
    const recorded: MockRequest = {
      method: request.method,
      url: request.url,
      uri,
      headers: _lowerCaseKeys(request.headers ?? {}),
      bodyBytes,
      body: utf8Decode(bodyBytes),
      queryParameters: Object.fromEntries(uri.searchParams.entries()),
      contentLength: request.contentLength,
    };
    this.requests.push(recorded);
    const response = await this._handler(recorded);
    const bytes =
      response.body == null
        ? new Uint8Array(0)
        : typeof response.body === 'string'
          ? utf8Encode(response.body)
          : response.body;
    return {
      statusCode: response.statusCode,
      headers: _lowerCaseKeys(response.headers ?? {}),
      contentLength: bytes.length,
      stream: _single(bytes),
    };
  }

  close(): void {
    this.closed = true;
  }
}

async function _readBody(body: HttpBody | undefined): Promise<Uint8Array> {
  if (body == null) return new Uint8Array(0);
  if (typeof body === 'string') return utf8Encode(body);
  if (body instanceof Uint8Array) return body;
  return readAllBytes(body);
}

async function* _single(bytes: Uint8Array): AsyncIterable<Uint8Array> {
  yield bytes;
}

function _lowerCaseKeys(headers: HttpHeaders): HttpHeaders {
  const result: HttpHeaders = {};
  for (const [key, value] of Object.entries(headers)) result[key.toLowerCase()] = value;
  return result;
}

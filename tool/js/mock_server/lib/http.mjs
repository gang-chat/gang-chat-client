// HTTP plumbing: error type, handler result helpers, body + multipart parsing,
// and the headers every response carries (CORS, request id, server time).
import { randomBytes } from 'node:crypto';

export const SERVER_TIME_HEADER = 'x-gang-server-time';
export const REQUEST_ID_HEADER = 'x-request-id';
export const MAX_BODY_BYTES = 64 * 1024 * 1024;

/**
 * Thrown by handlers; rendered as `{ "error": { code, message, request_id }, ...extra }`,
 * the shape `ApiException.fromResponse` / `AuthException.fromResponse` parse.
 * Messages are English on purpose: the client maps known phrases and codes to
 * its own Chinese copy (packages/core/src/protocol/error_messages.ts).
 */
export class ApiError extends Error {
  constructor(status, code, message, extra = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.extra = extra;
  }
}

export const badRequest = (message = 'invalid request body', code = 'bad_request') => new ApiError(400, code, message);
export const unauthorized = (message = 'invalid token', code = 'unauthorized') => new ApiError(401, code, message);
export const forbidden = (message = 'forbidden', code = 'forbidden') => new ApiError(403, code, message);
export const notFound = (message = 'not found', code = 'not_found') => new ApiError(404, code, message);
export const conflict = (message = 'conflict', code = 'conflict') => new ApiError(409, code, message);

/** Handler results. A plain object returned from a handler means `json(200, value)`. */
export const json = (status, body, headers = {}) => ({ kind: 'json', status, body, headers });
export const created = (body) => json(201, body);
export const ok = () => json(200, { ok: true });
export function binary({ status = 200, contentType, body, filename, disposition = 'inline', headers = {} }) {
  const extra = { ...headers };
  if (filename) extra['content-disposition'] = contentDisposition(disposition, filename);
  return { kind: 'binary', status, contentType, body, headers: extra };
}
/** Returned by handlers that wrote the response themselves (the SSE stream). */
export const HANDLED = Symbol('handled');

export function newRequestId() {
  return `req_${randomBytes(8).toString('hex')}`;
}

export function contentDisposition(type, filename) {
  const ascii = filename.replace(/[^\x20-\x7e]/g, '_').replace(/["\\]/g, '_');
  return `${type}; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(filename)}`;
}

/** CORS + tracing headers set on every response, including errors and the SSE stream. */
export function applyCommonHeaders(req, res, requestId) {
  const origin = req.headers.origin;
  res.setHeader('access-control-allow-origin', origin ?? '*');
  if (origin) res.setHeader('vary', 'origin');
  res.setHeader('access-control-allow-methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
  res.setHeader(
    'access-control-allow-headers',
    req.headers['access-control-request-headers'] ?? 'authorization, content-type, accept, idempotency-key, user-agent',
  );
  res.setHeader('access-control-expose-headers', `${SERVER_TIME_HEADER}, ${REQUEST_ID_HEADER}, content-disposition`);
  res.setHeader('access-control-max-age', '600');
  res.setHeader(REQUEST_ID_HEADER, requestId);
  res.setHeader(SERVER_TIME_HEADER, new Date().toISOString());
}

export function readBody(req, limit = MAX_BODY_BYTES) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    let failed = false;
    req.on('data', (chunk) => {
      if (failed) return;
      size += chunk.length;
      if (size > limit) {
        failed = true;
        reject(new ApiError(413, 'payload_too_large', 'uploaded file is too large'));
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      if (!failed) resolve(Buffer.concat(chunks));
    });
    req.on('error', (error) => {
      if (!failed) reject(error);
    });
  });
}

/** Decodes a JSON request body. Empty bodies decode to `{}`. */
export function parseJsonBody(buffer) {
  const text = buffer.toString('utf8').trim();
  if (text.length === 0) return {};
  try {
    const value = JSON.parse(text);
    return value ?? {};
  } catch {
    throw badRequest('invalid json body');
  }
}

/**
 * Minimal multipart/form-data parser (what `http.MultipartRequest` sends):
 * returns `{ fields: { name: value }, files: [{ field, filename, contentType, data }] }`.
 */
export function parseMultipart(buffer, contentType) {
  const match = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(contentType ?? '');
  if (!match) throw badRequest('missing multipart boundary');
  const delimiter = Buffer.from(`--${(match[1] ?? match[2]).trim()}`);
  const fields = {};
  const files = [];
  let cursor = buffer.indexOf(delimiter);
  while (cursor !== -1) {
    cursor += delimiter.length;
    if (buffer[cursor] === 0x2d && buffer[cursor + 1] === 0x2d) break; // closing `--boundary--`
    if (buffer[cursor] === 0x0d && buffer[cursor + 1] === 0x0a) cursor += 2;
    const headerEnd = buffer.indexOf('\r\n\r\n', cursor);
    if (headerEnd === -1) break;
    const next = buffer.indexOf(delimiter, headerEnd + 4);
    if (next === -1) break;
    let dataEnd = next;
    if (buffer[dataEnd - 2] === 0x0d && buffer[dataEnd - 1] === 0x0a) dataEnd -= 2;
    const headers = parsePartHeaders(buffer.subarray(cursor, headerEnd).toString('utf8'));
    const data = buffer.subarray(headerEnd + 4, dataEnd);
    const disposition = headers['content-disposition'] ?? '';
    const name = dispositionParam(disposition, 'name') ?? '';
    const filename = dispositionFilename(disposition);
    if (filename != null) {
      files.push({ field: name, filename, contentType: headers['content-type'] ?? 'application/octet-stream', data });
    } else {
      fields[name] = data.toString('utf8');
    }
    cursor = next;
  }
  return { fields, files };
}

function parsePartHeaders(text) {
  const headers = {};
  for (const line of text.split('\r\n')) {
    const colon = line.indexOf(':');
    if (colon > 0) headers[line.slice(0, colon).trim().toLowerCase()] = line.slice(colon + 1).trim();
  }
  return headers;
}

function dispositionParam(disposition, key) {
  const match = new RegExp(`(?:^|;)\\s*${key}="((?:[^"\\\\]|\\\\.)*)"`, 'i').exec(disposition);
  if (match) return match[1];
  const bare = new RegExp(`(?:^|;)\\s*${key}=([^;]+)`, 'i').exec(disposition);
  return bare ? bare[1].trim() : null;
}

function dispositionFilename(disposition) {
  const extended = /filename\*=(?:UTF-8|utf-8)''([^;]+)/.exec(disposition);
  if (extended) {
    try {
      return decodeURIComponent(extended[1]);
    } catch {
      // fall through to the plain parameter
    }
  }
  const plain = dispositionParam(disposition, 'filename');
  if (plain == null) return null;
  // Dart's MultipartFile percent-encodes CR, LF and quotes in filenames.
  return plain.replace(/%0D/gi, '\r').replace(/%0A/gi, '\n').replace(/%22/g, '"');
}

/** Parses a single `Range: bytes=a-b` header. Returns null for absent/unsupported ranges. */
export function parseRange(header, size) {
  const match = /^bytes=(\d*)-(\d*)$/.exec(String(header ?? '').trim());
  if (!match || size === 0) return null;
  let start;
  let end;
  if (match[1] === '') {
    const suffix = Number(match[2]);
    if (!suffix) return null;
    start = Math.max(0, size - suffix);
    end = size - 1;
  } else {
    start = Number(match[1]);
    end = match[2] === '' ? size - 1 : Math.min(Number(match[2]), size - 1);
  }
  if (start > end || start >= size) return { unsatisfiable: true };
  return { start, end };
}

export function sendJson(res, status, body, headers = {}) {
  const payload = Buffer.from(JSON.stringify(body));
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': payload.length, ...headers });
  res.end(payload);
}

export function sendError(res, error, requestId) {
  const body = { error: { code: error.code, message: error.message, request_id: requestId }, ...error.extra };
  sendJson(res, error.status, body);
}

/** Writes a handler result (see `json`, `binary`, plain objects). */
export function sendResult(req, res, result) {
  if (result === HANDLED || res.writableEnded) return;
  if (result == null) return sendJson(res, 200, { ok: true });
  if (result.kind === 'binary') {
    const body = Buffer.isBuffer(result.body) ? result.body : Buffer.from(result.body);
    res.writeHead(result.status, {
      'content-type': result.contentType ?? 'application/octet-stream',
      'content-length': body.length,
      ...result.headers,
    });
    res.end(req.method === 'HEAD' ? undefined : body);
    return;
  }
  if (result.kind === 'json') return sendJson(res, result.status, result.body, result.headers);
  return sendJson(res, 200, result);
}

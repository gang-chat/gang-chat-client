import { type HttpResponse, utf8Decode } from '../platform/http';

export const jsonUtf8ContentType = 'application/json; charset=utf-8';
export const jsonAcceptHeader = 'application/json';

export function encodeJsonBody(value: unknown): string {
  return JSON.stringify(value);
}

export function decodeUtf8Body(response: HttpResponse): string {
  return utf8Decode(response.bodyBytes);
}

export function decodeJsonBody(response: HttpResponse): unknown {
  const body = decodeUtf8Body(response);
  if (body.trim().length === 0) return null;
  return JSON.parse(body);
}

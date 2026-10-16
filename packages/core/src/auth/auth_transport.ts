import type { HttpClient } from '../platform/http';
import { platform } from '../platform/platform';

/**
 * Port of `auth_transport.dart` and its `auth_transport_io.dart` /
 * `auth_transport_stub.dart` conditional-import halves, merged into one module
 * because the TypeScript port has a single transport abstraction.
 */
export type AuthHttpClientFactory = () => HttpClient;

/**
 * The client the auth flow falls back to after repeated TLS handshake
 * failures on the direct client.
 *
 * PORT: Dart built an `IOClient` whose `findProxy` honoured the
 * `HTTP(S)_PROXY` environment variables. The port has no per-client proxy
 * switch: every request goes through the host's `platform().http` transport,
 * which owns proxy resolution (the desktop main process applies the system /
 * environment proxy rules itself). The fallback therefore retries through the
 * shared host transport, like the Dart web stub did with `http.Client()`. The
 * returned wrapper has no `close`, so closing an `AuthClient` never shuts the
 * app-wide transport down.
 */
export function createEnvironmentProxyAuthHttpClient(): HttpClient {
  return { send: (request) => platform().http.send(request) };
}

/**
 * Whether [error] is a TLS handshake failure (Dart `HandshakeException`).
 *
 * PORT: JavaScript has no `HandshakeException` type, so this is a best-effort
 * check of the error's name and text. Besides the Dart wording it recognises
 * the Node.js / Chromium (`net::ERR_SSL_*`, `net::ERR_CERT_*`) messages the
 * desktop transport surfaces for failed TLS handshakes.
 */
export function isTlsHandshakeFailure(error: unknown): boolean {
  if (error instanceof Error && error.name === 'HandshakeException') return true;
  return _looksLikeTlsHandshakeFailure(error);
}

function _looksLikeTlsHandshakeFailure(error: unknown): boolean {
  const message = _describe(error).toLowerCase();
  return (
    message.includes('handshakeexception') ||
    message.includes('handshake error') ||
    message.includes('tls handshake') ||
    message.includes('before secure tls connection was established') ||
    message.includes('err_ssl_') ||
    message.includes('err_cert_') ||
    message.includes('ssl routines')
  );
}

/** Dart `error.toString()`. */
function _describe(error: unknown): string {
  try {
    return String(error);
  } catch {
    return '';
  }
}

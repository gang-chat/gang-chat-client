/**
 * Port of `lib/src/live/livekit_url.dart`.
 *
 * The server can advertise its LiveKit endpoint with a loopback host (when
 * LiveKit runs next to the API). Clients reaching the API remotely must then
 * dial the API host instead, and always over a WebSocket scheme.
 */

export function resolveLiveKitServerUrl(opts: { serverUrl: string; apiBaseUrl: string }): string {
  const { serverUrl, apiBaseUrl } = opts;
  const liveKitUri = _parseUri(serverUrl);
  const apiUri = _parseUri(apiBaseUrl);
  if (liveKitUri == null || apiUri == null) return serverUrl;
  if (liveKitUri.host.length === 0 || apiUri.host.length === 0) return serverUrl;

  const host =
    _isLocalAdvertisedHost(liveKitUri.host) && !_isLocalAdvertisedHost(apiUri.host) ? apiUri.host : liveKitUri.host;

  // PORT: Dart's `Uri.replace(...).toString()` keeps the original path (and
  // does not append a trailing `/`), which the WHATWG `URL` serializer would.
  // Rebuild the string from its parts instead.
  const scheme = _webSocketScheme(liveKitUri.scheme);
  const formattedHost = host.includes(':') ? `[${host}]` : host;
  const port = liveKitUri.port == null ? '' : `:${liveKitUri.port}`;
  return `${scheme}://${liveKitUri.userInfo}${formattedHost}${port}${liveKitUri.rest}`;
}

interface _ParsedUri {
  scheme: string;
  /** `user:pass@` including the `@`, or an empty string. */
  userInfo: string;
  /** Lower-cased host without IPv6 brackets. */
  host: string;
  /** Explicit, non-default port. */
  port: string | null;
  /** Path, query and fragment exactly as given. */
  rest: string;
}

const _uriPattern = /^([a-zA-Z][a-zA-Z0-9+.-]*):\/\/([^/?#]*)(.*)$/s;

function _parseUri(value: string): _ParsedUri | null {
  const match = _uriPattern.exec(value.trim());
  if (match == null) return null;
  const scheme = match[1]!.toLowerCase();
  let authority = match[2]!;
  const rest = match[3]!;

  let userInfo = '';
  const at = authority.lastIndexOf('@');
  if (at >= 0) {
    userInfo = authority.slice(0, at + 1);
    authority = authority.slice(at + 1);
  }

  let host: string;
  let port: string | null = null;
  if (authority.startsWith('[')) {
    const close = authority.indexOf(']');
    if (close < 0) return null;
    host = authority.slice(1, close);
    const tail = authority.slice(close + 1);
    if (tail.startsWith(':') && tail.length > 1) port = tail.slice(1);
  } else {
    const colon = authority.lastIndexOf(':');
    if (colon >= 0) {
      host = authority.slice(0, colon);
      const portText = authority.slice(colon + 1);
      if (portText.length > 0) port = portText;
    } else {
      host = authority;
    }
  }
  if (port != null && !/^\d+$/.test(port)) return null;
  // Dart's Uri drops a scheme's default port when parsing.
  if ((scheme === 'http' && port === '80') || (scheme === 'https' && port === '443')) port = null;
  return { scheme, userInfo, host: host.toLowerCase(), port, rest };
}

function _webSocketScheme(scheme: string): string {
  if (scheme === 'https' || scheme === 'wss') return 'wss';
  return 'ws';
}

function _isLocalAdvertisedHost(host: string): boolean {
  const normalized = host.toLowerCase();
  return normalized === 'localhost' || normalized === '127.0.0.1' || normalized === '::1' || normalized === '0.0.0.0';
}

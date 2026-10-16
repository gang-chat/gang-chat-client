import { hasPlatform, platform } from '../platform/platform';

export class FeedbackMailDraft {
  constructor(opts: { from: string; to: string; subject: string; body: string }) {
    this.from = opts.from;
    this.to = opts.to;
    this.subject = opts.subject;
    this.body = opts.body;
  }

  readonly from: string;
  readonly to: string;
  readonly subject: string;
  readonly body: string;

  /**
   * Port of `Uri(scheme: 'mailto', path: to, queryParameters: {...})`,
   * encoded the way Dart does (query components with `+` for spaces).
   */
  toMailtoUri(): string {
    const query = [
      ['subject', this.subject],
      ['body', this.body],
      ['from', this.from],
    ]
      .map(([key, value]) => `${_encodeQueryComponent(key!)}=${_encodeQueryComponent(value!)}`)
      .join('&');
    return `mailto:${_encodePath(this.to)}?${query}`;
  }
}

/**
 * PORT: like [ExternalUriLauncher], the per-OS process spawning, Android
 * bridge and browser `mailto:` navigation are behind `platform().openExternal`.
 */
export class FeedbackMailService {
  openDraft(draft: FeedbackMailDraft): Promise<void> {
    return this.openMailto(draft.toMailtoUri());
  }

  async openMailto(uri: string): Promise<void> {
    if (!hasPlatform() || !(await platform().openExternal(uri))) {
      throw new Error('当前平台不支持打开邮件客户端');
    }
  }
}

/** Dart `Uri.encodeQueryComponent`: unreserved = A-Z a-z 0-9 - . _ ~, space → '+'. */
function _encodeQueryComponent(value: string): string {
  return encodeURIComponent(value)
    .replace(/[!'()*]/g, (char) => `%${char.charCodeAt(0).toString(16).toUpperCase()}`)
    .replace(/%20/g, '+');
}

/** Path segment encoding that keeps `@` (and the other sub-delims Dart's path allows). */
function _encodePath(value: string): string {
  return encodeURIComponent(value).replace(/%(40|21|24|26|27|28|29|2A|2B|2C|3B|3D|3A)/gi, (match) =>
    decodeURIComponent(match),
  );
}

/**
 * Static URL configuration for the Gang Chat client (port of `AppConfig`).
 *
 * Values come from, in priority order:
 *   1. build-time overrides passed to [AppConfig.load] (the desktop app maps
 *      `GANG_API_BASE_URL` / `GANG_ASSET_BASE_URL` / `GANG_RELEASE_BUCKET_URL`
 *      env vars at build time, like the Flutter `--dart-define`s);
 *   2. `assets/config/app_config.json`, the committed default shared with the
 *      Flutter app;
 *   3. the hard-coded fallbacks below.
 */
export class AppConfig {
  constructor(opts: { apiBaseUrl: string; assetBaseUrl: string; releaseBucketUrl: string }) {
    this.apiBaseUrl = opts.apiBaseUrl;
    this.assetBaseUrl = opts.assetBaseUrl;
    this.releaseBucketUrl = opts.releaseBucketUrl;
  }

  static defaults(): AppConfig {
    return new AppConfig({
      apiBaseUrl: AppConfig.fallbackApiBaseUrl,
      assetBaseUrl: AppConfig.fallbackAssetBaseUrl,
      releaseBucketUrl: AppConfig.fallbackReleaseBucketUrl,
    });
  }

  /** Base URL of the REST API, including the `/api/v1` prefix. */
  readonly apiBaseUrl: string;

  /** Origin used to resolve relative asset URLs. No trailing slash. */
  readonly assetBaseUrl: string;

  /** Public S3-compatible bucket URL used to discover releases. No trailing slash. */
  readonly releaseBucketUrl: string;

  static readonly fallbackApiBaseUrl = 'http://127.0.0.1:21116/api/v1';
  static readonly fallbackAssetBaseUrl = 'http://127.0.0.1:21116';
  static readonly fallbackReleaseBucketUrl = 'https://data.ky-z.com:9000/gang-chat';

  /** Resolve [path] (relative or absolute) against [assetBaseUrl]. */
  resolveAssetUrl(path: string | null | undefined): string | null {
    if (path == null || path.length === 0) return null;
    const normalizedBase = this.assetBaseUrl.endsWith('/') ? this.assetBaseUrl.slice(0, -1) : this.assetBaseUrl;

    let parsed: URL | null = null;
    try {
      parsed = new URL(path);
    } catch {
      parsed = null;
    }
    if (parsed != null && (parsed.protocol === 'http:' || parsed.protocol === 'https:')) {
      if (parsed.pathname.startsWith('/assets/')) {
        return `${normalizedBase}${parsed.pathname}${parsed.search}`;
      }
      return path;
    }
    if (path.toLowerCase().startsWith('data:')) return path;

    const suffix = path.startsWith('/') ? path : `/${path}`;
    return `${normalizedBase}${suffix}`;
  }

  /**
   * Build the configuration from the bundled JSON ([assetValues]) and
   * optional build-time [overrides]. Missing or empty values fall back.
   */
  static load(
    assetValues: Record<string, unknown> | null | undefined,
    overrides: { apiBaseUrl?: string; assetBaseUrl?: string; releaseBucketUrl?: string } = {},
  ): AppConfig {
    const values = assetValues ?? {};
    const pick = (key: string, fallback: string, override: string | undefined): string => {
      if (override != null && override.length > 0) return override;
      const value = values[key];
      if (typeof value === 'string' && value.length > 0) return value;
      return fallback;
    };
    return new AppConfig({
      apiBaseUrl: pick('api_base_url', AppConfig.fallbackApiBaseUrl, overrides.apiBaseUrl),
      assetBaseUrl: pick('asset_base_url', AppConfig.fallbackAssetBaseUrl, overrides.assetBaseUrl),
      releaseBucketUrl: pick('release_bucket_url', AppConfig.fallbackReleaseBucketUrl, overrides.releaseBucketUrl),
    });
  }

  /** Hosts the Flutter client sent DIRECT (bypassing any system proxy). */
  get directHosts(): string[] {
    const hosts = new Set<string>();
    for (const value of [this.apiBaseUrl, this.assetBaseUrl, this.releaseBucketUrl]) {
      try {
        const host = new URL(value).hostname.toLowerCase();
        if (host) hosts.add(host);
      } catch {
        // ignore malformed config
      }
    }
    return [...hosts];
  }
}

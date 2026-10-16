import { platform } from '../platform/platform';

/**
 * Stores the auth refresh token and the last-used API base URL.
 *
 * The refresh token is sensitive (it grants a logged-in session), so it stays
 * in the OS keychain via the host's secure storage. The API base URL is not
 * sensitive and lives in the preferences store so reading it never triggers a
 * macOS keychain authorization prompt.
 *
 * PORT: Dart passed `MacOsOptions(usesDataProtectionKeychain: false)` to
 * flutter_secure_storage; keychain options are now the host's
 * `platform().secureStorage` implementation detail.
 */
export class TokenStore {
  private static readonly _refreshTokenKey = 'gang.refreshToken';
  private static readonly _apiBaseUrlKey = 'gang.apiBaseUrl';

  readRefreshToken(): Promise<string | null> {
    return platform().secureStorage.read(TokenStore._refreshTokenKey);
  }

  writeRefreshToken(refreshToken: string): Promise<void> {
    return platform().secureStorage.write(TokenStore._refreshTokenKey, refreshToken);
  }

  clearRefreshToken(): Promise<void> {
    return platform().secureStorage.delete(TokenStore._refreshTokenKey);
  }

  async readApiBaseUrl(): Promise<string | null> {
    const prefs = platform().preferences;
    return prefs.getString(TokenStore._apiBaseUrlKey);
  }

  async writeApiBaseUrl(baseUrl: string): Promise<void> {
    const prefs = platform().preferences;
    await prefs.setString(TokenStore._apiBaseUrlKey, baseUrl);
  }
}

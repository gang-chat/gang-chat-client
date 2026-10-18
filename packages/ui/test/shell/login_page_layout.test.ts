import { afterEach, beforeEach, describe, expect, test } from 'vitest';
import { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';
import { FakeWindow, installFakePlatform } from '../../../core/test/support/fake_platform';
import { authAccountHistoryDropdownBottom, authContentBottom } from '../../src/shell/login_page';

describe('login page layout', () => {
  beforeEach(() => {
    installFakePlatform({ os: 'windows', window: new FakeWindow() });
  });

  afterEach(() => {
    installFakePlatform();
  });

  test('the auth window fits the page with 24px under the button', () => {
    const controller = new DesktopWindowController();
    expect(authContentBottom(false)).toBe(296);
    expect(authContentBottom(true)).toBe(358);
    expect(controller.authWidgetSize(true).height).toBe(authContentBottom(true) + 24);
    // Login leaves room for the saved-accounts dropdown at its full four rows.
    expect(authAccountHistoryDropdownBottom).toBe(325);
    expect(controller.authWidgetSize(false).height).toBe(
      Math.max(authContentBottom(false) + 24, authAccountHistoryDropdownBottom + 8),
    );
  });
});

import type { CurrentUser } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';

export class AccountLoadPatch {
  constructor(opts: { user: CurrentUser | null; loading: boolean; accountError: string | null }) {
    this.user = opts.user;
    this.loading = opts.loading;
    this.accountError = opts.accountError;
  }

  readonly user: CurrentUser | null;
  readonly loading: boolean;
  readonly accountError: string | null;
}

export function accountLoadStarted(opts: { user: CurrentUser | null }): AccountLoadPatch {
  return new AccountLoadPatch({ user: opts.user, loading: true, accountError: null });
}

export function accountLoadSucceeded(opts: { user: CurrentUser }): AccountLoadPatch {
  return new AccountLoadPatch({ user: opts.user, loading: false, accountError: null });
}

export function accountLoadCancelled(opts: { user: CurrentUser | null; accountError: string | null }): AccountLoadPatch {
  return new AccountLoadPatch({ user: opts.user, loading: false, accountError: opts.accountError });
}

export function accountLoadFailed(opts: { user: CurrentUser | null; failure: unknown }): AccountLoadPatch {
  return new AccountLoadPatch({
    user: opts.user,
    loading: false,
    accountError: userFacingErrorMessage(opts.failure),
  });
}

import type { UserSession } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';

export class AccountSessionsLoadPatch {
  constructor(opts: { sessions: UserSession[]; loading: boolean; securityError: string | null }) {
    this.sessions = opts.sessions;
    this.loading = opts.loading;
    this.securityError = opts.securityError;
  }

  readonly sessions: UserSession[];
  readonly loading: boolean;
  readonly securityError: string | null;
}

export function accountSessionsLoadStarted(opts: { sessions: UserSession[] }): AccountSessionsLoadPatch {
  return new AccountSessionsLoadPatch({ sessions: opts.sessions, loading: true, securityError: null });
}

export function accountSessionsLoadSucceeded(opts: { sessions: UserSession[] }): AccountSessionsLoadPatch {
  return new AccountSessionsLoadPatch({ sessions: opts.sessions, loading: false, securityError: null });
}

export function accountSessionsLoadCancelled(opts: {
  sessions: UserSession[];
  securityError: string | null;
}): AccountSessionsLoadPatch {
  return new AccountSessionsLoadPatch({
    sessions: opts.sessions,
    loading: false,
    securityError: opts.securityError,
  });
}

export function accountSessionsLoadFailed(opts: { sessions: UserSession[]; failure: unknown }): AccountSessionsLoadPatch {
  return new AccountSessionsLoadPatch({
    sessions: opts.sessions,
    loading: false,
    securityError: userFacingErrorMessage(opts.failure),
  });
}

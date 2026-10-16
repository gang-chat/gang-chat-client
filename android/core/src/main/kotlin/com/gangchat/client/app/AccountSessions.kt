package com.gangchat.client.app

import com.gangchat.client.protocol.UserSession

class AccountSessionsLoadPatch(
    val sessions: List<UserSession>,
    val loading: Boolean,
    val securityError: String?,
)

fun accountSessionsLoadStarted(sessions: List<UserSession>): AccountSessionsLoadPatch {
    return AccountSessionsLoadPatch(
        sessions = sessions,
        loading = true,
        securityError = null,
    )
}

fun accountSessionsLoadSucceeded(sessions: List<UserSession>): AccountSessionsLoadPatch {
    return AccountSessionsLoadPatch(
        sessions = sessions,
        loading = false,
        securityError = null,
    )
}

fun accountSessionsLoadCancelled(
    sessions: List<UserSession>,
    securityError: String?,
): AccountSessionsLoadPatch {
    return AccountSessionsLoadPatch(
        sessions = sessions,
        loading = false,
        securityError = securityError,
    )
}

fun accountSessionsLoadFailed(
    sessions: List<UserSession>,
    failure: Any,
): AccountSessionsLoadPatch {
    return AccountSessionsLoadPatch(
        sessions = sessions,
        loading = false,
        securityError = userFacingErrorMessage(failure),
    )
}

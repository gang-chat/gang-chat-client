package com.gangchat.client.app

import com.gangchat.client.protocol.CurrentUser

class AccountLoadPatch(
    val user: CurrentUser?,
    val loading: Boolean,
    val accountError: String?,
)

fun accountLoadStarted(user: CurrentUser?): AccountLoadPatch {
    return AccountLoadPatch(user = user, loading = true, accountError = null)
}

fun accountLoadSucceeded(user: CurrentUser): AccountLoadPatch {
    return AccountLoadPatch(user = user, loading = false, accountError = null)
}

fun accountLoadCancelled(
    user: CurrentUser?,
    accountError: String?,
): AccountLoadPatch {
    return AccountLoadPatch(
        user = user,
        loading = false,
        accountError = accountError,
    )
}

fun accountLoadFailed(
    user: CurrentUser?,
    failure: Any,
): AccountLoadPatch {
    return AccountLoadPatch(
        user = user,
        loading = false,
        accountError = userFacingErrorMessage(failure),
    )
}

package com.gangchat.client.app

import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.UserSummary

enum class AccountFormSaveTarget { account, profile, preferences }

enum class AccountAvatarErrorTarget { account, sticker }

enum class PasswordVisibilityField { current, newPassword, confirm }

/**
 * Dart's named constructors `AccountUpdateDraft.invalid(error)`,
 * `.noChanges()` and `.valid(...)` are the companion factories of the same
 * names.
 */
class AccountUpdateDraft private constructor(
    val username: String? = null,
    val email: String? = null,
    val emailPublic: Boolean? = null,
    val phoneNumber: String? = null,
    val phoneNumberPublic: Boolean? = null,
    val language: String? = null,
    val error: String? = null,
    val noChanges: Boolean = false,
) {
    val isValid: Boolean get() = error == null && !noChanges

    companion object {
        fun invalid(error: String): AccountUpdateDraft = AccountUpdateDraft(error = error)

        fun noChanges(): AccountUpdateDraft = AccountUpdateDraft(noChanges = true)

        fun valid(
            username: String? = null,
            email: String? = null,
            emailPublic: Boolean? = null,
            phoneNumber: String? = null,
            phoneNumberPublic: Boolean? = null,
            language: String? = null,
        ): AccountUpdateDraft =
            AccountUpdateDraft(
                username = username,
                email = email,
                emailPublic = emailPublic,
                phoneNumber = phoneNumber,
                phoneNumberPublic = phoneNumberPublic,
                language = language,
                error = null,
                noChanges = false,
            )
    }
}

/** Dart's `ProfileUpdateDraft.invalid` / `.noChanges` / `.valid` factories. */
class ProfileUpdateDraft private constructor(
    val displayName: String? = null,
    val bio: String? = null,
    val gender: String? = null,
    val defaultAvatarKey: String? = null,
    val avatarAssetId: String? = null,
    val error: String? = null,
    val noChanges: Boolean = false,
) {
    val isValid: Boolean get() = error == null && !noChanges

    companion object {
        fun invalid(error: String): ProfileUpdateDraft = ProfileUpdateDraft(error = error)

        fun noChanges(): ProfileUpdateDraft = ProfileUpdateDraft(noChanges = true)

        fun valid(
            displayName: String? = null,
            bio: String? = null,
            gender: String? = null,
            defaultAvatarKey: String? = null,
            avatarAssetId: String? = null,
        ): ProfileUpdateDraft =
            ProfileUpdateDraft(
                displayName = displayName,
                bio = bio,
                gender = gender,
                defaultAvatarKey = defaultAvatarKey,
                avatarAssetId = avatarAssetId,
                error = null,
                noChanges = false,
            )
    }
}

/** Dart's `PasswordChangeDraft.invalid` / `.valid` factories. */
class PasswordChangeDraft private constructor(
    val currentPassword: String? = null,
    val newPassword: String? = null,
    val error: String? = null,
) {
    val isValid: Boolean get() = error == null

    companion object {
        fun invalid(error: String): PasswordChangeDraft = PasswordChangeDraft(error = error)

        fun valid(
            currentPassword: String,
            newPassword: String,
        ): PasswordChangeDraft =
            PasswordChangeDraft(
                currentPassword = currentPassword,
                newPassword = newPassword,
                error = null,
            )
    }
}

private val _loginUsernamePattern = Regex("^[A-Za-z0-9_-]{3,32}$")

fun loginUsernameValidationError(username: String): String? {
    val trimmed = username.trim()
    if (trimmed.isEmpty()) {
        return "登录用户名不能为空"
    }
    if (!_loginUsernamePattern.containsMatchIn(trimmed)) {
        return "登录用户名需为 3-32 位，只能包含英文字母、数字、下划线或连字符"
    }
    return null
}

fun isLoginUsernameFormatValid(username: String): Boolean {
    return loginUsernameValidationError(username) == null
}

fun loginUsernameAvailabilityError(
    user: CurrentUser,
    username: String,
    candidates: Iterable<UserSummary>,
): String? {
    val normalizedUsername = username.trim().lowercase()
    val taken =
        candidates.any { candidate ->
            candidate.id != user.id &&
                candidate.username.trim().lowercase() == normalizedUsername
        }
    return if (taken) "该登录用户名已被其他用户使用" else null
}

class AccountFormSaveStatePatch(
    val savingAccount: Boolean,
    val savingProfile: Boolean,
    val accountError: String?,
    val notice: String?,
)

class AccountEditableFieldsPatch(
    val gender: String,
    val emailPublic: Boolean,
    val phoneNumberPublic: Boolean,
)

class PasswordChangeStatePatch(
    val changingPassword: Boolean,
    val securityError: String?,
    val notice: String?,
)

class PasswordVisibilityPatch(
    val obscureCurrentPassword: Boolean,
    val obscureNewPassword: Boolean,
    val obscureConfirmPassword: Boolean,
)

class AccountDeletionStatePatch(
    val deletingAccount: Boolean,
    val securityError: String?,
    val notice: String?,
)

class AccountAvatarStatePatch(
    val pendingAvatarAssetId: String?,
    val pendingAvatarUrl: String?,
    val clearUploadedAvatar: Boolean,
    val uploadingAvatar: Boolean,
    val accountError: String?,
    val stickerError: String?,
    val notice: String?,
)

fun passwordChangeValidationFailed(
    error: String,
    changingPassword: Boolean,
    notice: String?,
): PasswordChangeStatePatch {
    return PasswordChangeStatePatch(
        changingPassword = changingPassword,
        securityError = error,
        notice = notice,
    )
}

fun passwordVisibilityToggled(
    field: PasswordVisibilityField,
    obscureCurrentPassword: Boolean,
    obscureNewPassword: Boolean,
    obscureConfirmPassword: Boolean,
): PasswordVisibilityPatch {
    return PasswordVisibilityPatch(
        obscureCurrentPassword =
            if (field == PasswordVisibilityField.current) !obscureCurrentPassword else obscureCurrentPassword,
        obscureNewPassword =
            if (field == PasswordVisibilityField.newPassword) !obscureNewPassword else obscureNewPassword,
        obscureConfirmPassword =
            if (field == PasswordVisibilityField.confirm) !obscureConfirmPassword else obscureConfirmPassword,
    )
}

fun passwordChangeStarted(): PasswordChangeStatePatch {
    return PasswordChangeStatePatch(
        changingPassword = true,
        securityError = null,
        notice = null,
    )
}

fun passwordChangeFailed(failure: Any): PasswordChangeStatePatch {
    return PasswordChangeStatePatch(
        changingPassword = false,
        securityError = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun passwordChangeSucceeded(): PasswordChangeStatePatch {
    return PasswordChangeStatePatch(
        changingPassword = false,
        securityError = null,
        notice = passwordUpdatedNotice(),
    )
}

fun accountDeletionStarted(): AccountDeletionStatePatch {
    return AccountDeletionStatePatch(
        deletingAccount = true,
        securityError = null,
        notice = null,
    )
}

fun accountDeletionFailed(failure: Any): AccountDeletionStatePatch {
    return AccountDeletionStatePatch(
        deletingAccount = false,
        securityError = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun accountDeletionFinished(
    securityError: String?,
    notice: String?,
): AccountDeletionStatePatch {
    return AccountDeletionStatePatch(
        deletingAccount = false,
        securityError = securityError,
        notice = notice,
    )
}

fun accountFormSaveValidationFailed(
    error: String,
    savingAccount: Boolean,
    savingProfile: Boolean,
    notice: String?,
): AccountFormSaveStatePatch {
    return AccountFormSaveStatePatch(
        savingAccount = savingAccount,
        savingProfile = savingProfile,
        accountError = error,
        notice = notice,
    )
}

fun accountGenderChanged(
    emailPublic: Boolean,
    phoneNumberPublic: Boolean,
    gender: String,
): AccountEditableFieldsPatch {
    return AccountEditableFieldsPatch(
        gender = normalizeGender(gender),
        emailPublic = emailPublic,
        phoneNumberPublic = phoneNumberPublic,
    )
}

fun accountEmailPublicChanged(
    gender: String,
    phoneNumberPublic: Boolean,
    emailPublic: Boolean,
): AccountEditableFieldsPatch {
    return AccountEditableFieldsPatch(
        gender = gender,
        emailPublic = emailPublic,
        phoneNumberPublic = phoneNumberPublic,
    )
}

fun accountPhoneNumberPublicChanged(
    gender: String,
    emailPublic: Boolean,
    phoneNumberPublic: Boolean,
): AccountEditableFieldsPatch {
    return AccountEditableFieldsPatch(
        gender = gender,
        emailPublic = emailPublic,
        phoneNumberPublic = phoneNumberPublic,
    )
}

fun accountFormSaveNoChanges(
    target: AccountFormSaveTarget,
    savingAccount: Boolean,
    savingProfile: Boolean,
    accountError: String?,
): AccountFormSaveStatePatch {
    return AccountFormSaveStatePatch(
        savingAccount = savingAccount,
        savingProfile = savingProfile,
        accountError = accountError,
        notice = _accountFormNoChangesNotice(target),
    )
}

fun loginUsernameUpdateDraftFromForm(
    user: CurrentUser,
    username: String,
): AccountUpdateDraft {
    val nextUsername = username.trim()
    val validationError = loginUsernameValidationError(nextUsername)
    if (validationError != null) {
        return AccountUpdateDraft.invalid(validationError)
    }
    if (nextUsername == user.username) {
        return AccountUpdateDraft.noChanges()
    }
    return AccountUpdateDraft.valid(username = nextUsername)
}

fun accountFormSaveStarted(
    target: AccountFormSaveTarget,
    savingAccount: Boolean,
    savingProfile: Boolean,
): AccountFormSaveStatePatch {
    return AccountFormSaveStatePatch(
        savingAccount =
            _savingAccountForTarget(
                target,
                savingAccount = savingAccount,
                targetSaving = true,
            ),
        savingProfile =
            _savingProfileForTarget(
                target,
                savingProfile = savingProfile,
                targetSaving = true,
            ),
        accountError = null,
        notice = null,
    )
}

fun accountFormSaveCancelled(
    target: AccountFormSaveTarget,
    savingAccount: Boolean,
    savingProfile: Boolean,
    accountError: String?,
    notice: String?,
): AccountFormSaveStatePatch {
    return AccountFormSaveStatePatch(
        savingAccount =
            _savingAccountForTarget(
                target,
                savingAccount = savingAccount,
                targetSaving = false,
            ),
        savingProfile =
            _savingProfileForTarget(
                target,
                savingProfile = savingProfile,
                targetSaving = false,
            ),
        accountError = accountError,
        notice = notice,
    )
}

fun accountFormSaveFailed(
    target: AccountFormSaveTarget,
    savingAccount: Boolean,
    savingProfile: Boolean,
    failure: Any,
): AccountFormSaveStatePatch {
    return AccountFormSaveStatePatch(
        savingAccount =
            _savingAccountForTarget(
                target,
                savingAccount = savingAccount,
                targetSaving = false,
            ),
        savingProfile =
            _savingProfileForTarget(
                target,
                savingProfile = savingProfile,
                targetSaving = false,
            ),
        accountError = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun accountFormSaveSucceeded(
    target: AccountFormSaveTarget,
    savingAccount: Boolean,
    savingProfile: Boolean,
): AccountFormSaveStatePatch {
    return AccountFormSaveStatePatch(
        savingAccount =
            _savingAccountForTarget(
                target,
                savingAccount = savingAccount,
                targetSaving = false,
            ),
        savingProfile =
            _savingProfileForTarget(
                target,
                savingProfile = savingProfile,
                targetSaving = false,
            ),
        accountError = null,
        notice = _accountFormSavedNotice(target),
    )
}

class AccountPresetAvatarSelectionPatch(
    val defaultAvatarKey: String,
    val pendingAvatarAssetId: String?,
    val pendingAvatarUrl: String?,
    val clearUploadedAvatar: Boolean,
    val notice: String?,
)

fun accountPresetAvatarSelected(
    defaultAvatarKey: String,
    currentAvatarUrl: String?,
): AccountPresetAvatarSelectionPatch {
    val clearUploadedAvatar = shouldClearUploadedAvatarForPreset(currentAvatarUrl)
    return AccountPresetAvatarSelectionPatch(
        defaultAvatarKey = defaultAvatarKey,
        pendingAvatarAssetId = null,
        pendingAvatarUrl = null,
        clearUploadedAvatar = clearUploadedAvatar,
        notice = accountPresetAvatarNotice(clearUploadedAvatar = clearUploadedAvatar),
    )
}

fun accountAvatarPreparationStarted(
    target: AccountAvatarErrorTarget,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    clearUploadedAvatar: Boolean,
    uploadingAvatar: Boolean,
    accountError: String?,
    stickerError: String?,
): AccountAvatarStatePatch {
    return _accountAvatarPatch(
        target = target,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        clearUploadedAvatar = clearUploadedAvatar,
        uploadingAvatar = uploadingAvatar,
        accountError = accountError,
        stickerError = stickerError,
        targetError = null,
        notice = null,
    )
}

fun accountAvatarUploadStarted(
    target: AccountAvatarErrorTarget,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    clearUploadedAvatar: Boolean,
    accountError: String?,
    stickerError: String?,
): AccountAvatarStatePatch {
    return _accountAvatarPatch(
        target = target,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        clearUploadedAvatar = clearUploadedAvatar,
        uploadingAvatar = true,
        accountError = accountError,
        stickerError = stickerError,
        targetError = null,
        notice = null,
    )
}

fun accountAvatarActionCancelled(
    target: AccountAvatarErrorTarget,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    clearUploadedAvatar: Boolean,
    accountError: String?,
    stickerError: String?,
): AccountAvatarStatePatch {
    return _accountAvatarPatch(
        target = target,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        clearUploadedAvatar = clearUploadedAvatar,
        uploadingAvatar = false,
        accountError = accountError,
        stickerError = stickerError,
        targetError = null,
        notice = null,
    )
}

fun accountAvatarActionFailed(
    target: AccountAvatarErrorTarget,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    clearUploadedAvatar: Boolean,
    accountError: String?,
    stickerError: String?,
    failure: Any,
): AccountAvatarStatePatch {
    return _accountAvatarPatch(
        target = target,
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        clearUploadedAvatar = clearUploadedAvatar,
        uploadingAvatar = false,
        accountError = accountError,
        stickerError = stickerError,
        targetError = userFacingErrorMessage(failure),
        notice = null,
    )
}

fun accountAvatarPendingUploadSucceeded(
    assetId: String,
    assetUrl: String,
    stickerError: String?,
): AccountAvatarStatePatch {
    return AccountAvatarStatePatch(
        pendingAvatarAssetId = assetId,
        pendingAvatarUrl = assetUrl,
        clearUploadedAvatar = false,
        uploadingAvatar = false,
        accountError = null,
        stickerError = stickerError,
        notice = avatarUploadedPendingProfileNotice(),
    )
}

fun accountAvatarProfileUpdatedFromStickerSucceeded(
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    clearUploadedAvatar: Boolean,
    accountError: String?,
): AccountAvatarStatePatch {
    return AccountAvatarStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        clearUploadedAvatar = clearUploadedAvatar,
        uploadingAvatar = false,
        accountError = accountError,
        stickerError = null,
        notice = avatarUpdatedNotice(),
    )
}

fun stickerAvatarUploadFilename(stickerId: String): String {
    return "avatar-$stickerId.png"
}

fun accountUpdateDraftFromForm(
    user: CurrentUser,
    username: String,
    email: String,
    emailPublic: Boolean,
    phoneNumber: String,
    phoneNumberPublic: Boolean,
    language: String,
): AccountUpdateDraft {
    val nextUsername = username.trim()
    val nextEmailValue = email.trim()
    val nextPhoneValue = phoneNumber.trim()
    val usernameError = loginUsernameValidationError(nextUsername)
    if (usernameError != null) {
        return AccountUpdateDraft.invalid(usernameError)
    }
    if (nextEmailValue.isEmpty()) {
        return AccountUpdateDraft.invalid("邮箱不能为空")
    }

    val changedUsername = if (nextUsername == user.username) null else nextUsername
    val changedEmail = if (nextEmailValue == (user.email ?: "")) null else nextEmailValue
    val changedEmailPublic = if (emailPublic == user.emailPublic) null else emailPublic
    val changedPhone = if (nextPhoneValue == (user.phoneNumber ?: "")) null else nextPhoneValue
    val changedPhonePublic =
        if (phoneNumberPublic == user.phoneNumberPublic) null else phoneNumberPublic
    val nextLanguage = normalizeAccountLanguage(language)
    val changedLanguage =
        if (nextLanguage == normalizeAccountLanguage(user.language)) null else nextLanguage

    if (changedUsername == null &&
        changedEmail == null &&
        changedEmailPublic == null &&
        changedPhone == null &&
        changedPhonePublic == null &&
        changedLanguage == null
    ) {
        return AccountUpdateDraft.noChanges()
    }

    return AccountUpdateDraft.valid(
        username = changedUsername,
        email = changedEmail,
        emailPublic = changedEmailPublic,
        phoneNumber = changedPhone,
        phoneNumberPublic = changedPhonePublic,
        language = changedLanguage,
    )
}

fun preferencesUpdateDraftFromForm(
    user: CurrentUser,
    language: String,
): AccountUpdateDraft {
    val nextLanguage = normalizeAccountLanguage(language)
    if (nextLanguage == normalizeAccountLanguage(user.language)) {
        return AccountUpdateDraft.noChanges()
    }
    return AccountUpdateDraft.valid(language = nextLanguage)
}

fun normalizeAccountLanguage(language: String): String {
    return normalizeLanguagePreference(language)
}

fun profileUpdateDraftFromForm(
    user: CurrentUser,
    displayName: String,
    bio: String,
    gender: String,
    defaultAvatarKey: String,
    pendingAvatarAssetId: String? = null,
    clearUploadedAvatar: Boolean,
): ProfileUpdateDraft {
    val nextDisplayNameValue = displayName.trim()
    val nextBioValue = bio.trim()
    if (nextDisplayNameValue.isEmpty()) {
        return ProfileUpdateDraft.invalid("用户名不能为空")
    }

    val changedDisplayName =
        if (nextDisplayNameValue == user.displayName) null else nextDisplayNameValue
    val changedBio = if (nextBioValue == user.bio) null else nextBioValue
    val changedGender = if (gender == normalizeGender(user.gender)) null else gender
    val changedAvatarKey = if (defaultAvatarKey == user.defaultAvatarKey) null else defaultAvatarKey
    val changedAvatarAssetId = pendingAvatarAssetId ?: (if (clearUploadedAvatar) "" else null)

    if (changedDisplayName == null &&
        changedBio == null &&
        changedGender == null &&
        changedAvatarKey == null &&
        changedAvatarAssetId == null
    ) {
        return ProfileUpdateDraft.noChanges()
    }

    return ProfileUpdateDraft.valid(
        displayName = changedDisplayName,
        bio = changedBio,
        gender = changedGender,
        defaultAvatarKey = changedAvatarKey,
        avatarAssetId = changedAvatarAssetId,
    )
}

fun passwordChangeDraftFromForm(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    currentPasswordRequired: Boolean = true,
): PasswordChangeDraft {
    if ((currentPasswordRequired && currentPassword.isEmpty()) ||
        newPassword.isEmpty() ||
        confirmPassword.isEmpty()
    ) {
        return PasswordChangeDraft.invalid(
            if (currentPasswordRequired) "请完整填写当前密码、新密码和确认密码" else "请完整填写新密码和确认密码",
        )
    }
    if (newPassword.length < 8) {
        return PasswordChangeDraft.invalid("新密码至少需要 8 个字符")
    }
    if (newPassword != confirmPassword) {
        return PasswordChangeDraft.invalid("两次输入的新密码不一致")
    }
    return PasswordChangeDraft.valid(
        currentPassword = currentPassword,
        newPassword = newPassword,
    )
}

private fun _savingAccountForTarget(
    target: AccountFormSaveTarget,
    savingAccount: Boolean,
    targetSaving: Boolean,
): Boolean {
    return if (target == AccountFormSaveTarget.profile) savingAccount else targetSaving
}

private fun _savingProfileForTarget(
    target: AccountFormSaveTarget,
    savingProfile: Boolean,
    targetSaving: Boolean,
): Boolean {
    return if (target == AccountFormSaveTarget.profile) targetSaving else savingProfile
}

private fun _accountFormNoChangesNotice(target: AccountFormSaveTarget): String {
    return when (target) {
        AccountFormSaveTarget.account -> accountNoBindingChangesNotice()
        AccountFormSaveTarget.preferences -> preferencesNoChangesNotice()
        AccountFormSaveTarget.profile -> profileNoChangesNotice()
    }
}

private fun _accountFormSavedNotice(target: AccountFormSaveTarget): String {
    return when (target) {
        AccountFormSaveTarget.account -> accountBindingsSavedNotice()
        AccountFormSaveTarget.preferences -> preferencesSavedNotice()
        AccountFormSaveTarget.profile -> profileSavedNotice()
    }
}

private fun _accountAvatarPatch(
    target: AccountAvatarErrorTarget,
    pendingAvatarAssetId: String?,
    pendingAvatarUrl: String?,
    clearUploadedAvatar: Boolean,
    uploadingAvatar: Boolean,
    accountError: String?,
    stickerError: String?,
    targetError: String?,
    notice: String?,
): AccountAvatarStatePatch {
    return AccountAvatarStatePatch(
        pendingAvatarAssetId = pendingAvatarAssetId,
        pendingAvatarUrl = pendingAvatarUrl,
        clearUploadedAvatar = clearUploadedAvatar,
        uploadingAvatar = uploadingAvatar,
        accountError = if (target == AccountAvatarErrorTarget.account) targetError else accountError,
        stickerError = if (target == AccountAvatarErrorTarget.sticker) targetError else stickerError,
        notice = notice,
    )
}

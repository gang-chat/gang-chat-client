package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.protocol.defaultUserLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountFormsTest {
    @Test
    fun `accountUpdateDraftFromForm validates required fields`() {
        assertEquals(
            "登录用户名不能为空",
            accountUpdateDraftFromForm(
                user = _user(),
                username = "",
                email = "logan@example.test",
                emailPublic = false,
                phoneNumber = "",
                phoneNumberPublic = false,
                language = defaultUserLanguage,
            ).error,
        )
        assertEquals(
            "邮箱不能为空",
            accountUpdateDraftFromForm(
                user = _user(),
                username = "logan",
                email = "",
                emailPublic = false,
                phoneNumber = "",
                phoneNumberPublic = false,
                language = defaultUserLanguage,
            ).error,
        )
        assertEquals(
            "登录用户名需为 3-32 位，只能包含英文字母、数字、下划线或连字符",
            accountUpdateDraftFromForm(
                user = _user(),
                username = "lo gan",
                email = "logan@example.test",
                emailPublic = false,
                phoneNumber = "",
                phoneNumberPublic = false,
                language = defaultUserLanguage,
            ).error,
        )
    }

    @Test
    fun `loginUsernameUpdateDraftFromForm validates username only`() {
        assertNotNull(loginUsernameValidationError("lo"))
        assertNull(loginUsernameValidationError("logan_01-test"))
        assertFalse(isLoginUsernameFormatValid("logan.test"))

        assertTrue(
            loginUsernameUpdateDraftFromForm(
                user = _user(username = "logan"),
                username = " logan ",
            ).noChanges,
        )

        val draft =
            loginUsernameUpdateDraftFromForm(
                user = _user(username = "logan"),
                username = " new_logan ",
            )
        assertNull(draft.error)
        assertEquals("new_logan", draft.username)
        assertNull(draft.email)
        assertNull(draft.language)
    }

    @Test
    fun `loginUsernameAvailabilityError detects other users only`() {
        assertNull(
            loginUsernameAvailabilityError(
                user = _user(id = "user_1", username = "logan"),
                username = "Logan",
                candidates =
                    listOf(
                        _summary(id = "user_1", username = "logan"),
                        _summary(id = "user_2", username = "logan_suffix"),
                    ),
            ),
        )
        assertEquals(
            "该登录用户名已被其他用户使用",
            loginUsernameAvailabilityError(
                user = _user(id = "user_1", username = "logan"),
                username = "New_Logan",
                candidates = listOf(_summary(id = "user_2", username = "new_logan")),
            ),
        )
    }

    @Test
    fun `accountUpdateDraftFromForm returns changed account fields only`() {
        val draft =
            accountUpdateDraftFromForm(
                user = _user(email = "old@example.test", phoneNumber = ""),
                username = " logan ",
                email = "new@example.test",
                emailPublic = true,
                phoneNumber = " 123 ",
                phoneNumberPublic = false,
                language = "zh-Hant",
            )

        assertNull(draft.error)
        assertFalse(draft.noChanges)
        assertNull(draft.username)
        assertEquals("new@example.test", draft.email)
        assertEquals(true, draft.emailPublic)
        assertEquals("123", draft.phoneNumber)
        assertNull(draft.phoneNumberPublic)
        assertEquals("zh-Hant", draft.language)
    }

    @Test
    fun `preferencesUpdateDraftFromForm returns language changes only`() {
        assertTrue(
            preferencesUpdateDraftFromForm(
                user = _user(language = "zh-Hans"),
                language = "zh-Hans",
            ).noChanges,
        )

        val draft =
            preferencesUpdateDraftFromForm(
                user = _user(username = "dirty", email = "dirty@example.test"),
                language = "en",
            )

        assertNull(draft.error)
        assertFalse(draft.noChanges)
        assertNull(draft.username)
        assertNull(draft.email)
        assertNull(draft.emailPublic)
        assertNull(draft.phoneNumber)
        assertNull(draft.phoneNumberPublic)
        assertEquals("en", draft.language)
    }

    @Test
    fun `profileUpdateDraftFromForm validates and returns changed fields only`() {
        assertEquals(
            "用户名不能为空",
            profileUpdateDraftFromForm(
                user = _user(),
                displayName = " ",
                bio = "",
                gender = "secret",
                defaultAvatarKey = "blue-3",
                clearUploadedAvatar = false,
            ).error,
        )

        val draft =
            profileUpdateDraftFromForm(
                user = _user(bio = "old", gender = "unknown"),
                displayName = " Logan ",
                bio = "new",
                gender = "male",
                defaultAvatarKey = "green-2",
                pendingAvatarAssetId = "asset_1",
                clearUploadedAvatar = false,
            )

        assertNull(draft.error)
        assertNull(draft.displayName)
        assertEquals("new", draft.bio)
        assertEquals("male", draft.gender)
        assertEquals("green-2", draft.defaultAvatarKey)
        assertEquals("asset_1", draft.avatarAssetId)
    }

    @Test
    fun `profile and account drafts report noChanges`() {
        assertTrue(
            accountUpdateDraftFromForm(
                user = _user(email = "logan@example.test"),
                username = "logan",
                email = "logan@example.test",
                emailPublic = false,
                phoneNumber = "",
                phoneNumberPublic = false,
                language = defaultUserLanguage,
            ).noChanges,
        )
        assertTrue(
            profileUpdateDraftFromForm(
                user = _user(),
                displayName = "Logan",
                bio = "",
                gender = "secret",
                defaultAvatarKey = "blue-3",
                clearUploadedAvatar = false,
            ).noChanges,
        )
    }

    @Test
    fun `account editable fields patches update one form field at a time`() {
        val genderPatch =
            accountGenderChanged(
                gender = "female",
                emailPublic = true,
                phoneNumberPublic = false,
            )

        assertEquals("female", genderPatch.gender)
        assertTrue(genderPatch.emailPublic)
        assertFalse(genderPatch.phoneNumberPublic)

        val emailPatch =
            accountEmailPublicChanged(
                gender = genderPatch.gender,
                emailPublic = false,
                phoneNumberPublic = genderPatch.phoneNumberPublic,
            )

        assertEquals("female", emailPatch.gender)
        assertFalse(emailPatch.emailPublic)
        assertFalse(emailPatch.phoneNumberPublic)

        val phonePatch =
            accountPhoneNumberPublicChanged(
                gender = emailPatch.gender,
                emailPublic = emailPatch.emailPublic,
                phoneNumberPublic = true,
            )

        assertEquals("female", phonePatch.gender)
        assertFalse(phonePatch.emailPublic)
        assertTrue(phonePatch.phoneNumberPublic)
    }

    @Test
    fun `passwordChangeDraftFromForm validates and preserves passwords`() {
        assertEquals(
            "请完整填写当前密码、新密码和确认密码",
            passwordChangeDraftFromForm(
                currentPassword = "",
                newPassword = "new-password",
                confirmPassword = "new-password",
            ).error,
        )
        assertEquals(
            "新密码至少需要 8 个字符",
            passwordChangeDraftFromForm(
                currentPassword = "old-password",
                newPassword = "short",
                confirmPassword = "short",
            ).error,
        )
        assertEquals(
            "两次输入的新密码不一致",
            passwordChangeDraftFromForm(
                currentPassword = "old-password",
                newPassword = "new-password",
                confirmPassword = "other-password",
            ).error,
        )

        val draft =
            passwordChangeDraftFromForm(
                currentPassword = " old-password ",
                newPassword = " new-password ",
                confirmPassword = " new-password ",
            )

        assertTrue(draft.isValid)
        assertEquals(" old-password ", draft.currentPassword)
        assertEquals(" new-password ", draft.newPassword)

        val verifiedDraft =
            passwordChangeDraftFromForm(
                currentPassword = "",
                newPassword = "new-password",
                confirmPassword = "new-password",
                currentPasswordRequired = false,
            )
        assertTrue(verifiedDraft.isValid)
        assertTrue(verifiedDraft.currentPassword!!.isEmpty())
    }

    @Test
    fun `passwordVisibilityToggled flips only the requested field`() {
        val current =
            passwordVisibilityToggled(
                field = PasswordVisibilityField.current,
                obscureCurrentPassword = true,
                obscureNewPassword = true,
                obscureConfirmPassword = false,
            )

        assertFalse(current.obscureCurrentPassword)
        assertTrue(current.obscureNewPassword)
        assertFalse(current.obscureConfirmPassword)

        val next =
            passwordVisibilityToggled(
                field = PasswordVisibilityField.newPassword,
                obscureCurrentPassword = current.obscureCurrentPassword,
                obscureNewPassword = current.obscureNewPassword,
                obscureConfirmPassword = current.obscureConfirmPassword,
            )

        assertFalse(next.obscureCurrentPassword)
        assertFalse(next.obscureNewPassword)
        assertFalse(next.obscureConfirmPassword)

        val confirm =
            passwordVisibilityToggled(
                field = PasswordVisibilityField.confirm,
                obscureCurrentPassword = next.obscureCurrentPassword,
                obscureNewPassword = next.obscureNewPassword,
                obscureConfirmPassword = next.obscureConfirmPassword,
            )

        assertFalse(confirm.obscureCurrentPassword)
        assertFalse(confirm.obscureNewPassword)
        assertTrue(confirm.obscureConfirmPassword)
    }

    @Test
    fun `passwordChangeValidationFailed preserves busy state and notice`() {
        val patch =
            passwordChangeValidationFailed(
                error = "新密码至少需要 8 个字符",
                changingPassword = true,
                notice = "previous notice",
            )

        assertTrue(patch.changingPassword)
        assertEquals("新密码至少需要 8 个字符", patch.securityError)
        assertEquals("previous notice", patch.notice)
    }

    @Test
    fun `passwordChangeStarted clears security feedback and marks busy`() {
        val patch = passwordChangeStarted()

        assertTrue(patch.changingPassword)
        assertNull(patch.securityError)
        assertNull(patch.notice)
    }

    @Test
    fun `passwordChangeFailed clears busy state and reports error`() {
        val patch = passwordChangeFailed("request failed")

        assertFalse(patch.changingPassword)
        assertEquals("操作失败，请稍后重试", patch.securityError)
        assertNull(patch.notice)
    }

    @Test
    fun `passwordChangeSucceeded clears busy state and reports notice`() {
        val patch = passwordChangeSucceeded()

        assertFalse(patch.changingPassword)
        assertNull(patch.securityError)
        assertEquals("密码已更新", patch.notice)
    }

    @Test
    fun `accountDeletionStarted clears security feedback and marks busy`() {
        val patch = accountDeletionStarted()

        assertTrue(patch.deletingAccount)
        assertNull(patch.securityError)
        assertNull(patch.notice)
    }

    @Test
    fun `accountDeletionFailed clears busy state and reports error`() {
        val patch = accountDeletionFailed("delete failed")

        assertFalse(patch.deletingAccount)
        assertEquals("操作失败，请稍后重试", patch.securityError)
        assertNull(patch.notice)
    }

    @Test
    fun `accountDeletionFinished clears busy state and preserves feedback`() {
        val patch =
            accountDeletionFinished(
                securityError = null,
                notice = "previous notice",
            )

        assertFalse(patch.deletingAccount)
        assertNull(patch.securityError)
        assertEquals("previous notice", patch.notice)
    }

    @Test
    fun `accountFormSaveValidationFailed preserves busy state and notice`() {
        val patch =
            accountFormSaveValidationFailed(
                error = "邮箱不能为空",
                savingAccount = true,
                savingProfile = false,
                notice = "previous notice",
            )

        assertTrue(patch.savingAccount)
        assertFalse(patch.savingProfile)
        assertEquals("邮箱不能为空", patch.accountError)
        assertEquals("previous notice", patch.notice)
    }

    @Test
    fun `accountFormSaveNoChanges reports notice without clearing error`() {
        val accountPatch =
            accountFormSaveNoChanges(
                target = AccountFormSaveTarget.account,
                savingAccount = false,
                savingProfile = true,
                accountError = "previous error",
            )
        val profilePatch =
            accountFormSaveNoChanges(
                target = AccountFormSaveTarget.profile,
                savingAccount = true,
                savingProfile = false,
                accountError = null,
            )
        val preferencesPatch =
            accountFormSaveNoChanges(
                target = AccountFormSaveTarget.preferences,
                savingAccount = true,
                savingProfile = false,
                accountError = null,
            )

        assertFalse(accountPatch.savingAccount)
        assertTrue(accountPatch.savingProfile)
        assertEquals("previous error", accountPatch.accountError)
        assertEquals("没有账号绑定变更", accountPatch.notice)
        assertTrue(profilePatch.savingAccount)
        assertFalse(profilePatch.savingProfile)
        assertNull(profilePatch.accountError)
        assertEquals("没有用户资料变更", profilePatch.notice)
        assertTrue(preferencesPatch.savingAccount)
        assertFalse(preferencesPatch.savingProfile)
        assertEquals("没有偏好设置变更", preferencesPatch.notice)
    }

    @Test
    fun `accountFormSaveStarted sets only the target busy flag`() {
        val accountPatch =
            accountFormSaveStarted(
                target = AccountFormSaveTarget.account,
                savingAccount = false,
                savingProfile = true,
            )
        val profilePatch =
            accountFormSaveStarted(
                target = AccountFormSaveTarget.profile,
                savingAccount = true,
                savingProfile = false,
            )
        val preferencesPatch =
            accountFormSaveStarted(
                target = AccountFormSaveTarget.preferences,
                savingAccount = false,
                savingProfile = true,
            )

        assertTrue(accountPatch.savingAccount)
        assertTrue(accountPatch.savingProfile)
        assertNull(accountPatch.accountError)
        assertNull(accountPatch.notice)
        assertTrue(profilePatch.savingAccount)
        assertTrue(profilePatch.savingProfile)
        assertNull(profilePatch.accountError)
        assertNull(profilePatch.notice)
        assertTrue(preferencesPatch.savingAccount)
        assertTrue(preferencesPatch.savingProfile)
    }

    @Test
    fun `accountFormSaveCancelled clears only the target busy flag`() {
        val patch =
            accountFormSaveCancelled(
                target = AccountFormSaveTarget.profile,
                savingAccount = true,
                savingProfile = true,
                accountError = "previous error",
                notice = "previous notice",
            )

        assertTrue(patch.savingAccount)
        assertFalse(patch.savingProfile)
        assertEquals("previous error", patch.accountError)
        assertEquals("previous notice", patch.notice)
    }

    @Test
    fun `accountFormSaveFailed clears target busy flag and sets error`() {
        val patch =
            accountFormSaveFailed(
                target = AccountFormSaveTarget.account,
                savingAccount = true,
                savingProfile = true,
                failure = "request failed",
            )

        assertFalse(patch.savingAccount)
        assertTrue(patch.savingProfile)
        assertEquals("操作失败，请稍后重试", patch.accountError)
        assertNull(patch.notice)
    }

    @Test
    fun `accountFormSaveSucceeded clears target busy flag and sets notice`() {
        val accountPatch =
            accountFormSaveSucceeded(
                target = AccountFormSaveTarget.account,
                savingAccount = true,
                savingProfile = false,
            )
        val profilePatch =
            accountFormSaveSucceeded(
                target = AccountFormSaveTarget.profile,
                savingAccount = false,
                savingProfile = true,
            )
        val preferencesPatch =
            accountFormSaveSucceeded(
                target = AccountFormSaveTarget.preferences,
                savingAccount = true,
                savingProfile = false,
            )

        assertFalse(accountPatch.savingAccount)
        assertFalse(accountPatch.savingProfile)
        assertNull(accountPatch.accountError)
        assertEquals("账号绑定已保存", accountPatch.notice)
        assertFalse(profilePatch.savingAccount)
        assertFalse(profilePatch.savingProfile)
        assertNull(profilePatch.accountError)
        assertEquals("用户资料已保存", profilePatch.notice)
        assertFalse(preferencesPatch.savingAccount)
        assertFalse(preferencesPatch.savingProfile)
        assertEquals("偏好设置已保存", preferencesPatch.notice)
    }

    @Test
    fun `accountPresetAvatarSelected clears pending upload state`() {
        val patch =
            accountPresetAvatarSelected(
                defaultAvatarKey = "green-2",
                currentAvatarUrl = "/assets/avatar.png",
            )

        assertEquals("green-2", patch.defaultAvatarKey)
        assertNull(patch.pendingAvatarAssetId)
        assertNull(patch.pendingAvatarUrl)
        assertTrue(patch.clearUploadedAvatar)
        assertEquals("保存用户资料后将使用预设头像", patch.notice)
    }

    @Test
    fun `accountAvatarPreparationStarted clears target error and notice`() {
        val patch =
            accountAvatarPreparationStarted(
                target = AccountAvatarErrorTarget.account,
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/avatar.png",
                clearUploadedAvatar = true,
                uploadingAvatar = true,
                accountError = "account failed",
                stickerError = "sticker failed",
            )

        assertEquals("asset_1", patch.pendingAvatarAssetId)
        assertEquals("/avatar.png", patch.pendingAvatarUrl)
        assertTrue(patch.clearUploadedAvatar)
        assertTrue(patch.uploadingAvatar)
        assertNull(patch.accountError)
        assertEquals("sticker failed", patch.stickerError)
        assertNull(patch.notice)
    }

    @Test
    fun `accountAvatarUploadStarted marks avatar upload busy`() {
        val patch =
            accountAvatarUploadStarted(
                target = AccountAvatarErrorTarget.sticker,
                pendingAvatarAssetId = null,
                pendingAvatarUrl = null,
                clearUploadedAvatar = false,
                accountError = "account failed",
                stickerError = "sticker failed",
            )

        assertTrue(patch.uploadingAvatar)
        assertEquals("account failed", patch.accountError)
        assertNull(patch.stickerError)
        assertNull(patch.notice)
    }

    @Test
    fun `accountAvatarActionFailed routes errors by target`() {
        val accountPatch =
            accountAvatarActionFailed(
                target = AccountAvatarErrorTarget.account,
                pendingAvatarAssetId = null,
                pendingAvatarUrl = null,
                clearUploadedAvatar = false,
                accountError = null,
                stickerError = "sticker failed",
                failure = "account failed",
            )
        val stickerPatch =
            accountAvatarActionFailed(
                target = AccountAvatarErrorTarget.sticker,
                pendingAvatarAssetId = null,
                pendingAvatarUrl = null,
                clearUploadedAvatar = false,
                accountError = "account failed",
                stickerError = null,
                failure = "sticker failed",
            )

        assertFalse(accountPatch.uploadingAvatar)
        assertEquals("操作失败，请稍后重试", accountPatch.accountError)
        assertEquals("sticker failed", accountPatch.stickerError)
        assertFalse(stickerPatch.uploadingAvatar)
        assertEquals("account failed", stickerPatch.accountError)
        assertEquals("操作失败，请稍后重试", stickerPatch.stickerError)
    }

    @Test
    fun `accountAvatarActionCancelled clears target error and busy state`() {
        val patch =
            accountAvatarActionCancelled(
                target = AccountAvatarErrorTarget.account,
                pendingAvatarAssetId = "asset_1",
                pendingAvatarUrl = "/avatar.png",
                clearUploadedAvatar = false,
                accountError = "account failed",
                stickerError = "sticker failed",
            )

        assertEquals("asset_1", patch.pendingAvatarAssetId)
        assertEquals("/avatar.png", patch.pendingAvatarUrl)
        assertFalse(patch.uploadingAvatar)
        assertNull(patch.accountError)
        assertEquals("sticker failed", patch.stickerError)
        assertNull(patch.notice)
    }

    @Test
    fun `accountAvatarPendingUploadSucceeded stores pending avatar`() {
        val patch =
            accountAvatarPendingUploadSucceeded(
                assetId = "asset_1",
                assetUrl = "/avatar.png",
                stickerError = "sticker failed",
            )

        assertEquals("asset_1", patch.pendingAvatarAssetId)
        assertEquals("/avatar.png", patch.pendingAvatarUrl)
        assertFalse(patch.clearUploadedAvatar)
        assertFalse(patch.uploadingAvatar)
        assertNull(patch.accountError)
        assertEquals("sticker failed", patch.stickerError)
        assertEquals("头像已上传，保存用户资料后生效", patch.notice)
    }

    @Test
    fun `accountAvatarProfileUpdatedFromStickerSucceeded clears sticker state`() {
        val patch =
            accountAvatarProfileUpdatedFromStickerSucceeded(
                pendingAvatarAssetId = null,
                pendingAvatarUrl = null,
                clearUploadedAvatar = false,
                accountError = "account failed",
            )

        assertFalse(patch.uploadingAvatar)
        assertEquals("account failed", patch.accountError)
        assertNull(patch.stickerError)
        assertEquals("头像已更新", patch.notice)
    }

    @Test
    fun `stickerAvatarUploadFilename stays stable for avatar uploads`() {
        assertEquals(
            "avatar-sticker_1.png",
            stickerAvatarUploadFilename(stickerId = "sticker_1"),
        )
    }

    private fun _user(
        id: String = "user_1",
        username: String = "logan",
        displayName: String = "Logan",
        bio: String = "",
        gender: String = "secret",
        email: String? = null,
        emailPublic: Boolean = false,
        phoneNumber: String? = null,
        phoneNumberPublic: Boolean = false,
        defaultAvatarKey: String = "blue-3",
        language: String = defaultUserLanguage,
    ): CurrentUser {
        return CurrentUser(
            id = id,
            uid = "1001",
            username = username,
            displayName = displayName,
            bio = bio,
            gender = gender,
            email = email,
            emailPublic = emailPublic,
            phoneNumber = phoneNumber,
            phoneNumberPublic = phoneNumberPublic,
            avatarUrl = null,
            defaultAvatarKey = defaultAvatarKey,
            isSuperuser = false,
            language = language,
            createdAt = DateTime.utc(2026, 6, 4),
        )
    }

    private fun _summary(id: String, username: String): UserSummary {
        return UserSummary(
            id = id,
            uid = id,
            username = username,
            displayName = username,
            bio = "",
            gender = "secret",
            email = null,
            emailPublic = false,
            phoneNumber = null,
            phoneNumberPublic = false,
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            isSuperuser = false,
            isOnline = true,
        )
    }
}

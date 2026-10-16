package com.gangchat.client.app

import com.gangchat.client.protocol.AppVersionInfo
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.StickerPackStore
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSession
import com.gangchat.client.protocol.UserSummary

class PersonalStickerPacksResult(
    val packs: List<StickerPack>,
    val fromCache: Boolean,
)

class SettingsController(
    val api: GangApi?,
    val apiBaseUrl: String,
    val stickerPackStore: StickerPackStore,
    val managedUserId: String? = null,
) {
    val hasApi: Boolean get() = api != null
    val isManagingUser: Boolean get() = managedUserId != null
    val canDeleteAccount: Boolean get() = true

    suspend fun checkAppVersion(): AppVersionInfo? {
        val client = api ?: return null
        return client.getAppVersion()
    }

    suspend fun loadAccount(): CurrentUser? {
        val client = api ?: return null
        val targetUserId = managedUserId
        if (targetUserId != null) {
            return client.getForcedUserSettings(targetUserId)
        }
        return client.me()
    }

    suspend fun searchUsers(
        query: String,
        limit: Int = 20,
    ): List<UserSummary>? {
        val client = api ?: return null
        return client.searchUsers(query = query, limit = limit)
    }

    suspend fun loadSessions(): List<UserSession>? {
        val client = api ?: return null
        val targetUserId = managedUserId
        if (targetUserId != null) {
            return client.listForcedUserSessions(targetUserId)
        }
        return client.listSessions()
    }

    suspend fun loadPersonalStickerPacks(
        userId: String,
        forceReload: Boolean = false,
    ): PersonalStickerPacksResult? {
        if (!forceReload) {
            val cached =
                stickerPackStore.readPersonalPacks(
                    userId = userId,
                    apiBaseUrl = apiBaseUrl,
                )
            if (cached != null) {
                return PersonalStickerPacksResult(packs = cached, fromCache = true)
            }
        }

        val client = api ?: return null
        val packs =
            client.listStickerPacks(
                scope = "personal",
                ownerUserId = managedUserId,
            )
        stickerPackStore.writePersonalPacks(
            userId = userId,
            apiBaseUrl = apiBaseUrl,
            packs = packs,
        )
        return PersonalStickerPacksResult(packs = packs, fromCache = false)
    }

    suspend fun updateAccount(
        username: String? = null,
        email: String? = null,
        emailVerificationToken: String? = null,
        emailPublic: Boolean? = null,
        phoneNumber: String? = null,
        phoneNumberPublic: Boolean? = null,
        language: String? = null,
    ): CurrentUser? {
        val client = api ?: return null
        val targetUserId = managedUserId
        if (targetUserId != null) {
            return client.updateForcedUserSettings(
                userId = targetUserId,
                username = username,
                email = email,
                emailVerified = if (emailVerificationToken == null) null else true,
                emailPublic = emailPublic,
                phoneNumber = phoneNumber,
                phoneNumberPublic = phoneNumberPublic,
                language = language,
            )
        }
        return client.updateAccount(
            username = username,
            email = email,
            emailVerificationToken = emailVerificationToken,
            emailPublic = emailPublic,
            phoneNumber = phoneNumber,
            phoneNumberPublic = phoneNumberPublic,
            language = language,
        )
    }

    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        gender: String? = null,
        avatarAssetId: String? = null,
        defaultAvatarKey: String? = null,
    ): CurrentUser? {
        val client = api ?: return null
        val targetUserId = managedUserId
        if (targetUserId != null) {
            return client.updateForcedUserSettings(
                userId = targetUserId,
                displayName = displayName,
                bio = bio,
                gender = gender,
                avatarAssetId = avatarAssetId,
                defaultAvatarKey = defaultAvatarKey,
            )
        }
        return client.updateProfile(
            displayName = displayName,
            bio = bio,
            gender = gender,
            avatarAssetId = avatarAssetId,
            defaultAvatarKey = defaultAvatarKey,
        )
    }

    suspend fun uploadImageAsset(
        bytes: ByteArray,
        filename: String,
        purpose: String,
    ): UploadedAsset? {
        val client = api ?: return null
        return client.uploadImageAsset(
            bytes = bytes,
            filename = filename,
            purpose = purpose,
        )
    }

    suspend fun createStickerPack(
        name: String,
        sortOrder: Int? = null,
    ): StickerPack? {
        val client = api ?: return null
        return client.createStickerPack(
            name = name,
            sortOrder = sortOrder,
            ownerUserId = managedUserId,
        )
    }

    suspend fun addSticker(
        packId: String,
        assetId: String,
        name: String,
        sortOrder: Int? = null,
    ) {
        val client = api ?: return
        return client.addSticker(
            packId = packId,
            assetId = assetId,
            name = name,
            sortOrder = sortOrder,
            ownerUserId = managedUserId,
        )
    }

    suspend fun deleteSticker(
        packId: String,
        stickerId: String,
    ) {
        val client = api ?: return
        return client.deleteSticker(
            packId = packId,
            stickerId = stickerId,
            ownerUserId = managedUserId,
        )
    }

    suspend fun updateSticker(
        packId: String,
        stickerId: String,
        name: String? = null,
        sortOrder: Int? = null,
    ): Sticker? {
        val client = api ?: return null
        return client.updateSticker(
            packId = packId,
            stickerId = stickerId,
            name = name,
            sortOrder = sortOrder,
        )
    }

    suspend fun reorderStickers(
        packId: String,
        stickerIds: List<String>,
    ) {
        val client = api ?: return
        client.reorderStickers(packId = packId, stickerIds = stickerIds)
    }

    suspend fun downloadStickers(stickerIds: List<String>): DownloadedFile? {
        val client = api ?: return null
        return client.downloadStickers(stickerIds = stickerIds)
    }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ) {
        val client = api ?: return
        val targetUserId = managedUserId
        if (targetUserId != null) {
            return client.forceResetUserPassword(
                userId = targetUserId,
                newPassword = newPassword,
            )
        }
        return client.changePassword(
            currentPassword = currentPassword,
            newPassword = newPassword,
        )
    }

    suspend fun deleteMyAccount() {
        val client = api ?: return
        val targetUserId = managedUserId
        if (targetUserId != null) {
            return client.forceDeleteUserAccount(targetUserId)
        }
        return client.deleteMyAccount(confirm = true)
    }

    suspend fun setManagedAccountSuspended(suspended: Boolean): CurrentUser? {
        val client = api
        val targetUserId = managedUserId
        if (client == null || targetUserId == null) return null
        return client.updateForcedUserSettings(
            userId = targetUserId,
            status = if (suspended) "suspended" else "active",
        )
    }
}

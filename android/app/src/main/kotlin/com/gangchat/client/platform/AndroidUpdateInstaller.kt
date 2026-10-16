package com.gangchat.client.platform

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred

/**
 * Hands a downloaded release APK to the system installer (formerly the
 * `gang_chat/app_update` channel's `installApk`).
 *
 * The APK must sit in the private `cache/release-updates/` directory, be
 * named `GangChat_v<x.y.z>.apk`, belong to this package and be signed by the
 * installed signer. When the app is in the background the install is
 * deferred: the path is remembered in the `gang_chat_updates` preferences and
 * an "update ready" notification resumes it on the next foreground resume.
 */
class AndroidUpdateInstaller(context: Context) {
    private class ApkError(
        val code: String,
        val message: String,
    )

    private class PendingInstall(
        val apk: File,
        val result: CompletableDeferred<Unit>,
    )

    private val context: Context = context.applicationContext ?: context
    private var pendingInstall: PendingInstall? = null
    private var waitingForInstallPermission = false
    private var waitingForDeferredInstallPermission = false
    private var skipNextDeferredInstallResume = false

    private val preferences by lazy {
        this.context.getSharedPreferences(updatePreferencesName, Context.MODE_PRIVATE)
    }

    /**
     * Installs the APK at [path]. Returns once the system installer was
     * launched or the install was deferred; failures are
     * [PlatformException]s with the Flutter host's codes (`invalid_apk`,
     * `invalid_package`, `signature_mismatch`, `permission_denied`,
     * `installer_unavailable`, `install_in_progress`).
     */
    suspend fun installApk(path: String?) {
        if (pendingInstall != null) {
            throw PlatformException("install_in_progress", "An Android update install is already pending.")
        }
        if (path.isNullOrBlank()) {
            throw PlatformException("invalid_apk", "The APK path is missing.")
        }
        val apk = validateApk(path)

        if (!GangChatAppVisibility.isForeground) {
            deferInstaller(apk)
            return
        }

        if (needsInstallPermission()) {
            val activity = GangChatPlatform.requireActivity()
            val pending = PendingInstall(apk, CompletableDeferred())
            pendingInstall = pending
            waitingForInstallPermission = true
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                    installPermissionRequestCode,
                )
            } catch (_: ActivityNotFoundException) {
                clearPendingInstall()
                throw PlatformException(
                    "installer_unavailable",
                    "The system install permission page is unavailable.",
                )
            } catch (error: Exception) {
                clearPendingInstall()
                throw PlatformException("installer_unavailable", error.message)
            }
            pending.result.await()
            return
        }

        launchInstaller(apk)
    }

    fun onActivityResult(requestCode: Int): Boolean {
        if (requestCode != installPermissionRequestCode) return false
        if (pendingInstall != null) {
            finishInstallPermissionRequest()
        } else if (waitingForDeferredInstallPermission) {
            waitingForDeferredInstallPermission = false
            skipNextDeferredInstallResume = true
            if (!needsInstallPermission()) resumeDeferredInstall()
        }
        return true
    }

    fun onResume() {
        if (waitingForInstallPermission && pendingInstall != null) {
            finishInstallPermissionRequest()
            return
        }
        if (waitingForDeferredInstallPermission) {
            waitingForDeferredInstallPermission = false
            if (!needsInstallPermission()) resumeDeferredInstall()
            return
        }
        if (skipNextDeferredInstallResume) {
            skipNextDeferredInstallResume = false
            return
        }
        resumeDeferredInstall()
    }

    private fun finishInstallPermissionRequest() {
        val pending = clearPendingInstall() ?: return
        if (needsInstallPermission()) {
            pending.result.completeExceptionally(
                PlatformException("permission_denied", "Permission to install unknown apps was not granted."),
            )
            return
        }
        try {
            launchInstaller(pending.apk)
            pending.result.complete(Unit)
        } catch (error: PlatformException) {
            pending.result.completeExceptionally(error)
        }
    }

    private fun clearPendingInstall(): PendingInstall? {
        waitingForInstallPermission = false
        val pending = pendingInstall
        pendingInstall = null
        return pending
    }

    private fun needsInstallPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
    }

    private fun validateApk(path: String): File {
        val apk =
            canonicalUpdateApk(path)
                ?: throw PlatformException(
                    "invalid_apk",
                    "The APK is outside the private update directory or unreadable.",
                )
        val error = apkTrustError(apk)
        if (error != null) throw PlatformException(error.code, error.message)
        return apk
    }

    private fun canonicalUpdateApk(path: String): File? {
        val apk =
            try {
                File(path).canonicalFile
            } catch (_: Exception) {
                return null
            }
        val updateRoot =
            try {
                File(context.cacheDir, updateDirectoryName).canonicalFile
            } catch (_: Exception) {
                return null
            }
        val allowedPrefix = updateRoot.path + File.separator
        if (!apk.path.startsWith(allowedPrefix) ||
            !apk.name.matches(apkFilenamePattern) ||
            !apk.isFile ||
            !apk.canRead() ||
            apk.length() <= 0
        ) {
            return null
        }
        return apk
    }

    private fun apkTrustError(apk: File): ApkError? {
        val archiveInfo =
            packageArchiveInfo(apk)
                ?: return ApkError("invalid_apk", "Android could not parse the APK.")
        if (archiveInfo.packageName != context.packageName) {
            return ApkError("invalid_package", "The APK belongs to a different application.")
        }

        val installedInfo = installedPackageInfo()
        val installedSigners = installedInfo?.let(::signerDigests).orEmpty()
        val archiveSigners = signerDigests(archiveInfo)
        if (installedSigners.isEmpty() ||
            archiveSigners.isEmpty() ||
            installedSigners.intersect(archiveSigners).isEmpty()
        ) {
            return ApkError(
                "signature_mismatch",
                "The APK signing certificate does not match this installation.",
            )
        }
        return null
    }

    private fun launchInstaller(apk: File) {
        if (!GangChatAppVisibility.isForeground) {
            deferInstaller(apk)
            return
        }
        try {
            val intent = installerIntent(apk)
            GangChatNotifications.cancelUpdateReady(context)
            startActivity(intent)
            clearDeferredInstall()
        } catch (_: ActivityNotFoundException) {
            throw PlatformException("installer_unavailable", "No Android package installer is available.")
        } catch (error: PlatformException) {
            throw error
        } catch (error: Exception) {
            throw PlatformException("invalid_apk", error.message)
        }
    }

    private fun deferInstaller(apk: File) {
        rememberDeferredInstall(apk)
        GangChatNotifications.showUpdateReady(context, versionFromFilename(apk.name))
    }

    private fun resumeDeferredInstall() {
        if (!GangChatAppVisibility.isForeground || pendingInstall != null) return
        val apk = deferredInstallApk() ?: return
        if (needsInstallPermission()) {
            val activity = GangChatPlatform.activity ?: return
            waitingForDeferredInstallPermission = true
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                    installPermissionRequestCode,
                )
            } catch (_: Exception) {
                waitingForDeferredInstallPermission = false
            }
            return
        }

        try {
            val intent = installerIntent(apk)
            GangChatNotifications.cancelUpdateReady(context)
            startActivity(intent)
            clearDeferredInstall()
        } catch (_: Exception) {
            // Keep the validated APK pending so a later foreground resume can retry.
        }
    }

    private fun startActivity(intent: Intent) {
        val activity = GangChatPlatform.activity
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun rememberDeferredInstall(apk: File) {
        preferences.edit().putString(pendingApkPathKey, apk.path).apply()
    }

    private fun deferredInstallApk(): File? {
        val path = preferences.getString(pendingApkPathKey, null)?.trim().orEmpty()
        if (path.isEmpty()) return null
        val apk = canonicalUpdateApk(path)
        if (apk == null || apkTrustError(apk) != null) {
            clearDeferredInstall()
            return null
        }
        return apk
    }

    private fun clearDeferredInstall() {
        preferences.edit().remove(pendingApkPathKey).apply()
    }

    private fun installerIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent =
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, apkMimeType)
                clipData = ClipData.newRawUri("Gang Chat update", uri)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        // ColorOS and some other OEM installers hand the request from a
        // visible proxy activity to another component in the installer
        // package. Granting that package explicitly keeps the private APK
        // readable throughout its asynchronous prepare/verify phase.
        val installer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            } ?: throw ActivityNotFoundException()
        val installerPackage = installer.activityInfo.packageName
        context.grantUriPermission(installerPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent.setPackage(installerPackage)
    }

    private fun versionFromFilename(filename: String): String =
        apkFilenamePattern.matchEntire(filename)?.groupValues?.getOrNull(1).orEmpty()

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(apk: File): PackageInfo? {
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        return context.packageManager.getPackageArchiveInfo(apk.path, flags)
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo? {
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        return try {
            context.packageManager.getPackageInfo(context.packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo ?: return emptySet()
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                info.signatures
            }
        return signatures
            ?.map { signature ->
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }
            ?.toSet()
            .orEmpty()
    }

    companion object {
        const val installPermissionRequestCode = 4107
        const val updateDirectoryName = "release-updates"
        private const val updatePreferencesName = "gang_chat_updates"
        private const val pendingApkPathKey = "pending_apk_path"
        private const val apkMimeType = "application/vnd.android.package-archive"
        private val apkFilenamePattern =
            Regex("^GangChat_v(\\d+\\.\\d+\\.\\d+)\\.apk$", RegexOption.IGNORE_CASE)

        @Volatile
        private var installed: AndroidUpdateInstaller? = null

        /** The process-wide installer; [MissingPluginException] before the app installs it. */
        val instance: AndroidUpdateInstaller
            get() = installed
                ?: throw MissingPluginException("No implementation found for gang_chat/app_update")

        fun install(installer: AndroidUpdateInstaller) {
            installed = installer
        }
    }
}

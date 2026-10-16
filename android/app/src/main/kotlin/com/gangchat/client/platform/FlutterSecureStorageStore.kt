package com.gangchat.client.platform

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.gangchat.client.plugins.SecureStorageStore
import java.math.BigInteger
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.util.Calendar
import java.util.Locale
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * [SecureStorageStore] that reads and writes the storage of the Flutter
 * build's `flutter_secure_storage` 10.3.1 with the options the app used
 * (the Dart `AndroidOptions()` defaults: RSA-OAEP key wrapping, AES-GCM
 * values, `resetOnError`, `migrateOnAlgorithmChange`), so the refresh token
 * and remembered passwords survive an in-place upgrade:
 *
 * - values: `FlutterSecureStorage` preferences, key
 *   `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg_" + key`, value
 *   `Base64(iv[12] ‖ AES-GCM(ciphertext ‖ tag[128]))`;
 * - the AES key: `FlutterSecureKeyStorage` preferences, wrapped with the
 *   Android Keystore RSA key `<package>.FlutterSecureStoragePluginKeyOAEP`
 *   (OAEP, SHA-256 / MGF1-SHA-1);
 * - algorithm markers: `FlutterSecureStorageConfiguration:FlutterSecureStorage`
 *   (falling back to the legacy `FlutterSecureStorageConfiguration`).
 *
 * Data written by older plugin versions (RSA PKCS#1 v1.5 + AES-CBC, or no
 * markers at all) is decrypted and re-encrypted with the current algorithms
 * on first use, exactly like the plugin's non-biometric migration. If that
 * fails, everything is reset (the plugin's `resetOnError`), so the user has to
 * log in once. Not ported: the Jetpack `EncryptedSharedPreferences` import
 * (the app never enabled `encryptedSharedPreferences`) and biometric ciphers
 * (never configured).
 *
 * All work runs on one background thread at a time, like the plugin's
 * worker thread.
 */
class FlutterSecureStorageStore(
    context: Context,
    private val keyCipherFactory: (KeyCipherAlgorithm) -> KeyCipher =
        { algorithm -> AndroidKeyStoreRsaKeyCipher(context.applicationContext ?: context, algorithm) },
) : SecureStorageStore {
    /** The plugin's `KeyCipherAlgorithm` (names are stored as markers). */
    enum class KeyCipherAlgorithm { RSA_ECB_PKCS1Padding, RSA_ECB_OAEPwithSHA_256andMGF1Padding, AES_GCM_NoPadding }

    /** The plugin's `StorageCipherAlgorithm`. */
    enum class StorageCipherAlgorithm { AES_CBC_PKCS7Padding, AES_GCM_NoPadding }

    /** Wraps the AES storage key (the plugin's `KeyCipher`). */
    interface KeyCipher {
        fun wrap(key: Key): ByteArray

        fun unwrap(wrappedKey: ByteArray, algorithm: String): Key

        fun deleteKey()
    }

    private interface StorageCipher {
        fun encrypt(input: ByteArray): ByteArray

        fun decrypt(input: ByteArray): ByteArray

        fun deleteKey()
    }

    private val appContext: Context = context.applicationContext ?: context
    private val dataPreferences = appContext.getSharedPreferences(dataPreferencesName, Context.MODE_PRIVATE)
    private val keyPreferences = appContext.getSharedPreferences(keyStoragePreferencesName, Context.MODE_PRIVATE)
    private val namespacedConfig = appContext.getSharedPreferences(namespacedConfigName, Context.MODE_PRIVATE)
    private val legacyConfig = appContext.getSharedPreferences(legacyConfigName, Context.MODE_PRIVATE)
    private var storageCipher: StorageCipher? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)

    override suspend fun read(key: String): String? =
        withContext(worker) {
            initialize()
            val prefixedKey = addPrefixToKey(key)
            if (!dataPreferences.contains(prefixedKey)) return@withContext null
            try {
                decodeRawValue(dataPreferences.getString(prefixedKey, null))
            } catch (error: Exception) {
                logStorageError("read", key, error)
                // resetOnError: drop the unreadable value and retry.
                deletePrefixed(prefixedKey)
                try {
                    decodeRawValue(dataPreferences.getString(prefixedKey, null))
                } catch (_: Exception) {
                    resetAfterFailure()
                    null
                }
            }
        }

    override suspend fun write(key: String, value: String) {
        withContext(worker) {
            initialize()
            val prefixedKey = addPrefixToKey(key)
            try {
                writeUnsafe(prefixedKey, value)
            } catch (error: Exception) {
                logStorageError("write", key, error)
                deletePrefixed(prefixedKey)
                try {
                    writeUnsafe(prefixedKey, value)
                } catch (_: Exception) {
                    resetAfterFailure()
                }
            }
        }
    }

    override suspend fun delete(key: String) {
        withContext(worker) {
            initialize()
            deletePrefixed(addPrefixToKey(key))
        }
    }

    override suspend fun readAll(): Map<String, String> =
        withContext(worker) {
            initialize()
            try {
                readAllUnsafe()
            } catch (error: Exception) {
                logStorageError("readAll", null, error)
                deleteAllUnsafe()
                try {
                    readAllUnsafe()
                } catch (_: Exception) {
                    resetAfterFailure()
                    emptyMap()
                }
            }
        }

    override suspend fun deleteAll() {
        withContext(worker) {
            initialize()
            deleteAllUnsafe()
        }
    }

    // -- Plugin FlutterSecureStorage -------------------------------------------

    private fun addPrefixToKey(key: String): String = "${keyPrefix}_$key"

    private fun deletePrefixed(prefixedKey: String) {
        dataPreferences.edit().remove(prefixedKey).apply()
    }

    private fun deleteAllUnsafe() {
        dataPreferences.edit().clear().apply()
    }

    /** The plugin's outer handler with `resetOnError`: wipe the stored values. */
    private fun resetAfterFailure() {
        try {
            deleteAllUnsafe()
        } catch (_: Exception) {
        }
    }

    private fun writeUnsafe(prefixedKey: String, value: String) {
        val result = requireCipher().encrypt(value.toByteArray(Charsets.UTF_8))
        dataPreferences.edit().putString(prefixedKey, Base64.encodeToString(result, 0)).apply()
    }

    private fun readAllUnsafe(): Map<String, String> {
        val all = LinkedHashMap<String, String>()
        for ((keyWithPrefix, rawValue) in dataPreferences.all) {
            if (!keyWithPrefix.contains(keyPrefix)) continue
            val key = keyWithPrefix.replaceFirst("${keyPrefix}_", "")
            all[key] = decodeRawValue(rawValue as String?) ?: continue
        }
        return all
    }

    private fun decodeRawValue(value: String?): String? {
        if (value == null) return null
        val data = Base64.decode(value, 0)
        return String(requireCipher().decrypt(data), Charsets.UTF_8)
    }

    private fun requireCipher(): StorageCipher =
        storageCipher ?: throw IllegalStateException("FlutterSecureStorage is not initialized")

    private fun initialize() {
        if (storageCipher != null) return
        initializeStorageCipher(attempt = 0)
    }

    private fun initializeStorageCipher(attempt: Int) {
        val factory = CipherFactory()
        try {
            if (factory.requiresReEncryption()) {
                Log.w(tag, "Algorithm changed detected.")
                handleKeyMismatch(factory, attempt)
                return
            }
            storageCipher = factory.currentStorageCipher()
        } catch (error: BadPaddingException) {
            handleKeyMismatch(factory, attempt)
        } catch (error: InvalidKeyException) {
            handleKeyMismatch(factory, attempt)
        } catch (error: IllegalBlockSizeException) {
            handleKeyMismatch(factory, attempt)
        } catch (error: NoSuchAlgorithmException) {
            throw Exception("Required cryptographic algorithm not supported by device.", error)
        }
    }

    /** `migrateOnAlgorithmChange`, falling back to `resetOnError`. */
    private fun handleKeyMismatch(factory: CipherFactory, attempt: Int) {
        try {
            migrateNonBiometric(factory)
            setEncryptedPrefsMigrated()
        } catch (migrationError: Exception) {
            Log.e(tag, "Data migration failed: ${migrationError.message}", migrationError)
            if (attempt > 0) throw migrationError
            deleteAllDataAndKeys(factory)
            setEncryptedPrefsMigrated()
        }
    }

    private fun migrateNonBiometric(factory: CipherFactory) {
        val hasData = dataPreferences.all.any { (key, value) -> value is String && key.contains(keyPrefix) }
        val decrypted = LinkedHashMap<String, String>()
        if (hasData) {
            // Step 1-2: decrypt everything with the saved (old) cipher.
            val savedCipher = factory.savedStorageCipher()
            for ((key, value) in dataPreferences.all) {
                if (value is String && key.contains(keyPrefix)) {
                    decrypted[key] = String(savedCipher.decrypt(Base64.decode(value, 0)), Charsets.UTF_8)
                }
            }
            // Step 3: delete the old RSA key before creating a key for the new algorithm.
            if (factory.changedKeyAlgorithm()) {
                try {
                    factory.savedKeyCipher().deleteKey()
                    savedCipher.deleteKey()
                } catch (error: Exception) {
                    Log.w(tag, "Failed to delete old key from KeyStore (may not exist)", error)
                }
            }
        }
        // Step 4: update the algorithm markers.
        namespacedConfig.edit().also(factory::storeCurrentAlgorithms).commit()
        // Step 5: current cipher (creates a fresh AES key when needed).
        val currentCipher = factory.currentStorageCipher()
        // Step 6: re-encrypt.
        if (decrypted.isNotEmpty()) {
            val editor = dataPreferences.edit()
            for ((key, value) in decrypted) {
                editor.putString(key, Base64.encodeToString(currentCipher.encrypt(value.toByteArray(Charsets.UTF_8)), 0))
            }
            if (!editor.commit()) {
                throw Exception("Failed to commit encrypted data to disk - storage may be full or unavailable")
            }
        }
        storageCipher = currentCipher
    }

    private fun deleteAllDataAndKeys(factory: CipherFactory) {
        try {
            factory.currentKeyCipher().deleteKey()
        } catch (error: Exception) {
            Log.w(tag, "Failed to delete key from AndroidKeyStore (may not exist)", error)
        }
        dataPreferences.edit().clear().apply()
        keyPreferences.edit().clear().apply()
        namespacedConfig.edit().also(factory::storeCurrentAlgorithms).apply()
        initializeStorageCipher(attempt = 1)
    }

    private fun setEncryptedPrefsMigrated() {
        namespacedConfig.edit().putBoolean(encryptedPreferencesMigratedKey, true).commit()
    }

    private fun configString(key: String): String? =
        namespacedConfig.getString(key, null) ?: legacyConfig.getString(key, null)

    private fun logStorageError(operation: String, key: String?, error: Exception) {
        val target = if (key != null) "key '$key'" else "all data"
        Log.e(tag, "Storage operation '$operation' failed for $target. Attempting to delete corrupted data and retry...", error)
    }

    /** The plugin's `StorageCipherFactory` for the default (non-biometric) options. */
    private inner class CipherFactory {
        private val savedKeyAlgorithm: KeyCipherAlgorithm
        private val savedStorageAlgorithm: StorageCipherAlgorithm
        private val currentKeyAlgorithm = KeyCipherAlgorithm.RSA_ECB_OAEPwithSHA_256andMGF1Padding
        private val currentStorageAlgorithm = StorageCipherAlgorithm.AES_GCM_NoPadding

        init {
            val savedKey = configString(algorithmKeyMarker)
            val savedStorage = configString(algorithmStorageMarker)
            if (savedKey == null || savedStorage == null) {
                // No markers: the data was written with the historical
                // v9.2.4 defaults. Decrypt with those, then re-encrypt.
                savedKeyAlgorithm = KeyCipherAlgorithm.RSA_ECB_PKCS1Padding
                savedStorageAlgorithm = StorageCipherAlgorithm.AES_CBC_PKCS7Padding
                namespacedConfig.edit().also(::storeCurrentAlgorithms).apply()
            } else {
                savedKeyAlgorithm = keyAlgorithmFromString(savedKey)
                savedStorageAlgorithm = storageAlgorithmFromString(savedStorage)
            }
        }

        fun requiresReEncryption(): Boolean =
            savedKeyAlgorithm != currentKeyAlgorithm || savedStorageAlgorithm != currentStorageAlgorithm

        fun changedKeyAlgorithm(): Boolean = savedKeyAlgorithm != currentKeyAlgorithm

        fun savedKeyCipher(): KeyCipher = keyCipherFor(savedKeyAlgorithm)

        fun currentKeyCipher(): KeyCipher = keyCipherFactory(currentKeyAlgorithm)

        fun savedStorageCipher(): StorageCipher = storageCipherFor(savedKeyCipher(), savedStorageAlgorithm)

        fun currentStorageCipher(): StorageCipher = storageCipherFor(currentKeyCipher(), currentStorageAlgorithm)

        fun storeCurrentAlgorithms(editor: SharedPreferences.Editor) {
            editor.putString(algorithmKeyMarker, currentKeyAlgorithm.name)
            editor.putString(algorithmStorageMarker, currentStorageAlgorithm.name)
        }

        private fun keyCipherFor(algorithm: KeyCipherAlgorithm): KeyCipher {
            if (algorithm == KeyCipherAlgorithm.AES_GCM_NoPadding) {
                // Biometric Keystore keys need a user prompt; the app never used them.
                throw InvalidKeyException("Biometric key cipher is not supported")
            }
            return keyCipherFactory(algorithm)
        }

        private fun storageCipherFor(keyCipher: KeyCipher, algorithm: StorageCipherAlgorithm): StorageCipher =
            when (algorithm) {
                StorageCipherAlgorithm.AES_GCM_NoPadding -> WrappedAesStorageCipher(keyCipher, gcm = true)
                StorageCipherAlgorithm.AES_CBC_PKCS7Padding -> WrappedAesStorageCipher(keyCipher, gcm = false)
            }
    }

    /**
     * The plugin's `StorageCipherImplementationGCM` (12-byte IV, 128-bit tag)
     * and `StorageCipherImplementationAES18` (CBC, 16-byte IV): a random
     * 16-byte AES key, stored wrapped in `FlutterSecureKeyStorage`.
     */
    private inner class WrappedAesStorageCipher(keyCipher: KeyCipher, private val gcm: Boolean) : StorageCipher {
        private val preferencesKey = if (gcm) gcmKeyPreference else cbcKeyPreference
        private val secureRandom = SecureRandom()
        private val secretKey: Key
        private val ivSize = if (gcm) 12 else 16

        init {
            val aesKey = keyPreferences.getString(preferencesKey, null)
            if (aesKey != null) {
                // May throw BadPaddingException / InvalidKeyException after an algorithm change.
                secretKey = keyCipher.unwrap(Base64.decode(aesKey, Base64.DEFAULT), "AES")
            } else {
                val key = ByteArray(16)
                secureRandom.nextBytes(key)
                secretKey = SecretKeySpec(key, "AES")
                val encryptedKey = keyCipher.wrap(secretKey)
                keyPreferences.edit().putString(preferencesKey, Base64.encodeToString(encryptedKey, Base64.DEFAULT)).apply()
            }
        }

        private fun cipher(): Cipher =
            // PKCS5Padding is the JCA name of PKCS#7 padding for AES's 16-byte blocks.
            Cipher.getInstance(if (gcm) "AES/GCM/NoPadding" else "AES/CBC/PKCS5Padding")

        private fun parameterSpec(iv: ByteArray): AlgorithmParameterSpec =
            if (gcm) GCMParameterSpec(128, iv) else IvParameterSpec(iv)

        override fun encrypt(input: ByteArray): ByteArray {
            val iv = ByteArray(ivSize)
            secureRandom.nextBytes(iv)
            val cipher = cipher()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec(iv))
            return iv + cipher.doFinal(input)
        }

        override fun decrypt(input: ByteArray): ByteArray {
            val iv = input.copyOfRange(0, ivSize)
            val cipher = cipher()
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec(iv))
            return cipher.doFinal(input, ivSize, input.size - ivSize)
        }

        override fun deleteKey() {
            keyPreferences.edit().remove(preferencesKey).apply()
        }
    }

    companion object {
        private const val tag = "FlutterSecureStorage"
        const val dataPreferencesName = "FlutterSecureStorage"
        const val keyStoragePreferencesName = "FlutterSecureKeyStorage"
        const val namespacedConfigName = "FlutterSecureStorageConfiguration:FlutterSecureStorage"
        const val legacyConfigName = "FlutterSecureStorageConfiguration"
        const val keyPrefix = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg"
        const val algorithmKeyMarker = "FlutterSecureSAlgorithmKey"
        const val algorithmStorageMarker = "FlutterSecureSAlgorithmStorage"
        const val encryptedPreferencesMigratedKey = "ENCRYPTED_PREFERENCES_MIGRATED"
        const val cbcKeyPreference = "VGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK"
        const val gcmKeyPreference = "AES$cbcKeyPreference"

        /** OAEP parameters the plugin used (SHA-256 digest, MGF1 with SHA-1). */
        val oaepParameterSpec: OAEPParameterSpec =
            OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

        private fun keyAlgorithmFromString(name: String): KeyCipherAlgorithm =
            if (name == "AES_GCM_NoPadding_BIOMETRIC") KeyCipherAlgorithm.AES_GCM_NoPadding else KeyCipherAlgorithm.valueOf(name)

        private fun storageAlgorithmFromString(name: String): StorageCipherAlgorithm =
            if (name == "AES_GCM_NoPadding_BIOMETRIC") {
                StorageCipherAlgorithm.AES_GCM_NoPadding
            } else {
                StorageCipherAlgorithm.valueOf(name)
            }
    }
}

/**
 * The plugin's `KeyCipherImplementationRSA18` / `KeyCipherImplementationRSAOAEP`:
 * an Android Keystore RSA key pair under the same aliases, created on demand
 * with the same parameters, used to wrap the AES storage key.
 */
class AndroidKeyStoreRsaKeyCipher(
    private val context: Context,
    private val algorithm: FlutterSecureStorageStore.KeyCipherAlgorithm,
) : FlutterSecureStorageStore.KeyCipher {
    private val oaep = algorithm == FlutterSecureStorageStore.KeyCipherAlgorithm.RSA_ECB_OAEPwithSHA_256andMGF1Padding
    private val keyAlias =
        context.packageName + if (oaep) ".FlutterSecureStoragePluginKeyOAEP" else ".FlutterSecureStoragePluginKey"

    init {
        require(algorithm != FlutterSecureStorageStore.KeyCipherAlgorithm.AES_GCM_NoPadding) {
            "Biometric key cipher is not supported"
        }
        createRsaKeysIfNeeded()
    }

    override fun deleteKey() {
        val keyStore = KeyStore.getInstance(keystoreProvider)
        keyStore.load(null)
        keyStore.deleteEntry(keyAlias)
    }

    override fun wrap(key: Key): ByteArray {
        val keyStore = KeyStore.getInstance(keystoreProvider).apply { load(null) }
        val certificate =
            keyStore.getCertificate(keyAlias) ?: throw Exception("No certificate found under alias: $keyAlias")
        val publicKey = certificate.publicKey ?: throw Exception("No key found under alias: $keyAlias")
        val cipher = rsaCipher()
        cipher.init(Cipher.WRAP_MODE, publicKey, parameterSpec())
        return cipher.wrap(key)
    }

    override fun unwrap(wrappedKey: ByteArray, algorithm: String): Key {
        val keyStore = KeyStore.getInstance(keystoreProvider).apply { load(null) }
        val key = keyStore.getKey(keyAlias, null) ?: throw Exception("No key found under alias: $keyAlias")
        if (key !is PrivateKey) throw Exception("Not an instance of a PrivateKey")
        val cipher = rsaCipher()
        cipher.init(Cipher.UNWRAP_MODE, key, parameterSpec())
        return cipher.unwrap(wrappedKey, algorithm, Cipher.SECRET_KEY)
    }

    private fun rsaCipher(): Cipher =
        if (oaep) {
            Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround")
        } else {
            Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidKeyStoreBCWorkaround")
        }

    private fun parameterSpec(): AlgorithmParameterSpec? = if (oaep) FlutterSecureStorageStore.oaepParameterSpec else null

    private fun createRsaKeysIfNeeded() {
        val keyStore = KeyStore.getInstance(keystoreProvider).apply { load(null) }
        val privateKey = keyStore.getKey(keyAlias, null)
        val certificate = keyStore.getCertificate(keyAlias)
        if (privateKey == null || certificate == null) createKeys()
    }

    private fun createKeys() {
        // The plugin generated keys under an English locale (Keystore
        // certificate dates break under some RTL locales).
        val localeBefore = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 25) }
            val generator = KeyPairGenerator.getInstance("RSA", keystoreProvider)
            val spec =
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
                    .setCertificateSubject(X500Principal("CN=$keyAlias"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                    .setEncryptionPaddings(
                        if (oaep) KeyProperties.ENCRYPTION_PADDING_RSA_OAEP else KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                    )
                    .setCertificateSerialNumber(BigInteger.valueOf(1))
                    .setCertificateNotBefore(start.time)
                    .setCertificateNotAfter(end.time)
                    .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        } finally {
            Locale.setDefault(localeBefore)
        }
    }

    private companion object {
        const val keystoreProvider = "AndroidKeyStore"
    }
}

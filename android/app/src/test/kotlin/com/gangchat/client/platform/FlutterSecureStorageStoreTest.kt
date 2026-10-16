package com.gangchat.client.platform

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.gangchat.client.platform.FlutterSecureStorageStore.KeyCipherAlgorithm
import com.gangchat.client.plugins.FlutterSecureStorage
import com.gangchat.client.testing.GangTestRule
import java.security.Key
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compatibility with flutter_secure_storage 10.3.1 on Android. Robolectric has
 * no Android Keystore, so the Keystore RSA keys are modelled by [FakeKeyStore]
 * (same aliases, same wrapping transformations); everything else, the
 * preference files, markers, AES key storage and value encoding, is the real
 * code path. Fixtures are built the way the plugin wrote them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlutterSecureStorageStoreTest {
    @get:Rule
    val gang = GangTestRule()

    private lateinit var context: Context
    private lateinit var data: android.content.SharedPreferences
    private lateinit var keys: android.content.SharedPreferences
    private lateinit var config: android.content.SharedPreferences
    private lateinit var legacyConfig: android.content.SharedPreferences
    private val keyStore = FakeKeyStore()
    private val random = SecureRandom()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        data = context.getSharedPreferences("FlutterSecureStorage", Context.MODE_PRIVATE)
        keys = context.getSharedPreferences("FlutterSecureKeyStorage", Context.MODE_PRIVATE)
        config = context.getSharedPreferences("FlutterSecureStorageConfiguration:FlutterSecureStorage", Context.MODE_PRIVATE)
        legacyConfig = context.getSharedPreferences("FlutterSecureStorageConfiguration", Context.MODE_PRIVATE)
        for (prefs in listOf(data, keys, config, legacyConfig)) prefs.edit().clear().commit()
    }

    private fun store() = FlutterSecureStorageStore(context, keyStore::cipher)

    @Test
    fun `round trips values in the plugin's AES-GCM format`() =
        gang.runTest {
            FlutterSecureStorage.installStore(store())
            val storage = FlutterSecureStorage()

            storage.write("gang.refreshToken", "refresh-令牌")
            storage.write("gang.loginAccountPassword.alice", "p@ss")

            assertEquals("refresh-令牌", storage.read("gang.refreshToken"))
            assertEquals(
                mapOf("gang.refreshToken" to "refresh-令牌", "gang.loginAccountPassword.alice" to "p@ss"),
                storage.readAll(),
            )
            assertTrue(storage.containsKey("gang.refreshToken"))

            // On disk: prefixed key, Base64(iv[12] + GCM ciphertext) under the
            // AES key wrapped with the OAEP Keystore key, and the markers.
            val stored = data.getString(PREFIX + "_gang.refreshToken", null)!!
            val aesKey = keyStore.unwrap(KeyCipherAlgorithm.RSA_ECB_OAEPwithSHA_256andMGF1Padding, keys.getString(GCM_KEY, null)!!)
            assertEquals("refresh-令牌", decryptGcm(aesKey, stored))
            assertEquals("RSA_ECB_OAEPwithSHA_256andMGF1Padding", config.getString("FlutterSecureSAlgorithmKey", null))
            assertEquals("AES_GCM_NoPadding", config.getString("FlutterSecureSAlgorithmStorage", null))

            storage.delete("gang.loginAccountPassword.alice")
            assertNull(storage.read("gang.loginAccountPassword.alice"))
            storage.deleteAll()
            assertEquals(emptyMap<String, String>(), storage.readAll())
        }

    @Test
    fun `reads values written by flutter_secure_storage 10 with OAEP and GCM`() =
        gang.runTest {
            val aesKey = newAesKey()
            val wrappedKey = keyStore.wrap(KeyCipherAlgorithm.RSA_ECB_OAEPwithSHA_256andMGF1Padding, aesKey)
            keys.edit().putString(GCM_KEY, wrappedKey).commit()
            config.edit()
                .putString("FlutterSecureSAlgorithmKey", "RSA_ECB_OAEPwithSHA_256andMGF1Padding")
                .putString("FlutterSecureSAlgorithmStorage", "AES_GCM_NoPadding")
                .putBoolean("ENCRYPTED_PREFERENCES_MIGRATED", true)
                .commit()
            data.edit().putString(PREFIX + "_gang.refreshToken", encryptGcm(aesKey, "flutter-refresh-token")).commit()

            val store = store()

            assertEquals("flutter-refresh-token", store.read("gang.refreshToken"))
            assertEquals(mapOf("gang.refreshToken" to "flutter-refresh-token"), store.readAll())
            assertNull(store.read("missing"))
            // Nothing was migrated or re-keyed.
            assertEquals(wrappedKey, keys.getString(GCM_KEY, null))
            assertEquals(0, keyStore.deletedAliases.size)
            assertNotNull(data.getString(PREFIX + "_gang.refreshToken", null))
        }

    @Test
    fun `migrates values written with the historical PKCS1 and CBC defaults`() =
        gang.runTest {
            // flutter_secure_storage 9.x / early 10.0: no algorithm markers, AES
            // key wrapped with RSA PKCS#1 v1.5, values in AES-CBC.
            val oldKey = newAesKey()
            keys.edit().putString(CBC_KEY, keyStore.wrap(KeyCipherAlgorithm.RSA_ECB_PKCS1Padding, oldKey)).commit()
            data.edit()
                .putString(PREFIX + "_gang.refreshToken", encryptCbc(oldKey, "old-token"))
                .putString(PREFIX + "_gang.loginAccountPassword.bob", encryptCbc(oldKey, "密码"))
                .commit()

            val store = store()

            assertEquals("old-token", store.read("gang.refreshToken"))
            assertEquals("密码", store.read("gang.loginAccountPassword.bob"))

            // Re-encrypted with the current algorithms, old key material removed.
            assertEquals("RSA_ECB_OAEPwithSHA_256andMGF1Padding", config.getString("FlutterSecureSAlgorithmKey", null))
            assertEquals("AES_GCM_NoPadding", config.getString("FlutterSecureSAlgorithmStorage", null))
            assertTrue(config.getBoolean("ENCRYPTED_PREFERENCES_MIGRATED", false))
            assertFalse(keys.contains(CBC_KEY))
            assertTrue(keyStore.deletedAliases.contains(KeyCipherAlgorithm.RSA_ECB_PKCS1Padding))
            val newKey = keyStore.unwrap(KeyCipherAlgorithm.RSA_ECB_OAEPwithSHA_256andMGF1Padding, keys.getString(GCM_KEY, null)!!)
            assertEquals("old-token", decryptGcm(newKey, data.getString(PREFIX + "_gang.refreshToken", null)!!))

            // A second process start reads the migrated data directly.
            assertEquals("密码", store().read("gang.loginAccountPassword.bob"))
        }

    @Test
    fun `migrates markers found only in the legacy global configuration`() =
        gang.runTest {
            val oldKey = newAesKey()
            keys.edit().putString(CBC_KEY, keyStore.wrap(KeyCipherAlgorithm.RSA_ECB_PKCS1Padding, oldKey)).commit()
            legacyConfig.edit()
                .putString("FlutterSecureSAlgorithmKey", "RSA_ECB_PKCS1Padding")
                .putString("FlutterSecureSAlgorithmStorage", "AES_CBC_PKCS7Padding")
                .commit()
            data.edit().putString(PREFIX + "_gang.refreshToken", encryptCbc(oldKey, "legacy-config-token")).commit()

            assertEquals("legacy-config-token", store().read("gang.refreshToken"))
            assertEquals("AES_GCM_NoPadding", config.getString("FlutterSecureSAlgorithmStorage", null))
        }

    @Test
    fun `an unreadable value is dropped like resetOnError`() =
        gang.runTest {
            val store = store()
            store.write("good", "kept")
            data.edit().putString(PREFIX + "_bad", Base64.encodeToString(ByteArray(40) { 7 }, 0)).commit()

            assertNull(store.read("bad"))
            assertFalse(data.contains(PREFIX + "_bad"))
            assertEquals("kept", store.read("good"))
        }

    @Test
    fun `a lost Keystore key resets the storage so the user logs in again`() =
        gang.runTest {
            store().write("gang.refreshToken", "token")
            // e.g. app data restored from a backup onto a new device: the
            // preferences come back, the Keystore key does not.
            keyStore.forget(KeyCipherAlgorithm.RSA_ECB_OAEPwithSHA_256andMGF1Padding)

            val store = store()

            assertNull(store.read("gang.refreshToken"))
            assertEquals(emptyMap<String, String>(), store.readAll())
            store.write("gang.refreshToken", "fresh")
            assertEquals("fresh", store.read("gang.refreshToken"))
        }

    // -- Fixtures -----------------------------------------------------------------

    private fun newAesKey(): SecretKeySpec = SecretKeySpec(ByteArray(16).also(random::nextBytes), "AES")

    private fun encryptGcm(key: Key, value: String): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + cipher.doFinal(value.toByteArray()), 0)
    }

    private fun decryptGcm(key: Key, stored: String): String {
        val bytes = Base64.decode(stored, 0)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }

    private fun encryptCbc(key: Key, value: String): String {
        val iv = ByteArray(16).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        return Base64.encodeToString(iv + cipher.doFinal(value.toByteArray()), 0)
    }

    /**
     * Stands in for the Android Keystore: one RSA key pair per alias
     * (PKCS#1 key / OAEP key), created on demand like the plugin's
     * `createRSAKeysIfNeeded`, wrapping with the plugin's transformations.
     */
    private class FakeKeyStore {
        private val pairs = mutableMapOf<KeyCipherAlgorithm, KeyPair>()
        val deletedAliases = mutableListOf<KeyCipherAlgorithm>()

        fun cipher(alias: KeyCipherAlgorithm): FlutterSecureStorageStore.KeyCipher {
            pairs.getOrPut(alias) { generate() }
            return object : FlutterSecureStorageStore.KeyCipher {
                override fun wrap(key: Key): ByteArray = wrapRaw(alias, key)

                override fun unwrap(wrappedKey: ByteArray, algorithm: String): Key = unwrapRaw(alias, wrappedKey)

                override fun deleteKey() {
                    pairs.remove(alias)
                    deletedAliases.add(alias)
                }
            }
        }

        fun forget(alias: KeyCipherAlgorithm) {
            pairs.remove(alias)
        }

        fun wrap(alias: KeyCipherAlgorithm, key: Key): String {
            pairs.getOrPut(alias) { generate() }
            return Base64.encodeToString(wrapRaw(alias, key), Base64.DEFAULT)
        }

        fun unwrap(alias: KeyCipherAlgorithm, wrapped: String): Key = unwrapRaw(alias, Base64.decode(wrapped, Base64.DEFAULT))

        private fun wrapRaw(algorithm: KeyCipherAlgorithm, key: Key): ByteArray {
            val cipher = rsaCipher(algorithm)
            cipher.init(Cipher.WRAP_MODE, pairs.getValue(algorithm).public, spec(algorithm))
            return cipher.wrap(key)
        }

        private fun unwrapRaw(algorithm: KeyCipherAlgorithm, wrapped: ByteArray): Key {
            val pair = pairs[algorithm] ?: throw java.security.InvalidKeyException("No key found under alias")
            val cipher = rsaCipher(algorithm)
            cipher.init(Cipher.UNWRAP_MODE, pair.private, spec(algorithm))
            return cipher.unwrap(wrapped, "AES", Cipher.SECRET_KEY)
        }

        private fun rsaCipher(algorithm: KeyCipherAlgorithm): Cipher =
            if (algorithm == KeyCipherAlgorithm.RSA_ECB_OAEPwithSHA_256andMGF1Padding) {
                Cipher.getInstance("RSA/ECB/OAEPPadding")
            } else {
                Cipher.getInstance("RSA/ECB/PKCS1Padding")
            }

        private fun spec(algorithm: KeyCipherAlgorithm) =
            if (algorithm == KeyCipherAlgorithm.RSA_ECB_OAEPwithSHA_256andMGF1Padding) {
                FlutterSecureStorageStore.oaepParameterSpec
            } else {
                null
            }

        private fun generate(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    private companion object {
        const val PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg"
        const val CBC_KEY = "VGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK"
        const val GCM_KEY = "AESVGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK"
    }
}

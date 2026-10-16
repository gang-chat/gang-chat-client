package com.gangchat.client.plugins

/**
 * Kotlin facade for Flutter's `flutter_secure_storage` (`FlutterSecureStorage`).
 * The Android app installs a Keystore-backed store; tests use the in-memory
 * default ([setMockInitialValues]).
 */
class FlutterSecureStorage {
    suspend fun read(key: String): String? = store.read(key)

    suspend fun write(key: String, value: String?) {
        if (value == null) store.delete(key) else store.write(key, value)
    }

    suspend fun delete(key: String) = store.delete(key)

    suspend fun containsKey(key: String): Boolean = store.readAll().containsKey(key)

    suspend fun readAll(): Map<String, String> = store.readAll()

    suspend fun deleteAll() = store.deleteAll()

    companion object {
        @Volatile
        private var store: SecureStorageStore = InMemorySecureStorageStore()

        fun installStore(platformStore: SecureStorageStore) {
            store = platformStore
        }

        /** Dart's `FlutterSecureStorage.setMockInitialValues(values)`. */
        fun setMockInitialValues(values: Map<String, String>) {
            store = InMemorySecureStorageStore(values)
        }
    }
}

/** Platform persistence behind [FlutterSecureStorage]. */
interface SecureStorageStore {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String)

    suspend fun delete(key: String)

    suspend fun readAll(): Map<String, String>

    suspend fun deleteAll()
}

class InMemorySecureStorageStore(initial: Map<String, String> = emptyMap()) : SecureStorageStore {
    private val values = LinkedHashMap(initial)

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String) {
        values[key] = value
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }

    override suspend fun readAll(): Map<String, String> = LinkedHashMap(values)

    override suspend fun deleteAll() {
        values.clear()
    }
}

package com.gangchat.client.plugins

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Kotlin facade for Flutter's `shared_preferences` (legacy `SharedPreferences`
 * API): load once with [getInstance], read synchronously from the cache, write
 * through asynchronously.
 *
 * Values are `String`, `Boolean`, `Long` (Dart `int`), `Double` or
 * `List<String>`, as in the plugin. [getInt] narrows to `Int` for the many
 * small values the app stores; use [getLong] for 64-bit values.
 */
class SharedPreferences private constructor(
    private val store: SharedPreferencesStore,
    private val cache: MutableMap<String, Any>,
) {
    fun getKeys(): Set<String> = cache.keys.toSet()

    fun get(key: String): Any? = cache[key]

    fun containsKey(key: String): Boolean = cache.containsKey(key)

    fun getString(key: String): String? = cache[key] as? String

    fun getBool(key: String): Boolean? = cache[key] as? Boolean

    fun getInt(key: String): Int? = (cache[key] as? Long)?.toInt() ?: (cache[key] as? Int)

    fun getLong(key: String): Long? = (cache[key] as? Long) ?: (cache[key] as? Int)?.toLong()

    fun getDouble(key: String): Double? = cache[key] as? Double

    fun getStringList(key: String): List<String>? {
        val value = cache[key] as? List<*> ?: return null
        return value.map { it as String }
    }

    suspend fun setString(key: String, value: String): Boolean = set(key, value)

    suspend fun setBool(key: String, value: Boolean): Boolean = set(key, value)

    suspend fun setInt(key: String, value: Int): Boolean = set(key, value.toLong())

    suspend fun setLong(key: String, value: Long): Boolean = set(key, value)

    suspend fun setDouble(key: String, value: Double): Boolean = set(key, value)

    suspend fun setStringList(key: String, value: List<String>): Boolean = set(key, value.toList())

    suspend fun remove(key: String): Boolean {
        cache.remove(key)
        return store.remove(key)
    }

    suspend fun clear(): Boolean {
        cache.clear()
        return store.clear()
    }

    /** Re-reads the platform store (Dart `reload()`). */
    suspend fun reload() {
        val values = store.getAll()
        cache.clear()
        cache.putAll(values)
    }

    private suspend fun set(key: String, value: Any): Boolean {
        cache[key] = value
        return store.set(key, value)
    }

    companion object {
        @Volatile
        private var store: SharedPreferencesStore = InMemorySharedPreferencesStore()
        private var instance: SharedPreferences? = null
        private val loadLock = Mutex()

        /** Installs the platform store; the Android app does this before UI starts. */
        fun installStore(platformStore: SharedPreferencesStore) {
            store = platformStore
            instance = null
        }

        /** Dart's `SharedPreferences.getInstance()`. */
        suspend fun getInstance(): SharedPreferences =
            loadLock.withLock {
                instance ?: SharedPreferences(store, LinkedHashMap(store.getAll())).also { instance = it }
            }

        /**
         * Dart's `SharedPreferences.setMockInitialValues(values)` for tests:
         * an in-memory store seeded with [values] (ints may be given as Int).
         */
        fun setMockInitialValues(values: Map<String, Any>) {
            val normalized = values.mapValues { (_, v) -> if (v is Int) v.toLong() else v }
            installStore(InMemorySharedPreferencesStore(normalized))
        }
    }
}

/** Platform persistence behind [SharedPreferences]. */
interface SharedPreferencesStore {
    suspend fun getAll(): Map<String, Any>

    suspend fun set(key: String, value: Any): Boolean

    suspend fun remove(key: String): Boolean

    suspend fun clear(): Boolean
}

class InMemorySharedPreferencesStore(initial: Map<String, Any> = emptyMap()) : SharedPreferencesStore {
    private val values = LinkedHashMap(initial)

    override suspend fun getAll(): Map<String, Any> = LinkedHashMap(values)

    override suspend fun set(key: String, value: Any): Boolean {
        values[key] = value
        return true
    }

    override suspend fun remove(key: String): Boolean {
        values.remove(key)
        return true
    }

    override suspend fun clear(): Boolean {
        values.clear()
        return true
    }
}

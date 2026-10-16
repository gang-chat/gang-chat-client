package com.gangchat.client.platform

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.plugins.SharedPreferencesStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SharedPreferencesStore] over the files the Flutter build's
 * `shared_preferences` (legacy `SharedPreferences` API, shared_preferences_android
 * 2.4.26) wrote, so an in-place upgrade keeps every setting:
 *
 * - file `FlutterSharedPreferences`, every key prefixed with `flutter.`;
 * - `bool` → boolean, `int` → long, `String` → string;
 * - `double` → string `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu" + Double.toString(value)`;
 * - `List<String>` → string `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!" + jsonEncode(list)`
 *   (what the plugin writes now), and on read also the older
 *   `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu" + base64(Java-serialized ArrayList)` form
 *   and Android string sets (migrated to the JSON form when read);
 * - legacy BigInteger values (`"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy" + base-36`)
 *   read as `Long`.
 *
 * The keys this store exchanges with [com.gangchat.client.plugins.SharedPreferences]
 * are the app's keys without the `flutter.` prefix, like Dart's `SharedPreferences`.
 */
class FlutterSharedPreferencesStore(context: Context) : SharedPreferencesStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)

    override suspend fun getAll(): Map<String, Any> =
        withContext(Dispatchers.IO) {
            val result = LinkedHashMap<String, Any>()
            for ((key, value) in preferences.all) {
                if (!key.startsWith(keyPrefix) || value == null) continue
                result[key.substring(keyPrefix.length)] = transformPref(key, value)
            }
            result
        }

    override suspend fun set(key: String, value: Any): Boolean =
        withContext(Dispatchers.IO) {
            val prefixedKey = keyPrefix + key
            val editor = preferences.edit()
            when (value) {
                is String -> {
                    if (value.startsWith(listPrefix) ||
                        value.startsWith(bigIntegerPrefix) ||
                        value.startsWith(doublePrefix)
                    ) {
                        throw PlatformException(
                            "RuntimeException",
                            "StorageError: This string cannot be stored as it clashes with special identifier" +
                                " prefixes",
                        )
                    }
                    editor.putString(prefixedKey, value)
                }
                is Boolean -> editor.putBoolean(prefixedKey, value)
                is Long -> editor.putLong(prefixedKey, value)
                is Int -> editor.putLong(prefixedKey, value.toLong())
                is Double -> editor.putString(prefixedKey, doublePrefix + value.toString())
                is List<*> -> editor.putString(prefixedKey, encodeJsonList(value.map { it as String }))
                else -> throw PlatformException("InvalidOperation", "\"${value::class.simpleName}\" is not a supported type.")
            }
            editor.commit()
        }

    override suspend fun remove(key: String): Boolean =
        withContext(Dispatchers.IO) { preferences.edit().remove(keyPrefix + key).commit() }

    /** Dart's `clear()`: removes the `flutter.`-prefixed keys only. */
    override suspend fun clear(): Boolean =
        withContext(Dispatchers.IO) {
            val editor = preferences.edit()
            for (key in preferences.all.keys) {
                if (key.startsWith(keyPrefix)) editor.remove(key)
            }
            editor.commit()
        }

    private fun transformPref(key: String, value: Any): Any {
        when (value) {
            is String -> {
                if (value.startsWith(listPrefix)) {
                    // The JSON-encoded lists use an extended prefix to
                    // distinguish them from lists that were encoded on the platform.
                    return if (value.startsWith(jsonListPrefix)) {
                        decodeJsonList(value.substring(jsonListPrefix.length))
                    } else {
                        decodeLegacyList(value.substring(listPrefix.length))
                    }
                }
                if (value.startsWith(bigIntegerPrefix)) {
                    val encoded = value.substring(bigIntegerPrefix.length)
                    return BigInteger(encoded, Character.MAX_RADIX).toLong()
                }
                if (value.startsWith(doublePrefix)) {
                    return value.substring(doublePrefix.length).toDouble()
                }
                return value
            }
            is Set<*> -> {
                // Written by setStringSet in very old plugin versions; the app
                // expects a list. Migrate the value while we are at it.
                val listValue = value.map { it as String }
                preferences.edit().remove(key).putString(key, encodeJsonList(listValue)).apply()
                return listValue
            }
            is Int -> return value.toLong()
            is Float -> return value.toDouble()
            else -> return value
        }
    }

    /** An ObjectInputStream that only allows string lists (the plugin's `StringListObjectInputStream`). */
    private class StringListObjectInputStream(input: InputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass?): Class<*>? {
            val allowList =
                setOf("java.util.Arrays\$ArrayList", "java.util.ArrayList", "java.lang.String", "[Ljava.lang.String;")
            val name = desc?.name
            if (name != null && !allowList.contains(name)) throw ClassNotFoundException(desc.name)
            return super.resolveClass(desc)
        }
    }

    companion object {
        const val sharedPreferencesName = "FlutterSharedPreferences"
        const val keyPrefix = "flutter."
        const val listPrefix = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"
        const val jsonListPrefix = "$listPrefix!"
        const val doublePrefix = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
        const val bigIntegerPrefix = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy"

        fun encodeJsonList(value: List<String>): String = jsonListPrefix + jsonEncode(value)

        private fun decodeJsonList(json: String): List<String> =
            (jsonDecode(json) as List<*>).map { it as String }

        /** The plugin's legacy `ListEncoder.decode` (Base64 of a Java-serialized list). */
        fun decodeLegacyList(encoded: String): List<String> {
            try {
                StringListObjectInputStream(ByteArrayInputStream(Base64.decode(encoded, 0))).use { stream ->
                    return (stream.readObject() as List<*>).map { it as String }
                }
            } catch (error: java.io.IOException) {
                throw RuntimeException(error)
            } catch (error: ClassNotFoundException) {
                throw RuntimeException(error)
            }
        }

        /** The plugin's legacy `ListEncoder.encode`, for tests that build old-format fixtures. */
        fun encodeLegacyList(list: List<String>): String {
            val bytes = ByteArrayOutputStream()
            ObjectOutputStream(bytes).use { stream ->
                stream.writeObject(ArrayList(list))
                stream.flush()
            }
            return listPrefix + Base64.encodeToString(bytes.toByteArray(), 0)
        }
    }
}

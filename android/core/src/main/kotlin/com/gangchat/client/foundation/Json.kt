package com.gangchat.client.foundation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A decoded JSON object, Dart's `Map<String, Object?>` / `Map<String, dynamic>`.
 *
 * Decoded values are: `null`, [Boolean], [String], [Long] (JSON integers, like
 * Dart's 64-bit `int`), [Double] (numbers with a fraction or exponent),
 * `List<Any?>` and `Map<String, Any?>` (insertion ordered).
 *
 * JSON integers are always [Long]. Never cast a JSON number with `as Int` /
 * `as Double`; read it as [Number] (`(json["n"] as Number?)?.toInt()`) or with
 * the helpers below.
 */
typealias JsonMap = Map<String, Any?>

/** Dart's `FormatException`; `toString()` matches Dart. */
open class FormatException(
    override val message: String = "",
    val source: Any? = null,
    val offset: Int? = null,
) : Exception(message) {
    override fun toString(): String = if (message.isEmpty()) "FormatException" else "FormatException: $message"
}

private val lenientJson = Json { isLenient = false }

/** Dart's `jsonDecode(source)`; throws [FormatException] on invalid input. */
fun jsonDecode(source: String): Any? {
    val element =
        try {
            lenientJson.parseToJsonElement(source)
        } catch (error: Exception) {
            throw FormatException(error.message ?: "Invalid JSON", source)
        }
    return element.toDartValue()
}

/** Dart's `jsonEncode(value)` for maps, lists, strings, numbers, bools, null. */
fun jsonEncode(value: Any?): String = value.toJsonElement().toString()

private fun JsonElement.toDartValue(): Any? =
    when (this) {
        JsonNull -> null
        is JsonPrimitive ->
            when {
                isString -> content
                content == "true" -> true
                content == "false" -> false
                else -> parseNumber(content)
            }
        is JsonObject -> LinkedHashMap<String, Any?>(size).also { map -> for ((k, v) in this) map[k] = v.toDartValue() }
        is JsonArray -> ArrayList<Any?>(size).also { list -> for (v in this) list.add(v.toDartValue()) }
    }

private fun parseNumber(content: String): Any {
    val integral = content.none { it == '.' || it == 'e' || it == 'E' }
    if (integral) content.toLongOrNull()?.let { return it }
    return content.toDouble()
}

private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Int, is Long, is Short, is Byte -> JsonPrimitive((this as Number).toLong())
        is Double -> {
            require(isFinite()) { "Converting object to an encodable object failed: $this" }
            if (this == Math.rint(this) && kotlin.math.abs(this) < 1e15) {
                // Dart prints integral doubles with a trailing ".0".
                JsonUnquotedLiteral("${toLong()}.0")
            } else {
                JsonPrimitive(this)
            }
        }
        is Float -> toDouble().toJsonElement()
        is Number -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        is Enum<*> -> JsonPrimitive(name)
        else -> {
            val toJson = this::class.java.methods.firstOrNull { it.name == "toJson" && it.parameterCount == 0 }
            if (toJson != null) {
                toJson.invoke(this).toJsonElement()
            } else {
                throw IllegalArgumentException("Converting object to an encodable object failed: $this")
            }
        }
    }

@Suppress("FunctionName")
private fun JsonUnquotedLiteral(value: String): JsonElement =
    kotlinx.serialization.json.JsonUnquotedLiteral(value)

// --- Lenient readers mirroring the Dart models' `as T?` casts -------------

/** `json[key] as String?`, but a wrong type yields null instead of throwing. */
fun JsonMap.optString(key: String): String? = this[key] as? String

/** `(json[key] as num?)?.toInt()`. */
fun JsonMap.optInt(key: String): Int? = (this[key] as? Number)?.toInt()

/** `json[key] as int?` for values that may exceed 32 bits. */
fun JsonMap.optLong(key: String): Long? = (this[key] as? Number)?.toLong()

/** `(json[key] as num?)?.toDouble()`. */
fun JsonMap.optDouble(key: String): Double? = (this[key] as? Number)?.toDouble()

/** `json[key] as bool?`. */
fun JsonMap.optBool(key: String): Boolean? = this[key] as? Boolean

/** `json[key] as Map<String, Object?>?`. */
@Suppress("UNCHECKED_CAST")
fun JsonMap.optMap(key: String): JsonMap? = this[key] as? Map<String, Any?>

/** `json[key] as List<dynamic>?`. */
fun JsonMap.optList(key: String): List<Any?>? = this[key] as? List<Any?>

/** Casts a decoded JSON value to [JsonMap] (Dart `value as Map<String, Object?>`). */
@Suppress("UNCHECKED_CAST")
fun Any?.asJsonMap(): JsonMap = this as Map<String, Any?>

/** Dart `value is Map<String, Object?> ? value : null`. */
@Suppress("UNCHECKED_CAST")
fun Any?.asJsonMapOrNull(): JsonMap? = this as? Map<String, Any?>

/** Dart `(value as List).cast<Map<String, Object?>>()` keeping only maps. */
@Suppress("UNCHECKED_CAST")
fun Any?.asJsonMapList(): List<JsonMap> = (this as? List<*>)?.filterIsInstance<Map<*, *>>()?.map { it as JsonMap } ?: emptyList()

/** Dart `int` read from a decoded JSON number (throws like `as int`). */
fun Any?.jsonInt(): Int = (this as Number).toInt()

/** Dart `int` (64-bit) read from a decoded JSON number. */
fun Any?.jsonLong(): Long = (this as Number).toLong()

/** Dart `(value as num).toDouble()`. */
fun Any?.jsonDouble(): Double = (this as Number).toDouble()

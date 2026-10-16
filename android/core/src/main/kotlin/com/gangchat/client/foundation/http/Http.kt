package com.gangchat.client.foundation.http

import com.gangchat.client.foundation.AppCoroutines
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.utf8Encode
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.TreeMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/*
 * Kotlin port of the parts of Dart's `package:http` the app uses. Names map as
 * `http.Client` -> [HttpClient], `http.Request` -> [HttpRequest],
 * `http.Response` -> [HttpResponse], `http.StreamedResponse` ->
 * [StreamedResponse], `http.MultipartRequest` / `http.MultipartFile` ->
 * [MultipartRequest] / [MultipartFile], `http.ByteStream` -> [ByteStream],
 * `http.ClientException` -> [ClientException].
 */

/**
 * Dart's `http.ClientException`. Transport failures from the platform client
 * are mapped to messages close to Dart's `dart:io` ones (see
 * `OkHttpClientAdapter`), because callers match on them ("connection reset",
 * "connection closed", "timed out", ...).
 */
open class ClientException(
    override val message: String,
    val uri: Uri? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    override fun toString(): String = if (uri == null) "ClientException: $message" else "ClientException: $message, uri=$uri"
}

/** Case-insensitive header map, like Dart's `LinkedHashMap(equals: equalsIgnoreAsciiCase)`. */
class HeaderMap : MutableMap<String, String> by TreeMap(String.CASE_INSENSITIVE_ORDER) {
    override fun toString(): String = entries.joinToString(", ", "{", "}") { "${it.key}: ${it.value}" }
}

/**
 * Dart's `http.ByteStream`: a single-subscription stream of byte chunks.
 *
 * It is pull-based ([flow]) so request bodies keep backpressure (a Dart
 * `async*` generator feeding an upload becomes a `flow { emit(chunk) }`), and
 * it also supports Dart's `listen` API for incremental reads like SSE.
 */
class ByteStream(val flow: Flow<ByteArray>) : Stream<ByteArray> {
    private var consumed = false

    /** Dart's `byteStream.toBytes()`. */
    suspend fun toBytes(): ByteArray {
        markConsumed()
        val out = ByteArrayOutputStream()
        flow.collect { out.write(it) }
        return out.toByteArray()
    }

    /** Dart's `byteStream.bytesToString(encoding)`. */
    suspend fun bytesToString(charset: Charset = Charsets.UTF_8): String = String(toBytes(), charset)

    override fun listen(
        onData: (ByteArray) -> Unit,
        onError: ((Throwable) -> Unit)?,
        onDone: (() -> Unit)?,
        cancelOnError: Boolean,
    ): StreamSubscription<ByteArray> {
        markConsumed()
        var cancelled = false
        val job: Job =
            AppCoroutines.scope.launch(AppCoroutines.loop) {
                try {
                    flow.collect { chunk -> if (!cancelled) onData(chunk) }
                    if (!cancelled) onDone?.invoke()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (cancelled) return@launch
                    if (onError != null) onError(error) else AppCoroutines.uncaughtErrorHandler(error)
                    if (!cancelOnError) onDone?.invoke()
                }
            }
        return object : StreamSubscription<ByteArray> {
            override fun cancel() {
                cancelled = true
                job.cancel()
            }
        }
    }

    private fun markConsumed() {
        check(!consumed) { "Stream has already been listened to." }
        consumed = true
    }

    companion object {
        fun fromBytes(bytes: ByteArray): ByteStream = ByteStream(flowOf(bytes))
    }
}

/** Dart's `http.BaseRequest`. */
abstract class BaseRequest(val method: String, val url: Uri) {
    val headers: MutableMap<String, String> = HeaderMap()
    open var contentLength: Long? = null
    var persistentConnection: Boolean = true
    var followRedirects: Boolean = true
    var maxRedirects: Int = 5

    /** Content type to send, taken from [headers]. */
    internal val contentTypeHeader: String? get() = headers["content-type"]

    /** Dart's `finalize()`: the body as a byte stream. */
    abstract fun finalize(): ByteStream

    override fun toString(): String = "$method $url"
}

/** Dart's `http.Request` with a fully buffered body. */
class HttpRequest(method: String, url: Uri) : BaseRequest(method, url) {
    /** Dart's `encoding`; used for [body] and [bodyFields]. */
    var encoding: Charset = Charsets.UTF_8

    var bodyBytes: ByteArray = ByteArray(0)
        set(value) {
            field = value
        }

    override var contentLength: Long?
        get() = bodyBytes.size.toLong()
        set(_) = throw UnsupportedOperationException("Cannot set the contentLength property of non-streaming Request objects.")

    /** Dart's `body` setter: encodes and adds `charset` to the content type. */
    var body: String
        get() = String(bodyBytes, encoding)
        set(value) {
            bodyBytes = value.toByteArray(encoding)
            val contentType = headers["content-type"]
            if (contentType == null) {
                headers["content-type"] = "text/plain; charset=${charsetName(encoding)}"
            } else if (!contentType.contains("charset=", ignoreCase = true)) {
                headers["content-type"] = "$contentType; charset=${charsetName(encoding)}"
            }
        }

    /** Dart's `bodyFields` setter (form URL encoding). */
    var bodyFields: Map<String, String>
        get() = Uri.splitQueryString(body)
        set(fields) {
            val contentType = headers["content-type"]
            if (contentType == null) {
                headers["content-type"] = "application/x-www-form-urlencoded; charset=${charsetName(encoding)}"
            }
            bodyBytes =
                fields.entries.joinToString("&") { (k, v) -> "${Uri.encodeQueryComponent(k)}=${Uri.encodeQueryComponent(v)}" }
                    .toByteArray(encoding)
        }

    override fun finalize(): ByteStream = ByteStream.fromBytes(bodyBytes)

    private fun charsetName(charset: Charset): String = if (charset == Charsets.UTF_8) "utf-8" else charset.name().lowercase()
}

/** Dart's `http.StreamedRequest` is not used; uploads stream through [MultipartFile]. */

/** Dart's `http.MultipartFile`. */
class MultipartFile(
    val field: String,
    val stream: ByteStream,
    val length: Long,
    val filename: String? = null,
    val contentType: String? = null,
) {
    companion object {
        fun fromBytes(field: String, value: ByteArray, filename: String? = null, contentType: String? = null): MultipartFile =
            MultipartFile(field, ByteStream.fromBytes(value), value.size.toLong(), filename, contentType)

        fun fromString(field: String, value: String, filename: String? = null, contentType: String? = null): MultipartFile =
            fromBytes(field, utf8Encode(value), filename, contentType ?: "text/plain; charset=utf-8")
    }
}

/** Dart's `http.MultipartRequest`. */
class MultipartRequest(method: String, url: Uri) : BaseRequest(method, url) {
    val fields: MutableMap<String, String> = LinkedHashMap()
    val files: MutableList<MultipartFile> = mutableListOf()

    override fun finalize(): ByteStream =
        throw UnsupportedOperationException("MultipartRequest bodies are encoded by the HttpClient implementation")
}

/** Dart's `http.BaseResponse` fields. */
abstract class BaseResponse(
    val statusCode: Int,
    val contentLength: Long?,
    val request: BaseRequest?,
    headers: Map<String, String>,
    val isRedirect: Boolean,
    val persistentConnection: Boolean,
    val reasonPhrase: String?,
) {
    /** Response headers with lower-case names, like Dart. */
    val headers: Map<String, String> = LinkedHashMap<String, String>().also { map -> for ((k, v) in headers) map[k.lowercase()] = v }
}

/** Dart's `http.StreamedResponse`. */
class StreamedResponse(
    val stream: ByteStream,
    statusCode: Int,
    contentLength: Long? = null,
    request: BaseRequest? = null,
    headers: Map<String, String> = emptyMap(),
    isRedirect: Boolean = false,
    persistentConnection: Boolean = true,
    reasonPhrase: String? = null,
) : BaseResponse(statusCode, contentLength, request, headers, isRedirect, persistentConnection, reasonPhrase)

/** Dart's `http.Response`. */
class HttpResponse private constructor(
    val bodyBytes: ByteArray,
    statusCode: Int,
    request: BaseRequest?,
    headers: Map<String, String>,
    isRedirect: Boolean,
    persistentConnection: Boolean,
    reasonPhrase: String?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : BaseResponse(statusCode, bodyBytes.size.toLong(), request, headers, isRedirect, persistentConnection, reasonPhrase) {
    /**
     * Dart's `http.Response(body, statusCode, headers: ...)`: [body] is encoded
     * with the charset from `content-type`, defaulting to Latin-1 like Dart.
     */
    constructor(
        body: String,
        statusCode: Int,
        headers: Map<String, String> = emptyMap(),
        request: BaseRequest? = null,
        isRedirect: Boolean = false,
        persistentConnection: Boolean = true,
        reasonPhrase: String? = null,
    ) : this(body.toByteArray(charsetForHeaders(headers)), statusCode, request, headers, isRedirect, persistentConnection, reasonPhrase, Unit)

    /** Decoded with the `content-type` charset, defaulting to Latin-1 like Dart. */
    val body: String get() = String(bodyBytes, charsetForHeaders(headers))

    companion object {
        /** Dart's `http.Response.bytes(bodyBytes, statusCode, ...)`. */
        fun bytes(
            bodyBytes: ByteArray,
            statusCode: Int,
            headers: Map<String, String> = emptyMap(),
            request: BaseRequest? = null,
            isRedirect: Boolean = false,
            persistentConnection: Boolean = true,
            reasonPhrase: String? = null,
        ): HttpResponse = HttpResponse(bodyBytes, statusCode, request, headers, isRedirect, persistentConnection, reasonPhrase, Unit)

        /** Dart's `http.Response.fromStream(streamed)`. */
        suspend fun fromStream(response: StreamedResponse): HttpResponse =
            bytes(
                response.stream.toBytes(),
                response.statusCode,
                headers = response.headers,
                request = response.request,
                isRedirect = response.isRedirect,
                persistentConnection = response.persistentConnection,
                reasonPhrase = response.reasonPhrase,
            )

        internal fun charsetForHeaders(headers: Map<String, String>): Charset {
            val contentType = headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value
            val charset =
                contentType?.split(';')?.drop(1)?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                    ?.substringAfter('=')?.trim('"', ' ')
            return charset?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.ISO_8859_1
        }
    }
}

/**
 * Dart's `http.Client`. Implementations: the OkHttp-backed client returned by
 * [HttpClient] (the default, like `http.Client()`), and `MockClient` in tests.
 */
interface HttpClient {
    suspend fun send(request: BaseRequest): StreamedResponse

    fun close() {}
}

/** Dart's `http.Client()` factory: the shared platform client. */
fun HttpClient(): HttpClient = OkHttpClientAdapter.shared()

// Dart's BaseClient convenience methods.

suspend fun HttpClient.head(url: Uri, headers: Map<String, String>? = null): HttpResponse =
    sendUnstreamed("HEAD", url, headers, null, null)

suspend fun HttpClient.get(url: Uri, headers: Map<String, String>? = null): HttpResponse =
    sendUnstreamed("GET", url, headers, null, null)

/** Dart's `post`; [body] may be a `String`, `ByteArray` or `Map<String, String>`. */
suspend fun HttpClient.post(
    url: Uri,
    headers: Map<String, String>? = null,
    body: Any? = null,
    encoding: Charset? = null,
): HttpResponse = sendUnstreamed("POST", url, headers, body, encoding)

suspend fun HttpClient.put(
    url: Uri,
    headers: Map<String, String>? = null,
    body: Any? = null,
    encoding: Charset? = null,
): HttpResponse = sendUnstreamed("PUT", url, headers, body, encoding)

suspend fun HttpClient.patch(
    url: Uri,
    headers: Map<String, String>? = null,
    body: Any? = null,
    encoding: Charset? = null,
): HttpResponse = sendUnstreamed("PATCH", url, headers, body, encoding)

suspend fun HttpClient.delete(
    url: Uri,
    headers: Map<String, String>? = null,
    body: Any? = null,
    encoding: Charset? = null,
): HttpResponse = sendUnstreamed("DELETE", url, headers, body, encoding)

/** Dart's `client.read(url)`; throws [ClientException] on a non-2xx status. */
suspend fun HttpClient.read(url: Uri, headers: Map<String, String>? = null): String {
    val response = get(url, headers)
    checkResponseSuccess(url, response)
    return response.body
}

/** Dart's `client.readBytes(url)`. */
suspend fun HttpClient.readBytes(url: Uri, headers: Map<String, String>? = null): ByteArray {
    val response = get(url, headers)
    checkResponseSuccess(url, response)
    return response.bodyBytes
}

private fun checkResponseSuccess(url: Uri, response: HttpResponse) {
    if (response.statusCode < 400) return
    var message = "Request to $url failed with status ${response.statusCode}"
    if (response.reasonPhrase != null) message = "$message: ${response.reasonPhrase}"
    throw ClientException("$message.", url)
}

private suspend fun HttpClient.sendUnstreamed(
    method: String,
    url: Uri,
    headers: Map<String, String>?,
    body: Any?,
    encoding: Charset?,
): HttpResponse {
    val request = HttpRequest(method, url)
    if (headers != null) request.headers.putAll(headers)
    if (encoding != null) request.encoding = encoding
    when (body) {
        null -> Unit
        is String -> request.body = body
        is ByteArray -> request.bodyBytes = body
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            request.bodyFields = body as Map<String, String>
        }
        else -> throw IllegalArgumentException("Invalid request body \"$body\".")
    }
    return HttpResponse.fromStream(send(request))
}

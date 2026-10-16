package com.gangchat.client.foundation.http

import com.gangchat.client.foundation.AppCoroutines
import com.gangchat.client.foundation.Uri
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.http.HttpMethod
import okio.BufferedSink

/**
 * The platform `http.Client`: OkHttp under the Dart `package:http` API.
 *
 * Each [HttpClient] call returns its own adapter over one shared connection
 * pool. Like Dart's `IOClient.close()`, [close] aborts that adapter's
 * in-flight requests (for example an open event stream) and rejects new ones.
 * Like Dart's `HttpClient`, there are no read or write timeouts; callers add
 * their own.
 */
class OkHttpClientAdapter(private val client: OkHttpClient) : HttpClient {
    private val activeCalls: MutableSet<Call> = Collections.synchronizedSet(LinkedHashSet())

    @Volatile
    private var closed = false

    override suspend fun send(request: BaseRequest): StreamedResponse {
        if (closed) throw ClientException("HTTP request failed. Client is already closed.", request.url)
        val okRequest = buildRequest(request)
        val callClient =
            if (request.followRedirects && request.maxRedirects > 0) {
                client
            } else {
                client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            }
        val call = callClient.newCall(okRequest)
        activeCalls.add(call)
        val response =
            try {
                call.await()
            } catch (error: IOException) {
                activeCalls.remove(call)
                throw error.toClientException(request.url)
            }
        val body = response.body
        val headers = LinkedHashMap<String, String>()
        for (name in response.headers.names()) {
            headers[name.lowercase()] = response.headers.values(name).joinToString(",")
        }
        return StreamedResponse(
            stream = ByteStream(body.byteFlow(call, request.url)),
            statusCode = response.code,
            contentLength = body.contentLength().takeIf { it >= 0 },
            request = request,
            headers = headers,
            isRedirect = false,
            persistentConnection = !response.header("connection").equals("close", ignoreCase = true),
            reasonPhrase = response.message.ifEmpty { null },
        )
    }

    override fun close() {
        closed = true
        val calls = synchronized(activeCalls) { activeCalls.toList().also { activeCalls.clear() } }
        for (call in calls) call.cancel()
    }

    private fun buildRequest(request: BaseRequest): Request {
        val builder = Request.Builder().url(request.url.toString())
        for ((name, value) in request.headers) {
            if (name.equals("content-length", ignoreCase = true)) continue
            builder.header(name, value)
        }
        if (!request.persistentConnection) builder.header("Connection", "close")
        val body: RequestBody? =
            when (request) {
                is MultipartRequest -> multipartBody(request)
                is HttpRequest -> {
                    val mediaType = request.contentTypeHeader?.toMediaTypeOrNull()
                    if (HttpMethod.permitsRequestBody(request.method) &&
                        (request.bodyBytes.isNotEmpty() || HttpMethod.requiresRequestBody(request.method))
                    ) {
                        request.bodyBytes.toRequestBody(mediaType)
                    } else {
                        null
                    }
                }
                else -> {
                    val bytes = runBlocking { request.finalize().toBytes() }
                    if (HttpMethod.permitsRequestBody(request.method)) bytes.toRequestBody(request.contentTypeHeader?.toMediaTypeOrNull()) else null
                }
            }
        builder.method(request.method, body)
        return builder.build()
    }

    private fun multipartBody(request: MultipartRequest): RequestBody {
        val builder = MultipartBody.Builder("dart-http-boundary-" + randomBoundarySuffix()).setType(MultipartBody.FORM)
        for ((name, value) in request.fields) builder.addFormDataPart(name, value)
        for (file in request.files) {
            val disposition = StringBuilder("form-data; name=\"").append(browserEncode(file.field)).append('"')
            if (file.filename != null) disposition.append("; filename=\"").append(browserEncode(file.filename)).append('"')
            val partHeaders = Headers.Builder().addUnsafeNonAscii("Content-Disposition", disposition.toString()).build()
            builder.addPart(partHeaders, StreamingFileBody(file))
        }
        return builder.build()
    }

    /**
     * Streams a [MultipartFile] while OkHttp writes the request. The file's
     * producer runs on the event loop (it may report progress or wait for a
     * paused upload) and hands chunks over one at a time, which keeps Dart's
     * backpressure: the producer only advances as the socket accepts bytes.
     */
    private class StreamingFileBody(private val file: MultipartFile) : RequestBody() {
        override fun contentType(): MediaType? = (file.contentType ?: "application/octet-stream").toMediaTypeOrNull()

        override fun contentLength(): Long = file.length

        override fun isOneShot(): Boolean = true

        override fun writeTo(sink: BufferedSink) {
            runBlocking {
                file.stream.flow
                    .flowOn(AppCoroutines.loop)
                    .buffer(Channel.RENDEZVOUS)
                    .collect { chunk -> sink.write(chunk) }
            }
        }
    }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }
                },
            )
            continuation.invokeOnCancellation { cancel() }
        }

    private fun ResponseBody.byteFlow(call: Call, uri: Uri): Flow<ByteArray> {
        val body = this
        return flow {
            // A blocked socket read does not observe coroutine cancellation;
            // cancelling the call closes the socket and unblocks it.
            val job = currentCoroutineContext()[Job]
            val handle = job?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
            val input = body.byteStream()
            val buffer = ByteArray(16 * 1024)
            try {
                while (true) {
                    val read =
                        try {
                            withContext(Dispatchers.IO) { input.read(buffer) }
                        } catch (error: IOException) {
                            throw error.toClientException(uri)
                        }
                    if (read < 0) break
                    if (read > 0) emit(buffer.copyOf(read))
                }
            } finally {
                handle?.dispose()
                withContext(NonCancellable) { runCatching { body.close() } }
                activeCalls.remove(call)
            }
        }
    }

    companion object {
        /**
         * Hosts that always connect directly, bypassing any system proxy (the
         * app's own API, asset and release hosts; see `main.dart`'s
         * `_installConfiguredHostProxyBypass`).
         */
        @Volatile
        var directHosts: Set<String> = emptySet()

        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(0, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .proxySelector(DirectHostProxySelector)
                .build()
        }

        fun shared(): HttpClient = OkHttpClientAdapter(sharedClient)

        private fun randomBoundarySuffix(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+_."
            val random = java.security.SecureRandom()
            return buildString { repeat(70 - "dart-http-boundary-".length) { append(chars[random.nextInt(chars.length)]) } }
        }

        private fun browserEncode(value: String): String =
            value.replace(Regex("\r\n|\r|\n"), "%0D%0A").replace("\"", "%22")
    }

    private object DirectHostProxySelector : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val host = uri?.host?.lowercase()
            if (host != null && host in directHosts) return listOf(Proxy.NO_PROXY)
            return getDefault()?.select(uri) ?: listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            getDefault()?.connectFailed(uri, sa, ioe)
        }
    }
}

/**
 * Maps a platform transport failure onto a [ClientException] whose message
 * matches what Dart's `dart:io` reported, because the ported code matches on
 * these words (`api_client.dart` retry checks, `userFacingErrorMessage`).
 */
internal fun IOException.toClientException(uri: Uri): ClientException {
    val raw = message ?: javaClass.simpleName
    val lower = raw.lowercase()
    val text =
        when {
            this is UnknownHostException -> "SocketException: Failed host lookup: '${uri.host}' ($raw)"
            this is ConnectException -> "SocketException: Connection refused ($raw)"
            this is SSLException -> "HandshakeException: $raw"
            this is InterruptedIOException && lower.contains("timeout") -> "SocketException: Connection timed out ($raw)"
            this is EOFException || lower.contains("unexpected end of stream") ->
                "Connection closed before full header was received"
            lower.contains("reset") -> "Connection reset by peer"
            lower.contains("broken pipe") -> "Broken pipe"
            lower.contains("abort") -> "Software caused connection abort"
            lower == "canceled" || lower == "socket closed" -> "Connection closed while receiving data"
            else -> "Connection failed: $raw"
        }
    return ClientException(text, uri, this)
}


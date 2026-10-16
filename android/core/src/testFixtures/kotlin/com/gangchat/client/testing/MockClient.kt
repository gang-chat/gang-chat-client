package com.gangchat.client.testing

import com.gangchat.client.foundation.http.BaseRequest
import com.gangchat.client.foundation.http.ByteStream
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.http.MultipartRequest
import com.gangchat.client.foundation.http.StreamedResponse
import com.gangchat.client.foundation.utf8Encode
import java.io.ByteArrayOutputStream

/**
 * Dart's `package:http/testing.dart` `MockClient`: answers every request with
 * [handler]. The handler receives a buffered [HttpRequest] copy (headers and
 * body bytes), like Dart. Multipart requests are flattened to a readable body
 * (`name=value` fields and file parts) so tests can assert on them.
 */
class MockClient(private val handler: suspend (HttpRequest) -> HttpResponse) : HttpClient {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun send(request: BaseRequest): StreamedResponse {
        val copy = HttpRequest(request.method, request.url)
        copy.headers.putAll(request.headers)
        copy.bodyBytes =
            when (request) {
                is HttpRequest -> request.bodyBytes
                is MultipartRequest -> flattenMultipart(request)
                else -> request.finalize().toBytes()
            }
        requests.add(copy)
        val response = handler(copy)
        return StreamedResponse(
            stream = ByteStream.fromBytes(response.bodyBytes),
            statusCode = response.statusCode,
            contentLength = response.contentLength,
            request = request,
            headers = response.headers,
            isRedirect = response.isRedirect,
            persistentConnection = response.persistentConnection,
            reasonPhrase = response.reasonPhrase,
        )
    }

    private suspend fun flattenMultipart(request: MultipartRequest): ByteArray {
        val out = ByteArrayOutputStream()
        for ((name, value) in request.fields) out.write(utf8Encode("--field $name\r\n$value\r\n"))
        for (file in request.files) {
            out.write(utf8Encode("--file ${file.field} filename=${file.filename} length=${file.length}\r\n"))
            out.write(file.stream.toBytes())
            out.write(utf8Encode("\r\n"))
        }
        return out.toByteArray()
    }

    companion object {
        /** Dart's `MockClient.streaming((request, bodyStream) async => ...)`. */
        fun streaming(handler: suspend (BaseRequest) -> StreamedResponse): HttpClient =
            object : HttpClient {
                override suspend fun send(request: BaseRequest): StreamedResponse = handler(request)
            }
    }
}

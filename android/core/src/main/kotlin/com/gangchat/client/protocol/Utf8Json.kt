package com.gangchat.client.protocol

import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Decode

const val jsonUtf8ContentType = "application/json; charset=utf-8"
const val jsonAcceptHeader = "application/json"

fun encodeJsonBody(value: Any?): String = jsonEncode(value)

fun decodeUtf8Body(response: HttpResponse): String = utf8Decode(response.bodyBytes, allowMalformed = false)

fun decodeJsonBody(response: HttpResponse): Any? {
    val body = decodeUtf8Body(response)
    if (body.trim().isEmpty()) return null
    return jsonDecode(body)
}

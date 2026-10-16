package com.gangchat.client.foundation

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Dart's `utf8.decode(bytes, allowMalformed: ...)`. */
fun utf8Decode(bytes: ByteArray, allowMalformed: Boolean = false): String {
    val decoder = StandardCharsets.UTF_8.newDecoder()
    val action = if (allowMalformed) CodingErrorAction.REPLACE else CodingErrorAction.REPORT
    decoder.onMalformedInput(action).onUnmappableCharacter(action)
    return try {
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (error: java.nio.charset.CharacterCodingException) {
        throw FormatException("Invalid UTF-8 byte", bytes)
    }
}

/** Dart's `utf8.encode(string)`. */
fun utf8Encode(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8)

/** Dart's `base64Encode(bytes)`. */
fun base64Encode(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

/** Dart's `base64Decode(source)`; throws [FormatException] like Dart. */
fun base64Decode(source: String): ByteArray =
    try {
        java.util.Base64.getDecoder().decode(source)
    } catch (error: IllegalArgumentException) {
        throw FormatException("Invalid character", source)
    }

/** Dart's `base64Url.encode(bytes)`. */
fun base64UrlEncode(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().encodeToString(bytes)

/** Dart's `base64Url.decode(source)` (accepts missing padding like Dart's normalize). */
fun base64UrlDecode(source: String): ByteArray =
    try {
        java.util.Base64.getUrlDecoder().decode(source)
    } catch (error: IllegalArgumentException) {
        throw FormatException("Invalid character", source)
    }

/**
 * Dart's `stream.transform(utf8.decoder).transform(const LineSplitter())` on a
 * byte stream: decodes UTF-8 across chunk boundaries and emits lines without
 * their `\n` / `\r\n` / `\r` terminators. A trailing partial line is emitted
 * when the source closes.
 */
fun Stream<ByteArray>.utf8Lines(): Stream<String> {
    val source = this
    return object : Stream<String> {
        override fun listen(
            onData: (String) -> Unit,
            onError: ((Throwable) -> Unit)?,
            onDone: (() -> Unit)?,
            cancelOnError: Boolean,
        ): StreamSubscription<String> {
            val decoder: CharsetDecoder =
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val carry = ByteArrayOutputStream()
            val line = StringBuilder()
            var lastWasCr = false

            fun emitChars(chars: CharSequence) {
                for (c in chars) {
                    if (lastWasCr && c == '\n') {
                        lastWasCr = false
                        continue
                    }
                    lastWasCr = false
                    when (c) {
                        '\n' -> {
                            onData(line.toString())
                            line.setLength(0)
                        }
                        '\r' -> {
                            onData(line.toString())
                            line.setLength(0)
                            lastWasCr = true
                        }
                        else -> line.append(c)
                    }
                }
            }

            fun decodeChunk(bytes: ByteArray, endOfInput: Boolean) {
                carry.write(bytes)
                val input = ByteBuffer.wrap(carry.toByteArray())
                val output = CharBuffer.allocate(input.remaining() + 16)
                decoder.decode(input, output, endOfInput)
                if (endOfInput) decoder.flush(output)
                output.flip()
                carry.reset()
                if (input.hasRemaining()) carry.write(input.array(), input.position(), input.remaining())
                emitChars(output)
            }

            val upstream = source.listen(
                { bytes -> decodeChunk(bytes, false) },
                onError,
                {
                    decodeChunk(ByteArray(0), true)
                    if (line.isNotEmpty()) {
                        onData(line.toString())
                        line.setLength(0)
                    }
                    onDone?.invoke()
                },
                cancelOnError,
            )
            return object : StreamSubscription<String> {
                override fun cancel() = upstream.cancel()
            }
        }
    }
}

/** Dart's `const LineSplitter().convert(text)`. */
fun splitLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\r' || c == '\n') {
            lines.add(current.toString())
            current.setLength(0)
            if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
        } else {
            current.append(c)
        }
        i++
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
}

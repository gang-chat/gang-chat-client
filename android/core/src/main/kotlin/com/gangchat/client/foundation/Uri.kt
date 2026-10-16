package com.gangchat.client.foundation

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Dart's `Uri`, covering the parts the app uses: parsing with Dart's
 * normalization (lower-case scheme/host, default port dropped, invalid
 * characters percent-encoded), component getters, `queryParameters`,
 * `replace`, `resolve` and the static encode/decode helpers.
 *
 * Components are stored percent-encoded, exactly like Dart, so [toString]
 * round-trips and [path] / [query] return the encoded text while
 * [pathSegments] / [queryParameters] return decoded values.
 */
class Uri private constructor(
    val scheme: String,
    private val userInfoRaw: String,
    private val hostRaw: String?,
    private val portRaw: Int?,
    val path: String,
    private val queryRaw: String?,
    private val fragmentRaw: String?,
) {
    val userInfo: String get() = userInfoRaw

    val host: String get() = hostRaw ?: ""

    /** Explicit port, or the scheme's default (80/443), or 0. */
    val port: Int get() = portRaw ?: defaultPort(scheme)

    val query: String get() = queryRaw ?: ""

    val fragment: String get() = fragmentRaw ?: ""

    val hasScheme: Boolean get() = scheme.isNotEmpty()

    val hasAuthority: Boolean get() = hostRaw != null

    val hasPort: Boolean get() = portRaw != null

    val hasQuery: Boolean get() = queryRaw != null

    val hasFragment: Boolean get() = fragmentRaw != null

    val hasEmptyPath: Boolean get() = path.isEmpty()

    val hasAbsolutePath: Boolean get() = path.startsWith("/")

    /** Dart: has a scheme and no fragment. */
    val isAbsolute: Boolean get() = hasScheme && !hasFragment

    val authority: String
        get() {
            if (hostRaw == null) return ""
            val builder = StringBuilder()
            if (userInfoRaw.isNotEmpty()) builder.append(userInfoRaw).append('@')
            builder.append(hostRaw)
            if (portRaw != null) builder.append(':').append(portRaw)
            return builder.toString()
        }

    /** Dart `origin`: `scheme://host[:port]`, only for http/https. */
    val origin: String
        get() {
            check(scheme == "http" || scheme == "https") { "Origin is only applicable to schemes http and https: $this" }
            check(hostRaw != null && hostRaw.isNotEmpty()) { "A $scheme: URI should have a non-empty host name: $this" }
            return if (portRaw == null) "$scheme://$hostRaw" else "$scheme://$hostRaw:$portRaw"
        }

    /** Decoded path segments (Dart drops the leading empty segment). */
    val pathSegments: List<String>
        get() {
            if (path.isEmpty()) return emptyList()
            val trimmed = if (path.startsWith("/")) path.substring(1) else path
            if (trimmed.isEmpty()) return emptyList()
            return trimmed.split('/').map { decodeComponent(it) }
        }

    /** Decoded query parameters; `+` is a space; the last value of a key wins. */
    val queryParameters: Map<String, String>
        get() {
            val result = LinkedHashMap<String, String>()
            for ((key, value) in splitQuery(query)) result[key] = value
            return result
        }

    /** Decoded query parameters with every value of repeated keys. */
    val queryParametersAll: Map<String, List<String>>
        get() {
            val result = LinkedHashMap<String, MutableList<String>>()
            for ((key, value) in splitQuery(query)) result.getOrPut(key) { mutableListOf() }.add(value)
            return result
        }

    /**
     * Dart's `replace(...)`. Pass [queryParameters] / [pathSegments] to have
     * them encoded; pass [query] / [path] as already-encoded text.
     */
    fun replace(
        scheme: String? = null,
        userInfo: String? = null,
        host: String? = null,
        port: Int? = null,
        path: String? = null,
        pathSegments: Iterable<String>? = null,
        query: String? = null,
        queryParameters: Map<String, Any?>? = null,
        fragment: String? = null,
    ): Uri {
        val newScheme = scheme?.lowercase() ?: this.scheme
        val newPath =
            when {
                pathSegments != null -> makePathFromSegments(pathSegments, hostRaw != null || host != null)
                path != null -> normalize(path, pathChars)
                else -> this.path
            }
        val newQuery =
            when {
                queryParameters != null -> makeQuery(queryParameters)
                query != null -> normalize(query, queryChars)
                else -> queryRaw
            }
        return Uri(
            newScheme,
            userInfo?.let { normalize(it, userInfoChars) } ?: userInfoRaw,
            host?.lowercase() ?: hostRaw,
            (port ?: portRaw)?.takeUnless { it == defaultPort(newScheme) },
            newPath,
            newQuery,
            fragment?.let { normalize(it, queryChars) } ?: fragmentRaw,
        )
    }

    /** Dart's `removeFragment()`. */
    fun removeFragment(): Uri = Uri(scheme, userInfoRaw, hostRaw, portRaw, path, queryRaw, null)

    /** Dart's `resolve(reference)` (RFC 3986 reference resolution). */
    fun resolve(reference: String): Uri = resolveUri(parse(reference))

    fun resolveUri(reference: Uri): Uri {
        if (reference.hasScheme) {
            return Uri(reference.scheme, reference.userInfoRaw, reference.hostRaw, reference.portRaw, removeDotSegments(reference.path), reference.queryRaw, reference.fragmentRaw)
        }
        if (reference.hasAuthority) {
            return Uri(scheme, reference.userInfoRaw, reference.hostRaw, reference.portRaw, removeDotSegments(reference.path), reference.queryRaw, reference.fragmentRaw)
        }
        if (reference.path.isEmpty()) {
            return Uri(scheme, userInfoRaw, hostRaw, portRaw, path, reference.queryRaw ?: queryRaw, reference.fragmentRaw)
        }
        val merged =
            if (reference.path.startsWith("/")) {
                reference.path
            } else if (hostRaw != null && path.isEmpty()) {
                "/" + reference.path
            } else {
                path.substring(0, path.lastIndexOf('/') + 1) + reference.path
            }
        return Uri(scheme, userInfoRaw, hostRaw, portRaw, removeDotSegments(merged), reference.queryRaw, reference.fragmentRaw)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        if (scheme.isNotEmpty()) builder.append(scheme).append(':')
        if (hostRaw != null || scheme == "file") builder.append("//").append(authority)
        builder.append(path)
        if (queryRaw != null) builder.append('?').append(queryRaw)
        if (fragmentRaw != null) builder.append('#').append(fragmentRaw)
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean = other is Uri && other.toString() == toString()

    override fun hashCode(): Int = toString().hashCode()

    companion object {
        /**
         * Dart's `Uri(scheme:, userInfo:, host:, port:, path:, pathSegments:,
         * query:, queryParameters:, fragment:)`.
         */
        operator fun invoke(
            scheme: String = "",
            userInfo: String = "",
            host: String? = null,
            port: Int? = null,
            path: String? = null,
            pathSegments: Iterable<String>? = null,
            query: String? = null,
            queryParameters: Map<String, Any?>? = null,
            fragment: String? = null,
        ): Uri {
            val lowerScheme = scheme.lowercase()
            val hasAuthority = host != null || userInfo.isNotEmpty() || port != null
            var encodedPath =
                when {
                    pathSegments != null -> makePathFromSegments(pathSegments, hasAuthority)
                    path != null -> normalize(path, pathChars)
                    else -> ""
                }
            if (hasAuthority && encodedPath.isNotEmpty() && !encodedPath.startsWith("/")) encodedPath = "/$encodedPath"
            val encodedQuery =
                when {
                    queryParameters != null -> makeQuery(queryParameters)
                    query != null -> normalize(query, queryChars)
                    else -> null
                }
            return Uri(
                lowerScheme,
                normalize(userInfo, userInfoChars),
                if (hasAuthority) (host ?: "").lowercase() else null,
                port?.takeUnless { it == defaultPort(lowerScheme) },
                encodedPath,
                encodedQuery,
                fragment?.let { normalize(it, queryChars) },
            )
        }

        /** Dart's `Uri.http(authority, path, queryParameters)`. */
        fun http(authority: String, path: String = "", queryParameters: Map<String, Any?>? = null): Uri =
            fromAuthority("http", authority, path, queryParameters)

        /** Dart's `Uri.https(authority, path, queryParameters)`. */
        fun https(authority: String, path: String = "", queryParameters: Map<String, Any?>? = null): Uri =
            fromAuthority("https", authority, path, queryParameters)

        private fun fromAuthority(scheme: String, authority: String, path: String, queryParameters: Map<String, Any?>?): Uri {
            val parsed = parse("$scheme://$authority")
            return Uri(scheme, parsed.userInfoRaw, parsed.hostRaw ?: "", parsed.portRaw, normalize(path, pathChars).let { if (it.isEmpty() || it.startsWith("/")) it else "/$it" }, queryParameters?.let { makeQuery(it) }, null)
        }

        /** Dart's `Uri.parse(uri)`; throws [FormatException]. */
        fun parse(uri: String): Uri = tryParse(uri) ?: throw FormatException("Invalid URI", uri)

        /** Dart's `Uri.tryParse(uri)`. */
        fun tryParse(uri: String): Uri? {
            // Dart reads a leading ':' as the end of an empty scheme and fails
            // ("Invalid empty scheme"); the RFC 3986 regex would take it as a path.
            if (uri.startsWith(":")) return null
            val match = rfc3986.matchEntire(uri) ?: return null
            val g = match.groups
            val scheme = g[2]?.value ?: ""
            if (scheme.isNotEmpty() && !schemePattern.matches(scheme)) return null
            val lowerScheme = scheme.lowercase()
            val authority = g[4]?.value
            var userInfo = ""
            var host: String? = null
            var port: Int? = null
            if (authority != null) {
                var rest = authority
                val at = rest.lastIndexOf('@')
                if (at >= 0) {
                    userInfo = normalize(rest.substring(0, at), userInfoChars)
                    rest = rest.substring(at + 1)
                }
                val portSeparator =
                    if (rest.startsWith("[")) {
                        val close = rest.indexOf(']')
                        if (close < 0) return null
                        rest.indexOf(':', close)
                    } else {
                        rest.lastIndexOf(':')
                    }
                if (portSeparator >= 0) {
                    val portText = rest.substring(portSeparator + 1)
                    rest = rest.substring(0, portSeparator)
                    if (portText.isNotEmpty()) {
                        if (portText.any { !it.isDigit() }) return null
                        port = portText.toIntOrNull() ?: return null
                    }
                }
                host = normalize(rest, hostChars).lowercase()
            }
            val path = normalize(g[5]?.value ?: "", pathChars)
            val query = g[7]?.value?.let { normalize(it, queryChars) }
            val fragment = g[9]?.value?.let { normalize(it, queryChars) }
            return Uri(lowerScheme, userInfo, host, port?.takeUnless { it == defaultPort(lowerScheme) }, path, query, fragment)
        }

        /** Dart's `Uri.encodeComponent`: keeps `A-Za-z0-9-_.!~*'()`. */
        fun encodeComponent(component: String): String = encode(component, componentChars, spaceToPlus = false)

        /** Dart's `Uri.encodeQueryComponent`: keeps `A-Za-z0-9-._~`, space -> `+`. */
        fun encodeQueryComponent(component: String): String = encode(component, unreservedChars, spaceToPlus = true)

        /** Dart's `Uri.encodeFull`. */
        fun encodeFull(uri: String): String = encode(uri, fullChars, spaceToPlus = false)

        /** Dart's `Uri.decodeComponent`; throws [IllegalArgumentException] on bad escapes. */
        fun decodeComponent(encodedComponent: String): String = decode(encodedComponent, plusToSpace = false)

        /** Dart's `Uri.decodeQueryComponent`. */
        fun decodeQueryComponent(encodedComponent: String): String = decode(encodedComponent, plusToSpace = true)

        /** Dart's `Uri.decodeFull`. */
        fun decodeFull(uri: String): String = decode(uri, plusToSpace = false)

        /** Dart's `Uri.splitQueryString`. */
        fun splitQueryString(query: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            for ((key, value) in splitQuery(query)) result[key] = value
            return result
        }

        private val rfc3986 = Regex("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?$", RegexOption.DOT_MATCHES_ALL)
        private val schemePattern = Regex("[A-Za-z][A-Za-z0-9+.-]*")

        private const val alnum = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val unreservedChars = (alnum + "-._~").toSet()
        private val componentChars = (alnum + "-_.!~*'()").toSet()
        private val subDelims = "!$&'()*+,;="
        private val userInfoChars = (alnum + "-._~" + subDelims + ":").toSet()
        private val hostChars = (alnum + "-._~" + subDelims + "[]:").toSet()
        private val pathChars = (alnum + "-._~" + subDelims + ":@/").toSet()
        private val queryChars = (alnum + "-._~" + subDelims + ":@/?").toSet()
        private val fullChars = (alnum + "!#$&'()*+,-./:;=?@_~").toSet()

        private fun defaultPort(scheme: String): Int =
            when (scheme) {
                "http", "ws" -> 80
                "https", "wss" -> 443
                else -> 0
            }

        private fun encode(text: String, allowed: Set<Char>, spaceToPlus: Boolean): String {
            val builder = StringBuilder()
            for (byte in text.toByteArray(StandardCharsets.UTF_8)) {
                val c = (byte.toInt() and 0xFF).toChar()
                when {
                    byte >= 0 && c in allowed -> builder.append(c)
                    spaceToPlus && c == ' ' -> builder.append('+')
                    else -> builder.append('%').append(hex[(byte.toInt() shr 4) and 0xF]).append(hex[byte.toInt() and 0xF])
                }
            }
            return builder.toString()
        }

        /** Percent-encodes characters outside [allowed], keeping valid `%XX` escapes. */
        private fun normalize(text: String, allowed: Set<Char>): String {
            if (text.all { it in allowed }) return text
            val builder = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '%' && i + 2 < text.length && isHex(text[i + 1]) && isHex(text[i + 2])) {
                    builder.append('%').append(text[i + 1].uppercaseChar()).append(text[i + 2].uppercaseChar())
                    i += 3
                    continue
                }
                if (c in allowed) {
                    builder.append(c)
                } else {
                    val codePointEnd = if (Character.isHighSurrogate(c) && i + 1 < text.length) i + 2 else i + 1
                    builder.append(encode(text.substring(i, codePointEnd), emptySet(), spaceToPlus = false))
                    i = codePointEnd
                    continue
                }
                i++
            }
            return builder.toString()
        }

        private fun decode(text: String, plusToSpace: Boolean): String {
            if (text.none { it == '%' || (plusToSpace && it == '+') }) return text
            val bytes = ByteArrayOutputStream()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '%' -> {
                        require(i + 2 < text.length) { "Truncated URI" }
                        require(isHex(text[i + 1]) && isHex(text[i + 2])) { "Invalid URL encoding" }
                        bytes.write(text.substring(i + 1, i + 3).toInt(16))
                        i += 3
                    }
                    plusToSpace && c == '+' -> {
                        bytes.write(' '.code)
                        i++
                    }
                    else -> {
                        val end = if (Character.isHighSurrogate(c) && i + 1 < text.length) i + 2 else i + 1
                        bytes.write(text.substring(i, end).toByteArray(StandardCharsets.UTF_8))
                        i = end
                    }
                }
            }
            return String(bytes.toByteArray(), StandardCharsets.UTF_8)
        }

        private fun splitQuery(query: String): List<Pair<String, String>> {
            if (query.isEmpty()) return emptyList()
            return query.split('&').filter { it.isNotEmpty() }.map { part ->
                val eq = part.indexOf('=')
                if (eq < 0) {
                    decodeQueryComponent(part) to ""
                } else {
                    decodeQueryComponent(part.substring(0, eq)) to decodeQueryComponent(part.substring(eq + 1))
                }
            }
        }

        private fun makeQuery(parameters: Map<String, Any?>): String {
            val parts = mutableListOf<String>()
            for ((key, value) in parameters) {
                val encodedKey = encodeQueryComponent(key)
                when (value) {
                    null -> parts.add(encodedKey)
                    is Iterable<*> -> for (item in value) parts.add("$encodedKey=${encodeQueryComponent(item.toString())}")
                    else -> parts.add("$encodedKey=${encodeQueryComponent(value.toString())}")
                }
            }
            return parts.joinToString("&")
        }

        private fun makePathFromSegments(segments: Iterable<String>, hasAuthority: Boolean): String {
            val joined = segments.joinToString("/") { encode(it, pathChars - '/', spaceToPlus = false) }
            return if (hasAuthority && joined.isNotEmpty()) "/$joined" else joined
        }

        /** RFC 3986 section 5.2.4. */
        private fun removeDotSegments(path: String): String {
            if (!path.contains('.')) return path
            var input = path
            val output = StringBuilder()
            fun dropLastSegment() {
                val index = output.lastIndexOf("/")
                output.setLength(if (index >= 0) index else 0)
            }
            while (input.isNotEmpty()) {
                when {
                    input.startsWith("../") -> input = input.substring(3)
                    input.startsWith("./") -> input = input.substring(2)
                    input.startsWith("/./") -> input = input.substring(2)
                    input == "/." -> input = "/"
                    input.startsWith("/../") -> {
                        input = input.substring(3)
                        dropLastSegment()
                    }
                    input == "/.." -> {
                        input = "/"
                        dropLastSegment()
                    }
                    input == "." || input == ".." -> input = ""
                    else -> {
                        val next = input.indexOf('/', if (input.startsWith("/")) 1 else 0)
                        if (next < 0) {
                            output.append(input)
                            input = ""
                        } else {
                            output.append(input, 0, next)
                            input = input.substring(next)
                        }
                    }
                }
            }
            return output.toString()
        }

        private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

        private val hex = "0123456789ABCDEF".toCharArray()
    }
}

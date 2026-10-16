package com.gangchat.client.auth

import com.gangchat.client.config.HostPlatform
import com.gangchat.client.foundation.http.ClientException
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.OkHttpClientAdapter
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

// Port of auth_transport.dart and its dart:io variant (auth_transport_io.dart);
// the stub variant only served the web build.

typealias AuthHttpClientFactory = () -> HttpClient

/**
 * Dart's `HttpClient()..findProxy = HttpClient.findProxyFromEnvironment`
 * wrapped in an `IOClient`: an HTTP client that routes through the proxy named
 * by the `http_proxy` / `https_proxy` / `no_proxy` environment variables
 * instead of the system proxy settings.
 */
fun createEnvironmentProxyAuthHttpClient(): HttpClient {
    val selector = _EnvironmentProxySelector { HostPlatform.environment }
    val client =
        OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .proxySelector(selector)
            .proxyAuthenticator(_EnvironmentProxyAuthenticator { HostPlatform.environment })
            .build()
    return OkHttpClientAdapter(client)
}

/**
 * Whether [error] is a failed TLS handshake. On dart:io that was a
 * `HandshakeException`; here it is an `SSLException`, either raw or as the
 * cause of the `ClientException` the platform client throws (whose message
 * starts with `HandshakeException:`).
 */
fun isTlsHandshakeFailure(error: Any): Boolean {
    if (error is SSLException) return true
    if (error is ClientException && error.cause is SSLException) return true
    return _looksLikeTlsHandshakeFailure(error)
}

private fun _looksLikeTlsHandshakeFailure(error: Any): Boolean {
    val message = error.toString().lowercase()
    return message.contains("handshakeexception") ||
        message.contains("handshake error") ||
        message.contains("tls handshake")
}

/**
 * Dart's `HttpClient.findProxyFromEnvironment(url, environment:)`: returns
 * `"DIRECT"` or `"PROXY host:port"` (port 1080 when none is configured).
 */
internal fun findProxyFromEnvironment(
    scheme: String,
    host: String,
    environment: Map<String, String>,
): String {
    fun checkNoProxy(option: String?): String? {
        if (option == null) return null
        for (raw in option.split(",")) {
            val name = raw.trim()
            if ((name.startsWith("[") && name.endsWith("]") && "[$host]" == name) ||
                (name.isNotEmpty() && host.endsWith(name))
            ) {
                return "DIRECT"
            }
        }
        return null
    }

    fun checkProxy(rawOption: String?): String? {
        if (rawOption == null) return null
        var option = rawOption.trim()
        if (option.isEmpty()) return null
        var pos = option.indexOf("://")
        if (pos >= 0) {
            option = option.substring(pos + 3)
        }
        pos = option.indexOf("/")
        if (pos >= 0) {
            option = option.substring(0, pos)
        }
        // Add default port if no port configured.
        if (option.indexOf("[") == 0) {
            val colon = option.lastIndexOf(":")
            if (option.indexOf("]") > colon) option = "$option:1080"
        } else {
            if (option.indexOf(":") == -1) option = "$option:1080"
        }
        return "PROXY $option"
    }

    val noProxy = environment["no_proxy"] ?: environment["NO_PROXY"]
    checkNoProxy(noProxy)?.let { return it }

    if (scheme == "http") {
        val proxy = environment["http_proxy"] ?: environment["HTTP_PROXY"]
        checkProxy(proxy)?.let { return it }
    } else if (scheme == "https") {
        val proxy = environment["https_proxy"] ?: environment["HTTPS_PROXY"]
        checkProxy(proxy)?.let { return it }
    }
    return "DIRECT"
}

/** A parsed `PROXY [user:password@]host:port` entry, like dart:io's `_Proxy`. */
internal class _ProxyConfig(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
)

/**
 * Parses the result of [findProxyFromEnvironment]. Returns null for `DIRECT`
 * and for entries dart:io would reject as an invalid proxy configuration.
 */
internal fun _parseProxyConfiguration(configuration: String): _ProxyConfig? {
    val trimmed = configuration.trim()
    if (!trimmed.startsWith("PROXY ")) return null
    var proxy = trimmed.substring("PROXY ".length).trim()
    var username: String? = null
    var password: String? = null
    val at = proxy.indexOf("@")
    if (at != -1) {
        val userinfo = proxy.substring(0, at).trim()
        proxy = proxy.substring(at + 1).trim()
        val colon = userinfo.indexOf(":")
        if (colon == -1 || colon == 0 || colon == proxy.length - 1) return null
        username = userinfo.substring(0, colon).trim()
        password = userinfo.substring(colon + 1).trim()
    }
    val colon = proxy.lastIndexOf(":")
    if (colon == -1 || colon == 0 || colon == proxy.length - 1) return null
    var host = proxy.substring(0, colon).trim()
    if (host.startsWith("[") && host.endsWith("]")) {
        host = host.substring(1, host.length - 1)
    }
    val port = proxy.substring(colon + 1).trim().toIntOrNull() ?: return null
    return _ProxyConfig(host, port, username, password)
}

private class _EnvironmentProxySelector(
    private val environment: () -> Map<String, String>,
) : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        val scheme = uri?.scheme?.lowercase() ?: return listOf(Proxy.NO_PROXY)
        val host = uri.host ?: return listOf(Proxy.NO_PROXY)
        val config =
            _parseProxyConfiguration(findProxyFromEnvironment(scheme, host, environment()))
                ?: return listOf(Proxy.NO_PROXY)
        return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(config.host, config.port)))
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
}

/**
 * dart:io sends `Proxy-Authorization: Basic ...` when the proxy entry carries
 * credentials (`http://user:password@host:port`).
 */
private class _EnvironmentProxyAuthenticator(
    private val environment: () -> Map<String, String>,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Proxy-Authorization") != null) return null
        val url = response.request.url
        val config =
            _parseProxyConfiguration(findProxyFromEnvironment(url.scheme, url.host, environment()))
                ?: return null
        val username = config.username ?: return null
        val password = config.password ?: return null
        return response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(username, password))
            .build()
    }
}

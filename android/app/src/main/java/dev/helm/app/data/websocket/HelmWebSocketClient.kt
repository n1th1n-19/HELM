package dev.helm.app.data.websocket

import dev.helm.app.data.model.HelmEnvelope
import dev.helm.app.data.prefs.ConnectionMode
import dev.helm.app.data.prefs.ConnectionPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

@Singleton
class HelmWebSocketClient @Inject constructor(
    private val httpClient: HttpClient,
    private val prefs: ConnectionPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var session: WebSocketSession? = null

    private data class ConnectionParams(
        val url: String,
        val token: String?,
        val certFingerprint: String?,
    )

    private suspend fun resolveParams(): ConnectionParams {
        val port = prefs.wifiPort.first()
        val mode = prefs.mode.first()
        val token = prefs.token.first()
        val fingerprint = prefs.certFingerprint.first()
        return if (mode == ConnectionMode.WIFI) {
            val host = prefs.wifiHost.first().ifBlank { "localhost" }
            val scheme = if (fingerprint != null) "wss" else "ws"
            ConnectionParams("$scheme://$host:$port/helm", token, fingerprint)
        } else {
            ConnectionParams("ws://localhost:$port/helm", null, null)
        }
    }

    fun connect(): Flow<HelmEnvelope> = flow {
        val params = resolveParams()
        var customClient: HttpClient? = null
        val activeClient = if (params.certFingerprint != null) {
            buildSecureHttpClient(params.certFingerprint).also { customClient = it }
        } else {
            httpClient
        }

        val sess = activeClient.webSocketSession(params.url) {
            params.token?.let { t -> headers.append("X-Helm-Token", t) }
        }
        session = sess

        try {
            for (frame in sess.incoming) {
                if (frame is Frame.Text) {
                    runCatching {
                        json.decodeFromString<HelmEnvelope>(frame.readText())
                    }.onSuccess { emit(it) }
                }
            }
        } finally {
            session = null
            runCatching { sess.close() }
            customClient?.close()
        }
    }

    suspend fun sendText(text: String) {
        session?.send(Frame.Text(text))
    }

    suspend fun disconnect() {
        session?.close()
        session = null
    }

    private fun buildSecureHttpClient(fingerprint: String): HttpClient {
        val trustManager = PinnedTrustManager(fingerprint)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true } // self-signed; fingerprint IS the auth
            .build()
        return HttpClient(OkHttp) {
            install(WebSockets) { pingIntervalMillis = 20_000 }
            engine { preconfigured = okHttpClient }
        }
    }
}

private class PinnedTrustManager(private val expectedFingerprint: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(chain[0].encoded)
            .joinToString("") { "%02x".format(it) }
        if (actual != expectedFingerprint) {
            throw CertificateException(
                "Cert fingerprint mismatch: expected $expectedFingerprint, got $actual"
            )
        }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

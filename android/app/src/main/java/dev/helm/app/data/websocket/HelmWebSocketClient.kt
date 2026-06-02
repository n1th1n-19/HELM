package dev.helm.app.data.websocket

import dev.helm.app.data.model.HelmEnvelope
import dev.helm.app.data.prefs.ConnectionMode
import dev.helm.app.data.prefs.ConnectionPreferences
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelmWebSocketClient @Inject constructor(
    private val httpClient: HttpClient,
    private val prefs: ConnectionPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var session: WebSocketSession? = null

    // USB setup: adb reverse tcp:9090 tcp:9090  (reverse, not forward)
    private suspend fun resolveUrl(): String {
        val port = prefs.wifiPort.first()
        val mode = prefs.mode.first()
        return if (mode == ConnectionMode.WIFI) {
            val host = prefs.wifiHost.first().ifBlank { "localhost" }
            "ws://$host:$port/helm"
        } else {
            "ws://localhost:$port/helm"
        }
    }

    fun connect(): Flow<HelmEnvelope> = flow {
        val url = resolveUrl()
        val sess = httpClient.webSocketSession(url)
        session = sess
        try {
            for (frame in sess.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    runCatching {
                        json.decodeFromString<HelmEnvelope>(text)
                    }.onSuccess { envelope ->
                        emit(envelope)
                    }
                }
            }
        } finally {
            session = null
            runCatching { sess.close() }
        }
    }

    suspend fun sendText(text: String) {
        session?.send(Frame.Text(text))
    }

    suspend fun disconnect() {
        session?.close()
        session = null
    }
}

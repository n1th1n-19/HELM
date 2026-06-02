package dev.helm.app.data.websocket

import dev.helm.app.data.model.HelmEnvelope
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelmWebSocketClient @Inject constructor(private val httpClient: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var session: WebSocketSession? = null

    /** URL the agent listens on (via ADB port forward: adb forward tcp:8080 tcp:8080) */
    private val wsUrl = "ws://localhost:8080/helm"

    /**
     * Connect and emit [HelmEnvelope] messages as a cold [Flow].
     * The flow completes when the session closes or an exception is thrown.
     */
    fun connect(): Flow<HelmEnvelope> = flow {
        val sess = httpClient.webSocketSession(wsUrl)
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

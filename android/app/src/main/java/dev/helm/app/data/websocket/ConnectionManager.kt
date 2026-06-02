package dev.helm.app.data.websocket

import dev.helm.app.data.model.HelmEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState { Disconnected, Connecting, Connected, Reconnecting }

@Singleton
class ConnectionManager @Inject constructor(
    private val client: HelmWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableStateFlow<HelmEnvelope?>(null)
    val messages: StateFlow<HelmEnvelope?> = _messages.asStateFlow()

    private var connectJob: Job? = null

    /** Exponential backoff delays: 1s, 2s, 4s, 8s, 30s (cap) */
    private val backoffDelays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 30_000L)

    fun start() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch { connectWithRetry() }
    }

    fun stop() {
        connectJob?.cancel()
        connectJob = null
        scope.launch {
            client.disconnect()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private suspend fun connectWithRetry() {
        var attempt = 0
        while (true) {
            _connectionState.value = if (attempt == 0) ConnectionState.Connecting
                                     else ConnectionState.Reconnecting
            try {
                client.connect().collect { envelope ->
                    _connectionState.value = ConnectionState.Connected
                    attempt = 0 // reset on successful message
                    _messages.value = envelope
                }
                // Flow completed normally (server closed connection)
            } catch (e: Exception) {
                // Connection failed — will retry
            }
            if (_connectionState.value == ConnectionState.Connected) {
                _connectionState.value = ConnectionState.Reconnecting
            }
            val delayMs = backoffDelays.getOrElse(attempt) { backoffDelays.last() }
            attempt = (attempt + 1).coerceAtMost(backoffDelays.size)
            delay(delayMs)
        }
    }

    suspend fun send(text: String) = client.sendText(text)
}

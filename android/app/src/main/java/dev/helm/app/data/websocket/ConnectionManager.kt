package dev.helm.app.data.websocket

import android.util.Log
import dev.helm.app.data.model.HelmEnvelope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HelmConnection"

enum class ConnectionState { Disconnected, Connecting, Connected, Reconnecting }

@Singleton
class ConnectionManager @Inject constructor(
    private val client: HelmWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // SharedFlow so rapid-fire messages are not dropped (StateFlow only keeps latest).
    private val _messages = MutableSharedFlow<HelmEnvelope>(extraBufferCapacity = 64)
    val messages: SharedFlow<HelmEnvelope> = _messages.asSharedFlow()

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
        // runBlocking ensures disconnect completes before stop() returns, preventing a
        // racing coroutine from closing a session opened by a subsequent start() call.
        runBlocking { client.disconnect() }
        _connectionState.value = ConnectionState.Disconnected
    }

    private suspend fun connectWithRetry() {
        var attempt = 0
        while (true) {
            _connectionState.value = if (attempt == 0) ConnectionState.Connecting
                                     else ConnectionState.Reconnecting
            try {
                client.connect(onSessionOpen = {
                    // Session HTTP upgrade complete — mark Connected before any message arrives.
                    _connectionState.value = ConnectionState.Connected
                    _lastError.value = null
                    attempt = 0
                }).collect { envelope ->
                    if (!_messages.tryEmit(envelope)) {
                        Log.w(TAG, "message buffer full — dropping ${envelope.type}")
                    }
                }
                // Flow completed normally (server closed connection)
            } catch (e: CancellationException) {
                throw e // always rethrow — cancellation must propagate
            } catch (e: Exception) {
                val raw = e.message ?: e.javaClass.simpleName
                val msg = when {
                    "unexpected end of stream" in raw.lowercase() ->
                        "Agent not reachable — make sure helm-agent is running"
                    "connection refused" in raw.lowercase() ->
                        "Connection refused — agent not running on host"
                    "failed to connect" in raw.lowercase() ->
                        "Cannot connect to agent"
                    "timeout" in raw.lowercase() ->
                        "Connection timed out"
                    else -> raw
                }
                _lastError.value = msg
                Log.w(TAG, "connection failed: $raw")
            }
            if (_connectionState.value == ConnectionState.Connected) {
                _connectionState.value = ConnectionState.Reconnecting
            }
            val delayMs = backoffDelays.getOrElse(attempt) { backoffDelays.last() }
            attempt = (attempt + 1).coerceAtMost(backoffDelays.lastIndex)
            delay(delayMs)
        }
    }

    suspend fun send(text: String) = client.sendText(text)
}

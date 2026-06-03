package dev.helm.app.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.helm.app.data.model.CommandAction
import dev.helm.app.data.model.CommandAck
import dev.helm.app.data.model.HelmCommand
import dev.helm.app.data.model.HelmState
import dev.helm.app.data.repository.HelmRepository
import dev.helm.app.data.websocket.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val repository: HelmRepository,
) : ViewModel() {
    val state: StateFlow<HelmState> = repository.state
    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private val _lastAck = MutableStateFlow<CommandAck?>(null)
    val lastAck: StateFlow<CommandAck?> = _lastAck.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pendingAckJob: Job? = null

    fun executeCommand(action: CommandAction, args: Map<String, String> = emptyMap()) {
        pendingAckJob?.cancel()
        pendingAckJob = viewModelScope.launch {
            _isExecuting.value = true
            _lastAck.value = null
            _error.value = null
            val cmd = HelmCommand(
                id = java.util.UUID.randomUUID().toString(),
                action = action,
                args = if (args.isEmpty()) null else args,
            )
            try {
                repository.sendCommand(cmd)
            } catch (e: Exception) {
                _error.value = "Send failed: ${e.message ?: e.javaClass.simpleName}"
                _isExecuting.value = false
                return@launch
            }
            // Suspends until the ack arrives or 15 s elapses.
            val ack = withTimeoutOrNull(15_000L) {
                repository.state
                    .mapNotNull { it.commandAcks[cmd.id] }
                    .first()
            }
            _lastAck.value = ack
            if (ack == null) {
                _error.value = "Command timed out"
            }
            _isExecuting.value = false
        }
    }
}

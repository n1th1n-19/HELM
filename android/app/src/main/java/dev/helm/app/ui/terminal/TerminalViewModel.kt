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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val repository: HelmRepository,
) : ViewModel() {
    val state: StateFlow<HelmState> = repository.state
    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private val _lastAck = MutableStateFlow<CommandAck?>(null)
    val lastAck: StateFlow<CommandAck?> = _lastAck.asStateFlow()

    fun executeCommand(action: CommandAction, args: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            val cmd = HelmCommand(
                id = java.util.UUID.randomUUID().toString(),
                action = action,
                args = if (args.isEmpty()) null else args,
            )
            repository.sendCommand(cmd)
            // Watch for ack
            repository.state.collect { s ->
                val ack = s.commandAcks[cmd.id]
                if (ack != null) {
                    _lastAck.value = ack
                    return@collect
                }
            }
        }
    }
}

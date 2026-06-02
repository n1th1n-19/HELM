package dev.helm.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.helm.app.data.nsd.DiscoveredAgent
import dev.helm.app.data.nsd.NsdDiscovery
import dev.helm.app.data.prefs.ConnectionMode
import dev.helm.app.data.prefs.ConnectionPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val mode: ConnectionMode = ConnectionMode.USB,
    val wifiHost: String = "",
    val wifiPort: Int = ConnectionPreferences.DEFAULT_PORT,
    val discovered: List<DiscoveredAgent> = emptyList(),
    val isDiscovering: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: ConnectionPreferences,
    private val nsdDiscovery: NsdDiscovery,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var discoveryJob: Job? = null

    init {
        viewModelScope.launch {
            combine(prefs.mode, prefs.wifiHost, prefs.wifiPort) { mode, host, port ->
                SettingsUiState(mode = mode, wifiHost = host, wifiPort = port)
            }.collect { base ->
                _state.value = _state.value.copy(
                    mode = base.mode,
                    wifiHost = base.wifiHost,
                    wifiPort = base.wifiPort,
                )
            }
        }
    }

    fun setMode(mode: ConnectionMode) {
        viewModelScope.launch { prefs.setMode(mode) }
    }

    fun setWifiHost(host: String) {
        viewModelScope.launch { prefs.setWifiHost(host) }
    }

    fun setWifiPort(port: Int) {
        viewModelScope.launch { prefs.setWifiPort(port) }
    }

    fun selectDiscovered(agent: DiscoveredAgent) {
        viewModelScope.launch {
            prefs.setWifiHost(agent.host)
            prefs.setWifiPort(agent.port)
            prefs.setMode(ConnectionMode.WIFI)
        }
    }

    fun handleQrResult(raw: String) {
        val uri = android.net.Uri.parse(raw)
        if (uri.scheme == "helm") {
            val host = uri.host ?: return
            val port = uri.port.takeIf { it > 0 } ?: ConnectionPreferences.DEFAULT_PORT
            viewModelScope.launch {
                prefs.setWifiHost(host)
                prefs.setWifiPort(port)
                prefs.setMode(ConnectionMode.WIFI)
            }
        }
    }

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        _state.value = _state.value.copy(isDiscovering = true, discovered = emptyList())
        discoveryJob = viewModelScope.launch {
            nsdDiscovery.discoverAgents(context).collect { agents ->
                _state.value = _state.value.copy(discovered = agents)
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _state.value = _state.value.copy(isDiscovering = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }
}

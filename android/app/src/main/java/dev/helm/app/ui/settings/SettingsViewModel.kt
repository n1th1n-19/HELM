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
import dev.helm.app.data.repository.HelmRepository
import dev.helm.app.data.websocket.ConnectionState
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
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val lastError: String? = null,
    val isSecured: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: ConnectionPreferences,
    private val repository: HelmRepository,
    private val nsdDiscovery: NsdDiscovery,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var discoveryJob: Job? = null

    init {
        viewModelScope.launch {
            combine(prefs.mode, prefs.wifiHost, prefs.wifiPort) { mode, host, port ->
                Triple(mode, host, port)
            }.collect { (mode, host, port) ->
                _state.value = _state.value.copy(mode = mode, wifiHost = host, wifiPort = port)
            }
        }
        viewModelScope.launch {
            repository.connectionState.collect { cs ->
                _state.value = _state.value.copy(connectionState = cs)
            }
        }
        viewModelScope.launch {
            repository.lastError.collect { err ->
                _state.value = _state.value.copy(lastError = err)
            }
        }
        viewModelScope.launch {
            prefs.certFingerprint.collect { fp ->
                _state.value = _state.value.copy(isSecured = fp != null)
            }
        }
    }

    fun setMode(mode: ConnectionMode) {
        viewModelScope.launch {
            prefs.setMode(mode)
            repository.reconnect()
        }
    }

    fun setWifiHost(host: String) {
        viewModelScope.launch { prefs.setWifiHost(host) }
    }

    fun setWifiPort(port: Int) {
        viewModelScope.launch { prefs.setWifiPort(port) }
    }

    /** Save host+port then reconnect. Called by the Save button. */
    fun saveAndConnect(host: String, port: Int) {
        viewModelScope.launch {
            prefs.setWifiHost(host)
            prefs.setWifiPort(port)
            repository.reconnect()
        }
    }

    fun selectDiscovered(agent: DiscoveredAgent) {
        viewModelScope.launch {
            prefs.setWifiHost(agent.host)
            prefs.setWifiPort(agent.port)
            prefs.setToken(null)
            prefs.setCertFingerprint(null)
            prefs.setMode(ConnectionMode.WIFI)
            repository.reconnect()
        }
    }

    fun handleQrResult(raw: String) {
        val uri = android.net.Uri.parse(raw)
        when (uri.scheme) {
            "helm" -> {
                val host = uri.host ?: return
                val port = uri.port.takeIf { it > 0 } ?: ConnectionPreferences.DEFAULT_PORT
                viewModelScope.launch {
                    prefs.setWifiHost(host)
                    prefs.setWifiPort(port)
                    prefs.setToken(null)
                    prefs.setCertFingerprint(null)
                    prefs.setMode(ConnectionMode.WIFI)
                    repository.reconnect()
                }
            }
            "helms" -> {
                val host = uri.host ?: return
                val port = uri.port.takeIf { it > 0 } ?: ConnectionPreferences.DEFAULT_PORT
                val token = uri.getQueryParameter("token") ?: return
                val fingerprint = uri.getQueryParameter("cert") ?: return
                viewModelScope.launch {
                    prefs.setWifiHost(host)
                    prefs.setWifiPort(port)
                    prefs.setToken(token)
                    prefs.setCertFingerprint(fingerprint)
                    prefs.setMode(ConnectionMode.WIFI)
                    repository.reconnect()
                }
            }
        }
    }

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            _state.value = _state.value.copy(isDiscovering = true, discovered = emptyList())
            try {
                nsdDiscovery.discoverAgents(context).collect { agents ->
                    _state.value = _state.value.copy(discovered = agents)
                }
            } finally {
                _state.value = _state.value.copy(isDiscovering = false)
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
